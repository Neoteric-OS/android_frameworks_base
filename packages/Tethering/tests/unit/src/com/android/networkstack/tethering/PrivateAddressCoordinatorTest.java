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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.InetAddresses;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.ip.IpServer;
import android.net.util.NetworkConstants;
import android.net.util.PrefixUtils;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class PrivateAddressCoordinatorTest {
    private static final String TEST_MOBILE_IFNAME = "test_rmnet_data0";
    private static final String TEST_WIFI_IFNAME = "test_wlan0";
    @Mock private IpServer mHotspotIpServer;
    @Mock private IpServer mUsbIpServer;
    @Mock private IpServer mEthernetIpServer;

    private PrivateAddressCoordinator mPrivateAddressCoordinator;
    private final IpPrefix mBluetoothPrefix = new IpPrefix("192.168.44.0/24");

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mPrivateAddressCoordinator = spy(new PrivateAddressCoordinator());
    }

    @Test
    public void testDownstreamPrefixRequest() throws Exception {
        LinkAddress address = mPrivateAddressCoordinator.requestDownstreamAddress(
                mHotspotIpServer);
        final IpPrefix hotspotPrefix = PrefixUtils.asIpPrefix(address);
        assertNotEquals(hotspotPrefix, mBluetoothPrefix);

        address = mPrivateAddressCoordinator.requestDownstreamAddress(
                mHotspotIpServer);
        final IpPrefix testDupRequest = PrefixUtils.asIpPrefix(address);
        assertNotEquals(hotspotPrefix, testDupRequest);
        assertNotEquals(mBluetoothPrefix, testDupRequest);
        mPrivateAddressCoordinator.releaseDownstreamPrefix(mHotspotIpServer);

        address = mPrivateAddressCoordinator.requestDownstreamAddress(
                mUsbIpServer);
        final IpPrefix usbPrefix = PrefixUtils.asIpPrefix(address);
        assertNotEquals(usbPrefix, mBluetoothPrefix);
        assertNotEquals(usbPrefix, hotspotPrefix);
        mPrivateAddressCoordinator.releaseDownstreamPrefix(mUsbIpServer);
    }

    @Test
    public void testRequestDownstreamAddress() throws Exception {
        LinkAddress expectedAddress = new LinkAddress("192.168.43.42/24");
        int fakeSubAddr = 0x2b00;
        when(mPrivateAddressCoordinator.getRandomSubAddr()).thenReturn(fakeSubAddr);
        LinkAddress actualAddress = mPrivateAddressCoordinator.requestDownstreamAddress(
                mHotspotIpServer);
        assertEquals(actualAddress, expectedAddress);
        mPrivateAddressCoordinator.releaseDownstreamPrefix(mHotspotIpServer);

        fakeSubAddr = 0x2b01;
        when(mPrivateAddressCoordinator.getRandomSubAddr()).thenReturn(fakeSubAddr);
        actualAddress = mPrivateAddressCoordinator.requestDownstreamAddress(
                mHotspotIpServer);
        assertEquals(actualAddress, expectedAddress);
        mPrivateAddressCoordinator.releaseDownstreamPrefix(mHotspotIpServer);

        fakeSubAddr = 0x2bff;
        when(mPrivateAddressCoordinator.getRandomSubAddr()).thenReturn(fakeSubAddr);
        actualAddress = mPrivateAddressCoordinator.requestDownstreamAddress(
                mHotspotIpServer);
        assertEquals(actualAddress, expectedAddress);
        mPrivateAddressCoordinator.releaseDownstreamPrefix(mHotspotIpServer);

        expectedAddress = new LinkAddress("192.168.43.5/24");
        fakeSubAddr = 0x2b05;
        when(mPrivateAddressCoordinator.getRandomSubAddr()).thenReturn(fakeSubAddr);
        actualAddress = mPrivateAddressCoordinator.requestDownstreamAddress(
                mHotspotIpServer);
        assertEquals(actualAddress, expectedAddress);
        mPrivateAddressCoordinator.releaseDownstreamPrefix(mHotspotIpServer);
    }

    @Test
    public void testReserveBluetoothPrefix() throws Exception {
        final int fakeSubAddr = 0x2c05;
        when(mPrivateAddressCoordinator.getRandomSubAddr()).thenReturn(fakeSubAddr);
        LinkAddress address = mPrivateAddressCoordinator.requestDownstreamAddress(
                mHotspotIpServer);
        final IpPrefix hotspotPrefix = PrefixUtils.asIpPrefix(address);
        assertNotEquals("Should not get reserved prefix: ", mBluetoothPrefix, hotspotPrefix);
        mPrivateAddressCoordinator.releaseDownstreamPrefix(mHotspotIpServer);
    }

    @Test
    public void testNoConflictDownstreamPrefix() throws Exception {
        final int fakeHotspotSubAddr = 0x2b05;
        final IpPrefix predefinedPrefix = new IpPrefix("192.168.43.0/24");
        when(mPrivateAddressCoordinator.getRandomSubAddr()).thenReturn(fakeHotspotSubAddr);
        LinkAddress address = mPrivateAddressCoordinator.requestDownstreamAddress(
                mHotspotIpServer);
        final IpPrefix hotspotPrefix = PrefixUtils.asIpPrefix(address);
        assertEquals("Wrong wifi perfix: ", predefinedPrefix, hotspotPrefix);
        when(mHotspotIpServer.getAddress()).thenReturn(address);

        address = mPrivateAddressCoordinator.requestDownstreamAddress(
                mUsbIpServer);
        final IpPrefix usbPrefix = PrefixUtils.asIpPrefix(address);
        assertNotEquals(predefinedPrefix, usbPrefix);

        mPrivateAddressCoordinator.releaseDownstreamPrefix(mHotspotIpServer);
        mPrivateAddressCoordinator.releaseDownstreamPrefix(mUsbIpServer);
        address = mPrivateAddressCoordinator.requestDownstreamAddress(
                mUsbIpServer);
        final IpPrefix allowUseFreePrefix = PrefixUtils.asIpPrefix(address);
        assertEquals("Fail to reselect available perfix: ", predefinedPrefix, allowUseFreePrefix);
    }

    private LinkProperties buildUpstreamLinkProperties(boolean withIPv4, boolean withIPv6,
            boolean isMobile) {
        final String testIface;
        final String testIpv4Address;
        if (isMobile) {
            testIface = TEST_MOBILE_IFNAME;
            testIpv4Address = "10.0.0.1";
        } else {
            testIface = TEST_WIFI_IFNAME;
            testIpv4Address = "192.168.43.5";
        }

        final LinkProperties prop = new LinkProperties();
        prop.setInterfaceName(testIface);

        if (withIPv4) {
            prop.addLinkAddress(
                    new LinkAddress(InetAddresses.parseNumericAddress(testIpv4Address),
                            NetworkConstants.IPV4_ADDR_BITS));
        }

        if (withIPv6) {
            prop.addLinkAddress(
                    new LinkAddress(InetAddresses.parseNumericAddress("2001:db8::"),
                            NetworkConstants.RFC7421_PREFIX_LENGTH));
        }
        return prop;
    }

    @Test
    public void testNoConflictUpstreamPrefix() throws Exception {
        final int fakeHotspotSubId = 43;
        final Network wifiNetwork = new Network(1);
        final Network mobileNetwork = new Network(2);
        final int fakeHotspotSubAddr = 0x2b05;
        final IpPrefix predefinedPrefix = new IpPrefix("192.168.43.0/24");
        // Force always get subAddress "43.5" for conflict testing.
        when(mPrivateAddressCoordinator.getRandomSubAddr()).thenReturn(fakeHotspotSubAddr);
        // 1. Enable hotspot with prefix 192.168.43.0/24
        final LinkAddress hotspotAddr = mPrivateAddressCoordinator.requestDownstreamAddress(
                mHotspotIpServer);
        final IpPrefix hotspotPrefix = PrefixUtils.asIpPrefix(hotspotAddr);
        assertEquals("Wrong wifi perfix: ", predefinedPrefix, hotspotPrefix);
        when(mHotspotIpServer.getAddress()).thenReturn(hotspotAddr);
        // 2. Update v6 only mobile network, hotspot prefix should not be removed.
        List<String> testConflicts;
        final LinkProperties v6OnlyMobileProp = buildUpstreamLinkProperties(false, true, true);
        mPrivateAddressCoordinator.updateUpstreamPrefix(mobileNetwork, v6OnlyMobileProp);
        verify(mHotspotIpServer, never()).sendMessage(IpServer.CMD_RESTART_TETHERING);
        mPrivateAddressCoordinator.removeUpstreamPrefix(mobileNetwork);
        // 3. Update v4 only mobile network, hotspot prefix should not be removed.
        final LinkProperties v4OnlyMobileProp = buildUpstreamLinkProperties(true, false, true);
        mPrivateAddressCoordinator.updateUpstreamPrefix(mobileNetwork, v4OnlyMobileProp);
        verify(mHotspotIpServer, never()).sendMessage(IpServer.CMD_RESTART_TETHERING);
        // 4. Update v4v6 mobile network, hotspot prefix should not be removed.
        final LinkProperties v4v6MobileProp = buildUpstreamLinkProperties(true, true, true);
        mPrivateAddressCoordinator.updateUpstreamPrefix(mobileNetwork, v4v6MobileProp);
        verify(mHotspotIpServer, never()).sendMessage(IpServer.CMD_RESTART_TETHERING);
        // 5. Update v6 only wifi network, hotspot prefix should not be removed.
        final LinkProperties v6OnlyWifiProp = buildUpstreamLinkProperties(false, true, false);
        mPrivateAddressCoordinator.updateUpstreamPrefix(wifiNetwork, v6OnlyWifiProp);
        verify(mHotspotIpServer, never()).sendMessage(IpServer.CMD_RESTART_TETHERING);
        mPrivateAddressCoordinator.removeUpstreamPrefix(wifiNetwork);
        // 6. Update v4 only wifi network, it conflict with hotspot prefix.
        final LinkProperties v4OnlyWifiProp = buildUpstreamLinkProperties(true, false, false);
        mPrivateAddressCoordinator.updateUpstreamPrefix(wifiNetwork, v4OnlyWifiProp);
        verify(mHotspotIpServer).sendMessage(IpServer.CMD_RESTART_TETHERING);
        reset(mHotspotIpServer);
        // 7. Restart hotspot again and its prefix is different previous.
        mPrivateAddressCoordinator.releaseDownstreamPrefix(mHotspotIpServer);
        mPrivateAddressCoordinator.clearUpstreamPrefixes();
        final LinkAddress hotspotAddr2 = mPrivateAddressCoordinator.requestDownstreamAddress(
                mHotspotIpServer);
        final IpPrefix hotspotPrefix2 = PrefixUtils.asIpPrefix(hotspotAddr2);
        assertNotEquals(hotspotPrefix, hotspotPrefix2);
        when(mHotspotIpServer.getAddress()).thenReturn(hotspotAddr2);
        mPrivateAddressCoordinator.updateUpstreamPrefix(wifiNetwork, v4OnlyWifiProp);
        verify(mHotspotIpServer, never()).sendMessage(IpServer.CMD_RESTART_TETHERING);
        // 7. Usb tethering can be enabled and its prefix is different with conflict one.
        final LinkAddress usbAddr = mPrivateAddressCoordinator.requestDownstreamAddress(
                mUsbIpServer);
        final IpPrefix usbPrefix = PrefixUtils.asIpPrefix(usbAddr);
        assertNotEquals(predefinedPrefix, usbPrefix);
        assertNotEquals(hotspotPrefix2, usbPrefix);
        when(mUsbIpServer.getAddress()).thenReturn(usbAddr);
        // 8. Disable wifi upstream
        mPrivateAddressCoordinator.removeUpstreamPrefix(wifiNetwork);
        final LinkAddress ethAddr = mPrivateAddressCoordinator.requestDownstreamAddress(
                mEthernetIpServer);
        final IpPrefix ethPrefix = PrefixUtils.asIpPrefix(ethAddr);
        assertNotEquals(predefinedPrefix, ethPrefix);
        assertNotEquals(hotspotPrefix2, ethPrefix);
        assertNotEquals(usbPrefix, ethPrefix);
    }
}
