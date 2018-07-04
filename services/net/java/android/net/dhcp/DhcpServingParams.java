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

import static android.net.NetworkUtils.getPrefixMaskAsInet4Address;
import static android.net.dhcp.DhcpPacket.INFINITE_LEASE;
import static android.net.util.NetworkConstants.IPV4_MAX_MTU;
import static android.net.util.NetworkConstants.IPV4_MIN_MTU;

import static java.lang.Integer.toUnsignedLong;

import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.NetworkUtils;

import java.net.Inet4Address;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** @hide */
public class DhcpServingParams {
    public static final int MTU_UNSET = 0;
    public static final int MIN_PREFIX_LENGTH = 16;
    public static final int MAX_PREFIX_LENGTH = 30;

    /** Server inet address */
    public final LinkAddress serverAddr;
    public final Set<Inet4Address> defaultRouters;
    public final Set<Inet4Address> dnsServers;
    public final Set<Inet4Address> excludedAddrs;
    // DHCP uses uint32. Use long for clearer code, and check range when building.
    public final long dhcpLeaseTimeSecs;
    public final int linkMtu;

    /**
     * Checked exception thrown when some parameters used to build {@link DhcpServingParams} are
     * missing or invalid.
     */
    public static class InvalidParameterException extends Exception {
        public InvalidParameterException(String message) {
            super(message);
        }
    }

    private DhcpServingParams(LinkAddress serverAddr, Set<Inet4Address> defaultRouters,
            Set<Inet4Address> dnsServers, Set<Inet4Address> excludedAddrs, long dhcpLeaseTimeSecs,
            int linkMtu) {
        this.serverAddr = serverAddr;
        this.defaultRouters = defaultRouters;
        this.dnsServers = dnsServers;
        this.excludedAddrs = excludedAddrs;
        this.dhcpLeaseTimeSecs = dhcpLeaseTimeSecs;
        this.linkMtu = linkMtu;
    }

    public Inet4Address getServerInet4Addr() {
        return (Inet4Address)serverAddr.getAddress();
    }

    public Inet4Address getPrefixMaskAsAddress() {
        return getPrefixMaskAsInet4Address(serverAddr.getPrefixLength());
    }

    public Inet4Address getBroadcastAddress() {
        return NetworkUtils.getBroadcastAddress(getServerInet4Addr(), serverAddr.getPrefixLength());
    }

    public static class Builder {
        private LinkAddress serverAddr;
        private Set<Inet4Address> defaultRouters;
        private Set<Inet4Address> dnsServers;
        private Set<Inet4Address> excludedAddrs;
        private long dhcpLeaseTimeSecs;
        private int linkMtu = MTU_UNSET;

        public Builder setServerAddr(LinkAddress serverAddr) {
            this.serverAddr = serverAddr;
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

        public Builder setLinkMtu(int linkMtu) {
            this.linkMtu = linkMtu;
            return this;
        }

        public DhcpServingParams build() throws InvalidParameterException {
            if (serverAddr == null) {
                throw new InvalidParameterException("Missing serverAddr");
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
            if (!serverAddr.isIPv4()) {
                throw new InvalidParameterException("serverAddr must be IPv4");
            }
            excl.add((Inet4Address)serverAddr.getAddress());
            excl.addAll(defaultRouters);
            excl.addAll(dnsServers);

            if (dhcpLeaseTimeSecs <= 0 || dhcpLeaseTimeSecs > toUnsignedLong(INFINITE_LEASE)) {
                throw new InvalidParameterException("Invalid lease time: " + dhcpLeaseTimeSecs);
            }
            if (linkMtu != MTU_UNSET && (linkMtu < IPV4_MIN_MTU || linkMtu > IPV4_MAX_MTU)) {
                throw new InvalidParameterException("Invalid link MTU: " + linkMtu);
            }

            if (serverAddr.getPrefixLength() < MIN_PREFIX_LENGTH
                    || serverAddr.getPrefixLength() > MAX_PREFIX_LENGTH) {
                throw new InvalidParameterException("Prefix length is not in supported range");
            }
            final IpPrefix prefix = makeIpPrefix(serverAddr);
            for (Inet4Address addr : defaultRouters) {
                if (!prefix.contains(addr)) {
                    throw new InvalidParameterException(String.format(
                            "Default router %s is not in server prefix %s", addr, serverAddr));
                }
            }

            return new DhcpServingParams(serverAddr,
                    Collections.unmodifiableSet(new HashSet<>(defaultRouters)),
                    Collections.unmodifiableSet(new HashSet<>(dnsServers)),
                    Collections.unmodifiableSet(excl),
                    dhcpLeaseTimeSecs, linkMtu);
        }
    }

    static IpPrefix makeIpPrefix(LinkAddress addr) {
        return new IpPrefix(addr.getAddress(), addr.getPrefixLength());
    }
}
