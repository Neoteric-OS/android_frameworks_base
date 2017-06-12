/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.connectivity.tethering;

import static android.provider.Settings.Global.TETHER_OFFLOAD_DISABLED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.RouteInfo;
import android.net.util.SharedLog;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.test.mock.MockContentResolver;
import com.android.internal.util.test.FakeSettingsProvider;

import java.net.InetAddress;
import java.util.ArrayList;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


@RunWith(AndroidJUnit4.class)
@SmallTest
public class OffloadControllerTest {

    @Mock private OffloadHardwareInterface mHardware;
    @Mock private ApplicationInfo mApplicationInfo;
    @Mock private Context mContext;
    private final ArgumentCaptor<ArrayList> mStringArrayCaptor =
            ArgumentCaptor.forClass(ArrayList.class);
    private MockContentResolver mContentResolver;

    @Before public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        reset(mHardware);
        when(mContext.getApplicationInfo()).thenReturn(mApplicationInfo);
        when(mContext.getPackageName()).thenReturn("OffloadControllerTest");
        mContentResolver = new MockContentResolver(mContext);
        mContentResolver.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
    }

    private void setupFunctioningHardwareInterface() {
        when(mHardware.initOffloadConfig()).thenReturn(true);
        when(mHardware.initOffloadControl(any(OffloadHardwareInterface.ControlCallback.class)))
                .thenReturn(true);
    }

    @Test
    public void testNoSettingsValueAllowsStart() {
        setupFunctioningHardwareInterface();
        try {
            Settings.Global.getInt(mContentResolver, TETHER_OFFLOAD_DISABLED);
            fail();
        } catch (SettingNotFoundException expected) {}

        final OffloadController offload =
                new OffloadController(null, mHardware, mContentResolver, new SharedLog("test"));
        offload.start();

        final InOrder inOrder = inOrder(mHardware);
        inOrder.verify(mHardware, times(1)).initOffloadConfig();
        inOrder.verify(mHardware, times(1)).initOffloadControl(
                any(OffloadHardwareInterface.ControlCallback.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSettingsAllowsStart() {
        setupFunctioningHardwareInterface();
        Settings.Global.putInt(mContentResolver, TETHER_OFFLOAD_DISABLED, 0);

        final OffloadController offload =
                new OffloadController(null, mHardware, mContentResolver, new SharedLog("test"));
        offload.start();

        final InOrder inOrder = inOrder(mHardware);
        inOrder.verify(mHardware, times(1)).initOffloadConfig();
        inOrder.verify(mHardware, times(1)).initOffloadControl(
                any(OffloadHardwareInterface.ControlCallback.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSettingsDisablesStart() {
        setupFunctioningHardwareInterface();
        Settings.Global.putInt(mContentResolver, TETHER_OFFLOAD_DISABLED, 1);

        final OffloadController offload =
                new OffloadController(null, mHardware, mContentResolver, new SharedLog("test"));
        offload.start();

        final InOrder inOrder = inOrder(mHardware);
        inOrder.verify(mHardware, never()).initOffloadConfig();
        inOrder.verify(mHardware, never()).initOffloadControl(anyObject());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSetUpstreamLinkPropertiesWorking() throws Exception {
        setupFunctioningHardwareInterface();
        final OffloadController offload =
                new OffloadController(null, mHardware, mContentResolver, new SharedLog("test"));
        offload.start();

        final InOrder inOrder = inOrder(mHardware);
        inOrder.verify(mHardware, times(1)).initOffloadConfig();
        inOrder.verify(mHardware, times(1)).initOffloadControl(
                any(OffloadHardwareInterface.ControlCallback.class));
        inOrder.verifyNoMoreInteractions();

        offload.setUpstreamLinkProperties(null);
        // No change in local addresses means no call to setLocalPrefixes().
        inOrder.verify(mHardware, never()).setLocalPrefixes(mStringArrayCaptor.capture());
        inOrder.verify(mHardware, times(1)).setUpstreamParameters(
                eq(null), eq(null), eq(null), eq(null));
        inOrder.verifyNoMoreInteractions();
        reset(mHardware);

        final LinkProperties lp = new LinkProperties();

        final String testIfName = "rmnet_test0";
        lp.setInterfaceName(testIfName);
        offload.setUpstreamLinkProperties(lp);
        // No change in local addresses means no call to setLocalPrefixes().
        inOrder.verify(mHardware, never()).setLocalPrefixes(mStringArrayCaptor.capture());
        inOrder.verify(mHardware, times(1)).setUpstreamParameters(
                eq(testIfName), eq(null), eq(null), mStringArrayCaptor.capture());
        assertTrue(mStringArrayCaptor.getValue().isEmpty());
        inOrder.verifyNoMoreInteractions();

        final String ipv4Addr = "192.0.2.5";
        final String linkAddr = ipv4Addr + "/24";
        assertTrue(lp.addLinkAddress(new LinkAddress(linkAddr)));
        offload.setUpstreamLinkProperties(lp);
        // Upstream IPv4 addresses are not passed to the HAL.
        // No change in local addresses means no call to setLocalPrefixes().
        inOrder.verify(mHardware, never()).setLocalPrefixes(mStringArrayCaptor.capture());
        inOrder.verify(mHardware, times(1)).setUpstreamParameters(
                eq(testIfName), eq(ipv4Addr), eq(null), mStringArrayCaptor.capture());
        assertTrue(mStringArrayCaptor.getValue().isEmpty());
        inOrder.verifyNoMoreInteractions();

        final String ipv4Gateway = "192.0.2.1";
        assertTrue(lp.addRoute(new RouteInfo(InetAddress.getByName(ipv4Gateway))));
        offload.setUpstreamLinkProperties(lp);
        // Upstream IPv4 addresses are not passed to the HAL.
        // No change in local addresses means no call to setLocalPrefixes().
        inOrder.verify(mHardware, never()).setLocalPrefixes(mStringArrayCaptor.capture());
        inOrder.verify(mHardware, times(1)).setUpstreamParameters(
                eq(testIfName), eq(ipv4Addr), eq(ipv4Gateway), mStringArrayCaptor.capture());
        assertTrue(mStringArrayCaptor.getValue().isEmpty());
        inOrder.verifyNoMoreInteractions();

        final String ipv6Gw1 = "fe80::cafe";
        assertTrue(lp.addRoute(new RouteInfo(InetAddress.getByName(ipv6Gw1))));
        offload.setUpstreamLinkProperties(lp);
        // Upstream IPv4 addresses are not passed to the HAL.
        // No change in local addresses means no call to setLocalPrefixes().
        inOrder.verify(mHardware, never()).setLocalPrefixes(mStringArrayCaptor.capture());
        inOrder.verify(mHardware, times(1)).setUpstreamParameters(
                eq(testIfName), eq(ipv4Addr), eq(ipv4Gateway), mStringArrayCaptor.capture());
        ArrayList<String> v6gws = mStringArrayCaptor.getValue();
        assertEquals(1, v6gws.size());
        assertTrue(v6gws.contains(ipv6Gw1));
        inOrder.verifyNoMoreInteractions();

        final String ipv6Gw2 = "fe80::d00d";
        assertTrue(lp.addRoute(new RouteInfo(InetAddress.getByName(ipv6Gw2))));
        offload.setUpstreamLinkProperties(lp);
        // Upstream IPv4 addresses are not passed to the HAL.
        // No change in local addresses means no call to setLocalPrefixes().
        inOrder.verify(mHardware, never()).setLocalPrefixes(mStringArrayCaptor.capture());
        inOrder.verify(mHardware, times(1)).setUpstreamParameters(
                eq(testIfName), eq(ipv4Addr), eq(ipv4Gateway), mStringArrayCaptor.capture());
        v6gws = mStringArrayCaptor.getValue();
        assertEquals(2, v6gws.size());
        assertTrue(v6gws.contains(ipv6Gw1));
        assertTrue(v6gws.contains(ipv6Gw2));
        inOrder.verifyNoMoreInteractions();

        // Stacked links do not factor in to offload programming.
        final LinkProperties stacked = new LinkProperties();
        stacked.setInterfaceName("stacked");
        stacked.addLinkAddress(new LinkAddress("192.0.2.129/25"));
        stacked.addRoute(new RouteInfo(InetAddress.getByName("192.0.2.254")));
        stacked.addRoute(new RouteInfo(InetAddress.getByName("fe80::bad:f00")));
        assertTrue(lp.addStackedLink(stacked));
        offload.setUpstreamLinkProperties(lp);
        // Upstream IPv4 addresses are not passed to the HAL.
        // No change in local addresses means no call to setLocalPrefixes().
        inOrder.verify(mHardware, never()).setLocalPrefixes(mStringArrayCaptor.capture());
        inOrder.verify(mHardware, times(1)).setUpstreamParameters(
                eq(testIfName), eq(ipv4Addr), eq(ipv4Gateway), mStringArrayCaptor.capture());
        v6gws = mStringArrayCaptor.getValue();
        assertEquals(2, v6gws.size());
        assertTrue(v6gws.contains(ipv6Gw1));
        assertTrue(v6gws.contains(ipv6Gw2));
        inOrder.verifyNoMoreInteractions();

        // Upstream IPv6 address do get passed down to the HAL.
        final String linkAddr6 = "2001:db8::c001:ace/64";
        assertTrue(lp.addLinkAddress(new LinkAddress(linkAddr6)));
        offload.setUpstreamLinkProperties(lp);
        inOrder.verify(mHardware, times(1)).setLocalPrefixes(mStringArrayCaptor.capture());
        ArrayList<String> localAddrs = mStringArrayCaptor.getValue();
        assertEquals(1, localAddrs.size());
        assertEquals(linkAddr6, localAddrs.get(0));
        inOrder.verify(mHardware, times(1)).setUpstreamParameters(
                eq(testIfName), eq(ipv4Addr), eq(ipv4Gateway), mStringArrayCaptor.capture());
        v6gws = mStringArrayCaptor.getValue();
        assertEquals(2, v6gws.size());
        assertTrue(v6gws.contains(ipv6Gw1));
        assertTrue(v6gws.contains(ipv6Gw2));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testWorkingWifiTetheringExample() throws Exception {
        setupFunctioningHardwareInterface();
        final OffloadController offload =
                new OffloadController(null, mHardware, mContentResolver, new SharedLog("test"));

        // When the WiFi interface tells Tethering it's in STATE_TETHERED, the
        // TetherMasterSM tries to start offload.
        offload.start();

        final InOrder inOrder = inOrder(mHardware);
        inOrder.verify(mHardware, times(1)).initOffloadConfig();
        inOrder.verify(mHardware, times(1)).initOffloadControl(
                any(OffloadHardwareInterface.ControlCallback.class));
        inOrder.verifyNoMoreInteractions();

        final String wlanIfname = "wlan_test0";
        final LinkProperties wlanLp = new LinkProperties();
        wlanLp.setInterfaceName(wlanIfname);

        // [1] IPv4 link properties arrive from the IpServer instance.
        final String wlanAddr4 = "192.168.43.1/24";
        final LinkAddress wlanLinkAddr4 = new LinkAddress(wlanAddr4);
        assertTrue(wlanLp.addLinkAddress(wlanLinkAddr4));
        assertTrue(wlanLp.addRoute(new RouteInfo(wlanLinkAddr4)));
        offload.notifyDownstreamLinkProperties(wlanLp);
        inOrder.verify(mHardware, times(1)).setLocalPrefixes(mStringArrayCaptor.capture());
        ArrayList<String> localAddrs = mStringArrayCaptor.getValue();
        assertEquals(1, localAddrs.size());
        assertEquals(wlanAddr4, localAddrs.get(0));
//        inOrder.verify(mHardware, times(1)).addDownstreamPrefix(
//                eq(wlanIfname), eq("192.168.43.0/24"));
//        inOrder.verifyNoMoreInteractions();

        // [2] Upstream IPv4 arrives.
        final LinkProperties mobileLp = new LinkProperties();
        mobileLp.setInterfaceName("rmnet_test0");

        // [3] Upstream IPv6 arrives.
        // [4] Downstream IPv6 arrives.
    }
}
