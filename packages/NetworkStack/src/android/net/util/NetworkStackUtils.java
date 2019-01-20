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

import android.annotation.UnsupportedAppUsage;
import android.net.shared.Inet4AddressUtils;

import java.net.Inet4Address;

/**
 * Collection of utility methods for the NetworkStack.
 */
public class NetworkStackUtils {

    private NetworkStackUtils() {}

    /**
     * Returns the implicit netmask of an IPv4 address, as was the custom before 1993.
     */
    @UnsupportedAppUsage
    public static int getImplicitNetmask(Inet4Address address) {
        int firstByte = address.getAddress()[0] & 0xff;  // Convert to an unsigned value.
        if (firstByte < 128) {
            return 8;
        } else if (firstByte < 192) {
            return 16;
        } else if (firstByte < 224) {
            return 24;
        } else {
            return 32;  // Will likely not end well for other reasons.
        }
    }

    /**
     * Get the broadcast address for a given prefix.
     *
     * <p>For example 192.168.0.1/24 -> 192.168.0.255
     */
    public static Inet4Address getBroadcastAddress(Inet4Address addr, int prefixLength)
            throws IllegalArgumentException {
        final int intBroadcastAddr = Inet4AddressUtils.inet4AddressToIntHTH(addr)
                | ~Inet4AddressUtils.prefixLengthToV4NetmaskIntHTH(prefixLength);
        return Inet4AddressUtils.intToInet4AddressHTH(intBroadcastAddr);
    }
}
