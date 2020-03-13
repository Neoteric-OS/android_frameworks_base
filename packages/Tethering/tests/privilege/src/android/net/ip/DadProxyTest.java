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

import static android.system.OsConstants.IPPROTO_ICMPV6;
import static android.system.OsConstants.IPPROTO_TCP;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;

import android.app.Instrumentation;
import android.content.Context;
import android.net.InetAddresses;
import android.net.MacAddress;
import android.net.TestNetworkInterface;
import android.net.TestNetworkManager;
import android.net.util.InterfaceParams;
import android.net.util.IpUtils;
import android.net.util.TetheringUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.testutils.TapPacketReader;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import android.util.Log;


import android.os.IBinder;
import android.net.INetd;


@RunWith(AndroidJUnit4.class)
@SmallTest
public class DadProxyTest {
    private static final int DATA_BUFFER_LEN = 4096;
    private static final int PACKET_TIMEOUT_MS = 5_000;

    TestNetworkInterface mUpstreamTestIface, mTetheredTestIface;
    private String mUpstreamIfaceName, mTetheredIfaceName;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private TapPacketReader mUpstreamPacketReader, mTetheredPacketReader;
    private FileDescriptor mUpstreamTapFd, mTetheredTapFd;

    static private INetd mNetd;

    @BeforeClass
    public static void setupOnce() {
        System.loadLibrary("tetherutilsjni");

        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        final IBinder netdIBinder =
                (IBinder) inst.getContext().getSystemService(Context.NETD_SERVICE);
        mNetd = INetd.Stub.asInterface(netdIBinder);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mHandlerThread = new HandlerThread(getClass().getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        setupTapInterfaces();

        // Looper must be prepared here since AndroidJUnitRunner runs tests on separate threads.
        if (Looper.myLooper() == null) Looper.prepare();
    }

    @After
    public void tearDown() throws Exception {
        if (mHandlerThread != null) {
            mHandler.post(mUpstreamPacketReader::stop); // Also closes the socket
            mHandler.post(mTetheredPacketReader::stop); // Also closes the socket
            mUpstreamTapFd = null;
            mTetheredTapFd = null;
            mHandlerThread.quitSafely();
        }

        if (mTetheredIfaceName != null) {
            try {
                mNetd.networkRemoveInterface(INetd.LOCAL_NET_ID, mTetheredIfaceName);
            } catch (Exception e) { }
        }
        if (mUpstreamIfaceName != null) {
            try {
                mNetd.networkRemoveInterface(INetd.LOCAL_NET_ID, mUpstreamIfaceName);
            } catch (Exception e) { }
        }
    }

    private TestNetworkInterface setupTapInterface() {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        // Adopt the shell permission identity to create a test TAP interface.
        inst.getUiAutomation().adoptShellPermissionIdentity();

        AtomicReference<TestNetworkInterface> iface = new AtomicReference<>();
        runWithShellPermissionIdentity(() -> {
            final TestNetworkManager tnm = (TestNetworkManager) inst.getContext().getSystemService(
                Context.TEST_NETWORK_SERVICE);
            iface.set(tnm.createTapInterface());
        });

        return iface.get();
    }

    private void setupTapInterfaces() {
        // Create upstream test iface.
        mUpstreamTestIface = setupTapInterface();
        mUpstreamIfaceName = mUpstreamTestIface.getInterfaceName();
        mUpstreamTapFd = mUpstreamTestIface.getFileDescriptor().getFileDescriptor();
        mUpstreamPacketReader = new TapPacketReader(mHandler, mUpstreamTapFd,
                                                    DATA_BUFFER_LEN);
        mHandler.post(mUpstreamPacketReader::start);

        // Create tethered test iface.
        mTetheredTestIface = setupTapInterface();
        mTetheredIfaceName = mTetheredTestIface.getInterfaceName();
        mTetheredTapFd = mTetheredTestIface.getFileDescriptor().getFileDescriptor();
        mTetheredPacketReader = new TapPacketReader(mHandler, mTetheredTapFd,
                                                    DATA_BUFFER_LEN);
        mHandler.post(mTetheredPacketReader::start);
    }

    private static final int IPV6_HEADER_LEN = 40;
    private static final int ETH_HEADER_LEN = 14;
    private static final int ICMPV6_NA_NS_LEN = 24;
    private static final int LL_TARGET_OPTION_LEN = 8;
    private static final int ICMPV6_CHECKSUM_OFFSET = 2;
    public static final int ETHER_TYPE_IPV6 = 0x86dd;

    // TODO: move the IpUtils code to frameworks/lib/net and link it statically.
    private static int checksumFold(int sum) {
        while (sum > 0xffff) {
            sum = (sum >> 16) + (sum & 0xffff);
        }
        return sum;
    }

    private static short checksumAdjust(short checksum, short oldWord, short newWord) {
        checksum = (short) ~checksum;
        int tempSum = checksumFold(uint16(checksum) + uint16(newWord) + 0xffff - uint16(oldWord));
        return (short) ~tempSum;
    }

    public static int uint16(short s) {
        return s & 0xffff;
    }

    private static short icmpv6Checksum(ByteBuffer buf, int ipOffset, int transportOffset,
            int transportLen) {
        // The ICMPv6 checksum is the same as the TCP checksum, except the pseudo-header uses
        // 58 (ICMPv6) instead of 6 (TCP). Calculate the TCP checksum, and then do an incremental
        // checksum adjustment  for the change in the next header byte.
        short checksum = IpUtils.tcpChecksum(buf, ipOffset, transportOffset, transportLen);
        return checksumAdjust(checksum, (short) IPPROTO_TCP, (short) IPPROTO_ICMPV6);
    }

    private static ByteBuffer createIcmpV6Packet(int type) {
        // Refer to buildArpPacket()
        int icmpLen = ICMPV6_NA_NS_LEN +
            (type == NeighborPacketForwarder.ICMPV6_NEIGHBOR_ADVERTISEMENT ?
             LL_TARGET_OPTION_LEN : 0);
        final ByteBuffer buf = ByteBuffer.allocate(icmpLen + IPV6_HEADER_LEN + ETH_HEADER_LEN);

        // Ethernet header.
        final MacAddress srcMac = MacAddress.fromString("33:33:00:00:00:01");
        buf.put(srcMac.toByteArray());
        final MacAddress dstMac = MacAddress.fromString("01:02:03:04:05:06");
        buf.put(dstMac.toByteArray());
        buf.putShort((short) ETHER_TYPE_IPV6);

        // IPv6 header
        byte[] version = {(byte) 0x60, 0x00, 0x00, 0x00};
        buf.put(version);                                           // Version
        buf.putShort((byte) icmpLen);                               // Length
        buf.put((byte) IPPROTO_ICMPV6);                             // Next header
        buf.put((byte) 0xff);                                       // Hop limit

        final byte[] src = InetAddresses.parseNumericAddress("::").getAddress();
        buf.put(src);                                               // Src
        final byte[] dst = InetAddresses.parseNumericAddress("ff02::1:ff66:7788").getAddress();
        buf.put(dst);                                               // Dst

        // ICMPv6 Header
        buf.put((byte) type);                                       // Type
        buf.put((byte) 0x00);                                       // Code
        buf.putShort((short) 0);                                    // Checksum
        buf.putInt(0);                                              // Reserved
        byte[] target =
            InetAddresses.parseNumericAddress("fe80::1122:3344:5566:7788").getAddress();
        buf.put(target);

        if(type == NeighborPacketForwarder.ICMPV6_NEIGHBOR_ADVERTISEMENT) {
            //NA packet has LL target address
            //ICMPv6 Option
            buf.put((byte) 0x02);                                   // Type
            buf.put((byte) 0x01);                                   // Length
            byte[] ll_target = MacAddress.fromString("01:02:03:04:05:06").toByteArray();
            buf.put(ll_target);
        }

        // Populate checksum field
        final int transportOffset = ETH_HEADER_LEN + IPV6_HEADER_LEN;
        final short checksum = icmpv6Checksum(buf, ETH_HEADER_LEN, transportOffset, icmpLen);
        buf.putShort(transportOffset + ICMPV6_CHECKSUM_OFFSET, checksum);

        buf.flip();

        return buf;
    }

    private DadProxy setupProxy() {
        final InterfaceParams tetheredParams = InterfaceParams.getByName(mTetheredIfaceName);
        assertNotNull(tetheredParams);
        DadProxy proxy = new DadProxy(new Handler(Looper.myLooper()), tetheredParams);
        final InterfaceParams upstreamParams = InterfaceParams.getByName(mUpstreamIfaceName);
        assertNotNull(upstreamParams);
        proxy.setUpstreamIface(upstreamParams);
        return proxy;
    }

    private boolean waitForPacket(ByteBuffer packet, TapPacketReader reader) {
        byte[] p;
        int i = -1;
        while((p = reader.popPacket(PACKET_TIMEOUT_MS)) != null) {
            final ByteBuffer buffer = ByteBuffer.wrap(p, ETH_HEADER_LEN, p.length - ETH_HEADER_LEN);

            if (buffer.compareTo(packet) == 0) return true;
        }
        return false;
    }

    // Might remove need for sleep below... test
    private void disableIpv6ProvisioningDelays() throws Exception {
        // Speed up the test by disabling DAD and removing router_solicitation_delay.
        // We don't need to restore the default value because the interface is removed in tearDown.
        // TODO: speed up further by not waiting for RS but keying off first IPv6 packet.
        mNetd.setProcSysNet(INetd.IPV6, INetd.CONF, mUpstreamIfaceName, "dad_transmits", "0");
        mNetd.setProcSysNet(INetd.IPV6, INetd.CONF, mTetheredIfaceName, "dad_transmits", "0");
    }

    @Test
    public void testNsForwarding() throws Exception {
        DadProxy proxy = setupProxy();

        // Upstream iface is added to local network to simplify test case.
        // Otherwise the test needs to create and destroy a network for the upstream iface.
        mNetd.networkAddInterface(INetd.LOCAL_NET_ID, mUpstreamIfaceName);
        mNetd.networkAddInterface(INetd.LOCAL_NET_ID, mTetheredIfaceName);

        // Need to wait before sending any packets out newly created sockets.
        disableIpv6ProvisioningDelays();
        SystemClock.sleep(3000);

        ByteBuffer ns = createIcmpV6Packet(NeighborPacketForwarder.ICMPV6_NEIGHBOR_SOLICITATION);
        mTetheredPacketReader.sendResponse(ns);
        ns.rewind();

        assertTrue(waitForPacket(ns, mUpstreamPacketReader));
    }

    @Test
    public void testNsPacket() throws Exception {
        DadProxy proxy = setupProxy();

        // Need to wait before sending any packets out newly created sockets.
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {}

        ByteBuffer ns_packet = createIcmpV6Packet(NeighborPacketForwarder.ICMPV6_NEIGHBOR_SOLICITATION);
        byte[] arr = new byte[ns_packet.remaining()];
        ns_packet.get(arr);
        proxy.nsForwarder.handlePacket(arr, arr.length);
        ns_packet.rewind();

        assertTrue(waitForPacket(ns_packet, mUpstreamPacketReader));
    }

    @Test
    public void testNaPacket() throws Exception {
        DadProxy proxy = setupProxy();

        // Need to wait before sending any packets out newly created sockets.
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {}

        ByteBuffer na_packet = createIcmpV6Packet(
            NeighborPacketForwarder.ICMPV6_NEIGHBOR_ADVERTISEMENT);
        byte[] arr = new byte[na_packet.remaining()];
        na_packet.get(arr);
        proxy.naForwarder.handlePacket(arr, arr.length);
        na_packet.rewind();

        assertTrue(waitForPacket(na_packet, mTetheredPacketReader));
    }
}