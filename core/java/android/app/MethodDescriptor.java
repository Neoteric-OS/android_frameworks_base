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

@FlaggedApi(Flags.FLAG_APP_START_INFO)
public final class MethodDescriptor implements Parcelable {
    public final @NonNull String fullyQualifiedClassName;
    public final @NonNull String methodName;
    public final @NonNull String[] fullyQualifiedParameters;

    public MethodDescriptor(@NonNull String fullyQualifiedClassName, @NonNull String methodName,
            @NonNull String[] fullyQualifiedParameters) {
        this.fullyQualifiedClassName = fullyQualifiedClassName;
        this.methodName = methodName;
        this.fullyQualifiedParameters = fullyQualifiedParameters;
    }

    public static final @NonNull Parcelable.Creator<MethodDescriptor> CREATOR =
            new Parcelable.Creator<>() {
                @Override
                public MethodDescriptor createFromParcel(Parcel in) {
                    return new MethodDescriptor(in);
                }

                @Override
                public MethodDescriptor[] newArray(int size) {
                    return new MethodDescriptor[size];
                }
            };

    private MethodDescriptor(Parcel in) {
        this.fullyQualifiedClassName = in.readString();
        this.methodName = in.readString();
        this.fullyQualifiedParameters = in.readStringArray();
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeString(fullyQualifiedClassName);
        out.writeString(methodName);
        out.writeStringArray(fullyQualifiedParameters);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
