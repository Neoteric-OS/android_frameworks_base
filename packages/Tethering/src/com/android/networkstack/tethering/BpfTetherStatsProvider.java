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

import android.net.INetd;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkStats;
import android.net.NetworkStats.Entry;
import android.net.TetherStatsParcel;
import android.net.netstats.provider.NetworkStatsProvider;
import android.net.util.SharedLog;
import android.net.util.TetheringUtils.ForwardedStats;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.system.OsConstants;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.util.IndentingPrintWriter;

import java.io.IOException;
import java.net.NetworkInterface;
import java.util.HashMap;
import java.util.Objects;

/**
 * A class to provide the tethering stats of BPF offload interface.
 *
 * @hide
 */
public class BpfTetherStatsProvider extends NetworkStatsProvider {
    private static final String TAG = BpfTetherStatsProvider.class.getSimpleName();
    private static final ForwardedStats EMPTY_STATS = new ForwardedStats();
    private static final int DEFAULT_PERFORM_POLL_DELAY_MS = 5000;  // TODO: Make it customizable.

    private final Handler mHandler;
    private final INetd mNetd;
    private final SharedLog mLog;
    private UpstreamNetworkState mUpstreamNetworkState;
    private boolean mStarted = false;

    // Tracking remaining alert quota. Unlike limit quota is subject to interface, the alert
    // quota is interface independent and global for tether offload.
    private long mRemainingAlertQuota = QUOTA_UNLIMITED;

    // Maps upstream interface names to offloaded traffic statistics.
    // Always contains the latest value received from the BPF maps for each interface, regardless
    // of whether offload is currently running on that interface.
    private HashMap<Integer, ForwardedStats> mOffloadTetherStats = new HashMap<>();

    // Maps upstream interface names to interface quotas.
    // Always contains the latest value received from the framework for each interface, regardless
    // of whether offload is currently running (or is even supported) on that interface. Only
    // includes interfaces that have a quota set.
    private HashMap<String, Long> mInterfaceQuotas = new HashMap<>();

    // Maps upstream interface index to interface names.
    // Store all interface name since boot. Used for lookup what interface name it is from the
    // tether stats got from netd because netd reports interface index to present an interface.
    private HashMap<Integer, String> mInterfaceNames = new HashMap<>();

    // Maps upstream interface index to reference count.
    // Used to monitor if any IpSever is using a given upstream. It helps to do upstream
    // initialization or cleanup.
    private HashMap<Integer, Integer> mUpstreamsRefCount = new HashMap<>();

    // The latest offloaded traffic statistics per interface that has not been reported since the
    // latest service query. All interfaces that were ever tethering upstreams since boot are
    // included in this NetworkStats object.
    private NetworkStats mIfaceStats = new NetworkStats(0L, 0);

    // The same stats as above, but counts network stats per uid.
    private NetworkStats mUidStats = new NetworkStats(0L, 0);

    // Runnable that used by scheduling next polling of stats.
    private final Runnable mScheduledPollingTask = () -> {
        updateTetherStats();
        maybeSchedulePollingStats();
    };

    BpfTetherStatsProvider(@NonNull Handler handler, @NonNull INetd netd,
            @NonNull SharedLog log) {
        mHandler = handler;
        mNetd = netd;
        mLog = log.forSubComponent(TAG);
    }

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
    public void onSetLimit(String iface, long quotaBytes) {
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

    /**
     * Start BPF tethering offload stats polling.
     * Note that this can be only called on handler thread.
     * TODO: Perhaps check BPF support before starting.
     */
    public void start() {
        if (mStarted) return;

        mStarted = true;
        maybeSchedulePollingStats();

        mLog.i("BPF tether stats provider started");
    }

    /**
     * Stop BPF tethering offload stats polling and cleanup upstream parameters.
     * The data limit clean up and the latest tether stats update are not implemented here.
     * These cleanups are replies on that all IpServers calls releaseUpstream(). After the
     * last IpServer releases the upstream, releaseUpstream() does cleanup.
     * Note that this can be only called on handler thread.
     */
    public void stop() {
        if (!mStarted) return;

        // Stop scheduled polling tasks and poll the latest stats from BPF maps.
        if (mHandler.hasCallbacks(mScheduledPollingTask)) {
            mHandler.removeCallbacks(mScheduledPollingTask);
        }
        updateTetherStats();

        mUpstreamNetworkState = null;
        mStarted = false;

        mLog.i("BPF tether stats provider stopped");
    }

    /**
     * Update current tethering upstream network state.
     * Note that this can be only called on handler thread.
     */
    public void updateUpstreamNetworkState(@Nullable UpstreamNetworkState ns) {
        if (!mStarted || Objects.equals(mUpstreamNetworkState, ns)) return;

        if (ns == null) {
            mUpstreamNetworkState = null;
        } else {
            // Make a deep copy of the parts we need.
            mUpstreamNetworkState = new UpstreamNetworkState(
                    new LinkProperties(ns.linkProperties),
                    new NetworkCapabilities(ns.networkCapabilities),
                    new Network(ns.network));
        }
        updateUpstreamParameters();
    }

    /**
     * Setup upstream while IpServer requires the given upstream. This must be called before adding
     * forwarding rules because the data limit must be applied before starting tethering.
     * Note that this can be only called on handler thread.
     */
    public void setupUpstream(final Integer upstreamIfindex) {
        Integer refCount = mUpstreamsRefCount.get(upstreamIfindex);
        if (refCount == null) {
            refCount = new Integer(0);
        }

        // Setup the data limit on the given upstream if it is the first time to use.
        if (!isAnyDownstreamOnUpstream(upstreamIfindex)) {
            // If we failed to set a data limit, probably should not use this upstream, because we
            // may not want to blow through the data limit that we were told to apply.
            // TODO: Perhaps need to stop adding or removing forwarding rules.
            boolean success = updateDataLimit(upstreamIfindex);
            if (!success) {
                final String iface = mInterfaceNames.get(upstreamIfindex);
                mLog.e("Setting data limit for " + iface + " failed.");
            }
        }

        refCount++;
        mUpstreamsRefCount.put(upstreamIfindex, refCount);
    }

    /**
     * Release upstream while IpServer doesn't use the given upstream any more.
     * Note that this can be only called on handler thread.
     */
    public void releaseUpstream(final Integer upstreamIfindex) {
        Integer refCount = mUpstreamsRefCount.get(upstreamIfindex);
        if (refCount == null) {
            mLog.e("reference count is not found for upstream index " + upstreamIfindex);
            return;
        }
        refCount--;

        // If there are no more downstream on the upstream, clean up the data limit and remove the
        // entry automatically.
        if (refCount == 0) {
            mUpstreamsRefCount.remove(upstreamIfindex);
            cleanupDataLimit(upstreamIfindex);

            // After updating the latest stats, cleanup the tether stats from BPF map and
            // local cache.
            updateTetherStats();
            try {
                mNetd.tetherOffloadStatsRemove(upstreamIfindex);
            } catch (RemoteException | ServiceSpecificException e) {
                mLog.e("Exception when removing tether stats for upstream index "
                        + upstreamIfindex + ": " + e);
            }
            mOffloadTetherStats.remove(upstreamIfindex);
            return;
        }

        mUpstreamsRefCount.put(upstreamIfindex, refCount);
    }

    /** Dump information. */
    public void dump(@Nullable IndentingPrintWriter pw) {
        pw.println("Stats provider " + (mStarted ? "started" : "not started"));
        String upstream = currentUpstreamInterface();
        final Integer ifaceIndex = getInterfaceIndex(upstream);
        pw.println("Current upstream: [" + upstream + "] (index " + ifaceIndex + ")");
    }

    private String currentUpstreamInterface() {
        // Get IPv6 tethering upstream because BPF tethering offload supports IPv6 only.
        return (mUpstreamNetworkState != null)
                ? TetheringInterfaceUtils.getIPv6Interface(mUpstreamNetworkState) : null;
    }

    private int getInterfaceIndex(String ifName) {
        try {
            return NetworkInterface.getByName(ifName).getIndex();
        } catch (IOException | NullPointerException e) {
            // TODO: Consider throwing an exception if get interface index failed.
            mLog.e("Can't determine interface index for interface " + ifName + " : " + e);
            return 0;
        }
    }

    private void updateUpstreamParameters() {
        final String iface = currentUpstreamInterface();
        if (TextUtils.isEmpty(iface)) return;

        // Store the interface name for tether stats interface name lookup.
        final int upstreamIfindex = getInterfaceIndex(iface);
        mInterfaceNames.put(upstreamIfindex, iface);

        // Make sure we record this interface in the ForwardedStats map.
        mOffloadTetherStats.putIfAbsent(upstreamIfindex, EMPTY_STATS);
    }

    private boolean pushDataLimit(Integer ifindex, Long quotaBytes) {
        if (ifindex == null || ifindex <= 0) return false;

        try {
            mNetd.tetherOffloadSetInterfaceQuota(ifindex, quotaBytes);
        } catch (ServiceSpecificException e) {
            // Silently ignore exception when removing the quota. Its quota limitation probably has
            // already been removed or never added for unlimited quota.
            if (e.errorCode != OsConstants.ENODEV & quotaBytes == QUOTA_UNLIMITED) {
                mLog.e("Exception when updating quota " + quotaBytes + ": " + e);
                return false;
            }
        } catch (RemoteException e) {
            mLog.e("Exception when updating quota " + quotaBytes + ": " + e);
            return false;
        }

        return true;
    }

    private Long getQuotaBytes(String iface) {
        final Long limit = mInterfaceQuotas.get(iface);
        final Long quotaBytes = (limit != null) ? limit : QUOTA_UNLIMITED;

        return quotaBytes;
    }

    private boolean updateDataLimit(Integer ifindex) {
        final String iface = mInterfaceNames.get(ifindex);
        if (iface == null) {
            mLog.e("Fail to get the interface name for index " + ifindex);
            return false;
        }
        final Long quotaBytes = getQuotaBytes(iface);

        return pushDataLimit(ifindex, quotaBytes);
    }

    private boolean maybeUpdateDataLimit(String iface) {
        if (!mStarted || !TextUtils.equals(iface, currentUpstreamInterface())) {
            return true;
        }
        final Integer ifindex = getInterfaceIndex(iface);
        final Long quotaBytes = getQuotaBytes(iface);

        return pushDataLimit(ifindex, quotaBytes);
    }

    private boolean cleanupDataLimit(Integer ifindex) {
        if (isAnyDownstreamOnUpstream(ifindex)) {
            mLog.e("Can't remove data limit because there are downstream on the upstream index "
                    + ifindex);
            return false;
        }
        return pushDataLimit(ifindex, Long.valueOf(QUOTA_UNLIMITED));
    }

    boolean isAnyDownstreamOnUpstream(final Integer upstreamIfindex) {
        return mUpstreamsRefCount.get(upstreamIfindex) != null;
    }

    private void updateAlertQuota(long newQuota) {
        if (newQuota < QUOTA_UNLIMITED) {
            throw new IllegalArgumentException("invalid quota value " + newQuota);
        }
        if (mRemainingAlertQuota == newQuota) return;

        mRemainingAlertQuota = newQuota;
        if (mRemainingAlertQuota == 0) {
            mLog.i("onAlertReached");
            notifyAlertReached();
        }
    }

    private void updateNetworkStatsDelta(Integer ifIndex, ForwardedStats diff) {
        final String iface = mInterfaceNames.get(ifIndex);
        if (iface == null) {
            throw new IllegalStateException(
                    "Failed to lookup interface name for interface index " + ifIndex);
        }

        // Store the network stats delta for notifying the service.
        for (final int uid : new int[] { UID_TETHERING, UID_ALL }) {
            NetworkStats statsDiff = new NetworkStats(0L, 0);
            final Entry entry = new Entry(iface, uid, SET_DEFAULT, TAG_NONE, METERED_NO,
                    ROAMING_NO, DEFAULT_NETWORK_NO, diff.rxBytes, diff.rxPackets,
                    diff.txBytes, diff.txPackets, 0L /* operations */);
            statsDiff = statsDiff.addEntry(entry);

            if (uid == UID_TETHERING) {
                mUidStats = mUidStats.add(statsDiff);
            } else {
                mIfaceStats = mIfaceStats.add(statsDiff);
            }
        }
    }

    private void pushTetherStats() {
        try {
            notifyStatsUpdated(0 /* token */, mIfaceStats, mUidStats);

            // Clean the network stats delta after notified.
            mIfaceStats = new NetworkStats(0L, 0);
            mUidStats = new NetworkStats(0L, 0);
        } catch (RuntimeException e) {
            mLog.e("Cannot report network stats: ", e);
        }
    }

    private void updateTetherStats() {
        final TetherStatsParcel[] tetherStatsList;
        try {
            // The reported tether stats are total data usage for all upstream interfaces in
            // upstream lifetime.
            tetherStatsList = mNetd.tetherOffloadGetStats();
        } catch (RemoteException | ServiceSpecificException e) {
            throw new IllegalStateException("problem parsing tethering stats: ", e);
        }

        long usedAlertQuota = 0;
        for (TetherStatsParcel tetherStats : tetherStatsList) {
            try {
                final Integer ifIndex = tetherStats.ifIndex;
                final ForwardedStats curr = new ForwardedStats(tetherStats.rxBytes,
                        tetherStats.rxPackets, tetherStats.txBytes, tetherStats.txPackets);
                final ForwardedStats base = mOffloadTetherStats.get(ifIndex);
                final ForwardedStats diff = (base != null) ? curr.subtract(base) : curr;
                usedAlertQuota += diff.rxBytes + diff.txBytes;

                // Update the local cache for counting delta in tether stats update.
                mOffloadTetherStats.put(ifIndex, curr);
                // Update the delta of tether stats for notifying the service.
                updateNetworkStatsDelta(ifIndex, diff);
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

    private void maybeSchedulePollingStats() {
        if (!mStarted) return;

        if (mHandler.hasCallbacks(mScheduledPollingTask)) {
            mHandler.removeCallbacks(mScheduledPollingTask);
        }

        mHandler.postDelayed(mScheduledPollingTask, DEFAULT_PERFORM_POLL_DELAY_MS);
    }
}
