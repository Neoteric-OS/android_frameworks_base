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
import android.os.UserHandle
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.internal.util.test.BroadcastInterceptingContext
import com.android.networkstack.tethering.R
import com.android.server.connectivity.tethering.TetheringNotificationUpdater.DOWNSTREAM_NONE
import com.android.server.connectivity.tethering.TetheringNotificationUpdater.NO_ICON_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
const val WIFI_DOWNSTREAM_TYPE_MASK = 1 shl TETHERING_WIFI
const val USB_DOWNSTREAM_TYPE_MASK = 1 shl TETHERING_USB
const val OVERLAY_TITTLE = "Tethering active"
const val OVERLAY_TITTLE_PAUSE = "Tethering pause"
const val OVERLAY_TEXT_NO_CLIENT = "Tap here to set up."
const val OVERLAY_TEXT_POWER_SAVING = "Tap here to set up. Power saving on."
const val OVERLAY_TEXT_PAUSE = "Tap here to set up. Tethering is paused during SRLTE call."
const val OVERLAY_TEXT_WITH_CLIENT = "%d devices connected."
const val OVERLAY_TEXT_C_AND_PS = "%d devices connected. Power saving on."

@RunWith(AndroidJUnit4::class)
@SmallTest
class TetheringNotificationUpdaterTest {
    // lateinit used here for mocks as they need to be reinitialized between each test and the test
    // should crash if they are used before being initialized.
    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var notificationManager: NotificationManager
    @Mock private lateinit var overlayRes: Resources
    @Mock private lateinit var overlayIcon: TypedArray
    @Mock private lateinit var overlayPauseIcon: TypedArray
    @Mock private lateinit var overlayNumIcon: TypedArray
    private lateinit var context: Context
    private lateinit var notificationUpdater: TetheringNotificationUpdater

    fun setupOverlayResources() {
        doReturn(overlayIcon).`when`(overlayRes)
                .obtainTypedArray(R.array.tethering_notification_icons)
        doReturn(overlayPauseIcon).`when`(overlayRes)
                .obtainTypedArray(R.array.tethering_notification_pause_icons)
        doReturn(overlayNumIcon).`when`(overlayRes)
                .obtainTypedArray(R.array.tethering_notification_number_icons)
        doReturn(8).`when`(overlayIcon).length()
        doReturn(1).`when`(overlayPauseIcon).length()
        doReturn(5).`when`(overlayNumIcon).length()
        val downstreamArray = arrayOf("", "Wi-Fi", "USB", "Wi-Fi/USB", "Bluetooth",
                "Wi-Fi/Bluetooth", "Bluetooth/USB", "Wi-Fi/BT/USB")
        doReturn(downstreamArray).`when`(overlayRes)
                .getStringArray(R.array.tethering_downstream_combinations)
        doReturn(OVERLAY_TITTLE).`when`(overlayRes)
                .getString(R.string.tethering_notification_title_noclients)
        doReturn(OVERLAY_TITTLE_PAUSE).`when`(overlayRes)
                .getString(R.string.tethering_notification_pause_title)
        doReturn(OVERLAY_TEXT_NO_CLIENT).`when`(overlayRes)
                .getString(R.string.tethering_notification_text_noclients)
        doReturn(OVERLAY_TEXT_POWER_SAVING).`when`(overlayRes)
                .getString(R.string.tethering_notification_text_power_saving_noclients)
        doReturn(OVERLAY_TEXT_PAUSE).`when`(overlayRes)
                .getString(R.string.tethering_notification_pause_text)
        doReturn(OVERLAY_TITTLE).`when`(overlayRes)
                .getQuantityString(eq(R.plurals.tethering_notification_title), anyInt())
        doReturn(OVERLAY_TEXT_WITH_CLIENT).`when`(overlayRes)
                .getQuantityString(eq(R.plurals.tethering_notification_text), anyInt())
        doReturn(OVERLAY_TEXT_C_AND_PS).`when`(overlayRes)
                .getQuantityString(eq(R.plurals.tethering_notification_text_power_saving), anyInt())
        doReturn(R.drawable.stat_sys_tether_general).`when`(overlayIcon)
                .getResourceId(eq(WIFI_DOWNSTREAM_TYPE_MASK), eq(NO_ICON_ID))
        doReturn(R.drawable.stat_sys_tether_general).`when`(overlayPauseIcon)
                .getResourceId(anyInt(), eq(NO_ICON_ID))
        doReturn(R.drawable.stat_sys_tether_general).`when`(overlayNumIcon)
                .getResourceId(anyInt(), eq(NO_ICON_ID))
    }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        context = spy(BroadcastInterceptingContext(InstrumentationRegistry.getContext())).also {
            doReturn(mockContext).`when`(it).createContextAsUser(eq(UserHandle.ALL), eq(0))
            doReturn(it).`when`(it).createContextAsUser(eq(UserHandle.CURRENT), eq(0))
            doReturn(notificationManager).`when`(mockContext)
                    .getSystemService(Context.NOTIFICATION_SERVICE)
        }

        notificationUpdater = spy(TetheringNotificationUpdater(context)).also {
            doReturn(overlayRes).`when`(it).getResourcesForSubId(any(), eq(TEST_SUBID_1))
        }
    }

    private fun Notification.title() = this.extras.getString(Notification.EXTRA_TITLE)
    private fun Notification.text() = this.extras.getString(Notification.EXTRA_TEXT)

    private fun expectShowNotification(title: String, text: String) {
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

    @Test
    fun testNoNotificationWithWiFiDownstream() {
        // Hotspot enabled, no notification showed.
        notificationUpdater.onDownstreamChanged(WIFI_DOWNSTREAM_TYPE_MASK)
        expectClearNotification()

        // Power saving on, no notification showed.
        notificationUpdater.onPowerSavingChanged(true)
        expectClearNotification()

        // Upstream network suspended, no notification showed.
        notificationUpdater.onUpstreamNetworkSuspended(true)
        expectClearNotification()

        // One client connected and power saving on, no notification showed.
        notificationUpdater.onConnectedClientsChanged(1)
        expectClearNotification()
    }

    @Test
    fun testNotificationWithDefaultResources() {
        val title = context.getResources().getString(R.string.tethered_notification_title)
        val message = context.getResources().getString(R.string.tethered_notification_message)

        // Hotspot enabled, no notification showed.
        notificationUpdater.onDownstreamChanged(WIFI_DOWNSTREAM_TYPE_MASK)
        expectClearNotification()

        // Usb tethering enabled, showed enable notification
        notificationUpdater.onDownstreamChanged(
                WIFI_DOWNSTREAM_TYPE_MASK or USB_DOWNSTREAM_TYPE_MASK)
        expectShowNotification(title, message)

        // Power saving on, showed enable notification
        notificationUpdater.onPowerSavingChanged(true)
        expectShowNotification(title, message)

        // Power saving off, showed enable notification
        notificationUpdater.onPowerSavingChanged(false)
        expectShowNotification(title, message)

        // Upstream network suspended, showed enable notification
        notificationUpdater.onUpstreamNetworkSuspended(true)
        expectShowNotification(title, message)

        // Upstream network resumed, showed enable notification
        notificationUpdater.onUpstreamNetworkSuspended(false)
        expectShowNotification(title, message)

        // One client connected, showed enable notification
        notificationUpdater.onConnectedClientsChanged(1)
        expectShowNotification(title, message)

        // Client disconnected, showed enable notification
        notificationUpdater.onConnectedClientsChanged(0)
        expectShowNotification(title, message)

        // Remove wifi downstream, showed enable notification.
        notificationUpdater.onDownstreamChanged(USB_DOWNSTREAM_TYPE_MASK)
        expectShowNotification(title, message)

        // No downstream, no notification showed.
        notificationUpdater.onDownstreamChanged(DOWNSTREAM_NONE)
        expectClearNotification()
    }

    @Test
    fun testNotificationWithOverlayResources() {
        setupOverlayResources()

        // Hotspot enabled, no notification showed.
        notificationUpdater.onDownstreamChanged(WIFI_DOWNSTREAM_TYPE_MASK)
        expectClearNotification()

        // Set overlay resource sub id, showed enable notification with overlay text.
        notificationUpdater.onActiveDataSubscriptionIdChanged(TEST_SUBID_1)
        expectShowNotification(OVERLAY_TITTLE, OVERLAY_TEXT_NO_CLIENT)

        // Power saving on, showed enable notification with overlay power saving text.
        notificationUpdater.onPowerSavingChanged(true)
        expectShowNotification(OVERLAY_TITTLE, OVERLAY_TEXT_POWER_SAVING)

        val numClient = 2
        // Two clients connected, showed pause notification
        notificationUpdater.onConnectedClientsChanged(numClient)
        expectShowNotification(OVERLAY_TITTLE, OVERLAY_TEXT_C_AND_PS.format(numClient))

        // Power saving off, showed enable notification with overlay text.
        notificationUpdater.onPowerSavingChanged(false)
        expectShowNotification(OVERLAY_TITTLE, OVERLAY_TEXT_WITH_CLIENT.format(numClient))

        // Client disconnected, showed enable notification.
        notificationUpdater.onConnectedClientsChanged(0)
        expectShowNotification(OVERLAY_TITTLE, OVERLAY_TEXT_NO_CLIENT)

        // Upstream network suspended, showed pause notification.
        notificationUpdater.onUpstreamNetworkSuspended(true)
        expectShowNotification(OVERLAY_TITTLE_PAUSE, OVERLAY_TEXT_PAUSE)

        // Upstream network resumed, showed enable notification with overlay text.
        notificationUpdater.onUpstreamNetworkSuspended(false)
        expectShowNotification(OVERLAY_TITTLE, OVERLAY_TEXT_NO_CLIENT)

        // No downstream, no notification showed.
        notificationUpdater.onDownstreamChanged(DOWNSTREAM_NONE)
        expectClearNotification()
    }

    @Test
    fun testWrongFormatText() {
        val title = context.getResources().getString(R.string.tethered_notification_title)
        assertTrue(title.equals(notificationUpdater.formatText(
                "", "", R.string.tethered_notification_title)))
    }

    @Test
    fun testDisallowTetherNotification() {
        val disallowTitle = context.getResources()
                .getString(R.string.disable_tether_notification_title)
        val disallowMessage = context.getResources()
                .getString(R.string.disable_tether_notification_message)

        // Hotspot enabled, no notification showed.
        notificationUpdater.onDownstreamChanged(WIFI_DOWNSTREAM_TYPE_MASK)
        expectClearNotification()

        // User restrictions on, showed disallow notification.
        notificationUpdater.onUserRestrictionsChanged(true)
        expectShowNotification(disallowTitle, disallowMessage)

        // Hotspot disabled, showed disallow notification.
        notificationUpdater.onDownstreamChanged(DOWNSTREAM_NONE)
        expectShowNotification(disallowTitle, disallowMessage)

        // User restrictions off, no notification showed.
        notificationUpdater.onUserRestrictionsChanged(false)
        expectClearNotification()
    }
}
