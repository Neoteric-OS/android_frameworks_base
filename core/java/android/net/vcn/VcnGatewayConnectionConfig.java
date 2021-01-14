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
import android.annotation.SystemApi;
import android.annotation.SystemApi.Client;
import android.net.NetworkCapabilities;
import android.os.PersistableBundle;
import android.util.ArraySet;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;
import com.android.server.vcn.util.PersistableBundleUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * This class represents a configuration for a connection to a Virtual Carrier Network gateway.
 *
 * <p>Each VcnGatewayConnectionConfig represents a single logical connection to a carrier gateway,
 * and may provide one or more telephony services (as represented by network capabilities). Each
 * gateway is expected to provide mobility for a given session as the device roams across {@link
 * Network}s.
 *
 * <p>A VCN connection based on this configuration will be brought up dynamically based on device
 * settings, and filed NetworkRequests. Underlying networks will be selected based on the services
 * required by this configuration (as represented by network capabilities), and must be part of the
 * subscription group under which this configuration is registered (see {@link
 * VcnManager#setVcnConfig}).
 *
 * <p>Services that can be provided by a VCN network, or required for underlying networks are
 * limited to services provided by cellular networks:
 *
 * <ul>
 *   <li>{@link android.net.NetworkCapabilities.NET_CAPABILITY_MMS}
 *   <li>{@link android.net.NetworkCapabilities.NET_CAPABILITY_SUPL}
 *   <li>{@link android.net.NetworkCapabilities.NET_CAPABILITY_DUN}
 *   <li>{@link android.net.NetworkCapabilities.NET_CAPABILITY_FOTA}
 *   <li>{@link android.net.NetworkCapabilities.NET_CAPABILITY_IMS}
 *   <li>{@link android.net.NetworkCapabilities.NET_CAPABILITY_CBS}
 *   <li>{@link android.net.NetworkCapabilities.NET_CAPABILITY_IA}
 *   <li>{@link android.net.NetworkCapabilities.NET_CAPABILITY_RCS}
 *   <li>{@link android.net.NetworkCapabilities.NET_CAPABILITY_XCAP}
 *   <li>{@link android.net.NetworkCapabilities.NET_CAPABILITY_EIMS}
 *   <li>{@link android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET}
 *   <li>{@link android.net.NetworkCapabilities.NET_CAPABILITY_MCX}
 * </ul>
 *
 * <p>The meteredness and roaming of the VCN {@link Network} will be determined by that of the
 * underlying Network(s).
 *
 * <p>This class is the base class that contains common information of a VCN gateway connection
 * configuration. Please see android.net.ipsec.ike.vcn.VcnGatewayConnectionConfig for subclass.
 */
public abstract class VcnGatewayConnectionConfig {
    // TODO: Use MIN_MTU_V6 once it is public, @hide
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    static final int MIN_MTU_V6 = 1280;

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

    /** @hide */
    @SystemApi(client = Client.MODULE_LIBRARIES)
    public static int getDefaultMaxMtu() {
        return DEFAULT_MAX_MTU;
    }

    /**
     * The maximum number of retry intervals that may be specified.
     *
     * <p>Limited to ensure an upper bound on config sizes.
     */
    private static final int MAX_RETRY_INTERVAL_COUNT = 10;

    /**
     * The minimum allowable repeating retry interval
     *
     * <p>To ensure the device is not constantly being woken up, this retry interval MUST be greater
     * than this value.
     *
     * @see {@link Builder#setRetryInterval()}
     */
    private static final long MINIMUM_REPEATING_RETRY_INTERVAL_MS = TimeUnit.MINUTES.toMillis(15);

    private static final long[] DEFAULT_RETRY_INTERVALS_MS =
            new long[] {
                TimeUnit.SECONDS.toMillis(1),
                TimeUnit.SECONDS.toMillis(2),
                TimeUnit.SECONDS.toMillis(5),
                TimeUnit.SECONDS.toMillis(30),
                TimeUnit.MINUTES.toMillis(1),
                TimeUnit.MINUTES.toMillis(5),
                TimeUnit.MINUTES.toMillis(15)
            };

    /** @hide */
    @SystemApi(client = Client.MODULE_LIBRARIES)
    @NonNull
    public static long[] getDefaultRetryIntervalsMs() {
        return DEFAULT_RETRY_INTERVALS_MS;
    }

    private static final String EXPOSED_CAPABILITIES_KEY = "mExposedCapabilities";
    @NonNull private final Set<Integer> mExposedCapabilities;

    private static final String UNDERLYING_CAPABILITIES_KEY = "mUnderlyingCapabilities";
    @NonNull private final Set<Integer> mUnderlyingCapabilities;

    // TODO: Add Ike/ChildSessionParams as a subclass - maybe VcnIkeGatewayConnectionConfig

    private static final String MAX_MTU_KEY = "mMaxMtu";
    private final int mMaxMtu;

    private static final String RETRY_INTERVAL_MS_KEY = "mRetryIntervalsMs";
    @NonNull private final long[] mRetryIntervalsMs;

    /** @hide */
    @SystemApi(client = Client.MODULE_LIBRARIES)
    public VcnGatewayConnectionConfig(
            @NonNull Set<Integer> exposedCapabilities,
            @NonNull Set<Integer> underlyingCapabilities,
            @NonNull long[] retryIntervalsMs,
            @IntRange(from = MIN_MTU_V6) int maxMtu) {
        mExposedCapabilities = exposedCapabilities;
        mUnderlyingCapabilities = underlyingCapabilities;
        mRetryIntervalsMs = retryIntervalsMs;
        mMaxMtu = maxMtu;

        validate();
    }

    /** @hide */
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    public static VcnGatewayConnectionConfig fromPersistableBundle(@NonNull PersistableBundle in) {
        // TODO: build VcnIkeGatewayConnectionConfig from PersistableBundle
        throw new UnsupportedOperationException("Not implemented");
    }

    private void validate() {
        Preconditions.checkArgument(
                mExposedCapabilities != null && !mExposedCapabilities.isEmpty(),
                "exposedCapsBundle was null or empty");
        for (Integer cap : getAllExposedCapabilities()) {
            checkValidCapability(cap);
        }

        Preconditions.checkArgument(
                mUnderlyingCapabilities != null && !mUnderlyingCapabilities.isEmpty(),
                "underlyingCapabilities was null or empty");
        for (Integer cap : getAllUnderlyingCapabilities()) {
            checkValidCapability(cap);
        }

        Objects.requireNonNull(mRetryIntervalsMs, "retryIntervalsMs was null");
        validateRetryInterval(mRetryIntervalsMs);

        validateMaxMtu(mMaxMtu);
    }

    /** @hide */
    @SystemApi(client = Client.MODULE_LIBRARIES)
    public static void checkValidCapability(int capability) {
        Preconditions.checkArgument(
                ALLOWED_CAPABILITIES.contains(capability),
                "NetworkCapability " + capability + "out of range");
    }

    /** @hide */
    @SystemApi(client = Client.MODULE_LIBRARIES)
    public static void validateRetryInterval(@Nullable long[] retryIntervalsMs) {
        Preconditions.checkArgument(
                retryIntervalsMs != null
                        && retryIntervalsMs.length > 0
                        && retryIntervalsMs.length <= MAX_RETRY_INTERVAL_COUNT,
                "retryIntervalsMs was null, empty or exceed max interval count");

        final long repeatingInterval = retryIntervalsMs[retryIntervalsMs.length - 1];
        if (repeatingInterval < MINIMUM_REPEATING_RETRY_INTERVAL_MS) {
            throw new IllegalArgumentException(
                    "Repeating retry interval was too short, must be a minimum of 15 minutes: "
                            + repeatingInterval);
        }
    }

    /** @hide */
    @SystemApi(client = Client.MODULE_LIBRARIES)
    public static void validateMaxMtu(@IntRange(from = MIN_MTU_V6) int maxMtu) {
        Preconditions.checkArgument(
                maxMtu >= MIN_MTU_V6, "maxMtu must be at least IPv6 min MTU (1280)");
    }

    /**
     * Returns all exposed capabilities.
     *
     * @hide
     */
    @NonNull
    public Set<Integer> getAllExposedCapabilities() {
        return Collections.unmodifiableSet(mExposedCapabilities);
    }

    /**
     * Checks if this config is configured to support/expose a specific capability.
     *
     * @param capability the capability to check for
     * @hide
     */
    public boolean hasExposedCapability(int capability) {
        checkValidCapability(capability);

        return mExposedCapabilities.contains(capability);
    }

    /**
     * Returns all capabilities required of underlying networks.
     *
     * @hide
     */
    @NonNull
    public Set<Integer> getAllUnderlyingCapabilities() {
        return Collections.unmodifiableSet(mUnderlyingCapabilities);
    }

    /**
     * Checks if this config requires an underlying network to have the specified capability.
     *
     * @param capability the capability to check for
     * @hide
     */
    public boolean requiresUnderlyingCapability(int capability) {
        checkValidCapability(capability);

        return mUnderlyingCapabilities.contains(capability);
    }

    /**
     * Retrieves the configured retry intervals.
     *
     * @hide
     */
    @NonNull
    public long[] getRetryIntervalsMs() {
        return Arrays.copyOf(mRetryIntervalsMs, mRetryIntervalsMs.length);
    }

    /** Retrieves the maximum MTU allowed for this Gateway Connection. @hide */
    @IntRange(from = MIN_MTU_V6)
    public int getMaxMtu() {
        return mMaxMtu;
    }

    /**
     * Converts this config to a PersistableBundle.
     *
     * @hide
     */
    @NonNull
    @VisibleForTesting(visibility = Visibility.PROTECTED)
    @SystemApi(client = Client.MODULE_LIBRARIES)
    public PersistableBundle toPersistableBundle() {
        final PersistableBundle result = new PersistableBundle();

        final PersistableBundle exposedCapsBundle =
                PersistableBundleUtils.fromList(
                        new ArrayList<>(mExposedCapabilities),
                        PersistableBundleUtils.INTEGER_SERIALIZER);
        final PersistableBundle underlyingCapsBundle =
                PersistableBundleUtils.fromList(
                        new ArrayList<>(mUnderlyingCapabilities),
                        PersistableBundleUtils.INTEGER_SERIALIZER);

        result.putPersistableBundle(EXPOSED_CAPABILITIES_KEY, exposedCapsBundle);
        result.putPersistableBundle(UNDERLYING_CAPABILITIES_KEY, underlyingCapsBundle);
        result.putLongArray(RETRY_INTERVAL_MS_KEY, mRetryIntervalsMs);
        result.putInt(MAX_MTU_KEY, mMaxMtu);

        return result;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mExposedCapabilities,
                mUnderlyingCapabilities,
                Arrays.hashCode(mRetryIntervalsMs),
                mMaxMtu);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (!(other instanceof VcnGatewayConnectionConfig)) {
            return false;
        }

        final VcnGatewayConnectionConfig rhs = (VcnGatewayConnectionConfig) other;
        return mExposedCapabilities.equals(rhs.mExposedCapabilities)
                && mUnderlyingCapabilities.equals(rhs.mUnderlyingCapabilities)
                && Arrays.equals(mRetryIntervalsMs, rhs.mRetryIntervalsMs)
                && mMaxMtu == rhs.mMaxMtu;
    }
}
