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

package com.android.networkstack.tethering;

import static android.net.NetworkStats.DEFAULT_NETWORK_NO;
import static android.net.NetworkStats.METERED_NO;
import static android.net.NetworkStats.ROAMING_NO;
import static android.net.NetworkStats.SET_DEFAULT;
import static android.net.NetworkStats.TAG_NONE;
import static android.net.NetworkStats.UID_ALL;
import static android.net.NetworkStats.UID_TETHERING;

import static com.android.networkstack.tethering.BpfTetheringCoordinator
        .DEFAULT_PERFORM_POLL_INTERVAL_MS;
import static com.android.networkstack.tethering.BpfTetheringCoordinator.StatsType;
import static com.android.networkstack.tethering.BpfTetheringCoordinator.StatsType.STATS_PER_IFACE;
import static com.android.networkstack.tethering.BpfTetheringCoordinator.StatsType.STATS_PER_UID;

import static junit.framework.Assert.assertNotNull;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.INetd;
import android.net.NetworkStats;
import android.net.NetworkStats.Entry;
import android.net.TetherStatsParcel;
import android.net.util.SharedLog;
import android.os.Handler;
import android.os.test.TestLooper;
import android.provider.Settings;
import android.test.mock.MockContentResolver;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.test.FakeSettingsProvider;
import com.android.testutils.TestableNetworkStatsProviderCbBinder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BpfTetheringCoordinatorTest {
    @Mock private ApplicationInfo mApplicationInfo;
    @Mock private Context mContext;
    @Mock private NetworkStatsManager mStatsManager;
    @Mock private INetd mNetd;
    // Late init since methods must be called by the thread that created this object.
    private TestableNetworkStatsProviderCbBinder mTetherStatsProviderCb;
    private BpfTetheringCoordinator.BpfTetherStatsProvider mTetherStatsProvider;
    private final ArgumentCaptor<ArrayList> mStringArrayCaptor =
            ArgumentCaptor.forClass(ArrayList.class);
    private MockContentResolver mContentResolver;
    private final TestLooper mTestLooper = new TestLooper();
    private BpfTetheringCoordinator.Dependencies mDeps =
            new BpfTetheringCoordinator.Dependencies() {
            @Override
            int getPerformPollInterval() {
                return DEFAULT_PERFORM_POLL_INTERVAL_MS;
            }
    };

    @Before public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getApplicationInfo()).thenReturn(mApplicationInfo);
        when(mContext.getPackageName()).thenReturn("BpfTetheringCoordinatorTest");
        mContentResolver = new MockContentResolver(mContext);
        mContentResolver.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        FakeSettingsProvider.clearSettingsProvider();
    }

    @After public void tearDown() throws Exception {
        FakeSettingsProvider.clearSettingsProvider();
    }

    private void waitForIdle() {
        mTestLooper.dispatchAll();
    }

    private void setupFunctioningNetdInterface() throws Exception {
        when(mNetd.tetherOffloadGetStats()).thenReturn(new TetherStatsParcel[0]);
    }

    private BpfTetheringCoordinator makeBpfTetheringCoordinator() throws Exception {
        BpfTetheringCoordinator coordinator = new BpfTetheringCoordinator(
                new Handler(mTestLooper.getLooper()), mNetd, mStatsManager, new SharedLog("test"),
                mDeps);
        final ArgumentCaptor<BpfTetheringCoordinator.BpfTetherStatsProvider>
                tetherStatsProviderCaptor =
                ArgumentCaptor.forClass(BpfTetheringCoordinator.BpfTetherStatsProvider.class);
        verify(mStatsManager).registerNetworkStatsProvider(anyString(),
                tetherStatsProviderCaptor.capture());
        mTetherStatsProvider = tetherStatsProviderCaptor.getValue();
        assertNotNull(mTetherStatsProvider);
        mTetherStatsProviderCb = new TestableNetworkStatsProviderCbBinder();
        mTetherStatsProvider.setProviderCallbackBinder(mTetherStatsProviderCb);
        return coordinator;
    }

    private static @NonNull Entry buildTestEntry(@NonNull StatsType how,
            @NonNull String iface, long rxBytes, long rxPackets, long txBytes, long txPackets) {
        return new Entry(iface, how == STATS_PER_IFACE ? UID_ALL : UID_TETHERING,
                SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO, DEFAULT_NETWORK_NO, rxBytes,
                rxPackets, txBytes, txPackets, 0L);
    }

    private static @NonNull TetherStatsParcel buildTestTetherStatsParcel(@NonNull Integer ifIndex,
            long rxBytes, long rxPackets, long txBytes, long txPackets) {
        TetherStatsParcel parcel = new TetherStatsParcel();
        parcel.ifIndex = ifIndex;
        parcel.rxBytes = rxBytes;
        parcel.rxPackets = rxPackets;
        parcel.txBytes = txBytes;
        parcel.txPackets = txPackets;
        return parcel;
    }

    @Test
    public void testGetForwardedStats() throws Exception {
        setupFunctioningNetdInterface();

        final BpfTetheringCoordinator offload = makeBpfTetheringCoordinator();
        offload.start();

        final String wlanIface = "wlan0";
        final Integer wlanIfindex = 100;
        final String mobileIface = "rmnet_data0";
        final Integer mobileIfindex = 101;

        // Add interface name to lookup table.
        offload.addUpstreamNameToLookupTable(wlanIfindex, wlanIface);
        offload.addUpstreamNameToLookupTable(mobileIfindex, mobileIface);

        // [1] Both interface stats are changed.
        // Setup the tether stats of wlan and mobile interface.
        TetherStatsParcel[] tetherStatsList = new TetherStatsParcel[2];
        tetherStatsList[0] = buildTestTetherStatsParcel(wlanIfindex, 1000, 100, 2000, 200);
        tetherStatsList[1] = buildTestTetherStatsParcel(mobileIfindex, 3000, 300, 4000, 400);
        when(mNetd.tetherOffloadGetStats()).thenReturn(tetherStatsList);
        mTestLooper.moveTimeForward(DEFAULT_PERFORM_POLL_INTERVAL_MS);
        waitForIdle();

        final NetworkStats expectedIfaceStats = new NetworkStats(0L, 2)
                .addEntry(buildTestEntry(STATS_PER_IFACE, wlanIface, 1000, 100, 2000, 200))
                .addEntry(buildTestEntry(STATS_PER_IFACE, mobileIface, 3000, 300, 4000, 400));

        final NetworkStats expectedUidStats = new NetworkStats(0L, 2)
                .addEntry(buildTestEntry(STATS_PER_UID, wlanIface, 1000, 100, 2000, 200))
                .addEntry(buildTestEntry(STATS_PER_UID, mobileIface, 3000, 300, 4000, 400));

        // Force pushing stats update to verify the stats reported.
        mTetherStatsProvider.pushTetherStats();
        mTetherStatsProviderCb.expectNotifyStatsUpdated(expectedIfaceStats, expectedUidStats);

        // [2] Only one interface stats is changed.
        // The tether stats of mobile interface is accumulated and The tether stats of wlan
        // interface is the same.
        TetherStatsParcel[] tetherStatsListAccu = new TetherStatsParcel[2];
        tetherStatsListAccu[0] = buildTestTetherStatsParcel(wlanIfindex, 1000, 100, 2000, 200);
        tetherStatsListAccu[1] = buildTestTetherStatsParcel(mobileIfindex, 3010, 320, 4030, 440);
        when(mNetd.tetherOffloadGetStats()).thenReturn(tetherStatsListAccu);
        mTestLooper.moveTimeForward(DEFAULT_PERFORM_POLL_INTERVAL_MS);
        waitForIdle();

        final NetworkStats expectedIfaceStatsDiff = new NetworkStats(0L, 2)
                .addEntry(buildTestEntry(STATS_PER_IFACE, wlanIface, 0, 0, 0, 0))
                .addEntry(buildTestEntry(STATS_PER_IFACE, mobileIface, 10, 20, 30, 40));

        final NetworkStats expectedUidStatsDiff = new NetworkStats(0L, 2)
                .addEntry(buildTestEntry(STATS_PER_UID, wlanIface, 0, 0, 0, 0))
                .addEntry(buildTestEntry(STATS_PER_UID, mobileIface, 10, 20, 30, 40));

        // Force pushing stats update to verify that only diff of stats is reported.
        mTetherStatsProvider.pushTetherStats();
        mTetherStatsProviderCb.expectNotifyStatsUpdated(expectedIfaceStatsDiff,
                expectedUidStatsDiff);
    }
}
