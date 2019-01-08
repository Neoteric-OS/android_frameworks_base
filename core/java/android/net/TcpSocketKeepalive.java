/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.os.Binder;
import android.os.RemoteException;
import android.util.Log;

import java.io.FileDescriptor;
import java.net.Socket;
import java.util.concurrent.Executor;

/** @hide */
final class TcpSocketKeepalive extends SocketKeepalive {

    private final Socket mSocket;

    TcpSocketKeepalive(@NonNull IConnectivityManager service,
            @NonNull Network network,
            @NonNull Socket socket,
            @NonNull Executor executor,
            @NonNull Callback callback) {
        super(service, network, executor, callback);
        mSocket = socket;
    }

    /**
     * Starts keepalives. If this is a TCP socket, then:
     *
     * - The application must not write to or read from the socket after calling this method, until
     *   onDataReceived, onStopped, or onError are called. Otherwise the keepalive will fail
     *   with {@link #ERROR_SOCKET_NOT_IDLE}, or {@code #ERROR_INVALID_SOCKET} if the socket is
     *   entirely corrupted. Thus, data received from socket might not be valid, hence the
     *   application must close the socket and re-create new one.
     * - If the socket has data in the send or receive buffer, then this call will fail with
     *   {@link #ERROR_SOCKET_NOT_IDLE} and must be retried.
     */
    @Override
    void startImpl(int intervalSec) {
        try {
            final FileDescriptor fd = mSocket.getFileDescriptor$();
            mService.startTcpKeepalive(mNetwork, fd, intervalSec, mMessenger,  new Binder());
        } catch (RemoteException e) {
            Log.e(TAG, "Error starting packet keepalive: ", e);
            stopLooper();
        }
    }

    @Override
    void stopImpl() {
        try {
            if (mSlot != null) {
                mService.stopKeepalive(mNetwork, mSlot);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error stopping packet keepalive: ", e);
            stopLooper();
        }
    }
}
