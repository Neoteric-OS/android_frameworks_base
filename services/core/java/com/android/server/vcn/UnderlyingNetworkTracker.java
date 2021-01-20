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
import android.util.ArraySet;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.vcn.TelephonySubscriptionTracker.TelephonySubscriptionSnapshot;

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

    @NonNull private final SparseArray<NetworkCallback> mCellBringupCallbacks = new SparseArray<>();
    @NonNull private final NetworkCallback mWifiBringupCallback = new NetworkBringupCallback();
    @NonNull private final NetworkCallback mRouteSelectionCallback = new RouteSelectionCallback();

    @NonNull private final Set<Integer> mSubIds = new ArraySet<>();

    @NonNull private final Set<Integer> mRequiredUnderlyingNetworkCapabilities;

    @NonNull private TelephonySubscriptionSnapshot mLastSnapshot;
    private boolean mIsTornDown = false;

    @Nullable private UnderlyingNetworkRecord mSelectedUnderlyingNetworkRecord;

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
        super(Objects.requireNonNull(vcnContext, "Missing vcnContext").getLooper());
        mVcnContext = vcnContext;
        mSubscriptionGroup = Objects.requireNonNull(subscriptionGroup, "Missing subscriptionGroup");
        mLastSnapshot = Objects.requireNonNull(snapshot, "Missing snapshot");
        mCb = Objects.requireNonNull(cb, "Missing cb");
        mDeps = Objects.requireNonNull(deps, "Missing deps");
        mRequiredUnderlyingNetworkCapabilities =
                Objects.requireNonNull(
                        requiredUnderlyingNetworkCapabilities,
                        "Missing requiredUnderlyingNetworkCapabilities");
        mConnectivityManager = mVcnContext.getContext().getSystemService(ConnectivityManager.class);

        registerNetworkRequests();
    }

    private void registerNetworkRequests() {
        // register bringup requests for underlying Networks
        mConnectivityManager.requestBackgroundNetwork(
                getWifiNetworkRequest(), mWifiBringupCallback);
        updateSubIdsAndCellularRequests();

        // register Network-selection request used to decide selected underlying Network
        mConnectivityManager.requestBackgroundNetwork(
                getNetworkRequestBase().build(), mRouteSelectionCallback);
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
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                .addUnwantedCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED);
    }

    /**
     * Update the current subIds and Cellular bringup requests for this UnderlyingNetworkTracker.
     */
    private void updateSubIdsAndCellularRequests() {
        mVcnContext.ensureRunningOnLooperThread();

        // Don't bother re-filing NetworkRequests if this Tracker has been torn down.
        if (mIsTornDown) {
            return;
        }

        mSubIds.clear();

        // Ensure NetworkRequests filed for all current subIds in mSubscriptionGroup
        final Set<Integer> subIdsInSubGroup = mLastSnapshot.getAllSubIdsInGroup(mSubscriptionGroup);
        for (final int subId : subIdsInSubGroup) {
            mSubIds.add(subId);

            if (!mCellBringupCallbacks.contains(subId)) {
                final NetworkBringupCallback cb = new NetworkBringupCallback();
                mCellBringupCallbacks.put(subId, cb);

                mConnectivityManager.requestBackgroundNetwork(
                        getCellNetworkRequestForSubId(subId), cb);
            }
        }

        // unregister all NetworkCallbacks for outdated subIds
        for (int i = 0; i < mCellBringupCallbacks.size(); i++) {
            final int subId = mCellBringupCallbacks.keyAt(i);

            if (!mSubIds.contains(subId)) {
                final NetworkCallback cb = mCellBringupCallbacks.removeReturnOld(subId);
                mConnectivityManager.unregisterNetworkCallback(cb);
            }
        }
    }

    /**
     * Update this UnderlyingNetworkTracker's TelephonySubscriptionSnapshot.
     *
     * <p>Updating the TelephonySubscriptionSnapshot will cause this UnderlyingNetworkTracker to
     * reevaluate its NetworkBringupCallbacks. This may result in NetworkRequests being registered
     * or unregistered if the subIds mapped to the this Tracker's SubscriptionGroup change.
     */
    // package-private
    void updateSubscriptionSnapshot(@NonNull TelephonySubscriptionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "Missing snapshot");

        mLastSnapshot = snapshot;
        updateSubIdsAndCellularRequests();
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

        mIsTornDown = true;
    }

    private NetworkCapabilities getCapsWithSuspended(boolean isSuspended) {
        mVcnContext.ensureRunningOnLooperThread();

        final NetworkCapabilities newCaps =
                new NetworkCapabilities(mSelectedUnderlyingNetworkRecord.networkCapabilities);
        if (isSuspended) {
            newCaps.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED);
        } else {
            newCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED);
        }
        return newCaps;
    }

    /**
     * Returns whether an update for the specified Network is valid.
     *
     * <p>Also verifies that the caller is running on this VcnContext's Looper Thread.
     *
     * @return true if there is an UnderlyingNetworkRecord assigned for this
     *     UnderlyingNetworkTracker and the specified Network matches the current selected
     *     underlying Network.
     */
    private boolean isValidUpdate(@NonNull Network network) {
        mVcnContext.ensureRunningOnLooperThread();

        return mSelectedUnderlyingNetworkRecord != null
                && mSelectedUnderlyingNetworkRecord.network.equals(network);
    }

    private void updateRecordAndNotifyCallback(
            @NonNull Network network, @Nullable NetworkCapabilities networkCapabilities) {
        if (!isValidUpdate(network)) {
            Slog.wtf(TAG, "Invalid update to NetworkCapabilities");
            return;
        }

        updateRecordAndNotifyCallback(
                new UnderlyingNetworkRecord(
                        network,
                        networkCapabilities,
                        mSelectedUnderlyingNetworkRecord.linkProperties,
                        mSelectedUnderlyingNetworkRecord.blocked));
    }

    private void updateRecordAndNotifyCallback(
            @NonNull Network network, @Nullable LinkProperties linkProperties) {
        if (!isValidUpdate(network)) {
            Slog.wtf(TAG, "Invalid update to LinkProperties");
            return;
        }

        updateRecordAndNotifyCallback(
                new UnderlyingNetworkRecord(
                        network,
                        mSelectedUnderlyingNetworkRecord.networkCapabilities,
                        linkProperties,
                        mSelectedUnderlyingNetworkRecord.blocked));
    }

    private void updateRecordAndNotifyCallback(@NonNull Network network, boolean isBlocked) {
        if (!isValidUpdate(network)) {
            Slog.wtf(TAG, "Invalid update to isBlocked");
            return;
        }

        updateRecordAndNotifyCallback(
                new UnderlyingNetworkRecord(
                        network,
                        mSelectedUnderlyingNetworkRecord.networkCapabilities,
                        mSelectedUnderlyingNetworkRecord.linkProperties,
                        isBlocked));
    }

    private void updateRecordAndNotifyCallback(
            @Nullable UnderlyingNetworkRecord underlyingNetworkRecord) {
        // Only forward this update if it represents a change in the underlying Network record
        if (mSelectedUnderlyingNetworkRecord == null
                || !mSelectedUnderlyingNetworkRecord.equals(underlyingNetworkRecord)) {
            mSelectedUnderlyingNetworkRecord = underlyingNetworkRecord;
            mCb.onSelectedUnderlyingNetworkChanged(underlyingNetworkRecord);
        }
    }

    /**
     * NetworkBringupCallback is a NetworkCallback used to keep background, VCN-managed Networks
     * from being reaped by ConnectivityService.
     *
     * <p>NetworkBringupCallback only exists to prevent matching (VCN-managed) Networks from being
     * reaped, so use the default implementation of NetworkCallback.
     */
    @VisibleForTesting
    class NetworkBringupCallback extends NetworkCallback {}

    /**
     * RouteSelectionCallback is a NetworkCallback used to track the currently selected VCN-managed
     * underlying Network from ConnectivityService.
     */
    @VisibleForTesting
    class RouteSelectionCallback extends NetworkCallback {
        @Override
        public void onAvailable(
                @NonNull Network network,
                @NonNull NetworkCapabilities networkCapabilities,
                @NonNull LinkProperties linkProperties,
                boolean isBlocked) {
            mVcnContext.ensureRunningOnLooperThread();

            updateRecordAndNotifyCallback(
                    new UnderlyingNetworkRecord(
                            network, networkCapabilities, linkProperties, isBlocked));
        }

        @Override
        public void onLost(@NonNull Network network) {
            if (!isValidUpdate(network)) {
                Slog.wtf(TAG, "Non-underlying Network lost");
                return;
            }

            updateRecordAndNotifyCallback(null /* underlyingNetworkRecord */);
        }

        @Override
        public void onCapabilitiesChanged(
                @NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            updateRecordAndNotifyCallback(network, networkCapabilities);
        }

        @Override
        public void onNetworkSuspended(@NonNull Network network) {
            onCapabilitiesChanged(network, getCapsWithSuspended(true /* isSuspended */));
        }

        @Override
        public void onNetworkResumed(@NonNull Network network) {
            onCapabilitiesChanged(network, getCapsWithSuspended(false /* isSuspended */));
        }

        @Override
        public void onLinkPropertiesChanged(
                @NonNull Network network, @NonNull LinkProperties linkProperties) {
            updateRecordAndNotifyCallback(network, linkProperties);
        }

        @Override
        public void onBlockedStatusChanged(@NonNull Network network, boolean isBlocked) {
            updateRecordAndNotifyCallback(network, isBlocked);
        }
    }

    /** A record of a single underlying network, caching relevant fields. */
    public static class UnderlyingNetworkRecord {
        @NonNull public final Network network;
        @NonNull public final NetworkCapabilities networkCapabilities;
        @NonNull public final LinkProperties linkProperties;
        public final boolean blocked;

        @VisibleForTesting
        UnderlyingNetworkRecord(
                @NonNull Network network,
                @NonNull NetworkCapabilities networkCapabilities,
                @NonNull LinkProperties linkProperties,
                boolean blocked) {
            this.network = network;
            this.networkCapabilities = networkCapabilities;
            this.linkProperties = linkProperties;
            this.blocked = blocked;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UnderlyingNetworkRecord)) return false;
            final UnderlyingNetworkRecord that = (UnderlyingNetworkRecord) o;

            return network.equals(that.network)
                    && networkCapabilities.equals(that.networkCapabilities)
                    && linkProperties.equals(that.linkProperties)
                    && blocked == that.blocked;
        }

        @Override
        public int hashCode() {
            return Objects.hash(network, networkCapabilities, linkProperties, blocked);
        }
    }

    /** Callbacks for being notified of the changes in, or to the selected underlying network. */
    public interface UnderlyingNetworkTrackerCallback {
        /**
         * Fired when a new underlying network is selected, or properties have changed.
         *
         * <p>This callback does NOT signal a mobility event.
         *
         * @param underlying The details of the new underlying network
         */
        void onSelectedUnderlyingNetworkChanged(@Nullable UnderlyingNetworkRecord underlying);
    }

    private static class Dependencies {}
}
