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
import static android.net.dhcp.DhcpLease.EXPIRATION_NEVER;
import static android.net.util.NetworkConstants.IPV4_ADDR_BITS;

import static java.lang.Math.min;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.IpPrefix;
import android.net.MacAddress;
import android.net.util.SharedLog;
import android.os.SystemClock;
import android.util.ArrayMap;

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

/**
 * A repository managing IPv4 address assignments through DHCPv4.
 *
 * <p>This class is not thread-safe. All public methods should be called on a common thread or
 * use some synchronization mechanism.
 *
 * <p>Methods are optimized for a small number of allocated leases, assuming that most of the time
 * only 2~10 addresses will be allocated, which is the common case. Managing a large number of
 * addresses is supported but will be slower: some operations have complexity in O(num_leases).
 * @hide
 */
class DhcpLeaseRepository {
    public static final byte[] CLIENTID_UNSPEC = null;
    public static final Inet4Address INETADDR_UNSPEC = null;

    @NonNull
    private final SharedLog mLog;
    @NonNull
    private final Clock mClock;

    @NonNull
    private IpPrefix mPrefix;
    @NonNull
    private Set<Inet4Address> mReservedAddrs;
    private int mSubnetAddr;
    private int mSubnetMask;
    private int mNumAddresses;
    private long mLeaseTimeMs;

    public static class Clock {
        /**
         * @see SystemClock#elapsedRealtime()
         */
        public long elapsedRealtime() {
            return SystemClock.elapsedRealtime();
        }
    }

    /**
     * Next timestamp when committed or declined leases should be checked for expired ones. This
     * will always be lower than or equal to the time for the first lease to expire: it's OK not to
     * update this when removing entries, but it must always be updated when adding/updating.
     */
    private long mNextExpirationCheck = EXPIRATION_NEVER;

    static class DhcpLeaseException extends Exception {
        DhcpLeaseException(String message) {
            super(message);
        }
    }

    static class OutOfAddressesException extends DhcpLeaseException {
        OutOfAddressesException(String message) {
            super(message);
        }
    }

    static class InvalidAddressException extends DhcpLeaseException {
        InvalidAddressException(String message) {
            super(message);
        }
    }

    /**
     * Leases by IP address
     */
    private final ArrayMap<Inet4Address, DhcpLease> mCommittedLeases = new ArrayMap<>();

    /**
     * Map address -> expiration timestamp in ms. Addresses are guaranteed to be in the subnet, but
     * are not necessarily otherwise assignable */
    private final LinkedHashMap<Inet4Address, Long> mDeclinedAddrs = new LinkedHashMap<>();

    public DhcpLeaseRepository(@NonNull IpPrefix prefix, @NonNull Set<Inet4Address> reservedAddrs,
            long leaseTimeMs, @NonNull SharedLog log, @NonNull Clock clock) {
        updateParams(prefix, reservedAddrs, leaseTimeMs);
        mLog = log;
        mClock = clock;
    }

    public void updateParams(@NonNull IpPrefix prefix, @NonNull Set<Inet4Address> reservedAddrs,
            long leaseTimeMs) {
        mPrefix = prefix;
        mReservedAddrs = Collections.unmodifiableSet(new HashSet<>(reservedAddrs));
        mSubnetMask = prefixLengthToNetmaskInt(prefix.getPrefixLength());
        mSubnetAddr = inetAddressToInt((Inet4Address)prefix.getAddress()) & mSubnetMask;
        mNumAddresses = 1 << (IPV4_ADDR_BITS - prefix.getPrefixLength());
        mLeaseTimeMs = leaseTimeMs;

        cleanMap(mCommittedLeases, mPrefix, mReservedAddrs);
        cleanMap(mDeclinedAddrs, mPrefix, mReservedAddrs);
    }

    /**
     * From a map keyed by {@link Inet4Address}, remove entries where the key is outside the
     * specified prefix or inside a set of reserved addresses.
     */
    private static <T> void cleanMap(Map<Inet4Address, T> map, IpPrefix prefix,
            Set<Inet4Address> reserved) {
        final Iterator<Entry<Inet4Address, T>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            final Inet4Address addr = it.next().getKey();
            if (!prefix.contains(addr) || reserved.contains(addr)) {
                it.remove();
            }
        }
    }

    /**
     * Get a DHCP offer, to reply to a DHCPDISCOVER. Follows RFC2131 #4.3.1.
     * @param clientId Client identifier option if specified, or {@link #CLIENTID_UNSPEC}
     * @param relayAddr Internet address of the relay (giaddr), can be {@link Inet4Address#ANY}
     * @param reqAddr Requested address by the client (option 50), or {@link #INETADDR_UNSPEC}
     * @param hostname Client-provided hostname, or {@link DhcpLease#HOSTNAME_NONE}
     * @throws OutOfAddressesException The server does not have any available address
     * @throws InvalidAddressException The lease was requested from an unsupported subnet
     */
    @NonNull
    public DhcpLease getOffer(@Nullable byte[] clientId, @NonNull MacAddress hwAddr,
            @NonNull Inet4Address relayAddr,
            @Nullable Inet4Address reqAddr, @Nullable String hostname)
            throws OutOfAddressesException, InvalidAddressException {
        final long currentTime = mClock.elapsedRealtime();
        final long expTime = currentTime + mLeaseTimeMs;

        removeExpiredLeases(currentTime);

        // As per #4.3.1, addresses are assigned based on the relay address if present. This
        // implementation only assigns addresses if the relayAddr is inside our configured subnet.
        // This also applies when the client requested a specific address for consistency between
        // requests, and with older behavior.
        if (isIpAddrOutsidePrefix(mPrefix, relayAddr)) {
            throw new InvalidAddressException("Lease requested by relay from outside of subnet");
        }

        DhcpLease currentLease = findByClient(clientId, hwAddr);
        if (currentLease != null) {
            final DhcpLease extendedLease = currentLease.renew(expTime, hostname);
            mLog.log("Offering extended lease " + extendedLease);
            // Do not update lease time in the map: the offer is not committed yet.
            return extendedLease;
        }
        if (reqAddr != null && isValidAddress(reqAddr) && mPrefix.contains(reqAddr)
                && isAvailable(reqAddr)) {
            final DhcpLease lease = new DhcpLease(clientId, hwAddr, reqAddr, expTime, hostname);
            mLog.log("Offering requested lease " + lease);
            return lease;
        }

        final DhcpLease newLease = makeNewOffer(clientId, hwAddr, expTime, hostname);
        mLog.log("Offering new generated lease " + newLease);

        return newLease;
    }

    private static boolean isIpAddrOutsidePrefix(IpPrefix prefix, Inet4Address addr) {
        return addr != null && !addr.equals(Inet4Address.ANY) && !prefix.contains(addr);
    }

    @Nullable
    private DhcpLease findByClient(@Nullable byte[] clientId, @NonNull MacAddress hwAddr) {
        for (DhcpLease lease: mCommittedLeases.values()) {
            if (lease.matchesClient(clientId, hwAddr)) {
                return lease;
            }
        }

        // Note this differs from dnsmasq behavior, which would match by hwAddr if clientId was
        // given but no lease keyed on clientId matched. This would prevent one interface from
        // obtaining multiple leases with different clientId.
        return null;
    }

    /**
     * Request a lease (DHCPREQUEST). Follows RFC2131 #4.3.2.
     *
     * @param clientId Client identifier option if specified, or {@link #CLIENTID_UNSPEC}
     * @param reqAddr Requested address by the client (option 50), or {@link #INETADDR_UNSPEC}
     * @param sidSet Whether the server identifier was set in the request
     */
    @NonNull
    public DhcpLease requestLease(@Nullable byte[] clientId, @NonNull MacAddress hwAddr,
            @NonNull Inet4Address clientAddr, @Nullable Inet4Address reqAddr, boolean sidSet,
            @Nullable String hostname) throws InvalidAddressException {
        final long currentTime = mClock.elapsedRealtime();
        removeExpiredLeases(currentTime);
        final DhcpLease assignedLease = findByClient(clientId, hwAddr);

        final DhcpLease lease;
        if (reqAddr != null) {
            if (sidSet) {
                // Client in SELECTING state
                // Remove any current lease for the client
                if (assignedLease != null) {
                    mCommittedLeases.remove(assignedLease.getNetAddr());
                }
                lease = checkClientAndMakeLease(clientId, hwAddr, reqAddr, hostname, currentTime);
                mLog.log("DHCPREQUEST-SELECTING: created new lease " + lease);
            } else {
                // Client in INIT-REBOOT state, verifying previous cached configuration
                if (assignedLease != null && !assignedLease.getNetAddr().equals(reqAddr)) {
                    throw new InvalidAddressException(
                            "Incorrect address for client in INIT-REBOOT state");
                }

                // RFC2131 #4.3.2 says that we must not reply if assignedLease == null, but dnsmasq
                // will let the client use the requested address if available, when configured with
                // --dhcp-authoritative. This is preferable to avoid issues if we lost our lease DB:
                // client would not get a reply because we don't know their lease.
                lease = checkClientAndMakeLease(clientId, hwAddr, reqAddr, hostname, currentTime);
                mLog.log("DHCPREQUEST-INITREBOOT: created/renewd lease " + lease);
            }
        } else {
            // Client in RENEWING or REBINDING state
            if (assignedLease != null && !assignedLease.getNetAddr().equals(clientAddr)) {
                throw new InvalidAddressException(
                        "Incorrect address for client in RENEWING/REBINDING state");
            }

            // We might have lost our database: create lease when possible if unknown
            lease = checkClientAndMakeLease(clientId, hwAddr, clientAddr, hostname, currentTime);
            mLog.log("DHCPREQUEST-RENEWREBIND: created/renewed lease " + lease);
        }
        return lease;
    }

    /**
     * Check that the client can request the specified address and make the lease if yes.
     * @throws InvalidAddressException The client cannot request this lease
     */
    private DhcpLease checkClientAndMakeLease(byte[] clientId, MacAddress hwAddr, Inet4Address addr,
            String hostname, long currentTime) throws InvalidAddressException {
        final long expTime = currentTime + mLeaseTimeMs;
        final DhcpLease currentLease = mCommittedLeases.getOrDefault(addr, null);
        if (currentLease != null && !currentLease.matchesClient(clientId, hwAddr)) {
            throw new InvalidAddressException("Address in use");
        }

        final DhcpLease lease;
        if (currentLease == null) {
            if (mPrefix.contains(addr) && isValidAddress(addr) && !mReservedAddrs.contains(addr)) {
                lease = new DhcpLease(clientId, hwAddr, addr, expTime, hostname);
            } else {
                throw new InvalidAddressException("Lease not found and address unavailable");
            }
        } else {
            lease = currentLease.renew(expTime, hostname);
        }
        commitLease(lease);
        return lease;
    }

    private void commitLease(DhcpLease lease) {
        mCommittedLeases.put(lease.getNetAddr(), lease);
        if (lease.getExpTime() < mNextExpirationCheck) {
            mNextExpirationCheck = lease.getExpTime();
        }
    }

    public boolean releaseLease(@Nullable byte[] clientId, @NonNull MacAddress hwAddr,
            @NonNull Inet4Address addr) {
        final DhcpLease currentLease = mCommittedLeases.getOrDefault(addr, null);
        if (currentLease == null) {
            mLog.w("Could not release unknown lease for " + addr);
            return false;
        }
        if (currentLease.matchesClient(clientId, hwAddr)) {
            mCommittedLeases.remove(addr);
            mLog.log("Released lease " + currentLease);
            return true;
        }
        mLog.w(String.format("Not releasing lease %s: does not match client (cid %s, hwAddr %s)",
                currentLease, DhcpLease.clientIdToString(clientId), hwAddr));
        return false;
    }

    public void markLeaseUnavailable(@NonNull Inet4Address addr) {
        if (mDeclinedAddrs.containsKey(addr) || !mPrefix.contains(addr)) {
            mLog.logf("Not marking %s as unavailable: already declined or not in prefix", addr);
            return;
        }
        final long expTime = mClock.elapsedRealtime() + mLeaseTimeMs;
        mDeclinedAddrs.put(addr, expTime);
        mLog.logf("Marked %s as unavailable until %d", addr, expTime);
        if (expTime < mNextExpirationCheck) {
            mNextExpirationCheck = expTime;
        }
    }

    /**
     * Get the list of currently valid committed leases in the repository.
     */
    @NonNull
    public List<DhcpLease> getCommittedLeases() {
        removeExpiredLeases(mClock.elapsedRealtime());
        return new ArrayList<>(mCommittedLeases.values());
    }

    /**
     * Get the set of addresses that have been marked as declined in the repository.
     */
    @NonNull
    public Set<Inet4Address> getDeclinedAddresses() {
        removeExpiredLeases(mClock.elapsedRealtime());
        return new HashSet<>(mDeclinedAddrs.keySet());
    }

    private <T> long removeExpired(long currentTime, Map<Inet4Address, T> map, String tag,
            Function<T, Long> getExpTime) {
        final Iterator<Entry<Inet4Address, T>> it = map.entrySet().iterator();
        long firstExpiration = EXPIRATION_NEVER;
        while (it.hasNext()) {
            final Entry<Inet4Address, T> lease = it.next();
            final long expTime = getExpTime.apply(lease.getValue());
            if (expTime <= currentTime) {
                mLog.logf("Removing expired %s lease %s", tag, lease.getValue());
                it.remove();
            } else {
                firstExpiration = min(firstExpiration, expTime);
            }
        }
        return firstExpiration;
    }

    /**
     * Go through committed and declined leases and remove the expired ones.
     */
    private void removeExpiredLeases(long currentTime) {
        if (currentTime < mNextExpirationCheck) {
            return;
        }

        final long commExp = removeExpired(
                currentTime, mCommittedLeases, "committed", DhcpLease::getExpTime);
        final long declExp = removeExpired(
                currentTime, mDeclinedAddrs, "declined", Function.identity());

        mNextExpirationCheck = min(commExp, declExp);
    }

    private boolean isAvailable(Inet4Address addr) {
        return !mReservedAddrs.contains(addr) && !mCommittedLeases.containsKey(addr);
    }

    /**
     * Get the 0-based index of an address in the subnet
     */
    private int getAddrIndex(int addr) {
        // Reverse bytes order since mSubnetMask does not use ordering-friendly byte ordering.
        return Integer.reverseBytes(addr & ~mSubnetMask);
    }

    private int getAddrByIndex(int index) {
        return mSubnetAddr | Integer.reverseBytes(index);
    }

    /**
     * Get a valid address starting from the supplied one.
     *
     * <p>This only checks that the address is numerically valid for assignment, not whether it is
     * already in use. The address is assumed to be inside the configured prefix.
     */
    private int getValidAddress(int addr) {
        final int lastByteMask = 0xff000000; // Byte ordering used by inetAddressToInt
        int addrIndex = getAddrIndex(addr); // 0-based index of the address in the subnet

        // Some OSes do not handle addresses in .255 or .0 correctly: avoid those.
        if ((getAddrByIndex(addrIndex) & lastByteMask) == lastByteMask) {
            addrIndex = (addrIndex + 1) % mNumAddresses;
        }
        if ((getAddrByIndex(addrIndex) & lastByteMask) == 0) {
            addrIndex = (addrIndex + 1) % mNumAddresses;
        }

        // Do not use first or last address of range
        if (addrIndex == 0 || addrIndex == mNumAddresses - 1) {
            // Always valid and not end of range since prefixLength is at most 30 in serving params
            addrIndex = 1;
        }
        return getAddrByIndex(addrIndex);
    }

    /**
     * Returns whether the address is part of the assignable range of the configured subnet. This
     * assumes that the address is inside the prefix.
     */
    private boolean isValidAddress(Inet4Address addr) {
        final int intAddr = inetAddressToInt(addr);
        return getValidAddress(intAddr) == intAddr;
    }

    private int getNextAddress(int addr) {
        final int addrIndex = getAddrIndex(addr);
        final int nextAddress = getAddrByIndex((addrIndex + 1) % mNumAddresses);
        return getValidAddress(nextAddress);
    }

    /**
     * Calculate a first candidate address for a client by hashing the hardware address.
     *
     * <p>This will be a valid address as checked by {@link #getValidAddress(int)}, but may be
     * in use.
     * @return An IPv4 address encoded as 32-bit int
     */
    private int getClientAddressEpoch(MacAddress hwAddr) {
        /* This follows dnsmasq behavior. Advantages are: clients will often get the same
         * offers for different DISCOVER even if the lease was not yet accepted or has expired,
         * and we will generally not need to loop through many allocated addresses until we find
         * a free one. */
        int hash = 0;
        for (byte b : hwAddr.toByteArray()) {
            hash += b + (b << 8) + (b << 16);
        }
        // When using dnsmasq with the default Android DHCP ranges numAddresses would be 253,
        // since we used 192.168.x.2 -> 192.168.x.254 as ranges. With ranges defined with prefix
        // length, nAddresses is 256 on a /24, so this implementation will not result in the same
        // IPs as previous dnsmasq usage.
        final int addrIndex = hash % mNumAddresses;
        return getValidAddress(getAddrByIndex(addrIndex));
    }

    private DhcpLease makeNewOffer(byte[] clientId, MacAddress hwAddr, long expTime,
            String hostname) throws OutOfAddressesException {
        int intAddr = getClientAddressEpoch(hwAddr);
        // Loop until we find a free address, or we have tried all addresses
        // There is slightly less than this many usable addresses, but some extra looping is OK
        for (int i = 0; i < mNumAddresses; i++) {
            final Inet4Address addr = (Inet4Address) intToInetAddress(intAddr);
            if (isAvailable(addr) && !mDeclinedAddrs.containsKey(addr)) {
                return new DhcpLease(clientId, hwAddr, addr, expTime, hostname);
            }
            intAddr = getNextAddress(intAddr);
        }

        // Try freeing DECLINEd addresses if out of addresses.
        final Iterator<Inet4Address> it = mDeclinedAddrs.keySet().iterator();
        while (it.hasNext()) {
            final Inet4Address addr = it.next();
            it.remove();
            if (isValidAddress(addr) && isAvailable(addr)) {
                return new DhcpLease(clientId, hwAddr, addr, expTime, hostname);
            }
        }

        throw new OutOfAddressesException("No address available for offer");
    }
}
