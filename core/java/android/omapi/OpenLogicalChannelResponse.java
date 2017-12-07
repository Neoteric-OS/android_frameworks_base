/*
 * Copyright (C) 2017 The Android Open Source Project
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
/*
 * Copyright (c) 2017, The Linux Foundation.
 */
/*
 * Contributed by: Giesecke & Devrient GmbH.
 */

package android.omapi;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Class encapsulating open logical channel response APDU
 */
public class OpenLogicalChannelResponse implements Parcelable {

    private int mChannelNumber;
    private byte[] mSelectResponse;

    /** Class for creating a OpenLogicalChannelResponse instance. */
    public static final Parcelable.Creator<OpenLogicalChannelResponse> CREATOR =
            new Parcelable.Creator<OpenLogicalChannelResponse>() {
                public OpenLogicalChannelResponse createFromParcel(Parcel in) {
                    return new OpenLogicalChannelResponse(in);
                }

                public OpenLogicalChannelResponse[] newArray(int size) {
                    return new OpenLogicalChannelResponse[size];
                }
            };

    /**
     * Construct OpenLogicalChannelResponse from SELECT response APDU.
     *
     * @param channelNum Channel Number
     * @param selectResponse Array of SELECT response APDU bytes
     */
    public OpenLogicalChannelResponse(int channelNum, byte[] selectResponse) {
        mChannelNumber = channelNum;
        mSelectResponse = selectResponse;
    }

    /**
     * Construct OpenLogicalChannelResponse from a Parcel.
     *
     * @param in Parcel containing channel number and SELECT response APDU.
     */
    private OpenLogicalChannelResponse(Parcel in) {
        mChannelNumber = in.readInt();
        mSelectResponse = in.createByteArray();
    }

    /** Return the channel number. */
    public int getChannel() {
        return mChannelNumber;
    }

    /** Return the SELECT response APDU data as an array of bytes. */
    public byte[] getSelectResponse() {
        return mSelectResponse;
    }

    /**
     * Required implementation of describeContents for Parcelable.
     * There are no special object types in this instance.
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /** Flatten OpenLogicalChannelResponse instance to a Parcel. */
    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mChannelNumber);
        out.writeByteArray(mSelectResponse);
    }
}
