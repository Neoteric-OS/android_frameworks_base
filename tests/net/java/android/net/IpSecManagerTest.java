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

import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.IPPROTO_UDP;
import static android.system.OsConstants.SOCK_DGRAM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.support.test.filters.SmallTest;
import android.system.Os;
import com.android.server.IpSecService;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link IpSecManager}. */
@SmallTest
@RunWith(JUnit4.class)
public class IpSecManagerTest {

    private static final int TEST_UDP_ENCAP_PORT = 34567;
    private static final int DROID_SPI = 0xD1201D;
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

    private static final InetAddress GOOGLE_DNS_4;

    static {
        try {
            // Google Public DNS Addresses;
            GOOGLE_DNS_4 = InetAddress.getByName("8.8.8.8");
        } catch (UnknownHostException e) {
            throw new RuntimeException("Could not resolve DNS Addresses", e);
        }
    }

    private IpSecService mMockIpSecService;
    private IpSecManager mIpSecManager;
    INetd mMockNetd;

    @Before
    public void setUp() throws Exception {
        mMockIpSecService = mock(IpSecService.class);
        mIpSecManager = new IpSecManager(mMockIpSecService);
        mMockNetd = mock(INetd.class);
    }

    /*
     * Allocate a specific SPI
     * Close SPIs
     */
    @Test
    public void testAllocSpi() throws Exception {
        int resourceId = 1;
        IpSecSpiResponse spiResp =
                new IpSecSpiResponse(IpSecManager.Status.OK, resourceId, DROID_SPI);
        when(mMockIpSecService.reserveSecurityParameterIndex(
                        eq(IpSecTransform.DIRECTION_IN),
                        eq(GOOGLE_DNS_4.getHostAddress()),
                        eq(DROID_SPI),
                        anyObject()))
                .thenReturn(spiResp);

        IpSecManager.SecurityParameterIndex droidSpi =
                mIpSecManager.reserveSecurityParameterIndex(
                        IpSecTransform.DIRECTION_IN, GOOGLE_DNS_4, DROID_SPI);
        assertEquals(DROID_SPI, droidSpi.getSpi());

        droidSpi.close();

        verify(mMockIpSecService).releaseSecurityParameterIndex(resourceId);
    }

    @Test
    public void testAllocRandomSpi() throws Exception {
        int resourceId = 1;
        IpSecSpiResponse spiResp =
                new IpSecSpiResponse(IpSecManager.Status.OK, resourceId, DROID_SPI);
        when(mMockIpSecService.reserveSecurityParameterIndex(
                        eq(IpSecTransform.DIRECTION_OUT),
                        eq(GOOGLE_DNS_4.getHostAddress()),
                        eq(IpSecManager.INVALID_SECURITY_PARAMETER_INDEX),
                        anyObject()))
                .thenReturn(spiResp);

        IpSecManager.SecurityParameterIndex randomSpi =
                mIpSecManager.reserveSecurityParameterIndex(
                        IpSecTransform.DIRECTION_OUT, GOOGLE_DNS_4);

        assertEquals(DROID_SPI, randomSpi.getSpi());

        randomSpi.close();

        verify(mMockIpSecService).releaseSecurityParameterIndex(resourceId);
    }

    /*
     * Throws resource unavailable exception
     */
    @Test
    public void testAllocSpiResUnavaiableExeption() throws Exception {
        IpSecSpiResponse spiResp =
                new IpSecSpiResponse(IpSecManager.Status.RESOURCE_UNAVAILABLE, 0, 0);
        when(mMockIpSecService.reserveSecurityParameterIndex(
                        anyInt(), anyString(), anyInt(), anyObject()))
                .thenReturn(spiResp);

        try {
            mIpSecManager.reserveSecurityParameterIndex(IpSecTransform.DIRECTION_OUT, GOOGLE_DNS_4);
            fail("ResourceUnavailableException was not thrown");
        } catch (IpSecManager.ResourceUnavailableException e) {
        }
    }

    /*
     * Throws spi unavailable exception
     */
    @Test
    public void testAllocSpiSpiUnavaiableExeption() throws Exception {
        IpSecSpiResponse spiResp = new IpSecSpiResponse(IpSecManager.Status.SPI_UNAVAILABLE, 0, 0);
        when(mMockIpSecService.reserveSecurityParameterIndex(
                        anyInt(), anyString(), anyInt(), anyObject()))
                .thenReturn(spiResp);

        try {
            mIpSecManager.reserveSecurityParameterIndex(IpSecTransform.DIRECTION_OUT, GOOGLE_DNS_4);
            fail("ResourceUnavailableException was not thrown");
        } catch (IpSecManager.ResourceUnavailableException e) {
        }
    }

    /*
     * Should throw exception when request spi 0 in IpSecManager
     */
    @Test
    public void testRequestAllocInvalidSpi() throws Exception {
        try {
            mIpSecManager.reserveSecurityParameterIndex(
                    IpSecTransform.DIRECTION_OUT, GOOGLE_DNS_4, 0);
            fail("Able to allocate invalid spi");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testOpenEncapsulationSocket() throws Exception {
        int resourceId = 1;
        IpSecUdpEncapResponse udpEncapResp =
                new IpSecUdpEncapResponse(
                        IpSecManager.Status.OK,
                        resourceId,
                        TEST_UDP_ENCAP_PORT,
                        Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP));
        when(mMockIpSecService.openUdpEncapsulationSocket(eq(TEST_UDP_ENCAP_PORT), anyObject()))
                .thenReturn(udpEncapResp);

        IpSecManager.UdpEncapsulationSocket encapSocket =
                mIpSecManager.openUdpEncapsulationSocket(TEST_UDP_ENCAP_PORT);
        assertNotNull(encapSocket.getSocket());
        assertEquals(TEST_UDP_ENCAP_PORT, encapSocket.getPort());

        encapSocket.close();

        verify(mMockIpSecService).closeUdpEncapsulationSocket(resourceId);
    }

    @Test
    public void testOpenEncapsulationSocketOnRandomPort() throws Exception {
        int resourceId = 1;
        IpSecUdpEncapResponse udpEncapResp =
                new IpSecUdpEncapResponse(
                        IpSecManager.Status.OK,
                        resourceId,
                        TEST_UDP_ENCAP_PORT,
                        Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP));

        when(mMockIpSecService.openUdpEncapsulationSocket(eq(0), anyObject()))
                .thenReturn(udpEncapResp);

        IpSecManager.UdpEncapsulationSocket encapSocket =
                mIpSecManager.openUdpEncapsulationSocket();

        assertNotNull(encapSocket.getSocket());
        assertEquals(TEST_UDP_ENCAP_PORT, encapSocket.getPort());

        encapSocket.close();

        verify(mMockIpSecService).closeUdpEncapsulationSocket(resourceId);
    }

    @Test
    public void testOpenEncapsulationSocketWithInvalidPort() throws Exception {
        try {
            mIpSecManager.openUdpEncapsulationSocket(IpSecManager.INVALID_SECURITY_PARAMETER_INDEX);
            fail("IllegalArgumentException was not thrown");
        } catch (IllegalArgumentException e) {
        }
    }

    // TODO: add test when applicable transform builder interface is available

    @Test
    public void testBuildInvalidAlgosAuthCryptAfterCrypt() throws Exception {
        IpSecManager ipSecManager = new IpSecManager(mMockIpSecService);

        // Mocking the netd to allocate SPI
        when(mMockNetd.ipSecAllocateSpi(anyInt(), anyInt(), anyString(), anyString(), anyInt()))
                .thenReturn(DROID_SPI)
                .thenReturn(DROID_SPI + 1);

        IpSecAlgorithm encryptionAlgo = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm authenticatedEncryptionAlgo =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AUTH_KEY);

        InetAddress localAddr = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});

        /** Allocate and add SPI records in the IpSecService through IpSecManager interface. */
        IpSecManager.SecurityParameterIndex outSpi =
                ipSecManager.reserveSecurityParameterIndex(IpSecTransform.DIRECTION_OUT, localAddr);
        IpSecManager.SecurityParameterIndex inSpi =
                ipSecManager.reserveSecurityParameterIndex(IpSecTransform.DIRECTION_IN, localAddr);

        try {
            IpSecConfig ipSecConfig =
                    new IpSecTransform.Builder(mock(Context.class))
                            .setSpi(IpSecTransform.DIRECTION_OUT, outSpi)
                            .setSpi(IpSecTransform.DIRECTION_IN, inSpi)
                            .setEncryption(IpSecTransform.DIRECTION_OUT, encryptionAlgo)
                            .setAuthenticatedEncryption(
                                    IpSecTransform.DIRECTION_OUT, authenticatedEncryptionAlgo)
                            .getIpSecConfig();

            fail("Setting Authenticated Encryption after Encryption should fail");
        } catch (IllegalStateException e) {
            // Test passes
        }
    }

    @Test
    public void testBuildInvalidAlgosCryptAfterAuthCrypt() throws Exception {
        IpSecManager ipSecManager = new IpSecManager(mMockIpSecService);

        // Mocking the netd to allocate SPI
        when(mMockNetd.ipSecAllocateSpi(anyInt(), anyInt(), anyString(), anyString(), anyInt()))
                .thenReturn(DROID_SPI)
                .thenReturn(DROID_SPI + 1);

        IpSecAlgorithm encryptionAlgo = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm authenticatedEncryptionAlgo =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AUTH_KEY);

        InetAddress localAddr = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});

        /** Allocate and add SPI records in the IpSecService through IpSecManager interface. */
        IpSecManager.SecurityParameterIndex outSpi =
                ipSecManager.reserveSecurityParameterIndex(IpSecTransform.DIRECTION_OUT, localAddr);
        IpSecManager.SecurityParameterIndex inSpi =
                ipSecManager.reserveSecurityParameterIndex(IpSecTransform.DIRECTION_IN, localAddr);

        try {
            IpSecConfig ipSecConfig =
                    new IpSecTransform.Builder(mock(Context.class))
                            .setSpi(IpSecTransform.DIRECTION_OUT, outSpi)
                            .setSpi(IpSecTransform.DIRECTION_IN, inSpi)
                            .setAuthenticatedEncryption(
                                    IpSecTransform.DIRECTION_OUT, authenticatedEncryptionAlgo)
                            .setEncryption(IpSecTransform.DIRECTION_OUT, encryptionAlgo)
                            .getIpSecConfig();

            fail("Setting Encryption after Authenticated Encryption should fail");
        } catch (IllegalStateException e) {
            // Test passes
        }
    }

    @Test
    public void testBuildInvalidAlgosAuthCryptAfterAuth() throws Exception {
        IpSecManager ipSecManager = new IpSecManager(mMockIpSecService);

        // Mocking the netd to allocate SPI
        when(mMockNetd.ipSecAllocateSpi(anyInt(), anyInt(), anyString(), anyString(), anyInt()))
                .thenReturn(DROID_SPI)
                .thenReturn(DROID_SPI + 1);

        IpSecAlgorithm authenticationAlgo =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, AUTH_KEY);
        IpSecAlgorithm authenticatedEncryptionAlgo =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AUTH_KEY);

        InetAddress localAddr = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});

        /** Allocate and add SPI records in the IpSecService through IpSecManager interface. */
        IpSecManager.SecurityParameterIndex outSpi =
                ipSecManager.reserveSecurityParameterIndex(IpSecTransform.DIRECTION_OUT, localAddr);
        IpSecManager.SecurityParameterIndex inSpi =
                ipSecManager.reserveSecurityParameterIndex(IpSecTransform.DIRECTION_IN, localAddr);

        try {
            IpSecConfig ipSecConfig =
                    new IpSecTransform.Builder(mock(Context.class))
                            .setSpi(IpSecTransform.DIRECTION_OUT, outSpi)
                            .setSpi(IpSecTransform.DIRECTION_IN, inSpi)
                            .setAuthentication(IpSecTransform.DIRECTION_OUT, authenticationAlgo)
                            .setAuthenticatedEncryption(
                                    IpSecTransform.DIRECTION_OUT, authenticatedEncryptionAlgo)
                            .getIpSecConfig();

            fail("Setting Authenticated Encryption after Authentication should fail");
        } catch (IllegalStateException e) {
            // Test passes
        }
    }

    @Test
    public void testBuildInvalidAlgosAuthAfterAuthCrypt() throws Exception {
        IpSecManager ipSecManager = new IpSecManager(mMockIpSecService);

        // Mocking the netd to allocate SPI
        when(mMockNetd.ipSecAllocateSpi(anyInt(), anyInt(), anyString(), anyString(), anyInt()))
                .thenReturn(DROID_SPI)
                .thenReturn(DROID_SPI + 1);

        IpSecAlgorithm authenticationAlgo =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, AUTH_KEY);
        IpSecAlgorithm authenticatedEncryptionAlgo =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AUTH_KEY);

        InetAddress localAddr = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});

        /** Allocate and add SPI records in the IpSecService through IpSecManager interface. */
        IpSecManager.SecurityParameterIndex outSpi =
                ipSecManager.reserveSecurityParameterIndex(IpSecTransform.DIRECTION_OUT, localAddr);
        IpSecManager.SecurityParameterIndex inSpi =
                ipSecManager.reserveSecurityParameterIndex(IpSecTransform.DIRECTION_IN, localAddr);

        try {
            IpSecConfig ipSecConfig =
                    new IpSecTransform.Builder(mock(Context.class))
                            .setSpi(IpSecTransform.DIRECTION_OUT, outSpi)
                            .setSpi(IpSecTransform.DIRECTION_IN, inSpi)
                            .setAuthenticatedEncryption(
                                    IpSecTransform.DIRECTION_OUT, authenticatedEncryptionAlgo)
                            .setAuthentication(IpSecTransform.DIRECTION_OUT, authenticationAlgo)
                            .getIpSecConfig();

            fail("Setting Authentication after Authenticated Encryption should fail");
        } catch (IllegalStateException e) {
            // Test passes
        }
    }
}
