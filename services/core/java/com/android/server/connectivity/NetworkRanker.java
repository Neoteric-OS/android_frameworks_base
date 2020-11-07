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

package com.android.server.connectivity;

import static android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_ETHERNET;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkScore;

import com.android.internal.util.ArrayUtils;
import com.android.internal.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * A class that knows how to find the best network matching a request out of a list of networks.
 */
public class NetworkRanker {
    public NetworkRanker() { }

    // Transport preference order, if it comes down to that.
    private final int[] PREFERRED_TRANSPORTS = { TRANSPORT_ETHERNET, TRANSPORT_WIFI,
        TRANSPORT_BLUETOOTH, TRANSPORT_CELLULAR };

    // Function used to partition a list into two working areas depending on whether they
    // satisfy a predicate. All items satisfying the predicate will be put in |positive|, all
    // items that don't will be put in |negative|.
    // This is useful in this file because many of the ranking tests will retain only networks that
    // satisfy a predicate if any of them do, but keep them all if all of them do. Having working
    // areas is uncustomary in Java, but this function is called in a fairly intensive manner
    // and doing allocation quite that often might affect performance quite badly.
    private <T> void partitionInto(@NonNull final List<T> source, @NonNull Predicate<T> test,
            @NonNull final List<T> positive, @NonNull final List<T> negative) {
        positive.clear();
        negative.clear();
        for (final T item : source) {
            if (test.test(item)) {
                positive.add(item);
            } else {
                negative.add(item);
            }
        }
    }

    /**
     * Find the best network satisfying this request among the list of passed networks.
     */
    @Nullable
    public NetworkAgentInfo getBestNetwork(@NonNull final NetworkRequest request,
            @NonNull final List<NetworkAgentInfo> nais,
            @Nullable final NetworkAgentInfo currentSatisfier) {

        // If there is an unconnected lockdown VPN, no NAI can satisfy the request.
        for (final NetworkAgentInfo nai : nais) {
            if (nai.mScore.isVpnLockdown() && !nai.everConnected) return null;
        }

        // If there is a connected VPN applying to this request, return it.
        for (final NetworkAgentInfo nai : nais) {
            if (nai.isVPN() && !nai.networkCapabilities.appliesToUid(request.getRequestorUid())) {
                // Because only one VPN can apply to a given UID, there can only be one match.
                return nai;
            }
        }

        // The following tests will search for a network matching a given criterion. They all
        // function the same way : if any network matches the criterion, drop from consideration
        // all networks that don't. To achieve this, the tests below partition the list of
        // remaining candidates into accepted and rejected networks.
        // If only one candidate remains, that's the winner : if accepted.size == 1 return [1]
        // If none remain, the criterion did not help discriminate so keep them all ; if multiple
        // remain, keep only the accepted networks. In both cases, go on to evaluating the next
        // criterion.
        // Because the working areas will be wiped, a copy of the accepted networks needs to
        // be made. As an optimization, if none were rejected by this criterion, skip creating
        // a new array.

        // Used as working areas.
        final ArrayList<NetworkAgentInfo> accepted =
                new ArrayList<>(nais.size() /* initialCapacity */);
        final ArrayList<NetworkAgentInfo> rejected =
                new ArrayList<>(nais.size() /* initialCapacity */);
        ArrayList<NetworkAgentInfo> candidates = new ArrayList<>(nais);
        final boolean anyWiFiEverValidated = CollectionUtils.any(candidates,
                nai -> nai.everValidated && nai.networkCapabilities.hasTransport(TRANSPORT_WIFI));
        if (anyWiFiEverValidated) {
            partitionInto(candidates, nai -> !nai.mScore.hasBadWifiAvoidance(), accepted, rejected);
            if (accepted.size() == 1) return accepted.get(0);
            if (accepted.size() > 0 && rejected.size() > 0) candidates = new ArrayList<>(accepted);
        }

        // If any network is explicitly selected, don't choose one that isn't.
        partitionInto(candidates, nai -> nai.mScore.isExplicitlySelected(), accepted, rejected);
        if (accepted.size() == 1) return accepted.get(0);
        if (accepted.size() > 0 && rejected.size() > 0) candidates = new ArrayList<>(accepted);

        // If any network is validated, don't choose one that isn't.
        partitionInto(candidates, nai -> nai.mScore.isValidated(), accepted, rejected);
        if (accepted.size() == 1) return accepted.get(0);
        if (accepted.size() > 0 && rejected.size() > 0) candidates = new ArrayList<>(accepted);

        // If any network is not exiting, don't choose one that is.
        partitionInto(candidates, nai -> !nai.mScore.isExiting(), accepted, rejected);
        if (accepted.size() == 1) return accepted.get(0);
        if (accepted.size() > 0 && rejected.size() > 0) candidates = new ArrayList<>(accepted);

        // If any network is unmetered, don't choose a metered network.
        partitionInto(candidates, nai -> nai.mScore.isUnmetered(), accepted, rejected);
        if (accepted.size() == 1) return accepted.get(0);
        if (accepted.size() > 0 && rejected.size() > 0) candidates = new ArrayList<>(accepted);

        // If any network is for the default subscription, don't choose a network for another
        // subscription with the same transport.
        partitionInto(candidates, nai -> nai.mScore.isDefaultSubscription(), accepted, rejected);
        for (final NetworkAgentInfo defaultSubNai : accepted) {
            final int[] transports = defaultSubNai.networkCapabilities.getTransportTypes();
            candidates.removeIf(nai -> !nai.mScore.isDefaultSubscription()
                    && Arrays.equals(transports, nai.networkCapabilities.getTransportTypes()));
        }
        if (1 == candidates.size()) return candidates.get(0);

        // If some of the networks have a better transport than others, keep only the ones with
        // the best transports.
        for (final int transport : PREFERRED_TRANSPORTS) {
            partitionInto(candidates, nai -> nai.networkCapabilities.hasTransport(transport),
                    accepted, rejected);
            if (accepted.size() == 1) return accepted.get(0);
            if (accepted.size() > 0 && rejected.size() > 0) {
                candidates = new ArrayList<>(accepted);
                break;
            }
        }

        // At this point there are still multiple networks passing all the tests above. If any
        // of them is the previous satisfier, keep it.
        if (candidates.contains(currentSatisfier)) return currentSatisfier;

        // If there are still multiple options at this point but none of them is any of the
        // transports above, it doesn't matter which is returned. They are all the same.
        return getBestNetworkByLeagcyInt(request, nais);
    }

    // TODO : remove
    // Almost equivalent to Collections.max(nais), but allows returning null if no network
    // satisfies the request.
    @Nullable
    public NetworkAgentInfo getBestNetworkByLeagcyInt(@NonNull final NetworkRequest request,
            @NonNull final Collection<NetworkAgentInfo> nais) {
        NetworkAgentInfo bestNetwork = null;
        int bestScore = Integer.MIN_VALUE;
        for (final NetworkAgentInfo nai : nais) {
            if (!nai.satisfies(request)) continue;
            if (nai.getCurrentScore() > bestScore) {
                bestNetwork = nai;
                bestScore = nai.getCurrentScore();
            }
        }
        return bestNetwork;
    }

    public boolean canBeat(@NonNull final NetworkRequest request,
            @Nullable final NetworkAgentInfo champion,
            @NonNull final NetworkOffer offer) {
        return request.canBeSatisfiedBy(offer.caps)
                && (champion == null || champion.getCurrentScore() <= offer.score.legacyInt);
    }
}
