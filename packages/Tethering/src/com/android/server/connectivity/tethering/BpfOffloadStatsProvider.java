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

package com.android.server.connectivity.tethering;

import static android.net.NetworkStats.DEFAULT_NETWORK_NO;
import static android.net.NetworkStats.METERED_NO;
import static android.net.NetworkStats.ROAMING_NO;
import static android.net.NetworkStats.SET_DEFAULT;
import static android.net.NetworkStats.TAG_NONE;
import static android.net.NetworkStats.UID_ALL;
import static android.net.NetworkStats.UID_TETHERING;

import android.net.INetd;
import android.net.LinkProperties;
import android.net.NetworkStats;
import android.net.NetworkStats.Entry;
import android.net.TetherStatsParcel;
import android.net.netstats.provider.NetworkStatsProvider;
import android.net.util.SharedLog;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.system.OsConstants;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A class to provide the tethering stats of BPF offload interface.
 *
 * @hide
 */
public class BpfOffloadStatsProvider extends NetworkStatsProvider {
    private static final String TAG = BpfOffloadStatsProvider.class.getSimpleName();
    private static final BpfTetherStats EMPTY_STATS = new BpfTetherStats();
    private static final int DEFAULT_PERFORM_POLL_DELAY_MS = 5000;  // TODO: Make it customizable.

    // TODO: Move to common definition for using with class OffloadController.
    private enum StatsType {
        STATS_PER_IFACE,
        STATS_PER_UID,
    }

    /**
     * The object which records Tx/Rx forwarded bytes and packets.
     * TODO: Merge with the inner class ForwardedStats of class OffloadHardwareInterface.
     */
    private static class BpfTetherStats {
        public final long rxBytes;
        public final long rxPackets;
        public final long txBytes;
        public final long txPackets;

        BpfTetherStats() {
            rxBytes = 0;
            rxPackets = 0;
            txBytes = 0;
            txPackets = 0;
        }

        BpfTetherStats(long rxBytes, long rxPackets, long txBytes, long txPackets) {
            this.rxBytes = rxBytes;
            this.rxPackets = rxPackets;
            this.txBytes = txBytes;
            this.txPackets = txPackets;
        }

        public BpfTetherStats subtract(BpfTetherStats other) {
            // TODO: Perhaps throw an exception if any negative difference value just in case.
            final long rxBytesDiff = Math.max(rxBytes - other.rxBytes, 0);
            final long rxPacketsDiff = Math.max(rxPackets - other.rxPackets, 0);
            final long txBytesDiff = Math.max(txBytes - other.txBytes, 0);
            final long txPacketsDiff = Math.max(txPackets - other.txPackets, 0);
            return new BpfTetherStats(rxBytesDiff, rxPacketsDiff, txBytesDiff, txPacketsDiff);
        }
    }

    private final Handler mHandler;
    private final INetd mNetd;
    private final SharedLog mLog;
    private LinkProperties mUpstreamLinkProperties;
    private boolean mStarted = false;

    // Tracking remaining alert quota. Unlike limit quota is subject to interface, the alert
    // quota is interface independent and global for tether offload. Note that this should be
    // only accessed from handler thread if tether offload is started.
    private long mRemainingAlertQuota = NetworkStatsProvider.QUOTA_UNLIMITED;

    // Maps upstream interface names to offloaded traffic statistics.
    // Always contains the latest value received from the BPF maps for each interface, regardless
    // of whether offload is currently running on that interface.
    private ConcurrentHashMap<String, BpfTetherStats> mOffloadTetherStats =
            new ConcurrentHashMap<>(16, 0.75F, 1);

    // Maps upstream interface names to interface quotas.
    // Always contains the latest value received from the framework for each interface, regardless
    // of whether offload is currently running (or is even supported) on that interface. Only
    // includes upstream interfaces that have a quota set.
    private HashMap<String, Long> mInterfaceQuotas = new HashMap<>();

    // The offloaded traffic statistics per interface has been reported.
    private NetworkStats mIfaceStats = new NetworkStats(0L, 0);

    // The offloaded traffic statistics per uid has been reported.
    private NetworkStats mUidStats = new NetworkStats(0L, 0);

    // Runnable that used by scheduling next polling of stats.
    private final Runnable mSchedulePollingTask = () -> {
        updateTetherStats();
        maybeSchedulePollingStats();
    };

    BpfOffloadStatsProvider(@NonNull Handler handler, @NonNull INetd netd,
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

    /** Start BPF tethering offload stats polling. */
    public void start() {
        if (mStarted) return;

        // TODO: Perhaps check BPF support and INetd version before starting.
        mStarted = true;
        mHandler.post(() -> maybeSchedulePollingStats());
        mLog.i("bpf tether stats provider started");
    }

    /** Stop BPF tethering offload stats polling. */
    public void stop() {
        mStarted = false;
        mUpstreamLinkProperties = null;

        // Stop scheduled polling tasks and poll the latest stats from BPF maps.
        mHandler.post(() -> {
            if (mHandler.hasCallbacks(mSchedulePollingTask)) {
                mHandler.removeCallbacks(mSchedulePollingTask);
            }
            updateTetherStats();
        });
        mLog.i("bpf tether stats provider stopped");
    }

    /** Update current tethering upstream network state. */
    public void updateUpstreamNetworkState(@Nullable UpstreamNetworkState ns) {
        setUpstreamLinkProperties((ns != null) ? ns.linkProperties : null);
    }

    private String currentUpstreamInterface() {
        return (mUpstreamLinkProperties != null)
                ? mUpstreamLinkProperties.getInterfaceName() : null;
    }

    private void setUpstreamLinkProperties(LinkProperties lp) {
        if (!mStarted || Objects.equals(mUpstreamLinkProperties, lp)) return;

        mUpstreamLinkProperties = (lp != null) ? new LinkProperties(lp) : null;
        // Make sure we record this interface in the BpfTetherStats map.
        final String iface = currentUpstreamInterface();
        if (!TextUtils.isEmpty(iface)) mOffloadTetherStats.putIfAbsent(iface, EMPTY_STATS);

        pushUpstreamParameters();
    }

    private boolean pushUpstreamParameters() {
        final String iface = currentUpstreamInterface();
        if (TextUtils.isEmpty(iface)) return false;

        // Data limits can only be set once offload is running on the upstream.
        final boolean success = maybeUpdateDataLimit(iface);
        if (!success) {
            // If we failed to set a data limit, don't use this upstream, because we don't want to
            // blow through the data limit that we were told to apply.
            mLog.i("Setting data limit for " + iface + " failed, disabling offload.");
            stop();
        }

        return success;
    }

    private boolean maybeUpdateDataLimit(String iface) {
        // bandwidth{Set, Remove}InterfaceQuotaBpf may only be called while offload is occurring on
        // this upstream.
        if (!mStarted || !TextUtils.equals(iface, currentUpstreamInterface())) {
            return true;
        }

        final Long limit = mInterfaceQuotas.get(iface);
        try {
            if (limit == null) {
                mNetd.bandwidthRemoveInterfaceQuotaBpf(iface);
            } else {
                mNetd.bandwidthSetInterfaceQuotaBpf(iface, limit);
            }
        } catch (ServiceSpecificException e) {
            // Silently ignore exception when removing the quota. Its quota limitation probably has
            // already been removed or never added for unlimited quota.
            if (e.errorCode != OsConstants.ENODEV & limit == null /* remove quota */) {
                mLog.e("Exception when updating quota " + limit + ": " + e);
                return false;
            }
        } catch (RemoteException e) {
            mLog.e("Exception when updating quota " + limit + ": " + e);
            return false;
        }

        return true;
    }

    private void updateAlertQuota(long newQuota) {
        if (newQuota < NetworkStatsProvider.QUOTA_UNLIMITED) {
            throw new IllegalArgumentException("invalid quota value " + newQuota);
        }
        if (mRemainingAlertQuota == newQuota) return;

        mRemainingAlertQuota = newQuota;
        if (mRemainingAlertQuota == 0) {
            mLog.i("onAlertReached");
            notifyAlertReached();
        }
    }

    private NetworkStats getTetherStats(StatsType how) {
        NetworkStats stats = new NetworkStats(0L, 0);
        final int uid = (how == StatsType.STATS_PER_UID) ? UID_TETHERING : UID_ALL;

        // Build the statistics report without any binder call.
        for (final Map.Entry<String, BpfTetherStats> kv : mOffloadTetherStats.entrySet()) {
            final BpfTetherStats value = kv.getValue();
            final Entry entry = new Entry(kv.getKey(), uid, SET_DEFAULT, TAG_NONE, METERED_NO,
                    ROAMING_NO, DEFAULT_NETWORK_NO, value.rxBytes, value.rxPackets,
                    value.txBytes, value.txPackets, 0L /* operations */);
            stats = stats.addEntry(entry);
        }

        return stats;
    }

    private void pushTetherStats() {
        final NetworkStats ifaceDiff =
                getTetherStats(StatsType.STATS_PER_IFACE).subtract(mIfaceStats);
        final NetworkStats uidDiff =
                getTetherStats(StatsType.STATS_PER_UID).subtract(mUidStats);
        try {
            notifyStatsUpdated(0 /* token */, ifaceDiff, uidDiff);
            mIfaceStats = mIfaceStats.add(ifaceDiff);
            mUidStats = mUidStats.add(uidDiff);
        } catch (RuntimeException e) {
            mLog.e("Cannot report network stats: ", e);
        }
    }

    private void updateTetherStats() {
        final TetherStatsParcel[] tetherStatsList;
        try {
            // The reported tether stats are total data usage for all upstream interfaces from netd
            // started.
            tetherStatsList = mNetd.tetherGetStatsBpf();
        } catch (RemoteException | ServiceSpecificException e) {
            throw new IllegalStateException("problem parsing tethering stats: ", e);
        }

        long usedAlertQuota = 0;
        for (TetherStatsParcel tetherStats : tetherStatsList) {
            try {
                final String iface = tetherStats.iface;
                final BpfTetherStats curr = new BpfTetherStats(tetherStats.rxBytes,
                        tetherStats.rxPackets, tetherStats.txBytes, tetherStats.txPackets);

                final BpfTetherStats base = mOffloadTetherStats.get(iface);
                final BpfTetherStats diff = (base != null) ? curr.subtract(base) : curr;
                usedAlertQuota += diff.rxBytes + diff.txBytes;
                mOffloadTetherStats.put(iface, curr);
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

        if (mHandler.hasCallbacks(mSchedulePollingTask)) {
            mHandler.removeCallbacks(mSchedulePollingTask);
        }

        mHandler.postDelayed(mSchedulePollingTask, DEFAULT_PERFORM_POLL_DELAY_MS);
    }
}
