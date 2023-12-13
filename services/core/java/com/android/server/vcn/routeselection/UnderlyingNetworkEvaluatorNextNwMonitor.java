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
import android.net.Network;
import android.net.vcn.VcnManager;
import android.net.vcn.VcnUnderlyingNetworkTemplate;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.annotations.VisibleForTesting.Visibility;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.vcn.TelephonySubscriptionTracker.TelephonySubscriptionSnapshot;
import com.android.server.vcn.VcnContext;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * UnderlyingNetworkEvaluatorNextNwMonitor extends UnderlyingNetworkEvaluator's ability to enable
 * IPsec packet loss monitoring
 *
 * <p>This class is flag gated by "network_metric_monitor" and "ipsec_tramsform_state"
 *
 * @hide
 */
public class UnderlyingNetworkEvaluatorNextNwMonitor extends UnderlyingNetworkEvaluator {
    private static final int[] PENALTY_TIMEOUT_MIN_DEFAULT = new int[] {5};
    private static final boolean VDBG = true; // STOPSHIP: if true

    @NonNull private final Handler mHandler;
    @NonNull private final Object mExitPenaltyBoxToken;

    @NonNull private final NetworkEvaluatorCallback mEvaluatorCallback;
    @NonNull private final NetworkMetricMonitor mIpSecPacketLossDetector;

    // TODO: Support back-off timeouts
    private final long mPenaltyTimeoutMs;
    private final Dependencies mDependencies;

    private boolean mIsPenalized;

    @VisibleForTesting(visibility = Visibility.PACKAGE)
    public UnderlyingNetworkEvaluatorNextNwMonitor(
            @NonNull VcnContext vcnContext,
            @NonNull Network network,
            @NonNull List<VcnUnderlyingNetworkTemplate> underlyingNetworkTemplates,
            @NonNull ParcelUuid subscriptionGroup,
            @NonNull TelephonySubscriptionSnapshot lastSnapshot,
            @Nullable PersistableBundleWrapper carrierConfig,
            @NonNull NetworkEvaluatorCallback evaluatorCallback,
            @NonNull Dependencies dependencies) {
        super(
                vcnContext,
                network,
                underlyingNetworkTemplates,
                subscriptionGroup,
                lastSnapshot,
                carrierConfig);

        if (!mVcnContext.isIpSecPacketDetectorEnabled()) {
            logWtf("isIpSecPacketDetectorEnabled is false");
        }

        mHandler = new Handler(mVcnContext.getLooper());
        mExitPenaltyBoxToken = new Object();
        mPenaltyTimeoutMs = getPenaltyTimeoutMs(carrierConfig);

        mDependencies = dependencies;
        mEvaluatorCallback = evaluatorCallback;
        mIpSecPacketLossDetector =
                mDependencies.newIpSecPacketLossDetector(
                        mVcnContext,
                        mNetworkRecordBuilder.getNetwork(),
                        mCarrierConfig,
                        new MyMetricMonitorCallback());
    }

    public UnderlyingNetworkEvaluatorNextNwMonitor(
            @NonNull VcnContext vcnContext,
            @NonNull Network network,
            @NonNull List<VcnUnderlyingNetworkTemplate> underlyingNetworkTemplates,
            @NonNull ParcelUuid subscriptionGroup,
            @NonNull TelephonySubscriptionSnapshot lastSnapshot,
            @Nullable PersistableBundleWrapper carrierConfig,
            @NonNull NetworkEvaluatorCallback evaluatorCallback) {
        this(
                vcnContext,
                network,
                underlyingNetworkTemplates,
                subscriptionGroup,
                lastSnapshot,
                carrierConfig,
                evaluatorCallback,
                new Dependencies());
    }

    @VisibleForTesting(visibility = Visibility.PRIVATE)
    public static class Dependencies {
        public IpSecPacketLossDetector newIpSecPacketLossDetector(
                @NonNull VcnContext vcnContext,
                @NonNull Network network,
                @Nullable PersistableBundleWrapper carrierConfig,
                @NonNull NetworkMetricMonitor.NetworkMetricMonitorCallback callback) {
            return new IpSecPacketLossDetector(vcnContext, network, carrierConfig, callback);
        }
    }

    /** Callback to notify caller to reevaluate network selection */
    public interface NetworkEvaluatorCallback {
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

    static Comparator<UnderlyingNetworkEvaluator> getComparatorNextNwMonitor() {
        return (left, right) -> {
            final UnderlyingNetworkEvaluatorNextNwMonitor leftEval =
                    (UnderlyingNetworkEvaluatorNextNwMonitor) left;
            final UnderlyingNetworkEvaluatorNextNwMonitor rightEval =
                    (UnderlyingNetworkEvaluatorNextNwMonitor) right;
            if (leftEval.mIsPenalized != rightEval.mIsPenalized) {
                return leftEval.mIsPenalized ? -1 : 1;
            }

            return UnderlyingNetworkEvaluator.getComparator().compare(leftEval, rightEval);
        };
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

    public class ExitPenaltyBoxRunnable implements Runnable {
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

    @Override
    public void setSelectedNetwork(@Nullable UnderlyingNetworkRecord currentlySelected) {
        super.setSelectedNetwork(currentlySelected);
        mIpSecPacketLossDetector.setIsSelected(mIsSelected);
    }

    @Override
    public void setIpSecTransform(@NonNull IpSecTransform inTransform) {
        if (!mIsSelected) {
            return;
        }
        mIpSecPacketLossDetector.setIpSecTransform(inTransform);
    }

    @Override
    public void close() {
        logD("close");
        mIpSecPacketLossDetector.close();
        mHandler.removeCallbacksAndEqualMessages(mExitPenaltyBoxToken);
    }

    @Override
    void dumpInternal(IndentingPrintWriter pw) {
        pw.println("mIsPenalized: " + mIsPenalized);
    }

    public boolean isPenalized() {
        return mIsPenalized;
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

    private void logWtf(String msg) {
        Slog.i(TAG, getLogPrefix() + msg);
        LOCAL_LOG.log("[WTF ] " + TAG + getLogPrefix() + msg);
    }
}
