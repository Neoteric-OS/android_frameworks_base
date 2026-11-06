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

package com.android.systemui.volume.dialog.sliders.ui.viewmodel

import android.content.Context
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import com.android.internal.logging.UiEventLogger
import com.android.systemui.util.time.SystemClock
import com.android.systemui.volume.Events
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialog
import com.android.systemui.volume.dialog.domain.interactor.VolumeDialogVisibilityInteractor
import com.android.systemui.volume.dialog.shared.VolumeDialogLogger
import com.android.systemui.volume.dialog.shared.model.VolumeDialogStreamModel
import com.android.systemui.volume.dialog.shared.model.streamLabel
import com.android.systemui.volume.dialog.sliders.dagger.VolumeDialogSliderScope
import com.android.systemui.volume.dialog.sliders.domain.interactor.VolumeDialogSliderInputEventsInteractor
import com.android.systemui.volume.dialog.sliders.domain.interactor.VolumeDialogSliderInteractor
import com.android.systemui.volume.dialog.sliders.domain.model.VolumeDialogSliderType
import com.android.systemui.volume.panel.component.appvolume.domain.interactor.AppVolumeInteractor
import com.android.systemui.volume.dialog.sliders.shared.model.SliderInputEvent
import com.android.systemui.volume.dialog.ui.VolumeDialogUiEvent
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.ContentDescription
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/*
 This prevents volume slider updates while user interacts with it. This is needed due to the
 flawed VolumeDialogControllerImpl. It has a single threaded message queue that handles all state
 updates and doesn't skip sequential updates of the same stream. This leads to a bottleneck when
 user rigorously adjusts the slider.

 Remove this when getting rid of the VolumeDialogControllerImpl as this doesn't happen in the
 Volume Panel that uses the new coroutine-backed AudioRepository.
*/
// TODO(b/375355785) remove this
private const val VOLUME_UPDATE_GRACE_PERIOD = 1000

@VolumeDialogSliderScope
class VolumeDialogSliderViewModel
@Inject
constructor(
    private val context: Context,
    private val sliderType: VolumeDialogSliderType,
    private val interactor: VolumeDialogSliderInteractor,
    private val visibilityInteractor: VolumeDialogVisibilityInteractor,
    @VolumeDialog private val coroutineScope: CoroutineScope,
    private val volumeDialogSliderIconProvider: VolumeDialogSliderIconProvider,
    private val inputEventsInteractor: VolumeDialogSliderInputEventsInteractor,
    private val systemClock: SystemClock,
    private val logger: VolumeDialogLogger,
    private val uiEventLogger: UiEventLogger,
    private val appVolumeInteractor: AppVolumeInteractor,
    private val packageManager: PackageManager,
) {

    private val userVolumeUpdates = MutableStateFlow<VolumeUpdate?>(null)
    private val model: Flow<VolumeDialogStreamModel> = interactor.slider

    private val streamState: StateFlow<VolumeDialogSliderStateModel?> by lazy {
        combine(
                interactor.isDisabledByZenMode,
                model,
                model.flatMapLatest { streamModel ->
                    with(streamModel) {
                        val isMuted = muteSupported && muted
                        when (sliderType) {
                            is VolumeDialogSliderType.Stream ->
                                volumeDialogSliderIconProvider.getStreamIcon(
                                    stream = sliderType.audioStream,
                                    level = level,
                                    levelMin = levelMin,
                                    levelMax = levelMax,
                                    isMuted = isMuted,
                                    isRoutedToBluetooth = routedToBluetooth,
                                )
                            is VolumeDialogSliderType.RemoteMediaStream -> {
                                volumeDialogSliderIconProvider.getCastIcon(isMuted)
                            }
                            is VolumeDialogSliderType.AudioSharingStream -> {
                                volumeDialogSliderIconProvider.getAudioSharingIcon(isMuted)
                            }
                            is VolumeDialogSliderType.App ->
                                volumeDialogSliderIconProvider.getStreamIcon(
                                    stream = sliderType.audioStream,
                                    level = level,
                                    levelMin = levelMin,
                                    levelMax = levelMax,
                                    isMuted = isMuted,
                                    isRoutedToBluetooth = routedToBluetooth,
                                )
                        }
                    }
                },
                userVolumeUpdates,
            ) { isDisabledByZenMode, model, icon, currentVolumeUpdate ->
                val isInGracePeriod =
                    currentVolumeUpdate != null &&
                        getTimestampMillis() - currentVolumeUpdate.timestampMillis <
                            VOLUME_UPDATE_GRACE_PERIOD
                val isMinimumVolume = model.levelMin == currentVolumeUpdate?.level
                VolumeDialogSliderStateModel(
                    value =
                        if (currentVolumeUpdate != null && isInGracePeriod) {
                            if (isMinimumVolume) {
                                model.levelMin.toFloat()
                            } else {
                                currentVolumeUpdate.volume
                            }
                        } else {
                            if (model.muted) {
                                model.levelMin.toFloat()
                            } else {
                                model.level.toFloat()
                            }
                        },
                    isDisabled = isDisabledByZenMode,
                    valueRange = model.levelMin.toFloat()..model.levelMax.toFloat(),
                    icon = icon,
                    label = model.streamLabel(context),
                )
            }
            .stateIn(coroutineScope, SharingStarted.Eagerly, null)
    }

    private val appInfo =
        (sliderType as? VolumeDialogSliderType.App)?.let { type ->
            runCatching {
                packageManager.getApplicationInfoAsUser(
                    type.packageName,
                    0,
                    ActivityManager.getCurrentUser(),
                )
            }.getOrNull() ?: runCatching { packageManager.getApplicationInfo(type.packageName, 0) }
                .getOrNull()
        }
    private val appLabel =
        appInfo?.let { packageManager.getApplicationLabel(it).toString() }
            ?: (sliderType as? VolumeDialogSliderType.App)?.packageName.orEmpty()
    private val appIcon =
        appInfo?.let { runCatching { packageManager.getApplicationIcon(it) }.getOrNull() }
            ?: runCatching {
                    packageManager.getApplicationIcon(
                        (sliderType as? VolumeDialogSliderType.App)?.packageName.orEmpty()
                    )
                }
                .getOrNull()
            ?: context.getDrawable(com.android.systemui.res.R.drawable.ic_app_volume)!!

    val state: StateFlow<VolumeDialogSliderStateModel?> =
        if (sliderType is VolumeDialogSliderType.App) {
            appVolumeInteractor.activeAppVolumes
                .mapNotNull { volumes ->
                    volumes.firstOrNull { it.packageName == sliderType.packageName }
                }
                .distinctUntilChanged { old, new ->
                    old.volume == new.volume && old.isMuted == new.isMuted
                }
                .map { app ->
                    val drawable =
                        appIcon.constantState?.newDrawable()?.mutate() ?: appIcon.mutate()
                    if (app.isMuted || app.volume == 0f) {
                        drawable.colorFilter =
                            ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                    } else {
                        drawable.clearColorFilter()
                    }
                    VolumeDialogSliderStateModel(
                        value = app.volume * 100f,
                        isDisabled = false,
                        valueRange = 0f..100f,
                        icon =
                            Icon.Loaded(
                                drawable = drawable,
                                contentDescription = ContentDescription.Loaded(appLabel),
                            ),
                        label = appLabel,
                        isAppVolume = true,
                    )
                }
                .stateIn(coroutineScope, SharingStarted.Eagerly, null)
        } else {
            streamState
        }

    init {
        userVolumeUpdates
            .mapNotNull { it?.level }
            .distinctUntilChanged()
            .onEach { volume ->
                interactor.setStreamVolume(volume)
                Events.writeEvent(Events.EVENT_TOUCH_LEVEL_CHANGED, model.first().stream, volume)
            }
            .launchIn(coroutineScope)
    }

    fun setStreamVolume(volume: Float, fromUser: Boolean) {
        if (fromUser) {
            visibilityInteractor.resetDismissTimeout()
            if (sliderType is VolumeDialogSliderType.App) {
                appVolumeInteractor.setAppVolume(
                    sliderType.packageName,
                    (volume / 100f).coerceIn(0f, 1f),
                )
            } else {
                userVolumeUpdates.value =
                    VolumeUpdate(volume = volume, timestampMillis = getTimestampMillis())
            }
        }
    }

    fun onSliderDragStarted() {
        uiEventLogger.log(VolumeDialogUiEvent.VOLUME_DIALOG_SLIDER_STARTED_TRACKING_TOUCH)
    }

    fun onSliderDragFinished() {
        uiEventLogger.log(VolumeDialogUiEvent.VOLUME_DIALOG_SLIDER_STOPPED_TRACKING_TOUCH)
    }

    fun onSliderChangeFinished(volume: Float) {
        logger.onVolumeSliderAdjustmentFinished(
            volume = volume.roundToInt(),
            stream = sliderType.audioStream,
        )
    }

    fun onTouchEvent(pointerEvent: PointerEvent) {
        val position: Offset = pointerEvent.changes.first().position
        when (pointerEvent.type) {
            PointerEventType.Press ->
                inputEventsInteractor.onTouchEvent(
                    SliderInputEvent.Touch.Start(position.x, position.y)
                )

            PointerEventType.Move ->
                inputEventsInteractor.onTouchEvent(
                    SliderInputEvent.Touch.Move(position.x, position.y)
                )

            PointerEventType.Scroll ->
                inputEventsInteractor.onTouchEvent(
                    SliderInputEvent.Touch.Move(position.x, position.y)
                )

            PointerEventType.Release ->
                inputEventsInteractor.onTouchEvent(
                    SliderInputEvent.Touch.End(position.x, position.y)
                )
        }
    }

    private fun getTimestampMillis(): Long = systemClock.uptimeMillis()

    private data class VolumeUpdate(val volume: Float, val timestampMillis: Long) {

        val level: Int
            get() = volume.roundToInt()
    }
}
