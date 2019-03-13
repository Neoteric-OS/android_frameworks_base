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
import static android.net.ConnectivityManager.TETHERING_BLUETOOTH;
import static android.net.ConnectivityManager.TETHERING_INVALID;
import static android.net.ConnectivityManager.TETHERING_USB;
import static android.net.ConnectivityManager.TETHERING_WIFI;
import static android.net.ConnectivityManager.TETHER_ERROR_ENTITLEMENT_UNKONWN;
import static android.net.ConnectivityManager.TETHER_ERROR_NO_ERROR;
import static android.net.ConnectivityManager.TETHER_ERROR_PROVISION_FAILED;

import static com.android.internal.R.string.config_wifi_tether_enable;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import android.util.SparseIntArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.StateMachine;
import com.android.server.connectivity.MockableSystemProperties;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Re-check tethering provisioning for enabled downstream tether types.
 * Reference ConnectivityManager.TETHERING_{@code *} for each tether type.
 *
 * All methods of this class must be accessed from the thread of tethering
 * state machine.
 * @hide
 */
public class EntitlementManager {
    private static final String TAG = EntitlementManager.class.getSimpleName();
    private static final boolean DBG = false;

    @VisibleForTesting
    protected static final String DISABLE_PROVISIONING_SYSPROP_KEY = "net.tethering.noprovisioning";
    private static final String ACTION_PROVISIONING_ALARM =
            "com.android.server.connectivity.tethering.PROVISIONING_RECHECK_ALARM";

    // Used to specify the downstream type of tethering for entitlement app.
    public static final String EXTRA_TETHER_TYPE = "TETHER_TYPE";
    // Used for getting entitlement result from entitlement app.
    public static final String EXTRA_ENTITLEMENT_RESULT = "EntitlementResult";
    // {@link ComponentName} of the Service used to run tether provisioning.
    private static final ComponentName TETHER_SERVICE = ComponentName.unflattenFromString(
            Resources.getSystem().getString(config_wifi_tether_enable));
    private static final int MS_PER_HOUR = 60 * 60 * 1000;
    // Entitlement results to match the activity provision protocol.
    // Default assumes the entitlement result is negative if we do not receive a positive one.
    private static final int ENTITLEMENT_RESULT_DEFAULT = Activity.RESULT_CANCELED;
    private static final int ENTITLEMENT_RESULT_OK = Activity.RESULT_OK;
    private static final int EVENT_START_PROVISIONING = 0;
    private static final int EVENT_STOP_PROVISIONING = 1;
    private static final int EVENT_UPSTREAM_CHANGED = 2;
    private static final int EVENT_MAYBE_RUN_PROVISIONING = 3;
    private static final int EVENT_GET_ENTITLEMENT_VALUE = 4;


    // The ArraySet contains enabled downstream types, ex:
    // {@link ConnectivityManager.TETHERING_WIFI}
    // {@link ConnectivityManager.TETHERING_USB}
    // {@link ConnectivityManager.TETHERING_BLUETOOTH}
    private final @NonNull ArrayList<Integer> mCurrentTethers;
    private final @NonNull ArrayList<Integer> mSilentProvisioningList;
    private final Context mContext;
    private final int mPermissionChangeMessageCode;
    private final MockableSystemProperties mSystemProperties;
    private final SharedLog mLog;
    private final @NonNull SparseIntArray mEntitlementCacheValue;
    private final EntitlementHandler mHandler;
    private @Nullable TetheringConfiguration mConfig;
    private final StateMachine mTetherMasterSM;
    // Key: ConnectivityManager.TETHERING_*(downstream).
    // Value: ConnectivityManager.TETHER_ERROR_{NO_ERROR or PROVISION_FAILED}(provisioning result).
    private final @NonNull SparseIntArray mCellularPermitted;
    private PendingIntent mProvisioningRecheckAlarm;
    private boolean mCellularUpstreamPermitted = true;
    private boolean mUsingCellularAsUpstream = false;
    private boolean mNeedReRunProvisioningUi = false;
    private String mSilentProvisioningResponse = null;

    public EntitlementManager(Context ctx, StateMachine tetherMasterSM, SharedLog log,
            int permissionChangeMessageCode, MockableSystemProperties systemProperties) {

        mContext = ctx;
        mLog = log.forSubComponent(TAG);
        mCurrentTethers = new ArrayList<Integer>();
        mSilentProvisioningList = new ArrayList<Integer>();
        mCellularPermitted = new SparseIntArray();
        mSystemProperties = systemProperties;
        mEntitlementCacheValue = new SparseIntArray();
        mTetherMasterSM = tetherMasterSM;
        mPermissionChangeMessageCode = permissionChangeMessageCode;
        final Handler masterHandler = tetherMasterSM.getHandler();
        // Create entitlement's own handler which is associated with TetherMaster thread
        // let all entitlement processes run in the same thread.
        mHandler = new EntitlementHandler(masterHandler.getLooper());
        mContext.registerReceiver(mReceiver, new IntentFilter(ACTION_PROVISIONING_ALARM),
                null, mHandler);
    }

    /**
     * Pass a new TetheringConfiguration instance each time when Tethering#updateConfiguration()
     * is called. This must to be always called on the tethering SM thread.
     */
    public void updateConfiguration(TetheringConfiguration conf) {
        mConfig = conf;

        if (!isTetherProvisioningRequired()) return;

        if (mConfig.provisionResponse == mSilentProvisioningResponse
                || mConfig.provisionResponse.isEmpty()) return;

        if (mSilentProvisioningResponse != null) {
            mContext.unregisterReceiver(mSilentResultReceiver);
        }
        mSilentProvisioningResponse = mConfig.provisionResponse;
        mContext.registerReceiver(mSilentResultReceiver,
                new IntentFilter(mSilentProvisioningResponse),
                android.Manifest.permission.CONNECTIVITY_INTERNAL,
                mHandler);
    }

    /**
     * Check if cellular upstream is permitted.
     */
    public boolean isCellularUpstreamPermitted() {
        return mCellularUpstreamPermitted;
    }

    /**
     * This is called when tethering starts.
     * Launch provisioning app if upstream is cellular.
     *
     * @param downstreamType tethering type from ConnectivityManager.TETHERING_{@code *}
     * @param showProvisioningUi a boolean indicating whether to show the
     *        provisioning app UI if there is one.
     */
    public void startProvisioningIfNeeded(int downstreamType, boolean showProvisioningUi) {
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_START_PROVISIONING,
                downstreamType, encodeBool(showProvisioningUi)));
    }

    private void handleStartProvisioningIfNeeded(int type, boolean showProvisioningUi) {
        if (!isValidDownstreamType(type)) return;

        if (!mCurrentTethers.contains(type)) mCurrentTethers.add(type);

        if (isTetherProvisioningRequired()) {
            // If provisioning is required and the result is not available yet,
            // cellular upstream should not be allowed.
            if (mCellularPermitted.size() == 0) {
                mCellularUpstreamPermitted = false;
            }
            // If upstream is not cellular, provisioning app would not be launched
            // till upstream change to cellular.
            if (!mUsingCellularAsUpstream) {
                mNeedReRunProvisioningUi |= showProvisioningUi;
                return;
            }

            if (showProvisioningUi) {
                runUiTetherProvisioning(type);
                mNeedReRunProvisioningUi = false;
            } else {
                enqueueProvisioning(type);
            }
        } else {
            mCellularUpstreamPermitted = true;
        }
    }

    /**
     * Tell EntitlementManager that a given type of tethering has been disabled
     *
     * @param type tethering type from ConnectivityManager.TETHERING_{@code *}
     */
    public void stopProvisioningIfNeeded(int type) {
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_STOP_PROVISIONING, type, 0));
    }

    private void handleStopProvisioningIfNeeded(int type) {
        if (!isValidDownstreamType(type)) return;

        mCurrentTethers.remove(new Integer(type));
        // There are lurking bugs where the notion of "provisioning required" or
        // "tethering supported" may change without without tethering being notified properly.
        // Remove the mapping all the time no matter provisioning is required or not.
        removeDownstreamMapping(type);
        mSilentProvisioningList.remove(new Integer(type));
    }

    /**
     * Notify EntitlementManager if upstream is cellular or not.
     *
     * @param isCellular whether tethering upstream is cellular.
     */
    public void notifyUpstream(boolean isCellular) {
        mHandler.sendMessage(mHandler.obtainMessage(
                EVENT_UPSTREAM_CHANGED, encodeBool(isCellular), 0));
    }

    private void handleNotifyUpstream(boolean isCellular) {
        if (DBG) {
            mLog.i("notifyUpstream: " + isCellular
                    + ", mCellularUpstreamPermitted: " + mCellularUpstreamPermitted
                    + ", mNeedReRunProvisioningUi: " + mNeedReRunProvisioningUi);
        }
        mUsingCellularAsUpstream = isCellular;

        if (mUsingCellularAsUpstream) {
            handleMaybeRunProvisioning();
        }
    }

    /** Run provisioning if needed */
    public void maybeRunProvisioning() {
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_MAYBE_RUN_PROVISIONING));
    }

    private void handleMaybeRunProvisioning() {
        if (mCurrentTethers.size() == 0 || !isTetherProvisioningRequired()) {
            return;
        }

        // Whenever any entitlement value changes, all downstreams will re-evaluate whether they
        // are allowed. Therefore even if the silent check here ends in a failure and the UI later
        // yields success, then the downstream that got a failure will re-evaluate as a result of
        // the change and get the new correct value.
        for (Integer downstream : mCurrentTethers) {
            if (mCellularPermitted.indexOfKey(downstream) < 0) {
                if (mNeedReRunProvisioningUi) {
                    mNeedReRunProvisioningUi = false;
                    runUiTetherProvisioning(downstream);
                } else {
                    enqueueProvisioning(downstream);
                }
            }
        }
    }

    private void enqueueProvisioning(int downstream) {
        if (mSilentProvisioningList.contains(downstream)) return;

        // In case entitlement app can't handle new request when it is under processing
        // entitlement check. Just enqueue incoming downstream if there is other downstream
        // under processing. The pending downstream in the queue will be processed sequentially
        // after receiving entitlement result.
        mSilentProvisioningList.add(downstream);
        if (mSilentProvisioningList.size() > 1) return;

        runSilentTetherProvisioning(downstream);
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
     * Re-check tethering provisioning for all enabled tether types.
     * Reference ConnectivityManager.TETHERING_{@code *} for each tether type.
     *
     * Note: this method is only called from TetherMaster on the handler thread.
     * If there are new callers from different threads, the logic should move to
     * masterHandler to avoid race conditions.
     */
    public void reevaluateSimCardProvisioning() {
        if (DBG) mLog.i("reevaluateSimCardProvisioning");

        if (!mHandler.getLooper().isCurrentThread()) {
            // Except for test, this log should not appear in normal flow.
            mLog.log("reevaluateSimCardProvisioning() don't run in TetherMaster thread");
        }
        mEntitlementCacheValue.clear();
        mCellularPermitted.clear();

        // TODO: refine provisioning check to isTetherProvisioningRequired() ??
        if (!mConfig.hasMobileHotspotProvisionApp()
                || carrierConfigAffirmsEntitlementCheckNotRequired()) {
            evaluateCellularPermission();
            return;
        }

        if (mUsingCellularAsUpstream) {
            handleMaybeRunProvisioning();
        }
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
     * Run no UI tethering provisioning check.
     * @param type tethering type from ConnectivityManager.TETHERING_{@code *}
     */
    protected void runSilentTetherProvisioning(int type) {
        if (DBG) mLog.i("runSilentTetherProvisioning: " + type);
        final Intent intent = new Intent(mConfig.provisioningAppNoUi);
        intent.putExtra(EXTRA_TETHER_TYPE, type);
        intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND
                | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);

        setEntitlementAppActive(intent);
        mContext.sendBroadcast(intent);
    }

    private void setEntitlementAppActive(@NonNull final Intent intent) {
        final PackageManager packageManager = mContext.getPackageManager();
        final List<ResolveInfo> resolvers =
                packageManager.queryBroadcastReceivers(intent, PackageManager.MATCH_ALL);
        if (resolvers == null || resolvers.isEmpty()) {
            mLog.e("No found BroadcastReceivers for provision intent.");
            return;
        }

        for (ResolveInfo resolver : resolvers) {
            if (resolver.activityInfo == null || resolver.activityInfo.applicationInfo == null) {
                continue;
            }

            if (resolver.activityInfo.applicationInfo.isSystemApp()) {
                final String packageName = resolver.activityInfo.packageName;
                final UsageStatsManager usageStatsManager =
                        (UsageStatsManager) mContext.getSystemService(Context.USAGE_STATS_SERVICE);
                usageStatsManager.setAppInactive(packageName, false /* idle */);
            }
        }
    }

    /**
     * Run the UI-enabled tethering provisioning check.
     * @param type tethering type from ConnectivityManager.TETHERING_{@code *}
     */
    @VisibleForTesting
    protected void runUiTetherProvisioning(int type) {
        mCellularPermitted.put(type, TETHER_ERROR_ENTITLEMENT_UNKONWN);
        final ResultReceiver receiver = buildProxyReceiver(type, null);
        runUiTetherProvisioning(type, receiver);
    }

    @VisibleForTesting
    protected void runUiTetherProvisioning(int type, ResultReceiver receiver) {
        if (DBG) mLog.i("runUiTetherProvisioning: " + type);

        final Intent intent = new Intent(Settings.ACTION_TETHER_PROVISIONING);
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

    // Not needed to check if this don't run on the handler thread because it's private.
    private void scheduleProvisioningRechecks() {
        if (mProvisioningRecheckAlarm == null) {
            final int period = mConfig.provisioningCheckPeriod;
            if (period <= 0) return;

            final Intent intent = new Intent(ACTION_PROVISIONING_ALARM);
            mProvisioningRecheckAlarm = PendingIntent.getBroadcast(mContext, 0, intent, 0);
            final AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(
                    Context.ALARM_SERVICE);
            final long periodMs = period * MS_PER_HOUR;
            final long firstAlarmTime = SystemClock.elapsedRealtime() + periodMs;
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, firstAlarmTime, periodMs,
                    mProvisioningRecheckAlarm);
        }
    }

    private void cancelTetherProvisioningRechecks() {
        if (mProvisioningRecheckAlarm != null) {
            final AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(
                    Context.ALARM_SERVICE);
            alarmManager.cancel(mProvisioningRecheckAlarm);
            mProvisioningRecheckAlarm = null;
        }
    }

    private void evaluateCellularPermission() {
        final boolean oldPermitted = mCellularUpstreamPermitted;
        mCellularUpstreamPermitted = (!isTetherProvisioningRequired()
                || mCellularPermitted.indexOfValue(TETHER_ERROR_NO_ERROR) > -1);

        if (DBG) {
            mLog.i("Cellular permission change from " + oldPermitted
                    + " to " + mCellularUpstreamPermitted);
        }

        if (mCellularUpstreamPermitted != oldPermitted) {
            mLog.log("Cellular permission change: " + mCellularUpstreamPermitted);
            mTetherMasterSM.sendMessage(mPermissionChangeMessageCode);
        }
        // Only schedule periodic re-check when tether is provisioned
        // and the result is ok.
        if (mCellularUpstreamPermitted && mCellularPermitted.size() > 0) {
            scheduleProvisioningRechecks();
        } else {
            cancelTetherProvisioningRechecks();
        }
    }

    /**
     * Add the mapping between provisioning result and tethering type.
     * Notify UpstreamNetworkMonitor if Cellular permission changes.
     *
     * @param type tethering type from ConnectivityManager.TETHERING_{@code *}
     * @param resultCode Provisioning result
     */
    protected void addDownstreamMapping(int type, int resultCode) {
        mLog.i("addDownstreamMapping: " + type + ", result: " + resultCode
                + " ,TetherTypeRequested: " + mCurrentTethers.contains(type));

        if (!mCurrentTethers.contains(type)) return;

        mCellularPermitted.put(type, resultCode);
        evaluateCellularPermission();
    }

    /**
     * Remove the mapping for input tethering type.
     * @param type tethering type from ConnectivityManager.TETHERING_{@code *}
     */
    protected void removeDownstreamMapping(int type) {
        mLog.i("removeDownstreamMapping: " + type);
        mCellularPermitted.delete(type);
        evaluateCellularPermission();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_PROVISIONING_ALARM.equals(intent.getAction())) {
                mLog.log("Received provisioning alarm");
                reevaluateSimCardProvisioning();
            }
        }
    };

    private final BroadcastReceiver mSilentResultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DBG) mLog.i("Got provision result " + intent);

            if (mSilentProvisioningResponse == null) {
                mLog.e("Unexpected provision response " + intent);
                return;
            }
            if (mSilentProvisioningList.size() == 0) return;

            if (mSilentProvisioningResponse.equals(intent.getAction())) {
                // If entitlement application doesn't explicitly specify valid downstream
                // type in response result, just assume the downstream type is the first
                // value of mSilentProvisioningList.
                final int type = intent.getIntExtra(EXTRA_TETHER_TYPE, TETHERING_INVALID);
                final Integer downstream;
                if (isValidDownstreamType(type)) {
                    downstream = new Integer(type);
                    mSilentProvisioningList.remove(downstream);
                } else {
                    downstream = mSilentProvisioningList.remove(0);
                }

                final int result = intent.getIntExtra(EXTRA_ENTITLEMENT_RESULT,
                        ENTITLEMENT_RESULT_DEFAULT);
                final int entitlementValue = (result != ENTITLEMENT_RESULT_OK)
                        ? TETHER_ERROR_PROVISION_FAILED : TETHER_ERROR_NO_ERROR;
                updateEntitlementCacheValue(downstream, entitlementValue);

                if (mSilentProvisioningList.size() > 0) {
                    runSilentTetherProvisioning(mSilentProvisioningList.get(0));
                }
            }
        }
    };

    private class EntitlementHandler extends Handler {
        EntitlementHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_START_PROVISIONING:
                    handleStartProvisioningIfNeeded(msg.arg1, toBool(msg.arg2));
                    break;
                case EVENT_STOP_PROVISIONING:
                    handleStopProvisioningIfNeeded(msg.arg1);
                    break;
                case EVENT_UPSTREAM_CHANGED:
                    handleNotifyUpstream(toBool(msg.arg1));
                    break;
                case EVENT_MAYBE_RUN_PROVISIONING:
                    handleMaybeRunProvisioning();
                    break;
                case EVENT_GET_ENTITLEMENT_VALUE:
                    handleGetLatestTetheringEntitlementValue(msg.arg1, (ResultReceiver) msg.obj,
                            toBool(msg.arg2));
                    break;
                default:
                    mLog.log("Unknown event: " + msg.what);
                    break;
            }
        }
    }

    private static boolean toBool(int encodedBoolean) {
        return encodedBoolean != 0;
    }

    private static int encodeBool(boolean b) {
        return b ? 1 : 0;
    }

    private static boolean isValidDownstreamType(int type) {
        switch (type) {
            case TETHERING_BLUETOOTH:
            case TETHERING_USB:
            case TETHERING_WIFI:
                return true;
            default:
                return false;
        }
    }

    /**
     * Dump the infromation of EntitlementManager.
     * @param pw {@link PrintWriter} is used to print formatted
     */
    public void dump(PrintWriter pw) {
        pw.print("mCellularUpstreamPermitted: ");
        pw.println(mCellularUpstreamPermitted);
        for (Integer type : mCurrentTethers) {
            pw.print("Type: ");
            pw.print(typeString(type));
            if (mCellularPermitted.indexOfKey(type) > -1) {
                pw.print(", Value: ");
                pw.println(errorString(mCellularPermitted.get(type)));
            } else {
                pw.println(", Value: empty");
            }
        }
    }

    private static String typeString(int type) {
        switch (type) {
            case TETHERING_BLUETOOTH: return "TETHERING_BLUETOOTH";
            case TETHERING_INVALID: return "TETHERING_INVALID";
            case TETHERING_USB: return "TETHERING_USB";
            case TETHERING_WIFI: return "TETHERING_WIFI";
            default:
                return String.format("TETHERING UNKNOWN TYPE (%d)", type);
        }
    }

    private static String errorString(int value) {
        switch (value) {
            case TETHER_ERROR_ENTITLEMENT_UNKONWN: return "TETHER_ERROR_ENTITLEMENT_UNKONWN";
            case TETHER_ERROR_NO_ERROR: return "TETHER_ERROR_NO_ERROR";
            case TETHER_ERROR_PROVISION_FAILED: return "TETHER_ERROR_PROVISION_FAILED";
            default:
                return String.format("UNKNOWN ERROR (%d)", value);
        }
    }

    private ResultReceiver buildProxyReceiver(int type, final ResultReceiver receiver) {
        final ResultReceiver rr = new ResultReceiver(mHandler) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                final int updatedCacheValue = updateEntitlementCacheValue(type, resultCode);
                if (receiver != null) receiver.send(updatedCacheValue, null);
            }
        };

        return writeToParcel(rr);
    }

    // Instances of ResultReceiver need to be public classes for remote processes to be able
    // to load them (otherwise, ClassNotFoundException). For private classes, this method
    // performs a trick : round-trip parceling any instance of ResultReceiver will return a
    // vanilla instance of ResultReceiver sharing the binder token with the original receiver.
    // The binder token has a reference to the original instance of the private class and will
    // still call its methods, and can be sent over. However it cannot be used for anything
    // else than sending over a Binder call.
    // While round-trip parceling is not great, there is currently no other way of generating
    // a vanilla instance of ResultReceiver because all its fields are private.
    private ResultReceiver writeToParcel(final ResultReceiver receiver) {
        final Parcel parcel = Parcel.obtain();
        receiver.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final ResultReceiver receiverForSending = ResultReceiver.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        return receiverForSending;
    }

    /**
     * Update the last entitlement value to internal cache
     *
     * @param type tethering type from ConnectivityManager.TETHERING_{@code *}
     * @param resultCode last entitlement value
     * @return the last updated entitlement value
     */
    private int updateEntitlementCacheValue(int type, int resultCode) {
        if (DBG) {
            mLog.i("updateEntitlementCacheValue: " + type + ", result: " + resultCode);
        }
        final int value = (resultCode == TETHER_ERROR_NO_ERROR) ? resultCode :
                TETHER_ERROR_PROVISION_FAILED;
        mEntitlementCacheValue.put(type, value);
        addDownstreamMapping(type, value);
        return value;
    }

    /** Get the last value of the tethering entitlement check. */
    public void getLatestTetheringEntitlementResult(int downstream, ResultReceiver receiver,
            boolean showEntitlementUi) {
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_GET_ENTITLEMENT_VALUE,
                downstream, encodeBool(showEntitlementUi), receiver));

    }

    private void handleGetLatestTetheringEntitlementValue(int downstream, ResultReceiver receiver,
            boolean showEntitlementUi) {

        if (!isTetherProvisioningRequired()) {
            receiver.send(TETHER_ERROR_NO_ERROR, null);
            return;
        }

        final int cacheValue = mEntitlementCacheValue.get(
                downstream, TETHER_ERROR_ENTITLEMENT_UNKONWN);
        if (cacheValue == TETHER_ERROR_NO_ERROR || !showEntitlementUi) {
            receiver.send(cacheValue, null);
        } else {
            final ResultReceiver proxy = buildProxyReceiver(downstream, receiver);
            runUiTetherProvisioning(downstream, proxy);
        }
    }
}
