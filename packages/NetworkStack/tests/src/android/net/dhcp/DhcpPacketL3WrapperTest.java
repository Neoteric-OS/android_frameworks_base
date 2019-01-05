/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.net.dhcp;

import static android.net.dhcp.DhcpPacket.DHCP_BROADCAST_ADDRESS;
import static android.net.dhcp.DhcpPacket.DHCP_DNS_SERVER;
import static android.net.dhcp.DhcpPacket.DHCP_DOMAIN_NAME;
import static android.net.dhcp.DhcpPacket.DHCP_LEASE_TIME;
import static android.net.dhcp.DhcpPacket.DHCP_MTU;
import static android.net.dhcp.DhcpPacket.DHCP_REBINDING_TIME;
import static android.net.dhcp.DhcpPacket.DHCP_RENEWAL_TIME;
import static android.net.dhcp.DhcpPacket.DHCP_ROUTER;
import static android.net.dhcp.DhcpPacket.DHCP_SUBNET_MASK;
import static android.net.dhcp.DhcpPacket.DHCP_VENDOR_INFO;
import static android.net.dhcp.DhcpPacket.ENCAP_BOOTP;
import static android.net.dhcp.DhcpPacket.ENCAP_L2;
import static android.net.dhcp.DhcpPacket.ENCAP_L3;
import static android.net.dhcp.DhcpPacket.INADDR_ANY;
import static android.net.dhcp.DhcpPacket.INADDR_BROADCAST;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.DhcpResults;
import android.net.LinkAddress;
import android.net.NetworkUtils;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.internal.util.HexDump;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class DhcpPacketL3WrapperTest {

    @Before
    public void setUp() {
        DhcpPacket.testOverrideVendorId = "android-dhcp-???";
        DhcpPacket.testOverrideHostname = "android-01234567890abcde";
    }

    @After
    public void tearDown() throws Exception {
        DhcpClient.setRapidCommitOption(false);
    }

    private static Inet4Address v4Address(String addrString) throws IllegalArgumentException {
        return (Inet4Address) NetworkUtils.numericToInetAddress(addrString);
    }

    private void assertDhcpResults(String ipAddress, String gateway, String dnsServersString,
            String domains, String serverAddress, String vendorInfo, int leaseDuration,
            boolean hasMeteredHint, int mtu, DhcpResults dhcpResults) throws Exception {
        assertEquals(new LinkAddress(ipAddress), dhcpResults.ipAddress);
        assertEquals(v4Address(gateway), dhcpResults.gateway);

        String[] dnsServerStrings = dnsServersString.split(",");
        ArrayList dnsServers = new ArrayList();
        for (String dnsServerString : dnsServerStrings) {
            dnsServers.add(v4Address(dnsServerString));
        }
        assertEquals(dnsServers, dhcpResults.dnsServers);

        assertEquals(domains, dhcpResults.domains);
        assertEquals(v4Address(serverAddress), dhcpResults.serverAddress);
        assertEquals(vendorInfo, dhcpResults.vendorInfo);
        assertEquals(leaseDuration, dhcpResults.leaseDuration);
        assertEquals(hasMeteredHint, dhcpResults.hasMeteredHint());
        assertEquals(mtu, dhcpResults.mtu);
    }

    @Test
    public void testDecodePacketLengthL3() throws Exception {
        final byte[] packet = HexDump.hexStringToByteArray(
                // IP header.
                "451001480000000080118849c0a89003c0a89ff7"
                        // UDP header.
                        + "004300440134dcfa"
                        // BOOTP header.
                        + "02010600c997a63b0000000000000000c0a89ff70000000000000000"
                        // MAC address.
                        + "30766ff2a90c00000000000000000000"
                        // Server name.
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        // File.
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        // Options
                        + "638253633501023604c0a89003330400001c200104fffff0000304c0a89ffe06"
                        + "0808080808080804043a0400000e103b040000189cff00000000000000000000");

        DhcpPacket offerPacket = DhcpPacketL3Wrapper.decodePacket(
                packet, packet.length, ENCAP_L3).mPacket;
        assertTrue(offerPacket instanceof DhcpOfferPacket);  // Implicitly checks it's non-null.
        DhcpResults dhcpResults = offerPacket.toDhcpResults();
        assertDhcpResults("192.168.159.247/20", "192.168.159.254", "8.8.8.8,8.8.4.4",
                null, "192.168.144.3", null, 7200, false, 0, dhcpResults);
    }

    @Test
    public void testDecodePacketLengthL2() throws Exception {
        final byte[] packet = HexDump.hexStringToByteArray(
                // Ethernet header.
                "b4cef6000000e80462236e300800"
                        // IP header.
                        + "451001480000000080118849c0a89003c0a89ff7"
                        // UDP header.
                        + "004300440134dcfa"
                        // BOOTP header.
                        + "02010600c997a63b0000000000000000c0a89ff70000000000000000"
                        // MAC address.
                        + "30766ff2a90c00000000000000000000"
                        // Server name.
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        // File.
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        // Options
                        + "638253633501023604c0a89003330400001c200104fffff0000304c0a89ffe06"
                        + "0808080808080804043a0400000e103b040000189cff00000000000000000000");

        DhcpPacket offerPacket = DhcpPacketL3Wrapper.decodePacket(
                packet, packet.length, ENCAP_L2).mPacket;
        assertTrue(offerPacket instanceof DhcpOfferPacket);
        DhcpResults dhcpResults = offerPacket.toDhcpResults();
        assertDhcpResults("192.168.159.247/20", "192.168.159.254", "8.8.8.8,8.8.4.4",
                null, "192.168.144.3", null, 7200, false, 0, dhcpResults);
    }

    @Test
    public void testDecodePacketLengthBOOTP() throws Exception {
        final byte[] packet = HexDump.hexStringToByteArray(
                // BOOTP header.
                "02010600c997a63b0000000000000000c0a89ff70000000000000000"
                        // MAC address.
                        + "30766ff2a90c00000000000000000000"
                        // Server name.
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        // File.
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        // Options
                        + "638253633501023604c0a89003330400001c200104fffff0000304c0a89ffe06"
                        + "0808080808080804043a0400000e103b040000189cff00000000000000000000");

        DhcpPacket offerPacket = DhcpPacketL3Wrapper.decodePacket(
                packet, packet.length, ENCAP_BOOTP).mPacket;
        assertTrue(offerPacket instanceof DhcpOfferPacket);
        DhcpResults dhcpResults = offerPacket.toDhcpResults();
        assertDhcpResults("192.168.159.247/20", "192.168.159.254", "8.8.8.8,8.8.4.4",
                null, "192.168.144.3", null, 7200, false, 0, dhcpResults);
    }

    @Test
    public void testDecodePacketL3() throws Exception {
        final ByteBuffer packet = ByteBuffer.wrap(HexDump.hexStringToByteArray(
                // IP header.
                "451001480000000080118849c0a89003c0a89ff7"
                        // UDP header.
                        + "004300440134dcfa"
                        // BOOTP header.
                        + "02010600c997a63b0000000000000000c0a89ff70000000000000000"
                        // MAC address.
                        + "30766ff2a90c00000000000000000000"
                        // Server name.
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        // File.
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        // Options
                        + "638253633501023604c0a89003330400001c200104fffff0000304c0a89ffe06"
                        + "0808080808080804043a0400000e103b040000189cff00000000000000000000"));

        DhcpPacket offerPacket = DhcpPacketL3Wrapper.decodePacket(packet, ENCAP_L3).mPacket;
        assertTrue(offerPacket instanceof DhcpOfferPacket);  // Implicitly checks it's non-null.
        DhcpResults dhcpResults = offerPacket.toDhcpResults();
        assertDhcpResults("192.168.159.247/20", "192.168.159.254", "8.8.8.8,8.8.4.4",
                null, "192.168.144.3", null, 7200, false, 0, dhcpResults);
    }

    @Test
    public void testDecodePacketL2() throws Exception {
        final ByteBuffer packet = ByteBuffer.wrap(HexDump.hexStringToByteArray(
                // Ethernet header.
                "b4cef6000000e80462236e300800"
                        // IP header.
                        + "451001480000000080118849c0a89003c0a89ff7"
                        // UDP header.
                        + "004300440134dcfa"
                        // BOOTP header.
                        + "02010600c997a63b0000000000000000c0a89ff70000000000000000"
                        // MAC address.
                        + "30766ff2a90c00000000000000000000"
                        // Server name.
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        // File.
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        // Options
                        + "638253633501023604c0a89003330400001c200104fffff0000304c0a89ffe06"
                        + "0808080808080804043a0400000e103b040000189cff00000000000000000000"));

        DhcpPacket offerPacket = DhcpPacketL3Wrapper.decodePacket(packet, ENCAP_L2).mPacket;
        assertTrue(offerPacket instanceof DhcpOfferPacket);
        DhcpResults dhcpResults = offerPacket.toDhcpResults();
        assertDhcpResults("192.168.159.247/20", "192.168.159.254", "8.8.8.8,8.8.4.4",
                null, "192.168.144.3", null, 7200, false, 0, dhcpResults);
    }

    @Test
    public void testDecodePacketBOOTP() throws Exception {
        final ByteBuffer packet = ByteBuffer.wrap(HexDump.hexStringToByteArray(
                // BOOTP header.
                "02010600c997a63b0000000000000000c0a89ff70000000000000000"
                        // MAC address.
                        + "30766ff2a90c00000000000000000000"
                        // Server name.
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        // File.
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000000000000000"
                        // Options
                        + "638253633501023604c0a89003330400001c200104fffff0000304c0a89ffe06"
                        + "0808080808080804043a0400000e103b040000189cff00000000000000000000"));

        DhcpPacket offerPacket = DhcpPacketL3Wrapper.decodePacket(packet, ENCAP_BOOTP).mPacket;
        assertTrue(offerPacket instanceof DhcpOfferPacket);
        DhcpResults dhcpResults = offerPacket.toDhcpResults();
        assertDhcpResults("192.168.159.247/20", "192.168.159.254", "8.8.8.8,8.8.4.4",
                null, "192.168.144.3", null, 7200, false, 0, dhcpResults);
    }

    @Test
    public void testBuildDiscoverPacket() throws Exception {
        short secs = 7;
        int transactionId = 0xdeadbeef;
        byte[] hwaddr = {
                (byte) 0xda, (byte) 0x01, (byte) 0x19, (byte) 0x5b, (byte) 0xb1, (byte) 0x7a
        };

        DhcpClient.setRapidCommitOption(false);
        DhcpPacket packet = DhcpPacket.buildDiscoverPacket(
                transactionId, secs, hwaddr, false /* do unicast */, DhcpClient.REQUESTED_PARAMS);
        ByteBuffer packetBuffer = new DhcpPacketL3Wrapper(INADDR_ANY /* srcAddr */,
                INADDR_BROADCAST /* dstAddr */, true /* clientPacket */, packet)
                .buildPacket();

        byte[] headers = new byte[]{
                // IP header.
                (byte) 0x45, (byte) 0x10, (byte) 0x01, (byte) 0x56,
                (byte) 0x00, (byte) 0x00, (byte) 0x40, (byte) 0x00,
                (byte) 0x40, (byte) 0x11, (byte) 0x39, (byte) 0x88,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                // UDP header.
                (byte) 0x00, (byte) 0x44, (byte) 0x00, (byte) 0x43,
                (byte) 0x01, (byte) 0x42, (byte) 0x6a, (byte) 0x4a,
                // BOOTP.
                (byte) 0x01, (byte) 0x01, (byte) 0x06, (byte) 0x00,
                (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef,
                (byte) 0x00, (byte) 0x07, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0xda, (byte) 0x01, (byte) 0x19, (byte) 0x5b,
                (byte) 0xb1, (byte) 0x7a
        };
        byte[] options = new byte[]{
                // Magic cookie 0x63825363.
                (byte) 0x63, (byte) 0x82, (byte) 0x53, (byte) 0x63,
                // Message type DISCOVER.
                (byte) 0x35, (byte) 0x01, (byte) 0x01,
                // Client identifier Ethernet, da:01:19:5b:b1:7a.
                (byte) 0x3d, (byte) 0x07,
                (byte) 0x01,
                (byte) 0xda, (byte) 0x01, (byte) 0x19, (byte) 0x5b, (byte) 0xb1, (byte) 0x7a,
                // Max message size 1500.
                (byte) 0x39, (byte) 0x02, (byte) 0x05, (byte) 0xdc,
                // Version "android-dhcp-???".
                (byte) 0x3c, (byte) 0x10,
                'a', 'n', 'd', 'r', 'o', 'i', 'd', '-', 'd', 'h', 'c', 'p', '-', '?', '?', '?',
                // Hostname "android-01234567890abcde"
                (byte) 0x0c, (byte) 0x18,
                'a', 'n', 'd', 'r', 'o', 'i', 'd', '-',
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', 'a', 'b', 'c', 'd', 'e',
                // Requested parameter list.
                (byte) 0x37, (byte) 0x0a,
                DHCP_SUBNET_MASK,
                DHCP_ROUTER,
                DHCP_DNS_SERVER,
                DHCP_DOMAIN_NAME,
                DHCP_MTU,
                DHCP_BROADCAST_ADDRESS,
                DHCP_LEASE_TIME,
                DHCP_RENEWAL_TIME,
                DHCP_REBINDING_TIME,
                DHCP_VENDOR_INFO,
                // End options.
                (byte) 0xff,
                // Our packets are always of even length. TODO: find out why and possibly fix it.
                (byte) 0x00
        };
        byte[] expected = new byte[DhcpPacketL3Wrapper.MIN_PACKET_LENGTH_L3 + options.length];
        assertTrue((expected.length & 1) == 0);
        System.arraycopy(headers, 0, expected, 0, headers.length);
        System.arraycopy(
                options, 0, expected, DhcpPacketL3Wrapper.MIN_PACKET_LENGTH_L3, options.length);

        byte[] actual = new byte[packetBuffer.limit()];
        packetBuffer.get(actual);
        String msg =
                "Expected:\n  " + Arrays.toString(expected)
                        + "\nActual:\n  " + Arrays.toString(actual);
        assertTrue(msg, Arrays.equals(expected, actual));
    }

    @Test
    public void testBuildDiscoverPacketWithRapidCommitOption() throws Exception {
        short secs = 7;
        int transactionId = 0xdeadbeef;
        byte[] hwaddr = {
                (byte) 0xda, (byte) 0x01, (byte) 0x19, (byte) 0x5b, (byte) 0xb1, (byte) 0x7a
        };

        DhcpClient.setRapidCommitOption(true);
        DhcpPacket packet = DhcpPacket.buildDiscoverPacket(
                transactionId, secs, hwaddr, false /* do unicast */, DhcpClient.REQUESTED_PARAMS);
        ByteBuffer packetBuffer = new DhcpPacketL3Wrapper(INADDR_ANY /* srcAddr */,
                INADDR_BROADCAST /* dstAddr */, true /* clientPacket */, packet)
                .buildPacket();

        byte[] headers = new byte[]{
                // IP header.
                (byte) 0x45, (byte) 0x10, (byte) 0x01, (byte) 0x58,
                (byte) 0x00, (byte) 0x00, (byte) 0x40, (byte) 0x00,
                (byte) 0x40, (byte) 0x11, (byte) 0x39, (byte) 0x86,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                // UDP header.
                (byte) 0x00, (byte) 0x44, (byte) 0x00, (byte) 0x43,
                (byte) 0x01, (byte) 0x44, (byte) 0x1a, (byte) 0x46,
                // BOOTP.
                (byte) 0x01, (byte) 0x01, (byte) 0x06, (byte) 0x00,
                (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef,
                (byte) 0x00, (byte) 0x07, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0xda, (byte) 0x01, (byte) 0x19, (byte) 0x5b,
                (byte) 0xb1, (byte) 0x7a
        };
        byte[] options = new byte[]{
                // Magic cookie 0x63825363.
                (byte) 0x63, (byte) 0x82, (byte) 0x53, (byte) 0x63,
                // Message type DISCOVER.
                (byte) 0x35, (byte) 0x01, (byte) 0x01,
                // Client identifier Ethernet, da:01:19:5b:b1:7a.
                (byte) 0x3d, (byte) 0x07,
                (byte) 0x01,
                (byte) 0xda, (byte) 0x01, (byte) 0x19, (byte) 0x5b, (byte) 0xb1, (byte) 0x7a,
                // Max message size 1500.
                (byte) 0x39, (byte) 0x02, (byte) 0x05, (byte) 0xdc,
                // Version "android-dhcp-???".
                (byte) 0x3c, (byte) 0x10,
                'a', 'n', 'd', 'r', 'o', 'i', 'd', '-', 'd', 'h', 'c', 'p', '-', '?', '?', '?',
                // Hostname "android-01234567890abcde"
                (byte) 0x0c, (byte) 0x18,
                'a', 'n', 'd', 'r', 'o', 'i', 'd', '-',
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', 'a', 'b', 'c', 'd', 'e',
                // Requested parameter list.
                (byte) 0x37, (byte) 0x0a,
                DHCP_SUBNET_MASK,
                DHCP_ROUTER,
                DHCP_DNS_SERVER,
                DHCP_DOMAIN_NAME,
                DHCP_MTU,
                DHCP_BROADCAST_ADDRESS,
                DHCP_LEASE_TIME,
                DHCP_RENEWAL_TIME,
                DHCP_REBINDING_TIME,
                DHCP_VENDOR_INFO,
                // Rapid Commit
                (byte) 0x50, (byte) 0x00,
                // End options.
                (byte) 0xff,
                // Our packets are always of even length. TODO: find out why and possibly fix it.
                (byte) 0x00
        };
        byte[] expected = new byte[DhcpPacketL3Wrapper.MIN_PACKET_LENGTH_L3 + options.length];
        assertTrue((expected.length & 1) == 0);
        System.arraycopy(headers, 0, expected, 0, headers.length);
        System.arraycopy(
                options, 0, expected, DhcpPacketL3Wrapper.MIN_PACKET_LENGTH_L3, options.length);

        byte[] actual = new byte[packetBuffer.limit()];
        packetBuffer.get(actual);
        String msg =
                "Expected:\n  " + Arrays.toString(expected)
                        + "\nActual:\n  " + Arrays.toString(actual);
        assertTrue(msg, Arrays.equals(expected, actual));
    }
}
