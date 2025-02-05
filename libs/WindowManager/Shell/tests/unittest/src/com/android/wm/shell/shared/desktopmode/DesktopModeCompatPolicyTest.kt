/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.shared.desktopmode

import android.content.ComponentName
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.internal.R
import com.android.wm.shell.compatui.CompatUIShellTestCase
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [@link DesktopModeCompatPolicy].
 *
 * Build/Install/Run: atest WMShellUnitTests:DesktopModeCompatPolicyTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class DesktopModeCompatPolicyTest : CompatUIShellTestCase() {
    private lateinit var desktopModeCompatPolicy: DesktopModeCompatPolicy

    @Before
    fun setUp() {
        desktopModeCompatPolicy = DesktopModeCompatPolicy(mContext)
    }

    @Test
    fun testIsTopActivityExemptFromDesktopWindowing_onlyTransparentActivitiesInStack() {
        assertTrue(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                    .apply {
                        isActivityStackTransparent = true
                        isTopActivityNoDisplay = false
                        numActivities = 1
                    }))
    }

    @Test
    fun testIsTopActivityExemptFromDesktopWindowing_noActivitiesInStack() {
        assertFalse(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    isActivityStackTransparent = true
                    isTopActivityNoDisplay = false
                    numActivities = 0
                }))
    }

    @Test
    fun testIsTopActivityExemptFromDesktopWindowing_nonTransparentActivitiesInStack() {
        assertFalse(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    isActivityStackTransparent = false
                    isTopActivityNoDisplay = false
                    numActivities = 1
                }))
    }

    @Test
    fun testIsTopActivityExemptFromDesktopWindowing_transparentActivityStack_notDisplayed() {
        assertFalse(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    isActivityStackTransparent = true
                    isTopActivityNoDisplay = true
                    numActivities = 1
                }))
    }

    @Test
    fun testIsTopActivityExemptFromDesktopWindowing_systemUiTask() {
        val systemUIPackageName = context.resources.getString(R.string.config_systemUi)
        val baseComponent = ComponentName(systemUIPackageName, /* class */ "")
        assertTrue(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                    .apply {
                        baseActivity = baseComponent
                        isTopActivityNoDisplay = false
                    }))
    }

    @Test
    fun testIsTopActivityExemptFromDesktopWindowing_systemUiTask_notDisplayed() {
        val systemUIPackageName = context.resources.getString(R.string.config_systemUi)
        val baseComponent = ComponentName(systemUIPackageName, /* class */ "")
        assertFalse(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    baseActivity = baseComponent
                    isTopActivityNoDisplay = true
                }))
    }
}
