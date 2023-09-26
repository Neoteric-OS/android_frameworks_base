/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.NotificationManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.internal.net.VpnConfig;
import com.android.internal.net.VpnProfile;
import com.android.server.connectivity.Vpn;
import com.android.server.net.LockdownVpnTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class LockdownVpnTrackerTest {
    private static final Network TEST_NETWORK = new Network(123);
    private static final Network TEST_NETWORK_2 = new Network(124);

    // Use a context wrapper instead of a mock since LockdownVpnTracker builds notifications which
    // is tedious and currently unnecessary to mock.
    private Context mContext = new ContextWrapper(InstrumentationRegistry.getContext()) {
        @Override
        public Object getSystemService(String name) {
            if (Context.CONNECTIVITY_SERVICE.equals(name)) return mCm;
            if (Context.NOTIFICATION_SERVICE.equals(name)) return mNotificationManager;

            return super.getSystemService(name);
        }
    };
    @Mock private ConnectivityManager mCm;
    @Mock private Vpn mVpn;
    @Mock private NotificationManager mNotificationManager;
    @Mock private NetworkInfo mVpnNetworkInfo;
    @Mock private VpnConfig mVpnConfig;

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private VpnProfile mProfile;

    private VpnProfile createTestVpnProfile() {
        final String profileName = "testVpnProfile";
        final VpnProfile profile = new VpnProfile(profileName);
        profile.name = "My VPN";
        profile.server = "192.0.2.1";
        profile.dnsServers = "8.8.8.8";
        profile.type = VpnProfile.TYPE_IPSEC_XAUTH_PSK;

        return profile;
    }

    private NetworkCallback getDefaultNetworkCallback() {
        final ArgumentCaptor<NetworkCallback> callbackCaptor =
                ArgumentCaptor.forClass(NetworkCallback.class);
        verify(mCm).registerSystemDefaultNetworkCallback(callbackCaptor.capture(), eq(mHandler));
        return callbackCaptor.getValue();
    }

    private NetworkCallback getVpnNetworkCallback() {
        final ArgumentCaptor<NetworkCallback> callbackCaptor =
                ArgumentCaptor.forClass(NetworkCallback.class);
        verify(mCm).registerNetworkCallback(any(), callbackCaptor.capture(), eq(mHandler));
        return callbackCaptor.getValue();
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mHandlerThread = new HandlerThread("LockdownVpnTrackerTest");
        mHandlerThread.start();
        mHandler = mHandlerThread.getThreadHandler();

        doReturn(mVpnNetworkInfo).when(mVpn).getNetworkInfo();
        doReturn(false).when(mVpnNetworkInfo).isConnectedOrConnecting();
        doReturn(mVpnConfig).when(mVpn).getLegacyVpnConfig();

        mProfile = createTestVpnProfile();
    }

    @After
    public void tearDown() throws Exception {
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread.join();
        }
    }

    private LockdownVpnTracker initAndVerifyLockdownVpnTracker() {
        final LockdownVpnTracker lockdownVpnTracker =
                new LockdownVpnTracker(mContext, mHandler, mVpn, mProfile);
        lockdownVpnTracker.init();
        verify(mVpn).setEnableTeardown(false);
        verify(mVpn).setLockdown(true);
        verify(mCm).setLegacyLockdownVpnEnabled(true);
        verify(mVpn).stopVpnRunnerPrivileged();
        getDefaultNetworkCallback();
        getVpnNetworkCallback();
        verify(mNotificationManager).cancel(any(), eq(SystemMessage.NOTE_VPN_STATUS));

        return lockdownVpnTracker;
    }

    @Test
    public void testInit() {
        initAndVerifyLockdownVpnTracker();
    }

    @Test
    public void testShutdown() {
        final LockdownVpnTracker lockdownVpnTracker = initAndVerifyLockdownVpnTracker();
        final NetworkCallback defaultCallback = getDefaultNetworkCallback();
        final NetworkCallback vpnCallback = getVpnNetworkCallback();
        clearInvocations(mVpn, mCm, mNotificationManager);

        lockdownVpnTracker.shutdown();
        verify(mVpn).stopVpnRunnerPrivileged();
        verify(mVpn).setLockdown(false);
        verify(mCm).setLegacyLockdownVpnEnabled(false);
        verify(mNotificationManager).cancel(any(), eq(SystemMessage.NOTE_VPN_STATUS));
        verify(mVpn).setEnableTeardown(true);
        verify(mCm).unregisterNetworkCallback(defaultCallback);
        verify(mCm).unregisterNetworkCallback(vpnCallback);
    }

    @Test
    public void testDefaultLPChanged() {
        initAndVerifyLockdownVpnTracker();
        final NetworkCallback defaultCallback = getDefaultNetworkCallback();
        clearInvocations(mVpn, mCm, mNotificationManager);

        final LinkProperties lp = new LinkProperties();
        lp.setInterfaceName("rmnet0");

        defaultCallback.onLinkPropertiesChanged(TEST_NETWORK, lp);

        // Vpn is started
        verify(mVpn).startLegacyVpnPrivileged(mProfile, TEST_NETWORK, lp);
        verify(mNotificationManager).notify(any(), eq(SystemMessage.NOTE_VPN_STATUS), any());
    }

    @Test
    public void testDefaultLPChanged_sameNetworkAndIface() {
        initAndVerifyLockdownVpnTracker();
        final NetworkCallback defaultCallback = getDefaultNetworkCallback();
        final LinkProperties lp = new LinkProperties();
        lp.setInterfaceName("rmnet0");
        defaultCallback.onLinkPropertiesChanged(TEST_NETWORK, lp);
        clearInvocations(mVpn, mCm, mNotificationManager);

        // handleStateChangedLocked is not called on the same network even if the LinkProperties
        // change.
        lp.addLinkAddress(new LinkAddress("192.0.2.2/25"));
        defaultCallback.onLinkPropertiesChanged(TEST_NETWORK, lp);

        // Vpn still running.
        verify(mVpn, never()).stopVpnRunnerPrivileged();
        verify(mVpn, never()).startLegacyVpnPrivileged(mProfile, TEST_NETWORK, lp);
        verify(mNotificationManager, never()).cancel(any(), eq(SystemMessage.NOTE_VPN_STATUS));
    }

    @Test
    public void testDefaultLPChanged_newNetworkAndIface() {
        initAndVerifyLockdownVpnTracker();
        final NetworkCallback defaultCallback = getDefaultNetworkCallback();
        final LinkProperties cellLp = new LinkProperties();
        cellLp.setInterfaceName("rmnet0");
        defaultCallback.onLinkPropertiesChanged(TEST_NETWORK, cellLp);
        clearInvocations(mVpn, mCm, mNotificationManager);

        // New network and LinkProperties received
        final LinkProperties wifiLp = new LinkProperties();
        wifiLp.setInterfaceName("wlan0");
        defaultCallback.onLinkPropertiesChanged(TEST_NETWORK_2, wifiLp);

        // Vpn is restarted.
        verify(mVpn).stopVpnRunnerPrivileged();
        verify(mVpn).startLegacyVpnPrivileged(mProfile, TEST_NETWORK_2, wifiLp);
        verify(mNotificationManager, never()).cancel(any(), eq(SystemMessage.NOTE_VPN_STATUS));
        verify(mNotificationManager).notify(any(), eq(SystemMessage.NOTE_VPN_STATUS), any());
    }

    @Test
    public void testSystemDefaultLost() {
        initAndVerifyLockdownVpnTracker();
        final NetworkCallback defaultCallback = getDefaultNetworkCallback();
        final LinkProperties lp = new LinkProperties();
        lp.setInterfaceName("rmnet0");
        defaultCallback.onLinkPropertiesChanged(TEST_NETWORK, lp);
        clearInvocations(mVpn, mCm, mNotificationManager);

        defaultCallback.onLost(TEST_NETWORK);

        // Vpn is stopped
        verify(mVpn).stopVpnRunnerPrivileged();
        verify(mNotificationManager).cancel(any(), eq(SystemMessage.NOTE_VPN_STATUS));
    }
}
