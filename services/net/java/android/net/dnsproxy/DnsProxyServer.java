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

package android.net.dnsproxy;

import static android.net.TrafficStats.TAG_SYSTEM_DNSPROXY_SERVER;
import static android.system.OsConstants.AF_INET6;
import static android.system.OsConstants.IPPROTO_TCP;
import static android.system.OsConstants.IPPROTO_UDP;
import static android.system.OsConstants.SOCK_DGRAM;
import static android.system.OsConstants.SOCK_STREAM;
import static android.system.OsConstants.SOL_SOCKET;
import static android.system.OsConstants.SO_BINDTODEVICE;
import static android.system.OsConstants.SO_BROADCAST;
import static android.system.OsConstants.SO_RCVTIMEO;
import static android.system.OsConstants.SO_REUSEADDR;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.DnsPacket;
import android.net.DnsResolver;
import android.net.Network;
import android.net.NetworkUtils;
import android.net.TrafficStats;
import android.net.util.FdEventsReader;
import android.net.util.SharedLog;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructTimeval;

import com.android.internal.annotations.VisibleForTesting;

import libcore.io.IoUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;

/**
 * A DnsProxy server.
 *
 * <p>This server listens for and responds to packets on a single interface.
 *
 * <p>The server is single-threaded (including send/receive operations): all internal operations are
 * done on the provided {@link Looper}. Public methods are thread-safe and will schedule operations
 * on the looper asynchronously.
 * @hide
 */
public class DnsProxyServer {
    private static final String TAG = "DnsProxyServer";
    private static final int CMD_START_DNSPROXY_SERVER = 1;
    private static final int CMD_STOP_DNSPROXY_SERVER = 2;
    private static final int DNS_SERVER = 53;

    @NonNull
    private final DnsResolver mDnsResolver;
    @NonNull
    private final ServerHandler mHandler;
    @NonNull
    private final String mIfName;
    @NonNull
    private final SharedLog mLog;
    @NonNull
    private final Dependencies mDeps;
    @NonNull
    private final TCPListener mTCPListener;
    @NonNull
    private final UDPListener mUDPListener;

    private Network mNetwork;

    @Nullable
    private FileDescriptor mTCPSocket;
    @Nullable
    private FileDescriptor mUDPSocket;

    /**
     * Dependencies for the DnsProxyServer. Useful to be mocked in tests.
     */
    public interface Dependencies {
        /**
         * Send a packet to the specified fd.
         *
         * @param fd File descriptor of the socket.
         * @param buffer Data to be sent.
         * @param dstAddr Destination address of the packet.
         * @param dstPort Destination port of the packet.
         */
        void sendPacket(@NonNull FileDescriptor fd, @NonNull byte[] buffer,
                @NonNull InetAddress dstAddr, int dstPort) throws ErrnoException, IOException;

        /**
         * Create a UDP packet listener that will send packets to be processed.
         */
        UDPListener makeUDPListener();

        /**
         * Create a TCP packet listener that will accept connections
         * and send packets to be processed.
         */
        TCPListener makeTCPListener();
    }

    private class DependenciesImpl implements Dependencies {
        @Override
        public void sendPacket(@NonNull FileDescriptor fd, @NonNull byte[] buffer,
                @NonNull InetAddress dstAddr, int dstPort) throws ErrnoException, IOException {
            Os.sendto(fd, buffer, 0, buffer.length, 0, dstAddr, dstPort);
        }

        @Override
        public UDPListener makeUDPListener() {
            return new UDPListener();
        }
        @Override
        public TCPListener makeTCPListener() {
            return new TCPListener();
        }
    }

    public DnsProxyServer(@NonNull Looper looper, @NonNull String ifName, @NonNull SharedLog log) {
        this(looper, ifName, log, null);
    }

    @VisibleForTesting
    DnsProxyServer(@NonNull Looper looper, @NonNull String ifName, @NonNull SharedLog log,
            @Nullable Dependencies deps) {
        if (deps == null) {
            deps = new DependenciesImpl();
        }
        mHandler = new ServerHandler(looper);
        mIfName = ifName;
        mLog = log;
        mDeps = deps;
        mUDPListener = deps.makeUDPListener();
        mTCPListener = deps.makeTCPListener();
        mDnsResolver = DnsResolver.getInstance();
        mNetwork = null;
    }

    /**
     * Start listening for DNS queries and do the querying on {@code Network}.
     */
    public void startWithNetwork(Network network) {
        sendMessage(CMD_START_DNSPROXY_SERVER, network);
    }

    /**
     * Stop listening for DNS queries.
     *
     * <p>As the server is stopped asynchronously, some packets may still be processed shortly after
     * calling this method.
     */
    public void stop() {
        mHandler.sendEmptyMessage(CMD_STOP_DNSPROXY_SERVER);
    }

    private void sendMessage(int what, @Nullable Object obj) {
        mHandler.sendMessage(mHandler.obtainMessage(what, obj));
    }

    private class ServerHandler extends Handler {
        ServerHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case CMD_START_DNSPROXY_SERVER:
                    mNetwork = (Network) msg.obj;
                    // This is a no-op if the listener is already started
                    mUDPListener.start();
                    mTCPListener.start();
                    break;
                case CMD_STOP_DNSPROXY_SERVER:
                    // This is a no-op if the listener was not started
                    mUDPListener.stop();
                    mTCPListener.stop();
                    break;
            }
        }
    }

    private class DnsProxyPacket extends DnsPacket {
        DnsProxyPacket(byte[] data) throws ParseException {
            super(data);
            if (mHeader.getSectionCount(QDSECTION) == 0) {
                throw new ParseException("No question found");
            }
        }

        public boolean isQuery() {
            return ((mHeader.getFlags() & (1 << 15)) == 0);
        }
    }

    private byte[] setupReply(byte[] buf, int protoType)
            throws DnsPacket.ParseException, IOException {
        final ByteArrayOutputStream ansPacket = new ByteArrayOutputStream();
        if (protoType == IPPROTO_TCP) {
            byte[] answerLen = new byte[] { (byte) ((buf.length >> 8) & 0xff),
                    (byte) (buf.length & 0xff) };
            ansPacket.write(answerLen);
        }
        byte[] ansbuf = Arrays.copyOf(buf, buf.length);
        // TODO: Refine DnsProxyPacket to create answer.
        if (new DnsProxyPacket(buf).isQuery()) {
            // Change QR
            ansbuf[2] =  (byte) (ansbuf[2] | 0x80);
            // Change rcode
            ansbuf[3] =  (byte) (ansbuf[3] | 0x02);
        }
        ansPacket.write(ansbuf);
        return ansPacket.toByteArray();
    }

    @VisibleForTesting
    void processPacket(@NonNull FileDescriptor fd, int protoType, @NonNull byte[] buf,
            int len, @NonNull InetAddress dstAddr, int dstPort) {
        byte[] querybuf = Arrays.copyOf(buf, len);
        try {
            // Check if the query is valid.
            DnsPacket queryPacket = new DnsProxyPacket(querybuf);
            mDnsResolver.query(mNetwork, querybuf,
                    DnsResolver.FLAG_NO_RETRY | DnsResolver.FLAG_NO_CACHE_LOOKUP,
                    mHandler, answer -> {
                    transmitAnswer(fd, protoType, (answer == null)
                            ? querybuf : answer, dstAddr, dstPort);
                }
            );
        } catch (ErrnoException e) {
            transmitAnswer(fd, protoType, querybuf, dstAddr, dstPort);
        } catch (DnsPacket.ParseException e) {
            mLog.e("Ignored malformed packet: " + e.getMessage());
            if (protoType == IPPROTO_TCP) {
                IoUtils.closeQuietly(fd);
            }
        }
    }

    private void transmitAnswer(@NonNull FileDescriptor fd, int protoType,
            @NonNull byte[] buf, @NonNull InetAddress dstAddr, int dstPort) {
        try {
            final byte[] ansPacket = setupReply(buf, protoType);
            mDeps.sendPacket(fd, ansPacket, dstAddr, dstPort);
        } catch (DnsPacket.ParseException | ErrnoException | IOException e) {
            mLog.e("Can't send packet " + e);
        } finally {
            if (protoType == IPPROTO_TCP) {
                IoUtils.closeQuietly(fd);
            }
        }
    }

    // TODO: Refine the implementation and remove unused variable.
    @VisibleForTesting
    class TCPListener extends FdEventsReader<byte[]/* Unused */> {
        private static final int ACCEPT_SUCCESS = 2;
        private static final int TCP_RCV_TIMEOUT = 60000;
        private static final int BACKLOG = 5;

        TCPListener() {
            super(mHandler, new byte[0]/* Unused */);
        }

        @Override
        protected int recvBufSize(@NonNull byte[] buffer/* Unused */) {
            return 0;
        }

        @Override
        protected int readPacket(@NonNull FileDescriptor fd, @NonNull byte[] buffer/* Unused */)
                throws Exception {
            final InetSocketAddress addr = new InetSocketAddress();
            final FileDescriptor peerFd = Os.accept(fd, addr);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        TrafficStats.getAndSetThreadStatsTag(TAG_SYSTEM_DNSPROXY_SERVER);
                        NetworkUtils.protectFromVpn(peerFd);
                        StructTimeval tv = StructTimeval.fromMillis(TCP_RCV_TIMEOUT);
                        Os.setsockoptTimeval(peerFd, SOL_SOCKET, SO_RCVTIMEO, tv);
                        byte[] len = new byte[2];
                        final int read = Os.read(peerFd, len, 0, 2);
                        int packetLen = (short) ((len[0] << 8) | (len[1] & 0xff));
                        byte[] packet = new byte[packetLen];
                        int current = 0;
                        while (true) {
                            int readPacket = Os.read(peerFd, packet, current, packetLen - current);
                            if (readPacket > 0) {
                                current += readPacket;
                                if (current == packetLen) {
                                    break;
                                }
                            }
                        }
                        processPacket(peerFd, IPPROTO_TCP, packet, packetLen,
                                addr.getAddress(), addr.getPort());
                    } catch (IOException | ErrnoException e) {
                        IoUtils.closeQuietly(peerFd);
                    }
                }
            }).start();
            return ACCEPT_SUCCESS;
        }

        @Override
        protected void logError(String msg, Exception e) {
            mLog.e("Error receiving packet: " + msg, e);
        }

        @Override
        protected FileDescriptor createFd() {
            // TODO: have and use an API to set a socket tag without going through the thread tag
            final int oldTag = TrafficStats.getAndSetThreadStatsTag(TAG_SYSTEM_DNSPROXY_SERVER);
            try {
                mTCPSocket = Os.socket(AF_INET6, SOCK_STREAM, IPPROTO_TCP);
                Os.setsockoptInt(mTCPSocket, SOL_SOCKET, SO_REUSEADDR, 1);
                // SO_BINDTODEVICE actually takes a string. This works because the first member
                // of struct ifreq is a NULL-terminated interface name.
                // TODO: add a setsockoptString()
                Os.setsockoptIfreq(mTCPSocket, SOL_SOCKET, SO_BINDTODEVICE, mIfName);
                Os.bind(mTCPSocket, Inet6Address.ANY, DNS_SERVER);
                NetworkUtils.protectFromVpn(mTCPSocket);
                Os.listen(mTCPSocket, BACKLOG);
                return mTCPSocket;
            } catch (IOException | ErrnoException e) {
                mLog.e("Error creating TCP socket", e);
                DnsProxyServer.this.stop();
                return null;
            } finally {
                TrafficStats.setThreadStatsTag(oldTag);
            }
        }
    }

    static final class UDPPayLoad {
        final byte[] bytes = new byte[512];
        InetAddress srcAddr;
        int srcPort;
    }

    @VisibleForTesting
    class UDPListener extends FdEventsReader<UDPPayLoad> {
        UDPListener() {
            super(mHandler, new UDPPayLoad());
        }

        @Override
        protected int recvBufSize(@NonNull UDPPayLoad buffer) {
            return buffer.bytes.length;
        }

        @Override
        protected void handlePacket(@NonNull UDPPayLoad recvbuf, int length) {
            if (recvbuf.srcAddr == null) {
                return;
            }
            processPacket(mUDPSocket, IPPROTO_UDP, recvbuf.bytes, length,
                    recvbuf.srcAddr, recvbuf.srcPort);
        }
        @Override
        protected int readPacket(@NonNull FileDescriptor fd, @NonNull UDPPayLoad packetBuffer)
                throws Exception {
            final InetSocketAddress addr = new InetSocketAddress();
            final int read = Os.recvfrom(
                    fd, packetBuffer.bytes, 0, packetBuffer.bytes.length, 0 /* flags */, addr);

            // Buffers with null srcAddr will be dropped in handlePacket()
            packetBuffer.srcAddr = addr.getAddress();
            packetBuffer.srcPort = addr.getPort();
            return read;
        }

        @Override
        protected void logError(String msg, Exception e) {
            mLog.e("Error receiving packet: " + msg, e);
        }

        @Override
        protected FileDescriptor createFd() {
            // TODO: have and use an API to set a socket tag without going through the thread tag
            final int oldTag = TrafficStats.getAndSetThreadStatsTag(TAG_SYSTEM_DNSPROXY_SERVER);
            try {
                mUDPSocket = Os.socket(AF_INET6, SOCK_DGRAM, IPPROTO_UDP);
                Os.setsockoptInt(mUDPSocket, SOL_SOCKET, SO_REUSEADDR, 1);
                // SO_BINDTODEVICE actually takes a string. This works because the first member
                // of struct ifreq is a NULL-terminated interface name.
                // TODO: add a setsockoptString()
                Os.setsockoptIfreq(mUDPSocket, SOL_SOCKET, SO_BINDTODEVICE, mIfName);
                Os.setsockoptInt(mUDPSocket, SOL_SOCKET, SO_BROADCAST, 1);
                Os.bind(mUDPSocket, Inet6Address.ANY, DNS_SERVER);
                NetworkUtils.protectFromVpn(mUDPSocket);

                return mUDPSocket;
            } catch (IOException | ErrnoException e) {
                mLog.e("Error creating UDP socket", e);
                DnsProxyServer.this.stop();
                return null;
            } finally {
                TrafficStats.setThreadStatsTag(oldTag);
            }
        }
    }
}
