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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.Instrumentation;
import android.content.Context;
import android.net.TestNetworkInterface;
import android.net.TestNetworkManager;
import android.net.util.InterfaceParams;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.testutils.TapPacketReader;

import java.io.FileDescriptor;
import java.nio.ByteBuffer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DadProxyDaemonTest {
    private static final int DATA_BUFFER_LEN = 4096;

    private String mUpstreamIfaceName, mTetheredIfaceName;
    private HandlerThread mUpstreamPacketReaderThread, mTetheredPacketReaderThread;
    private Handler mUpstreamHandler, mTetheredHandler;
    private TapPacketReader mUpstreamPacketReader, mTetheredPacketReader;
    private FileDescriptor mUpstreamTapFd, mTetheredTapFd;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        setupTapInterfaces();
    }

    @After
    public void tearDown() throws Exception {

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
        TestNetworkInterface upstreamTestIface = setupTapInterface();
        mUpstreamIfaceName = upstreamTestIface.getInterfaceName();
        mUpstreamPacketReaderThread = new HandlerThread(DadProxyDaemonTest.class.getSimpleName());
        mUpstreamPacketReaderThread.start();
        mUpstreamHandler = mUpstreamPacketReaderThread.getThreadHandler();

        mUpstreamTapFd = upstreamTestIface.getFileDescriptor().getFileDescriptor();
        mUpstreamPacketReader = new TapPacketReader(mUpstreamHandler, mUpstreamTapFd, DATA_BUFFER_LEN);
        mUpstreamHandler.post(() -> mUpstreamPacketReader.start());

        // Create tethered test iface.
        TestNetworkInterface tetheredTestIface = setupTapInterface();
        mTetheredIfaceName = tetheredTestIface.getInterfaceName();
        mTetheredPacketReaderThread = new HandlerThread(DadProxyDaemonTest.class.getSimpleName()); // Use same handler as above?
        mTetheredPacketReaderThread.start();
        mTetheredHandler = mTetheredPacketReaderThread.getThreadHandler();

        mTetheredTapFd = tetheredTestIface.getFileDescriptor().getFileDescriptor();
        mTetheredPacketReader = new TapPacketReader(mTetheredHandler, mTetheredTapFd, DATA_BUFFER_LEN);
        mTetheredHandler.post(() -> mTetheredPacketReader.start());
    }

    private void removeTapInterfaces() {

    }

    private static ByteBuffer createIcmpV6Packet() {
        final ByteBuffer buf = ByteBuffer.allocate(72);

        return buf;
    }

    @Test
    public void testDadProxy() {
        final InterfaceParams tetheredParams = InterfaceParams.getByName(mTetheredIfaceName);
        assertNotNull(tetheredParams);
        Log.d("Tyler", "tetheredParams = " + tetheredParams.toString());
        // Can we use 1 handler for everything?
        DadProxyDaemon proxy = new DadProxyDaemon(new Handler(Looper.getMainLooper()), tetheredParams);
        proxy.setUpstreamIface(mUpstreamIfaceName);
    }

    @Test
    public void testUpstreamPacket() {

    }

    @Test
    public void testDownstreamProxy() {

    }
}