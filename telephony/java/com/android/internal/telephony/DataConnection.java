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

package com.android.internal.telephony;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * {@hide}
 */
public abstract class DataConnection extends Handler {

    protected String LOG_TAG = "DataConnection";

    /** Fail cause of last Data Call activate from RIL_LastDataCallActivateFailCause */

    protected final static int PS_NET_DOWN_REASON_OPERATOR_DETERMINED_BARRING         = 8;
    protected final static int PS_NET_DOWN_REASON_UNKNOWN_APN                         = 27;
    protected final static int PS_NET_DOWN_REASON_AUTH_FAILED                         = 29;
    protected final static int PS_NET_DOWN_REASON_OPTION_NOT_SUPPORTED                = 32;
    protected final static int PS_NET_DOWN_REASON_OPTION_UNSUBSCRIBED                 = 33;

/** It is likely that the number of error codes listed below will be removed
 * in the foreseeable future.  They have been added, but not agreed upon.
 *
 */
    protected final static int PS_NET_DOWN_REASON_NOT_SPECIFIED                       = 0;
    protected final static int PS_NET_DOWN_REASON_CLOSE_IN_PROGRESS                   = 1;
    protected final static int PS_NET_DOWN_REASON_NW_INITIATED_TERMINATION            = 2;
    protected final static int PS_NET_DOWN_REASON_APP_PREEMPTED                       = 3;    
    protected final static int PS_NET_DOWN_REASON_LLC_SNDCP_FAILURE                   = 25;  
    protected final static int PS_NET_DOWN_REASON_INSUFFICIENT_RESOURCES              = 26;
    protected final static int PS_NET_DOWN_REASON_UNKNOWN_PDP                         = 28;
    protected final static int PS_NET_DOWN_REASON_GGSN_REJECT                         = 30;
    protected final static int PS_NET_DOWN_REASON_ACTIVATION_REJECT                   = 31;
    protected final static int PS_NET_DOWN_REASON_OPTION_TEMP_OOO                     = 34;
    protected final static int PS_NET_DOWN_REASON_NSAPI_ALREADY_USED                  = 35;
    protected final static int PS_NET_DOWN_REASON_REGULAR_DEACTIVATION                = 36;
    protected final static int PS_NET_DOWN_REASON_QOS_NOT_ACCEPTED                    = 37;
    protected final static int PS_NET_DOWN_REASON_NETWORK_FAILURE                     = 38;
    protected final static int PS_NET_DOWN_REASON_UMTS_REATTACH_REQ                   = 39;
    protected final static int PS_NET_DOWN_REASON_TFT_SEMANTIC_ERROR                  = 41;
    protected final static int PS_NET_DOWN_REASON_TFT_SYNTAX_ERROR                    = 42;
    protected final static int PS_NET_DOWN_REASON_UNKNOWN_PDP_CONTEXT                 = 43;
    protected final static int PS_NET_DOWN_REASON_FILTER_SEMANTIC_ERROR               = 44;
    protected final static int PS_NET_DOWN_REASON_FILTER_SYNTAX_ERROR                 = 45;
    protected final static int PS_NET_DOWN_REASON_PDP_WITHOUT_ACTIVE_TFT              = 46;
    protected final static int PS_NET_DOWN_REASON_INVALID_TRANSACTION_ID              = 81;
    protected final static int PS_NET_DOWN_REASON_MESSAGE_INCORRECT_SEMANTIC          = 95;
    protected final static int PS_NET_DOWN_REASON_INVALID_MANDATORY_INFO              = 96;
    protected final static int PS_NET_DOWN_REASON_MESSAGE_TYPE_UNSUPPORTED            = 97;
    protected final static int PS_NET_DOWN_REASON_MSG_TYPE_NONCOMPATIBLE_STATE        = 98;
    protected final static int PS_NET_DOWN_REASON_UNKNOWN_INFO_ELEMENT                = 99;
    protected final static int PS_NET_DOWN_REASON_CONDITIONAL_IE_ERROR                = 100;
    protected final static int PS_NET_DOWN_REASON_MSG_AND_PROTOCOL_STATE_UNCOMPATIBLE = 101;
    protected final static int PS_NET_DOWN_REASON_PROTOCOL_ERROR                      = 111;
    protected final static int PS_NET_DOWN_REASON_APN_TYPE_CONFLICT                   = 112;
    protected final static int PS_NET_DOWN_REASON_UNKNOWN_CAUSE_CODE                  = 113;
    protected final static int PS_NET_DOWN_REASON_INTERNAL_MIN                        = 200;
    protected final static int PS_NET_DOWN_REASON_INTERNAL_ERROR                      = 201;
    protected final static int PS_NET_DOWN_REASON_INTERNAL_CALL_ENDED                 = 202;
    protected final static int PS_NET_DOWN_REASON_INTERNAL_UNKNOWN_CAUSE_CODE         = 203;
    protected final static int PS_NET_DOWN_REASON_INTERNAL_MAX                        = 204;
    protected final static int PS_NET_DOWN_REASON_CDMA_LOCK                           = 500;
    protected final static int PS_NET_DOWN_REASON_INTERCEPT                           = 501;
    protected final static int PS_NET_DOWN_REASON_REORDER                             = 502;
    protected final static int PS_NET_DOWN_REASON_REL_SO_REJ                          = 503;
    protected final static int PS_NET_DOWN_REASON_INCOM_CALL                          = 504;
    protected final static int PS_NET_DOWN_REASON_ALERT_STOP                          = 505;
    protected final static int PS_NET_DOWN_REASON_ACTIVATION                          = 506;
    protected final static int PS_NET_DOWN_REASON_MAX_ACCESS_PROBE                    = 507;
    protected final static int PS_NET_DOWN_REASON_CCS_NOT_SUPPORTED_BY_BS             = 508;
    protected final static int PS_NET_DOWN_REASON_NO_RESPONSE_FROM_BS                 = 509;
    protected final static int PS_NET_DOWN_REASON_REJECTED_BY_BS                      = 510;
    protected final static int PS_NET_DOWN_REASON_INCOMPATIBLE                        = 511;
    protected final static int PS_NET_DOWN_REASON_ALREADY_IN_TC                       = 512;
    protected final static int PS_NET_DOWN_REASON_USER_CALL_ORIG_DURING_GPS           = 513;
    protected final static int PS_NET_DOWN_REASON_USER_CALL_ORIG_DURING_SMS           = 514;
    protected final static int PS_NET_DOWN_REASON_NO_CDMA_SRV                         = 515;  
    protected final static int PS_NET_DOWN_REASON_CONF_FAILED                         = 1000;
    protected final static int PS_NET_DOWN_REASON_INCOM_REJ                           = 1001;
    protected final static int PS_NET_DOWN_REASON_NO_GW_SRV                           = 1002;
    protected final static int PS_NET_DOWN_REASON_CD_GEN_OR_BUSY                      = 1500;
    protected final static int PS_NET_DOWN_REASON_CD_BILL_OR_AUTH                     = 1501;
    protected final static int PS_NET_DOWN_REASON_CHG_HDR                             = 1502;
    protected final static int PS_NET_DOWN_REASON_EXIT_HDR                            = 1503;
    protected final static int PS_NET_DOWN_REASON_HDR_NO_SESSION                      = 1504;
    protected final static int PS_NET_DOWN_REASON_HDR_ORIG_DURING_GPS_FIX             = 1505;
    protected final static int PS_NET_DOWN_REASON_HDR_CS_TIMEOUT                      = 1506;
    protected final static int PS_NET_DOWN_REASON_HDR_RELEASED_BY_CM                  = 1507;
    protected final static int PS_NET_DOWN_REASON_CLIENT_END                          = 2000;
    protected final static int PS_NET_DOWN_REASON_NO_SRV                              = 2001;
    protected final static int PS_NET_DOWN_REASON_FADE                                = 2002;
    protected final static int PS_NET_DOWN_REASON_REL_NORMAL                          = 2003;
    protected final static int PS_NET_DOWN_REASON_ACC_IN_PROG                         = 2004;
    protected final static int PS_NET_DOWN_REASON_ACC_FAIL                            = 2005;
    protected final static int PS_NET_DOWN_REASON_REDIR_OR_HANDOFF                    = 2006;

    // the inherited class

    public enum State {
        ACTIVE, /* has active data connection */
        ACTIVATING, /* during connecting process */
        INACTIVE; /* has empty data connection */

        public String toString() {
            switch (this) {
            case ACTIVE:
                return "active";
            case ACTIVATING:
                return "setting up";
            default:
                return "inactive";
            }
        }

        public boolean isActive() {
            return this == ACTIVE;
        }

        public boolean isInactive() {
            return this == INACTIVE;
        }
    }

    public enum FailCause {
        NONE,
        BAD_APN,
        BAD_PAP_SECRET,
        BARRED,
        USER_AUTHENTICATION,
        SERVICE_OPTION_NOT_SUPPORTED,
        SERVICE_OPTION_NOT_SUBSCRIBED,
        SIM_LOCKED,
        RADIO_OFF,
        NO_SIGNAL,
        NO_DATA_PLAN,
        RADIO_NOT_AVAILABLE,
        SUSPENED_TEMPORARY,
        RADIO_ERROR_RETRY,
        UNKNOWN;

        public boolean isPermanentFail() {
            return (this == RADIO_OFF);
        }

        public String toString() {
            switch (this) {
            case NONE:
                return "no error";
            case BAD_APN:
                return "bad apn";
            case BAD_PAP_SECRET:
                return "bad pap secret";
            case BARRED:
                return "barred";
            case USER_AUTHENTICATION:
                return "error user autentication";
            case SERVICE_OPTION_NOT_SUPPORTED:
                return "data not supported";
            case SERVICE_OPTION_NOT_SUBSCRIBED:
                return "datt not subcribed";
            case SIM_LOCKED:
                return "sim locked";
            case RADIO_OFF:
                return "radio is off";
            case NO_SIGNAL:
                return "no signal";
            case NO_DATA_PLAN:
                return "no data plan";
            case RADIO_NOT_AVAILABLE:
                return "radio not available";
            case SUSPENED_TEMPORARY:
                return "suspend temporary";
            case RADIO_ERROR_RETRY:
                return "transient radio error";
            default:
                return "unknown data error";
            }
        }
    }

    // ***** Event codes
    protected static final int EVENT_SETUP_DATA_CONNECTION_DONE = 1;
    protected static final int EVENT_GET_LAST_FAIL_DONE = 2;
    protected static final int EVENT_LINK_STATE_CHANGED = 3;
    protected static final int EVENT_DEACTIVATE_DONE = 4;
    protected static final int EVENT_FORCE_RETRY = 5;

    // EVENT_SETUP_DATA_CONNECTION_DONE
    protected abstract void onSetupConnectionCompleted(AsyncResult ar);

    protected abstract void onGetLastFailCompleted(AsyncResult ar); 

    protected abstract void onLinkStateChanged(AsyncResult ar);

    protected abstract void onDeactivated(AsyncResult ar);

    protected abstract void onForceRetry();

    protected abstract void disconnect(Message msg);

    // member variables
    protected State state;
    private Phone phone;
    protected long createTime;
    protected long lastFailTime;
    protected FailCause lastFailCause;

    protected DataConnection(String logTag, Phone phone) {
        super();

        this.LOG_TAG = logTag;
        this.phone = phone;
    }

    protected void clearSettings() {
        Log.d(LOG_TAG, "DataConnection.clearSettings()");

        this.state = State.INACTIVE;
        this.createTime = -1;
        this.lastFailTime = -1;
        this.lastFailCause = FailCause.NONE;
    }

    @Override
    public void handleMessage(Message msg) {

        Log.d(LOG_TAG, "DataConnection.handleMessage()");

        switch (msg.what) {

        case EVENT_SETUP_DATA_CONNECTION_DONE:
            onSetupConnectionCompleted((AsyncResult) msg.obj);
            break;

        case EVENT_FORCE_RETRY:
            onForceRetry();
            break;

        case EVENT_GET_LAST_FAIL_DONE:
            onGetLastFailCompleted((AsyncResult) msg.obj);
            break;

        case EVENT_LINK_STATE_CHANGED:
            onLinkStateChanged((AsyncResult) msg.obj);
            break;

        case EVENT_DEACTIVATE_DONE:
            onDeactivated((AsyncResult) msg.obj);
            break;
        }
    }

    public State getState() {
        Log.d(LOG_TAG, "DataConnection.getState()");

        return state;
    }

    public long getConnectionTime() {
        Log.d(LOG_TAG, "DataConnection.getConnectionTime()");
        return createTime;
    }

    public long getLastFailTime() {
        Log.d(LOG_TAG, "DataConnection.getLastFailTime()");
        return lastFailTime;
    }

    public FailCause getLastFailCause() {
        Log.d(LOG_TAG, "DataConnection.getLastFailCause()");
        return lastFailCause;
    }
}
