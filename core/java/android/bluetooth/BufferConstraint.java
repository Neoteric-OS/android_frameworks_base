/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.bluetooth;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Stores a codec's dynamic audio buffer config information.
 *
 * {@hide}
 */
@SystemApi
public final class BufferConstraint implements Parcelable {

    private static final String TAG = "BufferConstraint";
    private int mDefaultMillis;
    private int mMaximumMillis;
    private int mMinimumMillis;

    public BufferConstraint(int defaultMillis, int maximumMillis,
            int minimumMillis) {
        mDefaultMillis = defaultMillis;
        mMaximumMillis = maximumMillis;
        mMinimumMillis = minimumMillis;
    }

    BufferConstraint(Parcel in) {
        mDefaultMillis = in.readInt();
        mMaximumMillis = in.readInt();
        mMinimumMillis = in.readInt();
    }

    public static final @NonNull Parcelable.Creator<BufferConstraint> CREATOR =
            new Parcelable.Creator<BufferConstraint>() {
                public BufferConstraint createFromParcel(Parcel in) {
                    return new BufferConstraint(in);
                }

                public BufferConstraint[] newArray(int size) {
                    return new BufferConstraint[size];
                }
            };

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeInt(mDefaultMillis);
        out.writeInt(mMaximumMillis);
        out.writeInt(mMinimumMillis);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Get the default buffer millis
     *
     * @return default buffer millis
     * @hide
     */
    @SystemApi
    public int getDefaultMillis() {
        return mDefaultMillis;
    }

    /**
     * Get the maximum buffer millis
     *
     * @return maximum buffer millis
     * @hide
     */
    @SystemApi
    public int getMaximumMillis() {
        return mMaximumMillis;
    }

    /**
     * Get the minimum buffer millis
     *
     * @return minimum buffer millis
     * @hide
     */
    @SystemApi
    public int getMinimumMillis() {
        return mMinimumMillis;
    }
}
