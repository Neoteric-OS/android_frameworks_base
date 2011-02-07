/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.content.SharedPreferences;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.net.IPVersion;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.data.DataProfile.DataProfileType;

/**
 * {@hide}
 */
public abstract class DataConnectionTracker extends Handler implements DataInterface {
    protected static final boolean DBG = true;

    protected final String LOG_TAG = "DATA";

    /**
     * IDLE: ready to start data connection setup, default state INITING: state
     * of issued setupDefaultPDP() but not finish yet CONNECTING: state of
     * issued startPppd() but not finish yet SCANNING: data connection fails
     * with one profile but other profiles are available ready to start data
     * connection on other profiles (before INITING) CONNECTED: IP connection is
     * setup DISCONNECTING: Connection.disconnect() has been called, but PDP
     * context is not yet de-activated FAILED: data connection fail for all data
     * profiles getDataConnectionState() maps State to DataState FAILED or IDLE
     * : DISCONNECTED INITING or CONNECTING or SCANNING: CONNECTING CONNECTED :
     * CONNECTED or DISCONNECTING
     */
    public enum State {
        IDLE, INITING, CONNECTING, SCANNING, WAITING_ALARM, CONNECTED, DISCONNECTING, FAILED
    }

    public enum Activity {
        NONE, DATAIN, DATAOUT, DATAINANDOUT, DORMANT
    }

    PhoneBase mPhone;

    DataProfileTracker mDpt;

    // set to false to disable *all* mobile data connections!
    boolean mMasterDataEnabled = true;

    boolean mDnsCheckDisabled = false;

    /***** Event Codes *****/
    protected static final int EVENT_UPDATE_DATA_CONNECTIONS = 1;

    protected static final int EVENT_SERVICE_TYPE_DISABLED = 2;

    protected static final int EVENT_SERVICE_TYPE_ENABLED = 3;

    protected static final int EVENT_DISCONNECT_DONE = 4;

    protected static final int EVENT_CONNECT_DONE = 5;

    protected static final int EVENT_VOICE_CALL_STARTED = 6;

    protected static final int EVENT_VOICE_CALL_ENDED = 7;

    protected static final int EVENT_RADIO_ON = 8;

    protected static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE = 9;

    protected static final int EVENT_DATA_CALL_LIST_CHANGED = 10;

    protected static final int EVENT_DATA_CONNECTION_ATTACHED = 11;

    protected static final int EVENT_DATA_CONNECTION_DETACHED = 12;

    protected static final int EVENT_ROAMING_ON = 13;

    protected static final int EVENT_ROAMING_OFF = 14;

    protected static final int EVENT_DATA_PROFILE_DB_CHANGED = 15;

    protected static final int EVENT_MASTER_DATA_ENABLED = 16;

    protected static final int EVENT_MASTER_DATA_DISABLED = 17;

    protected static final int EVENT_RADIO_TECHNOLOGY_CHANGED = 18;

    /* CDMA only */
    protected static final int EVENT_CDMA_OTA_PROVISION = 20;

    protected static final int EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED = 21;

    /* GSM only */
    protected static final int EVENT_PS_RESTRICT_ENABLED = 25;

    protected static final int EVENT_PS_RESTRICT_DISABLED = 26;

    protected static final int EVENT_RECORDS_LOADED = 30;

    /**
     * Default constructor
     */
    protected DataConnectionTracker(PhoneBase phone) {
        super();
        this.mPhone = phone;
        this.mDpt = new DataProfileTracker(phone.getContext());
    }

    public void dispose() {
        mDpt.dispose();
        mDpt = null;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_RADIO_ON:
                onRadioOn();
                break;

            case EVENT_RADIO_OFF_OR_NOT_AVAILABLE:
                onRadioOff();
                break;

            case EVENT_SERVICE_TYPE_DISABLED:
                onServiceTypeDisabled((DataServiceType) msg.obj);
                break;

            case EVENT_SERVICE_TYPE_ENABLED:
                onServiceTypeEnabled((DataServiceType) msg.obj);
                break;

            case EVENT_CONNECT_DONE:
                onConnectDone((AsyncResult) msg.obj);
                break;

            case EVENT_DISCONNECT_DONE:
                onDisconnectDone((AsyncResult) msg.obj);
                break;

            case EVENT_MASTER_DATA_DISABLED:
                onMasterDataDisabled();
                break;

            case EVENT_MASTER_DATA_ENABLED:
                onMasterDataEnabled();
                break;

            case EVENT_ROAMING_OFF:
                onRoamingOff();
                break;

            case EVENT_ROAMING_ON:
                onRoamingOn();
                break;

            case EVENT_VOICE_CALL_STARTED:
                onVoiceCallStarted();
                break;

            case EVENT_VOICE_CALL_ENDED:
                onVoiceCallEnded();
                break;

            default:
                Log.e(LOG_TAG, "[DCT] Unhandle event : " + msg.what);
        }
    }

    abstract protected void onServiceTypeEnabled(DataServiceType type);
    abstract protected void onServiceTypeDisabled(DataServiceType type);
    abstract protected void onDisconnectDone(AsyncResult obj);
    abstract protected void onConnectDone(AsyncResult obj);
    abstract protected void onRoamingOff();
    abstract protected void onRoamingOn();
    abstract protected void onVoiceCallStarted();
    abstract protected void onVoiceCallEnded();
    abstract protected void onRadioOn();
    abstract protected void onRadioOff();
    abstract protected void onMasterDataEnabled();
    abstract protected void onMasterDataDisabled();
    abstract protected boolean isConcurrentVoiceAndData();
    abstract protected int getDataServiceState();
    abstract protected boolean isDataInRoaming();
    abstract public void setDataConnectionAsDesired(boolean desiredPowerState,
            Message onCompleteMsg);
    synchronized public int disableApnType(String type) {

        DataServiceType serviceType = DataServiceType.apnTypeStringToServiceType(type);
        if (serviceType == null) {
            // unknown apn type!
            return APN_REQUEST_FAILED;
        }

        if (mDpt.isServiceTypeEnabled(serviceType) == false
                || mDpt.isServiceTypeActive(serviceType) == false) {
            // service type is already inactive or not enabled at all.
            // TODO: is APN_REQUEST_FAILED appropriate? or should it be
            // APN_REQUEST_STARTED?

            /* send out disconnected notifications - no harm doing this */
            notifyDataConnection(serviceType, REASON_SERVICE_TYPE_DISABLED);

            return APN_REQUEST_FAILED;
        }

        /* mark service type as disabled */
        mDpt.setServiceTypeEnabled(serviceType, false);

        sendMessage(obtainMessage(EVENT_SERVICE_TYPE_DISABLED, serviceType));

        return APN_REQUEST_STARTED;
    }

    /*
     * (non-Javadoc)
     * @see
     * com.android.internal.telephony.DataPhone#enableApnType(java.lang.String)
     * Application has no way to request INET or INET6 to be enabled, so we
     * enable both depending on whether supported data profiles are available.
     */
    synchronized public int enableApnType(String type) {

        DataServiceType serviceType = DataServiceType.apnTypeStringToServiceType(type);
        if (serviceType == null) {
            // unknown apn type!
            return APN_REQUEST_FAILED;
        }

        /* mark service type as enabled */
        mDpt.setServiceTypeEnabled(serviceType, true);

        if (mDpt.isServiceTypeActive(serviceType) == true) {

            // service type is already active, send out notifications!
            notifyDataConnection(serviceType, REASON_SERVICE_TYPE_ENABLED);

            /*
             * do an update data connections, just in case it was active only on
             * one IP version and not other.
             */
            sendMessage(obtainMessage(EVENT_SERVICE_TYPE_ENABLED, serviceType));

            return APN_ALREADY_ACTIVE;
        }

        sendMessage(obtainMessage(EVENT_SERVICE_TYPE_ENABLED, serviceType));

        return APN_REQUEST_STARTED;
    }

    /*
     * (non-Javadoc)
     * @see com.android.internal.telephony.DataPhone#disableDataConnectivity()
     * Disable ALL data!
     */
    public boolean disableDataConnectivity() {
        mMasterDataEnabled = false;
        sendMessage(obtainMessage(EVENT_MASTER_DATA_DISABLED));
        return true;
    }

    public boolean enableDataConnectivity() {
        mMasterDataEnabled = true;
        sendMessage(obtainMessage(EVENT_MASTER_DATA_ENABLED));
        return true;
    }

    public boolean isDataConnectivityEnabled() {
        return mMasterDataEnabled;
    }

    /*
     * TODO: This API should be deprecated or something. The way this is done
     * now, states don't get combined correctly. The only reason for this to
     * stick around is because its part of TelephonyManager and UI seems to be
     * using this to display mobile data connection icon?
     */
    public DataState getDataConnectionState() {
        /*
         * return state as CONNECTED, if at least one data connection is active
         * on either INET or INET6.
         */
        DataState ret = DataState.DISCONNECTED;
        if (getDataServiceState() != ServiceState.STATE_IN_SERVICE) {
            // If we're out of service, open TCP sockets may still work
            // but no data will flow
            ret = DataState.DISCONNECTED;
        } else {
            for (DataServiceType ds : DataServiceType.values()) {
                if (mDpt.getState(ds, IPVersion.INET) == State.CONNECTED
                        || mDpt.getState(ds, IPVersion.INET6) == State.CONNECTED) {
                    ret = DataState.CONNECTED;
                    break;
                }
            }
        }
        return ret;
    }

    public DataState getDataConnectionState(String apnType, IPVersion ipv) {

        DataServiceType ds = DataServiceType.apnTypeStringToServiceType(apnType);
        if (ds == null || ipv == null)
            return DataState.DISCONNECTED;

        DataState ret = DataState.DISCONNECTED;

        State dsState = mDpt.getState(ds, ipv);

        if (getDataServiceState() != ServiceState.STATE_IN_SERVICE) {
            // If we're out of service, open TCP sockets may still work
            // but no data will flow
            ret = DataState.DISCONNECTED;
        } else {
            switch (dsState) {
                case FAILED:
                case IDLE:
                    ret = DataState.DISCONNECTED;
                    break;

                case CONNECTED:
                case DISCONNECTING:
                    if (TelephonyManager.getDefault().getCallState()
                            != TelephonyManager.CALL_STATE_IDLE
                            && !isConcurrentVoiceAndData()) {
                        ret = DataState.SUSPENDED;
                    } else {
                        ret = DataState.CONNECTED;
                    }
                    break;

                case INITING:
                case CONNECTING:
                case SCANNING:
                    ret = DataState.CONNECTING;
                    break;
            }
        }

        return ret;
    }

    public String getActiveApn(String apnType, IPVersion ipv) {
        DataServiceType serviceType = DataServiceType.apnTypeStringToServiceType(apnType);
        if (serviceType == null || ipv == null)
            return null;

        DataConnection dc = mDpt.getActiveDataConnection(serviceType, ipv);
        if (dc == null)
            return null;

        DataProfile dp = dc.getDataProfile();
        if (dp != null && dp.getDataProfileType() == DataProfileType.PROFILE_TYPE_3GPP_APN) {
            return ((ApnSetting) dp).apn.toString();
        }

        return null;
    }

    public String[] getActiveApnTypes() {
        ArrayList<String> result = new ArrayList<String>();
        for (DataServiceType ds : DataServiceType.values()) {
            if (mDpt.isServiceTypeActive(ds))
                result.add(ds.toApnTypeString());
        }
        String[] ret = new String[result.size()];
        return (String[]) result.toArray(ret);
    }

    // The data roaming setting is now located in the shared preferences.
    // See if the requested preference value is the same as that stored in
    // the shared values. If it is not, then update it.
    public void setDataRoamingEnabled(boolean enabled) {
        if (getDataRoamingEnabled() != enabled) {
            Settings.Secure.putInt(mPhone.getContext().getContentResolver(),
                    Settings.Secure.DATA_ROAMING, enabled ? 1 : 0);
            if (isDataInRoaming()) {
                sendMessage(obtainMessage(EVENT_ROAMING_ON));
            }
        }
    }

    // Retrieve the data roaming setting from the shared preferences.
    public boolean getDataRoamingEnabled() {
        try {
            return Settings.Secure.getInt(mPhone.getContext().getContentResolver(),
                    Settings.Secure.DATA_ROAMING) > 0;
        } catch (SettingNotFoundException snfe) {
            return false;
        }
    }

    public String[] getDnsServers(String apnType) {
        String[] v4List = getDnsServers(apnType, IPVersion.INET);
        String[] v6List = getDnsServers(apnType, IPVersion.INET6);

        /* combine v4+v6 list */

        String[] result = new String[v4List.length + v6List.length];
        System.arraycopy(v4List, 0, result, 0, v4List.length);
        System.arraycopy(v6List, 0, result, v4List.length, v6List.length);

        return result;
    }

    public String[] getDnsServers(String apnType, IPVersion ipv) {
        DataServiceType serviceType = DataServiceType.apnTypeStringToServiceType(apnType);
        if (serviceType == null || ipv == null)
            return null;

        DataConnection dc = mDpt.getActiveDataConnection(serviceType, ipv);
        if (dc != null) {
            return dc.getDnsServers().clone();
        }

        return null;
    }

    public String getGateway(String apnType, IPVersion ipv) {
        DataServiceType serviceType = DataServiceType.apnTypeStringToServiceType(apnType);
        if (serviceType == null || ipv == null)
            return null;

        DataConnection dc = mDpt.getActiveDataConnection(serviceType, ipv);
        if (dc != null) {
            return dc.getGatewayAddress();
        }

        return null;
    }

    public String getInterfaceName(String apnType, IPVersion ipv) {
        DataServiceType serviceType = DataServiceType.apnTypeStringToServiceType(apnType);
        if (serviceType == null || ipv == null)
            return null;

        DataConnection dc = mDpt.getActiveDataConnection(serviceType, ipv);
        if (dc != null) {
            return dc.getInterface();
        }

        return null;
    }

    public String[] getIpAddress(String apnType) {
        String[] v4List = getIpAddress(apnType, IPVersion.INET);
        String[] v6List = getIpAddress(apnType, IPVersion.INET6);

        /* combine v4+v6 list */

        String[] result = new String[v4List.length + v6List.length];
        System.arraycopy(v4List, 0, result, 0, v4List.length);
        System.arraycopy(v6List, 0, result, v4List.length, v6List.length);

        return result;
    }

    public String[] getIpAddress(String apnType, IPVersion ipv) {
        DataServiceType serviceType = DataServiceType.apnTypeStringToServiceType(apnType);
        if (serviceType == null || ipv == null)
            return null;

        DataConnection dc = mDpt.getActiveDataConnection(serviceType, ipv);
        if (dc != null) {
            return dc.getIpAddressList();
        }

        return null;
    }

    public boolean isServiceTypeActiveOnDualBearerInterface(String apnType) {

        DataServiceType serviceType = DataServiceType.apnTypeStringToServiceType(apnType);
        if (serviceType == null)
            return false;

        if (mDpt.isServiceTypeActive(serviceType)) {
            return mDpt.getActiveDataConnection(serviceType, IPVersion.INET) == mDpt
                    .getActiveDataConnection(serviceType, IPVersion.INET6);
        }

        return false;
    }

    void notifyDataConnection(DataServiceType ds, String reason) {
        // TODO: Connectivity Service and above doesn't know about IPV6+MPDP
        // just yet
        // so just make it work.
        mPhone.notifyDataConnection(reason);
    }

    protected void notifyAllDataServiceTypes(String reason) {
        for (DataServiceType ds : DataServiceType.values()) {
            notifyDataConnection(ds, reason);
        }
    }

    // notify data connection as failed - applicable for default type only???
    void notifyDataConnectionFail(String reason) {
        mPhone.notifyDataConnection(reason);
    }

    public void getDataCallList(Message response) {
        mPhone.mCM.getDataCallList(response);
    }

    // Key used to read/write "disable DNS server check" pref (used for testing)
    public static final String DNS_SERVER_CHECK_DISABLED_KEY = "dns_server_check_disabled_key";

    /**
     * Disables the DNS check (i.e., allows "0.0.0.0"). Useful for lab testing
     * environment.
     *
     * @param b true disables the check, false enables.
     */
    public void disableDnsCheck(boolean b) {
        mDnsCheckDisabled = b;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mPhone.getContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean(DNS_SERVER_CHECK_DISABLED_KEY, b);
        editor.commit();
    }

    /**
     * Returns true if the DNS check is currently disabled.
     */
    public boolean isDnsCheckDisabled() {
        return mDnsCheckDisabled;
    }

    public void setState(State dcState) {
        //do nothing - simulator might be broken..
        //TODO: fix this.
    }
}
