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

package com.android.server.connectivity.tethering

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.net.ConnectivityManager.TETHERING_USB
import android.net.ConnectivityManager.TETHERING_WIFI
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.internal.util.test.BroadcastInterceptingContext
import com.android.server.connectivity.tethering.TetheringNotificationUpdater.NO_ICON_ID
import com.android.tethering.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

const val TEST_SUBID_1 = 0
const val TEST_SUBID_2 = 1
const val OVERLAY_TITTLE_NO_CLIENT = "Tethering active"
const val OVERLAY_TITTLE_WITH_CLIENT = "Mobile hotspot active"
const val OVERLAY_TITTLE_PAUSE = "Tethering pause"
const val OVERLAY_TEXT_NO_CLIENT = "Tap here to set up."
const val OVERLAY_TEXT_WITH_CLIENT = "%d devices connected."
const val OVERLAY_TEXT_PAUSE = "Tap here to set up. Tethering is paused during SRLTE call."
const val OVERLAY_TEXT_POWER_SAVING = "Tap here to set up. Power saving on."
const val OVERLAY_TEXT_C_AND_PS = "%d devices connected. Power saving on."

@RunWith(AndroidJUnit4::class)
@SmallTest
class TetheringNotificationUpdaterTest {
    // lateinit used here for mocks as they need to be reinitialized between each test and the test
    // should crash if they are used before being initialized.
    @Mock private lateinit var notificationManager: NotificationManager
    @Mock private lateinit var overlayRes: Resources
    @Mock private lateinit var overlayIcon: TypedArray
    @Mock private lateinit var overlayNumIcon: TypedArray
    @Mock private lateinit var overlayPauseIcon: TypedArray
    private lateinit var defaultRes: Resources
    private lateinit var notificationUpdater: TetheringNotificationUpdater

    fun setupOverlayResources() {
        doReturn(overlayIcon).`when`(overlayRes)
                .obtainTypedArray(R.array.tethering_notification_icons)
        doReturn(overlayNumIcon).`when`(overlayRes)
                .obtainTypedArray(R.array.tethering_notification_number_icons)
        doReturn(overlayPauseIcon).`when`(overlayRes)
                .obtainTypedArray(R.array.tethering_notification_pause_icons)
        doReturn(8).`when`(overlayIcon).length()
        doReturn(5).`when`(overlayNumIcon).length()
        doReturn(1).`when`(overlayPauseIcon).length()
        doReturn(emptyArray<String>()).`when`(overlayRes)
                .getStringArray(R.array.tethering_downstream_combinations)
        doReturn(OVERLAY_TITTLE_NO_CLIENT).`when`(overlayRes)
                .getString(R.string.tethering_notification_title_noclients)
        doReturn(OVERLAY_TITTLE_PAUSE).`when`(overlayRes)
                .getString(R.string.tethering_notification_pause_title)
        doReturn(OVERLAY_TEXT_NO_CLIENT).`when`(overlayRes)
                .getString(R.string.tethering_notification_text_noclients)
        doReturn(OVERLAY_TEXT_PAUSE).`when`(overlayRes)
                .getString(R.string.tethering_notification_pause_text)
        doReturn(OVERLAY_TEXT_POWER_SAVING).`when`(overlayRes)
                .getString(R.string.tethering_notification_text_power_saving_noclients)
        doReturn(OVERLAY_TITTLE_WITH_CLIENT).`when`(overlayRes)
                .getQuantityString(eq(R.plurals.tethering_notification_title), anyInt())
        doReturn(OVERLAY_TEXT_WITH_CLIENT).`when`(overlayRes)
                .getQuantityString(eq(R.plurals.tethering_notification_text), anyInt())
        doReturn(OVERLAY_TEXT_C_AND_PS).`when`(overlayRes)
                .getQuantityString(eq(R.plurals.tethering_notification_text_power_saving), anyInt())
        doReturn(R.drawable.stat_sys_tether_general).`when`(overlayIcon)
                .getResourceId(eq(1 shl TETHERING_WIFI), eq(NO_ICON_ID))
    }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        val context = spy(BroadcastInterceptingContext(InstrumentationRegistry.getContext())).also {
            doReturn(notificationManager).`when`(it).getSystemService(Context.NOTIFICATION_SERVICE)
        }

        defaultRes = context.getResources()
        doReturn(defaultRes).`when`(context).getResources()

        notificationUpdater = spy(TetheringNotificationUpdater(context)).also {
            doReturn(defaultRes).`when`(it).getResourcesForSubId(any(), eq(TEST_SUBID_1))
            doReturn(overlayRes).`when`(it).getResourcesForSubId(any(), eq(TEST_SUBID_2))
        }

        notificationUpdater.onActiveSubscriptionIdChanged(TEST_SUBID_1)
        expectClearNotification()
    }

    private fun Notification.title() = this.extras.getString(Notification.EXTRA_TITLE)
    private fun Notification.text() = this.extras.getString(Notification.EXTRA_TEXT)

    private fun expectNotification(title: String, text: String) {
        verify(notificationManager, never()).cancel(any(), anyInt())

        val notificationCaptor = ArgumentCaptor.forClass(Notification::class.java)
        verify(notificationManager, times(1)).notify(any(), anyInt(), notificationCaptor.capture())

        val notification = notificationCaptor.getValue()
        assertEquals(title, notification.title())
        assertEquals(text, notification.text())

        reset(notificationManager)
    }

    private fun expectClearNotification() {
        verify(notificationManager, times(1)).cancel(any(), anyInt())
        verify(notificationManager, never()).notify(any(), anyInt(), any())
        reset(notificationManager)
    }

    private fun expectNoNotification() {
        verify(notificationManager, never()).cancel(any(), anyInt())
        verify(notificationManager, never()).notify(any(), anyInt(), any())
        reset(notificationManager)
    }

    @Test
    fun testNoNotificationWithWiFiDownstream() {
        // Hotspot enabled, no notification showed.
        notificationUpdater.onDownstreamStarted(TETHERING_WIFI)
        expectClearNotification()

        // Power saving on, no notification showed.
        notificationUpdater.onPowerSavingChanged(true)
        expectClearNotification()

        // Power saving on, no notification showed.
        notificationUpdater.onNetworkSuspendChanged(true)
        expectClearNotification()

        // One client connected and power saving on, no notification showed.
        notificationUpdater.onConnectedClientsChanged(1)
        expectClearNotification()
    }

    @Test
    fun testNotificationWithDefaultResources() {
        val title = defaultRes.getString(R.string.tethered_notification_title)
        val message = defaultRes.getString(R.string.tethered_notification_message)

        // Hotspot enabled, no notification showed.
        notificationUpdater.onDownstreamStarted(TETHERING_WIFI)
        expectClearNotification()

        // Usb tethering enabled, showed enable notification
        notificationUpdater.onDownstreamStarted(TETHERING_USB)
        expectNotification(title, message)

        // Power saving on, showed enable notification
        notificationUpdater.onPowerSavingChanged(true)
        expectNotification(title, message)

        // One client connected and power saving on, showed enable notification
        notificationUpdater.onConnectedClientsChanged(1)
        expectNotification(title, message)

        // Network suspended, showed enable notification
        notificationUpdater.onNetworkSuspendChanged(true)
        expectNoNotification()

        // Network resumed, showed enable notification
        notificationUpdater.onNetworkSuspendChanged(false)
        expectNotification(title, message)

        // One client connected and power saving off, showed enable notification
        notificationUpdater.onPowerSavingChanged(false)
        expectNotification(title, message)

        // Remove wifi downstream, showed enable notification.
        notificationUpdater.onDownstreamStopped(TETHERING_WIFI)
        expectNotification(title, message)

        // No downstream, no notification showed.
        notificationUpdater.onDownstreamStopped(TETHERING_USB)
        expectClearNotification()
    }

    @Test
    fun testNotificationWithOverlayResources() {
        setupOverlayResources()

        // Hotspot enabled, no notification showed.
        notificationUpdater.onDownstreamStarted(TETHERING_WIFI)
        expectClearNotification()

        // Set overlay resource sub id, showed enable notification with overly text.
        notificationUpdater.onActiveSubscriptionIdChanged(TEST_SUBID_2)
        expectNotification(OVERLAY_TITTLE_NO_CLIENT, OVERLAY_TEXT_NO_CLIENT)

        // NetworkCapabilities doesn't have NET_CAPABILITY_NOT_SUSPENDED, showed pause notification.
        notificationUpdater.onNetworkSuspendChanged(true)
        expectNotification(OVERLAY_TITTLE_PAUSE, OVERLAY_TEXT_PAUSE)

        // Power saving on, showed pause notification
        notificationUpdater.onPowerSavingChanged(true)
        expectNotification(OVERLAY_TITTLE_PAUSE, OVERLAY_TEXT_PAUSE)

        // One client connected, showed pause notification
        val numClient = 2
        notificationUpdater.onConnectedClientsChanged(numClient)
        expectNotification(OVERLAY_TITTLE_PAUSE, OVERLAY_TEXT_PAUSE)

        // NetworkCapabilities has NET_CAPABILITY_NOT_SUSPENDED, showed enable notification with
        // client and power saving info.
        notificationUpdater.onNetworkSuspendChanged(false)
        expectNotification(
                OVERLAY_TITTLE_WITH_CLIENT,
                String.format(OVERLAY_TEXT_C_AND_PS, numClient))

        // Power saving off, showed enable notification with client.
        notificationUpdater.onPowerSavingChanged(false)
        expectNotification(
                OVERLAY_TITTLE_WITH_CLIENT,
                String.format(OVERLAY_TEXT_WITH_CLIENT, numClient))

        // Client disconnected, showed enable notification.
        notificationUpdater.onConnectedClientsChanged(0)
        expectNotification(OVERLAY_TITTLE_NO_CLIENT, OVERLAY_TEXT_NO_CLIENT)

        // No downstream, no notification showed.
        notificationUpdater.onDownstreamStopped(TETHERING_WIFI)
        expectClearNotification()
    }
}
