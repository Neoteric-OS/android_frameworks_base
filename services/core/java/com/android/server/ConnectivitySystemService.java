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

package com.android.server;

import android.content.Context;
import android.net.INetworkPolicyManager;
import android.net.INetworkStatsService;
import android.os.INetworkManagementService;
import android.os.ServiceManager;
import android.util.Log;

/**
 * Connectivity service for core networking.
 */
public final class ConnectivitySystemService extends SystemService {
    private static final String TAG = "ConnectivitySystemService";
    final ConnectivityService mConnectivity;

    public ConnectivitySystemService(Context context) {
        super(context);
        mConnectivity = new ConnectivityService(context, getNetworkManagementService(),
                getNetworkStatsService(), getNetworkPolicyManager());
    }

    @Override
    public void onStart() {
        Log.i(TAG, "Registering " + Context.CONNECTIVITY_SERVICE);
        publishBinderService(Context.CONNECTIVITY_SERVICE, mConnectivity);
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            mConnectivity.systemReady();
        }
    }

    private INetworkManagementService getNetworkManagementService() {
        return INetworkManagementService.Stub.asInterface(
               ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE));
    }

    private INetworkStatsService getNetworkStatsService() {
        return INetworkStatsService.Stub.asInterface(
                ServiceManager.getService(Context.NETWORK_STATS_SERVICE));
    }

    private INetworkPolicyManager getNetworkPolicyManager() {
        return INetworkPolicyManager.Stub.asInterface(
                ServiceManager.getService(Context.NETWORK_POLICY_SERVICE));
    }

}
