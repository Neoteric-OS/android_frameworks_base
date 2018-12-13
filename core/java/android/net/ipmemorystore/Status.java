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

package android.net.ipmemorystore;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * A parcelable status representing the result of an operation.
 * @hide
 */
public class Status implements Parcelable {
    public static final int SUCCESS = 0;

    public final int resultCode;

    public Status(final int resultCode) {
        this.resultCode = resultCode;
    }

    public boolean isSuccess() {
        return SUCCESS == resultCode;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest, final int flags) {
        dest.writeInt(resultCode);
    }

    public static final Creator<Status> CREATOR = new Creator<Status>() {
        @Override
        public Status createFromParcel(@NonNull final Parcel in) {
            return new Status(in.readInt());
        }

        @Override
        public Status[] newArray(final int size) {
            return new Status[size];
        }
    };

    /** Pretty print */
    public String toString() {
        switch (resultCode) {
            case SUCCESS: return "SUCCESS";
            default: return "Unknown value ?!";
        }
    }
}
