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

import android.app.ActivityManager
import com.android.systemui.shared.system.BlurUtils.isVolumeAndPowerBlurEnabled
import com.android.systemui.statusbar.policy.DeviceProvisionedController
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialog
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialogScope
import com.android.systemui.volume.dialog.domain.interactor.ExpandedAudioTileDetailsFeatureInteractor
import com.android.systemui.volume.dialog.domain.interactor.VolumeDialogVisibilityInteractor
import com.android.systemui.volume.dialog.shared.VolumeDialogLogger
import com.android.systemui.volume.dialog.shared.model.VolumeDialogVisibilityModel
import com.android.systemui.volume.dialog.sliders.dagger.VolumeDialogSliderComponent
import com.android.systemui.volume.dialog.sliders.domain.interactor.VolumeDialogSlidersInteractor
import com.android.systemui.volume.dialog.sliders.domain.model.VolumeDialogSliderType
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@VolumeDialogScope
class VolumeDialogSlidersViewModel
@Inject
constructor(
    @VolumeDialog coroutineScope: CoroutineScope,
    private val slidersInteractor: VolumeDialogSlidersInteractor,
    private val sliderComponentFactory: VolumeDialogSliderComponent.Factory,
    private val volumeDialogLogger: VolumeDialogLogger,
    private val expandedAudioTileDetailsFeatureInteractor: ExpandedAudioTileDetailsFeatureInteractor,
    private val visibilityInteractor: VolumeDialogVisibilityInteractor,
    deviceProvisionedController: DeviceProvisionedController,
) {

    // Use horizontal volume dialog if the audio tile details view is enabled
    val isVolumeDialogVertical = !expandedAudioTileDetailsFeatureInteractor.isEnabled()

    // Show blur if the flag is enabled and the volume dialog is vertical
    val showBlur = isVolumeAndPowerBlurEnabled() && isVolumeDialogVertical

    val isExpandButtonVisible: StateFlow<Boolean> =
        visibilityInteractor.dialogVisibility
            .filterIsInstance(VolumeDialogVisibilityModel.Visible::class)
            .map { model ->
                isVolumeDialogVertical &&
                    deviceProvisionedController.isCurrentUserSetup() &&
                    model.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE
            }
            .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    val isExpanded: StateFlow<Boolean> = slidersInteractor.isExpanded

    private val sliderComponents =
        mutableMapOf<VolumeDialogSliderType, VolumeDialogSliderComponent>()

    init {
        // Load expandable sliders upfront so their state is ready when the dialog expands
        if (isVolumeDialogVertical) {
            slidersInteractor.expandableSliders.forEach { sliderComponent(it).sliderViewModel() }
        }
    }

    private fun sliderComponent(type: VolumeDialogSliderType): VolumeDialogSliderComponent =
        sliderComponents.getOrPut(type) { sliderComponentFactory.create(type) }

    fun onExpandButtonClicked() {
        slidersInteractor.setExpanded(!isExpanded.value)
        visibilityInteractor.resetDismissTimeout()
    }

    val sliders: Flow<VolumeDialogSliderUiModel> =
        slidersInteractor.sliders
            .map { slidersModel ->
                volumeDialogLogger.onVolumeSlidersUpdated(
                    slidersModel.slider.audioStream,
                    slidersModel.floatingSliders.map { it.audioStream },
                )
                VolumeDialogSliderUiModel(
                    sliderComponent = sliderComponent(slidersModel.slider),
                    floatingSliderComponent = slidersModel.floatingSliders.map(::sliderComponent),
                )
            }
            .stateIn(coroutineScope, SharingStarted.Eagerly, null)
            .filterNotNull()
}

/** Models slider ui */
data class VolumeDialogSliderUiModel(
    val sliderComponent: VolumeDialogSliderComponent,
    val floatingSliderComponent: List<VolumeDialogSliderComponent>,
)
