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

import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;

import android.app.Instrumentation;
import android.content.Context;
import android.net.InetAddresses;
import android.net.TestNetworkInterface;
import android.net.TestNetworkManager;
import android.net.util.InterfaceParams;
import android.net.util.TetheringUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.testutils.TapPacketReader;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import android.util.Log;


@RunWith(AndroidJUnit4.class)
@SmallTest
public class DadProxyTest {
    private static final int DATA_BUFFER_LEN = 4096;
    private static final int PACKET_TIMEOUT_MS = 5_000;

    TestNetworkInterface mUpstreamTestIface, mTetheredTestIface;
    private String mUpstreamIfaceName, mTetheredIfaceName;
    private HandlerThread mUpstreamPacketReaderThread, mTetheredPacketReaderThread, mHandlerThread;
    private Handler mUpstreamHandler, mTetheredHandler, mHandler;
    private TapPacketReader mUpstreamPacketReader, mTetheredPacketReader;
    private FileDescriptor mUpstreamTapFd, mTetheredTapFd;
    private byte[] mUpstreamMac, mTetheredMac;

    @Mock private TetheringUtils.TetheringUtilsNative mMockNative;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mHandlerThread = new HandlerThread(getClass().getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        setupTapInterfaces();
    }

    @After
    public void tearDown() throws Exception {
        if (mUpstreamPacketReaderThread != null) {
            mUpstreamHandler.post(() -> mUpstreamPacketReader.stop()); // Also closes the socket
            mUpstreamTapFd = null;
        }
        if (mTetheredPacketReaderThread != null) {
            mTetheredHandler.post(() -> mTetheredPacketReader.stop()); // Also closes the socket
            mTetheredTapFd = null;
        }

        if (mUpstreamPacketReaderThread != null) {
            mUpstreamPacketReaderThread.quitSafely();
        }
        if (mTetheredPacketReaderThread != null) {
            mTetheredPacketReaderThread.quitSafely();
        }
    }

    private TestNetworkInterface setupTapInterface() {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        // Adopt the shell permission identity to create a test TAP interface.
        inst.getUiAutomation().adoptShellPermissionIdentity();

        final TestNetworkInterface iface;
        try {
            final TestNetworkManager tnm = (TestNetworkManager)
                    inst.getContext().getSystemService(Context.TEST_NETWORK_SERVICE);
            iface = tnm.createTapInterface();
        } finally {
            // Drop the identity in order to regain the network stack permissions, which the shell
            // does not have.
            inst.getUiAutomation().dropShellPermissionIdentity();
        }

        return iface;
    }

    private void setupTapInterfaces() {
        // Create upstream test iface.
        mUpstreamTestIface = setupTapInterface();
        mUpstreamIfaceName = mUpstreamTestIface.getInterfaceName();
        mUpstreamPacketReaderThread = new HandlerThread(DadProxyTest.class.getSimpleName());
        mUpstreamPacketReaderThread.start();
        mUpstreamHandler = mUpstreamPacketReaderThread.getThreadHandler();

        mUpstreamTapFd = mUpstreamTestIface.getFileDescriptor().getFileDescriptor();
        mUpstreamPacketReader = new TapPacketReader(mUpstreamHandler, mUpstreamTapFd, DATA_BUFFER_LEN);
        mUpstreamHandler.post(() -> mUpstreamPacketReader.start());

        // Create tethered test iface.
        mTetheredTestIface = setupTapInterface();
        mTetheredIfaceName = mTetheredTestIface.getInterfaceName();
        // Use same handler as above?
        mTetheredPacketReaderThread = new HandlerThread(DadProxyTest.class.getSimpleName());
        mTetheredPacketReaderThread.start();
        mTetheredHandler = mTetheredPacketReaderThread.getThreadHandler();

        mTetheredTapFd = mTetheredTestIface.getFileDescriptor().getFileDescriptor();
        mTetheredPacketReader = new TapPacketReader(mTetheredHandler, mTetheredTapFd, DATA_BUFFER_LEN);
        mTetheredHandler.post(() -> mTetheredPacketReader.start());
    }

    private static final int IPV6_HEADER_LEN = 40;
    private static final int ETH_HEADER_LEN = 14;
    private static final int ICMPV6_HEADER_LEN = 24;
    private static final int LL_TARGET_OPTION_LEN = 8;

    // Move function to Util location?
    private static ByteBuffer createIcmpV6Packet(int type) {
        // Refer to buildArpPacket()
        int length = IPV6_HEADER_LEN + ICMPV6_HEADER_LEN; // ETH_HEADER_LEN
        // Add in optional LL target address len for NA packets
        length += (type == NeighborPacketForwarder.ICMPV6_NEIGHBOR_ADVERTISEMENT ? LL_TARGET_OPTION_LEN : 0);
        final ByteBuffer buf = ByteBuffer.allocate(length);

        // Eth header isn't needed when sending straight to handlePacket()
        // Eth Header, 14
        // byte[] eth_dst = {(byte) 0x01, 0x02, 0x03, 0x04, 0x05, 0x06}; //6 bytes
        // buf.put(eth_dst);
        // byte[] eth_src = {(byte) 0x01, 0x02, 0x03, 0x04, 0x05, 0x06}; //6 bytes
        // buf.put(eth_src);
        // byte[] ipv6_type = {(byte) 0x86, (byte) 0xdd};
        // buf.put(ipv6_type);

        // IPv6 header, 40
        byte[] version = {(byte) 0x60, 0x00, 0x00, 0x00}; //4 bytes
        buf.put(version); //src
        buf.putShort((byte) 0x18);
        buf.put((byte) 0x3a);
        buf.put((byte) 0xff);

        final byte[] src = InetAddresses.parseNumericAddress("::").getAddress();
        buf.put(src); // Src
        final byte[] allNodes = InetAddresses.parseNumericAddress("ff02::1").getAddress();
        buf.put(allNodes); // Dst

        // ICMPv6 Header
        buf.put((byte) type);
        buf.put((byte) 0x00); // Code
        byte[] checksum = {(byte) 0x73, (byte) 0xd2};
        buf.put(checksum); // Checksum
        buf.putInt(0); // Reserved
        byte[] target = {(byte) 0xfe, (byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88};
        buf.put(target);

        if(type == NeighborPacketForwarder.ICMPV6_NEIGHBOR_ADVERTISEMENT) {
            //NA packet has LL target address
            //ICMPv6 Option
            buf.put((byte) 0x02); // Type
            buf.put((byte) 0x01); // Length
            byte[] ll_target = {(byte) 0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
            buf.put(ll_target);
        }
        buf.flip();

        return buf;
    }

    @Test
    public void testDadProxy() {
        // Looper must be prepared here since AndroidJUnitRunner runs tests on seperate threads.
        if (Looper.myLooper() == null) Looper.prepare();

        final InterfaceParams tetheredParams = InterfaceParams.getByName(mTetheredIfaceName);
        assertNotNull(tetheredParams);
        Log.d("Tyler", "tetheredParams = " + tetheredParams.toString());
        // Can we use 1 handler for everything?
        DadProxy proxy = new DadProxy(new Handler(Looper.myLooper()), tetheredParams, mMockNative);
        assertNotNull(proxy);
        proxy.setUpstreamIface(InterfaceParams.getByName(mUpstreamIfaceName));
    }

    // Send packet to upstreamSocket, expect forwarding to tethered socket
    // Doesn't work since sendTo() pipes packet to output chain, and the NeighborPacketForwarder grabs
    // packets from input chain.
    @Test
    public void testUpstreamNaPacket() {
        // Looper must be prepared here since AndroidJUnitRunner runs tests on seperate threads.
        if (Looper.myLooper() == null) Looper.prepare();

        final InterfaceParams tetheredParams = InterfaceParams.getByName(mTetheredIfaceName);
        assertNotNull(tetheredParams);
        // Can we use 1 handler for everything?
        DadProxy proxy = new DadProxy(new Handler(Looper.myLooper()), tetheredParams, mMockNative);
        assertNotNull(proxy);
        final InterfaceParams upstreamParams = InterfaceParams.getByName(mUpstreamIfaceName);
        assertNotNull(upstreamParams);
        proxy.setUpstreamIface(upstreamParams);

        try {
            mUpstreamPacketReader.sendResponse(createIcmpV6Packet(NeighborPacketForwarder.ICMPV6_NEIGHBOR_ADVERTISEMENT));
        } catch (IOException e) {
        }
    }

    // Send packet directly to handlePacket()
    // Errors out with eaddrnotavail during sendTo() in handlePacket()
    @Test
    public void testUpstreamNsPacket() {
        // Looper must be prepared here since AndroidJUnitRunner runs tests on seperate threads.
        if (Looper.myLooper() == null) Looper.prepare();

        final InterfaceParams tetheredParams = InterfaceParams.getByName(mTetheredIfaceName);
        assertNotNull(tetheredParams);
        DadProxy proxy = new DadProxy(new Handler(Looper.myLooper()), tetheredParams, mMockNative);
        assertNotNull(proxy);
        final InterfaceParams upstreamParams = InterfaceParams.getByName(mUpstreamIfaceName);
        assertNotNull(upstreamParams);
        proxy.setUpstreamIface(upstreamParams);

        ByteBuffer ns_packet = createIcmpV6Packet(NeighborPacketForwarder.ICMPV6_NEIGHBOR_SOLICITATION);
        byte[] arr = new byte[ns_packet.remaining()];
        ns_packet.get(arr);
        proxy.nsForwarder.handlePacket(arr, arr.length);
    }
}