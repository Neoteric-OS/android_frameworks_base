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

package android.net;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.SubscriptionManager;

import java.util.Objects;

/**
 * NetworkSpecifir object for cellular network request. Apps should use the
 * {@link TelephonyNetworkSpecifier.Builder} class to create an instance.
 *
 * @hide
 */
public final class TelephonyNetworkSpecifier extends NetworkSpecifier implements Parcelable {

    private final int mSubId;

    /**
     * Return the subscription Id of current TelephonyNetworkSpecifier object.
     * @return The subscription id.
     */
    public int getSubscriptionId() {
        return mSubId;
    }

    /**
     * @hide
     */
    public TelephonyNetworkSpecifier(int subId) {
        this.mSubId = subId;
    }

    public static final @NonNull Creator<TelephonyNetworkSpecifier> CREATOR =
            new Creator<TelephonyNetworkSpecifier>() {
                @Override
                public TelephonyNetworkSpecifier createFromParcel(Parcel in) {
                    int subId = in.readInt();
                    return new TelephonyNetworkSpecifier(subId);
                }

                @Override
                public TelephonyNetworkSpecifier[] newArray(int size) {
                    return new TelephonyNetworkSpecifier[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mSubId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSubId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TelephonyNetworkSpecifier)) {
            return false;
        }

        TelephonyNetworkSpecifier lhs = (TelephonyNetworkSpecifier) obj;
        return mSubId == lhs.mSubId;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("TelephonyNetworkSpecifier [")
                .append(", mSubId = ").append(mSubId)
                .append("]")
                .toString();
    }

    /** @hide **/
    @Override
    public boolean satisfiedBy(NetworkSpecifier other) {
        if (this == other) {
            return true;
        }

        // Any generic requests should be satisfied by a specific telephony network.
        // For simplicity, we treat null same as MatchAllNetworkSpecifier
        if (other == null || other instanceof MatchAllNetworkSpecifier) {
            return true;
        }

        if (other instanceof TelephonyNetworkSpecifier) {
            return ((TelephonyNetworkSpecifier) other).equals(this);
        }
        return equals(other);
    }


    /**
     * Builder to create {@link TelephonyNetworkSpecifier} object.
     */
    public static final class Builder {
        private int mSubId;

        public Builder() {
            mSubId = SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;
        }

        /**
         * Set the subscription id.
         *
         * @param subId The subscription Id.
         * @return Instance of {@link Buillder} to enable the chaining of the builder method.
         */
        public @NonNull Builder setSubscriptionId(int subId) {
            // Unfortunately, invalid subId has been used as valid input for a while.
            // If we force valid subId only, many contract will be broken. Turn it off for now.
            // if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            //     throw new IllegalArgumentException("Invalid subscription id: " + subId);
            // }
            mSubId = subId;
            return this;
        }

        /**
         * Create a NetworkSpecifier for the cellular network request.
         */
        public @NonNull TelephonyNetworkSpecifier build() {
            return new TelephonyNetworkSpecifier(mSubId);
        }
    }
}
