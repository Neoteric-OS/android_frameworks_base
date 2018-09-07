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
      private static final int EVENT_START_PROVISIONING = 0;
      private static final int EVENT_STOP_PROVISIONING = 1;
      private static final int EVENT_DEFAULT_TRANSPORT_IS_CELLULAR_CHANGED = 2;
      private static final int EVENT_ADD_DOWNSTREAM_MAPPING = 3;
      private static final int EVENT_REMOVE_DOWNSTREAM_RESULT = 4;


      // The ArraySet contains enabled downstream types, ex:
      // {@link ConnectivityManager.TETHERING_WIFI}
      // {@link ConnectivityManager.TETHERING_USB}
      // {@link ConnectivityManager.TETHERING_BLUETOOTH}
      @GuardedBy("mCurrentTethers")
      private final ArraySet<Integer> mCurrentTethers;
      private final Context mContext;
      private final int mPermissionChangeMessageCode;
      private final MockableSystemProperties mSystemProperties;
      private final SharedLog mLog;
      private final EntitlementHandler mHandler;
      private @Nullable TetheringConfiguration mConfig;
      private final StateMachine mTetherMasterSM;
      //key: TETHERING_TYPE, value: PROVISION_RESULT
      private final SparseIntArray mMobilePermitted;
      private PendingIntent mProvisioningRecheckAlarm;
      private boolean mMobileUpstreamPermitted = true;
      private boolean mUsingCellularAsUpstream = false;
      private boolean mNeedReRunProvisioningUi = false;

      public EntitlementManager(Context ctx, StateMachine target,
              SharedLog log, int what, MockableSystemProperties systemProperties) {

          mContext = ctx;
          mLog = log.forSubComponent(TAG);;
          mCurrentTethers = new ArraySet<Integer>();
          mMobilePermitted = new SparseIntArray();
          mSystemProperties = systemProperties;
          mTetherMasterSM = target;
          mPermissionChangeMessageCode = what;
          final Handler masterHandler = target.getHandler();
          // Create entitlement's own handler which is associated with TetherMaster thread
          // let all entitlement processing run in the same thread.
          mHandler = new EntitlementHandler(masterHandler.getLooper());
          mContext.registerReceiver(mReceiver, new IntentFilter(INTENT_PROVISIONING_ALARM),
                  null, mHandler);
      }

      /**
       * Pass a new TetheringConfiguration instance each time when
       * Tethering#updateConfiguration() is called.
       */
      public void updateConfiguration(TetheringConfiguration conf) {
          mConfig = conf;
      }

      /**
       * Check if mobile upstream is permitted.
       */
      public boolean isMobileUpstreamPermitted() {
          return mMobileUpstreamPermitted;
      }

      /**
       * This is called when tethering starts.
       * If current default network is cellular, launch UI provisioning app
       * If current default network is not cellular, run silent provisioning
       * check first and re-run UI provisioning when the default network
       * swithes to cellular.
       *
       * @param type Tethering type from ConnectivityManager.TETHERING_{@code *}
       * @param showProvisioningUi a boolean indicating whether to show the
       *        provisioning app UI if there is one.
       */
      public void startProvisioningIfNeeded(int type, boolean showProvisioningUi) {
          mHandler.sendMessage(mHandler.obtainMessage(EVENT_START_PROVISIONING,
                  type, encodeBool(showProvisioningUi)));
      }

      private void handleStartProvisioningIfNeeded(int type, boolean showProvisioningUi) {
          if (!isValidDownstreamType(type)) return;

          if (!mCurrentTethers.contains(type)) mCurrentTethers.add(type);

          if (isTetherProvisioningRequired()) {
              // If provisioning is required and the result is not known yet,
              // mobile upstream should not be allowed.
              if (mMobilePermitted.size() == 0) {
                  mMobileUpstreamPermitted = false;
              }

              if (mUsingCellularAsUpstream && showProvisioningUi) {
                  runUiTetherProvisioning(type);
                  mNeedReRunProvisioningUi = false;
              } else {
                  runSilentTetherProvisioning(type);
                  mNeedReRunProvisioningUi |= showProvisioningUi;
              }
          } else {
              mMobileUpstreamPermitted = true;
          }
      }

      /**
       * Tell EntitlementManager that a given type of tethering has been disabled
       *
       * @param type Tethering type from ConnectivityManager.TETHERING_{@code *}
       */
      public void stopProvisioningIfNeeded(int type) {
          mHandler.sendMessage(mHandler.obtainMessage(EVENT_STOP_PROVISIONING, type, 0));
      }

      private void handleStopProvisioningIfNeeded(int type) {
          if (!isValidDownstreamType(type)) return;

          int index = mCurrentTethers.indexOf(type);
          if (index >= 0) mCurrentTethers.remove(index);
          // There are lurking bugs where the notion of "provisioning required" or
          // "tethering supported" may change without noticing tethering properly.
          // Remove the mapping all the time no matter provisioning is required or not
          removeDownStreamMapping(type);
      }

      /**
       * When default internet network is mobile, assume the user wants to use mobile
       * as upstream. When mobile don't permitted as upstream, re-run UI provisioning
       * check to trigger UI pop up if possible.
       *
       * @param up Whether the default internet network is internet network is mobile or not
       */
      public void setCellularIsDefaultInternetUpstream(boolean up) {
          mHandler.sendMessage(mHandler.obtainMessage(
                  EVENT_DEFAULT_TRANSPORT_IS_CELLULAR_CHANGED, encodeBool(up), 0));
      }
      private void handleSetCellularIsDefaultInternetUpstream(boolean up) {
          if (DBG) {
              Log.d(TAG, "setCellularIsDefaultInternetUpstream: " + up +
                      ", mMobileUpstreamPermitted: " + mMobileUpstreamPermitted +
                      ", mNeedReRunProvisioningUi: " + mNeedReRunProvisioningUi);
          }
          mUsingCellularAsUpstream = up;

          if (mCurrentTethers.size() == 0 || !isTetherProvisioningRequired()) {
              return;
          }

          if (mUsingCellularAsUpstream && !mMobileUpstreamPermitted &&
                  mNeedReRunProvisioningUi) {
              // Just to show UI, re-run provisioning check for any enabled type.
              int enabledType = mCurrentTethers.valueAt(0);
              runUiTetherProvisioning(enabledType);
              mNeedReRunProvisioningUi = false;
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
       * Re-check tethering provisioning for all enabled tether type.
       * Reference ConnectivityManager.TETHERING_{@code *} for each tether type.
       *
       * Note: this method is only called from TetherMaster on the handler thread.
       * If there are new caller from different thread, the logic should move to
       * masterHandler to avoid race conditions.
     */
    public void reevaluateSimCardProvisioning() {
        if (DBG) Log.d(TAG, "reevaluateSimCardProvisioning");
        if (!mHandler.getLooper().isCurrentThread()) {
            mLog.log("Fix me, reevaluateSimCardProvisioning() don't run in TetherMaster thread");
        }
        mMobilePermitted.clear();

        if (mCurrentTethers.size() == 0) return;

        // TODO: refine provisioning check to isTetherProvisioningRequired() ??
        if (!mConfig.hasMobileHotspotProvisionApp()
                || carrierConfigAffirmsEntitlementCheckNotRequired()) {
            checkIfPermittedChange();
            return;
        }

        for (Integer type : mCurrentTethers) {
            runSilentTetherProvisioning(type);
        }
        mNeedReRunProvisioningUi = true;
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
     * @param type Tethering type from ConnectivityManager.TETHERING_{@code *}
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
     * Run the UI-enabled tethering provisioning check.
     * @param type Tethering type from ConnectivityManager.TETHERING_{@code *}
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
        if (mProvisioningRecheckAlarm == null) {
            int period = mConfig.provisioningCheckPeriod;
            if (period == 0) return;

            Intent intent = new Intent(INTENT_PROVISIONING_ALARM);
            mProvisioningRecheckAlarm = PendingIntent.getBroadcast(mContext, 0, intent, 0);
            AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(
                    Context.ALARM_SERVICE);
            long periodMs = period * MS_PER_HOUR;
            long firstTime = SystemClock.elapsedRealtime() + periodMs;
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, firstTime, periodMs,
                    mProvisioningRecheckAlarm);
        }
    }

    private void cancelTetherProvisioningRechecks() {
        if (mProvisioningRecheckAlarm != null) {
            AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(
                    Context.ALARM_SERVICE);
            alarmManager.cancel(mProvisioningRecheckAlarm);
            mProvisioningRecheckAlarm = null;
        }
    }

    private void checkIfPermittedChange() {
        boolean prePermitted = mMobileUpstreamPermitted;
        mMobileUpstreamPermitted = (!isTetherProvisioningRequired()
                || mMobilePermitted.indexOfValue(TETHER_ERROR_NO_ERROR) > -1);

        if (DBG) {
            Log.d(TAG, "checkIfPermittedChange from " + prePermitted
                    + " to " + mMobileUpstreamPermitted);
        }

        if (mMobileUpstreamPermitted != prePermitted) {
            mLog.log("Entitlement permitted change: " + mMobileUpstreamPermitted);
            mTetherMasterSM.sendMessage(mPermissionChangeMessageCode);
        }
        // Only schedule periodic re-check when tether is provisioned
        // and the result is ok.
        if (mMobileUpstreamPermitted && mMobilePermitted.size() > 0) {
            scheduleProvisioningRechecks();
        } else {
            cancelTetherProvisioningRechecks();
        }

        if (mUsingCellularAsUpstream && !mMobileUpstreamPermitted &&
                mNeedReRunProvisioningUi) {
            if (mCurrentTethers.size() == 0) return;

            // Just to show UI, re-run provisioning check for any enabled type.
            int enabledType = mCurrentTethers.valueAt(0);
            runUiTetherProvisioning(enabledType);
            mNeedReRunProvisioningUi = false;
        }
    }

    /**
     * Add the mapping between provisioning result and tethering type.
     * Notify UpstreamNetworkMonitor if mobile permission changes.
     *
     * @param type Tethering type from ConnectivityManager.TETHERING_{@code *}
     * @param resultcode Provisioning result
     */
    protected void addDownStreamMapping(int type, int resultcode) {
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_ADD_DOWNSTREAM_MAPPING,
                type, resultcode));
    }

    private void handleAddDownStreamMapping(int type, int resultcode) {
        if (DBG) {
            Log.d(TAG, "addDownStreamMapping: " + type + ", result: " + resultcode
                    + " ,TetherTypeRequested: " + mCurrentTethers.contains(type));
        }
        if (!mCurrentTethers.contains(type)) return;

        mMobilePermitted.put(type, resultcode);
        checkIfPermittedChange();
    }

    /**
     * remove the mapping for input tethering type
     * @param type Tethering type from ConnectivityManager.TETHERING_{@code *}
     */
    protected void removeDownStreamMapping(int type) {
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_REMOVE_DOWNSTREAM_RESULT,
                type, 0));
    }

    private void handleRemoveDownStreamMapping(int type) {
        if (DBG) Log.d(TAG, "removeDownStreamMapping: " + type);
        mMobilePermitted.delete(type);
        checkIfPermittedChange();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (INTENT_PROVISIONING_ALARM.equals(intent.getAction())) {
                mLog.log("Received provisioning alarm: " + intent);
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
                case EVENT_START_PROVISIONING:
                    handleStartProvisioningIfNeeded(msg.arg1, toBool(msg.arg2));
                    break;
                case EVENT_STOP_PROVISIONING:
                    handleStopProvisioningIfNeeded(msg.arg1);
                    break;
                case EVENT_DEFAULT_TRANSPORT_IS_CELLULAR_CHANGED:
                    handleSetCellularIsDefaultInternetUpstream(toBool(msg.arg1));
                    break;
                case EVENT_ADD_DOWNSTREAM_MAPPING:
                    handleAddDownStreamMapping(msg.arg1, msg.arg2);
                    break;
                case EVENT_REMOVE_DOWNSTREAM_RESULT:
                    handleRemoveDownStreamMapping(msg.arg1);
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
     * dump the log of EntitlementManager
     * @param pw {@link PrintWriter} is used to print formatted
     */
    public void dump(PrintWriter pw) {
        pw.print("mMobileUpstreamPermitted: ");
        pw.println(mMobileUpstreamPermitted);
        for (int i = 0; i < mMobilePermitted.size(); i++) {
            pw.print("Type: ");
            pw.print(typeString(mMobilePermitted.keyAt(i)));
            pw.print(", Value: ");
            pw.println(valueString(mMobilePermitted.valueAt(i)));
        }
    }

    private static String typeString(int type) {
        switch (type) {
            case TETHERING_BLUETOOTH: return "TETHERING_BLUETOOTH";
            case TETHERING_INVALID: return "TETHERING_INVALID";
            case TETHERING_USB: return "TETHERING_USB";
            case TETHERING_WIFI: return "TETHERING_WIFI";
            default:
                return String.format("UNKNOWN (%d)", type);
        }
    }

    private static String valueString(int value) {
        switch (value) {
            case TETHER_ERROR_NO_ERROR: return "TETHER_ERROR_NO_ERROR";
            case TETHER_ERROR_PROVISION_FAILED: return "TETHER_ERROR_PROVISION_FAILED";
            default:
                return String.format("UNKNOWN (%d)", value);
        }
    }
}
