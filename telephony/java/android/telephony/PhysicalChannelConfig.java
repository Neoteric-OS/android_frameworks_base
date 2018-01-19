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

/**
 * @hide
 */
public class PhysicalChannelConfig implements Parcelable {

    /**
     * UE has connection to cell for signalling and possibly data (3GPP 36.331, 25.331).
     */
    public static int CONNECTION_PRIMARY_SERVING = 0;

    /**
     * UE has connection to cell for data (3GPP 36.331, 25.331).
     */
    public static int CONNECTION_SECONDARY_SERVING = 1;

    /**
     * Connection status of the cell.
     *
     * <p>One of {@link #CONNECTION_PRIMARY_SERVING}, {@link #CONNECTION_SECONDARY_SERVING}.
     */
    private int mCellConnectionStatus;

    /**
     * Cell bandwidth, in kHz.
     */
    private int mCellBandwidthDownlinkKhz;

    public PhysicalChannelConfig(int status, int bandwidth) {
        mCellConnectionStatus = status;
        mCellBandwidthDownlinkKhz = bandwidth;
    }

    public PhysicalChannelConfig(Parcel in) {
        mCellConnectionStatus = in.readInt();
        mCellBandwidthDownlinkKhz = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mCellConnectionStatus);
        dest.writeInt(mCellBandwidthDownlinkKhz);
    }


    public static final Parcelable.Creator<PhysicalChannelConfig> CREATOR =
        new Parcelable.Creator<PhysicalChannelConfig>() {
            public PhysicalChannelConfig createFromParcel(Parcel in) {
                return new PhysicalChannelConfig(in);
            }

            public PhysicalChannelConfig[] newArray(int size) {
                return new PhysicalChannelConfig[size];
            }
        };
}
