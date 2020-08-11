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
package android.net.vcn;

import static com.android.internal.annotations.VisibleForTesting.Visibility;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.LinkProperties;
import android.os.PersistableBundle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.Arrays;
import java.util.Objects;

/**
 * This class represents a configuration for an individual tunnel within a Virtual Carrier Network
 *
 * @hide
 */
public final class VcnTunnelConfig {
    private static final int DEFAULT_MAX_MTU = 1500;

    private static final String TUNNEL_CAPABILITIES_KEY = "mTunnelCapabilities";
    @NonNull private final int[] mTunnelCapabilities;
    // TODO: Add Ike/ChildSessionParams once they are parcelable, preferably as a subclass

    private static final String MAX_MTU_KEY = "mMaxMtu";
    private final int mMaxMtu;

    private static final String IS_METERED_KEY = "mIsMetered";
    private final boolean mIsMetered;

    private static final String IS_ROAMING_KEY = "mIsRoaming";
    private final boolean mIsRoaming;

    @VisibleForTesting(visibility = Visibility.PRIVATE)
    public VcnTunnelConfig(
            @NonNull int[] tunnelCapabilities,
            @IntRange(from = LinkProperties.MIN_MTU_V6) int maxMtu,
            boolean isMetered,
            boolean isRoaming) {
        mTunnelCapabilities = tunnelCapabilities;
        mMaxMtu = maxMtu;
        mIsMetered = isMetered;
        mIsRoaming = isRoaming;

        validate();
    }

    /** @hide */
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    public VcnTunnelConfig(@NonNull PersistableBundle in) {
        mTunnelCapabilities = in.getIntArray(TUNNEL_CAPABILITIES_KEY);
        mMaxMtu = in.getInt(MAX_MTU_KEY);
        mIsMetered = in.getBoolean(IS_METERED_KEY);
        mIsRoaming = in.getBoolean(IS_ROAMING_KEY);

        validate();
    }

    private void validate() {
        Preconditions.checkArgument(
                mTunnelCapabilities != null && mTunnelCapabilities.length > 0,
                "tunnelCapabilities was null or empty");

        Preconditions.checkArgument(
                mMaxMtu >= LinkProperties.MIN_MTU_V6,
                "maxMtu must be at least IPv6 min MTU (1280)");
    }

    /**
     * Retrieves the (app-facing) capabilities exposed by the tunnel
     *
     * @hide
     */
    @NonNull
    public int[] getTunnelCapabilities() {
        return Arrays.copyOf(mTunnelCapabilities, mTunnelCapabilities.length);
    }

    /**
     * Retrieves the maximum MTU allowed for this tunnel
     *
     * @hide
     */
    @IntRange(from = LinkProperties.MIN_MTU_V6)
    public int getMaxMtu() {
        return mMaxMtu;
    }

    /**
     * Retrieves the meteredness of the tunnel
     *
     * @hide
     */
    public boolean isMetered() {
        return mIsMetered;
    }

    /**
     * Retrieves the roaming state of the tunnel
     *
     * @hide
     */
    public boolean isRoaming() {
        return mIsRoaming;
    }

    /**
     * Converts this config to a persistable bundle.
     *
     * @hide
     */
    @NonNull
    @VisibleForTesting(visibility = Visibility.PROTECTED)
    public PersistableBundle toPersistableBundle() {
        final PersistableBundle result = new PersistableBundle();

        result.putIntArray(TUNNEL_CAPABILITIES_KEY, mTunnelCapabilities);
        result.putInt(MAX_MTU_KEY, mMaxMtu);
        result.putBoolean(IS_METERED_KEY, mIsMetered);
        result.putBoolean(IS_ROAMING_KEY, mIsRoaming);

        return result;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(mTunnelCapabilities), mMaxMtu, mIsMetered, mIsRoaming);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (!(other instanceof VcnTunnelConfig)) {
            return false;
        }

        final VcnTunnelConfig rhs = (VcnTunnelConfig) other;
        return Arrays.equals(mTunnelCapabilities, rhs.mTunnelCapabilities)
                && mMaxMtu == rhs.mMaxMtu
                && mIsMetered == rhs.mIsMetered
                && mIsRoaming == rhs.mIsRoaming;
    }

    /** This class is used to incrementally build {@link VcnTunnelConfig} objects */
    public static class Builder {
        private final int[] mTunnelCapabilities;

        private int mMaxMtu = DEFAULT_MAX_MTU;
        private boolean mIsMetered = true;
        private boolean mIsRoaming = false;

        /**
         * Construct an instance of this builder
         *
         * @param tunnelCapabilities the app-facing capabilities exposed by this VCN Tunnel (i.e.,
         *     the capabilities that this VCN Tunnel will support)
         */
        public Builder(@NonNull int[] tunnelCapabilities) {
            Preconditions.checkArgument(
                    tunnelCapabilities != null && tunnelCapabilities.length > 0,
                    "tunnelCapabilities was null or empty");

            mTunnelCapabilities = tunnelCapabilities;
        }

        /**
         * Sets the maximum MTU allowed for this VCN tunnel
         *
         * <p>The system may reduce the MTU below the maximum specified based on signals such as the
         * MTU of the underlying networks (and adjusted for tunnel overhead).
         *
         * @param maxMtu the maximum MTU allowed for this tunnel. Must be greater than the IPv6
         *     minimum MTU of 1280. Defaults to 1500.
         * @return this {@link Builder} instance, for chaining
         */
        @NonNull
        public Builder setMaxMtu(@IntRange(from = LinkProperties.MIN_MTU_V6) int maxMtu) {
            Preconditions.checkArgument(
                    maxMtu >= LinkProperties.MIN_MTU_V6,
                    "maxMtu must be at least IPv6 min MTU (1280)");

            mMaxMtu = maxMtu;
            return this;
        }

        /**
         * Sets whether this VCN Tunnel should be considered metered
         *
         * @param isMetered whether or not this tunnel should have the {@link
         *     NetworkCapabilities.NET_CAPABILITY_NOT_METERED}. A value of {@code true} indicates
         *     the Network is metered, and the resultant Network will NOT have the {@link
         *     NetworkCapabilities.NET_CAPABILITY_NOT_METERED}. Defaults to {@code true}.
         * @return this {@link Builder} instance, for chaining
         */
        @NonNull
        public Builder setMetered(boolean isMetered) {
            mIsMetered = isMetered;
            return this;
        }

        /**
         * Sets whether this VCN Tunnel should be considered roaming
         *
         * @param isRoaming whether or not this tunnel should have the {@link
         *     NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING}. A value of {@code true} indicates
         *     the Network is roaming, and the resultant Network will NOT have the {@link
         *     NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING}. Defaults to {@code false}.
         * @return this {@link Builder} instance, for chaining
         */
        @NonNull
        public Builder setRoaming(boolean isRoaming) {
            mIsRoaming = isRoaming;
            return this;
        }

        /**
         * Builds and validates the VcnTunnelConfig
         *
         * @return an immutable VcnTunnelConfig instance
         */
        @NonNull
        public VcnTunnelConfig build() {
            return new VcnTunnelConfig(mTunnelCapabilities, mMaxMtu, mIsMetered, mIsRoaming);
        }
    }
}
