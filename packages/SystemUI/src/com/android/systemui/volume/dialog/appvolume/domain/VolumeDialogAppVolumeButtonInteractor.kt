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

package com.android.systemui.volume.dialog.appvolume.domain

import android.app.ActivityManager
import com.android.app.tracing.coroutines.flow.flowName
import com.android.systemui.statusbar.policy.DeviceProvisionedController
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialog
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialogScope
import com.android.systemui.volume.dialog.domain.interactor.VolumeDialogVisibilityInteractor
import com.android.systemui.volume.dialog.shared.model.VolumeDialogVisibilityModel
import com.android.systemui.volume.panel.component.appvolume.domain.interactor.AppVolumeInteractor
import com.android.systemui.volume.dialog.sliders.domain.interactor.VolumeDialogSlidersInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@VolumeDialogScope
class VolumeDialogAppVolumeButtonInteractor
@Inject
constructor(
    @VolumeDialog private val coroutineScope: CoroutineScope,
    private val appVolumeInteractor: AppVolumeInteractor,
    private val slidersInteractor: VolumeDialogSlidersInteractor,
    private val visibilityInteractor: VolumeDialogVisibilityInteractor,
    private val deviceProvisionedController: DeviceProvisionedController,
) {

    private val isDialogVisibleAndProvisioned: Flow<Boolean> =
        visibilityInteractor.dialogVisibility
            .filterIsInstance(VolumeDialogVisibilityModel.Visible::class)
            .map { model ->
                deviceProvisionedController.isCurrentUserSetup() &&
                    model.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE
            }

    val isVisible: StateFlow<Boolean> =
        combine(isDialogVisibleAndProvisioned, appVolumeInteractor.isAvailable) { isDialogVisible, isAvailable ->
            isDialogVisible && isAvailable
        }
        .flowName("VDAVBI#isVisible")
        .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    val isAppVolumeOnly: StateFlow<Boolean> = slidersInteractor.isAppVolumeOnly

    fun onButtonClicked() {
        slidersInteractor.toggleAppVolumeOnly()
        visibilityInteractor.resetDismissTimeout()
    }
}
