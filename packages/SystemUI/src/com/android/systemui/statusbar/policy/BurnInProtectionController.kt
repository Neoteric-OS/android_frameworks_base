/*
 * Copyright (C) 2017-2018 Paranoid Android
 * Copyright (C) 2022 FlamingoOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy

import android.content.Context
import android.util.Log
import android.view.Display

import com.android.internal.policy.SystemBarUtils

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.doze.util.zigzag
import com.android.systemui.keyguard.WakefulnessLifecycle
import com.android.systemui.navigationbar.NavigationBarController
import com.android.systemui.navigationbar.NavigationModeController
import com.android.systemui.navigationbar.views.NavigationBarView
import com.android.systemui.res.R
import com.android.systemui.shared.system.QuickStepContract.isGesturalMode
import com.android.systemui.statusbar.data.repository.HomeStatusBarComponentsRepository
import com.android.systemui.statusbar.phone.PhoneStatusBarView
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener

import dagger.Lazy
import javax.inject.Inject

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val TAG = BurnInProtectionController::class.simpleName

@SysUISingleton
class BurnInProtectionController @Inject constructor(
    private val context: Context,
    configurationController: ConfigurationController,
    navigationModeController: NavigationModeController,
    private val navigationBarControllerLazy: Lazy<NavigationBarController>,
    private val homeStatusBarComponentsRepository: Lazy<HomeStatusBarComponentsRepository>,
    private val wakefulnessLifecycle: WakefulnessLifecycle,
) : NavigationModeController.ModeChangedListener,
    ConfigurationListener,
    WakefulnessLifecycle.Observer {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val shiftEnabled = context.resources.getBoolean(
        R.bool.config_statusBarBurnInProtection)

    private val shiftInterval = context.resources.getInteger(
        R.integer.config_statusBarBurnInProtectionShiftInterval) * 1000L

    private var navigationMode: Int = navigationModeController.addListener(this)

    private var navigationBarView: NavigationBarView? = null
    private var phoneStatusBarView: PhoneStatusBarView? = null

    private var shiftJob: Job? = null
    private var shiftCounter = 0

    private var maxStatusBarOffsetX = 0
    private var maxStatusBarOffsetY = 0
    private var maxNavBarOffsetX = 0
    private var maxNavBarOffsetY = 0

    private var statusBarOffset = Offset.Zero
    private var navBarOffset = Offset.Zero

    init {
        logD {
            "shiftEnabled = $shiftEnabled, isGesturalMode = ${isGesturalMode()}"
        }
        configurationController.addCallback(this)
        wakefulnessLifecycle.addObserver(this)
        loadResources()
        if (wakefulnessLifecycle.wakefulness == WakefulnessLifecycle.WAKEFULNESS_AWAKE ||
            wakefulnessLifecycle.wakefulness == WakefulnessLifecycle.WAKEFULNESS_WAKING) {
            startShiftTimer()
        }
    }

    private fun loadResources()  {
        with(context.resources) {
            maxStatusBarOffsetX = minOf(
                getDimensionPixelSize(R.dimen.status_bar_padding_start),
                getDimensionPixelSize(R.dimen.status_bar_padding_end),
                getDimensionPixelSize(R.dimen.status_bar_offset_max_x)
            ) / 2
            maxStatusBarOffsetY = minOf(
                SystemBarUtils.getStatusBarHeight(context) -
                getDimensionPixelSize(com.android.internal.R.dimen.status_bar_height_default),
                getDimensionPixelSize(R.dimen.status_bar_offset_max_y)
            ) / 2
        }
        calculateNavBarMaxOffset()
        logD {
            "maxStatusBarOffsetX = $maxStatusBarOffsetX, maxStatusBarOffsetY = $maxStatusBarOffsetY"
        }
    }

    private fun calculateNavBarMaxOffset() {
        with(context.resources) {
            maxNavBarOffsetX = if (isGesturalMode()) {
                0
            } else {
                getDimensionPixelSize(R.dimen.floating_rotation_button_min_margin) / 4
            }
            maxNavBarOffsetY = if (isGesturalMode()) {
                getDimensionPixelSize(R.dimen.navigation_handle_bottom) / 3
            } else {
                val frameHeight = getDimensionPixelSize(R.dimen.navigation_bar_height)
                val buttonHeight = getDimensionPixelSize(R.dimen.navigation_icon_size)
                (frameHeight - buttonHeight) / 3
            }
        }
        logD {
            "maxNavBarOffsetX = $maxNavBarOffsetX, maxNavBarOffsetY = $maxNavBarOffsetY"
        }
    }

    fun setNavigationBarView(navigationBarView: NavigationBarView?) {
        this.navigationBarView = navigationBarView
    }

    fun setPhoneStatusBarView(phoneStatusBarView: PhoneStatusBarView?) {
        this.phoneStatusBarView = phoneStatusBarView
    }

    fun startShiftTimer() {
        if (!shiftEnabled || (shiftJob?.isActive == true)) return
        shiftJob = coroutineScope.launch {
            while (isActive) {
                val sbOffset = Offset(
                    getBurnInOffset(maxStatusBarOffsetX),
                    getBurnInOffset(maxStatusBarOffsetY)
                )
                val nbOffset = Offset(
                    getBurnInOffset(maxNavBarOffsetX),
                    getBurnInOffset(maxNavBarOffsetY)
                )
                logD {
                    "new offsets: sbOffset = $sbOffset, nbOffset = $nbOffset"
                }
                updateViews(sbOffset, nbOffset)
                delay(shiftInterval)
                shiftCounter++
            }
        }
        logD {
            "Started shift job"
        }
    }

    private fun getBurnInOffset(maxOffset: Int): Int {
        val amplitude = maxOffset.toFloat()
        val period = amplitude * 2
        val mult = if ((shiftCounter / period) % 2 == 0f) 1 else -1
        return mult * Math.round(zigzag(shiftCounter.toFloat(), amplitude, period))
    }

    private fun getStatusBarView(): PhoneStatusBarView? =
        phoneStatusBarView ?: homeStatusBarComponentsRepository.get()
            .componentsByDisplayId.value[Display.DEFAULT_DISPLAY]?.phoneStatusBarView

    private fun getNavBarView(): NavigationBarView? =
        navigationBarView ?: navigationBarControllerLazy.get()
            .getNavigationBarView(Display.DEFAULT_DISPLAY)

    private fun updateViews(sbOffset: Offset, nbOffset: Offset) {
        val sbView = getStatusBarView()
        if (sbOffset != statusBarOffset) {
            logD {
                "Translating statusbar"
            }
            sbView?.offsetStatusBar(sbOffset)
            statusBarOffset = sbOffset
        }
        val navView = getNavBarView()
        if (nbOffset != navBarOffset) {
            logD {
                "Translating navbar"
            }
            navView?.offsetNavBar(nbOffset)
            navBarOffset = nbOffset
        }
    }

    override fun onFinishedGoingToSleep() {
        stopShiftTimer()
    }

    override fun onStartedWakingUp() {
        startShiftTimer()
    }

    fun stopShiftTimer() {
        if (!shiftEnabled || (shiftJob?.isActive != true)) return
        logD {
            "Cancelling shift job"
        }
        coroutineScope.launch {
            shiftJob?.cancelAndJoin()
            updateViews(Offset.Zero, Offset.Zero)
            logD {
                "Cancelled shift job"
            }
        }
    }

    override fun onNavigationModeChanged(mode: Int) {
        if (navigationMode == mode) return
        navigationMode = mode
        logD {
            "onNavigationModeChanged: isGesturalMode = ${isGesturalMode()}"
        }
        calculateNavBarMaxOffset()
    }

    override fun onDensityOrFontScaleChanged() {
        logD {
            "onDensityOrFontScaleChanged"
        }
        loadResources()
    }

    private fun isGesturalMode() = isGesturalMode(navigationMode)
}

private inline fun logD(crossinline msg: () -> String) {
    if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, msg())
    }
}

data class Offset(
    val x: Int,
    val y: Int
) {
    companion object {
        val Zero = Offset(0, 0)
    }
}
