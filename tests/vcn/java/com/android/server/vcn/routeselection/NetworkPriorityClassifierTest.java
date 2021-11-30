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

package com.android.server.vcn.routeselection;

import static android.net.vcn.VcnUnderlyingNetworkPriority.NETWORK_QUALITY_OK;

import static com.android.server.vcn.VcnTestUtils.setupSystemService;
import static com.android.server.vcn.routeselection.NetworkPriorityClassifier.PRIORITY_ANY;
import static com.android.server.vcn.routeselection.NetworkPriorityClassifier.calculatePriorityClass;
import static com.android.server.vcn.routeselection.NetworkPriorityClassifier.matchCellUnderlyingNetworkPriority;
import static com.android.server.vcn.routeselection.NetworkPriorityClassifier.matchWifiUnderlyingNetworkPriority;
import static com.android.server.vcn.routeselection.UnderlyingNetworkControllerTest.getLinkPropertiesWithName;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.vcn.CellUnderlyingNetworkPriority;
import android.net.vcn.VcnGatewayConnectionConfig;
import android.net.vcn.VcnManager;
import android.net.vcn.WifiUnderlyingNetworkPriority;
import android.os.ParcelUuid;
import android.os.PersistableBundle;
import android.os.test.TestLooper;
import android.telephony.TelephonyManager;
import android.util.ArraySet;

import com.android.server.vcn.TelephonySubscriptionTracker.TelephonySubscriptionSnapshot;
import com.android.server.vcn.VcnContext;
import com.android.server.vcn.VcnNetworkProvider;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;
import java.util.UUID;

public class NetworkPriorityClassifierTest {
    private static final String SSID = "TestWifi";

    private static final int WIFI_RSSI = -60;
    private static final int WIFI_RSSI_LOW = -100;

    private static final int SUB_ID = 1;
    private static final int CARRIER_ID = 1;
    private static final int CARRIER_ID_OTHER = 2;
    private static final String PLMN_ID = "123456";
    private static final String PLMN_ID_OTHER = "234567";
    private static final ParcelUuid SUB_GROUP = new ParcelUuid(new UUID(0, 0));

    private static final NetworkCapabilities WIFI_NETWORK_CAPABILITIES =
            new NetworkCapabilities.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setSignalStrength(WIFI_RSSI)
                    .setSsid(SSID)
                    .build();

    private static final NetworkCapabilities CELL_NETWORK_CAPABILITIES =
            new NetworkCapabilities.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .setSubscriptionIds(Set.of(SUB_ID))
                    .build();

    private static final LinkProperties LINK_PROPERTIES = getLinkPropertiesWithName("test_iface");

    @Mock private Network mNetwork;
    @Mock private TelephonySubscriptionSnapshot mSubscriptionSnapshot;
    @Mock private TelephonyManager mTelephonyManager;

    private TestLooper mTestLooper;
    private VcnContext mVcnContext;
    private UnderlyingNetworkRecord mWifiNetworkRecord;
    private UnderlyingNetworkRecord mCellNetworkRecord;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        final Context mockContext = mock(Context.class);
        mTestLooper = new TestLooper();
        mVcnContext =
                spy(
                        new VcnContext(
                                mockContext,
                                mTestLooper.getLooper(),
                                mock(VcnNetworkProvider.class),
                                false /* isInTestMode */));
        resetVcnContext();
        setupSystemService(
                mockContext, mTelephonyManager, Context.TELEPHONY_SERVICE, TelephonyManager.class);

        mWifiNetworkRecord =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        WIFI_NETWORK_CAPABILITIES,
                        LINK_PROPERTIES,
                        false /* isBlocked */);

        mCellNetworkRecord =
                new UnderlyingNetworkRecord(
                        mNetwork,
                        CELL_NETWORK_CAPABILITIES,
                        LINK_PROPERTIES,
                        false /* isBlocked */);
        when(mTelephonyManager.createForSubscriptionId(SUB_ID)).thenReturn(mTelephonyManager);
        when(mTelephonyManager.getNetworkOperator()).thenReturn(PLMN_ID);
        when(mTelephonyManager.getSimSpecificCarrierId()).thenReturn(CARRIER_ID);
    }

    private void resetVcnContext() {
        reset(mVcnContext);
        doNothing().when(mVcnContext).ensureRunningOnLooperThread();
    }

    private void checkMatchWifi(
            boolean isSelectedNetwork, PersistableBundle carrierConfig, boolean expectTrue) {
        final WifiUnderlyingNetworkPriority wifiNetworkPriority =
                new WifiUnderlyingNetworkPriority.Builder()
                        .setNetworkQuality(NETWORK_QUALITY_OK)
                        .setAllowMetered(true /* allowMetered */)
                        .build();
        final UnderlyingNetworkRecord selectedNetworkRecord =
                isSelectedNetwork ? mWifiNetworkRecord : null;
        assertEquals(
                expectTrue,
                matchWifiUnderlyingNetworkPriority(
                        wifiNetworkPriority,
                        mWifiNetworkRecord,
                        selectedNetworkRecord,
                        carrierConfig));
    }

    @Test
    public void testMatchSelectedWifi() {
        checkMatchWifi(
                true /* isSelectedNetwork */, null /* carrierConfig */, true /* expectTrue */);
    }

    @Test
    public void testMatchSelectedWifiBelowRssiThreshold() {
        final PersistableBundle carrierConfig = new PersistableBundle();
        carrierConfig.putInt(
                VcnManager.VCN_NETWORK_SELECTION_WIFI_EXIT_RSSI_THRESHOLD_KEY, WIFI_RSSI + 1);

        checkMatchWifi(true /* isSelectedNetwork */, carrierConfig, false /* expectTrue */);
    }

    @Test
    public void testMatchUnselectedWifi() {
        checkMatchWifi(
                false /* isSelectedNetwork */, null /* carrierConfig */, true /* expectTrue */);
    }

    @Test
    public void testMatchUnselectedWifiBelowRssiThreshold() {
        final PersistableBundle carrierConfig = new PersistableBundle();
        carrierConfig.putInt(
                VcnManager.VCN_NETWORK_SELECTION_WIFI_ENTRY_RSSI_THRESHOLD_KEY, WIFI_RSSI + 1);

        checkMatchWifi(false /* isSelectedNetwork */, carrierConfig, false /* expectTrue */);
    }

    private void verifyMatchWifiWithSsid(boolean useMatchedSsid, boolean expectTrue) {
        final String nwPrioritySsid = useMatchedSsid ? SSID : SSID + "f";
        final WifiUnderlyingNetworkPriority wifiNetworkPriority =
                new WifiUnderlyingNetworkPriority.Builder()
                        .setNetworkQuality(NETWORK_QUALITY_OK)
                        .setAllowMetered(true /* allowMetered */)
                        .setSsid(nwPrioritySsid)
                        .build();

        assertEquals(
                expectTrue,
                matchWifiUnderlyingNetworkPriority(
                        wifiNetworkPriority,
                        mWifiNetworkRecord,
                        null /* currentlySelecetd */,
                        null /* carrierConfig */));
    }

    @Test
    public void testMatchWifiWithSsid() {
        verifyMatchWifiWithSsid(true /* useMatchedSsid */, true /* expectTrue */);
    }

    @Test
    public void testMatchWifiFailWithWrongSsid() {
        verifyMatchWifiWithSsid(false /* useMatchedSsid */, false /* expectTrue */);
    }

    @Test
    public void testMatchWifiWithoutNotMeteredBit() {
        final WifiUnderlyingNetworkPriority wifiNetworkPriority =
                new WifiUnderlyingNetworkPriority.Builder()
                        .setNetworkQuality(NETWORK_QUALITY_OK)
                        .setAllowMetered(false /* allowMetered */)
                        .build();

        assertFalse(
                matchWifiUnderlyingNetworkPriority(
                        wifiNetworkPriority,
                        mWifiNetworkRecord,
                        null /* currentlySelecetd */,
                        null /* carrierConfig */));
    }

    private static CellUnderlyingNetworkPriority.Builder getDefaultCellNetworkPriorityBuilder() {
        return new CellUnderlyingNetworkPriority.Builder()
                .setNetworkQuality(NETWORK_QUALITY_OK)
                .setAllowMetered(true /* allowMetered */)
                .setAllowRoaming(true /* allowRoaming */);
    }

    @Test
    public void testMatchMacroCell() {
        assertTrue(
                matchCellUnderlyingNetworkPriority(
                        getDefaultCellNetworkPriorityBuilder().build(),
                        mVcnContext,
                        mCellNetworkRecord,
                        SUB_GROUP,
                        mSubscriptionSnapshot));
    }

    @Test
    public void testMatchOpportunisticCell() {
        final CellUnderlyingNetworkPriority opportunisticCellNetworkPriority =
                new CellUnderlyingNetworkPriority.Builder()
                        .setNetworkQuality(NETWORK_QUALITY_OK)
                        .setAllowMetered(true /* allowMetered */)
                        .setAllowRoaming(true /* allowRoaming */)
                        .setRequireOpportunistic(true /* requireOpportunistic */)
                        .build();

        when(mSubscriptionSnapshot.isOpportunistic(SUB_ID)).thenReturn(true);
        when(mSubscriptionSnapshot.getAllSubIdsInGroup(SUB_GROUP)).thenReturn(new ArraySet<>());

        assertTrue(
                matchCellUnderlyingNetworkPriority(
                        opportunisticCellNetworkPriority,
                        mVcnContext,
                        mCellNetworkRecord,
                        SUB_GROUP,
                        mSubscriptionSnapshot));
    }

    private void verifyMatchMacroCellWithAllowedPlmnIds(
            boolean useMatchedPlmnId, boolean expectTrue) {
        final String networkPriorityPlmnId = useMatchedPlmnId ? PLMN_ID : PLMN_ID_OTHER;
        final CellUnderlyingNetworkPriority networkPriority =
                getDefaultCellNetworkPriorityBuilder()
                        .setAllowedPlmnIds(Set.of(networkPriorityPlmnId))
                        .build();

        assertEquals(
                expectTrue,
                matchCellUnderlyingNetworkPriority(
                        networkPriority,
                        mVcnContext,
                        mCellNetworkRecord,
                        SUB_GROUP,
                        mSubscriptionSnapshot));
    }

    @Test
    public void testMatchMacroCellWithAllowedPlmnIds() {
        verifyMatchMacroCellWithAllowedPlmnIds(true /* useMatchedPlmnId */, true /* expectTrue */);
    }

    @Test
    public void testMatchMacroCellFailWithdisallowedPlmnIds() {
        verifyMatchMacroCellWithAllowedPlmnIds(
                false /* useMatchedPlmnId */, false /* expectTrue */);
    }

    private void verifyMatchMacroCellWithAllowedSpecificCarrierIds(
            boolean useMatchedCarrierId, boolean expectTrue) {
        final int networkPriorityCarrierId = useMatchedCarrierId ? CARRIER_ID : CARRIER_ID_OTHER;
        final CellUnderlyingNetworkPriority networkPriority =
                getDefaultCellNetworkPriorityBuilder()
                        .setAllowedSpecificCarrierIds(Set.of(networkPriorityCarrierId))
                        .build();

        assertEquals(
                expectTrue,
                matchCellUnderlyingNetworkPriority(
                        networkPriority,
                        mVcnContext,
                        mCellNetworkRecord,
                        SUB_GROUP,
                        mSubscriptionSnapshot));
    }

    @Test
    public void testMatchMacroCellWithAllowedSpecificCarrierIds() {
        verifyMatchMacroCellWithAllowedSpecificCarrierIds(
                true /* useMatchedCarrierId */, true /* expectTrue */);
    }

    @Test
    public void testMatchMacroCellFailWithdisallowedSpecificCarrierIds() {
        verifyMatchMacroCellWithAllowedSpecificCarrierIds(
                false /* useMatchedCarrierId */, false /* expectTrue */);
    }

    @Test
    public void testMatchWifiFailWithoutNotRoamingBit() {
        final CellUnderlyingNetworkPriority networkPriority =
                getDefaultCellNetworkPriorityBuilder()
                        .setAllowRoaming(false /* allowRoaming */)
                        .build();

        assertFalse(
                matchCellUnderlyingNetworkPriority(
                        networkPriority,
                        mVcnContext,
                        mCellNetworkRecord,
                        SUB_GROUP,
                        mSubscriptionSnapshot));
    }

    private void verifyCalculatePriorityClass(
            UnderlyingNetworkRecord networkRecord, int expectedIndex) {
        final int priorityIndex =
                calculatePriorityClass(
                        networkRecord,
                        mVcnContext,
                        VcnGatewayConnectionConfig.DEFAULT_UNDERLYING_NETWORK_PRIORITIES,
                        SUB_GROUP,
                        mSubscriptionSnapshot,
                        null /* currentlySelected */,
                        null /* carrierConfig */);

        assertEquals(expectedIndex, priorityIndex);
    }

    @Test
    public void testCalculatePriorityClass() throws Exception {
        verifyCalculatePriorityClass(mCellNetworkRecord, 2);
    }

    @Test
    public void testCalculatePriorityClassFailToMatchAny() throws Exception {
        final NetworkCapabilities nc =
                new NetworkCapabilities.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .setSignalStrength(WIFI_RSSI_LOW)
                        .setSsid(SSID)
                        .build();
        final UnderlyingNetworkRecord wifiNetworkRecord =
                new UnderlyingNetworkRecord(mNetwork, nc, LINK_PROPERTIES, false /* isBlocked */);

        verifyCalculatePriorityClass(wifiNetworkRecord, PRIORITY_ANY);
    }
}
