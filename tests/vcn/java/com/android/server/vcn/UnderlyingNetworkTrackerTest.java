/*
 * Copyright (C) 2021 The Android Open Source Project
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
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TelephonyNetworkSpecifier;
import android.os.ParcelUuid;
import android.os.test.TestLooper;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.ArraySet;

import com.android.server.vcn.UnderlyingNetworkTracker.NetworkBringupCallback;
import com.android.server.vcn.UnderlyingNetworkTracker.RouteSelectionCallback;
import com.android.server.vcn.UnderlyingNetworkTracker.UnderlyingNetworkRecord;
import com.android.server.vcn.UnderlyingNetworkTracker.UnderlyingNetworkTrackerCallback;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class UnderlyingNetworkTrackerTest {
    private static final ParcelUuid INITIAL_SUB_GROUP = new ParcelUuid(new UUID(0, 0));
    private static final int INITIAL_SUB_ID_1 = 1;
    private static final int INITIAL_SUB_ID_2 = 2;

    private static final NetworkCapabilities INITIAL_NETWORK_CAPABILITIES =
            new NetworkCapabilities.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .build();
    private static final NetworkCapabilities SUSPENDED_NETWORK_CAPABILITIES =
            new NetworkCapabilities.Builder(INITIAL_NETWORK_CAPABILITIES)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
                    .build();
    private static final NetworkCapabilities UPDATED_NETWORK_CAPABILITIES =
            new NetworkCapabilities.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();

    private static final LinkProperties INITIAL_LINK_PROPERTIES =
            getLinkPropertiesWithName("initial_iface");
    private static final LinkProperties UPDATED_LINK_PROPERTIES =
            getLinkPropertiesWithName("updated_iface");

    @Mock private Context mContext;
    @Mock private VcnNetworkProvider mVcnNetworkProvider;
    @Mock private ConnectivityManager mConnectivityManager;
    @Mock private SubscriptionManager mSubscriptionManager;
    @Mock private UnderlyingNetworkTrackerCallback mNetworkTrackerCb;
    @Mock private Network mNetwork;

    @Captor private ArgumentCaptor<RouteSelectionCallback> mRouteSelectionCallbackCaptor;

    private TestLooper mTestLooper;
    private VcnContext mVcnContext;
    private UnderlyingNetworkTracker mUnderlyingNetworkTracker;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mTestLooper = new TestLooper();
        mVcnContext = spy(new VcnContext(mContext, mTestLooper.getLooper(), mVcnNetworkProvider));
        doNothing().when(mVcnContext).ensureRunningOnLooperThread();

        setupSystemService(
                mConnectivityManager, Context.CONNECTIVITY_SERVICE, ConnectivityManager.class);
        setupSystemService(
                mSubscriptionManager,
                Context.TELEPHONY_SUBSCRIPTION_SERVICE,
                SubscriptionManager.class);

        List<SubscriptionInfo> initialSubInfos =
                Arrays.asList(
                        getSubscriptionInfoForSubId(INITIAL_SUB_ID_1),
                        getSubscriptionInfoForSubId(INITIAL_SUB_ID_2));
        when(mSubscriptionManager.getSubscriptionsInGroup(eq(INITIAL_SUB_GROUP)))
                .thenReturn(initialSubInfos);

        Set<Integer> requiredUnderlyingNetworkCapabilities = new ArraySet<>();
        requiredUnderlyingNetworkCapabilities.add(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        mUnderlyingNetworkTracker =
                new UnderlyingNetworkTracker(
                        mVcnContext,
                        INITIAL_SUB_GROUP,
                        requiredUnderlyingNetworkCapabilities,
                        mNetworkTrackerCb);
    }

    private static LinkProperties getLinkPropertiesWithName(String iface) {
        LinkProperties linkProperties = new LinkProperties();
        linkProperties.setInterfaceName(iface);
        return linkProperties;
    }

    private void setupSystemService(Object service, String name, Class<?> serviceClass) {
        when(mContext.getSystemServiceName(eq(serviceClass))).thenReturn(name);
        when(mContext.getSystemService(eq(name))).thenReturn(service);
    }

    private SubscriptionInfo getSubscriptionInfoForSubId(int subId) {
        SubscriptionInfo subInfo = mock(SubscriptionInfo.class);
        when(subInfo.getSubscriptionId()).thenReturn(subId);
        return subInfo;
    }

    @Test
    public void testNetworkCallbacksRegisteredOnStartup() {
        // verify NetworkCallbacks registered when instantiated
        verify(mConnectivityManager)
                .requestBackgroundNetwork(
                        eq(getExpectedWifiRequest()), any(NetworkBringupCallback.class));
        verify(mConnectivityManager)
                .requestBackgroundNetwork(
                        eq(getExpectedCellRequestForSubId(INITIAL_SUB_ID_1)),
                        any(NetworkBringupCallback.class));
        verify(mConnectivityManager)
                .requestBackgroundNetwork(
                        eq(getExpectedCellRequestForSubId(INITIAL_SUB_ID_2)),
                        any(NetworkBringupCallback.class));
        verify(mConnectivityManager)
                .requestBackgroundNetwork(
                        eq(getRouteSelectionRequest()), any(RouteSelectionCallback.class));

        verify(mSubscriptionManager).getSubscriptionsInGroup(eq(INITIAL_SUB_GROUP));
    }

    private NetworkRequest getExpectedWifiRequest() {
        return getExpectedRequestBase()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
    }

    private NetworkRequest getExpectedCellRequestForSubId(int subId) {
        return getExpectedRequestBase()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .setNetworkSpecifier(new TelephonyNetworkSpecifier(subId))
                .build();
    }

    private NetworkRequest getRouteSelectionRequest() {
        return getExpectedRequestBase().build();
    }

    private NetworkRequest.Builder getExpectedRequestBase() {
        return new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                .addUnwantedCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED);
    }

    @Test
    public void testTeardown() {
        mUnderlyingNetworkTracker.teardown();

        // Expect 3 NetworkBringupCallbacks to be unregistered: 1 for WiFi and 2 for Cellular (1x
        // for each subId)
        verify(mConnectivityManager, times(3))
                .unregisterNetworkCallback(any(NetworkBringupCallback.class));
        verify(mConnectivityManager).unregisterNetworkCallback(any(RouteSelectionCallback.class));
    }

    @Test
    public void testUnderlyingNetworkRecordEquals() {
        UnderlyingNetworkRecord recordA =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        INITIAL_NETWORK_CAPABILITIES,
                        INITIAL_LINK_PROPERTIES,
                        false /* blocked */);
        UnderlyingNetworkRecord recordB =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        INITIAL_NETWORK_CAPABILITIES,
                        INITIAL_LINK_PROPERTIES,
                        false /* blocked */);
        UnderlyingNetworkRecord recordC =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        UPDATED_NETWORK_CAPABILITIES,
                        UPDATED_LINK_PROPERTIES,
                        false /* blocked */);

        assertEquals(recordA, recordB);
        assertNotEquals(recordA, recordC);
    }

    @Test
    public void testRecordTrackerCallbackNotifiedForNetworkChange() {
        verifyOnAvailable();
    }

    private RouteSelectionCallback verifyOnAvailable() {
        return verifyOnAvailable(INITIAL_NETWORK_CAPABILITIES);
    }

    private RouteSelectionCallback verifyOnAvailable(NetworkCapabilities networkCapabilities) {
        verify(mConnectivityManager)
                .requestBackgroundNetwork(
                        eq(getRouteSelectionRequest()), mRouteSelectionCallbackCaptor.capture());

        RouteSelectionCallback cb = mRouteSelectionCallbackCaptor.getValue();
        cb.onAvailable(
                mNetwork, networkCapabilities, INITIAL_LINK_PROPERTIES, false /* isBlocked */);

        UnderlyingNetworkRecord expectedRecord =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        networkCapabilities,
                        INITIAL_LINK_PROPERTIES,
                        false /* blocked */);
        verify(mNetworkTrackerCb).onSelectedUnderlyingNetworkChanged(eq(expectedRecord));
        return cb;
    }

    @Test
    public void testRecordTrackerCallbackNotifiedForNetworkCapabilitiesChange() {
        RouteSelectionCallback cb = verifyOnAvailable();

        cb.onCapabilitiesChanged(mNetwork, UPDATED_NETWORK_CAPABILITIES);

        UnderlyingNetworkRecord expectedRecord =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        UPDATED_NETWORK_CAPABILITIES,
                        INITIAL_LINK_PROPERTIES,
                        false /* blocked */);
        verify(mNetworkTrackerCb).onSelectedUnderlyingNetworkChanged(eq(expectedRecord));
    }

    @Test
    public void testRecordTrackerCallbackNotifiedForLinkPropertiesChange() {
        RouteSelectionCallback cb = verifyOnAvailable();

        cb.onLinkPropertiesChanged(mNetwork, UPDATED_LINK_PROPERTIES);

        UnderlyingNetworkRecord expectedRecord =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        INITIAL_NETWORK_CAPABILITIES,
                        UPDATED_LINK_PROPERTIES,
                        false /* blocked */);
        verify(mNetworkTrackerCb).onSelectedUnderlyingNetworkChanged(eq(expectedRecord));
    }

    @Test
    public void testRecordTrackerCallbackNotifiedForNetworkSuspended() {
        RouteSelectionCallback cb = verifyOnAvailable();

        cb.onNetworkSuspended(mNetwork);

        UnderlyingNetworkRecord expectedRecord =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        SUSPENDED_NETWORK_CAPABILITIES,
                        INITIAL_LINK_PROPERTIES,
                        false /* blocked */);
        verify(mNetworkTrackerCb).onSelectedUnderlyingNetworkChanged(eq(expectedRecord));
    }

    @Test
    public void testRecordTrackerCallbackNotifiedForNetworkResumed() {
        RouteSelectionCallback cb = verifyOnAvailable(SUSPENDED_NETWORK_CAPABILITIES);

        cb.onNetworkResumed(mNetwork);

        UnderlyingNetworkRecord expectedRecord =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        INITIAL_NETWORK_CAPABILITIES,
                        INITIAL_LINK_PROPERTIES,
                        false /* blocked */);
        verify(mNetworkTrackerCb).onSelectedUnderlyingNetworkChanged(eq(expectedRecord));
    }

    @Test
    public void testRecordTrackerCallbackNotifiedForBlocked() {
        RouteSelectionCallback cb = verifyOnAvailable();

        cb.onBlockedStatusChanged(mNetwork, true /* isBlocked */);

        UnderlyingNetworkRecord expectedRecord =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        INITIAL_NETWORK_CAPABILITIES,
                        INITIAL_LINK_PROPERTIES,
                        true /* blocked */);
        verify(mNetworkTrackerCb).onSelectedUnderlyingNetworkChanged(eq(expectedRecord));
    }

    @Test
    public void testRecordTrackerCallbackNotifiedForNetworkLoss() {
        RouteSelectionCallback cb = verifyOnAvailable();

        cb.onLost(mNetwork);

        verify(mNetworkTrackerCb).onSelectedUnderlyingNetworkChanged(null);
    }

    @Test
    public void testRecordTrackerCallbackIgnoresDuplicateRecord() {
        RouteSelectionCallback cb = verifyOnAvailable();

        // reset mNetworkTrackerCb to ignore onSelectedUnderlyingNetworkChanged() call in
        // verifyOnAvailable()
        reset(mNetworkTrackerCb);

        cb.onCapabilitiesChanged(mNetwork, INITIAL_NETWORK_CAPABILITIES);

        verify(mNetworkTrackerCb, never()).onSelectedUnderlyingNetworkChanged(any());
    }
}
