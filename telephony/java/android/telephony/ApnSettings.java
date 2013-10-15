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

/**
 * Immutable ApnSettings information from a point in time.
 */
public class ApnSettings implements Parcelable {

    //the fields of APN Settings
    public static final String FIELD_APN      = "apn";
    public static final String FIELD_MMSPROXY = "mmsproxy";
    public static final String FIELD_MMSPORT  = "mmsport";
    public static final String FIELD_MMSC     = "mmsc";
    public static final String FIELD_TYPE     = "type";

    private String mApn;
    private String mMmsc;
    private String mMmsPort;
    private String mMmsProxy;
    private String mType;

    /** @hide */
    protected ApnSettings() {
    }

    /** @hide */
    protected ApnSettings(ApnSettings apn) {
        mApn = apn.mApn;
        mMmsProxy = apn.mMmsProxy;
        mMmsPort = apn.mMmsPort;
        mMmsc = apn.mMmsc;
        mType = apn.mType;
    }

    /** @hide */
    public ApnSettings(String apn, String mmsproxy, String mmsport,
            String mmsc, String type) {
        mApn = apn;
        mMmsProxy = mmsproxy;
        mMmsPort = mmsport;
        mMmsc = mmsc;
        mType = type;
    }

    /**
     * Get the type of the APN
     *
     * @return the type of the APN
     */
    public String getApnType() {
        return mType;
    }

    /**
     * Get the name of the APN
     *
     * @return the name of the APN
     */
    public String getApnName() {
        return mApn;
    }

    /**
     * Get the MMS proxy config of the APN
     *
     * @return the MMS proxy of the APN
     */
    public String getApnMmsProxy() {
        return mMmsProxy;
    }

    /**
     * Get the MMS port of the APN
     *
     * @return MMS port of the APN
     */
    public String getApnMmsPort() {
        return mMmsPort;
    }

    /**
     * Get the MMSC of the APN
     *
     * @return the MMSC of the APN
     */
    public String getApnMmsc() {
        return mMmsc;
    }


    /**
     * Implement the Parcelable interface
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /** Implement the Parcelable interface */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mApn);
        dest.writeString(mMmsc);
        dest.writeString(mMmsPort);
        dest.writeString(mMmsProxy);
        dest.writeString(mType);
    }

    /**
     * create ApnSettings from Parcel
     *
     * @hide
     */
    protected ApnSettings(Parcel in) {
        mApn      = in.readString();
        mMmsc     = in.readString();
        mMmsPort  = in.readString();
        mMmsProxy = in.readString();
        mType     = in.readString();
    }

    /** Implement the Parcelable interface */
    public static final Creator<ApnSettings> CREATOR = new Creator<ApnSettings>() {
        @Override
        public ApnSettings createFromParcel(Parcel in) {
            return new ApnSettings(in);
        }

        @Override
        public ApnSettings[] newArray(int size) {
            return new ApnSettings[size];
        }
    };
}
