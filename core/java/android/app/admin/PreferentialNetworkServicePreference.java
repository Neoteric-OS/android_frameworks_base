/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.app.admin;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Network preferences to be set for the user profile
 * {@see DevicePolicyManager#setPreferentialNetworkServicePreference}.
 */
public final class PreferentialNetworkServicePreference implements Parcelable {
    final boolean mPreferentialNetworkServiceEnabled;
    final int mPreferentialNetworkSubLevel;
    final boolean mAllowFallback;
    @Nullable final List<Integer> mUidAllowList;

    /**
     * Preferential network sub level 1
     */
    public static final int PREFERENTIAL_NETWORK_SUB_LEVEL_1 = 1;

    /**
     * Preferential network sub level 2
     */
    public static final int PREFERENTIAL_NETWORK_SUB_LEVEL_2 = 2;

    /**
     * Preferential network sub level 3
     */
    public static final int PREFERENTIAL_NETWORK_SUB_LEVEL_3 = 3;

    /**
     * Preferential network sub level 4
     */
    public static final int PREFERENTIAL_NETWORK_SUB_LEVEL_4 = 4;

    /**
     * Preferential network sub level 5
     */
    public static final int PREFERENTIAL_NETWORK_SUB_LEVEL_5 = 5;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = { "PREFERENTIAL_NETWORK_SUB_LEVEL_" }, value = {
            PREFERENTIAL_NETWORK_SUB_LEVEL_1,
            PREFERENTIAL_NETWORK_SUB_LEVEL_2,
            PREFERENTIAL_NETWORK_SUB_LEVEL_3,
            PREFERENTIAL_NETWORK_SUB_LEVEL_4,
            PREFERENTIAL_NETWORK_SUB_LEVEL_5,
    })

    public @interface PreferentialNetworkPreferenceSubLevel {
    }

    private PreferentialNetworkServicePreference(boolean preferentialNetworkServiceEnabled,
            boolean allowFallback, List<Integer> uidAllowList,
            @PreferentialNetworkPreferenceSubLevel int preferentialNetworkSubLevel) {
        mPreferentialNetworkServiceEnabled = preferentialNetworkServiceEnabled;
        mAllowFallback = allowFallback;
        mUidAllowList = uidAllowList;
        mPreferentialNetworkSubLevel = preferentialNetworkSubLevel;
    }

    private PreferentialNetworkServicePreference(Parcel in) {
        mPreferentialNetworkServiceEnabled = in.readBoolean();
        mAllowFallback = in.readBoolean();
        mUidAllowList = in.readArrayList(Integer.class.getClassLoader(), Integer.class);
        mPreferentialNetworkSubLevel = in.readInt();
    }

    public boolean isPreferentialNetworkServiceEnabled() {
        return mPreferentialNetworkServiceEnabled;
    }

    /**
     * is fallback to default network allowed. This boolean allows an enterprise admin to
     * configure whether default connection (default internet or wifi) should be used or not
     * if an enterprise connection is not available.
     * @return true if fallback is allowed, else false.
     */
    public boolean getFallbackToSystemDefault() {
        return mAllowFallback;
    }

    /**
     * @return List of uids applicable for the profile preference.
     *      Empty list would mean that this request applies to all uids in the profile.
     */
    public @NonNull List<Integer> getUidAllowList() {
        return new ArrayList<>(mUidAllowList);
    }

    /**
     * @return preference enterprise sub-level. valid values starts from
     * PREFERENTIAL_NETWORK_SUB_LEVEL_1 to
     * PREFERENTIAL_NETWORK_SUB_LEVEL_5.
     * preference sub-level is applicable only if preference network service is enabled
     *
     */
    public int getPreferentialNetworkSubLevel() {
        return mPreferentialNetworkSubLevel;
    }

    @Override
    public String toString() {
        return "PreferentialNetworkServicePreference{"
                + "mPreferentialNetworkServiceEnabled=" + isPreferentialNetworkServiceEnabled()
                + "mAllowFallback=" + getFallbackToSystemDefault()
                + "mUidAllowList=" + mUidAllowList.toString()
                + "mPreferentialNetworkSubLevel=" + mPreferentialNetworkSubLevel
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final PreferentialNetworkServicePreference that = (PreferentialNetworkServicePreference) o;
        return mPreferentialNetworkServiceEnabled == that.mPreferentialNetworkServiceEnabled
                && mAllowFallback == that.mAllowFallback
                && mPreferentialNetworkSubLevel == that.mPreferentialNetworkSubLevel
                && (mUidAllowList == that.mUidAllowList
                || mUidAllowList.equals(that.mUidAllowList));
    }

    @Override
    public int hashCode() {
        return ((Objects.hashCode(mPreferentialNetworkServiceEnabled) * 17)
                + (Objects.hashCode(mAllowFallback) * 19)
                + (Objects.hashCode(mUidAllowList) * 23)
                + mPreferentialNetworkSubLevel * 29);
    }

    /**
     * Builder used to create {@link PreferentialNetworkServicePreference} objects.
     * Specify the preferred Network preference
     */
    public static final class Builder {
        boolean mPreferentialNetworkServiceEnabled;
        int mPreferentialNetworkSubLevel;
        boolean mAllowFallback;
        @Nullable List<Integer> mUidAllowList;

        /**
         * Constructs an empty Builder with PROFILE_NETWORK_PREFERENCE_DEFAULT profile preference
         */
        public Builder() {}

        /**
         * Set the preferential network preference
         * See the documentation for the individual preferences for a description of the supported
         * behaviors. Default value is false.
         * @param preferentialNetworkServiceEnabled  the desired network preference to use
         * @return The builder to facilitate chaining.
         */
        @NonNull
        public PreferentialNetworkServicePreference.Builder setPreferentialNetworkServiceEnabled(
                @PreferentialNetworkPreferenceSubLevel boolean preferentialNetworkServiceEnabled) {
            mPreferentialNetworkServiceEnabled = preferentialNetworkServiceEnabled;
            return this;
        }

        /**
         * Set allow fallback. This boolean allows an enterprise admin to configure whether
         * default connection (default internet or wifi) should be used or not if an enterprise
         * connection is not available.
         * @param allowFallback  true if fallback is allowed else false
         * @return The builder to facilitate chaining.
         */
        @NonNull
        @SuppressLint("MissingGetterMatchingBuilder")
        public PreferentialNetworkServicePreference.Builder setFallbackToSystemDefault(
                boolean allowFallback) {
            mAllowFallback = allowFallback;
            return this;
        }

        /**
         * This is a list of uids for which profile perefence is set.
         * Null would mean that this preference applies to all uids in the profile.
         * @param uids  list of uids
         * @return The builder to facilitate chaining.
         */
        @NonNull
        public PreferentialNetworkServicePreference.Builder setUidAllowList(
                @Nullable List<Integer> uids) {
            mUidAllowList = new ArrayList<Integer>(uids);
            return this;
        }

        /**
         * Returns an instance of {@link PreferentialNetworkServicePreference} created from the
         * fields set on this builder.
         */
        @NonNull
        public PreferentialNetworkServicePreference  build() {
            if (mPreferentialNetworkServiceEnabled) {
                if ((mPreferentialNetworkSubLevel
                        < PREFERENTIAL_NETWORK_SUB_LEVEL_1)
                        || (mPreferentialNetworkSubLevel
                        > PREFERENTIAL_NETWORK_SUB_LEVEL_5)) {
                    throw new IllegalArgumentException("Invalid preference sub-level");
                }
            }
            return new PreferentialNetworkServicePreference(mPreferentialNetworkServiceEnabled,
                    mAllowFallback, mUidAllowList, mPreferentialNetworkSubLevel);
        }

        /**
         * Set the preference enterprise sub-level. valid values starts from
         * PREFERENTIAL_NETWORK_SUB_LEVEL_1 to
         * PREFERENTIAL_NETWORK_SUB_LEVEL_5.
         * preference sub-level is applicable only if preferenctial network service is enabled.
         * @param preferenceSubLevel  preference sub level
         * @return The builder to facilitate chaining.
         */
        @NonNull
        public PreferentialNetworkServicePreference.Builder setPreferentialNetworkSubLevel(
                int preferenceSubLevel) {
            mPreferentialNetworkSubLevel = preferenceSubLevel;
            return this;
        }
    }

    @Override
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        dest.writeBoolean(mPreferentialNetworkServiceEnabled);
        dest.writeBoolean(mAllowFallback);
        dest.writeList(mUidAllowList);
        dest.writeInt(mPreferentialNetworkSubLevel);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    public static final Creator<PreferentialNetworkServicePreference> CREATOR =
            new Creator<PreferentialNetworkServicePreference>() {
                @Override
                public PreferentialNetworkServicePreference[] newArray(int size) {
                    return new PreferentialNetworkServicePreference[size];
                }

                @Override
                public PreferentialNetworkServicePreference  createFromParcel(
                        @NonNull android.os.Parcel in) {
                    return new PreferentialNetworkServicePreference(in);
                }
            };
}
