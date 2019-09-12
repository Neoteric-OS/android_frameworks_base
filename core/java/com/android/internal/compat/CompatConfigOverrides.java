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

package com.android.internal.compat;


import android.os.Parcel;
import android.os.Parcelable;
import android.util.ArraySet;

/**
 * Parcelable containing compat config overrides for a given application.
 */
public class CompatConfigOverrides implements Parcelable {

    public final ArraySet<Long> enabled;
    public final ArraySet<Long> disabled;
    public final String packageName;

    public CompatConfigOverrides(
            ArraySet<Long> enabled, ArraySet<Long> disabled, String packageName) {
        this.enabled = enabled;
        this.disabled = disabled;
        this.packageName = packageName;
    }

    private CompatConfigOverrides(Parcel in) {
        enabled = (ArraySet<Long>) in.readArraySet(null);
        disabled = (ArraySet<Long>) in.readArraySet(null);
        packageName = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeArraySet(enabled);
        dest.writeArraySet(disabled);
        dest.writeString(packageName);
    }
    public static final Parcelable.Creator<CompatConfigOverrides> CREATOR =
            new Parcelable.Creator<CompatConfigOverrides>() {

                @Override
                public CompatConfigOverrides createFromParcel(Parcel in) {
                    return new CompatConfigOverrides(in);
                }

                @Override
                public CompatConfigOverrides[] newArray(int size) {
                    return new CompatConfigOverrides[size];
                }
            };
}
