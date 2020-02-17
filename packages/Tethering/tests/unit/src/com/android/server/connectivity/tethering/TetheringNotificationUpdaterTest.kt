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
import android.net.ConnectivityManager.TETHERING_BLUETOOTH
import android.net.ConnectivityManager.TETHERING_USB
import android.net.ConnectivityManager.TETHERING_WIFI
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.os.UserHandle
import android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.internal.util.test.BroadcastInterceptingContext
import com.android.networkstack.tethering.R
import com.android.server.connectivity.tethering.TetheringNotificationUpdater.DOWNSTREAM_NONE
import com.android.server.connectivity.tethering.TetheringNotificationUpdater.ENABLE_NOTIFICATION_ID
import com.android.server.connectivity.tethering.TetheringNotificationUpdater.RESTRICT_NOTIFICATION_ID
import com.android.server.connectivity.tethering.TetheringNotificationUpdater.ROAMING_NOTIFICATION_ID
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
const val NUMBER_ICON_ID = 5
const val PAUSE_ICON_ID = 6
const val WIFI_MASK = 1 shl TETHERING_WIFI
const val USB_MASK = 1 shl TETHERING_USB
const val BT_MASK = 1 shl TETHERING_BLUETOOTH
const val TITTLE = "Tethering active"
const val MESSAGE = "Tap here to set up."
const val TEST_TITTLE = "%1\$s active"
const val TEST_MESSAGE = "Tap to set up %1\$s."
const val TEST_MESSAGE_POWER_SAVE = "Tap to set up hotspot. Power saving on"
const val TEST_TITTLE_WITH_CLIENT = "%1\$s active with %2\$d devices connected."
const val TEST_MESSAGE_WITH_CLIENT = "%2\$d devices connected. Tap to set up %1\$s."
const val TEST_MESSAGE_WITH_CLIENT_POWER_SAVE =
        "%2\$d devices connected. Tap to set up %1\$s. Power saving on."
const val TEST_PAUSE_TITTLE = "Tethering pause"
const val TEST_PAUSE_MESSAGE = "Tap here to set up. Tethering is paused due to network suspended."
const val TEST_ROAMING_TITLE = "Tethering is on"
const val TEST_ROAMING_MESSAGE = "Additional charges may apply while roaming."

@RunWith(AndroidJUnit4::class)
@SmallTest
class TetheringNotificationUpdaterTest {
    // lateinit used here for mocks as they need to be reinitialized between each test and the test
    // should crash if they are used before being initialized.
    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var notificationManager: NotificationManager
    @Mock private lateinit var defaultResources: Resources
    @Mock private lateinit var testResources: Resources
    private lateinit var context: Context
    private lateinit var notificationUpdater: TetheringNotificationUpdater

    private fun setupResources() {
        doReturn(arrayOf(
                "USB;android.test:drawable/usb", "WIFI|USB|BT;android.test:drawable/general",
                "WIFI|BT;android.test:drawable/general", "WIFI|USB;android.test:drawable/general",
                "USB|BT;android.test:drawable/general", "BT;android.test:drawable/bluetooth"))
                .`when`(defaultResources)
                .getStringArray(R.array.tethering_notification_icons)
        doReturn(arrayOf("WIFI;android.test:drawable/wifi")).`when`(testResources)
                .getStringArray(R.array.tethering_notification_icons)
        doReturn(arrayOfNulls<String>(0)).`when`(defaultResources)
                .getStringArray(R.array.tethering_downstream_combinations)
        doReturn(arrayOf("WIFI;Hotspot")).`when`(testResources)
                .getStringArray(R.array.tethering_downstream_combinations)
        doReturn(arrayOf("1,2,3,4,5;android.test:drawable/number")).`when`(testResources)
                .getStringArray(R.array.tethering_notification_icons_with_client)
        doReturn(arrayOf("WIFI;android.test:drawable/pause")).`when`(testResources)
                .getStringArray(R.array.tethering_notification_pause_icons)
        doReturn(true).`when`(testResources)
                .getBoolean(R.bool.config_upstream_roaming_notification)
        doReturn(TITTLE).`when`(defaultResources).getString(R.string.tethering_notification_title)
        doReturn(MESSAGE).`when`(defaultResources)
                .getString(R.string.tethering_notification_message)
        doReturn(MESSAGE).`when`(defaultResources)
                .getString(R.string.tethering_notification_message_power_saving)
        doReturn(TEST_TITTLE).`when`(testResources).getString(R.string.tethering_notification_title)
        doReturn(TEST_MESSAGE).`when`(testResources)
                .getString(R.string.tethering_notification_message)
        doReturn(TEST_MESSAGE_POWER_SAVE).`when`(testResources)
                .getString(R.string.tethering_notification_message_power_saving)
        doReturn(TEST_TITTLE_WITH_CLIENT).`when`(testResources)
                .getQuantityString(eq(R.plurals.tethering_notification_title_with_client), anyInt())
        doReturn(TEST_MESSAGE_WITH_CLIENT).`when`(testResources)
                .getQuantityString(eq(
                        R.plurals.tethering_notification_message_with_client), anyInt())
        doReturn(TEST_MESSAGE_WITH_CLIENT_POWER_SAVE).`when`(testResources)
                .getQuantityString(eq(
                        R.plurals.tethering_notification_message_with_client_power_saving),
                        anyInt())
        doReturn(TEST_PAUSE_TITTLE).`when`(testResources)
                .getString(R.string.tethering_notification_pause_title)
        doReturn(TEST_PAUSE_MESSAGE).`when`(testResources)
                .getString(R.string.tethering_notification_pause_message)
        doReturn(TEST_ROAMING_TITLE).`when`(testResources)
                .getString(R.string.upstream_roaming_notification_title)
        doReturn(TEST_ROAMING_MESSAGE).`when`(testResources)
                .getString(R.string.upstream_roaming_notification_message)
        doReturn(USB_ICON_ID).`when`(defaultResources)
                .getIdentifier(eq("android.test:drawable/usb"), any(), any())
        doReturn(BT_ICON_ID).`when`(defaultResources)
                .getIdentifier(eq("android.test:drawable/bluetooth"), any(), any())
        doReturn(GENERAL_ICON_ID).`when`(defaultResources)
                .getIdentifier(eq("android.test:drawable/general"), any(), any())
        doReturn(WIFI_ICON_ID).`when`(testResources)
                .getIdentifier(eq("android.test:drawable/wifi"), any(), any())
        doReturn(NUMBER_ICON_ID).`when`(testResources)
                .getIdentifier(eq("android.test:drawable/number"), any(), any())
        doReturn(PAUSE_ICON_ID).`when`(testResources)
                .getIdentifier(eq("android.test:drawable/pause"), any(), any())
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

    private fun expectShowNotification(
        iconId: Int,
        title: String = "",
        message: String = "",
        combinations: String = "Hotspot",
        clientNumber: Int = 0,
        id: Int = ENABLE_NOTIFICATION_ID
    ) {
        val notificationCaptor = ArgumentCaptor.forClass(Notification::class.java)
        verify(notificationManager, times(1)).notify(any(), eq(id), notificationCaptor.capture())

        val notification = notificationCaptor.getValue()
        assertEquals(iconId, notification.smallIcon.resId)
        assertEquals(String.format(title, combinations, clientNumber), notification.title())
        assertEquals(String.format(message, combinations, clientNumber), notification.text())
    }

    private fun expectClearNotification(id: Int = ENABLE_NOTIFICATION_ID) =
        verify(notificationManager, times(1)).cancel(any(), eq(id))

    private fun expectNothing() {
        verify(notificationManager, never()).cancel(any(), anyInt())
        verify(notificationManager, never()).notify(any(), anyInt(), any())
    }

    private fun assertNotification(
        iconId: Int = 0,
        title: String = "",
        message: String = "",
        combinations: String = "Hotspot",
        clientNumber: Int = 0,
        roamingTitle: String = "",
        roamingMessage: String = ""
    ) {
        when ("" != roamingTitle) {
            true -> expectShowNotification(R.drawable.stat_sys_tether_upstream_roaming,
                    roamingTitle, roamingMessage, id = ROAMING_NOTIFICATION_ID)
            else -> expectClearNotification(ROAMING_NOTIFICATION_ID)
        }
        when ("" != title) {
            true -> expectShowNotification(iconId, title, message, combinations, clientNumber)
            else -> expectClearNotification()
        }

        reset(notificationManager)
    }

    @Test
    fun testNotificationWithDownstreamChanged() {
        // Hotspot enabled, no notification showed.
        notificationUpdater.onDownstreamChanged(WIFI_MASK)
        assertNotification()

        // Same downstream changed, nothing happened.
        notificationUpdater.onDownstreamChanged(WIFI_MASK)
        expectNothing()

        // Usb tethering enabled, showed enable notification
        notificationUpdater.onDownstreamChanged(WIFI_MASK or USB_MASK)
        assertNotification(GENERAL_ICON_ID, TITTLE, MESSAGE)

        // Remove wifi downstream, showed enable notification.
        notificationUpdater.onDownstreamChanged(USB_MASK)
        assertNotification(USB_ICON_ID, TITTLE, MESSAGE)

        // No downstream, no notification showed.
        notificationUpdater.onDownstreamChanged(DOWNSTREAM_NONE)
        assertNotification()
    }

    @Test
    fun testNotificationWithActiveDataSubscriptionIdChanged() {
        // Same subId changed, nothing happened.
        notificationUpdater.onActiveDataSubscriptionIdChanged(INVALID_SUBSCRIPTION_ID)
        expectNothing()

        // Hotspot enabled, no notification showed with default resource.
        notificationUpdater.onDownstreamChanged(WIFI_MASK)
        assertNotification()

        // Usb tethering enabled, showed enable notification with default resource
        notificationUpdater.onDownstreamChanged(WIFI_MASK or USB_MASK)
        assertNotification(GENERAL_ICON_ID, TITTLE, MESSAGE)

        // Set test sub id, cleared notification with test resource.
        notificationUpdater.onActiveDataSubscriptionIdChanged(TEST_SUBID)
        assertNotification()

        // Remove usb downstream, showed enable notification with test resource.
        notificationUpdater.onDownstreamChanged(WIFI_MASK)
        assertNotification(WIFI_ICON_ID, TEST_TITTLE, TEST_MESSAGE)

        // No downstream, no notification showed.
        notificationUpdater.onDownstreamChanged(DOWNSTREAM_NONE)
        assertNotification()
    }

    private fun assertIconNumbers(resId: Int, number: Int, configs: Array<String?>) {
        doReturn(configs).`when`(defaultResources).getStringArray(resId)
        assertEquals(number, notificationUpdater.getIcons(resId).size)
    }

    @Test
    fun testGetIcons() {
        assertIconNumbers(R.array.tethering_notification_icons, 0, arrayOfNulls<String>(0))
        assertIconNumbers(R.array.tethering_notification_icons, 0, arrayOf(null, ""))
        assertIconNumbers(R.array.tethering_notification_icons, 2,
                arrayOf(";", "WIFI", "1;2", " USB,; ", " BT ; android.test:drawable/xxx "))

        assertIconNumbers(R.array.tethering_notification_icons_with_client, 0,
                arrayOfNulls<String>(0))
        assertIconNumbers(R.array.tethering_notification_icons_with_client, 0, arrayOf(null, ""))
        assertIconNumbers(R.array.tethering_notification_icons_with_client, 2,
                arrayOf(";", "1", "WIFI;Hotspot", " 3,; ", " 5 ; android.test:drawable/xxx "))
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
        notificationUpdater.onDownstreamChanged(USB_MASK)
        assertNotification(USB_ICON_ID, TITTLE, MESSAGE)

        // Power saving on, showed enable notification
        notificationUpdater.onPowerSavingChanged(true)
        assertNotification(USB_ICON_ID, TITTLE, MESSAGE)

        // Same power saving status. Nothing happened.
        notificationUpdater.onPowerSavingChanged(true)
        expectNothing()

        // Set test sub id, cleared notification with test resource.
        notificationUpdater.onActiveDataSubscriptionIdChanged(TEST_SUBID)
        assertNotification()

        // Hotspot enabled, showed enable notification with power saving message.
        notificationUpdater.onDownstreamChanged(WIFI_MASK)
        assertNotification(WIFI_ICON_ID, TEST_TITTLE, TEST_MESSAGE_POWER_SAVE)

        // Power saving off, showed enable notification with overlay text.
        notificationUpdater.onPowerSavingChanged(false)
        assertNotification(WIFI_ICON_ID, TEST_TITTLE, TEST_MESSAGE)

        // No downstream, no notification showed.
        notificationUpdater.onDownstreamChanged(DOWNSTREAM_NONE)
        assertNotification()
    }

    @Test
    fun testFormatText() {
        val enableText = "Tethering enabled"
        doReturn(TITTLE).`when`(defaultResources).getString(R.string.tethered_notification_title)
        doReturn(enableText).`when`(testResources)
                .getString(R.string.tethered_notification_title)

        assertEquals(TITTLE, notificationUpdater.formatText(
                TITTLE, "Tethering", R.string.tethered_notification_title))
        assertEquals(TITTLE, notificationUpdater.formatText(
                TITTLE, "", R.string.tethered_notification_title))
        assertEquals(enableText, notificationUpdater.formatText(
                "%1\$s enabled", "Tethering", R.string.tethered_notification_title))

        assertEquals(TITTLE, notificationUpdater.formatText(
                "%10\$s enabled", "Tethering", R.string.tethered_notification_title))
        notificationUpdater.onActiveDataSubscriptionIdChanged(TEST_SUBID)
        assertEquals(enableText, notificationUpdater.formatText(
                "%10\$s enabled", "Tethering", R.string.tethered_notification_title))
    }

    private fun assertTextNumbers(resId: Int, number: Int, configs: Array<String?>) {
        doReturn(configs).`when`(defaultResources).getStringArray(resId)
        assertEquals(number, notificationUpdater.getTexts(resId).size)
    }

    @Test
    fun testGetTexts() {
        assertTextNumbers(R.array.tethering_downstream_combinations, 0, arrayOfNulls<String>(0))
        assertTextNumbers(R.array.tethering_downstream_combinations, 0, arrayOf(null, ""))
        assertIconNumbers(R.array.tethering_downstream_combinations, 2,
                arrayOf(";", "WIFI", "1;2", " USB,; ", " BT ; Bluetooth"))
    }

    @Test
    fun testSetupRestrictedNotification() {
        val title = context.resources.getString(R.string.disable_tether_notification_title)
        val message = context.resources.getString(R.string.disable_tether_notification_message)
        val disallowTitle = "Tether function is disallowed"
        val disallowMessage = "Please contact your admin"
        doReturn(title).`when`(defaultResources)
                .getString(R.string.disable_tether_notification_title)
        doReturn(message).`when`(defaultResources)
                .getString(R.string.disable_tether_notification_message)
        doReturn(disallowTitle).`when`(testResources)
                .getString(R.string.disable_tether_notification_title)
        doReturn(disallowMessage).`when`(testResources)
                .getString(R.string.disable_tether_notification_message)

        // User restrictions on. Show restricted notification.
        notificationUpdater.setupRestrictedNotificationLocked()
        expectShowNotification(R.drawable.stat_sys_tether_general, title, message,
                id = RESTRICT_NOTIFICATION_ID)
        reset(notificationManager)

        // Set test sub id, cleared notification with test resource.
        notificationUpdater.onActiveDataSubscriptionIdChanged(TEST_SUBID)
        assertNotification()

        // User restrictions on again. Show restricted notification with test resource.
        notificationUpdater.setupRestrictedNotificationLocked()
        expectShowNotification(R.drawable.stat_sys_tether_general, disallowTitle, disallowMessage,
                id = RESTRICT_NOTIFICATION_ID)
    }

    @Test
    fun testNotificationWithConnectedClientsChanged() {
        // Set test sub id. Clear notification.
        notificationUpdater.onActiveDataSubscriptionIdChanged(TEST_SUBID)
        assertNotification()

        // Enable hotspot. Show enable notification with test resources.
        notificationUpdater.onDownstreamChanged(WIFI_MASK)
        assertNotification(WIFI_ICON_ID, TEST_TITTLE, TEST_MESSAGE)

        // One client connected. Show notification with connected client number.
        notificationUpdater.onConnectedClientsChanged(1)
        assertNotification(NUMBER_ICON_ID, TEST_TITTLE_WITH_CLIENT, TEST_MESSAGE_WITH_CLIENT,
                clientNumber = 1)

        // Same number client connected. Nothing happened.
        notificationUpdater.onConnectedClientsChanged(1)
        expectNothing()

        // Two client connected. Show notification with connected client number.
        notificationUpdater.onConnectedClientsChanged(2)
        assertNotification(NUMBER_ICON_ID, TEST_TITTLE_WITH_CLIENT, TEST_MESSAGE_WITH_CLIENT,
                clientNumber = 2)

        // Power saving on. Show notification with connected client number and power saving status.
        notificationUpdater.onPowerSavingChanged(true)
        assertNotification(NUMBER_ICON_ID, TEST_TITTLE_WITH_CLIENT,
                TEST_MESSAGE_WITH_CLIENT_POWER_SAVE, clientNumber = 2)

        // Set R.array.tethering_notification_icons_with_client length to 0 and change connected
        // client to one. Show notification without connected client.
        doReturn(arrayOfNulls<String>(0)).`when`(testResources)
                .getStringArray(R.array.tethering_notification_icons_with_client)
        notificationUpdater.onConnectedClientsChanged(1)
        assertNotification(WIFI_ICON_ID, TEST_TITTLE, TEST_MESSAGE_POWER_SAVE)
    }

    @Test
    fun testNotificationWithSuspendedCapabilitiesChanged() {
        // Set test sub id. Clear notification.
        notificationUpdater.onActiveDataSubscriptionIdChanged(TEST_SUBID)
        assertNotification()

        // Enable hotspot. Show enable notification with test resources.
        notificationUpdater.onDownstreamChanged(WIFI_MASK)
        assertNotification(WIFI_ICON_ID, TEST_TITTLE, TEST_MESSAGE)

        // Upstream network suspended. Show pause notification.
        notificationUpdater.onUpstreamCapabilitiesChanged(NetworkCapabilities())
        assertNotification(PAUSE_ICON_ID, TEST_PAUSE_TITTLE, TEST_PAUSE_MESSAGE)

        // Same NetworkCapabilities. Nothing happened.
        notificationUpdater.onUpstreamCapabilitiesChanged(NetworkCapabilities())
        expectNothing()

        // Power saving on. Still show pause notification
        notificationUpdater.onPowerSavingChanged(true)
        assertNotification(PAUSE_ICON_ID, TEST_PAUSE_TITTLE, TEST_PAUSE_MESSAGE)

        // One client connected. Still show pause notification
        notificationUpdater.onConnectedClientsChanged(1)
        assertNotification(PAUSE_ICON_ID, TEST_PAUSE_TITTLE, TEST_PAUSE_MESSAGE)

        // Upstream network resumed. Show notification with connected client number and power saving
        // status.
        notificationUpdater.onUpstreamCapabilitiesChanged(
                NetworkCapabilities().addCapability(NET_CAPABILITY_NOT_SUSPENDED))
        assertNotification(NUMBER_ICON_ID, TEST_TITTLE_WITH_CLIENT,
                TEST_MESSAGE_WITH_CLIENT_POWER_SAVE, clientNumber = 1)

        // Set R.array.tethering_notification_icons_with_client length to 0 and change upstream
        // network to suspended state. Still show notification with connected client number and
        // power saving status.
        doReturn(arrayOfNulls<String>(0)).`when`(testResources)
                .getStringArray(R.array.tethering_notification_pause_icons)
        notificationUpdater.onUpstreamCapabilitiesChanged(NetworkCapabilities())
        assertNotification(NUMBER_ICON_ID, TEST_TITTLE_WITH_CLIENT,
                TEST_MESSAGE_WITH_CLIENT_POWER_SAVE, clientNumber = 1)
    }

    @Test
    fun testNotificationWithRoamingCapabilitiesChanged() {
        // Set test sub id. Clear notification.
        notificationUpdater.onActiveDataSubscriptionIdChanged(TEST_SUBID)
        assertNotification()

        // Enable hotspot. Show enable notification with test resources.
        notificationUpdater.onDownstreamChanged(WIFI_MASK)
        assertNotification(WIFI_ICON_ID, TEST_TITTLE, TEST_MESSAGE)

        // Upstream network changed to roaming state. Show both roaming and enable notification.
        notificationUpdater.onUpstreamCapabilitiesChanged(
                NetworkCapabilities().addTransportType(TRANSPORT_CELLULAR)
                        .addCapability(NET_CAPABILITY_NOT_SUSPENDED))
        assertNotification(WIFI_ICON_ID, TEST_TITTLE, TEST_MESSAGE,
                roamingTitle = TEST_ROAMING_TITLE, roamingMessage = TEST_ROAMING_MESSAGE)

        // Same NetworkCapabilities. Nothing happened.
        notificationUpdater.onUpstreamCapabilitiesChanged(
                NetworkCapabilities().addTransportType(TRANSPORT_CELLULAR)
                        .addCapability(NET_CAPABILITY_NOT_SUSPENDED))
        expectNothing()

        // Power saving on. Show both roaming and enable notification with power saving status.
        notificationUpdater.onPowerSavingChanged(true)
        assertNotification(WIFI_ICON_ID, TEST_TITTLE, TEST_MESSAGE_POWER_SAVE,
                roamingTitle = TEST_ROAMING_TITLE, roamingMessage = TEST_ROAMING_MESSAGE)

        // One client connected. Show both roaming and enable notification with connected client
        // number and power saving status.
        notificationUpdater.onConnectedClientsChanged(1)
        assertNotification(NUMBER_ICON_ID, TEST_TITTLE_WITH_CLIENT,
                TEST_MESSAGE_WITH_CLIENT_POWER_SAVE, clientNumber = 1,
                roamingTitle = TEST_ROAMING_TITLE, roamingMessage = TEST_ROAMING_MESSAGE)

        // Upstream network suspended. Show both roaming and pause notification.
        notificationUpdater.onUpstreamCapabilitiesChanged(
                NetworkCapabilities().addTransportType(TRANSPORT_CELLULAR))
        assertNotification(PAUSE_ICON_ID, TEST_PAUSE_TITTLE, TEST_PAUSE_MESSAGE,
                roamingTitle = TEST_ROAMING_TITLE, roamingMessage = TEST_ROAMING_MESSAGE)

        // Upstream network changed to home state. Only show pause notification.
        notificationUpdater.onUpstreamCapabilitiesChanged(
                NetworkCapabilities().addTransportType(TRANSPORT_CELLULAR)
                        .addCapability(NET_CAPABILITY_NOT_ROAMING))
        assertNotification(PAUSE_ICON_ID, TEST_PAUSE_TITTLE, TEST_PAUSE_MESSAGE)

        // Set R.bool.config_upstream_roaming_notification to false and change upstream
        // network to roaming state again. Still only show pause notification.
        doReturn(false).`when`(testResources)
                .getBoolean(R.bool.config_upstream_roaming_notification)
        notificationUpdater.onUpstreamCapabilitiesChanged(
                NetworkCapabilities().addTransportType(TRANSPORT_CELLULAR))
        assertNotification(PAUSE_ICON_ID, TEST_PAUSE_TITTLE, TEST_PAUSE_MESSAGE)
    }
}