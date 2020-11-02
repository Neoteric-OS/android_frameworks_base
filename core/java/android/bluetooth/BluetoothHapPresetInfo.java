/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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
 * @hide
 */
@SystemApi
public final class BluetoothHapPresetInfo implements Parcelable {
    private int mPresetIndex;
    private String mPresetName;
    private boolean mIsWritable;
    private boolean mIsAvailable;

    /**
     * HapPresetInfo constructor
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param presetIndex Preset index
     * @param presetName Preset Name
     * @param isWritable Is writable flag
     * @param isAvailable Is available flag
     */
    public BluetoothHapPresetInfo(int presetIndex, @NonNull String presetName,
            boolean isWritable, boolean isAvailable) {
        this.mPresetIndex = presetIndex;
        this.mPresetName = presetName;
        this.mIsWritable = isWritable;
        this.mIsAvailable = isAvailable;
    }

    /**
     * HapPresetInfo constructor
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param in HapPresetInfo parcel
     */
    BluetoothHapPresetInfo(@NonNull Parcel in) {
        mPresetIndex = in.readInt();
        mPresetName = in.readString();
        mIsWritable = in.readBoolean();
        mIsAvailable = in.readBoolean();
    }

    /**
     * HapPresetInfo preset index
     *
     * @return Preset index
     * @hide
     */
    public int getIndex() {
        return mPresetIndex;
    }

    /**
     * HapPresetInfo preset name
     *
     * @return Preset name
     * @hide
     */
    public String getName() {
        return mPresetName;
    }

    /**
     * HapPresetInfo preset writability
     *
     * @return If preset is writable
     * @hide
     */
    public boolean isWritable() {
        return mIsWritable;
    }

    /**
     * HapPresetInfo availability
     *
     * @return If preset is available
     * @hide
     */
    public boolean isAvailable() {
        return mIsAvailable;
    }

    /**
     * HapPresetInfo array creator
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     */
    public static final @NonNull Creator<BluetoothHapPresetInfo> CREATOR =
            new Creator<BluetoothHapPresetInfo>() {
                public BluetoothHapPresetInfo createFromParcel(@NonNull Parcel in) {
                    return new BluetoothHapPresetInfo(in);
                }

                public BluetoothHapPresetInfo[] newArray(int size) {
                    return new BluetoothHapPresetInfo[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mPresetIndex);
        dest.writeString(mPresetName);
        dest.writeBoolean(mIsWritable);
        dest.writeBoolean(mIsAvailable);
    }
}
