/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Object representing the quality of a network as perceived by the user.
 *
 * A NetworkScore object represents the characteristics of a network that affects how good the
 * network is considered for a particular use.
 * @hide
 */
@SystemApi
public final class NetworkScore implements Parcelable {
    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            DONT_FORCE_KEEPUP,
            FORCE_KEEPUP_FOR_HANDOVER
    })
    public @interface ForceKeepupReason { }

    public static final int DONT_FORCE_KEEPUP = 0;
    public static final int FORCE_KEEPUP_FOR_HANDOVER = 1;

    // TODO : remove this, it's not necessary with an API to listen to all requests
    @NonNull
    public static final NetworkScore INVINCIBLE_SCORE = new NetworkScore(1000, DONT_FORCE_KEEPUP);

    // This will be removed soon. Do *NOT* depend on it for any new code that is not part of
    // a migration.
    public final int legacyInt;

    private final int forceKeepupReason;

    public NetworkScore(final int legacyInt, @ForceKeepupReason int forceKeepupReason) {
        this.legacyInt = legacyInt;
        this.forceKeepupReason = forceKeepupReason;
    }

    private NetworkScore(@NonNull final Parcel in) {
        legacyInt = in.readInt();
        forceKeepupReason = in.readInt();
    }

    public int getForceKeepupReason() {
        return forceKeepupReason;
    }

    public int getLegacyInt() {
        return legacyInt;
    }

    @Override
    public String toString() {
        return "Score(" + legacyInt + ")";
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest, final int flags) {
        dest.writeInt(legacyInt);
        dest.writeInt(forceKeepupReason);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull public static final Creator<NetworkScore> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public NetworkScore createFromParcel(@NonNull final Parcel in) {
            return new NetworkScore(in);
        }

        @Override
        @NonNull
        public NetworkScore[] newArray(int size) {
            return new NetworkScore[size];
        }
    };

    public static final class Builder {
        private static final int INVALID_LEGACY_INT = Integer.MIN_VALUE;
        private int mLegacyInt = INVALID_LEGACY_INT;
        private int mForceKeepupReason = DONT_FORCE_KEEPUP;

        @NonNull
        public Builder setForceKeepupReason(@ForceKeepupReason final int reason) {
            mForceKeepupReason = reason;
            return this;
        }

        @NonNull
        public Builder setLegacyInt(final int score) {
            mLegacyInt = score;
            return this;
        }

        @NonNull
        public NetworkScore build() {
            return new NetworkScore(mLegacyInt, mForceKeepupReason);
        }
    }
}
