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

import static android.net.vcn.VcnManager.VCN_NETWORK_SELECTION_PENALTY_TIMEOUT_MIN_LIST_KEY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.IpSecTransform;
import android.net.Network;
import android.net.vcn.VcnGatewayConnectionConfig;

import com.android.server.vcn.routeselection.NetworkMetricMonitor.NetworkMetricMonitorCallback;
import com.android.server.vcn.routeselection.UnderlyingNetworkEvaluatorNextNwMonitor.Dependencies;
import com.android.server.vcn.routeselection.UnderlyingNetworkEvaluatorNextNwMonitor.NetworkEvaluatorCallback;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.concurrent.TimeUnit;

public class UnderlyingNetworkEvalNextNwMonitorTest extends UnderlyingNetworkEvaluatorTest {
    private static final int PENALTY_TIMEOUT_MIN = 10;
    private static final long PENALTY_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(PENALTY_TIMEOUT_MIN);

    @Mock private IpSecPacketLossDetector mIpSecPacketLossDetector;
    @Mock private Dependencies mDependencies;
    @Mock private NetworkEvaluatorCallback mEvaluatorCallback;

    @Captor private ArgumentCaptor<NetworkMetricMonitorCallback> mMetricMonitorCbCaptor;

    private UnderlyingNetworkEvaluatorNextNwMonitor mNetworkEvaluator;

    @Before
    public void setUp() {
        super.setUp();

        when(mDependencies.newIpSecPacketLossDetector(any(), any(), any(), any()))
                .thenReturn(mIpSecPacketLossDetector);

        when(mCarrierConfig.getIntArray(
                        eq(VCN_NETWORK_SELECTION_PENALTY_TIMEOUT_MIN_LIST_KEY), anyObject()))
                .thenReturn(new int[] {PENALTY_TIMEOUT_MIN});

        mNetworkEvaluator = newUnderlyingNetworkEvaluator();
    }

    private UnderlyingNetworkEvaluatorNextNwMonitor newUnderlyingNetworkEvaluator() {
        final UnderlyingNetworkEvaluatorNextNwMonitor evaluator =
                new UnderlyingNetworkEvaluatorNextNwMonitor(
                        mVcnContext,
                        mNetwork,
                        VcnGatewayConnectionConfig.DEFAULT_UNDERLYING_NETWORK_TEMPLATES,
                        SUB_GROUP,
                        mSubscriptionSnapshot,
                        mCarrierConfig,
                        mEvaluatorCallback,
                        mDependencies);
        evaluator.setNetworkCapabilities(CELL_NETWORK_CAPABILITIES);
        evaluator.setLinkProperties(LINK_PROPERTIES);
        evaluator.setIsBlocked(false /* isBlocked */);

        return evaluator;
    }

    private static UnderlyingNetworkRecord newTestNetworkRecord(Network network) {
        return new UnderlyingNetworkRecord(
                network, CELL_NETWORK_CAPABILITIES, LINK_PROPERTIES, false);
    }

    private void checkSetSelectedNetwork(
            UnderlyingNetworkRecord selected, boolean isSelectedExpected) {
        mNetworkEvaluator.setSelectedNetwork(selected);
        verify(mIpSecPacketLossDetector).setIsSelected(isSelectedExpected);
    }

    @Test
    public void testSetSelectedNetwork_selected() throws Exception {
        checkSetSelectedNetwork(newTestNetworkRecord(mNetwork), true /* isSelectedExpected */);
    }

    @Test
    public void testSetSelectedNetwork_unselected() throws Exception {
        checkSetSelectedNetwork(
                newTestNetworkRecord(mock(Network.class)), false /* isSelectedExpected */);
    }

    @Test
    public void testSetSelectedNetwork_selectNull() throws Exception {
        checkSetSelectedNetwork(null, false /* isSelectedExpected */);
    }

    @Test
    public void testSetIpSecTransform_onSelectedNetwork() throws Exception {
        // Make the network selected
        mNetworkEvaluator.setSelectedNetwork(newTestNetworkRecord(mNetwork));
        mNetworkEvaluator.setIpSecTransform(makeDummyIpSecTransform());

        verify(mIpSecPacketLossDetector).setIpSecTransform(any(IpSecTransform.class));
    }

    @Test
    public void testSetIpSecTransform_onUnSelectedNetwork() throws Exception {
        mNetworkEvaluator.setIpSecTransform(makeDummyIpSecTransform());

        verify(mIpSecPacketLossDetector, never()).setIpSecTransform(any());
    }

    @Test
    public void close() throws Exception {
        mNetworkEvaluator.close();

        verify(mIpSecPacketLossDetector).close();
        assertNull(mTestLooper.nextMessage());
    }

    private NetworkMetricMonitorCallback getMetricMonitorCbCaptor() {
        verify(mDependencies)
                .newIpSecPacketLossDetector(any(), any(), any(), mMetricMonitorCbCaptor.capture());

        return mMetricMonitorCbCaptor.getValue();
    }

    private void checkPenalizeNetwork() throws Exception {
        assertFalse(mNetworkEvaluator.isPenalized());

        // Validation failed
        when(mIpSecPacketLossDetector.isValidationFailed()).thenReturn(true);
        getMetricMonitorCbCaptor().onValidationResultChanged();

        // Verify the evaluator is penalized
        assertTrue(mNetworkEvaluator.isPenalized());
        verify(mEvaluatorCallback).onEvaluationResultChanged();
    }

    @Test
    public void testRcvValidationResult_penalizeNetwork_penaltyTimeout() throws Exception {
        checkPenalizeNetwork();

        // Penalty timeout
        mTestLooper.moveTimeForward(PENALTY_TIMEOUT_MS);
        mTestLooper.dispatchAll();

        // Verify the evaluator is not penalized
        assertFalse(mNetworkEvaluator.isPenalized());
        verify(mEvaluatorCallback, times(2)).onEvaluationResultChanged();
    }

    @Test
    public void testRcvValidationResult_penalizeNetwork_passValidation() throws Exception {
        checkPenalizeNetwork();

        // Validation passed
        when(mIpSecPacketLossDetector.isValidationFailed()).thenReturn(false);
        getMetricMonitorCbCaptor().onValidationResultChanged();

        // Verify the evaluator is not penalized and penalty timeout is canceled
        assertFalse(mNetworkEvaluator.isPenalized());
        verify(mEvaluatorCallback, times(2)).onEvaluationResultChanged();
        mTestLooper.moveTimeForward(PENALTY_TIMEOUT_MS);
        assertNull(mTestLooper.nextMessage());
    }

    @Test
    public void testRcvValidationResult_penalizeNetwork_closeEvaluator() throws Exception {
        checkPenalizeNetwork();

        mNetworkEvaluator.close();

        // Verify penalty timeout is canceled
        mTestLooper.moveTimeForward(PENALTY_TIMEOUT_MS);
        assertNull(mTestLooper.nextMessage());
    }

    @Test
    public void testRcvValidationResult_PenaltyStateUnchanged() throws Exception {
        assertFalse(mNetworkEvaluator.isPenalized());

        // Validation passed
        when(mIpSecPacketLossDetector.isValidationFailed()).thenReturn(false);
        getMetricMonitorCbCaptor().onValidationResultChanged();

        // Verifications
        assertFalse(mNetworkEvaluator.isPenalized());
        verify(mEvaluatorCallback, never()).onEvaluationResultChanged();
    }

    @Test
    public void testCompare() throws Exception {
        when(mIpSecPacketLossDetector.isValidationFailed()).thenReturn(true);
        getMetricMonitorCbCaptor().onValidationResultChanged();

        final UnderlyingNetworkEvaluatorNextNwMonitor notPenalized =
                newUnderlyingNetworkEvaluator();

        assertTrue(mNetworkEvaluator.isPenalized());
        assertFalse(notPenalized.isPenalized());
        assertEquals(mNetworkEvaluator.getPriorityClass(), notPenalized.getPriorityClass());

        final int result =
                UnderlyingNetworkEvaluatorNextNwMonitor.getComparatorNextNwMonitor()
                        .compare(mNetworkEvaluator, notPenalized);
        assertEquals(-1, result);
    }
}
