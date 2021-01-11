/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.net.vcn;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.TransportInfo;
import android.net.wifi.WifiInfo;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * VcnTransportInfo represents the TransportInfo for a VCN Network.
 *
 * @hide
 */
public class VcnTransportInfo implements TransportInfo, Parcelable {
    @Nullable private final WifiInfo mWifiInfo;
    private final int mSubId;

    public VcnTransportInfo(@Nullable WifiInfo wifiInfo, int subId) {
        mWifiInfo = wifiInfo;
        mSubId = subId;
    }

    /** Get the {@link WifiInfo} for this VcnTransportInfo. */
    @Nullable
    public WifiInfo getWifiInfo() {
        return mWifiInfo;
    }

    /** Get the subId for the VCN Network associated with this VcnTransportInfo. */
    public int getSubId() {
        return mSubId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mWifiInfo, mSubId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VcnTransportInfo)) return false;
        final VcnTransportInfo that = (VcnTransportInfo) o;

        return mWifiInfo.equals(that.mWifiInfo) && mSubId == that.mSubId;
    }

    /** {@inheritDoc} */
    @Override
    public int describeContents() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {}

    /** Implement the Parcelable interface */
    public static final @NonNull Creator<VcnTransportInfo> CREATOR =
            new Creator<VcnTransportInfo>() {
                public VcnTransportInfo createFromParcel(Parcel in) {
                    return new VcnTransportInfo(null /* wifiInfo */, 0 /* subId */);
                }

                public VcnTransportInfo[] newArray(int size) {
                    return new VcnTransportInfo[size];
                }
            };
}
