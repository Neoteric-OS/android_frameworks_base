/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.net.dhcp.DhcpServingParamsParcel;
import android.net.dhcp.IDhcpServerCallbacks;
import android.os.RemoteException;

/**
 * Service used to communicate with the network stack, which is running in a separate module.
 * @hide
 */
public class NetworkStackConnector {
    @NonNull
    private final INetworkStackConnector mConnector;

    public NetworkStackConnector(@NonNull INetworkStackConnector connector) {
        mConnector = connector;
    }

    /**
     * Create a DHCP server according to the specified parameters.
     *
     * <p>The server will be returned asynchronously through the provided callbacks.
     */
    public void makeDhcpServer(String ifName, DhcpServingParamsParcel params,
            IDhcpServerCallbacks cb) {
        try {
            mConnector.makeDhcpServer(ifName, params, cb);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
    }
}
