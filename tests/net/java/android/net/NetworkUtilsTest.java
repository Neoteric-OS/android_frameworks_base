/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.net;

import static android.net.NetworkUtils.getImplicitNetmask;
import static android.net.NetworkUtils.inet4AddressToInt;
import static android.net.NetworkUtils.inetAddressToInt;
import static android.net.NetworkUtils.intToInet4Address;
import static android.net.NetworkUtils.intToInetAddress;
import static android.net.NetworkUtils.netmaskToPrefixLength;
import static android.net.NetworkUtils.prefixLengthToNetmaskInt;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.fail;

import android.support.test.runner.AndroidJUnit4;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.TreeSet;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@android.support.test.filters.SmallTest
public class NetworkUtilsTest {

    private InetAddress Address(String addr) {
        return InetAddress.parseNumericAddress(addr);
    }

    private Inet4Address IPv4Address(String addr) {
        return (Inet4Address) Address(addr);
    }

    @Test
    public void testGetImplicitNetmask() {
        assertEquals(8, getImplicitNetmask(IPv4Address("4.2.2.2")));
        assertEquals(8, getImplicitNetmask(IPv4Address("10.5.6.7")));
        assertEquals(16, getImplicitNetmask(IPv4Address("173.194.72.105")));
        assertEquals(16, getImplicitNetmask(IPv4Address("172.23.68.145")));
        assertEquals(24, getImplicitNetmask(IPv4Address("192.0.2.1")));
        assertEquals(24, getImplicitNetmask(IPv4Address("192.168.5.1")));
        assertEquals(32, getImplicitNetmask(IPv4Address("224.0.0.1")));
        assertEquals(32, getImplicitNetmask(IPv4Address("255.6.7.8")));
    }

    private void assertInvalidNetworkMask(Inet4Address addr) {
        try {
            netmaskToPrefixLength(addr);
            fail("Invalid netmask " + addr.getHostAddress() + " did not cause exception");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testInetAddressToInt_Deprecated() {
        assertEquals(0, inetAddressToInt(IPv4Address("0.0.0.0")));
        assertEquals(0x000080ff, inetAddressToInt(IPv4Address("255.128.0.0")));
        assertEquals(0x0080ff0a, inetAddressToInt(IPv4Address("10.255.128.0")));
        assertEquals(0x00feff0a, inetAddressToInt(IPv4Address("10.255.254.0")));
        assertEquals(0xfeffa8c0, inetAddressToInt(IPv4Address("192.168.255.254")));
        assertEquals(0xffffa8c0, inetAddressToInt(IPv4Address("192.168.255.255")));
    }

    @Test
    public void testIntToInetAddress_Deprecated() {
        assertEquals(IPv4Address("0.0.0.0"), intToInetAddress(0));
        assertEquals(IPv4Address("255.128.0.0"), intToInetAddress(0x000080ff));
        assertEquals(IPv4Address("10.255.128.0"), intToInetAddress(0x0080ff0a));
        assertEquals(IPv4Address("10.255.254.0"), intToInetAddress(0x00feff0a));
        assertEquals(IPv4Address("192.168.255.254"), intToInetAddress(0xfeffa8c0));
        assertEquals(IPv4Address("192.168.255.255"), intToInetAddress(0xffffa8c0));
    }

    @Test
    public void testInet4AddressToInt() {
        assertEquals(0, inet4AddressToInt(IPv4Address("0.0.0.0")));
        assertEquals(0xff800000, inet4AddressToInt(IPv4Address("255.128.0.0")));
        assertEquals(0x0aff8000, inet4AddressToInt(IPv4Address("10.255.128.0")));
        assertEquals(0x0afffe00, inet4AddressToInt(IPv4Address("10.255.254.0")));
        assertEquals(0xc0a8fffe, inet4AddressToInt(IPv4Address("192.168.255.254")));
        assertEquals(0xc0a8ffff, inet4AddressToInt(IPv4Address("192.168.255.255")));
    }

    @Test
    public void testIntToInet4Address() {
        assertEquals(IPv4Address("0.0.0.0"), intToInet4Address(0));
        assertEquals(IPv4Address("255.128.0.0"), intToInet4Address(0xff800000));
        assertEquals(IPv4Address("10.255.128.0"), intToInet4Address(0x0aff8000));
        assertEquals(IPv4Address("10.255.254.0"), intToInet4Address(0x0afffe00));
        assertEquals(IPv4Address("192.168.255.254"), intToInet4Address(0xc0a8fffe));
        assertEquals(IPv4Address("192.168.255.255"), intToInet4Address(0xc0a8ffff));
    }

    @Test
    public void testNetmaskToPrefixLength() {
        assertEquals(0, netmaskToPrefixLength(IPv4Address("0.0.0.0")));
        assertEquals(9, netmaskToPrefixLength(IPv4Address("255.128.0.0")));
        assertEquals(17, netmaskToPrefixLength(IPv4Address("255.255.128.0")));
        assertEquals(23, netmaskToPrefixLength(IPv4Address("255.255.254.0")));
        assertEquals(31, netmaskToPrefixLength(IPv4Address("255.255.255.254")));
        assertEquals(32, netmaskToPrefixLength(IPv4Address("255.255.255.255")));

        assertInvalidNetworkMask(IPv4Address("0.0.0.1"));
        assertInvalidNetworkMask(IPv4Address("255.255.255.253"));
        assertInvalidNetworkMask(IPv4Address("255.255.0.255"));
    }


    @Test
    public void testPrefixLengthToNetmaskInt() {
        assertEquals(0, prefixLengthToNetmaskInt(0));
        assertEquals(0xff800000 /* 255.128.0.0 */, prefixLengthToNetmaskInt(9));
        assertEquals(0xffff8000 /* 255.255.128.0 */, prefixLengthToNetmaskInt(17));
        assertEquals(0xfffffe00 /* 255.255.254.0 */, prefixLengthToNetmaskInt(23));
        assertEquals(0xfffffffe /* 255.255.255.254 */, prefixLengthToNetmaskInt(31));
        assertEquals(0xffffffff /* 255.255.255.255 */, prefixLengthToNetmaskInt(32));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPrefixLengthToNetmaskInt_NegativeLength() {
        prefixLengthToNetmaskInt(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPrefixLengthToNetmaskInt_LengthTooLarge() {
        prefixLengthToNetmaskInt(33);
    }

    private void checkAddressMasking(String expectedAddr, String addr, int prefixLength) {
        final int prefix = prefixLengthToNetmaskInt(prefixLength);
        final int addrInt = inet4AddressToInt(IPv4Address(addr));
        assertEquals(IPv4Address(expectedAddr), intToInet4Address(prefix & addrInt));
    }

    @Test
    public void testPrefixLengthToNetmaskInt_MaskAddr() {
        checkAddressMasking("192.168.0.0", "192.168.128.1", 16);
        checkAddressMasking("255.240.0.0", "255.255.255.255", 12);
        checkAddressMasking("255.255.255.255", "255.255.255.255", 32);
        checkAddressMasking("0.0.0.0", "255.255.255.255", 0);
    }

    @Test
    public void testRoutedIPv4AddressCount() {
        final TreeSet<IpPrefix> set = new TreeSet<>(IpPrefix.lengthComparator());
        // No routes routes to no addresses.
        assertEquals(0, NetworkUtils.routedIPv4AddressCount(set));

        set.add(new IpPrefix("0.0.0.0/0"));
        assertEquals(1l << 32, NetworkUtils.routedIPv4AddressCount(set));

        set.add(new IpPrefix("20.18.0.0/16"));
        set.add(new IpPrefix("20.18.0.0/24"));
        set.add(new IpPrefix("20.18.0.0/8"));
        // There is a default route, still covers everything
        assertEquals(1l << 32, NetworkUtils.routedIPv4AddressCount(set));

        set.clear();
        set.add(new IpPrefix("20.18.0.0/24"));
        set.add(new IpPrefix("20.18.0.0/8"));
        // The 8-length includes the 24-length prefix
        assertEquals(1l << 24, NetworkUtils.routedIPv4AddressCount(set));

        set.add(new IpPrefix("10.10.10.126/25"));
        // The 8-length does not include this 25-length prefix
        assertEquals((1l << 24) + (1 << 7), NetworkUtils.routedIPv4AddressCount(set));

        set.clear();
        set.add(new IpPrefix("1.2.3.4/32"));
        set.add(new IpPrefix("1.2.3.4/32"));
        set.add(new IpPrefix("1.2.3.4/32"));
        set.add(new IpPrefix("1.2.3.4/32"));
        assertEquals(1l, NetworkUtils.routedIPv4AddressCount(set));

        set.add(new IpPrefix("1.2.3.5/32"));
        set.add(new IpPrefix("1.2.3.6/32"));

        set.add(new IpPrefix("1.2.3.7/32"));
        set.add(new IpPrefix("1.2.3.8/32"));
        set.add(new IpPrefix("1.2.3.9/32"));
        set.add(new IpPrefix("1.2.3.0/32"));
        assertEquals(7l, NetworkUtils.routedIPv4AddressCount(set));

        // 1.2.3.4/30 eats 1.2.3.{4-7}/32
        set.add(new IpPrefix("1.2.3.4/30"));
        set.add(new IpPrefix("6.2.3.4/28"));
        set.add(new IpPrefix("120.2.3.4/16"));
        assertEquals(7l - 4 + 4 + 16 + 65536, NetworkUtils.routedIPv4AddressCount(set));
    }

    @Test
    public void testRoutedIPv6AddressCount() {
        final TreeSet<IpPrefix> set = new TreeSet<>(IpPrefix.lengthComparator());
        // No routes routes to no addresses.
        assertEquals(BigInteger.ZERO, NetworkUtils.routedIPv6AddressCount(set));

        set.add(new IpPrefix("::/0"));
        assertEquals(BigInteger.ONE.shiftLeft(128), NetworkUtils.routedIPv6AddressCount(set));

        set.add(new IpPrefix("1234:622a::18/64"));
        set.add(new IpPrefix("add4:f00:80:f7:1111::6adb/96"));
        set.add(new IpPrefix("add4:f00:80:f7:1111::6adb/8"));
        // There is a default route, still covers everything
        assertEquals(BigInteger.ONE.shiftLeft(128), NetworkUtils.routedIPv6AddressCount(set));

        set.clear();
        set.add(new IpPrefix("add4:f00:80:f7:1111::6adb/96"));
        set.add(new IpPrefix("add4:f00:80:f7:1111::6adb/8"));
        // The 8-length includes the 96-length prefix
        assertEquals(BigInteger.ONE.shiftLeft(120), NetworkUtils.routedIPv6AddressCount(set));

        set.add(new IpPrefix("10::26/64"));
        // The 8-length does not include this 64-length prefix
        assertEquals(BigInteger.ONE.shiftLeft(120).add(BigInteger.ONE.shiftLeft(64)),
                NetworkUtils.routedIPv6AddressCount(set));

        set.clear();
        set.add(new IpPrefix("add4:f00:80:f7:1111::6ad4/128"));
        set.add(new IpPrefix("add4:f00:80:f7:1111::6ad4/128"));
        set.add(new IpPrefix("add4:f00:80:f7:1111::6ad4/128"));
        set.add(new IpPrefix("add4:f00:80:f7:1111::6ad4/128"));
        assertEquals(BigInteger.ONE, NetworkUtils.routedIPv6AddressCount(set));

        set.add(new IpPrefix("add4:f00:80:f7:1111::6ad5/128"));
        set.add(new IpPrefix("add4:f00:80:f7:1111::6ad6/128"));
        set.add(new IpPrefix("add4:f00:80:f7:1111::6ad7/128"));
        set.add(new IpPrefix("add4:f00:80:f7:1111::6ad8/128"));
        set.add(new IpPrefix("add4:f00:80:f7:1111::6ad9/128"));
        set.add(new IpPrefix("add4:f00:80:f7:1111::6ad0/128"));
        assertEquals(BigInteger.valueOf(7), NetworkUtils.routedIPv6AddressCount(set));

        // add4:f00:80:f7:1111::6ad4/126 eats add4:f00:8[:f7:1111::6ad{4-7}/128
        set.add(new IpPrefix("add4:f00:80:f7:1111::6ad4/126"));
        set.add(new IpPrefix("d00d:f00:80:f7:1111::6ade/124"));
        set.add(new IpPrefix("f00b:a33::/112"));
        assertEquals(BigInteger.valueOf(7l - 4 + 4 + 16 + 65536),
                NetworkUtils.routedIPv6AddressCount(set));
    }
}
