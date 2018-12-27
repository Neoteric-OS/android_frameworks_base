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

import android.net.IpSecManager.UdpEncapsulationSocket;
import android.os.Binder;
import android.os.RemoteException;
import android.util.Log;

import java.net.InetAddress;
import java.util.concurrent.Executor;

/** @hide */
public final class NattSocketKeepalive extends SocketKeepalive {
    /** The NAT-T destination port for IPsec */
    public static final int NATT_PORT = 4500;

    private final InetAddress mSource;
    private final InetAddress mDestination;
    private final UdpEncapsulationSocket mSocket;

    NattSocketKeepalive(IConnectivityManager service, Network network,
            UdpEncapsulationSocket socket, InetAddress source, InetAddress destination,
            Executor executor, Callback callback) {
        super(service, network, executor, callback);
        mSource = source;
        mDestination = destination;
        mSocket = socket;
    }

    @Override
    public void start(int intervalSec) {
        try {
            // TODO: Create new interface in ConnectivityService and pass fd to it.
            mService.startNattKeepalive(mNetwork, intervalSec, mMessenger, new Binder(),
                    mSource.getHostAddress(), mSocket.getPort(), mDestination.getHostAddress());
        } catch (RemoteException e) {
            Log.e(TAG, "Error starting packet keepalive: ", e);
            stopLooper();
        }
    }
}
