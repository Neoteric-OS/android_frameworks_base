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
import static android.net.netstats.provider.NetworkStatsProvider.QUOTA_UNLIMITED;

import static com.android.networkstack.tethering.BpfCoordinator
        .DEFAULT_PERFORM_POLL_INTERVAL_MS;
import static com.android.networkstack.tethering.BpfCoordinator.StatsType;
import static com.android.networkstack.tethering.BpfCoordinator.StatsType.STATS_PER_IFACE;
import static com.android.networkstack.tethering.BpfCoordinator.StatsType.STATS_PER_UID;

import static junit.framework.Assert.assertNotNull;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.usage.NetworkStatsManager;
import android.net.INetd;
import android.net.InetAddresses;
import android.net.MacAddress;
import android.net.NetworkStats;
import android.net.TetherOffloadRuleParcel;
import android.net.TetherStatsParcel;
import android.net.ip.IpServer;
import android.net.util.SharedLog;
import android.os.Handler;
import android.os.test.TestLooper;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.networkstack.tethering.BpfCoordinator.Ipv6ForwardingRule;
import com.android.testutils.TestableNetworkStatsProviderCbBinder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BpfCoordinatorTest {
    private static final int DOWNSTREAM_IFINDEX = 10;
    private static final MacAddress DOWNSTREAM_MAC = MacAddress.ALL_ZEROS_ADDRESS;

    @Mock private NetworkStatsManager mStatsManager;
    @Mock private INetd mNetd;
    @Mock private IpServer mIpServer;

    // Late init since methods must be called by the thread that created this object.
    private TestableNetworkStatsProviderCbBinder mTetherStatsProviderCb;
    private BpfCoordinator.BpfTetherStatsProvider mTetherStatsProvider;
    private final ArgumentCaptor<ArrayList> mStringArrayCaptor =
            ArgumentCaptor.forClass(ArrayList.class);
    private final TestLooper mTestLooper = new TestLooper();
    private BpfCoordinator.Dependencies mDeps =
            new BpfCoordinator.Dependencies() {
            @Override
            int getPerformPollInterval() {
                return DEFAULT_PERFORM_POLL_INTERVAL_MS;
            }
    };

    @Before public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    private void waitForIdle() {
        mTestLooper.dispatchAll();
    }

    private void setupFunctioningNetdInterface() throws Exception {
        when(mNetd.tetherOffloadGetStats()).thenReturn(new TetherStatsParcel[0]);
    }

    @NonNull
    private BpfCoordinator makeBpfCoordinator() throws Exception {
        BpfCoordinator coordinator = new BpfCoordinator(
                new Handler(mTestLooper.getLooper()), mNetd, mStatsManager, new SharedLog("test"),
                mDeps);
        final ArgumentCaptor<BpfCoordinator.BpfTetherStatsProvider>
                tetherStatsProviderCaptor =
                ArgumentCaptor.forClass(BpfCoordinator.BpfTetherStatsProvider.class);
        verify(mStatsManager).registerNetworkStatsProvider(anyString(),
                tetherStatsProviderCaptor.capture());
        mTetherStatsProvider = tetherStatsProviderCaptor.getValue();
        assertNotNull(mTetherStatsProvider);
        mTetherStatsProviderCb = new TestableNetworkStatsProviderCbBinder();
        mTetherStatsProvider.setProviderCallbackBinder(mTetherStatsProviderCb);
        return coordinator;
    }

    @NonNull
    private static NetworkStats.Entry buildTestEntry(@NonNull StatsType how,
            @NonNull String iface, long rxBytes, long rxPackets, long txBytes, long txPackets) {
        return new NetworkStats.Entry(iface, how == STATS_PER_IFACE ? UID_ALL : UID_TETHERING,
                SET_DEFAULT, TAG_NONE, METERED_NO, ROAMING_NO, DEFAULT_NETWORK_NO, rxBytes,
                rxPackets, txBytes, txPackets, 0L);
    }

    @NonNull
    private static TetherStatsParcel buildTestTetherStatsParcel(@NonNull Integer ifIndex,
            long rxBytes, long rxPackets, long txBytes, long txPackets) {
        final TetherStatsParcel parcel = new TetherStatsParcel();
        parcel.ifIndex = ifIndex;
        parcel.rxBytes = rxBytes;
        parcel.rxPackets = rxPackets;
        parcel.txBytes = txBytes;
        parcel.txPackets = txPackets;
        return parcel;
    }

    private void setTetherOffloadStatsList(TetherStatsParcel[] tetherStatsList) throws Exception {
        when(mNetd.tetherOffloadGetStats()).thenReturn(tetherStatsList);
        mTestLooper.moveTimeForward(DEFAULT_PERFORM_POLL_INTERVAL_MS);
        waitForIdle();
    }

    @Test
    public void testGetForwardedStats() throws Exception {
        setupFunctioningNetdInterface();

        final BpfCoordinator coordinator = makeBpfCoordinator();
        coordinator.start();

        final String wlanIface = "wlan0";
        final Integer wlanIfIndex = 100;
        final String mobileIface = "rmnet_data0";
        final Integer mobileIfIndex = 101;

        // Add interface name to lookup table. In realistic case, the upstream interface name will
        // be added by IpServer when IpServer has received with a new IPv6 upstream update event.
        coordinator.addUpstreamNameToLookupTable(wlanIfIndex, wlanIface);
        coordinator.addUpstreamNameToLookupTable(mobileIfIndex, mobileIface);

        // [1] Both interface stats are changed.
        // Setup the tether stats of wlan and mobile interface. Note that move forward the time of
        // the looper to make sure the new tether stats has been updated by polling update thread.
        setTetherOffloadStatsList(new TetherStatsParcel[] {
                buildTestTetherStatsParcel(wlanIfIndex, 1000, 100, 2000, 200),
                buildTestTetherStatsParcel(mobileIfIndex, 3000, 300, 4000, 400)});

        final NetworkStats expectedIfaceStats = new NetworkStats(0L, 2)
                .addEntry(buildTestEntry(STATS_PER_IFACE, wlanIface, 1000, 100, 2000, 200))
                .addEntry(buildTestEntry(STATS_PER_IFACE, mobileIface, 3000, 300, 4000, 400));

        final NetworkStats expectedUidStats = new NetworkStats(0L, 2)
                .addEntry(buildTestEntry(STATS_PER_UID, wlanIface, 1000, 100, 2000, 200))
                .addEntry(buildTestEntry(STATS_PER_UID, mobileIface, 3000, 300, 4000, 400));

        // Force pushing stats update to verify the stats reported.
        // TODO: Perhaps make #expectNotifyStatsUpdated to use test TetherStatsParcel object for
        // verifying the notification.
        mTetherStatsProvider.pushTetherStats();
        mTetherStatsProviderCb.expectNotifyStatsUpdated(expectedIfaceStats, expectedUidStats);

        // [2] Only one interface stats is changed.
        // The tether stats of mobile interface is accumulated and The tether stats of wlan
        // interface is the same.
        setTetherOffloadStatsList(new TetherStatsParcel[] {
                buildTestTetherStatsParcel(wlanIfIndex, 1000, 100, 2000, 200),
                buildTestTetherStatsParcel(mobileIfIndex, 3010, 320, 4030, 440)});

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

        // [3] Stop coordinator.
        // Shutdown the coordinator and clear the invocation history, especially the
        // tetherOffloadGetStats() calls.
        coordinator.stop();
        clearInvocations(mNetd);

        // Verify the polling update thread stopped.
        mTestLooper.moveTimeForward(DEFAULT_PERFORM_POLL_INTERVAL_MS);
        waitForIdle();
        verify(mNetd, never()).tetherOffloadGetStats();
    }

    @Test
    public void testOnSetAlert() throws Exception {
        setupFunctioningNetdInterface();

        final BpfCoordinator coordinator = makeBpfCoordinator();
        coordinator.start();

        final String mobileIface = "rmnet_data0";
        final Integer mobileIfIndex = 100;
        coordinator.addUpstreamNameToLookupTable(mobileIfIndex, mobileIface);

        // Verify that set quota to 0 will immediately triggers a callback.
        mTetherStatsProvider.onSetAlert(0);
        waitForIdle();
        mTetherStatsProviderCb.expectNotifyAlertReached();

        // Verify that notifyAlertReached never fired if quota is not yet reached.
        when(mNetd.tetherOffloadGetStats()).thenReturn(
                new TetherStatsParcel[] {buildTestTetherStatsParcel(mobileIfIndex, 0, 0, 0, 0)});
        mTetherStatsProvider.onSetAlert(100);
        mTestLooper.moveTimeForward(DEFAULT_PERFORM_POLL_INTERVAL_MS);
        waitForIdle();
        mTetherStatsProviderCb.assertNoCallback();

        // Verify that notifyAlertReached fired when quota is reached.
        when(mNetd.tetherOffloadGetStats()).thenReturn(
                new TetherStatsParcel[] {buildTestTetherStatsParcel(mobileIfIndex, 50, 0, 50, 0)});
        mTestLooper.moveTimeForward(DEFAULT_PERFORM_POLL_INTERVAL_MS);
        waitForIdle();
        mTetherStatsProviderCb.expectNotifyAlertReached();

        // Verify that set quota with UNLIMITED won't trigger any callback.
        mTetherStatsProvider.onSetAlert(QUOTA_UNLIMITED);
        mTestLooper.moveTimeForward(DEFAULT_PERFORM_POLL_INTERVAL_MS);
        waitForIdle();
        mTetherStatsProviderCb.assertNoCallback();
    }

    /**
     * Custom ArgumentMatcher for TetherOffloadRuleParcel. This is needed because generated stable
     * AIDL classes don't have equals(), so we cannot just use eq(). A custom assert, such as:
     *
     * private void checkFooCalled(StableParcelable p, ...) {
     *     ArgumentCaptor<FooParam> captor = ArgumentCaptor.forClass(FooParam.class);
     *     verify(mMock).foo(captor.capture());
     *     Foo foo = captor.getValue();
     *     assertFooMatchesExpectations(foo);
     * }
     *
     * almost works, but not quite. This is because if the code under test calls foo() twice, the
     * first call to checkFooCalled() matches both the calls, putting both calls into the captor,
     * and then fails with TooManyActualInvocations. It also makes it harder to use other mockito
     * features such as never(), inOrder(), etc.
     *
     * This approach isn't great because if the match fails, the error message is unhelpful
     * (actual: "android.net.TetherOffloadRuleParcel@8c827b0" or some such), but at least it does
     * work.
     *
     * See ConnectivityServiceTest#assertRoutesAdded for an alternative approach which solves the
     * TooManyActualInvocations problem described above by forcing the caller of the custom assert
     * method to specify all expected invocations in one call. This is useful when the stable
     * parcelable class being asserted on has a corresponding Java object (eg., RouteInfo and
     * RouteInfoParcelable), and the caller can just pass in a list of them. It not useful here
     * because there is no such object.
     */
    private static class TetherOffloadRuleParcelMatcher implements
            ArgumentMatcher<TetherOffloadRuleParcel> {
        public final int upstreamIfindex;
        public final int downstreamIfindex;
        public final Inet6Address address;
        public final MacAddress srcMac;
        public final MacAddress dstMac;

        TetherOffloadRuleParcelMatcher(@NonNull Ipv6ForwardingRule rule) {
            upstreamIfindex = rule.upstreamIfindex;
            downstreamIfindex = rule.downstreamIfindex;
            address = rule.address;
            srcMac = rule.srcMac;
            dstMac = rule.dstMac;
        }

        public boolean matches(@NonNull TetherOffloadRuleParcel parcel) {
            return upstreamIfindex == parcel.inputInterfaceIndex
                    && (downstreamIfindex == parcel.outputInterfaceIndex)
                    && Arrays.equals(address.getAddress(), parcel.destination)
                    && (128 == parcel.prefixLength)
                    && Arrays.equals(srcMac.toByteArray(), parcel.srcL2Address)
                    && Arrays.equals(dstMac.toByteArray(), parcel.dstL2Address);
        }

        public String toString() {
            return String.format("TetherOffloadRuleParcelMatcher(%d, %d, %s, %s, %s",
                    upstreamIfindex, downstreamIfindex, address.getHostAddress(), srcMac, dstMac);
        }
    }

    @NonNull
    private TetherOffloadRuleParcel matches(@NonNull Ipv6ForwardingRule rule) {
        return argThat(new TetherOffloadRuleParcelMatcher(rule));
    }

    @NonNull
    private static Ipv6ForwardingRule buildTestForwardingRule(
            int upstreamIfindex, @NonNull InetAddress address, @NonNull MacAddress dstMac) {
        return new Ipv6ForwardingRule(upstreamIfindex, DOWNSTREAM_IFINDEX, (Inet6Address) address,
                DOWNSTREAM_MAC, dstMac);
    }

    // TODO: Add tests for tetherOffloadRuleClear, tetherOffloadRuleUpdate.
    @Test
    public void testSetInterfaceQuota() throws Exception {
        setupFunctioningNetdInterface();

        final BpfCoordinator coordinator = makeBpfCoordinator();

        final String ethernetIface = "eth1";
        final String mobileIface = "rmnet_data0";
        final Integer ethernetIfIndex = 100;
        final Integer mobileIfIndex = 101;
        final long ethernetLimit = 12345;
        final long mobileLimit = 12345678;

        final InetAddress neighA = InetAddresses.parseNumericAddress("2001:db8::1");
        final InetAddress neighB = InetAddresses.parseNumericAddress("2001:db8::2");
        final MacAddress macA = MacAddress.fromString("00:00:00:00:00:0a");
        final MacAddress macB = MacAddress.fromString("11:22:33:00:00:0b");

        coordinator.addUpstreamNameToLookupTable(ethernetIfIndex, ethernetIface);
        coordinator.addUpstreamNameToLookupTable(mobileIfIndex, mobileIface);

        final InOrder inOrder = inOrder(mNetd);

        // Suppose Ethernet as current upstream. The coordinator doesn't monitor the upstream
        // change. It only cares about the rule changes. So we doesn't update any upstream change
        // event here.

        // Applying an interface quota to the current upstream does not take any immediate action.
        mTetherStatsProvider.onSetLimit(ethernetIface, ethernetLimit);
        waitForIdle();
        inOrder.verify(mNetd, never()).tetherOffloadSetInterfaceQuota(anyInt(), anyLong());

        // Adding the first rule on current upstream immediately send the quota to netd.
        Ipv6ForwardingRule ethernetRuleA = buildTestForwardingRule(ethernetIfIndex, neighA, macA);
        coordinator.tetherOffloadRuleAdd(mIpServer, ethernetRuleA);
        inOrder.verify(mNetd).tetherOffloadRuleAdd(matches(ethernetRuleA));
        inOrder.verify(mNetd).tetherOffloadSetInterfaceQuota(ethernetIfIndex, ethernetLimit);
        inOrder.verifyNoMoreInteractions();

        // Adding the second rule on current upstream does not send the quota to netd.
        Ipv6ForwardingRule ethernetRuleB = buildTestForwardingRule(ethernetIfIndex, neighB, macB);
        coordinator.tetherOffloadRuleAdd(mIpServer, ethernetRuleB);
        inOrder.verify(mNetd).tetherOffloadRuleAdd(matches(ethernetRuleB));
        inOrder.verify(mNetd, never()).tetherOffloadSetInterfaceQuota(anyInt(), anyLong());

        // Removing the second rule on current upstream does not send the quota to netd.
        coordinator.tetherOffloadRuleRemove(mIpServer, ethernetRuleB);
        inOrder.verify(mNetd).tetherOffloadRuleRemove(matches(ethernetRuleB));
        inOrder.verify(mNetd, never()).tetherOffloadSetInterfaceQuota(anyInt(), anyLong());

        // Removing the last rule on current upstream immediately send the cleanup stuff to netd.
        when(mNetd.tetherOffloadGetAndClearStats(ethernetIfIndex))
                .thenReturn(buildTestTetherStatsParcel(ethernetIfIndex, 0, 0, 0, 0));
        coordinator.tetherOffloadRuleRemove(mIpServer, ethernetRuleA);
        inOrder.verify(mNetd).tetherOffloadRuleRemove(matches(ethernetRuleA));
        inOrder.verify(mNetd).tetherOffloadGetAndClearStats(ethernetIfIndex);
        inOrder.verifyNoMoreInteractions();

        // Suppose that the current upstream switches to cellular. The coordinator doesn't monitor
        // the upstream change. It only cares about the rule changes. So we doesn't update any
        // upstream change event here.

        // Applying an interface quota to another upstream, mobile, does not take any immediate
        // action.
        mTetherStatsProvider.onSetLimit(mobileIface, mobileLimit);
        waitForIdle();
        inOrder.verify(mNetd, never()).tetherOffloadSetInterfaceQuota(mobileIfIndex, mobileLimit);

        // Adding the first rule on current upstream immediately send the quota to netd.
        Ipv6ForwardingRule mobileRuleA = buildTestForwardingRule(mobileIfIndex, neighA, macA);
        coordinator.tetherOffloadRuleAdd(mIpServer, mobileRuleA);
        inOrder.verify(mNetd).tetherOffloadRuleAdd(matches(mobileRuleA));
        inOrder.verify(mNetd).tetherOffloadSetInterfaceQuota(mobileIfIndex, mobileLimit);
        inOrder.verifyNoMoreInteractions();

        // Applying the interface quota boundary {min, max, infinity} on current upstream which has
        // already added a rule immediately send the quota to netd
        for (final long quota : new long[] {0, Long.MAX_VALUE, QUOTA_UNLIMITED}) {
            mTetherStatsProvider.onSetLimit(mobileIface, quota);
            waitForIdle();
            inOrder.verify(mNetd).tetherOffloadSetInterfaceQuota(mobileIfIndex, quota);
            inOrder.verifyNoMoreInteractions();
        }
    }

    @Test
    public void testTetherOffloadRule() throws Exception {
        setupFunctioningNetdInterface();

        final BpfCoordinator coordinator = makeBpfCoordinator();

        final String ethernetIface = "eth1";
        final String mobileIface = "rmnet_data0";
        final Integer ethernetIfIndex = 100;
        final Integer mobileIfIndex = 101;

        final InetAddress neighA = InetAddresses.parseNumericAddress("2001:db8::1");
        final InetAddress neighB = InetAddresses.parseNumericAddress("2001:db8::2");
        final MacAddress macA = MacAddress.fromString("00:00:00:00:00:0a");
        final MacAddress macB = MacAddress.fromString("11:22:33:00:00:0b");

        coordinator.addUpstreamNameToLookupTable(ethernetIfIndex, ethernetIface);
        coordinator.addUpstreamNameToLookupTable(mobileIfIndex, mobileIface);

        final InOrder inOrder = inOrder(mNetd);
        when(mNetd.tetherOffloadGetAndClearStats(ethernetIfIndex))
                .thenReturn(buildTestTetherStatsParcel(ethernetIfIndex, 10, 20, 30, 40));
        when(mNetd.tetherOffloadGetAndClearStats(mobileIfIndex))
                .thenReturn(buildTestTetherStatsParcel(mobileIfIndex, 50, 60, 70, 80));

        // [1] Adding rules on the upstream Ethernet.
        Ipv6ForwardingRule ethernetRuleA = buildTestForwardingRule(ethernetIfIndex, neighA, macA);
        Ipv6ForwardingRule ethernetRuleB = buildTestForwardingRule(ethernetIfIndex, neighB, macB);

        coordinator.tetherOffloadRuleAdd(mIpServer, ethernetRuleA);
        inOrder.verify(mNetd).tetherOffloadRuleAdd(matches(ethernetRuleA));
        inOrder.verify(mNetd).tetherOffloadSetInterfaceQuota(ethernetIfIndex, QUOTA_UNLIMITED);

        coordinator.tetherOffloadRuleAdd(mIpServer, ethernetRuleB);
        inOrder.verify(mNetd).tetherOffloadRuleAdd(matches(ethernetRuleB));

        // [2] Update the upstream interface index of rules from Ethernet to cellular.
        Ipv6ForwardingRule mobileRuleA = buildTestForwardingRule(mobileIfIndex, neighA, macA);
        Ipv6ForwardingRule mobileRuleB = buildTestForwardingRule(mobileIfIndex, neighB, macB);

        // The rules are removed and re-added one by one. The first rule is added on a upstream
        // causes the limit to be added. The last rule is removed on a given upstream causes
        // cleanup which gets the stats, clears the stats and clears the limit.
        coordinator.tetherOffloadRuleUpdate(mIpServer, mobileIfIndex);
        inOrder.verify(mNetd).tetherOffloadRuleRemove(matches(ethernetRuleA));
        inOrder.verify(mNetd).tetherOffloadRuleAdd(matches(mobileRuleA));
        inOrder.verify(mNetd).tetherOffloadSetInterfaceQuota(mobileIfIndex, QUOTA_UNLIMITED);
        inOrder.verify(mNetd).tetherOffloadRuleRemove(matches(ethernetRuleB));
        inOrder.verify(mNetd).tetherOffloadGetAndClearStats(ethernetIfIndex);
        inOrder.verify(mNetd).tetherOffloadRuleAdd(matches(mobileRuleB));

        // [3] Clear all rules for a given IpServer.
        coordinator.tetherOffloadRuleClear(mIpServer);
        inOrder.verify(mNetd).tetherOffloadRuleRemove(matches(mobileRuleA));
        inOrder.verify(mNetd).tetherOffloadRuleRemove(matches(mobileRuleB));
        inOrder.verify(mNetd).tetherOffloadGetAndClearStats(mobileIfIndex);

        // [4] Force pushing stats update to verify that the last diff of stats is reported on all
        // upstreams.
        mTetherStatsProvider.pushTetherStats();
        mTetherStatsProviderCb.expectNotifyStatsUpdated(
                new NetworkStats(0L, 2)
                .addEntry(buildTestEntry(STATS_PER_IFACE, ethernetIface, 10, 20, 30, 40))
                .addEntry(buildTestEntry(STATS_PER_IFACE, mobileIface, 50, 60, 70, 80)),
                new NetworkStats(0L, 2)
                .addEntry(buildTestEntry(STATS_PER_UID, ethernetIface, 10, 20, 30, 40))
                .addEntry(buildTestEntry(STATS_PER_UID, mobileIface, 50, 60, 70, 80)));
    }
}
