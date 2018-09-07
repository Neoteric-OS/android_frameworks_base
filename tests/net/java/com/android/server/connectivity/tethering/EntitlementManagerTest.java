/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.connectivity.tethering;

import static android.net.ConnectivityManager.TETHERING_BLUETOOTH;
import static android.net.ConnectivityManager.TETHERING_USB;
import static android.net.ConnectivityManager.TETHERING_WIFI;
import static android.net.ConnectivityManager.TETHER_ERROR_NO_ERROR;
import static android.net.ConnectivityManager.TETHER_ERROR_PROVISION_FAILED;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.net.util.SharedLog;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.test.TestLooper;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.telephony.CarrierConfigManager;

import com.android.internal.R;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.connectivity.MockableSystemProperties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class EntitlementManagerTest {

    private static final int EVENT_EM_UPDATE = 1;
    private static final String[] PROVISIONING_APP_NAME = {"some", "app"};
    private static final String PROVISIONING_NO_UI_APP_NAME = "no_ui_app";

    @Mock private CarrierConfigManager mCarrierConfigManager;
    @Mock private Context mContext;
    @Mock private ContentResolver mContent;
    @Mock private MockableSystemProperties mSystemProperties;
    @Mock private Resources mResources;
    @Mock private SharedLog mLog;

    // Like so many Android system APIs, these cannot be mocked because it is marked final.
    // We have to use the real versions.
    private final PersistableBundle mCarrierConfig = new PersistableBundle();
    private final TestLooper mLooper = new TestLooper();

    private TestStateMachine mSM;
    private MockEntitlementManager mEnMgr;

    public class MockEntitlementManager extends EntitlementManager {

        public int uiProvisionCount = 0;
        public int silentProvisionCount = 0;

        public MockEntitlementManager(Context ctx, StateMachine target,
                SharedLog log, int what, MockableSystemProperties systemProperties) {
            super(ctx, target, log, what, systemProperties);
        }

        @Override
        protected void runUiTetherProvisioning(int type) {
            uiProvisionCount++;

        }

        @Override
        protected void runSilentTetherProvisioning(int type) {
            silentProvisionCount++;
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getContentResolver()).thenReturn(mContent);
        when(mResources.getStringArray(R.array.config_tether_dhcp_range))
            .thenReturn(new String[0]);
        when(mResources.getStringArray(R.array.config_tether_usb_regexs))
            .thenReturn(new String[0]);
        when(mResources.getStringArray(R.array.config_tether_wifi_regexs))
            .thenReturn(new String[0]);
        when(mResources.getStringArray(R.array.config_tether_bluetooth_regexs))
            .thenReturn(new String[0]);
        // Produce some acceptable looking provision app setting if requested.
        when(mResources.getStringArray(R.array.config_mobile_hotspot_provision_app))
            .thenReturn(PROVISIONING_APP_NAME);
        when(mResources.getString(R.string.config_mobile_hotspot_provision_app_no_ui))
            .thenReturn(PROVISIONING_NO_UI_APP_NAME);
        when(mResources.getIntArray(R.array.config_tether_upstream_types))
            .thenReturn(new int[0]);
        when(mLog.forSubComponent(anyString())).thenReturn(mLog);
        when(mSystemProperties.getBoolean(eq(EntitlementManager.DISABLE_PROVISIONING_SYSPROP_KEY),
            anyBoolean())).thenReturn(false);
        // Act like the CarrierConfigManager is present and ready unless told otherwise.
        when(mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE))
            .thenReturn(mCarrierConfigManager);
        when(mCarrierConfigManager.getConfig()).thenReturn(mCarrierConfig);
        mCarrierConfig.putBoolean(CarrierConfigManager.KEY_REQUIRE_ENTITLEMENT_CHECKS_BOOL, true);

        mSM = new TestStateMachine();
        mEnMgr = new MockEntitlementManager(mContext, mSM, mLog, EVENT_EM_UPDATE,
                mSystemProperties);
        mEnMgr.updateConfiguration(new TetheringConfiguration(mContext, mLog));
    }

    @After
    public void tearDown() throws Exception {
        if (mSM != null) {
            mSM.quit();
            mSM = null;
        }
    }

    @Test
    public void canRequireProvisioning() {
        assertTrue(mEnMgr.isTetherProvisioningRequired());
    }

    @Test
    public void toleratesCarrierConfigManagerMissing() {
        when(mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE))
            .thenReturn(null);
        mEnMgr.updateConfiguration(new TetheringConfiguration(mContext, mLog));
        // Couldn't get the CarrierConfigManager, but still had a declared provisioning app.
        // Therefore provisioning still be required.
        assertTrue(mEnMgr.isTetherProvisioningRequired());
    }

    @Test
    public void toleratesCarrierConfigMissing() {
        when(mCarrierConfigManager.getConfig()).thenReturn(null);
        mEnMgr.updateConfiguration(new TetheringConfiguration(mContext, mLog));
        // We still have a provisioning app configured, so still require provisioning.
        assertTrue(mEnMgr.isTetherProvisioningRequired());
    }

    @Test
    public void provisioningNotRequiredWhenAppNotFound() {
        when(mResources.getStringArray(R.array.config_mobile_hotspot_provision_app))
            .thenReturn(null);
        mEnMgr.updateConfiguration(new TetheringConfiguration(mContext, mLog));
        assertTrue(!mEnMgr.isTetherProvisioningRequired());
        when(mResources.getStringArray(R.array.config_mobile_hotspot_provision_app))
            .thenReturn(new String[] {"malformedApp"});
        mEnMgr.updateConfiguration(new TetheringConfiguration(mContext, mLog));
        assertFalse(mEnMgr.isTetherProvisioningRequired());
    }

    @Test
    public void verifyPermissionresult() {
        mEnMgr.startProvisioningIfNeeded(TETHERING_WIFI, true);
        mLooper.dispatchAll();
        mEnMgr.addDownStreamMapping(TETHERING_WIFI, TETHER_ERROR_PROVISION_FAILED);
        mLooper.dispatchAll();
        assertFalse(mEnMgr.isMobileUpstreamPermitted());
        mEnMgr.stopProvisioningIfNeeded(TETHERING_WIFI);
        mLooper.dispatchAll();
        mEnMgr.startProvisioningIfNeeded(TETHERING_WIFI, true);
        mLooper.dispatchAll();
        mEnMgr.addDownStreamMapping(TETHERING_WIFI, TETHER_ERROR_NO_ERROR);
        mLooper.dispatchAll();
        assertTrue(mEnMgr.isMobileUpstreamPermitted());
    }

    @Test
    public void verifyPermissionIfAllNotApproved() {
        mEnMgr.startProvisioningIfNeeded(TETHERING_WIFI, true);
        mLooper.dispatchAll();
        mEnMgr.addDownStreamMapping(TETHERING_WIFI, TETHER_ERROR_PROVISION_FAILED);
        mLooper.dispatchAll();
        assertFalse(mEnMgr.isMobileUpstreamPermitted());
        mEnMgr.startProvisioningIfNeeded(TETHERING_USB, true);
        mLooper.dispatchAll();
        mEnMgr.addDownStreamMapping(TETHERING_USB, TETHER_ERROR_PROVISION_FAILED);
        mLooper.dispatchAll();
        assertFalse(mEnMgr.isMobileUpstreamPermitted());
        mEnMgr.startProvisioningIfNeeded(TETHERING_BLUETOOTH, true);
        mLooper.dispatchAll();
        mEnMgr.addDownStreamMapping(TETHERING_BLUETOOTH, TETHER_ERROR_PROVISION_FAILED);
        mLooper.dispatchAll();
        assertFalse(mEnMgr.isMobileUpstreamPermitted());
    }

    @Test
    public void verifyPermissionIfAnyApproved() {
        mEnMgr.startProvisioningIfNeeded(TETHERING_WIFI, true);
        mLooper.dispatchAll();
        mEnMgr.addDownStreamMapping(TETHERING_WIFI, TETHER_ERROR_NO_ERROR);
        mLooper.dispatchAll();
        assertTrue(mEnMgr.isMobileUpstreamPermitted());
        mLooper.dispatchAll();
        mEnMgr.startProvisioningIfNeeded(TETHERING_USB, true);
        mLooper.dispatchAll();
        mEnMgr.addDownStreamMapping(TETHERING_USB, TETHER_ERROR_PROVISION_FAILED);
        mLooper.dispatchAll();
        assertTrue(mEnMgr.isMobileUpstreamPermitted());
        mEnMgr.stopProvisioningIfNeeded(TETHERING_WIFI);
        mLooper.dispatchAll();
        assertFalse(mEnMgr.isMobileUpstreamPermitted());

    }

    @Test
    public void testRunTetherProvisioning() {
        // 1. start ui provisioning, default internet is mobile
        mEnMgr.setCellularDefaultInternetUp(true);
        mLooper.dispatchAll();
        mEnMgr.startProvisioningIfNeeded(TETHERING_USB, true);
        mLooper.dispatchAll();
        assertTrue(mEnMgr.uiProvisionCount == 1);
        mEnMgr.addDownStreamMapping(TETHERING_USB, TETHER_ERROR_PROVISION_FAILED);
        mLooper.dispatchAll();
        // 2. start no-ui provisioning
        mEnMgr.startProvisioningIfNeeded(TETHERING_WIFI, false);
        mLooper.dispatchAll();
        assertTrue(mEnMgr.silentProvisionCount == 1);
        mEnMgr.addDownStreamMapping(TETHERING_WIFI, TETHER_ERROR_PROVISION_FAILED);
        mLooper.dispatchAll();
        // 3. tear down mobile internet, then start ui provisioning
        mEnMgr.setCellularDefaultInternetUp(false);
        mLooper.dispatchAll();
        mEnMgr.startProvisioningIfNeeded(TETHERING_BLUETOOTH, true);
        mLooper.dispatchAll();
        assertTrue(mEnMgr.silentProvisionCount == 2);
        mEnMgr.addDownStreamMapping(TETHERING_BLUETOOTH, TETHER_ERROR_PROVISION_FAILED);
        mLooper.dispatchAll();
        // 4. switch default internet to mobile
        mEnMgr.setCellularDefaultInternetUp(true);
        mLooper.dispatchAll();
        assertTrue(mEnMgr.uiProvisionCount == 2);
        // 5. tear down mobile internet, then switch SIM
        mEnMgr.setCellularDefaultInternetUp(false);
        mLooper.dispatchAll();
        mEnMgr.reevaluateSimCardProvisioning();
        assertTrue(mEnMgr.silentProvisionCount == 5);
        mEnMgr.addDownStreamMapping(TETHERING_USB, TETHER_ERROR_PROVISION_FAILED);
        mLooper.dispatchAll();
        mEnMgr.addDownStreamMapping(TETHERING_WIFI, TETHER_ERROR_PROVISION_FAILED);
        mLooper.dispatchAll();
        mEnMgr.addDownStreamMapping(TETHERING_BLUETOOTH, TETHER_ERROR_PROVISION_FAILED);
        mLooper.dispatchAll();
        // 6. switch default internet back to mobile
        mEnMgr.setCellularDefaultInternetUp(true);
        mLooper.dispatchAll();
        assertTrue(mEnMgr.uiProvisionCount == 3);
        mEnMgr.addDownStreamMapping(TETHERING_BLUETOOTH, TETHER_ERROR_PROVISION_FAILED);
        mLooper.dispatchAll();
    }

    public class TestStateMachine extends StateMachine {
        public final ArrayList<Message> messages = new ArrayList<>();
        private final State
                mLoggingState = new EntitlementManagerTest.TestStateMachine.LoggingState();

        class LoggingState extends State {
            @Override public void enter() {
                messages.clear();
            }

            @Override public void exit() {
                messages.clear();
            }

            @Override public boolean processMessage(Message msg) {
                messages.add(msg);
                return true;
            }
        }

        public TestStateMachine() {
            super("EntitlementManagerTest.TestStateMachine", mLooper.getLooper());
            addState(mLoggingState);
            setInitialState(mLoggingState);
            super.start();
        }
    }
}
