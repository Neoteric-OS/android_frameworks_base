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

package android.net;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.ServiceManager;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.test.AndroidTestCase;
import android.util.Log;

import com.android.server.IpSecService;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.io.ByteArrayOutputStream;

import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import static android.net.IpSecTransform.Builder;

import static android.system.OsConstants.IPPROTO_TCP;
import static android.system.OsConstants.IPPROTO_UDP;
import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.IPPROTO_UDP;
import static android.system.OsConstants.SOCK_DGRAM;



public class IpSecManagerTest extends AndroidTestCase {

    private static final String TAG = IpSecManagerTest.class.getSimpleName();

    private IpSecManager mISM;

    private ConnectivityManager mCM;

    private static final InetAddress GOOGLE_DNS_4;
    private static final InetAddress GOOGLE_DNS_6;

    static {
        try {
            // Google Public DNS Addresses;
            GOOGLE_DNS_4 = InetAddress.getByName("8.8.8.8");
            GOOGLE_DNS_6 = InetAddress.getByName("2001:4860:4860::8888");
        } catch (UnknownHostException e) {
            throw new RuntimeException("Could not resolve DNS Addresses", e);
        }
    }

    private static final InetAddress[] GOOGLE_DNS_LIST =
            new InetAddress[] {GOOGLE_DNS_4, GOOGLE_DNS_6};

    private static final int DROID_SPI = 0xD1201D;

    private static final byte[] CRYPT_KEY =
            new byte[] {
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
                0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
                0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F
            };
    private static final byte[] AUTH_KEY =
            new byte[] {
                0x7A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7F,
                0x7A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7F
            };

    private static final String IPV4_LOOPBACK = "127.0.0.1";
    private static final String IPV6_LOOPBACK = "::1";

    private InetAddress local = null;
    private static final int TEST_UDP_ENCAP_PORT = 34567;
    private IpSecService mockIpSecService = null;

    IpSecSpiResponse expectedSpiResp = new IpSecSpiResponse(
                IpSecManager.Status.OK, 0x1, DROID_SPI);
    IpSecUdpEncapResponse ipSecUdpEncapResp = null;
    IpSecTransformResponse tsfResp = new IpSecTransformResponse(IpSecManager.Status.OK, 0x1);
    Context mockContext = null;

    protected void setUp() throws Exception {
        super.setUp();
        //mCM = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        mCM = mock(ConnectivityManager.class);
        mockContext = mock(Context.class);

        mockIpSecService = mock(IpSecService.class);
        when(mockIpSecService.reserveSecurityParameterIndex(anyInt(),
            anyString(), eq(DROID_SPI), anyObject())).thenReturn(expectedSpiResp);
        when(mockIpSecService.reserveSecurityParameterIndex(anyInt(),
            anyString(), anyInt(), anyObject())).thenReturn(expectedSpiResp);
        try {
            FileDescriptor sockFd = Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
            ipSecUdpEncapResp = new IpSecUdpEncapResponse(IpSecManager.Status.OK, 0x1, TEST_UDP_ENCAP_PORT, sockFd);

            when(mockIpSecService.openUdpEncapsulationSocket(eq(TEST_UDP_ENCAP_PORT), anyObject())).thenReturn(ipSecUdpEncapResp);

            local = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot set up tests correctly "+e.getMessage());
        }

        mISM = new IpSecManager(mockIpSecService);
    }

    
    /*
     * Allocate a specific SPI
     * Close SPIs
     */
    public void testAllocSpi() throws Exception {
        for (InetAddress addr : GOOGLE_DNS_LIST) {
            IpSecManager.SecurityParameterIndex randomSpi = null, droidSpi = null;
            randomSpi = mISM.reserveSecurityParameterIndex(IpSecTransform.DIRECTION_OUT, addr);
            assertTrue(
                    "Failed to receive a valid SPI",
                    randomSpi.getSpi() != IpSecManager.INVALID_SECURITY_PARAMETER_INDEX);

            droidSpi =
                    mISM.reserveSecurityParameterIndex(
                            IpSecTransform.DIRECTION_IN, addr, DROID_SPI);
            assertTrue(
                    "Failed to allocate specified SPI, " + DROID_SPI,
                    droidSpi.getSpi() == DROID_SPI);

            randomSpi.close();
            droidSpi.close();
        }
    }

    public void testIpSecServiceApi() throws Exception {
        try {
            assertTrue(ipSecUdpEncapResp != null);

            assertTrue(mockIpSecService.reserveSecurityParameterIndex(IpSecTransform.DIRECTION_OUT,
                IPV4_LOOPBACK, DROID_SPI, new Binder()).spi == expectedSpiResp.spi);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error running testIpSecServiceApi");
        }
    }

    public void testBuildEncapsulationSocket() throws Exception {
        IpSecManager.UdpEncapsulationSocket encapSocket = mISM.openUdpEncapsulationSocket(TEST_UDP_ENCAP_PORT);
        assertTrue(encapSocket.getSocket() != null);
        assertTrue(encapSocket.getPort() == TEST_UDP_ENCAP_PORT);
    }

    void setUpTestIpSecTransformBuilder() {
        try {
            when(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(mCM);
            when(mockIpSecService.createTransportModeTransform(anyObject(), anyObject())).thenReturn(tsfResp);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot set up testUdpEncapsulation correctly "+e.getMessage());
        }
    }

    public void testIpSecTransformBuilder() throws Exception {

        setUpTestIpSecTransformBuilder();
        
        assertTrue(mISM != null);
        assertTrue(tsfResp != null);

        IpSecManager.UdpEncapsulationSocket encapSocket = mISM.openUdpEncapsulationSocket(34567);

        IpSecManager.SecurityParameterIndex outSpi =
                mISM.reserveSecurityParameterIndex(IpSecTransform.DIRECTION_OUT, local);

        IpSecManager.SecurityParameterIndex inSpi =
                mISM.reserveSecurityParameterIndex(
                        IpSecTransform.DIRECTION_IN, local, DROID_SPI);
    
        IpSecTransform.Builder builder = mock(IpSecTransform.Builder.class);
        builder = new IpSecTransform.Builder(mockContext);

        IpSecTransform transform = builder
                        .setSpi(IpSecTransform.DIRECTION_OUT, outSpi)
                        .setEncryption(
                                IpSecTransform.DIRECTION_OUT,
                                new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY))
                        .setAuthentication(
                                IpSecTransform.DIRECTION_OUT,
                                new IpSecAlgorithm(
                                        IpSecAlgorithm.AUTH_HMAC_SHA256,
                                        AUTH_KEY,
                                        AUTH_KEY.length * 8))
                        .setSpi(IpSecTransform.DIRECTION_IN, inSpi)
                        .setEncryption(
                                IpSecTransform.DIRECTION_IN,
                                new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY))
                        .setAuthentication(
                                IpSecTransform.DIRECTION_IN,
                                new IpSecAlgorithm(
                                        IpSecAlgorithm.AUTH_HMAC_SHA256,
                                        AUTH_KEY,
                                        CRYPT_KEY.length * 8))
                        .setIpv4Encapsulation(encapSocket, encapSocket.getPort())
                        .buildTransportModeTransform(local, mockIpSecService);
        assertTrue(transform != null);

        builder = new IpSecTransform.Builder(mockContext);
        transform = builder
                        .setSpi(IpSecTransform.DIRECTION_OUT, outSpi)
                        .buildTransportModeTransform(local, mockIpSecService);
        assertTrue(transform != null);
    }
}

