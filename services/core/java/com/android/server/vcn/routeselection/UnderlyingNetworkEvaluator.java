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
import android.annotation.Nullable;
import android.net.IpSecTransform;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.vcn.VcnUnderlyingNetworkTemplate;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import com.android.internal.util.IndentingPrintWriter;
import com.android.server.vcn.TelephonySubscriptionTracker.TelephonySubscriptionSnapshot;
import com.android.server.vcn.VcnContext;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * UnderlyingNetworkEvaluator tracks network-selection-related knowledge for a network candidate.
 *
 * @hide
 */
class UnderlyingNetworkEvaluator {
    private static final String TAG = UnderlyingNetworkEvaluator.class.getSimpleName();

    // TODO: Read it from CarrierConfig
    private static final long PENALTY_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10L);

    // TODO: Read it from trunk stable feature flag
    private static final boolean IPSEC_PACKET_LOSS_DETECTOR = true;

    @NonNull private final VcnContext mVcnContext;
    @NonNull private final Handler mHandler;
    @NonNull private final Object mExitPenaltyBoxToken;
    @NonNull private final List<VcnUnderlyingNetworkTemplate> mUnderlyingNetworkTemplates;
    @NonNull private final ParcelUuid mSubscriptionGroup;
    @NonNull private final UnderlyingNetworkRecord.Builder mNetworkRecordBuilder;

    @NonNull private TelephonySubscriptionSnapshot mLastSnapshot;
    @NonNull private PersistableBundleWrapper mCarrierConfig;
    @Nullable private UnderlyingNetworkRecord mCurrentRecord;

    @Nullable private final NetworkMetricMonitor mIpSecPacketLossDetector;
    @NonNull private final NetworkEvaluatorCallback mEvaluatorCallback;

    private boolean mIsSelected;
    private boolean mIsPenalized;
    private int mPriorityClass = NetworkPriorityClassifier.PRIORITY_INVALID;

    UnderlyingNetworkEvaluator(
            @NonNull VcnContext vcnContext,
            @NonNull Network network,
            @NonNull List<VcnUnderlyingNetworkTemplate> underlyingNetworkTemplates,
            @NonNull ParcelUuid subscriptionGroup,
            @NonNull TelephonySubscriptionSnapshot lastSnapshot,
            @NonNull PersistableBundleWrapper carrierConfig,
            @NonNull NetworkEvaluatorCallback evaluatorCallback) {
        mVcnContext = vcnContext;
        mHandler = new Handler(mVcnContext.getLooper());
        mExitPenaltyBoxToken = new Object();
        mNetworkRecordBuilder = new UnderlyingNetworkRecord.Builder(network);

        mUnderlyingNetworkTemplates = underlyingNetworkTemplates;
        mSubscriptionGroup = subscriptionGroup;
        mLastSnapshot = lastSnapshot;
        mCarrierConfig = mCarrierConfig;
        mEvaluatorCallback = evaluatorCallback;

        mCurrentRecord = null;

        if (IPSEC_PACKET_LOSS_DETECTOR) {
            mIpSecPacketLossDetector =
                    new IpSecPacketLossDetector(vcnContext, new MyMetricMonitorCallback());
        } else {
            mIpSecPacketLossDetector = null;
        }

        mIsSelected = false;
        mIsPenalized = false;

        updatePriorityClass();
    }

    interface NetworkEvaluatorCallback {
        /**
         * Called when the either of mIsPenalized or mPriorityClass has changed
         *
         * <p>When receiving this call, UnderlyingNetworkController should reevaluate all network
         * candidates (UnderlyingNetworkRecord) for VCN underlying network selection
         */
        void onEvaluationResultChanged();
    }

    private class MyMetricMonitorCallback
            implements NetworkMetricMonitor.NetworkMetricMonitorCallback {
        public void onValidationResultChanged() {
            handleValidationResultChanged();
        }
    }

    static Comparator<UnderlyingNetworkEvaluator> getComparator() {
        return (left, right) -> {
            // TODO: flag gated
            if (left.mIsPenalized != right.mIsPenalized) {
                return left.mIsPenalized ? 1 : -1;
            }

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

    private void handleValidationResultChanged() {
        final boolean wasPenalized = mIsPenalized;
        mIsPenalized = mIpSecPacketLossDetector.isValidationFailed();

        if (wasPenalized == mIsPenalized) {
            return;
        }

        if (mIsPenalized) {
            mHandler.postDelayed(
                    new ExitPenaltyBoxRunnable(), mExitPenaltyBoxToken, PENALTY_TIMEOUT_MS);
        } else {
            // exit the penalty box
            mHandler.removeCallbacksAndEqualMessages(mExitPenaltyBoxToken);
        }

        mEvaluatorCallback.onEvaluationResultChanged();
    }

    private class ExitPenaltyBoxRunnable implements Runnable {
        @Override
        public void run() {
            if (!mIsPenalized) {
                Log.w(
                        TAG,
                        "Evaluator not being penalized but ExitPenaltyBoxRunnable was scheduled");
                return;
            }
            mIsPenalized = false;
            mEvaluatorCallback.onEvaluationResultChanged();
        }
    }

    private void updatePriorityClass() {
        if (mNetworkRecordBuilder.isValid()) {
            mPriorityClass =
                    NetworkPriorityClassifier.calculatePriorityClass(
                            mVcnContext,
                            mNetworkRecordBuilder.build(),
                            mUnderlyingNetworkTemplates,
                            mSubscriptionGroup,
                            mLastSnapshot,
                            mCurrentRecord,
                            mCarrierConfig);
        } else {
            mPriorityClass = NetworkPriorityClassifier.PRIORITY_INVALID;
        }
    }

    void setNetworkCapabilities(@NonNull NetworkCapabilities nc) {
        mNetworkRecordBuilder.setNetworkCapabilities(nc);
        updatePriorityClass();
    }

    void setLinkProperties(@NonNull LinkProperties lp) {
        mNetworkRecordBuilder.setLinkProperties(lp);
        updatePriorityClass();
    }

    void setIsBlocked(boolean isBlocked) {
        mNetworkRecordBuilder.setIsBlocked(isBlocked);
        updatePriorityClass();
    }

    void setLastSnapshotAndCarrierConfig(
            @NonNull TelephonySubscriptionSnapshot lastSnapshot,
            @NonNull PersistableBundleWrapper carrierConfig) {
        // TODO: Update priority class if flag is enabled
        mLastSnapshot = lastSnapshot;
        mCarrierConfig = carrierConfig;
        updatePriorityClass();
    }

    void setSelectedNetwork(@Nullable UnderlyingNetworkRecord currentlySelected) {
        if (currentlySelected == null) {
            mIsSelected = false;
        } else {
            mIsSelected = Objects.equals(currentlySelected, mNetworkRecordBuilder.getNetwork());
        }
        updatePriorityClass();
    }

    void setIpSecTransform(@NonNull IpSecTransform inTransform) {
        if (mIpSecPacketLossDetector == null) {
            return;
        }

        mIpSecPacketLossDetector.setIpSecTransform(inTransform);
        updatePriorityClass();
    }

    boolean isValid() {
        return mNetworkRecordBuilder.isValid();
    }

    Network getNetwork() {
        return mNetworkRecordBuilder.getNetwork();
    }

    UnderlyingNetworkRecord getNetworkRecord() {
        return mNetworkRecordBuilder.build();
    }

    int getPriorityClass() {
        return mPriorityClass;
    }

    void dump(IndentingPrintWriter pw) {
        pw.println("UnderlyingNetworkEvaluator:");
        pw.increaseIndent();

        getNetworkRecord().dump(pw);

        pw.println("mIsSelected: " + mIsSelected);
        pw.println("mIsPenalized: " + mIsPenalized);
        pw.println("mPriorityClass: " + mPriorityClass);

        pw.decreaseIndent();
    }

    @NonNull
    UnderlyingNetworkRecordEvaluated getNetworkRecordEvaluated(
            List<VcnUnderlyingNetworkTemplate> underlyingNetworkTemplates,
            ParcelUuid subscriptionGroup,
            TelephonySubscriptionSnapshot snapshot,
            UnderlyingNetworkRecord currentlySelected,
            PersistableBundleWrapper carrierConfig) {
        return new UnderlyingNetworkRecordEvaluated(
                mVcnContext,
                mNetworkRecordBuilder.build(),
                underlyingNetworkTemplates,
                subscriptionGroup,
                snapshot,
                currentlySelected,
                carrierConfig);
    }
}
