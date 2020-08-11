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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
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
 * This class represents a configuration for a Virtual Carrier Network.
 *
 * <p>Each {@link VcnGatewayConnectionConfig} instance added represents a connection that will be
 * brought up on demand based on app-requested {@link Network}s.
 *
 * @hide
 */
public final class VcnConfig implements Parcelable {
    @NonNull private static final String TAG = VcnConfig.class.getSimpleName();

    private static final int MAX_RETRY_INTERVAL_COUNT = 10;
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

    private static final String RETRY_INTERVAL_MS_KEY = "mRetryIntervalsMs";
    @NonNull private final long[] mRetryIntervalsMs;

    private static final String TUNNEL_CONFIGS_KEY = "mTunnelConfigs";
    @NonNull private final Set<VcnGatewayConnectionConfig> mTunnelConfigs;

    private VcnConfig(
            @NonNull long[] retryIntervalsMs,
            @NonNull Set<VcnGatewayConnectionConfig> tunnelConfigs) {
        mRetryIntervalsMs = Objects.requireNonNull(retryIntervalsMs, "retryIntervalsMs was null");
        mTunnelConfigs = Collections.unmodifiableSet(tunnelConfigs);

        validate();
    }

    /**
     * Deserializes a VcnConfig from a PersistableBundle.
     *
     * @hide
     */
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    public VcnConfig(@NonNull PersistableBundle in) {
        mRetryIntervalsMs = in.getLongArray(RETRY_INTERVAL_MS_KEY);

        final PersistableBundle tunnelConfigsBundle = in.getPersistableBundle(TUNNEL_CONFIGS_KEY);
        mTunnelConfigs =
                new ArraySet<>(
                        PersistableBundleUtils.toList(
                                tunnelConfigsBundle, VcnGatewayConnectionConfig::new));

        validate();
    }

    private void validate() {
        validateRetryInterval(mRetryIntervalsMs);
        Preconditions.checkCollectionNotEmpty(mTunnelConfigs, "tunnelConfigs");
    }

    private static void validateRetryInterval(@Nullable long[] retryIntervalsMs) {
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

    /**
     * Retrieves the configured retry intervals.
     *
     * @hide
     */
    @NonNull
    public long[] getRetryIntervalsMs() {
        return Arrays.copyOf(mRetryIntervalsMs, mRetryIntervalsMs.length);
    }

    /**
     * Retrieves the set of configured tunnels.
     *
     * @hide
     */
    @NonNull
    public Set<VcnGatewayConnectionConfig> getTunnelConfigs() {
        return Collections.unmodifiableSet(mTunnelConfigs);
    }

    /**
     * Serializes this object to a PersistableBundle.
     *
     * @hide
     */
    @NonNull
    public PersistableBundle toPersistableBundle() {
        final PersistableBundle result = new PersistableBundle();

        result.putLongArray(RETRY_INTERVAL_MS_KEY, mRetryIntervalsMs);

        final PersistableBundle tunnelConfigsBundle =
                PersistableBundleUtils.fromList(
                        new ArrayList<>(mTunnelConfigs),
                        VcnGatewayConnectionConfig::toPersistableBundle);
        result.putPersistableBundle(TUNNEL_CONFIGS_KEY, tunnelConfigsBundle);

        return result;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(mRetryIntervalsMs), mTunnelConfigs);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (!(other instanceof VcnConfig)) {
            return false;
        }

        final VcnConfig rhs = (VcnConfig) other;
        return Arrays.equals(mRetryIntervalsMs, rhs.mRetryIntervalsMs)
                && mTunnelConfigs.equals(rhs.mTunnelConfigs);
    }

    // Parcelable methods

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(toPersistableBundle(), flags);
    }

    @NonNull
    public static final Parcelable.Creator<VcnConfig> CREATOR =
            new Parcelable.Creator<VcnConfig>() {
                @NonNull
                public VcnConfig createFromParcel(Parcel in) {
                    return new VcnConfig((PersistableBundle) in.readParcelable(null));
                }

                @NonNull
                public VcnConfig[] newArray(int size) {
                    return new VcnConfig[size];
                }
            };

    /** This class is used to incrementally build {@link VcnConfig} objects. */
    public static class Builder {
        @NonNull private final Set<VcnGatewayConnectionConfig> mTunnelConfigs = new ArraySet<>();
        @NonNull private long[] mRetryIntervalsMs = DEFAULT_RETRY_INTERVALS_MS;

        /**
         * Set the retry interval between VCN establishment attempts upon successive failures.
         *
         * <p>The last retry interval will be repeated until safe mode is entered, or a connection
         * is successfully established, at which point the retry timers will be reset. For power
         * reasons, the last (repeated) retry interval MUST be at least 15 minutes.
         *
         * <p>Retry intervals MAY be subject to system power saving modes. That is to say that if
         * the system enters a power saving mode, the retry may not occur until the device leaves
         * the specified power saving mode.
         *
         * <p>Each tunnel as defined by {@link VcnGatewayConnectionConfig} will retry separately,
         * but if safe mode is enabled, all tunnels will be disabled.
         *
         * @param retryIntervalsMs the millisecond intervals after which the VCN will attempt to
         *     retry a session initiation. At least one, but no more than 10 retry intervals must be
         *     provided, with the last (repeating) retry interval at least 15 minutes between
         *     retries.
         * @return this {@link Builder} instance, for chaining.
         * @see VcnManager
         */
        @NonNull
        public Builder setRetryInterval(@NonNull long[] retryIntervalsMs) {
            validateRetryInterval(retryIntervalsMs);

            mRetryIntervalsMs = retryIntervalsMs;
            return this;
        }

        /**
         * Adds a {@link VcnGatewayConnectionConfig} with the configuration for an individual
         * tunnel.
         *
         * @param tunnelConfig the configuration for an individual tunnel.
         * @return this {@link Builder} instance, for chaining.
         */
        @NonNull
        public Builder addTunnelConfig(@NonNull VcnGatewayConnectionConfig tunnelConfig) {
            Objects.requireNonNull(tunnelConfig, "tunnelConfig was null");

            mTunnelConfigs.add(tunnelConfig);
            return this;
        }

        /**
         * Builds and validates the VcnConfig.
         *
         * @return an immutable VcnConfig instance.
         */
        @NonNull
        public VcnConfig build() {
            return new VcnConfig(mRetryIntervalsMs, mTunnelConfigs);
        }
    }
}
