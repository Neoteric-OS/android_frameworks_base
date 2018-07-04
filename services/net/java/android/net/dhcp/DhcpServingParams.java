/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.net.NetworkUtils.inetAddressToInt;
import static android.net.NetworkUtils.intToInetAddress;
import static android.net.NetworkUtils.prefixLengthToNetmaskInt;
import static android.net.dhcp.DhcpPacket.INFINITE_LEASE;
import static android.net.util.NetworkConstants.IPV4_ADDR_BITS;
import static android.net.util.NetworkConstants.IPV4_MAX_MTU;
import static android.net.util.NetworkConstants.IPV4_MIN_MTU;

import static java.lang.Integer.toUnsignedLong;

import android.net.IpPrefix;

import java.net.Inet4Address;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** @hide */
public class DhcpServingParams {

    /** Server inet address */
    public final Inet4Address inet4Addr;
    public final IpPrefix onlinkPrefix;
    public final Set<Inet4Address> defaultRouters;
    public final Set<Inet4Address> dnsServers;
    public final Set<Inet4Address> excludedAddrs;
    // DHCP uses uint32. Use long for clearer code, and check range when building.
    public final long dhcpLeaseTimeSecs;
    public final int upstreamMtu;

    /**
     * Checked exception thrown when some parameters used to build {@link DhcpServingParams} are
     * missing or invalid.
     */
    public static class InvalidParameterException extends Exception {
        public InvalidParameterException(String message) {
            super(message);
        }
    }

    private DhcpServingParams(Inet4Address inet4Addr, IpPrefix onlinkPrefix,
            Set<Inet4Address> defaultRouters, Set<Inet4Address> dnsServers,
            Set<Inet4Address> excludedAddrs, long dhcpLeaseTimeSecs, int upstreamMtu) {
        this.inet4Addr = inet4Addr;
        this.onlinkPrefix = onlinkPrefix;
        this.defaultRouters = defaultRouters;
        this.dnsServers = dnsServers;
        this.excludedAddrs = excludedAddrs;
        this.dhcpLeaseTimeSecs = dhcpLeaseTimeSecs;
        this.upstreamMtu = upstreamMtu;
    }

    public Inet4Address getPrefixMaskAsAddress() {
        return (Inet4Address)intToInetAddress(prefixLengthToNetmaskInt(
                onlinkPrefix.getPrefixLength()));
    }

    public Inet4Address getBroadcastAddress() {
        final int intBroadcastAddr = inetAddressToInt((Inet4Address)onlinkPrefix.getAddress())
                | ~prefixLengthToNetmaskInt(onlinkPrefix.getPrefixLength());
        return (Inet4Address)intToInetAddress(intBroadcastAddr);
    }

    public static class Builder {
        private Inet4Address inet4Addr;
        private IpPrefix onlinkPrefix;
        private Set<Inet4Address> defaultRouters;
        private Set<Inet4Address> dnsServers;
        private Set<Inet4Address> excludedAddrs;
        private long dhcpLeaseTimeSecs;
        private int upstreamMtu;

        public Builder setInet4Addr(Inet4Address inet4Addr) {
            this.inet4Addr = inet4Addr;
            return this;
        }

        public Builder setOnlinkPrefix(IpPrefix onlinkPrefix) {
            this.onlinkPrefix = onlinkPrefix;
            return this;
        }

        public Builder setDefaultRouters(Set<Inet4Address> defaultRouters) {
            this.defaultRouters = defaultRouters;
            return this;
        }

        public Builder setDnsServers(Set<Inet4Address> dnsServers) {
            this.dnsServers = Collections.unmodifiableSet(dnsServers);
            return this;
        }

        public Builder setExcludedAddrs(Set<Inet4Address> excludedAddrs) {
            this.excludedAddrs = excludedAddrs;
            return this;
        }

        public Builder setDhcpLeaseTimeSecs(long dhcpLeaseTimeSecs) {
            this.dhcpLeaseTimeSecs = dhcpLeaseTimeSecs;
            return this;
        }

        public Builder setUpstreamMtu(int upstreamMtu) {
            this.upstreamMtu = upstreamMtu;
            return this;
        }

        public DhcpServingParams build() throws InvalidParameterException {
            if (inet4Addr == null) {
                throw new InvalidParameterException("Missing inet4Addr");
            }
            if (onlinkPrefix == null) {
                throw new InvalidParameterException("Missing onlinkPrefix");
            }
            if (defaultRouters == null) {
                throw new InvalidParameterException("Missing defaultRouters");
            }
            if (dnsServers == null) {
                // Empty set is OK, but enforce explicitly setting it
                throw new InvalidParameterException("Missing dnsServers");
            }
            Set<Inet4Address> excl = new HashSet<>();
            if (excludedAddrs != null) {
                excl.addAll(excludedAddrs);
            }
            excl.add(inet4Addr);
            excl.addAll(defaultRouters);
            excl.addAll(dnsServers);

            if (dhcpLeaseTimeSecs <= 0 || dhcpLeaseTimeSecs > toUnsignedLong(INFINITE_LEASE)) {
                throw new InvalidParameterException("Invalid lease time: " + dhcpLeaseTimeSecs);
            }
            if (upstreamMtu < IPV4_MIN_MTU || upstreamMtu > IPV4_MAX_MTU) {
                throw new InvalidParameterException("Invalid upstream MTU: " + upstreamMtu);
            }
            if (!onlinkPrefix.isIPv4()) {
                throw new InvalidParameterException("onlinkPrefix must be IPv4");
            }
            if (onlinkPrefix.getPrefixLength() < 2
                    || onlinkPrefix.getPrefixLength() > (IPV4_ADDR_BITS - 2)) {
                throw new InvalidParameterException("Prefix length is not in supported range");
            }
            for (Inet4Address addr : defaultRouters) {
                if (!onlinkPrefix.contains(addr)) {
                    throw new InvalidParameterException(String.format(
                            "Default router %s is not in onlinkPrefix %s", addr, onlinkPrefix));
                }
            }

            return new DhcpServingParams(inet4Addr, onlinkPrefix,
                    Collections.unmodifiableSet(new HashSet<>(defaultRouters)),
                    Collections.unmodifiableSet(new HashSet<>(dnsServers)),
                    Collections.unmodifiableSet(excl),
                    dhcpLeaseTimeSecs, upstreamMtu);
        }
    }

}
