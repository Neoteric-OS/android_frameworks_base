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
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.util.CollectionUtils;
import com.android.server.net.NetworkStatsSubscriptionsMonitor.Delegate;
import com.android.server.net.NetworkStatsSubscriptionsMonitor.RatTypeListener;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(JUnit4.class)
public final class NetworkStatsSubscriptionsMonitorTest {
    @Mock private Context mContext;
    @Mock private PhoneStateListener mPhoneStateListener;
    @Mock private SubscriptionManager mSubscriptionManager;
    @Mock private TelephonyManager mTelephonyManager;
    @Mock private Delegate mDelegate;
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

    private List<Integer> mSubList = new ArrayList<>();
    private static final int ADD_SUBID = 0;
    private static final int REMOVE_SUBID = 1;

    private int[] updateSubList(int subId, int cmd) {
        switch(cmd) {
            case ADD_SUBID:
                if (!mSubList.contains(subId)) {
                    mSubList.add(subId);
                }
                break;
            case REMOVE_SUBID:
                for (int i = 0; i < mSubList.size(); i++) {
                    if (subId == mSubList.get(i)) {
                        mSubList.remove(i);
                    }
                }
                break;
        }

        final int[] list = new int[mSubList.size()];
        for (int i = 0; i < mSubList.size(); i++) {
            list[i] = mSubList.get(i);
        }
        return list;
    }

    private void setRatTypeForSub(List<RatTypeListener> listeners,
            int subId, int type) {
        when(mServiceState.getDataNetworkType()).thenReturn(type);
        final RatTypeListener match = CollectionUtils
                .find(listeners, it -> it.getSubId() == subId);
        if (match != null) {
            match.onServiceStateChanged(mServiceState);
        }
    }

    private void setSubscriberIdForSub(NetworkStatsSubscriptionsMonitor monitor,
            int subId, String subscriberId, int cmd) {
        final int[] subList = updateSubList(subId, cmd);
        when(mSubscriptionManager.getActiveAndHiddenSubscriptionIdList())
                .thenReturn(subList);
        when(mTelephonyManager.getSubscriberId(subId)).thenReturn(subscriberId);
        monitor.onSubscriptionsChanged();
    }

    private void assertRatTypeChangeForSub(NetworkStatsSubscriptionsMonitor monitor,
            String subscriberId, int ratType) {
        assertEquals(monitor.getRatTypeForSubscriberId(subscriberId), ratType);
        verify(mDelegate).onCollapsedRatTypeChanged(eq(subscriberId), eq(ratType));
    }

    @Test
    public void testOnSubscriptionsChangedAndRatTypeListener() {
        final int TEST_SUBID1 = 3;
        final int TEST_SUBID2 = 5;
        final String TEST_IMSI1 = "466921234567890";
        final String TEST_IMSI2 = "466920987654321";
        final String TEST_IMSI3 = "466929999999999";

        final NetworkStatsSubscriptionsMonitor monitor =
                new NetworkStatsSubscriptionsMonitor(mContext, mExecutor, mDelegate);
        final ArgumentCaptor<RatTypeListener> ratTypeListenerCaptor =
                ArgumentCaptor.forClass(RatTypeListener.class);

        monitor.start();
        // Insert sim1
        setSubscriberIdForSub(monitor, TEST_SUBID1, TEST_IMSI1, ADD_SUBID);
        // Insert sim2
        setSubscriberIdForSub(monitor, TEST_SUBID2, TEST_IMSI2, ADD_SUBID);
        verify(mTelephonyManager, times(2)).listen(ratTypeListenerCaptor.capture(),
                eq(PhoneStateListener.LISTEN_SERVICE_STATE));
        verify(mDelegate, never()).onCollapsedRatTypeChanged(any(),
                eq(TelephonyManager.NETWORK_TYPE_UNKNOWN));

        // Get NETWORK_TYPE_UNKNOWN before receive onServiceStateChanged()
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                monitor.getRatTypeForSubscriberId(TEST_IMSI1));
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                monitor.getRatTypeForSubscriberId(TEST_IMSI2));

        // Set RAT type of sim1 to UMTS and set RAT type of sim2 to LTE.
        setRatTypeForSub(ratTypeListenerCaptor.getAllValues(), TEST_SUBID1,
                TelephonyManager.NETWORK_TYPE_UMTS);
        setRatTypeForSub(ratTypeListenerCaptor.getAllValues(), TEST_SUBID2,
                TelephonyManager.NETWORK_TYPE_LTE);

        // Verify RAT type after subscriber gets onServiceStateChanged() callback
        assertRatTypeChangeForSub(monitor, TEST_IMSI1,
                TelephonyManager.NETWORK_TYPE_UMTS);
        assertRatTypeChangeForSub(monitor, TEST_IMSI2,
                TelephonyManager.NETWORK_TYPE_LTE);

        // Get NETWORK_TYPE_UNKNOWN since the subscriber does not register.
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                monitor.getRatTypeForSubscriberId(TEST_IMSI3));

        // Remove sim2.
        // Verify that callbacks are fired and RAT type is correct after removing sim.
        setSubscriberIdForSub(monitor, TEST_SUBID2, TEST_IMSI2, REMOVE_SUBID);
        verify(mTelephonyManager).listen(ratTypeListenerCaptor.capture(),
                eq(PhoneStateListener.LISTEN_NONE));
        verify(mDelegate).onCollapsedRatTypeChanged(eq(TEST_IMSI2),
                eq(TelephonyManager.NETWORK_TYPE_UNKNOWN));
        // Verify that sim1 does not get any callback and the RAT type is not changed.
        verify(mDelegate, never()).onCollapsedRatTypeChanged(eq(TEST_IMSI1),
                eq(TelephonyManager.NETWORK_TYPE_UNKNOWN));
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS,
                monitor.getRatTypeForSubscriberId(TEST_IMSI1));

        monitor.stop();
        // Verify stop monitoring subscription changes and all listeners are removed
        assertRatTypeChangeForSub(monitor, TEST_IMSI1,
                TelephonyManager.NETWORK_TYPE_UNKNOWN);
    }
}
