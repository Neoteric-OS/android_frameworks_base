/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.app.timezone;

import android.annotation.SystemApi;
import android.content.Context;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * The interface through which a time zone update application interacts with the Android time zone
 * rules manager to handle rules updates.
 *
 * <p>Applications obtain this using {@link android.app.Activity#getSystemService(String)} with
 * {@link Context#TIME_ZONE_RULES_MANAGER_SERVICE}.
 * @hide
 */
@SystemApi
public final class RulesManager {
    static final String TAG = "RulesManager";
    static final boolean DEBUG = false;

    /**
     * Indicates that an operation succeeded.
     *
     * @hide
     */
    @SystemApi
    public static final int SUCCESS = 0;

    /**
     * Indicates that an install/uninstall cannot be initiated because there is one already in
     * progress.
     *
     * @hide
     */
    @SystemApi
    public static final int ERROR_OPERATION_IN_PROGRESS = 1;

    /**
     * Indicates a general failure associated with install/uninstallation operations.
     *
     * @hide
     */
    @SystemApi
    public static final int ERROR_GENERAL_FAILURE = 2;

    private Context mContext;
    private static IRulesManager sIRulesManager;

    private static void checkServiceBinder() {
        if (sIRulesManager == null) {
            logDebug("Getting sIRulesManager service");
            sIRulesManager = IRulesManager.Stub.asInterface(
                    ServiceManager.getService(Context.TIME_ZONE_RULES_MANAGER_SERVICE));
        }
    }

    /** @hide */
    public RulesManager(Context context) {
        mContext = context;
    }

    @SystemApi
    public RulesState getRulesState() {
        checkServiceBinder();
        try {
            logDebug("sIRulesManager.getRulesState()");
            RulesState rulesState = sIRulesManager.getRulesState();
            logDebug("sIRulesManager.getRulesState() returned " + rulesState);
            return rulesState;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @SystemApi
    public int requestInstall(File bundleFile, InstallOperationCallback installCallback)
            throws IOException {
        checkServiceBinder();
        // Convert the bundleFile to a ParcelFileHandle
        ParcelFileDescriptor bundleFileDescriptor =
                ParcelFileDescriptor.open(bundleFile, ParcelFileDescriptor.MODE_READ_ONLY);

        IInstallOperationCallback iCallback =
                new InstallOperationCallbackWrapper(mContext, installCallback);
        try {
            logDebug("sIRulesManager.requestInstall()");
            return sIRulesManager.requestInstall(bundleFileDescriptor, iCallback);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        } finally {
            // Tidy up bundleFileDescriptor. TODO Here or on callback or not needed?
            bundleFileDescriptor.close();
        }
    }

    @SystemApi
    public int requestUninstall(InstallOperationCallback uninstallCallback) {
        checkServiceBinder();
        IInstallOperationCallback iCallback =
                new InstallOperationCallbackWrapper(mContext, uninstallCallback);
        try {
            logDebug("sIRulesManager.requestUninstall()");
            return sIRulesManager.requestUninstall(iCallback);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /*
     * We wrap incoming binder calls with a private class implementation that
     * redirects them into main-thread actions.  This serializes the backup
     * progress callbacks nicely within the usual main-thread lifecycle pattern.
     */
    private class InstallOperationCallbackWrapper extends IInstallOperationCallback.Stub {
        final Handler mHandler;
        final InstallOperationCallback mInstallOperationCallback;

        InstallOperationCallbackWrapper(
                Context context, InstallOperationCallback installOperationCallback) {
            mInstallOperationCallback = installOperationCallback;
            mHandler = new Handler(context.getMainLooper());
        }

        // Binder calls into this object just enqueue on the main-thread handler
        @Override
        public void onFinished(int status) {
            logDebug("mInstallOperationCallback.onFinished(status), status=" + status);
            mHandler.post(() -> mInstallOperationCallback.onFinished(status));
        }
    }

    @SystemApi
    public void checkComplete(byte[] token, boolean succeeded) {
        checkServiceBinder();
        try {
            logDebug("sIRulesManager.checkComplete() with token=" + Arrays.toString(token));
            sIRulesManager.checkComplete(token, succeeded);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    static void logDebug(String msg) {
        if (DEBUG) {
            Log.v(TAG, msg);
        }
    }
}
