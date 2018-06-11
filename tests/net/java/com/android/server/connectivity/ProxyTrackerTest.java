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

package com.android.server.connectivity;

import static android.provider.Settings.Global.GLOBAL_HTTP_PROXY_EXCLUSION_LIST;
import static android.provider.Settings.Global.GLOBAL_HTTP_PROXY_HOST;
import static android.provider.Settings.Global.GLOBAL_HTTP_PROXY_PORT;
import static android.provider.Settings.Global.HTTP_PROXY;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.AlarmManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Proxy;
import android.net.ProxyInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.test.mock.MockContentResolver;

import com.android.internal.util.test.FakeSettingsProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link ProxyTracker}.
 *
 * Build, install and run with:
 *  runtest frameworks-net -c com.android.server.connectivity.ProxyTrackerTest
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ProxyTrackerTest {
    private static final int TEST_PAC_EVENT = 2097;
    private static final String TEST_GLOBAL_HOST = "test.proxy.com";
    private static final int TEST_GLOBAL_PORT = 3128;
    private static final String TEST_GLOBAL_EXCLUSION_LIST = "excluded1,excluded2";
    private static final String TEST_DEPRECATED_PROXY_HOST = "deprecated.proxy.com";
    private static final int TEST_DEPRECATED_PROXY_PORT = 3129;
    private static final String TEST_DEPRECATED_HTTP_PROXY =
            TEST_DEPRECATED_PROXY_HOST + ":" + TEST_DEPRECATED_PROXY_PORT;

    @Mock private Context mContext;
    @Mock private Handler mHandler;
    @Mock private MockContentResolver mContentResolver;
    @Mock private AlarmManager mAlarmManager;
    private ProxyTracker mProxyTracker;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Settings.Global.clearProviderForTest();
        // Used to read and write settings
        mContentResolver = new MockContentResolver();
        mContentResolver.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        // Used by intent senders to make sure the package is who it claims to be
        final String testPackage = InstrumentationRegistry.getInstrumentation().getContext()
                .getPackageName();
        when(mContext.getPackageName()).thenReturn(testPackage);
        // Called by PacManager to schedule alarms against itself for exponential backoff
        // of downloading the PAC file.
        when(mContext.getSystemService(Context.ALARM_SERVICE)).thenReturn(mAlarmManager);
        mProxyTracker = new ProxyTracker(mContext, mHandler, TEST_PAC_EVENT);
    }

    // Test that the first info is equal to any of the auxInfos, in any direction.
    private void ensureAllEqual(@NonNull final ProxyInfo mainInfo,
            @NonNull final ProxyInfo[] auxInfos) {
        ensureEquality(true, mainInfo, auxInfos);
    }

    // Test that the first info is not equal to any of the auxInfos, in any direction.
    private void ensureAllNotEqual(@NonNull final ProxyInfo mainInfo,
            @NonNull final ProxyInfo[] auxInfos) {
        ensureEquality(false, mainInfo, auxInfos);
    }

    private void ensureEquality(final boolean equals, @NonNull final ProxyInfo mainInfo,
            @NonNull final ProxyInfo[] auxInfos) {
        for (final ProxyInfo auxInfo : auxInfos) {
            android.util.Log.e("EQ " + equals, "" + mainInfo + " <> " + auxInfo);
            assertEquals(equals, ProxyTracker.proxyInfoEqual(mainInfo, auxInfo));
            assertEquals(equals, ProxyTracker.proxyInfoEqual(auxInfo, mainInfo));
        }
    }

    @Test
    public void testProxyInfoEquals() {
        final ProxyInfo validProxy = ProxyInfo.buildDirectProxy("test.proxy.com", 3128);
        final ProxyInfo emptyHostProxy = ProxyInfo.buildDirectProxy("", 3128);
        final ProxyInfo nullHostProxy = ProxyInfo.buildDirectProxy(null, 0);
        final ProxyInfo validPacProxy = ProxyInfo.buildPacProxy(Uri.parse("http://test.proxy.com"));
        // Proxy set to localhost:-1 with Uri.EMPTY. This is different from a proxy without a host.
        final ProxyInfo emptyPacProxy = ProxyInfo.buildPacProxy(Uri.EMPTY);

        ensureAllNotEqual(validProxy, new ProxyInfo[] {
                emptyHostProxy, nullHostProxy, validPacProxy, emptyPacProxy});

        ensureAllEqual(emptyHostProxy, new ProxyInfo[] { nullHostProxy });
        ensureAllNotEqual(emptyHostProxy, new ProxyInfo[] { validPacProxy, emptyPacProxy });

        ensureAllNotEqual(nullHostProxy, new ProxyInfo[] { validPacProxy, emptyPacProxy });

        ensureAllNotEqual(validPacProxy, new ProxyInfo[] { emptyPacProxy });
    }

    private void writeGlobalProxySettings() {
        final ContentResolver cr = mContext.getContentResolver();
        Settings.Global.putString(cr, GLOBAL_HTTP_PROXY_HOST, TEST_GLOBAL_HOST);
        Settings.Global.putInt(cr, GLOBAL_HTTP_PROXY_PORT, TEST_GLOBAL_PORT);
        Settings.Global.putString(cr, GLOBAL_HTTP_PROXY_EXCLUSION_LIST, TEST_GLOBAL_EXCLUSION_LIST);
    }

    private void writeDeprecatedGlobalProxySettings() {
        final ContentResolver cr = mContext.getContentResolver();
        Settings.Global.putString(cr, HTTP_PROXY, TEST_DEPRECATED_HTTP_PROXY);
    }

    private interface IntentMatcher {
        boolean matchIntent(Intent i);
    }
    private void verifyIntentSentMatching(final IntentMatcher im) {
        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendStickyBroadcastAsUser(captor.capture(), eq(UserHandle.ALL));
        assertTrue(im.matchIntent(captor.getValue()));
        clearInvocations(mContext);
    }
    private void verifyIntentSentForProxy(final ProxyInfo info) {
        verifyIntentSentMatching(i -> info.equals(i.getParcelableExtra(Proxy.EXTRA_PROXY_INFO)));
    }

    @Test
    public void testLoadProxyFromSettings() {
        writeGlobalProxySettings();
        mProxyTracker.loadGlobalProxy();

        final ProxyInfo globalProxy = mProxyTracker.getGlobalProxy();
        assertEquals(globalProxy.getHost(), TEST_GLOBAL_HOST);
        assertEquals(globalProxy.getPort(), TEST_GLOBAL_PORT);
        assertArrayEquals(globalProxy.getExclusionList(), TEST_GLOBAL_EXCLUSION_LIST.split(","));

        final ProxyInfo defaultProxy = mProxyTracker.getDefaultProxy();
        assertEquals(globalProxy, defaultProxy);

        // TODO : why not send the broadcast here, but do in testDeprecatedProxyOverrides ?
        // The implementation behaves like this because the global settings are read first and
        // the old values are null, so no broadcast, but when the deprecated settings are read
        // they override something that did exist and the broadcast is sent. It is not sent
        // if only the deprecated proxy is sent either.
        verify(mContext, never()).sendStickyBroadcastAsUser(any(), any());
    }

    @Test
    public void testDeprecatedProxyOverridesGlobalProxy() {
        writeGlobalProxySettings();
        writeDeprecatedGlobalProxySettings();
        mProxyTracker.loadGlobalProxy();

        final ProxyInfo globalProxy = mProxyTracker.getGlobalProxy();
        assertEquals(globalProxy.getHost(), TEST_DEPRECATED_PROXY_HOST);
        assertEquals(globalProxy.getPort(), TEST_DEPRECATED_PROXY_PORT);
        assertArrayEquals(globalProxy.getExclusionList(), new String[]{""});

        final ProxyInfo defaultProxy = mProxyTracker.getDefaultProxy();
        assertEquals(globalProxy, defaultProxy);

        verify(mContext, times(1)).sendStickyBroadcastAsUser(any(), any());
    }

    @Test
    public void testSetProxySendsBroadcast() {
        mProxyTracker.loadGlobalProxy();

        final ProxyInfo manualProxy =
                ProxyInfo.buildDirectProxy(TEST_GLOBAL_HOST, TEST_GLOBAL_PORT);
        mProxyTracker.setDefaultProxy(manualProxy);
        assertEquals(manualProxy, mProxyTracker.getDefaultProxy());
        assertEquals(null, mProxyTracker.getGlobalProxy());

        verifyIntentSentForProxy(manualProxy);

        mProxyTracker.setDefaultProxy(null);
        assertEquals(null, mProxyTracker.getDefaultProxy());
        assertEquals(null, mProxyTracker.getGlobalProxy());

        verifyIntentSentForProxy(new ProxyInfo("", 0, ""));
    }
}
