/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.connectivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Resources;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.INetworkPolicyManager;
import android.net.INetworkStatsService;
import android.net.InterfaceConfiguration;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.INetworkManagementService;
import android.os.PersistableBundle;
import android.os.ResultReceiver;
import android.os.UserHandle;
import android.os.test.TestLooper;
import android.os.UserHandle;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.telephony.CarrierConfigManager;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.TelephonyManager;
import android.util.MutableBoolean;
import android.util.MutableInt;

import java.util.ArrayList;
import java.util.List;

import com.android.internal.util.test.BroadcastInterceptingContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TetheringTest {
    private static final String[] PROVISIONING_APP_NAME = {"some", "app"};

    @Mock private Context mContext;
    @Mock private ConnectivityManager mConnectivityManager;
    @Mock private INetworkManagementService mNMService;
    @Mock private INetworkStatsService mStatsService;
    @Mock private INetworkPolicyManager mPolicyManager;
    @Mock private MockableSystemProperties mSystemProperties;
    @Mock private Resources mResources;
    @Mock private UsbManager mUsbManager;
    @Mock private WifiManager mWifiManager;
    @Mock private CarrierConfigManager mCarrierConfigManager;
    @Mock private TelephonyManager mTelephonyManager;

    // Like so many Android system APIs, these cannot be mocked because it is marked final.
    // We have to use the real versions.
    private final PersistableBundle mCarrierConfig = new PersistableBundle();
    private final TestLooper mLooper = new TestLooper();
    private final String mTestIfname = "test_wlan0";

    private BroadcastInterceptingContext mServiceContext;
    private final MutableInt mTetherServiceResult =
            new MutableInt(ConnectivityManager.TETHER_ERROR_NO_ERROR);
    private Tethering mTethering;

    private class MockContext extends BroadcastInterceptingContext {
        MockContext(Context base) {
            super(base);
        }

        @Override
        public Resources getResources() { return mResources; }

        @Override
        public Object getSystemService(String name) {
            if (Context.CONNECTIVITY_SERVICE.equals(name)) return mConnectivityManager;
            if (Context.WIFI_SERVICE.equals(name)) return mWifiManager;
            if (Context.TELEPHONY_SERVICE.equals(name)) return mTelephonyManager;
            return super.getSystemService(name);
        }
    }

    @Before public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mResources.getStringArray(com.android.internal.R.array.config_tether_dhcp_range))
                .thenReturn(new String[0]);
        when(mResources.getStringArray(com.android.internal.R.array.config_tether_usb_regexs))
                .thenReturn(new String[0]);
        when(mResources.getStringArray(com.android.internal.R.array.config_tether_wifi_regexs))
                .thenReturn(new String[]{ "test_wlan\\d" });
        when(mResources.getStringArray(com.android.internal.R.array.config_tether_bluetooth_regexs))
                .thenReturn(new String[0]);
        when(mResources.getIntArray(com.android.internal.R.array.config_tether_upstream_types))
                .thenReturn(new int[0]);
        when(mNMService.listInterfaces())
                .thenReturn(new String[]{ "test_rmnet_data0", mTestIfname });
        when(mNMService.getInterfaceConfig(anyString()))
                .thenReturn(new InterfaceConfiguration());
        mServiceContext = new MockContext(mContext);
        setTetherServiceResult(ConnectivityManager.TETHER_ERROR_NO_ERROR);
        setupFakeTetherServiceResponses();

        mTethering = new Tethering(mServiceContext, mNMService, mStatsService, mPolicyManager,
                                   mLooper.getLooper(), mSystemProperties);
    }

    private void setupFakeTetherServiceResponses() {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                // TODO: Only verify services which match the right component name. This component
                // name is statically loaded from system resources in Tethering.
                Intent intent = (Intent) invocation.getArguments()[0];
                if (intent.getBooleanExtra(ConnectivityManager.EXTRA_RUN_PROVISION, false)) {
                    ResultReceiver receiver = (ResultReceiver) intent.getParcelableExtra(
                            ConnectivityManager.EXTRA_PROVISION_CALLBACK);
                    receiver.send(mTetherServiceResult.value, null);
                }

                return null;
            }
        }).when(mContext).startServiceAsUser(any(Intent.class), any(UserHandle.class));
    }

    private void setupForRequiredProvisioning() {
        // Produce some acceptable looking provision app setting if requested.
        when(mResources.getStringArray(
                com.android.internal.R.array.config_mobile_hotspot_provision_app))
                .thenReturn(PROVISIONING_APP_NAME);
        // Don't disable tethering provisioning unless requested.
        when(mSystemProperties.getBoolean(eq(Tethering.DISABLE_PROVISIONING_SYSPROP_KEY),
                                          anyBoolean())).thenReturn(false);
        // Act like the CarrierConfigManager is present and ready unless told otherwise.
        when(mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE))
                .thenReturn(mCarrierConfigManager);
        when(mCarrierConfigManager.getConfig()).thenReturn(mCarrierConfig);
        mCarrierConfig.putBoolean(CarrierConfigManager.KEY_REQUIRE_ENTITLEMENT_CHECKS_BOOL, true);
    }

    private void assertCarrierAllowsTethering(int expectedResult) {
        final MutableBoolean hasHitCallback = new MutableBoolean(false);
        ResultReceiver receiver = new ResultReceiver(null) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                assertEquals(expectedResult, resultCode);
                hasHitCallback.value = true;
            }
        };

        mTethering.runTetherProvisioningCheck(ConnectivityManager.TETHERING_WIFI, receiver);
        assertTrue(hasHitCallback.value);
    }

    private void simulateAvailableCellNetwork() {
        CellInfoGsm gsmCellInfo = new CellInfoGsm();
        gsmCellInfo.setCellSignalStrength(new CellSignalStrengthGsm());
        gsmCellInfo.setRegistered(true);
        List<CellInfo> networkList = new ArrayList<>();
        networkList.add(gsmCellInfo);
        when(mTelephonyManager.getAllCellInfo()).thenReturn(networkList);
    }

    /**
     * Sets the next result sent by TetherService when started.
     * @param tetherServiceResult The result of the provisioining check.
     *         (ConnectivityManager.TETHER_ERROR_NO_ERROR, etc).
     */
    private void setTetherServiceResult(int tetherServiceResult) {
        mTetherServiceResult.value = tetherServiceResult;
    }

    @Test
    public void canRequireProvisioning() {
        setupForRequiredProvisioning();
        assertTrue(mTethering.isTetherProvisioningRequired());
    }

    @Test
    public void toleratesCarrierConfigManagerMissing() {
        setupForRequiredProvisioning();
        when(mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE))
                .thenReturn(null);
        // Couldn't get the CarrierConfigManager, but still had a declared provisioning app.
        // We therefore still require provisioning.
        assertTrue(mTethering.isTetherProvisioningRequired());
    }

    @Test
    public void toleratesCarrierConfigMissing() {
        setupForRequiredProvisioning();
        when(mCarrierConfigManager.getConfig()).thenReturn(null);
        // We still have a provisioning app configured, so still require provisioning.
        assertTrue(mTethering.isTetherProvisioningRequired());
    }

    @Test
    public void provisioningNotRequiredWhenAppNotFound() {
        setupForRequiredProvisioning();
        when(mResources.getStringArray(
                com.android.internal.R.array.config_mobile_hotspot_provision_app))
                .thenReturn(null);
        assertTrue(!mTethering.isTetherProvisioningRequired());
        when(mResources.getStringArray(
                com.android.internal.R.array.config_mobile_hotspot_provision_app))
                .thenReturn(new String[] {"malformedApp"});
        assertTrue(!mTethering.isTetherProvisioningRequired());
    }

    private void sendWifiApStateChanged(int state) {
        final Intent intent = new Intent(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        intent.putExtra(WifiManager.EXTRA_WIFI_AP_STATE, state);
        mServiceContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    @Test
    public void workingLocalOnlyHotspot() throws Exception {
        when(mConnectivityManager.isTetheringSupported()).thenReturn(true);
        when(mWifiManager.setWifiApEnabled(any(WifiConfiguration.class), anyBoolean()))
                .thenReturn(true);

        // Emulate externally-visible WifiManager effects, causing the
        // per-interface state machine to start up, and telling us that
        // hotspot mode is to be started.
        mTethering.interfaceStatusChanged(mTestIfname, true);
        sendWifiApStateChanged(WifiManager.WIFI_AP_STATE_ENABLED);
        mLooper.dispatchAll();

        verify(mNMService, times(1)).listInterfaces();
        verify(mNMService, times(1)).getInterfaceConfig(mTestIfname);
        verify(mNMService, times(1))
                .setInterfaceConfig(eq(mTestIfname), any(InterfaceConfiguration.class));
        verify(mNMService, times(1)).tetherInterface(mTestIfname);
        verify(mNMService, times(1)).setIpForwardingEnabled(true);
        verify(mNMService, times(1)).startTethering(any(String[].class));
        verifyNoMoreInteractions(mNMService);
        // UpstreamNetworkMonitor will be started, and will register two callbacks:
        // a "listen all" and a "track default".
        verify(mConnectivityManager, times(1)).registerNetworkCallback(
                any(NetworkRequest.class), any(NetworkCallback.class), any(Handler.class));
        verify(mConnectivityManager, times(1)).registerDefaultNetworkCallback(
                any(NetworkCallback.class), any(Handler.class));
        // TODO: Figure out why this isn't exactly once, for sendTetherStateChangedBroadcast().
        verify(mConnectivityManager, atLeastOnce()).isTetheringSupported();
        verifyNoMoreInteractions(mConnectivityManager);

        // Emulate externally-visible WifiManager effects, when hotspot mode
        // is being torn down.
        sendWifiApStateChanged(WifiManager.WIFI_AP_STATE_DISABLED);
        mTethering.interfaceRemoved(mTestIfname);
        mLooper.dispatchAll();

        verify(mNMService, times(1)).untetherInterface(mTestIfname);
        // TODO: Why is {g,s}etInterfaceConfig() called more than once?
        verify(mNMService, atLeastOnce()).getInterfaceConfig(mTestIfname);
        verify(mNMService, atLeastOnce())
                .setInterfaceConfig(eq(mTestIfname), any(InterfaceConfiguration.class));
        verify(mNMService, times(1)).stopTethering();
        verify(mNMService, times(1)).setIpForwardingEnabled(false);
        verifyNoMoreInteractions(mNMService);
        // Asking for the last error after the per-interface state machine
        // has been reaped yields an unknown interface error.
        assertEquals(ConnectivityManager.TETHER_ERROR_UNKNOWN_IFACE,
                mTethering.getLastTetherError(mTestIfname));
    }

    @Test
    public void workingWifiTethering() throws Exception {
        when(mConnectivityManager.isTetheringSupported()).thenReturn(true);
        when(mWifiManager.setWifiApEnabled(any(WifiConfiguration.class), anyBoolean()))
                .thenReturn(true);

        // Emulate pressing the WiFi tethering button.
        mTethering.startTethering(ConnectivityManager.TETHERING_WIFI, null, false);
        mLooper.dispatchAll();
        verify(mWifiManager, times(1)).setWifiApEnabled(null, true);
        verifyNoMoreInteractions(mWifiManager);
        verifyNoMoreInteractions(mConnectivityManager);
        verifyNoMoreInteractions(mNMService);

        // Emulate externally-visible WifiManager effects, causing the
        // per-interface state machine to start up, and telling us that
        // tethering mode is to be started.
        mTethering.interfaceStatusChanged(mTestIfname, true);
        sendWifiApStateChanged(WifiManager.WIFI_AP_STATE_ENABLED);
        mLooper.dispatchAll();

        verify(mNMService, times(1)).listInterfaces();
        verify(mNMService, times(1)).getInterfaceConfig(mTestIfname);
        verify(mNMService, times(1))
                .setInterfaceConfig(eq(mTestIfname), any(InterfaceConfiguration.class));
        verify(mNMService, times(1)).tetherInterface(mTestIfname);
        verify(mNMService, times(1)).setIpForwardingEnabled(true);
        verify(mNMService, times(1)).startTethering(any(String[].class));
        verifyNoMoreInteractions(mNMService);
        // UpstreamNetworkMonitor will be started, and will register two callbacks:
        // a "listen all" and a "track default".
        verify(mConnectivityManager, times(1)).registerNetworkCallback(
                any(NetworkRequest.class), any(NetworkCallback.class), any(Handler.class));
        verify(mConnectivityManager, times(1)).registerDefaultNetworkCallback(
                any(NetworkCallback.class), any(Handler.class));
        // In tethering mode, in the default configuration, an explicit request
        // for a mobile network is also made.
        verify(mConnectivityManager, atLeastOnce()).getNetworkInfo(anyInt());
        verify(mConnectivityManager, times(1)).requestNetwork(
                any(NetworkRequest.class), any(NetworkCallback.class), eq(0), anyInt(),
                any(Handler.class));
        // TODO: Figure out why this isn't exactly once, for sendTetherStateChangedBroadcast().
        verify(mConnectivityManager, atLeastOnce()).isTetheringSupported();
        verifyNoMoreInteractions(mConnectivityManager);

        /////
        // We do not currently emulate any upstream being found.
        //
        // This is why there are no calls to verify mNMService.enableNat() or
        // mNMService.startInterfaceForwarding().
        /////

        // Emulate pressing the WiFi tethering button.
        mTethering.stopTethering(ConnectivityManager.TETHERING_WIFI);
        mLooper.dispatchAll();
        verify(mWifiManager, times(1)).setWifiApEnabled(null, false);
        verifyNoMoreInteractions(mWifiManager);
        verifyNoMoreInteractions(mConnectivityManager);
        verifyNoMoreInteractions(mNMService);

        // Emulate externally-visible WifiManager effects, when tethering mode
        // is being torn down.
        sendWifiApStateChanged(WifiManager.WIFI_AP_STATE_DISABLED);
        mTethering.interfaceRemoved(mTestIfname);
        mLooper.dispatchAll();

        verify(mNMService, times(1)).untetherInterface(mTestIfname);
        // TODO: Why is {g,s}etInterfaceConfig() called more than once?
        verify(mNMService, atLeastOnce()).getInterfaceConfig(mTestIfname);
        verify(mNMService, atLeastOnce())
                .setInterfaceConfig(eq(mTestIfname), any(InterfaceConfiguration.class));
        verify(mNMService, times(1)).stopTethering();
        verify(mNMService, times(1)).setIpForwardingEnabled(false);
        verifyNoMoreInteractions(mNMService);
        // Asking for the last error after the per-interface state machine
        // has been reaped yields an unknown interface error.
        assertEquals(ConnectivityManager.TETHER_ERROR_UNKNOWN_IFACE,
                mTethering.getLastTetherError(mTestIfname));
    }

    @Test
    public void runTetherProvisioningCheckProvisioningNotRequired() {
        when(mSystemProperties.getBoolean(eq(Tethering.DISABLE_PROVISIONING_SYSPROP_KEY),
                                          anyBoolean())).thenReturn(true);

        assertCarrierAllowsTethering(ConnectivityManager.TETHER_PROVISIONING_SUCCESS);
    }

    @Test
    public void runTetherProvisioningCheckProvisioningNoCellNetwork() {
        setupForRequiredProvisioning();
        when(mTelephonyManager.getAllCellInfo()).thenReturn(null);
        when(mTelephonyManager.getDataEnabled()).thenReturn(true);

        assertCarrierAllowsTethering(ConnectivityManager.TETHER_PROVISIONING_UNKNOWN);
    }

    @Test
    public void runTetherProvisioningCheckProvisioningCellDataDisabled() {
        setupForRequiredProvisioning();
        simulateAvailableCellNetwork();
        when(mTelephonyManager.getDataEnabled()).thenReturn(false);

        assertCarrierAllowsTethering(ConnectivityManager.TETHER_PROVISIONING_UNKNOWN);
    }

    @Test
    public void runTetherProvisioningCheckProvisioningDataNotConnected() {
        setupForRequiredProvisioning();
        simulateAvailableCellNetwork();
        when(mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI))
                .thenReturn(null);
        when(mTelephonyManager.getDataEnabled()).thenReturn(true);
        when(mTelephonyManager.getDataState()).thenReturn(TelephonyManager.DATA_DISCONNECTED);

        assertCarrierAllowsTethering(ConnectivityManager.TETHER_PROVISIONING_UNKNOWN);
    }

    @Test
    public void runTetherProvisioningCheckProvisioningSuccessOnCellData() {
        setupForRequiredProvisioning();
        simulateAvailableCellNetwork();
        when(mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI))
                .thenReturn(null);
        when(mTelephonyManager.getDataEnabled()).thenReturn(true);
        when(mTelephonyManager.getDataState()).thenReturn(TelephonyManager.DATA_CONNECTED);

        assertCarrierAllowsTethering(ConnectivityManager.TETHER_PROVISIONING_SUCCESS);
    }

    @Test
    public void runTetherProvisioningCheckProvisioningProvisioningFailed() {
        setupForRequiredProvisioning();
        simulateAvailableCellNetwork();
        when(mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI))
                .thenReturn(null);
        when(mTelephonyManager.getDataEnabled()).thenReturn(true);
        when(mTelephonyManager.getDataState()).thenReturn(TelephonyManager.DATA_CONNECTED);
        setTetherServiceResult(ConnectivityManager.TETHER_ERROR_PROVISION_FAILED);

        assertCarrierAllowsTethering(ConnectivityManager.TETHER_PROVISIONING_FAIL);
    }

    // TODO: Test that a request for hotspot mode doesn't interface with an
    // already operating tethering mode interface.
}
