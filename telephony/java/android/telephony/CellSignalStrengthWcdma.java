/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.annotation.UnsupportedAppUsage;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.telephony.Rlog;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Wcdma signal strength related information.
 */
public final class CellSignalStrengthWcdma extends CellSignalStrength implements Parcelable {

    private static final String LOG_TAG = "CellSignalStrengthWcdma";
    private static final boolean DBG = false;

    private static final int WCDMA_SIGNAL_STRENGTH_GREAT = 12;
    private static final int WCDMA_SIGNAL_STRENGTH_GOOD = 8;
    private static final int WCDMA_SIGNAL_STRENGTH_MODERATE = 5;

    private static final int MIN_WCDMA_RSCP = -120;
    private static final int MAX_WCDMA_RSCP = -24;

    private static final int MIN_WCDMA_RSSI = 0;
    private static final int MAX_WCDMA_RSSI = 31;

    @UnsupportedAppUsage
    private int mSignalStrength; // in ASU; Valid values are (0-31, 99) as defined in TS 27.007 8.5
                                 // or CellInfo.UNAVAILABLE if unknown
    @UnsupportedAppUsage
    private int mBitErrorRate; // bit error rate (0-7, 99) as defined in TS 27.007 8.5 or
                               // CellInfo.UNAVAILABLE if unknown
    private int mRscp; // bit error rate (0-96, 255) as defined in TS 27.007 8.69 or
                       // CellInfo.UNAVAILABLE if unknown
    private int mEcNo; // signal to noise radio (0-49, 255) as defined in TS 27.007 8.69 or
                       // CellInfo.UNAVAILABLE if unknown
    private int mLevel;

    /** @hide */
    public CellSignalStrengthWcdma() {
        setDefaultValues();
    }

    /** @hide */
    public CellSignalStrengthWcdma(int ss, int ber, int rscp, int ecno) {
        mSignalStrength = ss;
        mBitErrorRate = ber;
        mRscp = rscp;
        mEcNo = ecno;
        customizeForCarrier(null, null);
    }

    /** @hide */
    public CellSignalStrengthWcdma(android.hardware.radio.V1_0.WcdmaSignalStrength wcdma) {
        this(wcdma.signalStrength, wcdma.bitErrorRate, CellInfo.UNAVAILABLE, CellInfo.UNAVAILABLE);
    }

    /** @hide */
    public CellSignalStrengthWcdma(android.hardware.radio.V1_2.WcdmaSignalStrength wcdma) {
        this(wcdma.base.signalStrength, wcdma.base.bitErrorRate, wcdma.rscp, wcdma.ecno);
    }

    /** @hide */
    public CellSignalStrengthWcdma(CellSignalStrengthWcdma s) {
        copyFrom(s);
    }

    /** @hide */
    protected void copyFrom(CellSignalStrengthWcdma s) {
        mSignalStrength = s.mSignalStrength;
        mBitErrorRate = s.mBitErrorRate;
        mRscp = s.mRscp;
        mEcNo = s.mEcNo;
        mLevel = s.mLevel;
    }

    /** @hide */
    @Override
    public CellSignalStrengthWcdma copy() {
        return new CellSignalStrengthWcdma(this);
    }

    /** @hide */
    @Override
    public void setDefaultValues() {
        mSignalStrength = CellInfo.UNAVAILABLE;
        mBitErrorRate = CellInfo.UNAVAILABLE;
        mRscp = CellInfo.UNAVAILABLE;
        mEcNo = CellInfo.UNAVAILABLE;
        mLevel = CellInfo.UNAVAILABLE;
    }

    private static final String sLevelCalculationMethod = "rssi";
    private static final int[] sThresholds = new int[]{3, 8, 13, 18};

    /**
     * Retrieve an abstract level value for the overall signal strength.
     *
     * @return a single integer from 0 to 4 representing the general signal quality.
     *     0 represents very poor signal strength while 4 represents a very strong signal strength.
     */
    @Override
    public int getLevel() {
        return mLevel;
    }

    /** @hide */
    @Override
    public void customizeForCarrier(PersistableBundle cc, ServiceState ss) {
        // TODO: abstract this entire thing into a series of functions
        String calcMethod = cc.getString(
                CarrierConfigManager.KEY_WCDMA_DEFAULT_SIGNAL_STRENGTH_MEASUREMENT_STRING,
                sLevelCalculationMethod);
        int[] thresholds = cc.getIntArray(
                CarrierConfigManager.KEY_WCDMA_RSCP_THRESHOLDS_INT_ARRAY);
        if (thresholds == null) thresholds = sThresholds;

        int level = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        switch (calcMethod) {
            case "rscp":
                int rscp = getRscp();
                if (rscp < MIN_WCDMA_RSCP || rscp > MAX_WCDMA_RSCP) {
                    mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
                    return;
                }
                for (; level < thresholds.length && rscp >= thresholds[level]; level++);
                mLevel = level;
                return;
            case "rssi":
                // FIXME: let's stop using ASU internally
                int rssi = mSignalStrength;
                if (rssi < MIN_WCDMA_RSSI || rssi > MAX_WCDMA_RSSI) {
                    mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
                    return;
                }
                for (; level < thresholds.length && rssi >= thresholds[level]; level++);
                mLevel = level;
            default:
                mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        }

    }

    /**
     * Get the signal strength as dBm
     */
    @Override
    public int getDbm() {
        int dBm;

        int level = mSignalStrength;
        int asu = (level == 99 ? CellInfo.UNAVAILABLE : level);
        if (asu != CellInfo.UNAVAILABLE) {
            dBm = -113 + (2 * asu);
        } else {
            dBm = CellInfo.UNAVAILABLE;
        }
        if (DBG) log("getDbm=" + dBm);
        return dBm;
    }

    /**
     * Get the RSCP as dBm
     * @hide
     */
    public int getRscp() {
        if (mRscp > 96) return CellInfo.UNAVAILABLE;
        return -120 + mRscp;
    }

    /**
     * Get the signal level as an asu value between 0..31, 99 is unknown
     * Asu is calculated based on 3GPP RSRP. Refer to 3GPP 27.007 (Ver 10.3.0) Sec 8.69
     */
    @Override
    public int getAsuLevel() {
        // ASU ranges from 0 to 31 - TS 27.007 Sec 8.5
        // asu = 0 (-113dB or less) is very weak
        // signal, its better to show 0 bars to the user in such cases.
        // asu = 99 is a special case, where the signal strength is unknown.
        int level = mSignalStrength;
        if (DBG) log("getAsuLevel=" + level);
        return level;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSignalStrength, mBitErrorRate, mRscp, mEcNo, mLevel);
    }

    @Override
    public boolean equals (Object o) {
        if (!(o instanceof CellSignalStrengthWcdma)) return false;
        CellSignalStrengthWcdma s = (CellSignalStrengthWcdma) o;

        return mSignalStrength == s.mSignalStrength
                && mBitErrorRate == s.mBitErrorRate
                && mRscp == s.mRscp
                && mEcNo == s.mEcNo
                && mLevel == s.mLevel;
    }

    /**
     * @return string representation.
     */
    @Override
    public String toString() {
        return "CellSignalStrengthWcdma:"
                + " ss=" + mSignalStrength
                + " ber=" + mBitErrorRate
                + " rscp=" + mRscp
                + " ecno=" + mEcNo
                + " level=" + mLevel;
    }

    /** Implement the Parcelable interface */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (DBG) log("writeToParcel(Parcel, int): " + toString());
        dest.writeInt(mSignalStrength);
        dest.writeInt(mBitErrorRate);
        dest.writeInt(mRscp);
        dest.writeInt(mEcNo);
        dest.writeInt(mLevel);
    }

    /**
     * Construct a SignalStrength object from the given parcel
     * where the token is already been processed.
     */
    private CellSignalStrengthWcdma(Parcel in) {
        mSignalStrength = in.readInt();
        mBitErrorRate = in.readInt();
        mRscp = in.readInt();
        mEcNo = in.readInt();
        mLevel = in.readInt();
        if (DBG) log("CellSignalStrengthWcdma(Parcel): " + toString());
    }

    /** Implement the Parcelable interface */
    @Override
    public int describeContents() {
        return 0;
    }

    /** Implement the Parcelable interface */
    @SuppressWarnings("hiding")
    public static final Parcelable.Creator<CellSignalStrengthWcdma> CREATOR =
            new Parcelable.Creator<CellSignalStrengthWcdma>() {
        @Override
        public CellSignalStrengthWcdma createFromParcel(Parcel in) {
            return new CellSignalStrengthWcdma(in);
        }

        @Override
        public CellSignalStrengthWcdma[] newArray(int size) {
            return new CellSignalStrengthWcdma[size];
        }
    };

    /**
     * log
     */
    private static void log(String s) {
        Rlog.w(LOG_TAG, s);
    }
}
