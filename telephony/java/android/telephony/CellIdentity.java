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

/**
 * CellIdentity represents the identity of a unique cell. This is the base class for
 * CellIdentityXxx which represents cell identity for specific network access technology.
 */
public abstract class CellIdentity implements Parcelable {
    /**
     * Cell identity type
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "TYPE_", value = {TYPE_GSM, TYPE_CDMA, TYPE_LTE, TYPE_WCDMA, TYPE_TDSCDMA})
    public @interface Type {}

    /** Unknown cell type */
    public static final int TYPE_UNKNOWN        = 0;
    /** GSM cell identity type */
    public static final int TYPE_GSM            = 1;
    /** CDMA cell identity type */
    public static final int TYPE_CDMA           = 2;
    /** LTE cell identity type */
    public static final int TYPE_LTE            = 3;
    /** WCDMA cell identity type */
    public static final int TYPE_WCDMA          = 4;
    /** TDS-CDMA cell identity type */
    public static final int TYPE_TDSCDMA        = 5;

    private final int mType;

    protected CellIdentity(int type) {
        mType = type;
    }

    /** Implement the Parcelable interface */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * @return The type of the cell identity
     */
    public @Type int getType() { return mType; }

    /** Implement the Parcelable interface */
    @Override
    public abstract void writeToParcel(Parcel dest, int flags);

    /**
     * Used by child classes for parceling.
     *
     * @hide
     */
    protected void writeToParcel(Parcel dest, int flags, int type) {
        dest.writeInt(type);
    }

    /**
     * Construct from Parcel
     * @hide
     */
    protected CellIdentity(Parcel source) {
        mType = source.readInt();
    }

    /** Implement the Parcelable interface */
    public static final Creator<CellIdentity> CREATOR =
            new Creator<CellIdentity>() {
                @Override
                public CellIdentity createFromParcel(Parcel in) {
                    int type = in.readInt();
                    switch (type) {
                        case TYPE_GSM: return CellIdentityGsm.createFromParcelBody(in);
                        case TYPE_WCDMA: return CellIdentityWcdma.createFromParcelBody(in);
                        case TYPE_CDMA: return CellIdentityCdma.createFromParcelBody(in);
                        case TYPE_LTE: return CellIdentityLte.createFromParcelBody(in);
                        case TYPE_TDSCDMA: return CellIdentityTdscdma.createFromParcelBody(in);
                        default: throw new IllegalArgumentException("Bad Cell identity Parcel");
                    }
                }

                @Override
                public CellIdentity[] newArray(int size) {
                    return new CellIdentity[size];
                }
            };
}