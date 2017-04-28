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

package com.android.server;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.test.TestLooper;
import android.content.Context;
import android.content.ContentResolver;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import com.android.server.NsdService.DaemonConnection;
import com.android.server.NsdService.DaemonConnectionSupplier;
import com.android.server.NsdService.NativeCallbackReceiver;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.ArgumentCaptor;
import java.util.Arrays;

// TODOs:
//  - test client disconnects
//  - test client can send requests and receive replies
//  - test NSD_ON ENABLE/DISABLED listening
//  - test in-flight requests are clean at client disconnection
@RunWith(AndroidJUnit4.class)
@SmallTest
public class NsdServiceTest {

    static final int PROTOCOL = NsdManager.PROTOCOL_DNS_SD;

    @Mock Context mContext;
    @Mock ContentResolver mResolver;
    @Mock NsdService.NsdSettings mSettings;
    @Mock DaemonConnection mDaemon;
    NativeCallbackReceiver mDaemonCallback;
    TestLooper mLooper;
    TestHandler mHandler;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mLooper = new TestLooper();
        mHandler = new TestHandler(mLooper.getLooper());
        when(mContext.getContentResolver()).thenReturn(mResolver);
    }

    @Test
    public void testClientsCanConnectAndDisconnect() {
        when(mSettings.isEnabled()).thenReturn(true);

        NsdService service = makeService();

        NsdManager client1 = connectClient(service);
        verify(mDaemon, timeout(100).times(1)).start();

        NsdManager client2 = connectClient(service);

        mLooper.startAutoDispatch();

        client1.disconnect();
        client2.disconnect();

        verify(mDaemon, timeout(500).times(1)).stop();
    }

    @Test
    public void testServiceRegistration() {
        // client 1 register service
        //
        // client 2 discover service
        //
        // client 2 gets service info
        //
        // client
    }

    @Test
    public void testClientRequestsAreGCedAtDisconnection() {
        when(mSettings.isEnabled()).thenReturn(true);
        when(mDaemon.execute(any(String.class))).thenReturn(true);

        NsdService service = makeService();
        NsdManager client = connectClient(service);

        verify(mDaemon, timeout(100).times(1)).start();

        NsdServiceInfo request = new NsdServiceInfo("a_name", "a_type");
        request.setPort(2201);

        mLooper.startAutoDispatch();

        // Client registration request
        NsdManager.RegistrationListener listener1 = mock(NsdManager.RegistrationListener.class);
        client.registerService(request, PROTOCOL, listener1);

        verifyDaemonCommand("register 2 a_name a_type 2201");
        //verify(mDaemon, timeout(100).times(1)).execute(

        // Client discovery request
        NsdManager.DiscoveryListener listener2 = mock(NsdManager.DiscoveryListener.class);
        client.discoverServices("a_type", PROTOCOL, listener2);
        verify(mDaemon, timeout(100).times(1)).execute("discover 3 a_type");

        // Client resolve request
        NsdManager.ResolveListener listener3 = mock(NsdManager.ResolveListener.class);
        client.resolveService(request, listener3);
        verify(mDaemon, timeout(100).times(1)).execute("resolve 4 a_name a_type local.");

        client.disconnect();

        verify(mDaemon, timeout(500).times(1)).stop();
        // checks that request are cleaned
    }

    NsdService makeService() {
        DaemonConnectionSupplier supplier = (callback) -> {
            mDaemonCallback = callback;
            return mDaemon;
        };
        NsdService service = new NsdService(mContext, mSettings, mHandler, supplier);
        verify(mDaemon, never()).execute(any(String.class));
        return service;
    }

    NsdManager connectClient(NsdService service) {
        mLooper.startAutoDispatch();
        NsdManager client = new NsdManager(mContext, service);
        mLooper.stopAutoDispatch();
        return client;
    }

    void verifyDaemonCommand(String want) {
        ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(mDaemon, timeout(100).times(1)).execute(commandCaptor.capture(), argumentsCaptor.capture());
        String got = commandCaptor.getValue() + Arrays.toString(argumentsCaptor.getValue());
        assertEquals(want, got);
    }

    public static class TestHandler extends Handler {
        public Message lastMessage;

        TestHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            lastMessage = obtainMessage();
            lastMessage.copyFrom(msg);
        }
    }
}
