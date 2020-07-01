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

package android.net.dnsproxy

import android.net.DnsResolver.FLAG_NO_RETRY
import android.net.dnsproxy.DnsProxyServer.addPacketLength
import android.net.dnsproxy.DnsProxyServer.setupFailResponse
import android.system.OsConstants.EBADF

import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue

import org.mockito.AdditionalMatchers.aryEq
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import android.net.DnsResolver
import android.net.Network
import android.net.dnsproxy.DnsProxyServer.ResponderDependencies
import android.net.dnsproxy.DnsProxyServer.ServerDependencies
import android.net.util.SharedLog
import android.os.HandlerThread
import android.os.MessageQueue
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.testing.TestableLooper.RunWithLooper

import androidx.test.filters.SmallTest

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import java.io.FileDescriptor
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Arrays
import java.util.concurrent.Executor

private val TEST_IFACE = "testIface"
private val TEST_CLIENT_ADDR = InetAddress.parseNumericAddress("192.168.0.42")
private val TEST_CLIENT_PORT = 572
private val RCODE_FORMERR = 1
private val QUERY_FLAGS = FLAG_NO_RETRY

@RunWith(AndroidTestingRunner::class)
@SmallTest
@RunWithLooper
class DnsProxyServerTest {
    private val mockResponderDeps = mock(ResponderDependencies::class.java)
    private val mockQueue = mock(MessageQueue::class.java)
    private val mockNetwork = mock(Network::class.java)
    private val mockDnsResolver = mock(DnsResolver::class.java)
    private val mockLog = mock(SharedLog::class.java)
    private var mockInetSocketAddress = mock(InetSocketAddress::class.java)
    private val mockTestExecutor = mock(Executor::class.java)
    private var mockHandlerThread = mock(HandlerThread::class.java)

    private var mTestTCPSocket = spy(Os.open("/dev/null", 0, OsConstants.O_RDONLY))
    private var mTestUDPSocket = spy(Os.open("/dev/null", 0, OsConstants.O_RDONLY))
    private lateinit var mLooper: TestableLooper
    private lateinit var mServer: DnsProxyServer

    private val mockServerDeps = mock(ServerDependencies::class.java).apply {
        doReturn(mTestUDPSocket).`when`(this).makeUDPSocket()
        doReturn(mTestTCPSocket).`when`(this).makeTCPSocket()
        doReturn(mockHandlerThread).`when`(this).makeHandlerThread(any())
    }

    // TODO: Extend DnsPacket and use it to create test packets.
    private val queryPacket = byteArrayOf(
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
            0x00, 0x01) /* Class */

    private val invalidPacket = byteArrayOf(
            /* Header */
            0x55, 0x66, /* Transaction ID */
            0x81.toByte(), 0x80.toByte(), /* Flags */
            0x00, 0x01, /* Questions */
            0x00, 0x00, /* Answer RRs */
            0x00, 0x00, /* Authority RRs */
            0x00, 0x00, /* Additional RRs */
            /* Queries */
            0x03, 0x77, 0x77, 0x77, 0x06, 0x67, 0x6F, 0x6F, 0x67, 0x6c, 0x65,
            0x03, 0x63, 0x6f, 0x6d, 0x00, /* Name */
            0x00, 0x01, /* Type */
            0x00, 0x01) /* Class */

    private val answerPacket = byteArrayOf(
            /* Header */
            0x55, 0x66, /* Transaction ID */
            0x81.toByte(), 0x80.toByte(), /* Flags */
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
            0xc0.toByte(), 0x0c, /* Name */
            0x00, 0x01, /* Type */
            0x00, 0x01, /* Class */
            0x00, 0x00, 0x01, 0x2b, /* TTL */
            0x00, 0x04, /* Data length */
            0xac.toByte(), 0xd9.toByte(), 0xa1.toByte(), 0x84.toByte()) /* Address */

    // Packet size < DNS header
    private val malformedPacket = byteArrayOf(0x35, 0x36, 0x21, 0x50, 0x20, 0x01, 0x11, 0x05)

    @Before
    @Throws(Exception::class)
    fun setUp() {
        mLooper = TestableLooper.get(this)
        val looper = spy(mLooper.looper)
        doReturn(mockQueue).`when`(looper).queue
        doReturn(looper).`when`(mockHandlerThread).looper

        mServer = DnsProxyServer(TEST_IFACE,
                SharedLog(DnsProxyServerTest::class.java.simpleName), mockServerDeps)
        mServer.start(mockNetwork)
        mLooper.processAllMessages()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        mServer.stop()
        mLooper.processMessages(1)
        verify(mockQueue, times(1)).removeOnFileDescriptorEventListener(eq(mTestUDPSocket))
        verify(mockQueue, times(1)).removeOnFileDescriptorEventListener(eq(mTestTCPSocket))
        assertTrue(!mTestUDPSocket.valid())
        assertTrue(!mTestTCPSocket.valid())
        verify(mockHandlerThread, times(1)).quitSafely()
    }

    @Test
    fun testStart() {
        verify(mockServerDeps, times(1)).listenDnsUDP(eq(mTestUDPSocket), any())
        verify(mockServerDeps, times(1)).listenDnsTCP(eq(mTestTCPSocket), any())
        verify(mockQueue, times(1))
                .addOnFileDescriptorEventListener(eq(mTestUDPSocket), anyInt(), any())
        verify(mockQueue, times(1))
                .addOnFileDescriptorEventListener(eq(mTestTCPSocket), anyInt(), any())
    }

    @Test
    fun testUpdateUpstream() {
        assertTrue(mServer.isUpstreamAvailable)
        mServer.updateUpstream(null)
        mLooper.processMessages(1)
        assertFalse(mServer.isUpstreamAvailable)
        mServer.updateUpstream(mockNetwork)
        mLooper.processMessages(1)
        assertTrue(mServer.isUpstreamAvailable)
    }

    @Test
    fun testRequestResponder() {
        val request = spy(object : DnsProxyServer.RequestResponder(
                mockNetwork, mTestUDPSocket, mockLog,
                mockResponderDeps, mockDnsResolver, mockTestExecutor) {
            internal override fun transmitAnswer(
                fd: FileDescriptor?,
                buf: ByteArray?,
                dstAddr: InetAddress?,
                dstPort: Int
            ) {
            }

            @Throws(IOException::class, ErrnoException::class)
            internal override fun readPacket(fd: FileDescriptor?): ByteArray {
                mDstAddr = mockInetSocketAddress
                return queryPacket
            }
                })

        // handleRequest, exception case
        Mockito.`when`(request.readPacket(any())).thenThrow(ErrnoException("Test Errno", EBADF))
        request.handleRequest()
        verify(request, never()).processDnsPacket(any(), any(), any(), any(), anyInt())
        verify(request, times(1)).cleanup(eq(mTestUDPSocket))

        // handleRequest, normal case
        reset(request)
        request.handleRequest()
        verify(request, never()).processDnsPacket(eq(mockNetwork), eq(mTestUDPSocket),
                aryEq(queryPacket), eq(TEST_CLIENT_ADDR), eq(TEST_CLIENT_PORT))
        verify(mockDnsResolver, times(1))
                .rawQuery(eq(mockNetwork), aryEq(queryPacket), eq(QUERY_FLAGS),
                        eq(mockTestExecutor), any(), any())

        // Process NormalQuery
        reset(request)
        reset(mockDnsResolver)
        var packetBuf = queryPacket
        request.processDnsPacket(mockNetwork, mTestUDPSocket, packetBuf,
                TEST_CLIENT_ADDR, TEST_CLIENT_PORT)
        verify(mockDnsResolver, times(1))
                .rawQuery(eq(mockNetwork), aryEq(packetBuf), eq(QUERY_FLAGS), eq(mockTestExecutor),
                        any(), any())
        reset(mockDnsResolver)
        // Process InvalidQuery
        packetBuf = invalidPacket
        request.processDnsPacket(mockNetwork, mTestUDPSocket, packetBuf,
                TEST_CLIENT_ADDR, TEST_CLIENT_PORT)
        verify(mockDnsResolver, never()).rawQuery(any(), any(), anyInt(), any(), any(), any())
        verify(request, times(1))
                .transmitAnswer(eq(mTestUDPSocket),
                        aryEq(setupFailResponse(packetBuf, RCODE_FORMERR)),
                        eq(TEST_CLIENT_ADDR), eq(TEST_CLIENT_PORT))

        // Process MalformedPacket
        packetBuf = malformedPacket
        request.processDnsPacket(mockNetwork, mTestUDPSocket, packetBuf,
                TEST_CLIENT_ADDR, TEST_CLIENT_PORT)
        verify(mockDnsResolver, never()).rawQuery(any(), any(), anyInt(), any(), any(), any())
        verify(request, times(1)).cleanup(eq(mTestUDPSocket))
    }

    @Test
    fun testUDPRequestResponder() {
        val answerBuf = answerPacket
        doAnswer { inv ->
            val buf = inv.getArgument<ByteArray>(1)
            System.arraycopy(buf, 0, answerBuf, 0, answerBuf.size)
            answerBuf.size
        }.`when`(mockResponderDeps).recvfrom(any(), any(),
                eq(DnsProxyServer.UDPRequestResponder.UDP_BUFFER_LENGTH), any())
        val request = DnsProxyServer.UDPRequestResponder(
                mockNetwork, mTestUDPSocket, mockLog,
                mockResponderDeps, mockDnsResolver, mockTestExecutor)
        assertTrue(request is DnsProxyServer.RequestResponder)

        request.transmitAnswer(mTestUDPSocket, answerBuf, TEST_CLIENT_ADDR, TEST_CLIENT_PORT)
        verify(mockResponderDeps, times(1))
                .sendPacket(eq(mTestUDPSocket), aryEq(answerBuf), eq(TEST_CLIENT_ADDR),
                        eq(TEST_CLIENT_PORT))

        val buf = request.readPacket(mTestUDPSocket)
        verify(mockResponderDeps, times(1))
                .recvfrom(eq(mTestUDPSocket), any(),
                        eq(DnsProxyServer.UDPRequestResponder.UDP_BUFFER_LENGTH),
                        any())
        assertTrue(Arrays.equals(buf, answerBuf))
    }

    @Test
    fun testTCPRequestResponder() {
        val answerBuf = answerPacket
        // Return length of packet
        doAnswer { inv ->
            val buf = inv.getArgument<ByteArray>(1)
            buf[0] = (answerBuf.size and 0xff00).toByte()
            buf[1] = (answerBuf.size and 0xff).toByte()
            2
        }.`when`(mockResponderDeps).read(any(), any(), eq(0), eq(2))
        // Return packet
        doAnswer { inv ->
            val buf = inv.getArgument<ByteArray>(1)
            System.arraycopy(buf, 0, answerBuf, 0, answerBuf.size)
            answerBuf.size
        }.`when`(mockResponderDeps).read(any(), any(), eq(0), eq(answerBuf.size))

        val request = spy(DnsProxyServer.TCPRequestResponder(
                mockNetwork, mTestTCPSocket,
                mockInetSocketAddress, mockLog, mockResponderDeps,
                mockDnsResolver, mockTestExecutor))
        assertTrue(request is DnsProxyServer.RequestResponder)

        request.transmitAnswer(mTestTCPSocket, answerBuf, TEST_CLIENT_ADDR, TEST_CLIENT_PORT)
        verify(mockResponderDeps, times(1))
                .sendPacket(eq(mTestTCPSocket), aryEq(addPacketLength(answerBuf)),
                        eq(TEST_CLIENT_ADDR),
                        eq(TEST_CLIENT_PORT))
        verify(request, times(1)).cleanup(eq(mTestTCPSocket))

        val buf = request.readPacket(mTestTCPSocket)
        verify(request, times(1)).setSocketTimeout(eq(mTestTCPSocket))
        verify(mockResponderDeps, times(1)).read(eq(mTestTCPSocket), any(), eq(0), eq(2))
        verify(mockResponderDeps, times(1))
                .read(eq(mTestTCPSocket), any(), eq(0), eq(answerBuf.size))
        assertTrue(Arrays.equals(buf, answerBuf))
    }
}
