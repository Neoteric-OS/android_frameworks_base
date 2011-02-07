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

import android.os.SystemProperties;
import android.util.Log;
import android.util.Patterns;

import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.data.DataProfile.DataProfileType;

/**
 * {@hide}
 */
public class MMDataConnection extends DataConnection {

    boolean DBG = true;

    private static final String LOG_TAG = "DATA";

    /* 3GPP failcauses are sufficient here. 3GPP2 failcauses will map. */
    private static final int PDP_SUCCESS = 0x00;
    private static final int PDP_FAIL_OPERATOR_BARRED = 0x08;
    private static final int PDP_FAIL_INSUFFICIENT_RESOURCES = 0x1A;
    private static final int PDP_FAIL_MISSING_UKNOWN_APN = 0x1B;
    private static final int PDP_FAIL_UNKNOWN_PDP_ADDRESS_TYPE = 0x1C;
    private static final int PDP_FAIL_USER_AUTHENTICATION = 0x1D;
    private static final int PDP_FAIL_ACTIVATION_REJECT_GGSN = 0x1E;
    private static final int PDP_FAIL_ACTIVATION_REJECT_UNSPECIFIED = 0x1F;
    private static final int PDP_FAIL_SERVICE_OPTION_NOT_SUPPORTED = 0x20;
    private static final int PDP_FAIL_SERVICE_OPTION_NOT_SUBSCRIBED = 0x21;
    private static final int PDP_FAIL_SERVICE_OPTION_OUT_OF_ORDER = 0x22;
    private static final int PDP_FAIL_NSAPI_IN_USE = 0x23;
    private static final int PDP_FAIL_NETWORK_FAILURE = 0x26;
    private static final int PDP_FAIL_ONLY_IPV4_ALLOWED = 0x32;
    private static final int PDP_FAIL_ONLY_IPV6_ALLOWED = 0x33;
    private static final int PDP_FAIL_ONLY_SINGLE_BEARER_ALLOWED = 0x34;
    private static final int PDP_FAIL_PROTOCOL_ERRORS   = 0x6F;
    private static final int PDP_FAIL_ERROR_UNSPECIFIED = 0xffff;

    private static final int PDP_FAIL_REGISTRATION_FAIL = -1;
    private static final int PDP_FAIL_GPRS_REGISTRATION_FAIL = -2;
    private static final int PDP_FAIL_PREF_RADIO_TECH_CHANGED = -3;
    private static final int PDP_FAIL_RADIO_POWER_OFF = -4;
    private static final int PDP_FAIL_TETHERED_CALL_ACTIVE = -5;

    private MMDataConnection(PhoneBase phone, String name) {
        super(phone, name);
    }

    static MMDataConnection makeDataConnection(PhoneBase phone) {
        synchronized (mCountLock) {
            mCount += 1;
        }
        MMDataConnection dc = new MMDataConnection(phone, "MMDC -"
                + mCount);
        dc.start();
        return dc;
    }

    /**
     * Setup a data call with the specified data profile
     *
     * @param onCompleted notify success or not after down
     */
    protected void onConnect(ConnectionParams cp) {

        logi("Connecting : dataProfile = " + cp.dp.toString());

        /* case APN */
        if (cp.dp.getDataProfileType() == DataProfileType.PROFILE_TYPE_3GPP_APN) {
            ApnSetting apn = (ApnSetting) cp.dp;

            setHttpProxy(apn.proxy, apn.port);

            int authType = apn.authType;
            if (authType == -1) {
                authType = (apn.user != null) ? RILConstants.SETUP_DATA_AUTH_PAP_CHAP
                        : RILConstants.SETUP_DATA_AUTH_NONE;
            }
            this.phone.mCM.setupDataCall(
                    Integer.toString(cp.radioTech),
                    Integer.toString(0), apn.apn, apn.user, apn.password,
                    Integer.toString(authType),
                    cp.bearerType.toString(),
                    obtainMessage(EVENT_SETUP_DATA_CONNECTION_DONE, cp));
        } else if (cp.dp.getDataProfileType() == DataProfileType.PROFILE_TYPE_3GPP2_NAI) {
            CdmaNAI nai = (CdmaNAI) cp.dp;
            this.phone.mCM.setupDataCall(
                    Integer.toString(cp.radioTech),
                    Integer.toString(nai.mProfileId), null, null, null,
                    Integer.toString(RILConstants.SETUP_DATA_AUTH_PAP_CHAP),
                    cp.bearerType.toString(),
                    obtainMessage(EVENT_SETUP_DATA_CONNECTION_DONE, cp));
        }
    }

    private void setHttpProxy(String httpProxy, String httpPort) {
        if (httpProxy == null || httpProxy.length() == 0) {
            SystemProperties.set("net.gprs.http-proxy", null);
            return;
        }

        if (httpPort == null || httpPort.length() == 0) {
            httpPort = "8080"; // Default to port 8080
        }

        SystemProperties.set("net.gprs.http-proxy", "http://" + httpProxy + ":" + httpPort
                        + "/");
    }

    @Override
    protected boolean isDnsOk(String[] domainNameServers) {
        //TODO: this will break for IPV6 dns servers.
        if (NULL_IP.equals(dnsServers[0]) && NULL_IP.equals(dnsServers[1])
                && !phone.isDnsCheckDisabled()) {
            // Work around a race condition where QMI does not fill in DNS:
            // Deactivate PDP and let DataConnectionTracker retry.
            // Do not apply the race condition workaround for MMS APN
            // if Proxy is an IP-address.
            // Otherwise, the default APN will not be restored anymore.
            if (mDataProfile.getDataProfileType() == DataProfileType.PROFILE_TYPE_3GPP_APN
                    && mDataProfile.canHandleServiceType(DataServiceType.SERVICE_TYPE_MMS)
                    && isIpAddress(((ApnSetting)mDataProfile).mmsProxy)) {
                return false;
            }
        }
        return true;
    }

    /* TODO: Fix this function - also add support for IPV6 */
    private boolean isIpAddress(String address) {
        if (address == null)
            return false;

        return Patterns.IP_ADDRESS.matcher(((ApnSetting)mDataProfile).mmsProxy).matches();
    }

    void logd(String logString) {
        if (DBG) {
            Log.d(LOG_TAG, "[DC cid = " + cid + "]" + logString);
        }
    }

    void logv(String logString) {
        if (DBG) {
            Log.d(LOG_TAG, "[DC cid = " + cid + "]" + logString);
        }
    }

    void logi(String logString) {
        Log.i(LOG_TAG, "[DC cid = " + cid + "]" + logString);
    }

    void loge(String logString) {
        Log.e(LOG_TAG, "[DC cid = " + cid + "]" + logString);
    }

    public String toString() {
        return ("Cid=" + cid + ", State=" + getStateAsString()
                + ", bearerType=" + mBearerType + ", create=" + createTime
                + ", lastFail=" + lastFailTime + ", lastFailCause=" + lastFailCause
                + ", dp=" + mDataProfile);
    }

    @Override
    protected void log(String s) {
        logv(s);
    }

    @Override
    protected FailCause getFailCauseFromRequest(int rilCause) {
        FailCause cause;

        switch (rilCause) {
            case PDP_SUCCESS:
                cause = FailCause.NONE;
                break;
            case PDP_FAIL_INSUFFICIENT_RESOURCES:
                cause = FailCause.INSUFFICIENT_RESOURCES;
                break;
            case PDP_FAIL_MISSING_UKNOWN_APN:
                cause = FailCause.MISSING_UNKNOWN_APN;
                break;
            case PDP_FAIL_UNKNOWN_PDP_ADDRESS_TYPE:
                cause = FailCause.UNKNOWN_PDP_ADDRESS;
                break;
            case PDP_FAIL_USER_AUTHENTICATION:
                cause = FailCause.USER_AUTHENTICATION;
                break;
            case PDP_FAIL_ACTIVATION_REJECT_GGSN:
                cause = FailCause.ACTIVATION_REJECT_GGSN;
                break;
            case PDP_FAIL_ACTIVATION_REJECT_UNSPECIFIED:
                cause = FailCause.ACTIVATION_REJECT_UNSPECIFIED;
                break;
            case PDP_FAIL_SERVICE_OPTION_OUT_OF_ORDER:
                cause = FailCause.SERVICE_OPTION_OUT_OF_ORDER;
                break;
            case PDP_FAIL_SERVICE_OPTION_NOT_SUPPORTED:
                cause = FailCause.SERVICE_OPTION_NOT_SUPPORTED;
                break;
            case PDP_FAIL_SERVICE_OPTION_NOT_SUBSCRIBED:
                cause = FailCause.SERVICE_OPTION_NOT_SUBSCRIBED;
                break;
            case PDP_FAIL_NSAPI_IN_USE:
                cause = FailCause.NSAPI_IN_USE;
                break;
            case PDP_FAIL_ONLY_IPV4_ALLOWED:
                cause = FailCause.ONLY_IPV4_ALLOWED;
                break;
            case PDP_FAIL_ONLY_IPV6_ALLOWED:
                cause = FailCause.ONLY_IPV6_ALLOWED;
                break;
            case PDP_FAIL_ONLY_SINGLE_BEARER_ALLOWED:
                cause = FailCause.ONLY_SINGLE_BEARER_ALLOWED;
                break;
            case PDP_FAIL_PROTOCOL_ERRORS:
                cause = FailCause.PROTOCOL_ERRORS;
                break;
            case PDP_FAIL_ERROR_UNSPECIFIED:
                cause = FailCause.UNKNOWN;
                break;
            case PDP_FAIL_REGISTRATION_FAIL:
                cause = FailCause.REGISTRATION_FAIL;
                break;
            case PDP_FAIL_GPRS_REGISTRATION_FAIL:
                cause = FailCause.GPRS_REGISTRATION_FAIL;
                break;
            case PDP_FAIL_PREF_RADIO_TECH_CHANGED:
                cause = FailCause.PREF_RADIO_TECH_CHANGED;
                break;
            case PDP_FAIL_RADIO_POWER_OFF:
                cause = FailCause.RADIO_POWER_OFF;
                break;
            default:
                cause = FailCause.UNKNOWN;
        }
        return cause;
    }
}
