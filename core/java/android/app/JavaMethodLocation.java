/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

public final class JavaMethodLocation implements Parcelable {
    public final String odexPath;
    public final int odexOffset;
    public final int methodOffset;

    @FlaggedApi(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public JavaMethodLocation(String odexPath, int odexOffset, int methodOffset) {
        this.odexPath = odexPath;
        this.odexOffset = odexOffset;
        this.methodOffset = methodOffset;
    }

    @FlaggedApi(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    public static final @NonNull Parcelable.Creator<JavaMethodLocation> CREATOR =
            new Parcelable.Creator<>() {
                @Override
                public JavaMethodLocation createFromParcel(Parcel in) {
                    return new JavaMethodLocation(in);
                }

                @Override
                public JavaMethodLocation[] newArray(int size) {
                    return new JavaMethodLocation[size];
                }
            };

    private JavaMethodLocation(Parcel in) {
        this.odexPath = in.readString();
        this.odexOffset = in.readInt();
        this.methodOffset = in.readInt();
    }

    @FlaggedApi(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeString(odexPath);
        out.writeInt(odexOffset);
        out.writeInt(methodOffset);
    }

    @FlaggedApi(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
    @Override
    public int describeContents() {
        return 0;
    }
}
