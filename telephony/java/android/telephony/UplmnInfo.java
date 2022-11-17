/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.Objects;

public final class UplmnInfo implements Parcelable {

    private String mOperatorNumeric;
    private int mNetworkMode;
    private int mPriority;

	@NonNull
    public String getOperatorNumeric() {
        return mOperatorNumeric;
    }

    public int getNetworMode() {
        return mNetworkMode;
    }

    public int getPriority() {
        return mPriority;
    }

    public void setOperatorNumeric(@NonNull String operatorNumeric) {
        mOperatorNumeric = operatorNumeric;
    }

    public void setNetworMode(int networkMode) {
        mNetworkMode = networkMode;
    }

    public void setPriority(int index) {
        mPriority = index;
    }
	
    private UplmnInfo(Parcel in) {
        mOperatorNumeric = in.readString();
        mNetworkMode = in.readInt();
        mPriority = in.readInt();
    }

    public UplmnInfo(@NonNull String operatorNumeric, int networkMode,
            int priority) {
        mOperatorNumeric = operatorNumeric;
        mNetworkMode = networkMode;
        mPriority = priority;
    }

    @Override
    public String toString() {
        return "UplmnInfo " + mOperatorNumeric
                + "/" + mNetworkMode
                + "/" + mPriority;
    }

    /**
     * Parcelable interface implemented below.
     * This is a simple effort to make UplmnInfo parcelable rather than
     * trying to make the conventional containing object (AsyncResult),
     * implement parcelable.  This functionality is needed for the
     * NetworkQueryService to fix 1128695.
     */

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Implement the Parcelable interface.
     * Method to serialize a UplmnInfo object.
     */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mOperatorNumeric);
        dest.writeInt(mNetworkMode);
        dest.writeInt(mPriority);
    }

    /**
     * Parcel creator class.
     */
    public static final @NonNull Parcelable.Creator<UplmnInfo> CREATOR = new Creator<UplmnInfo>() {
        public UplmnInfo createFromParcel(Parcel in) {
            return new UplmnInfo(in);
        }
        public UplmnInfo[] newArray(int size) {
            return new UplmnInfo[size];
        }
    };

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof UplmnInfo)) {
            return false;
        }

        UplmnInfo o = (UplmnInfo) other;
        return mNetworkMode == o.mNetworkMode
				&& mPriority == o.mPriority
                && TextUtils.equals(mOperatorNumeric, o.mOperatorNumeric);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mOperatorNumeric, mNetworkMode, mPriority);
    }
}
