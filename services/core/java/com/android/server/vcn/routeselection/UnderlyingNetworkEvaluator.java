/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.vcn.routeselection;

import static com.android.server.vcn.util.PersistableBundleUtils.PersistableBundleWrapper;

import android.annotation.NonNull;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.vcn.VcnUnderlyingNetworkTemplate;
import android.os.ParcelUuid;

import com.android.server.vcn.TelephonySubscriptionTracker.TelephonySubscriptionSnapshot;
import com.android.server.vcn.VcnContext;

import java.util.List;

/**
 * UnderlyingNetworkEvaluator tracks network-selection-related knowledge for a network candidate.
 *
 * @hide
 */
class UnderlyingNetworkEvaluator {
    @NonNull private final UnderlyingNetworkRecord.Builder mNetworkRecordBuilder;

    UnderlyingNetworkEvaluator(@NonNull Network network) {
        mNetworkRecordBuilder = new UnderlyingNetworkRecord.Builder(network);
    }

    void setNetworkCapabilities(@NonNull NetworkCapabilities nc) {
        mNetworkRecordBuilder.setNetworkCapabilities(nc);
    }

    void setLinkProperties(@NonNull LinkProperties lp) {
        mNetworkRecordBuilder.setLinkProperties(lp);
    }

    void setIsBlocked(boolean isBlocked) {
        mNetworkRecordBuilder.setIsBlocked(isBlocked);
    }

    boolean isValid() {
        return mNetworkRecordBuilder.isValid();
    }

    @NonNull
    UnderlyingNetworkRecordEvaluated getNetworkRecordEvaluated(
            VcnContext vcnContext,
            List<VcnUnderlyingNetworkTemplate> underlyingNetworkTemplates,
            ParcelUuid subscriptionGroup,
            TelephonySubscriptionSnapshot snapshot,
            UnderlyingNetworkRecord currentlySelected,
            PersistableBundleWrapper carrierConfig) {
        return new UnderlyingNetworkRecordEvaluated(
                vcnContext,
                mNetworkRecordBuilder.build(),
                underlyingNetworkTemplates,
                subscriptionGroup,
                snapshot,
                currentlySelected,
                carrierConfig);
    }
}
