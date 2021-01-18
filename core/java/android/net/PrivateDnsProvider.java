/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Represents the private DNS provider.
 * @hide
 */
@SystemApi
public final class PrivateDnsProvider implements Parcelable {
    private static final String TAG = "PrivateDnsProvider";

    /** Provider name. */
    @NonNull
    public final String name;

    public PrivateDnsProvider(@NonNull final String name) {
        this.name = name;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (!(o instanceof PrivateDnsProvider)) return false;
        final PrivateDnsProvider other = (PrivateDnsProvider) o;
        return  this.name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    /**
     * Parcelable Implementation.
     * Note that this object implements parcelable (and needs to keep doing this as it inherits
     * from a class that does), but should usually be parceled as a stable parcelable using
     * the toStableParcelable() and fromStableParcelable() methods.
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /** Write to parcel. */
    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeString(name);
    }

    /** Parcelable Creator. */
    public static final @NonNull Parcelable.Creator<PrivateDnsProvider> CREATOR =
            new Parcelable.Creator<PrivateDnsProvider>() {
                public PrivateDnsProvider createFromParcel(Parcel in) {
                    return new PrivateDnsProvider(in.readString());
                }

                public PrivateDnsProvider[] newArray(int size) {
                    return new PrivateDnsProvider[size];
                }
            };

    @Override
    public String toString() {
        return "name: " + name;
    }
}
