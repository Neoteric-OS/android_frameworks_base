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

package android.net;

import android.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

/**
 * This class represents an IP range, i.e., a contiguous block of IP addresses defined by a starting
 * and ending IP address. These addresses may not be power-of-two aligned.
 *
 * <p>Conversion to prefixes are deterministic, and will always return the same set of {@link
 * IpPrefix}(es). Ordering of IpPrefix instances is not guaranteed.
 *
 * @hide
 */
public final class IpRange {
    private static final int SIGNUM_POSITIVE = 1;

    private final byte[] mStartAddr;
    private final byte[] mEndAddr;

    public IpRange(@NonNull InetAddress startAddr, @NonNull InetAddress endAddr) {
        Preconditions.checkNotNull(startAddr, "startAddr must not be null");
        Preconditions.checkNotNull(endAddr, "endAddr must not be null");
        Preconditions.checkArgument(
                startAddr.getClass().equals(endAddr.getClass()),
                "Invalid range: Address family mismatch");
        Preconditions.checkArgument(
                addrToBigInteger(startAddr.getAddress())
                                .compareTo(addrToBigInteger(endAddr.getAddress()))
                        < 0,
                "Invalid range; start address must be before end address");

        mStartAddr = startAddr.getAddress();
        mEndAddr = endAddr.getAddress();
    }

    public IpRange(@NonNull InetAddress startAddr, int prefixLen) {
        Preconditions.checkNotNull(startAddr, "startAddr must not be null");

        // Validate by building IpPrefix. Throws IAE if prefix length is invalid for the
        // InetAddress family.
        final IpPrefix prefix = new IpPrefix(startAddr, prefixLen);

        // Use masked address from IpPrefix to zero out lower order bits.
        mStartAddr = prefix.getRawAddress();

        // Set all non-prefix bits to max.
        mEndAddr = prefix.getRawAddress();
        for (int bitIndex = prefixLen; bitIndex < 8 * mEndAddr.length; ++bitIndex) {
            mEndAddr[bitIndex / 8] |= (byte) (0x80 >> (bitIndex % 8));
        }
    }

    private InetAddress getAsInetAddress(byte[] address) {
        try {
            return InetAddress.getByAddress(address);
        } catch (UnknownHostException e) {
            // Cannot happen. InetAddress.getByAddress can only throw an exception if the byte
            // array is the wrong length, but we check that in the constructor.
            throw new IllegalArgumentException("Address is invalid");
        }
    }

    @VisibleForTesting
    public InetAddress getStartAddr() {
        return getAsInetAddress(mStartAddr);
    }

    @VisibleForTesting
    public InetAddress getEndAddr() {
        return getAsInetAddress(mEndAddr);
    }

    /**
     * Converts this IP range to a list of IpPrefix instances.
     *
     * <p>This method outputs the IpPrefix instances for use in the routing architecture.
     */
    public List<IpPrefix> asIpPrefixes() throws UnknownHostException {
        final boolean isIpv6 = (mStartAddr.length == 16);
        final List<IpPrefix> result = new ArrayList<>();
        final Queue<IpPrefix> workingSet = new LinkedList<>();

        // Start with the any-address.
        workingSet.add(new IpPrefix(isIpv6 ? Inet6Address.ANY : Inet4Address.ANY, 0));

        // While items are still in the queue, test and narrow to subsets.
        while (!workingSet.isEmpty()) {
            final IpPrefix workingPrefix = workingSet.poll();
            final IpRange workingRange =
                    new IpRange(workingPrefix.getAddress(), workingPrefix.getPrefixLength());

            // If the other range is contained within, it's part of the output. Do not test subsets,
            // or we will end up with duplicates.
            if (containsRange(workingRange)) {
                result.add(workingPrefix);
                continue;
            }

            // If there is any overlap, split the working range into it's two subsets, and
            // reevaluate those.
            if (overlapsRange(workingRange)) {
                workingSet.addAll(getSubsetPrefixes(workingPrefix));
            }
        }

        result.sort(IpPrefix.lengthComparator());
        return result;
    }

    /** Returns the two prefixes that comprise the given prefix. */
    @VisibleForTesting
    public List<IpPrefix> getSubsetPrefixes(IpPrefix prefix) throws UnknownHostException {
        final List<IpPrefix> result = new ArrayList<>();

        final int newPrefixLen = prefix.getPrefixLength() + 1;
        result.add(new IpPrefix(prefix.getRawAddress(), newPrefixLen));

        final byte[] other = prefix.getRawAddress();
        other[prefix.getPrefixLength() / 8] =
                (byte) (other[prefix.getPrefixLength() / 8]
                        ^ (0x80 >> (prefix.getPrefixLength() % 8)));
        result.add(new IpPrefix(other, newPrefixLen));

        return result;
    }

    /**
     * Checks if the other IP range is contained within this one
     *
     * <p>Checks based on byte values. For other to be contained within this IP range, other's
     * starting address must be greater or equal to the current IpRange's starting address, and the
     * other's ending address must be less than or equal to the current IP range's ending address.
     */
    @VisibleForTesting
    public boolean containsRange(IpRange other) {
        return addrToBigInteger(mStartAddr).compareTo(addrToBigInteger(other.mStartAddr)) <= 0
                && addrToBigInteger(mEndAddr).compareTo(addrToBigInteger(other.mEndAddr)) >= 0;
    }

    /**
     * Checks if the other IP range overlaps with this one
     *
     * <p>Checks based on byte values. For there to be overlap, this IpRange's starting address must
     * be less than the other's ending address, and vice versa.
     */
    @VisibleForTesting
    public boolean overlapsRange(IpRange other) {
        return addrToBigInteger(mStartAddr).compareTo(addrToBigInteger(other.mEndAddr)) <= 0
                && addrToBigInteger(other.mStartAddr).compareTo(addrToBigInteger(mEndAddr)) <= 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStartAddr, mEndAddr);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof IpRange)) {
            return false;
        }

        final IpRange other = (IpRange) obj;
        return Arrays.equals(mStartAddr, other.mStartAddr)
                && Arrays.equals(mEndAddr, other.mEndAddr);
    }

    /** Gets the InetAddress in BigInteger form */
    @VisibleForTesting
    public static BigInteger addrToBigInteger(byte[] addr) {
        // Since addr.getAddress() returns network byte order (big-endian), it is compatibile with
        // the BigInteger constructor (which assumes big-endian).
        return new BigInteger(SIGNUM_POSITIVE, addr);
    }
}
