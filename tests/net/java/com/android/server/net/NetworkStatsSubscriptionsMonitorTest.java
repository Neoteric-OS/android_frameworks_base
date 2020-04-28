/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.net;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyRegistryManager;

import com.android.server.net.NetworkStatsSubscriptionsMonitor.Delegate;
import com.android.server.net.NetworkStatsSubscriptionsMonitor.RatTypeListener;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(JUnit4.class)
public final class NetworkStatsSubscriptionsMonitorTest {
    @Mock private Context mContext;
    @Mock private PhoneStateListener mPhoneStateListener;
    @Mock private SubscriptionManager mSubscriptionManager;
    @Mock private TelephonyManager mTelephonyManager;
    @Mock private TelephonyRegistryManager mTelephonyRegistryManager;
    @Mock private Delegate mDelegate;
    @Mock private Handler mHandler;
    @Mock private ServiceState mServiceState;

    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        when(mTelephonyManager.createForSubscriptionId(anyInt())).thenReturn(mTelephonyManager);

        when(mContext.getSystemService(eq(Context.TELEPHONY_SUBSCRIPTION_SERVICE)))
                .thenReturn(mSubscriptionManager);
        when(mContext.getSystemService(eq(Context.TELEPHONY_SERVICE)))
                .thenReturn(mTelephonyManager);
    }

    @Test
    public void testStartStop() {
        final NetworkStatsSubscriptionsMonitor monitor =
                new NetworkStatsSubscriptionsMonitor(mContext, mExecutor, mDelegate);

        // Verify that addOnSubscriptionsChangedListener() is never called before start().
        verify(mSubscriptionManager, never())
                .addOnSubscriptionsChangedListener(mExecutor, monitor);
        monitor.start();
        verify(mSubscriptionManager).addOnSubscriptionsChangedListener(mExecutor, monitor);

        // Verify that removeOnSubscriptionsChangedListener() is never called before stop()
        verify(mSubscriptionManager, never()).removeOnSubscriptionsChangedListener(monitor);
        monitor.stop();
        verify(mSubscriptionManager).removeOnSubscriptionsChangedListener(monitor);
    }

    private void setSubscriptionIdList(int[] list) {
        when(mSubscriptionManager.getActiveAndHiddenSubscriptionIdList())
                .thenReturn(list);
    }

    private void setRatTypeForSubscriber(int type) {
        when(mServiceState.getDataNetworkType()).thenReturn(type);
    }

    private void setRatTypeForSubscriber(List<RatTypeListener> listeners,
            int subId, int type) {
        when(mServiceState.getDataNetworkType()).thenReturn(type);
        for (RatTypeListener listener : listeners) {
            if (listener.getSubId() == subId) {
                listener.onServiceStateChanged(mServiceState);
            }
        }
    }

    private void setSubscriberIdForSub(int subId, String subscriberId) {
        when(mTelephonyManager.getSubscriberId(subId)).thenReturn(subscriberId);
    }

    @Test
    public void testOnSubscriptionsChangedAndRatTypeListener() {
        final int[] testSubIdList1 = {1, 2};
        final String testImsi1 = "466921234567890";
        final String testImsi2 = "466920987654321";
        final String testImsi3 = "466929999999999";

        setSubscriptionIdList(testSubIdList1);
        setSubscriberIdForSub(testSubIdList1[0], testImsi1);
        setSubscriberIdForSub(testSubIdList1[1], testImsi2);

        final NetworkStatsSubscriptionsMonitor monitor =
                new NetworkStatsSubscriptionsMonitor(mContext, mExecutor, mDelegate);
        final ArgumentCaptor<RatTypeListener> ratTypeListenerCaptor =
                ArgumentCaptor.forClass(RatTypeListener.class);

        monitor.start();
        monitor.onSubscriptionsChanged();
        verify(mTelephonyManager, times(2)).listen(ratTypeListenerCaptor.capture(),
                eq(PhoneStateListener.LISTEN_SERVICE_STATE));
        verify(mDelegate, never()).onCollapsedRatTypeChanged(any(),
                eq(TelephonyManager.NETWORK_TYPE_UNKNOWN));

        // Get NETWORK_TYPE_UNKNOWN before receive onServiceStateChanged()
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                monitor.getRatTypeForSubscriberId(testImsi1));
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                monitor.getRatTypeForSubscriberId(testImsi2));

        // Set RAT type for specific subscriber
        setRatTypeForSubscriber(ratTypeListenerCaptor.getAllValues(), 1,
                TelephonyManager.NETWORK_TYPE_UMTS);
        setRatTypeForSubscriber(ratTypeListenerCaptor.getAllValues(), 2,
                TelephonyManager.NETWORK_TYPE_LTE);

        // Verify RAT type after subscriber gets onServiceStateChanged() callback
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS,
                monitor.getRatTypeForSubscriberId(testImsi1));
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE,
                monitor.getRatTypeForSubscriberId(testImsi2));
        // Get NETWORK_TYPE_UNKNOWN since the subscriber does not register.
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                monitor.getRatTypeForSubscriberId(testImsi3));

        // Verify RAT type changes from NETWORK_TYPE_UMTS to NETWORK_TYPE_LTE
        setRatTypeForSubscriber(ratTypeListenerCaptor.getAllValues(), 1,
                TelephonyManager.NETWORK_TYPE_LTE);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE,
                monitor.getRatTypeForSubscriberId(testImsi1));

        // Verify handleRemoveRatTypeListener()
        final int[] testSubIdList2 = {1};
        setSubscriptionIdList(testSubIdList2);

        monitor.onSubscriptionsChanged();
        verify(mTelephonyManager).listen(ratTypeListenerCaptor.capture(),
                eq(PhoneStateListener.LISTEN_NONE));
        verify(mDelegate).onCollapsedRatTypeChanged(eq(testImsi2),
                eq(TelephonyManager.NETWORK_TYPE_UNKNOWN));

        monitor.stop();
        verify(mDelegate).onCollapsedRatTypeChanged(eq(testImsi1),
                eq(TelephonyManager.NETWORK_TYPE_UNKNOWN));
    }
}
