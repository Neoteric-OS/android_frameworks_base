/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server;

import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.EADDRINUSE;
import static android.system.OsConstants.IPPROTO_UDP;
import static android.system.OsConstants.SOCK_DGRAM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.INetd;
import android.net.IpSecManager;
import android.net.IpSecSpiResponse;
import android.net.IpSecTransform;
import android.net.IpSecUdpEncapResponse;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructTimeval;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

/** Unit tests for {@link IpSecService}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class IpSecServiceTest {

    private static final int DROID_SPI = 0xD1201D;
    private static final int TEST_UDP_ENCAP_INVALID_PORT = 100;
    private static final int TEST_UDP_ENCAP_PORT_OUT_RANGE = 100000;

    private static final InetAddress INADDR_ANY;

    static {
        try {
            INADDR_ANY = InetAddress.getByAddress(new byte[] {0, 0, 0, 0});
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    Context mMockContext;
    INetd mMockNetd;
    IpSecService.IpSecServiceConfiguration mMockIpSecSrvConfig;
    IpSecService mIpSecService;

    @Before
    public void setUp() throws Exception {
        mMockContext = mock(Context.class);
        mMockNetd = mock(INetd.class);
        mMockIpSecSrvConfig = mock(IpSecService.IpSecServiceConfiguration.class);
        mIpSecService = new IpSecService(mMockContext, mMockIpSecSrvConfig);

        // Injecting mock netd
        when(mMockIpSecSrvConfig.getNetdInstance()).thenReturn(mMockNetd);
    }

    @Test
    public void testIpSecServiceCreate() throws InterruptedException {
        IpSecService ipSecSrv = IpSecService.create(mMockContext);
        assertNotNull(ipSecSrv);
    }

    @Test
    public void testReleaseInvalidSecurityParameterIndex() throws Exception {
        try {
            mIpSecService.releaseSecurityParameterIndex(1);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
        }
    }

    /** This function finds an available port */
    int findUnusedPort() throws Exception {
        // Get an available port.
        ServerSocket s = new ServerSocket(0);
        int port = s.getLocalPort();
        s.close();
        return port;
    }

    @Test
    public void testOpenAndCloseUdpEncapsulationSocket() throws Exception {
        int localport = findUnusedPort();

        IpSecUdpEncapResponse udpEncapResp =
                mIpSecService.openUdpEncapsulationSocket(localport, new Binder());
        assertNotNull(udpEncapResp);
        assertEquals(IpSecManager.Status.OK, udpEncapResp.status);
        assertEquals(localport, udpEncapResp.port);

        mIpSecService.closeUdpEncapsulationSocket(udpEncapResp.resourceId);
        udpEncapResp.fileDescriptor.close();
        // TODO: Added check for the resource tracker
    }

    @Test
    public void testOpenUdpEncapsulationSocketAfterClose() throws Exception {
        int localport = findUnusedPort();
        IpSecUdpEncapResponse udpEncapResp =
                mIpSecService.openUdpEncapsulationSocket(localport, new Binder());
        assertNotNull(udpEncapResp);
        assertEquals(IpSecManager.Status.OK, udpEncapResp.status);
        assertEquals(localport, udpEncapResp.port);

        mIpSecService.closeUdpEncapsulationSocket(udpEncapResp.resourceId);
        udpEncapResp.fileDescriptor.close();

        /** Check if localport is available. */
        FileDescriptor newSocket = Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
        Os.bind(newSocket, INADDR_ANY, localport);
        Os.close(newSocket);
    }

    /**
     * This function checks if the IpSecService holds the reserved port. If
     * closeUdpEncapsulationSocket is not called, the socket cleanup should not be complete.
     */
    @Test
    public void testUdpEncapPortNotReleased() throws Exception {
        int localport = findUnusedPort();
        IpSecUdpEncapResponse udpEncapResp =
                mIpSecService.openUdpEncapsulationSocket(localport, new Binder());
        assertNotNull(udpEncapResp);
        assertEquals(IpSecManager.Status.OK, udpEncapResp.status);
        assertEquals(localport, udpEncapResp.port);

        udpEncapResp.fileDescriptor.close();

        FileDescriptor newSocket = Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
        try {
            Os.bind(newSocket, INADDR_ANY, localport);
            fail("ErrnoException not thrown");
        } catch (ErrnoException e) {
            assertEquals(EADDRINUSE, e.errno);
        }
        mIpSecService.closeUdpEncapsulationSocket(udpEncapResp.resourceId);
    }

    @Test
    public void testOpenUdpEncapsulationSocketOnRandomPort() throws Exception {
        IpSecUdpEncapResponse udpEncapResp =
                mIpSecService.openUdpEncapsulationSocket(0, new Binder());
        assertNotNull(udpEncapResp);
        assertEquals(IpSecManager.Status.OK, udpEncapResp.status);
        assertNotEquals(0, udpEncapResp.port);

        mIpSecService.closeUdpEncapsulationSocket(udpEncapResp.resourceId);
        udpEncapResp.fileDescriptor.close();
    }

    @Test
    public void testOpenUdpEncapsulationSocketPortRange() throws Exception {
        try {
            mIpSecService.openUdpEncapsulationSocket(TEST_UDP_ENCAP_INVALID_PORT, new Binder());
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
        }

        try {
            mIpSecService.openUdpEncapsulationSocket(TEST_UDP_ENCAP_PORT_OUT_RANGE, new Binder());
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testOpenUdpEncapsulationSocketTwice() throws Exception {
        int localport = findUnusedPort();

        IpSecUdpEncapResponse udpEncapResp =
                mIpSecService.openUdpEncapsulationSocket(localport, new Binder());
        assertNotNull(udpEncapResp);
        assertEquals(IpSecManager.Status.OK, udpEncapResp.status);
        assertEquals(localport, udpEncapResp.port);

        mIpSecService.openUdpEncapsulationSocket(localport, new Binder());
        IpSecUdpEncapResponse testUdpEncapResp =
                mIpSecService.openUdpEncapsulationSocket(localport, new Binder());
        assertEquals(IpSecManager.Status.RESOURCE_UNAVAILABLE, testUdpEncapResp.status);

        mIpSecService.closeUdpEncapsulationSocket(udpEncapResp.resourceId);
        udpEncapResp.fileDescriptor.close();
    }

    @Test
    public void testCloseInvalidUdpEncapsulationSocket() throws Exception {
        try {
            mIpSecService.closeUdpEncapsulationSocket(1);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testDeleteInvalidTransportModeTransform() throws Exception {
        try {
            mIpSecService.deleteTransportModeTransform(1);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testRemoveTransportModeTransform() throws Exception {
        ParcelFileDescriptor pfd = ParcelFileDescriptor.fromSocket(new Socket());
        mIpSecService.removeTransportModeTransform(pfd, 1);

        verify(mMockNetd).ipSecRemoveTransportModeTransform(pfd.getFileDescriptor());
    }

    @Test
    public void testValidateIpAddresses() throws Exception {
        String[] invalidAddresses =
                new String[] {"www.google.com", "::", "2001::/64", "0.0.0.0", ""};
        for (String address : invalidAddresses) {
            try {
                IpSecSpiResponse spiResp =
                        mIpSecService.reserveSecurityParameterIndex(
                                IpSecTransform.DIRECTION_OUT, address, DROID_SPI, new Binder());
                fail("Invalid address was passed through IpSecService validation: " + address);
            } catch (IllegalArgumentException e) {
            } catch (Exception e) {
                fail(
                        "Invalid InetAddress was not caught in validation: "
                                + address
                                + ", Exception: "
                                + e);
            }
        }
    }

    @Test
    public void testSetSockStatsUid() throws Exception {
        int testUid = 12345;

        QtaguidStats testUidBefore = getQtaguidStats(Os.getuid());
        QtaguidStats otherUidBefore = getQtaguidStats(testUid);

        FileDescriptor recvFd = Os.socket(AF_INET, SOCK_DGRAM, 0);
        Os.bind(recvFd, InetAddress.getByAddress(new byte[] {0, 0, 0, 0}), 0);
        mIpSecService.setSockStatsUid(recvFd, testUid);
        StructTimeval tv = StructTimeval.fromMillis(20);
        Os.setsockoptTimeval(recvFd, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO, tv);

        InetSocketAddress to = ((InetSocketAddress) Os.getsockname(recvFd));
        FileDescriptor sendFd = Os.socket(AF_INET, SOCK_DGRAM, 0);
        mIpSecService.setSockStatsUid(sendFd, testUid);
        byte[] msg = ("Hello, I'm going to a socket address: " + to.toString()).getBytes("UTF-8");
        int len = msg.length;

        assertEquals(len, Os.sendto(sendFd, msg, 0, len, 0, to));
        byte[] received = new byte[msg.length + 42];
        InetSocketAddress from = new InetSocketAddress();
        assertEquals(len, Os.recvfrom(recvFd, received, 0, received.length, 0, from));

        // It's too fast to check qtaguid stats.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        QtaguidStats testUidAfter = getQtaguidStats(Os.getuid());
        QtaguidStats otherUidAfter = getQtaguidStats(testUid);

        // Check that no packets were attributed to the test-runner UID
        assertTrue(testUidBefore.isEqual(testUidAfter));

        // Check that packets were attributed to the testUid (12345)
        assertEquals(otherUidBefore.txBytes + 80, otherUidAfter.txBytes);
        assertEquals(otherUidBefore.rxBytes + 80, otherUidAfter.rxBytes);
        assertEquals(otherUidBefore.txPackets + 1, otherUidAfter.txPackets);
        assertEquals(otherUidBefore.rxPackets + 1, otherUidAfter.rxPackets);
    }

    @Test
    public void testIpSecIp4UdpEncapExemptionTriggers() throws Exception {
        int localport = findUnusedPort();
        IpSecUdpEncapResponse udpEncapResp =
                mIpSecService.openUdpEncapsulationSocket(localport, new Binder());

        ArgumentCaptor<FileDescriptor> argumentCaptor =
                ArgumentCaptor.forClass(FileDescriptor.class);

        verify(mMockNetd).ipSecAddIp4UdpEncapExemption(argumentCaptor.capture());

        // Check that it's using the same socket
        assertEquals(
                Os.getsockname(argumentCaptor.getValue()).toString(),
                Os.getsockname(udpEncapResp.fileDescriptor.getFileDescriptor()).toString());

        mIpSecService.closeUdpEncapsulationSocket(udpEncapResp.resourceId);
        verify(mMockNetd).ipSecRemoveIp4UdpEncapExemption(eq(udpEncapResp.port));
    }

    @Test
    public void testOpenUdpEncapsulationSocketCallsSetSocketOwner() throws Exception {
        int localport = findUnusedPort();
        IpSecUdpEncapResponse udpEncapResp =
                mIpSecService.openUdpEncapsulationSocket(localport, new Binder());

        verify(mMockNetd).ipSecSetSocketOwner(anyObject(), eq(Os.getuid()));

        mIpSecService.closeUdpEncapsulationSocket(udpEncapResp.resourceId);
    }

    private static class QtaguidStats {
        public final int uid;
        public final int txBytes;
        public final int rxBytes;
        public final int txPackets;
        public final int rxPackets;

        public QtaguidStats(
                int uid, String txBytes, String rxBytes, String txPackets, String rxPackets) {
            this.uid = uid;
            this.txBytes = Integer.valueOf(txBytes);
            this.rxBytes = Integer.valueOf(rxBytes);
            this.txPackets = Integer.valueOf(txPackets);
            this.rxPackets = Integer.valueOf(rxPackets);
        }

        public boolean isEqual(QtaguidStats other) {
            return this.txBytes == other.txBytes
                    && this.rxBytes == other.rxBytes
                    && this.txPackets == other.txPackets
                    && this.rxPackets == other.rxPackets;
        }

        public String toString() {
            return String.format(
                    "QtaguidStats - UID %d: TX: %d pkts, %d bytes; RX: %d pkts, %d bytes;",
                    uid, txPackets, txBytes, rxPackets, rxBytes);
        }
    }

    private static final HashMap<String, Integer> headers = new HashMap<>();

    /*
     * Has to be done through the raw file because we are looking for stats for other UIDs as well.
     */
    private QtaguidStats getQtaguidStats(int uid) throws Exception {
        BufferedReader in = new BufferedReader(new FileReader("/proc/net/xt_qtaguid/stats"));
        String line = in.readLine();
        int lineCounter = 0;

        try {
            while (line != null) {
                final String[] fields = line.split(" ");

                if (headers.isEmpty()) {
                    int i = 0;
                    for (String key : fields) {
                        headers.put(key, i++);
                    }
                } else {
                    if (lineCounter != 0
                            && Integer.valueOf(fields[headers.get("uid_tag_int")]) == uid
                            && Integer.valueOf(fields[headers.get("cnt_set")]) == 0) {
                        return new QtaguidStats(
                                uid,
                                fields[headers.get("tx_bytes")],
                                fields[headers.get("rx_bytes")],
                                fields[headers.get("tx_packets")],
                                fields[headers.get("rx_packets")]);
                    }
                }

                line = in.readLine();
                lineCounter++;
            }
        } finally {
            in.close();
        }

        return new QtaguidStats(uid, "0", "0", "0", "0");
    }
}
