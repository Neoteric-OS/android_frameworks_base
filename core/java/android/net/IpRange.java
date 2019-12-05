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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * This class represents an IP range, i.e., a contiguous block of IP addresses defined by a starting
 * and ending IP address. These addresses may not be power-of-two aligned.
 *
 * @hide
 */
public final class IpRange {
    private static final int SIGNUM_POSITIVE = 1;

    private final InetAddress mStartAddr;
    private final InetAddress mEndAddr;

    public IpRange(@NonNull InetAddress startAddr, @NonNull InetAddress endAddr) {
        Preconditions.checkNotNull(startAddr, "startAddr must not be null");
        Preconditions.checkNotNull(endAddr, "endAddr must not be null");
        Preconditions.checkArgument(
                startAddr.getClass().equals(endAddr.getClass()),
                "Invalid range: Address family mismatch");

        mStartAddr = startAddr;
        mEndAddr = endAddr;
    }

    public IpRange(@NonNull InetAddress startAddr, int prefixLen) {
        Preconditions.checkNotNull(startAddr, "startAddr must not be null");

        // Validate by building IpPrefix
        new IpPrefix(startAddr, prefixLen);

        mStartAddr = startAddr;

        // Build the max integer for the non-prefix bits
        BigInteger nonPrefixMax =
                BigInteger.ONE
                        .shiftLeft(startAddr.getAddress().length * 8 - prefixLen)
                        .subtract(BigInteger.ONE);

        // Set all non-prefix bits to max.
        try {
            mEndAddr =
                    bigIntegerToInetAddress(
                            addrToBigInteger(startAddr).or(nonPrefixMax),
                            startAddr instanceof Inet6Address);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Failed to generate ending address", e);
        }
    }

    @VisibleForTesting
    public InetAddress getStartAddr() {
        return mStartAddr;
    }

    @VisibleForTesting
    public InetAddress getEndAddr() {
        return mEndAddr;
    }

    /**
     * Converts this IP range to a list of IpPrefix instances.
     *
     * <p>This method outputs the IpPrefix instances for use in the routing architecture.
     */
    public List<IpPrefix> asIpPrefixes() throws UnknownHostException {
        boolean isIpv6 = mStartAddr instanceof Inet6Address;
        List<IpPrefix> result = new ArrayList<>();

        Queue<IpPrefix> workingSet = new LinkedList<>();

        // Start with the any-address.
        workingSet.add(new IpPrefix(isIpv6 ? Inet6Address.ANY : Inet4Address.ANY, 0));

        // While items are still in the queue, test and narrow to subsets.
        while (!workingSet.isEmpty()) {
            IpPrefix workingPrefix = workingSet.poll();
            IpRange workingRange =
                    new IpRange(workingPrefix.getAddress(), workingPrefix.getPrefixLength());

            // If the other range is contained within, it's part of the output. Do not test subsets,
            // or we will end up with duplicates.
            if (containsRange(workingRange)) {
                result.add(workingPrefix);
                continue;
            }

            // If there is any overlap, split the working range into it's two subsets, and
            // reevaluate those.
            if (isOverlappingRange(workingRange)
                    && ((isIpv6 && workingPrefix.getPrefixLength() < 128)
                            || (!isIpv6 && workingPrefix.getPrefixLength() < 32))) {
                workingSet.addAll(getSubsetPrefixes(workingPrefix));
            }
        }

        return result;
    }

    /** Returns the two prefixes that comprise the given prefix. */
    @VisibleForTesting
    public List<IpPrefix> getSubsetPrefixes(IpPrefix prefix) throws UnknownHostException {
        List<IpPrefix> result = new ArrayList<>();

        int newPrefixLen = prefix.getPrefixLength() + 1;
        result.add(new IpPrefix(prefix.getAddress(), newPrefixLen));

        // Build the other prefix, xoring the highest-order non-prefix bit.
        BigInteger value = new BigInteger(SIGNUM_POSITIVE, prefix.getRawAddress());
        int shiftBytes = prefix.getRawAddress().length * 8 - newPrefixLen;
        value = value.xor(BigInteger.ONE.shiftLeft(shiftBytes));
        result.add(
                new IpPrefix(
                        bigIntegerToInetAddress(value, prefix.getAddress() instanceof Inet6Address),
                        newPrefixLen));

        return result;
    }

    /** Converts the big integers back to an InetAddress */
    @VisibleForTesting
    public static InetAddress bigIntegerToInetAddress(BigInteger bigInt, boolean isIpv6)
            throws UnknownHostException {
        int addrLenBytes = isIpv6 ? 16 : 4;
        byte[] output = new byte[addrLenBytes];
        byte[] byteValueWithSign = bigInt.toByteArray();

        // Truncate the sign bit if it added a byte beyond the addrLenBytes. Safe otherwise,
        // since sign bit is always 0 (twos-complement positive).
        int byteValueStartIndex = byteValueWithSign.length > addrLenBytes ? 1 : 0;
        int byteValueActualLen = byteValueWithSign.length - byteValueStartIndex;

        System.arraycopy(
                byteValueWithSign,
                byteValueStartIndex,
                output,
                addrLenBytes - byteValueActualLen,
                byteValueActualLen);

        return InetAddress.getByAddress(output);
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
    public boolean isOverlappingRange(IpRange other) {
        return addrToBigInteger(mStartAddr).compareTo(addrToBigInteger(other.mEndAddr)) <= 0
                && addrToBigInteger(other.mStartAddr).compareTo(addrToBigInteger(mEndAddr)) <= 0;
    }

    /** Gets the InetAddress in BigInteger form */
    @VisibleForTesting
    public static BigInteger addrToBigInteger(InetAddress addr) {
        // Since addr.getAddress() returns network byte order (big-endian), it is compatibile with
        // the BigInteger constructor (which assumes big-endian).
        return new BigInteger(SIGNUM_POSITIVE, addr.getAddress());
    }
}
