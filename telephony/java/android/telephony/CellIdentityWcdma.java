/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.telephony.Rlog;

import java.util.Objects;

/**
 * CellIdentity to represent a unique UMTS cell
 */
public final class CellIdentityWcdma implements Parcelable {

    private static final String LOG_TAG = "CellIdentityWcdma";
    private static final boolean DBG = false;

    // 3-digit Mobile Country Code, 0..999
    private final int mMcc;
    // 2 or 3-digit Mobile Network Code, 0..999
    private final int mMnc;
    // 16-bit Location Area Code, 0..65535
    private final int mLac;
    // 28-bit UMTS Cell Identity described in TS 25.331, 0..268435455
    private final int mCid;
    // 9-bit UMTS Primary Scrambling Code described in TS 25.331, 0..511
    private final int mPsc;
    // 16-bit UMTS Absolute RF Channel Number
    private final int mUarfcn;
    // Mobile Country Code in string format
    private final String mMccStr;
    // Mobile Network Code in string format
    private final String mMncStr;
    // long alpha ONS or EONS
    private final String mAlphaLong;
    // short alpha ONS or EONS
    private final String mAlphaShort;

    /**
     * @hide
     */
    public CellIdentityWcdma() {
        mMcc = Integer.MAX_VALUE;
        mMnc = Integer.MAX_VALUE;
        mLac = Integer.MAX_VALUE;
        mCid = Integer.MAX_VALUE;
        mPsc = Integer.MAX_VALUE;
        mUarfcn = Integer.MAX_VALUE;
        mMccStr = "";
        mMncStr = "";
        mAlphaLong = "";
        mAlphaShort = "";
    }
    /**
     * public constructor
     * @param mcc 3-digit Mobile Country Code, 0..999
     * @param mnc 2 or 3-digit Mobile Network Code, 0..999
     * @param lac 16-bit Location Area Code, 0..65535
     * @param cid 28-bit UMTS Cell Identity
     * @param psc 9-bit UMTS Primary Scrambling Code
     *
     * @hide
     */
    public CellIdentityWcdma (int mcc, int mnc, int lac, int cid, int psc) {
        this(mcc, mnc, lac, cid, psc, Integer.MAX_VALUE, "", "", "", "");
    }

    /**
     * public constructor
     * @param mcc 3-digit Mobile Country Code, 0..999
     * @param mnc 2 or 3-digit Mobile Network Code, 0..999
     * @param lac 16-bit Location Area Code, 0..65535
     * @param cid 28-bit UMTS Cell Identity
     * @param psc 9-bit UMTS Primary Scrambling Code
     * @param uarfcn 16-bit UMTS Absolute RF Channel Number
     *
     * @hide
     */
    public CellIdentityWcdma (int mcc, int mnc, int lac, int cid, int psc, int uarfcn) {
        this(mcc, mnc, lac, cid, psc, uarfcn, "", "", "", "");
    }

    /**
     * public constructor
     * @param mcc 3-digit Mobile Country Code, 0..999
     * @param mnc 2 or 3-digit Mobile Network Code, 0..999
     * @param lac 16-bit Location Area Code, 0..65535
     * @param cid 28-bit UMTS Cell Identity
     * @param psc 9-bit UMTS Primary Scrambling Code
     * @param uarfcn 16-bit UMTS Absolute RF Channel Number
     * @param mccStr Mobile Country Code in string format
     * @param mncStr Mobile Network Code in string format
     * @param alphal long alpha ONS or EONS
     * @param alphas short alpha ONS or EONS
     *
     * @hide
     */
    public CellIdentityWcdma (int mcc, int mnc, int lac, int cid, int psc, int uarfcn,
                              String mccStr, String mncStr, String alphal, String alphas) {
        mMcc = mcc;
        mMnc = mnc;
        mLac = lac;
        mCid = cid;
        mPsc = psc;
        mUarfcn = uarfcn;
        mMccStr = mccStr;
        mMncStr = mncStr;
        mAlphaLong = alphal;
        mAlphaShort = alphas;
    }

    private CellIdentityWcdma(CellIdentityWcdma cid) {
        this(cid.mMcc, cid.mMnc, cid.mLac, cid.mCid, cid.mPsc, cid.mUarfcn, cid.mMccStr,
                cid.mMncStr, cid.mAlphaLong, cid.mAlphaShort);
    }

    CellIdentityWcdma copy() {
        return new CellIdentityWcdma(this);
    }

    /**
     * @return 3-digit Mobile Country Code, 0..999, Integer.MAX_VALUE if unknown
     * @deprecated Use {@link #getMcc} instead.
     */
    @Deprecated
    public int getMcc() {
        return mMcc;
    }

    /**
     * @return 2 or 3-digit Mobile Network Code, 0..999, Integer.MAX_VALUE if unknown
     * @deprecated Use {@link #getMnc} instead.
     */
    @Deprecated
    public int getMnc() {
        return mMnc;
    }

    /**
     * @return 16-bit Location Area Code, 0..65535, Integer.MAX_VALUE if unknown
     */
    public int getLac() {
        return mLac;
    }

    /**
     * @return CID
     * 28-bit UMTS Cell Identity described in TS 25.331, 0..268435455, Integer.MAX_VALUE if unknown
     */
    public int getCid() {
        return mCid;
    }

    /**
     * @return 9-bit UMTS Primary Scrambling Code described in TS 25.331, 0..511, Integer.MAX_VALUE
     * if unknown
     */
    public int getPsc() {
        return mPsc;
    }

    /**
     * @return Mobile Country Code in string version, empty string if unknown
     */
    public String getMcc() {
        return mMccStr;
    }

    /**
     * @return Mobile Network Code in string version, empty string if unknown
     */
    public String getMnc() {
        return mMncStr;
    }

    /**
     * @return a 5 or 6 character string, empty string if unknown
     */
    public String getSimOperatorNumeric() {
        return mMccStr + mMncStr;
    }

    /**
     * @return Alpha long ONS or EONS, empty string if unknown
     */
    public String getAlphaLong() { return mAlphaLong; }

    /**
     * @return Alpha short ONS or EONS, empty string if unknown
     */
    public String getAlphaShort() { return mAlphaShort; }

    @Override
    public int hashCode() {
        return Objects.hash(mMcc, mMnc, mLac, mCid, mPsc);
    }

    /**
     * @return 16-bit UMTS Absolute RF Channel Number, Integer.MAX_VALUE if unknown
     */
    public int getUarfcn() {
        return mUarfcn;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof CellIdentityWcdma)) {
            return false;
        }

        CellIdentityWcdma o = (CellIdentityWcdma) other;
        return mMcc == o.mMcc &&
                mMnc == o.mMnc &&
                mLac == o.mLac &&
                mCid == o.mCid &&
                mPsc == o.mPsc &&
                mUarfcn == o.mUarfcn &&
                mMccStr.equals(o.mMccStr) &&
                mMncStr.equals(o.mMncStr) &&
                mAlphaLong.equals(o.mAlphaLong) &&
                mAlphaShort.equals(o.mAlphaShort);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CellIdentityWcdma:{");
        sb.append(" mMcc=").append(mMcc);
        sb.append(" mMnc=").append(mMnc);
        sb.append(" mLac=").append(mLac);
        sb.append(" mCid=").append(mCid);
        sb.append(" mPsc=").append(mPsc);
        sb.append(" mUarfcn=").append(mUarfcn);
        sb.append(" mMccStr=").append(mMccStr);
        sb.append(" mMncStr=").append(mMncStr);
        sb.append(" mAlphaLong=").append(mAlphaLong);
        sb.append(" mAlphaShort=").append(mAlphaShort);
        sb.append("}");

        return sb.toString();
    }

    /** Implement the Parcelable interface */
    @Override
    public int describeContents() {
        return 0;
    }

    /** Implement the Parcelable interface */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (DBG) log("writeToParcel(Parcel, int): " + toString());
        dest.writeInt(mMcc);
        dest.writeInt(mMnc);
        dest.writeInt(mLac);
        dest.writeInt(mCid);
        dest.writeInt(mPsc);
        dest.writeInt(mUarfcn);
        dest.writeString(mMccStr);
        dest.writeString(mMncStr);
        dest.writeString(mAlphaLong);
        dest.writeString(mAlphaShort);
    }

    /** Construct from Parcel, type has already been processed */
    private CellIdentityWcdma(Parcel in) {
        mMcc = in.readInt();
        mMnc = in.readInt();
        mLac = in.readInt();
        mCid = in.readInt();
        mPsc = in.readInt();
        mUarfcn = in.readInt();
        mMccStr = in.readString();
        mMncStr = in.readString();
        mAlphaLong = in.readString();
        mAlphaShort = in.readString();
        if (DBG) log("CellIdentityWcdma(Parcel): " + toString());
    }

    /** Implement the Parcelable interface */
    @SuppressWarnings("hiding")
    public static final Creator<CellIdentityWcdma> CREATOR =
            new Creator<CellIdentityWcdma>() {
                @Override
                public CellIdentityWcdma createFromParcel(Parcel in) {
                    return new CellIdentityWcdma(in);
                }

                @Override
                public CellIdentityWcdma[] newArray(int size) {
                    return new CellIdentityWcdma[size];
                }
            };

    /**
     * log
     */
    private static void log(String s) {
        Rlog.w(LOG_TAG, s);
    }
}