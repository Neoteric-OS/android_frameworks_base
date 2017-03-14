/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.net.util;

import android.net.INetd;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.util.Log;


/**
 * @hide
 */
public class NetdService {
    private static final String TAG = NetdService.class.getSimpleName();
    private static final String NETD_SERVICE_NAME = "netd";
    private static final int BASE_TIMEOUT_MS = 100;
    private static final int MAX_TIMEOUT_MS = 1000;


    /**
     * Return an INetd instance, or null if not available.
     *
     * It is the caller's responsibility to check for a null return value
     * and to handle RemoteException errors from invocations on the returned
     * interface if, for example, netd dies and is restarted.
     *
     * Returned instances of INetd should not be cached.
     *
     * @return an INetd instance or null.
     */
    public static INetd getInstance() {
        // NOTE: ServiceManager does no caching for the netd service,
        // because netd is not one of the defined common services.
        final INetd netdInstance = INetd.Stub.asInterface(
                ServiceManager.getService(NETD_SERVICE_NAME));
        if (netdInstance == null) {
            Log.w(TAG, "WARNING: returning null INetd instance.");
        }
        return netdInstance;
    }

    /**
     * Blocks until an INetd instance is available.
     *
     * It is the caller's responsibility to handle RemoteException errors
     * from invocations on the returned interface if, for example, netd
     * dies after this interface was returned.
     *
     * Returned instances of INetd should not be cached.
     *
     * @return an INetd instance or null.
     */
    public static INetd get() {
        for (int i = 0; ; i++) {
            final INetd netdInstance = getInstance();
            if (netdInstance != null) {
                return netdInstance;
            }

            // No netdInstance was received; sleep and retry.
            final int timeoutMs = (i < (MAX_TIMEOUT_MS / BASE_TIMEOUT_MS))
                    ? (i * BASE_TIMEOUT_MS)
                    : MAX_TIMEOUT_MS;
            try {
                Thread.sleep(timeoutMs);
            } catch (InterruptedException e) {}
        }
    }

    public static interface NetdCommand {
        void run(INetd netd) throws RemoteException, ServiceSpecificException;
    }

    /**
     * Blocks until an INetd instance is availabe, and retries until either
     * the command succeeds or a ServiceSpecificError is thrown.
     */
    public static void run(NetdCommand cmd) {
        while (true) {
            try {
                cmd.run(get());
                return;
            } catch (RemoteException re) {}
        }
    }
}
