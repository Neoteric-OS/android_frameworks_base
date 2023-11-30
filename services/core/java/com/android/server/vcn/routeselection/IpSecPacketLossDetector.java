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

package com.android.server.vcn.routeselection;

import android.annotation.Nullable;
import android.net.IpSecTransformState;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.OutcomeReceiver;
import android.util.Log;

import com.android.server.vcn.VcnContext;

import java.util.concurrent.TimeUnit;

/**
 * NetworkMetricMonitor is responsible for managing metric monitoring and tracking the validation
 * result.
 */
class IpSecPacketLossDetector extends NetworkMetricMonitor {
    private static final String TAG = IpSecPacketLossDetector.class.getSimpleName();

    private static final int PACKET_LOSS_RATE_UNAVALAIBLE = -1;

    // TODO: Read them from CarrierConfig
    private static final long POLL_IPSEC_STATE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(10L);
    private static final int PACKET_LOSS_RATE_PERCENTAGE_THRESHOLD = 50;

    private final Handler mHandler;
    private final Object mPollIpSecStateToken;

    private boolean mIsStarted;

    @Nullable private IpSecTransformState mLastIpSecTransformState;

    IpSecPacketLossDetector(VcnContext vcnContext, NetworkMetricMonitorCallback callback) {
        super(vcnContext, callback);
        mHandler = new Handler(getVcnContext().getLooper());
        mPollIpSecStateToken = new Object();
        mIsStarted = false;
    }

    @Override
    String getTag() {
        return TAG;
    }

    @Override
    void startOrStop() {
        if (getTransformIn() != null) {
            // Start
            if (mIsStarted) {
                // Already started
                return;
            }
            mIsStarted = true;
            mHandler.postDelayed(new PollIpSecStateRunnable(), mPollIpSecStateToken, 0L);
        } else {
            stop();
        }
    }

    @Override
    void stop() {
        mIsStarted = false;
        mHandler.removeCallbacksAndEqualMessages(mPollIpSecStateToken);
        mLastIpSecTransformState = null;
    }

    @Override
    boolean isStarted() {
        return mIsStarted;
    }

    private class PollIpSecStateRunnable implements Runnable {
        @Override
        public void run() {
            if (!mIsStarted) {
                Log.wtf(TAG, "Monitor stopped but PollIpSecStateRunnable not removed from Handler");
                return;
            }

            getTransformIn()
                    .getIpSecTransformState(
                            new HandlerExecutor(new Handler(getVcnContext().getLooper())),
                            new TransformStateReceiver());

            mHandler.postDelayed(
                    new PollIpSecStateRunnable(),
                    mPollIpSecStateToken,
                    POLL_IPSEC_STATE_INTERVAL_MS);
        }
    }

    private class TransformStateReceiver
            implements OutcomeReceiver<IpSecTransformState, RuntimeException> {
        public void onResult(IpSecTransformState state) {
            logD("TransformStateReceiver#onResult");

            if (!isStarted()) {
                logD("Monitor is stopped. Ignore the result");
                return;
            }

            onReceiveTransformState(state);
        }

        public void onError(RuntimeException error) {
            // Nothing we can do here
            logD("TransformStateReceiver#onError " + error.toString());
        }
    }

    private void onReceiveTransformState(IpSecTransformState state) {
        if (mLastIpSecTransformState == null) {
            // This is first time to poll the state
            mLastIpSecTransformState = state;
            return;
        } else {
            final int packetLossRate = getPacketLossRatePercentage(mLastIpSecTransformState, state);
            logD(
                    "packetLossRate "
                            + packetLossRate
                            + " %  from "
                            + mLastIpSecTransformState.getTimestamp()
                            + " to "
                            + state.getTimestamp());

            if (packetLossRate == PACKET_LOSS_RATE_UNAVALAIBLE) {
                return;
            } else if (packetLossRate < PACKET_LOSS_RATE_PERCENTAGE_THRESHOLD) {
                mLastIpSecTransformState = state;
                onValidationResultReceived(false /* isFailed */);
            } else {
                onValidationResultReceived(true /* isFailed */);
                // TODO: Add a dumpsys
            }
        }
    }

    static int getPacketLossRatePercentage(IpSecTransformState before, IpSecTransformState after) {
        final long diffHiSeqNum =
                after.getRxHighestSequenceNumber() - before.getRxHighestSequenceNumber();
        final long diffPktCnt = after.getPacketCount() - before.getPacketCount();
        final byte[] replayBitmap = after.getReplayBitmap();
        final long replayWindowSize = replayBitmap.length * 8;

        long pktCntWithinWindow = 0;
        for (byte b : replayBitmap) {
            while (b > 0) {
                if ((b & 1) == 1) {
                    pktCntWithinWindow++;
                }
                b = (byte) (b >>> 1);
            }
        }

        final long expectPktCntBetweenWindows = diffHiSeqNum - replayWindowSize;
        if (expectPktCntBetweenWindows < 0 || expectPktCntBetweenWindows == 0) {
            Log.d(TAG, "Didn't receive enough packet to estimate packet" + " loss");
            return PACKET_LOSS_RATE_UNAVALAIBLE;
        }

        final long actualPktCntBetweenWindows = diffPktCnt - pktCntWithinWindow;
        if (actualPktCntBetweenWindows < 0) {
            Log.wtf(TAG, "Impossible data: actualPktCntBetweenWindows smaller than zero");
            return PACKET_LOSS_RATE_UNAVALAIBLE;
        }

        return (int) (100 - (actualPktCntBetweenWindows * 100) / expectPktCntBetweenWindows);
    }
}
