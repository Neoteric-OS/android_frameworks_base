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

import static android.net.vcn.VcnUnderlyingNetworkTemplate.MATCH_ANY;
import static android.net.vcn.VcnUnderlyingNetworkTemplate.getMatchCriteriaString;

import static com.android.internal.annotations.VisibleForTesting.Visibility;
import static com.android.server.vcn.util.PersistableBundleUtils.INTEGER_DESERIALIZER;
import static com.android.server.vcn.util.PersistableBundleUtils.INTEGER_SERIALIZER;
import static com.android.server.vcn.util.PersistableBundleUtils.STRING_DESERIALIZER;
import static com.android.server.vcn.util.PersistableBundleUtils.STRING_SERIALIZER;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.NetworkCapabilities;
import android.net.vcn.VcnUnderlyingNetworkTemplate.MatchCriteria;
import android.os.PersistableBundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.ArraySet;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.vcn.util.PersistableBundleUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * This class represents a configuration for a priority rule class of underlying cellular networks.
 *
 * <p>See {@link VcnUnderlyingNetworkTemplate}
 */
public final class VcnCellUnderlyingNetworkTemplate extends VcnUnderlyingNetworkTemplate {
    private static final String ALLOWED_NETWORK_PLMN_IDS_KEY = "mAllowedNetworkPlmnIds";
    @NonNull private final Set<String> mAllowedNetworkPlmnIds;
    private static final String ALLOWED_SPECIFIC_CARRIER_IDS_KEY = "mAllowedSpecificCarrierIds";
    @NonNull private final Set<Integer> mAllowedSpecificCarrierIds;

    private static final String NOT_ROAMING_MATCH_KEY = "mNotRoamingMatchCriteria";
    private final int mNotRoamingMatchCriteria;

    private static final String OPPORTUNISTIC_MATCH_KEY = "mOpportunisticMatchCriteria";
    private final int mOpportunisticMatchCriteria;

    private VcnCellUnderlyingNetworkTemplate(
            int networkQuality,
            int unmeteredMatchCriteria,
            Set<String> allowedNetworkPlmnIds,
            Set<Integer> allowedSpecificCarrierIds,
            int notRoamingMatchCriteria,
            int opportunisticMatchCriteria) {
        super(NETWORK_PRIORITY_TYPE_CELL, networkQuality, unmeteredMatchCriteria);
        mAllowedNetworkPlmnIds = new ArraySet<>(allowedNetworkPlmnIds);
        mAllowedSpecificCarrierIds = new ArraySet<>(allowedSpecificCarrierIds);
        mNotRoamingMatchCriteria = notRoamingMatchCriteria;
        mOpportunisticMatchCriteria = opportunisticMatchCriteria;

        validate();
    }

    /** @hide */
    @Override
    protected void validate() {
        super.validate();
        validatePlmnIds(mAllowedNetworkPlmnIds);
        Objects.requireNonNull(mAllowedSpecificCarrierIds, "matchingCarrierIds is null");
        validateMatchCriteria(mNotRoamingMatchCriteria, "mNotRoamingMatchCriteria");
        validateMatchCriteria(mOpportunisticMatchCriteria, "mOpportunisticMatchCriteria");
    }

    private static void validatePlmnIds(Set<String> matchingOperatorPlmnIds) {
        Objects.requireNonNull(matchingOperatorPlmnIds, "matchingOperatorPlmnIds is null");

        // A valid PLMN is a concatenation of MNC and MCC, and thus consists of 5 or 6 decimal
        // digits.
        for (String id : matchingOperatorPlmnIds) {
            if ((id.length() == 5 || id.length() == 6) && id.matches("[0-9]+")) {
                continue;
            } else {
                throw new IllegalArgumentException("Found invalid PLMN ID: " + id);
            }
        }
    }

    /** @hide */
    @NonNull
    @VisibleForTesting(visibility = Visibility.PROTECTED)
    public static VcnCellUnderlyingNetworkTemplate fromPersistableBundle(
            @NonNull PersistableBundle in) {
        Objects.requireNonNull(in, "PersistableBundle is null");

        final int networkQuality = in.getInt(NETWORK_QUALITY_KEY);
        final int unmeteredMatchCriteria = in.getInt(UNMETERED_MATCH_KEY);

        final PersistableBundle plmnIdsBundle =
                in.getPersistableBundle(ALLOWED_NETWORK_PLMN_IDS_KEY);
        Objects.requireNonNull(plmnIdsBundle, "plmnIdsBundle is null");
        final Set<String> allowedNetworkPlmnIds =
                new ArraySet<String>(
                        PersistableBundleUtils.toList(plmnIdsBundle, STRING_DESERIALIZER));

        final PersistableBundle specificCarrierIdsBundle =
                in.getPersistableBundle(ALLOWED_SPECIFIC_CARRIER_IDS_KEY);
        Objects.requireNonNull(specificCarrierIdsBundle, "specificCarrierIdsBundle is null");
        final Set<Integer> allowedSpecificCarrierIds =
                new ArraySet<Integer>(
                        PersistableBundleUtils.toList(
                                specificCarrierIdsBundle, INTEGER_DESERIALIZER));

        final int notRoamingMatchCriteria = in.getInt(NOT_ROAMING_MATCH_KEY);
        final int opportunisticMatchCriteria = in.getInt(OPPORTUNISTIC_MATCH_KEY);

        return new VcnCellUnderlyingNetworkTemplate(
                networkQuality,
                unmeteredMatchCriteria,
                allowedNetworkPlmnIds,
                allowedSpecificCarrierIds,
                notRoamingMatchCriteria,
                opportunisticMatchCriteria);
    }

    /** @hide */
    @Override
    @NonNull
    @VisibleForTesting(visibility = Visibility.PROTECTED)
    public PersistableBundle toPersistableBundle() {
        final PersistableBundle result = super.toPersistableBundle();

        final PersistableBundle plmnIdsBundle =
                PersistableBundleUtils.fromList(
                        new ArrayList<>(mAllowedNetworkPlmnIds), STRING_SERIALIZER);
        result.putPersistableBundle(ALLOWED_NETWORK_PLMN_IDS_KEY, plmnIdsBundle);

        final PersistableBundle specificCarrierIdsBundle =
                PersistableBundleUtils.fromList(
                        new ArrayList<>(mAllowedSpecificCarrierIds), INTEGER_SERIALIZER);
        result.putPersistableBundle(ALLOWED_SPECIFIC_CARRIER_IDS_KEY, specificCarrierIdsBundle);

        result.putInt(NOT_ROAMING_MATCH_KEY, mNotRoamingMatchCriteria);
        result.putInt(OPPORTUNISTIC_MATCH_KEY, mOpportunisticMatchCriteria);

        return result;
    }

    /**
     * Retrieve the matching operator PLMN IDs, or an empty set if any PLMN ID is acceptable.
     *
     * @see Builder#setMatchingOperatorPlmnIds(Set)
     */
    @NonNull
    public Set<String> getMatchingOperatorPlmnIds() {
        return Collections.unmodifiableSet(mAllowedNetworkPlmnIds);
    }

    /**
     * Retrieve the matching specific carrier IDs, or an empty set if any specific carrier ID is
     * acceptable.
     *
     * @see Builder#setMatchingSpecificCarrierIds(Set)
     */
    @NonNull
    public Set<Integer> getMatchingSpecificCarrierIds() {
        return Collections.unmodifiableSet(mAllowedSpecificCarrierIds);
    }

    /**
     * Return the matching criteria for {@link NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING}.
     *
     * @see Builder#setNotRoamingMatch(int)
     */
    @MatchCriteria
    public int getNotRoamingMatch() {
        return mNotRoamingMatchCriteria;
    }

    /**
     * Return the matching criteria with regard to requiring opportunistic.
     *
     * @see Builder#setOpportunisticMatch(int)
     */
    @MatchCriteria
    public int getOpportunisticMatch() {
        return mOpportunisticMatchCriteria;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                mAllowedNetworkPlmnIds,
                mAllowedSpecificCarrierIds,
                mNotRoamingMatchCriteria,
                mOpportunisticMatchCriteria);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (!super.equals(other)) {
            return false;
        }

        if (!(other instanceof VcnCellUnderlyingNetworkTemplate)) {
            return false;
        }

        final VcnCellUnderlyingNetworkTemplate rhs = (VcnCellUnderlyingNetworkTemplate) other;
        return Objects.equals(mAllowedNetworkPlmnIds, rhs.mAllowedNetworkPlmnIds)
                && Objects.equals(mAllowedSpecificCarrierIds, rhs.mAllowedSpecificCarrierIds)
                && mNotRoamingMatchCriteria == rhs.mNotRoamingMatchCriteria
                && mOpportunisticMatchCriteria == rhs.mOpportunisticMatchCriteria;
    }

    /** @hide */
    @Override
    void dumpTransportSpecificFields(IndentingPrintWriter pw) {
        pw.println("mAllowedNetworkPlmnIds: " + mAllowedNetworkPlmnIds.toString());
        pw.println("mAllowedSpecificCarrierIds: " + mAllowedSpecificCarrierIds.toString());
        pw.println("mNotRoamingMatchCriteria: " + getMatchCriteriaString(mNotRoamingMatchCriteria));
        pw.println(
                "mOpportunisticMatchCriteria: "
                        + getMatchCriteriaString(mOpportunisticMatchCriteria));
    }

    /** This class is used to incrementally build WifiNetworkPriority objects. */
    public static final class Builder extends VcnUnderlyingNetworkTemplate.Builder<Builder> {
        @NonNull private final Set<String> mAllowedNetworkPlmnIds = new ArraySet<>();
        @NonNull private final Set<Integer> mAllowedSpecificCarrierIds = new ArraySet<>();

        private int mNotRoamingMatchCriteria = MATCH_ANY;
        private int mOpportunisticMatchCriteria = MATCH_ANY;

        /** Construct a Builder object. */
        public Builder() {}

        /**
         * Set operator PLMN IDs with which a network can match this priority rule.
         *
         * <p>This is used to distinguish cases where roaming agreements may dictate a different
         * priority from a partner's networks.
         *
         * @param matchingOperatorPlmnIds the matching operator PLMN IDs in String. Network with one
         *     of the matching PLMN IDs can match this priority rule. Defaults to an empty set,
         *     allowing ANY PLMN ID. A valid PLMN is a concatenation of MNC and MCC, and thus
         *     consists of 5 or 6 decimal digits. See {@link SubscriptionInfo#getMccString()} and
         *     {@link SubscriptionInfo#getMncString()}.
         */
        @NonNull
        public Builder setMatchingOperatorPlmnIds(@NonNull Set<String> matchingOperatorPlmnIds) {
            validatePlmnIds(matchingOperatorPlmnIds);

            mAllowedNetworkPlmnIds.clear();
            mAllowedNetworkPlmnIds.addAll(matchingOperatorPlmnIds);
            return this;
        }

        /**
         * Set specific carrier IDs with which a network can match this priority rule.
         *
         * @param matchingSpecificCarrierIds the matching specific carrier IDs. Network with one of
         *     the specific carrier IDs can match this priority rule. Defaults to an empty set,
         *     allowing ANY carrier ID. See {@link TelephonyManager#getSimSpecificCarrierId()}.
         */
        @NonNull
        public Builder setMatchingSpecificCarrierIds(
                @NonNull Set<Integer> matchingSpecificCarrierIds) {
            Objects.requireNonNull(
                    matchingSpecificCarrierIds, "matchingSpecificCarrierIds is null");

            mAllowedSpecificCarrierIds.clear();
            mAllowedSpecificCarrierIds.addAll(matchingSpecificCarrierIds);
            return this;
        }

        /**
         * Set the matching criteria for {@link NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING}.
         *
         * @param notRoamingMatchCriteria the flag to indicate the matching criteria for {@link
         *     NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING}. Defaults to {@link #MATCH_ANY}.
         */
        @NonNull
        public Builder setNotRoamingMatch(int notRoamingMatchCriteria) {
            mNotRoamingMatchCriteria = notRoamingMatchCriteria;
            return this;
        }

        /**
         * Set the matching criteria with regard to requiring opportunistic.
         *
         * @param opportunisticMatchCriteria the flag to the matching criteria with regard to
         *     requiring opportunistic. Defaults to {@link #MATCH_ANY}. See {@link
         *     SubscriptionManager#setOpportunistic(boolean, int)}
         */
        @NonNull
        public Builder setOpportunisticMatch(int opportunisticMatchCriteria) {
            mOpportunisticMatchCriteria = opportunisticMatchCriteria;
            return this;
        }

        /** Build the VcnCellUnderlyingNetworkTemplate. */
        @NonNull
        public VcnCellUnderlyingNetworkTemplate build() {
            return new VcnCellUnderlyingNetworkTemplate(
                    mNetworkQuality,
                    mUnmeteredMatchCriteria,
                    mAllowedNetworkPlmnIds,
                    mAllowedSpecificCarrierIds,
                    mNotRoamingMatchCriteria,
                    mOpportunisticMatchCriteria);
        }

        /** @hide */
        @Override
        Builder self() {
            return this;
        }
    }
}
