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

package com.android.server.connectivity.tethering

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.net.ConnectivityManager.TETHERING_BLUETOOTH
import android.net.ConnectivityManager.TETHERING_USB
import android.net.ConnectivityManager.TETHERING_WIFI
import android.os.UserHandle
import android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.internal.util.test.BroadcastInterceptingContext
import com.android.networkstack.tethering.R
import com.android.server.connectivity.tethering.TetheringNotificationUpdater.DOWNSTREAM_NONE
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

const val TEST_SUBID = 0
const val WIFI_ICON_ID = 1
const val USB_ICON_ID = 2
const val BT_ICON_ID = 3
const val GENERAL_ICON_ID = 4
const val WIFI_MASK = 1 shl TETHERING_WIFI
const val USB_MASK = 1 shl TETHERING_USB
const val BT_MASK = 1 shl TETHERING_BLUETOOTH
const val TITTLE = "Tethering active"
const val MESSAGE = "Tap here to set up."
const val TEST_TITTLE = "Hotspot active"
const val TEST_MESSAGE = "Tap to set up hotspot."
const val TEST_MESSAGE_POWER_SAVE = "Tap to set up hotspot. Power saving on"

@RunWith(AndroidJUnit4::class)
@SmallTest
class TetheringNotificationUpdaterTest {
    // lateinit used here for mocks as they need to be reinitialized between each test and the test
    // should crash if they are used before being initialized.
    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var notificationManager: NotificationManager
    @Mock private lateinit var defaultResources: Resources
    @Mock private lateinit var testResources: Resources
    @Mock private lateinit var defaultArray: TypedArray
    @Mock private lateinit var testArray: TypedArray
    private lateinit var context: Context
    private lateinit var notificationUpdater: TetheringNotificationUpdater

    private fun setupResources() {
        doReturn(defaultArray).`when`(defaultResources)
                .obtainTypedArray(R.array.tethering_notification_icons)
        doReturn(testArray).`when`(testResources)
                .obtainTypedArray(R.array.tethering_notification_icons)
        val defaultIcons = arrayOf(
                "USB;android.test:drawable/usb", "BT;android.test:drawable/bluetooth",
                "WIFI|BT;android.test:drawable/general", "WIFI|USB;android.test:drawable/general",
                "USB|BT;android.test:drawable/general", "WIFI|USB|BT;android.test:drawable/general")
        val testIcons = arrayOf("WIFI;android.test:drawable/wifi")
        doReturn(defaultIcons.size).`when`(defaultArray).length()
        doReturn(testIcons.size).`when`(testArray).length()
        for (i in defaultIcons.indices) doReturn(defaultIcons[i]).`when`(defaultArray).getString(i)
        for (i in testIcons.indices) doReturn(testIcons[i]).`when`(testArray).getString(i)
        doReturn(TITTLE).`when`(defaultResources).getString(R.string.tethered_notification_title)
        doReturn(MESSAGE).`when`(defaultResources).getString(R.string.tethered_notification_message)
        doReturn(MESSAGE).`when`(defaultResources)
                .getString(R.string.tethered_notification_message_power_saving)
        doReturn(TEST_TITTLE).`when`(testResources).getString(R.string.tethered_notification_title)
        doReturn(TEST_MESSAGE).`when`(testResources)
                .getString(R.string.tethered_notification_message)
        doReturn(TEST_MESSAGE_POWER_SAVE).`when`(testResources)
                .getString(R.string.tethered_notification_message_power_saving)
        doReturn(USB_ICON_ID).`when`(defaultResources)
                .getIdentifier(eq("android.test:drawable/usb"), any(), any())
        doReturn(BT_ICON_ID).`when`(defaultResources)
                .getIdentifier(eq("android.test:drawable/bluetooth"), any(), any())
        doReturn(GENERAL_ICON_ID).`when`(defaultResources)
                .getIdentifier(eq("android.test:drawable/general"), any(), any())
        doReturn(WIFI_ICON_ID).`when`(testResources)
                .getIdentifier(eq("android.test:drawable/wifi"), any(), any())
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
            doReturn(defaultResources).`when`(it)
                    .getResourcesForSubId(any(), eq(INVALID_SUBSCRIPTION_ID))
            doReturn(testResources).`when`(it).getResourcesForSubId(any(), eq(TEST_SUBID))
        }
        setupResources()
    }

    private fun Notification.title() = this.extras.getString(Notification.EXTRA_TITLE)
    private fun Notification.text() = this.extras.getString(Notification.EXTRA_TEXT)

    private fun expectShowNotification(iconId: Int, title: String, text: String) {
        verify(notificationManager, never()).cancel(any(), anyInt())

        val notificationCaptor = ArgumentCaptor.forClass(Notification::class.java)
        verify(notificationManager, times(1)).notify(any(), anyInt(), notificationCaptor.capture())

        val notification = notificationCaptor.getValue()
        assertEquals(iconId, notification.smallIcon.resId)
        assertEquals(title, notification.title())
        assertEquals(text, notification.text())

        reset(notificationManager)
    }

    private fun expectClearNotification() {
        verify(notificationManager, times(1)).cancel(any(), anyInt())
        verify(notificationManager, never()).notify(any(), anyInt(), any())
        reset(notificationManager)
    }

    private fun expectNothing() {
        verify(notificationManager, never()).cancel(any(), anyInt())
        verify(notificationManager, never()).notify(any(), anyInt(), any())
    }

    private fun assertNotification(
        mask: Int,
        showNoitification: Boolean = false,
        iconId: Int = 0,
        title: String = "",
        message: String = ""
    ) {
        notificationUpdater.onDownstreamChanged(mask)
        when (showNoitification) {
            true -> expectShowNotification(iconId, title, message)
            else -> expectClearNotification()
        }
    }

    @Test
    fun testNotificationWithDownstreamChanged() {
        // Hotspot enabled, no notification showed.
        assertNotification(WIFI_MASK)

        // Same downstream changed, nothing happened.
        notificationUpdater.onDownstreamChanged(WIFI_MASK)
        expectNothing()

        // Usb tethering enabled, showed enable notification
        assertNotification(WIFI_MASK or USB_MASK, true, GENERAL_ICON_ID, TITTLE, MESSAGE)

        // Remove wifi downstream, showed enable notification.
        assertNotification(USB_MASK, true, USB_ICON_ID, TITTLE, MESSAGE)

        // No downstream, no notification showed.
        assertNotification(DOWNSTREAM_NONE)
    }

    @Test
    fun testNotificationWithActiveDataSubscriptionIdChanged() {
        // Same subId changed, nothing happened.
        notificationUpdater.onActiveDataSubscriptionIdChanged(INVALID_SUBSCRIPTION_ID)
        expectNothing()

        // Hotspot enabled, no notification showed with default resource.
        assertNotification(WIFI_MASK)

        // Usb tethering enabled, showed enable notification with default resource
        assertNotification(WIFI_MASK or USB_MASK, true, GENERAL_ICON_ID, TITTLE, MESSAGE)

        // Set test sub id, cleared notification with test resource.
        notificationUpdater.onActiveDataSubscriptionIdChanged(TEST_SUBID)
        expectClearNotification()

        // Remove usb downstream, showed enable notification with test resource.
        assertNotification(WIFI_MASK, true, WIFI_ICON_ID, TEST_TITTLE, TEST_MESSAGE)

        // No downstream, no notification showed.
        assertNotification(DOWNSTREAM_NONE)
    }

    private fun assertIconNumbers(number: Int, configs: Array<String?>) {
        doReturn(configs.size).`when`(defaultArray).length()
        for (i in configs.indices) doReturn(configs[i]).`when`(defaultArray).getString(i)
        assertEquals(number,
                notificationUpdater.getIcons(R.array.tethering_notification_icons).size)
    }

    @Test
    fun testGetIcons() {
        assertIconNumbers(0, arrayOfNulls<String>(0))
        assertIconNumbers(0, arrayOf(null, ""))
        assertIconNumbers(2,
                arrayOf(";", "WIFI", "1;2", " USB,; ", " BT ; android.test:drawable/xxx "))
    }

    @Test
    fun testGetDownstreamTypesMask() {
        assertEquals(DOWNSTREAM_NONE, notificationUpdater.getDownstreamTypesMask(""))
        assertEquals(DOWNSTREAM_NONE, notificationUpdater.getDownstreamTypesMask("1"))
        assertEquals(DOWNSTREAM_NONE, notificationUpdater.getDownstreamTypesMask("WIFI_P2P"))
        assertEquals(WIFI_MASK, notificationUpdater.getDownstreamTypesMask(" WIFI "))
        assertEquals(USB_MASK, notificationUpdater.getDownstreamTypesMask("USB | B T"))
        assertEquals(BT_MASK, notificationUpdater.getDownstreamTypesMask(" WIFI: | BT"))
        assertEquals(WIFI_MASK or USB_MASK,
                notificationUpdater.getDownstreamTypesMask("1|2|USB|WIFI|BLUETOOTH||"))
    }

    @Test
    fun testNotificationWithPowerSavingChanged() {
        // Usb tethering enabled, showed enable notification
        assertNotification(USB_MASK, true, USB_ICON_ID, TITTLE, MESSAGE)

        // Power saving on, showed enable notification
        notificationUpdater.onPowerSavingChanged(true)
        expectShowNotification(USB_ICON_ID, TITTLE, MESSAGE)

        // Set test sub id, cleared notification with test resource.
        notificationUpdater.onActiveDataSubscriptionIdChanged(TEST_SUBID)
        expectClearNotification()

        // Hotspot enabled, showed enable notification with power saving message.
        assertNotification(WIFI_MASK, true, WIFI_ICON_ID, TEST_TITTLE, TEST_MESSAGE_POWER_SAVE)

        // Power saving off, showed enable notification with overlay text.
        notificationUpdater.onPowerSavingChanged(false)
        expectShowNotification(WIFI_ICON_ID, TEST_TITTLE, TEST_MESSAGE)

        // No downstream, no notification showed.
        assertNotification(DOWNSTREAM_NONE)
    }
}