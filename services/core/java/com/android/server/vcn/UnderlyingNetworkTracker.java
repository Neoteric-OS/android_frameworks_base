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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkCapabilities.NetCapability;
import android.net.NetworkRequest;
import android.net.TelephonyNetworkSpecifier;
import android.os.Handler;
import android.os.ParcelUuid;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.ArraySet;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.annotations.VisibleForTesting.Visibility;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Tracks a set of Networks underpinning a VcnGatewayConnection.
 *
 * <p>A single UnderlyingNetworkTracker is built to serve a SINGLE VCN Gateway Connection, and MUST
 * be torn down with the VcnGatewayConnection in order to ensure underlying networks are allowed to
 * be reaped.
 *
 * @hide
 */
public class UnderlyingNetworkTracker extends Handler {
    @NonNull private static final String TAG = UnderlyingNetworkTracker.class.getSimpleName();

    @NonNull private final VcnContext mVcnContext;
    @NonNull private final ParcelUuid mSubscriptionGroup;
    @NonNull private final UnderlyingNetworkTrackerCallback mCb;
    @NonNull private final Dependencies mDeps;
    @NonNull private final ConnectivityManager mConnectivityManager;
    @NonNull private final SubscriptionManager mSubscriptionManager;

    @NonNull private final SparseArray<NetworkCallback> mCellBringupCallbacks = new SparseArray<>();
    @NonNull private final NetworkCallback mWifiBringupCallback = new NetworkBringupCallback();
    @NonNull private final NetworkCallback mRouteSelectionCallback = new RouteSelectionCallback();

    @NonNull private final Set<Integer> mSubIds = new ArraySet<>();

    @NonNull private final Set<Integer> mRequiredUnderlyingNetworkCapabilities;

    // Track NetworkCallback fields individually because NetworkCallback#onAvailable(Network,
    // NetworkCapabilities, LinkProperties, isBlocked) can't be used directly
    @Nullable private Network mSelectedNetwork;
    @Nullable private NetworkCapabilities mSelectedCapabilities;
    @Nullable private LinkProperties mSelectedProperties;
    private boolean mSelectedIsBlocked;
    private boolean mWasIsBlockedSet;

    public UnderlyingNetworkTracker(
            @NonNull VcnContext vcnContext,
            @NonNull ParcelUuid subscriptionGroup,
            @NonNull Set<Integer> requiredUnderlyingNetworkCapabilities,
            @NonNull UnderlyingNetworkTrackerCallback cb) {
        this(
                vcnContext,
                subscriptionGroup,
                requiredUnderlyingNetworkCapabilities,
                cb,
                new Dependencies());
    }

    private UnderlyingNetworkTracker(
            @NonNull VcnContext vcnContext,
            @NonNull ParcelUuid subscriptionGroup,
            @NonNull Set<Integer> requiredUnderlyingNetworkCapabilities,
            @NonNull UnderlyingNetworkTrackerCallback cb,
            @NonNull Dependencies deps) {
        super(Objects.requireNonNull(vcnContext, "Missing vcnContext").getLooper());
        mVcnContext = vcnContext;
        mSubscriptionGroup = Objects.requireNonNull(subscriptionGroup, "Missing subscriptionGroup");
        mCb = Objects.requireNonNull(cb, "Missing cb");
        mDeps = Objects.requireNonNull(deps, "Missing deps");
        mRequiredUnderlyingNetworkCapabilities =
                Objects.requireNonNull(
                        requiredUnderlyingNetworkCapabilities,
                        "Missing requiredUnderlyingNetworkCapabilities");
        mConnectivityManager = mVcnContext.getContext().getSystemService(ConnectivityManager.class);
        mSubscriptionManager = mVcnContext.getContext().getSystemService(SubscriptionManager.class);

        registerNetworkRequests();
    }

    private void registerNetworkRequests() {
        // register bringup requests for underlying Networks
        mConnectivityManager.requestBackgroundNetwork(
                getWifiNetworkRequest(), this, mWifiBringupCallback);
        updateSubIdsAndCellularRequests();

        // register Network-selection request used to decide selected underlying Network
        mConnectivityManager.requestBackgroundNetwork(
                getNetworkRequestBase().build(), this, mRouteSelectionCallback);
    }

    private NetworkRequest getWifiNetworkRequest() {
        return getNetworkRequestBase().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
    }

    private NetworkRequest getCellNetworkRequestForSubId(int subId) {
        return getNetworkRequestBase()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .setNetworkSpecifier(new TelephonyNetworkSpecifier(subId))
                .build();
    }

    private NetworkRequest.Builder getNetworkRequestBase() {
        NetworkRequest.Builder requestBase = new NetworkRequest.Builder();
        for (@NetCapability int capability : mRequiredUnderlyingNetworkCapabilities) {
            requestBase.addCapability(capability);
        }

        return requestBase
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                .addUnwantedCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED);
    }

    /**
     * Update the current subIds and Cellular bringup requests for this UnderlyingNetworkTracker.
     */
    private void updateSubIdsAndCellularRequests() {
        mVcnContext.ensureRunningOnLooperThread();

        Set<Integer> prevSubIds = new ArraySet<>(mSubIds);
        mSubIds.clear();

        // Ensure NetworkRequests filed for all current subIds in mSubscriptionGroup
        // STOPSHIP: b/177364490 use TelephonySubscriptionSnapshot to avoid querying Telephony
        List<SubscriptionInfo> subInfos =
                mSubscriptionManager.getSubscriptionsInGroup(mSubscriptionGroup);

        for (SubscriptionInfo subInfo : subInfos) {
            final int subId = subInfo.getSubscriptionId();
            mSubIds.add(subId);

            if (!mCellBringupCallbacks.contains(subId)) {
                final NetworkBringupCallback cb = new NetworkBringupCallback();
                mCellBringupCallbacks.put(subId, cb);

                mConnectivityManager.requestBackgroundNetwork(
                        getCellNetworkRequestForSubId(subId), this, cb);
            }
        }

        // unregister all NetworkCallbacks for outdated subIds
        for (final int subId : prevSubIds) {
            if (!mSubIds.contains(subId)) {
                final NetworkCallback cb = mCellBringupCallbacks.removeReturnOld(subId);
                mConnectivityManager.unregisterNetworkCallback(cb);
            }
        }
    }

    /** Tears down this Tracker, and releases all underlying network requests. */
    public void teardown() {
        mVcnContext.ensureRunningOnLooperThread();

        mConnectivityManager.unregisterNetworkCallback(mWifiBringupCallback);
        mConnectivityManager.unregisterNetworkCallback(mRouteSelectionCallback);

        for (final int subId : mSubIds) {
            final NetworkCallback cb = mCellBringupCallbacks.removeReturnOld(subId);
            mConnectivityManager.unregisterNetworkCallback(cb);
        }
        mSubIds.clear();
    }

    private void resetNetworkTracking() {
        mSelectedNetwork = null;
        mSelectedCapabilities = null;
        mSelectedProperties = null;
        mSelectedIsBlocked = false;
        mWasIsBlockedSet = false;
    }

    /** Returns whether the currently selected Network matches the given network. */
    private static boolean isSameNetwork(
            @Nullable Network selectedNetwork, @NonNull Network network) {
        return selectedNetwork != null && selectedNetwork.equals(network);
    }

    /** Notify the Callback if a full UnderlyingNetworkRecord exists. */
    private void maybeNotifyCallback() {
        // Only forward this update if all fields have been provided by ConnectivityService
        if (mSelectedNetwork != null
                && mSelectedCapabilities != null
                && mSelectedProperties != null
                && mWasIsBlockedSet) {
            mCb.onSelectedUnderlyingNetworkChanged(
                    new UnderlyingNetworkRecord(
                            mSelectedNetwork,
                            mSelectedCapabilities,
                            mSelectedProperties,
                            mSelectedIsBlocked));
        }
    }

    private void handleNetworkAvailable(@NonNull Network network) {
        mVcnContext.ensureRunningOnLooperThread();

        resetNetworkTracking();

        mSelectedNetwork = network;
    }

    private void handleNetworkLost(@NonNull Network network) {
        mVcnContext.ensureRunningOnLooperThread();

        if (!isSameNetwork(mSelectedNetwork, network)) {
            Slog.wtf(TAG, "Non-underlying Network lost");
            return;
        }

        resetNetworkTracking();

        mCb.onSelectedUnderlyingNetworkChanged(null /* underlyingNetworkRecord */);
    }

    private void handleCapabilitiesChanged(
            @NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
        mVcnContext.ensureRunningOnLooperThread();

        if (!isSameNetwork(mSelectedNetwork, network)) {
            Slog.wtf(TAG, "Invalid update to NetworkCapabilities");
            return;
        }

        // Don't notify the callback if the NetworkCapabilities are unchanged.
        if (networkCapabilities.equals(mSelectedCapabilities)) {
            return;
        }

        mSelectedCapabilities = networkCapabilities;

        maybeNotifyCallback();
    }

    private NetworkCapabilities getCapsWithSuspended(boolean isSuspended) {
        final NetworkCapabilities newCaps = new NetworkCapabilities(mSelectedCapabilities);
        if (isSuspended) {
            newCaps.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED);
        } else {
            newCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED);
        }
        return newCaps;
    }

    private void handleNetworkSuspended(@NonNull Network network, boolean isSuspended) {
        mVcnContext.ensureRunningOnLooperThread();

        if (!isSameNetwork(mSelectedNetwork, network)) {
            Slog.wtf(TAG, "Invalid update to isSuspended");
            return;
        }

        handleCapabilitiesChanged(network, getCapsWithSuspended(isSuspended));
    }

    private void handlePropertiesChanged(
            @NonNull Network network, @NonNull LinkProperties linkProperties) {
        mVcnContext.ensureRunningOnLooperThread();

        if (!isSameNetwork(mSelectedNetwork, network)) {
            Slog.wtf(TAG, "Invalid update to LinkProperties");
            return;
        }

        // Don't notify the callback if the LinkProperties are unchanged.
        if (linkProperties.equals(mSelectedProperties)) {
            return;
        }

        mSelectedProperties = linkProperties;

        maybeNotifyCallback();
    }

    private void handleNetworkBlocked(@NonNull Network network, boolean isBlocked) {
        mVcnContext.ensureRunningOnLooperThread();

        if (!isSameNetwork(mSelectedNetwork, network)) {
            Slog.wtf(TAG, "Invalid update to isBlocked");
            return;
        }

        // Don't notify the callback if isBlocked is unchanged (must have been set previously).
        if (mWasIsBlockedSet && (mSelectedIsBlocked == isBlocked)) {
            return;
        }

        mWasIsBlockedSet = true;
        mSelectedIsBlocked = isBlocked;

        maybeNotifyCallback();
    }

    /**
     * NetworkBringupCallback is a NetworkCallback used to keep background, VCN-managed Networks
     * from being reaped by ConnectivityService.
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
    class RouteSelectionCallback extends NetworkCallback {
        @Override
        public void onAvailable(@NonNull Network network) {
            handleNetworkAvailable(network);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handleNetworkLost(network);
        }

        @Override
        public void onCapabilitiesChanged(
                @NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            handleCapabilitiesChanged(network, networkCapabilities);
        }

        @Override
        public void onNetworkSuspended(@NonNull Network network) {
            handleNetworkSuspended(network, true /* isSuspended */);
        }

        @Override
        public void onNetworkResumed(@NonNull Network network) {
            handleNetworkSuspended(network, false /* isSuspended */);
        }

        @Override
        public void onLinkPropertiesChanged(
                @NonNull Network network, @NonNull LinkProperties linkProperties) {
            handlePropertiesChanged(network, linkProperties);
        }

        @Override
        public void onBlockedStatusChanged(@NonNull Network network, boolean isBlocked) {
            handleNetworkBlocked(network, isBlocked);
        }
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
