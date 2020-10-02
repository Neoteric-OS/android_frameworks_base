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


import static com.android.server.vcn.UnderlyingNetworkTracker.UnderlyingNetworkRecord;
import static com.android.server.vcn.VcnTestUtils.setupIpSecManager;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.annotation.NonNull;
import android.content.Context;
import android.net.IpSecManager;
import android.net.IpSecTunnelInterfaceResponse;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.vcn.VcnGatewayConnectionConfig;
import android.net.vcn.VcnGatewayConnectionConfigTest;
import android.os.ParcelUuid;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.IpSecService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

/** Tests for VcnGatewayConnection.DisconnectedState */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class VcnGatewayConnectionDisconnectedStateTest {
    private static final ParcelUuid TEST_SUB_GRP = new ParcelUuid(UUID.randomUUID());
    private static final int TEST_IPSEC_RESOURCE_ID = 1;
    private static final String TEST_IPSEC_TUNNEL_IFACE = "IPSEC_IFACE";

    @NonNull private final Context mContext;
    @NonNull private final TestLooper mTestLooper;
    @NonNull private final VcnNetworkProvider mVcnNetworkProvider;
    @NonNull private final VcnContext mVcnContext;
    @NonNull private final VcnGatewayConnectionConfig mConfig;
    @NonNull private final VcnGatewayConnection.Dependencies mDeps;

    @NonNull private final IpSecService mIpSecSvc;
    @NonNull private final IpSecManager mIpSecMgr;

    private VcnGatewayConnection mGatewayConnection;

    public VcnGatewayConnectionDisconnectedStateTest() {
        mContext = mock(Context.class);
        mTestLooper = new TestLooper();
        mVcnNetworkProvider = mock(VcnNetworkProvider.class);
        mVcnContext = mock(VcnContext.class);
        mConfig = VcnGatewayConnectionConfigTest.buildTestConfig();
        mDeps = mock(VcnGatewayConnection.Dependencies.class);

        mIpSecSvc = mock(IpSecService.class);
        mIpSecMgr = setupIpSecManager(mContext, mIpSecSvc);

        doReturn(mContext).when(mVcnContext).getContext();
        doReturn(mTestLooper.getLooper()).when(mVcnContext).getLooper();
        doReturn(mVcnNetworkProvider).when(mVcnContext).getVcnNetworkProvider();
    }

    @Before
    public void setUp() throws Exception {
        IpSecTunnelInterfaceResponse resp =
                new IpSecTunnelInterfaceResponse(
                        IpSecManager.Status.OK, TEST_IPSEC_RESOURCE_ID, TEST_IPSEC_TUNNEL_IFACE);
        doReturn(resp).when(mIpSecSvc).createTunnelInterface(any(), any(), any(), any(), any());

        mGatewayConnection = new VcnGatewayConnection(mVcnContext, TEST_SUB_GRP, mConfig, mDeps);
    }

    @Test
    public void testNetworkChangesTriggerStateTransitions() throws Exception {
        mGatewayConnection.transitionTo(mGatewayConnection.mDisconnectedState);
        mTestLooper.dispatchAll();

        final UnderlyingNetworkRecord underlying =
                new UnderlyingNetworkRecord(
                        new Network(0),
                        new NetworkCapabilities(),
                        new LinkProperties(),
                        false /* blocked */);
        mGatewayConnection.onSelectedUnderlyingNetworkChanged(underlying);
        mTestLooper.dispatchAll();

        assertEquals(mGatewayConnection.mDnsResolutionState, mGatewayConnection.getCurrentState());
    }
}
