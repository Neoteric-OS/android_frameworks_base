/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class IpRangeTest {

    private static InetAddress address(String addr) {
        return InetAddress.parseNumericAddress(addr);
    }

    private static final Inet4Address IPV4_ADDR = (Inet4Address) address("192.0.2.4");
    private static final Inet4Address IPV4_RANGE_END = (Inet4Address) address("192.0.3.1");
    private static final Inet6Address IPV6_ADDR = (Inet6Address) address("2001:db8::");
    private static final Inet6Address IPV6_RANGE_END = (Inet6Address) address("2001:db9:010f::");

    // Explicitly cast everything to byte because "error: possible loss of precision".
    private static final byte[] IPV4_BYTES = {(byte) 192, (byte) 0, (byte) 2, (byte) 4};
    private static final byte[] IPV6_BYTES = {
        (byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xb8,
        (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef,
        (byte) 0x0f, (byte) 0x00, (byte) 0x00, (byte) 0x00,
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xa0
    };

    @Test
    public void testConstructorBadArguments() {
        try {
            new IpRange(null, IPV6_ADDR);
            fail("Expected NullPointerException: null start address");
        } catch (RuntimeException expected) {
        }

        try {
            new IpRange(IPV6_ADDR, null);
            fail("Expected NullPointerException: null end address");
        } catch (RuntimeException expected) {
        }

        try {
            new IpRange(null, null);
            fail("Expected NullPointerException: null addresses");
        } catch (RuntimeException expected) {
        }

        try {
            new IpRange(null, 0);
            fail("Expected NullPointerException: null start address");
        } catch (RuntimeException expected) {
        }

        try {
            new IpRange(IPV4_ADDR, -1);
            fail("Expected IllegalArgumentException: invalid prefix length");
        } catch (RuntimeException expected) {
        }

        try {
            new IpRange(IPV4_ADDR, 33);
            fail("Expected IllegalArgumentException: invalid prefix length");
        } catch (RuntimeException expected) {
        }

        try {
            new IpRange(IPV6_ADDR, -1);
            fail("Expected IllegalArgumentException: invalid prefix length");
        } catch (RuntimeException expected) {
        }

        try {
            new IpRange(IPV6_ADDR, 129);
            fail("Expected IllegalArgumentException: invalid prefix length");
        } catch (RuntimeException expected) {
        }
    }

    @Test
    public void testConstructor() {
        IpRange r = new IpRange(IPV4_ADDR, 32);
        assertEquals(IPV4_ADDR, r.getStartAddr());
        assertEquals(IPV4_ADDR, r.getEndAddr());

        r = new IpRange(IPV4_ADDR, 16);
        assertEquals(IPV4_ADDR, r.getStartAddr());
        assertEquals(address("192.0.255.255"), r.getEndAddr());

        r = new IpRange(IPV6_ADDR, 128);
        assertEquals(IPV6_ADDR, r.getStartAddr());
        assertEquals(IPV6_ADDR, r.getEndAddr());

        r = new IpRange(IPV6_ADDR, 64);
        assertEquals(IPV6_ADDR, r.getStartAddr());
        assertEquals(address("2001:db8:0:0:ffff:ffff:ffff:ffff"), r.getEndAddr());
    }

    @Test
    public void testContainsRangeEqualRanges() {
        IpRange r1 = new IpRange(IPV6_ADDR, 32);
        IpRange r2 = new IpRange(IPV6_ADDR, 32);

        assertTrue(r1.containsRange(r2));
    }

    @Test
    public void testContainsRangeSubset() {
        IpRange r1 = new IpRange(IPV6_ADDR, 32);
        IpRange r2 = new IpRange(IPV6_ADDR, 36);

        assertTrue(r1.containsRange(r2));
    }

    @Test
    public void testContainsRangeNotContains() {
        IpRange r1 = new IpRange(IPV6_ADDR, 32);
        IpRange r2 = new IpRange(IPV6_ADDR, 30);

        assertFalse(r1.containsRange(r2));
    }

    @Test
    public void testIsOverlappingRangeEquals() {
        IpRange r1 = new IpRange(IPV6_ADDR, 32);
        IpRange r2 = new IpRange(IPV6_ADDR, 32);

        assertTrue(r1.isOverlappingRange(r2));
    }

    @Test
    public void testIsOverlappingRangeDisjoint() {
        IpRange r1 = new IpRange(IPV6_ADDR, 32);
        IpRange r2 = new IpRange(address("2001:db9::"), 32);

        assertFalse(r1.isOverlappingRange(r2));
    }

    @Test
    public void testIsOverlappingRangePartialOverlap() {
        IpRange r1 = new IpRange(address("2001:db9::"), 32);
        IpRange r2 = new IpRange(address("2001:db8::"), address("2001:db9::1"));

        assertTrue(r1.isOverlappingRange(r2));
    }

    @Test
    public void testBigIntegerInetAddrUtilsIsLossless() throws Exception {
        assertEquals(
                IPV6_ADDR,
                IpRange.bigIntegerToInetAddress(IpRange.addrToBigInteger(IPV6_ADDR), true));
        assertEquals(
                IPV4_ADDR,
                IpRange.bigIntegerToInetAddress(IpRange.addrToBigInteger(IPV4_ADDR), false));
    }

    @Test
    public void testIpRangeToPrefixesIpv4FullRange() throws Exception {
        IpRange range = new IpRange(address("0.0.0.0"), address("255.255.255.255"));
        Set<IpPrefix> prefixes = new HashSet<>();
        prefixes.add(new IpPrefix("0.0.0.0/0"));

        assertEquals(prefixes, new HashSet<>(range.asIpPrefixes()));
    }

    @Test
    public void testIpRangeToPrefixesIpv4() throws Exception {
        IpRange range = new IpRange(IPV4_ADDR, IPV4_RANGE_END);
        Set<IpPrefix> prefixes = new HashSet<>();
        prefixes.add(new IpPrefix("192.0.2.4/30"));
        prefixes.add(new IpPrefix("192.0.2.8/29"));
        prefixes.add(new IpPrefix("192.0.2.16/28"));
        prefixes.add(new IpPrefix("192.0.2.32/27"));
        prefixes.add(new IpPrefix("192.0.2.64/26"));
        prefixes.add(new IpPrefix("192.0.2.128/25"));
        prefixes.add(new IpPrefix("192.0.3.0/31"));

        assertEquals(prefixes, new HashSet<>(range.asIpPrefixes()));
    }

    @Test
    public void testIpRangeToPrefixesIpv6FullRange() throws Exception {
        IpRange range =
                new IpRange(address("::"), address("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"));
        Set<IpPrefix> prefixes = new HashSet<>();
        prefixes.add(new IpPrefix("::/0"));

        assertEquals(prefixes, new HashSet<>(range.asIpPrefixes()));
    }

    @Test
    public void testIpRangeToPrefixesIpv6() throws Exception {
        IpRange range = new IpRange(IPV6_ADDR, IPV6_RANGE_END);
        Set<IpPrefix> prefixes = new HashSet<>();
        prefixes.add(new IpPrefix("2001:db8:0:0:0:0:0:0/32"));
        prefixes.add(new IpPrefix("2001:db9:0:0:0:0:0:0/40"));
        prefixes.add(new IpPrefix("2001:db9:100:0:0:0:0:0/45"));
        prefixes.add(new IpPrefix("2001:db9:108:0:0:0:0:0/46"));
        prefixes.add(new IpPrefix("2001:db9:10c:0:0:0:0:0/47"));
        prefixes.add(new IpPrefix("2001:db9:10e:0:0:0:0:0/48"));
        prefixes.add(new IpPrefix("2001:db9:10f:0:0:0:0:0/128"));

        assertEquals(prefixes, new HashSet<>(range.asIpPrefixes()));
    }
}
