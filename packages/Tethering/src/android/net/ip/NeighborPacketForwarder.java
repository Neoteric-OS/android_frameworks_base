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

import static android.system.OsConstants.AF_INET6;
import static android.system.OsConstants.IPPROTO_ICMPV6;
import static android.system.OsConstants.SOCK_RAW;
import static android.system.OsConstants.SOL_SOCKET;
import static android.system.OsConstants.SO_BINDTODEVICE;
import static android.system.OsConstants.SO_SNDTIMEO;

import android.system.ErrnoException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import java.io.FileDescriptor;
import java.net.Inet6Address;
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
    private static final String TAG = NeighborPacketForwarder.class.getSimpleName();
    private static final boolean DBG = false;
    private static final boolean VDBG = false;

    private volatile FileDescriptor mFd;

    private static final byte[] ALL_NODES = new byte[] {
        (byte) 0xff, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
    };

    private InterfaceParams mTetheredInterface;
    private InetSocketAddress mAllNodes;
    private String mUpstreamIface;
    private String mListenIface;
    private String mSendface;

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
        mUpstreamIface = null;
        mTetheredInterface = tetheredInterface;
        mType = type;

        if(mType == PacketType.ADVERT) {
            mSendface = tetheredInterface.name;
            mAllNodes = new InetSocketAddress(getAllNodesForScopeId(mTetheredInterface.index), 0);
        }
        else {
            mAllNodes = null;
            mListenIface = tetheredInterface.name;
            start();
        }
    }

    public void setUpstreamIface(String upstreamIface) {
        Log.e(TAG, "set upstream start new " + upstreamIface + ", old: " + mUpstreamIface);
        try {
            if(upstreamIface == null) {
                if(mType == PacketType.ADVERT) {
                    stop();
                }
                else {
                    mAllNodes = null;
                }
                }
            else if (mUpstreamIface == null || !mUpstreamIface.equals(upstreamIface)) {
                if(mType == PacketType.ADVERT) {
                    // start() will reset the mFd in super class with new upstream iface.
                    mListenIface = upstreamIface;
                    start();
                }
                else {
                    mSendface = upstreamIface;
                    mAllNodes = new InetSocketAddress(getAllNodesForScopeId(
                                                    Os.if_nametoindex(upstreamIface)), 0);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "set upstream exception: " + e);
        }
        mUpstreamIface = upstreamIface;
    }

    // TODO: move this to a util location. Present here NS and RA
    private static Inet6Address getAllNodesForScopeId(int scopeId) {
        try {
            return Inet6Address.getByAddress("ff02::1", ALL_NODES, scopeId);
        } catch (UnknownHostException uhe) {
            Log.wtf(TAG, "Failed to construct ff02::1 InetAddress: " + uhe);
            return null;
        }
    }

    @Override
    protected FileDescriptor createFd() {
        final int SEND_TIMEOUT_MS = 300;

        final int statsTag = TrafficStats.getAndSetThreadStatsTag(
                TrafficStatsConstants.TAG_SYSTEM_NEIGHBOR);

        try {
            mFd = Os.socket(AF_INET6, SOCK_RAW, IPPROTO_ICMPV6);
            Os.setsockoptTimeval(mFd, SOL_SOCKET, SO_SNDTIMEO,
                                 StructTimeval.fromMillis(SEND_TIMEOUT_MS));
            SocketUtils.bindSocketToInterface(mFd, mListenIface);

            // TODO: convert setup function to generic?
            if(mType == PacketType.ADVERT) {
            TetheringUtils.setupNaSocket(mFd, Os.if_nametoindex(mListenIface));
            } else if(mType == PacketType.SOLICIT) {
                TetheringUtils.setupNsSocket(mFd, Os.if_nametoindex(mListenIface));
            }
        } catch (ErrnoException|SocketException e) {
            Log.e(TAG, "Failed to create NA socket", e);
            return null;
        } finally {
            //TODO: check if this is necessary.
            TrafficStats.setThreadStatsTag(statsTag);
        }

        return mFd;
    }

    @Override
    protected void handlePacket(byte[] recvbuf, int length) {
        try {
            FileDescriptor fd = Os.socket(AF_INET6, SOCK_RAW, IPPROTO_ICMPV6);
            SocketUtils.bindSocketToInterface(fd, mSendface);
            //TODO: check if MAC needs to be changed to local MAC's iface.
            //TODO: add trafficStats
            Os.sendto(fd, recvbuf, 0, length, 0, mAllNodes);
        } catch (ErrnoException | SocketException e) { //UnknownHostException
            Log.e(TAG, "handlePacket error: " + e);
        }
    }
}
