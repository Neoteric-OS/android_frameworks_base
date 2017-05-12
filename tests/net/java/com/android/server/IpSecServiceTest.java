/*
 * Copyright (C) 2012 The Android Open Source Project
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
import static android.system.OsConstants.IPPROTO_UDP;
import static android.system.OsConstants.SOCK_DGRAM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.INetd;
import android.net.IpSecAlgorithm;
import android.net.IpSecConfig;
import android.net.IpSecManager;
import android.net.IpSecSpiResponse;
import android.net.IpSecTransform;
import android.net.IpSecTransformResponse;
import android.net.IpSecUdpEncapResponse;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.system.Os;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link IpSecService}. */
@RunWith(JUnit4.class)
public class IpSecServiceTest {

    private static final int DROID_SPI = 0xD1201D;
    private static final int TEST_UDP_ENCAP_INVALID_PORT = 100;
    private static final int TEST_UDP_ENCAP_PORT = 34567;

    private static final String IPV4_LOOPBACK = "127.0.0.1";
    private static final String IPV4_ADDR = "192.168.0.2";

    private static final byte[] CRYPT_KEY = {
        0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
        0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
        0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
        0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F
    };
    private static final byte[] AUTH_KEY = {
        0x7A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7F,
        0x7A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7F
    };

    static final IpSecTransformResponse TSF_RESP =
        new IpSecTransformResponse(IpSecManager.Status.OK, 0x1);
    IpSecUdpEncapResponse mIpSecUdpEncapResp = null;
    private InetAddress mLocalAddr = null;

    Context mMockContext = mock(Context.class);
    INetd mMockNetd = mock(INetd.class);
    IpSecService.IpSecServiceConfiguration mMockIpSecSrvConfig =
            mock(IpSecService.IpSecServiceConfiguration.class);
    IpSecService mMockIpSecService = new IpSecService(mMockContext, mMockIpSecSrvConfig);
    ConnectivityManager mMockConnectivityManager = mock(ConnectivityManager.class);
    IpSecManager mMockIpSecManager;

    @Before
    public void setUp() throws Exception {
        mLocalAddr = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        // Injecting mock netd
        when(mMockIpSecSrvConfig.getNetdInstance()).thenReturn(mMockNetd);
    }

    @Test
    public void testIpSecServiceCreate() throws InterruptedException {
        IpSecService ipSecSrv = IpSecService.create(mMockContext);
        assertNotNull(ipSecSrv);
    }

    @Test
    public void testIpSecServiceReserveSpi() throws Exception {
        when(mMockNetd.ipSecAllocateSpi(
                        anyInt(),
                        eq(IpSecTransform.DIRECTION_OUT),
                        anyString(),
                        eq(IPV4_LOOPBACK),
                        eq(DROID_SPI)))
                .thenReturn(DROID_SPI);
        when(mMockNetd.ipSecAllocateSpi(
                        anyInt(),
                        eq(IpSecTransform.DIRECTION_OUT),
                        anyString(),
                        eq(IPV4_ADDR),
                        eq(DROID_SPI)))
                .thenReturn(0);

        IpSecSpiResponse spiResp =
                mMockIpSecService.reserveSecurityParameterIndex(
                        IpSecTransform.DIRECTION_OUT, IPV4_LOOPBACK, DROID_SPI, new Binder());
        assertEquals(IpSecManager.Status.OK, spiResp.status);
        assertEquals(DROID_SPI, spiResp.spi);

        spiResp =
                mMockIpSecService.reserveSecurityParameterIndex(
                        IpSecTransform.DIRECTION_OUT, IPV4_ADDR, DROID_SPI, new Binder());
        assertEquals(IpSecManager.Status.OK, spiResp.status);
        assertEquals(0, spiResp.spi);
    }

    @Test
   public void testOpenUdpEncapsulationSocket() throws Exception {
        // Get an available port and store in localport
        ServerSocket s = new ServerSocket(0);
        int localport = s.getLocalPort();
        s.close();

        IpSecUdpEncapResponse udpEncapResp =
                mMockIpSecService.openUdpEncapsulationSocket(localport, new Binder());
        assertNotNull(udpEncapResp);
        assertEquals(localport, udpEncapResp.port);

        Os.close(udpEncapResp.fileDescriptor.getFileDescriptor());
    }

    @Test
    public void testOpenUdpEncapsulationSocketOnRandomPort() throws Exception {
        IpSecUdpEncapResponse udpEncapResp =
                mMockIpSecService.openUdpEncapsulationSocket(0, new Binder());
        assertNotNull(udpEncapResp);
        assertEquals(IpSecManager.Status.OK, udpEncapResp.status);

        Os.close(udpEncapResp.fileDescriptor.getFileDescriptor());
    }

    @Test
    public void testOpenUdpEncapsulationSocketPortRange() throws Exception {
        try {
            IpSecUdpEncapResponse udpEncapResp =
                    mMockIpSecService.openUdpEncapsulationSocket(
                            TEST_UDP_ENCAP_INVALID_PORT, new Binder());
            fail("IllegalArgumentException not catch");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testOpenUdpEncapsulationSocketTwice() throws Exception {
        // Get an available port and store in localport
        ServerSocket s = new ServerSocket(0);
        int localport = s.getLocalPort();
        s.close();

        IpSecUdpEncapResponse udpEncapResp =
                mMockIpSecService.openUdpEncapsulationSocket(localport, new Binder());
        assertNotNull(udpEncapResp);
        assertEquals(localport, udpEncapResp.port);

        IpSecUdpEncapResponse testUdpEncapResp =
                mMockIpSecService.openUdpEncapsulationSocket(localport, new Binder());
        assertEquals(IpSecManager.Status.RESOURCE_UNAVAILABLE, testUdpEncapResp.status);

        Os.close(udpEncapResp.fileDescriptor.getFileDescriptor());
    }

    @Test
    public void testReleaseSecurityParameterIndex() throws Exception {
        when(mMockNetd.ipSecAllocateSpi(
                        anyInt(),
                        eq(IpSecTransform.DIRECTION_OUT),
                        anyString(),
                        eq(IPV4_LOOPBACK),
                        eq(DROID_SPI)))
                .thenReturn(DROID_SPI);

        IpSecSpiResponse spiResp =
                mMockIpSecService.reserveSecurityParameterIndex(
                        IpSecTransform.DIRECTION_OUT, IPV4_LOOPBACK, DROID_SPI, new Binder());

        mMockIpSecService.releaseSecurityParameterIndex(spiResp.resourceId);
    }

    @Test
    public void testReleaseInvalidSecurityParameterIndex() throws Exception {
        try {
            mMockIpSecService.releaseSecurityParameterIndex(1);
            fail("Should throw exception when fetching non-existing record");
        //} catch (Exception e) {
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testCloseUdpEncapsulationSocket() throws Exception {
        IpSecUdpEncapResponse udpEncapResp =
                mMockIpSecService.openUdpEncapsulationSocket(0, new Binder());
        assertNotNull(udpEncapResp);
        assertEquals(IpSecManager.Status.OK, udpEncapResp.status);
        mMockIpSecService.closeUdpEncapsulationSocket(udpEncapResp.resourceId);
        Os.close(udpEncapResp.fileDescriptor.getFileDescriptor());
    }

    @Test
    public void testCloseInvalidUdpEncapsulationSocket() throws Exception {
        try {
            mMockIpSecService.closeUdpEncapsulationSocket(1);
            fail("Should throw exception when fetching non-existing record");
        } catch (IllegalArgumentException e) {
        }
    }

    IpSecConfig buildIpSecConfig() throws Exception {
        when(mMockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
                .thenReturn(mMockConnectivityManager);

        // Mocking the netd to add SA
        when(mMockNetd.ipSecAddSecurityAssociation(
                        anyInt(),
                        eq(0),
                        anyInt(),
                        eq(""),
                        eq(""),
                        anyLong(),
                        eq(DROID_SPI),
                        anyString(),
                        eq(AUTH_KEY),
                        anyInt(),
                        anyString(),
                        eq(CRYPT_KEY),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt()))
                .thenReturn(DROID_SPI);
        when(mMockNetd.ipSecAllocateSpi(anyInt(), anyInt(), anyString(), anyString(), anyInt()))
                .thenReturn(DROID_SPI);
        when(mMockNetd.ipSecAllocateSpi(
                        anyInt(), anyInt(), anyString(), anyString(), eq(DROID_SPI)))
                .thenReturn(DROID_SPI);

        mMockIpSecManager = new IpSecManager(mMockIpSecService);

        IpSecAlgorithm encryptAlgo = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm authAlgo =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, AUTH_KEY, AUTH_KEY.length * 8);

        // Allocate and add SPI records in the IpSecService
        IpSecManager.SecurityParameterIndex outSpi =
                mMockIpSecManager.reserveSecurityParameterIndex(
                        IpSecTransform.DIRECTION_OUT, mLocalAddr);
        IpSecManager.SecurityParameterIndex inSpi =
               mMockIpSecManager.reserveSecurityParameterIndex(
                        IpSecTransform.DIRECTION_IN, mLocalAddr, DROID_SPI);

        IpSecConfig ipSecConfig =
                new IpSecTransform.Builder(mMockContext)
                        .setSpi(IpSecTransform.DIRECTION_OUT, outSpi)
                        .setSpi(IpSecTransform.DIRECTION_IN, inSpi)
                        .setEncryption(IpSecTransform.DIRECTION_OUT, encryptAlgo)
                        .setAuthentication(IpSecTransform.DIRECTION_OUT, authAlgo)
                        .setEncryption(IpSecTransform.DIRECTION_IN, encryptAlgo)
                        .setAuthentication(IpSecTransform.DIRECTION_IN, authAlgo)
                        .getIpSecConfig();
        return ipSecConfig;
    }

    @Test
    public void testCreateTransportModeTransform() throws Exception {
        IpSecConfig ipSecConfig = buildIpSecConfig();

        IpSecTransformResponse createTransformResp =
                mMockIpSecService.createTransportModeTransform(ipSecConfig, new Binder());
        assertEquals(IpSecManager.Status.OK, createTransformResp.status);
        assertEquals("cbc(aes)", ipSecConfig.getEncryption(IpSecTransform.DIRECTION_IN).getName());
        assertEquals(
                "hmac(sha256)",
                ipSecConfig.getAuthentication(IpSecTransform.DIRECTION_IN).getName());
        assertEquals("cbc(aes)", ipSecConfig.getEncryption(IpSecTransform.DIRECTION_OUT).getName());
        assertEquals(
                "hmac(sha256)",
                ipSecConfig.getAuthentication(IpSecTransform.DIRECTION_OUT).getName());
    }

    @Test
    public void testDeleteTransportModeTransform() throws Exception {
        IpSecConfig ipSecConfig = buildIpSecConfig();

        IpSecTransformResponse createTransformResp =
                mMockIpSecService.createTransportModeTransform(ipSecConfig, new Binder());
        mMockIpSecService.deleteTransportModeTransform(createTransformResp.resourceId);
    }

    @Test
    public void testDeleteInvalidTransportModeTransform() throws Exception {
        try {
            mMockIpSecService.deleteTransportModeTransform(1);
            fail("Should throw exception when fetching non-existing record");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testApplyTransportModeTransform() throws Exception {
        IpSecConfig ipSecConfig = buildIpSecConfig();

        IpSecTransformResponse createTransformResp =
                mMockIpSecService.createTransportModeTransform(ipSecConfig, new Binder());

        FileDescriptor sockFd = Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
        int resourceId = createTransformResp.resourceId;
        mMockIpSecService.applyTransportModeTransform(ParcelFileDescriptor.dup(sockFd), resourceId);
    }

    @Test
    public void testRemoveTransportModeTransformSuccess() throws Exception {
        ParcelFileDescriptor pfd =
                ParcelFileDescriptor.dup(Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP));
        mMockIpSecService.removeTransportModeTransform(pfd, 1);
    }

    @Test
    public void testRemoveTransportModeTransformSrvSpecException() throws Exception {
        doThrow(ServiceSpecificException.class)
                .when(mMockNetd)
                .ipSecRemoveTransportModeTransform(anyObject());
        ParcelFileDescriptor pfd =
                ParcelFileDescriptor.dup(Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP));
        mMockIpSecService.removeTransportModeTransform(pfd, 1);
    }
}
