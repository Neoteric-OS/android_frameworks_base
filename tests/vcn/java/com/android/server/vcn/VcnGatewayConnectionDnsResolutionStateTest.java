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

package com.android.server.vcn;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import android.net.DnsResolver;
import android.os.CancellationSignal;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

/** Tests for VcnGatewayConnection.DisconnectedState */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class VcnGatewayConnectionDnsResolutionStateTest extends VcnGatewayConnectionTestBase {
    @Before
    public void setUp() throws Exception {
        super.setUp();

        mGatewayConnection.setUnderlyingNetwork(TEST_UNDERLYING_NETWORK_RECORD_1);
        mGatewayConnection.transitionTo(mGatewayConnection.mDnsResolutionState);
        mTestLooper.dispatchAll();
    }

    @Test
    public void testNetworkChangesTriggerReevaluation() throws Exception {
        ArgumentCaptor<CancellationSignal> captor =
                ArgumentCaptor.forClass(CancellationSignal.class);
        verify(mDeps).queryDns(
                any(),
                anyInt(),
                eq(TEST_UNDERLYING_NETWORK_RECORD_1.network),
                any(),
                captor.capture(),
                any());
        CancellationSignal cancellationSignal = captor.getValue();
        assertNotNull(cancellationSignal);
        assertFalse(cancellationSignal.isCanceled());

        // Re-trigger an underlying network change
        mGatewayConnection.onSelectedUnderlyingNetworkChanged(TEST_UNDERLYING_NETWORK_RECORD_2);
        mTestLooper.dispatchAll();

        assertTrue(cancellationSignal.isCanceled());
        verify(mDeps).queryDns(
                any(),
                anyInt(),
                eq(TEST_UNDERLYING_NETWORK_RECORD_2.network),
                any(),
                any(),
                any());
        assertEquals(mGatewayConnection.mDnsResolutionState, mGatewayConnection.getCurrentState());
        assertEquals(TEST_UNDERLYING_NETWORK_RECORD_2, mGatewayConnection.getUnderlyingNetwork());
    }

    @Test
    public void testDnsResolutionTransitionsToConnectingState() throws Exception {
        ArgumentCaptor<DnsResolver.Callback<List<InetAddress>>> captor =
                ArgumentCaptor.forClass(DnsResolver.Callback.class);
        verify(mDeps).queryDns(any(), anyInt(), any(), any(), any(), captor.capture());
        DnsResolver.Callback<List<InetAddress>> callback = captor.getValue();

        callback.onAnswer(
                Collections.singletonList(VcnGatewayConnection.DUMMY_ADDR),
                VcnGatewayConnection.DNS_NOERROR);
        mTestLooper.dispatchAll();

        assertEquals(mGatewayConnection.mConnectingState, mGatewayConnection.getCurrentState());
    }

    @Test
    public void testTeardownCancelsDnsQuery() throws Exception {
        ArgumentCaptor<CancellationSignal> captor =
                ArgumentCaptor.forClass(CancellationSignal.class);
        verify(mDeps).queryDns(
                any(),
                anyInt(),
                eq(TEST_UNDERLYING_NETWORK_RECORD_1.network),
                any(),
                captor.capture(),
                any());
        CancellationSignal cancellationSignal = captor.getValue();
        assertNotNull(cancellationSignal);
        assertFalse(cancellationSignal.isCanceled());

        // Re-trigger an underlying network change
        mGatewayConnection.teardown();
        mTestLooper.dispatchAll();

        assertTrue(cancellationSignal.isCanceled());
        verify(mUnderlyingNetworkTracker).teardown();
    }

    @Test
    public void testSessionLostCancelsDnsQueryAndRetries() throws Exception {
        ArgumentCaptor<Integer> tokenCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<CancellationSignal> cancellationSignalCaptor =
                ArgumentCaptor.forClass(CancellationSignal.class);
        verify(mDeps).queryDns(
                any(),
                tokenCaptor.capture(),
                eq(TEST_UNDERLYING_NETWORK_RECORD_1.network),
                any(),
                cancellationSignalCaptor.capture(),
                any());
        CancellationSignal cancellationSignal = cancellationSignalCaptor.getValue();
        assertNotNull(cancellationSignal);
        assertFalse(cancellationSignal.isCanceled());

        // Re-trigger an underlying network change
        mGatewayConnection.sessionLost(
                tokenCaptor.getValue(), new RuntimeException("Test Session Lost"));
        mTestLooper.dispatchAll();

        assertTrue(cancellationSignal.isCanceled());
        assertEquals(mGatewayConnection.mRetryTimeoutState, mGatewayConnection.getCurrentState());
    }
}
