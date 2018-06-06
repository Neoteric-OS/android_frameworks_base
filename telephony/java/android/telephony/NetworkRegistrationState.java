/*
 * Copyright 2017 The Android Open Source Project
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

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.AccessNetworkConstants.TransportType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Objects;

/**
 * Description of a mobile network registration state
 * @hide
 */
@SystemApi
public class NetworkRegistrationState implements Parcelable {
    /**
     * Network domain
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "DOMAIN_", value = {DOMAIN_CS, DOMAIN_PS})
    public @interface Domain {}

    /** Circuit switching domain */
    public static final int DOMAIN_CS = 1;
    /** Packet switching domain */
    public static final int DOMAIN_PS = 2;

    /**
     * Registration state
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "REG_STATE_",
            value = {REG_STATE_NOT_REG_NOT_SEARCHING, REG_STATE_HOME, REG_STATE_NOT_REG_SEARCHING,
                    REG_STATE_DENIED, REG_STATE_UNKNOWN, REG_STATE_ROAMING})
    public @interface RegState {}

    /** Not registered. The device is not currently searching a new operator to register */
    public static final int REG_STATE_NOT_REG_NOT_SEARCHING = 0;
    /** Registered on home network */
    public static final int REG_STATE_HOME                  = 1;
    /** Not registered. The device is currently searching a new operator to register */
    public static final int REG_STATE_NOT_REG_SEARCHING     = 2;
    /** Registration denied */
    public static final int REG_STATE_DENIED                = 3;
    /** Registration state is unknown */
    public static final int REG_STATE_UNKNOWN               = 4;
    /** Registered on roaming network */
    public static final int REG_STATE_ROAMING               = 5;

    /**
     * Supported service type
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "SERVICE_TYPE_",
            value = {SERVICE_TYPE_VOICE, SERVICE_TYPE_DATA, SERVICE_TYPE_SMS, SERVICE_TYPE_VIDEO,
                    SERVICE_TYPE_EMERGENCY})
    public @interface ServiceType {}

    public static final int SERVICE_TYPE_VOICE      = 1;
    public static final int SERVICE_TYPE_DATA       = 2;
    public static final int SERVICE_TYPE_SMS        = 3;
    public static final int SERVICE_TYPE_VIDEO      = 4;
    public static final int SERVICE_TYPE_EMERGENCY  = 5;

    /**
     * Network reject cause. For {@link TransportType#WWAN} transport, the values are same as
     * the definition in 3GPP TS 24.008 10.5.3.6 (UMTS) and 24.301 9.9.3.9 (LTE).
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "REJECT_CAUSE_",
            value = {REJECT_CAUSE_IMSI_UNKNOWN_IN_HLR, REJECT_CAUSE_ILLEGAL_MS,
                    REJECT_CAUSE_IMSI_UNKNOWN_IN_VLR, REJECT_CAUSE_IMEI_NOT_ACCEPTED,
                    REJECT_CAUSE_ILLEGAL_ME, REJECT_CAUSE_EPS_SERVICES_NOT_ALLOWED,
                    REJECT_CAUSE_EPS_AND_NON_EPS_SERVICES_NOT_ALLOWED,
                    REJECT_CAUSE_IDENTITY_NOT_DERIVED_BY_NETWORK, REJECT_CAUSE_PLMN_NOT_ALLOWED,
                    REJECT_CAUSE_LOCATION_AREA_NOT_ALLOWED,
                    REJECT_CAUSE_ROAMING_NOT_ALLOWED_IN_THIS_LOCATION_AREA,
                    REJECT_CAUSE_EPS_SERVICES_NOT_ALLOWED_IN_THIS_PLMN,
                    REJECT_CAUSE_NO_SUITABLE_CELLS_IN_LOCATION_AREA,
                    REJECT_CAUSE_MSC_TEMPORARILY_NOT_REACHABLE, REJECT_CAUSE_NETWORK_FAILURE,
                    REJECT_CAUSE_CS_DOMAIN_NOT_AVAILABLE, REJECT_CAUSE_ESM_FAILURE,
                    REJECT_CAUSE_MAC_FAILURE, REJECT_CAUSE_SYNCH_FAILURE, REJECT_CAUSE_CONGESTION,
                    REJECT_CAUSE_GSM_AUTHENTICATION_UNACCEPTABLE,
                    REJECT_CAUSE_SECURITY_MODE_REJECTED_UNSPECIFIED,
                    REJECT_CAUSE_NOT_AUTHORIZED_FOR_THIS_CSG,
                    REJECT_CAUSE_NON_EPS_AUTHENTICATION_UNACCEPTABLE,
                    REJECT_CAUSE_SERVICE_OPTION_NOT_SUPPORTED,
                    REJECT_CAUSE_REQUESTED_SERVICE_OPTION_NOT_SUBSCRIBED,
                    REJECT_CAUSE_SERVICE_OPTION_TEMPORARILY_OUT_OF_ORDER,
                    REJECT_CAUSE_REQUESTED_SERVICE_OPTION_NOT_AUTHORIZED,
                    REJECT_CAUSE_CALL_CANNOT_BE_IDENTIFIED,
                    REJECT_CAUSE_CS_SERVICE_TEMPORARILY_NOT_AVAILABLE,
                    REJECT_CAUSE_NO_EPS_BEARER_CONTEXT_ACTIVATED,
                    REJECT_CAUSE_RETRY_UPON_ENTRY_INTO_A_NEW_CELL,
                    REJECT_CAUSE_SEMANTICALLY_INCORRECT_MESSAGE,
                    REJECT_CAUSE_INVALID_MANDATORY_INFORMATION,
                    REJECT_CAUSE_MESSAGE_TYPE_NON_EXISTENT_OR_NOT_IMPLEMENTED,
                    REJECT_CAUSE_MESSAGE_TYPE_NOT_COMPATIBLE_WITH_THE_PROTOCOL_STATE,
                    REJECT_CAUSE_INFORMATION_ELEMENT_NON_EXISTENT_OR_NOT_IMPLEMENTED,
                    REJECT_CAUSE_CONDITIONAL_IE_ERROR,
                    REJECT_CAUSE_MESSAGE_NOT_COMPATIBLE_WITH_THE_PROTOCOL_STATE,
                    REJECT_CAUSE_PROTOCOL_ERROR_UNSPECIFIED})
    public @interface RejectCause {}

    public static final int REJECT_CAUSE_IMSI_UNKNOWN_IN_HLR                                 = 2;
    public static final int REJECT_CAUSE_ILLEGAL_MS                                          = 3;
    public static final int REJECT_CAUSE_IMSI_UNKNOWN_IN_VLR                                 = 4;
    public static final int REJECT_CAUSE_IMEI_NOT_ACCEPTED                                   = 5;
    public static final int REJECT_CAUSE_ILLEGAL_ME                                          = 6;
    public static final int REJECT_CAUSE_EPS_SERVICES_NOT_ALLOWED                            = 7;
    public static final int REJECT_CAUSE_EPS_AND_NON_EPS_SERVICES_NOT_ALLOWED                = 8;
    public static final int REJECT_CAUSE_IDENTITY_NOT_DERIVED_BY_NETWORK                     = 10;
    public static final int REJECT_CAUSE_PLMN_NOT_ALLOWED                                    = 11;
    public static final int REJECT_CAUSE_LOCATION_AREA_NOT_ALLOWED                           = 12;
    public static final int REJECT_CAUSE_ROAMING_NOT_ALLOWED_IN_THIS_LOCATION_AREA           = 13;
    public static final int REJECT_CAUSE_EPS_SERVICES_NOT_ALLOWED_IN_THIS_PLMN               = 14;
    public static final int REJECT_CAUSE_NO_SUITABLE_CELLS_IN_LOCATION_AREA                  = 15;
    public static final int REJECT_CAUSE_MSC_TEMPORARILY_NOT_REACHABLE                       = 16;
    public static final int REJECT_CAUSE_NETWORK_FAILURE                                     = 17;
    public static final int REJECT_CAUSE_CS_DOMAIN_NOT_AVAILABLE                             = 18;
    public static final int REJECT_CAUSE_ESM_FAILURE                                         = 19;
    public static final int REJECT_CAUSE_MAC_FAILURE                                         = 20;
    public static final int REJECT_CAUSE_SYNCH_FAILURE                                       = 21;
    public static final int REJECT_CAUSE_CONGESTION                                          = 22;
    public static final int REJECT_CAUSE_GSM_AUTHENTICATION_UNACCEPTABLE                     = 23;
    public static final int REJECT_CAUSE_SECURITY_MODE_REJECTED_UNSPECIFIED                  = 24;
    public static final int REJECT_CAUSE_NOT_AUTHORIZED_FOR_THIS_CSG                         = 25;
    public static final int REJECT_CAUSE_NON_EPS_AUTHENTICATION_UNACCEPTABLE                 = 26;
    public static final int REJECT_CAUSE_SERVICE_OPTION_NOT_SUPPORTED                        = 32;
    public static final int REJECT_CAUSE_REQUESTED_SERVICE_OPTION_NOT_SUBSCRIBED             = 33;
    public static final int REJECT_CAUSE_SERVICE_OPTION_TEMPORARILY_OUT_OF_ORDER             = 34;
    public static final int REJECT_CAUSE_REQUESTED_SERVICE_OPTION_NOT_AUTHORIZED             = 35;
    public static final int REJECT_CAUSE_CALL_CANNOT_BE_IDENTIFIED                           = 38;
    public static final int REJECT_CAUSE_CS_SERVICE_TEMPORARILY_NOT_AVAILABLE                = 39;
    public static final int REJECT_CAUSE_NO_EPS_BEARER_CONTEXT_ACTIVATED                     = 40;
    public static final int REJECT_CAUSE_RETRY_UPON_ENTRY_INTO_A_NEW_CELL                    = 48;
    public static final int REJECT_CAUSE_SEMANTICALLY_INCORRECT_MESSAGE                      = 95;
    public static final int REJECT_CAUSE_INVALID_MANDATORY_INFORMATION                       = 96;
    public static final int REJECT_CAUSE_MESSAGE_TYPE_NON_EXISTENT_OR_NOT_IMPLEMENTED        = 97;
    public static final int REJECT_CAUSE_MESSAGE_TYPE_NOT_COMPATIBLE_WITH_THE_PROTOCOL_STATE = 98;
    public static final int REJECT_CAUSE_INFORMATION_ELEMENT_NON_EXISTENT_OR_NOT_IMPLEMENTED = 99;
    public static final int REJECT_CAUSE_CONDITIONAL_IE_ERROR                                = 100;
    public static final int REJECT_CAUSE_MESSAGE_NOT_COMPATIBLE_WITH_THE_PROTOCOL_STATE      = 101;
    public static final int REJECT_CAUSE_PROTOCOL_ERROR_UNSPECIFIED                          = 111;

    /** {@link TransportType}*/
    private final int mTransportType;

    @Domain
    private final int mDomain;

    @RegState
    private final int mRegState;

    private final int mAccessNetworkTechnology;

    @RejectCause
    private final int mReasonForDenial;

    private final boolean mEmergencyOnly;

    private final int[] mAvailableServices;

    @Nullable
    private final CellIdentity mCellIdentity;

    @Nullable
    private VoiceSpecificRegistrationStates mVoiceSpecificStates;

    @Nullable
    private DataSpecificRegistrationStates mDataSpecificStates;

    /**
     * @param transportType Transport type. Must be one of the {@link TransportType}.
     * @param domain Network domain. Must be a {@link Domain}. For {@link TransportType#WLAN}
     * transport, this must set to {@link #DOMAIN_PS}.
     * @param regState Network registration state. Must be one of the {@link RegState}. For
     * {@link TransportType#WLAN} transport, only {@link #REG_STATE_HOME} and
     * {@link #REG_STATE_NOT_REG_NOT_SEARCHING} are valid states.
     * @param accessNetworkTechnology Access network technology. Must be one of TelephonyManager
     * NETWORK_TYPE_XXXX. For {@link TransportType#WLAN} transport, set to
     * {@link TelephonyManager#NETWORK_TYPE_IWLAN}.
     * @param reasonForDenial Reason for denial if the registration state is
     * {@link #REG_STATE_DENIED}. Must be one of the {@link RejectCause}.
     * @param emergencyOnly True if this registration is for emergency only.
     * @param availableServices The list of the supported services. Each element must be one of
     * the {@link ServiceType}.
     * @param cellIdentity The identity representing a unique cell or wifi AP. Set to null if the
     * information is not available.
     */
    public NetworkRegistrationState(int transportType, @Domain int domain, @RegState int regState,
            int accessNetworkTechnology, @RejectCause int reasonForDenial, boolean emergencyOnly,
            int[] availableServices, @Nullable CellIdentity cellIdentity) {
        mTransportType = transportType;
        mDomain = domain;
        mRegState = regState;
        mAccessNetworkTechnology = accessNetworkTechnology;
        mReasonForDenial = reasonForDenial;
        mAvailableServices = availableServices;
        mCellIdentity = cellIdentity;
        mEmergencyOnly = emergencyOnly;
    }

    /**
     * Constructor for voice network registration states.
     * @hide
     */
    public NetworkRegistrationState(int transportType, int domain, int regState,
            int accessNetworkTechnology, int reasonForDenial, boolean emergencyOnly,
            int[] availableServices, @Nullable CellIdentity cellIdentity, boolean cssSupported,
            int roamingIndicator, int systemIsInPrl, int defaultRoamingIndicator) {
        this(transportType, domain, regState, accessNetworkTechnology,
                reasonForDenial, emergencyOnly, availableServices, cellIdentity);

        mVoiceSpecificStates = new VoiceSpecificRegistrationStates(cssSupported, roamingIndicator,
                systemIsInPrl, defaultRoamingIndicator);
    }

    /**
     * Constructor for data network registration states.
     * @hide
     */
    public NetworkRegistrationState(int transportType, int domain, int regState,
            int accessNetworkTechnology, int reasonForDenial, boolean emergencyOnly,
            int[] availableServices, @Nullable CellIdentity cellIdentity, int maxDataCalls) {
        this(transportType, domain, regState, accessNetworkTechnology,
                reasonForDenial, emergencyOnly, availableServices, cellIdentity);

        mDataSpecificStates = new DataSpecificRegistrationStates(maxDataCalls);
    }

    protected NetworkRegistrationState(Parcel source) {
        mTransportType = source.readInt();
        mDomain = source.readInt();
        mRegState = source.readInt();
        mAccessNetworkTechnology = source.readInt();
        mReasonForDenial = source.readInt();
        mEmergencyOnly = source.readBoolean();
        mAvailableServices = source.createIntArray();
        mCellIdentity = source.readParcelable(CellIdentity.class.getClassLoader());
        mVoiceSpecificStates = source.readParcelable(
                VoiceSpecificRegistrationStates.class.getClassLoader());
        mDataSpecificStates = source.readParcelable(
                DataSpecificRegistrationStates.class.getClassLoader());
    }

    /**
     * @return The transport type.
     */
    public int getTransportType() { return mTransportType; }

    /**
     * @return The network domain.
     */
    public @Domain int getDomain() { return mDomain; }

    /**
     * @return The registration state.
     */
    public @RegState int getRegState() {
        return mRegState;
    }

    /**
     * @return Whether emergency is enabled.
     */
    public boolean isEmergencyEnabled() { return mEmergencyOnly; }

    /**
     * @return List of available service types.
     */
    public int[] getAvailableServices() { return mAvailableServices; }

    /**
     * @return The access network technology. Must be one of TelephonyManager.NETWORK_TYPE_XXXX.
     */
    public int getAccessNetworkTechnology() {
        return mAccessNetworkTechnology;
    }

    /**
     * @return Reason for denial from network.
     */
    public int getReasonForDenial() {
        return mReasonForDenial;
    }

    /**
     * @return The cell information.
     */
    public CellIdentity getCellIdentity() {
        return mCellIdentity;
    }

    /**
     * @hide
     */
    @Nullable
    public VoiceSpecificRegistrationStates getVoiceSpecificStates() {
        return mVoiceSpecificStates;
    }

    /**
     * @hide
     */
    @Nullable
    public DataSpecificRegistrationStates getDataSpecificStates() {
        return mDataSpecificStates;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private static String regStateToString(int regState) {
        switch (regState) {
            case REG_STATE_NOT_REG_NOT_SEARCHING: return "NOT_REG_NOT_SEARCHING";
            case REG_STATE_HOME: return "HOME";
            case REG_STATE_NOT_REG_SEARCHING: return "NOT_REG_SEARCHING";
            case REG_STATE_DENIED: return "DENIED";
            case REG_STATE_UNKNOWN: return "UNKNOWN";
            case REG_STATE_ROAMING: return "ROAMING";
        }
        return "Unknown reg state " + regState;
    }

    @Override
    public String toString() {
        return new StringBuilder("NetworkRegistrationState{")
                .append("transportType=").append(mTransportType)
                .append(" domain=").append((mDomain == DOMAIN_CS) ? "CS" : "PS")
                .append(" regState=").append(regStateToString(mRegState))
                .append(" accessNetworkTechnology=")
                .append(TelephonyManager.getNetworkTypeName(mAccessNetworkTechnology))
                .append(" reasonForDenial=").append(mReasonForDenial)
                .append(" emergencyEnabled=").append(mEmergencyOnly)
                .append(" supportedServices=").append(mAvailableServices)
                .append(" cellIdentity=").append(mCellIdentity)
                .append(" voiceSpecificStates=").append(mVoiceSpecificStates)
                .append(" dataSpecificStates=").append(mDataSpecificStates)
                .append("}").toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTransportType, mDomain, mRegState, mAccessNetworkTechnology,
                mReasonForDenial, mEmergencyOnly, mAvailableServices, mCellIdentity,
                mVoiceSpecificStates, mDataSpecificStates);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || !(o instanceof NetworkRegistrationState)) {
            return false;
        }

        NetworkRegistrationState other = (NetworkRegistrationState) o;
        return mTransportType == other.mTransportType
                && mDomain == other.mDomain
                && mRegState == other.mRegState
                && mAccessNetworkTechnology == other.mAccessNetworkTechnology
                && mReasonForDenial == other.mReasonForDenial
                && mEmergencyOnly == other.mEmergencyOnly
                && (mAvailableServices == other.mAvailableServices
                    || Arrays.equals(mAvailableServices, other.mAvailableServices))
                && equals(mCellIdentity, other.mCellIdentity)
                && equals(mVoiceSpecificStates, other.mVoiceSpecificStates)
                && equals(mDataSpecificStates, other.mDataSpecificStates);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mTransportType);
        dest.writeInt(mDomain);
        dest.writeInt(mRegState);
        dest.writeInt(mAccessNetworkTechnology);
        dest.writeInt(mReasonForDenial);
        dest.writeBoolean(mEmergencyOnly);
        dest.writeIntArray(mAvailableServices);
        dest.writeParcelable(mCellIdentity, 0);
        dest.writeParcelable(mVoiceSpecificStates, 0);
        dest.writeParcelable(mDataSpecificStates, 0);
    }

    public static final Parcelable.Creator<NetworkRegistrationState> CREATOR =
            new Parcelable.Creator<NetworkRegistrationState>() {
        @Override
        public NetworkRegistrationState createFromParcel(Parcel source) {
            return new NetworkRegistrationState(source);
        }

        @Override
        public NetworkRegistrationState[] newArray(int size) {
            return new NetworkRegistrationState[size];
        }
    };

    private static boolean equals(Object o1, Object o2) {
        if (o1 == o2) {
            return true;
        } else if (o1 == null) {
            return false;
        } else {
            return o1.equals(o2);
        }
    }
}
