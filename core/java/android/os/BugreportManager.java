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

package android.os;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.content.Context;
import android.os.IBinder.DeathRecipient;
import android.util.Slog;

import java.io.FileDescriptor;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Service that provides a privileged API to capture and consume bugreports.
 *
 * @hide
 */
@SystemApi
@SystemService(Context.BUGREPORT_SERVICE)
public class BugreportManager {
    private static final String TAG = "BugreportManager";
    private static final long DEFAULT_BUGREPORT_SERVICE_TIMEOUT_MILLIS = 30 * 1000;

    private final Context mContext;

    /** @hide */
    public BugreportManager(@NonNull Context context) {
        mContext = context;
    }

    /**
     * An interface describing the listener for bugreport progress and status.
     */
    public interface BugreportListener {
        /**
         * Called when there is a progress update.
         * @param progress the progress in [0.0, 100.0]
         */
        void onProgress(float progress);

        @Retention(RetentionPolicy.SOURCE)
        @IntDef(prefix = { "BUGREPORT_ERROR_" }, value = {
                BUGREPORT_ERROR_INVALID_INPUT,
                BUGREPORT_ERROR_RUNTIME
        })

        /** Possible error codes taking a bugreport can encounter */
        @interface BugreportErrorCode {}

        /** The input options were invalid */
        int BUGREPORT_ERROR_INVALID_INPUT = 1;

        /** A runtime error occured */
        int BUGREPORT_ERROR_RUNTIME = 2;

        /**
         * Called when taking bugreport resulted in an error.
         *
         * @param errorCode the error that occurred. Possible values are
         *     {@code BUGREPORT_ERROR_INVALID_INPUT}, {@code BUGREPORT_ERROR_RUNTIME}.
         */
        void onError(@BugreportErrorCode int errorCode);

        /**
         * Called when taking bugreport finishes successfully
         *
         * @param durationMs time capturing bugreport took in milliseconds
         * @param title title for the bugreport; helpful in reminding the user why they took it
         * @param description detailed description for the bugreport
         */
        void onFinished(long durationMs, @NonNull String title,
                @NonNull String description);
    }

    /**
     * Starts a bugreport asynchronously.
     *
     * @param bugreportFd file to write the bugreport. This should be opened in write-only,
     *     append mode.
     * @param screenshotFd file to write the screenshot, if necessary. This should be opened
     *     in write-only, append mode.
     * @param params options that specify what kind of a bugreport should be taken
     * @param listener callback for progress and status updates
     */
    @RequiresPermission(android.Manifest.permission.DUMP)
    public void startBugreport(@NonNull FileDescriptor bugreportFd,
            @Nullable FileDescriptor screenshotFd,
            @NonNull BugreportParams params, @Nullable BugreportListener listener) {
        // TODO(b/111441001): Enforce android.Manifest.permission.DUMP if necessary.
        startBugreport(bugreportFd, screenshotFd, params.getMode(), listener);
    }

    private void startBugreport(@NonNull FileDescriptor bugreportFd,
            @Nullable FileDescriptor screenshotFd, @BugreportParams.BugreportMode int mode,
            @NonNull BugreportListener listener) {
        // TODO(b/111441001):
        // 1. Handle the case where another bugreport is in progress
        // 2. Make everything threadsafe
        // 3. Pass validation & other errors on listener
        validate(mode);

        // Start bugreport service.
        SystemProperties.set("ctl.start", "bugreport");
        IDumpstate ds = getService();
        if (ds == null) {
            Slog.w(TAG, "Unable to get bugreport service");
            // TODO(b/111441001): pass error on listener
            return;
        }
        try {
            DumpstateListener dsListener = new DumpstateListener(listener);
            ds.startBugreport(bugreportFd, screenshotFd, mode, dsListener);
        }  catch (RemoteException e) {
            Slog.w(TAG, "IDumpstate.startBugreport failed: " + e.getMessage());
        }
    }

    private boolean validate(@BugreportParams.BugreportMode int mode) {
        if (mode != BugreportParams.BUGREPORT_MODE_FULL
                && mode != BugreportParams.BUGREPORT_MODE_INTERACTIVE
                && mode != BugreportParams.BUGREPORT_MODE_REMOTE
                && mode != BugreportParams.BUGREPORT_MODE_WEAR
                && mode != BugreportParams.BUGREPORT_MODE_TELEPHONY
                && mode != BugreportParams.BUGREPORT_MODE_WIFI) {
            Slog.w(TAG, "Unknown bugreport mode: " + mode);
            return false;
        }
        return true;
    }

    private IDumpstate getService() {
        IDumpstate ds = null;
        boolean timedOut = false;
        int totalTimeWaitedMillis = 0;
        int seedWaitTimeMillis = 500;
        while (!timedOut) {
            ds = IDumpstate.Stub.asInterface(ServiceManager.getService("dumpstate"));
            if (ds != null) {
                Slog.i(TAG, "Got bugreport service handle.");
                break;
            }
            SystemClock.sleep(seedWaitTimeMillis);
            Slog.i(TAG,
                    "Waiting to get dumpstate service handle (" + totalTimeWaitedMillis + "ms)");
            totalTimeWaitedMillis += seedWaitTimeMillis;
            seedWaitTimeMillis *= 2;
            timedOut = totalTimeWaitedMillis > DEFAULT_BUGREPORT_SERVICE_TIMEOUT_MILLIS;
        }
        if (timedOut) {
            Slog.w(TAG,
                    "Timed out waiting to get dumpstate service handle ("
                    + totalTimeWaitedMillis + "ms)");
        }
        return ds;
    }

    // TODO(b/111441001) Connect up with BugreportListener methods.
    private final class DumpstateListener extends IDumpstateListener.Stub
            implements DeathRecipient {
        private final BugreportListener mListener;

        DumpstateListener(@Nullable BugreportListener listener) {
            mListener = listener;
        }

        @Override
        public void binderDied() {
            // TODO(b/111441001): implement
        }

        @Override
        public void onProgressUpdated(int progress) throws RemoteException {
            // TODO(b/111441001): implement
        }

        @Override
        public void onMaxProgressUpdated(int maxProgress) throws RemoteException {
            // TODO(b/111441001): implement
        }

        @Override
        public void onSectionComplete(String title, int status, int size, int durationMs)
                throws RemoteException {
            // TODO(b/111441001): implement
        }
    }
}
