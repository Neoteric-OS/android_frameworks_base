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

import static com.android.server.connectivity.NetworkNotificationManager.NotificationType.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;
import com.android.server.connectivity.NetworkNotificationManager.NotificationType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class NetworkNotificationManagerTest {

    static final NetworkCapabilities CELL_CAPABILITIES = new NetworkCapabilities();
    static final NetworkCapabilities WIFI_CAPABILITIES = new NetworkCapabilities();
    static final NetworkCapabilities VPN_CAPABILITIES = new NetworkCapabilities();
    static {
        CELL_CAPABILITIES.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        CELL_CAPABILITIES.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        WIFI_CAPABILITIES.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        WIFI_CAPABILITIES.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // Set the underyling network to wifi.
        VPN_CAPABILITIES.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        VPN_CAPABILITIES.addTransportType(NetworkCapabilities.TRANSPORT_VPN);
        VPN_CAPABILITIES.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        VPN_CAPABILITIES.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
    }

    @Mock Context mCtx;
    @Mock Resources mResources;
    @Mock PackageManager mPm;
    @Mock TelephonyManager mTelephonyManager;
    @Mock NotificationManager mNotificationManager;
    @Mock NetworkAgentInfo mWifiNai;
    @Mock NetworkAgentInfo mCellNai;
    @Mock NetworkAgentInfo mVpnNai;
    @Mock NetworkInfo mNetworkInfo;
    ArgumentCaptor<Notification> mCaptor;

    NetworkNotificationManager mManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mCaptor = ArgumentCaptor.forClass(Notification.class);
        mWifiNai.networkCapabilities = WIFI_CAPABILITIES;
        mWifiNai.networkInfo = mNetworkInfo;
        mCellNai.networkCapabilities = CELL_CAPABILITIES;
        mCellNai.networkInfo = mNetworkInfo;

        mVpnNai.networkCapabilities = VPN_CAPABILITIES;
        mVpnNai.networkInfo = mNetworkInfo;
        when(mCtx.getResources()).thenReturn(mResources);
        when(mCtx.getPackageManager()).thenReturn(mPm);
        when(mCtx.getApplicationInfo()).thenReturn(new ApplicationInfo());
        when(mNetworkInfo.getExtraInfo()).thenReturn("extra");
        when(mResources.getColor(anyInt(), any())).thenReturn(0xFF607D8B);

        mManager = new NetworkNotificationManager(mCtx, mTelephonyManager, mNotificationManager);
    }

    @Test
    public void testGetTransportType() {
        doReturn(true).when(mVpnNai).isVPN();
        assertEquals(NetworkCapabilities.TRANSPORT_WIFI, mManager.getTransportType(mWifiNai));
        assertEquals(NetworkCapabilities.TRANSPORT_CELLULAR, mManager.getTransportType(mCellNai));
        assertEquals(NetworkCapabilities.TRANSPORT_VPN, mManager.getTransportType(mVpnNai));
    }

    @Test
    public void testTitleOfPrivateDnsBroken() {
        final int id1 = 100;
        final int id2 = 101;
        final int id3 = 102;
        final String tag1 = NetworkNotificationManager.tagFor(id1);
        final String tag2 = NetworkNotificationManager.tagFor(id2);
        final String tag3 = NetworkNotificationManager.tagFor(id3);
        // Test the title of mobile data.
        mManager.showNotification(id1, PRIVATE_DNS_BROKEN, mCellNai, null, null, true);
        verify(mNotificationManager, times(1))
                .notifyAsUser(eq(tag1), eq(PRIVATE_DNS_BROKEN.eventId), any(), any());
        verify(mResources, times(1)).getString(R.string.mobile_no_internet);
        verify(mResources, times(1)).getString(R.string.private_dns_broken_detailed);

        // Test the title of wifi.
        mManager.showNotification(id2, PRIVATE_DNS_BROKEN, mWifiNai, null, null, true);
        verify(mNotificationManager, times(1))
                .notifyAsUser(eq(tag2), eq(PRIVATE_DNS_BROKEN.eventId), any(), any());
        verify(mResources, times(1)).getString(R.string.wifi_no_internet, eq(any()));
        verify(mResources, times(2)).getString(R.string.private_dns_broken_detailed);

        // Test the title of other networks.
        doReturn(true).when(mVpnNai).isVPN();
        mManager.showNotification(id3, PRIVATE_DNS_BROKEN, mVpnNai, null, null, true);
        verify(mNotificationManager, times(1))
                .notifyAsUser(eq(tag3), eq(PRIVATE_DNS_BROKEN.eventId), any(), any());
        verify(mResources, times(1)).getString(R.string.other_networks_no_internet);
        verify(mResources, times(3)).getString(R.string.private_dns_broken_detailed);
    }

    @Test
    public void testNotificationsShownAndCleared() {
        final int NETWORK_ID_BASE = 100;
        List<NotificationType> types = Arrays.asList(NotificationType.values());
        List<Integer> ids = new ArrayList<>(types.size());
        for (int i = 0; i < types.size(); i++) {
            ids.add(NETWORK_ID_BASE + i);
        }
        Collections.shuffle(ids);
        Collections.shuffle(types);

        for (int i = 0; i < ids.size(); i++) {
            mManager.showNotification(ids.get(i), types.get(i), mWifiNai, mCellNai, null, false);
        }

        List<Integer> idsToClear = new ArrayList<>(ids);
        Collections.shuffle(idsToClear);
        for (int i = 0; i < ids.size(); i++) {
            mManager.clearNotification(idsToClear.get(i));
        }

        for (int i = 0; i < ids.size(); i++) {
            final int id = ids.get(i);
            final int eventId = types.get(i).eventId;
            final String tag = NetworkNotificationManager.tagFor(id);
            verify(mNotificationManager, times(1)).notifyAsUser(eq(tag), eq(eventId), any(), any());
            verify(mNotificationManager, times(1)).cancelAsUser(eq(tag), eq(eventId), any());
        }
    }

    @Test
    public void testNoInternetNotificationsNotShownForCellular() {
        mManager.showNotification(100, NO_INTERNET, mCellNai, mWifiNai, null, false);
        mManager.showNotification(101, LOST_INTERNET, mCellNai, mWifiNai, null, false);

        verify(mNotificationManager, never()).notifyAsUser(any(), anyInt(), any(), any());

        mManager.showNotification(102, NO_INTERNET, mWifiNai, mCellNai, null, false);

        final int eventId = NO_INTERNET.eventId;
        final String tag = NetworkNotificationManager.tagFor(102);
        verify(mNotificationManager, times(1)).notifyAsUser(eq(tag), eq(eventId), any(), any());
    }

    @Test
    public void testNotificationsNotShownIfNoInternetCapability() {
        mWifiNai.networkCapabilities = new NetworkCapabilities();
        mWifiNai.networkCapabilities .addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        mManager.showNotification(102, NO_INTERNET, mWifiNai, mCellNai, null, false);
        mManager.showNotification(103, LOST_INTERNET, mWifiNai, mCellNai, null, false);
        mManager.showNotification(104, NETWORK_SWITCH, mWifiNai, mCellNai, null, false);

        verify(mNotificationManager, never()).notifyAsUser(any(), anyInt(), any(), any());
    }

    @Test
    public void testDuplicatedNotificationsNoInternetThenSignIn() {
        final int id = 101;
        final String tag = NetworkNotificationManager.tagFor(id);

        // Show first NO_INTERNET
        mManager.showNotification(id, NO_INTERNET, mWifiNai, mCellNai, null, false);
        verify(mNotificationManager, times(1))
                .notifyAsUser(eq(tag), eq(NO_INTERNET.eventId), any(), any());

        // Captive portal detection triggers SIGN_IN a bit later, clearing the previous NO_INTERNET
        mManager.showNotification(id, SIGN_IN, mWifiNai, mCellNai, null, false);
        verify(mNotificationManager, times(1))
                .cancelAsUser(eq(tag), eq(NO_INTERNET.eventId), any());
        verify(mNotificationManager, times(1))
                .notifyAsUser(eq(tag), eq(SIGN_IN.eventId), any(), any());

        // Network disconnects
        mManager.clearNotification(id);
        verify(mNotificationManager, times(1)).cancelAsUser(eq(tag), eq(SIGN_IN.eventId), any());
    }

    @Test
    public void testDuplicatedNotificationsSignInThenNoInternet() {
        final int id = 101;
        final String tag = NetworkNotificationManager.tagFor(id);

        // Show first SIGN_IN
        mManager.showNotification(id, SIGN_IN, mWifiNai, mCellNai, null, false);
        verify(mNotificationManager, times(1))
                .notifyAsUser(eq(tag), eq(SIGN_IN.eventId), any(), any());
        reset(mNotificationManager);

        // NO_INTERNET arrives after, but is ignored.
        mManager.showNotification(id, NO_INTERNET, mWifiNai, mCellNai, null, false);
        verify(mNotificationManager, never()).cancelAsUser(any(), anyInt(), any());
        verify(mNotificationManager, never()).notifyAsUser(any(), anyInt(), any(), any());

        // Network disconnects
        mManager.clearNotification(id);
        verify(mNotificationManager, times(1)).cancelAsUser(eq(tag), eq(SIGN_IN.eventId), any());
    }

    @Test
    public void testSameLevelNotifications() {
        final int id = 101;
        final String tag = NetworkNotificationManager.tagFor(id);

        mManager.showNotification(id, LOGGED_IN, mWifiNai, mCellNai, null, false);
        verify(mNotificationManager, times(1))
                .notifyAsUser(eq(tag), eq(LOGGED_IN.eventId), any(), any());

        mManager.showNotification(id, LOST_INTERNET, mWifiNai, mCellNai, null, false);
        verify(mNotificationManager, times(1))
                .notifyAsUser(eq(tag), eq(LOST_INTERNET.eventId), any(), any());
    }

    @Test
    public void testClearNotificationByType() {
        final int id = 101;
        final String tag = NetworkNotificationManager.tagFor(id);

        // clearNotification(int id, NotificationType notifyType) will check if given type is equal
        // to previous type or not. If they are equal then clear the notification; if they are not
        // equal then return.

        mManager.showNotification(id, LOGGED_IN, mWifiNai, mCellNai, null, false);
        verify(mNotificationManager, times(1))
                .notifyAsUser(eq(tag), eq(LOGGED_IN.eventId), any(), any());

        // Previous notification is LOGGED_IN and given type is LOGGED_IN too. The notification
        // should be cleared.
        mManager.clearNotification(id, LOGGED_IN);
        verify(mNotificationManager, times(1))
                .cancelAsUser(eq(tag), eq(LOGGED_IN.eventId), any());

        mManager.showNotification(id, LOGGED_IN, mWifiNai, mCellNai, null, false);
        verify(mNotificationManager, times(2))
                .notifyAsUser(eq(tag), eq(LOGGED_IN.eventId), any(), any());

        // LOST_INTERNET notification popup after LOGGED_IN notification.
        mManager.showNotification(id, LOST_INTERNET, mWifiNai, mCellNai, null, false);
        verify(mNotificationManager, times(1))
                .notifyAsUser(eq(tag), eq(LOST_INTERNET.eventId), any(), any());

        // Previous notification is LOST_INTERNET and given type is LOGGED_IN. The notification
        // shouldn't be cleared.
        mManager.clearNotification(id, LOGGED_IN);
        // LOST_INTERNET shouldn't be cleared.
        verify(mNotificationManager, never())
                .cancelAsUser(eq(tag), eq(LOST_INTERNET.eventId), any());
    }
}
