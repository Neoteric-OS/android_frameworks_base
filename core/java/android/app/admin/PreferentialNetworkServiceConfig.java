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
 * {@see DevicePolicyManager#setPreferentialNetworkServiceConfig}.
 */
public final class PreferentialNetworkServiceConfig implements Parcelable {
    final boolean mIsEnabled;
    final int mNetworkId;
    final boolean mAllowFallbackToDefaultConnection;
    @Nullable final List<Integer> mUidAllowlist;

    /**
     * Preferential network identifier 1.
     */
    public static final int PREFERENTIAL_NETWORK_ID_1 = 1;

    /**
     * Preferential network identifier 2.
     */
    public static final int PREFERENTIAL_NETWORK_ID_2 = 2;

    /**
     * Preferential network identifier 3.
     */
    public static final int PREFERENTIAL_NETWORK_ID_3 = 3;

    /**
     * Preferential network identifier 4.
     */
    public static final int PREFERENTIAL_NETWORK_ID_4 = 4;

    /**
     * Preferential network identifier 5.
     */
    public static final int PREFERENTIAL_NETWORK_ID_5 = 5;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = { "PREFERENTIAL_NETWORK_ID_" }, value = {
            PREFERENTIAL_NETWORK_ID_1,
            PREFERENTIAL_NETWORK_ID_2,
            PREFERENTIAL_NETWORK_ID_3,
            PREFERENTIAL_NETWORK_ID_4,
            PREFERENTIAL_NETWORK_ID_5,
    })

    public @interface PreferentialNetworkPreferenceId {
    }

    private PreferentialNetworkServiceConfig(boolean isEnabled,
            boolean allowFallbackToDefaultConnection, List<Integer> uidAllowlist,
            @PreferentialNetworkPreferenceId int networkId) {
        mIsEnabled = isEnabled;
        mAllowFallbackToDefaultConnection = allowFallbackToDefaultConnection;
        mUidAllowlist = uidAllowlist;
        mNetworkId = networkId;
    }

    private PreferentialNetworkServiceConfig(Parcel in) {
        mIsEnabled = in.readBoolean();
        mAllowFallbackToDefaultConnection = in.readBoolean();
        mUidAllowlist = in.readArrayList(Integer.class.getClassLoader(), Integer.class);
        mNetworkId = in.readInt();
    }

    /**
     * Is the preferential network enabled.
     * @return true if enabled else false
     */
    public boolean isEnabled() {
        return mIsEnabled;
    }

    /**
     * is fallback to default network allowed. This boolean allows an enterprise admin to
     * configure whether default connection (default internet or wifi) should be used or not
     * if an enterprise connection is not available.
     * @return true if fallback is allowed, else false.
     */
    public boolean getAllowFallbackToDefaultConnection() {
        return mAllowFallbackToDefaultConnection;
    }

    /**
     * @return List of uids applicable for the profile preference.
     *      Empty list would mean that this request applies to all uids in the profile.
     */
    public @NonNull List<Integer> getUidAllowlist() {
        return new ArrayList<>(mUidAllowlist);
    }

    /**
     * @return preference enterprise identifier.
     * valid values starts from
     * PREFERENTIAL_NETWORK_ID_1 to PREFERENTIAL_NETWORK_ID_5.
     * preference identifier is applicable only if preference network service is enabled
     *
     */
    public @PreferentialNetworkPreferenceId int getNetworkId() {
        return mNetworkId;
    }

    @Override
    public String toString() {
        return "PreferentialNetworkServiceConfig{"
                + "mIsEnabled=" + isEnabled()
                + "mAllowFallbackToDefaultConnection=" + getAllowFallbackToDefaultConnection()
                + "mUidAllowlist=" + mUidAllowlist.toString()
                + "mNetworkId=" + mNetworkId
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final PreferentialNetworkServiceConfig that = (PreferentialNetworkServiceConfig) o;
        return mIsEnabled == that.mIsEnabled
                && mAllowFallbackToDefaultConnection == that.mAllowFallbackToDefaultConnection
                && mNetworkId == that.mNetworkId
                && Objects.equals(mUidAllowlist, that.mUidAllowlist);
    }

    @Override
    public int hashCode() {
        return ((Objects.hashCode(mIsEnabled) * 17)
                + (Objects.hashCode(mAllowFallbackToDefaultConnection) * 19)
                + (Objects.hashCode(mUidAllowlist) * 23)
                + mNetworkId * 29);
    }

    /**
     * Builder used to create {@link PreferentialNetworkServiceConfig} objects.
     * Specify the preferred Network preference
     */
    public static final class Builder {
        boolean mIsEnabled = false;
        int mNetworkId = PREFERENTIAL_NETWORK_ID_1;
        boolean mAllowFallbackToDefaultConnection = true;
        @Nullable List<Integer> mUidAllowlist = new ArrayList<>();

        /**
         * Constructs an empty Builder with PROFILE_NETWORK_PREFERENCE_DEFAULT profile preference
         */
        public Builder() {}

        /**
         * Set the preferential network preference.
         * See the documentation for the individual preferences for a description of the supported
         * behaviors. Default value is false.
         * @param isEnabled  the desired network preference to use, true to enable else false
         * @return The builder to facilitate chaining.
         */
        @NonNull
        public PreferentialNetworkServiceConfig.Builder setEnabled(boolean isEnabled) {
            mIsEnabled = isEnabled;
            return this;
        }

        /**
         * Set allow fallback.
         * This boolean allows an enterprise admin to configure whether
         * default connection (default internet or wifi) should be used or not if an enterprise
         * connection is not available.
         * @param allowFallbackToDefaultConnection  true if fallback is allowed else false
         * @return The builder to facilitate chaining.
         */
        @NonNull
        @SuppressLint("MissingGetterMatchingBuilder")
        public PreferentialNetworkServiceConfig.Builder setAllowFallbackToDefaultConnection(
                boolean allowFallbackToDefaultConnection) {
            mAllowFallbackToDefaultConnection = allowFallbackToDefaultConnection;
            return this;
        }

        /**
         * This is a list of uids for which profile perefence is set.
         * Empty list woul
         * d mean that this preference applies to all uids in the profile.
         * @param uids  list of uids
         * @return The builder to facilitate chaining.
         */
        @NonNull
        public PreferentialNetworkServiceConfig.Builder setUidAllowlist(
                @NonNull List<Integer> uids) {
            mUidAllowlist = new ArrayList<Integer>(uids);
            return this;
        }

        /**
         * Returns an instance of {@link PreferentialNetworkServiceConfig} created from the
         * fields set on this builder.
         */
        @NonNull
        public PreferentialNetworkServiceConfig build() {
            return new PreferentialNetworkServiceConfig(mIsEnabled,
                    mAllowFallbackToDefaultConnection, mUidAllowlist, mNetworkId);
        }

        /**
         * Set the preference enterprise identifier.
         * Valid values starts from PREFERENTIAL_NETWORK_ID_1 to
         * PREFERENTIAL_NETWORK_ID_5.
         * preference identifier is applicable only if preferential network service is enabled.
         * @param preferenceId  preference Id
         * @return The builder to facilitate chaining.
         */
        @NonNull
        public PreferentialNetworkServiceConfig.Builder setNetworkId(
                @PreferentialNetworkPreferenceId int preferenceId) {
            if ((mNetworkId < PREFERENTIAL_NETWORK_ID_1)
                    || (mNetworkId > PREFERENTIAL_NETWORK_ID_5)) {
                throw new IllegalArgumentException("Invalid preference identifier");
            }
            mNetworkId = preferenceId;
            return this;
        }
    }

    @Override
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        dest.writeBoolean(mIsEnabled);
        dest.writeBoolean(mAllowFallbackToDefaultConnection);
        dest.writeList(mUidAllowlist);
        dest.writeInt(mNetworkId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    public static final Creator<PreferentialNetworkServiceConfig> CREATOR =
            new Creator<PreferentialNetworkServiceConfig>() {
                @Override
                public PreferentialNetworkServiceConfig[] newArray(int size) {
                    return new PreferentialNetworkServiceConfig[size];
                }

                @Override
                public PreferentialNetworkServiceConfig createFromParcel(
                        @NonNull android.os.Parcel in) {
                    return new PreferentialNetworkServiceConfig(in);
                }
            };
}
