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

import android.annotation.NonNull;
import android.annotation.UnsupportedAppUsage;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 * Contains phone signal strength related information.
 */
public class SignalStrength implements Parcelable {

    private static final String LOG_TAG = "SignalStrength";
    private static final boolean DBG = false;

    /** @hide */
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.P)
    public static final int SIGNAL_STRENGTH_NONE_OR_UNKNOWN =
            CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN; // = 0
    /** @hide */
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.P)
    public static final int SIGNAL_STRENGTH_POOR =
            CellSignalStrength.SIGNAL_STRENGTH_POOR; // = 1
    /** @hide */
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.P)
    public static final int SIGNAL_STRENGTH_MODERATE =
            CellSignalStrength.SIGNAL_STRENGTH_MODERATE; // = 2
    /** @hide */
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.P)
    public static final int SIGNAL_STRENGTH_GOOD =
            CellSignalStrength.SIGNAL_STRENGTH_GOOD; // = 3
    /** @hide */
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.P)
    public static final int SIGNAL_STRENGTH_GREAT =
            CellSignalStrength.SIGNAL_STRENGTH_GREAT; // = 4
    /** @hide */
    @UnsupportedAppUsage
    public static final int NUM_SIGNAL_STRENGTH_BINS = 5;
    /** @hide */
    public static final String[] SIGNAL_STRENGTH_NAMES = {
        "none", "poor", "moderate", "good", "great"
    };

    /**
     * Indicates the invalid measures of signal strength.
     *
     * For example, this can be returned by {@link #getEvdoDbm()} or {@link #getCdmaDbm()}
     */
    public static final int INVALID = Integer.MAX_VALUE;

    private static final int LTE_RSRP_THRESHOLDS_NUM = 4;

    private static final int WCDMA_RSCP_THRESHOLDS_NUM = 4;

    /* The type of signal measurement */
    private static final String MEASUREMENT_TYPE_RSCP = "rscp";

    CellSignalStrengthCdma mCdma;
    CellSignalStrengthGsm mGsm;
    CellSignalStrengthWcdma mWcdma;
    CellSignalStrengthTdscdma mTdscdma;
    CellSignalStrengthLte mLte;
    CellSignalStrength mPrimary; // used for things like getLevel and getAsuLevel()

    /** Parameters reported by the Radio */
    @UnsupportedAppUsage
    private int mGsmSignalStrength; // Valid values are (0-31, 99) as defined in TS 27.007 8.5
    @UnsupportedAppUsage
    private int mGsmBitErrorRate;   // bit error rate (0-7, 99) as defined in TS 27.007 8.5
    @UnsupportedAppUsage
    private int mCdmaDbm;   // This value is the RSSI value
    @UnsupportedAppUsage
    private int mCdmaEcio;  // This value is the Ec/Io
    @UnsupportedAppUsage
    private int mEvdoDbm;   // This value is the EVDO RSSI value
    @UnsupportedAppUsage
    private int mEvdoEcio;  // This value is the EVDO Ec/Io
    @UnsupportedAppUsage
    private int mEvdoSnr;   // Valid values are 0-8.  8 is the highest signal to noise ratio
    @UnsupportedAppUsage
    private int mLteSignalStrength;
    @UnsupportedAppUsage
    private int mLteRsrp;
    @UnsupportedAppUsage
    private int mLteRsrq;
    @UnsupportedAppUsage
    private int mLteRssnr;
    @UnsupportedAppUsage
    private int mLteCqi;
    @UnsupportedAppUsage
    private int mTdScdmaRscp; // Valid values are -24...-120dBm or INVALID if unknown
    private int mWcdmaSignalStrength;
    private int mWcdmaRscpAsu;  // the WCDMA RSCP in ASU as reported from the HAL
    @UnsupportedAppUsage
    private int mWcdmaRscp;     // the WCDMA RSCP in dBm

    /** Parameters from the framework */
    @UnsupportedAppUsage
    private int mLteRsrpBoost; // offset to be reduced from the rsrp threshold while calculating
                                // signal strength level

    /**
     * Create a new SignalStrength from a intent notifier Bundle
     *
     * This method is used by PhoneStateIntentReceiver and maybe by
     * external applications.
     *
     * @param m Bundle from intent notifier
     * @return newly created SignalStrength
     *
     * @hide
     */
    @UnsupportedAppUsage
    public static SignalStrength newFromBundle(Bundle m) {
        SignalStrength ret;
        ret = new SignalStrength();
        ret.setFromNotifierBundle(m);
        return ret;
    }

    /**
     * Empty constructor
     *
     * @hide
     */
    @UnsupportedAppUsage
    public SignalStrength() {
        this(true);
    }

    /**
     * This constructor is used to create SignalStrength with default
     * values and set the gsmFlag with the value passed in the input
     *
     * @param gsmFlag true if Gsm Phone,false if Cdma phone
     * @return newly created SignalStrength
     * @hide
     */
    @UnsupportedAppUsage
    public SignalStrength(boolean gsmFlag) {
        this(new CellSignalStrengthCdma(), new CellSignalStrengthGsm(),
                new CellSignalStrengthWcdma(), new CellSignalStrengthTdscdma(),
                new CellSignalStrengthLte(), 0 /* no Rsrp Boost */);
    }

    /**
     * Constructor with all fields present
     *
     * @hide
     */
    public SignalStrength(
            @NonNull CellSignalStrengthCdma cdma,
            @NonNull CellSignalStrengthGsm gsm,
            @NonNull CellSignalStrengthWcdma wcdma,
            @NonNull CellSignalStrengthTdscdma tdscdma,
            @NonNull CellSignalStrengthLte lte,
            int lteRsrpBoost) {
        // TODO(nharold): Input Validation
        mCdma = cdma;
        mGsm = gsm;
        mWcdma = wcdma;
        mTdscdma = tdscdma;
        mLte = lte;
        mLteRsrpBoost = lteRsrpBoost;
        if (DBG) log("initialize: " + toString());
    }

    /**
     * Constructor for Radio HAL V1.0
     *
     * @hide
     */
    public SignalStrength(android.hardware.radio.V1_0.SignalStrength signalStrength) {
        this(new CellSignalStrengthCdma(signalStrength.cdma, signalStrength.evdo),
                new CellSignalStrengthGsm(signalStrength.gw),
                new CellSignalStrengthWcdma(),
                new CellSignalStrengthTdscdma(signalStrength.tdScdma),
                new CellSignalStrengthLte(signalStrength.lte));
    }

    /**
     * Constructor for Radio HAL V1.2
     *
     * @hide
     */
    public SignalStrength(android.hardware.radio.V1_2.SignalStrength signalStrength) {
        this(new CellSignalStrengthCdma(signalStrength.cdma, signalStrength.evdo),
                new CellSignalStrengthGsm(signalStrength.gsm),
                new CellSignalStrengthWcdma(signalStrength.wcdma),
                new CellSignalStrengthTdscdma(signalStrength.tdScdma),
                new CellSignalStrengthLte(signalStrength.lte));
    }

    /**
     * Comprehensive Constructor
     *
     * @hide
     */
    public SignalStrength(CellSignalStrengthCdma cdma,
            CellSignalStrengthGsm gsm,
            CellSignalStrengthWcdma wcdma,
            CellSignalStrengthTdscdma tdscdma,
            CellSignalStrengthLte lte) {
        mCdma = cdma;
        mGsm = gsm;
        mWcdma = wcdma;
        mTdscdma = tdscdma;
        mLte = lte;
        // Find the primary signal strength for things like getLevel()
        mPrimary = getPrimary();

        // TODO(nharold): calculate levels and such here
    }

    private CellSignalStrength getPrimary() {
        if (mLte.getLevel() != SIGNAL_STRENGTH_NONE_OR_UNKNOWN) return mLte;
        if (mCdma.getLevel() != SIGNAL_STRENGTH_NONE_OR_UNKNOWN) return mCdma;
        if (mTdscdma.getLevel() != SIGNAL_STRENGTH_NONE_OR_UNKNOWN) return mTdscdma;
        if (mWcdma.getLevel() != SIGNAL_STRENGTH_NONE_OR_UNKNOWN) return mWcdma;
        if (mGsm.getLevel() != SIGNAL_STRENGTH_NONE_OR_UNKNOWN) return mGsm;
        return mLte;
    }

    /** @hide */
    public void customizeForCarrier(PersistableBundle cc, ServiceState ss) {
        mCdma.customizeForCarrier(cc, ss);
        mGsm.customizeForCarrier(cc, ss);
        mWcdma.customizeForCarrier(cc, ss);
        mTdscdma.customizeForCarrier(cc, ss);
        mLte.customizeForCarrier(cc, ss);
    }

    /**
     * Copy constructors
     *
     * @param s Source SignalStrength
     *
     * @hide
     */
    @UnsupportedAppUsage
    public SignalStrength(SignalStrength s) {
        copyFrom(s);
    }

    /**
     * @hide
     */
    @UnsupportedAppUsage
    protected void copyFrom(SignalStrength s) {
        mCdma = new CellSignalStrengthCdma(s.mCdma);
        mGsm = new CellSignalStrengthGsm(s.mGsm);
        mWcdma = new CellSignalStrengthWcdma(s.mWcdma);
        mTdscdma = new CellSignalStrengthTdscdma(s.mTdscdma);
        mLte = new CellSignalStrengthLte(s.mLte);
        mLteRsrpBoost = s.mLteRsrpBoost;
    }

    /**
     * Construct a SignalStrength object from the given parcel.
     *
     * @hide
     */
    @UnsupportedAppUsage
    public SignalStrength(Parcel in) {
        if (DBG) log("Size of signalstrength parcel:" + in.dataSize());

        mCdma = in.readParcelable(CellSignalStrengthCdma.class.getClassLoader());
        mGsm = in.readParcelable(CellSignalStrengthGsm.class.getClassLoader());
        mWcdma = in.readParcelable(CellSignalStrengthWcdma.class.getClassLoader());
        mTdscdma = in.readParcelable(CellSignalStrengthTdscdma.class.getClassLoader());
        mLte = in.readParcelable(CellSignalStrengthLte.class.getClassLoader());
        mLteRsrpBoost = in.readInt();
    }

    /**
     * {@link Parcelable#writeToParcel}
     */
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(mCdma, 0);
        out.writeParcelable(mGsm, 0);
        out.writeParcelable(mWcdma, 0);
        out.writeParcelable(mTdscdma, 0);
        out.writeParcelable(mLte, 0);
        out.writeInt(mLteRsrpBoost);
    }

    /**
     * {@link Parcelable#describeContents}
     */
    public int describeContents() {
        return 0;
    }

    /**
     * {@link Parcelable.Creator}
     *
     * @hide
     */
    @UnsupportedAppUsage
    public static final Parcelable.Creator<SignalStrength> CREATOR = new Parcelable.Creator() {
        public SignalStrength createFromParcel(Parcel in) {
            return new SignalStrength(in);
        }

        public SignalStrength[] newArray(int size) {
            return new SignalStrength[size];
        }
    };

    /**
     * Get the GSM Signal Strength, valid values are (0-31, 99) as defined in TS
     * 27.007 8.5
     */
    public int getGsmSignalStrength() {
        return mGsm.getAsuLevel();
    }

    /**
     * Get the GSM bit error rate (0-7, 99) as defined in TS 27.007 8.5
     */
    public int getGsmBitErrorRate() {
        return mGsm.getBitErrorRate();
    }

    /**
     * Get the CDMA RSSI value in dBm
     *
     * @return the CDMA RSSI value or {@link #INVALID} if invalid
     */
    public int getCdmaDbm() {
        return mCdma.getCdmaDbm();
    }

    /**
     * Get the CDMA Ec/Io value in dB*10
     */
    public int getCdmaEcio() {
        return mCdma.getCdmaEcio();
    }

    /**
     * Get the EVDO RSSI value in dBm
     *
     * @return the EVDO RSSI value or {@link #INVALID} if invalid
     */
    public int getEvdoDbm() {
        return mCdma.getEvdoDbm();
    }

    /**
     * Get the EVDO Ec/Io value in dB*10
     */
    public int getEvdoEcio() {
        return mCdma.getEvdoEcio();
    }

    /**
     * Get the signal to noise ratio. Valid values are 0-8. 8 is the highest.
     */
    public int getEvdoSnr() {
        return mCdma.getEvdoSnr();
    }

    /** @hide */
    @UnsupportedAppUsage
    public int getLteSignalStrength() {
        return mLte.getRssi();
    }

    /** @hide */
    @UnsupportedAppUsage
    public int getLteRsrp() {
        return mLte.getRsrp();
    }

    /** @hide */
    @UnsupportedAppUsage
    public int getLteRsrq() {
        return mLte.getRsrq();
    }

    /** @hide */
    @UnsupportedAppUsage
    public int getLteRssnr() {
        return mLte.getRssnr();
    }

    /** @hide */
    @UnsupportedAppUsage
    public int getLteCqi() {
        return mLte.getCqi();
    }

    /** @hide */
    public int getLteRsrpBoost() {
        return mLteRsrpBoost;
    }

    /**
     * Retrieve an abstract level value for the overall signal strength.
     *
     * @return a single integer from 0 to 4 representing the general signal quality.
     *     This may take into account many different radio technology inputs.
     *     0 represents very poor signal strength
     *     while 4 represents a very strong signal strength.
     */
    public int getLevel() {
        return mPrimary.getLevel();
    }

    /**
     * Get the signal level as an asu value between 0..31, 99 is unknown
     *
     * @hide
     */
    @UnsupportedAppUsage
    public int getAsuLevel() {
        return mPrimary.getAsuLevel();
    }

    /**
     * Get the signal strength as dBm
     *
     * @hide
     */
    @UnsupportedAppUsage
    public int getDbm() {
        return mPrimary.getDbm();
    }

    /**
     * Get Gsm signal strength as dBm
     *
     * @hide
     */
    @UnsupportedAppUsage
    public int getGsmDbm() {
        return mGsm.getDbm();
    }

    /**
     * Get gsm as level 0..4
     *
     * @hide
     */
    @UnsupportedAppUsage
    public int getGsmLevel() {
        return mGsm.getLevel();
    }

    /**
     * Get the gsm signal level as an asu value between 0..31, 99 is unknown
     *
     * @hide
     */
    @UnsupportedAppUsage
    public int getGsmAsuLevel() {
        return mGsm.getAsuLevel();
    }

    /**
     * Get cdma as level 0..4
     *
     * @hide
     */
    @UnsupportedAppUsage
    public int getCdmaLevel() {
        return mCdma.getLevel();
    }

    /**
     * Get the cdma signal level as an asu value between 0..31, 99 is unknown
     *
     * @hide
     */
    @UnsupportedAppUsage
    public int getCdmaAsuLevel() {
        return mCdma.getAsuLevel();
    }

    /**
     * Get Evdo as level 0..4
     *
     * @hide
     */
    @UnsupportedAppUsage
    public int getEvdoLevel() {
        return mCdma.getEvdoLevel();
    }

    /**
     * Get the evdo signal level as an asu value between 0..31, 99 is unknown
     *
     * @hide
     */
    @UnsupportedAppUsage
    public int getEvdoAsuLevel() {
        return mCdma.getEvdoAsuLevel();
    }

    /**
     * Get LTE as dBm
     *
     * @hide
     */
    @UnsupportedAppUsage
    public int getLteDbm() {
        return mLte.getRsrp();
    }

    /**
     * Get LTE as level 0..4
     *
     * @hide
     */
    @UnsupportedAppUsage
    public int getLteLevel() {
        return mLte.getLevel();
    }

    /**
     * Get the LTE signal level as an asu value between 0..97, 99 is unknown
     * Asu is calculated based on 3GPP RSRP. Refer to 3GPP 27.007 (Ver 10.3.0) Sec 8.69
     *
     * @hide
     */
    @UnsupportedAppUsage
    public int getLteAsuLevel() {
        return mLte.getAsuLevel();
    }

    /**
     * @return true if this is for GSM
     */
    public boolean isGsm() {
        return !(mPrimary instanceof CellSignalStrengthCdma);
    }

    /**
     * @return get TD_SCDMA dbm
     *
     * @hide
     */
    @UnsupportedAppUsage
    public int getTdScdmaDbm() {
        return mTdscdma.getRscp();
    }

    /**
     * Get TD-SCDMA as level 0..4
     * Range : 25 to 120
     * INT_MAX: 0x7FFFFFFF denotes invalid value
     * Reference: 3GPP TS 25.123, section 9.1.1.1
     *
     * @hide
     */
    @UnsupportedAppUsage
    public int getTdScdmaLevel() {
        return mTdscdma.getLevel();
     }

    /**
     * Get the TD-SCDMA signal level as an asu value.
     *
     * @hide
     */
    @UnsupportedAppUsage
    public int getTdScdmaAsuLevel() {
        return mTdscdma.getAsuLevel();
    }

    /**
     * Gets WCDMA RSCP as a dbm value between -120 and -24, as defined in TS 27.007 8.69.
     *
     * @hide
     */
    public int getWcdmaRscp() {
        return mWcdma.getRscp();
    }

    /**
     * Get the WCDMA signal level as an ASU value between 0-96, 255 is unknown
     *
     * @hide
     */
    public int getWcdmaAsuLevel() {
        /*
         * 3GPP 27.007 (Ver 10.3.0) Sec 8.69
         * 0      -120 dBm or less
         * 1      -119 dBm
         * 2...95 -118... -25 dBm
         * 96     -24 dBm or greater
         * 255    not known or not detectable
         */
        return mWcdma.getAsuLevel();
    }

    /**
     * Gets WCDMA signal strength as a dbm value between -120 and -24, as defined in TS 27.007 8.69.
     *
     * @hide
     */
    public int getWcdmaDbm() {
        return mWcdma.getDbm();
    }

    /**
     * Get WCDMA as level 0..4
     *
     * @hide
     */
    public int getWcdmaLevel() {
        return mWcdma.getLevel();
    }

   /**
     * @return hash code
     */
    @Override
    public int hashCode() {
        int primeNum = 31;
        return ((mGsmSignalStrength * primeNum)
                + (mGsmBitErrorRate * primeNum)
                + (mCdmaDbm * primeNum) + (mCdmaEcio * primeNum)
                + (mEvdoDbm * primeNum) + (mEvdoEcio * primeNum) + (mEvdoSnr * primeNum)
                + (mLteSignalStrength * primeNum) + (mLteRsrp * primeNum)
                + (mLteRsrq * primeNum) + (mLteRssnr * primeNum) + (mLteCqi * primeNum)
                + (mLteRsrpBoost * primeNum) + (mTdScdmaRscp * primeNum)
                + (mWcdmaSignalStrength * primeNum) + (mWcdmaRscpAsu * primeNum)
                + (mWcdmaRscp * primeNum));
    }

    /**
     * @return true if the signal strengths are the same
     */
    @Override
    public boolean equals (Object o) {
        SignalStrength s;

        try {
            s = (SignalStrength) o;
        } catch (ClassCastException ex) {
            return false;
        }

        if (o == null) {
            return false;
        }

        return mGsmSignalStrength == s.mGsmSignalStrength
                && mGsmBitErrorRate == s.mGsmBitErrorRate
                && mCdmaDbm == s.mCdmaDbm
                && mCdmaEcio == s.mCdmaEcio
                && mEvdoDbm == s.mEvdoDbm
                && mEvdoEcio == s.mEvdoEcio
                && mEvdoSnr == s.mEvdoSnr
                && mLteSignalStrength == s.mLteSignalStrength
                && mLteRsrp == s.mLteRsrp
                && mLteRsrq == s.mLteRsrq
                && mLteRssnr == s.mLteRssnr
                && mLteCqi == s.mLteCqi
                && mLteRsrpBoost == s.mLteRsrpBoost
                && mTdScdmaRscp == s.mTdScdmaRscp
                && mWcdmaSignalStrength == s.mWcdmaSignalStrength
                && mWcdmaRscpAsu == s.mWcdmaRscpAsu
                && mWcdmaRscp == s.mWcdmaRscp;
    }

    /**
     * @return string representation.
     */
    @Override
    public String toString() {
        return "SignalStrength:"
                + " " + mGsmSignalStrength
                + " " + mGsmBitErrorRate
                + " " + mCdmaDbm
                + " " + mCdmaEcio
                + " " + mEvdoDbm
                + " " + mEvdoEcio
                + " " + mEvdoSnr
                + " " + mLteSignalStrength
                + " " + mLteRsrp
                + " " + mLteRsrq
                + " " + mLteRssnr
                + " " + mLteCqi
                + " " + mLteRsrpBoost
                + " " + mTdScdmaRscp
                + " " + mWcdmaSignalStrength
                + " " + mWcdmaRscpAsu
                + " " + mWcdmaRscp;
    }

    /**
     * Set SignalStrength based on intent notifier map
     *
     * @param m intent notifier map
     * @hide
     */
    @UnsupportedAppUsage
    private void setFromNotifierBundle(Bundle m) {
        mGsmSignalStrength = m.getInt("GsmSignalStrength");
        mGsmBitErrorRate = m.getInt("GsmBitErrorRate");
        mCdmaDbm = m.getInt("CdmaDbm");
        mCdmaEcio = m.getInt("CdmaEcio");
        mEvdoDbm = m.getInt("EvdoDbm");
        mEvdoEcio = m.getInt("EvdoEcio");
        mEvdoSnr = m.getInt("EvdoSnr");
        mLteSignalStrength = m.getInt("LteSignalStrength");
        mLteRsrp = m.getInt("LteRsrp");
        mLteRsrq = m.getInt("LteRsrq");
        mLteRssnr = m.getInt("LteRssnr");
        mLteCqi = m.getInt("LteCqi");
        mLteRsrpBoost = m.getInt("LteRsrpBoost");
        mTdScdmaRscp = m.getInt("TdScdma");
        mWcdmaSignalStrength = m.getInt("WcdmaSignalStrength");
        mWcdmaRscpAsu = m.getInt("WcdmaRscpAsu");
        mWcdmaRscp = m.getInt("WcdmaRscp");
    }

    /**
     * Set intent notifier Bundle based on SignalStrength
     *
     * @param m intent notifier Bundle
     * @hide
     */
    @UnsupportedAppUsage
    public void fillInNotifierBundle(Bundle m) {
        m.putInt("GsmSignalStrength", mGsmSignalStrength);
        m.putInt("GsmBitErrorRate", mGsmBitErrorRate);
        m.putInt("CdmaDbm", mCdmaDbm);
        m.putInt("CdmaEcio", mCdmaEcio);
        m.putInt("EvdoDbm", mEvdoDbm);
        m.putInt("EvdoEcio", mEvdoEcio);
        m.putInt("EvdoSnr", mEvdoSnr);
        m.putInt("LteSignalStrength", mLteSignalStrength);
        m.putInt("LteRsrp", mLteRsrp);
        m.putInt("LteRsrq", mLteRsrq);
        m.putInt("LteRssnr", mLteRssnr);
        m.putInt("LteCqi", mLteCqi);
        m.putInt("LteRsrpBoost", mLteRsrpBoost);
        m.putInt("TdScdma", mTdScdmaRscp);
        m.putInt("WcdmaSignalStrength", mWcdmaSignalStrength);
        m.putInt("WcdmaRscpAsu", mWcdmaRscpAsu);
        m.putInt("WcdmaRscp", mWcdmaRscp);
        m.putBoolean("IsGsm", isGsm());
    }

    /**
     * Gets the default threshold array for determining the display level of LTE signal bar.
     *
     * @return int array for determining the display level.
     */
    private int[] getDefaultLteRsrpThresholds() {
        return CarrierConfigManager.getDefaultConfig().getIntArray(
                CarrierConfigManager.KEY_LTE_RSRP_THRESHOLDS_INT_ARRAY);
    }

    /**
     * Gets the default threshold array for determining the display level of WCDMA signal bar.
     *
     * @return int array for determining the display level.
     */
    private int[] getDefaultWcdmaRscpThresholds() {
        return CarrierConfigManager.getDefaultConfig().getIntArray(
                CarrierConfigManager.KEY_WCDMA_RSCP_THRESHOLDS_INT_ARRAY);
    }

    /**
     * log
     */
    private static void log(String s) {
        Rlog.w(LOG_TAG, s);
    }
}
