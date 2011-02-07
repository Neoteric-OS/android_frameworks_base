/*
 * Copyright (C) 2007 The Android Open Source Project
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

import java.util.List;

import android.os.Message;

import com.android.internal.net.IPVersion;


/**
 * Internal interface used to control the phone; SDK developers cannot
 * obtain this interface.
 *
 * {@hide}
 *
 */
public interface DataInterface {

    /**
     * The state of a data connection.
     * <ul>
     * <li>CONNECTED = IP traffic should be available</li>
     * <li>CONNECTING = Currently setting up data connection</li>
     * <li>DISCONNECTED = IP not available</li>
     * <li>SUSPENDED = connection is created but IP traffic is
     *                 temperately not available. i.e. voice call is in place
     *                 in 2G network</li>
     * </ul>
     */
    enum DataState {
        CONNECTED, CONNECTING, DISCONNECTED, SUSPENDED;
    };

    public enum DataActivityState {
        /**
         * The state of a data activity.
         * <ul>
         * <li>NONE = No traffic</li>
         * <li>DATAIN = Receiving IP ppp traffic</li>
         * <li>DATAOUT = Sending IP ppp traffic</li>
         * <li>DATAINANDOUT = Both receiving and sending IP ppp traffic</li>
         * <li>DORMANT = The data connection is still active,
                                     but physical link is down</li>
         * </ul>
         */
        NONE, DATAIN, DATAOUT, DATAINANDOUT, DORMANT;
    };

    enum BearerType {
        IP, IPV6, IPV4V6;

        @Override
        public String toString() {
            switch (this) {
                case IP:
                    return "IP";
                case IPV6:
                    return "IPV6";
                case IPV4V6:
                    return "IPV4V6";
                default:
                    return "unknown";
            }
        }

        boolean supportsIpVersion(IPVersion ipv) {
            if (ipv != null && this == BearerType.IPV4V6)
                return true;
            if (ipv == IPVersion.INET && this == BearerType.IP)
                return true;
            if (ipv == IPVersion.INET6 && this == BearerType.IPV6)
                return true;
            return false;
        }
    }

    static final String DATA_APN_TYPE_KEY = "apnType";
    static final String DATA_IPV4_INFO = "ipv4Info";
    static final String DATA_IPV6_INFO = "ipv6Info";
    static final String NETWORK_UNAVAILABLE_KEY = "networkUnvailable";

 /**
     * APN types for data connections.  These are usage categories for an APN
     * entry.  One APN entry may support multiple APN types, eg, a single APN
     * may service regular internet traffic ("default") as well as MMS-specific
     * connections.<br/>
     * APN_TYPE_ALL is a special type to indicate that this APN entry can
     * service all data connections.
     */
    static final String APN_TYPE_ALL = "*";
    /** APN type for default data traffic */
    static final String APN_TYPE_DEFAULT = "default";
    /** APN type for MMS traffic */
    static final String APN_TYPE_MMS = "mms";
    /** APN type for SUPL assisted GPS */
    static final String APN_TYPE_SUPL = "supl";
    /** APN type for DUN traffic */
    static final String APN_TYPE_DUN = "dun";
    /** APN type for HiPri traffic */
    static final String APN_TYPE_HIPRI = "hipri";

    // "Features" accessible through the connectivity manager
    static final String FEATURE_ENABLE_MMS = "enableMMS";
    static final String FEATURE_ENABLE_SUPL = "enableSUPL";
    static final String FEATURE_ENABLE_DUN = "enableDUN";
    static final String FEATURE_ENABLE_HIPRI = "enableHIPRI";

    /**
     * Return codes for <code>enableApnType()</code>
     */
    static final int APN_ALREADY_ACTIVE     = 0;
    static final int APN_REQUEST_STARTED    = 1;
    static final int APN_TYPE_NOT_AVAILABLE = 2;
    static final int APN_REQUEST_FAILED     = 3;

    /**
     * Optional reasons for disconnect and connect
     */
    static final String REASON_ROAMING_ON = "roamingOn";
    static final String REASON_ROAMING_OFF = "roamingOff";
    static final String REASON_SERVICE_TYPE_DISABLED = "apnTypeDisabled";
    static final String REASON_SERVICE_TYPE_ENABLED = "apnTypeEnabled";
    static final String REASON_MASTER_DATA_DISABLED = "masterDataDisabled";
    static final String REASON_MASTER_DATA_ENABLED = "masterDataEnabled";
    static final String REASON_DATA_NETWORK_ATTACH = "dataNetworkAttached";
    static final String REASON_DATA_NETWORK_DETACH = "dataNetworkDetached";
    static final String REASON_RADIO_ON = "radioOn";
    static final String REASON_RADIO_OFF = "radioOff";
    static final String REASON_NETWORK_DISCONNECT = "networkOrModemDisconnect";
    static final String REASON_VOICE_CALL_ENDED = "2GVoiceCallEnded";
    static final String REASON_VOICE_CALL_STARTED = "2GVoiceCallStarted";
    static final String REASON_PS_RESTRICT_ENABLED = "psRestrictEnabled";
    static final String REASON_PS_RESTRICT_DISABLED = "psRestrictDisabled";
    static final String REASON_RADIO_TECHNOLOGY_CHANGED = "radioTechnologyChanged";
    static final String REASON_ICC_LOADED = "iccRecordsLoaded";
    static final String REASON_DATA_PROFILE_LIST_CHANGED = "dataProfileDbChanged";
    static final String REASON_CDMA_SUBSCRIPTION_SOURCE_CHANGED = "cdmaSubscriptionSourceChanged";
    static final String REASON_CDMA_OTA_PROVISION = "cdmaOtaPovisioning";

    /**
     * Get the current DataState. Returns connected if at least one data connection is active.
     * No change notification exists at this interface -- use
     * {@link android.telephony.PhoneStateListener} instead.
     */
    DataState getDataConnectionState();

    /**
     * Get the current DataState for the specified apntype on specified ip
     * version. Returns connected if at least one data connection is active that
     * supports the specified apntype on specified ip version.
     * No change notification exists at this interface -- use
     * {@link android.telephony.PhoneStateListener} instead.
     */
    DataState getDataConnectionState(String apnType, IPVersion ipv);

    /**
     * Get the current DataActivityState. No change notification exists at this
     * interface -- use
     * {@link android.telephony.TelephonyManager} instead.
     */
    DataActivityState getDataActivityState();

    /**
     * Get current mutiple data connection status
     *
     * @return list of data connections
     */
    List<DataConnection> getCurrentDataConnectionList();

    /**
     * Get the current active Data Call list
     *
     * @param response <strong>On success</strong>, "response" bytes is
     * made available as:
     * (String[])(((AsyncResult)response.obj).result).
     * <strong>On failure</strong>,
     * (((AsyncResult)response.obj).result) == null and
     * (((AsyncResult)response.obj).exception) being an instance of
     * com.android.internal.telephony.gsm.CommandException
     */
    void getDataCallList(Message response);

    /**
     * Disables the DNS check (i.e., allows "0.0.0.0").
     * Useful for lab testing environment.
     * @param b true disables the check, false enables.
     */
    void disableDnsCheck(boolean b);

    /**
     * Returns true if the DNS check is currently disabled.
     */
    boolean isDnsCheckDisabled();

    /**
     * Returns an array of string identifiers for the APN types serviced by the
     * currently active or last connected APN.
     *  @return The string array.
     */
    String[] getActiveApnTypes();

    /**
     * Returns a string identifier for currently active APN on the specified apn
     * type and ip version if any.
     *
     * @return The string name.
     */

    String getActiveApn(String type, IPVersion ipv);

    /**
     * Allow mobile data connections.
     * @return {@code true} if the operation started successfully
     * <br/>{@code false} if it
     * failed immediately.<br/>
     * Even in the {@code true} case, it may still fail later
     * during setup, in which case an asynchronous indication will
     * be supplied.
     */
    boolean enableDataConnectivity();

    /**
     * Disallow mobile data connections, and terminate any that
     * are in progress.
     * @return {@code true} if the operation started successfully
     * <br/>{@code false} if it
     * failed immediately.<br/>
     * Even in the {@code true} case, it may still fail later
     * during setup, in which case an asynchronous indication will
     * be supplied.
     */
    boolean disableDataConnectivity();

    /**
     * Report the current state of data connectivity (enabled or disabled)
     * @return {@code false} if data connectivity has been explicitly disabled,
     * {@code true} otherwise.
     */
    boolean isDataConnectivityEnabled();

    /**
     * Enables the specified APN type. Only works for "special" APN types,
     * i.e., not the default APN.
     * @param type The desired APN type. Cannot be {@link #APN_TYPE_DEFAULT}.
     * @return <code>APN_ALREADY_ACTIVE</code> if the current APN
     * services the requested type.<br/>
     * <code>APN_TYPE_NOT_AVAILABLE</code> if the carrier does not
     * support the requested APN.<br/>
     * <code>APN_REQUEST_STARTED</code> if the request has been initiated.<br/>
     * <code>APN_REQUEST_FAILED</code> if the request was invalid.<br/>
     * A <code>ACTION_ANY_DATA_CONNECTION_STATE_CHANGED</code> broadcast will
     * indicate connection state progress.
     */
    int enableApnType(String type);

    /**
     * Disables the specified APN type, and switches back to the default APN,
     * if necessary. Switching to the default APN will not happen if default
     * data traffic has been explicitly disabled via a call to {@link #disableDataConnectivity}.
     * <p/>Only works for "special" APN types,
     * i.e., not the default APN.
     * @param type The desired APN type. Cannot be {@link #APN_TYPE_DEFAULT}.
     * @return <code>APN_ALREADY_ACTIVE</code> if the default APN
     * is already active.<br/>
     * <code>APN_REQUEST_STARTED</code> if the request to switch to the default
     * APN has been initiated.<br/>
     * <code>APN_REQUEST_FAILED</code> if the request was invalid.<br/>
     * A <code>ACTION_ANY_DATA_CONNECTION_STATE_CHANGED</code> broadcast will
     * indicate connection state progress.
     */
    int disableApnType(String type);

    /**
     * Report on whether data connectivity is allowed.
     */
    boolean isDataConnectivityPossible();

    /**
     * Returns true, if the specified apn type is established through an
     * interface that supports both v4 and v6 as opposed to separate interface
     * for v4 and v6. It is possible for network to reject dual bearer in favor
     * of separate v4 and v6 bearers and this might result in separate interface
     * for v4, v6.
     */
    boolean isServiceTypeActiveOnDualBearerInterface(String apnType);

    /**
     * Returns the name of the network interface used by the specified APN type
     * on the specified ip version.
     */
    String getInterfaceName(String apnType, IPVersion ipv);

    /**
     * Returns the list of IP address of the network interface used by the specified
     * APN type (combines V4+V6 addresses..)
     */
    String[] getIpAddress(String apnType);

    /**
     * Returns the list of IP address of the network interface used by the specified
     * APN type on specified ip version.
     */
    String[] getIpAddress(String apnType, IPVersion ipv);

    /**
     * Returns the gateway for the network interface used by the specified APN
     * type.
     */
    String getGateway(String apnType, IPVersion ipv);

    /**
     * Returns the DNS servers for the network interface used by the specified
     * APN type (combines v4+v6 list)
     */
    public String[] getDnsServers(String apnType);

    /**
     * Returns the DNS servers for the network interface used by the specified
     * APN type on specified ip version.
     */
    public String[] getDnsServers(String apnType, IPVersion ipv);
}
