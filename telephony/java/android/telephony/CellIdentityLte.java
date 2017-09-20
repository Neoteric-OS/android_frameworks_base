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

import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.Rlog;

import java.util.Objects;

/**
 * CellIdentity is to represent a unique LTE cell
 */
public final class CellIdentityLte implements Parcelable {

    private static final String LOG_TAG = "CellIdentityLte";
    private static final boolean DBG = false;

    // 3-digit Mobile Country Code, 0..999
    private final int mMcc;
    // 2 or 3-digit Mobile Network Code, 0..999
    private final int mMnc;
    // 28-bit cell identity
    private final int mCi;
    // physical cell id 0..503
    private final int mPci;
    // 16-bit tracking area code
    private final int mTac;
    // 18-bit Absolute RF Channel Number
    private final int mEarfcn;
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
    public CellIdentityLte() {
        mMcc = Integer.MAX_VALUE;
        mMnc = Integer.MAX_VALUE;
        mCi = Integer.MAX_VALUE;
        mPci = Integer.MAX_VALUE;
        mTac = Integer.MAX_VALUE;
        mEarfcn = Integer.MAX_VALUE;
        mMccStr = "";
        mMncStr = "";
        mAlphaLong = "";
        mAlphaShort = "";
    }

    /**
     *
     * @param mcc 3-digit Mobile Country Code, 0..999
     * @param mnc 2 or 3-digit Mobile Network Code, 0..999
     * @param ci 28-bit Cell Identity
     * @param pci Physical Cell Id 0..503
     * @param tac 16-bit Tracking Area Code
     *
     * @hide
     */
    public CellIdentityLte (int mcc, int mnc, int ci, int pci, int tac) {
        this(mcc, mnc, ci, pci, tac, Integer.MAX_VALUE, "", "", "", "");
    }

    /**
     *
     * @param mcc 3-digit Mobile Country Code, 0..999
     * @param mnc 2 or 3-digit Mobile Network Code, 0..999
     * @param ci 28-bit Cell Identity
     * @param pci Physical Cell Id 0..503
     * @param tac 16-bit Tracking Area Code
     * @param earfcn 18-bit LTE Absolute RF Channel Number
     *
     * @hide
     */
    public CellIdentityLte (int mcc, int mnc, int ci, int pci, int tac, int earfcn) {
        this(mcc, mnc, ci, pci, tac, earfcn, "", "", "", "");
    }

    /**
     *
     * @param mcc 3-digit Mobile Country Code, 0..999
     * @param mnc 2 or 3-digit Mobile Network Code, 0..999
     * @param ci 28-bit Cell Identity
     * @param pci Physical Cell Id 0..503
     * @param tac 16-bit Tracking Area Code
     * @param earfcn 18-bit LTE Absolute RF Channel Number
     * @param mccStr Mobile Country Code in string format
     * @param mncStr Mobile Network Code in string format
     * @param alphal long alpha ONS or EONS
     * @param alphas short alpha ONS or EONS
     *
     * @hide
     */
    public CellIdentityLte (int mcc, int mnc, int ci, int pci, int tac, int earfcn, String mccStr,
                            String mncStr, String alphal, String alphas) {
        mMcc = mcc;
        mMnc = mnc;
        mCi = ci;
        mPci = pci;
        mTac = tac;
        mEarfcn = earfcn;
        mMccStr = mccStr;
        mMncStr = mncStr;
        mAlphaLong = alphal;
        mAlphaShort = alphas;
    }

    private CellIdentityLte(CellIdentityLte cid) {
        mMcc = cid.mMcc;
        mMnc = cid.mMnc;
        mCi = cid.mCi;
        mPci = cid.mPci;
        mTac = cid.mTac;
        mEarfcn = cid.mEarfcn;
        mMccStr = cid.mMccStr;
        mMncStr = cid.mMncStr;
        mAlphaLong = cid.mAlphaLong;
        mAlphaShort = cid.mAlphaShort;
    }

    CellIdentityLte copy() {
        return new CellIdentityLte(this);
    }

    /**
     * @return 3-digit Mobile Country Code, 0..999, Integer.MAX_VALUE if unknown
     * @deprecated
     */
    @Deprecated
    public int getMcc() {
        return mMcc;
    }

    /**
     * @return 2 or 3-digit Mobile Network Code, 0..999, Integer.MAX_VALUE if unknown
     * @deprecated
     */
    @Deprecated
    public int getMnc() {
        return mMnc;
    }

    /**
     * @return 28-bit Cell Identity, Integer.MAX_VALUE if unknown
     */
    public int getCi() {
        return mCi;
    }

    /**
     * @return Physical Cell Id 0..503, Integer.MAX_VALUE if unknown
     */
    public int getPci() {
        return mPci;
    }

    /**
     * @return 16-bit Tracking Area Code, Integer.MAX_VALUE if unknown
     */
    public int getTac() {
        return mTac;
    }

    /**
     * @return 18-bit Absolute RF Channel Number, Integer.MAX_VALUE if unknown
     */
    public int getEarfcn() {
        return mEarfcn;
    }

    /**
     * @return Mobile Country Code in string format, empty string if unknown
     */
    public String getMcc() {
        return mMccStr;
    }

    /**
     * @return Mobile Network Code in string format, empty string if unknown
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
        return Objects.hash(mMcc, mMnc, mCi, mPci, mTac);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof CellIdentityLte)) {
            return false;
        }

        CellIdentityLte o = (CellIdentityLte) other;
        return mMcc == o.mMcc &&
                mMnc == o.mMnc &&
                mCi == o.mCi &&
                mPci == o.mPci &&
                mTac == o.mTac &&
                mEarfcn == o.mEarfcn &&
                mMccStr.equals(o.mMccStr) &&
                mMncStr.equals(o.mMncStr) &&
                mAlphaLong.equals(o.mAlphaLong) &&
                mAlphaShort.equals(o.mAlphaShort);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CellIdentityLte:{");
        sb.append(" mMcc="); sb.append(mMcc);
        sb.append(" mMnc="); sb.append(mMnc);
        sb.append(" mCi="); sb.append(mCi);
        sb.append(" mPci="); sb.append(mPci);
        sb.append(" mTac="); sb.append(mTac);
        sb.append(" mEarfcn="); sb.append(mEarfcn);
        sb.append(" mMccStr="); sb.append(mMccStr);
        sb.append(" mMncStr="); sb.append(mMncStr);
        sb.append(" mAlphaLong="); sb.append(mAlphaLong);
        sb.append(" mAlphaShort="); sb.append(mAlphaShort);
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
        dest.writeInt(mCi);
        dest.writeInt(mPci);
        dest.writeInt(mTac);
        dest.writeInt(mEarfcn);
        dest.writeString(mMccStr);
        dest.writeString(mMncStr);
        dest.writeString(mAlphaLong);
        dest.writeString(mAlphaShort);
    }

    /** Construct from Parcel, type has already been processed */
    private CellIdentityLte(Parcel in) {
        mMcc = in.readInt();
        mMnc = in.readInt();
        mCi = in.readInt();
        mPci = in.readInt();
        mTac = in.readInt();
        mEarfcn = in.readInt();
        mMccStr = in.readString();
        mMncStr = in.readString();
        mAlphaLong = in.readString();
        mAlphaShort = in.readString();
        if (DBG) log("CellIdentityLte(Parcel): " + toString());
    }

    /** Implement the Parcelable interface */
    @SuppressWarnings("hiding")
    public static final Creator<CellIdentityLte> CREATOR =
            new Creator<CellIdentityLte>() {
                @Override
                public CellIdentityLte createFromParcel(Parcel in) {
                    return new CellIdentityLte(in);
                }

                @Override
                public CellIdentityLte[] newArray(int size) {
                    return new CellIdentityLte[size];
                }
            };

    /**
     * log
     */
    private static void log(String s) {
        Rlog.w(LOG_TAG, s);
    }
}