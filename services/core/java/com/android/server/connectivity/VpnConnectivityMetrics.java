/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.connectivity;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class for logging VPN connectivity health event
 * @hide
 */
public class VpnConnectivityMetrics {
    public VpnConnectivityMetrics() {}

    private List<VpnConnection> mVpnConnectionList = new ArrayList<>();
    private Map<Integer, VpnMetricCollector> mVpnMetricCollectorMap = new HashMap<>();

    public class VpnMetricCollector {
        private final int mUserId;
        VpnMetricCollector(int userId) {
            mUserId = userId;
        }

        private VpnConnectionParams mVpnConnectionParams = null;

        /** Build VpnConnection proto and add it to the stored list. */
        private void buildAndAppendVpnConnectionMetric() {
            if (mVpnConnectionParams == null) {
                return;
            }
            final VpnConnection vpnConnection = new VpnConnection();
            vpnConnection.setVpnConnectionParams(mVpnConnectionParams);
            mVpnConnectionList.add(vpnConnection);
        }
    }

    /** Create a new VpnMetricCollector for the given user, to be used in Vpn */
    public VpnMetricCollector newCollector(int userId) {
        final VpnMetricCollector newVpnMetricCollector = new VpnMetricCollector(userId);
        mVpnMetricCollectorMap.put(userId, newVpnMetricCollector);
        return newVpnMetricCollector;
    }

    /** Build and get the currently stored metrics */
    @VisibleForTesting
    public List<VpnConnection> pullMetrics() {
        for (VpnMetricCollector vpnMetricCollector : mVpnMetricCollectorMap.values()) {
            vpnMetricCollector.buildAndAppendVpnConnectionMetric();
        }

        return mVpnConnectionList;
    }
}
