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
import android.annotation.Nullable;
import android.net.dhcp.ConnectivityModuleConnector;
import android.net.dhcp.DhcpServingParamsParcel;
import android.net.dhcp.IDhcpServerCallbacks;
import android.net.util.SharedLog;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;

/**
 * Service used to communicate with the network stack, which is running in a separate module.
 * @hide
 */
public class TetheringIpClient extends NetworkStackClientBase {
    private static final String TAG = NetworkStackClient.class.getSimpleName();

    private static TetheringIpClient sInstance;

    @VisibleForTesting
    protected TetheringIpClient(@NonNull Dependencies dependencies) {
        super(dependencies, new SharedLog(TAG));
    }

    private TetheringIpClient() {
        this(makeDependencies());
    }

    private static class DependenciesImpl implements Dependencies {
        @Override
        public void addToServiceManager(@NonNull IBinder service) {
            // Expect service is added by NetworkStackClient in system server.
        }

        @Override
        public void checkCallerUid() {
            // TetheringIpClient should only be used for Tethering module.
        }

        @Override
        public ConnectivityModuleConnector getConnectivityModuleConnector() {
            return ConnectivityModuleConnector.getInstance();
        }
    }

    /**
     * Get the NetworkStackClient singleton instance.
     */
    public static synchronized NetworkStackClient getInstance() {
        if (sInstance == null) {
            sInstance = new NetworkStackClient();
        }
        return sInstance;
    }

    /**
     * Create a DHCP server according to the specified parameters.
     *
     * <p>The server will be returned asynchronously through the provided callbacks.
     */
    public void makeDhcpServer(final String ifName, final DhcpServingParamsParcel params,
            final IDhcpServerCallbacks cb) {
        requestConnector(connector -> {
            try {
                connector.makeDhcpServer(ifName, params, cb);
            } catch (RemoteException e) {
                e.rethrowFromSystemServer();
            }
        });
    }

    /**
     * Log a message in the local log.
     */
    private void log(@NonNull String message) {
        synchronized (mLog) {
            mLog.log(message);
        }
    }

    private void logWtf(@NonNull String message, @Nullable Throwable e) {
        Slog.wtf(TAG, message);
        synchronized (mLog) {
            mLog.e(message, e);
        }
    }

    private void loge(@NonNull String message, @Nullable Throwable e) {
        synchronized (mLog) {
            mLog.e(message, e);
        }
    }

    /**
     * Log a message in the local and system logs.
     */
    private void logi(@NonNull String message) {
        synchronized (mLog) {
            mLog.i(message);
        }
    }

    /**
     * Dump NetworkStackClient logs to the specified {@link PrintWriter}.
     */
    public void dump(PrintWriter pw) {
        // dump is thread-safe on SharedLog
        mLog.dump(null, pw, null);
        // dump connectivity module connector logs.
        ConnectivityModuleConnector.getInstance().dump(pw);

        super.dump(pw);
    }
}
