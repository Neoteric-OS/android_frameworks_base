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

import static android.net.vcn.VcnManager.VCN_NETWORK_SELECTION_IPSEC_PACKET_LOSS_PERCENT_THRESHOLD_KEY;
import static android.net.vcn.VcnManager.VCN_NETWORK_SELECTION_POLL_IPSEC_STATE_INTERVAL_SECONDS_KEY;

import static com.android.server.vcn.util.PersistableBundleUtils.PersistableBundleWrapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.IpSecTransformState;
import android.os.OutcomeReceiver;

import com.android.server.vcn.routeselection.IpSecPacketLossDetector.PacketLossCalculator;
import com.android.server.vcn.routeselection.NetworkMetricMonitor.IpSecTransformWrapper;
import com.android.server.vcn.routeselection.NetworkMetricMonitor.NetworkMetricMonitorCallback;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.concurrent.TimeUnit;

public class IpSecPacketLossDetectorTest extends NetworkSelectionTestBase {
    private static final String TAG = IpSecPacketLossDetectorTest.class.getSimpleName();

    private static final int REPLAY_BITMAP_LEN = 512;
    private static final int IPSEC_PACKET_LOSS_PERCENT_THRESHOLD = 5;
    private static final long POLL_IPSEC_STATE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(30L);

    @Mock private IpSecTransformWrapper mIpSecTransform;
    @Mock private NetworkMetricMonitorCallback mMetricMonitorCallback;
    @Mock private PersistableBundleWrapper mCarrierConfig;
    @Mock private IpSecPacketLossDetector.Dependencies mDependencies;
    @Spy private PacketLossCalculator mPacketLossCalculator = new PacketLossCalculator();

    @Captor private ArgumentCaptor<OutcomeReceiver> mTransformStateReceiverCaptor;

    private IpSecPacketLossDetector mIpSecPacketLossDetector;
    private IpSecTransformState mTransformStateInitial;

    @Before
    public void setUp() {
        super.setUp();
        doReturn(true).when(mFeatureFlags).networkMetricMonitor();
        doReturn(true).when(mCoreNetFeatureFlags).ipsecTransformState();

        mTransformStateInitial = newTransformState(0, 0, newReplayBitmap(0));

        when(mCarrierConfig.getInt(
                        eq(VCN_NETWORK_SELECTION_POLL_IPSEC_STATE_INTERVAL_SECONDS_KEY), anyInt()))
                .thenReturn((int) TimeUnit.MILLISECONDS.toSeconds(POLL_IPSEC_STATE_INTERVAL_MS));
        when(mCarrierConfig.getInt(
                        eq(VCN_NETWORK_SELECTION_IPSEC_PACKET_LOSS_PERCENT_THRESHOLD_KEY),
                        anyInt()))
                .thenReturn(IPSEC_PACKET_LOSS_PERCENT_THRESHOLD);

        when(mDependencies.getPacketLossCalculator()).thenReturn(mPacketLossCalculator);

        mIpSecPacketLossDetector =
                new IpSecPacketLossDetector(
                        mVcnContext,
                        mNetwork,
                        mCarrierConfig,
                        mMetricMonitorCallback,
                        mDependencies);
    }

    private static IpSecTransformState newTransformState(
            long rxSeqNo, long packtCount, byte[] replayBitmap) {
        return new IpSecTransformState.Builder()
                .setRxHighestSequenceNumber(rxSeqNo)
                .setPacketCount(packtCount)
                .setReplayBitmap(replayBitmap)
                .build();
    }

    private static byte[] newReplayBitmap(int receivedPktCnt) {
        final byte[] bitmap = new byte[REPLAY_BITMAP_LEN];
        if (receivedPktCnt == 0) {
            return bitmap;
        }

        for (int i = 0; i < REPLAY_BITMAP_LEN; i++) {
            if (receivedPktCnt < 8 || receivedPktCnt == 8) {
                int b = 1;
                for (int j = 1; j < receivedPktCnt; j++) {
                    b = b << 1;
                    b++;
                }
                bitmap[i] = (byte) b;
                return bitmap;
            }

            bitmap[i] = (byte) 0xff;
            receivedPktCnt -= 8;
        }
        return bitmap;
    }

    private static byte[] newReplayBitmapAllReceived() {
        return newReplayBitmap(REPLAY_BITMAP_LEN * 8);
    }

    private void verifyStopped() {
        assertFalse(mIpSecPacketLossDetector.isValidationFailed());
        assertFalse(mIpSecPacketLossDetector.isStarted());
        assertNull(mTestLooper.nextMessage());
    }

    @Test
    public void testInitialization() throws Exception {
        assertFalse(mIpSecPacketLossDetector.isSelected());
        verifyStopped();
    }

    private OutcomeReceiver<IpSecTransformState, RuntimeException>
            startMonitorAndCaptureStateReceiver() {
        mIpSecPacketLossDetector.setIsSelected(true /* setIsSelected */);
        mIpSecPacketLossDetector.setIpSecTransformInternal(mIpSecTransform);

        // Trigger the runnable
        mTestLooper.dispatchAll();

        verify(mIpSecTransform)
                .getIpSecTransformState(any(), mTransformStateReceiverCaptor.capture());
        return mTransformStateReceiverCaptor.getValue();
    }

    @Test
    public void testStartMonitor() throws Exception {
        final OutcomeReceiver<IpSecTransformState, RuntimeException> xformStateReceiver =
                startMonitorAndCaptureStateReceiver();

        assertFalse(mIpSecPacketLossDetector.isValidationFailed());
        assertTrue(mIpSecPacketLossDetector.isSelected());
        assertTrue(mIpSecPacketLossDetector.isStarted());
        assertEquals(mIpSecTransform, mIpSecPacketLossDetector.getTransformInInternal());

        // Mock receiving a state
        xformStateReceiver.onResult(mTransformStateInitial);

        // Verify the first polled state is stored
        assertEquals(mTransformStateInitial, mIpSecPacketLossDetector.getLastTransformState());
        verify(mPacketLossCalculator, never())
                .getPacketLossRatePercentage(any(), any(), anyString());

        // Verify next poll is scheduled
        assertNull(mTestLooper.nextMessage());
        mTestLooper.moveTimeForward(POLL_IPSEC_STATE_INTERVAL_MS);
        assertNotNull(mTestLooper.nextMessage());
    }

    @Test
    public void testStopMonitor() throws Exception {
        mIpSecPacketLossDetector.setIsSelected(true /* setIsSelected */);
        mIpSecPacketLossDetector.setIpSecTransformInternal(mIpSecTransform);

        assertTrue(mIpSecPacketLossDetector.isStarted());
        assertNotNull(mTestLooper.nextMessage());

        // Unselect the monitor
        mIpSecPacketLossDetector.setIsSelected(false /* setIsSelected */);
        verifyStopped();
    }

    @Test
    public void testClose() throws Exception {
        mIpSecPacketLossDetector.setIsSelected(true /* setIsSelected */);
        mIpSecPacketLossDetector.setIpSecTransformInternal(mIpSecTransform);

        assertTrue(mIpSecPacketLossDetector.isStarted());
        assertNotNull(mTestLooper.nextMessage());

        // Stop the monitor
        mIpSecPacketLossDetector.close();
        verifyStopped();
        verify(mIpSecTransform).close();
    }

    @Test
    public void testTransformStateReceiverOnResultWhenStopped() throws Exception {
        final OutcomeReceiver<IpSecTransformState, RuntimeException> xformStateReceiver =
                startMonitorAndCaptureStateReceiver();
        xformStateReceiver.onResult(mTransformStateInitial);

        // Unselect the monitor
        mIpSecPacketLossDetector.setIsSelected(false /* setIsSelected */);
        verifyStopped();

        xformStateReceiver.onResult(newTransformState(1, 1, newReplayBitmap(1)));
        verify(mPacketLossCalculator, never())
                .getPacketLossRatePercentage(any(), any(), anyString());
    }

    @Test
    public void testTransformStateReceiverOnError() throws Exception {
        final OutcomeReceiver<IpSecTransformState, RuntimeException> xformStateReceiver =
                startMonitorAndCaptureStateReceiver();
        xformStateReceiver.onResult(mTransformStateInitial);

        xformStateReceiver.onError(new RuntimeException("Test"));
        verify(mPacketLossCalculator, never())
                .getPacketLossRatePercentage(any(), any(), anyString());
    }

    private void checkHandleLossRate(
            int mockPacketLossRate, boolean isLastStateExpectedToUpdate, boolean isCallbackExpected)
            throws Exception {
        final OutcomeReceiver<IpSecTransformState, RuntimeException> xformStateReceiver =
                startMonitorAndCaptureStateReceiver();
        doReturn(mockPacketLossRate)
                .when(mPacketLossCalculator)
                .getPacketLossRatePercentage(any(), any(), anyString());

        // Mock receiving two states with mTransformStateInitial and an arbitrary transformNew
        final IpSecTransformState transformNew = newTransformState(1, 1, newReplayBitmap(1));
        xformStateReceiver.onResult(mTransformStateInitial);
        xformStateReceiver.onResult(transformNew);

        // Verifications
        verify(mPacketLossCalculator)
                .getPacketLossRatePercentage(
                        eq(mTransformStateInitial), eq(transformNew), anyString());

        if (isLastStateExpectedToUpdate) {
            assertEquals(transformNew, mIpSecPacketLossDetector.getLastTransformState());
        } else {
            assertEquals(mTransformStateInitial, mIpSecPacketLossDetector.getLastTransformState());
        }

        if (isCallbackExpected) {
            verify(mMetricMonitorCallback).onValidationResultChanged();
        } else {
            verify(mMetricMonitorCallback, never()).onValidationResultChanged();
        }
    }

    @Test
    public void testHandleLossRate_validationPass() throws Exception {
        checkHandleLossRate(
                2, true /* isLastStateExpectedToUpdate */, false /* isCallbackExpected */);
    }

    @Test
    public void testHandleLossRate_validationFail() throws Exception {
        checkHandleLossRate(
                22, true /* isLastStateExpectedToUpdate */, true /* isCallbackExpected */);
    }

    @Test
    public void testHandleLossRate_resultUnavalaible() throws Exception {
        checkHandleLossRate(
                IpSecPacketLossDetector.PACKET_LOSS_UNAVALAIBLE,
                false /* isLastStateExpectedToUpdate */,
                false /* isCallbackExpected */);
    }

    @Test
    public void testCalculatePacketLoss_hiSeqSmallerThanWinSize() throws Exception {
        final IpSecTransformState stateHiSeqSmallerThanWinSize =
                newTransformState(3000, 3000, newReplayBitmap(3000));
        checkCalculatePacketLoss(mTransformStateInitial, stateHiSeqSmallerThanWinSize, 0);
    }

    @Test
    public void testCalculatePacketLoss_windowsOverlapped() throws Exception {
        final IpSecTransformState oldState =
                newTransformState(5000, 5000, newReplayBitmapAllReceived());
        final IpSecTransformState newState =
                newTransformState(7000, 7000, newReplayBitmapAllReceived());
        checkCalculatePacketLoss(oldState, newState, 0);
    }

    @Test
    public void testCalculatePacketLoss_pass() throws Exception {
        final IpSecTransformState stateNotLossy =
                newTransformState(9999, 10000, newReplayBitmapAllReceived());
        checkCalculatePacketLoss(mTransformStateInitial, stateNotLossy, 0);
    }

    @Test
    public void testCalculatePacketLoss_fail() throws Exception {
        final IpSecTransformState stateLossy = newTransformState(9999, 5000, newReplayBitmap(4000));
        checkCalculatePacketLoss(mTransformStateInitial, stateLossy, 50);
    }

    @Test
    public void testCalculatePacketLoss_replayWindowNotChanged() throws Exception {
        checkCalculatePacketLoss(
                mTransformStateInitial,
                mTransformStateInitial,
                IpSecPacketLossDetector.PACKET_LOSS_UNAVALAIBLE);
    }

    private void checkCalculatePacketLoss(
            IpSecTransformState oldState, IpSecTransformState newState, int expectedLossRate)
            throws Exception {
        assertEquals(
                expectedLossRate,
                mPacketLossCalculator.getPacketLossRatePercentage(oldState, newState, TAG));
    }
}
