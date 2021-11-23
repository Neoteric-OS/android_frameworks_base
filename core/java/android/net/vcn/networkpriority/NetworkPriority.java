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
package android.net.vcn.networkpriority;

import static com.android.internal.annotations.VisibleForTesting.Visibility;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.PersistableBundle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

// TODO: Add documents
/** @hide */
public abstract class NetworkPriority {
    /** @hide */
    protected static final int NETWORK_PRIORITY_TYPE_WIFI = 1;
    /** @hide */
    protected static final int NETWORK_PRIORITY_TYPE_CELL = 2;

    /** Denotes that network quality needs to be OK */
    public static final int NETWORK_QUALITY_OK = 0;
    /** Denotes that any network quality is acceptable */
    public static final int NETWORK_QUALITY_ANY = Integer.MAX_VALUE;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({NETWORK_QUALITY_OK, NETWORK_QUALITY_ANY})
    public @interface NetworkQuality {}

    private static final String NETWORK_PRIORITY_TYPE_KEY = "mNetworkPriorityType";
    private final int mNetworkPriorityType;

    protected static final String NETWORK_QUALITY_KEY = "mNetworkQuality";
    private final int mNetworkQuality;

    protected static final String ALLOW_METERED_KEY = "mAllowMetered";
    private final boolean mAllowMetered;

    protected NetworkPriority(int networkPriorityType, int networkQuality, boolean allowMetered) {
        mNetworkPriorityType = networkPriorityType;
        mNetworkQuality = networkQuality;
        mAllowMetered = allowMetered;
        validate();
    }

    protected void validate() {
        Builder.validateNetworkQuality(mNetworkQuality);
    }

    /** @hide */
    @NonNull
    @VisibleForTesting(visibility = Visibility.PROTECTED)
    public static NetworkPriority fromPersistableBundle(@NonNull PersistableBundle in) {
        Objects.requireNonNull(in, "PersistableBundle is null");

        final int networkPriorityType = in.getInt(NETWORK_PRIORITY_TYPE_KEY);
        switch (networkPriorityType) {
            case NETWORK_PRIORITY_TYPE_WIFI:
                return WifiNetworkPriority.fromPersistableBundle(in);
            case NETWORK_PRIORITY_TYPE_CELL:
                throw new UnsupportedOperationException("Not implemented");
            default:
                throw new IllegalArgumentException(
                        "Invalid networkPriorityType:" + networkPriorityType);
        }
    }

    /** @hide */
    @NonNull
    PersistableBundle toPersistableBundle() {
        final PersistableBundle result = new PersistableBundle();

        result.putInt(NETWORK_PRIORITY_TYPE_KEY, mNetworkPriorityType);
        result.putInt(NETWORK_QUALITY_KEY, mNetworkQuality);
        result.putBoolean(ALLOW_METERED_KEY, mAllowMetered);

        return result;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mNetworkPriorityType, mNetworkQuality, mAllowMetered);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (!(other instanceof NetworkPriority)) {
            return false;
        }

        final NetworkPriority rhs = (NetworkPriority) other;
        return mNetworkPriorityType == rhs.mNetworkPriorityType
                && mNetworkQuality == rhs.mNetworkQuality
                && mAllowMetered == rhs.mAllowMetered;
    }

    /** Retrieve the required network quality. */
    @NetworkQuality
    public int getNetworkQuality() {
        return mNetworkQuality;
    }

    /** Retrieve if a metered network is allowed. */
    public boolean allowMetered() {
        return mAllowMetered;
    }

    /**
     * This class is used to incrementally build NetworkPriority objects.
     *
     * @param <T> The subclass to be built.
     */
    public abstract static class Builder<T extends Builder<T>> {
        /** @hide */
        protected int mNetworkQuality = NETWORK_QUALITY_ANY;
        /** @hide */
        protected boolean mAllowMetered = false;

        /** @hide */
        protected Builder() {}

        /**
         * Set the required network quality.
         *
         * @param networkQuality the required network quality. Defaults to NETWORK_QUALITY_ANY
         */
        @NonNull
        public T setNetworkQuality(@NetworkQuality int networkQuality) {
            validateNetworkQuality(networkQuality);
            mNetworkQuality = networkQuality;
            return self();
        }

        private static void validateNetworkQuality(int networkQuality) {
            Preconditions.checkArgument(
                    networkQuality == NETWORK_QUALITY_ANY || networkQuality == NETWORK_QUALITY_OK,
                    "Invalid networkQuality:" + networkQuality);
        }

        /**
         * Set if a metered network is allowed.
         *
         * @param allowMetered the flag to indicate if a metered network is allowed, defaults to
         *     {@code false}
         */
        @NonNull
        public T setAllowMetered(boolean allowMetered) {
            mAllowMetered = allowMetered;
            return self();
        }

        /** @hide */
        abstract T self();
    }
}
