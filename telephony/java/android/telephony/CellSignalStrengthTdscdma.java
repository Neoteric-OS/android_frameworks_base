/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;

import java.util.Objects;

/**
 * Tdscdma signal strength related information.
 *
 * @hide
 */
public final class CellSignalStrengthTdscdma extends CellSignalStrength implements Parcelable {

    private static final String LOG_TAG = "CellSignalStrengthTdscdma";
    private static final boolean DBG = false;

    private static final int TDSCDMA_SIGNAL_STRENGTH_GREAT = 12;
    private static final int TDSCDMA_SIGNAL_STRENGTH_GOOD = 8;
    private static final int TDSCDMA_SIGNAL_STRENGTH_MODERATE = 5;

    private int mSignalStrength; // in ASU; Valid values are (0-31, 99) as defined in TS 27.007 8.5
                                 // or CellInfo.UNAVAILABLE if unknown
    private int mBitErrorRate; // bit error rate (0-7, 99) as defined in TS 27.007 8.5 or
                               // CellInfo.UNAVAILABLE if unknown
    private int mRscp; // Pilot power (0-96, 255) as defined in TS 27.007 8.69 or
                       // CellInfo.UNAVAILABLE if unknown

    private int mLevel;

    /** @hide */
    public CellSignalStrengthTdscdma() {
        setDefaultValues();
    }

    /** @hide */
    public CellSignalStrengthTdscdma(android.hardware.radio.V1_0.TdScdmaSignalStrength tdscdma) {
        this(CellInfo.UNAVAILABLE, CellInfo.UNAVAILABLE, convertTdscdmaRscpTo1_2(tdscdma.rscp));
    }

    /** @hide */
    public CellSignalStrengthTdscdma(android.hardware.radio.V1_2.TdscdmaSignalStrength tdscdma) {
        this(tdscdma.signalStrength, tdscdma.bitErrorRate, tdscdma.rscp);
    }

    /** @hide */
    public CellSignalStrengthTdscdma(int ss, int ber, int rscp) {
        mSignalStrength = ss;
        mBitErrorRate = ber;
        mRscp = rscp;
        customizeForCarrier(null, null);
    }

    private static int convertTdscdmaRscpTo1_2(int rscp) {
        // The HAL 1.0 range is 25..120; the ASU/ HAL 1.2 range is 0..96;
        // yes, this means the range in 1.0 cannot express -24dBm = 96
        if (rscp >= 25 && rscp <= 120) {
            // First we flip the sign to convert from the HALs -rscp to the actual RSCP value.
            int rscpDbm = -rscp;
            // Then to convert from RSCP to ASU, we apply the offset which aligns 0 ASU to -120dBm.
            return rscpDbm + 120;
        }
        return Integer.MAX_VALUE;
    }

    /** @hide */
    public CellSignalStrengthTdscdma(CellSignalStrengthTdscdma s) {
        copyFrom(s);
    }

    /** @hide */
    protected void copyFrom(CellSignalStrengthTdscdma s) {
        mSignalStrength = s.mSignalStrength;
        mBitErrorRate = s.mBitErrorRate;
        mRscp = s.mRscp;
        mLevel = s.mLevel;
    }

    /** @hide */
    @Override
    public CellSignalStrengthTdscdma copy() {
        return new CellSignalStrengthTdscdma(this);
    }

    /** @hide */
    @Override
    public void setDefaultValues() {
        mSignalStrength = CellInfo.UNAVAILABLE;
        mBitErrorRate = CellInfo.UNAVAILABLE;
        mRscp = CellInfo.UNAVAILABLE;
        mLevel = CellInfo.UNAVAILABLE;
    }

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
        // ASU ranges from 0 to 31 - TS 27.007 Sec 8.5
        // asu = 0 (-113dB or less) is very weak
        // signal, its better to show 0 bars to the user in such cases.
        // asu = 99 is a special case, where the signal strength is unknown.
        int asu = mSignalStrength;
        if (asu <= 2 || asu == 99) {
            mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        } else if (asu >= TDSCDMA_SIGNAL_STRENGTH_GREAT) {
            mLevel = SIGNAL_STRENGTH_GREAT;
        } else if (asu >= TDSCDMA_SIGNAL_STRENGTH_GOOD) {
            mLevel = SIGNAL_STRENGTH_GOOD;
        } else if (asu >= TDSCDMA_SIGNAL_STRENGTH_MODERATE) {
            mLevel = SIGNAL_STRENGTH_MODERATE;
        } else {
            mLevel = SIGNAL_STRENGTH_POOR;
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
        return Objects.hash(mSignalStrength, mBitErrorRate, mRscp, mLevel);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CellSignalStrengthTdscdma)) return false;
        CellSignalStrengthTdscdma s = (CellSignalStrengthTdscdma) o;

        return mSignalStrength == s.mSignalStrength
                && mBitErrorRate == s.mBitErrorRate
                && mRscp == s.mRscp
                && mLevel == s.mLevel;
    }

    /**
     * @return string representation.
     */
    @Override
    public String toString() {
        return "CellSignalStrengthTdscdma:"
                + " ss=" + mSignalStrength
                + " ber=" + mBitErrorRate
                + " rscp=" + mRscp
                + " level=" + mLevel;
    }

    /** Implement the Parcelable interface */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (DBG) log("writeToParcel(Parcel, int): " + toString());
        dest.writeInt(mSignalStrength);
        dest.writeInt(mBitErrorRate);
        dest.writeInt(mRscp);
        dest.writeInt(mLevel);
    }

    /**
     * Construct a SignalStrength object from the given parcel
     * where the token is already been processed.
     */
    private CellSignalStrengthTdscdma(Parcel in) {
        mSignalStrength = in.readInt();
        mBitErrorRate = in.readInt();
        mRscp = in.readInt();
        mLevel = in.readInt();
        if (DBG) log("CellSignalStrengthTdscdma(Parcel): " + toString());
    }

    /** Implement the Parcelable interface */
    @Override
    public int describeContents() {
        return 0;
    }

    /** Implement the Parcelable interface */
    @SuppressWarnings("hiding")
    public static final Parcelable.Creator<CellSignalStrengthTdscdma> CREATOR =
            new Parcelable.Creator<CellSignalStrengthTdscdma>() {
        @Override
        public CellSignalStrengthTdscdma createFromParcel(Parcel in) {
            return new CellSignalStrengthTdscdma(in);
        }

        @Override
        public CellSignalStrengthTdscdma[] newArray(int size) {
            return new CellSignalStrengthTdscdma[size];
        }
    };

    /**
     * log
     */
    private static void log(String s) {
        Rlog.w(LOG_TAG, s);
    }
}
