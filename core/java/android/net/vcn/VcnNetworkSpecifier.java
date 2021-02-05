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

package android.net.vcn;

import android.annotation.NonNull;
import android.net.NetworkSpecifier;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.annotations.VisibleForTesting.Visibility;

import java.util.Arrays;

/**
 * NetworkSpecifier object for VCN network requests.
 *
 * <p>A VCN Network runs on the concept of a subscription group, and thus matches any subId
 * contained within the subscription group.
 *
 * @hide
 */
public final class VcnNetworkSpecifier extends NetworkSpecifier implements Parcelable {
    private final int[] mSubIds;

    /**
     * Builds a new VcnNetworkSpecifier with the given list of subIds
     *
     * @hide
     */
    public VcnNetworkSpecifier(int[] subIds) {
        mSubIds = subIds;
    }

    /**
     * Retrieves the list of subIds supported by this VcnNetworkSpecifier
     *
     * @hide
     */
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    public int[] getSubIds() {
        return mSubIds;
    }

    public static final @NonNull Creator<VcnNetworkSpecifier> CREATOR =
            new Creator<VcnNetworkSpecifier>() {
                @Override
                public VcnNetworkSpecifier createFromParcel(Parcel in) {
                    int[] subIds = in.createIntArray();
                    return new VcnNetworkSpecifier(subIds);
                }

                @Override
                public VcnNetworkSpecifier[] newArray(int size) {
                    return new VcnNetworkSpecifier[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeIntArray(mSubIds);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mSubIds);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof VcnNetworkSpecifier)) {
            return false;
        }

        VcnNetworkSpecifier lhs = (VcnNetworkSpecifier) obj;
        return Arrays.equals(mSubIds, lhs.mSubIds);
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("VcnNetworkSpecifier [")
                .append("mSubIds = ").append(Arrays.toString(mSubIds))
                .append("]")
                .toString();
    }
}
