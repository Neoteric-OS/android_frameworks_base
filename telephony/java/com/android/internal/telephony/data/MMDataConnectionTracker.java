/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.internal.telephony.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.http.conn.util.InetAddressUtils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncResult;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.CellLocation;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;

import com.android.internal.net.IPVersion;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.RetryManager;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.data.DataProfile.DataProfileType;
import com.android.internal.telephony.gsm.GSMPhone;

/*
 * Definitions:
 * - DataProfile(dp) :Information required to setup a connection (ex. ApnSetting)
 * - DataService(ds) : A particular feature requested by connectivity service, (MMS, GPS) etc.
 *                     also called APN Type
 * - DataConnection(dc) : Underlying PDP connection, associated with a dp.
 * - DataProfileTracker(dpt): Keeps track of services enabled, active, and dc that supports them
 *
 * What we know:
 * - A set of service types that needs to be enabled
 * - Data profiles needed to establish the service type
 * - Each Data profile will also tell us whether IPv4v6/IPv4/IPv6 is possible with that data profile
 * - Priorities of services. (this can be used if MPDP is not supported, or limited # of pdp)
 * - For each service type, it is possible that same APN can handle ipv4 and ipv6. It is
 *   also possible that there are different APNs. This is handled.
 *
 * What we don't know:
 * - We don't know if the underlying network will support IPV6 or not.
 * - We don't know if the underlying network will support MPDP or not (even in 3GPP)
 * - If nw does support mpdp, we dont know how many pdp sessions it can handle
 * - We don't know how many PDP sessions/interfaces modem can handle
 * - We don't know if modem can disconnect existing calls in favor of new ones
 *   based on some profile priority.
 * - We don't know if IP continuity is possible or not possible across technologies.
 *
 * What we assume:
 * - Modem will not tear down the data call if IP continuity is possible.
 * - If modem is aware of service priority, then these priorities are in sync
 *   with what is mentioned here, or we might end up in an infinite setup/disconnect
 *   cycle!
 *
 *
 * State Handling:
 * - states are associated with <service type, ip version> tuple.
 * - this is to handle scenario such as follows,
 *   default data might be connected on ipv4,  but we might be scanning different
 *   apns for default data on ipv6
 *   TODO: need a state machine and everything!
 */

public class MMDataConnectionTracker extends DataConnectionTracker {

    private static final String LOG_TAG = "DATA";

    private static final int DATA_CONNECTION_POOL_SIZE = 8;

    private static final String INTENT_RECONNECT_ALARM
                                            = "com.android.internal.telephony.gprs-reconnect";
    private static final String INTENT_RECONNECT_ALARM_EXTRA_REASON = "reason";
    private static final String INTENT_RECONNECT_ALARM_SERVICE_TYPE = "ds";
    private static final String INTENT_RECONNECT_ALARM_IP_VERSION = "ipv";

    /**
     * Constants for the data connection activity:
     * physical link down/up
     */
     private static final int DATA_CONNECTION_ACTIVE_PH_LINK_INACTIVE = 0;
     private static final int DATA_CONNECTION_ACTIVE_PH_LINK_DOWN = 1;
     private static final int DATA_CONNECTION_ACTIVE_PH_LINK_UP = 2;

    // ServiceStateTracker to keep track of network service state
    ServiceStateTracker mDsst;

    boolean isDctActive = true;

    // keeps track of data statistics activity
    private DataNetStatistics mPollNetStat;

    // keeps track of wifi status - TODO: WHY?
    private boolean mIsWifiConnected = false;

    // Intent sent when the reconnect alarm fires.
    private PendingIntent mReconnectIntent = null;

    //following flags are used in isReadyForData()
    private boolean mNoAutoAttach = false;
    private boolean mIsPsRestricted = false;
    private boolean mDesiredPowerState = true;

    Message mDisconnectAllCompleteMsg;

    private static final boolean SUPPORT_IPV4 = SystemProperties.getBoolean(
            "persist.telephony.support_ipv4", true);

    private static final boolean SUPPORT_IPV6 = SystemProperties.getBoolean(
            "persist.telephony.support_ipv6", true);

    /*
     * If we need to support multiple APNs on a Single/Limited PDN network, then following
     * flag might need to be set to true. This will ensure that only highest priority services
     * are active. Defaults to FALSE (priority arbitration off).
     */
    private static final boolean SUPPORT_SERVICE_PRIORITY_ARBITRATION = SystemProperties.getBoolean(
            "persist.telephony.prior.arbit", false);

    /*
     * warning: if this flag is set then all connections are disconnected when
     * updatedataconnections() is called
     */
    private int mDisconnectPendingCount = 0;
    private boolean mDataCallSetupPending = false;

    /*
     * context to make sure the onUpdateDataConnections doesn't get executed
     * over and over again unnecessarily.
     */
    int mUpdateDataConnectionsContext = 0;

    /**
     * mDataCallList holds all the Data connection,
     */
    private ArrayList<DataConnection> mDataConnectionList;

    BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public synchronized void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            logv("intent received :" + action);
            if (action.equals(Intent.ACTION_SCREEN_ON)) {
                mPollNetStat.notifyScreenState(true);
                stopNetStatPoll();
                startNetStatPoll();

            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                mPollNetStat.notifyScreenState(false);
                stopNetStatPoll();
                startNetStatPoll();

            } else if (action.startsWith((INTENT_RECONNECT_ALARM))) {
                String reason = intent.getStringExtra(INTENT_RECONNECT_ALARM_EXTRA_REASON);
                DataServiceType ds = DataServiceType.valueOf(intent.getStringExtra(INTENT_RECONNECT_ALARM_SERVICE_TYPE));
                IPVersion ipv = IPVersion.valueOf(intent.getStringExtra(INTENT_RECONNECT_ALARM_IP_VERSION));
                /* set state as scanning so that updateDataConnections will process the data call */
                if (mDpt.getState(ds, ipv)==State.WAITING_ALARM)
                    mDpt.setState(State.SCANNING, ds, ipv);
                updateDataConnections(reason);

            } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                final android.net.NetworkInfo networkInfo = (NetworkInfo) intent
                        .getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                mIsWifiConnected = (networkInfo != null && networkInfo.isConnected());

            } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                final boolean enabled = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN) == WifiManager.WIFI_STATE_ENABLED;
                if (!enabled) {
                    // when WIFI got disabled, the NETWORK_STATE_CHANGED_ACTION
                    // quit and wont report disconnected till next enabling.
                    mIsWifiConnected = false;
                }
            }
        }
    };

    public MMDataConnectionTracker(PhoneBase phone) {
        super(phone);

        mDsst = ((GSMPhone)phone).getServiceStateTracker();
        mPollNetStat = new DataNetStatistics(this);

        // register for events.
        mPhone.mCM.registerForOn(this, EVENT_RADIO_ON, null);
        mPhone.mCM.registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
        mPhone.mCM.registerForDataStateChanged(this, EVENT_DATA_CALL_LIST_CHANGED, null);

        mDsst.registerForDataConnectionAttached(this, EVENT_DATA_CONNECTION_ATTACHED, null);
        mDsst.registerForDataConnectionDetached(this, EVENT_DATA_CONNECTION_DETACHED, null);

        mDsst.registerForRoamingOn(this, EVENT_ROAMING_ON, null);
        mDsst.registerForRoamingOff(this, EVENT_ROAMING_OFF, null);

        if (mPhone.getPhoneType() == Phone.PHONE_TYPE_CDMA) { /* CDMA only */
            mPhone.mCM.registerForCdmaOtaProvision(this, EVENT_CDMA_OTA_PROVISION, null);
        } else if (mPhone.getPhoneType() == Phone.PHONE_TYPE_GSM) { /* GSM only */
            mDsst.registerForPsRestrictedEnabled(this, EVENT_PS_RESTRICT_ENABLED, null);
            mDsst.registerForPsRestrictedDisabled(this, EVENT_PS_RESTRICT_DISABLED, null);
        }

        mPhone.getIccRecords().registerForRecordsLoaded(this, EVENT_RECORDS_LOADED, null);

        mDpt.registerForDataProfileDbChanged(this, EVENT_DATA_PROFILE_DB_CHANGED, null);

        IntentFilter filter = new IntentFilter();
        for (DataServiceType ds : DataServiceType.values()) {
            filter.addAction(getAlarmIntentName(ds, IPVersion.INET));
            filter.addAction(getAlarmIntentName(ds, IPVersion.INET6));
        }
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);

        phone.getContext().registerReceiver(mIntentReceiver, filter, null, this);

        createDataCallList();

        // This preference tells us
        // 1) initial condition for "dataEnabled", and
        // 2) whether the RIL will setup the baseband to auto-PS attach.
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(phone.getContext());
        boolean dataDisabledOnBoot = sp.getBoolean(PhoneBase.DATA_DISABLED_ON_BOOT_KEY, false);
        mDpt.setServiceTypeEnabled(DataServiceType.SERVICE_TYPE_DEFAULT, !dataDisabledOnBoot);
        mNoAutoAttach = dataDisabledOnBoot;

   		logv("SUPPORT_IPV4 = " + SUPPORT_IPV4);
        logv("SUPPORT_IPV6 = " + SUPPORT_IPV6);
        logv("SUPPORT_SERVICE_PRIORITY_ARBITRATION = " + SUPPORT_SERVICE_PRIORITY_ARBITRATION);
    }

    public void dispose() {

        // mark DCT as disposed
        isDctActive = false;

        mPhone.mCM.unregisterForOn(this);
        mPhone.mCM.unregisterForOffOrNotAvailable(this);
        mPhone.mCM.unregisterForDataStateChanged(this);

        mDsst.unregisterForDataConnectionAttached(this);
        mDsst.unregisterForDataConnectionDetached(this);

        mPhone.getIccRecords().unregisterForRecordsLoaded(this);

        mDsst.unregisterForRoamingOn(this);
        mDsst.unregisterForRoamingOff(this);

        if (mPhone.getPhoneType() == Phone.PHONE_TYPE_CDMA) { /* CDMA only */
            mPhone.mCM.unregisterForCdmaOtaProvision(this);
        } else if (mPhone.getPhoneType() == Phone.PHONE_TYPE_GSM) { /* GSM only */
            mDsst.unregisterForPsRestrictedEnabled(this);
            mDsst.unregisterForPsRestrictedDisabled(this);
        }

        mDpt.unregisterForDataProfileDbChanged(this);

        destroyDataCallList();

        mPhone.getContext().unregisterReceiver(this.mIntentReceiver);

        super.dispose();
    }

    public void handleMessage(Message msg) {

        if (isDctActive == false) {
            logw("Ignoring handler messages, DCT marked as disposed.");
            return;
        }

        switch (msg.what) {
            case EVENT_UPDATE_DATA_CONNECTIONS:
                onUpdateDataConnections(((String)msg.obj), (int)msg.arg1);
                break;

            case EVENT_RECORDS_LOADED:
                onRecordsLoaded();
                break;

            case EVENT_DATA_CONNECTION_ATTACHED:
                onDataConnectionAttached();
                break;

            case EVENT_DATA_CONNECTION_DETACHED:
                onDataConnectionDetached();
                break;

            case EVENT_DATA_CALL_LIST_CHANGED:
                // unsolicited
                onDataCallListChanged((AsyncResult) msg.obj);
                break;

            case EVENT_DATA_PROFILE_DB_CHANGED:
                onDataProfileListChanged((AsyncResult) msg.obj);
                break;

            case EVENT_CDMA_OTA_PROVISION:
                onCdmaOtaProvision((AsyncResult) msg.obj);
                break;

            case EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED:
                updateDataConnections(REASON_CDMA_SUBSCRIPTION_SOURCE_CHANGED);
                break;

            case EVENT_PS_RESTRICT_ENABLED:
                logi("PS restrict enabled.");
                /**
                 * We don't need to explicitly to tear down the PDP context when
                 * PS restricted is enabled. The base band will deactive PDP
                 * context and notify us with PDP_CONTEXT_CHANGED. But we should
                 * stop the network polling and prevent reset PDP.
                 */
                stopNetStatPoll();
                mIsPsRestricted = true;
                break;

            case EVENT_PS_RESTRICT_DISABLED:
                logi("PS restrict disable.");
                /**
                 * When PS restrict is removed, we need setup PDP connection if
                 * PDP connection is down.
                 */
                mIsPsRestricted = false;
                updateDataConnections(REASON_PS_RESTRICT_DISABLED);
                break;

            default:
                super.handleMessage(msg);
                break;
        }
    }

    protected void updateDataConnections(String reason) {
        mUpdateDataConnectionsContext++;
        Message msg = obtainMessage(EVENT_UPDATE_DATA_CONNECTIONS, //what
                mUpdateDataConnectionsContext, //arg1
                0, //arg2
                reason); //userObj
        sendMessage(msg);
    }

    private void onCdmaOtaProvision(AsyncResult ar) {
        if (ar.exception != null) {
            int[] otaProvision = (int[]) ar.result;
            if ((otaProvision != null) && (otaProvision.length > 1)) {
                switch (otaProvision[0]) {
                    case Phone.CDMA_OTA_PROVISION_STATUS_COMMITTED:
                    case Phone.CDMA_OTA_PROVISION_STATUS_OTAPA_STOPPED:
                        mDpt.resetAllProfilesAsWorking();
                        mDpt.resetAllServiceStates();
                        updateDataConnections(Phone.REASON_CDMA_OTA_PROVISION);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private void onDataProfileListChanged(AsyncResult ar) {
        String reason = (String) ((AsyncResult) ar).result;

        mDpt.resetAllProfilesAsWorking();
        mDpt.resetAllServiceStates();

        disconnectAllConnections(reason, null);
    }

    protected void onRecordsLoaded() {
        mDpt.updateOperatorNumeric(
                mPhone.getIccRecords().getIccOperatorNumeric(), REASON_ICC_LOADED);
        updateDataConnections(REASON_ICC_LOADED);
    }

    protected void onDataConnectionAttached() {
        // reset all profiles as working so as to give
        // all data profiles fair chance again.
        mDpt.resetAllProfilesAsWorking();
        mDpt.resetAllServiceStates();

        /*
         * send a data connection notification update, with latest states, it is
         * possible data went out of service and came back in service without
         * data calls being disconnected
         */
        notifyAllDataServiceTypes(REASON_DATA_NETWORK_ATTACH);

        updateDataConnections(REASON_DATA_NETWORK_ATTACH);
    }

    protected void onDataConnectionDetached() {
        /*
         * Ideally, nothing needs to be done, data connections will disconnected
         * one by one, and update data connections will be done then. But that
         * might not happen, or might take time. So still need to trigger a data
         * connection state update, because data was detached and packets are
         * not going to flow anyway.
         */
        notifyAllDataServiceTypes(REASON_DATA_NETWORK_DETACH);
    }

    @Override
    protected void onRadioOn() {
        mDpt.resetAllProfilesAsWorking();
        mDpt.resetAllServiceStates();
        updateDataConnections(REASON_RADIO_ON);
    }

    @Override
    protected void onRadioOff() {
        //cleanup for next time, probably not required.
        mDpt.resetAllProfilesAsWorking();
        mDpt.resetAllServiceStates();
    }

    @Override
    protected void onRoamingOff() {
        mDpt.resetAllProfilesAsWorking();
        mDpt.resetAllServiceStates();
        updateDataConnections(REASON_ROAMING_OFF);
    }

    @Override
    protected void onRoamingOn() {
        if (getDataOnRoamingEnabled() == false) {
            disconnectAllConnections(REASON_ROAMING_ON, null);
        }
        updateDataConnections(REASON_ROAMING_ON);
    }

    @Override
    protected void onVoiceCallEnded() {
        mDpt.resetAllProfilesAsWorking();
        mDpt.resetAllServiceStates();
        updateDataConnections(REASON_VOICE_CALL_ENDED);
        notifyAllDataServiceTypes(REASON_VOICE_CALL_ENDED);
    }

    @Override
    protected void onVoiceCallStarted() {
        updateDataConnections(REASON_VOICE_CALL_STARTED);
        notifyAllDataServiceTypes(REASON_VOICE_CALL_STARTED);
    }

    @Override
    protected void onMasterDataDisabled() {
        disconnectAllConnections(REASON_MASTER_DATA_DISABLED, null);
    }

    @Override
    protected void onMasterDataEnabled() {
        mDpt.resetAllProfilesAsWorking();
        mDpt.resetAllServiceStates();
        updateDataConnections(REASON_MASTER_DATA_ENABLED);
    }

    @Override
    protected void onServiceTypeDisabled(DataServiceType ds) {

        /* if the dc corresponding to the ds we are disabling is not in use by any other ds;
         * bring it down. Do this for each ip type.
         */
        for (IPVersion ipv : IPVersion.values()) {
            if (mDpt.isServiceTypeActive(ds, ipv) == false)
                continue;

            DataConnection dc = mDpt.getActiveDataConnection(ds, ipv);
            boolean tearDownNeeded = true;
            for (DataServiceType t : DataServiceType.values()) {
                if (t != ds && mDpt.isServiceTypeEnabled(t)
                        && mDpt.getActiveDataConnection(t, ipv) == dc) {
                    tearDownNeeded = false; //dc used by somebody else.
                }
            }
            if (tearDownNeeded) {
                tryDisconnectDataCall(dc, REASON_SERVICE_TYPE_DISABLED);
            }
        }

        //not required - but might be a useful trigger if something else is pending.
        updateDataConnections(REASON_SERVICE_TYPE_DISABLED);
    }

    @Override
    protected void onServiceTypeEnabled(DataServiceType type) {
        mDpt.resetAllProfilesAsWorking();
        mDpt.resetServiceState(type);
        updateDataConnections(REASON_SERVICE_TYPE_ENABLED);
    }

    /**
     * @param explicitPoll if true, indicates that *we* polled for this update
     *            while state == CONNECTED rather than having it delivered via
     *            an unsolicited response (which could have happened at any
     *            previous state
     */
    @SuppressWarnings("unchecked")
    protected void onDataCallListChanged(AsyncResult ar) {

        ArrayList<DataCallState> dcStates;
        dcStates = (ArrayList<DataCallState>) (ar.result);

        if (ar.exception != null) {
            // This is probably "radio not available" or something
            // of that sort. If so, the whole connection is going
            // to come down soon anyway
            return;
        }

        boolean needDataConnectionUpdate = false;
        String dataConnectionUpdateReason = null;
        boolean isDataDormant = true; // will be set to false, if atleast one
                                      // data connection is not dormant.

        for (DataConnection dc: mDataConnectionList) {

            if (dc.isActive() == false) {
                continue;
            }

            DataCallState activeDC = getDataCallStateByCid(dcStates, dc.cid);
            if (activeDC == null) {
                logi("DC has disappeared from list : dc = " + dc);
                dc.resetSynchronously(); //TODO: do this asynchronously
                // services will be marked as inactive, on data connection
                // update
                needDataConnectionUpdate = true;
                if (dataConnectionUpdateReason == null) {
                    dataConnectionUpdateReason = REASON_NETWORK_DISCONNECT;
                }
                handleDisconnectedDc(dc, dataConnectionUpdateReason);
            } else if (activeDC.active == DATA_CONNECTION_ACTIVE_PH_LINK_INACTIVE) {
                /* TODO: ril can give proper disconnect reasons */
                DataConnection.FailCause failCause =
                    DataConnection.FailCause.NETWORK_OR_MODEM_DISCONNECT;
                logi("DC is inactive : dc = " + dc);
                logi("   inactive cause = " + failCause);

                dc.resetSynchronously(); //TODO: do this asynchronously
                needDataConnectionUpdate = true;
                if (dataConnectionUpdateReason == null) {
                    dataConnectionUpdateReason = REASON_NETWORK_DISCONNECT;
                }
                handleDisconnectedDc(dc, dataConnectionUpdateReason);
            } else {
                switch (activeDC.active) {
                    case DATA_CONNECTION_ACTIVE_PH_LINK_UP:
                        isDataDormant = false;
                        break;

                    case DATA_CONNECTION_ACTIVE_PH_LINK_DOWN:
                        // do nothing
                        break;

                    default:
                        loge("dc.cid = " + dc.cid + ", unexpected DataCallState.active="
                                + activeDC.active);
                }
            }
        }

        if (needDataConnectionUpdate) {
            updateDataConnections(dataConnectionUpdateReason);
        }

        if (isDataDormant) {
            mPollNetStat.setActivity(Activity.DORMANT);
            stopNetStatPoll();
        } else {
            mPollNetStat.setActivity(Activity.NONE);
            startNetStatPoll();
        }
        notifyDataActivity();
    }

    private DataCallState getDataCallStateByCid(ArrayList<DataCallState> states, int cid) {
        for (int i = 0, s = states.size(); i < s; i++) {
            if (states.get(i).cid == cid)
                return states.get(i);
        }
        return null;
    }

    @Override
    protected void onConnectDone(AsyncResult ar) {

        mDataCallSetupPending = false;

        CallbackData c = (CallbackData) ar.userObj;

        /*
         * If setup is successful,  ar.result will contain the MMDataConnection instance
         * if setup failure, ar.result will contain the failure reason.
         */
        if (ar.exception == null) { /* connection is up!! */

            /*
             * Handle 3GPP rel 8, ESM 50, 51, 52
             * if there is a partial failure, override bearer type
             */

            DataConnection.FailCause cause = (DataConnection.FailCause) (ar.result);
            if (cause.isPartialFailure()) {
                switch (cause) {
                    case ONLY_IPV4_ALLOWED:
                        logv("[cid " + c.dc.cid + " ] Forcing bearer type " +
                                c.dc.getBearerType() + ">>" + BearerType.IP);
                        c.dc.mBearerType = BearerType.IP;
                        logv("[ESM partial fail " + cause + "]. Disabling IPV6 on data profile. dp="
                                + c.dp.toShortString() + ", ipv=" + IPVersion.INET6);
                        c.dp.setWorking(false, IPVersion.INET6);
                        break;

                    case ONLY_IPV6_ALLOWED:
                        logv("[cid " + c.dc.cid + " ] Forcing bearer type " +
                                c.dc.getBearerType() + ">>" + BearerType.IPV6);
                        c.dc.mBearerType = BearerType.IPV6;
                        logv("[ESM partial fail " + cause + "] Disabling IPV4 on data profile. dp="
                                + c.dp.toShortString() + ", ipv=" + IPVersion.INET);
                        c.dp.setWorking(false, IPVersion.INET);
                        break;

                    case ONLY_SINGLE_BEARER_ALLOWED:
                        /*
                         * No easy to determine if V4 or V6 PDN came up other
                         * than looking up the ip address :|. TODO: there might
                         * be a better way.
                         */
                        String ip[] = c.dc.getIpAddressList();
                        if (ip != null && ip.length > 0) {
                            if (InetAddressUtils.isIPv6Address(ip[0])) {
                                logv("[cid " + c.dc.cid + " ] Forcing bearer type " +
                                        c.dc.getBearerType() + ">>" + BearerType.IPV6);
                                c.dc.mBearerType = BearerType.IPV6;
                            } else {
                                logv("[cid " + c.dc.cid + " ] Forcing bearer type " +
                                        c.dc.getBearerType() + ">>" + BearerType.IP);
                                c.dc.mBearerType = BearerType.IP;
                            }
                        } else {
                            loge("unexpected: ONLY_SINGLE_BEARER_ALLOWED "+
                            "returned with no IP address on any bearer type ");
                        }
                        logv("[ESM partial fail " + cause + "] - rety other ip version later");
                        break;
                    default:
                        logw("unexpected: unhandled partial failure case. assuming success");
                }
            }

            logi("--------------------------");
            logi("Data call setup : SUCCESS / " + cause);
            logi("  service type  : " + c.ds);
            logi("  data profile  : " + c.dp.toShortString());
            logi("  cid/bearertype: " + c.dc.cid + "/" + c.dc.getBearerType());
            logi("  ip address    : " + Arrays.toString(c.dc.getIpAddressList()));
            logi("  gw            : " + c.dc.gatewayAddress);
            logi("  dns           : " + Arrays.toString(c.dc.getDnsServers()));
            logi("--------------------------");

            handleConnectedDc(c.dc, c.reason);

            //we might have other things to do, so call update updateDataConnections() again.
            updateDataConnections(c.reason);
            return; //done.
        }

        //ASSERT: Data call setup has failed.

        DataConnection.FailCause cause = (DataConnection.FailCause) (ar.result);

        logi("--------------------------");
        logi("Data call setup : FAILED");
        logi("  service type  : " + c.ds);
        logi("  data profile  : " + c.dp.toShortString());
        logi("  bearer        : " + c.dc.getBearerType());
        logi("  fail cause    : " + cause);
        logi("--------------------------");

        boolean needDataConnectionUpdate = true;

        /*
         * look at the error code and determine what is the best thing to do :
         * there is no guarantee that modem/network is capable of reporting the
         * correct failure reason, so we do our best to get all requested
         * services up, but somehow making sure we don't retry endlessly.
         */

        if (cause.ipVersionNotSupported()) {
            /*
             * it might not be possible for us to know if its the network that
             * doesn't support the requested bearer type (ex. IPV6) in general,
             * or if its the profile we tried that doesn't support the requested
             * ip types.
             */
            logv("Disabling data profile. dp=" + c.dp.toShortString() + ", ipv=" + c.ipv);
            c.dp.setWorking(false, c.ipv);
            // set state to scanning because can try on other data
            // profiles that might work with this ds+ipv.
            mDpt.setState(State.SCANNING, c.ds, c.ipv);
        } else if (cause.isDataProfileFailure()) {
            /*
             * this profile doesn't work, mark it as not working, so that we
             * have other profiles to try with. It is possible that
             * modem/network didn't report IP_VERSION_NOT_SUPPORTED, but profile
             * might still work with other IPV.
             */
            logv("Disabling data profile. dp=" + c.dp.toShortString() + ", ipv=" + c.ipv);
            c.dp.setWorking(false, c.ipv);
            // set state to scanning because can try on other data
            // profiles that might work with this ds+ipv.
            mDpt.setState(State.SCANNING, c.ds, c.ipv);
        } else if (mDpt.isServiceTypeActive(c.ds) == false &&
                cause.isPdpAvailabilityFailure()) {
            /*
             * not every modem, or network might be able to report this but if
             * we know this is the failure reason, we know exactly what to do!
             * check if low priority services are active, if yes tear it down!
             * But do not bother de-activating low priority calls if the same service
             * is already active on other ip versions.
             */
            if (SUPPORT_SERVICE_PRIORITY_ARBITRATION && disconnectOneLowPriorityDataCall(c.ds, c.reason)) {
                logv("Disconnected low priority data call [pdp availability failure.]");
                needDataConnectionUpdate = false;
                // will be called, when disconnect is complete.
            }
            // set state to scanning because can try on other data
            // profiles that might work with this ds+ipv.
            mDpt.setState(State.SCANNING, c.ds, c.ipv);
        } else if (mDpt.isServiceTypeActive(c.ds) == false
                && SUPPORT_SERVICE_PRIORITY_ARBITRATION
                && disconnectOneLowPriorityDataCall(c.ds, c.reason)) {
            logv("Disconnected low priority data call [pdp availability failure.]");
            /*
             * We do this because there is no way to know if the failure was
             * caused because of network resources not being available! But do
             * not bother de-activating low priority calls if the same service
             * is already active on other ip versions.
             */
            needDataConnectionUpdate = false;
            // set state to scanning because can try on other data
            // profiles that might work with this ds+ipv.
            mDpt.setState(State.SCANNING, c.ds, c.ipv);
        } else if (cause.isPermanentFail()) {
            /*
             * even though modem reports permanent failure, it is not clear
             * if failure is related to data profile, ip version, mpdp etc.
             * its safer to try and exhaust all data profiles.
             */
            logv("Permanent failure. Disabling data profile. dp=" +
                    c.dp.toShortString() + ", ipv="+ c.ipv);
            c.dp.setWorking(false, c.ipv);
            // set state to scanning because can try on other data
            // profiles that might work with this ds+ipv.
            mDpt.setState(State.SCANNING, c.ds, c.ipv);
        } else {
            logv("Data call setup failure cause unknown / temporary failure.");
            /*
             * If we reach here, then it is a temporary failure and we are trying
             * to setup data call on the highest priority service that is enabled.
             * 1. Retry if possible
             * 2. If no more retries possible, disable the data profile.
             * 3. If no more valid data profiles, mark service as disabled and set state
             *    to failed, notify.
             * 4. if default is the highest priority service left enabled,
             *    it will be retried forever!
             */

            RetryManager retryManager = mDpt.getRetryManager(c.ds, c.ipv);

            boolean scheduleAlarm = false;
            long nextReconnectDelay = 0; /* if scheduleAlarm == true */

            if (retryManager.isRetryNeeded()) {
                /* 1 : we have retries left. so Retry! */
                scheduleAlarm  = true;
                nextReconnectDelay = retryManager.getRetryTimer();
                retryManager.increaseRetryCount();
                // set state to scanning because can try on other data
                // profiles that might work with this ds+ipv.
                mDpt.setState(State.WAITING_ALARM, c.ds, c.ipv);
            } else {
                /* 2 : enough of retries. disable the data profile */
                logv("No retries left, disabling data profile. dp=" +
                        c.dp.toShortString() + ", ipv = "+ c.ipv);
                c.dp.setWorking(false, c.ipv);
                if (mDpt.getNextWorkingDataProfile(
                        c.ds, getDataProfileTypeToUse(), c.ipv) != null) {
                    // set state to scanning because can try on other data
                    // profiles that might work with this ds+ipv.
                    mDpt.setState(State.SCANNING, c.ds, c.ipv);
                } else {
                    if (c.ds != DataServiceType.SERVICE_TYPE_DEFAULT) {
                        /*
                         * No more valid data profiles, mark service as disabled
                         * and set state to failed, notify.
                         */
                        // but make sure service is not active on different IPV!
                        if (mDpt.isServiceTypeActive(c.ds) == false) {
                            logv("No data profiles left to try, disabling service  " + c.ds);
                            mDpt.setServiceTypeEnabled(c.ds, false);
                        }
                        mDpt.setState(State.FAILED, c.ds, c.ipv);
                        notifyDataConnection(c.ds, c.reason);
                    } else {
                        /* 4 */
                        /* we don't have any higher priority services
                         * enabled and we ran out of other profiles to try.
                         * So retry forever with the last profile we have.
                         */
                        logv("Retry forever using last disabled data profile. dp=" +
                                c.dp.toShortString() + ", ipv = " + c.ipv);
                        c.dp.setWorking(true, c.ipv);
                        mDpt.setState(State.WAITING_ALARM, c.ds, c.ipv);
                        notifyDataConnection(c.ds, c.reason);
                        notifyDataConnectionFail(c.reason);

                        retryManager.retryForeverUsingLastTimeout();
                        scheduleAlarm = true;
                        nextReconnectDelay = retryManager.getRetryTimer();
                        retryManager.increaseRetryCount();
                    }
                }
            }

            if (scheduleAlarm) {
                logd("Scheduling next attempt on " + c.ds + " for " + (nextReconnectDelay / 1000)
                        + "s. Retry count = " + retryManager.getRetryCount());

                AlarmManager am = (AlarmManager) mPhone.getContext().getSystemService(
                        Context.ALARM_SERVICE);

                Intent intent = new Intent(getAlarmIntentName(c.ds, c.ipv));
                intent.putExtra(INTENT_RECONNECT_ALARM_EXTRA_REASON, c.reason);
                intent.putExtra(INTENT_RECONNECT_ALARM_SERVICE_TYPE, c.ds.toString());
                intent.putExtra(INTENT_RECONNECT_ALARM_IP_VERSION, c.ipv.toString());

                mReconnectIntent = PendingIntent.getBroadcast(mPhone.getContext(), 0, intent, 0);
                // cancel any pending wakeup - TODO: does this work?
                am.cancel(mReconnectIntent);
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime()
                        + nextReconnectDelay, mReconnectIntent);
                needDataConnectionUpdate = true;
            }
        }

        if (needDataConnectionUpdate) {
            updateDataConnections(c.reason);
        }

        logDataConnectionFailure(c.ds, c.dp, c.ipv, cause);
    }

    private String getAlarmIntentName(DataServiceType ds, IPVersion ipv) {
        return (INTENT_RECONNECT_ALARM + "." + ds + "." + ipv);
    }

    private void logDataConnectionFailure(DataServiceType ds, DataProfile dp, IPVersion ipv,
            DataConnection.FailCause cause) {
        if (cause.isEventLoggable()) {
            CellLocation loc = TelephonyManager.getDefault().getCellLocation();
            int id = -1;
            if (loc != null) {
                if (loc instanceof GsmCellLocation)
                    id = ((GsmCellLocation) loc).getCid();
                else
                    id = ((CdmaCellLocation) loc).getBaseStationId();
            }

            if (getRadioTechnology() == RILConstants.SETUP_DATA_TECH_GSM) {
                EventLog.writeEvent(EventLogTags.PDP_NETWORK_DROP,
                        id, getRadioTechnology());
            } else {
                EventLog.writeEvent(EventLogTags.CDMA_DATA_DROP,
                        id, getRadioTechnology());
            }
        }
    }

    /* disconnect exactly one data call whose priority is lower than serviceType */
    private boolean disconnectOneLowPriorityDataCall(DataServiceType serviceType, String reason) {
        for (DataServiceType ds : DataServiceType.values()) {
            if (ds.isLowerPriorityThan(serviceType) && mDpt.isServiceTypeEnabled(ds)
                    && mDpt.isServiceTypeActive(ds)) {
                // we are clueless as to whether IPV4/IPV6 are on same network PDP or
                // different, so disconnect both.
                boolean disconnectDone = false;
                DataConnection dc;
                dc = mDpt.getActiveDataConnection(ds, IPVersion.INET);
                if (dc != null) {
                    tryDisconnectDataCall(dc, reason);
                    disconnectDone = true;
                }
                dc = mDpt.getActiveDataConnection(ds, IPVersion.INET6);
                if (dc != null) {
                    tryDisconnectDataCall(dc, reason);
                    disconnectDone = true;
                }
                if (disconnectDone) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void onDisconnectDone(AsyncResult ar) {

        CallbackData c = (CallbackData) ar.userObj;
        logv("onDisconnectDone: reason=" + c.reason);
        handleDisconnectedDc(c.dc, c.reason);

        if (mDisconnectPendingCount > 0)
            mDisconnectPendingCount--;

        if (mDisconnectPendingCount == 0 && mDisconnectAllCompleteMsg != null) {
            mDisconnectAllCompleteMsg.sendToTarget();
            mDisconnectAllCompleteMsg = null;
        }

        updateDataConnections(c.reason); //check for something else to do.
    }

    private synchronized void disconnectAllConnections(String reason,
            Message disconnectAllCompleteMsg) {

        if (mDisconnectPendingCount != 0) {
            logv("disconnect all data connections in progress. ignoring.");
            return;
        }

        mDisconnectPendingCount = 0;
        mDisconnectAllCompleteMsg = disconnectAllCompleteMsg;

        for (DataConnection dc : mDataConnectionList) {
            if (dc.isInactive() == false ) {
                tryDisconnectDataCall(dc, reason);
                mDisconnectPendingCount++;
            }
        }

        if (mDisconnectPendingCount == 0 && mDisconnectAllCompleteMsg != null) {
            mDisconnectAllCompleteMsg.sendToTarget();
            mDisconnectAllCompleteMsg = null;
            updateDataConnections(reason);
        }
    }

    /*
     * Walk through list of all data connections and make sure that all enabled
     * services are active and there are no unnecessary data call being held
     * active.
     */
    synchronized protected void onUpdateDataConnections(String reason, int context) {
        if (context != mUpdateDataConnectionsContext) {
            //we have other EVENT_UPDATE_DATA_CONNECTIONS on the way.
            logv("onUpdateDataConnections [ignored] : reason=" + reason);
            return;
        }

        logv("onUpdateDataConnections: reason=" + reason);
        dumpDataCalls();
        dumpDataServiceTypes();

        // Check for data readiness!
        boolean isReadyForData = isReadyForData()
                                    && getDesiredPowerState()
                                    && mPhone.mCM.getRadioState().isOn();

        if (isReadyForData == false) {
            logi("***** NOT Ready for data :");
            logi("   " + "getDesiredPowerState() = " + getDesiredPowerState());
            logi("   " + "mCm.getRadioState() = " + mPhone.mCM.getRadioState());
            logi("   " + dumpDataReadinessinfo());
            return; // we will be called, when some event, triggers us back into
                    // readiness.
        } else {
            logi("Ready for data : ");
            logi("   " + "getDesiredPowerState() = " + getDesiredPowerState());
            logi("   " + "mCm.getRadioState() = " + mPhone.mCM.getRadioState());
            logi("   " + dumpDataReadinessinfo());
        }

        /*
         * If we had issued a data call setup before or data call disconnect
         * before, then wait for it to complete before trying any new calls.
         * This is an optimization that can be removed. Serializing data call
         * setup requests make life easier for debugging etc.
         */
        if (mDataCallSetupPending == true) {
            logi("Data Call setup pending. Not trying to bring up any new data connections.");
            return;
        }

        if (mDisconnectPendingCount > 0) {
            logi("Data Call disconnect request pending."
                    + "Not trying to bring up any new data connections.");
            return;
        }

        /*
         * Ensure that all requested services are active. Do setup data call as
         * required in order of decreasing service priority - highest priority
         * service gets data call setup first!
         */
        for (DataServiceType ds : DataServiceType.getPrioritySortedValues()) {
            if (mDpt.isServiceTypeEnabled(ds) == true) {

                //IPV4
                if (SUPPORT_IPV4
                        && mDpt.isServiceTypeActive(ds, IPVersion.INET) == false
                        && mDpt.getState(ds, IPVersion.INET) != State.WAITING_ALARM) {
                    boolean setupDone = trySetupDataCall(ds, IPVersion.INET, reason);
                    if (setupDone)
                        return; //one at a time, in order of priority
                }

                //IPV6
                if (SUPPORT_IPV6
                        && mDpt.isServiceTypeActive(ds, IPVersion.INET6) == false
                        && mDpt.getState(ds, IPVersion.INET6) != State.WAITING_ALARM) {
                    boolean setupDone = trySetupDataCall(ds, IPVersion.INET6, reason);
                    if (setupDone)
                        return; //one at a time, in order of priority
                }
            }
        }
    }

    private void handleDisconnectedDc(DataConnection dc, String reason) {

        logv("handleDisconnectedDc() : " + dc);

        for (DataServiceType ds : DataServiceType.values()) {
            boolean needUpdate = false;

            if (mDpt.getActiveDataConnection(ds, IPVersion.INET) == dc) {
                mDpt.setServiceTypeAsInactive(ds, IPVersion.INET);
                needUpdate = true;
            }
            if (mDpt.getActiveDataConnection(ds, IPVersion.INET6) == dc) {
                mDpt.setServiceTypeAsInactive(ds, IPVersion.INET6);
                needUpdate = true;
            }
            if (needUpdate) {
                notifyDataConnection(ds, reason);
                if (ds == DataServiceType.SERVICE_TYPE_DEFAULT
                        && mDpt.isServiceTypeActive(ds) == false) {
                    SystemProperties.set("gsm.defaultpdpcontext.active", "false");
                }
            }
        }
    }

    private void handleConnectedDc(DataConnection dc, String reason) {

        logv("handleConnectedDc() : " + dc);

        for (DataServiceType ds : DataServiceType.values()) {
            boolean needUpdate = false;

            if (mDpt.isServiceTypeActive(ds, IPVersion.INET) == false
                    && dc.getBearerType().supportsIpVersion(IPVersion.INET)
                    && dc.getDataProfile().canHandleServiceType(ds)) {
                mDpt.setServiceTypeAsActive(ds, dc, IPVersion.INET);
                needUpdate = true;
            }

            if (mDpt.isServiceTypeActive(ds, IPVersion.INET6) == false
                    && dc.getBearerType().supportsIpVersion(IPVersion.INET6)
                    && dc.getDataProfile().canHandleServiceType(ds)) {
                mDpt.setServiceTypeAsActive(ds, dc, IPVersion.INET6);
                needUpdate = true;
            }

            if (needUpdate) {
                notifyDataConnection(ds, reason);
                if (ds == DataServiceType.SERVICE_TYPE_DEFAULT) {
                    SystemProperties.set("gsm.defaultpdpcontext.active", "true");
                }
            }
        }
    }

    private boolean getDesiredPowerState() {
        return mDesiredPowerState;
    }

    @Override
    public synchronized void setDataConnectionAsDesired(boolean desiredPowerState,
            Message onCompleteMsg) {

        mDesiredPowerState = desiredPowerState;

        /*
         * TODO: fix this workaround. For 1x, we should not disconnect data call
         * before powering off.
         */

        if (mDesiredPowerState == false &&
                getRadioTechnology() != ServiceState.RADIO_TECHNOLOGY_1xRTT) {
            disconnectAllConnections(Phone.REASON_RADIO_OFF, onCompleteMsg);
        }

        if (onCompleteMsg != null) {
            onCompleteMsg.sendToTarget();
        }
    }

    private boolean isReadyForData() {

        boolean isReadyForData = isDataConnectivityEnabled();

        boolean roaming = isDataInRoaming();
        isReadyForData = isReadyForData && (!roaming || getDataOnRoamingEnabled());

        int dataRegState = mDsst.getDataServiceState();

        if (mPhone.getPhoneType() == Phone.PHONE_TYPE_GSM) {
            isReadyForData = isReadyForData
                    && (dataRegState == ServiceState.STATE_IN_SERVICE || mNoAutoAttach);
            isReadyForData = isReadyForData && mPhone.getIccRecords() != null
                    && mPhone.getIccRecords().getRecordsLoaded() && !mIsPsRestricted;
        } else if (mPhone.getPhoneType() == Phone.PHONE_TYPE_CDMA) {
            isReadyForData = isReadyForData
                    && (mPhone.mCM.getRadioState() == CommandsInterface.RadioState.NV_READY)
                    || (mPhone.getIccRecords() != null 
                            && mPhone.getIccRecords().getRecordsLoaded());
        } else {
            // unexpected. but handle.
            isReadyForData = false;
        }

        return isReadyForData;
    }

    /**
     * The only circumstances under which we report that data connectivity is not
     * possible are
     * <ul>
     * <li>Data roaming is disallowed and we are roaming.</li>
     * <li>The current data state is {@code DISCONNECTED} for a reason other than
     * having explicitly disabled connectivity. In other words, data is not available
     * because the phone is out of coverage or some like reason.</li>
     * </ul>
     * @return {@code true} if data connectivity is possible, {@code false} otherwise.
     */
    public boolean isDataConnectivityPossible() {
        //TODO: is there any difference from isReadyForData()?
        return isReadyForData();
    }

    private int getRadioTechnology() {
        return mPhone.getServiceState().getRadioTechnology();
    }

    private boolean isCdma(int rat) {
        switch (rat) {
            case ServiceState.RADIO_TECHNOLOGY_IS95A:
            case ServiceState.RADIO_TECHNOLOGY_IS95B:
            case ServiceState.RADIO_TECHNOLOGY_1xRTT:
            case ServiceState.RADIO_TECHNOLOGY_EVDO_0:
            case ServiceState.RADIO_TECHNOLOGY_EVDO_A:
            case ServiceState.RADIO_TECHNOLOGY_EVDO_B:
                return true;
            default:
                return false;
        }
    }

    private boolean isGsm(int rat) {
        switch (rat) {
            case ServiceState.RADIO_TECHNOLOGY_GPRS:
            case ServiceState.RADIO_TECHNOLOGY_EDGE:
            case ServiceState.RADIO_TECHNOLOGY_UMTS:
            case ServiceState.RADIO_TECHNOLOGY_HSDPA:
            case ServiceState.RADIO_TECHNOLOGY_HSUPA:
            case ServiceState.RADIO_TECHNOLOGY_HSPA:
                return true;
            default:
                return false;
        }
    }

    public String dumpDataReadinessinfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("[DataRadioTech = ").append(getRadioTechnology());
        sb.append(", data network state = ").append(getDataServiceState());
        sb.append(", mMasterDataEnabled = ").append(mMasterDataEnabled);
        sb.append(", is Roaming = ").append(mPhone.getServiceState().getRoaming());
        sb.append(", dataOnRoamingEnable = ").append(getDataOnRoamingEnabled());
        sb.append(", isPsRestricted = ").append(mIsPsRestricted);
        sb.append(", desiredPowerState  = ").append(getDesiredPowerState());
        sb.append(", mIccRecords = ").append(mPhone.getIccRecords().getRecordsLoaded())
                                     .append("/"+mPhone.getIccRecords().getIccOperatorNumeric());
        sb.append(", cdmaSubSourceNV? = ")
          .append(mPhone.mCM.getRadioState() == CommandsInterface.RadioState.NV_READY);
        sb.append("]");
        return sb.toString();
    }

    void dumpDataCalls() {
        logv("---dc list---");
        for (DataConnection dc: mDataConnectionList) {
            if (dc.isInactive() == false) {
                StringBuilder sb = new StringBuilder();
                sb.append("cid = " + dc.cid);
                sb.append(", state = "+dc.getStateAsString());
                sb.append(", ipv = "+dc.getBearerType());
                sb.append(", ipaddress = "+Arrays.toString(dc.getIpAddressList()));
                sb.append(", gw="+dc.getGatewayAddress());
                sb.append(", dns="+ Arrays.toString(dc.getDnsServers()));
                logv(sb.toString());
            }
        }
    }

    void dumpDataServiceTypes() {
        logv("---ds list---");
        for (DataServiceType ds: DataServiceType.values()) {
            StringBuilder sb = new StringBuilder();
            sb.append("ds= " + ds);
            sb.append(", enabled = "+mDpt.isServiceTypeEnabled(ds));
            sb.append(", active = v4:")
            .append(mDpt.getState(ds, IPVersion.INET));
            if (mDpt.isServiceTypeActive(ds, IPVersion.INET6)) {
                sb.append("("+mDpt.getActiveDataConnection(ds, IPVersion.INET6).cid+")");
            }
            sb.append(" v6:")
            .append(mDpt.getState(ds, IPVersion.INET6));
            if (mDpt.isServiceTypeActive(ds, IPVersion.INET6)) {
                sb.append("("+mDpt.getActiveDataConnection(ds, IPVersion.INET6).cid+")");
            }
            logv(sb.toString());
        }
    }

    class CallbackData {
        DataConnection dc;
        DataProfile dp;
        String reason;
        DataServiceType ds;
        IPVersion ipv;
    }

    private boolean tryDisconnectDataCall(DataConnection dc, String reason) {
        logv("tryDisconnectDataCall : dc=" + dc + ", reason=" + reason);

        CallbackData c = new CallbackData();
        c.dc = dc;
        c.reason = reason;

        dc.disconnect(obtainMessage(EVENT_DISCONNECT_DONE, c));
        return true;
    }



    private boolean trySetupDataCall(DataServiceType ds, IPVersion ipv, String reason) {
        logv("trySetupDataCall : ds=" + ds + ", ipv=" + ipv + ", reason=" + reason);
        DataProfile dp = mDpt.getNextWorkingDataProfile(ds, getDataProfileTypeToUse(), ipv);
        if (dp == null) {
            logw("no working data profile available to establish service type " +
                    ds + " on " + ipv);
            mDpt.setState(State.FAILED, ds, ipv);
            notifyDataConnection(ds, reason);
            return false;
        }
        DataConnection dc = findFreeDataCall();
        if (dc == null) {
            // if this happens, it probably means that our data call list is not
            // big enough!
            boolean ret = SUPPORT_SERVICE_PRIORITY_ARBITRATION &&
                            disconnectOneLowPriorityDataCall(ds, reason);
            // irrespective of ret, we should return true here
            // - if a call was indeed disconnected, then updateDataConnections()
            //   will take care of setting up call again
            // - if no calls were disconnected, then updateDataConnections will fail for every
            //   service type anyway.
            return true;
        }

        mDpt.setState(State.CONNECTING, ds, ipv);
        notifyDataConnection(ds, reason);

        mDataCallSetupPending = true;

        /* Decide the bearer type to use..criterion is as follows:
         * 1. if IPV4 & IPV6 are both not active and if we have a data profile that supports
         * dual bearer then set bearer type as dual bearer.
         * 2. else just use the bearer type corresponding to ip version that was requested.
         */

        BearerType b = ipv == IPVersion.INET ? BearerType.IP : BearerType.IPV6;
        if (mDpt.isServiceTypeActive(ds, IPVersion.INET) == false
                && mDpt.isServiceTypeActive(ds, IPVersion.INET6) == false
                && dp.getBearerType() == BearerType.IPV4V6) {
            b = BearerType.IPV4V6;
        }

        //Assertion: dc!=null && dp!=null
        CallbackData c = new CallbackData();
        c.dc = dc;
        c.dp = dp;
        c.ds = ds;
        c.ipv = ipv;
        c.reason = reason;
        dc.connect(isCdma(getRadioTechnology()) ? RILConstants.SETUP_DATA_TECH_CDMA
                : RILConstants.SETUP_DATA_TECH_GSM, dp, b, obtainMessage(EVENT_CONNECT_DONE, c));
        return true;
    }

    private DataProfileType getDataProfileTypeToUse() {
        /*
         * For now, just return the profile based on the phone type, but this
         * can change.
         */
        if (isCdma(getRadioTechnology())) {
            return DataProfileType.PROFILE_TYPE_3GPP2_NAI;
        } else if (isGsm(getRadioTechnology())) {
            return DataProfileType.PROFILE_TYPE_3GPP_APN;
        }
        return null;
    }

    private void createDataCallList() {
        mDataConnectionList = new ArrayList<DataConnection>();
        DataConnection dc;

        for (int i = 0; i < DATA_CONNECTION_POOL_SIZE; i++) {
            dc = MMDataConnection.makeDataConnection(this.mPhone);
            mDataConnectionList.add(dc);
        }
    }

    private void destroyDataCallList() {
        if (mDataConnectionList != null) {
            mDataConnectionList.removeAll(mDataConnectionList);
        }
    }

    private MMDataConnection findFreeDataCall() {
        for (DataConnection conn : mDataConnectionList) {
            MMDataConnection dc = (MMDataConnection) conn;
            if (dc.isInactive()) {
                return dc;
            }
        }
        return null;
    }

    protected void startNetStatPoll() {
        if (mPollNetStat.isEnablePoll() == false) {
            mPollNetStat.resetPollStats();
            mPollNetStat.setEnablePoll(true);
            mPollNetStat.run();
        }
    }

    protected void stopNetStatPoll() {
        mPollNetStat.setEnablePoll(false);
        removeCallbacks(mPollNetStat);
    }

    // Retrieve the data roaming setting from the shared preferences.
    public boolean getDataOnRoamingEnabled() {
        try {
            return Settings.Secure.getInt(mPhone.getContext().getContentResolver(),
                    Settings.Secure.DATA_ROAMING) > 0;
        } catch (SettingNotFoundException snfe) {
            return false;
        }
    }

    protected boolean isConcurrentVoiceAndData() {
        return mDsst.isConcurrentVoiceAndData();
    }

    protected int getDataServiceState() {
        return mDsst.getDataServiceState();
    }

    protected boolean isDataInRoaming() {
        return mPhone.getServiceState().getRoaming();
    }

    public DataActivityState getDataActivityState() {
        DataActivityState ret = DataActivityState.NONE;
        if (getDataServiceState() == ServiceState.STATE_IN_SERVICE) {
            switch (mPollNetStat.getActivity()) {
                case DATAIN:
                    ret = DataActivityState.DATAIN;
                    break;
                case DATAOUT:
                    ret = DataActivityState.DATAOUT;
                    break;
                case DATAINANDOUT:
                    ret = DataActivityState.DATAINANDOUT;
                    break;
                case DORMANT:
                    ret = DataActivityState.DORMANT;
                    break;
            }
        }
        return ret;
    }

    public void notifyDataActivity() {
        mPhone.notifyDataActivity();
    }

    @SuppressWarnings("unchecked")
    public List<DataConnection> getCurrentDataConnectionList() {
        ArrayList<DataConnection> dcs = (ArrayList<DataConnection>) mDataConnectionList.clone();
        return dcs;
    }

    void loge(String string) {
        Slog.e(LOG_TAG, "[DCT] " + string);
    }

    void logw(String string) {
        Slog.w(LOG_TAG, "[DCT] " + string);
    }

    void logd(String string) {
        Slog.d(LOG_TAG, "[DCT] " + string);
    }

    void logv(String string) {
        Slog.v(LOG_TAG, "[DCT] " + string);
    }

    void logi(String string) {
        Log.i(LOG_TAG, "[DCT] " + string);
    }
}
