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

package android.net;

import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.content.Context;
import android.os.INetworkManagementService;
import android.os.RemoteException;
import android.os.ServiceManager;

import java.util.Objects;

/**
 * Wrapper class to track network data activity with netd.
 *
 * @hide
 */
@SystemApi
public class NetworkActivityWrapper {
    private INetworkManagementService mNMS;

    public NetworkActivityWrapper() {
        mNMS = INetworkManagementService.Stub.asInterface(
                ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE));
        Objects.requireNonNull(mNMS, "NetworkManageManagementService is unavailable");
    }

    private INetworkManagementService getNMS() {
        // NMS is acquired in the constructor. If callers create this too early while NMS may not be
        // ready yet, the NMS object may be null. Give a chance to get NMS again when callers start
        // using it.
        if (mNMS == null) {
            mNMS = INetworkManagementService.Stub.asInterface(
                    ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE));
        }
        Objects.requireNonNull(mNMS, "NetworkManageManagementService is unavailable");
        return mNMS;
    }

    /**
     * Add idletimer for specific interface.
     *
     * @param iface Name of target interface
     * @param timeout The time in seconds that will trigger idletimer
     * @param type The network type of the target interface. One of
     *             {@link ConnectivityManager.TYPE_MOBILE} or
     *             {@link ConnectivityManager.TYPE_WIFI}. Do not track any other networks.
     * @throws IllegalStateException in case of failure, with an error code indicating the
     *         cause of the failure.
     * @throws NullPointerException if {@link Context.NETWORKMANAGEMENT_SERVICE} is unavailable
     *
     * @hide
     */
    @SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
    @RequiresPermission(anyOf = {
            NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
            android.Manifest.permission.NETWORK_STACK})
    public void addIdleTimer(@NonNull String iface, int timeout, int type) {
        try {
            getNMS().addIdleTimer(iface, timeout, type);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
    }

    /**
     * Remove idletimer for specific interface.
     *
     * @param iface Name of target interface
     * @throws IllegalStateException in case of failure, with an error code indicating the
     *         cause of the failure.
     * @throws NullPointerException if {@link Context.NETWORKMANAGEMENT_SERVICE} is unavailable
     *
     * @hide
     */
    @SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
    @RequiresPermission(anyOf = {
            NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
            android.Manifest.permission.NETWORK_STACK})
    public void removeIdleTimer(@NonNull String iface) {
        try {
            getNMS().removeIdleTimer(iface);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
    }
}
