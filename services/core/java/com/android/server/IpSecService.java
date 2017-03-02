/*
 * Copyright (C) 2007 The Android Open Source Project
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

import static android.Manifest.permission.DUMP;
import static android.Manifest.permission.SHUTDOWN;

import android.content.Context;
import android.net.IIpSecService;
import android.net.INetd;
import android.os.Binder;
import android.os.Handler;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;

/** @hide */
public class IpSecService extends IIpSecService.Stub implements Watchdog.Monitor {
    private static final String TAG = "IpSecService";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private static final String NETD_TAG = "NetdConnector";
    private static final String NETD_SERVICE_NAME = "netd";

    /** Binder context for this service */
    private final Context mContext;

    /** connector object for communicating with netd */
    private final NativeDaemonConnector mConnector;

    private final Handler mFgHandler;

    private INetd mNetdService;

    private final Thread mThread;
    private CountDownLatch mConnectedSignal = new CountDownLatch(1);

    /**
     * Constructs a new IpSecService instance
     *
     * @param context Binder context for this service
     */
    private IpSecService(Context context, String socket) {
        mContext = context;

        // make sure this is on the same looper as our NativeDaemonConnector for sync purposes
        mFgHandler = new Handler(FgThread.get().getLooper());

        mConnector =
                new NativeDaemonConnector(
                        new NetdCallbackReceiver(),
                        socket,
                        10,
                        NETD_TAG,
                        160,
                        null /*wakelock*/,
                        FgThread.get().getLooper());
        mThread = new Thread(mConnector, NETD_TAG);

        Watchdog.getInstance().addMonitor(this);
    }

    static IpSecService create(Context context, String socket) throws InterruptedException {
        final IpSecService service = new IpSecService(context, socket);
        final CountDownLatch connectedSignal = service.mConnectedSignal;
        if (DBG) Log.d(TAG, "Creating IpSecService");
        service.mThread.start();
        if (DBG) Log.d(TAG, "Awaiting socket connection");
        connectedSignal.await();
        if (DBG) Log.d(TAG, "Connected");
        service.connectNativeNetdService();
        return service;
    }

    public static IpSecService create(Context context) throws InterruptedException {
        return create(context, NETD_SERVICE_NAME);
    }

    public void systemReady() {
        if (DBG) {
            final long start = System.currentTimeMillis();
            prepareNativeDaemon();
            final long delta = System.currentTimeMillis() - start;
            Log.d(TAG, "Prepared in " + delta + "ms");
            return;
        } else {
            prepareNativeDaemon();
        }
    }

    private void connectNativeNetdService() {
        mNetdService = INetd.Stub.asInterface(ServiceManager.getService(NETD_SERVICE_NAME));

        if (!isNetdAlive()) {
            Log.wtf(TAG, "Can't connect to NativeNetdService " + NETD_SERVICE_NAME);
        }
    }

    /**
     * Prepare native daemon once connected, enabling modules and pushing any existing in-memory
     * rules.
     */
    private void prepareNativeDaemon() {}

    //
    // Netd Callback handling
    //

    private class NetdCallbackReceiver implements INativeDaemonConnectorCallbacks {
        @Override
        public void onDaemonConnected() {
            Log.i(TAG, "onDaemonConnected()");
            // event is dispatched from internal NDC thread, so we prepare the
            // daemon back on main thread.
            if (mConnectedSignal != null) {
                // The system is booting and we're connecting to netd for the first time.
                mConnectedSignal.countDown();
                mConnectedSignal = null;
            } else {
                // We're reconnecting to netd after the socket connection
                // was interrupted (e.g., if it crashed).
                mFgHandler.post(
                        new Runnable() {
                            @Override
                            public void run() {
                                connectNativeNetdService();
                                prepareNativeDaemon();
                            }
                        });
            }
        }

        @Override
        public boolean onCheckHoldWakeLock(int code) {
            return false;
        }

        @Override
        public boolean onEvent(int code, String raw, String[] cooked) {
            String errorMessage = String.format("Invalid event from daemon (%s)", raw);
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void monitor() {
        if (mConnector != null) {
            mConnector.monitor();
        }
    }

    @Override
    public void shutdown() {
        // TODO: remove from aidl if nobody calls externally
        mContext.enforceCallingOrSelfPermission(SHUTDOWN, TAG);

        Log.i(TAG, "Shutting down");
    }

    private static void enforceSystemUid() {
        final int uid = Binder.getCallingUid();
        if (uid != Process.SYSTEM_UID) {
            throw new SecurityException("Only available to AID_SYSTEM");
        }
    }

    boolean isNetdAlive() {
        if (mNetdService == null) {
            return false;
        }

        try {
            return mNetdService.isAlive();
        } catch (RemoteException re) {
            return false;
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mContext.enforceCallingOrSelfPermission(DUMP, TAG);

        pw.println("IpSecService Log:");
        pw.println("NetdNativeService Connection: " + (isNetdAlive() ? "alive" : "dead"));
        pw.println();
    }
}
