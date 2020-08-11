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

import static android.net.NetworkCapabilities.NetCapability;

import static com.android.internal.annotations.VisibleForTesting.Visibility;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.os.PersistableBundle;
import android.util.ArraySet;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;
import com.android.server.vcn.util.PersistableBundleUtils;

import java.util.BitSet;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * This class represents a configuration for an individual tunnel within a Virtual Carrier Network.
 *
 * <p>An instance of a tunnel based on this configuration will be brought up dynamically based on
 * NetworkRequests filed by apps, or as the system-wide default network switches.
 *
 * <p>Each tunnel configuration will expose a {@link Network} with the given Telephony-related
 * capabilities (and INTERNET; see {@link Builder.addTunnelCapability}), and will select the
 * underlying network based on the required capabilities set in the configuration (see {@link
 * Builder.addRequiredUnderlyingCapability}). Allowed capabilities (as exposed by the VCN tunnel's
 * Network, or in selection of underlying networks) include:
 *
 * <ul>
 *   <li>{@link NetworkCapabilities.NET_CAPABILITY_MMS}
 *   <li>{@link NetworkCapabilities.NET_CAPABILITY_SUPL}
 *   <li>{@link NetworkCapabilities.NET_CAPABILITY_DUN}
 *   <li>{@link NetworkCapabilities.NET_CAPABILITY_FOTA}
 *   <li>{@link NetworkCapabilities.NET_CAPABILITY_IMS}
 *   <li>{@link NetworkCapabilities.NET_CAPABILITY_CBS}
 *   <li>{@link NetworkCapabilities.NET_CAPABILITY_IA}
 *   <li>{@link NetworkCapabilities.NET_CAPABILITY_RCS}
 *   <li>{@link NetworkCapabilities.NET_CAPABILITY_XCAP}
 *   <li>{@link NetworkCapabilities.NET_CAPABILITY_EIMS}
 *   <li>{@link NetworkCapabilities.NET_CAPABILITY_INTERNET}
 *   <li>{@link NetworkCapabilities.NET_CAPABILITY_MCX}
 * </ul>
 *
 * @hide
 */
public final class VcnTunnelConfig {
    private static final Set<Integer> ALLOWED_CAPABILITIES;

    static {
        Set<Integer> allowedCaps = new ArraySet<>();
        allowedCaps.add(NetworkCapabilities.NET_CAPABILITY_MMS);
        allowedCaps.add(NetworkCapabilities.NET_CAPABILITY_SUPL);
        allowedCaps.add(NetworkCapabilities.NET_CAPABILITY_DUN);
        allowedCaps.add(NetworkCapabilities.NET_CAPABILITY_FOTA);
        allowedCaps.add(NetworkCapabilities.NET_CAPABILITY_IMS);
        allowedCaps.add(NetworkCapabilities.NET_CAPABILITY_CBS);
        allowedCaps.add(NetworkCapabilities.NET_CAPABILITY_IA);
        allowedCaps.add(NetworkCapabilities.NET_CAPABILITY_RCS);
        allowedCaps.add(NetworkCapabilities.NET_CAPABILITY_XCAP);
        allowedCaps.add(NetworkCapabilities.NET_CAPABILITY_EIMS);
        allowedCaps.add(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        allowedCaps.add(NetworkCapabilities.NET_CAPABILITY_MCX);

        ALLOWED_CAPABILITIES = Collections.unmodifiableSet(allowedCaps);
    }

    private static final int DEFAULT_MAX_MTU = 1500;

    private static final String TUNNEL_CAPABILITIES_KEY = "mTunnelCapabilities";
    @NonNull private final BitSet mTunnelCapabilities;

    private static final String UNDERLYING_CAPABILITIES_KEY = "mUnderlyingCapabilities";
    @NonNull private final BitSet mUnderlyingCapabilities;

    // TODO: Add Ike/ChildSessionParams as a subclass - maybe VcnIkeTunnelConfig

    private static final String MAX_MTU_KEY = "mMaxMtu";
    private final int mMaxMtu;

    private static final String IS_METERED_KEY = "mIsMetered";
    private final boolean mIsMetered;

    private static final String IS_ROAMING_KEY = "mIsRoaming";
    private final boolean mIsRoaming;

    @VisibleForTesting(visibility = Visibility.PRIVATE)
    public VcnTunnelConfig(
            @NonNull BitSet tunnelCapabilities,
            @NonNull BitSet underlyingCapabilities,
            @IntRange(from = LinkProperties.MIN_MTU_V6) int maxMtu,
            boolean isMetered,
            boolean isRoaming) {
        mTunnelCapabilities = tunnelCapabilities;
        mUnderlyingCapabilities = underlyingCapabilities;
        mMaxMtu = maxMtu;
        mIsMetered = isMetered;
        mIsRoaming = isRoaming;

        validate();
    }

    /** @hide */
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    public VcnTunnelConfig(@NonNull PersistableBundle in) {
        final PersistableBundle tunnelCapsBundle = in.getPersistableBundle(TUNNEL_CAPABILITIES_KEY);
        final PersistableBundle underlyingCapsBundle =
                in.getPersistableBundle(UNDERLYING_CAPABILITIES_KEY);

        mTunnelCapabilities = BitSet.valueOf(PersistableBundleUtils.toByteArray(tunnelCapsBundle));
        mUnderlyingCapabilities =
                BitSet.valueOf(PersistableBundleUtils.toByteArray(underlyingCapsBundle));

        mMaxMtu = in.getInt(MAX_MTU_KEY);
        mIsMetered = in.getBoolean(IS_METERED_KEY);
        mIsRoaming = in.getBoolean(IS_ROAMING_KEY);

        validate();
    }

    private void validate() {
        Preconditions.checkArgument(
                mTunnelCapabilities != null && mTunnelCapabilities.cardinality() > 0,
                "tunnelCapabilities was null or empty");

        Preconditions.checkArgument(
                mUnderlyingCapabilities != null && mUnderlyingCapabilities.cardinality() > 0,
                "underlyingCapabilities was null or empty");

        Preconditions.checkArgument(
                mMaxMtu >= LinkProperties.MIN_MTU_V6,
                "maxMtu must be at least IPv6 min MTU (1280)");
    }

    private static void checkValidCapability(int tunnelCapability) {
        Preconditions.checkArgument(
                ALLOWED_CAPABILITIES.contains(tunnelCapability),
                "NetworkCapability " + tunnelCapability + "out of range");
    }

    /**
     * Checks if this tunnel is configured to support/expose a specific capability.
     *
     * @param tunnelCapability the capability to check for
     * @hide
     */
    public boolean hasTunnelCapability(@NetCapability int tunnelCapability) {
        checkValidCapability(tunnelCapability);

        return mTunnelCapabilities.get(tunnelCapability);
    }

    /**
     * Checks if this tunnel requires an underlying network to have the specified capability.
     *
     * @param underlyingCapability the capability to check for
     * @hide
     */
    public boolean requiresUnderlyingCapability(@NetCapability int underlyingCapability) {
        checkValidCapability(underlyingCapability);

        return mUnderlyingCapabilities.get(underlyingCapability);
    }

    /**
     * Retrieves the maximum MTU allowed for this tunnel.
     *
     * @hide
     */
    @IntRange(from = LinkProperties.MIN_MTU_V6)
    public int getMaxMtu() {
        return mMaxMtu;
    }

    /**
     * Retrieves the meteredness of the tunnel.
     *
     * @hide
     */
    public boolean isMetered() {
        return mIsMetered;
    }

    /**
     * Retrieves the roaming state of the tunnel.
     *
     * @hide
     */
    public boolean isRoaming() {
        return mIsRoaming;
    }

    /**
     * Converts this config to a PersistableBundle.
     *
     * @hide
     */
    @NonNull
    @VisibleForTesting(visibility = Visibility.PROTECTED)
    public PersistableBundle toPersistableBundle() {
        final PersistableBundle result = new PersistableBundle();

        final PersistableBundle tunnelCapsBundle =
                PersistableBundleUtils.fromByteArray(mTunnelCapabilities.toByteArray());
        final PersistableBundle underlyingCapsBundle =
                PersistableBundleUtils.fromByteArray(mUnderlyingCapabilities.toByteArray());

        result.putPersistableBundle(TUNNEL_CAPABILITIES_KEY, tunnelCapsBundle);
        result.putPersistableBundle(UNDERLYING_CAPABILITIES_KEY, underlyingCapsBundle);
        result.putInt(MAX_MTU_KEY, mMaxMtu);
        result.putBoolean(IS_METERED_KEY, mIsMetered);
        result.putBoolean(IS_ROAMING_KEY, mIsRoaming);

        return result;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mTunnelCapabilities, mUnderlyingCapabilities, mMaxMtu, mIsMetered, mIsRoaming);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (!(other instanceof VcnTunnelConfig)) {
            return false;
        }

        final VcnTunnelConfig rhs = (VcnTunnelConfig) other;
        return Objects.equals(mTunnelCapabilities, rhs.mTunnelCapabilities)
                && Objects.equals(mUnderlyingCapabilities, rhs.mUnderlyingCapabilities)
                && mMaxMtu == rhs.mMaxMtu
                && mIsMetered == rhs.mIsMetered
                && mIsRoaming == rhs.mIsRoaming;
    }

    /** This class is used to incrementally build {@link VcnTunnelConfig} objects. @hide */
    public static class Builder {
        private final BitSet mTunnelCapabilities = new BitSet();
        private final BitSet mUnderlyingCapabilities = new BitSet();

        private int mMaxMtu = DEFAULT_MAX_MTU;
        private boolean mIsMetered = true;
        private boolean mIsRoaming = false;

        /**
         * Add a capability that this VCN tunnel will support.
         *
         * <p>Only Telephony service capabilities and {@link
         * NetworkCapabilities.NET_CAPABILITY_INTERNET} allowed as a tunnel-supported capabilities:
         *
         * <ul>
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_MMS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_SUPL}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_DUN}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_FOTA}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_IMS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_CBS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_IA}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_RCS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_XCAP}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_EIMS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_INTERNET}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_MCX}
         * </ul>
         *
         * @param tunnelCapability the app-facing capability to be exposed by this VCN Tunnel (i.e.,
         *     the capabilities that this VCN Tunnel will support).
         * @return this {@link Builder} instance, for chaining
         */
        public Builder addTunnelCapability(@NetCapability int tunnelCapability) {
            checkValidCapability(tunnelCapability);

            mTunnelCapabilities.set(tunnelCapability);
            return this;
        }

        /**
         * Remove a capability that this VCN tunnel will support.
         *
         * <p>Only Telephony service capabilities and {@link
         * NetworkCapabilities.NET_CAPABILITY_INTERNET} allowed as a tunnel-supported capabilities:
         *
         * <ul>
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_MMS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_SUPL}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_DUN}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_FOTA}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_IMS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_CBS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_IA}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_RCS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_XCAP}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_EIMS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_INTERNET}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_MCX}
         * </ul>
         *
         * @param tunnelCapability the app-facing capability to not be exposed by this VCN Tunnel
         *     (i.e., the capabilities that this VCN Tunnel will support)
         * @return this {@link Builder} instance, for chaining
         */
        public Builder removeTunnelCapability(@NetCapability int tunnelCapability) {
            checkValidCapability(tunnelCapability);

            mTunnelCapabilities.clear(tunnelCapability);
            return this;
        }

        /**
         * Require a capability for Networks underlying this VCN tunnel.
         *
         * <p>Only Telephony service capabilities and {@link
         * NetworkCapabilities.NET_CAPABILITY_INTERNET} allowed as a capabilities required of
         * underlying networks:
         *
         * <ul>
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_MMS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_SUPL}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_DUN}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_FOTA}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_IMS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_CBS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_IA}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_RCS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_XCAP}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_EIMS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_INTERNET}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_MCX}
         * </ul>
         *
         * @param underlyingCapability the capability that a network MUST have in order to be an
         *     underlying network for this VCN tunnel.
         * @return this {@link Builder} instance, for chaining
         */
        public Builder addRequiredUnderlyingCapability(@NetCapability int underlyingCapability) {
            checkValidCapability(underlyingCapability);

            mUnderlyingCapabilities.set(underlyingCapability);
            return this;
        }

        /**
         * Remove a requirement of a capability for Networks underlying this VCN tunnel.
         *
         * <p>Calling this method will allow Networks that do NOT have this capability to be
         * selected as an underlying network for this VCN tunnel. However, underlying networks MAY
         * still have the removed capability.
         *
         * <p>Only Telephony service capabilities and {@link
         * NetworkCapabilities.NET_CAPABILITY_INTERNET} allowed as a capabilities required of
         * underlying networks:
         *
         * <ul>
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_MMS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_SUPL}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_DUN}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_FOTA}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_IMS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_CBS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_IA}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_RCS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_XCAP}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_EIMS}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_INTERNET}
         *   <li>{@link NetworkCapabilities.NET_CAPABILITY_MCX}
         * </ul>
         *
         * @param underlyingCapability the capability that a network DOES NOT need to have in order
         *     to be an underlying network for this VCN tunnel.
         * @return this {@link Builder} instance, for chaining
         */
        public Builder removeRequiredUnderlyingCapability(@NetCapability int underlyingCapability) {
            checkValidCapability(underlyingCapability);

            mUnderlyingCapabilities.clear(underlyingCapability);
            return this;
        }

        /**
         * Sets the maximum MTU allowed for this VCN tunnel.
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
            return new VcnTunnelConfig(
                    mTunnelCapabilities, mUnderlyingCapabilities, mMaxMtu, mIsMetered, mIsRoaming);
        }
    }
}
