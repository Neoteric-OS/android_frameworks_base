/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.headsup

import android.content.Context
import com.android.systemui.res.R

/**
 * A class shared between [StackScrollAlgorithm] and [StackStateAnimator] to ensure all heads up
 * animations use the same animation values.
 */
class HeadsUpAnimator(context: Context) {
    init {
        NotificationsHunSharedAnimationValues.assertInNewMode()
    }

    var headsUpAppearHeightBottom: Int = 0
    var stackTopMargin: Int = 0

    private var headsUpAppearStartAboveScreen = context.fetchHeadsUpAppearStartAboveScreen()

    /**
     * Returns the Y translation for a heads-up notification animation.
     *
     * For an appear animation, the returned Y translation should be the starting value of the
     * animation. For a disappear animation, the returned Y translation should be the ending value
     * of the animation.
     */
    fun getHeadsUpYTranslation(isHeadsUpFromBottom: Boolean): Int {
        NotificationsHunSharedAnimationValues.assertInNewMode()

        if (isHeadsUpFromBottom) {
            // start from or end at the bottom of the screen
            return headsUpAppearHeightBottom + headsUpAppearStartAboveScreen
        }

        // start from or end at the top of the screen
        return -stackTopMargin - headsUpAppearStartAboveScreen
    }

    /** Should be invoked when resource values may have changed. */
    fun updateResources(context: Context) {
        headsUpAppearStartAboveScreen = context.fetchHeadsUpAppearStartAboveScreen()
    }

    private fun Context.fetchHeadsUpAppearStartAboveScreen(): Int {
        return this.resources.getDimensionPixelSize(R.dimen.heads_up_appear_y_above_screen)
    }
}
