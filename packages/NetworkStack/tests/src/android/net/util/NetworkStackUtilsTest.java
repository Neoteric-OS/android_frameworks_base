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

package android.net.util;

import static android.net.util.NetworkStackUtils.getBroadcastAddress;
import static android.net.util.NetworkStackUtils.getImplicitNetmask;

import static junit.framework.Assert.assertEquals;

import android.net.InetAddresses;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet4Address;

@RunWith(AndroidJUnit4.class)
@android.support.test.filters.SmallTest
public class NetworkStackUtilsTest {

    private Inet4Address ipv4Address(String addr) {
        return (Inet4Address) InetAddresses.parseNumericAddress(addr);
    }

    @Test
    public void testGetImplicitNetmask() {
        assertEquals(8, getImplicitNetmask(ipv4Address("4.2.2.2")));
        assertEquals(8, getImplicitNetmask(ipv4Address("10.5.6.7")));
        assertEquals(16, getImplicitNetmask(ipv4Address("173.194.72.105")));
        assertEquals(16, getImplicitNetmask(ipv4Address("172.23.68.145")));
        assertEquals(24, getImplicitNetmask(ipv4Address("192.0.2.1")));
        assertEquals(24, getImplicitNetmask(ipv4Address("192.168.5.1")));
        assertEquals(32, getImplicitNetmask(ipv4Address("224.0.0.1")));
        assertEquals(32, getImplicitNetmask(ipv4Address("255.6.7.8")));
    }

    @Test
    public void testGetBroadcastAddress() {
        assertEquals("192.168.15.255",
                getBroadcastAddress(ipv4Address("192.168.0.123"), 20).getHostAddress());
        assertEquals("192.255.255.255",
                getBroadcastAddress(ipv4Address("192.168.0.123"), 8).getHostAddress());
        assertEquals("192.168.0.123",
                getBroadcastAddress(ipv4Address("192.168.0.123"), 32).getHostAddress());
        assertEquals("255.255.255.255",
                getBroadcastAddress(ipv4Address("192.168.0.123"), 0).getHostAddress());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetBroadcastAddress_PrefixTooLarge() {
        getBroadcastAddress(ipv4Address("192.168.0.123"), 33);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetBroadcastAddress_NegativePrefix() {
        getBroadcastAddress(ipv4Address("192.168.0.123"), -1);
    }
}
