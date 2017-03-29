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

package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.util.Objects;

/** @hide */
public final class StringNetworkSpecifier extends NetworkSpecifier implements Parcelable {
    public final String specifier;

    public StringNetworkSpecifier(String specifier) {
        if (TextUtils.isEmpty(specifier)) {
            throw new IllegalArgumentException("Network specifier must not be empty");
        }
        this.specifier = specifier;
    }

    @Override
    public boolean satisfiedBy(NetworkSpecifier other) {
        if (other == null) return true;
        if (!(other instanceof StringNetworkSpecifier)) return false;
        return specifier.equals(((StringNetworkSpecifier) other).specifier);
    }

    public boolean equals(Object o) {
        if (!(o instanceof StringNetworkSpecifier)) return false;
        StringNetworkSpecifier other = (StringNetworkSpecifier) o;
        return specifier.equals(other.specifier);
    }

    public int hashCode() {
        return Objects.hashCode(specifier);
    }

    public String toString() {
        return specifier;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(specifier);
    }

    public static final Parcelable.Creator<StringNetworkSpecifier> CREATOR =
            new Parcelable.Creator<StringNetworkSpecifier>() {
        public StringNetworkSpecifier createFromParcel(Parcel in) {
            return new StringNetworkSpecifier(in.readString());
        }
        public StringNetworkSpecifier[] newArray(int size) {
            return new StringNetworkSpecifier[size];
        }
    };
}
