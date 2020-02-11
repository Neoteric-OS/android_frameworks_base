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

import static android.net.TetheringManager.TETHERING_BLUETOOTH;

import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.util.ArrayMap;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;

import java.net.Inet4Address;
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
    public static final int DEFAULT_PREFIX_LENGTH = 24;

    private static final int MAX_SUBNET_NUM = 256;
    private static final int BYTE_MASK = 0xff;
    // reserved for bluetooth tethering.
    private static final int BLUETOOTH_RESERVED = 50;

    public static final String BLUETOOTH_PREFIX = "192.168.50.0/24";

    private final ArrayMap<String, IpPrefix> mUpstreamPrefixMap;
    private final ArrayMap<Integer, IpPrefix> mDownstreamPrefixMap;
    // IANA has reserved the following three blocks of the IP address space for private intranets:
    // 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
    // Tethering use 192.168.0.0/16 that has 256 contiguous class C network numbers.
    private static final String DEFAULT_TETHERING_PREFIX = "192.168.0.0/16";
    private final IpPrefix mTetheringPrefix;
    private IpPrefix mLastConflictPrefix;

    public PrivateAddressCoordinator() {
        mUpstreamPrefixMap = new ArrayMap();
        mDownstreamPrefixMap = new ArrayMap();
        mTetheringPrefix = new IpPrefix("192.168.0.0/16");
    }

    /**
     * Record IpPrefix which may be possible to conflict with tethering downstream, kicking out
     * conflict downstreams and downstream types. If no conflict, empty ArrayList would be return.
     */
    public ArrayList<Integer> updateUpstreamPrefix(final LinkProperties lp) {
        final LinkAddress linkAddr = getAddressInPrefix(lp.getLinkAddresses());
        if (linkAddr == null) {
            removeUpstreamPrefix(lp.getInterfaceName());
            return new ArrayList<Integer>();
        }

        final IpPrefix newPrefix = new IpPrefix(linkAddr.getAddress(), linkAddr.getPrefixLength());
        mUpstreamPrefixMap.put(lp.getInterfaceName(), newPrefix);
        final ArrayList<Integer> conflicts = getConflictDownstreamTypes(newPrefix);
        if (conflicts.size() > 0) mLastConflictPrefix = newPrefix;
        return conflicts;
    }

    private LinkAddress getAddressInPrefix(final List<LinkAddress> linkAddresses) {
        for (LinkAddress address : linkAddresses) {
            // Typically a link only has one IPv4 address.
            final InetAddress ipv4Address = address.getAddress();
            if ((ipv4Address instanceof Inet4Address)) return address;
        }

        return null;
    }

    /** Remove IpPrefix records corresponding to input interface name. */
    public void removeUpstreamPrefix(final String interfaceName) {
        mUpstreamPrefixMap.remove(interfaceName);
    }

    /** Request downstream prefix. */
    public IpPrefix requestDownstreamPrefix(final int interfaceType) {
        final int index = mDownstreamPrefixMap.indexOfKey(interfaceType);
        if (index >= 0) {
            return mDownstreamPrefixMap.valueAt(index);
        }

        // TODO: support random prefix for bluetooth after b/148078390 is fixed.
        if (interfaceType == TETHERING_BLUETOOTH) {
            final IpPrefix prefix = new IpPrefix(BLUETOOTH_PREFIX);
            updateDownstreamPrefix(interfaceType, prefix);
            return prefix;
        }

        // Prefix would be 192.168.[subNet].0/24.
        final byte[] bytes = mTetheringPrefix.getRawAddress();
        int random = getRandomSubNetId();
        for (int i = 0; i < MAX_SUBNET_NUM; i++) {
            final int subNet = (random + i) & BYTE_MASK;
            if (subNet == BLUETOOTH_RESERVED) continue;

            bytes[2] = (byte) subNet;
            final InetAddress addr;
            try {
                addr = InetAddress.getByAddress(bytes);
            } catch (UnknownHostException e) {
                throw new IllegalStateException("Invalid address, shouldn't happen.", e);
            }

            final IpPrefix prefix = new IpPrefix(addr, DEFAULT_PREFIX_LENGTH);
            // Do not choose the prefix which would conflict last time.
            if (mLastConflictPrefix != null
                    && isConflictPrefix(prefix, mLastConflictPrefix)) continue;
            // Check whether this prefix is in use.
            if (isDownstreamPrefixInUse(prefix)) continue;
            // Check whether this prefix is conflict with any upstream network.
            if (!isConflictWithUpstream(prefix)) {
                updateDownstreamPrefix(interfaceType, prefix);
                return prefix;
            }
        }

        // No available prefix.
        return null;
    }

    /** Get random subnet value. Return value is in 0 ~ 255. */
    @VisibleForTesting
    public int getRandomSubNetId() {
        return ((new Random()).nextInt()) & BYTE_MASK; // subNet is in 0 ~ 255.
    }

    private void updateDownstreamPrefix(final int interfaceType, final IpPrefix prefix) {
        mDownstreamPrefixMap.put(interfaceType, prefix);
    }

    /** Remove downstream record for interfaceType. */
    public IpPrefix removeDownstreamPrefix(final int interfaceType) {
        return mDownstreamPrefixMap.remove(interfaceType);
    }

    /** Clear all prefix records. */
    public void clearAllPrefixes() {
        mUpstreamPrefixMap.clear();
        mDownstreamPrefixMap.clear();
    }

    private boolean isConflictWithUpstream(final IpPrefix source) {
        for (int i = 0; i < mUpstreamPrefixMap.size(); i++) {
            final IpPrefix target = mUpstreamPrefixMap.valueAt(i);
            if (isConflictPrefix(source, target)) return true;
        }
        return false;
    }

    private ArrayList<Integer> getConflictDownstreamTypes(final IpPrefix source) {
        final ArrayList<Integer> conflicts = new ArrayList<>();
        for (int i = 0; i < mDownstreamPrefixMap.size(); i++) {
            final IpPrefix target = mDownstreamPrefixMap.valueAt(i);
            if (isConflictPrefix(source, target)) {
                conflicts.add(mDownstreamPrefixMap.keyAt(i));
            }
        }
        return conflicts;
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
