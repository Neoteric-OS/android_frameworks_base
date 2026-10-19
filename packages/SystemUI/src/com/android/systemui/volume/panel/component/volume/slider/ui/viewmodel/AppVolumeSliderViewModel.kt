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

package com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.UiBackground
import com.android.systemui.haptics.slider.SliderHapticFeedbackFilter
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.res.R
import com.android.systemui.volume.panel.component.appvolume.domain.interactor.AppVolumeInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class AppVolumeSliderViewModel
@AssistedInject
constructor(
    @Assisted private val packageName: String,
    @Assisted private val coroutineScope: CoroutineScope,
    @UiBackground private val uiBackgroundContext: CoroutineContext,
    @Application private val context: Context,
    private val packageManager: PackageManager,
    private val appVolumeInteractor: AppVolumeInteractor,
    private val hapticsViewModelFactory: SliderHapticsViewModel.Factory,
) : SliderViewModel {

    private val appInfo =
        runCatching {
            packageManager.getApplicationInfoAsUser(
                packageName,
                0,
                ActivityManager.getCurrentUser(),
            )
        }.getOrNull() ?: runCatching {
            packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull()
    private val appLabel =
        appInfo?.let { packageManager.getApplicationLabel(it).toString() } ?: packageName
    private val rawIcon: Drawable =
        appInfo?.let { runCatching { packageManager.getApplicationIcon(it) }.getOrNull() }
            ?: runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
            ?: context.getDrawable(R.drawable.ic_app_volume)!!

    private val initialAppVolume = appVolumeInteractor.getAppVolume(packageName)
    private val mutableVolume = MutableStateFlow(initialAppVolume?.volume ?: 1f)
    private val mutableIsMuted = MutableStateFlow(initialAppVolume?.isMuted ?: false)

    init {
        appVolumeInteractor.activeAppVolumes
            .mapNotNull { list -> list.find { it.packageName == packageName } }
            .distinctUntilChanged { old, new ->
                old.volume == new.volume && old.isMuted == new.isMuted
            }
            .onEach { av ->
                mutableVolume.value = av.volume
                mutableIsMuted.value = av.isMuted
            }
            .launchIn(coroutineScope)
    }

    override val slider: StateFlow<SliderState> =
        combine(mutableVolume, mutableIsMuted) { vol, isMuted ->
            val progress = (vol * 100f).roundToInt().toFloat()
            val appIconDrawable = rawIcon.constantState?.newDrawable()?.mutate() ?: rawIcon.mutate()
            if (isMuted || progress == 0f) {
                appIconDrawable.colorFilter = MONOCHROME_COLOR_FILTER
            } else {
                appIconDrawable.clearColorFilter()
            }
            State(
                value = progress,
                valueRange = 0f..100f,
                icon = Icon.Loaded(appIconDrawable, null),
                label = appLabel,
                isEnabled = true,
                step = 1f,
                isMuted = isMuted,
            )
        }
        .stateIn(coroutineScope, SharingStarted.Eagerly, SliderState.Empty)

    override fun onValueChanged(state: SliderState, newValue: Float) {
        val rounded = newValue.roundToInt()
        val vol = rounded * 0.01f
        mutableVolume.value = vol
        appVolumeInteractor.setAppVolume(packageName, vol)
    }

    override fun onValueChangeFinished() {}

    override fun toggleMuted(state: SliderState) {
        val newMuted = !mutableIsMuted.value
        mutableIsMuted.value = newMuted
        appVolumeInteractor.setAppMute(packageName, newMuted)
    }

    override fun getSliderHapticsViewModelFactory(): SliderHapticsViewModel.Factory? =
        hapticsViewModelFactory

    private data class State(
        override val value: Float,
        override val valueRange: ClosedFloatingPointRange<Float>,
        override val icon: Icon.Loaded?,
        override val label: String,
        override val isEnabled: Boolean,
        override val step: Float,
        val isMuted: Boolean,
    ) : SliderState {
        override val isAppVolume: Boolean
            get() = true

        override val hapticFilter: SliderHapticFeedbackFilter
            get() = SliderHapticFeedbackFilter()

        override val disabledMessage: String?
            get() = null

        override val isMutable: Boolean
            get() = true

        override val a11yContentDescription: String
            get() = label

        override val a11yClickDescription: String?
            get() = null

        override val a11yStateDescription: String?
            get() = null
    }

    @AssistedFactory
    interface Factory {
        fun create(
            packageName: String,
            coroutineScope: CoroutineScope,
        ): AppVolumeSliderViewModel
    }

    private companion object {
        val MONOCHROME_COLOR_FILTER =
            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }
}
