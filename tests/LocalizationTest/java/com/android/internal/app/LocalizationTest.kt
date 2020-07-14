package com.android.android.internal.app

import android.os.SystemProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.internal.R
import com.android.internal.app.LocalePicker
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.MockitoSession

@RunWith(AndroidJUnit4::class)
class LocalizationTest {
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val unfilteredLocales = context.getResources().getStringArray(R.array.supported_locales)

    private lateinit var mockitoSession: MockitoSession

    @Before
    fun setUp() {
        mockitoSession = mockitoSession()
                .initMocks(this)
                .spyStatic(SystemProperties::class.java)
                .startMocking()
    }

    @After
    fun tearDown() {
        mockitoSession.finishMocking()
    }

    @Test
    fun testGetSupportedLocales_noFilter() {
        // Filter not set.
        setTestLocaleFilter(null)

        val locales1 = LocalePicker.getSupportedLocales(context)

        assertThat(locales1).isEqualTo(unfilteredLocales)

        // Empty filter.
        setTestLocaleFilter("")

        val locales2 = LocalePicker.getSupportedLocales(context)

        assertThat(locales2).isEqualTo(unfilteredLocales)
    }

    @Test
    fun testGetSupportedLocales_invalidFilter() {
        setTestLocaleFilter("**")

        val locales = LocalePicker.getSupportedLocales(context)

        assertThat(locales).isEqualTo(unfilteredLocales)
    }

    @Test
    fun testGetSupportedLocales_inclusiveFilter() {
        setTestLocaleFilter("^(de-AT|de-DE|en|ru).*")

        val locales = LocalePicker.getSupportedLocales(context)

        assertThat(locales).isEqualTo(
                unfilteredLocales
                        .filter { it.startsWithAnyOf("de-AT", "de-DE", "en", "ru") }
                        .toTypedArray()
        )
    }

    @Test
    fun testGetSupportedLocales_exclusiveFilter() {
        setTestLocaleFilter("^(?!de-IT|es|fr).*")

        val locales = LocalePicker.getSupportedLocales(context)

        assertThat(locales).isEqualTo(
                unfilteredLocales
                        .filter { !it.startsWithAnyOf("de-IT", "es", "fr") }
                        .toTypedArray()
        )
    }

    private fun setTestLocaleFilter(localeFilter: String?) {
        doReturn(localeFilter).`when` { SystemProperties.get(eq("ro.localization.locale_filter")) }
    }

    private fun String.startsWithAnyOf(vararg prefixes: String): Boolean {
        prefixes.forEach {
            if (startsWith(it)) return true
        }

        return false
    }
}
