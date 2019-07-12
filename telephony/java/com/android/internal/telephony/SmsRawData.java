/*
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/


package com.android.internal.telephony;

import android.annotation.UnsupportedAppUsage;
import android.os.Parcel;
import android.os.Parcelable;

/** A parcelable holder class of byte[] pdu and an index for an SMS EF record. */
public class SmsRawData implements Parcelable {
    byte[] mData;
    int mIndex;

    //Static Methods
    @UnsupportedAppUsage
    public static final Parcelable.Creator<SmsRawData> CREATOR =
            new Parcelable.Creator<SmsRawData>() {
        @Override
        public SmsRawData createFromParcel(Parcel source) {
            int size;
            size = source.readInt();
            byte[] data = new byte[size];
            source.readByteArray(data);
            int index = source.readInt();
            return new SmsRawData(data, index);
        }

        @Override
        public SmsRawData[] newArray(int size) {
            return new SmsRawData[size];
        }
    };

    // Constructor
    @UnsupportedAppUsage
    public SmsRawData(byte[] data, int index) {
        mData = data;
        mIndex = index;
    }

    @UnsupportedAppUsage
    public byte[] getBytes() {
        return mData;
    }

    public int getIndex() {
        return mIndex;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mData.length);
        dest.writeByteArray(mData);
        dest.writeInt(mIndex);
    }
}
