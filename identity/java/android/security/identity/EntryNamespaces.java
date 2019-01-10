/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.security.identity;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * @hide
 */
public class EntryNamespaces implements Parcelable {
    public static final Parcelable.Creator<EntryNamespaces> CREATOR =
            new Parcelable.Creator<EntryNamespaces>() {
                @Override
                public EntryNamespaces createFromParcel(Parcel in) {
                    // TODO(swillden): Auto-generated method stub
                    return null;
                }

                @Override
                public EntryNamespaces[] newArray(int arg0) {
                    // TODO(swillden): Auto-generated method stub
                    return null;
                }
            };


    @Override
    public int describeContents() {
        // TODO(swillden): Auto-generated method stub
        return 0;
    }

    @Override
    public void writeToParcel(Parcel arg0, int arg1) {
        // TODO(swillden): Auto-generated method stub

    }
}
