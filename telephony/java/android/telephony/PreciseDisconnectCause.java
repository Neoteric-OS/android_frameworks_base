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

package android.telephony;

//TODO: Complete description
/**
 * 
 */
public class PreciseDisconnectCause {

    /* Codes obtained from ril.h RIL_LastCallFailCause and CallFailCause
     * TODO: Complete Javadoc with TS 24.008 causes */
    /** The disconnect cause is not valid (Not received a disconnect cause)*/
    public static final int PRECISE_DISCONNECT_CAUSE_NOT_VALID                      = -1;
    /** No disconnect cause provided. Generally a local disconnect or an incoming missed call */
    public static final int PRECISE_DISCONNECT_CAUSE_NO_DISCONNECT_CAUSE_AVAILABLE  = 0;
    /**
     * The destination cannot be reached because the number, although valid,
     * is not currently assigned
     */
    public static final int PRECISE_DISCONNECT_CAUSE_UNOBTAINABLE_NUMBER            = 1;
    /** One of the users involved in the call has requested that the call is cleared */
    public static final int PRECISE_DISCONNECT_CAUSE_NORMAL                         = 16;
    /** The called user is unable to accept another call */
    public static final int PRECISE_DISCONNECT_CAUSE_BUSY                           = 17;
    /** The called number is no longer assigned */
    public static final int PRECISE_DISCONNECT_CAUSE_NUMBER_CHANGED                 = 22;
    /** Provided in response to a STATUS ENQUIRY message */
    public static final int PRECISE_DISCONNECT_CAUSE_STATUS_ENQUIRY                 = 30;
    /** Reports a normal disconnect only when no other normal cause applies */
    public static final int PRECISE_DISCONNECT_CAUSE_NORMAL_UNSPECIFIED             = 31;
    /** There is no channel presently available to handle the call */
    public static final int PRECISE_DISCONNECT_CAUSE_NO_CIRCUIT_AVAIL               = 34;
    /**
     * The network is not functioning correctly and the condition is not likely to last
     * a long period of time
     */
    public static final int PRECISE_DISCONNECT_CAUSE_TEMPORARY_FAILURE              = 41;
    /** The switching equipment is experiencing a period of high traffic */
    public static final int PRECISE_DISCONNECT_CAUSE_SWITCHING_CONGESTION           = 42;
    /** The channel cannot be provided */
    public static final int PRECISE_DISCONNECT_CAUSE_CHANNEL_NOT_AVAIL              = 44;
    /** The requested quality of service (ITU-T X.213) cannot be provided */
    public static final int PRECISE_DISCONNECT_CAUSE_QOS_NOT_AVAIL                  = 49;
    /** The requested bearer capability is not available at this time */
    public static final int PRECISE_DISCONNECT_CAUSE_BEARER_NOT_AVAIL               = 58;
    /** The call clearing is due to ACM being greater than or equal to ACMmax */
    public static final int PRECISE_DISCONNECT_CAUSE_ACM_LIMIT_EXCEEDED             = 68;

    /* TODO: codes not present in TS 24.008 document */
    public static final int PRECISE_DISCONNECT_CAUSE_CALL_BARRED                    = 240;
    public static final int PRECISE_DISCONNECT_CAUSE_FDN_BLOCKED                    = 241;
    /** The given IMSI is not known at the VLR */
    /** TS 24.008 cause 4 */
    public static final int PRECISE_DISCONNECT_CAUSE_IMSI_UNKNOWN_IN_VLR            = 242;
    /**
     * The network does not accept emergency call establishment using an IMEI or not accept attach
     * procedure for emergency services using an IMEI
     * */
    /** TS 24.008 cause 5 */
    public static final int PRECISE_DISCONNECT_CAUSE_IMEI_NOT_ACCEPTED              = 243;

    public static final int PRECISE_DISCONNECT_CAUSE_CDMA_LOCKED_UNTIL_POWER_CYCLE  = 1000;
    public static final int PRECISE_DISCONNECT_CAUSE_CDMA_DROP                      = 1001;
    public static final int PRECISE_DISCONNECT_CAUSE_CDMA_INTERCEPT                 = 1002;
    public static final int PRECISE_DISCONNECT_CAUSE_CDMA_REORDER                   = 1003;
    public static final int PRECISE_DISCONNECT_CAUSE_CDMA_SO_REJECT                 = 1004;
    public static final int PRECISE_DISCONNECT_CAUSE_CDMA_RETRY_ORDER               = 1005;
    public static final int PRECISE_DISCONNECT_CAUSE_CDMA_ACCESS_FAILURE            = 1006;
    public static final int PRECISE_DISCONNECT_CAUSE_CDMA_PREEMPTED                 = 1007;
    public static final int PRECISE_DISCONNECT_CAUSE_CDMA_NOT_EMERGENCY             = 1008;
    public static final int PRECISE_DISCONNECT_CAUSE_CDMA_ACCESS_BLOCKED            = 1009;
    /** Disconnected due to unspecified reasons */
    public static final int PRECISE_DISCONNECT_CAUSE_ERROR_UNSPECIFIED              = 0xffff;

}