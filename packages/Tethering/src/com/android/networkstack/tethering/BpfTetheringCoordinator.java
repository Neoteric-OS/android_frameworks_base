/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.networkstack.tethering;

import static android.net.NetworkStats.DEFAULT_NETWORK_NO;
import static android.net.NetworkStats.METERED_NO;
import static android.net.NetworkStats.ROAMING_NO;
import static android.net.NetworkStats.SET_DEFAULT;
import static android.net.NetworkStats.TAG_NONE;
import static android.net.NetworkStats.UID_ALL;
import static android.net.NetworkStats.UID_TETHERING;
import static android.net.netstats.provider.NetworkStatsProvider.QUOTA_UNLIMITED;

import android.app.usage.NetworkStatsManager;
import android.net.INetd;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkStats;
import android.net.NetworkStats.Entry;
import android.net.TetherOffloadRuleParcel;
import android.net.TetherStatsParcel;
import android.net.netstats.provider.NetworkStatsProvider;
import android.net.util.SharedLog;
import android.net.util.TetheringUtils.ForwardedStats;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.text.TextUtils;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.NetworkInterface;
import java.util.HashMap;
import java.util.HashSet;

/**
 *  This coordinator is responsible for providing BPF offload relevant stuff.
 *  - Get tethering stats.
 *  - Set data limit.
 *  - Set global alert.
 *  - Add/remove forwarding rules.
 *
 * @hide
 */
public class BpfTetheringCoordinator {
    private static final String TAG = BpfTetheringCoordinator.class.getSimpleName();
    @VisibleForTesting
    static final int DEFAULT_PERFORM_POLL_INTERVAL_MS = 5000; // TODO: Make it customizable.

    @VisibleForTesting
    enum StatsType {
        STATS_PER_IFACE,
        STATS_PER_UID,
    }

    @NonNull
    private final Handler mHandler;
    @NonNull
    private final INetd mNetd;
    @NonNull
    private final SharedLog mLog;
    @NonNull
    private final Dependencies mDeps;
    @Nullable
    private final BpfTetherStatsProvider mStatsProvider;
    private UpstreamNetworkState mUpstreamNetworkState;
    private boolean mStarted = false;

    // Tracking remaining alert quota. Unlike limit quota is subject to interface, the alert
    // quota is interface independent and global for tether offload.
    private long mRemainingAlertQuota = QUOTA_UNLIMITED;

    // Maps upstream interface index to offloaded traffic statistics.
    // Always contains the latest value received from the BPF maps for each interface, regardless
    // of whether offload is currently running on that interface.
    private SparseArray<ForwardedStats> mOffloadTetherStats = new SparseArray<>();

    // Maps upstream interface names to interface quotas.
    // Always contains the latest value received from the framework for each interface, regardless
    // of whether offload is currently running (or is even supported) on that interface. Only
    // includes interfaces that have a quota set.
    private HashMap<String, Long> mInterfaceQuotas = new HashMap<>();

    // Maps upstream interface index to interface names.
    // Store all interface name since boot. Used for lookup what interface name it is from the
    // tether stats got from netd because netd reports interface index to present an interface.
    private SparseArray<String> mInterfaceNames = new SparseArray<>();

    // Maps upstream interface index to the client address who is using on the upstream.
    // Used to monitor if any client is using a given upstream. It helps to do upstream
    // initialization and cleanup. Note that we don't care the client on which downstream.
    private SparseArray<HashSet<Inet6Address>> mClientAddresses = new SparseArray<>();

    // Runnable that used by scheduling next polling of stats.
    private final Runnable mScheduledPollingTask = () -> {
        updateForwardedStatsFromNetd();
        maybeSchedulePollingStats();
    };

    @VisibleForTesting
    static class Dependencies {
        int getPerformPollInterval() {
            // TODO: Consider make this configurable.
            return DEFAULT_PERFORM_POLL_INTERVAL_MS;
        }
    }

    BpfTetheringCoordinator(@NonNull Handler handler, @NonNull INetd netd,
            @NonNull NetworkStatsManager nsm, @NonNull SharedLog log, @NonNull Dependencies deps) {
        mHandler = handler;
        mNetd = netd;
        mLog = log.forSubComponent(TAG);
        BpfTetherStatsProvider provider = new BpfTetherStatsProvider();
        try {
            nsm.registerNetworkStatsProvider(getClass().getSimpleName(), provider);
        } catch (RuntimeException e) {
            // TODO: Perhaps not allow to use BPF offload because the reregistration failure
            // implied that no data limit could be applies on a metered upstream if any.
            mLog.e("Cannot register offload stats provider: " + e);
            provider = null;
        }
        mStatsProvider = provider;
        mDeps = deps;
    }

    /**
     * Start BPF tethering offload stats polling.
     * Note that this can be only called on handler thread.
     * TODO: Perhaps check BPF support before starting.
     */
    public void start() {
        if (mStarted) return;

        mStarted = true;
        maybeSchedulePollingStats();

        mLog.i("BPF tethering coordinator started");
    }

    /**
     * Stop BPF tethering offload stats polling and cleanup upstream parameters.
     * The data limit cleanup and the tether stats maps cleanup are not implemented here.
     * These cleanups reply on that all IpServers calls #removeForwardingRule. After the
     * last rule is removed from the upstream, #removeForwardingRule does the cleanup stuff.
     * Note that this can be only called on handler thread.
     */
    public void stop() {
        if (!mStarted) return;

        // Stop scheduled polling tasks and poll the latest stats from BPF maps.
        if (mHandler.hasCallbacks(mScheduledPollingTask)) {
            mHandler.removeCallbacks(mScheduledPollingTask);
        }
        updateForwardedStatsFromNetd();

        mUpstreamNetworkState = null;
        mStarted = false;

        mLog.i("BPF tethering coordinator stopped");
    }

    /**
     * Call when UpstreamNetworkState may be changed.
     * Note that this can be only called on handler thread.
     */
    public void updateUpstreamNetworkState(@Nullable UpstreamNetworkState ns) {
        if (!mStarted) return;

        if (ns == null) {
            mUpstreamNetworkState = null;
        } else {
            // Make a deep copy of the parts we need.
            mUpstreamNetworkState = new UpstreamNetworkState(
                    new LinkProperties(ns.linkProperties),
                    new NetworkCapabilities(ns.networkCapabilities),
                    new Network(ns.network));
        }
    }

    /**
     * Add forwarding rule. Before adding the first rule on a given upstream, must add data
     * limit on the given upstream.
     * Note that this can be only called on handler thread.
     * TODO: Help IpServer to add forwarding rules.
     */
    public void addForwardingRule(@NonNull Ipv6ForwardingRule rule) {
        int upstreamIfindex = rule.upstreamIfindex;
        HashSet<Inet6Address> clients = mClientAddresses.get(upstreamIfindex);
        if (clients == null) {
            clients = new HashSet<Inet6Address>();
        }

        // Setup the data limit on the given upstream before adding the first rule.
        if (!isAnyClientOnUpstream(upstreamIfindex)) {
            // If we failed to set a data limit, probably should not use this upstream, because we
            // may not want to blow through the data limit that we were told to apply.
            // TODO: Perhaps stop adding or removing forwarding rules.
            boolean success = updateDataLimit(upstreamIfindex);
            if (!success) {
                final String iface = mInterfaceNames.get(upstreamIfindex);
                mLog.e("Setting data limit for " + iface + " failed.");
            }
        }

        clients.add(rule.address);
        mClientAddresses.put(upstreamIfindex, clients);

        // TODO: Move the adding forwarding rule mechanism from IpServer to here.
    }

    /**
     * Remove forwarding rule. After removing the last rule on a given upstream, must clear data
     * limit, update the last tether stats and remove the tether stats in the BPF maps.
     * Note that this can be only called on handler thread.
     * TODO: Help IpServer to remove forwarding rules.
     */
    public void removeForwardingRule(@NonNull Ipv6ForwardingRule rule) {
        int upstreamIfindex = rule.upstreamIfindex;
        HashSet<Inet6Address> clients = mClientAddresses.get(upstreamIfindex);

        // Avoid unnecessary work on a non-existent rule which may have never been added or
        // removed already.
        if (clients == null) return;

        clients.remove(rule.address);
        // TODO: Move the removing forwarding rule mechanism from IpServer to here.

        // If there are no more downstream on the upstream, do cleanup for the given upstream.
        if (clients.isEmpty()) {
            try {
                final TetherStatsParcel stats =
                        mNetd.tetherOffloadGetAndClearStats(upstreamIfindex);
                accumulateUsedQuotaAndStatsDiff(new TetherStatsParcel[] {stats});
            } catch (RemoteException | ServiceSpecificException e) {
                mLog.e("Exception when cleanup tether stats for upstream index "
                        + upstreamIfindex + ": " + e);
            }
            mOffloadTetherStats.remove(upstreamIfindex);
            mClientAddresses.remove(upstreamIfindex);  // remove the entry which has no client.
            return;
        }

        mClientAddresses.put(upstreamIfindex, clients);
    }

    /**
     * Add upstream name to lookup table. The lookup table is used for tether stats interface name
     * lookup because the netd only reports interface index in BPF tether stats but the service
     * expects the interface name in NetworkStats object.
     * Note that this can be only called on handler thread.
     */
    public void addUpstreamNameToLookupTable(int upstreamIfindex, String upstreamIface) {
        if (upstreamIfindex <= 0) return;

        // The same interface index to name mapping may be added by different IpServer objects or
        // re-added by reconnection on the same upstream interface. Ignore the duplicate one.
        // TODO: Use SparseArray#contains once it is supported.
        if (mInterfaceNames.get(upstreamIfindex) != null) {
            mInterfaceNames.put(upstreamIfindex, upstreamIface);
        }
    }

    /** IPv6 forwarding rule class. */
    public static class Ipv6ForwardingRule {
        public final int upstreamIfindex;
        public final int downstreamIfindex;
        public final Inet6Address address;
        public final MacAddress srcMac;
        public final MacAddress dstMac;

        public Ipv6ForwardingRule(int upstreamIfindex, int downstreamIfIndex,
                @NonNull Inet6Address address, @NonNull MacAddress srcMac,
                @NonNull MacAddress dstMac) {
            this.upstreamIfindex = upstreamIfindex;
            this.downstreamIfindex = downstreamIfIndex;
            this.address = address;
            this.srcMac = srcMac;
            this.dstMac = dstMac;
        }

        /** Return a new rule object which updates with new upstream index. */
        public Ipv6ForwardingRule onNewUpstream(int newUpstreamIfindex) {
            return new Ipv6ForwardingRule(newUpstreamIfindex, downstreamIfindex, address, srcMac,
                    dstMac);
        }

        /** Don't manipulate TetherOffloadRuleParcel directly because implementing onNewUpstream()
         *  would be error-prone due to generated stable AIDL classes not having a copy constructor.
         */
        public TetherOffloadRuleParcel toTetherOffloadRuleParcel() {
            final TetherOffloadRuleParcel parcel = new TetherOffloadRuleParcel();
            parcel.inputInterfaceIndex = upstreamIfindex;
            parcel.outputInterfaceIndex = downstreamIfindex;
            parcel.destination = address.getAddress();
            parcel.prefixLength = 128;
            parcel.srcL2Address = srcMac.toByteArray();
            parcel.dstL2Address = dstMac.toByteArray();
            return parcel;
        }
    }

    @VisibleForTesting
    class BpfTetherStatsProvider extends NetworkStatsProvider {
        // The offloaded traffic statistics per interface that has not been reported since the
        // latest stats update. Only the interfaces that were ever tethering upstreams and has
        // pending tether stats delta are included in this NetworkStats object.
        private NetworkStats mIfaceStats = new NetworkStats(0L, 0);

        // The same stats as above, but counts network stats per uid.
        private NetworkStats mUidStats = new NetworkStats(0L, 0);

        @Override
        public void onRequestStatsUpdate(int token) {
            mHandler.post(() -> pushTetherStats());
        }

        @Override
        public void onSetAlert(long quotaBytes) {
            mLog.i("onSetAlert: " + quotaBytes);
            mHandler.post(() -> updateAlertQuota(quotaBytes));
        }

        @Override
        public void onSetLimit(@NonNull String iface, long quotaBytes) {
            mLog.i("onSetLimit: " + iface + ", " + quotaBytes);
            mHandler.post(() -> {
                final Long curIfaceQuota = mInterfaceQuotas.get(iface);

                if (null == curIfaceQuota && QUOTA_UNLIMITED == quotaBytes) return;

                if (quotaBytes == QUOTA_UNLIMITED) {
                    mInterfaceQuotas.remove(iface);
                } else {
                    mInterfaceQuotas.put(iface, quotaBytes);
                }
                maybeUpdateDataLimit(iface);
            });
        }

        @VisibleForTesting
        void pushTetherStats() {
            try {
                notifyStatsUpdated(0 /* token */, mIfaceStats, mUidStats);

                // Clear the accumulated tether stats delta after reported. Note that create a new
                // empty object because NetworkStats#clear has been hidden.
                mIfaceStats = new NetworkStats(0L, 0);
                mUidStats = new NetworkStats(0L, 0);
            } catch (RuntimeException e) {
                mLog.e("Cannot report network stats: ", e);
            }
        }

        private void accumulateDiff(@NonNull NetworkStats ifaceDiff,
                @NonNull NetworkStats uidDiff) {
            mIfaceStats = mIfaceStats.add(ifaceDiff);
            mUidStats = mUidStats.add(uidDiff);
        }
    }

    @Nullable
    private String currentUpstreamInterface() {
        // Get IPv6 tethering upstream because BPF tethering offload supports IPv6 only.
        return (mUpstreamNetworkState != null)
                ? TetheringInterfaceUtils.getIPv6Interface(mUpstreamNetworkState) : null;
    }

    // Used to get the interface index on current upstream.
    private int getInterfaceIndex(String ifName) {
        try {
            return NetworkInterface.getByName(ifName).getIndex();
        } catch (IOException | NullPointerException e) {
            // TODO: Consider throwing an exception if get interface index failed.
            mLog.e("Can't determine interface index for interface " + ifName + " : " + e);
            return 0;
        }
    }

    @NonNull
    private Long getQuotaBytes(String iface) {
        final Long limit = mInterfaceQuotas.get(iface);
        final Long quotaBytes = (limit != null) ? limit : QUOTA_UNLIMITED;

        return quotaBytes;
    }

    private boolean setDataLimitToNetd(Integer ifindex, Long quotaBytes) {
        if (ifindex == null || ifindex <= 0) return false;

        try {
            mNetd.tetherOffloadSetInterfaceQuota(ifindex, quotaBytes);
        } catch (RemoteException | ServiceSpecificException e) {
            mLog.e("Exception when updating quota " + quotaBytes + ": " + e);
            return false;
        }

        return true;
    }

    // Handle the data limit update from the service which is the stats provider registered for.
    private void maybeUpdateDataLimit(String iface) {
        if (!mStarted || !TextUtils.equals(iface, currentUpstreamInterface())) return;

        // Set data limit only on a given upstream which has client(s) because it implies the
        // upstream is under tethering.
        final Integer ifindex = getInterfaceIndex(iface);
        if (!isAnyClientOnUpstream(ifindex)) return;

        final Long quotaBytes = getQuotaBytes(iface);
        setDataLimitToNetd(ifindex, quotaBytes);
    }

    // Handle the data limit update while adding forwarding rules.
    private boolean updateDataLimit(Integer ifindex) {
        final String iface = mInterfaceNames.get(ifindex);
        if (iface == null) {
            mLog.e("Fail to get the interface name for index " + ifindex);
            return false;
        }
        final Long quotaBytes = getQuotaBytes(iface);

        return setDataLimitToNetd(ifindex, quotaBytes);
    }

    boolean isAnyClientOnUpstream(Integer upstreamIfindex) {
        return mClientAddresses.get(upstreamIfindex) != null;
    }

    @NonNull
    private NetworkStats buildNetworkStats(@NonNull StatsType type, @NonNull Integer ifIndex,
            @NonNull ForwardedStats diff) {
        final String iface = mInterfaceNames.get(ifIndex);
        if (iface == null) {
            throw new IllegalStateException(
                    "Failed to lookup interface name for interface index " + ifIndex);
        }
        NetworkStats stats = new NetworkStats(0L, 0);
        final int uid = (type == StatsType.STATS_PER_UID) ? UID_TETHERING : UID_ALL;
        stats = stats.addEntry(new Entry(iface, uid, SET_DEFAULT, TAG_NONE, METERED_NO,
                ROAMING_NO, DEFAULT_NETWORK_NO, diff.rxBytes, diff.rxPackets,
                diff.txBytes, diff.txPackets, 0L /* operations */));
        return stats;
    }

    private void updateAlertQuota(long newQuota) {
        if (newQuota < QUOTA_UNLIMITED) {
            throw new IllegalArgumentException("invalid quota value " + newQuota);
        }
        if (mRemainingAlertQuota == newQuota) return;

        mRemainingAlertQuota = newQuota;
        if (mRemainingAlertQuota == 0) {
            mLog.i("onAlertReached");
            if (mStatsProvider != null) mStatsProvider.notifyAlertReached();
        }
    }

    private void accumulateUsedQuotaAndStatsDiff(
            @NonNull final TetherStatsParcel[] tetherStatsList) {
        long usedAlertQuota = 0;
        for (TetherStatsParcel tetherStats : tetherStatsList) {
            try {
                final Integer ifIndex = tetherStats.ifIndex;
                final ForwardedStats curr = new ForwardedStats(tetherStats);
                final ForwardedStats base = mOffloadTetherStats.get(ifIndex);
                final ForwardedStats diff = (base != null) ? curr.subtract(base) : curr;
                usedAlertQuota += diff.rxBytes + diff.txBytes;

                // Update the local cache for counting tether stats delta.
                mOffloadTetherStats.put(ifIndex, curr);

                // Update the accumulated tether stats delta to the stats provider for the service
                // querying.
                if (mStatsProvider != null) {
                    mStatsProvider.accumulateDiff(
                            buildNetworkStats(StatsType.STATS_PER_IFACE, ifIndex, diff),
                            buildNetworkStats(StatsType.STATS_PER_UID, ifIndex, diff));
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new IllegalStateException("invalid tethering stats " + e);
            }
        }

        if (mRemainingAlertQuota > 0 && usedAlertQuota > 0) {
            // Trim to zero if overshoot.
            final long newQuota = Math.max(mRemainingAlertQuota - usedAlertQuota, 0);
            updateAlertQuota(newQuota);
        }

        // TODO: Count the used limit quota for notifying data limit reached.
    }

    private void updateForwardedStatsFromNetd() {
        final TetherStatsParcel[] tetherStatsList;
        try {
            // The reported tether stats are total data usage for current upstream interface
            // since tethering start.
            tetherStatsList = mNetd.tetherOffloadGetStats();
        } catch (RemoteException | ServiceSpecificException e) {
            throw new IllegalStateException("problem parsing tethering stats: ", e);
        }
        accumulateUsedQuotaAndStatsDiff(tetherStatsList);
    }

    private void maybeSchedulePollingStats() {
        if (!mStarted) return;

        if (mHandler.hasCallbacks(mScheduledPollingTask)) {
            mHandler.removeCallbacks(mScheduledPollingTask);
        }

        mHandler.postDelayed(mScheduledPollingTask, mDeps.getPerformPollInterval());
    }
}
