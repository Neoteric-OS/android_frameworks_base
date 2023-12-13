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

import static com.android.server.VcnManagementService.LOCAL_LOG;
import static com.android.server.vcn.util.PersistableBundleUtils.PersistableBundleWrapper;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.IpSecTransform;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.vcn.VcnManager;
import android.net.vcn.VcnUnderlyingNetworkTemplate;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Slog;

import com.android.internal.util.IndentingPrintWriter;
import com.android.server.vcn.TelephonySubscriptionTracker.TelephonySubscriptionSnapshot;
import com.android.server.vcn.VcnContext;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * UnderlyingNetworkEvaluator evaluates the quality and priority class of a network candidate for
 * route selection.
 *
 * @hide
 */
class UnderlyingNetworkEvaluator {
    private static final String TAG = UnderlyingNetworkEvaluator.class.getSimpleName();

    private static final boolean VDBG = true; // STOPSHIP: if true

    private static final int[] PENALTY_TIMEOUT_MIN_DEFAULT = new int[] {5};

    @NonNull private final VcnContext mVcnContext;
    @NonNull private final Handler mHandler;
    @NonNull private final Object mExitPenaltyBoxToken;
    @NonNull private final UnderlyingNetworkRecord.Builder mNetworkRecordBuilder;

    @NonNull private final List<VcnUnderlyingNetworkTemplate> mUnderlyingNetworkTemplates;
    @NonNull private final ParcelUuid mSubscriptionGroup;
    @NonNull private final TelephonySubscriptionSnapshot mLastSnapshot;
    @Nullable private final PersistableBundleWrapper mCarrierConfig;

    @NonNull private final NetworkEvaluatorCallback mEvaluatorCallback;
    @Nullable private final NetworkMetricMonitor mIpSecPacketLossDetector;

    @Nullable private UnderlyingNetworkRecord mCurrentRecord;

    // TODO: Support back-off timeouts
    private final long mPenaltyTimeoutMs;

    private boolean mIsSelected;
    private boolean mIsPenalized;
    private int mPriorityClass = NetworkPriorityClassifier.PRIORITY_INVALID;

    UnderlyingNetworkEvaluator(
            @NonNull VcnContext vcnContext,
            @NonNull Network network,
            @NonNull List<VcnUnderlyingNetworkTemplate> underlyingNetworkTemplates,
            @NonNull ParcelUuid subscriptionGroup,
            @NonNull TelephonySubscriptionSnapshot lastSnapshot,
            @Nullable PersistableBundleWrapper carrierConfig,
            @NonNull NetworkEvaluatorCallback evaluatorCallback) {
        mVcnContext = vcnContext;
        mHandler = new Handler(mVcnContext.getLooper());
        mExitPenaltyBoxToken = new Object();
        mNetworkRecordBuilder = new UnderlyingNetworkRecord.Builder(network);

        mUnderlyingNetworkTemplates = underlyingNetworkTemplates;
        mSubscriptionGroup = subscriptionGroup;
        mLastSnapshot = lastSnapshot;
        mCarrierConfig = carrierConfig;
        mEvaluatorCallback = evaluatorCallback;

        mPenaltyTimeoutMs = getPenaltyTimeoutMs(carrierConfig);

        mCurrentRecord = null;
        mIsSelected = false;

        updatePriorityClass();

        if (mVcnContext.getFeatureFlags().networkMetricMonitor()
                && mVcnContext.getCoreNetFeatureFlags().ipsecTransformState()) {
            logD("Enable IpSecPacketLossDetector");
            mIpSecPacketLossDetector =
                    new IpSecPacketLossDetector(
                            mVcnContext, network, mCarrierConfig, new MyMetricMonitorCallback());
        } else {
            mIpSecPacketLossDetector = null;
        }

        logInfo("Constructed");
    }

    /** Callback to notify caller to reevaluate network selection */
    interface NetworkEvaluatorCallback {
        /**
         * Called when either of mIsPenalized or mPriorityClass has changed
         *
         * <p>When receiving this call, UnderlyingNetworkController should reevaluate all network
         * candidates for VCN underlying network selection
         */
        void onEvaluationResultChanged();
    }

    private class MyMetricMonitorCallback
            implements NetworkMetricMonitor.NetworkMetricMonitorCallback {
        public void onValidationResultChanged() {
            mVcnContext.ensureRunningOnLooperThread();

            logD("#onValidationResultChanged");
            handleValidationResultChanged();
        }
    }

    private static long getPenaltyTimeoutMs(@Nullable PersistableBundleWrapper carrierConfig) {
        final int[] timeoutMinuteList;
        if (carrierConfig != null) {
            timeoutMinuteList =
                    carrierConfig.getIntArray(
                            VcnManager.VCN_NETWORK_SELECTION_PENALTY_TIMEOUT_MIN_LIST_KEY,
                            PENALTY_TIMEOUT_MIN_DEFAULT);
        } else {
            timeoutMinuteList = PENALTY_TIMEOUT_MIN_DEFAULT;
        }
        return TimeUnit.MINUTES.toMillis(timeoutMinuteList[0]);
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

    static Comparator<UnderlyingNetworkEvaluator> getComparator() {
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

    private void handleValidationResultChanged() {
        final boolean wasPenalized = mIsPenalized;
        mIsPenalized = mIpSecPacketLossDetector.isValidationFailed();

        logV(
                String.format(
                        "#handleValidationResultChanged: wasPenalized %b mIsPenalized %b",
                        wasPenalized, mIsPenalized));

        if (wasPenalized == mIsPenalized) {
            return;
        }

        if (mIsPenalized) {
            mHandler.postDelayed(
                    new ExitPenaltyBoxRunnable(), mExitPenaltyBoxToken, mPenaltyTimeoutMs);
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
                logWtf("Evaluator not being penalized but ExitPenaltyBoxRunnable was scheduled");
                return;
            }
            mIsPenalized = false;
            mEvaluatorCallback.onEvaluationResultChanged();
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

    void setSelectedNetwork(@Nullable UnderlyingNetworkRecord currentlySelected) {
        if (currentlySelected == null) {
            mIsSelected = false;
        } else {
            mIsSelected =
                    Objects.equals(currentlySelected.network, mNetworkRecordBuilder.getNetwork());
        }

        updatePriorityClass();

        if (mIpSecPacketLossDetector != null) {
            mIpSecPacketLossDetector.setIsSelected(mIsSelected);
        }
    }

    void setIpSecTransform(@NonNull IpSecTransform inTransform) {
        if (!mIsSelected) {
            return;
        }

        if (mIpSecPacketLossDetector != null) {
            mIpSecPacketLossDetector.setIpSecTransform(inTransform);
        }
    }

    void close() {
        logD("close");
        if (mIpSecPacketLossDetector != null) {
            mIpSecPacketLossDetector.close();
        }
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

        if (mNetworkRecordBuilder.isValid()) {
            getNetworkRecord().dump(pw);
        } else {
            pw.println("mNetwork: " + mNetworkRecordBuilder.getNetwork());
        }

        pw.println("mIsSelected: " + mIsSelected);
        pw.println("mPriorityClass: " + mPriorityClass);

        pw.decreaseIndent();
    }

    private String getLogPrefix() {
        return "[Network " + mNetworkRecordBuilder.getNetwork() + "] ";
    }

    private void logV(String msg) {
        if (VDBG) {
            Slog.i(TAG, getLogPrefix() + msg);
            LOCAL_LOG.log("[VERBOSE ] " + TAG + getLogPrefix() + msg);
        }
    }

    private void logD(String msg) {
        Slog.i(TAG, getLogPrefix() + msg);
        LOCAL_LOG.log("[DEBUG ] " + TAG + getLogPrefix() + msg);
    }

    private void logInfo(String msg) {
        Slog.i(TAG, getLogPrefix() + msg);
        LOCAL_LOG.log("[INFO ] " + TAG + getLogPrefix() + msg);
    }

    private void logWtf(String msg) {
        Slog.i(TAG, getLogPrefix() + msg);
        LOCAL_LOG.log("[WTF ] " + TAG + getLogPrefix() + msg);
    }
}
