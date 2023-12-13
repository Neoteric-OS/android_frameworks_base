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
import android.text.TextUtils;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.annotations.VisibleForTesting.Visibility;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.vcn.TelephonySubscriptionTracker.TelephonySubscriptionSnapshot;
import com.android.server.vcn.VcnContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * UnderlyingNetworkEvaluator evaluates the quality and priority class of a network candidate for
 * route selection.
 *
 * @hide
 */
public class UnderlyingNetworkEvaluator {
    private static final String TAG = UnderlyingNetworkEvaluator.class.getSimpleName();

    private static final int[] PENALTY_TIMEOUT_MINUTE_DEFAULT = new int[] {5};
    private static final boolean VDBG = false; // STOPSHIP: if true

    @NonNull private final VcnContext mVcnContext;
    @NonNull private final Handler mHandler;
    @NonNull private final Object mCancellationToken;

    @NonNull private final UnderlyingNetworkRecord.Builder mNetworkRecordBuilder;
    @NonNull private final List<VcnUnderlyingNetworkTemplate> mUnderlyingNetworkTemplates;
    @NonNull private final ParcelUuid mSubscriptionGroup;
    @NonNull private final TelephonySubscriptionSnapshot mLastSnapshot;
    @Nullable private final PersistableBundleWrapper mCarrierConfig;

    @NonNull private final NetworkEvaluatorCallback mEvaluatorCallback;
    @NonNull private final List<NetworkMetricMonitor> mMetricMonitors = new ArrayList<>();

    @NonNull private final Dependencies mDependencies;

    // TODO: Support back-off timeouts
    private final long mPenalizedUntilMs;

    private boolean mIsSelected;
    private boolean mIsPenalized;
    private int mPriorityClass = NetworkPriorityClassifier.PRIORITY_INVALID;

    @VisibleForTesting(visibility = Visibility.PRIVATE)
    public UnderlyingNetworkEvaluator(
            @NonNull VcnContext vcnContext,
            @NonNull Network network,
            @NonNull List<VcnUnderlyingNetworkTemplate> underlyingNetworkTemplates,
            @NonNull ParcelUuid subscriptionGroup,
            @NonNull TelephonySubscriptionSnapshot lastSnapshot,
            @Nullable PersistableBundleWrapper carrierConfig,
            @NonNull NetworkEvaluatorCallback evaluatorCallback,
            @NonNull Dependencies dependencies) {
        mVcnContext = vcnContext;
        mHandler = new Handler(mVcnContext.getLooper());
        mCancellationToken = new Object();
        mDependencies = dependencies;
        mEvaluatorCallback = evaluatorCallback;

        mUnderlyingNetworkTemplates = underlyingNetworkTemplates;
        mSubscriptionGroup = subscriptionGroup;
        mLastSnapshot = lastSnapshot;
        mCarrierConfig = carrierConfig;

        mNetworkRecordBuilder = new UnderlyingNetworkRecord.Builder(network);
        mIsSelected = false;
        mIsPenalized = false;
        mPenalizedUntilMs = getPenaltyTimeoutMs(carrierConfig);

        updatePriorityClass();

        if (isIpSecPacketLossDetectorEnabled()) {
            mMetricMonitors.add(
                    mDependencies.newIpSecPacketLossDetector(
                            mVcnContext,
                            mNetworkRecordBuilder.getNetwork(),
                            mCarrierConfig,
                            new MetricMonitorCallbackImpl()));
        }
    }

    public UnderlyingNetworkEvaluator(
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
        /** Get an IpSecPacketLossDetector instance */
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
         * Called when mIsPenalized changed
         *
         * <p>When receiving this call, UnderlyingNetworkController should reevaluate all network
         * candidates for VCN underlying network selection
         */
        void onEvaluationResultChanged();
    }

    private class MetricMonitorCallbackImpl
            implements NetworkMetricMonitor.NetworkMetricMonitorCallback {
        public void onValidationResultChanged() {
            mVcnContext.ensureRunningOnLooperThread();

            handleValidationResultChanged();
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
                            mIsSelected,
                            mCarrierConfig);
        } else {
            mPriorityClass = NetworkPriorityClassifier.PRIORITY_INVALID;
        }
    }

    private boolean isIpSecPacketLossDetectorEnabled() {
        return isIpSecPacketLossDetectorEnabled(mVcnContext);
    }

    private static boolean isIpSecPacketLossDetectorEnabled(VcnContext vcnContext) {
        return vcnContext.isFlagIpSecTransformStateEnabled()
                && vcnContext.isFlagNetworkMetricMonitorEnabled();
    }

    /** Get the comparator for UnderlyingNetworkEvaluator */
    public static Comparator<UnderlyingNetworkEvaluator> getComparator(VcnContext vcnContext) {
        return (left, right) -> {
            if (isIpSecPacketLossDetectorEnabled(vcnContext)) {
                if (left.mIsPenalized != right.mIsPenalized) {
                    return left.mIsPenalized ? -1 : 1;
                }
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

    private static long getPenaltyTimeoutMs(@Nullable PersistableBundleWrapper carrierConfig) {
        final int[] timeoutMinuteList;

        if (carrierConfig != null) {
            timeoutMinuteList =
                    carrierConfig.getIntArray(
                            VcnManager.VCN_NETWORK_SELECTION_PENALTY_TIMEOUT_MINUTE_LIST_KEY,
                            PENALTY_TIMEOUT_MINUTE_DEFAULT);
        } else {
            timeoutMinuteList = PENALTY_TIMEOUT_MINUTE_DEFAULT;
        }

        // TODO: Add the support of back-off timeouts and return the full list
        return TimeUnit.MINUTES.toMillis(timeoutMinuteList[0]);
    }

    private void handleValidationResultChanged() {
        final boolean wasPenalized = mIsPenalized;
        mIsPenalized = false;
        for (NetworkMetricMonitor monitor : mMetricMonitors) {
            mIsPenalized |= monitor.isValidationFailed();
        }

        if (wasPenalized == mIsPenalized) {
            return;
        }

        logInfo(
                TextUtils.formatSimple(
                        "#handleValidationResultChanged: wasPenalized %b mIsPenalized %b",
                        wasPenalized, mIsPenalized));

        if (mIsPenalized) {
            mHandler.postDelayed(
                    new ExitPenaltyBoxRunnable(), mCancellationToken, mPenalizedUntilMs);
        } else {
            // Exit the penalty box
            mHandler.removeCallbacksAndEqualMessages(mCancellationToken);
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

    /** Set the NetworkCapabilities */
    public void setNetworkCapabilities(@NonNull NetworkCapabilities nc) {
        mNetworkRecordBuilder.setNetworkCapabilities(nc);
        updatePriorityClass();
    }

    /** Set the LinkProperties */
    public void setLinkProperties(@NonNull LinkProperties lp) {
        mNetworkRecordBuilder.setLinkProperties(lp);
        updatePriorityClass();
    }

    /** Set whether the network is blocked */
    public void setIsBlocked(boolean isBlocked) {
        mNetworkRecordBuilder.setIsBlocked(isBlocked);
        updatePriorityClass();
    }

    /** Set whether the network is selected as VCN's underlying network */
    public void setIsSelected(boolean isSelected) {
        mIsSelected = isSelected;
        updatePriorityClass();
        for (NetworkMetricMonitor monitor : mMetricMonitors) {
            monitor.setIsSelected(isSelected);
        }
    }

    /** Update the IpSecTransform applied to the network */
    public void setIpSecTransform(@NonNull IpSecTransform inTransform) {
        if (!mIsSelected) {
            logWtf("setIpSecTransform on an unselected evaluator");
            return;
        }

        for (NetworkMetricMonitor monitor : mMetricMonitors) {
            monitor.setIpSecTransform(inTransform);
        }
    }

    /** Close the evaluator and stop all the underlying network metric monitors */
    public void close() {
        mHandler.removeCallbacksAndEqualMessages(mCancellationToken);

        for (NetworkMetricMonitor monitor : mMetricMonitors) {
            monitor.close();
        }
    }

    /** Return whether this network evaluator is valid */
    public boolean isValid() {
        return mNetworkRecordBuilder.isValid();
    }

    /** Return the network */
    public Network getNetwork() {
        return mNetworkRecordBuilder.getNetwork();
    }

    /** Return the network record */
    public UnderlyingNetworkRecord getNetworkRecord() {
        return mNetworkRecordBuilder.build();
    }

    /** Return the priority class for network selection */
    public int getPriorityClass() {
        return mPriorityClass;
    }

    /** Return whether the network is being penalized */
    public boolean isPenalized() {
        return mIsPenalized;
    }

    /** Dump the information of this instance */
    public void dump(IndentingPrintWriter pw) {
        pw.println("UnderlyingNetworkEvaluator:");
        pw.increaseIndent();

        if (mNetworkRecordBuilder.isValid()) {
            getNetworkRecord().dump(pw);
        } else {
            pw.println("mNetwork: " + mNetworkRecordBuilder.getNetwork());
        }

        pw.println("mIsSelected: " + mIsSelected);
        pw.println("mPriorityClass: " + mPriorityClass);
        pw.println("mIsPenalized: " + mIsPenalized);

        pw.decreaseIndent();
    }

    private String getLogPrefix() {
        return "[Network " + mNetworkRecordBuilder.getNetwork() + "] ";
    }

    private void logInfo(String msg) {
        Slog.i(TAG, getLogPrefix() + msg);
        LOCAL_LOG.log("[INFO ] " + TAG + getLogPrefix() + msg);
    }

    private void logWtf(String msg) {
        Slog.wtf(TAG, getLogPrefix() + msg);
        LOCAL_LOG.log("[WTF ] " + TAG + getLogPrefix() + msg);
    }
}
