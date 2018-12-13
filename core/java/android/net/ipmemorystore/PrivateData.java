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
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A class wrapping a binary blob for the private use of a client of the IP memory store.
 * @hide
 */
public class PrivateData implements Parcelable {
    // The data.
    @NonNull
    public final ByteBuffer data;

    PrivateData(@NonNull final ByteBuffer data) {
        this.data = data;
    }

    /** @hide */
    public static class Builder {
        @NonNull
        private ByteBuffer mData;

        /**
         * Set the private data.
         * @param data The data.
         * @return This builder.
         */
        public Builder setByteBuffer(@NonNull final ByteBuffer data) {
            mData = data;
            return this;
        }

        /**
         * Return the built PrivateData object.
         * @return The built PrivateData object.
         */
        public PrivateData build() {
            return new PrivateData(mData);
        }
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (!(o instanceof PrivateData)) return false;
        final PrivateData other = (PrivateData) o;
        return data.equals(other.data);
    }

    @Override
    public int hashCode() {
        // Not directly data.hashCode() so that the PrivateData container does
        // not have the same hashCode as the contained ByteBuffer.
        return Objects.hashCode(data.hashCode());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest, final int flags) {
        dest.writeByteArray(data.array());
    }

    public static final Creator<PrivateData> CREATOR = new Creator<PrivateData>() {
        @Override
        public PrivateData createFromParcel(@NonNull final Parcel in) {
            return new PrivateData(ByteBuffer.wrap(in.createByteArray()));
        }

        @Override
        public PrivateData[] newArray(final int size) {
            return new PrivateData[size];
        }
    };

    /** Pretty print */
    public String toString() {
        final StringBuilder sb = new StringBuilder("PrivateData : [");
        final int limit = data.limit();
        for (int i = 0; i < limit; ++i) {
            sb.append(String.format("%02X", data.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }
}
