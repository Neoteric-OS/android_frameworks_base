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
package android.net;

import android.annotation.TestApi;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;

/**
 * This class is used to return the interface name and fd of the test interface
 *
 * @hide
 */
@TestApi
public final class TestTunTapInterface implements Parcelable {
    private static final String TAG = "TestTunTapInterface";

    public final ParcelFileDescriptor fileDescriptor;
    public final String interfaceName;
    // Parcelable Methods

    @Override
    public int describeContents() {
        return (fileDescriptor != null) ? Parcelable.CONTENTS_FILE_DESCRIPTOR : 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(fileDescriptor, PARCELABLE_WRITE_RETURN_VALUE);
        out.writeString(interfaceName);
    }

    public TestTunTapInterface(ParcelFileDescriptor pfd, String intf) {
        fileDescriptor = pfd;
        interfaceName = intf;
    }

    private TestTunTapInterface(Parcel in) {
        fileDescriptor = in.readParcelable(ParcelFileDescriptor.class.getClassLoader());
        interfaceName = in.readString();
    }

    public static final Parcelable.Creator<TestTunTapInterface> CREATOR =
            new Parcelable.Creator<TestTunTapInterface>() {
                public TestTunTapInterface createFromParcel(Parcel in) {
                    return new TestTunTapInterface(in);
                }

                public TestTunTapInterface[] newArray(int size) {
                    return new TestTunTapInterface[size];
                }
            };
}
