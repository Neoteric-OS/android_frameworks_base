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

package android.net.ip;

import static android.net.util.SocketUtils.makePacketSocketAddress;
import static android.system.OsConstants.AF_INET6;
import static android.system.OsConstants.AF_PACKET;
import static android.system.OsConstants.ETH_P_IPV6;
import static android.system.OsConstants.IPV6_MULTICAST_LOOP;
import static android.system.OsConstants.IPPROTO_IPV6;
import static android.system.OsConstants.IPPROTO_RAW;
import static android.system.OsConstants.SOCK_DGRAM;
import static android.system.OsConstants.SOCK_NONBLOCK;
import static android.system.OsConstants.SOCK_RAW;
import static android.system.OsConstants.SOL_SOCKET;
import static android.system.OsConstants.SO_REUSEADDR;
import static android.system.OsConstants.SO_SNDTIMEO;

import android.system.ErrnoException;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import java.io.FileDescriptor;
import java.net.SocketAddress;
import android.net.IpPrefix;
import android.net.util.InterfaceParams;
import android.net.util.PacketReader;
import android.net.util.SocketUtils;
import android.net.util.TetheringUtils;
import android.net.TrafficStats;
import android.os.Handler;
import android.util.Log;
import android.system.StructTimeval;
import android.system.Os;

import com.android.internal.util.TrafficStatsConstants;


/**
 * Basic IPv6 Neighbor Advertisement Forwarder.
 *
 * Forward NA packets from upstream iface to tethered iface
 * and NS packets from tethered iface to upstream iface.
 *
 * @hide
 */
public class NeighborPacketForwarder extends PacketReader {
    private String TAG;
    private static final boolean DBG = false;
    private static final boolean VDBG = false;

    private final TetheringUtils.Native mNative;

    private FileDescriptor mFd;

    private static final int IPV6_ADDR_LEN = 16;
    private static final int IPV6_DST_ADDR_OFFSET = 24;
    private static final int IPV6_HEADER_LEN = 40;
    private static final int ETH_HEADER_LEN = 14;

    private InterfaceParams mListenIfaceParams, mSendIfaceParams;
    
    private int mType;
    final public static int ICMPV6_NEIGHBOR_ADVERTISEMENT  = 136;
    final public static int ICMPV6_NEIGHBOR_SOLICITATION = 135;

    public NeighborPacketForwarder(Handler h, InterfaceParams tetheredInterface, int type,
                                   TetheringUtils.Native aNative) {
        super(h);
        TAG = NeighborPacketForwarder.class.getSimpleName() + "-" +
                tetheredInterface.name + "-" + type;
        mType = type;
        mNative = aNative;

        if(mType == ICMPV6_NEIGHBOR_ADVERTISEMENT) {
            mSendIfaceParams = tetheredInterface;
        }
        else {
            mListenIfaceParams = tetheredInterface;
        }
    }

    public void setUpstreamIface(InterfaceParams upstreamParams) {
        final InterfaceParams oldUpstreamParams;

        if(mType == ICMPV6_NEIGHBOR_ADVERTISEMENT) {
            oldUpstreamParams = mListenIfaceParams;
            mListenIfaceParams = upstreamParams;
        } else {
            oldUpstreamParams = mSendIfaceParams;
            mSendIfaceParams = upstreamParams;
        }

        if (oldUpstreamParams == null && upstreamParams != null) {
            start();
        } else if (oldUpstreamParams != null && upstreamParams == null) {
            stop();
        } else if (oldUpstreamParams != null && upstreamParams != null &&
                !Objects.equals(oldUpstreamParams.name, upstreamParams.name)) {
            stop();
            start();
        }
    }

    // TODO: move NetworkStackUtils.closeSocketQuietly to frameworks/libs/net.
    private void closeSocketQuietly(FileDescriptor fd) {
        try {
            SocketUtils.closeSocket(fd);
        } catch (IOException ignored) {
        }
    }

    @Override
    protected FileDescriptor createFd() {
        try {
            // ICMPv6 packets from modem do not have eth header, so RAW socket cannot be used.
            // To keep uniformity in both directions PACKET socket can be used.
            mFd = Os.socket(AF_PACKET, SOCK_DGRAM | SOCK_NONBLOCK, ETH_P_IPV6);

            SocketAddress bindAddress = SocketUtils.makePacketSocketAddress(
                                                        ETH_P_IPV6, mListenIfaceParams.index);

            // TODO: convert setup*Socket to setupICMPv6BpfFilter with filter type?
            if(mType == ICMPV6_NEIGHBOR_ADVERTISEMENT) {
                mNative.setupNaSocket(mFd);
            } else if(mType == ICMPV6_NEIGHBOR_SOLICITATION) {
                mNative.setupNsSocket(mFd);
            }

            Os.bind(mFd, bindAddress);
        } catch (ErrnoException|SocketException e) {
            Log.wtf(TAG, "Failed to create  socket", e);
            closeSocketQuietly(mFd);
            return null;
        }

        return mFd;
    }

    private Inet6Address getIpv6DestinationAddress(byte[] recvbuf) {
        byte[] dstAddrBuf = new byte[IPV6_ADDR_LEN];
        System.arraycopy(recvbuf, IPV6_DST_ADDR_OFFSET, dstAddrBuf, 0, dstAddrBuf.length);

        Inet6Address dstAddr;
        try {
            dstAddr = (Inet6Address) Inet6Address.getByAddress(null, dstAddrBuf);
        } catch (UnknownHostException|ClassCastException impossible) {
            throw new AssertionError("16-byte array not valid IPv6 address?");
        }
        return dstAddr;
    }

    @Override
    protected void handlePacket(byte[] recvbuf, int length) {
        FileDescriptor fd = null;

        // The BPF filter should already have checked the length of the packet, but...
        if (length < IPV6_HEADER_LEN) {
            return;
        }
        Inet6Address destv6 = getIpv6DestinationAddress(recvbuf);
        if (!destv6.isMulticastAddress()) {
            return;
        }
        InetSocketAddress dest = new InetSocketAddress(destv6, 0);

        try {
            fd = Os.socket(AF_INET6, SOCK_RAW | SOCK_NONBLOCK, IPPROTO_RAW);
            SocketUtils.bindSocketToInterface(fd, mSendIfaceParams.name);

            int ret = Os.sendto(fd, recvbuf, 0, length, 0, dest);
            Log.e(TAG, "handle packet sent: " + ret);
        } catch (ErrnoException|SocketException e) {
            Log.e(TAG, "handlePacket error: " + e);
        } finally {
            closeSocketQuietly(fd);
        }
    }
}
