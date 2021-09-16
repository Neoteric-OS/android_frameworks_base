/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.hdmi;

import static com.android.server.hdmi.Constants.DISABLED;
import static com.android.server.hdmi.Constants.ENABLED;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.SystemService;
import com.android.server.hdmi.HdmiAnnotations.ServiceThreadOnly;
import com.android.server.LocalServices;

import libcore.util.EmptyArray;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public class HdmiAudioService extends SystemService {
    private static final String TAG = "HdmiAudioService";

    // Handler used to run a task in service thread.
    private final Handler mHandler = new Handler();

     @Nullable
    private Looper mIoLooper;

    // A thread to handle synchronous IO of EARC control service.
    private final HandlerThread mIoThread = new HandlerThread("Hdmi Audio Io Thread");

    private final SettingsObserver mSettingsObserver;

    @Nullable
    private HdmiEarcController mEarcController;

    // Used to synchronize the access to the service.
    private final Object mLock = new Object();

    // Set to true while chip supports EARC.
    @GuardedBy("mLock")
    private boolean mEarcSupported;

    // Set to true while EARC feature is enabled.
    @GuardedBy("mLock")
    private boolean mEarcControlFeatureEnabled;

    // Indicate current eARC status.
    @GuardedBy("mLock")
    private int mEarcStatus;

    //Set eARC port
    @GuardedBy("mLock")
    private int mEarcPortId = Constants.INVALID_PORT_ID;

    public HdmiAudioService(Context context) {
        super(context);
        mSettingsObserver = new SettingsObserver(mHandler);
    }

    @Override
    public void onStart() {
        initService();
        if (mEarcController != null) {
            // Register ContentObserver to monitor the settings change.
            registerContentObserver();
        }
    }

    private void registerContentObserver() {
        ContentResolver resolver = getContext().getContentResolver();
        String[] settings = new String[] {
                Global.HDMI_EARC_CONTROL_ENABLED
        };
        for (String s : settings) {
            resolver.registerContentObserver(Global.getUriFor(s), false, mSettingsObserver,
                    UserHandle.USER_ALL);
        }
    }

    @VisibleForTesting
    void initService() {
        if (mIoLooper == null) {
            mIoThread.start();
            mIoLooper = mIoThread.getLooper();
        }

        mEarcController = HdmiEarcController.create(this);
        mEarcControlFeatureEnabled = readBooleanSetting(Global.HDMI_EARC_CONTROL_ENABLED, true);
        if (mEarcController != null) {
            mEarcSupported = isEarcSupported();
            Slog.i(TAG, "mEarcSupported:" + mEarcSupported);
            if (mEarcSupported) {
                Slog.i(TAG, "mEarcControlFeatureEnabled:" + mEarcControlFeatureEnabled);
                setEarcControlFeatureEnabled(mEarcControlFeatureEnabled);
            }
        } else {
            Slog.i(TAG, "Device does not support HDMI eARC.");
            return;
        }
    }

    void setEarcControlFeatureEnabled(boolean enabled) {
        assertRunOnServiceThread();
        synchronized (mLock) {
            mEarcControlFeatureEnabled = enabled;
            Slog.i(TAG,"set mEarcControlFeatureEnabled:"+ enabled);
            if (enabled) {
                mEarcController.controlFeature(Constants.PREFER_EARC);
            } else {
                mEarcController.controlFeature(Constants.ARC_ONLY);
            }
        }
        return;
    }

    boolean isEarcControlFeatureEnabled() {
        synchronized (mLock) {
            return mEarcControlFeatureEnabled;
        }
    }

    private void notifyEarcStatusToAudioService(boolean enabled) {
        Slog.i(TAG,"notifyEarcStatusToAudioService: "+enabled);
        getAudioManager().setWiredDeviceConnectionState(
                AudioManager.DEVICE_OUT_HDMI_EARC,
                enabled ? 1 : 0, "", "");
    }

    /**
     * Returns eARC status of system.
     */
    int getEarcStatus() {
        return mEarcController.getStatus(mEarcPortId);
    }

    /**
     * Returns eARC status of system.
     */
    boolean isEarcSupported() {
        return mEarcController.isSupported();
    }

    AudioManager getAudioManager() {
        return (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
    }

    /**
    * Called when a new eARC status change event is issued.
    *
    * @param earcStatus indicate system's eARC status
    */
    @ServiceThreadOnly
    void onEarcStatus(int earcStatus) {
        assertRunOnServiceThread();
        Slog.i(TAG, "eARC status event, old:[" + mEarcStatus + "], new:[" + earcStatus + "]");
        mEarcStatus = earcStatus;
        Global.putInt(getContext().getContentResolver(),
                    Global.HDMI_EARC_CONNECTED, earcStatus);
        switch (earcStatus) {
            case Constants.HDMI_EARC_ENABLED:
                Slog.d(TAG, "eARC Connected.");
                notifyEarcStatusToAudioService(true);
                break;
            case Constants.HDMI_EARC_NOT_ENABLED:
                Slog.d(TAG, "eARC Disconnected.");
                notifyEarcStatusToAudioService(false);
                break;
            case Constants.HDMI_EARC_IDLE:
                Slog.d(TAG, "eARC Disconnected.");
                notifyEarcStatusToAudioService(false);
            default:
                Slog.w(TAG, "Do not handle this. eARC Status:" + earcStatus);
                return;
        }
    }

    private class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        // onChange is set up to run in service thread.
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            String option = uri.getLastPathSegment();
            boolean enabled = readBooleanSetting(option, true);
            switch (option) {
                case Global.HDMI_EARC_CONTROL_ENABLED:
                    Slog.i(TAG, "eARC control enabled changed:" + enabled);
                    setEarcControlFeatureEnabled(enabled);
                    break;
            }
        }
    }

    boolean readBooleanSetting(String key, boolean defVal) {
        ContentResolver cr = getContext().getContentResolver();
        return Global.getInt(cr, key, toInt(defVal)) == ENABLED;
    }

    private static int toInt(boolean enabled) {
        return enabled ? ENABLED : DISABLED;
    }

    @ServiceThreadOnly
    void onEarcAudioLatency(int flag) {
        assertRunOnServiceThread();
        HdmiLogger.debug("Audio Svc onEarcAudioLatency: %d", flag);
    }

    @ServiceThreadOnly
    void onEarcCaps(int flag) {
        assertRunOnServiceThread();
        HdmiLogger.debug("Audio Svc onEarcAudioCaps: %d", flag);
    }

    @VisibleForTesting
    protected Looper getServiceLooper() {
        return mHandler.getLooper();
    }

    /**
     * Returns {@link Looper} for IO operation.
     */
    @Nullable
    @VisibleForTesting
    protected Looper getIoLooper() {
        return mIoLooper;
    }

    @VisibleForTesting
    void setIoLooper(Looper ioLooper) {
        mIoLooper = ioLooper;
    }

    private void assertRunOnServiceThread() {
        if (Looper.myLooper() != mHandler.getLooper()) {
            throw new IllegalStateException("Should run on service thread.");
        }
    }
}
