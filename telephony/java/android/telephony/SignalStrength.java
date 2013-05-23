/*
 * Copyright (C) 2012 The Android Open Source Project
 * Portions Copyright (C) 2012-2013 Motorola Mobility LLC All Rights Reserved.
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

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemProperties;
import com.android.internal.telephony.TelephonyProperties;
import android.telephony.Rlog;

/**
 * Contains phone signal strength related information.
 */
public class SignalStrength implements Parcelable {

    private static final String LOG_TAG = "SignalStrength";
    private static final boolean DBG = false;

    /** @hide */
    public static final int SIGNAL_STRENGTH_NONE_OR_UNKNOWN = 0;
    /** @hide */
    public static final int SIGNAL_STRENGTH_POOR = 1;
    /** @hide */
    public static final int SIGNAL_STRENGTH_MODERATE = 2;
    /** @hide */
    public static final int SIGNAL_STRENGTH_GOOD = 3;
    /** @hide */
    public static final int SIGNAL_STRENGTH_GREAT = 4;
    // Allow for up to 6 signal strength levels
    /** @hide */
    public static final int SIGNAL_STRENGTH_GREATER = 5;
    /** @hide */
    public static final int SIGNAL_STRENGTH_GREATEST = 6;
    /** @hide */
    public static final int NUM_SIGNAL_STRENGTH_BINS = 7;
    /** @hide */
    public static final String[] SIGNAL_STRENGTH_NAMES = {
        "none", "poor", "moderate", "good", "great",
        "greater", "greatest"
    };

    private static final int DEFAULT_MAX_LEVEL = 4;

    /** @hide */
    //Use int max, as -1 is a valid value in signal strength
    public static final int INVALID = 0x7FFFFFFF;

    private int mGsmSignalStrength; // Valid values are (0-31, 99) as defined in TS 27.007 8.5
    private int mGsmBitErrorRate;   // bit error rate (0-7, 99) as defined in TS 27.007 8.5
    private int mCdmaDbm;   // This value is the RSSI value
    private int mCdmaEcio;  // This value is the Ec/Io
    private int mEvdoDbm;   // This value is the EVDO RSSI value
    private int mEvdoEcio;  // This value is the EVDO Ec/Io
    private int mEvdoSnr;   // Valid values are 0-8.  8 is the highest signal to noise ratio
    private int mLteSignalStrength;
    private int mLteRsrp;
    private int mLteRsrq;
    private int mLteRssnr;
    private int mLteCqi;
    // The Received Signal Code Power in dBm multipled by -1;
    private int mUmtsRscp; // valid value are (-120~-25) as defined, 3GPP TS 25.123 9.1.1.1
    // Received energy per chip divided by the power density in the band.
    private int mUmtsEcno; // Ec/No (dB) = RSCP(db) - RSSI (db)

    private int mGsmLevel;
    private int mUmtsLevel;
    private int mCdmaLevel;
    private int mEvdoLevel;
    private int mLteLevel;

    // all radio tech should only have same max level;
    private int mMaxLevel;

    private boolean isGsm; // This value is set by the ServiceStateTracker onSignalStrengthResult

    // this is for CDMA/LTE phones, for LTE on CDMA, isGsm is confused
    private boolean maybeLteBasedOnCdma = false;

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
    public SignalStrength() {
        mGsmSignalStrength = 99;
        mGsmBitErrorRate = -1;
        mCdmaDbm = -1;
        mCdmaEcio = -1;
        mEvdoDbm = -1;
        mEvdoEcio = -1;
        mEvdoSnr = -1;
        mLteSignalStrength = 99;
        mLteRsrp = INVALID;
        mLteRsrq = INVALID;
        mLteRssnr = INVALID;
        mLteCqi = INVALID;
        mUmtsRscp = INVALID;
        mUmtsEcno = INVALID;
        isGsm = true;

        mGsmLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        mUmtsLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        mCdmaLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        mEvdoLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        mLteLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;

        mMaxLevel = DEFAULT_MAX_LEVEL;
    }

    /**
     * This constructor is used to create SignalStrength with default
     * values and set the isGsmFlag with the value passed in the input
     *
     * @param gsmFlag true if Gsm Phone,false if Cdma phone
     * @return newly created SignalStrength
     * @hide
     */
    public SignalStrength(boolean gsmFlag) {
        mGsmSignalStrength = 99;
        mGsmBitErrorRate = -1;
        mCdmaDbm = -1;
        mCdmaEcio = -1;
        mEvdoDbm = -1;
        mEvdoEcio = -1;
        mEvdoSnr = -1;
        mLteSignalStrength = 99;
        mLteRsrp = INVALID;
        mLteRsrq = INVALID;
        mLteRssnr = INVALID;
        mLteCqi = INVALID;
        mUmtsRscp = INVALID;
        mUmtsEcno = INVALID;
        isGsm = gsmFlag;
    }

    /**
     * Constructor
     *
     * @hide
     */
    public SignalStrength(int gsmSignalStrength, int gsmBitErrorRate,
            int cdmaDbm, int cdmaEcio,
            int evdoDbm, int evdoEcio, int evdoSnr,
            int lteSignalStrength, int lteRsrp, int lteRsrq, int lteRssnr, int lteCqi,
            int umtsRscp, int umtsEcno, boolean gsmFlag) {
        initialize(gsmSignalStrength, gsmBitErrorRate, cdmaDbm, cdmaEcio,
                evdoDbm, evdoEcio, evdoSnr, lteSignalStrength, lteRsrp,
                lteRsrq, lteRssnr, lteCqi, umtsRscp, umtsEcno, gsmFlag);
    }

    /**
     * Constructor
     *
     * @hide
     */
    public SignalStrength(int gsmSignalStrength, int gsmBitErrorRate,
            int cdmaDbm, int cdmaEcio,
            int evdoDbm, int evdoEcio, int evdoSnr,
            int lteSignalStrength, int lteRsrp, int lteRsrq, int lteRssnr, int lteCqi,
            boolean gsmFlag) {
        initialize(gsmSignalStrength, gsmBitErrorRate, cdmaDbm, cdmaEcio,
                evdoDbm, evdoEcio, evdoSnr, lteSignalStrength, lteRsrp,
                lteRsrq, lteRssnr, lteCqi, INVALID, -1, gsmFlag);
    }

    /**
     * Constructor
     *
     * @hide
     */
    public SignalStrength(int gsmSignalStrength, int gsmBitErrorRate,
            int cdmaDbm, int cdmaEcio,
            int evdoDbm, int evdoEcio, int evdoSnr,
            boolean gsmFlag) {
        initialize(gsmSignalStrength, gsmBitErrorRate, cdmaDbm, cdmaEcio,
                evdoDbm, evdoEcio, evdoSnr, 99, INVALID,
                INVALID, INVALID, INVALID, INVALID, -1, gsmFlag);
    }

    /**
     * Copy constructors
     *
     * @param s Source SignalStrength
     *
     * @hide
     */
    public SignalStrength(SignalStrength s) {
        copyFrom(s);
    }

    /**
     * Initialize gsm/cdma values, sets lte values to defaults.
     *
     * @param gsmSignalStrength
     * @param gsmBitErrorRate
     * @param cdmaDbm
     * @param cdmaEcio
     * @param evdoDbm
     * @param evdoEcio
     * @param evdoSnr
     * @param gsm
     *
     * @hide
     */
    public void initialize(int gsmSignalStrength, int gsmBitErrorRate,
            int cdmaDbm, int cdmaEcio,
            int evdoDbm, int evdoEcio, int evdoSnr,
            boolean gsm) {
        initialize(gsmSignalStrength, gsmBitErrorRate, cdmaDbm, cdmaEcio,
                evdoDbm, evdoEcio, evdoSnr, 99, INVALID,
                INVALID, INVALID, INVALID, INVALID, -1, gsm);
    }

    /**
     * Initialize all the values
     *
     * @param gsmSignalStrength
     * @param gsmBitErrorRate
     * @param cdmaDbm
     * @param cdmaEcio
     * @param evdoDbm
     * @param evdoEcio
     * @param evdoSnr
     * @param lteSignalStrength
     * @param lteRsrp
     * @param lteRsrq
     * @param lteRssnr
     * @param lteCqi
     * @param umtsRscp
     *@param  umtsEcno
     * @param gsm
     *
     * @hide
     */
    public void initialize(int gsmSignalStrength, int gsmBitErrorRate,
            int cdmaDbm, int cdmaEcio,
            int evdoDbm, int evdoEcio, int evdoSnr,
            int lteSignalStrength, int lteRsrp, int lteRsrq, int lteRssnr, int lteCqi,
            int umtsRscp, int umtsEcno, boolean gsm) {
        mGsmSignalStrength = gsmSignalStrength;
        mGsmBitErrorRate = gsmBitErrorRate;
        mCdmaDbm = cdmaDbm;
        mCdmaEcio = cdmaEcio;
        mEvdoDbm = evdoDbm;
        mEvdoEcio = evdoEcio;
        mEvdoSnr = evdoSnr;
        mLteSignalStrength = lteSignalStrength;
        mLteRsrp = lteRsrp;
        mLteRsrq = lteRsrq;
        mLteRssnr = lteRssnr;
        mLteCqi = lteCqi;
        mUmtsRscp = umtsRscp;
        mUmtsEcno = umtsEcno;
        isGsm = gsm;
        if (DBG) log("initialize: " + toString());

        mGsmLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        mUmtsLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        mCdmaLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        mEvdoLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        mLteLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        mMaxLevel = DEFAULT_MAX_LEVEL;
    }

    /**
     * @hide
     */
    protected void copyFrom(SignalStrength s) {
        mGsmSignalStrength = s.mGsmSignalStrength;
        mGsmBitErrorRate = s.mGsmBitErrorRate;
        mCdmaDbm = s.mCdmaDbm;
        mCdmaEcio = s.mCdmaEcio;
        mEvdoDbm = s.mEvdoDbm;
        mEvdoEcio = s.mEvdoEcio;
        mEvdoSnr = s.mEvdoSnr;
        mLteSignalStrength = s.mLteSignalStrength;
        mLteRsrp = s.mLteRsrp;
        mLteRsrq = s.mLteRsrq;
        mLteRssnr = s.mLteRssnr;
        mLteCqi = s.mLteCqi;
        mUmtsRscp = s.mUmtsRscp;
        mUmtsEcno = s.mUmtsEcno;
        isGsm = s.isGsm;

        mGsmLevel = s.mGsmLevel;
        mUmtsLevel = s.mUmtsLevel;
        mCdmaLevel = s.mCdmaLevel;
        mEvdoLevel = s.mEvdoLevel;
        mLteLevel = s.mLteLevel;
        mMaxLevel = s.mMaxLevel;
    }

    /**
     * Construct a SignalStrength object from the given parcel.
     *
     * @hide
     */
    public SignalStrength(Parcel in) {
        if (DBG) log("Size of signalstrength parcel:" + in.dataSize());

        if (in.dataAvail() > 0) mGsmSignalStrength = in.readInt();
        if (in.dataAvail() > 0) mGsmBitErrorRate = in.readInt();
        if (in.dataAvail() > 0) mCdmaDbm = in.readInt();
        if (in.dataAvail() > 0) mCdmaEcio = in.readInt();
        if (in.dataAvail() > 0) mEvdoDbm = in.readInt();
        if (in.dataAvail() > 0) mEvdoEcio = in.readInt();
        if (in.dataAvail() > 0) mEvdoSnr = in.readInt();
        if (in.dataAvail() > 0) mLteSignalStrength = in.readInt();
        if (in.dataAvail() > 0) mLteRsrp = in.readInt();
        if (in.dataAvail() > 0) mLteRsrq = in.readInt();
        if (in.dataAvail() > 0) mLteRssnr = in.readInt();
        if (in.dataAvail() > 0) mLteCqi = in.readInt();
        mUmtsRscp = in.dataAvail() > 0 ? in.readInt() : INVALID;
        mUmtsEcno = in.dataAvail() > 0 ? in.readInt() : INVALID;
        // TODO: following are not from RIL, need to read from parcel?
        if (in.dataAvail() > 0) isGsm = (in.readInt() != 0);
        if (in.dataAvail() > 0) mGsmLevel = in.readInt();
        if (in.dataAvail() > 0) mUmtsLevel = in.readInt();
        if (in.dataAvail() > 0) mCdmaLevel = in.readInt();
        if (in.dataAvail() > 0) mEvdoLevel = in.readInt();
        if (in.dataAvail() > 0) mLteLevel = in.readInt();
        if (in.dataAvail() > 0) mMaxLevel = in.readInt();
    }

    /**
     * {@link Parcelable#writeToParcel}
     */
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mGsmSignalStrength);
        out.writeInt(mGsmBitErrorRate);
        out.writeInt(mCdmaDbm);
        out.writeInt(mCdmaEcio);
        out.writeInt(mEvdoDbm);
        out.writeInt(mEvdoEcio);
        out.writeInt(mEvdoSnr);
        out.writeInt(mLteSignalStrength);
        out.writeInt(mLteRsrp);
        out.writeInt(mLteRsrq);
        out.writeInt(mLteRssnr);
        out.writeInt(mLteCqi);
        out.writeInt(mUmtsRscp);
        out.writeInt(mUmtsEcno);
        out.writeInt(isGsm ? 1 : 0);
        out.writeInt(mGsmLevel);
        out.writeInt(mUmtsLevel);
        out.writeInt(mCdmaLevel);
        out.writeInt(mEvdoLevel);
        out.writeInt(mLteLevel);
        out.writeInt(mMaxLevel);
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
    public static final Parcelable.Creator<SignalStrength> CREATOR = new Parcelable.Creator() {
        public SignalStrength createFromParcel(Parcel in) {
            return new SignalStrength(in);
        }

        public SignalStrength[] newArray(int size) {
            return new SignalStrength[size];
        }
    };

    /**
     * Validate the individual signal strength fields as per the range
     * specified in ril.h
     * Set to invalid any field that is not in the valid range
     * Cdma, evdo, lte rsrp & rsrq values are sign converted
     * when received from ril interface
     *
     * @return
     *      Valid values for all signalstrength fields
     * @hide
     */
    public void validateInput() {
        if (DBG) log("Signal before validate=" + this);
        // TS 27.007 8.5
        mGsmSignalStrength = mGsmSignalStrength >= 0 ? mGsmSignalStrength : 99;
        // BER no change;

        mCdmaDbm = mCdmaDbm > 0 ? -mCdmaDbm : -120;
        mCdmaEcio = (mCdmaEcio > 0) ? -mCdmaEcio : -160;

        mEvdoDbm = (mEvdoDbm > 0) ? -mEvdoDbm : -120;
        mEvdoEcio = (mEvdoEcio >= 0) ? -mEvdoEcio : SignalStrength.INVALID;
        mEvdoSnr = ((mEvdoSnr >= 0) && (mEvdoSnr <= 8)) ? mEvdoSnr : -1;

        // TS 36.214 Physical Layer Section 5.1.3, TS 36.331 RRC
        mLteSignalStrength = (mLteSignalStrength >= 0) ? mLteSignalStrength : 99;
        mLteRsrp = ((mLteRsrp >= 44) && (mLteRsrp <= 140)) ? -mLteRsrp : SignalStrength.INVALID;
        mLteRsrq = ((mLteRsrq >= 3) && (mLteRsrq <= 20)) ? -mLteRsrq : SignalStrength.INVALID;
        mLteRssnr = ((mLteRssnr >= -200) && (mLteRssnr <= 300)) ? mLteRssnr
                : SignalStrength.INVALID;
        //Cqi no change
        mUmtsRscp = ((mUmtsRscp >= 25) && (mUmtsRscp <= 120)) ? -mUmtsRscp : SignalStrength.INVALID;
        mUmtsEcno = (mUmtsEcno != SignalStrength.INVALID && mUmtsEcno >= 0) ?
                -mUmtsEcno : SignalStrength.INVALID;
        if (DBG) log("Signal after validate=" + this);
    }

    /**
     * @param true - Gsm, Lte phones
     *        false - Cdma phones
     *
     * Used by voice phone to set the isGsm
     *        flag
     * @hide
     */
    public void setGsm(boolean gsmFlag) {
        isGsm = gsmFlag;
    }

    /**
     * @param true - Cdma, Lte phones
     *        false - Gsm phones
     *
     * Used by Cdma, Lte phones to set the maybeLteBasedOnCdma
     *        flag
     * @hide
     */
    public void setMaybeLteBasedOnCdma(boolean lteBasedOnCdmaFlag) {
        maybeLteBasedOnCdma = lteBasedOnCdmaFlag;
    }

    /**
     * Get the GSM Signal Strength, valid values are (0-31, 99) as defined in TS
     * 27.007 8.5
     */
    public int getGsmSignalStrength() {
        return this.mGsmSignalStrength;
    }

    /**
     * Get the GSM bit error rate (0-7, 99) as defined in TS 27.007 8.5
     */
    public int getGsmBitErrorRate() {
        return this.mGsmBitErrorRate;
    }

    /**
     * The Received Signal Code Power in dBm multipled by -1.
     * Range : 25 to 120
     * INT_MAX: 0x7FFFFFFF denotes invalid value.
     * Reference: 3GPP TS 25.123, section 9.1.1.1
     * @hide
     */
    public int getUmtsRscp() {
        return this.mUmtsRscp;
    }

    /**
     * Received energy per chip divided by the power density in the band.
     * Ec/No (dB) = RSCP(db) - RSSI (db)
     * @hide
     */
    public int getUmtsEcno() {
        return this.mUmtsEcno;
    }

    /**
     * Get the CDMA RSSI value in dBm
     */
    public int getCdmaDbm() {
        return this.mCdmaDbm;
    }

    /**
     * Get the CDMA Ec/Io value in dB*10
     */
    public int getCdmaEcio() {
        return this.mCdmaEcio;
    }

    /**
     * Get the EVDO RSSI value in dBm
     */
    public int getEvdoDbm() {
        return this.mEvdoDbm;
    }

    /**
     * Get the EVDO Ec/Io value in dB*10
     */
    public int getEvdoEcio() {
        return this.mEvdoEcio;
    }

    /**
     * Get the signal to noise ratio. Valid values are 0-8. 8 is the highest.
     */
    public int getEvdoSnr() {
        return this.mEvdoSnr;
    }

    /** @hide */
    public int getLteSignalStrenght() {
        return this.mLteSignalStrength;
    }

    /** @hide */
    public int getLteRsrp() {
        return this.mLteRsrp;
    }

    /** @hide */
    public int getLteRsrq() {
        return this.mLteRsrq;
    }

    /** @hide */
    public int getLteRssnr() {
        return this.mLteRssnr;
    }

    /** @hide */
    public int getLteCqi() {
        return this.mLteCqi;
    }

    /**
     * Set GSM signal level as an int from 0..maxLevel
     *
     * @hide
     */
    public void setGsmLevel( int gsmLevel) {
        mGsmLevel = gsmLevel;
    }

    /**
     * Set UMTS signal level as an int from 0..maxLevel
     *
     * @hide
     */
    public void setUmtsLevel( int umtsLevel) {
        mUmtsLevel = umtsLevel;
    }

    /**
     * Set CDMA signal level as an int from 0..maxLevel
     *
     * @hide
     */
    public void setCdmaLevel( int cdmaLevel) {
        mCdmaLevel = cdmaLevel;
    }

    /**
     * Set Evdo signal level as an int from 0..maxLevel
     *
     * @hide
     */
    public void setEvdoLevel( int evdoLevel) {
        mEvdoLevel = evdoLevel;
    }

    /**
     * Set Lte signal level as an int from 0..maxLevel
     *
     * @hide
     */
    public void setLteLevel( int lteLevel) {
        mLteLevel = lteLevel;
    }

    /**
     * Set signal MaxLevel as an int from 0..MaxLevel
     *
     * @hide
     */
    public void setMaxLevel( int maxLevel) {
        mMaxLevel = maxLevel;
    }

    /**
     * Get signal level as an int from 0..maxLevel
     *
     * @hide
     */
    public int getLevel() {
        int level = getLteLevel();

        if (level == SIGNAL_STRENGTH_NONE_OR_UNKNOWN) {
            if (isGsm && !maybeLteBasedOnCdma) {
                level = getUmtsLevel();
                if (level == SIGNAL_STRENGTH_NONE_OR_UNKNOWN) {
                    level = getGsmLevel();
                }
            } else { // cdma or lte/cdma
                level = getEvdoLevel();
                if (level == SIGNAL_STRENGTH_NONE_OR_UNKNOWN) {
                    level = getCdmaLevel();
                }
            }
        }
        if (DBG) log("getLevel=" + level);
        return level;
    }

    /**
     * Get Max Levels for current radio type
     *
     * @hide
     */
    public int getMaxLevel() {
        return mMaxLevel;
    }

    /**
     * Get the signal level as an asu value between 0..31, 99 is unknown
     *
     * @hide
     */
    public int getAsuLevel() {
        int asuLevel = 0;
        if (getLteLevel() == SIGNAL_STRENGTH_NONE_OR_UNKNOWN) {
            if (isGsm && !maybeLteBasedOnCdma) {
                asuLevel = getGsmAsuLevel();
            } else {
                int cdmaAsuLevel = getCdmaAsuLevel();
                int evdoAsuLevel = getEvdoAsuLevel();
                if (evdoAsuLevel == 0) {
                    /* We don't know evdo use, cdma */
                    asuLevel = cdmaAsuLevel;
                } else if (cdmaAsuLevel == 0) {
                    /* We don't know cdma use, evdo */
                    asuLevel = evdoAsuLevel;
                } else {
                    /* We know both, use the lowest level */
                    asuLevel = cdmaAsuLevel < evdoAsuLevel ? cdmaAsuLevel : evdoAsuLevel;
                }
            }
        } else {
            asuLevel = getLteAsuLevel();
        }
        if (DBG) log("getAsuLevel=" + asuLevel);
        return asuLevel;
    }

    /**
     * Get the signal strength as dBm
     *
     * @hide
     */
    public int getDbm() {
        int dBm = INVALID;

        if (getLteLevel() == SIGNAL_STRENGTH_NONE_OR_UNKNOWN) {
            if(isGsm() & !maybeLteBasedOnCdma) {
                dBm = getGsmDbm();
            } else {
                int cdmaDbm = getCdmaDbm();
                int evdoDbm = getEvdoDbm();

                return (evdoDbm == -120) ? cdmaDbm : ((cdmaDbm == -120) ? evdoDbm
                        : (cdmaDbm < evdoDbm ? cdmaDbm : evdoDbm));
            }
        } else {
            dBm = getLteDbm();
        }
        if (DBG) log("getDbm=" + dBm);
        return dBm;
    }

    /**
     * Get Gsm signal strength as dBm
     *
     * @hide
     */
    public int getGsmDbm() {
        int dBm;

        int gsmSignalStrength = getGsmSignalStrength();
        int asu = (gsmSignalStrength == 99 ? -1 : gsmSignalStrength);
        if (asu != -1) {
            dBm = -113 + (2 * asu);
        } else {
            dBm = -1;
        }
        if (DBG) log("getGsmDbm=" + dBm);
        return dBm;
    }

    /**
     * Get gsm as level 0..maxLevel
     *
     * @hide
     */
    public int getGsmLevel() {
        return mGsmLevel;
    }

    /**
     * Get umts as level 0..maxLevel
     *
     * @hide
     */
    public int getUmtsLevel() {
        return mUmtsLevel;
    }

    /**
     * Get the gsm signal level as an asu value between 0..31, 99 is unknown
     *
     * @hide
     */
    public int getGsmAsuLevel() {
        // ASU ranges from 0 to 31 - TS 27.007 Sec 8.5
        // asu = 0 (-113dB or less) is very weak
        // signal, its better to show 0 bars to the user in such cases.
        // asu = 99 is a special case, where the signal strength is unknown.
        int level = getGsmSignalStrength();
        if (DBG) log("getGsmAsuLevel=" + level);
        return level;
    }

    /**
     * Get cdma as level 0..maxLevel
     *
     * @hide
     */
    public int getCdmaLevel() {
        return mCdmaLevel;
    }

    /**
     * Get the cdma signal level as an asu value between 0..31, 99 is unknown
     *
     * @hide
     */
    public int getCdmaAsuLevel() {
        final int cdmaDbm = getCdmaDbm();
        final int cdmaEcio = getCdmaEcio();
        int cdmaAsuLevel;
        int ecioAsuLevel;

        if (cdmaDbm >= -75) cdmaAsuLevel = 16;
        else if (cdmaDbm >= -82) cdmaAsuLevel = 8;
        else if (cdmaDbm >= -90) cdmaAsuLevel = 4;
        else if (cdmaDbm >= -95) cdmaAsuLevel = 2;
        else if (cdmaDbm >= -100) cdmaAsuLevel = 1;
        else cdmaAsuLevel = 99;

        // Ec/Io are in dB*10
        if (cdmaEcio >= -90) ecioAsuLevel = 16;
        else if (cdmaEcio >= -100) ecioAsuLevel = 8;
        else if (cdmaEcio >= -115) ecioAsuLevel = 4;
        else if (cdmaEcio >= -130) ecioAsuLevel = 2;
        else if (cdmaEcio >= -150) ecioAsuLevel = 1;
        else ecioAsuLevel = 99;

        int level = (cdmaAsuLevel < ecioAsuLevel) ? cdmaAsuLevel : ecioAsuLevel;
        if (DBG) log("getCdmaAsuLevel=" + level);
        return level;
    }

    /**
     * Get Evdo as level 0..maxLevel
     *
     * @hide
     */
    public int getEvdoLevel() {
        return mEvdoLevel;
    }

    /**
     * Get the evdo signal level as an asu value between 0..31, 99 is unknown
     *
     * @hide
     */
    public int getEvdoAsuLevel() {
        int evdoDbm = getEvdoDbm();
        int evdoSnr = getEvdoSnr();
        int levelEvdoDbm;
        int levelEvdoSnr;

        if (evdoDbm >= -65) levelEvdoDbm = 16;
        else if (evdoDbm >= -75) levelEvdoDbm = 8;
        else if (evdoDbm >= -85) levelEvdoDbm = 4;
        else if (evdoDbm >= -95) levelEvdoDbm = 2;
        else if (evdoDbm >= -105) levelEvdoDbm = 1;
        else levelEvdoDbm = 99;

        if (evdoSnr >= 7) levelEvdoSnr = 16;
        else if (evdoSnr >= 6) levelEvdoSnr = 8;
        else if (evdoSnr >= 5) levelEvdoSnr = 4;
        else if (evdoSnr >= 3) levelEvdoSnr = 2;
        else if (evdoSnr >= 1) levelEvdoSnr = 1;
        else levelEvdoSnr = 99;

        int level = (levelEvdoDbm < levelEvdoSnr) ? levelEvdoDbm : levelEvdoSnr;
        if (DBG) log("getEvdoAsuLevel=" + level);
        return level;
    }

    /**
     * Get LTE as dBm
     *
     * @hide
     */
    public int getLteDbm() {
        return mLteRsrp;
    }

    /**
     * Get LTE as level 0..maxLevel
     *
     * @hide
     */
    public int getLteLevel() {
        return mLteLevel;
    }

    /**
     * Get the LTE signal level as an asu value between 0..97, 99 is unknown
     * Asu is calculated based on 3GPP RSRP. Refer to 3GPP 27.007 (Ver 10.3.0) Sec 8.69
     *
     * @hide
     */
    public int getLteAsuLevel() {
        int lteAsuLevel = 99;
        int lteDbm = getLteDbm();
        /*
         * 3GPP 27.007 (Ver 10.3.0) Sec 8.69
         * 0   -140 dBm or less
         * 1   -139 dBm
         * 2...96  -138... -44 dBm
         * 97  -43 dBm or greater
         * 255 not known or not detectable
         */
        /*
         * validateInput will always give a valid range between -140 t0 -44 as
         * per ril.h. so RSRP >= -43 & <-140 will fall under asu level 255
         * and not 97 or 0
         */
        if (lteDbm == SignalStrength.INVALID) lteAsuLevel = 255;
        else lteAsuLevel = lteDbm + 140;
        if (DBG) log("Lte Asu level: "+lteAsuLevel);
        return lteAsuLevel;
    }

    /**
     * @return true if this is for GSM
     */
    public boolean isGsm() {
        return this.isGsm;
    }

    /**
     * @return true if this is cdma/lte phone
     * @hide
     */
    public boolean maybeLteBasedOnCdma() {
        return this.maybeLteBasedOnCdma;
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
                + (mUmtsRscp * primeNum) + (mUmtsEcno * primeNum)
                + (isGsm ? 1 : 0));
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

        return (mGsmSignalStrength == s.mGsmSignalStrength
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
                && mUmtsRscp == s.mUmtsRscp
                && mUmtsEcno == s.mUmtsEcno
                && isGsm == s.isGsm
            );
    }

    /**
     * @return string representation.
     */
    @Override
    public String toString() {
        return ("SignalStrength:"
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
                + " " + mUmtsRscp
                + " " + mUmtsEcno
                + " " + (isGsm ? "gsm|lte" : "cdma")
                + " " + mGsmLevel
                + " " + mUmtsLevel
                + " " + mCdmaLevel
                + " " + mEvdoLevel
                + " " + mLteLevel
                + " " + mMaxLevel
            );
    }

    /**
     * Set SignalStrength based on intent notifier map
     *
     * @param m intent notifier map
     * @hide
     */
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
        mUmtsRscp = m.getInt("UmtsRscp");
        mUmtsEcno = m.getInt("UmtsEcno");
        isGsm = m.getBoolean("isGsm");
        mGsmLevel = m.getInt("GsmLevel");
        mUmtsLevel = m.getInt("UmtsLevel");
        mCdmaLevel = m.getInt("CdmaLevel");
        mEvdoLevel = m.getInt("EvdoLevel");
        mLteLevel = m.getInt("LteLevel");
        mMaxLevel = m.getInt("MaxLevel");
    }

    /**
     * Set intent notifier Bundle based on SignalStrength
     *
     * @param m intent notifier Bundle
     * @hide
     */
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
        m.putInt("UmtsRscp", mUmtsRscp);
        m.putInt("UmtsEcno", mUmtsEcno);
        m.putBoolean("isGsm", Boolean.valueOf(isGsm));
        m.putInt("GsmLevel", mGsmLevel);
        m.putInt("UmtsLevel", mUmtsLevel);
        m.putInt("CdmaLevel", mCdmaLevel);
        m.putInt("EvdoLevel", mEvdoLevel);
        m.putInt("LteLevel", mLteLevel);
        m.putInt("MaxLevel", mMaxLevel);
    }

    /**
     * log
     */
    private static void log(String s) {
        Rlog.w(LOG_TAG, s);
    }
}
