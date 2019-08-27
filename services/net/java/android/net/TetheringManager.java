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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.util.SharedLog;
import android.os.IBinder;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;

/**
 * Service used to communicate with the tethering, which is running in a separate module.
 * @hide
 */
public class TetheringManager {
    private static final String TAG = TetheringManager.class.getSimpleName();

    private static TetheringManager sInstance;

    @Nullable
    private ITetheringConnector mConnector;

    @GuardedBy("mLog")
    private final SharedLog mLog = new SharedLog(TAG);

    private TetheringManager() { }

    /**
     * Get the TetheringManager singleton instance.
     */
    public static synchronized TetheringManager getInstance() {
        if (sInstance == null) {
            sInstance = new TetheringManager();
        }
        return sInstance;
    }

    private class TetheringConnection implements
            ConnectivityModuleConnector.ModuleServiceCallback {
        @Override
        public void onModuleServiceConnected(@NonNull IBinder service) {
            logi("Tethering service connected");
            registerTetheringService(service);
        }
    }

    private void registerTetheringService(@NonNull IBinder service) {
        final ITetheringConnector connector = ITetheringConnector.Stub.asInterface(service);

        log("Tethering service registered");

        // Currently TetheringManager instance is only used by ConnectivityService and mConnector
        // only expect to assgin once when system server start and bind tethering service.
        // TODO: Change mConnector to final before TetheringManager put into boot classpath.
        mConnector = connector;
    }

    /**
     * Start the tethering service. Should be called only once on device startup.
     *
     * <p>This method will start the tethering service either in the network stack process,
     * or inside the system server on devices that do not support the tethering module.
     */
    public void start() {
        ConnectivityModuleConnector.getInstance().startModuleService(
                ITetheringConnector.class.getName(), PERMISSION_MAINLINE_NETWORK_STACK,
                new TetheringConnection());
        log("Tethering service start requested");
    }

    /**
     * Log a message in the local log.
     */
    private void log(@NonNull String message) {
        synchronized (mLog) {
            mLog.log(message);
        }
    }

    /**
     * Log a condition that should never happen.
     */
    private void logWtf(@NonNull String message, @Nullable Throwable e) {
        Slog.wtf(TAG, message);
        synchronized (mLog) {
            mLog.e(message, e);
        }
    }

    /**
     * Log a ERROR level message in the local and system logs.
     */
    private void loge(@NonNull String message, @Nullable Throwable e) {
        synchronized (mLog) {
            mLog.e(message, e);
        }
    }

    /**
     * Log a INFO level message in the local and system logs.
     */
    private void logi(@NonNull String message) {
        synchronized (mLog) {
            mLog.i(message);
        }
    }

    /**
     * Dump TetheringManager logs to the specified {@link PrintWriter}.
     */
    public void dump(PrintWriter pw) {
        // dump is thread-safe on SharedLog
        mLog.dump(null, pw, null);

        pw.println();
    }
}
