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
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * Description of a mobile network registration state
 *
 */
public final class NetworkRegistrationState implements Parcelable {
    /**
     * Network domain
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({DOMAIN_CS, DOMAIN_PS})
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
    @IntDef({NOT_REG_NOT_SEARCHING, HOME, NOT_REG_SEARCHING, DENIED, UNKNOWN, ROAMING})
    public @interface RegState {}

    /** Not registered. The device is not currently searching a new operator to register */
    public static final int NOT_REG_NOT_SEARCHING = 0;
    /** Registered on home network */
    public static final int HOME = 1;
    /** Not registered. The device is currently searching a new operator to register */
    public static final int NOT_REG_SEARCHING = 2;
    /** Registration denied */
    public static final int DENIED = 3;
    /** Registration state is unknown */
    public static final int UNKNOWN = 4;
    /** Registered on roaming network */
    public static final int ROAMING = 5;

    /**
     * Supported service type
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({VOICE, DATA, SMS, VIDEO, EMERGENCY})
    public @interface SupportedServiceType {}

    public static final int VOICE = 1;
    public static final int DATA = 2;
    public static final int SMS = 3;
    public static final int VIDEO = 4;
    public static final int EMERGENCY = 5;

    @Domain
    private final int mDomain;

    @RegState
    private final int mRegState;

    private final int mAccessNetworkTechnology;

    private final int mReasonForDenial;

    private final List<Integer> mSupportedServices;

    private final CellIdentity mCellIdentity;

    /**
     * @param domain Network domain. Must be DOMAIN_CS or DOMAIN_PS.
     * @param regState Network registration state.
     * @param accessNetworkTechnology See TelephonyManager NETWORK_TYPE_XXXX.
     * @param reasonForDenial Reason for denial if the registration state is DENIED.
     * @param supportedServices The supported service.
     * @param cellIdentity The identity representing a unique cell
     */
    public NetworkRegistrationState(int domain, int regState, int accessNetworkTechnology,
                                    int reasonForDenial, List<Integer> supportedServices,
                                    CellIdentity cellIdentity) {
        mDomain = domain;
        mRegState = regState;
        mAccessNetworkTechnology = accessNetworkTechnology;
        mReasonForDenial = reasonForDenial;
        mSupportedServices = supportedServices;
        mCellIdentity = cellIdentity;
    }

    private NetworkRegistrationState(Parcel source) {
        mDomain = source.readInt();
        mRegState = source.readInt();
        mAccessNetworkTechnology = source.readInt();
        mReasonForDenial = source.readInt();
        mSupportedServices = new ArrayList<>();
        source.readList(mSupportedServices, Integer.class.getClassLoader());
        mCellIdentity = source.readParcelable(CellIdentity.class.getClassLoader());
    }

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
     * @return List of supported service types
     */
    public List<Integer> getSupportedServices() { return mSupportedServices; }

    /**
     * @return The access network technology. Must be one of TelephonyManager.NETWORK_TYPE_XXXX.
     */
    public int getAccessNetworkTechnology() {
        return mAccessNetworkTechnology;
    }

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    private static String regStateToString(int regState) {
        switch (regState) {
            case NOT_REG_NOT_SEARCHING: return "NOT_REG_NOT_SEARCHING";
            case HOME: return "HOME";
            case NOT_REG_SEARCHING: return "NOT_REG_SEARCHING";
            case DENIED: return "DENIED";
            case UNKNOWN: return "UNKNOWN";
            case ROAMING: return "ROAMING";
        }
        return "Unknown reg state " + regState;
    }

    @Override
    public String toString() {
        return "[regState=" + regStateToString(mRegState) + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || !(o instanceof NetworkRegistrationState)) {
            return false;
        }

        NetworkRegistrationState other = (NetworkRegistrationState) o;
        return mDomain == other.mDomain
                && mRegState == other.mRegState
                && mAccessNetworkTechnology == other.mAccessNetworkTechnology
                && mReasonForDenial == other.mReasonForDenial
                && mCellIdentity == other.mCellIdentity;
    }

    /** @hide */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mDomain);
        dest.writeInt(mRegState);
        dest.writeInt(mAccessNetworkTechnology);
        dest.writeInt(mReasonForDenial);
        dest.writeList(mSupportedServices);
        dest.writeParcelable(mCellIdentity, 0);
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
}
