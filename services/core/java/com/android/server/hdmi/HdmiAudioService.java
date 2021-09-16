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
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiTvClient;
import android.hardware.hdmi.HdmiTvClient.SelectCallback;
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
import java.util.Arrays;
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
    private int mEarcControlFeatureEnabled;

    // Indicate current eARC status.
    @GuardedBy("mLock")
    private int mEarcStatus;

    // Indicate current eARC Audio Latency.
    @GuardedBy("mLock")
    private int mEarcLatency;

    //Set eARC port
    @GuardedBy("mLock")
    private int mEarcPortId = Constants.INVALID_PORT_ID;

    // Indicate current eARC SAD Length.
    @GuardedBy("mLock")
    private int mEarcSadLen = 0;

    // Indicate current eARC SADB Length.
    @GuardedBy("mLock")
    private int mEarcSadbLen = 0;

    // Indicate current eARC VSADB Length.
    @GuardedBy("mLock")
    private int mEarcVsadbLen = 0;

    // eARC capability format
    private final int EARC_CAPS_VERSION = 0x00;
    private final int EARC_CAPS_BLOCK_ID = 0x01;
    private final int EARC_CAPS_PAYLOAD_LENGTH = 0x02;
    private final int EARC_CAPS_DATA_START = 0x03;

    // Table 55 CTA Data Block Tag Codes
    private final int TAGCODE_AUDIO_DATA_BLOCK = 0x01;  //Includes one or more Short Audio Descriptors
    private final int TAGCODE_SADB_DATA_BLOCK = 0x04;   //Speaker Allocation Data Block
    private final int TAGCODE_USE_EXTENEDED_TAG = 0x07; //Use Extended Tag

    // Table 56 Extended Tag Format (2nd Byte of Data Block)
    private final int EXTENDED_TAGCODE_VSADB = 0x11;    //Vendor-Specific Audio Data Block

    // eARC capability mask and shift
    private final int EARC_CAPS_TAGCODE_MASK = 0xE0;
    private final int EARC_CAPS_TAGCODE_SHIFT = 0x05;
    private final int EARC_CAPS_LENGTH_MASK = 0x1F;

    private byte[] mEarcSad;
    private byte[] mEarcSadb;
    private byte[] mEarcVsadb;


    private HdmiControlManager mHdmiControlManager = null;

    public HdmiAudioService(Context context) {
        super(context);
        mSettingsObserver = new SettingsObserver(mHandler);
        mHdmiControlManager = (HdmiControlManager) context.getSystemService(Context.HDMI_CONTROL_SERVICE);
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
        mEarcControlFeatureEnabled = readIntSetting(Global.HDMI_EARC_CONTROL_ENABLED, Constants.PREFER_EARC);
        if (mEarcController != null) {
            mEarcSupported = isEarcSupported();
            Slog.i(TAG, "mEarcSupported:" + mEarcSupported);
            if (mEarcSupported) {
                Slog.i(TAG, "mEarcControlFeatureEnabled:" + mEarcControlFeatureEnabled);
                mEarcPortId = getPortId();
                Slog.i(TAG, "mEarcPortId:" + mEarcPortId);
                setEarcControlFeatureEnabled(mEarcControlFeatureEnabled);
            }
        } else {
            Slog.i(TAG, "Device does not support HDMI eARC.");
            return;
        }
    }

    void setEarcControlFeatureEnabled(int eArcControl) {
        assertRunOnServiceThread();
        synchronized (mLock) {
            mEarcControlFeatureEnabled = eArcControl;
            Slog.i(TAG,"set mEarcControlFeatureEnabled:"+ eArcControl);
            mEarcController.controlFeature(eArcControl);
        }
        return;
    }

    boolean isEarcControlFeatureEnabled() {
        synchronized (mLock) {
            if (mEarcControlFeatureEnabled == Constants.PREFER_EARC) {
                return true;
            }
            return false;
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
     * Returns eARC capability of eARC device.
     */
    byte[] getEarcCapability() {
        return mEarcController.getCapability(mEarcPortId);
    }

    /**
     * Returns eARC Audio Latency of eARC device.
     */
    int getEarcLatency() {
        return mEarcController.getLatency(mEarcPortId);
    }

    /**
     * Returns if chip support eARC.
     */
    boolean isEarcSupported() {
        return mEarcController.isSupported();
    }

    /**
     * Returns eARC status of system.
     */
    int getPortId() {
        return mEarcController.getPortId();
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
                clearEarcCaps();
                notifyEarcStatusToAudioService(false);
                break;
            case Constants.HDMI_EARC_IDLE:
                Slog.d(TAG, "eARC Disconnected.");
                clearEarcCaps();
                notifyEarcStatusToAudioService(false);
                break;
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
            switch (option) {
                case Global.HDMI_EARC_CONTROL_ENABLED:
                    int earcControl = readIntSetting(option, Constants.PREFER_EARC);
                    Slog.i(TAG, "eARC control enabled changed:" + earcControl);
                    // Disable ARC before system enables EARC
                    if ((earcControl == Constants.PREFER_EARC)
                                && mHdmiControlManager != null) {
                        HdmiTvClient hdmiTvClient = mHdmiControlManager.getTvClient();
                        if (hdmiTvClient != null) {
                            hdmiTvClient.setSystemAudioMode(false, new SelectCallback() {
                                @Override
                                public void onComplete(int result) {
                                    Slog.i(TAG, "setSystemAudioMode returned " + result);
                                    setEarcControlFeatureEnabled(earcControl);
                                }
                            });
                        } else {
                            Slog.i(TAG, "Get hdmiTvClient failed");
                        }
                    } else {
                        setEarcControlFeatureEnabled(earcControl);
                    }
                    break;
            }
        }
    }

    int readIntSetting(String key, int defVal) {
        ContentResolver cr = getContext().getContentResolver();
        return Global.getInt(cr, key, defVal);
    }

    boolean readBooleanSetting(String key, boolean defVal) {
        ContentResolver cr = getContext().getContentResolver();
        return Global.getInt(cr, key, toInt(defVal)) == ENABLED;
    }

    private static int toInt(boolean enabled) {
        return enabled ? ENABLED : DISABLED;
    }

    void clearEarcCaps() {
        String keyValue = Constants.AUDIO_PARAMETER_AVR_CAPS;
        byte[] buf = new byte[4];
        mEarcSad = null;
        mEarcSadb = null;
        mEarcVsadb = null;
        buf[0] = (byte) 0x00;
        buf[1] = (byte) 0x00;
        buf[2] = (byte) 0x00;
        buf[3] = (byte) 0x00;
        keyValue += Arrays.toString(buf);
        keyValue += "[][]";
        getAudioManager().setParameters(keyValue);
    }

    /**
    * Parse eARC Capabilities as spec. and pass SAD/SADB/VSADB to Audio.
    *
    * @param earcCaps indicates all raw eARC capabilities.
    */
    String parseEarcCaps(byte[] earcCaps) {
        int earcCapsSize = earcCaps[EARC_CAPS_PAYLOAD_LENGTH];
        int i = EARC_CAPS_DATA_START;
        String result = null;
        HdmiLogger.debug( "earcCapsSize:%d", i, earcCapsSize);
        mEarcSad = null;
        mEarcSadb = null;
        mEarcVsadb = null;
        while (i < earcCapsSize) {
            // Tag Code : Bit 5-7
            int tagCode = (earcCaps[i] & EARC_CAPS_TAGCODE_MASK) >> EARC_CAPS_TAGCODE_SHIFT ;
            // Length : Bit 0-4
            int length = earcCaps[i] & EARC_CAPS_LENGTH_MASK;
            HdmiLogger.debug( "earcCaps[%d]: 0x%x", i, earcCaps[i]);
            HdmiLogger.debug( "tagCode:0x%x, length: %d", tagCode, length);
            switch (tagCode) {
                case TAGCODE_AUDIO_DATA_BLOCK:
                    mEarcSadLen = length;
                    Slog.i(TAG, "mEarcSadLen:"+ mEarcSadLen);
                    mEarcSad = new byte[mEarcSadLen];
                    System.arraycopy(earcCaps, ++i, mEarcSad, 0, mEarcSadLen);
                    for (int j=0; j<mEarcSadLen; j++) {
                        HdmiLogger.debug( "SAD[%d]:0x%x", j, mEarcSad[j]);
                    }
                    break;
                case TAGCODE_SADB_DATA_BLOCK:
                    mEarcSadbLen = length;
                    Slog.i(TAG, "mEarcSadbLen:"+ mEarcSadbLen);
                    mEarcSadb = new byte[mEarcSadbLen + 1];
                    System.arraycopy(earcCaps, i++, mEarcSadb, 0, (mEarcSadbLen + 1));
                    for (int j=0; j<mEarcSadbLen+1; j++) {
                        HdmiLogger.debug( "SADB[%d]:0x%x", j, mEarcSadb[j]);
                    }
                    break;
                case TAGCODE_USE_EXTENEDED_TAG:
                    if (earcCaps[i + 1] == EXTENDED_TAGCODE_VSADB) {
                        mEarcVsadbLen = length;
                        Slog.i(TAG, "mEarcVsadbLen:"+ mEarcVsadbLen);
                        mEarcVsadb = new byte[mEarcVsadbLen + 1];
                        System.arraycopy(earcCaps, i++, mEarcVsadb, 0, (mEarcVsadbLen + 1));
                        for (int j=0; j<mEarcVsadbLen+1; j++) {
                            HdmiLogger.debug( "VSADB[%d]:0x%x", j, mEarcVsadb[j]);
                        }
                    }
                    break;
                default:
                    Slog.i(TAG, "Do not handle this tagCode:"+ tagCode);
                    break;
            }
            i += length;
        }
        if (mEarcSad != null) {
            result = Arrays.toString(mEarcSad).replace(",", "");
        } else {
            result = "[]";
        }
        if ((mEarcSadb != null) && (mEarcVsadb != null)) {
            result += Arrays.toString(mEarcSadb).replace(",", "").replace("]", "");
            result += Arrays.toString(mEarcVsadb).replace(",", "").replace("[", " ");
        } else if (mEarcSadb != null) {
            result += Arrays.toString(mEarcSadb).replace(",", "");
        } else if (mEarcVsadb != null) {
            result += Arrays.toString(mEarcVsadb).replace(",", "");
        } else {
            result += "[]";
        }
        return result;
    }

    @ServiceThreadOnly
    void onEarcAudioLatency(int flag) {
        assertRunOnServiceThread();
        mEarcLatency = getEarcLatency();
        HdmiLogger.debug("mEarcLatency: %d", mEarcLatency);
        String keyValue = Constants.AUDIO_PARAMETER_AVR_LATENCY + mEarcLatency;
        getAudioManager().setParameters(keyValue);
    }

    @ServiceThreadOnly
    void onEarcCaps(int flag) {
        assertRunOnServiceThread();
        HdmiLogger.debug("onEarcAudioCaps: %d", flag);
        if (mEarcStatus == Constants.HDMI_EARC_NOT_ENABLED) {
            Slog.i(TAG, "System is not in eARC mode, don't get/set eARC capabilities.");
            return;
        }
        // avr_capability= [SAD_byte, byte of SADB+VSADB, Port_number, ARC(0)/EARC_Mode(1)][SAD raw data][SADB+VSADB]
        String keyValue = Constants.AUDIO_PARAMETER_AVR_CAPS;
        byte[] buf = new byte[4];
        buf[0] = (byte) mEarcSadLen;
        buf[1] = (byte) (mEarcVsadbLen + mEarcSadbLen);
        buf[2] = (byte) mEarcPortId;
        buf[3] = (byte) 0x01;
        keyValue += Arrays.toString(buf);
        keyValue += parseEarcCaps(getEarcCapability());
        Slog.i(TAG, "keyValue:" + keyValue);
        getAudioManager().setParameters(keyValue);
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
