/*
 * Copyright (c) 2010, Code Aurora Forum. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at

 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package android.net;

import java.io.IOException;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Map;
import java.util.Set;

/**
 * TODO: documentation
 * <p>
 * Provides a client-side TCP socket.
 *
 * @see LinkSocketNotifier
 */
public class LinkSocket extends Socket {

    /**
     * Creates a new unconnected socket.
     *
     * @param notifier
     *            a reference to a class that implements
     *            {@code LinkSocketNotifier}
     */
    public LinkSocket(LinkSocketNotifier notifier) {
        return;
    }

    /**
     * Creates a new unconnected socket using the given proxy type.
     *
     * @param notifier
     *            a reference to a class that implements
     *            {@code LinkSocketNotifier}
     * @param proxy
     *            the specified proxy for this socket
     * @throws IllegalArgumentException
     *             if the argument proxy is null or of an invalid
     *             type.
     * @throws SecurityException
     *             if a security manager exists and it denies the
     *             permission to connect to the given proxy.
     */
    public LinkSocket(LinkSocketNotifier notifier, Proxy proxy) {
        return;
    }

    /**
     * Set the needs of the socket. If the socket is already connected
     * or is a duplicate socket then the new needs will be ignored. A
     * needs map can be created via the {@code createNeedsMap} static
     * method.
     *
     * @param needs
     *            the needs of the socket
     * @return true if needs are successfully set, false otherwise
     */
    public boolean setNeeds(Map<Integer, String> needs) {
        return false;
    }

    /**
     * Return a {@code Map} of the given needs of the socket
     *
     * @return a {@code Map} of the given needs of the socket
     */
    public Map<Integer, String> getNeeds() {
        return null;
    }

    /**
     * @return the isNotificationEnabled
     */
    public boolean isNotificationEnabled() {
        return false;
    }

    /**
     * @param isEnabled
     *            {@code true} to enable callback notifications,
     *            {@code false} to disable
     */
    public void setNotificationEnabled(boolean isEnabled) {
        return;
    }

    /**
     * Return a {@code Map} of the given capabilities of the given
     * socket
     *
     * @return a {@code Map} of the given capabilities of the given
     *         socket
     */
    public Map<Integer, String> getCapabilities() {
        return null;
    }

    /**
     * Return a {@code Map} of the requested current capabilities of
     * the given socket
     *
     * @param capabilities
     *            {@code Set} of capabilities requested
     * @return a {@code Map} of the requested current capabilities of
     *         the given socket
     */
    public Map<Integer, String> getCapabilities(Set<Integer> capabilities) {
        return null;
    }

    /**
     * Provide the set of capabilities the application is interested
     * in tracking for the socket
     *
     * @param capabilities
     *            a {@code Set} of capabilities to track
     */
    public void setTrackedCapabilities(Set<Integer> capabilities) {
        return;
    }

    /**
     * Connects this socket to the given remote host address and port
     * specified by the dstName and dstPort.
     *
     * @param dstName
     *            the address of the remote host to connect to.
     * @param dstPort
     *            the port to connect to on the remote host.
     * @param timeout
     *            the timeout value in milliseconds or 0 for an
     *            infinite timeout.
     * @throws IllegalArgumentException
     *             if the given SocketAddress is invalid or not
     *             supported.
     * @throws IOException
     *             if the socket is already connected or an error
     *             occurs while connecting.
     */
    public void connect(String dstName, int dstPort, int timeout) throws IOException {
        return;
    }

    /**
     * Connects this socket to the given remote host address and port
     * specified by the dstName and dstPort.
     *
     * @param dstName
     *            the address of the remote host to connect to.
     * @param dstPort
     *            the port to connect to on the remote host.
     * @throws IllegalArgumentException
     *             if the given SocketAddress is invalid or not
     *             supported.
     * @throws IOException
     *             if the socket is already connected or an error
     *             occurs while connecting.
     */
    public void connect(String dstName, int dstPort) throws IOException {
        return;
    }

    /**
     * Connects this socket to the given remote host address and port
     * specified by the SocketAddress remoteAddr with the specified
     * timeout. The connecting method will block until the connection
     * is established or an error occurred.
     *
     * @deprecated Use
     *             {@code connect(String dstName, int dstPort, int timeout)}
     *             instead. Using this method may result in reduced
     *             functionality.
     * @param remoteAddr
     *            the address and port of the remote host to connect
     *            to.
     * @param timeout
     *            the timeout value in milliseconds or 0 for an
     *            infinite timeout.
     * @throws IllegalArgumentException
     *             if the given SocketAddress is invalid or not
     *             supported or the timeout value is negative.
     * @throws IOException
     *             if the socket is already connected or an error
     *             occurs while connecting.
     */
    @Override
    @Deprecated
    public void connect(SocketAddress remoteAddr, int timeout) throws IOException,
            IllegalArgumentException {
        return;
    }

    /**
     * Connects this socket to the given remote host address and port
     * specified by the SocketAddress {@code remoteAddr}.
     *
     * @deprecated Use {@code connect(String dstName, int dstPort)}
     *             instead. Using this method may result in reduced
     *             functionality.
     * @param remoteAddr
     *            the address and port of the remote host to connect
     *            to.
     * @throws IllegalArgumentException
     *             if the given SocketAddress is invalid or not
     *             supported.
     * @throws IOException
     *             if the socket is already connected or an error
     *             occurs while connecting.
     */
    @Override
    @Deprecated
    public void connect(SocketAddress remoteAddr) throws IOException, IllegalArgumentException {
        return;
    }

    /**
     * Connect a duplicate socket to the same remote host address and
     * port as the original, using the best currently available
     * connection.
     *
     * @param timeout
     *            the timeout value in milliseconds or 0 for an
     *            infinite timeout.
     * @throws IOException
     *             if the socket is already connected or an error
     *             occurs while connecting.
     */
    public void connect(int timeout) throws IOException {
        return;
    }

    /**
     * Connect a duplicate socket to the same remote host address and
     * port as the original, using the best currently available
     * connection.
     *
     * @throws IOException
     *             if the socket is already connected or an error
     *             occurs while connecting.
     */
    public void connect() throws IOException {
        return;
    }

    /**
     * Closes the socket. It is not possible to reconnect or re-bind
     * to this socket thereafter which means a new socket instance has
     * to be created.
     *
     * @throws IOException
     *             if an error occurs while closing the socket.
     */
    @Override
    public synchronized void close() throws IOException {
        return;
    }

    /**
     * Request that a new LinkSocket be created using a different
     * radio (such as WiFi or 3G) than the current LinkSocket. If a
     * different radio is available a callback will be made via
     * {@code onBetterLinkAvail} If unable to find a better radio,
     * application will be notified via {@code onNewLinkUnavailable}
     *
     * @see LinkSocketNotifier#onBetterLinkAvail(LinkSocket,
     *      LinkSocket)
     * @param linkRequestReason
     *            reason for requesting a new link. Reason codes are
     *            TBD.
     */
    public void requestNewLink(int linkRequestReason) {
        return;
    }

    /**
     * @deprecated LinkSocket will automatically pick the optimum
     *             interface to bind to.
     * @param localAddr
     *            the specific address and port on the local machine
     *            to bind to
     * @throws IOException
     *             always as this method is deprecated for LinkSocket
     */
    @Override
    @Deprecated
    public void bind(SocketAddress localAddr) throws IOException {
        return;
    }

    /**
     * Create a needs map, and populate it with values depending on
     * role type.
     *
     * @param applicationRole
     *            a {@code LinkSocket.Role}
     * @return a needs map
     */
    public static Map<Integer, String> createNeedsMap(String applicationRole) {
        return null;
    }

    /**
     * The different keys that the needs and capabilities maps
     * understand.
     *
     * Keys starting with RW are read + write, i.e. the application
     * can request for a certain requirement corresponding to that
     * key.
     *
     * Keys starting with RO are read only, i.e. the application can
     * read the value of that key from the socket but cannot request a
     * corresponding requirement.
     */
    public static final class Key {

        // no constructor
        private Key() {
            return;
        }

        /**
         * Lets the LinkSocket know about the typical data usage of
         * the application. A value of "0" will result in the role
         * being set to {@code Role.DEFAULT}.
         * <p>
         * This key is set via the needs map.
         *
         * @see LinkSocket.Role
         */
        public final static int RW_ROLE = 1;

        /**
         * An integer representing the network type.
         * <p>
         * This key is stored in the capabilities map.
         *
         * @see ConnectivityManager
         */
        public final static int RO_NETWORK_TYPE = 3;

        /**
         * Desired minimum forward link (download) bandwidth for the
         * socket in kilobits per second (kbps). Values should be
         * strings such as "50", "100", "1500", etc.
         * <p>
         * This key is set via the needs map.
         */
        public final static int RW_DESIRED_FWD_BW = 100;

        /**
         * Required minimum forward link (download) bandwidth, in
         * kilobits per second (kbps), below which the socket cannot
         * function. Values should be strings such as "50", "100",
         * "1500", etc.
         * <p>
         * This key is set via the needs map.
         */
        public final static int RW_REQUIRED_FWD_BW = 101;

        /**
         * Available forward link (download) bandwidth for the socket.
         * This value is in kilobits per second (kbps). Values will be
         * strings such as "50", "100", "1500", etc.
         * <p>
         * This key is stored in the capabilities map.
         */
        public final static int RO_AVAILABLE_FWD_BW = 102;

        /**
         * Desired minimum reverse link (upload) bandwidth for the
         * socket in kilobits per second (kbps). Values should be
         * strings such as "50", "100", "1500", etc.
         * <p>
         * This key is set via the needs map.
         */
        public final static int RW_DESIRED_REV_BW = 110;

        /**
         * Required minimum reverse link (upload) bandwidth, in
         * kilobits per second (kbps), below which the socket cannot
         * function. If a rate is not specified, the default rate of
         * kbps will be used. Values should be strings such as "50",
         * "100", "1500", etc.
         * <p>
         * This key is set via the needs map.
         */
        public final static int RW_REQUIRED_REV_BW = 111;

        /**
         * Available reverse link (upload) bandwidth for the socket.
         * This value is in kilobits per second (kbps). Values will be
         * strings such as "50", "100", "1500", etc.
         * <p>
         * This key is stored in the capabilities map.
         */
        public final static int RO_AVAILABLE_REV_BW = 112;

        /**
         * Maximum latency for the socket, in milliseconds, above
         * which the socket cannot function. Values should be strings
         * such as "50", "300", "500", etc.
         * <p>
         * This key is set via the needs map.
         */
        public final static int RW_MAX_ALLOWED_LATENCY = 120;

        /**
         * Interface that the socket is bound to. This can be a
         * virtual interface (e.g. VPN or Mobile IP) or a physical
         * interface (e.g. wlan0 or rmnet0). Values will be strings
         * such as "wlan0", "rmnet0"
         * <p>
         * This key is stored in the capabilities map.
         */
        public final static int RO_BOUND_INTERFACE = 140;

        /**
         * Physical interface that the socket is routed on. This can
         * be different from BOUND_INTERFACE in cases such as VPN or
         * Mobile IP. The physical interface may change over time if
         * seamless mobility is supported. Values will be strings such
         * as "wlan0", "rmnet0"
         * <p>
         * This key is stored in the capabilities map.
         */
        public final static int RO_PHYSICAL_INTERFACE = 150;
    }

    /**
     * Role informs the LinkSocket about the data usage patterns of
     * your application.
     * <P>
     * {@code Role.DEFAULT} is the default role, and is used whenever
     * a role isn't set.
     */
    public static final class Role {

        // no constructor
        private Role() {
            return;
        }

        // examples only, discuss which roles should be defined, and
        // then
        // code these to match

        /** Default Role */
        public static final String DEFAULT = "0";
        /** Bulk download */
        public static final String BULK_DOWNLOAD = "1";
        /** Bulk upload */
        public static final String BULK_UPLOAD = "2";

        /** VoIP Application at 24kbps */
        public static final String VOIP_24KBPS = "100";
        /** VoIP Application at 32kbps */
        public static final String VOIP_32KBPS = "101";
        /** VoIP Application at 48kbps */
        public static final String VOIP_48KBPS = "102";
        /** VoIP Application at 64kbps */
        public static final String VOIP_64KBPS = "103";
        /** VoIP Application at 96kbps */
        public static final String VOIP_96KBPS = "104";
        /** VoIP Application at 128kbps */
        public static final String VOIP_128KBPS = "105";

        /** Video Streaming at 360p */
        public static final String VIDEO_STREAMING_360P = "200";
        /** Video Streaming at 480p */
        public static final String VIDEO_STREAMING_480P = "201";
        /** Video Streaming at 720p */
        public static final String VIDEO_STREAMING_720P = "202";
        /** Video Streaming at 1080p */
        public static final String VIDEO_STREAMING_1080P = "203";

        /** Video Chat Application at 360p */
        public static final String VIDEO_CHAT_360P = "300";
        /** Video Chat Application at 480p */
        public static final String VIDEO_CHAT_480P = "301";
        /** Video Chat Application at 720p */
        public static final String VIDEO_CHAT_720P = "302";
        /** Video Chat Application at 1080p */
        public static final String VIDEO_CHAT_1080P = "303";
    }

    /**
     * Reason codes an application can specify when requesting for a
     * new link.
     * <p>
     * TODO: need better documentation and more/better reasons
     */
    public static final class LinkRequestReason {

        // no constructor
        private LinkRequestReason() {
            return;
        }

        /** This link is working properly */
        public static final int LINK_PROBLEM_NONE = 0;
        /** This link has an unknown issue */
        public static final int LINK_PROBLEM_UNKNOWN = 1;
    }
}
