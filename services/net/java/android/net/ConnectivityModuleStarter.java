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

import static android.net.NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK;
import static android.os.IServiceManager.DUMP_FLAG_PRIORITY_HIGH;
import static android.os.IServiceManager.DUMP_FLAG_PRIORITY_NORMAL;

import android.annotation.NonNull;
import android.content.Context;
import android.net.util.SharedLog;
import android.os.IBinder;
import android.os.ServiceManager;

import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;

/**
 * Class used to start Tethering and NetworkStack from {@link com.android.server.SystemServer}.
 * @hide
 */
public class ConnectivityModuleStarter {
    private static final String TAG = ConnectivityModuleStarter.class.getSimpleName();

    private static ConnectivityModuleStarter sInstance;

    @NonNull
    private final Dependencies mDependencies;

    private final SharedLog mLog = new SharedLog(TAG);

    @VisibleForTesting
    protected ConnectivityModuleStarter(@NonNull Dependencies dependencies) {
        mDependencies = dependencies;
    }

    private ConnectivityModuleStarter() {
        this(new DependenciesImpl());
    }

    @VisibleForTesting
    protected interface Dependencies {
        void addToServiceManager(@NonNull String name, @NonNull IBinder service);
        ConnectivityModuleConnector getConnectivityModuleConnector();
        void onServiceRegistered(@NonNull ConnectivityModuleCallback callback,
                @NonNull IBinder service);
    }

    private static class DependenciesImpl implements Dependencies {
        @Override
        public void addToServiceManager(@NonNull String name, @NonNull IBinder service) {
            ServiceManager.addService(name, service, false /* allowIsolated */,
                    DUMP_FLAG_PRIORITY_HIGH | DUMP_FLAG_PRIORITY_NORMAL);
        }

        @Override
        public ConnectivityModuleConnector getConnectivityModuleConnector() {
            return ConnectivityModuleConnector.getInstance();
        }

        @Override
        public void onServiceRegistered(@NonNull ConnectivityModuleCallback callback,
                @NonNull IBinder service) {
            callback.onServiceRegistered(service);
        }
    }

    /**
     * Get the ConnectivityModuleStarter singleton instance.
     */
    public static synchronized ConnectivityModuleStarter getInstance() {
        if (sInstance == null) {
            sInstance = new ConnectivityModuleStarter();
        }
        return sInstance;
    }

    /**
     * Start the network stack. Should be called only once on device startup.
     *
     * <p>This method will start the network stack either in the network stack process, or inside
     * the system server on devices that do not support the network stack module.
     */
    public void startNetworkStack() {
        mDependencies.getConnectivityModuleConnector().startModuleService(
                INetworkStackConnector.class.getName(), PERMISSION_MAINLINE_NETWORK_STACK,
                service -> {
                    mLog.i("Network stack service connected");
                    mDependencies.addToServiceManager(Context.NETWORK_STACK_SERVICE, service);
                    mDependencies.onServiceRegistered(NetworkStackClient.getInstance(), service);
                });
        mLog.log("Network stack service start requested");
    }

    /**
     * Start the tethering. Should be called only once on device startup.
     *
     * <p>This method will start the tethering either in the network stack process, or inside
     * the system server on devices that do not support the network stack module.
     */
    public void startTethering() {
        mDependencies.getConnectivityModuleConnector().startModuleService(
                ITetheringConnector.class.getName(), PERMISSION_MAINLINE_NETWORK_STACK,
                service -> {
                    mLog.i("Tethering service connected");
                    mDependencies.addToServiceManager(Context.TETHERING_SERVICE, service);
                });
        mLog.log("Tethering service start requested");
    }

    /** Dump logs. */
    public void dump(PrintWriter pw) {
        // dump is thread-safe on SharedLog
        mLog.dump(null, pw, null);
        // dump connectivity module connector logs.
        ConnectivityModuleConnector.getInstance().dump(pw);
    }
}
