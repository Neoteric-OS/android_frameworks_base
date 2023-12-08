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
import android.net.IpSecTransformState;
import android.net.Network;
import android.net.vcn.VcnManager;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.OutcomeReceiver;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.vcn.VcnContext;

import java.util.concurrent.TimeUnit;

/** IpSecPacketLossDetector will detect the IPsec packet loss */
public class IpSecPacketLossDetector extends NetworkMetricMonitor {
    private static final String TAG = IpSecPacketLossDetector.class.getSimpleName();

    @VisibleForTesting static final int PACKET_LOSS_UNAVALAIBLE = -1;

    private static final int IPSEC_PACKET_LOSS_PERCENT_THRESHOLD_DEFAULT = 50;
    private static final int POLL_IPSEC_STATE_INTERVAL_SECONDS_DEFAULT = 20;

    private final long mPollIpSecStateIntervalMs;
    private final int mPacketLossRatePercentThreshold;

    @NonNull private final Handler mHandler;
    @NonNull private final Object mPollIpSecStateToken;
    @NonNull private final PacketLossCalculator mPacketLossCalculator;

    private boolean mIsStarted;

    @Nullable private IpSecTransformState mLastIpSecTransformState;

    @VisibleForTesting
    public IpSecPacketLossDetector(
            @NonNull VcnContext vcnContext,
            @NonNull Network network,
            @Nullable PersistableBundleWrapper carrierConfig,
            @NonNull NetworkMetricMonitorCallback callback,
            @NonNull Dependencies deps) {
        super(vcnContext, network, carrierConfig, callback);
        mHandler = new Handler(getVcnContext().getLooper());
        mPollIpSecStateToken = new Object();
        mPacketLossCalculator = deps.getPacketLossCalculator();
        mIsStarted = false;

        mPollIpSecStateIntervalMs = getPollIpSecStateIntervalMs(carrierConfig);
        mPacketLossRatePercentThreshold = getPacketLossRatePercentThreshold(carrierConfig);
    }

    public IpSecPacketLossDetector(
            @NonNull VcnContext vcnContext,
            @NonNull Network network,
            @Nullable PersistableBundleWrapper carrierConfig,
            @NonNull NetworkMetricMonitorCallback callback) {
        this(vcnContext, network, carrierConfig, callback, new Dependencies());
    }

    @VisibleForTesting
    public static class Dependencies {
        public PacketLossCalculator getPacketLossCalculator() {
            return new PacketLossCalculator();
        }
    }

    private static long getPollIpSecStateIntervalMs(
            @Nullable PersistableBundleWrapper carrierConfig) {
        final int seconds;
        if (carrierConfig != null) {
            seconds =
                    carrierConfig.getInt(
                            VcnManager.VCN_NETWORK_SELECTION_POLL_IPSEC_STATE_INTERVAL_SECONDS_KEY,
                            POLL_IPSEC_STATE_INTERVAL_SECONDS_DEFAULT);
        } else {
            seconds = POLL_IPSEC_STATE_INTERVAL_SECONDS_DEFAULT;
        }
        return TimeUnit.SECONDS.toMillis(seconds);
    }

    private static int getPacketLossRatePercentThreshold(
            @Nullable PersistableBundleWrapper carrierConfig) {
        if (carrierConfig != null) {
            return carrierConfig.getInt(
                    VcnManager.VCN_NETWORK_SELECTION_IPSEC_PACKET_LOSS_PERCENT_THRESHOLD_KEY,
                    IPSEC_PACKET_LOSS_PERCENT_THRESHOLD_DEFAULT);
        }
        return IPSEC_PACKET_LOSS_PERCENT_THRESHOLD_DEFAULT;
    }

    @Override
    protected String getClassName() {
        return TAG;
    }

    @Override
    protected void startOrStop() {
        if (getTransformInInternal() != null) {
            start();
            reset();

            // Inject for test
            mHandler.postDelayed(new PollIpSecStateRunnable(), mPollIpSecStateToken, 0L);
        } else {
            stop();
        }
    }

    @Override
    public void stop() {
        super.stop();
        mIsStarted = false;
        reset();
    }

    private void reset() {
        mHandler.removeCallbacksAndEqualMessages(mPollIpSecStateToken);
        mLastIpSecTransformState = null;
    }

    @VisibleForTesting
    @Nullable
    public IpSecTransformState getLastTransformState() {
        return mLastIpSecTransformState;
    }

    private class PollIpSecStateRunnable implements Runnable {
        @Override
        public void run() {
            logV("PollIpSecStateRunnable#run");

            if (!isStarted()) {
                logWtf("Monitor stopped but PollIpSecStateRunnable not removed from Handler");
                return;
            }

            // TEST: spy the IpSecTransform
            getTransformInInternal()
                    .getIpSecTransformState(
                            new HandlerExecutor(mHandler), new IpSecTransformStateReceiver());

            // Schedule for next poll
            mHandler.postDelayed(
                    new PollIpSecStateRunnable(), mPollIpSecStateToken, mPollIpSecStateIntervalMs);
        }
    }

    private class IpSecTransformStateReceiver
            implements OutcomeReceiver<IpSecTransformState, RuntimeException> {
        public void onResult(IpSecTransformState state) {
            getVcnContext().ensureRunningOnLooperThread();
            logV("TransformStateReceiver#onResult");

            if (!isStarted()) {
                logV("Monitor is stopped. Ignore the result");
                return;
            }

            onIpSecTransformStateReceived(state);
        }

        public void onError(RuntimeException error) {
            getVcnContext().ensureRunningOnLooperThread();

            // Nothing we can do here
            logV("TransformStateReceiver#onError " + error.toString());
        }
    }

    private void onIpSecTransformStateReceived(@NonNull IpSecTransformState state) {
        if (mLastIpSecTransformState == null) {
            // This is first time to poll the state
            mLastIpSecTransformState = state;
            return;
        }

        final int packetLossRate =
                mPacketLossCalculator.getPacketLossRatePercentage(
                        mLastIpSecTransformState, state, getLogPrefix());

        if (packetLossRate == PACKET_LOSS_UNAVALAIBLE) {
            return;
        }

        final String logMsg =
                "packetLossRate "
                        + packetLossRate
                        + " %  in the past "
                        + (state.getTimestamp() - mLastIpSecTransformState.getTimestamp())
                        + "ms";
        mLastIpSecTransformState = state;
        if (packetLossRate < mPacketLossRatePercentThreshold) {
            logV(logMsg);
            onValidationResultReceivedInternal(false /* isFailed */);
        } else {
            logInfo(logMsg);
            onValidationResultReceivedInternal(true /* isFailed */);
        }
    }

    @VisibleForTesting
    public static class PacketLossCalculator {
        /**
         * Calculate the packet loss rate between the lower bounds of the old and new replay
         * windows.
         */
        public int getPacketLossRatePercentage(
                IpSecTransformState oldState, IpSecTransformState newState, String logPrefix) {
            logIpSecTransform("oldState", oldState, logPrefix);
            logIpSecTransform("newState", newState, logPrefix);

            final long replayWindowSize = oldState.getReplayBitmap().length * 8;
            final long oldSeqHi = oldState.getRxHighestSequenceNumber();
            final long oldSeqLow = Math.max(0L, oldSeqHi - replayWindowSize + 1);
            final long newSeqHi = newState.getRxHighestSequenceNumber();
            final long newSeqLow = Math.max(0L, newSeqHi - replayWindowSize + 1);

            if (newSeqHi < replayWindowSize) {
                // Impossible to estimate data loss because all packets can be out-of-order
                // delivered at this moment.
                logV(TAG, logPrefix + "Estimation unsupported: newSeqHi < replayWindowSize");
                return PACKET_LOSS_UNAVALAIBLE;
            }

            // Windows overlapped
            if (newSeqLow - oldSeqHi < 0 || newSeqLow - oldSeqHi == 0) {
                // TODO: Support it
                logV(
                        TAG,
                        logPrefix
                                + "Estimation unsupported: replay windows overlapped newSeqLow "
                                + newSeqLow
                                + " oldSeqHi "
                                + oldSeqHi);
                return PACKET_LOSS_UNAVALAIBLE;
            }

            final long expectPktCnt =
                    newSeqLow - oldSeqLow - getRxPacketInWindow(oldState.getReplayBitmap());
            final long actualPktCnt =
                    newState.getPacketCount()
                            - oldState.getPacketCount()
                            - getRxPacketInWindow(newState.getReplayBitmap());

            logV(
                    TAG,
                    String.format(
                            logPrefix + " expectPktCnt: %d; actualPktCnt %d",
                            expectPktCnt,
                            actualPktCnt));

            if (expectPktCnt < 0 || expectPktCnt == 0 || actualPktCnt < 0) {
                logWtf(TAG, "Impossible data");
                return PACKET_LOSS_UNAVALAIBLE;
            }

            return (int) (100 - (actualPktCnt * 100) / expectPktCnt);
        }
    }

    private static void logIpSecTransform(
            String transformTag, IpSecTransformState state, String logPrefix) {
        final String stateString =
                String.format(
                        " seqNo: %d | pktCnt: %d | pktCntInWindow: %d ",
                        state.getRxHighestSequenceNumber(),
                        state.getPacketCount(),
                        getRxPacketInWindow(state.getReplayBitmap()));
        logV(TAG, logPrefix + " " + transformTag + stateString);
    }

    /** Get the number of received packets within the bitmap */
    private static long getRxPacketInWindow(byte[] replayBitmap) {
        long pktCntInWindow = 0;

        for (byte b : replayBitmap) {
            // Convert the byte to int to perform bit shift correctly
            int bInt = b & 0xff;
            for (int i = 0; i < 8; i++) {
                if ((bInt & 1) == 1) {
                    pktCntInWindow++;
                }
                bInt = bInt >>> 1;
            }
        }

        return pktCntInWindow;
    }
}
