/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.net.ConnectivityManager.TETHERING_WIFI;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.UserHandle;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.test.BroadcastInterceptingContext;
import com.android.tethering.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TetheringNotificationTest {
    @Mock private NotificationManager mNotificationManager;
    @Mock private Resources mResources;
    @Mock private Resources mResources2;
    @Mock private TypedArray mIconArray;
    @Mock private TypedArray mIconArray2;
    @Mock private TypedArray mNumberIconArray;
    @Mock private TypedArray mNumberIconArray2;
    @Mock private TypedArray mPauseIconArray;
    private Context mContext;
    private TetheringNotification mTetheringNotification;
    private static final int TEST_SUBID_1 = 0;
    private static final int TEST_SUBID_2 = 1;
    private static final String NOTIFICATION_TITTLE_NO_CLIENT = "Tethering active";
    private static final String NOTIFICATION_TITTLE_WITH_CLIENT = "Mobile hotspot active";
    private static final String NOTIFICATION_TITTLE_PAUSE = "Tethering pause";
    private static final String NOTIFICATION_TEXT_NO_CLIENT = "Tap here to set up.";
    private static final String NOTIFICATION_TEXT_WITH_CLIENT = "%d device connected.";
    private static final String NOTIFICATION_TEXT_PAUSE =
            "Tap here to set up. Tethering is paused during SRLTE call.";
    private static final String NOTIFICATION_TEXT_POWER_SAVING =
            "Tap here to set up. Power saving on.";
    private static final String NOTIFICATION_TEXT_C_AND_PS =
            "%d device connected. Power saving on.";

    private void setupResoures() {
        // Overlay resource(Has set tethering icon and number icon)
        when(mResources.obtainTypedArray(R.array.tethering_notification_icons))
                .thenReturn(mIconArray);
        when(mResources.obtainTypedArray(R.array.tethering_notification_number_icons))
                .thenReturn(mNumberIconArray);
        when(mResources.obtainTypedArray(R.array.tethering_notification_pause_icons))
                .thenReturn(mPauseIconArray);
        when(mIconArray.length()).thenReturn(1);
        when(mNumberIconArray.length()).thenReturn(1);
        when(mPauseIconArray.length()).thenReturn(1);
        when(mResources.getStringArray(R.array.tethering_downstream_combinations))
                .thenReturn(new String[0]);
        when(mResources.getString(R.string.tethering_notification_title_noclients))
                .thenReturn(NOTIFICATION_TITTLE_NO_CLIENT);
        when(mResources.getString(R.string.tethering_notification_pause_title))
                .thenReturn(NOTIFICATION_TITTLE_PAUSE);
        when(mResources.getString(R.string.tethering_notification_text_noclients))
                .thenReturn(NOTIFICATION_TEXT_NO_CLIENT);
        when(mResources.getString(R.string.tethering_notification_pause_text))
                .thenReturn(NOTIFICATION_TEXT_PAUSE);
        when(mResources.getString(R.string.tethering_notification_text_power_saving_noclients))
                .thenReturn(NOTIFICATION_TEXT_POWER_SAVING);
        when(mResources.getQuantityString(eq(R.plurals.tethering_notification_title), anyInt()))
                .thenReturn(NOTIFICATION_TITTLE_WITH_CLIENT);
        when(mResources.getQuantityString(eq(R.plurals.tethering_notification_text), anyInt()))
                .thenReturn(NOTIFICATION_TEXT_WITH_CLIENT);
        when(mResources.getQuantityString(
                eq(R.plurals.tethering_notification_text_power_saving), anyInt()))
                .thenReturn(NOTIFICATION_TEXT_C_AND_PS);

        // Default resource(No tethering icon or number icon set)
        when(mResources2.obtainTypedArray(R.array.tethering_notification_icons))
                .thenReturn(mIconArray2);
        when(mResources2.obtainTypedArray(R.array.tethering_notification_number_icons))
                .thenReturn(mNumberIconArray2);
        when(mIconArray2.length()).thenReturn(0);
        when(mNumberIconArray2.length()).thenReturn(0);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mContext = new BroadcastInterceptingContext(InstrumentationRegistry.getContext()) {
            @Override
            public Object getSystemService(String name) {
                switch (name) {
                    case Context.NOTIFICATION_SERVICE:
                        return mNotificationManager;
                    default:
                        return super.getSystemService(name);
                }
            }

            @Override
            public Resources getResources() {
                return mResources;
            }
        };

        setupResoures();
        mTetheringNotification = new TetheringNotification(mContext) {
            @Override
            protected Resources getResourcesForSubIdWrapper(Context c, int subId) {
                if (subId == TEST_SUBID_2) return mResources;
                return mResources2;
            }
        };
    }

    private String getNotificationTitle(Notification n) {
        return n.extras.getString(Notification.EXTRA_TITLE);
    }

    private String getNotificationText(Notification n) {
        return n.extras.getString(Notification.EXTRA_TEXT);
    }

    private void expectNotification(final String title, final String text) {
        verify(mNotificationManager, never()).cancelAsUser(any(), anyInt(), eq(UserHandle.ALL));

        final ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(Notification.class);
        verify(mNotificationManager, times(1))
                .notifyAsUser(any(), anyInt(), notificationCaptor.capture(), eq(UserHandle.ALL));

        final Notification notification = notificationCaptor.getValue();
        assertEquals(title, getNotificationTitle(notification));
        assertEquals(text, getNotificationText(notification));
    }

    private void expectClearNotification() {
        verify(mNotificationManager, times(1)).cancelAsUser(any(), anyInt(), eq(UserHandle.ALL));
        verify(mNotificationManager, never())
                .notifyAsUser(any(), anyInt(), any(), eq(UserHandle.ALL));
    }

    private void expectNoNotification() {
        verify(mNotificationManager, never()).cancelAsUser(any(), anyInt(), eq(UserHandle.ALL));
        verify(mNotificationManager, never())
                .notifyAsUser(any(), anyInt(), any(), eq(UserHandle.ALL));
    }

    @Test
    public void testNoNotificationWithDefaultResource() {
        mTetheringNotification.onSubscriptionIdChanged(TEST_SUBID_1);
        expectClearNotification();

        reset(mNotificationManager);
        mTetheringNotification.onDownstreamStarted(TETHERING_WIFI, TEST_SUBID_1);
        expectNoNotification();

        reset(mNotificationManager);
        mTetheringNotification.onPowerSavingChanged(true);
        expectNoNotification();

        reset(mNotificationManager);
        mTetheringNotification.onNetworkSuspendChanged(true);
        expectNoNotification();

        reset(mNotificationManager);
        mTetheringNotification.onNumClientsChanged(1);
        expectNoNotification();
    }

    @Test
    public void testNoNotificationWithInvalidSubscriptionId() {
        mTetheringNotification.onDownstreamStarted(TETHERING_WIFI, TEST_SUBID_1);
        expectClearNotification();

        reset(mNotificationManager);
        mTetheringNotification.onPowerSavingChanged(true);
        expectClearNotification();

        reset(mNotificationManager);
        mTetheringNotification.onNetworkSuspendChanged(true);
        expectClearNotification();

        reset(mNotificationManager);
        mTetheringNotification.onNumClientsChanged(1);
        expectClearNotification();
    }

    @Test
    public void testNoNotificationWithoutDownstream() {
        mTetheringNotification.onSubscriptionIdChanged(TEST_SUBID_1);
        expectClearNotification();

        reset(mNotificationManager);
        mTetheringNotification.onPowerSavingChanged(true);
        expectClearNotification();

        reset(mNotificationManager);
        mTetheringNotification.onNetworkSuspendChanged(true);
        expectClearNotification();

        reset(mNotificationManager);
        mTetheringNotification.onNumClientsChanged(1);
        expectClearNotification();
    }

    @Test
    public void testShowNotificationWithResourceOverlay() {
        mTetheringNotification.onSubscriptionIdChanged(TEST_SUBID_2);
        expectClearNotification();

        // No client and power saving off, show enable notification
        reset(mNotificationManager);
        mTetheringNotification.onDownstreamStarted(TETHERING_WIFI, TEST_SUBID_2);
        expectNotification(NOTIFICATION_TITTLE_NO_CLIENT, NOTIFICATION_TEXT_NO_CLIENT);

        // Power saving on
        reset(mNotificationManager);
        mTetheringNotification.onPowerSavingChanged(true);
        expectNotification(NOTIFICATION_TITTLE_NO_CLIENT, NOTIFICATION_TEXT_POWER_SAVING);

        // One client connected and power saving on.
        reset(mNotificationManager);
        final int numClient = 1;
        mTetheringNotification.onNumClientsChanged(numClient);
        expectNotification(
                NOTIFICATION_TITTLE_WITH_CLIENT,
                String.format(NOTIFICATION_TEXT_C_AND_PS, numClient));

        // One client connected and power saving off
        reset(mNotificationManager);
        mTetheringNotification.onPowerSavingChanged(false);
        expectNotification(
                NOTIFICATION_TITTLE_WITH_CLIENT,
                String.format(NOTIFICATION_TEXT_WITH_CLIENT, numClient));

        // Remove downstream, no notification showed.
        reset(mNotificationManager);
        mTetheringNotification.onDownstreamStopped(TETHERING_WIFI, TEST_SUBID_2);
        expectClearNotification();
    }

    @Test
    public void testShowPauseNotification() {
        mTetheringNotification.onSubscriptionIdChanged(TEST_SUBID_2);
        expectClearNotification();

        // Show enable notification
        reset(mNotificationManager);
        mTetheringNotification.onDownstreamStarted(TETHERING_WIFI, TEST_SUBID_2);
        expectNotification(NOTIFICATION_TITTLE_NO_CLIENT, NOTIFICATION_TEXT_NO_CLIENT);

        // In SRLTE call, show pause notification
        reset(mNotificationManager);
        mTetheringNotification.onNetworkSuspendChanged(true);
        expectNotification(NOTIFICATION_TITTLE_PAUSE, NOTIFICATION_TEXT_PAUSE);

        // Power saving on, still show pause notification
        reset(mNotificationManager);
        mTetheringNotification.onPowerSavingChanged(true);
        expectNotification(NOTIFICATION_TITTLE_PAUSE, NOTIFICATION_TEXT_PAUSE);

        // One client connected, still show pause notification
        reset(mNotificationManager);
        final int numClient = 1;
        mTetheringNotification.onNumClientsChanged(numClient);
        expectNotification(NOTIFICATION_TITTLE_PAUSE, NOTIFICATION_TEXT_PAUSE);

        // End of SRLTE call, show enable notification with client and power saving info.
        reset(mNotificationManager);
        mTetheringNotification.onNetworkSuspendChanged(false);
        expectNotification(
                NOTIFICATION_TITTLE_WITH_CLIENT,
                String.format(NOTIFICATION_TEXT_C_AND_PS, numClient));

        // Remove downstream, no notification showed.
        reset(mNotificationManager);
        mTetheringNotification.onDownstreamStopped(TETHERING_WIFI, TEST_SUBID_2);
        expectClearNotification();
    }

    @Test
    public void testSubscriptionIdChanged() {
        mTetheringNotification.onSubscriptionIdChanged(TEST_SUBID_1);
        expectClearNotification();

        // No notification with default resource.
        reset(mNotificationManager);
        mTetheringNotification.onDownstreamStarted(TETHERING_WIFI, TEST_SUBID_1);
        expectNoNotification();

        // Subscription id changed, no notification showed.
        reset(mNotificationManager);
        mTetheringNotification.onSubscriptionIdChanged(TEST_SUBID_2);
        expectClearNotification();

        // Remove downstream, no notification showed.
        reset(mNotificationManager);
        mTetheringNotification.onDownstreamStopped(TETHERING_WIFI, TEST_SUBID_1);
        expectClearNotification();

        // Show notification with overlay resource.
        reset(mNotificationManager);
        mTetheringNotification.onDownstreamStarted(TETHERING_WIFI, TEST_SUBID_2);
        expectNotification(NOTIFICATION_TITTLE_NO_CLIENT, NOTIFICATION_TEXT_NO_CLIENT);
    }
}
