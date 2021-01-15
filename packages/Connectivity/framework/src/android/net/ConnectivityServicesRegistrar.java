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

import android.annotation.SystemApi;
import android.app.SystemServiceRegistry;
import android.content.Context;

/**
 * Connectivity service initializer for core networking. This is called by system server to create
 * a new instance of ConnectivityService.
 * @hide
 */
@SystemApi
public final class ConnectivityServicesRegistrar {
    private ConnectivityServicesRegistrar() {}

    public static void registerServiceWrappers() {
        SystemServiceRegistry.registerContextAwareService(
                Context.CONNECTIVITY_SERVICE,
                ConnectivityManager.class,
                (context, serviceBinder) -> {
                    IConnectivityManager icm = IConnectivityManager.Stub.asInterface(serviceBinder);
                    return new ConnectivityManager(context, icm);
                }
        );

        SystemServiceRegistry.registerContextAwareService(
                Context.VPN_MANAGEMENT_SERVICE,
                VpnManager.class,
                (context) -> {
                    final ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                            Context.CONNECTIVITY_SERVICE);
                    return cm.createVpnManager();
                }
        );

        SystemServiceRegistry.registerContextAwareService(
                Context.CONNECTIVITY_DIAGNOSTICS_SERVICE,
                ConnectivityDiagnosticsManager.class,
                (context) -> {
                    final ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                            Context.CONNECTIVITY_SERVICE);
                    return cm.createDiagnosticsManager();
                }
        );

        SystemServiceRegistry.registerContextAwareService(
                Context.TEST_NETWORK_SERVICE,
                TestNetworkManager.class,
                context -> {
                    final ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                            Context.CONNECTIVITY_SERVICE);
                    return cm.startOrGetTestNetworkManager();
                }
        );
    }
}
