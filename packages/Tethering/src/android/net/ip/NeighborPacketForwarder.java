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

import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;

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
 * Forward NA packet from upstream iface to tethered iface.
 *
 * @hide
 */
public class NeighborPacketForwarder extends PacketReader {
    private String TAG;
    private static final boolean DBG = false;
    private static final boolean VDBG = false;

    private final int SEND_TIMEOUT_MS = 300;

    private volatile FileDescriptor mFd;

    private static final int ETH_HEADER_LEN = 14;

    private InterfaceParams mTetheredInterface;
    private String mUpstreamIface;
    private String mListenIface;
    private String mSendIface;

    public enum PacketType {
        // NA packets are forwarded from upstream to tethered client.
        ADVERT,
        // NS pacets are forwarded from tethered client to upstream.
        SOLICIT
    }
    PacketType mType;

    public NeighborPacketForwarder(Handler h, InterfaceParams tetheredInterface,
                                   PacketType type) {
        super(h);
        TAG = NeighborPacketForwarder.class.getSimpleName() + "-" + type;
        mUpstreamIface = null;
        mTetheredInterface = tetheredInterface;
        mType = type;

        if(mType == PacketType.ADVERT) {
            mSendIface = tetheredInterface.name;
        }
        else {
            mListenIface = tetheredInterface.name;
            start();
        }
    }

    public void setUpstreamIface(String upstreamIface) {
        Log.e(TAG, "set upstream for " + mType + " start new " + upstreamIface + ", old: " + mUpstreamIface);
        try {
            if(upstreamIface == null && mType == PacketType.ADVERT) {
                    stop();
                }
            else if (mUpstreamIface == null || !mUpstreamIface.equals(upstreamIface)) {
                if(mType == PacketType.ADVERT) {
                    // start() will restart the fd in the super class on the new upstream iface.
                    mListenIface = upstreamIface;
                    start();
                }
                else {
                    mSendIface = upstreamIface;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "set upstream exception: " + e);
        }

        mUpstreamIface = upstreamIface;
    }

    @Override
    protected FileDescriptor createFd() {
        final int statsTag = TrafficStats.getAndSetThreadStatsTag(
                TrafficStatsConstants.TAG_SYSTEM_NEIGHBOR);

        try {
            mFd = Os.socket(AF_PACKET, SOCK_DGRAM | SOCK_NONBLOCK, ETH_P_IPV6);

            Os.setsockoptInt(mFd, SOL_SOCKET, SO_REUSEADDR, 1);
            SocketUtils.bindSocketToInterface(mFd, mListenIface);

            // TODO: convert setup*Socket to setupICMPv6BpfFilter with filter type?
            if(mType == PacketType.ADVERT) {
                TetheringUtils.setupNaSocket(mFd);
            } else if(mType == PacketType.SOLICIT) {
                TetheringUtils.setupNsSocket(mFd);
            }
        } catch (ErrnoException|SocketException e) {
            Log.e(TAG, "Failed to create  socket", e);
            return null;
        } finally {
            TrafficStats.setThreadStatsTag(statsTag);
        }

        return mFd;
    }

    @Override
    protected void handlePacket(byte[] recvbuf, int length) {
        try {
            FileDescriptor fd = Os.socket(AF_INET6, SOCK_RAW | SOCK_NONBLOCK, IPPROTO_RAW);
            Os.setsockoptInt(fd, SOL_SOCKET, SO_REUSEADDR, 1);
            SocketUtils.bindSocketToInterface(fd, mSendIface);

            InetSocketAddress dest = new InetSocketAddress(TetheringUtils.getAllNodesForScopeId(
                                                          Os.if_nametoindex(mSendIface)), 0);
            int ret = Os.sendto(fd, recvbuf, 0, length, 0, dest);
            Log.e(TAG, "handle packet sent: " + ret);
            SocketUtils.closeSocket(fd);
        } catch (Exception e) {
            Log.e(TAG, "handlePacket error: " + e);
        }
    }
}
