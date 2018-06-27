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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.MacAddress;
import android.os.SystemClock;
import android.text.TextUtils;

import com.android.internal.util.HexDump;

import java.net.Inet4Address;
import java.util.Arrays;
import java.util.Objects;

/**
 * An IPv4 address assignment done through DHCPv4.
 * @hide
 */
public class DhcpLease {
    public static final long EXPIRATION_NEVER = Long.MAX_VALUE;
    public static final String HOSTNAME_NONE = null;

    private final @Nullable byte[] mClientId;
    private final @NonNull MacAddress mHwAddr;
    private final Inet4Address mNetAddr;
    /**
     * Expiration time for the lease, to compare with {@link SystemClock#elapsedRealtime()}.
     */
    private final long mExpTime;
    private final @Nullable String mHostname;

    public DhcpLease(@Nullable byte[] clientId, @NonNull MacAddress hwAddr, Inet4Address netAddr,
            long expTime, @Nullable String hostname) {
        mClientId = clientId == null ? null : Arrays.copyOf(clientId, clientId.length);
        mHwAddr = hwAddr;
        mNetAddr = netAddr;
        mExpTime = expTime;
        mHostname = hostname;
    }

    public @Nullable byte[] getClientId() {
        return mClientId;
    }

    public MacAddress getHwAddr() {
        return mHwAddr;
    }

    public @Nullable String getHostname() {
        return mHostname;
    }

    public Inet4Address getNetAddr() {
        return mNetAddr;
    }

    public long getExpTime() {
        return mExpTime;
    }

    /**
     * Push back the expiration time of this lease. If the provided time is sooner than the original
     * expiration time, the original lease will be returned as-is.
     *
     * <p>The lease hostname is updated with the provided one if set.
     * @return A {@link DhcpLease} with expiration time set to max(expTime, currentExpTime)
     */
    public DhcpLease renew(long expTime, @Nullable String hostname) {
        if (expTime <= mExpTime) {
            return this;
        }
        return new DhcpLease(mClientId, mHwAddr, mNetAddr, expTime,
                hostname == null ? mHostname : hostname);
    }

    public boolean matchesClient(@Nullable byte[] clientId, @NonNull MacAddress hwAddr) {
        return (mClientId != null && Arrays.equals(mClientId, clientId))
                || (clientId == null && mClientId == null && mHwAddr.equals(hwAddr));
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DhcpLease)) {
            return false;
        }
        final DhcpLease lease = (DhcpLease)obj;
        return Arrays.equals(mClientId, lease.mClientId)
                && mHwAddr.equals(lease.mHwAddr)
                && mNetAddr.equals(lease.mNetAddr)
                && mExpTime == lease.mExpTime
                && TextUtils.equals(mHostname, lease.mHostname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mClientId, mHwAddr, mNetAddr, mHostname, mExpTime);
    }

    private static String bytesToString(byte[] bytes) {
        if (bytes == null) {
            return "null";
        }
        return HexDump.toHexString(bytes);
    }

    @Override
    public String toString() {
        return String.format("clientId: %s, hwAddr %s, netAddr: %s, expTime: %d, hostname: %s",
                bytesToString(mClientId), mHwAddr.toString(), mNetAddr, mExpTime, mHostname);
    }
}
