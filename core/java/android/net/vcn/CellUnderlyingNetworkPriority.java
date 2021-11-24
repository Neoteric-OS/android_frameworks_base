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

import static com.android.internal.annotations.VisibleForTesting.Visibility;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.PersistableBundle;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

// TODO: Add documents
/** @hide */
public final class CellUnderlyingNetworkPriority extends VcnUnderlyingNetworkPriority {
    private static final String ALLOWED_NETWORK_PLMN_IDS_KEY = "mAllowedNetworkPlmnIds";
    @NonNull private final Set<String> mAllowedNetworkPlmnIds = new HashSet<>();

    private static final String ALLOWED_SPECIFIC_CARRIER_IDS_KEY = "mAllowedSpecificCarrierIds";
    @NonNull private final Set<Integer> mAllowedSpecificCarrierIds = new HashSet<>();

    private static final String ALLOW_ROAMING_KEY = "mAllowRoaming";
    private final boolean mAllowRoaming;

    private static final String REQUIRE_OPPORTUNISTIC_KEY = "mRequireOpportunistic";
    private final boolean mRequireOpportunistic;

    private CellUnderlyingNetworkPriority(
            int networkQuality,
            boolean allowMetered,
            Set<String> allowedNetworkPlmnIds,
            Set<Integer> allowedSpecificCarrierIds,
            boolean allowRoaming,
            boolean requireOpportunistic) {
        super(NETWORK_PRIORITY_TYPE_CELL, networkQuality, allowMetered);
        mAllowedNetworkPlmnIds.addAll(allowedNetworkPlmnIds);
        mAllowedSpecificCarrierIds.addAll(allowedSpecificCarrierIds);
        mAllowRoaming = allowRoaming;
        mRequireOpportunistic = requireOpportunistic;

        validate();
    }

    /** @hide */
    @Override
    protected void validate() {
        super.validate();
        Builder.validatePlmnIds(mAllowedNetworkPlmnIds);
        Objects.requireNonNull(mAllowedSpecificCarrierIds, "allowedCarrierIds is null");
    }

    /** @hide */
    @NonNull
    @VisibleForTesting(visibility = Visibility.PROTECTED)
    public static CellUnderlyingNetworkPriority fromPersistableBundle(
            @NonNull PersistableBundle in) {
        Objects.requireNonNull(in, "PersistableBundle is null");

        final int networkQuality = in.getInt(NETWORK_QUALITY_KEY);
        final boolean allowMetered = in.getBoolean(ALLOW_METERED_KEY);

        final String[] allowedNetworkPlmnIdsArray = in.getStringArray(ALLOWED_NETWORK_PLMN_IDS_KEY);
        Objects.requireNonNull(allowedNetworkPlmnIdsArray, "allowedNetworkPlmnIdsArray is null");
        final Set<String> allowedNetworkPlmnIds =
                new HashSet<String>(Arrays.asList(allowedNetworkPlmnIdsArray));

        final int[] allowedSpecificCarrierIdsArray =
                in.getIntArray(ALLOWED_SPECIFIC_CARRIER_IDS_KEY);
        Objects.requireNonNull(
                allowedSpecificCarrierIdsArray, "allowedSpecificCarrierIdsArray is null");
        final Set<Integer> allowedSpecificCarrierIds = new HashSet<Integer>();
        for (int id : allowedSpecificCarrierIdsArray) {
            allowedSpecificCarrierIds.add(id);
        }

        final boolean allowRoaming = in.getBoolean(ALLOW_ROAMING_KEY);
        final boolean requireOpportunistic = in.getBoolean(REQUIRE_OPPORTUNISTIC_KEY);

        return new CellUnderlyingNetworkPriority(
                networkQuality,
                allowMetered,
                allowedNetworkPlmnIds,
                allowedSpecificCarrierIds,
                allowRoaming,
                requireOpportunistic);
    }

    /** @hide */
    @Override
    @NonNull
    @VisibleForTesting(visibility = Visibility.PROTECTED)
    public PersistableBundle toPersistableBundle() {
        final PersistableBundle result = super.toPersistableBundle();

        result.putStringArray(
                ALLOWED_NETWORK_PLMN_IDS_KEY, mAllowedNetworkPlmnIds.toArray(new String[0]));
        result.putIntArray(
                ALLOWED_SPECIFIC_CARRIER_IDS_KEY,
                mAllowedSpecificCarrierIds.stream().mapToInt(i -> i).toArray());
        result.putBoolean(ALLOW_ROAMING_KEY, mAllowRoaming);
        result.putBoolean(REQUIRE_OPPORTUNISTIC_KEY, mRequireOpportunistic);

        return result;
    }

    /** Retrieve the allowed PLMN IDs, or an empty set if any PLMN ID is acceptable. */
    @NonNull
    public Set<String> getAllowedPlmnIds() {
        return Collections.unmodifiableSet(mAllowedNetworkPlmnIds);
    }

    /**
     * Retrieve the allowed specific carrier IDs, or an empty set if any specific carrier ID is
     * acceptable.
     */
    @NonNull
    public Set<Integer> getAllowedSpecificCarrierIds() {
        return Collections.unmodifiableSet(mAllowedSpecificCarrierIds);
    }

    /** Return if roaming is allowed. */
    public boolean allowRoaming() {
        return mAllowRoaming;
    }

    /** Return if requiring an opportunistic network. */
    public boolean requireOpportunistic() {
        return mRequireOpportunistic;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                mAllowedNetworkPlmnIds,
                mAllowedSpecificCarrierIds,
                mAllowRoaming,
                mRequireOpportunistic);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (!super.equals(other)) {
            return false;
        }

        if (!(other instanceof CellUnderlyingNetworkPriority)) {
            return false;
        }

        final CellUnderlyingNetworkPriority rhs = (CellUnderlyingNetworkPriority) other;
        return Objects.equals(mAllowedNetworkPlmnIds, rhs.mAllowedNetworkPlmnIds)
                && Objects.equals(mAllowedSpecificCarrierIds, rhs.mAllowedSpecificCarrierIds)
                && mAllowRoaming == rhs.mAllowRoaming
                && mRequireOpportunistic == rhs.mRequireOpportunistic;
    }

    /** This class is used to incrementally build WifiNetworkPriority objects. */
    public static class Builder extends VcnUnderlyingNetworkPriority.Builder<Builder> {
        @NonNull private final Set<String> mAllowedNetworkPlmnIds = new HashSet<>();
        @NonNull private final Set<Integer> mAllowedSpecificCarrierIds = new HashSet<>();

        private boolean mAllowRoaming = false;
        private boolean mRequireOpportunistic = false;

        /** Construct a Builder object. */
        public Builder() {}

        /**
         * Set allowed PLMN IDs.
         *
         * @param allowedNetworkPlmnIds the allowed PLMN IDs in String, or an empty set if any PLMN
         *     ID is acceptable. A valid PLMN is a concatenation of MNC and MCC, and thus consists
         *     of 5 or 6 decimal digits. See {@link SubscriptionInfo#getMccString()} and {@link
         *     SubscriptionInfo#getMncString()}.
         */
        @NonNull
        public Builder setAllowedPlmnIds(@NonNull Set<String> allowedNetworkPlmnIds) {
            validatePlmnIds(allowedNetworkPlmnIds);

            mAllowedNetworkPlmnIds.clear();
            mAllowedNetworkPlmnIds.addAll(allowedNetworkPlmnIds);
            return this;
        }

        private static void validatePlmnIds(Set<String> allowedNetworkPlmnIds) {
            Objects.requireNonNull(allowedNetworkPlmnIds, "allowedNetworkPlmnIds is null");

            // A valid PLMN is a concatenation of MNC and MCC, and thus consists of 5 or 6 decimal
            // digits.
            for (String id : allowedNetworkPlmnIds) {
                if ((id.length() == 5 || id.length() == 6) && id.matches("[0-9]+")) {
                    continue;
                } else {
                    throw new IllegalArgumentException("Found invalid PLMN ID: " + id);
                }
            }
        }

        /**
         * Set allowed specific carrier IDs.
         *
         * @param allowedSpecificCarrierIds the allowed specific carrier IDs, or an empty set of any
         *     specific carrier ID is acceptable. See {@link
         *     TelephonyManager#getSimSpecificCarrierId()}.
         */
        @NonNull
        public Builder setAllowedSpecificCarrierIds(
                @NonNull Set<Integer> allowedSpecificCarrierIds) {
            Objects.requireNonNull(allowedSpecificCarrierIds, "allowedCarrierIds is null");
            mAllowedSpecificCarrierIds.clear();
            mAllowedSpecificCarrierIds.addAll(allowedSpecificCarrierIds);
            return this;
        }

        /**
         * Set if roaming is allowed.
         *
         * @param allowRoaming the flag to indicate if roaming is allowed. Defaults to {@code
         *     false}.
         */
        @NonNull
        public Builder setAllowRoaming(boolean allowRoaming) {
            mAllowRoaming = allowRoaming;
            return this;
        }

        /**
         * Set if requiring an opportunistic network.
         *
         * @param requireOpportunistic the flag to indicate if caller requires an opportunistic
         *     network. Defaults to {@code false}.
         */
        @NonNull
        public Builder setRequireOpportunistic(boolean requireOpportunistic) {
            mRequireOpportunistic = requireOpportunistic;
            return this;
        }

        /** Build the CellUnderlyingNetworkPriority. */
        @NonNull
        public CellUnderlyingNetworkPriority build() {
            return new CellUnderlyingNetworkPriority(
                    mNetworkQuality,
                    mAllowMetered,
                    mAllowedNetworkPlmnIds,
                    mAllowedSpecificCarrierIds,
                    mAllowRoaming,
                    mRequireOpportunistic);
        }

        /** @hide */
        @Override
        Builder self() {
            return this;
        }
    }
}
