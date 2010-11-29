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

import java.util.Arrays;

import android.os.AsyncResult;
import android.os.Message;
import android.os.SystemProperties;
import android.util.EventLog;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.data.DataInterface.BearerType;
import com.android.internal.util.HierarchicalState;
import com.android.internal.util.HierarchicalStateMachine;

/**
 * {@hide}
 *
 * DataConnection HierarchicalStateMachine.
 *
 * This is an abstract base class for representing a single data connection.
 * Instances of this class such as <code>CdmaDataConnection</code> and
 * <code>GsmDataConnection</code>, * represent a connection via the cellular network.
 * There may be multiple data connections and all of them are managed by the
 * <code>DataConnectionTracker</code>.
 *
 * Instances are asynchronous state machines and have two primary entry points
 * <code>connect()</code> and <code>disconnect</code>. The message a parameter will be returned
 * hen the operation completes. The <code>msg.obj</code> will contain an AsyncResult
 * object and <code>AsyncResult.userObj</code> is the original <code>msg.obj</code>. if successful
 * with the <code>AsyncResult.result == null</code> and <code>AsyncResult.exception == null</code>.
 * If an error <code>AsyncResult.result = FailCause</code> and
 * <code>AsyncResult.exception = new Exception()</code>.
 *
 * The other public methods are provided for debugging.
 *
 * Below is the state machine description for this class.
 *
 * DataConnection {
 *   + mDefaultState {
 *        EVENT_RESET { clearSettings, notifiyDisconnectCompleted, >mInactiveState }.
 *        EVENT_CONNECT {  notifyConnectCompleted(FailCause.UNKNOWN) }.
 *        EVENT_DISCONNECT { notifyDisconnectCompleted }.
 *
 *        // Ignored messages
 *        EVENT_SETUP_DATA_CONNECTION_DONE,
 *        EVENT_GET_LAST_FAIL_DONE,
 *        EVENT_DEACTIVATE_DONE.
 *     }
 *   ++ # mInactiveState 
 *        e(doNotifications)
 *        x(clearNotifications) {
 *            EVENT_RESET { notifiyDisconnectCompleted }.
 *            EVENT_CONNECT {startConnecting, >mActivatingState }.
 *        }
 *   ++   mActivatingState {
 *            EVENT_DISCONNECT { %EVENT_DISCONNECT }.
 *            EVENT_SETUP_DATA_CONNECTION_DONE {
 *                  if (SUCCESS) { notifyConnectCompleted(FailCause.NONE), >mActiveState }.
 *                  if (ERR_BadCommand) {
 *                         notifyConnectCompleted(FailCause.UNKNOWN), >mInactiveState }.
 *                  if (ERR_BadDns) { tearDownData($DEACTIVATE_DONE), >mDisconnectingBadDnsState }.
 *                  if (ERR_Other) { notifyConnectCompleted(result), >mInactive }.
 *                  if (ERR_Stale) {}.
 *            }
 *        }
 *   ++   mActiveState {
 *            EVENT_DISCONNECT { tearDownData($EVENT_DEACTIVATE_DONE), >mDisconnecting }.
 *        }
 *   ++   mDisconnectingState {
 *            EVENT_DEACTIVATE_DONE { notifyDisconnectCompleted, >mInactiveState }.
 *        }
 *   ++   mDisconnectingBadDnsState {
 *            EVENT_DEACTIVATE_DONE { notifyConnectComplete(FailCause.UNKNOWN), >mInactiveState }.
 *        }
 *  }
 */
public abstract class DataConnection extends HierarchicalStateMachine {
    protected static final boolean DBG = true;

    protected static Object mCountLock = new Object();
    protected static int mCount;

    /**
     * Class returned by onSetupConnectionCompleted.
     */
    protected enum SetupResult {
        ERR_BadCommand,
        ERR_BadDns,
        ERR_Other,
        ERR_Stale,
        SUCCESS;

        public FailCause mFailCause;

        @Override
        public String toString() {
            switch (this) {
                case ERR_BadCommand: return "Bad Command";
                case ERR_BadDns: return "Bad DNS";
                case ERR_Other: return "Other error";
                case ERR_Stale: return "Stale command";
                case SUCCESS: return "SUCCESS";
                default: return "unknown";
            }
        }
    }

    /**
     * Used internally for saving connecting parameters.
     */
    protected static class ConnectionParams {
        public ConnectionParams(int radioTech, DataProfile dp, BearerType bearerType, Message onCompletedMsg) {
            this.radioTech = radioTech;
            this.dp = dp;
            this.bearerType = bearerType;
            this.onCompletedMsg = onCompletedMsg;
        }

        public int tag;
        public int radioTech;
        public DataProfile dp;
        public BearerType bearerType;
        public Message onCompletedMsg;
    }

    /**
     * An instance used for notification of blockingReset.
     * TODO: Remove when blockingReset is removed.
     */
    class ResetSynchronouslyLock {
    }

    /**
     * Used internally for saving disconnecting parameters.
     */
    protected static class DisconnectParams {
        public DisconnectParams(Message onCompletedMsg) {
            this.onCompletedMsg = onCompletedMsg;
        }
        public DisconnectParams(ResetSynchronouslyLock lockObj) {
            this.lockObj = lockObj;
        }

        public int tag;
        public Message onCompletedMsg;
        public ResetSynchronouslyLock lockObj;
    }

    /**
     * Returned as the reason for a connection failure.
     */
    public enum FailCause {
        NONE,
        OPERATOR_BARRED,
        INSUFFICIENT_RESOURCES,
        MISSING_UNKNOWN_APN,
        UNKNOWN_PDP_ADDRESS,
        USER_AUTHENTICATION,
        ACTIVATION_REJECT_GGSN,
        ACTIVATION_REJECT_UNSPECIFIED,
        SERVICE_OPTION_NOT_SUPPORTED,
        SERVICE_OPTION_NOT_SUBSCRIBED,
        SERVICE_OPTION_OUT_OF_ORDER,
        NSAPI_IN_USE,
        ONLY_IPV4_ALLOWED,
        ONLY_IPV6_ALLOWED,
        ONLY_SINGLE_BEARER_ALLOWED,
        PROTOCOL_ERRORS,
        REGISTRATION_FAIL,
        GPRS_REGISTRATION_FAIL,
        PREF_RADIO_TECH_CHANGED,
        RADIO_POWER_OFF,
        TETHERED_CALL_ACTIVE,
        PDP_NOT_AVAILABLE,
        NETWORK_OR_MODEM_DISCONNECT,
        UNKNOWN,

        RADIO_NOT_AVAILABLE;

        public boolean isPermanentFail() {
            return (this == OPERATOR_BARRED) || (this == MISSING_UNKNOWN_APN) ||
                   (this == UNKNOWN_PDP_ADDRESS) || (this == USER_AUTHENTICATION) ||
                   (this == ACTIVATION_REJECT_GGSN) || (this == ACTIVATION_REJECT_UNSPECIFIED) ||
                   (this == SERVICE_OPTION_NOT_SUPPORTED) ||
                   (this == SERVICE_OPTION_NOT_SUBSCRIBED) || (this == NSAPI_IN_USE) ||
                   (this == PROTOCOL_ERRORS) ||  (this == RADIO_POWER_OFF) ||
                   (this == PREF_RADIO_TECH_CHANGED);
        }

        public boolean isEventLoggable() {
            return (this == OPERATOR_BARRED) || (this == INSUFFICIENT_RESOURCES) ||
                    (this == UNKNOWN_PDP_ADDRESS) || (this == USER_AUTHENTICATION) ||
                    (this == ACTIVATION_REJECT_GGSN) || (this == ACTIVATION_REJECT_UNSPECIFIED) ||
                    (this == SERVICE_OPTION_NOT_SUBSCRIBED) ||
                    (this == SERVICE_OPTION_NOT_SUPPORTED) ||
                    (this == SERVICE_OPTION_OUT_OF_ORDER) || (this == NSAPI_IN_USE) ||
                    (this == PROTOCOL_ERRORS);
        }

        public boolean isPartialFailure() {
            return (this == ONLY_IPV4_ALLOWED || this == ONLY_IPV6_ALLOWED
                    || this == ONLY_SINGLE_BEARER_ALLOWED);
        }

        public boolean ipVersionNotSupported() {
            return (this == ONLY_IPV4_ALLOWED || this == ONLY_IPV6_ALLOWED);
        }

        /*
         * indicates that setup failure is caused by some sort of data profile
         * issues
         */
        public boolean isDataProfileFailure() {
            return (this == MISSING_UNKNOWN_APN) || (this == USER_AUTHENTICATION);
        }

       /* indicates that setup failure is caused by lack of network resources,
        * network supports no mpdp, or limited pdp etc. it could also be because
        * of tethered mode call being active.
        */
       public boolean isPdpAvailabilityFailure() {
           return (this == PDP_NOT_AVAILABLE);
       }

        @Override
        public String toString() {
            switch (this) {
            case NONE:
                return "No Error";
            case OPERATOR_BARRED:
                return "Operator Barred";
            case INSUFFICIENT_RESOURCES:
                return "Insufficient Resources";
            case MISSING_UNKNOWN_APN:
                return "Missing / Unknown APN";
            case UNKNOWN_PDP_ADDRESS:
                return "Unknown PDP Address";
            case USER_AUTHENTICATION:
                return "Error User Authentication";
            case ACTIVATION_REJECT_GGSN:
                return "Activation Reject GGSN";
            case ACTIVATION_REJECT_UNSPECIFIED:
                return "Activation Reject unspecified";
            case SERVICE_OPTION_NOT_SUPPORTED:
                return "Data Not Supported";
            case SERVICE_OPTION_NOT_SUBSCRIBED:
                return "Data Not subscribed";
            case SERVICE_OPTION_OUT_OF_ORDER:
                return "Data Services Out of Order";
            case NSAPI_IN_USE:
                return "NSAPI in use";
            case PROTOCOL_ERRORS:
                return "Protocol Errors";
            case REGISTRATION_FAIL:
                return "Network Registration Failure";
            case GPRS_REGISTRATION_FAIL:
                return "Data Network Registration Failure";
            case RADIO_NOT_AVAILABLE:
                return "Radio Not Available";
            case ONLY_IPV4_ALLOWED:
                return "Only IPv4 allowed";
            case ONLY_IPV6_ALLOWED:
                return "Only IPv6 allowed";
            case ONLY_SINGLE_BEARER_ALLOWED:
                return "Only single address bearers allowed";
            default:
                return "Unknown Data Error";
            }
        }
    }

    // ***** Event codes for driving the state machine
    protected static final int EVENT_RESET = 1;
    protected static final int EVENT_CONNECT = 2;
    protected static final int EVENT_SETUP_DATA_CONNECTION_DONE = 3;
    protected static final int EVENT_DEACTIVATE_DONE = 4;
    protected static final int EVENT_DISCONNECT = 5;

    //***** Tag IDs for EventLog
    protected static final int EVENT_LOG_BAD_DNS_ADDRESS = 50100;

    //***** Member Variables
    protected int mTag;
    protected PhoneBase phone;
    protected int cid;
    protected DataProfile mDataProfile;
    protected BearerType mBearerType;
    protected String interfaceName;
    protected String[] ipAddressList;
    protected String gatewayAddress;
    protected String[] dnsServers;
    protected long createTime;
    protected long lastFailTime;
    protected FailCause lastFailCause;
    protected static final String NULL_IP = "0.0.0.0";
    Object userData;

    //***** Abstract methods
    public abstract String toString();

    protected abstract void onConnect(ConnectionParams cp);

    protected abstract FailCause getFailCauseFromRequest(int rilCause);

    protected abstract boolean isDnsOk(String[] domainNameServers);

    protected abstract void log(String s);


   //***** Constructor
    protected DataConnection(PhoneBase phone, String name) {
        super(name);
        if (DBG) log("DataConnection constructor E");
        this.phone = phone;
        this.cid = -1;
        this.dnsServers = new String[2];

        clearSettings();

        setDbg(false);
        addState(mDefaultState);
            addState(mInactiveState, mDefaultState);
            addState(mActivatingState, mDefaultState);
            addState(mActiveState, mDefaultState);
            addState(mDisconnectingState, mDefaultState);
            addState(mDisconnectingBadDnsState, mDefaultState);
        setInitialState(mInactiveState);
        if (DBG) log("DataConnection constructor X");
    }

    /**
     * TearDown the data connection.
     *
     * @param o will be returned in AsyncResult.userObj
     *          and is either a DisconnectParams or ConnectionParams.
     */
    private void tearDownData(Object o) {
        if (phone.mCM.getRadioState().isOn()) {
            if (DBG) log("tearDownData radio is on, call deactivateDataCall");
            phone.mCM.deactivateDataCall(cid, obtainMessage(EVENT_DEACTIVATE_DONE, o));
        } else {
            if (DBG) log("tearDownData radio is off sendMessage EVENT_DEACTIVATE_DONE immediately");
            AsyncResult ar = new AsyncResult(o, null, null);
            sendMessage(obtainMessage(EVENT_DEACTIVATE_DONE, ar));
        }
    }

    /**
     * Send the connectionCompletedMsg.
     *
     * @param cp is the ConnectionParams
     * @param cause
     */
    private void notifyConnectCompleted(ConnectionParams cp, FailCause cause) {
        Message connectionCompletedMsg = cp.onCompletedMsg;
        if (connectionCompletedMsg == null) {
            return;
        }

        long timeStamp = System.currentTimeMillis();
        connectionCompletedMsg.arg1 = cid;

        if (cause == FailCause.NONE || cause.isPartialFailure()) {
            createTime = timeStamp;
            AsyncResult.forMessage(connectionCompletedMsg, cause, null);
        } else {
            lastFailCause = cause;
            lastFailTime = timeStamp;
            AsyncResult.forMessage(connectionCompletedMsg, cause, new Exception());
        }
        if (DBG) log("notifyConnection at " + timeStamp + " cause=" + cause);

        connectionCompletedMsg.sendToTarget();
    }

    /**
     * Send ar.userObj if its a message, which is should be back to originator.
     *
     * @param dp is the DisconnectParams.
     */
    private void notifyDisconnectCompleted(DisconnectParams dp) {
        if (DBG) log("NotifyDisconnectCompleted");

        if (dp.onCompletedMsg != null) {
            Message msg = dp.onCompletedMsg;
            log(String.format("msg.what=%d msg.obj=%s",
                    msg.what, ((msg.obj instanceof String) ? (String) msg.obj : "<no-reason>")));
            AsyncResult.forMessage(msg);
            msg.sendToTarget();
        }
        if (dp.lockObj != null) {
            synchronized(dp.lockObj) {
                dp.lockObj.notify();
            }
        }

        clearSettings();
    }

    /**
     * Clear all settings called when entering mInactiveState.
     */
    protected void clearSettings() {
        if (DBG) log("clearSettings");

        this.createTime = -1;
        this.lastFailTime = -1;
        this.lastFailCause = FailCause.NONE;

        mDataProfile = null;
        mBearerType = null;
        interfaceName = null;
        ipAddressList = null;
        gatewayAddress = null;
        dnsServers[0] = null;
        dnsServers[1] = null;
    }

    /**
     * Process setup completion.
     *
     * @param ar is the result
     * @return SetupResult.
     */
    private SetupResult onSetupConnectionCompleted(AsyncResult ar) {
        SetupResult result;
        String[] response = ((String[]) ar.result);
        ConnectionParams cp = (ConnectionParams) ar.userObj;

        if (ar.exception != null) {
            if (DBG) log("DataConnection Init failed " + ar.exception);

            if (ar.exception instanceof CommandException
                    && ((CommandException) (ar.exception)).getCommandError()
                    == CommandException.Error.RADIO_NOT_AVAILABLE) {
                result = SetupResult.ERR_BadCommand;
                result.mFailCause = FailCause.RADIO_NOT_AVAILABLE;
            } else if (ar.exception instanceof CommandException
                    && ((CommandException)(ar.exception)).getCommandError()
                    == CommandException.Error.SETUP_DATA_CALL_FAILURE) {
                result = SetupResult.ERR_Other;
                int rilFailCause = ((int[]) (ar.result))[0];
                result.mFailCause = getFailCauseFromRequest(rilFailCause);
            } else {
                result = SetupResult.ERR_Other;
                result.mFailCause = FailCause.UNKNOWN;
            }
        } else if (cp.tag != mTag) {
            if (DBG) {
                log("BUG: onSetupConnectionCompleted is stale cp.tag=" + cp.tag + ", mtag=" + mTag);
            }
            result = SetupResult.ERR_Stale;
        } else {
//            log("onSetupConnectionCompleted received " + response.length + " response strings:");
//            for (int i = 0; i < response.length; i++) {
//                log("  response[" + i + "]='" + response[i] + "'");
//            }
            if (response.length >= 2) {

                FailCause  f = FailCause.NONE; //assume success?

                cid = Integer.parseInt(response[0]);
                interfaceName = response[1];
                mDataProfile = cp.dp;
                mBearerType = cp.bearerType;

                if (response.length > 2) {
                    ipAddressList = response[2] == null ? null : response[2].split(" ");
                    if (response.length > 3) {
                        f = getFailCauseFromRequest(Integer.parseInt(response[3]));
                        //possible partial success (3GPP rel 8. esm fail case 50, 51, 52;
                        if (DBG && f != FailCause.NONE) {
                            log("partial success on cid " + cid + " with failcause : " + f);
                        }
                    }

                    String prefix = "net." + interfaceName + ".";
                    gatewayAddress = SystemProperties.get(prefix + "gw");
                    dnsServers[0] = SystemProperties.get(prefix + "dns1");
                    dnsServers[1] = SystemProperties.get(prefix + "dns2");
                    if (DBG) {
                        log("interface=" + interfaceName
                            + " ipAddress=" + Arrays.toString(ipAddressList)
                            + " gateway=" + gatewayAddress + " DNS1=" + dnsServers[0]
                            + " DNS2=" + dnsServers[1]);
                    }

                    if (isDnsOk(dnsServers)) {
                        result = SetupResult.SUCCESS;
                    } else {
                        result = SetupResult.ERR_BadDns;
                    }
                } else {
                    result = SetupResult.SUCCESS;
                }
                result.mFailCause = f;
            } else {
                result = SetupResult.ERR_Other;
                result.mFailCause = FailCause.UNKNOWN;
            }
        }

        if (DBG) log("DataConnection setup result='" + result + "' on cid=" + cid);
        return result;
    }

    /**
     * The parent state for all other states.
     */
    private class DcDefaultState extends HierarchicalState {
        @Override
        protected boolean processMessage(Message msg) {
            AsyncResult ar;

            switch (msg.what) {
                case EVENT_RESET:
                    if (DBG) log("DcDefaultState: msg.what=EVENT_RESET");
                    clearSettings();
                    if (msg.obj != null) {
                        notifyDisconnectCompleted((DisconnectParams) msg.obj);
                    }
                    transitionTo(mInactiveState);
                    break;

                case EVENT_CONNECT:
                    if (DBG) log("DcDefaultState: msg.what=EVENT_CONNECT, fail not expected");
                    ConnectionParams cp = (ConnectionParams) msg.obj;
                    notifyConnectCompleted(cp, FailCause.UNKNOWN);
                    break;

                case EVENT_DISCONNECT:
                    if (DBG) log("DcDefaultState: msg.what=EVENT_DISCONNECT");
                    notifyDisconnectCompleted((DisconnectParams) msg.obj);
                    break;

                default:
                    if (DBG) {
                        log("DcDefaultState: shouldn't happen but ignore msg.what=" + msg.what);
                    }
                    break;
            }

            return true;
        }
    }
    private DcDefaultState mDefaultState = new DcDefaultState();

    /**
     * The state machine is inactive and expects a EVENT_CONNECT.
     */
    private class DcInactiveState extends HierarchicalState {
        private ConnectionParams mConnectionParams = null;
        private FailCause mFailCause = null;
        private DisconnectParams mDisconnectParams = null;

        public void setEnterNotificationParams(ConnectionParams cp, FailCause cause) {
            log("DcInactiveState: setEnterNoticationParams cp,cause");
            mConnectionParams = cp;
            mFailCause = cause;
        }

        public void setEnterNotificationParams(DisconnectParams dp) {
          log("DcInactiveState: setEnterNoticationParams dp");
            mDisconnectParams = dp;
        }

        @Override protected void enter() {
            mTag += 1;

            /**
             * Now that we've transitioned to Inactive state we
             * can send notifications. Previously we sent the
             * notifications in the processMessage handler but
             * that caused a race condition because the synchronous
             * call to isInactive.
             */
            if ((mConnectionParams != null) && (mFailCause != null)) {
                log("DcInactiveState: enter notifyConnectCompleted");
                notifyConnectCompleted(mConnectionParams, mFailCause);
            }
            if (mDisconnectParams != null) {
              log("DcInactiveState: enter notifyDisconnectCompleted");
                notifyDisconnectCompleted(mDisconnectParams);
            }
        }

        @Override protected void exit() {
            // clear notifications
            mConnectionParams = null;
            mFailCause = null;
            mDisconnectParams = null;
        }

        @Override protected boolean processMessage(Message msg) {
            boolean retVal;

            switch (msg.what) {
                case EVENT_RESET:
                    if (DBG) {
                        log("DcInactiveState: msg.what=EVENT_RESET, ignore we're already reset");
                    }
                    if (msg.obj != null) {
                        notifyDisconnectCompleted((DisconnectParams) msg.obj);
                    }
                    retVal = true;
                    break;

                case EVENT_CONNECT:
                    if (DBG) log("DcInactiveState msg.what=EVENT_CONNECT");
                    ConnectionParams cp = (ConnectionParams) msg.obj;
                    cp.tag = mTag;
                    onConnect(cp);
                    transitionTo(mActivatingState);
                    retVal = true;
                    break;

                default:
                    if (DBG) log("DcInactiveState nothandled msg.what=" + msg.what);
                    retVal = false;
                    break;
            }
            return retVal;
        }
    }
    private DcInactiveState mInactiveState = new DcInactiveState();

    /**
     * The state machine is activating a connection.
     */
    private class DcActivatingState extends HierarchicalState {
        @Override protected boolean processMessage(Message msg) {
            boolean retVal;
            AsyncResult ar;
            ConnectionParams cp;

            switch (msg.what) {
                case EVENT_DISCONNECT:
                    if (DBG) log("DcActivatingState deferring msg.what=EVENT_DISCONNECT");
                    deferMessage(msg);
                    retVal = true;
                    break;

                case EVENT_SETUP_DATA_CONNECTION_DONE:
                    if (DBG) log("DcActivatingState msg.what=EVENT_SETUP_DATA_CONNECTION_DONE");

                    ar = (AsyncResult) msg.obj;
                    cp = (ConnectionParams) ar.userObj;

                    SetupResult result = onSetupConnectionCompleted(ar);
                    switch (result) {
                        case SUCCESS:
                            // All is well (possible partial failure)
                            mActiveState.setEnterNotificationParams(cp, result.mFailCause);
                            transitionTo(mActiveState);
                            break;
                        case ERR_BadCommand:
                            // Vendor ril rejected the command and didn't connect.
                            // Transition to inactive but send notifications after
                            // we've entered the mInactive state.
                            mInactiveState.setEnterNotificationParams(cp, result.mFailCause);
                            transitionTo(mInactiveState);
                            break;
                        case ERR_BadDns:
                            // Connection succeeded but DNS info is bad so disconnect
                            EventLog.writeEvent(EventLogTags.PDP_BAD_DNS_ADDRESS, dnsServers[0]);
                            tearDownData(cp);
                            transitionTo(mDisconnectingBadDnsState);
                            break;
                        case ERR_Other:
                            mInactiveState.setEnterNotificationParams(cp, result.mFailCause);
                            transitionTo(mInactiveState);
                            break;
                        case ERR_Stale:
                            // Request is stale, ignore.
                            break;
                        default:
                            throw new RuntimeException("Unkown SetupResult, should not happen");
                    }
                    retVal = true;
                    break;

                default:
                    if (DBG) log("DcActivatingState not handled msg.what=" + msg.what);
                    retVal = false;
                    break;
            }
            return retVal;
        }
    }
    private DcActivatingState mActivatingState = new DcActivatingState();

    /**
     * The state machine is connected, expecting an EVENT_DISCONNECT.
     */
    private class DcActiveState extends HierarchicalState {
        private ConnectionParams mConnectionParams = null;
        private FailCause mFailCause = null;

        public void setEnterNotificationParams(ConnectionParams cp, FailCause cause) {
            log("DcInactiveState: setEnterNoticationParams cp,cause");
            mConnectionParams = cp;
            mFailCause = cause;
        }

        @Override public void enter() {
            /**
             * Now that we've transitioned to Active state we
             * can send notifications. Previously we sent the
             * notifications in the processMessage handler but
             * that caused a race condition because the synchronous
             * call to isActive.
             */
            if ((mConnectionParams != null) && (mFailCause != null)) {
                log("DcActiveState: enter notifyConnectCompleted");
                notifyConnectCompleted(mConnectionParams, mFailCause);
            }
        }

        @Override protected void exit() {
            // clear notifications
            mConnectionParams = null;
            mFailCause = null;
        }

        @Override protected boolean processMessage(Message msg) {
            boolean retVal;

            switch (msg.what) {
                case EVENT_DISCONNECT:
                    if (DBG) log("DcActiveState msg.what=EVENT_DISCONNECT");
                    DisconnectParams dp = (DisconnectParams) msg.obj;
                    dp.tag = mTag;
                    tearDownData(dp);
                    transitionTo(mDisconnectingState);
                    retVal = true;
                    break;

                default:
                    if (DBG) log("DcActiveState nothandled msg.what=" + msg.what);
                    retVal = false;
                    break;
            }
            return retVal;
        }
    }
    private DcActiveState mActiveState = new DcActiveState();

    /**
     * The state machine is disconnecting.
     */
    private class DcDisconnectingState extends HierarchicalState {
        @Override protected boolean processMessage(Message msg) {
            boolean retVal;

            switch (msg.what) {
                case EVENT_DEACTIVATE_DONE:
                    if (DBG) log("DcDisconnectingState msg.what=EVENT_DEACTIVATE_DONE");
                    AsyncResult ar = (AsyncResult) msg.obj;
                    DisconnectParams dp = (DisconnectParams) ar.userObj;
                    if (dp.tag == mTag) {
                        // Transition to inactive but send notifications after
                        // we've entered the mInactive state.
                        mInactiveState.setEnterNotificationParams((DisconnectParams) ar.userObj);
                        transitionTo(mInactiveState);
                    } else {
                        if (DBG) log("DcDisconnectState EVENT_DEACTIVATE_DONE stale dp.tag="
                                + dp.tag + " mTag=" + mTag);
                    }
                    retVal = true;
                    break;

                default:
                    if (DBG) log("DcDisconnectingState not handled msg.what=" + msg.what);
                    retVal = false;
                    break;
            }
            return retVal;
        }
    }
    private DcDisconnectingState mDisconnectingState = new DcDisconnectingState();

    /**
     * The state machine is disconnecting after a bad dns setup
     * was found in mInactivatingState.
     */
    private class DcDisconnectingBadDnsState extends HierarchicalState {
        @Override protected boolean processMessage(Message msg) {
            boolean retVal;

            switch (msg.what) {
                case EVENT_DEACTIVATE_DONE:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    ConnectionParams cp = (ConnectionParams) ar.userObj;
                    if (cp.tag == mTag) {
                        if (DBG) log("DcDisconnectingBadDnsState msg.what=EVENT_DEACTIVATE_DONE");
                        // Transition to inactive but send notifications after
                        // we've entered the mInactive state.
                        mInactiveState.setEnterNotificationParams(cp, FailCause.UNKNOWN);
                        transitionTo(mInactiveState);
                    } else {
                        if (DBG) log("DcDisconnectingBadDnsState EVENT_DEACTIVE_DONE stale dp.tag="
                                + cp.tag + ", mTag=" + mTag);
                    }
                    retVal = true;
                    break;

                default:
                    if (DBG) log("DcDisconnectingBadDnsState not handled msg.what=" + msg.what);
                    retVal = false;
                    break;
            }
            return retVal;
        }
    }
    private DcDisconnectingBadDnsState mDisconnectingBadDnsState = new DcDisconnectingBadDnsState();

    // ******* public interface

    /**
     * Disconnect from the network.
     *
     * @param onCompletedMsg is sent with its msg.obj as an AsyncResult object.
     *        With AsyncResult.userObj set to the original msg.obj.
     */
    public void reset(Message onCompletedMsg) {
        sendMessage(obtainMessage(EVENT_RESET, new DisconnectParams(onCompletedMsg)));
    }

    /**
     * Reset the connection and wait for it to complete.
     * TODO: Remove when all callers only need the asynchronous
     * reset defined above.
     */
    public void resetSynchronously() {
        ResetSynchronouslyLock lockObj = new ResetSynchronouslyLock();
        synchronized(lockObj) {
            sendMessage(obtainMessage(EVENT_RESET, new DisconnectParams(lockObj)));
            try {
                lockObj.wait();
            } catch (InterruptedException e) {
                log("blockingReset: unexpected interrupted of wait()");
            }
        }
    }

    /**
     * Connect to the data profile and return an AsyncResult in onCompletedMsg.
     *
     * @param onCompletedMsg is sent with its msg.obj as an AsyncResult object.
     *        With AsyncResult.userObj set to the original msg.obj,
     *        AsyncResult.result = FailCause and AsyncResult.exception = Exception().
     */
    public void connect(int radioTech, DataProfile dp,
            BearerType bearerType, Message onCompletedMsg) {
        sendMessage(obtainMessage(EVENT_CONNECT,
                new ConnectionParams(radioTech, dp, bearerType, onCompletedMsg)));
    }

    /**
     * Disconnect from the network.
     *
     * @param onCompletedMsg is sent with its msg.obj as an AsyncResult object.
     *        With AsyncResult.userObj set to the original msg.obj.
     */
    public void disconnect(Message onCompletedMsg) {
        sendMessage(obtainMessage(EVENT_DISCONNECT, new DisconnectParams(onCompletedMsg)));
    }

    // ****** The following are used for debugging.

    /**
     * TODO: This should be an asynchronous call and we wouldn't
     * have to use handle the notification in the DcInactiveState.enter.
     *
     * @return true if the state machine is in the inactive state.
     */
    public boolean isInactive() {
        boolean retVal = getCurrentState() == mInactiveState;
        return retVal;
    }

    /**
     * TODO: This should be an asynchronous call and we wouldn't
     * have to use handle the notification in the DcActiveState.enter.
     *
     * @return true if the state machine is in the active state.
     */
    public boolean isActive() {
        boolean retVal = getCurrentState() == mActiveState;
        return retVal;
    }

    public DataProfile getDataProfile() {
        return mDataProfile;
    }

    public BearerType getBearerType() {
        return mBearerType;
    }

    /**
     * @return the interface name as a string.
     */
    public String getInterface() {
        return interfaceName;
    }

    /**
     * @return the ip address as a string.
     */
    public String[] getIpAddressList() {
        return ipAddressList;
    }

    /**
     * @return the gateway address as a string.
     */
    public String getGatewayAddress() {
        return gatewayAddress;
    }

    /**
     * @return an array of associated DNS addresses.
     */
    public String[] getDnsServers() {
        return dnsServers;
    }

    /**
     * @return the current state as a string.
     */
    public String getStateAsString() {
        String retVal = getCurrentState().getName();
        return retVal;
    }

    /**
     * @return the time of when this connection was created.
     */
    public long getConnectionTime() {
        return createTime;
    }

    /**
     * @return the time of the last failure.
     */
    public long getLastFailTime() {
        return lastFailTime;
    }

    /**
     * @return the last cause of failure.
     */
    public FailCause getLastFailCause() {
        return lastFailCause;
    }
}
