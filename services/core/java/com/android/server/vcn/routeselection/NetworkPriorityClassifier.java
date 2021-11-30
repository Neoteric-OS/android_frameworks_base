/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.vcn.VcnUnderlyingNetworkPriority.NETWORK_QUALITY_ANY;

import static com.android.server.VcnManagementService.LOCAL_LOG;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.NetworkCapabilities;
import android.net.vcn.CellUnderlyingNetworkPriority;
import android.net.vcn.VcnManager;
import android.net.vcn.VcnUnderlyingNetworkPriority;
import android.net.vcn.WifiUnderlyingNetworkPriority;
import android.os.ParcelUuid;
import android.os.PersistableBundle;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.annotations.VisibleForTesting.Visibility;
import com.android.server.vcn.TelephonySubscriptionTracker.TelephonySubscriptionSnapshot;
import com.android.server.vcn.VcnContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** @hide */
class NetworkPriorityClassifier {
    @NonNull private static final String TAG = NetworkPriorityClassifier.class.getSimpleName();
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

    /** Priority for any other networks (including unvalidated, etc) */
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    static final int PRIORITY_ANY = Integer.MAX_VALUE;

    /**
     * Gives networks a priority class, based on caller configuration in VcnGatewayConnectionConfig
     */
    public static int calculatePriorityClass(
            UnderlyingNetworkRecord networkRecord,
            VcnContext vcnContext,
            LinkedHashSet underlyingNetworkPriorities,
            ParcelUuid subscriptionGroup,
            TelephonySubscriptionSnapshot snapshot,
            UnderlyingNetworkRecord currentlySelected,
            PersistableBundle carrierConfig) {
        // mRouteSelectionNetworkRequest requires a network be both VALIDATED and NOT_SUSPENDED

        if (networkRecord.isBlocked) {
            logWtf("Network blocked for System Server: " + networkRecord.network);
            return PRIORITY_ANY;
        }

        final List<VcnUnderlyingNetworkPriority> nwPriorities =
                new ArrayList<>(underlyingNetworkPriorities);

        for (int i = 0; i < nwPriorities.size(); i++) {
            VcnUnderlyingNetworkPriority nwPriority = nwPriorities.get(i);
            if (nwPriority instanceof WifiUnderlyingNetworkPriority
                    && matchWifiUnderlyingNetworkPriority(
                            (WifiUnderlyingNetworkPriority) nwPriority,
                            networkRecord,
                            currentlySelected,
                            carrierConfig)) {
                return i;
            }

            if (nwPriority instanceof CellUnderlyingNetworkPriority
                    && matchCellUnderlyingNetworkPriority(
                            (CellUnderlyingNetworkPriority) nwPriority,
                            vcnContext,
                            networkRecord,
                            subscriptionGroup,
                            snapshot)) {
                return i;
            }
        }
        return PRIORITY_ANY;
    }

    @VisibleForTesting(visibility = Visibility.PRIVATE)
    public static boolean matchWifiUnderlyingNetworkPriority(
            WifiUnderlyingNetworkPriority networkPriority,
            UnderlyingNetworkRecord networkRecord,
            UnderlyingNetworkRecord currentlySelected,
            PersistableBundle carrierConfig) {
        final NetworkCapabilities caps = networkRecord.networkCapabilities;

        if (!caps.hasTransport(TRANSPORT_WIFI)) {
            return false;
        }

        final boolean isSelectedNetwork =
                currentlySelected != null
                        && networkRecord.network.equals(currentlySelected.network);

        if (networkPriority.getNetworkQuality() != NETWORK_QUALITY_ANY
                && caps.getSignalStrength() < getWifiExitRssiThreshold(carrierConfig)
                && isSelectedNetwork) {
            return false;
        }

        if (networkPriority.getNetworkQuality() != NETWORK_QUALITY_ANY
                && caps.getSignalStrength() < getWifiEntryRssiThreshold(carrierConfig)
                && !isSelectedNetwork) {
            return false;
        }

        if (!networkPriority.allowMetered() && !caps.hasCapability(NET_CAPABILITY_NOT_METERED)) {
            return false;
        }

        if (networkPriority.getSsid() != null && networkPriority.getSsid() != caps.getSsid()) {
            return false;
        }

        return true;
    }

    @VisibleForTesting(visibility = Visibility.PRIVATE)
    public static boolean matchCellUnderlyingNetworkPriority(
            CellUnderlyingNetworkPriority networkPriority,
            VcnContext vcnContext,
            UnderlyingNetworkRecord networkRecord,
            ParcelUuid subscriptionGroup,
            TelephonySubscriptionSnapshot snapshot) {
        final NetworkCapabilities caps = networkRecord.networkCapabilities;

        if (!caps.hasTransport(TRANSPORT_CELLULAR)) {
            android.util.Log.e("TEST", "caps.hasTransport(TRANSPORT_CELLULAR)");
            return false;
        }

        if (!networkPriority.allowMetered() && !caps.hasCapability(NET_CAPABILITY_NOT_METERED)) {
            android.util.Log.e("TEST", "networkPriority.allowMetered()");
            return false;
        }

        if (snapshot == null) {
            logWtf("Got null snapshot");
            return false;
        }

        if (!networkPriority.getAllowedPlmnIds().isEmpty()) {
            final TelephonyManager defaultTelephonyMgr =
                    vcnContext.getContext().getSystemService(TelephonyManager.class);
            for (int subId : caps.getSubscriptionIds()) {
                final String plmnId =
                        defaultTelephonyMgr.createForSubscriptionId(subId).getNetworkOperator();
                if (!networkPriority.getAllowedPlmnIds().contains(plmnId)) {
                    android.util.Log.e("TEST", "getAllowedPlmnIds");
                    return false;
                }
            }
        }

        if (!networkPriority.getAllowedSpecificCarrierIds().isEmpty()) {
            final TelephonyManager defaultTelephonyMgr =
                    vcnContext.getContext().getSystemService(TelephonyManager.class);
            for (int subId : caps.getSubscriptionIds()) {
                final int carrierId =
                        defaultTelephonyMgr
                                .createForSubscriptionId(subId)
                                .getSimSpecificCarrierId();
                if (!networkPriority.getAllowedSpecificCarrierIds().contains(carrierId)) {
                    android.util.Log.e("TEST", "getAllowedSpecificCarrierIds");
                    return false;
                }
            }
        }

        if (!networkPriority.allowRoaming() && !caps.hasCapability(NET_CAPABILITY_NOT_ROAMING)) {
            android.util.Log.e("TEST", "allowRoaming");
            return false;
        }

        if (networkPriority.requireOpportunistic()) {
            if (!isOpportunistic(snapshot, caps.getSubscriptionIds())) {
                android.util.Log.e("TEST", "isOpportunistic");
                return false;
            }

            // If this carrier is the active data provider, ensure that opportunistic is only
            // ever prioritized if it is also the active data subscription. This ensures that
            // if an opportunistic subscription is still in the process of being switched to,
            // or switched away from, the VCN does not attempt to continue using it against the
            // decision made at the telephony layer. Failure to do so may result in the modem
            // switching back and forth.
            //
            // Allow the following two cases:
            // 1. Active subId is NOT in the group that this VCN is supporting
            // 2. This opportunistic subscription is for the active subId
            if (snapshot.getAllSubIdsInGroup(subscriptionGroup)
                            .contains(SubscriptionManager.getActiveDataSubscriptionId())
                    && !caps.getSubscriptionIds()
                            .contains(SubscriptionManager.getActiveDataSubscriptionId())) {
                return false;
            }
        }

        return true;
    }

    static boolean isOpportunistic(
            @NonNull TelephonySubscriptionSnapshot snapshot, Set<Integer> subIds) {
        android.util.Log.d("TEST", "isOpportunistic " + snapshot);
        android.util.Log.d("TEST", "subIds.size() " + subIds.size());
        if (snapshot == null) {
            logWtf("Got null snapshot");
            return false;
        }
        for (int subId : subIds) {
            android.util.Log.d("TEST", "subId " + subId);
            if (snapshot.isOpportunistic(subId)) {
                android.util.Log.d("TEST", "subId " + subId);
                return true;
            }
        }
        return false;
    }

    static int getWifiEntryRssiThreshold(@Nullable PersistableBundle carrierConfig) {
        if (carrierConfig != null) {
            return carrierConfig.getInt(
                    VcnManager.VCN_NETWORK_SELECTION_WIFI_ENTRY_RSSI_THRESHOLD_KEY,
                    WIFI_ENTRY_RSSI_THRESHOLD_DEFAULT);
        }
        return WIFI_ENTRY_RSSI_THRESHOLD_DEFAULT;
    }

    static int getWifiExitRssiThreshold(@Nullable PersistableBundle carrierConfig) {
        if (carrierConfig != null) {
            return carrierConfig.getInt(
                    VcnManager.VCN_NETWORK_SELECTION_WIFI_EXIT_RSSI_THRESHOLD_KEY,
                    WIFI_EXIT_RSSI_THRESHOLD_DEFAULT);
        }
        return WIFI_EXIT_RSSI_THRESHOLD_DEFAULT;
    }

    static String priorityClassToString(int priorityClass) {
        return "Not implemented";
    }

    private static void logWtf(String msg) {
        Slog.wtf(TAG, msg);
        LOCAL_LOG.log(TAG + " WTF: " + msg);
    }
}
