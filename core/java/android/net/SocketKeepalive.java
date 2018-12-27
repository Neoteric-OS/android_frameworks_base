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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.util.concurrent.Executor;

/**
 * Allows applications to request that the system periodically send specific packets on their
 * behalf, using hardware offload to save battery power.
 *
 * To request that the system send keepalives, call one of the methods that return a
 * {@link SocketKeepalive} object, such as {@link ConnectivityManager#makeSocketKeepalive},
 * passing in a non-null callback. If the {@link SocketKeepalive.Callback} is successfully
 * started, the callback's {@code onStarted} method will be called. If an error occurs,
 * {@code onError} will be called, specifying one of the {@code ERROR_*} constants in this
 * class.
 *
 * To stop an existing keepalive, call {@link SocketKeepalive#stop}. The system will call
 * {@link SocketKeepalive.Callback#onStopped} if the operation was successful or
 * {@link SocketKeepalive.Callback#onError} if an error occurred.
 */
public abstract class SocketKeepalive implements AutoCloseable {
    static final String TAG = "SocketKeepalive";

    /** @hide */
    public static final int SUCCESS = 0;

    /** @hide */
    public static final int NO_KEEPALIVE = -1;

    /** @hide */
    public static final int DATA_RECEIVED = -2;

    /** @hide */
    public static final int BINDER_DIED = -10;

    /** The specified {@code Network} is not connected. */
    public static final int ERROR_INVALID_NETWORK = -20;
    /** The specified IP addresses are invalid. For example, the specified source IP address is
     * not configured on the specified {@code Network}. */
    public static final int ERROR_INVALID_IP_ADDRESS = -21;
    /** The requested port is invalid. */
    public static final int ERROR_INVALID_PORT = -22;
    /** The packet length is invalid (e.g., too long). */
    public static final int ERROR_INVALID_LENGTH = -23;
    /** The packet transmission interval is invalid (e.g., too short). */
    public static final int ERROR_INVALID_INTERVAL = -24;
    /** The target socket is invalid. */
    public static final int ERROR_INVALID_SOCKET = -25;
    /** The target socket is not idle. */
    public static final int ERROR_SOCKET_NOT_IDLE = -26;

    /** The hardware does not support this request. */
    public static final int ERROR_HARDWARE_UNSUPPORTED = -30;
    /** The hardware returned an error. */
    public static final int ERROR_HARDWARE_ERROR = -31;

    /** The minimum interval in seconds between keepalive packet transmissions. */
    public static final int MIN_INTERVAL = 10;

    final IConnectivityManager mService;
    final Network mNetwork;
    final Executor mExecutor;
    final SocketKeepalive.Callback mCallback;
    final Looper mLooper;
    final Messenger mMessenger;

    volatile Integer mSlot;


    SocketKeepalive(IConnectivityManager service, Network network, Executor executor,
            Callback callback) {
        mService = service;
        mNetwork = network;
        mExecutor = executor;
        mCallback = callback;
        HandlerThread thread = new HandlerThread(TAG);
        thread.start();
        mLooper = thread.getLooper();
        mMessenger = new Messenger(new Handler(mLooper) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case NetworkAgent.EVENT_PACKET_KEEPALIVE:
                        int error = message.arg2;
                        try {
                            if (error == SUCCESS) {
                                if (mSlot == null) {
                                    mSlot = message.arg1;
                                    mExecutor.execute(() -> {
                                        mCallback.onStarted();
                                    });
                                } else {
                                    mSlot = null;
                                    stopLooper();
                                    mExecutor.execute(() -> {
                                        mCallback.onStopped();
                                    });
                                }
                            } else if (error == DATA_RECEIVED) {
                                stopLooper();
                                mExecutor.execute(() -> {
                                    mCallback.onDataReceived();
                                });
                            } else {
                                stopLooper();
                                mExecutor.execute(() -> {
                                    mCallback.onError(error);
                                });
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Exception in keepalive callback(" + error + ")", e);
                        }
                        break;
                    default:
                        Log.e(TAG, "Unhandled message " + Integer.toHexString(message.what));
                        break;
                }
            }
        });
    }

    /**
     * Starts keepalives. If this is a TCP socket, then:
     *
     * - The application must not write to or read from the socket after calling this method,
     *   until {@link Callback#onDataReceived}, {@link Callback#onStopped}, or
     *   {@link Callback#onError} are called.
     * - If the socket has data in the send or receive buffer, then this call will fail with
     *   {@code ERROR_SOCKET_NOT_IDLE} and must be retried.
     *
     * The first packet is sent at any time between now and intervalSec seconds from when onStarted
     * is called.
     */
    public abstract void start(int intervalSec);

    void stopLooper() {
        mLooper.quit();
    }

    /**
     * Requests that keepalive be stopped. Application must wait for {@link Callback#onStopped}
     * before using object.
     */
    public void stop() {
        try {
            if (mSlot != null) {
                mService.stopKeepalive(mNetwork, mSlot);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error stopping packet keepalive: ", e);
            stopLooper();
        }
    }

    /**
     * Deactivate this {@link SocketKeepalive} and free allocated resources. The instance won't be
     * usable again if {@close()} is called.
     */
    @Override
    public void close() {
        stop();
        stopLooper();
    }

    /**
     * The callback which app can use to learn the status changes of {@link SocketKeepalive}. See
     * {@link SocketKeepalive}.
     */
    public static class Callback {
        /** The requested keepalive was successfully started. */
        public void onStarted() {}
        /** The keepalive was successfully stopped. */
        public void onStopped() {}
        /** An error occurred. */
        public void onError(int error) {}
        /** The keepalive on a TCP socket was stopped because the socket received data. */
        public void onDataReceived() {}
    }
}
