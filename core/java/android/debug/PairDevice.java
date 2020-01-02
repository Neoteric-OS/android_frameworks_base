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

package android.debug;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Class containing the host device informations, also broadcast this class to
 * the setting UI, in order to updating the paired devices.
 * @hide
 */
public class PairDevice implements Parcelable {
    /**
     * The human-readable name of the device.
     */
    @NonNull private String mName;

    /**
     * The device's guid.
     */
    @NonNull private String mGuid;

    /**
     * Indicates whether the device is currently connected to adbd.
     */
    private boolean mConnected;

    public PairDevice(@NonNull String name, @NonNull String guid, boolean connected) {
        if (name.isEmpty() || guid.isEmpty()) {
            throw new IllegalArgumentException("Empty name/guid is not allowed");
        }
        mName = name;
        mGuid = guid;
        mConnected = connected;
    }

    /**
     * Sets the device name.
     */
    public void setDeviceName(@NonNull String name) {
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Empty name is not allowed");
        }
        mName = name;
    }

    /**
     * Returns the device name.
     */
    @NonNull
    public String getDeviceName() {
        return mName;
    }

    /**
     * Sets the device GUID.
     */
    public void setGuid(@NonNull String guid) {
        if (guid.isEmpty()) {
            throw new IllegalArgumentException("Empty guid is not allowed");
        }
        mGuid = guid;
    }

    /**
     * Returns the device GUID.
     */
    @NonNull
    public String getGuid() {
        return mGuid;
    }

    /**
     * Sets the adb connection state of the device.
     */
    public void setConnected(boolean connected) {
        mConnected = connected;
    }

    /**
     * Returns the adb connection state of the device.
     */
    public boolean isConnected() {
        return mConnected;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeString(mGuid);
        dest.writeBoolean(mConnected);
    }

    /**
     * print device string
     */
    @Override
    public String toString() {
        return "\n" + mName + "\n" + mGuid + "\n" + mConnected;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private PairDevice(Parcel source) {
        mName = source.readString();
        mGuid = source.readString();
        mConnected = source.readBoolean();
    }

    @NonNull
    public static final Parcelable.Creator<PairDevice> CREATOR =
            new Creator<PairDevice>() {
                @Override
                public PairDevice createFromParcel(Parcel source) {
                    return new PairDevice(source);
                }

                @Override
                public PairDevice[] newArray(int size) {
                    return new PairDevice[size];
                }
            };
}
