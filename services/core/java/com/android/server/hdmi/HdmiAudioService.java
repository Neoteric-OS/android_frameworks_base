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
import android.hardware.hdmi.IHdmiAudioService;
import com.android.internal.util.DumpUtils;
import com.android.internal.util.IndentingPrintWriter;

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
    private int mEarcStatus = Constants.HDMI_EARC_IDLE;

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

    // Indicate atmos supported in eARC;
    @GuardedBy("mLock")
    private boolean mAtmosSupported = false;

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

    //eARC hal return value definition.
    private final int EARC_RESULT_OK = 0;
    private final int EARC_RESULT_EARC_NOT_SUPPORT = 1;
    private final int EARC_RESULT_INVALID_ARG = 2;
    private final int EARC_RESULT_NO_RESPONED = 3;
    private final int EARC_RESULT_UNKNOWN = 4;

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
        try{
            publishBinderService(Context.HDMI_AUDIO_SERVICE, new BinderService());
        }catch (Exception e){
            Slog.d(TAG, "Error while publish binder service for HdmiAudioServiceManager.");
            Slog.d(TAG, e.getMessage());
        }
        if (mEarcController != null) {
            // Register ContentObserver to monitor the settings change.
            registerContentObserver();
        }
    }

    private void registerContentObserver() {
        ContentResolver resolver = getContext().getContentResolver();
        String[] settings = new String[] {
                Global.HDMI_EARC_CONTROL_ENABLED,
                Global.HDMI_ARC_SWITCH_TO_EARC,
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
        mEarcControlFeatureEnabled = readIntSetting(Global.HDMI_EARC_CONTROL_ENABLED, Constants.ARC_ONLY);
        if (mEarcController != null) {
            mEarcSupported = isEarcSupported();
            if (mEarcSupported) {
                mEarcPortId = getPortId();
                Slog.i(TAG, "initService mEarcControlFeatureEnabled:" + mEarcControlFeatureEnabled);
                // Default value is ARC_ONLY in driver. This could avoid setting eARC mode again.
                // If default value in driver is modified, this should also be modified.
                // TODO: Check support mode from driver directly.
                if (mEarcControlFeatureEnabled != Constants.ARC_ONLY) {
                    setEarcControlFeatureEnabled(mEarcControlFeatureEnabled);
                }
                Slog.i(TAG, "mEarcStatus:" + mEarcStatus);
                if (mEarcStatus != getEarcStatus()) {
                    HdmiAudioService.this.onEarcStatus(getEarcStatus());
                }
            }
        } else {
            Slog.i(TAG, "Device does not support HDMI eARC.");
            return;
        }
    }

    void setEarcControlFeatureEnabled(int eArcControl) {
        int ret = -1;
        assertRunOnServiceThread();
        synchronized (mLock) {
            mEarcControlFeatureEnabled = eArcControl;
            Slog.i(TAG,"set mEarcControlFeatureEnabled:"+ eArcControl);
            ret = mEarcController.controlFeature(eArcControl);

            if (ret != EARC_RESULT_OK) {
                Slog.w(TAG,"set mEarcControlFeatureEnabled fail with ret: " + ret + ", Retry again.");
                ret = mEarcController.controlFeature(eArcControl);
                if (ret != EARC_RESULT_OK) {
                    Slog.e(TAG,"Retry mEarcControlFeatureEnabled fail with ret: " + ret);
                } else {
                    Slog.i(TAG,"Retry mEarcControlFeatureEnabled Success.");
                }
            } else {
                Slog.i(TAG,"set mEarcControlFeatureEnabled Success.");
            }
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
     * Set Raw Data to eARC Capablity
     */    
    String setEarcRawCaps(byte[] rawCaps) {
        if (getEarcStatus() != Constants.HDMI_EARC_IDLE) {
            Slog.i(TAG, "eARC/ARC device is connected. Set raw caps is NOT aollowed!");
            return null;
        }
        return parseEarcCaps(rawCaps);
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

        Global.putInt(getContext().getContentResolver(),
                    Global.HDMI_EARC_CONNECTED, earcStatus);
        mHdmiControlManager.setEarcConnectionState(earcStatus);

        switch (earcStatus) {
            case Constants.HDMI_EARC_ENABLED:
                Slog.d(TAG, "eARC Connected.");
                notifyEarcStatusToAudioService(true);
                break;
            case Constants.HDMI_EARC_NOT_ENABLED:
                if (mEarcStatus == Constants.HDMI_EARC_ENABLED) {
                    Slog.d(TAG, "eARC Disconnected.");
                    clearEarcCaps();
                    notifyEarcStatusToAudioService(false);
                }
                break;
            case Constants.HDMI_EARC_IDLE:
                if (mEarcStatus == Constants.HDMI_EARC_ENABLED) {
                    Slog.d(TAG, "eARC Disconnected.");
                    clearEarcCaps();
                    notifyEarcStatusToAudioService(false);
                }
                break;
            case Constants.HDMI_EARC_WAITING:
                if (mEarcStatus == Constants.HDMI_EARC_ENABLED) {
                    Slog.d(TAG, "eARC Disconnected.");
                    clearEarcCaps();
                    notifyEarcStatusToAudioService(false);
                }
                break;
            default:
                Slog.w(TAG, "Do not handle this. eARC Status:" + earcStatus);
                return;
        }

        synchronized (mLock) {
            mEarcStatus = earcStatus;
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
                    int earcControl = readIntSetting(option, Constants.ARC_ONLY);
                    Slog.i(TAG, "eARC control enabled changed:" + earcControl);
                    if (earcControl != Constants.PREFER_EARC) {
                        setEarcControlFeatureEnabled(earcControl);
                    }
                    break;
                case Global.HDMI_ARC_SWITCH_TO_EARC:
                    boolean switchToEarc = readBooleanSetting(option, false);
                    if(switchToEarc) {
                        Slog.i(TAG, "HDMI ARC is ready to switch to eARC");
                        setEarcControlFeatureEnabled(Constants.PREFER_EARC);
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

    void writeBooleanSetting(String key, boolean value) {
        ContentResolver cr = getContext().getContentResolver();
        Global.putInt(cr, key, toInt(value));
    }

    private static int toInt(boolean enabled) {
        return enabled ? ENABLED : DISABLED;
    }

    private void setEarcAtmosSupported(int atmosBit) {
        if (atmosBit != 0) {
            Slog.i(TAG, "AVR supports ATMOS");
            mAtmosSupported = true;
        } else {
            mAtmosSupported = false;
        }
        writeBooleanSetting(Constants.AVR_CAPABILITY_DDP_ATMOS, mAtmosSupported);
    }

    /**
    * Parse eARC DDP ATMOS as spec.
    * Short Audio Descriptor data is a byte array, and 3 bytes a set.
    * Bit 3 ~ 6 in first byte of a set indicate a specific audio format.
    *
    * We will check first byte in each set to find DDP audio data to parse atmos bit.
    * The last bit in third byte in DDP data will be atmos bit.
    * 0 : Support ATMOS.
    * 1 : Not support ATMOS.
    */
    private void parseEarcAtmosSupported(byte[] params) {
        Slog.i(TAG, "Parsing Atmos bit.");
        int size = params.length;

        synchronized (mLock) {
            for (int i = 0; i < size; i++ ) {
                if( i % 3 == 0 ) {
                    switch (params[i] & Constants.AUDIO_FORMAT_MASK) {
                        case Constants.AUDIO_FORMAT_DDP:
                            Slog.i(TAG, "AVR supports DDP");
                            setEarcAtmosSupported(params[i + 2] % 2);
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }

    void clearEarcCaps() {
        String keyValue = Constants.AUDIO_PARAMETER_AVR_CAPS;
        byte[] buf = new byte[4];
        mEarcSad = null;
        mEarcSadb = null;
        mEarcVsadb = null;
        mEarcSadLen = 0;
        mEarcSadbLen = 0;
        mEarcVsadbLen = 0;
        buf[0] = (byte) 0x00;
        buf[1] = (byte) 0x00;
        buf[2] = (byte) 0x00;
        buf[3] = (byte) 0x00;
        keyValue += Arrays.toString(buf);
        keyValue += "[][]";
        getAudioManager().setParameters(keyValue);

        Slog.i(TAG, "clear eARC Atmos bit.");
        mAtmosSupported = false;
        writeBooleanSetting(Constants.AVR_CAPABILITY_DDP_ATMOS, false);
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
        mEarcSadLen = 0;
        mEarcSadbLen = 0;
        mEarcVsadbLen = 0;
        while (i < earcCapsSize) {
            // Tag Code : Bit 5-7
            int tagCode = (earcCaps[i] & EARC_CAPS_TAGCODE_MASK) >> EARC_CAPS_TAGCODE_SHIFT ;
            // Length : Bit 0-4
            int length = earcCaps[i] & EARC_CAPS_LENGTH_MASK;
            HdmiLogger.debug( "earcCaps[%d]: 0x%x", i, earcCaps[i]);
            HdmiLogger.debug( "tagCode:0x%x, length: %d", tagCode, length);
            if (length == 0) {
                Slog.i(TAG, "End Marker of eARC capability.");
                break;
            }
            switch (tagCode) {
                case TAGCODE_AUDIO_DATA_BLOCK:
                    mEarcSadLen = length;
                    Slog.i(TAG, "mEarcSadLen:"+ mEarcSadLen);
                    mEarcSad = new byte[mEarcSadLen];
                    System.arraycopy(earcCaps, (i + 1), mEarcSad, 0, mEarcSadLen);
                    for (int j=0; j<mEarcSadLen; j++) {
                        HdmiLogger.debug( "SAD[%d]:0x%x", j, mEarcSad[j]);
                    }
                    break;
                case TAGCODE_SADB_DATA_BLOCK:
                    //Include Tag code size
                    mEarcSadbLen = length + 1;
                    Slog.i(TAG, "mEarcSadbLen:"+ mEarcSadbLen);
                    mEarcSadb = new byte[mEarcSadbLen];
                    System.arraycopy(earcCaps, i, mEarcSadb, 0, mEarcSadbLen);
                    for (int j=0; j<mEarcSadbLen; j++) {
                        HdmiLogger.debug( "SADB[%d]:0x%x", j, mEarcSadb[j]);
                    }
                    break;
                case TAGCODE_USE_EXTENEDED_TAG:
                    if (earcCaps[i + 1] == EXTENDED_TAGCODE_VSADB) {
                        mEarcVsadbLen = length + 1; //Include Tag code size
                        Slog.i(TAG, "mEarcVsadbLen:"+ mEarcVsadbLen);
                        mEarcVsadb = new byte[mEarcVsadbLen];
                        System.arraycopy(earcCaps, i, mEarcVsadb, 0, mEarcVsadbLen);
                        for (int j=0; j<mEarcVsadbLen; j++) {
                            HdmiLogger.debug( "VSADB[%d]:0x%x", j, mEarcVsadb[j]);
                        }
                    }
                    break;
                default:
                    Slog.i(TAG, "Do not handle this tagCode:"+ tagCode);
                    break;
            }
            i += length;
            i++;
        }
        byte[] buf = new byte[4];
        buf[0] = (byte) mEarcSadLen;
        buf[1] = (byte) (mEarcVsadbLen + mEarcSadbLen);
        buf[2] = (byte) mEarcPortId;
        buf[3] = (byte) 0x01;
        result = Arrays.toString(buf);
        if (mEarcSad != null) {
            result += Arrays.toString(mEarcSad).replace(",", "");
        } else {
            result += "[]";
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
        keyValue += parseEarcCaps(getEarcCapability());
        Slog.i(TAG, "keyValue:" + keyValue);
        parseEarcAtmosSupported(mEarcSad);
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

    /**
     * Sets the work source UID to the Binder calling UID.
     * Work source UID allows access to the original calling UID of a Binder call in the Runnables
     * that it spawns.
     * This is necessary because Runnables that are executed on the service thread
     * take on the calling UID of the service thread.
     */
    private void setWorkSourceUidToCallingUid() {
        Binder.setCallingWorkSourceUid(Binder.getCallingUid());
    }
    private void enforceAccessPermission() {
        getContext().enforceCallingOrSelfPermission("android.permission.HDMI_CEC", TAG);
    }
    private void initBinderCall() {
        enforceAccessPermission();
        setWorkSourceUidToCallingUid();
    }

    private class BinderService extends IHdmiAudioService.Stub {
        @Override
        public int getEarcStatus() {
            initBinderCall();
            return mEarcController.getStatus(mEarcPortId);
        }

        @Override
        @Nullable
        public String setEarcRawCaps(byte[] rawCaps) {
            initBinderCall();
            return HdmiAudioService.this.setEarcRawCaps(rawCaps);
        }

        @Override
        protected void dump(FileDescriptor fd, final PrintWriter writer, String[] args) {
            if (!DumpUtils.checkDumpPermission(getContext(), TAG, writer)) return;
            final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");

            pw.println("Dumpsys Hdmi Audio Service");
            pw.println("******************************************");
            pw.println("mEarcSupported: " + mEarcSupported);
            pw.println("mEarcControlFeatureEnabled: " + mEarcControlFeatureEnabled);
            pw.println("mEarcStatus: " + mEarcStatus);
            pw.println("mEarcLatency: " + mEarcLatency);
            pw.println("mEarcPortId: " + mEarcPortId);
            pw.println("mEarcSadLen: " + mEarcSadLen);
            pw.println("mEarcSad: " + Arrays.toString(mEarcSad));
            pw.println("mEarcSadbLen: " + mEarcSadbLen);
            pw.println("mEarcSadb: " + Arrays.toString(mEarcSadb));
            pw.println("mEarcVsadbLen: " + mEarcVsadbLen);
            pw.println("mEarcVsadb: " + Arrays.toString(mEarcVsadb));
            pw.println("mAtmosSupported: " + mAtmosSupported);
        }
    }
}
