package com.android.server;


import android.content.Context;

import android.net.ConnectivityManager;
import android.net.INetd;
import android.net.IpSecAlgorithm;
import android.net.IpSecManager;
import android.net.IpSecSpiResponse;
import android.net.IpSecTransform;
import android.net.IpSecTransformResponse;
import android.net.IpSecUdpEncapResponse;
import android.net.UidRange;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.Os;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static android.system.OsConstants.AF_INET;
import static android.system.OsConstants.IPPROTO_UDP;
import static android.system.OsConstants.SOCK_DGRAM;

import static org.junit.Assert.assertTrue;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for {@link IpSecService}. */
@RunWith(JUnit4.class)
public class IpSecServiceTest {

    private static final int DROID_SPI = 0xD1201D;
    private static final int TEST_UDP_ENCAP_PORT = 34567;

    private static final String IPV4_LOOPBACK = "127.0.0.1";

    private static final byte[] CRYPT_KEY =
            {
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
                0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
                0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F
            };
    private static final byte[] AUTH_KEY =
            {
                0x7A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7F,
                0x7A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7F
            };

    IpSecTransformResponse tsfResp = new IpSecTransformResponse(IpSecManager.Status.OK, 0x1);
    IpSecUdpEncapResponse ipSecUdpEncapResp = null;
    private InetAddress local = null;

    Context mockContext = mock(Context.class);
    INetd mockNetd = mock(INetd.class);
    IpSecService mockIpSecService = mock(IpSecService.class);
    ConnectivityManager mCM = mock(ConnectivityManager.class);
    IpSecManager mISM = null;

    @Before
    public void setUp() throws IOException, UnknownHostException,
                    InterruptedException, RemoteException, ErrnoException {
        // Mock return value for testIpSecServiceReserveSpi
        //when(mockNetd.ipSecAllocateSpi(anyInt(), anyInt(), anyString(), anyString(), eq(DROID_SPI)))
        when(mockNetd.ipSecAllocateSpi(anyInt(), anyInt(), anyString(), anyString(), anyInt()))
                    .thenReturn(DROID_SPI);

        // Mock return value for testOpenUdpEncapsulationSocket
        FileDescriptor sockFd = Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
        ipSecUdpEncapResp = new IpSecUdpEncapResponse(IpSecManager.Status.OK, 0x1, TEST_UDP_ENCAP_PORT, sockFd);
        when(mockIpSecService.openUdpEncapsulationSocket(eq(TEST_UDP_ENCAP_PORT), anyObject()))
                    .thenReturn(ipSecUdpEncapResp);

        local = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
    }

    @Test
    public void testIpSecServiceCreate() throws InterruptedException {
        IpSecService ipSecSrv = IpSecService.create(mockContext);
        assertTrue(ipSecSrv != null);
    }

    @Test
    public void testIpSecServiceReserveSpi() throws InterruptedException, RemoteException {
        IpSecService ipSecSrv = IpSecService.create(mockContext).setINetd(mockNetd);

        assertTrue(ipSecSrv.reserveSecurityParameterIndex(IpSecTransform.DIRECTION_OUT,
            IPV4_LOOPBACK, DROID_SPI, new Binder()).spi == DROID_SPI);
    }

    @Test
    public void testOpenUdpEncapsulationSocket() throws IOException,
                    InterruptedException, RemoteException {
        IpSecService ipSecSrv = IpSecService.create(mockContext);
        IpSecUdpEncapResponse ipSecUdpEncapResp = ipSecSrv.openUdpEncapsulationSocket(TEST_UDP_ENCAP_PORT, new Binder());
        assertTrue(ipSecUdpEncapResp.port == TEST_UDP_ENCAP_PORT);
    }

    void setUpTestCreateTransportModeTransform() throws RemoteException, InterruptedException {
        when(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(mCM);

        // For building a mocking transform
        when(mockIpSecService.createTransportModeTransform(anyObject(), anyObject())).thenReturn(tsfResp);

        // Mocking the netd to add SA
        when(mockNetd.ipSecAddSecurityAssociation(anyInt(), anyInt(), anyInt(),
                anyString(), anyString(), anyLong(),
                eq(DROID_SPI), anyString(), anyObject(), anyInt(),
                anyString(), anyObject(), anyInt(),
                anyInt(), anyInt(), anyInt())).thenReturn(DROID_SPI);
        when(mockNetd.ipSecAllocateSpi(anyInt(), anyInt(), anyString(), anyString(), eq(DROID_SPI)))
                    .thenReturn(DROID_SPI);
    }

    @Test
    public void testCreateTransportModeTransform() throws InterruptedException, RemoteException,
                    IpSecManager.ResourceUnavailableException, IpSecManager.SpiUnavailableException,
                    IOException {
        setUpTestCreateTransportModeTransform();

        IpSecService ipSecSrv = IpSecService.create(mockContext).setINetd(mockNetd);
        mISM = new IpSecManager(ipSecSrv);

        // Allocate and add SPI records in the IpSecService 
        IpSecManager.SecurityParameterIndex outSpi =
                mISM.reserveSecurityParameterIndex(IpSecTransform.DIRECTION_OUT, local);
        IpSecManager.SecurityParameterIndex inSpi =
                mISM.reserveSecurityParameterIndex(
                        IpSecTransform.DIRECTION_IN, local, DROID_SPI);

        IpSecTransform transform = new IpSecTransform.Builder(mockContext)
                        .setSpi(IpSecTransform.DIRECTION_OUT, outSpi)
                        .setSpi(IpSecTransform.DIRECTION_IN, inSpi)
                        .buildTransportModeTransform(local, mockIpSecService);

        IpSecTransformResponse createTransformResp =
                ipSecSrv.createTransportModeTransform(transform.getIpSecConfig(), new Binder());
        assertTrue(createTransformResp.status == IpSecManager.Status.OK);
    }

    @Test
    public void testDumpMessage() throws InterruptedException, RemoteException,
                    IpSecManager.ResourceUnavailableException, IpSecManager.SpiUnavailableException,
                    IOException {
        setUpTestCreateTransportModeTransform();

        IpSecService ipSecSrv = IpSecService.create(mockContext).setINetd(mockNetd);
        mISM = new IpSecManager(ipSecSrv);

        IpSecManager.UdpEncapsulationSocket encapSocket =
                    mISM.openUdpEncapsulationSocket(TEST_UDP_ENCAP_PORT);

        // Allocate and add SPI records in the IpSecService
        IpSecManager.SecurityParameterIndex outSpi =
                mISM.reserveSecurityParameterIndex(IpSecTransform.DIRECTION_OUT, local);
        IpSecManager.SecurityParameterIndex inSpi =
                mISM.reserveSecurityParameterIndex(
                        IpSecTransform.DIRECTION_IN, local, DROID_SPI);

        IpSecTransform transform = new IpSecTransform.Builder(mockContext)
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
        IpSecTransformResponse createTransformResp =
                ipSecSrv.createTransportModeTransform(transform.getIpSecConfig(), new Binder());

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        ipSecSrv.dump(null, pw, null);
        String result = sw.toString();

        assertTrue(result.contains("IpSecService Log"));
        assertTrue(result.contains("encryption=(cbc(aes) key length 32"));
        assertTrue(result.contains("authentication=(hmac(sha256) key length 32"));
        assertTrue(result.contains("mRemoteAddress='127.0.0.1'"));
    }
}

