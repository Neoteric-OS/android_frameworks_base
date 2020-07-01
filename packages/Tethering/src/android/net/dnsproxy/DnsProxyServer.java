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

package android.net.dnsproxy;

import static android.net.DnsResolver.FLAG_NO_RETRY;
import static android.net.util.TetheringUtils.closeSocketQuietly;
import static android.os.MessageQueue.OnFileDescriptorEventListener.EVENT_ERROR;
import static android.os.MessageQueue.OnFileDescriptorEventListener.EVENT_INPUT;
import static android.system.OsConstants.AF_INET6;
import static android.system.OsConstants.EBADF;
import static android.system.OsConstants.EINVAL;
import static android.system.OsConstants.IPPROTO_TCP;
import static android.system.OsConstants.IPPROTO_UDP;
import static android.system.OsConstants.SOCK_DGRAM;
import static android.system.OsConstants.SOCK_STREAM;
import static android.system.OsConstants.SOL_SOCKET;
import static android.system.OsConstants.SO_BROADCAST;
import static android.system.OsConstants.SO_RCVTIMEO;
import static android.system.OsConstants.SO_REUSEADDR;
import static android.system.OsConstants.SO_SNDTIMEO;

import static com.android.internal.util.TrafficStatsConstants.TAG_SYSTEM_DNSPROXY_SERVER;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.DnsResolver;
import android.net.InetAddresses;
import android.net.Network;
import android.net.TrafficStats;
import android.net.util.SharedLog;
import android.net.util.SocketUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructTimeval;

import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.DnsPacket;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/**
 * A DnsProxy server.
 *
 * <p>This server listens for and responds to packets on a single interface.
 *
 * <p>The server would handle non-blocking events on the provided
 * {@link HandlerThread} asynchronously.
 * Non-blocking events
 * 1. UDP events (including send/receive operations)
 * 2. TCP events (accepting new connections)
 * 3. UDP DNS responses
 *
 * This server also uses one fixed thread pool for handling accepted TCP connections
 * since TCP receive/send operations might be blocking.
 * Every new TCP accepted connection will be handled in {@link ExecutorService}
 * (Including receive/send operations and TCP DNS responses)
 *
 * @hide
 */
public class DnsProxyServer {
    private static final int FD_EVENTS = EVENT_INPUT | EVENT_ERROR;
    private static final int MAX_TCP_THREADS = 2;
    private static final int BACKLOG = 5;
    private static final int CMD_START_DNS_PROXY_SERVER = 1;
    private static final int CMD_STOP_DNS_PROXY_SERVER = 2;
    private static final int CMD_UPDATE_UPSTREAM_DNS_PROXY_SERVER = 3;
    private static final int DNS_PORT = 53;
    private static final int QUERY_FLAGS = FLAG_NO_RETRY;
    private static final Inet6Address IPV6_ADDR_ANY =
            (Inet6Address) InetAddresses.parseNumericAddress("::");

    @NonNull
    private final HandlerThread mHandlerThread;
    @NonNull
    private final ServerHandler mHandler;
    @NonNull
    private final MessageQueue mQueue;
    @NonNull
    private final SharedLog mLog;
    @NonNull
    private final ServerDependencies mDeps;
    @NonNull
    private final FileDescriptor mTCPSocket;
    @NonNull
    private final FileDescriptor mUDPSocket;
    @NonNull
    private final String mIfName;
    @NonNull
    private final ExecutorService mTCPExecutor;
    @NonNull
    private final Executor mUDPExecutor;

    // Accessed only on the handler thread
    @Nullable
    private Network mNetwork;

    /**
     * Dependencies for the DnsProxyServer. Useful to be mocked in tests.
     */
    public interface ServerDependencies {
        /**
         * Create a UDP socket that will be used to listen UDP DNS request.
         *
         * @throws ErrnoException Create socket failed.
         */
        FileDescriptor makeUDPSocket() throws ErrnoException;

        /**
         * Create a TCP socket that will be used to listen TCP DNS request.
         *
         * @throws ErrnoException Create socket failed.
         */
        FileDescriptor makeTCPSocket() throws ErrnoException;

        /**
         * Create a started handler thread by given name.
         */
        HandlerThread makeHandlerThread(@NonNull String ifName);

        /**
         * Listen DNS port on given UDP socket and interface.
         */
        void listenDnsUDP(@NonNull FileDescriptor fd, @NonNull String ifName)
                throws ErrnoException, IOException;

        /**
         * Listen DNS port on given TCP socket and interface.
         */
        void listenDnsTCP(@NonNull FileDescriptor fd, @NonNull String ifName)
                throws ErrnoException, IOException;
    }

    /**
     * Dependencies for the RequestResponder. Useful to be mocked in tests.
     */
    public interface ResponderDependencies {
        /**
         * Send a packet to the specified fd.
         *
         * @param fd      File descriptor of the socket connected with tethering client.
         * @param buffer  Data to be sent.
         * @param dstAddr Destination address of the packet.
         * @param dstPort Destination port of the packet.
         */
        void sendPacket(@NonNull FileDescriptor fd, @NonNull byte[] buffer,
                @NonNull InetAddress dstAddr, int dstPort) throws ErrnoException, IOException;

        /**
         * Receive a message from the specified fd.
         *
         * @param fd       File descriptor of the socket.
         * @param queryBuf buffer to put received packet.
         * @param from     {@link InetSocketAddress} for the packet.
         */
        int recvfrom(@NonNull FileDescriptor fd, @NonNull byte[] queryBuf, int bufLen,
                @NonNull InetSocketAddress from) throws ErrnoException, IOException;

        /**
         * Read from a file descriptor.
         *
         * @param fd         File descriptor of the socket.
         * @param buf        buffer to put received packet.
         * @param byteOffset offset
         * @param byteCount  size
         */
        int read(@NonNull FileDescriptor fd, byte[] buf, int byteOffset, int byteCount)
                throws ErrnoException, IOException;

        /**
         * Set socket option.
         *
         * @param fd        File descriptor of the socket.
         * @param option    Socket option
         * @param timeMs    timeMs
         */
        void setsockoptTimeval(@NonNull FileDescriptor fd, int option, int timeMs)
                throws ErrnoException;
    }

    private static class ServerDependenciesImpl implements ServerDependencies {
        @Override
        public FileDescriptor makeUDPSocket() throws ErrnoException {
            // TODO: have and use an API to set a socket tag without going through the thread tag
            final int oldTag = TrafficStats.getAndSetThreadStatsTag(TAG_SYSTEM_DNSPROXY_SERVER);
            try {
                final FileDescriptor fd = Os.socket(AF_INET6, SOCK_DGRAM, IPPROTO_UDP);
                return fd;
            } finally {
                TrafficStats.setThreadStatsTag(oldTag);
            }
        }

        @Override
        public FileDescriptor makeTCPSocket() throws ErrnoException {
            // TODO: have and use an API to set a socket tag without going through the thread tag
            final int oldTag = TrafficStats.getAndSetThreadStatsTag(TAG_SYSTEM_DNSPROXY_SERVER);
            try {
                final FileDescriptor fd = Os.socket(AF_INET6, SOCK_STREAM, IPPROTO_TCP);
                return fd;
            } finally {
                TrafficStats.setThreadStatsTag(oldTag);
            }
        }

        @Override
        public HandlerThread makeHandlerThread(@NonNull String ifName) {
            final HandlerThread ht = new HandlerThread(
                    DnsProxyServer.class.getSimpleName() + "." + ifName);
            ht.start();
            return ht;
        }

        @Override
        public void listenDnsUDP(@NonNull FileDescriptor fd, @NonNull String ifName)
                throws ErrnoException, IOException {
            Os.setsockoptInt(fd, SOL_SOCKET, SO_REUSEADDR, 1);
            SocketUtils.bindSocketToInterface(fd, ifName);
            Os.setsockoptInt(fd, SOL_SOCKET, SO_BROADCAST, 1);
            Os.bind(fd, IPV6_ADDR_ANY, DNS_PORT);
        }

        @Override
        public void listenDnsTCP(@NonNull FileDescriptor fd, @NonNull String ifName)
                throws ErrnoException, IOException {
            Os.setsockoptInt(fd, SOL_SOCKET, SO_REUSEADDR, 1);
            SocketUtils.bindSocketToInterface(fd, ifName);
            Os.bind(fd, IPV6_ADDR_ANY, DNS_PORT);
            Os.listen(fd, BACKLOG);
        }
    }

    private static class ResponderDependenciesImpl implements ResponderDependencies {
        @Override
        public void sendPacket(@NonNull FileDescriptor fd, @NonNull byte[] buffer,
                @NonNull InetAddress dstAddr, int dstPort) throws ErrnoException, IOException {
            Os.sendto(fd, buffer, 0, buffer.length, 0, dstAddr, dstPort);
        }

        @Override
        public int recvfrom(@NonNull FileDescriptor fd,
                @NonNull byte[] buf, int bufLen, @NonNull InetSocketAddress from)
                throws ErrnoException, IOException {
            return Os.recvfrom(fd, buf, 0, bufLen, 0 /* flags */, from);
        }

        @Override
        public int read(@NonNull FileDescriptor fd, byte[] buf, int byteOffset, int byteCount)
                throws ErrnoException, IOException {
            return Os.read(fd, buf, byteOffset, byteCount);
        }

        @Override
        public void setsockoptTimeval(@NonNull FileDescriptor fd, int option, int timeMs)
                throws ErrnoException {
            Os.setsockoptTimeval(fd, SOL_SOCKET, option, StructTimeval.fromMillis(timeMs));
        }
    }

    public DnsProxyServer(@NonNull String ifName, @NonNull SharedLog log) throws ErrnoException {
        this(ifName, log, new ServerDependenciesImpl());
    }

    @VisibleForTesting
    DnsProxyServer(@NonNull String ifName, @NonNull SharedLog log,
            @NonNull ServerDependencies deps) throws ErrnoException {
        mHandlerThread = deps.makeHandlerThread(ifName);
        mHandler = new ServerHandler(mHandlerThread.getLooper());
        mQueue = mHandlerThread.getLooper().getQueue();
        mIfName = ifName;
        mLog = log;
        mDeps = deps;
        mUDPSocket = mDeps.makeUDPSocket();
        mTCPSocket = mDeps.makeTCPSocket();
        mTCPExecutor = Executors.newFixedThreadPool(MAX_TCP_THREADS);
        mUDPExecutor = mHandler::post;
    }

    /**
     * Start listening for DNS queries and do the querying on {@code Network}.
     */
    public void start(@Nullable Network network) throws ErrnoException, IOException {
        startListen();
        sendMessage(CMD_START_DNS_PROXY_SERVER, network);
    }

    /**
     * Update upstream network to {@code Network}.
     */
    public void updateUpstream(@Nullable Network network) {
        sendMessage(CMD_UPDATE_UPSTREAM_DNS_PROXY_SERVER, network);
    }

    /**
     * Stop listening for DNS queries.
     *
     * <p>As the server is stopped asynchronously, some packets may still be processed shortly after
     * calling this method.
     */
    public void stop() {
        sendMessage(CMD_STOP_DNS_PROXY_SERVER);
    }

    private void sendMessage(int what, @Nullable Object obj) {
        mHandler.sendMessage(mHandler.obtainMessage(what, obj));
    }

    private void sendMessage(int what) {
        mHandler.sendMessage(mHandler.obtainMessage(what));
    }

    private class ServerHandler extends Handler {
        ServerHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case CMD_START_DNS_PROXY_SERVER:
                    mNetwork = (Network) msg.obj;
                    registerFds();
                    break;
                case CMD_STOP_DNS_PROXY_SERVER:
                    stopListenDns();
                    mHandlerThread.quitSafely();
                    break;
                case CMD_UPDATE_UPSTREAM_DNS_PROXY_SERVER:
                    mNetwork = (Network) msg.obj;
                    break;
                default:
                    return;
            }
        }
    }

    private static class DnsProxyPacket extends DnsPacket {
        private static final int DNS_HEADER_QUERY_OFFSET = 15;

        DnsProxyPacket(@NonNull byte[] data) throws DnsPacket.ParseException, ErrnoException {
            super(data);
            if (mHeader.getRecordCount(QDSECTION) == 0) {
                throw new ErrnoException("No question found", EINVAL);
            }
        }

        boolean isQuery() {
            return ((mHeader.flags & (1 << DNS_HEADER_QUERY_OFFSET)) == 0);
        }
    }

    /**
     * Add packet length to front of |buf|.
     */
    public static byte[] addPacketLength(@NonNull byte[] buf) {
        final byte[] ansPacket = new byte[buf.length + 2];
        ansPacket[0] = (byte) ((buf.length >> 8) & 0xff);
        ansPacket[1] = (byte) (buf.length & 0xff);
        System.arraycopy(buf, 0, ansPacket, 2, buf.length);
        return ansPacket;
    }

    /**
     * Modify the |queryBuf|, change its rcode and set QR to produce a fail DNS response packet.
     */
    public static byte[] setupFailResponse(@NonNull byte[] queryBuf, int rcode) {
        // Change rcode and set OR.
        // TODO: Refine DnsProxyPacket to create answer.
        queryBuf[2] = (byte) (queryBuf[2] | 0x80);
        queryBuf[3] = (byte) rcode;
        return queryBuf;
    }

    @VisibleForTesting
    boolean isUpstreamAvailable() {
        return (mNetwork != null);
    }

    private void registerFd(@NonNull FileDescriptor listenFd,
            @NonNull BiConsumer<FileDescriptor, Integer> eventConsumer) {
        mQueue.addOnFileDescriptorEventListener(
                listenFd,
                FD_EVENTS,
                (fd, events) -> {
                    if (!isUpstreamAvailable()) {
                        mLog.i("No upstream Network");
                        return events;
                    }
                    eventConsumer.accept(fd, events);
                    return events;
                });
    }

    private void startListen() throws ErrnoException, IOException {
        if (!mTCPSocket.valid() || !mUDPSocket.valid()) {
            closeSocketQuietly(mUDPSocket);
            closeSocketQuietly(mTCPSocket);
            throw new ErrnoException("Invalid socket", EBADF);
        }
        mDeps.listenDnsTCP(mTCPSocket, mIfName);
        mDeps.listenDnsUDP(mUDPSocket, mIfName);
    }

    private void registerFds() {
        registerFd(mTCPSocket,
                (fd, events) -> {
                    final int oldTag = TrafficStats.getAndSetThreadStatsTag(
                            TAG_SYSTEM_DNSPROXY_SERVER);
                    try {
                        final InetSocketAddress addr = new InetSocketAddress(0);
                        final FileDescriptor peerFd = Os.accept(fd, addr);
                        mTCPExecutor.submit(() -> new TCPRequestResponder(mNetwork, peerFd, addr,
                                mLog, mTCPExecutor).handleRequest()
                        );
                    } catch (ErrnoException | SocketException e) {
                        mLog.e("Error on accepting TCP request : ", e);
                    } finally {
                        TrafficStats.setThreadStatsTag(oldTag);
                    }
                });

        registerFd(mUDPSocket,
                (fd, events) -> new UDPRequestResponder(mNetwork, fd, mLog,
                        mUDPExecutor).handleRequest());
    }

    private void stopListenDns() {
        mQueue.removeOnFileDescriptorEventListener(mUDPSocket);
        mQueue.removeOnFileDescriptorEventListener(mTCPSocket);
        closeSocketQuietly(mUDPSocket);
        closeSocketQuietly(mTCPSocket);
    }

    abstract static class RequestResponder {
        private static final int RCODE_FORMERR = 1;
        private static final int RCODE_SERVFAIL = 2;

        @NonNull
        private final DnsResolver mDns;
        @NonNull
        private final Network mNetwork;
        @NonNull
        protected final SharedLog mLog;
        @NonNull
        protected final ResponderDependencies mDeps;
        @NonNull
        private final FileDescriptor mFd;
        @NonNull
        private final Executor mExecutor;

        @Nullable
        protected InetSocketAddress mDstAddr;

        RequestResponder(@NonNull Network network, @NonNull FileDescriptor fd,
                @NonNull SharedLog log, @NonNull ResponderDependencies deps,
                @NonNull DnsResolver dns, @NonNull Executor executor) {
            mFd = fd;
            mDeps = deps;
            mLog = log;
            mDns = dns;
            mNetwork = network;
            mExecutor = executor;
        }

        public final void handleRequest() {
            final byte[] queryPacket;
            try {
                queryPacket = readPacket(mFd);
            } catch (IOException | ErrnoException e) {
                mLog.e("read packet failed: ", e);
                cleanup(mFd);
                return;
            }
            // Should not happen
            if (mDstAddr == null) {
                mLog.e("no destination found");
                cleanup(mFd);
                return;
            }
            processDnsPacket(mNetwork, mFd, queryPacket, mDstAddr.getAddress(), mDstAddr.getPort());
        }

        private void performDnsQuery(@NonNull Network network, @NonNull FileDescriptor dstFd,
                @NonNull byte[] queryBuf, @NonNull InetAddress dstAddr, int dstPort) {
            final DnsResolver.Callback<byte[]> callback = new DnsResolver.Callback<byte[]>() {
                @Override
                public void onAnswer(@NonNull byte[] answer, int rcode) {
                    transmitAnswer(dstFd, answer, dstAddr, dstPort);
                }

                @Override
                public void onError(@NonNull DnsResolver.DnsException error) {
                    transmitAnswer(dstFd, setupFailResponse(queryBuf, RCODE_SERVFAIL), dstAddr,
                            dstPort);
                }
            };
            // Depends on |mExecutor|, it is possible that methods of |callback|
            // are called in different thread than this method is running.
            // It's fine because all necessary works before querying are already done.
            mDns.rawQuery(network, queryBuf, QUERY_FLAGS, mExecutor, null, callback);
        }

        final void processDnsPacket(@NonNull Network network, @NonNull FileDescriptor fd,
                @NonNull byte[] queryPacket, @NonNull InetAddress dstAddr, int dstPort) {
            try {
                // Check if the query is valid.
                if (!new DnsProxyPacket(queryPacket).isQuery()) {
                    transmitAnswer(fd,
                            setupFailResponse(queryPacket, RCODE_FORMERR), dstAddr, dstPort);
                    return;
                }
            } catch (DnsPacket.ParseException | ErrnoException e) {
                mLog.e("Ignored malformed packet", e);
                cleanup(fd);
                return;
            }

            performDnsQuery(network, fd, queryPacket, dstAddr, dstPort);
        }

        void cleanup(@NonNull FileDescriptor fd) {}

        abstract void transmitAnswer(@NonNull FileDescriptor fd,
                @NonNull byte[] buf, @NonNull InetAddress dstAddr, int dstPort);

        @NonNull
        abstract byte[] readPacket(@NonNull FileDescriptor fd) throws IOException, ErrnoException;
    }

    @VisibleForTesting
    static class UDPRequestResponder extends RequestResponder {
        static final int UDP_BUFFER_LENGTH = 512;

        private final byte[] mUdpBuffer;

        UDPRequestResponder(@NonNull Network network, @NonNull FileDescriptor fd,
                @NonNull SharedLog log, @NonNull Executor executor) {
            this(network, fd, log, new ResponderDependenciesImpl(),
                    DnsResolver.getInstance(), executor);
        }

        UDPRequestResponder(@NonNull Network network, @NonNull FileDescriptor fd,
                @NonNull SharedLog log, @NonNull ResponderDependencies deps,
                @NonNull DnsResolver dns, @NonNull Executor executor) {
            super(network, fd, log, deps, dns, executor);
            mUdpBuffer = new byte[UDP_BUFFER_LENGTH];
        }

        @Override
        void transmitAnswer(@NonNull FileDescriptor fd,
                @NonNull byte[] buf, @NonNull InetAddress dstAddr, int dstPort) {
            try {
                mDeps.sendPacket(fd, buf, dstAddr, dstPort);
            } catch (ErrnoException | IOException e) {
                mLog.e("Can't send packet ", e);
            }
        }

        @Override
        @NonNull
        byte[] readPacket(@NonNull FileDescriptor fd) throws IOException, ErrnoException {
            mDstAddr = new InetSocketAddress(0);
            final int read = mDeps.recvfrom(
                    fd, mUdpBuffer, UDP_BUFFER_LENGTH, mDstAddr);
            return Arrays.copyOf(mUdpBuffer, read);
        }
    }

    @VisibleForTesting
    static class TCPRequestResponder extends RequestResponder {
        private static final int TCP_RCV_TIMEOUT_MS = 30000;
        private static final int TCP_SND_TIMEOUT_MS = 30000;

        TCPRequestResponder(@NonNull Network network, @NonNull FileDescriptor fd,
                @NonNull InetSocketAddress dstAddr, @NonNull SharedLog log,
                @NonNull Executor executor) {
            this(network, fd, dstAddr, log, new ResponderDependenciesImpl(),
                    DnsResolver.getInstance(), executor);
        }

        TCPRequestResponder(@NonNull Network network, @NonNull FileDescriptor fd,
                @NonNull InetSocketAddress dstAddr, @NonNull SharedLog log,
                @NonNull ResponderDependencies deps, @NonNull DnsResolver dns,
                @NonNull Executor executor) {
            super(network, fd, log, deps, dns, executor);
            mDstAddr = dstAddr;
        }

        void setSocketTimeout(@NonNull FileDescriptor fd) throws ErrnoException {
            mDeps.setsockoptTimeval(fd, SO_SNDTIMEO, TCP_SND_TIMEOUT_MS);
            mDeps.setsockoptTimeval(fd, SO_RCVTIMEO, TCP_RCV_TIMEOUT_MS);
        }

        @Override
        void transmitAnswer(@NonNull FileDescriptor fd,
                @NonNull byte[] buf, @NonNull InetAddress dstAddr, int dstPort) {
            try {
                mDeps.sendPacket(fd, addPacketLength(buf), dstAddr, dstPort);
            } catch (ErrnoException | IOException e) {
                mLog.e("Send TCP answer failed ", e);
            } finally {
                cleanup(fd);
            }
        }

        @NonNull
        private byte[] readBytes(@NonNull FileDescriptor fd, int size)
                throws IOException, ErrnoException {
            int read, current = 0;
            final byte[] buf = new byte[size];
            while ((read = mDeps.read(fd, buf, current, size)) > 0) {
                current += read;
                if ((size -= read) == 0) break;
            }
            return buf;
        }

        @Override
        @NonNull
        byte[] readPacket(@NonNull FileDescriptor fd) throws IOException, ErrnoException {
            setSocketTimeout(fd);
            // Read length of packet
            final int readSize = 2;
            final byte[] len = readBytes(fd, readSize);
            // Read packet
            final int packetLen = (short) ((len[0] << 8) | (len[1] & 0xff));
            final byte[] tcpBuf = readBytes(fd, packetLen);
            return tcpBuf;
        }

        @Override
        void cleanup(@NonNull FileDescriptor fd) {
            closeSocketQuietly(fd);
        }
    }
}
