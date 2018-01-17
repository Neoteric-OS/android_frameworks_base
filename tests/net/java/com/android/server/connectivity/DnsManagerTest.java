/*
 * Copyright (C) 2018, The Android Open Source Project
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

import static android.net.ConnectivityManager.PRIVATE_DNS_MODE_OFF;
import static android.net.ConnectivityManager.PRIVATE_DNS_MODE_OPPORTUNISTIC;
import static android.net.ConnectivityManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.net.INetd;
import android.net.LinkProperties;
import android.os.INetworkManagementService;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;

import com.android.internal.util.test.FakeSettingsProvider;
import com.android.server.connectivity.MockableSystemProperties;

import org.junit.runner.RunWith;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link DnsManager}.
 *
 * Build, install and run with:
 *  runtest frameworks-net -c com.android.server.connectivity.DnsManagerTest
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class DnsManagerTest {
    static final int TEST_NETID = 100;

    DnsManager mDnsManager;
    MockContentResolver mContentResolver;

    @Mock Context mCtx;
    @Mock INetd mNetd;
    @Mock INetworkManagementService mNMService;
    @Mock MockableSystemProperties mSystemProperties;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContentResolver = new MockContentResolver();
        mContentResolver.addProvider(Settings.AUTHORITY,
                new FakeSettingsProvider());
        when(mCtx.getContentResolver()).thenReturn(mContentResolver);
        when(mNMService.getNetdService()).thenReturn(mNetd);

        mDnsManager = new DnsManager(mCtx, mNMService, mSystemProperties);
    }

    @Test
    public void testPrivateDns() throws RemoteException {
        boolean[] hasValidated = {false, true, false};
        boolean[] noValidated = {false, false, false};
        Settings.Global.putString(mContentResolver,
                Settings.Global.PRIVATE_DNS_SPECIFIER, "dnstls.example.com");
        LinkProperties lp = new LinkProperties();

        // Private DNS in strict mode and a validated server exists
        Settings.Global.putString(mContentResolver,
                Settings.Global.PRIVATE_DNS_MODE,
                PRIVATE_DNS_MODE_PROVIDER_HOSTNAME);
        doReturn(hasValidated).when(mNetd).getPrivateDnsValidationStatuses(
                anyInt(), any());
        mDnsManager.updateLinkPropertiesPrivateDns(TEST_NETID, lp);
        assertTrue(lp.isPrivateDnsActive());
        assertEquals("dnstls.example.com", lp.getPrivateDnsServerName());

        // Private DNS in strict mode and no validated servers
        doReturn(noValidated).when(mNetd).getPrivateDnsValidationStatuses(
                anyInt(), any());
        mDnsManager.updateLinkPropertiesPrivateDns(TEST_NETID, lp);
        assertTrue(lp.isPrivateDnsActive());
        assertEquals("dnstls.example.com", lp.getPrivateDnsServerName());

        // Private DNS in opportunistic mode and a validated server exists
        Settings.Global.putString(mContentResolver,
                Settings.Global.PRIVATE_DNS_MODE,
                PRIVATE_DNS_MODE_OPPORTUNISTIC);
        doReturn(hasValidated).when(mNetd).getPrivateDnsValidationStatuses(
                anyInt(), any());
        mDnsManager.updateLinkPropertiesPrivateDns(TEST_NETID, lp);
        assertTrue(lp.isPrivateDnsActive());
        assertNull(lp.getPrivateDnsServerName());

        // Private DNS in opportunistic mode and no validated servers
        doReturn(noValidated).when(mNetd).getPrivateDnsValidationStatuses(
                anyInt(), any());
        mDnsManager.updateLinkPropertiesPrivateDns(TEST_NETID, lp);
        assertFalse(lp.isPrivateDnsActive());
        assertNull(lp.getPrivateDnsServerName());

        // Private DNS turned off and a validated server exists
        Settings.Global.putString(mContentResolver,
                Settings.Global.PRIVATE_DNS_MODE, PRIVATE_DNS_MODE_OFF);
        doReturn(hasValidated).when(mNetd).getPrivateDnsValidationStatuses(
                anyInt(), any());
        mDnsManager.updateLinkPropertiesPrivateDns(TEST_NETID, lp);
        assertFalse(lp.isPrivateDnsActive());
        assertNull(lp.getPrivateDnsServerName());

        // Private DNS turned off and no validated servers
        doReturn(noValidated).when(mNetd).getPrivateDnsValidationStatuses(
                anyInt(), any());
        mDnsManager.updateLinkPropertiesPrivateDns(TEST_NETID, lp);
        assertFalse(lp.isPrivateDnsActive());
        assertNull(lp.getPrivateDnsServerName());
    }
}
