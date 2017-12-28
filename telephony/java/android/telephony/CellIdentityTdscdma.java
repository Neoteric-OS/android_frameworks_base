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

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.util.Objects;

/**
 * CellIdentity is to represent a unique TD-SCDMA cell
 */
public final class CellIdentityTdscdma extends CellIdentity {

    private static final String LOG_TAG = "CellIdentityTdscdma";
    private static final boolean DBG = false;

    // 3-digit Mobile Country Code, 0..999, INT_MAX if unknown.
    private final String mMcc;
    // 2 or 3-digit Mobile Network Code, 0..999, INT_MAX if unknown.
    private final String mMnc;
    // 16-bit Location Area Code, 0..65535, INT_MAX if unknown.
    private final int mLac;
    // 28-bit UMTS Cell Identity described in TS 25.331, 0..268435455, INT_MAX if unknown.
    private final int mCid;
    // 8-bit Cell Parameters ID described in TS 25.331, 0..127, INT_MAX if unknown.
    private final int mCpid;

    /**
     * @hide
     */
    public CellIdentityTdscdma() {
        super(TYPE_UNKNOWN);
        mMcc = null;
        mMnc = null;
        mLac = Integer.MAX_VALUE;
        mCid = Integer.MAX_VALUE;
        mCpid = Integer.MAX_VALUE;
    }

    /**
     * @param mcc 3-digit Mobile Country Code, 0..999
     * @param mnc 2 or 3-digit Mobile Network Code, 0..999
     * @param lac 16-bit Location Area Code, 0..65535, INT_MAX if unknown
     * @param cid 28-bit UMTS Cell Identity described in TS 25.331, 0..268435455, INT_MAX if unknown
     * @param cpid 8-bit Cell Parameters ID described in TS 25.331, 0..127, INT_MAX if unknown
     *
     * @hide
     */
    public CellIdentityTdscdma (int mcc, int mnc, int lac, int cid, int cpid) {
        this(String.valueOf(mcc), String.valueOf(mnc), lac, cid, cpid);
    }

    /**
     * @param mcc 3-digit Mobile Country Code in string format
     * @param mnc 2 or 3-digit Mobile Network Code in string format
     * @param lac 16-bit Location Area Code, 0..65535, INT_MAX if unknown
     * @param cid 28-bit UMTS Cell Identity described in TS 25.331, 0..268435455, INT_MAX if unknown
     * @param cpid 8-bit Cell Parameters ID described in TS 25.331, 0..127, INT_MAX if unknown
     *
     * @hide
     */
    public CellIdentityTdscdma (String mcc, String mnc, int lac, int cid, int cpid) {
        super(TYPE_TDSCDMA);
        // Only allow INT_MAX if unknown string mcc/mnc
        if (mcc == null || mcc.matches("^[0-9]{3}$")) {
            mMcc = mcc;
        } else if (mcc.isEmpty() || mcc.equals(String.valueOf(Integer.MAX_VALUE))) {
            // If the mccStr is empty or unknown, set it as null.
            mMcc = null;
        } else {
            // after the bug got fixed.
            mMcc = null;
            log("invalid MCC format: " + mcc);
        }

        if (mnc == null || mnc.matches("^[0-9]{2,3}$")) {
            mMnc = mnc;
        } else if (mnc.isEmpty() || mnc.equals(String.valueOf(Integer.MAX_VALUE))) {
            // If the mncStr is empty or unknown, set it as null.
            mMnc = null;
        } else {
            // after the bug got fixed.
            mMnc = null;
            log("invalid MNC format: " + mnc);
        }

        mLac = lac;
        mCid = cid;
        mCpid = cpid;
    }

    private CellIdentityTdscdma(CellIdentityTdscdma cid) {
        this(cid.mMcc, cid.mMnc, cid.mLac, cid.mCid, cid.mCpid);
    }

    CellIdentityTdscdma copy() {
        return new CellIdentityTdscdma(this);
    }

    /**
     * Get Mobile Country Code in string format
     * @return Mobile Country Code in string format, null if unknown
     */
    public String getMccStr() { return mMcc; }

    /**
     * Get Mobile Network Code in string format
     * @return Mobile Network Code in string format, null if unknown
     */
    public String getMncStr() {
        return mMnc;
    }

    /**
     * @return 16-bit Location Area Code, 0..65535, INT_MAX if unknown
     */
    public int getLac() {
        return mLac;
    }

    /**
     * @return 28-bit UMTS Cell Identity described in TS 25.331, 0..268435455, INT_MAX if unknown
     */
    public int getCid() {
        return mCid;
    }

    /**
     * @return 8-bit Cell Parameters ID described in TS 25.331, 0..127, INT_MAX if unknown
     */
    public int getCpid() {
        return mCpid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mMcc, mMnc, mLac, mCid, mCpid);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof CellIdentityTdscdma)) {
            return false;
        }

        CellIdentityTdscdma o = (CellIdentityTdscdma) other;
        return TextUtils.equals(mMcc, o.mMcc)
                && TextUtils.equals(mMnc, o.mMnc)
                && mLac == o.mLac
                && mCid == o.mCid
                && mCpid == o.mCpid;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CellIdentityTdscdma:{");
        sb.append(" mMcc="); sb.append(mMcc);
        sb.append(" mMnc="); sb.append(mMnc);
        sb.append(" mLac="); sb.append(mLac);
        sb.append(" mCid="); sb.append(mCid);
        sb.append(" mCpid="); sb.append(mCpid);
        sb.append("}");

        return sb.toString();
    }

    /** Implement the Parcelable interface */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (DBG) log("writeToParcel(Parcel, int): " + toString());
        super.writeToParcel(dest, flags, TYPE_TDSCDMA);
        dest.writeString(mMcc);
        dest.writeString(mMnc);
        dest.writeInt(mLac);
        dest.writeInt(mCid);
        dest.writeInt(mCpid);
    }

    /** Construct from Parcel, type has already been processed */
    private CellIdentityTdscdma(Parcel in) {
        this(in.readString(), in.readString(), in.readInt(), in.readInt(), in.readInt());

        if (DBG) log("CellIdentityTdscdma(Parcel): " + toString());
    }

    /** Implement the Parcelable interface */
    @SuppressWarnings("hiding")
    public static final Creator<CellIdentityTdscdma> CREATOR =
            new Creator<CellIdentityTdscdma>() {
                @Override
                public CellIdentityTdscdma createFromParcel(Parcel in) {
                    in.readInt();   // skip
                    return createFromParcelBody(in);
                }

                @Override
                public CellIdentityTdscdma[] newArray(int size) {
                    return new CellIdentityTdscdma[size];
                }
            };

    /** @hide */
    protected static CellIdentityTdscdma createFromParcelBody(Parcel in) {
        return new CellIdentityTdscdma(in);
    }

    private static void log(String s) {
        Rlog.w(LOG_TAG, s);
    }
}