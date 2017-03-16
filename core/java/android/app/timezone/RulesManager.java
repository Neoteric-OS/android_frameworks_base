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

import android.content.Context;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;

/**
 * The interface through which a time zone update application interacts with the Android system
 * to handle time zone rule updates.
 *
 * <p>This interface is intended for use with the default APK-based time zone rules update
 * application but it can also be used by OEMs if that mechanism is turned off using configuration.
 * All callers must possess the {@link android.Manifest.permission#UPDATE_TIME_ZONE_RULES} system
 * permission.
 *
 * <p>When using the default mechanism, when properly configured the Android system will send a
 * {@link RulesUpdaterContract#ACTION_TRIGGER_RULES_UPDATE_CHECK} intent with a
 * {@link RulesUpdaterContract#EXTRA_CHECK_TOKEN} extra to the time zone rules updater application
 * when it detects that it or the OEM's APK containing time zone rules data has been modified. The
 * updater application is then responsible for calling one of
 * {@link #requestInstall(ParcelFileDescriptor, byte[], Callback)},
 * {@link #requestUninstall(byte[], Callback)} or
 * {@link #requestNothing(byte[], boolean)}, indicating, respectively, whether a new time zone rules
 * distro should be installed, the current distro should be uninstalled, or there is nothing to do
 * (or that the correct operation could not be determined due to an error). In each case the updater
 * must pass the {@link RulesUpdaterContract#EXTRA_CHECK_TOKEN} value it received from the intent
 * back so the system in the {@code checkToken} parameter.
 *
 * <p>If OEMs want to handle their own time zone rules updates, perhaps via a server-side component
 * rather than an APK, then they should disable the default triggering mechanism in config and are
 * responsible for triggering their own update checks / installs / uninstalls. In this case the
 * "check token" parameter can be left null and there is never any need to call
 * {@link #requestNothing(byte[], boolean)}.
 *
 * <p>OEMs should not mix the default mechanism and their own as this could lead to conflicts and
 * unnecessary checks being triggered.
 *
 * <p>Applications obtain this using {@link android.app.Activity#getSystemService(String)} with
 * {@link Context#TIME_ZONE_RULES_MANAGER_SERVICE}.
 * @hide
 */
// TODO(nfuller): Expose necessary APIs for OEMs with @SystemApi. http://b/31008728
public final class RulesManager {
    private static final String TAG = "timezone.RulesManager";
    private static final boolean DEBUG = false;

    /**
     * Indicates that an operation succeeded.
     */
    public static final int SUCCESS = 0;

    /**
     * Indicates that an install/uninstall cannot be initiated because there is one already in
     * progress.
     */
    public static final int ERROR_OPERATION_IN_PROGRESS = 1;

    /**
     * Indicates an install / uninstall did not fully succeed for an unknown reason.
     */
    public final static int ERROR_UNKNOWN_FAILURE = 2;

    /**
     * Indicates an install failed because of a structural issue with the provided distro,
     * e.g. it wasn't in the right format or the contents were structured incorrectly.
     */
    public final static int ERROR_INSTALL_BAD_DISTRO_STRUCTURE = 3;

    /**
     * Indicates an install failed because of a versioning issue with the provided distro,
     * e.g. it was created for a different version of Android.
     */
    public final static int ERROR_INSTALL_BAD_DISTRO_FORMAT_VERSION = 4;

    /**
     * Indicates an install failed because the rules provided are too old for the device,
     * e.g. the Android device shipped with a newer rules version.
     */
    public final static int ERROR_INSTALL_RULES_TOO_OLD = 5;

    /**
     * Indicates an install failed because the distro contents failed validation.
     */
    public final static int ERROR_INSTALL_VALIDATION_ERROR = 6;

    private final Context mContext;
    private final IRulesManager mIRulesManager;

    public RulesManager(Context context) {
        mContext = context;
        mIRulesManager = IRulesManager.Stub.asInterface(
                ServiceManager.getService(Context.TIME_ZONE_RULES_MANAGER_SERVICE));
    }

    public RulesState getRulesState() {
        try {
            logDebug("sIRulesManager.getRulesState()");
            RulesState rulesState = mIRulesManager.getRulesState();
            logDebug("sIRulesManager.getRulesState() returned " + rulesState);
            return rulesState;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public int requestInstall(
            ParcelFileDescriptor distroFileDescriptor, byte[] checkToken, Callback installCallback)
            throws IOException {

        ICallback iCallback = new CallbackWrapper(mContext, installCallback);
        try {
            logDebug("sIRulesManager.requestInstall()");
            return mIRulesManager.requestInstall(distroFileDescriptor, checkToken, iCallback);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public int requestUninstall(byte[] checkToken, Callback uninstallCallback) {
        ICallback iCallback = new CallbackWrapper(mContext, uninstallCallback);
        try {
            logDebug("sIRulesManager.requestUninstall()");
            return mIRulesManager.requestUninstall(checkToken, iCallback);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /*
     * We wrap incoming binder calls with a private class implementation that
     * redirects them into main-thread actions.  This serializes the backup
     * progress callbacks nicely within the usual main-thread lifecycle pattern.
     */
    private class CallbackWrapper extends ICallback.Stub {
        final Handler mHandler;
        final Callback mCallback;

        CallbackWrapper(Context context, Callback callback) {
            mCallback = callback;
            mHandler = new Handler(context.getMainLooper());
        }

        // Binder calls into this object just enqueue on the main-thread handler
        @Override
        public void onFinished(int status) {
            logDebug("mCallback.onFinished(status), status=" + status);
            mHandler.post(() -> mCallback.onFinished(status));
        }
    }

    public void requestNothing(byte[] token, boolean succeeded) {
        try {
            logDebug("sIRulesManager.checkComplete() with token=" + Arrays.toString(token));
            mIRulesManager.requestNothing(token, succeeded);
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
