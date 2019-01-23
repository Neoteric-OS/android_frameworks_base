/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static android.system.OsConstants.SOL_SOCKET;
import static android.system.OsConstants.SO_BINDTODEVICE;

import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.net.NetworkUtils;
import android.system.ErrnoException;
import android.system.NetlinkSocketAddress;
import android.system.Os;
import android.system.PacketSocketAddress;

import libcore.io.IoBridge;
import libcore.io.IoUtils;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketException;

/**
 * Collection of utilities to interact with raw sockets.
 * @hide
 */
@SystemApi
@TestApi
public class SocketUtils {
    /**
     * Create a raw datagram socket that is bound to an interface.
     *
     * <p>Data sent through the socket will go directly to the underlying network, ignoring VPNs.
     */
    public static void bindSocketToInterface(FileDescriptor socket, String iface)
            throws ErrnoException {
        // SO_BINDTODEVICE actually takes a string. This works because the first member
        // of struct ifreq is a NULL-terminated interface name.
        // TODO: add a setsockoptString()
        Os.setsockoptIfreq(socket, SOL_SOCKET, SO_BINDTODEVICE, iface);
        NetworkUtils.protectFromVpn(socket);
    }

    /**
     * Make a socket address to communicate with netlink.
     */
    public static SocketAddress makeNetlinkSocketAddress(int portId, int groupsMask) {
        return new NetlinkSocketAddress(portId, groupsMask);
    }

    /**
     * Make socket address that packet sockets can bind to.
     */
    public static SocketAddress makePacketSocketAddress(short protocol, int ifIndex) {
        return new PacketSocketAddress(protocol, ifIndex);
    }

    /**
     * Make a socket address that packet socket can send packets to.
     */
    public static SocketAddress makePacketSocketAddress(int ifIndex, byte[] hwAddr) {
        return new PacketSocketAddress(ifIndex, hwAddr);
    }

    /**
     * Bind the socket to the specified address.
     */
    public static void bind(FileDescriptor fd, SocketAddress address)
            throws ErrnoException, SocketException {
        Os.bind(fd, address);
    }

    /**
     * Send bytes with the specified SocketAddress.
     */
    public static int sendTo(FileDescriptor fd, byte[] bytes, int byteOffset, int byteCount,
            int flags, SocketAddress address) throws ErrnoException, SocketException {
        return Os.sendto(fd, bytes, byteOffset, byteCount, flags, address);
    }

    /**
     * @see IoBridge#closeAndSignalBlockedThreads(FileDescriptor)
     */
    public static void closeAndSignalBlockedThreads(FileDescriptor fd) throws IOException {
        IoBridge.closeAndSignalBlockedThreads(fd);
    }

    /**
     * @see IoUtils#setBlocking(FileDescriptor, boolean)
     */
    public static void setBlocking(FileDescriptor fd, boolean blocking) throws IOException {
        IoUtils.setBlocking(fd, blocking);
    }

    private SocketUtils() {}
}
