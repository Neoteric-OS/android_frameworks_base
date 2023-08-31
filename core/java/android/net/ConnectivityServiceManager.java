/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.annotation.SystemApi;
import android.annotation.SystemApi.Client;
import android.content.Context;
import android.os.IBinder;
import android.os.ServiceManager;

/**
 * Provides a way to register and obtain the system service binder objects managed by the Thread
 * network service in the connectivity/tethering mainline module.
 *
 * <p>Only the connectivity/tethering mainline module will be able to access an instance of this
 * class.
 *
 * <p>See packages/modules/Connectivity/thread for more information about Thread network service.
 *
 * @hide
 */
@SystemApi(client = Client.MODULE_LIBRARIES)
public class ConnectivityServiceManager {
    /** @hide */
    public ConnectivityServiceManager() {}

    /** A class that exposes the method to obtain each system service. */
    public static final class ServiceRegisterer {
        @NonNull private final String mServiceName;

        /** @hide */
        public ServiceRegisterer(@NonNull String serviceName) {
            mServiceName = serviceName;
        }

        /**
         * Returns the service from the service manager.
         *
         * If the service is not running, ServiceManager will attempt to start it.
         *
         * @return {@code null} only if there are permission problems or fatal errors
         */
        @Nullable
        public IBinder getService() {
            return ServiceManager.getService(mServiceName);
        }
    }

    /** Returns {@link ServiceRegisterer} for the "ot_daemon" service. */
    @NonNull
    public ServiceRegisterer getThreadDaemonServiceRegisterer() {
        return new ServiceRegisterer("ot_daemon");
    }
}
