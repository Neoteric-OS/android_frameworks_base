/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.hardware.iris;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Container for iris metadata.
 * @hide
 */
public final class Iris implements Parcelable {
    private CharSequence mName;
    private int mGroupId;
    private int mIrisId;
    private long mDeviceId; // physical device this is associated with

    public Iris(CharSequence name, int groupId, int irisId, long deviceId) {
        mName = name;
        mGroupId = groupId;
        mIrisId = irisId;
        mDeviceId = deviceId;
    }

    private Iris(Parcel in) {
        mName = in.readString();
        mGroupId = in.readInt();
        mIrisId = in.readInt();
        mDeviceId = in.readLong();
    }

    /**
     * Gets the human-readable name for the given iris.
     * @return name given to iris
     */
    public CharSequence getName() { return mName; }

    /**
     * Gets the device-specific iris id.  Used by Settings to map a name to a specific
     * iris template.
     * @return device-specific id for this iris
     * @hide
     */
    public int getIrisId() { return mIrisId; }

    /**
     * Gets the group id specified when the iris was enrolled.
     * @return group id for the set of irises this one belongs to.
     * @hide
     */
    public int getGroupId() { return mGroupId; }

    /**
     * Device this iris belongs to.
     * @hide
     */
    public long getDeviceId() { return mDeviceId; }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mName.toString());
        out.writeInt(mGroupId);
        out.writeInt(mIrisId);
        out.writeLong(mDeviceId);
    }

    public static final Parcelable.Creator<Iris> CREATOR
            = new Parcelable.Creator<Iris>() {
        public Iris createFromParcel(Parcel in) {
            return new Iris(in);
        }

        public Iris[] newArray(int size) {
            return new Iris[size];
        }
    };
};