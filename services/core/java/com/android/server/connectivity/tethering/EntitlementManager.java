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

package com.android.server.connectivity.tethering;

import static android.net.ConnectivityManager.EXTRA_ADD_TETHER_TYPE;
import static android.net.ConnectivityManager.EXTRA_PROVISION_CALLBACK;
import static android.net.ConnectivityManager.EXTRA_RUN_PROVISION;
import static android.net.ConnectivityManager.TETHERING_BLUETOOTH;
import static android.net.ConnectivityManager.TETHERING_INVALID;
import static android.net.ConnectivityManager.TETHERING_USB;
import static android.net.ConnectivityManager.TETHERING_WIFI;
import static android.net.ConnectivityManager.TETHER_ERROR_NO_ERROR;
import static android.net.ConnectivityManager.TETHER_ERROR_PROVISION_FAILED;

import static com.android.internal.R.string.config_wifi_tether_enable;

import android.annotation.Nullable;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.util.SharedLog;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.StateMachine;
import com.android.server.connectivity.MockableSystemProperties;

import java.io.PrintWriter;

/**
 * This class encapsulates entitlement/provisioning mechanics
 * provisioning check only applies to the use of the mobile network as an upstream
 *
 * @hide
 */
public class EntitlementManager {
    private static final String TAG = EntitlementManager.class.getSimpleName();
    private static final boolean DBG = false;

    protected static final String DISABLE_PROVISIONING_SYSPROP_KEY = "net.tethering.noprovisioning";
    private static final String INTENT_PROVISIONING_ALARM =
            "com.android.server.connectivity.tethering.PROVISIONING_RECHECK_ALARM";

    // {@link ComponentName} of the Service used to run tether provisioning.
    private static final ComponentName TETHER_SERVICE = ComponentName.unflattenFromString(
            Resources.getSystem().getString(config_wifi_tether_enable));
    private static final int MS_PER_HOUR = 60 * 60 * 1000;
    private static final int EVENT_START_RPOVISIONING       = 0;
    private static final int EVENT_STOP_RPOVISIONING        = 1;
    private static final int EVENT_CELLUALR_STATE_CHANGE    = 2;
    private static final int EVENT_ADD_DOWNSTREAM_RESULT    = 3;
    private static final int EVENT_REMOVE_DOWNSTREAM_RESULT = 4;


    // The ArraySet contains enabled downstream types, ex:
    // {@link ConnectivityManager.TETHERING_WIFI}
    // {@link ConnectivityManager.TETHERING_USB}
    // {@link ConnectivityManager.TETHERING_BLUETOOTH}
    @GuardedBy("mCurrentTethers")
    private final ArraySet<Integer> mCurrentTethers;
    private final Context mContext;
    private final int mWhat;
    private final MockableSystemProperties mSystemProperties;
    private final SharedLog mLog;
    private final EntitlementHandler mHandler;
    private @Nullable TetheringConfiguration mConfig;
    private final StateMachine mTarget;
    //key: TETHERING_TYPE, value: PROVISION_RESULT
    private final SparseIntArray mMobilePermittedMap;
    private PendingIntent mProvisionRecheckAlarm;
    private boolean mCurrentPermitted = true;
    private boolean mCellularUsing = false;
    private boolean mNeedReRunUI = false;

    public EntitlementManager(Context ctx, StateMachine target,
            SharedLog log, int what, MockableSystemProperties systemProperties) {

        mContext = ctx;
        mLog = log;
        mCurrentTethers = new ArraySet<Integer>();
        mMobilePermittedMap = new SparseIntArray();
        mSystemProperties = systemProperties;
        mTarget = target;
        mWhat = what;
        final Handler masterHandler = target.getHandler();
        // create entitlement's own handler which is associated with TetherMaster thread
        // let all entitlement thing run in the same thread
        mHandler = new EntitlementHandler(masterHandler.getLooper());
        mContext.registerReceiver(mReceiver, new IntentFilter(INTENT_PROVISIONING_ALARM),
                null, mHandler);
    }

    /**
     * Pass a new TetheringConfiguration instance each time when
     * Tethering.java's updateConfiguration() is called.
     */
    public void updateConfiguration(TetheringConfiguration conf) {
        mConfig = conf;
    }

    /**
     * check if mobile upstream is permitted
     */
    public boolean isMobileUpstreamPermitted() {
        return mCurrentPermitted;
    }

    /**
     * This is called when tethering start
     * If current default network is cellular, launch UI provisioning app
     * If current default network is not cellular, run silent provisioning
     * check first and re-run UI provisioning when default network is switch
     * to cellular.
     *
     * @param type Tethering type
     * @param showProvisioningUi a boolean indicating to show the provisioning
     *         app UI if there is one.
     */
    public void startProvisioningIfNeeded(int type, boolean showProvisioningUi) {
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_START_RPOVISIONING,
                type, encodeBool(showProvisioningUi)));
    }

    private void internalStartProvisioningIfNeeded(int type, boolean showProvisioningUi) {
        if (isInValidType(type)) return;

        if (!mCurrentTethers.contains(type)) mCurrentTethers.add(type);

        if (isTetherProvisioningRequired()) {
            //If provisioning is required and don't get any result yet,
            //mobile upstream should not be allowed.
            if (mMobilePermittedMap.size() == 0) {
                mCurrentPermitted = false;
            }

            if (mCellularUsing && showProvisioningUi) {
                runUiTetherProvisioning(type);
                mNeedReRunUI = false;
            } else {
                runSilentTetherProvisioning(type);
                mNeedReRunUI |= showProvisioningUi;
            }
        } else {
            mCurrentPermitted = true;
        }
    }

    /**
     * Tell EntitlementManager that a given type of tethering has been disabled
     *
     * @param type Tethering type
     */
    public void stopProvisioningIfNeeded(int type) {
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_STOP_RPOVISIONING, type, 0));
    }

    private void internalStopProvisioningIfNeeded(int type) {
        if (isInValidType(type)) return;

        int index = mCurrentTethers.indexOf(type);
        if (index >= 0) mCurrentTethers.remove(index);
        // We have had potential bugs where our notion of "provisioning required" or
        // "tethering supported" may change without noticing us properly. To we call
        // this all the time whether provisioning is required or not
        removeDownStreamMapping(type);
    }

    /**
     * When default internet network is mobile, suppose user want to
     * use mobile as upstream. We should run UI provisioning check
     * if mobile may not permitted as upstream.
     *
     * @param up Default internet network is mobile or not
     */
    public void setCellularDefaultInternetUp(boolean up) {
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_CELLUALR_STATE_CHANGE,
                encodeBool(up), 0));
    }
    private void internalSetCellularDefaultInternetUp(boolean up) {
        if (DBG) {
            Log.d(TAG, "setCellularDefaultInternetUp: " + up + ", mCurrentPermitted: "
                    + mCurrentPermitted + ", NeedRecheck: " + mNeedReRunUI);
        }
        mCellularUsing = up;

        if (mCurrentTethers.size() == 0
                || !isTetherProvisioningRequired()) {
            return;
        }

        if (mCellularUsing && !mCurrentPermitted && mNeedReRunUI) {
            // just to show UI, re-run provisioning check for any enabled type
            int enabledType = mCurrentTethers.valueAt(0);
            runUiTetherProvisioning(enabledType);
            mNeedReRunUI = false;
        }
    }

    /**
     * Check if the device requires a provisioning check in order to enable tethering.
     *
     * @return a boolean - {@code true} indicating tether provisioning is required by the carrier.
     */
    @VisibleForTesting
    public boolean isTetherProvisioningRequired() {
        if (mSystemProperties.getBoolean(DISABLE_PROVISIONING_SYSPROP_KEY, false)
                || mConfig.provisioningApp.length == 0) {
            return false;
        }
        if (carrierConfigAffirmsEntitlementCheckNotRequired()) {
            return false;
        }
        return (mConfig.provisioningApp.length == 2);
    }

    /**
     * Re-check tethering provisioning for all enabled tether type
     *
     * Note: the only caller is called from the same TetherMaster thread
     * If there are new caller from different thread, the function should
     * move to masterHandler.
     */
    public void reevaluateSimCardProvisioning() {
        if (DBG) Log.d(TAG, "reevaluateSimCardProvisioning");
        mMobilePermittedMap.clear();

        if (mCurrentTethers.size() == 0) return;

        //TODO: refine provisioning check to isTetherProvisioningRequired() ??
        if (!mConfig.hasMobileHotspotProvisionApp()
                || carrierConfigAffirmsEntitlementCheckNotRequired()) {
            checkIfPermittedChange();
            return;
        }

        for (Integer type : mCurrentTethers) {
            runSilentTetherProvisioning(type);
        }
        mNeedReRunUI = true;
    }

    // The logic here is aimed solely at confirming that a CarrierConfig exists
    // and affirms that entitlement checks are not required.
    //
    // TODO: find a better way to express this, or alter the checking process
    // entirely so that this is more intuitive.
    private boolean carrierConfigAffirmsEntitlementCheckNotRequired() {
        // Check carrier config for entitlement checks
        final CarrierConfigManager configManager = (CarrierConfigManager) mContext
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager == null) return false;

        final PersistableBundle carrierConfig = configManager.getConfig();
        if (carrierConfig == null) return false;

        // A CarrierConfigManager was found and it has a config.
        final boolean isEntitlementCheckRequired = carrierConfig.getBoolean(
                CarrierConfigManager.KEY_REQUIRE_ENTITLEMENT_CHECKS_BOOL);
        return !isEntitlementCheckRequired;
    }

    /**
     * run no UI tethering provisioning check
     * @param type Tethering type
     */
    protected void runSilentTetherProvisioning(int type) {
        if (DBG) Log.d(TAG, "runSilentTetherProvisioning: " + type);
        ResultReceiver receiver = buildReceiver(type);

        Intent intent = new Intent();
        intent.putExtra(EXTRA_ADD_TETHER_TYPE, type);
        intent.putExtra(EXTRA_RUN_PROVISION, true);
        intent.putExtra(EXTRA_PROVISION_CALLBACK, receiver);
        intent.setComponent(TETHER_SERVICE);
        final long ident = Binder.clearCallingIdentity();
        try {
            mContext.startServiceAsUser(intent, UserHandle.CURRENT);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    /**
     * There are UI shown when running this tethering provisioning check method
     * @param type Tethering type
     */
    protected void runUiTetherProvisioning(int type) {
        if (DBG) Log.d(TAG, "runUiTetherProvisioning: " + type);
        ResultReceiver receiver = buildReceiver(type);

        Intent intent = new Intent(Settings.ACTION_TETHER_PROVISIONING);
        intent.putExtra(EXTRA_ADD_TETHER_TYPE, type);
        intent.putExtra(EXTRA_PROVISION_CALLBACK, receiver);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final long ident = Binder.clearCallingIdentity();
        try {
            mContext.startActivityAsUser(intent, UserHandle.CURRENT);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private ResultReceiver buildReceiver(int type) {
        ResultReceiver rr = new ResultReceiver(null) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                addDownStreamMapping(type, resultCode);
            }
        };

        Parcel parcel = Parcel.obtain();
        rr.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ResultReceiver receiverForSending = ResultReceiver.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        return receiverForSending;
    }

    private void scheduleProvisioningRechecks() {
        if (mProvisionRecheckAlarm == null) {
            int period = mConfig.provisionCheckPeriod;
            if (period == 0) return;

            Intent intent = new Intent(INTENT_PROVISIONING_ALARM);
            mProvisionRecheckAlarm = PendingIntent.getBroadcast(mContext, 0, intent, 0);
            AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(
                    Context.ALARM_SERVICE);
            long periodMs = period * MS_PER_HOUR;
            long firstTime = SystemClock.elapsedRealtime() + periodMs;
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, firstTime, periodMs,
                    mProvisionRecheckAlarm);
        }
    }

    private void cancelTetherProvisioningRechecks() {
        if (mProvisionRecheckAlarm != null) {
            AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(
                    Context.ALARM_SERVICE);
            alarmManager.cancel(mProvisionRecheckAlarm);
            mProvisionRecheckAlarm = null;
        }
    }

    private void checkIfPermittedChange() {
        boolean prePermitted = mCurrentPermitted;
        mCurrentPermitted = (!isTetherProvisioningRequired()
                || mMobilePermittedMap.indexOfValue(TETHER_ERROR_NO_ERROR) > -1);

        if (DBG) {
            Log.d(TAG, "checkIfPermittedChange from " + prePermitted
                    + " to " + mCurrentPermitted);
        }

        if (mCurrentPermitted != prePermitted) {
            mLog.log("Entitlement permitted change: " + mCurrentPermitted);
            mTarget.sendMessage(mWhat);
        }
        // Only schedule periodic re-check when tether is provisioned
        // and the result is ok.
        if (mCurrentPermitted && mMobilePermittedMap.size() > 0) {
            scheduleProvisioningRechecks();
        } else {
            cancelTetherProvisioningRechecks();
        }

        if (mCellularUsing && !mCurrentPermitted && mNeedReRunUI) {
            if (mCurrentTethers.size() == 0) return;

            // just to show UI, re-run provisioning check for any enabled type
            int enabledType = mCurrentTethers.valueAt(0);
            runUiTetherProvisioning(enabledType);
            mNeedReRunUI = false;
        }
    }

    /**
     * add the mapping between provisioning result and tethering type
     * notify UpstreamNetworkMonitor if mobile permission is change
     *
     * @param type Tethering type
     * @param resultcode Provisioning result
     */
    protected void addDownStreamMapping(int type, int resultcode) {
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_ADD_DOWNSTREAM_RESULT,
                type, resultcode));
    }

    private void internalAddDownStreamMapping(int type, int resultcode) {
        if (DBG) {
            Log.d(TAG, "addDownStreamMapping: " + type + ", result: " + resultcode
                    + " ,TetherTypeRequested: " + mCurrentTethers.contains(type));
        }
        if (!mCurrentTethers.contains(type)) return;

        mMobilePermittedMap.put(type, resultcode);
        checkIfPermittedChange();
    }

    /**
     * remove the mapping for input tethering type
     * @param type Tethering type
     */
    protected void removeDownStreamMapping(int type) {
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_REMOVE_DOWNSTREAM_RESULT,
                type, 0));
    }

    private void internalRemoveDownStreamMapping(int type) {
        if (DBG) Log.d(TAG, "removeDownStreamMapping: " + type);
        mMobilePermittedMap.delete(type);
        checkIfPermittedChange();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mLog.log("Got provision alarm " + intent);

            if (INTENT_PROVISIONING_ALARM.equals(intent.getAction())) {
                reevaluateSimCardProvisioning();
            }
        }
    };

    private class EntitlementHandler extends Handler {
        public EntitlementHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_START_RPOVISIONING:
                    internalStartProvisioningIfNeeded(msg.arg1, toBool(msg.arg2));
                    break;
                case EVENT_STOP_RPOVISIONING:
                    internalStopProvisioningIfNeeded(msg.arg1);
                    break;
                case EVENT_CELLUALR_STATE_CHANGE:
                    internalSetCellularDefaultInternetUp(toBool(msg.arg1));
                    break;
                case EVENT_ADD_DOWNSTREAM_RESULT:
                    internalAddDownStreamMapping(msg.arg1, msg.arg2);
                    break;
                case EVENT_REMOVE_DOWNSTREAM_RESULT:
                    internalRemoveDownStreamMapping(msg.arg1);
                    break;
            }
        }
    }

    private static boolean toBool(int encodedBoolean) {
        return encodedBoolean != 0; // Only 0 means false.
    }

    private static int encodeBool(boolean b) {
        return b ? 1 : 0;
    }

    private static boolean isInValidType(int type) {
        switch (type) {
            case TETHERING_BLUETOOTH:
            case TETHERING_USB:
            case TETHERING_WIFI:
                return false;
            default:
                return true;
        }
    }

    /**
     * dump the log of EntitlementManager
     * @param pw {@link PrintWriter} is used to print formatted
     */
    public void dump(PrintWriter pw) {
        pw.print("mCurrentPermitted: ");
        pw.println(mCurrentPermitted);
        for (int i = 0; i < mMobilePermittedMap.size(); i++) {
            pw.print("Type: ");
            pw.print(typeString(mMobilePermittedMap.keyAt(i)));
            pw.print(", Value: ");
            pw.println(valueString(mMobilePermittedMap.valueAt(i)));
        }
    }

    private static String typeString(int type) {
        switch (type) {
            case TETHERING_BLUETOOTH: return "TETHERING_BLUETOOTH";
            case TETHERING_INVALID: return "TETHERING_INVALID";
            case TETHERING_USB: return "TETHERING_USB";
            case TETHERING_WIFI: return "TETHERING_WIFI";
            default:
                return String.format("UNKNOWN (%s)", type);
        }
    }

    private static String valueString(int value) {
        switch (value) {
            case TETHER_ERROR_NO_ERROR: return "TETHER_ERROR_NO_ERROR";
            case TETHER_ERROR_PROVISION_FAILED: return "TETHER_ERROR_PROVISION_FAILED";
            default:
                return String.format("UNKNOWN (%s)", value);
        }
    }
}
