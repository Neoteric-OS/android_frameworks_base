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

package com.android.server.vcn.routeselection;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.vcn.VcnGatewayConnectionConfig;
import android.os.ParcelUuid;
import android.os.PersistableBundle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.annotations.VisibleForTesting.Visibility;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.vcn.TelephonySubscriptionTracker.TelephonySubscriptionSnapshot;

import java.util.Comparator;
import java.util.Objects;

/** @hide */
/** A record of a single underlying network, caching relevant fields. */
public class UnderlyingNetworkRecord {
    @NonNull public final Network network;
    @NonNull public final NetworkCapabilities networkCapabilities;
    @NonNull public final LinkProperties linkProperties;
    public final boolean isBlocked;

    @VisibleForTesting(visibility = Visibility.PRIVATE)
    public UnderlyingNetworkRecord(
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

    /**
     * Gives networks a priority class, based on the following priorities:
     *
     * <ol>
     *   <li>Opportunistic cellular
     *   <li>Carrier WiFi, signal strength >= WIFI_ENTRY_RSSI_THRESHOLD_DEFAULT
     *   <li>Carrier WiFi, active network + signal strength >= WIFI_EXIT_RSSI_THRESHOLD_DEFAULT
     *   <li>Macro cellular
     *   <li>Any others
     * </ol>
     */
    private int calculatePriorityClass(
            VcnGatewayConnectionConfig connectionConfig,
            ParcelUuid subscriptionGroup,
            TelephonySubscriptionSnapshot snapshot,
            UnderlyingNetworkRecord currentlySelected,
            PersistableBundle carrierConfig) {
        return NetworkPriorityClassifier.calculatePriorityClass(
                this,
                connectionConfig,
                subscriptionGroup,
                snapshot,
                currentlySelected,
                carrierConfig);
    }

    public static Comparator<UnderlyingNetworkRecord> getComparator(
            VcnGatewayConnectionConfig connectionConfig,
            ParcelUuid subscriptionGroup,
            TelephonySubscriptionSnapshot snapshot,
            UnderlyingNetworkRecord currentlySelected,
            PersistableBundle carrierConfig) {
        return (left, right) -> {
            return Integer.compare(
                    left.calculatePriorityClass(
                            connectionConfig,
                            subscriptionGroup,
                            snapshot,
                            currentlySelected,
                            carrierConfig),
                    right.calculatePriorityClass(
                            connectionConfig,
                            subscriptionGroup,
                            snapshot,
                            currentlySelected,
                            carrierConfig));
        };
    }

    /** Dumps the state of this record for logging and debugging purposes. */
    public void dump(
            IndentingPrintWriter pw,
            VcnGatewayConnectionConfig connectionConfig,
            ParcelUuid subscriptionGroup,
            TelephonySubscriptionSnapshot snapshot,
            UnderlyingNetworkRecord currentlySelected,
            PersistableBundle carrierConfig) {
        pw.println("UnderlyingNetworkRecord:");
        pw.increaseIndent();

        final int priorityClass =
                calculatePriorityClass(
                        connectionConfig,
                        subscriptionGroup,
                        snapshot,
                        currentlySelected,
                        carrierConfig);
        pw.println(
                "Priority class: "
                        + NetworkPriorityClassifier.priorityClassToString(priorityClass)
                        + " ("
                        + priorityClass
                        + ")");
        pw.println("mNetwork: " + network);
        pw.println("mNetworkCapabilities: " + networkCapabilities);
        pw.println("mLinkProperties: " + linkProperties);

        pw.decreaseIndent();
    }

    /** Builder to incrementally construct an UnderlyingNetworkRecord. */
    static class Builder {
        @NonNull private final Network mNetwork;

        @Nullable private NetworkCapabilities mNetworkCapabilities;
        @Nullable private LinkProperties mLinkProperties;
        boolean mIsBlocked;
        boolean mWasIsBlockedSet;

        @Nullable private UnderlyingNetworkRecord mCached;

        Builder(@NonNull Network network) {
            mNetwork = network;
        }

        @NonNull
        Network getNetwork() {
            return mNetwork;
        }

        void setNetworkCapabilities(@NonNull NetworkCapabilities networkCapabilities) {
            mNetworkCapabilities = networkCapabilities;
            mCached = null;
        }

        @Nullable
        NetworkCapabilities getNetworkCapabilities() {
            return mNetworkCapabilities;
        }

        void setLinkProperties(@NonNull LinkProperties linkProperties) {
            mLinkProperties = linkProperties;
            mCached = null;
        }

        void setIsBlocked(boolean isBlocked) {
            mIsBlocked = isBlocked;
            mWasIsBlockedSet = true;
            mCached = null;
        }

        boolean isValid() {
            return mNetworkCapabilities != null && mLinkProperties != null && mWasIsBlockedSet;
        }

        UnderlyingNetworkRecord build() {
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
