package com.android.server;


import android.content.Context;

import android.net.ConnectivityManager;
import android.net.INetd;
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

    /**
     * Skeleton Netd class for mocking purpose
     */
    public class Netd implements INetd {
        public boolean isAlive() { return true; }
        public boolean firewallReplaceUidChain(String chainName, boolean isWhitelist, int[] uids) { return true; }
        public boolean bandwidthEnableDataSaver(boolean enable) { return true; }
        public void networkRejectNonSecureVpn(boolean add, UidRange[] uidRanges) {}
        public void socketDestroy(UidRange[] uidRanges, int[] exemptUids) {}
        public void setResolverConfiguration(int netId, String[] servers,
                    String[] domains, int[] params) {}
        public void getResolverInfo(int netId, String[] servers, String[] domains, int[] params, int[] stats) {}
        public boolean tetherApplyDnsInterfaces() { return true; }
        public void interfaceAddAddress(String ifName, String addrString, int prefixLength) {}
        public void interfaceDelAddress(String ifName, String addrString, int prefixLength) {}
        public void setProcSysNet(int family, int which, String ifname, String parameter, String value) {}
        public int getMetricsReportingLevel() { return 0; }
        public void setMetricsReportingLevel(int level) {}
        public int ipSecAllocateSpi(int transformId, int direction, String localAddress, String remoteAddress,
            int spi) { return 0; }
        public int ipSecAddSecurityAssociation(int transformId, int mode, int direction,
            String localAddress, String remoteAddress, long underlyingNetworkHandle,
            int spi, String authAlgo, byte[] authKey, int authTruncBits,
            String cryptAlgo, byte[] cryptKey, int cryptTruncBits,
            int encapType, int encapLocalPort, int encapRemotePort) { return 0; }
        public void ipSecDeleteSecurityAssociation(int transformId, int direction, String localAddress,
            String remoteAddress, int spi) {}
        public void ipSecApplyTransportModeTransform(FileDescriptor socket, int transformId, int direction,
            String localAddress, String remoteAddress, int spi) {}
        public void ipSecRemoveTransportModeTransform(FileDescriptor socket) {}
        public IBinder asBinder() { return new Binder(); }
    }

    private static final int DROID_SPI = 0xD1201D;
    private static final int TEST_UDP_ENCAP_PORT = 34567;

    private static final String IPV4_LOOPBACK = "127.0.0.1";

    IpSecTransformResponse tsfResp = new IpSecTransformResponse(IpSecManager.Status.OK, 0x1);
    IpSecUdpEncapResponse ipSecUdpEncapResp = null;
    private InetAddress local = null;

    Context mockContext = mock(Context.class);
    Netd mockNetd = mock(Netd.class);
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
}

