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

package com.android.server.vcn;

import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TelephonyNetworkSpecifier;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.annotations.VisibleForTesting.Visibility;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.vcn.TelephonySubscriptionTracker.TelephonySubscriptionSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Tracks a set of Networks underpinning a VcnGatewayConnection.
 *
 * <p>A single UnderlyingNetworkTracker is built to serve a SINGLE VCN Gateway Connection, and MUST
 * be torn down with the VcnGatewayConnection in order to ensure underlying networks are allowed to
 * be reaped.
 *
 * @hide
 */
public class UnderlyingNetworkTracker {
    @NonNull private static final String TAG = UnderlyingNetworkTracker.class.getSimpleName();

    /**
     * Minimum signal strength for a WiFi network to be eligible for switching to
     *
     * <p>A network that satisfies this is eligible to become the selected underlying network with
     * no additional conditions
     */
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    static final int WIFI_ENTRY_RSSI_THRESHOLD_DEFAULT = -70;

    /**
     * Minimum signal strength to continue using a WiFi network
     *
     * <p>A network that satisfies the conditions may ONLY continue to be used if it is already
     * selected as the underlying network. A WiFi network satisfying this condition, but NOT the
     * prospective-network RSSI threshold CANNOT be switched to.
     */
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    static final int WIFI_EXIT_RSSI_THRESHOLD_DEFAULT = -74;

    /** Priority for any cellular network for which the subscription is listed as opportunistic */
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    static final int PRIORITY_OPPORTUNISTIC_CELLULAR = 0;

    /** Priority for any WiFi network which satisfies the prospective-network RSSI threshold */
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    static final int PRIORITY_WIFI_PROSPECTIVE = 1;

    /** Priority for any WiFi network which only satisfies the in-use RSSI threshold */
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    static final int PRIORITY_WIFI_IN_USE = 2;

    /** Priority for any standard macro cellular network */
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    static final int PRIORITY_MACRO_CELLULAR = 3;

    /** Priority for any other networks (including unvalidated, etc) */
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    static final int PRIORITY_ANY = Integer.MAX_VALUE;

    @NonNull private final VcnContext mVcnContext;
    @NonNull private final ParcelUuid mSubscriptionGroup;
    @NonNull private final Set<Integer> mRequiredUnderlyingNetworkCapabilities;
    @NonNull private final UnderlyingNetworkTrackerCallback mCb;
    @NonNull private final Dependencies mDeps;
    @NonNull private final Handler mHandler;
    @NonNull private final ConnectivityManager mConnectivityManager;

    @NonNull private final List<NetworkCallback> mCellBringupCallbacks = new ArrayList<>();
    @Nullable private NetworkCallback mWifiBringupCallback;
    @Nullable private NetworkCallback mWifiSignalStrengthCallback;
    @Nullable private UnderlyingNetworkListener mRouteSelectionCallback;

    @NonNull private TelephonySubscriptionSnapshot mLastSnapshot;
    @Nullable private PersistableBundle mCarrierConfig;
    private boolean mIsQuitting = false;

    @Nullable private UnderlyingNetworkRecord mCurrentRecord;
    @Nullable private UnderlyingNetworkRecord.Builder mRecordInProgress;

    public UnderlyingNetworkTracker(
            @NonNull VcnContext vcnContext,
            @NonNull ParcelUuid subscriptionGroup,
            @NonNull TelephonySubscriptionSnapshot snapshot,
            @NonNull Set<Integer> requiredUnderlyingNetworkCapabilities,
            @NonNull UnderlyingNetworkTrackerCallback cb) {
        this(
                vcnContext,
                subscriptionGroup,
                snapshot,
                requiredUnderlyingNetworkCapabilities,
                cb,
                new Dependencies());
    }

    private UnderlyingNetworkTracker(
            @NonNull VcnContext vcnContext,
            @NonNull ParcelUuid subscriptionGroup,
            @NonNull TelephonySubscriptionSnapshot snapshot,
            @NonNull Set<Integer> requiredUnderlyingNetworkCapabilities,
            @NonNull UnderlyingNetworkTrackerCallback cb,
            @NonNull Dependencies deps) {
        mVcnContext = Objects.requireNonNull(vcnContext, "Missing vcnContext");
        mSubscriptionGroup = Objects.requireNonNull(subscriptionGroup, "Missing subscriptionGroup");
        mLastSnapshot = Objects.requireNonNull(snapshot, "Missing snapshot");
        mRequiredUnderlyingNetworkCapabilities =
                Objects.requireNonNull(
                        requiredUnderlyingNetworkCapabilities,
                        "Missing requiredUnderlyingNetworkCapabilities");
        mCb = Objects.requireNonNull(cb, "Missing cb");
        mDeps = Objects.requireNonNull(deps, "Missing deps");

        mHandler = new Handler(mVcnContext.getLooper());

        mConnectivityManager = mVcnContext.getContext().getSystemService(ConnectivityManager.class);

        // TODO: Listen for changes in carrier config that affect this.
        for (int subId : mLastSnapshot.getAllSubIdsInGroup(mSubscriptionGroup)) {
            mCarrierConfig =
                    mVcnContext
                            .getContext()
                            .getSystemService(CarrierConfigManager.class)
                            .getConfigForSubId(subId);
            if (mCarrierConfig != null) {
                break;
            }
        }

        registerOrUpdateNetworkRequests();
    }

    private void registerOrUpdateNetworkRequests() {
        NetworkCallback oldRouteSelectionCallback = mRouteSelectionCallback;
        NetworkCallback oldWifiCallback = mWifiBringupCallback;
        NetworkCallback oldWifiSignalStrengthCallback = mWifiSignalStrengthCallback;
        List<NetworkCallback> oldCellCallbacks = new ArrayList<>(mCellBringupCallbacks);
        mCellBringupCallbacks.clear();

        // Register new callbacks. Make-before-break; always register new callbacks before removal
        // of old callbacks
        if (!mIsQuitting) {
            mRouteSelectionCallback = new UnderlyingNetworkListener();
            mConnectivityManager.registerNetworkCallback(
                    getRouteSelectionRequest(), mRouteSelectionCallback, mHandler);

            mWifiSignalStrengthCallback = new NetworkBringupCallback();
            mConnectivityManager.registerNetworkCallback(
                    getWifiSignalStrengthNetworkRequest(), mWifiSignalStrengthCallback, mHandler);

            mWifiBringupCallback = new NetworkBringupCallback();
            mConnectivityManager.requestBackgroundNetwork(
                    getWifiNetworkRequest(), mWifiBringupCallback, mHandler);

            for (final int subId : mLastSnapshot.getAllSubIdsInGroup(mSubscriptionGroup)) {
                final NetworkBringupCallback cb = new NetworkBringupCallback();
                mCellBringupCallbacks.add(cb);

                mConnectivityManager.requestBackgroundNetwork(
                        getCellNetworkRequestForSubId(subId), cb, mHandler);
            }
        } else {
            mRouteSelectionCallback = null;
            mWifiBringupCallback = null;
            // mCellBringupCallbacks already cleared above.
        }

        // Unregister old callbacks (as necessary)
        if (oldRouteSelectionCallback != null) {
            mConnectivityManager.unregisterNetworkCallback(oldRouteSelectionCallback);
        }
        if (oldWifiCallback != null) {
            mConnectivityManager.unregisterNetworkCallback(oldWifiCallback);
        }
        for (NetworkCallback cellBringupCallback : oldCellCallbacks) {
            mConnectivityManager.unregisterNetworkCallback(cellBringupCallback);
        }
    }

    /**
     * Builds the Route selection request
     *
     * <p>This request is guaranteed to select carrier-owned, non-VCN underlying networks by virtue
     * of a populated set of subIds as expressed in NetworkCapabilities#getSubscriptionIds(). Only
     * carrier owned networks may be selected, as the request specifies only subIds in the VCN's
     * subscription group, while the VCN networks are excluded by virtue of not having subIds set on
     * the VCN-exposed networks.
     */
    private NetworkRequest getRouteSelectionRequest() {
        return getBaseNetworkRequestBuilder()
                .setSubscriptionIds(mLastSnapshot.getAllSubIdsInGroup(mSubscriptionGroup))
                .build();
    }

    /**
     * Builds the WiFi bringup request
     *
     * <p>This request is built specifically to match only carrier-owned WiFi networks, but is also
     * built to ONLY keep Carrier WiFi Networks alive (but never bring them up). This is a result of
     * the WifiNetworkFactory not advertising a list of subIds, and therefore not accepting this
     * request. As such, it will bind to a Carrier WiFi Network that has already been brought up,
     * but will NEVER bring up a Carrier WiFi network itself.
     */
    private NetworkRequest getWifiNetworkRequest() {
        return getBaseNetworkRequestBuilder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setSubscriptionIds(mLastSnapshot.getAllSubIdsInGroup(mSubscriptionGroup))
                .build();
    }

    /**
     * Builds the WiFi disconnect threshold signal strength request
     *
     * <p>This request ensures that WiFi reports the crossing of the THRESHOLD_IN_USE RSSI value.
     * Without this request, WiFi rate-limits, and reports signal strength changes at too slow a
     * pace to effectively select away from a failing WiFi network.
     *
     * <p>Only the disconnect threshold is requested; for the association threshold, the worst case
     * is that the device takes longer to switch to Carrier WiFi, but that optimization comes at the
     * cost of the use of a NetworkRequest (which is capped on a per-process basis).
     */
    private NetworkRequest getWifiSignalStrengthNetworkRequest() {
        return getBaseNetworkRequestBuilder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setSubscriptionIds(mLastSnapshot.getAllSubIdsInGroup(mSubscriptionGroup))
                .setSignalStrength(
                        WIFI_RSSI_THRESHOLD_IN_USE) // Ensure wifi updates signal strengths when
                                                    // crossing this threshold.
                .build();
    }

    /**
     * Builds a Cellular bringup request for a given subId
     *
     * <p>This request is filed in order to ensure that the Telephony stack always has a
     * NetworkRequest to bring up a VCN underlying cellular network. It is required in order to
     * ensure that even when a VCN (appears as Cellular) satisfies the default request, Telephony
     * will bring up additional underlying Cellular networks.
     *
     * <p>Since this request MUST make it to the TelephonyNetworkFactory, subIds are not specified
     * in the NetworkCapabilities, but rather in the TelephonyNetworkSpecifier.
     */
    private NetworkRequest getCellNetworkRequestForSubId(int subId) {
        return getBaseNetworkRequestBuilder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .setNetworkSpecifier(new TelephonyNetworkSpecifier(subId))
                .build();
    }

    /**
     * Builds and returns a NetworkRequest builder common to all Underlying Network requests
     */
    private NetworkRequest.Builder getBaseNetworkRequestBuilder() {
        return new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED);
    }

    /**
     * Update this UnderlyingNetworkTracker's TelephonySubscriptionSnapshot.
     *
     * <p>Updating the TelephonySubscriptionSnapshot will cause this UnderlyingNetworkTracker to
     * reevaluate its NetworkBringupCallbacks. This may result in NetworkRequests being registered
     * or unregistered if the subIds mapped to the this Tracker's SubscriptionGroup change.
     */
    public void updateSubscriptionSnapshot(@NonNull TelephonySubscriptionSnapshot newSnapshot) {
        Objects.requireNonNull(newSnapshot, "Missing newSnapshot");

        final TelephonySubscriptionSnapshot oldSnapshot = mLastSnapshot;
        mLastSnapshot = newSnapshot;

        // Only trigger re-registration if subIds in this group have changed
        if (oldSnapshot
                .getAllSubIdsInGroup(mSubscriptionGroup)
                .equals(newSnapshot.getAllSubIdsInGroup(mSubscriptionGroup))) {
            return;
        }
        registerOrUpdateNetworkRequests();
    }

    /** Tears down this Tracker, and releases all underlying network requests. */
    public void teardown() {
        mVcnContext.ensureRunningOnLooperThread();
        mIsQuitting = true;

        // Will unregister all existing callbacks, but not register new ones due to quitting flag.
        registerOrUpdateNetworkRequests();
    }

    private void reevaluateNetworks() {
        TreeSet<UnderlyingNetworkRecord> sorted =
                new TreeSet<>(UnderlyingNetworkRecord.getComparator(mLastSnapshot, mCurrentRecord));
        sorted.addAll(mRouteSelectionCallback.getUnderlyingNetworks());

        UnderlyingNetworkRecord candidate = sorted.isEmpty() ? null : sorted.first();
        if (Objects.equals(mCurrentRecord, candidate)) {
            return;
        }

        mCurrentRecord = candidate;
        mCb.onSelectedUnderlyingNetworkChanged(mCurrentRecord);
    }

    /**
     * NetworkBringupCallback is used to keep background, VCN-managed Networks from being reaped.
     *
     * <p>NetworkBringupCallback only exists to prevent matching (VCN-managed) Networks from being
     * reaped, and no action is taken on any events firing.
     */
    @VisibleForTesting
    class NetworkBringupCallback extends NetworkCallback {}

    /**
     * RouteSelectionCallback is used to select the "best" underlying Network.
     *
     * <p>The "best" network is determined by ConnectivityService, which is treated as a source of
     * truth.
     */
    @VisibleForTesting
    class UnderlyingNetworkListener extends NetworkCallback {
        private final Map<Network, UnderlyingNetworkRecord.Builder> mUnderlyingNetworks =
                new ArrayMap<>();

        private List<UnderlyingNetworkRecord> getUnderlyingNetworks() {
            final List<UnderlyingNetworkRecord> records = new ArrayList<>();

            for (UnderlyingNetworkRecord.Builder builder : mUnderlyingNetworks.values()) {
                if (builder.isValid()) {
                    records.add(builder.build());
                }
            }

            return records;
        }

        @Override
        public void onAvailable(@NonNull Network network) {
            mUnderlyingNetworks.put(network, new UnderlyingNetworkRecord.Builder(network));
        }

        @Override
        public void onLost(@NonNull Network network) {
            mUnderlyingNetworks.remove(network);

            reevaluateNetworks();
        }

        @Override
        public void onCapabilitiesChanged(
                @NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            final UnderlyingNetworkRecord.Builder builder = mUnderlyingNetworks.get(network);
            if (builder == null) {
                Slog.wtf(TAG, "Got capabilities change for unknown key: " + network);
                return;
            }

            builder.setNetworkCapabilities(networkCapabilities);
            if (builder.isValid()) {
                reevaluateNetworks();
            }
        }

        @Override
        public void onLinkPropertiesChanged(
                @NonNull Network network, @NonNull LinkProperties linkProperties) {
            final UnderlyingNetworkRecord.Builder builder = mUnderlyingNetworks.get(network);
            if (builder == null) {
                Slog.wtf(TAG, "Got link properties change for unknown key: " + network);
                return;
            }

            builder.setLinkProperties(linkProperties);
            if (builder.isValid()) {
                reevaluateNetworks();
            }
        }

        @Override
        public void onBlockedStatusChanged(@NonNull Network network, boolean isBlocked) {
            final UnderlyingNetworkRecord.Builder builder = mUnderlyingNetworks.get(network);
            if (builder == null) {
                Slog.wtf(TAG, "Got blocked status change for unknown key: " + network);
                return;
            }

            builder.setIsBlocked(isBlocked);
            if (builder.isValid()) {
                reevaluateNetworks();
            }
        }
    }

    private int getWifiEntryRssiThreshold() {
        if (mCarrierConfig != null) {
            return mCarrierConfig.getInt(
                    VCN_NETWORK_SELECTION_WIFI_ENTRY_RSSI_THRESHOLD_KEY,
                    WIFI_ENTRY_RSSI_THRESHOLD_DEFAULT);
        }

        return WIFI_ENTRY_RSSI_THRESHOLD_DEFAULT;
    }

    private int getWifiExitRssiThreshold() {
        if (mCarrierConfig != null) {
            return mCarrierConfig.getInt(
                    VCN_NETWORK_SELECTION_WIFI_EXIT_RSSI_THRESHOLD_KEY,
                    WIFI_EXIT_RSSI_THRESHOLD_DEFAULT);
        }

        return WIFI_EXIT_RSSI_THRESHOLD_DEFAULT;
    }

    /** A record of a single underlying network, caching relevant fields. */
    public static class UnderlyingNetworkRecord {
        @NonNull public final Network network;
        @NonNull public final NetworkCapabilities networkCapabilities;
        @NonNull public final LinkProperties linkProperties;
        public final boolean isBlocked;

        @VisibleForTesting(visibility = Visibility.PRIVATE)
        UnderlyingNetworkRecord(
                @NonNull Network network,
                @NonNull NetworkCapabilities networkCapabilities,
                @NonNull LinkProperties linkProperties,
                boolean isBlocked) {
            this.network = network;
            this.networkCapabilities = networkCapabilities;
            this.linkProperties = linkProperties;
            this.isBlocked = isBlocked;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UnderlyingNetworkRecord)) return false;
            final UnderlyingNetworkRecord that = (UnderlyingNetworkRecord) o;

            return network.equals(that.network)
                    && networkCapabilities.equals(that.networkCapabilities)
                    && linkProperties.equals(that.linkProperties)
                    && isBlocked == that.isBlocked;
        }

        @Override
        public int hashCode() {
            return Objects.hash(network, networkCapabilities, linkProperties, isBlocked);
        }

        private boolean isOpportunistic(@NonNull TelephonySubscriptionSnapshot snapshot) {
            if (snapshot == null) {
                Slog.wtf(TAG, "Got null snapshot");
                return false;
            }

            final Set<Integer> subIds = networkCapabilities.getSubscriptionIds();
            for (int subId : subIds) {
                if (snapshot.isOpportunistic(subId)) {
                    return true;
                }
            }

            return false;
        }

        /**
         * Gives networks a priority class, based on the following priorities:
         *
         * <ol>
         *   <li>Opportunistic cellular
         *   <li>Carrier WiFi, signal strength > WIFI_ENTRY_RSSI_THRESHOLD_DEFAULT
         *   <li>Carrier WiFi, active network + signal strength > WIFI_EXIT_RSSI_THRESHOLD_DEFAULT
         *   <li>Macro cellular
         *   <li>Any others
         * </ol>
         */
        private int calculatePriorityClass(
                TelephonySubscriptionSnapshot snapshot, UnderlyingNetworkRecord currentlySelected) {
            final NetworkCapabilities caps = networkCapabilities;

            if (isBlocked || !caps.hasCapability(NET_CAPABILITY_VALIDATED)) {
                Slog.wtf(TAG, "Network blocked for System Server: " + network);
                return PRIORITY_ANY;
            }

            if (caps.hasTransport(TRANSPORT_CELLULAR) && isOpportunistic(snapshot)) {
                return PRIORITY_OPPORTUNISTIC_CELLULAR;
            }

            if (caps.hasTransport(TRANSPORT_WIFI)) {
                if (caps.getSignalStrength() > getWifiEntryRssiThreshold()) {
                    return PRIORITY_WIFI_PROSPECTIVE;
                }

                if (caps.getSignalStrength() > getWifiExitRssiThreshold()
                        && currentlySelected != null
                        && network.equals(currentlySelected.network)) {
                    return PRIORITY_WIFI_IN_USE;
                }
            }

            if (caps.hasTransport(TRANSPORT_CELLULAR)) {
                return PRIORITY_MACRO_CELLULAR;
            }

            return PRIORITY_ANY;
        }

        private static Comparator<UnderlyingNetworkRecord> getComparator(
                TelephonySubscriptionSnapshot snapshot, UnderlyingNetworkRecord currentlySelected) {
            return (left, right) -> {
                return Integer.compare(
                        left.calculatePriorityClass(snapshot, currentlySelected),
                        right.calculatePriorityClass(snapshot, currentlySelected));
            };
        }

        /** Dumps the state of this record for logging and debugging purposes. */
        public void dump(IndentingPrintWriter pw) {
            pw.println("UnderlyingNetworkRecord:");
            pw.increaseIndent();

            pw.println("mNetwork: " + network);
            pw.println("mNetworkCapabilities: " + networkCapabilities);
            pw.println("mLinkProperties: " + linkProperties);

            pw.decreaseIndent();
        }

        /** Builder to incrementally construct an UnderlyingNetworkRecord. */
        private static class Builder {
            @NonNull private final Network mNetwork;

            @Nullable private NetworkCapabilities mNetworkCapabilities;
            @Nullable private LinkProperties mLinkProperties;
            boolean mIsBlocked;
            boolean mWasIsBlockedSet;

            @Nullable private UnderlyingNetworkRecord mCached;

            private Builder(@NonNull Network network) {
                mNetwork = network;
            }

            @NonNull
            private Network getNetwork() {
                return mNetwork;
            }

            private void setNetworkCapabilities(@NonNull NetworkCapabilities networkCapabilities) {
                mNetworkCapabilities = networkCapabilities;
                mCached = null;
            }

            @Nullable
            private NetworkCapabilities getNetworkCapabilities() {
                return mNetworkCapabilities;
            }

            private void setLinkProperties(@NonNull LinkProperties linkProperties) {
                mLinkProperties = linkProperties;
                mCached = null;
            }

            private void setIsBlocked(boolean isBlocked) {
                mIsBlocked = isBlocked;
                mWasIsBlockedSet = true;
                mCached = null;
            }

            private boolean isValid() {
                return mNetworkCapabilities != null && mLinkProperties != null && mWasIsBlockedSet;
            }

            private UnderlyingNetworkRecord build() {
                if (!isValid()) {
                    throw new IllegalArgumentException(
                            "Called build before UnderlyingNetworkRecord was valid");
                }

                if (mCached == null) {
                    mCached =
                            new UnderlyingNetworkRecord(
                                    mNetwork, mNetworkCapabilities, mLinkProperties, mIsBlocked);
                }

                return mCached;
            }
        }
    }

    /** Callbacks for being notified of the changes in, or to the selected underlying network. */
    public interface UnderlyingNetworkTrackerCallback {
        /**
         * Fired when a new underlying network is selected, or properties have changed.
         *
         * <p>This callback does NOT signal a mobility event.
         *
         * @param underlyingNetworkRecord The details of the new underlying network
         */
        void onSelectedUnderlyingNetworkChanged(
                @Nullable UnderlyingNetworkRecord underlyingNetworkRecord);
    }

    private static class Dependencies {}
}
