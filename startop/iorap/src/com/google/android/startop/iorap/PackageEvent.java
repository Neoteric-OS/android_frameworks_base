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

package com.google.android.startop.iorap;

import android.annotation.NonNull;
import android.os.Parcelable;
import android.os.Parcel;
import android.net.Uri;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * @hide
 */
public class PackageEvent implements Parcelable {

    /** Intent.ACTION_PACKAGE_REPLACED */
    public static final int TYPE_REPLACED = 0;
    private static final int TYPE_MAX = 0;

    /** @hide */
    @IntDef(flag = true, prefix = { "TYPE_" }, value = {
            TYPE_REPLACED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {}

    @Type public final int type;

    public final Uri packageUri;
    public final String packageName;

    @NonNull
    public static PackageEvent createReplaced(Uri packageUri, String packageName) {
        return new PackageEvent(TYPE_REPLACED, packageUri, packageName);
    }

    private PackageEvent(@Type int type, Uri packageUri, String packageName) {
        this.type = type;
        this.packageUri = packageUri;
        this.packageName = packageName;

        checkConstructorArguments();
    }

    private void checkConstructorArguments() {
        CheckHelpers.checkTypeInRange(type, TYPE_MAX);
        Objects.requireNonNull(packageUri, "packageUri");
        Objects.requireNonNull(packageName, "packageName");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other instanceof PackageEvent) {
            return equals((PackageEvent) other);
        }
        return false;
    }

    private boolean equals(PackageEvent other) {
        return type == other.type &&
                Objects.equals(packageUri, other.packageUri) &&
                Objects.equals(packageName, other.packageName);
    }

    @Override
    public String toString() {
        return String.format("{packageUri: %s, packageName: %s}", packageUri, packageName);
    }

    //<editor-fold desc="Binder boilerplate">
    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(type);
        packageUri.writeToParcel(out, flags);
        out.writeString(packageName);
    }

    private PackageEvent(Parcel in) {
        this.type = in.readInt();
        this.packageUri = Uri.CREATOR.createFromParcel(in);
        this.packageName = in.readString();

        checkConstructorArguments();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<PackageEvent> CREATOR
            = new Parcelable.Creator<PackageEvent>() {
        public PackageEvent createFromParcel(Parcel in) {
            return new PackageEvent(in);
        }

        public PackageEvent[] newArray(int size) {
            return new PackageEvent[size];
        }
    };
    //</editor-fold>
}
