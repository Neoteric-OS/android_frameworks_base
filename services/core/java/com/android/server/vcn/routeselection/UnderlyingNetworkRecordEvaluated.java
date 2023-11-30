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

package com.android.server.vcn.routeselection;

import static com.android.server.vcn.util.PersistableBundleUtils.PersistableBundleWrapper;

import android.net.Network;
import android.net.vcn.VcnUnderlyingNetworkTemplate;
import android.os.ParcelUuid;

import com.android.internal.util.IndentingPrintWriter;
import com.android.server.vcn.TelephonySubscriptionTracker.TelephonySubscriptionSnapshot;
import com.android.server.vcn.VcnContext;

import java.util.Comparator;
import java.util.List;

/**
 * UnderlyingNetworkRecordEvaluated represents a network candidate that has been evaluated
 *
 * @hide
 */
class UnderlyingNetworkRecordEvaluated {
    private final UnderlyingNetworkRecord mNetworkRecord;
    private final boolean mIsSelected;
    private final int mPriorityClass;

    UnderlyingNetworkRecordEvaluated(
            VcnContext vcnContext,
            UnderlyingNetworkRecord networkRecord,
            List<VcnUnderlyingNetworkTemplate> underlyingNetworkTemplates,
            ParcelUuid subscriptionGroup,
            TelephonySubscriptionSnapshot snapshot,
            UnderlyingNetworkRecord currentlySelected,
            PersistableBundleWrapper carrierConfig) {
        mNetworkRecord = networkRecord;
        mIsSelected = isSelected(mNetworkRecord, currentlySelected);
        mPriorityClass =
                NetworkPriorityClassifier.calculatePriorityClass(
                        vcnContext,
                        mNetworkRecord,
                        underlyingNetworkTemplates,
                        subscriptionGroup,
                        snapshot,
                        currentlySelected,
                        carrierConfig);
    }

    private static boolean isSelected(
            UnderlyingNetworkRecord networkToCheck, UnderlyingNetworkRecord currentlySelected) {
        if (currentlySelected == null) {
            return false;
        }
        if (currentlySelected.network.equals(networkToCheck.network)) {
            return true;
        }
        return false;
    }

    static Comparator<UnderlyingNetworkRecordEvaluated> getComparator() {
        return (left, right) -> {
            final int leftIndex = left.mPriorityClass;
            final int rightIndex = right.mPriorityClass;

            // In the case of networks in the same priority class, prioritize based on other
            // criteria (eg. actively selected network, link metrics, etc)
            if (leftIndex == rightIndex) {
                // TODO: Improve the strategy of network selection when both UnderlyingNetworkRecord
                // fall into the same priority class.
                if (left.mIsSelected) {
                    return -1;
                }
                if (right.mIsSelected) {
                    return 1;
                }
            }
            return Integer.compare(leftIndex, rightIndex);
        };
    }

    Network getNetwork() {
        return mNetworkRecord.network;
    }

    int getPriorityClass() {
        return mPriorityClass;
    }

    UnderlyingNetworkRecord getNetworkRecord() {
        return mNetworkRecord;
    }

    void dump(IndentingPrintWriter pw) {
        pw.println("UnderlyingNetworkRecordEvaluated:");
        pw.increaseIndent();

        mNetworkRecord.dump(pw);

        pw.println("mIsSelected: " + mIsSelected);
        pw.println("mPriorityClass: " + mPriorityClass);

        pw.decreaseIndent();
    }
}
