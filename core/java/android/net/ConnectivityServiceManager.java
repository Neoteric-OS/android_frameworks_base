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
import android.annotation.SystemApi;
import android.annotation.SystemApi.Client;
import android.content.Context;
import android.os.IBinder;
import android.os.ServiceManager;

import java.util.function.Supplier;

/**
 * Provides a way to register and obtain the system service binder objects managed by the
 * connectivity/tethering mainline module.
 *
 * @hide
 */
public class ConnectivityServiceManager {
    private ConnectivityServiceManager() {}

    /** Returns a {@link Supplier} for the Thread daemon binder service (i.e. "ot_daemon"). */
    @NonNull
    public static Supplier<IBinder> getThreadDaemonServiceSupplier() {
        return  () -> ServiceManager.waitForService("ot_daemon");
    }
}
