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

import android.net.TetheringRequestParcel;

import com.android.internal.annotations.VisibleForTesting;

import java.io.FileDescriptor;
import java.net.Inet6Address;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Native methods for tethering utilization.
 *
 * {@hide}
 */
public class TetheringUtils {
    public static final byte[] ALL_NODES = new byte[] {
        (byte) 0xff, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
    };

    @VisibleForTesting
    public static class TetheringUtilsNative {
        public void setupNaSocket(FileDescriptor fd) throws SocketException {
            native_setupNaSocket(fd);
        }

        public void setupNsSocket(FileDescriptor fd) throws SocketException {
            native_setupNsSocket(fd);
        }

        // TODO: Should setupRaSocket() be moved as well?
    }

    /**
     * Configures a socket for receiving and sending ICMPv6 neighbor advertisments.
     * @param fd the socket's {@link FileDescriptor}.
     */
    private static native void native_setupNaSocket(FileDescriptor fd)
            throws SocketException;

    /**
     * Configures a socket for receiving and sending ICMPv6 neighbor solicitations.
     * @param fd the socket's {@link FileDescriptor}.
     */
    private static native void native_setupNsSocket(FileDescriptor fd)
            throws SocketException;

    /**
     * Configures a socket for receiving ICMPv6 router solicitations and sending advertisements.
     * @param fd the socket's {@link FileDescriptor}.
     * @param ifIndex the interface index.
     */
    public static native void setupRaSocket(FileDescriptor fd, int ifIndex)
            throws SocketException;

    /**
     * Read s as an unsigned 16-bit integer.
     */
    public static int uint16(short s) {
        return s & 0xffff;
    }

    /** Check whether two TetheringRequestParcels are the same. */
    public static boolean isTetheringRequestEquals(final TetheringRequestParcel request,
            final TetheringRequestParcel otherRequest) {
        if (request == otherRequest) return true;

        return request != null && otherRequest != null
                && request.tetheringType == otherRequest.tetheringType
                && Objects.equals(request.localIPv4Address, otherRequest.localIPv4Address)
                && Objects.equals(request.staticClientAddress, otherRequest.staticClientAddress)
                && request.exemptFromEntitlementCheck == otherRequest.exemptFromEntitlementCheck
                && request.showProvisioningUi == otherRequest.showProvisioningUi;
    }

    public static Inet6Address getAllNodesForScopeId(int scopeId) {
        try {
            return Inet6Address.getByAddress("ff02::1", ALL_NODES, scopeId);
        } catch (UnknownHostException uhe) {
            Log.wtf("TetheringUtils", "Failed to construct Inet6Address from "
                + Arrays.toString(ALL_NODES) + " and scopedId " + scopeId);
            return null;
        }
    }
}
