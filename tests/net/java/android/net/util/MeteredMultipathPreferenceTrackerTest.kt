/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.net.util

import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager.MULTIPATH_PREFERENCE_HANDOVER
import android.net.ConnectivityManager.MULTIPATH_PREFERENCE_PERFORMANCE
import android.net.ConnectivityManager.MULTIPATH_PREFERENCE_RELIABILITY
import android.provider.Settings
import android.provider.Settings.Global.NETWORK_METERED_MULTIPATH_PREFERENCE
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.test.mock.MockContentResolver
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.internal.util.test.FakeSettingsProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

/**
 * Tests for [MeteredMultipathPreferenceTracker].
 *
 * Build, install and run with:
 * atest android.net.util.MeteredMultipathPreferenceTrackerTest
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class MeteredMultipathPreferenceTrackerTest {
    private val resources = mock(Resources::class.java)
    private val telephonyManager = mock(TelephonyManager::class.java)
    private val subscriptionManager = mock(SubscriptionManager::class.java)
    private val resolver = MockContentResolver().apply {
        addProvider(Settings.AUTHORITY, FakeSettingsProvider()) }
    private val context = mock(Context::class.java).also {
        doReturn(Context.TELEPHONY_SERVICE).`when`(it)
                .getSystemServiceName(TelephonyManager::class.java)
        doReturn(telephonyManager).`when`(it).getSystemService(Context.TELEPHONY_SERVICE)
        doReturn(subscriptionManager).`when`(it)
                .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
        doReturn(resolver).`when`(it).contentResolver
        doReturn(resources).`when`(it).resources
    }
    private val tracker = MeteredMultipathPreferenceTracker(context, null /* handler */)

    private fun assertMultipathPreference(preference: Int) {
        Settings.Global.putString(resolver, NETWORK_METERED_MULTIPATH_PREFERENCE,
                preference.toString())
        tracker.updateMeteredMultipathPreference()
        assertEquals(preference, tracker.meteredMultipathPreference)
    }

    @Test
    fun testUpdateMeteredMultipathPreference() {
        assertMultipathPreference(MULTIPATH_PREFERENCE_HANDOVER)
        assertMultipathPreference(MULTIPATH_PREFERENCE_RELIABILITY)
        assertMultipathPreference(MULTIPATH_PREFERENCE_PERFORMANCE)
    }

    @Test
    fun testOnActiveDataSubscriptionIdChanged() {
        val testSubId = 1000

        // Modify meteredMultipathPreference settings value and local variables in
        // MultinetworkPolicyTracker should be also updated after subId changed.
        Settings.Global.putString(resolver, NETWORK_METERED_MULTIPATH_PREFERENCE,
                MULTIPATH_PREFERENCE_PERFORMANCE.toString())

        val listenerCaptor = ArgumentCaptor.forClass(
                MeteredMultipathPreferenceTracker
                .ActiveDataSubscriptionIdChangedListener::class.java)
        verify(telephonyManager, times(1))
                .registerPhoneStateListener(any(), listenerCaptor.capture())
        val listener = listenerCaptor.value
        listener.onActiveDataSubscriptionIdChanged(testSubId)

        // Check if meteredMultipathPreference values have been updated.
        assertEquals(MULTIPATH_PREFERENCE_PERFORMANCE, tracker.meteredMultipathPreference)
    }
}
