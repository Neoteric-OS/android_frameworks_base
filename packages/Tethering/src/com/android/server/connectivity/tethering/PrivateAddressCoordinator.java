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
package com.android.server.connectivity.tethering;

import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.util.PrefixUtils;
import android.util.ArrayMap;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

    private static final int MAX_PREFIX_NUM = 256;
    private static final int BYTE_MASK = 0xff;
    // reserved for bluetooth tethering.
    private static final int BLUETOOTH_RESERVED = 50;
    private static final byte DEFAULT_ID = (byte) 42;

    public static final String BLUETOOTH_PREFIX = "192.168.44.0/24";

    private final ArrayMap<Network, List<IpPrefix>> mUpstreamPrefixMap;
    private final ArrayMap<String, IpPrefix> mDownstreamPrefixMap;
    // IANA has reserved the following three blocks of the IP address space for private intranets:
    // 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
    // Tethering use 192.168.0.0/16 that has 256 contiguous class C network numbers.
    private static final String DEFAULT_TETHERING_PREFIX = "192.168.0.0/16";
    private final IpPrefix mTetheringPrefix;

    public PrivateAddressCoordinator() {
        mUpstreamPrefixMap = new ArrayMap<>();
        mDownstreamPrefixMap = new ArrayMap<>();
        mTetheringPrefix = new IpPrefix(DEFAULT_TETHERING_PREFIX);
    }

    /**
     * Record a new upstream IpPrefix which may conflict with tethering downstreams. The downstream
     * interface List would be return if their prefixes is conflict with upstream. Otherwise, empty
     * List would be return.
     */
    public List<String> updateUpstreamPrefix(final Network network,
            final LinkProperties lp) {
        final List<IpPrefix> ipv4Prefixes = getIpv4Prefixes(lp.getAllLinkAddresses());
        if (ipv4Prefixes.isEmpty()) {
            removeUpstreamPrefix(network);
            return new ArrayList<>(0);
        }

        mUpstreamPrefixMap.put(network, ipv4Prefixes);
        return getConflictDownstreams(ipv4Prefixes);
    }

    private List<IpPrefix> getIpv4Prefixes(final List<LinkAddress> linkAddresses) {
        final ArrayList<IpPrefix> list = new ArrayList<>();
        for (LinkAddress address : linkAddresses) {
            if (address.isIpv6()) continue;

            list.add(PrefixUtils.asIpPrefix(address));
        }

        return list;
    }

    private ArrayList<String> getConflictDownstreams(final List<IpPrefix> prefixes) {
        final ArrayList<String> conflicts = new ArrayList<>();
        for (int i = 0; i < mDownstreamPrefixMap.size(); i++) {
            final IpPrefix target = mDownstreamPrefixMap.valueAt(i);
            for (IpPrefix source : prefixes) {
                if (isConflictPrefix(source, target)) {
                    conflicts.add(mDownstreamPrefixMap.keyAt(i));
                    break;
                }
            }
        }
        return conflicts;
    }

    /** Remove IpPrefix records corresponding to input interface name. */
    public void removeUpstreamPrefix(final Network network) {
        mUpstreamPrefixMap.remove(network);
    }

    /**
     * Pick a random available address and mark its prefix as in use for the provided interface,
     * returns null if there is no available address"
     */
    @Nullable
    public LinkAddress requestDownstreamAddress(final String iface) {
        // Address would be 192.168.[subAddress]/24.
        final byte[] bytes = mTetheringPrefix.getRawAddress();
        final int subAddress = getRandomSubAddr();
        final int subNet = (subAddress >> 8) & BYTE_MASK;
        bytes[3] = getSanitizedSubId(subAddress, (byte) 0, (byte) 1, (byte) 0xff);
        for (int i = 0; i < MAX_PREFIX_NUM; i++) {
            final int newSubNet = (subNet + i) & BYTE_MASK;
            if (newSubNet == BLUETOOTH_RESERVED) continue;

            bytes[2] = (byte) newSubNet;
            final InetAddress addr;
            try {
                addr = InetAddress.getByAddress(bytes);
            } catch (UnknownHostException e) {
                throw new IllegalStateException("Invalid address, shouldn't happen.", e);
            }

            final IpPrefix prefix = new IpPrefix(addr, PREFIX_LENGTH);
            // Check whether this prefix is in use.
            if (isDownstreamPrefixInUse(prefix)) continue;
            // Check whether this prefix is conflict with any upstream network.
            if (isConflictWithUpstream(prefix)) continue;

            updateDownstreamPrefix(iface, prefix);
            return new LinkAddress(addr, PREFIX_LENGTH);
        }

        // No available address.
        return null;
    }

    /** Get random sub address value. Return value is in 0 ~ 0xffff. */
    @VisibleForTesting
    public int getRandomSubAddr() {
        return ((new Random()).nextInt()) & 0xffff; // subNet is in 0 ~ 0xffff.
    }

    private byte getSanitizedSubId(final int source, byte... excluded) {
        final byte subId = (byte) (source & BYTE_MASK);
        for (byte value : excluded) {
            if (subId == value) return DEFAULT_ID;
        }

        return subId;
    }

    private void updateDownstreamPrefix(final String iface, final IpPrefix prefix) {
        mDownstreamPrefixMap.put(iface, prefix);
    }

    /** Remove downstream record for interface. */
    public void removeDownstreamPrefix(final String iface) {
        mDownstreamPrefixMap.remove(iface);

        // Clear upstream prefix records when no downstream records. Clear upstream prefix records
        // here instead of Tethering class because Tethering state machine woululd leave alive
        // staste during restarting tethering and corresponding downstream records would not be
        // removed in that case.
        if (mDownstreamPrefixMap.isEmpty()) clearUpstreamPrefixes();
    }

    /** Clear upstream prefixes records. */
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

    private boolean isDownstreamPrefixInUse(final IpPrefix source) {
        // This class always generates downstream prefixes with the same prefix length, so
        // prefixes cannot be contained in each other. They can only be equal to each other.
        return mDownstreamPrefixMap.containsValue(source);
    }

    void dump(final IndentingPrintWriter pw) {
        pw.println("mUpstreamPrefixMap:");
        pw.increaseIndent();
        for (int i = 0; i < mUpstreamPrefixMap.size(); i++) {
            pw.println(mUpstreamPrefixMap.keyAt(i) + " - "
                    + mUpstreamPrefixMap.valueAt(i));
        }
        pw.println("mDownstreamPrefixMap:");
        for (int i = 0; i < mDownstreamPrefixMap.size(); i++) {
            pw.println(mDownstreamPrefixMap.keyAt(i) + " - " + mDownstreamPrefixMap.valueAt(i));
        }
        pw.decreaseIndent();
    }
}
