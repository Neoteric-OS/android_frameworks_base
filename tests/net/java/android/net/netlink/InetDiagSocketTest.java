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

package android.net.netlink;

import static android.net.netlink.StructNlMsgHdr.NLM_F_DUMP;
import static android.net.netlink.StructNlMsgHdr.NLM_F_REQUEST;
import static android.os.Process.INVALID_UID;
import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.AF_INET6;
import static android.system.OsConstants.IPPROTO_TCP;
import static android.system.OsConstants.IPPROTO_UDP;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.netlink.StructNlMsgHdr;
import android.os.Process;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.filters.SmallTest;
import android.support.test.InstrumentationRegistry;
import android.util.Log;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import libcore.util.HexEncoding;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class InetDiagSocketTest {
    private final String TAG = "InetDiagSocketTest";
    private ConnectivityManager mCm;
    private Context mContext;
    private final static int SOCKET_TIMEOUT_MS = 100;

    @Before
    public void setUp() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = instrumentation.getTargetContext();
        mCm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    private void checkConnectionOwnerUidTcp(Socket s, boolean succeed) {
        int expectedUid;
        InetSocketAddress local;
        if (succeed) {
            expectedUid = Process.myUid();
            local = new InetSocketAddress(s.getLocalAddress(), s.getLocalPort());
        } else {
            // Use a port which is unused on Android to avoid potential collisions.
            local = new InetSocketAddress(1);
            expectedUid = INVALID_UID;
        }
        InetSocketAddress remote = new InetSocketAddress(s.getInetAddress(), s.getPort());
        int uid = mCm.getConnectionOwnerUid(IPPROTO_TCP, local, remote);
        assertEquals(expectedUid, uid);
    }

    private void checkConnectionOwnerUidUdp(DatagramSocket s, boolean sourceOnly, boolean succeed)
            throws Exception {
        int expectedUid;
        InetSocketAddress local;
        if (succeed) {
            expectedUid = Process.myUid();
            local = new InetSocketAddress(s.getLocalAddress(), s.getLocalPort());
        } else {
            // Use a port which is unused on Android to avoid potential collisions.
            local = new InetSocketAddress(1);
            expectedUid = INVALID_UID;
        }
        InetSocketAddress remote;
        if (sourceOnly) {
            remote = new InetSocketAddress(0);
        } else {
            remote = new InetSocketAddress(s.getInetAddress(), s.getPort());
        }
        int uid = mCm.getConnectionOwnerUid(IPPROTO_UDP, local, remote);
        assertEquals(expectedUid, uid);
    }

    private void checkGetConnectionOwnerUidUdp(String to, String from, boolean sourceOnly,
                                               boolean succeed)
            throws Exception {
        DatagramSocket s;
        if (from == null) {
            s = new DatagramSocket();
        } else {
            s = new DatagramSocket(new InetSocketAddress(InetAddress.getByName(from), 0));
        }
        s.setSoTimeout(SOCKET_TIMEOUT_MS);
        s.connect(InetAddress.getByName(to), 7);

        checkConnectionOwnerUidUdp(s, sourceOnly, succeed);
        s.close();
    }

    private void checkGetConnectionOwnerUidTcp(String to, String from, boolean succeed)
            throws Exception {
        ServerSocket listen = new ServerSocket(0, 10, InetAddress.getByName("::"));
        Socket client = new Socket();
        if (from != null) {
            InetSocketAddress local = new InetSocketAddress(InetAddress.getByName(from), 0);
            client.bind(local);
        }
        client.connect(new InetSocketAddress(InetAddress.getByName("::"), listen.getLocalPort()),
                SOCKET_TIMEOUT_MS);
        Socket server = null;
        listen.setSoTimeout(SOCKET_TIMEOUT_MS);
        server = listen.accept();
        checkConnectionOwnerUidTcp(client, succeed);
        checkConnectionOwnerUidTcp(server, succeed);
        listen.close();
        client.close();
        server.close();
    }

    public void getConnectionOwnerUidSucceed(boolean succeed) throws Exception {
        checkGetConnectionOwnerUidTcp("::", null, succeed);
        checkGetConnectionOwnerUidTcp("0.0.0.0", null, succeed);
        checkGetConnectionOwnerUidTcp("127.0.0.1", "127.0.0.2", succeed);
        checkGetConnectionOwnerUidUdp("2001:db8:dead:beef::f02", null, false, succeed);
        checkGetConnectionOwnerUidUdp("192.168.2.254", null, false, succeed);
        checkGetConnectionOwnerUidUdp("127.0.0.1", "127.0.0.2", false, succeed);
        checkGetConnectionOwnerUidUdp("127.0.0.1", "127.0.0.2", true, succeed);
    }

    @Test
    public void testGetConnectionOwnerUid() throws Exception {
        getConnectionOwnerUidSucceed(true);
        getConnectionOwnerUidSucceed(false);
    }

    /**
     * TODO Add tests for pure IPv4 sockets created with android.system.Os#socket(AF_INET, ...).
     */

    // Hexadecimal representation of InetDiagReqV2 request.
    private static final String INET_DIAG_REQ_V2_UDP_INET4_HEX =
            // struct nlmsghdr
            "48000000" +     // length = 72
            "1400" +         // type = SOCK_DIAG_BY_FAMILY
            "0103" +         // flags = NLM_F_REQUEST | NLM_F_DUMP
            "00000000" +     // seqno
            "00000000" +     // pid (0 == kernel)
            // struct inet_diag_req_v2
            "02" +           // family = AF_INET
            "11" +           // protcol = IPPROTO_UDP
            "00" +           // idiag_ext
            "00" +           // pad
            "ffffffff" +     // idiag_states
            // inet_diag_sockid
            "a5de" +         // idiag_sport = 42462
            "b971" +         // idiag_dport = 47473
            "0a006402000000000000000000000000" + // idiag_src = 10.0.100.2
            "08080808000000000000000000000000" + // idiag_dst = 8.8.8.8
            "00000000" +     // idiag_if
            "ffffffffffffffff"; // idiag_cookie = INET_DIAG_NOCOOKIE
    private static final byte[] INET_DIAG_REQ_V2_UDP_INET4_BYTES =
            HexEncoding.decode(INET_DIAG_REQ_V2_UDP_INET4_HEX.toCharArray(), false);

    @Test
    public void testInetDiagReqV2UdpInet4() throws Exception {
        InetSocketAddress local = new InetSocketAddress(InetAddress.getByName("10.0.100.2"),
                42462);
        InetSocketAddress remote = new InetSocketAddress(InetAddress.getByName("8.8.8.8"),
                47473);
        final byte[] msg = InetDiagMessage.InetDiagReqV2(IPPROTO_UDP, local, remote, AF_INET,
                (short) (NLM_F_REQUEST | NLM_F_DUMP));
        assertArrayEquals(INET_DIAG_REQ_V2_UDP_INET4_BYTES, msg);
    }

    // Hexadecimal representation of InetDiagReqV2 request.
    private static final String INET_DIAG_REQ_V2_TCP_INET6_HEX =
            // struct nlmsghdr
            "48000000" +     // length = 72
            "1400" +         // type = SOCK_DIAG_BY_FAMILY
            "0100" +         // flags = NLM_F_REQUEST
            "00000000" +     // seqno
            "00000000" +     // pid (0 == kernel)
            // struct inet_diag_req_v2
            "0a" +           // family = AF_INET6
            "06" +           // protcol = IPPROTO_TCP
            "00" +           // idiag_ext
            "00" +           // pad
            "ffffffff" +     // idiag_states
                // inet_diag_sockid
                "a5de" +         // idiag_sport = 42462
                "b971" +         // idiag_dport = 47473
                "fe8000000000000086c9b2fffe6aed4b" + // idiag_src = fe80::86c9:b2ff:fe6a:ed4b
                "08080808000000000000000000000000" + // idiag_dst = 8.8.8.8
                "00000000" +     // idiag_if
                "ffffffffffffffff"; // idiag_cookie = INET_DIAG_NOCOOKIE
    private static final byte[] INET_DIAG_REQ_V2_TCP_INET6_BYTES =
            HexEncoding.decode(INET_DIAG_REQ_V2_TCP_INET6_HEX.toCharArray(), false);

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x ", b));
        return sb.toString();
    }

    @Test
    public void testInetDiagReqV2TcpInet6() throws Exception {
        InetSocketAddress local = new InetSocketAddress(
                InetAddress.getByName("fe80::86c9:b2ff:fe6a:ed4b"), 42462);
        InetSocketAddress remote = new InetSocketAddress(InetAddress.getByName("8.8.8.8"),
                47473);
        byte[] msg = InetDiagMessage.InetDiagReqV2(IPPROTO_TCP, local, remote, AF_INET6,
                NLM_F_REQUEST);

        assertArrayEquals(INET_DIAG_REQ_V2_TCP_INET6_BYTES, msg);
    }

    // Hexadecimal representation of InetDiagReqV2 request.
    private static final String INET_DIAG_MSG_HEX =
            // struct nlmsghdr
            "58000000" +     // length = 88
            "1400" +         // type = SOCK_DIAG_BY_FAMILY
            "0200" +         // flags = NLM_F_MULTI
            "00000000" +     // seqno
            "f5220000" +     // pid (0 == kernel)
            // struct inet_diag_msg
            "0a" +           // family = AF_INET6
            "01" +           // idiag_state
            "00" +           // idiag_timer
            "00" +           // idiag_retrans
                // inet_diag_sockid
                "a817" +     // idiag_sport = 43031
                "960f" +     // idiag_dport = 38415
                "fe8000000000000086c9b2fffe6aed4b" + // idiag_src = fe80::86c9:b2ff:fe6a:ed4b
                "00000000000000000000ffff08080808" + // idiag_dst = 8.8.8.8
                "00000000" + // idiag_if
                "ffffffffffffffff" + // idiag_cookie = INET_DIAG_NOCOOKIE
            "00000000" +     // idiag_expires
            "00000000" +     // idiag_rqueue
            "00000000" +     // idiag_wqueue
            "a3270000" +     // idiag_uid
            "A57E1900";      // idiag_inode
    private static final byte[] INET_DIAG_MSG_BYTES =
            HexEncoding.decode(INET_DIAG_MSG_HEX.toCharArray(), false);

    @Test
    public void testParseInetDiagResponse() throws Exception {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(INET_DIAG_MSG_BYTES);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        final NetlinkMessage msg = NetlinkMessage.parse(byteBuffer);
        assertNotNull(msg);

        assertTrue(msg instanceof InetDiagMessage);
        final InetDiagMessage inetDiagMsg = (InetDiagMessage) msg;
        assertEquals(10147, inetDiagMsg.mStructInetDiagMsg.idiag_uid);

        final StructNlMsgHdr hdr = inetDiagMsg.getHeader();
        assertNotNull(hdr);
        assertEquals(NetlinkConstants.SOCK_DIAG_BY_FAMILY, hdr.nlmsg_type);
        assertEquals(StructNlMsgHdr.NLM_F_MULTI, hdr.nlmsg_flags);
        assertEquals(0, hdr.nlmsg_seq);
        assertEquals(8949, hdr.nlmsg_pid);
    }
}
