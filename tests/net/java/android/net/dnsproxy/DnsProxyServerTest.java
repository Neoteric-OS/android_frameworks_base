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

import static android.system.OsConstants.IPPROTO_TCP;
import static android.system.OsConstants.IPPROTO_UDP;

import static junit.framework.Assert.assertTrue;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Network;
import android.net.dnsproxy.DnsProxyServer.Dependencies;
import android.net.dnsproxy.DnsProxyServer.TCPListener;
import android.net.dnsproxy.DnsProxyServer.UDPListener;
import android.net.util.SharedLog;
import android.os.test.TestLooper;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.system.ErrnoException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DnsProxyServerTest {
    private static final String PROP_DEXMAKER_SHARE_CLASSLOADER = "dexmaker.share_classloader";
    private static final String TEST_IFACE = "testiface";
    private static final InetAddress TEST_CLIENT_ADDR = InetAddress.parseNumericAddress(
            "192.168.0.42");
    private static final int TEST_CLIENT_PORT = 572;
    private static final int TEST_NETID = 337;
    private static final int RCODE_FormErr = 1;
    private static final int RCODE_ServFail = 2;

    @NonNull
    @Mock
    private Dependencies mDeps;
    @NonNull
    @Mock
    private TCPListener mTCPListener;
    @NonNull
    @Mock
    private UDPListener mUDPListener;
    @NonNull
    private FileDescriptor mTestSocket;

    @NonNull
    @Captor
    private ArgumentCaptor<byte[]> mAnswerBuffCaptor;
    @NonNull
    @Captor
    private ArgumentCaptor<byte[]> mQueryBuffCaptor;
    @NonNull
    @Captor
    private ArgumentCaptor<InetAddress> mDstAddrCaptor;
    @NonNull
    @Captor
    private ArgumentCaptor<Integer> mDstPortCaptor;
    @NonNull
    @Captor
    private ArgumentCaptor<Integer> mProtoTypeCaptor;
    @NonNull
    @Captor
    private ArgumentCaptor<FileDescriptor> mFDCaptor;


    @NonNull
    private TestLooper mLooper;
    @NonNull
    private DnsProxyServer mServer;

    @Nullable
    private String mPrevShareClassloaderProp;

    @Before
    public void setUp() throws Exception {
        // Allow mocking package-private classes
        mPrevShareClassloaderProp = System.getProperty(PROP_DEXMAKER_SHARE_CLASSLOADER);
        System.setProperty(PROP_DEXMAKER_SHARE_CLASSLOADER, "true");

        MockitoAnnotations.initMocks(this);

        final FileOutputStream os = new FileOutputStream(new File("/dev/null"));
        mTestSocket = os.getFD();

        when(mDeps.makeUDPListener()).thenReturn(mUDPListener);
        when(mDeps.makeTCPListener()).thenReturn(mTCPListener);
        doNothing().when(mDeps).doQuery(mFDCaptor.capture(), mProtoTypeCaptor.capture(),
                mQueryBuffCaptor.capture(), mDstAddrCaptor.capture(), mDstPortCaptor.capture());
        doNothing().when(mDeps)
                .sendPacket(mFDCaptor.capture(), mProtoTypeCaptor.capture(),
                        mAnswerBuffCaptor.capture(), mDstAddrCaptor.capture(),
                        mDstPortCaptor.capture());

        when(mDeps.makeTCPListener()).thenReturn(mTCPListener);
        when(mDeps.makeUDPListener()).thenReturn(mUDPListener);

        mLooper = new TestLooper();
        mServer = new DnsProxyServer(mLooper.getLooper(), TEST_IFACE,
                new SharedLog(DnsProxyServerTest.class.getSimpleName()), mDeps);

        mServer.startWithNetwork(new Network(TEST_NETID));
        mLooper.dispatchAll();
    }

    @After
    public void tearDown() throws Exception {
        mServer.stop();
        mLooper.dispatchAll();
        System.setProperty(PROP_DEXMAKER_SHARE_CLASSLOADER,
                (mPrevShareClassloaderProp == null ? "" : mPrevShareClassloaderProp));
    }

    @Test
    public void testStart() throws Exception {
        verify(mTCPListener, times(1)).start();
        verify(mUDPListener, times(1)).start();
    }

    @Test
    public void testStop() throws Exception {
        mServer.stop();
        mLooper.dispatchAll();
        verify(mTCPListener, times(1)).stop();
        verify(mUDPListener, times(1)).stop();
    }

    @Test
    public void testProcessPacketNormaolQuery() throws Exception {
        // UDP
        byte[] packetBuf = getQueryPacket();

        mServer.processPacket(mTestSocket, IPPROTO_UDP, packetBuf, packetBuf.length,
                TEST_CLIENT_ADDR, TEST_CLIENT_PORT);
        verify(mDeps, times(1))
                .doQuery(eq(mTestSocket), eq(IPPROTO_UDP), aryEq(packetBuf),
                        eq(TEST_CLIENT_ADDR), eq(TEST_CLIENT_PORT));

        byte[] answerBuf = getAnswerPacket();

        mServer.transmitAnswer(mTestSocket, IPPROTO_UDP, answerBuf,
                TEST_CLIENT_ADDR, TEST_CLIENT_PORT);
        verify(mDeps, times(1))
                .sendPacket(eq(mTestSocket), eq(IPPROTO_UDP), aryEq(answerBuf),
                        eq(TEST_CLIENT_ADDR), eq(TEST_CLIENT_PORT));

        // TCP
        packetBuf = getQueryPacket();

        mServer.processPacket(mTestSocket, IPPROTO_TCP, packetBuf, packetBuf.length,
                TEST_CLIENT_ADDR, TEST_CLIENT_PORT);
        verify(mDeps, times(1))
                .doQuery(eq(mTestSocket), eq(IPPROTO_TCP), aryEq(packetBuf),
                        eq(TEST_CLIENT_ADDR), eq(TEST_CLIENT_PORT));

        answerBuf = getAnswerPacket();

        mServer.transmitAnswer(mTestSocket, IPPROTO_TCP, answerBuf,
                TEST_CLIENT_ADDR, TEST_CLIENT_PORT);
        verify(mDeps, times(1))
                .sendPacket(eq(mTestSocket), eq(IPPROTO_TCP), aryEq(addPacketLength(answerBuf)),
                        eq(TEST_CLIENT_ADDR), eq(TEST_CLIENT_PORT));
    }

    @Test
    public void testProcessPacketInvalidQuery() throws Exception {
        // UDP
        byte[] packetBuf = getInvalidPacket();
        mServer.processPacket(mTestSocket, IPPROTO_UDP, packetBuf, packetBuf.length,
                TEST_CLIENT_ADDR, TEST_CLIENT_PORT);

        verify(mDeps, never())
                .doQuery(any(), anyInt(), any(byte[].class), any(), anyInt());
        verify(mDeps, times(1))
                .sendPacket(eq(mTestSocket), eq(IPPROTO_UDP),
                        aryEq(setupFailResponse(packetBuf, RCODE_FormErr)),
                        eq(TEST_CLIENT_ADDR), eq(TEST_CLIENT_PORT));

        // TCP
        packetBuf = getInvalidPacket();
        mServer.processPacket(mTestSocket, IPPROTO_TCP, packetBuf, packetBuf.length,
                TEST_CLIENT_ADDR, TEST_CLIENT_PORT);

        verify(mDeps, never())
                .doQuery(any(), anyInt(), any(byte[].class), any(), anyInt());
        verify(mDeps, times(1))
                .sendPacket(eq(mTestSocket), eq(IPPROTO_TCP),
                        aryEq(addPacketLength(setupFailResponse(packetBuf, RCODE_FormErr))),
                        eq(TEST_CLIENT_ADDR), eq(TEST_CLIENT_PORT));
    }

    @Test
    public void testProcessMalformedPacket() throws Exception {
        // UDP
        byte[] packetBuf = getMalformedPacket();

        mServer.processPacket(mTestSocket, IPPROTO_UDP, packetBuf, packetBuf.length,
                TEST_CLIENT_ADDR, TEST_CLIENT_PORT);

        verify(mDeps, never()).doQuery(
                any(), anyInt(), any(byte[].class), any(), anyInt());
        verify(mDeps, never()).sendPacket(
                any(), anyInt(), any(byte[].class), any(), anyInt());

        // TCP
        mServer.processPacket(mTestSocket, IPPROTO_TCP, packetBuf, packetBuf.length,
                TEST_CLIENT_ADDR, TEST_CLIENT_PORT);

        verify(mDeps, never()).doQuery(
                any(), anyInt(), any(byte[].class), any(), anyInt());
        verify(mDeps, never()).sendPacket(
                any(), anyInt(), any(byte[].class), any(), anyInt());
    }

    @Test
    public void testProcessPacketHandleQueryFail() throws Exception {
        doAnswer(inv -> {
            throw new ErrnoException("Test Errno", 100 /* ENETDOWN */);
        }).when(mDeps).doQuery(any(), anyInt(), any(byte[].class), any(), anyInt());

        // UDP
        byte[] packetBuf = getQueryPacket();
        mServer.processPacket(mTestSocket, IPPROTO_UDP, packetBuf, packetBuf.length,
                TEST_CLIENT_ADDR, TEST_CLIENT_PORT);

        verify(mDeps, times(1))
                .doQuery(any(), anyInt(), any(byte[].class), any(), anyInt());
        verify(mDeps, times(1))
                .sendPacket(eq(mTestSocket), eq(IPPROTO_UDP),
                        aryEq(setupFailResponse(packetBuf, RCODE_ServFail)),
                        eq(TEST_CLIENT_ADDR), eq(TEST_CLIENT_PORT));

        // TCP
        packetBuf = getQueryPacket();
        mServer.processPacket(mTestSocket, IPPROTO_TCP, packetBuf, packetBuf.length,
                TEST_CLIENT_ADDR, TEST_CLIENT_PORT);

        verify(mDeps, times(2))
                .doQuery(any(), anyInt(), any(byte[].class), any(), anyInt());
        verify(mDeps, times(1))
                .sendPacket(eq(mTestSocket), eq(IPPROTO_TCP),
                        aryEq(addPacketLength(setupFailResponse(packetBuf, RCODE_ServFail))),
                        eq(TEST_CLIENT_ADDR), eq(TEST_CLIENT_PORT));
    }

    @Test
    public void testTransmitAnswerNormal() throws Exception {
        // UDP
        byte[] packetBuf = getAnswerPacket();
        mServer.transmitAnswer(mTestSocket, IPPROTO_UDP, packetBuf,
                TEST_CLIENT_ADDR, TEST_CLIENT_PORT);

        verify(mDeps, never())
                .doQuery(any(), anyInt(), any(byte[].class), any(), anyInt());
        verify(mDeps, times(1))
                .sendPacket(eq(mTestSocket), eq(IPPROTO_UDP), aryEq(packetBuf),
                        eq(TEST_CLIENT_ADDR), eq(TEST_CLIENT_PORT));

        // TCP
        packetBuf = getAnswerPacket();
        mServer.transmitAnswer(mTestSocket, IPPROTO_TCP, packetBuf,
                TEST_CLIENT_ADDR, TEST_CLIENT_PORT);

        verify(mDeps, never())
                .doQuery(any(), anyInt(), any(byte[].class), any(), anyInt());
        verify(mDeps, times(1))
                .sendPacket(eq(mTestSocket), eq(IPPROTO_TCP),
                        aryEq(addPacketLength(packetBuf)), eq(TEST_CLIENT_ADDR),
                        eq(TEST_CLIENT_PORT));
    }

    @Test
    public void testTransmitAnswerHandleSendFail() throws Exception {
        doAnswer(inv -> {
            throw new ErrnoException("Test Errno", 100 /* ENETDOWN */);
        }).when(mDeps).sendPacket(any(), anyInt(), any(byte[].class), any(), anyInt());

        // UDP
        byte[] packetBuf = getAnswerPacket();
        mServer.transmitAnswer(mTestSocket, IPPROTO_UDP, packetBuf,
                TEST_CLIENT_ADDR, TEST_CLIENT_PORT);

        verify(mDeps, never())
                .doQuery(any(), anyInt(), any(byte[].class), any(), anyInt());
        verify(mDeps, times(1))
                .sendPacket(eq(mTestSocket), eq(IPPROTO_UDP),
                        aryEq(setupFailResponse(packetBuf, RCODE_ServFail)),
                        eq(TEST_CLIENT_ADDR), eq(TEST_CLIENT_PORT));

        // TCP
        packetBuf = getAnswerPacket();
        mServer.transmitAnswer(mTestSocket, IPPROTO_TCP, packetBuf,
                TEST_CLIENT_ADDR, TEST_CLIENT_PORT);

        verify(mDeps, never())
                .doQuery(any(), anyInt(), any(byte[].class), any(), anyInt());
        verify(mDeps, times(1))
                .sendPacket(eq(mTestSocket), eq(IPPROTO_TCP),
                        aryEq(addPacketLength(packetBuf)),
                        eq(TEST_CLIENT_ADDR), eq(TEST_CLIENT_PORT));

    }

    // TODO: Extend DnsPacket and use it to create test packets.
    private byte[] getQueryPacket() {
        return new byte[]{
                /* Header */
                0x55, 0x66, /* Transaction ID */
                0x10, 0x00, /* Flags */
                0x00, 0x01, /* Questions */
                0x00, 0x00, /* Answer RRs */
                0x00, 0x00, /* Authority RRs */
                0x00, 0x00, /* Additional RRs */
                /* Queries */
                0x03, 0x77, 0x77, 0x77, 0x06, 0x67, 0x6F, 0x6F, 0x67, 0x6c, 0x65,
                0x03, 0x63, 0x6f, 0x6d, 0x00, /* Name */
                0x00, 0x01, /* Type */
                0x00, 0x01, /* Class */
        };
    }

    private byte[] getMalformedPacket() {
        // Packet size < DNS header
        return new byte[]{
                0x35, 0x36,
                0x21, 0x50,
                0x20, 0x01,
                0x11, 0x05,
        };
    }

    private byte[] getInvalidPacket() {
        return new byte[]{
                /* Header */
                0x55, 0x66, /* Transaction ID */
                (byte) 0x81, (byte) 0x80, /* Flags */
                0x00, 0x01, /* Questions */
                0x00, 0x00, /* Answer RRs */
                0x00, 0x00, /* Authority RRs */
                0x00, 0x00, /* Additional RRs */
                /* Queries */
                0x03, 0x77, 0x77, 0x77, 0x06, 0x67, 0x6F, 0x6F, 0x67, 0x6c, 0x65,
                0x03, 0x63, 0x6f, 0x6d, 0x00, /* Name */
                0x00, 0x01, /* Type */
                0x00, 0x01, /* Class */
        };
    }

    private byte[] getAnswerPacket() {
        return new byte[]{
                /* Header */
                0x55, 0x66, /* Transaction ID */
                (byte) 0x81, (byte) 0x80, /* Flags */
                0x00, 0x01, /* Questions */
                0x00, 0x01, /* Answer RRs */
                0x00, 0x00, /* Authority RRs */
                0x00, 0x00, /* Additional RRs */
                /* Queries */
                0x03, 0x77, 0x77, 0x77, 0x06, 0x67, 0x6F, 0x6F, 0x67, 0x6c, 0x65,
                0x03, 0x63, 0x6f, 0x6d, 0x00, /* Name */
                0x00, 0x01, /* Type */
                0x00, 0x01, /* Class */
                /* Answers */
                (byte) 0xc0, 0x0c, /* Name */
                0x00, 0x01, /* Type */
                0x00, 0x01, /* Class */
                0x00, 0x00, 0x01, 0x2b, /* TTL */
                0x00, 0x04, /* Data length */
                (byte) 0xac, (byte) 0xd9, (byte) 0xa1, (byte) 0x84 /* Address */
        };
    }

    private static void assertArrayEquals(byte[] expected, byte[] actual) {
        assertTrue("Expected=" + Arrays.toString(expected) + ", actual=" + Arrays.toString(actual),
                Arrays.equals(expected, actual));
    }

    private byte[] addPacketLength(@NonNull byte[] buf) throws Exception {
        final byte[] ansPacket = new byte[buf.length + 2];
        ansPacket[0] = (byte) ((buf.length >> 8) & 0xff);
        ansPacket[1] = (byte) (buf.length & 0xff);
        System.arraycopy(buf, 0, ansPacket, 2, buf.length);
        return ansPacket;
    }

    private byte[] setupFailResponse(@NonNull byte[] querybuf, int rcode) {
        // Change rcode and set OR.
        // TODO: Refine DnsProxyPacket to create answer.
        querybuf[2] = (byte) (querybuf[2] | 0x80);
        querybuf[3] = (byte) rcode;
        return querybuf;
    }
}
