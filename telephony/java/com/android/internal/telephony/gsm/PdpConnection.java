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

package com.android.internal.telephony.gsm;

import android.os.*;
import android.util.EventLog;
import android.util.Log;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.DataConnection;
import com.android.internal.telephony.DataLink;
import com.android.internal.telephony.RILConstants;

/**
 * {@hide}
 */
public class PdpConnection extends DataConnection {

    private static final String LOG_TAG = "GSM";
    private static final boolean DBG  = true;
    private static final boolean FAKE_FAIL = false;

    /** Fail cause of last PDP activate, from RIL_LastPDPActivateFailCause */
    private static final int PDP_FAIL_RIL_BARRED = 8;
    private static final int PDP_FAIL_RIL_BAD_APN = 27;
    private static final int PDP_FAIL_RIL_USER_AUTHENTICATION = 29;
    private static final int PDP_FAIL_RIL_SERVICE_OPTION_NOT_SUPPORTED = 32;
    private static final int PDP_FAIL_RIL_SERVICE_OPTION_NOT_SUBSCRIBED = 33;
    private static final int PDP_FAIL_RIL_ERROR_UNSPECIFIED = 0xffff;

    //***** Tag IDs for EventLog
    private static final int EVENT_LOG_BAD_DNS_ADDRESS = 50100;

    //***** Instance Variables
    private GSMPhone phone;
    private String pdp_name;
    private Message onConnectCompleted;
    private Message onDisconnect;
    private int cid;
    private String interfaceName;
    private String ipAddress;
    private String gatewayAddress;
    private String[] dnsServers;
    protected ApnSetting apn;

    private static final String NULL_IP = "0.0.0.0";

    // dataLink is only used to support pppd link
    DataLink dataLink;
    // receivedDisconnectReq is set when disconnect pdp link during activating
    private boolean receivedDisconnectReq;

    //***** Constructor
    PdpConnection(GSMPhone phone) {
        super("GSM", phone);
        
        this.phone = phone;
        onConnectCompleted = null;
        onDisconnect = null;
        this.cid = -1;
        this.dataLink = null;
        receivedDisconnectReq = false;
        this.dnsServers = new String[2];
        clearSettings();

        if (SystemProperties.get("ro.radio.use-ppp","no").equals("yes")) {
            dataLink = new PppLink(
                    (GsmDataConnectionTracker) phone.mDataConnection);
            dataLink.setOnLinkChange(this, EVENT_LINK_STATE_CHANGED, null);
        }
    }

    /**
     * Setup PDP connection for provided apn
     * @param apn for this connection
     * @param onCompleted notify success or not after down
     */
    void connect(ApnSetting apn, Message onCompleted) {
        if (DBG) log("Connecting to carrier: '" + apn.carrier
                + "' APN: '" + apn.apn
                + "' proxy: '" + apn.proxy + "' port: '" + apn.port);

        setHttpProxy (apn.proxy, apn.port);

        state = State.ACTIVATING;
        this.apn = apn;
        onConnectCompleted = onCompleted;
        createTime = -1;
        lastFailTime = -1;
        lastFailCause = FailCause.NONE;
        receivedDisconnectReq = false;

        if (FAKE_FAIL) {
            // for debug before baseband implement error in setup PDP
            if (apn.apn.equalsIgnoreCase("badapn")){
                notifyFail(FailCause.BAD_APN, onConnectCompleted);
                return;
            }
        }

        phone.mCM.setupDataCall(Integer.toString(RILConstants.GSM_PHONE), null, apn.apn, apn.user, 
                apn.password, obtainMessage(EVENT_SETUP_DATA_CONNECTION_DONE));
    }

    protected void disconnect(Message msg) {
        onDisconnect = msg;
        if (state == State.ACTIVE) {
            if (dataLink != null) {
                dataLink.disconnect();
            }

            if (phone.mCM.getRadioState().isOn()) {
                phone.mCM.deactivateDataCall(cid, obtainMessage(EVENT_DEACTIVATE_DONE, msg));
            }
        } else if (state == State.ACTIVATING) {
            receivedDisconnectReq = true;
        }
    }

    private void setHttpProxy(String httpProxy, String httpPort) {
        if (httpProxy == null || httpProxy.length() == 0) {
            phone.setSystemProperty("net.gprs.http-proxy", null);
            return;
        }

        if (httpPort == null || httpPort.length() == 0) {
            httpPort = "8080";     // Default to port 8080
        }

        phone.setSystemProperty("net.gprs.http-proxy",
                "http://" + httpProxy + ":" + httpPort + "/");
    }

    public String toString() {
        return "State=" + state + " Apn=" + apn +
               " create=" + createTime + " lastFail=" + lastFailTime +
               " lastFailCause=" + lastFailCause;
    }

    protected String getInterface() {
        return interfaceName;
    }

    protected String getIpAddress() {
        return ipAddress;
    }

    protected String getGatewayAddress() {
        return gatewayAddress;
    }

    protected String[] getDnsServers() {
        return dnsServers;
    }

    private void notifyFail(FailCause cause, Message onCompleted) {
        if (onCompleted == null) return;

        state = State.INACTIVE;
        lastFailCause = cause;
        lastFailTime = System.currentTimeMillis();
        onConnectCompleted = null;

        if (DBG) log("Notify PDP fail at " + lastFailTime
                + " due to " + lastFailCause);

        AsyncResult.forMessage(onCompleted, cause, new Exception());
        onCompleted.sendToTarget();
    }

    private void notifySuccess(Message onCompleted) {
        if (onCompleted == null) return;

        state = State.ACTIVE;
        createTime = System.currentTimeMillis();
        onConnectCompleted = null;
        onCompleted.arg1 = cid;

        if (DBG) log("Notify PDP success at " + createTime);

        AsyncResult.forMessage(onCompleted);
        onCompleted.sendToTarget();
    }

    private void notifyDisconnect(Message msg) {
        if (DBG) log("Notify PDP disconnect");

        if (msg != null) {
            AsyncResult.forMessage(msg);
            msg.sendToTarget();
        }
        clearSettings();
    }

    protected void clearSettings() {
        super.clearSettings();

        receivedDisconnectReq = false;
        onConnectCompleted = null;
        interfaceName = null;
        ipAddress = null;
        gatewayAddress = null;
        dnsServers[0] = null;
        dnsServers[1] = null;
    }

    private void onLinkStateChanged(DataLink.LinkState linkState) {
        switch (linkState) {
            case LINK_UP:
                notifySuccess(onConnectCompleted);
                break;

            case LINK_DOWN:
            case LINK_EXITED:
                phone.mCM.getLastPdpFailCause(
                        obtainMessage (EVENT_GET_LAST_FAIL_DONE));
                break;
        }
    }

    private FailCause getFailCauseFromRequest(int rilCause) {
        FailCause cause;

        switch (rilCause) {
            case PDP_FAIL_RIL_BARRED:
                cause = FailCause.BARRED;
                break;
            case PDP_FAIL_RIL_BAD_APN:
                cause = FailCause.BAD_APN;
                break;
            case PDP_FAIL_RIL_USER_AUTHENTICATION:
                cause = FailCause.USER_AUTHENTICATION;
                break;
            case PDP_FAIL_RIL_SERVICE_OPTION_NOT_SUPPORTED:
                cause = FailCause.SERVICE_OPTION_NOT_SUPPORTED;
                break;
            case PDP_FAIL_RIL_SERVICE_OPTION_NOT_SUBSCRIBED:
                cause = FailCause.SERVICE_OPTION_NOT_SUBSCRIBED;
                break;
            default:
                cause = FailCause.UNKNOWN;
        }
        return cause;
    }


    private void log(String s) {
        Log.d(LOG_TAG, "[PdpConnection] " + s);
    }

    @Override
    protected void onDeactivated(AsyncResult ar) {
        notifyDisconnect((Message) ar.userObj);
        if (DBG) log("PDP Connection Deactivated");
    }

    @Override
    protected void onForceRetry() {
        if (receivedDisconnectReq) {
            notifyDisconnect(onDisconnect);
        } else {
            notifyFail(FailCause.RADIO_ERROR_RETRY, onConnectCompleted);
        }
    }

    @Override
    protected void onGetLastFailCompleted(AsyncResult ar) {
        if (receivedDisconnectReq) {
            // Don't bother reporting the error if there's already a
            // pending disconnect request, since DataConnectionTracker
            // has already updated its state.
            notifyDisconnect(onDisconnect);
        } else {
            FailCause cause = FailCause.UNKNOWN;

            if (ar.exception == null) {
                int rilFailCause = ((int[]) (ar.result))[0];
                cause = getFailCauseFromRequest(rilFailCause);
            }
            notifyFail(cause, onConnectCompleted);
        }
    }

    @Override
    protected void onLinkStateChanged(AsyncResult ar) {
        DataLink.LinkState ls  = (DataLink.LinkState) ar.result;
        onLinkStateChanged(ls);
    }

    @Override
    protected void onSetupConnectionCompleted(AsyncResult ar) {
        if (ar.exception != null) {
            Log.e(LOG_TAG, "PDP Context Init failed " + ar.exception);
            
            if (receivedDisconnectReq) {
                // Don't bother reporting the error if there's already a
                // pending disconnect request, since DataConnectionTracker
                // has already updated its state.
                notifyDisconnect(onDisconnect);
            } else {
                if ( ar.exception instanceof CommandException &&
                        ((CommandException) (ar.exception)).getCommandError()
                        == CommandException.Error.RADIO_NOT_AVAILABLE) {
                    notifyFail(FailCause.RADIO_NOT_AVAILABLE,
                            onConnectCompleted);
                } else {
                    phone.mCM.getLastPdpFailCause(
                            obtainMessage(EVENT_GET_LAST_FAIL_DONE));
                }
            }
        } else {
            if (receivedDisconnectReq) {
                // Don't bother reporting success if there's already a
                // pending disconnect request, since DataConnectionTracker
                // has already updated its state.
                disconnect(onDisconnect);
            } else {
                String[] response = ((String[]) ar.result);
                cid = Integer.parseInt(response[0]);
                
                if (response.length > 2) {
                    interfaceName = response[1];
                    ipAddress = response[2];
                    String prefix = "net." + interfaceName + ".";
                    gatewayAddress = SystemProperties.get(prefix + "gw");
                    dnsServers[0] = SystemProperties.get(prefix + "dns1");
                    dnsServers[1] = SystemProperties.get(prefix + "dns2");
                    if (DBG) {
                        log("interface=" + interfaceName + " ipAddress=" + ipAddress
                            + " gateway=" + gatewayAddress + " DNS1=" + dnsServers[0]
                            + " DNS2=" + dnsServers[1]);
                    }

                    if (NULL_IP.equals(dnsServers[0]) && NULL_IP.equals(dnsServers[1])) {
                        // Work around a race condition where QMI does not fill in DNS:
                        // Deactivate PDP and let DataConnectionTracker retry.
                        EventLog.writeEvent(EVENT_LOG_BAD_DNS_ADDRESS, dnsServers[0]);
                        phone.mCM.deactivateDataCall(cid, obtainMessage(EVENT_FORCE_RETRY));
                        return;
                    }
                }

                if (dataLink != null) {
                    dataLink.connect();
                } else {
                    onLinkStateChanged(DataLink.LinkState.LINK_UP);
                }

                if (DBG) log("PDP setup on cid = " + cid);
            }
        }
    }

    public ApnSetting getApn() {
        return this.apn;
    }
}
