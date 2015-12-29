/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.os.Parcel;
import android.os.Parcelable;

import android.util.Log;

/**
 * Out Of Band Data for Bluetooth device.
 */
public class OOBData implements Parcelable {
    public byte[] securityManagerTK;

    public byte[] getSecurityManagerTK() {
        return securityManagerTK;
    }

    public void setSecurityManagerTK(byte[] securityManagerTK) {
        this.securityManagerTK = securityManagerTK;
    }

    public OOBData() { }

    private OOBData(Parcel in) {
        securityManagerTK = in.createByteArray();
    }

    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeByteArray(securityManagerTK);
    }

    public static final Parcelable.Creator<OOBData> CREATOR
            = new Parcelable.Creator<OOBData>() {
        public OOBData createFromParcel(Parcel in) {
            return new OOBData(in);
        }

        public OOBData[] newArray(int size) {
            return new OOBData[size];
        }
    };
}