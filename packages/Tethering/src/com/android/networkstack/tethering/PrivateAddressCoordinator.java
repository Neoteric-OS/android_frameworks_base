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

import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.TetheringManager.TETHERING_BLUETOOTH;
import static android.net.TetheringManager.TETHERING_WIFI_P2P;
import static android.net.util.PrefixUtils.asIpPrefix;

import static com.android.net.module.util.Inet4AddressUtils.inet4AddressToIntHTH;
import static com.android.net.module.util.Inet4AddressUtils.intToInet4AddressHTH;
import static com.android.net.module.util.Inet4AddressUtils.prefixLengthToV4NetmaskIntHTH;

import static java.util.Arrays.asList;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.Network;
import android.net.ip.IpServer;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * This class coordinate IP addresses conflict problem.
 *
 * Tethering downstream IP addresses may conflict with network assigned addresses. This
 * coordinator is responsible for recording all of network assigned addresses and dispatched
 * free address to downstream interfaces.
 *
 * This class is not thread-safe and should be accessed on the same tethering internal thread.
 * @hide
 */
public class PrivateAddressCoordinator {
    public static final int PREFIX_LENGTH = 24;

    // Upstream monitor would be stopped when tethering is down. When tethering restart, downstream
    // address may be requested before coordinator get current upstream notification. To ensure
    // coordinator do not select conflict downstream prefix, mUpstreamPrefixMap would not be cleared
    // when tethering is down. Instead tethering would remove all deprecated upstreams from
    // mUpstreamPrefixMap when tethering is starting. See #maybeRemoveDeprecatedUpstreams().
    private final ArrayMap<Network, List<IpPrefix>> mUpstreamPrefixMap;
    private final ArraySet<IpServer> mDownstreams;
    private static final String LEGACY_WIFI_P2P_IFACE_ADDRESS = "192.168.49.1/24";
    private static final String LEGACY_BLUETOOTH_IFACE_ADDRESS = "192.168.44.1/24";
    private final ArrayList<IpPrefix> mTetheringPrefixes;
    private final ConnectivityManager mConnectivityMgr;
    private final TetheringConfiguration mConfig;
    // keyed by downstream type(TetheringManager.TETHERING_*).
    private final SparseArray<LinkAddress> mCachedAddresses;

    public PrivateAddressCoordinator(Context context, TetheringConfiguration config) {
        mDownstreams = new ArraySet<>();
        mUpstreamPrefixMap = new ArrayMap<>();
        mConnectivityMgr = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        mConfig = config;
        mCachedAddresses = new SparseArray<>();
        // Reserved static addresses for bluetooth and wifi p2p.
        mCachedAddresses.put(TETHERING_BLUETOOTH, new LinkAddress(LEGACY_BLUETOOTH_IFACE_ADDRESS));
        mCachedAddresses.put(TETHERING_WIFI_P2P, new LinkAddress(LEGACY_WIFI_P2P_IFACE_ADDRESS));

        mTetheringPrefixes = new ArrayList<>(Arrays.asList(
                new IpPrefix("192.168.0.0/16"),
                new IpPrefix("172.16.0.0/12"),
                new IpPrefix("10.0.0.0/8")));
    }

    /**
     * Record a new upstream IpPrefix which may conflict with tethering downstreams.
     * The downstreams will be notified if a conflict is found. When updateUpstreamPrefix is called,
     * UpstreamNetworkState must have an already populated LinkProperties.
     */
    public void updateUpstreamPrefix(final UpstreamNetworkState ns) {
        // Do not support VPN as upstream. Normally, networkCapabilities is not expected to be null,
        // but just checking to be sure.
        if (ns.networkCapabilities != null && ns.networkCapabilities.hasTransport(TRANSPORT_VPN)) {
            removeUpstreamPrefix(ns.network);
            return;
        }

        final ArrayList<IpPrefix> ipv4Prefixes = getIpv4Prefixes(
                ns.linkProperties.getAllLinkAddresses());
        if (ipv4Prefixes.isEmpty()) {
            removeUpstreamPrefix(ns.network);
            return;
        }

        mUpstreamPrefixMap.put(ns.network, ipv4Prefixes);
        handleMaybePrefixConflict(ipv4Prefixes);
    }

    private ArrayList<IpPrefix> getIpv4Prefixes(final List<LinkAddress> linkAddresses) {
        final ArrayList<IpPrefix> list = new ArrayList<>();
        for (LinkAddress address : linkAddresses) {
            if (!address.isIpv4()) continue;

            list.add(asIpPrefix(address));
        }

        return list;
    }

    private void handleMaybePrefixConflict(final List<IpPrefix> prefixes) {
        for (IpServer downstream : mDownstreams) {
            final IpPrefix target = getDownstreamPrefix(downstream);
            if (target == null) continue;

            for (IpPrefix source : prefixes) {
                if (isConflictPrefix(source, target)) {
                    downstream.sendMessage(IpServer.CMD_NOTIFY_PREFIX_CONFLICT);
                    break;
                }
            }
        }
    }

    /** Remove IpPrefix records corresponding to input network. */
    public void removeUpstreamPrefix(final Network network) {
        mUpstreamPrefixMap.remove(network);
    }

    /**
     * Maybe remove deprecated upstream records, this would be called once tethering started without
     * any exiting tethered downstream.
     */
    public void maybeRemoveDeprecatedUpstreams() {
        if (mUpstreamPrefixMap.isEmpty()) return;

        // Remove all upstreams that are no longer valid networks
        final Set<Network> toBeRemoved = new HashSet<>(mUpstreamPrefixMap.keySet());
        toBeRemoved.removeAll(asList(mConnectivityMgr.getAllNetworks()));

        mUpstreamPrefixMap.removeAll(toBeRemoved);
    }

    /**
     * Pick a random available address and mark its prefix as in use for the provided IpServer,
     * returns null if there is no available address.
     */
    @Nullable
    public LinkAddress requestDownstreamAddress(final IpServer ipServer, boolean useLastAddress) {
        if (mConfig.shouldEnableWifiP2pDedicatedIp()
                && ipServer.interfaceType() == TETHERING_WIFI_P2P) {
            return new LinkAddress(LEGACY_WIFI_P2P_IFACE_ADDRESS);
        }

        final LinkAddress cachedAddress = mCachedAddresses.get(ipServer.interfaceType());
        if (useLastAddress && cachedAddress != null
                && !isConflictWithUpstream(asIpPrefix(cachedAddress))) {
            return cachedAddress;
        }

        for (IpPrefix prefixRange : mTetheringPrefixes) {
            final LinkAddress newAddress = chooseDownstreamAddress(prefixRange);
            if (newAddress != null) {
                mDownstreams.add(ipServer);
                mCachedAddresses.put(ipServer.interfaceType(), newAddress);
                return newAddress;
            }
        }

        // No available address.
        return null;
    }

    private int getPrefixAddress(final IpPrefix prefix) {
        return inet4AddressToIntHTH((Inet4Address) prefix.getAddress());
    }

    /**
     * Check whether input prefix conflict with upstream prefixes or inused downstream prefixes.
     * If yes, return any of them.
     */
    private IpPrefix getConflictPrefix(final IpPrefix prefix) {
        for (int i = 0; i < mUpstreamPrefixMap.size(); i++) {
            final List<IpPrefix> list = mUpstreamPrefixMap.valueAt(i);
            for (IpPrefix upstream : list) {
                if (isConflictPrefix(prefix, upstream)) return upstream;
            }
        }

        for (int i = 0; i < mCachedAddresses.size(); i++) {
            final IpPrefix downstream = asIpPrefix(mCachedAddresses.valueAt(i));
            if (isConflictPrefix(prefix, downstream)) return downstream;
        }

        // IpServer may use manually-defined address (mStaticIpv4ServerAddr) which does not include
        // in mCachedAddresses.
        for (IpServer downstream : mDownstreams) {
            final IpPrefix target = getDownstreamPrefix(downstream);
            if (target == null) continue;

            if (isConflictPrefix(prefix, target)) return target;
        }

        return null;
    }

    // Get the next non-conflict subAddress. E.g: To get next subAddress from 10.0.0.0/8, the
    // previous selecting address is 10.20.42.24/24(subAddress: 0.20.42.24) and conflictPrefix is
    // 10.16.0.0/20 (10.16.0.0 ~ 10.16.15.255). Its max conflict subAddress is 0.16.15.255, the
    // next subAddress is 0.16.16.24 ((0.16.15.255 + 0.0.1.0) & 255.255.255.0 + 0.0.0.24).
    private int getNextSubAddress(final int subAddress, final IpPrefix conflictPrefix,
            final int prefixRangeMask) {
        final int suffixMask = ~prefixLengthToV4NetmaskIntHTH(conflictPrefix.getPrefixLength());
        final int maxConflict = (getPrefixAddress(conflictPrefix) | suffixMask) & ~prefixRangeMask;

        final int subPrefixMask = prefixLengthToV4NetmaskIntHTH(PREFIX_LENGTH);
        final int nextSubPrefix = (maxConflict + (1 << (32 - PREFIX_LENGTH))) & subPrefixMask;
        return nextSubPrefix + (subAddress & ~subPrefixMask);
    }

    private LinkAddress chooseDownstreamAddress(final IpPrefix prefixRange) {
        final int prefixRangeMask = prefixLengthToV4NetmaskIntHTH(prefixRange.getPrefixLength());
        final int rootAddress = getPrefixAddress(prefixRange) & prefixRangeMask;
        final int subAddress = getSanitizedSubAddr(~prefixRangeMask);

        LinkAddress downstreamAddress = findAvailableAddressFromRange(
                subAddress, (~prefixRangeMask) + 1, rootAddress, prefixRangeMask);
        if (downstreamAddress != null) return downstreamAddress;

        final int suffix = ~prefixLengthToV4NetmaskIntHTH(PREFIX_LENGTH);
        downstreamAddress = findAvailableAddressFromRange(
                (subAddress & suffix), subAddress, rootAddress, prefixRangeMask);

        return downstreamAddress;
    }

    private LinkAddress findAvailableAddressFromRange(final int start, final int end,
            final int rootAddress, final int prefixRangeMask) {
        int newSubAddress = start;
        while (newSubAddress < end) {
            final InetAddress address = intToInet4AddressHTH(rootAddress | newSubAddress);
            final IpPrefix prefix = new IpPrefix(address, PREFIX_LENGTH);

            final IpPrefix conflictPrefix = getConflictPrefix(prefix);

            if (conflictPrefix == null) return new LinkAddress(address, PREFIX_LENGTH);

            newSubAddress = getNextSubAddress(newSubAddress, conflictPrefix, prefixRangeMask);
        }

        return null;
    }

    /** Get random int which could be used to generate random address. */
    @VisibleForTesting
    public int getRandomInt() {
        return (new Random()).nextInt();
    }

    /** Get random subAddress and avoid selecting x.x.x.0, x.x.x.1 and x.x.x.255 address. */
    private int getSanitizedSubAddr(final int subAddrMask) {
        final int randomSubAddr = getRandomInt() & subAddrMask;
        // If prefix length > 30, the selecting speace would be less than 4 which may be hard to
        // avoid 3 consecutive address.
        if (PREFIX_LENGTH > 30) return randomSubAddr;

        // TODO: maybe it is not necessary to avoid .0, .1 and .255 address because tethering
        // address would not be conflicted.
        final int candidate = randomSubAddr & 0xff;
        if (candidate == 0 || candidate == 1 || candidate == 255) {
            return (randomSubAddr & 0xfffffffc) + 2;
        }

        return randomSubAddr;
    }

    /** Release downstream record for IpServer. */
    public void releaseDownstream(final IpServer ipServer) {
        mDownstreams.remove(ipServer);
    }

    /** Clear current upstream prefixes records. */
    public void clearUpstreamPrefixes() {
        mUpstreamPrefixMap.clear();
    }

    private boolean isConflictWithUpstream(final IpPrefix source) {
        for (int i = 0; i < mUpstreamPrefixMap.size(); i++) {
            final List<IpPrefix> list = mUpstreamPrefixMap.valueAt(i);
            for (IpPrefix target : list) {
                if (isConflictPrefix(source, target)) return true;
            }
        }
        return false;
    }

    private boolean isConflictPrefix(final IpPrefix prefix1, final IpPrefix prefix2) {
        if (prefix2.getPrefixLength() < prefix1.getPrefixLength()) {
            return prefix2.contains(prefix1.getAddress());
        }

        return prefix1.contains(prefix2.getAddress());
    }

    private IpPrefix getDownstreamPrefix(final IpServer downstream) {
        final LinkAddress address = downstream.getAddress();
        if (address == null) return null;

        return asIpPrefix(address);
    }

    void dump(final IndentingPrintWriter pw) {
        pw.println("mUpstreamPrefixMap:");
        pw.increaseIndent();
        for (int i = 0; i < mUpstreamPrefixMap.size(); i++) {
            pw.println(mUpstreamPrefixMap.keyAt(i) + " - " + mUpstreamPrefixMap.valueAt(i));
        }
        pw.decreaseIndent();

        pw.println("mDownstreams:");
        pw.increaseIndent();
        for (IpServer ipServer : mDownstreams) {
            pw.println(ipServer.interfaceType() + " - " + ipServer.getAddress());
        }
        pw.decreaseIndent();

        pw.println("mCachedAddresses:");
        pw.increaseIndent();
        for (int i = 0; i < mCachedAddresses.size(); i++) {
            pw.println(mCachedAddresses.keyAt(i) + " - " + mCachedAddresses.valueAt(i));
        }
        pw.decreaseIndent();
    }
}
