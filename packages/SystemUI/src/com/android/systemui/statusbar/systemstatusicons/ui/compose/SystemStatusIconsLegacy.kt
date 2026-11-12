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

package com.android.systemui.statusbar.systemstatusicons.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.android.systemui.statusbar.phone.StatusIconContainer
import com.android.systemui.statusbar.phone.ui.StatusBarIconController
import com.android.systemui.statusbar.phone.ui.TintedIconManager

@Suppress("NAME_SHADOWING")
@Composable
fun SystemStatusIconsLegacy(
    statusBarIconController: StatusBarIconController,
    iconContainer: StatusIconContainer,
    iconManager: TintedIconManager,
    useExpandedFormat: Boolean,
    isTransitioning: Boolean,
    foregroundColor: Int,
    backgroundColor: Int,
    isSingleCarrier: Boolean,
    isMicCameraIndicationEnabled: Boolean,
    isPrivacyChipEnabled: Boolean,
    isLocationIndicationEnabled: Boolean,
    modifier: Modifier = Modifier,
    /**
     * This content should call through to [SystemStatusIconsLegacyAndroidView]. One way is to
     * provide it using [movableSystemStatusIconsLegacyAndroidView]. If remembered tied to the
     * [TintedIconManager], it will be re-used and avoid issues of re-using the same
     * [TintedIconManager].
     */
    content: MovableSystemStatusIconLegacy,
) {
    val carrierIconSlots =
        listOf(
            stringResource(id = com.android.internal.R.string.status_bar_mobile),
            stringResource(id = com.android.internal.R.string.status_bar_stacked_mobile),
        )
    val cameraSlot = stringResource(id = com.android.internal.R.string.status_bar_camera)
    val micSlot = stringResource(id = com.android.internal.R.string.status_bar_microphone)
    val locationSlot = stringResource(id = com.android.internal.R.string.status_bar_location)

    /*
     * Use `rememberUpdatedState` to guarantee that a state will be exposed (without recomposition)
     * for all these parameters, so the update block will be called when any of them changes.
     */
    val useExpandedFormat by rememberUpdatedState(useExpandedFormat)
    val isTransitioning by rememberUpdatedState(isTransitioning)
    val foregroundColor by rememberUpdatedState(foregroundColor)
    val backgroundColor by rememberUpdatedState(backgroundColor)
    val isSingleCarrier by rememberUpdatedState(isSingleCarrier)
    val isMicCameraIndicationEnabled by rememberUpdatedState(isMicCameraIndicationEnabled)
    val isPrivacyChipEnabled by rememberUpdatedState(isPrivacyChipEnabled)
    val isLocationIndicationEnabled by rememberUpdatedState(isLocationIndicationEnabled)

    val update =
        remember(
            statusBarIconController,
            iconManager,
            carrierIconSlots,
            cameraSlot,
            micSlot,
            locationSlot,
        ) {
            { container: StatusIconContainer ->
                container.setQsExpansionTransitioning(isTransitioning)

                if (isSingleCarrier || !useExpandedFormat) {
                    container.removeIgnoredSlots(carrierIconSlots)
                } else {
                    container.addIgnoredSlots(carrierIconSlots)
                }

                if (isPrivacyChipEnabled) {
                    if (isMicCameraIndicationEnabled) {
                        container.addIgnoredSlot(cameraSlot)
                        container.addIgnoredSlot(micSlot)
                    } else {
                        container.removeIgnoredSlot(cameraSlot)
                        container.removeIgnoredSlot(micSlot)
                    }
                    if (isLocationIndicationEnabled) {
                        container.addIgnoredSlot(locationSlot)
                    } else {
                        container.removeIgnoredSlot(locationSlot)
                    }
                } else {
                    container.removeIgnoredSlot(cameraSlot)
                    container.removeIgnoredSlot(micSlot)
                    container.removeIgnoredSlot(locationSlot)
                }

                iconManager.setTint(foregroundColor, backgroundColor)
            }
        }

    content(statusBarIconController, iconContainer, update, modifier)
}

/** Alias for [movableSystemStatusIconsLegacyAndroidView] */
typealias MovableSystemStatusIconLegacy =
    @Composable
    (StatusBarIconController, StatusIconContainer, (StatusIconContainer) -> Unit, Modifier) -> Unit

/**
 * Returns a movable content for the given `TintedIconManager`. This can be used to guarantee that
 * the same one is always used (to prevent double registration with [StatusBarIconController]) when
 * used with a cache.
 *
 * @param keepIconGroupRegistered Whether to keep the icon group registered with the
 *   [StatusBarIconController] when the AndroidView hosting it is released. This should only be
 *   used when the [TintedIconManager] outlives the view hosting it (e.g. when it's cached in a
 *   [com.android.systemui.shade.ui.composable.StatusIconContext]), so that the detached icon
 *   container stays populated and up to date and re-hosting it does not re-inflate all its icons.
 */
@RememberInComposition
fun movableSystemStatusIconsLegacyAndroidView(
    iconManager: TintedIconManager,
    keepIconGroupRegistered: Boolean = false,
): MovableSystemStatusIconLegacy {
    return movableContentOf {
        statusBarIconController: StatusBarIconController,
        iconContainer: StatusIconContainer,
        update: (StatusIconContainer) -> Unit,
        modifier: Modifier ->
        SystemStatusIconsLegacyAndroidView(
            statusBarIconController,
            iconManager,
            iconContainer,
            update,
            keepIconGroupRegistered,
            modifier,
        )
    }
}

@Composable
private fun SystemStatusIconsLegacyAndroidView(
    statusBarIconController: StatusBarIconController,
    iconManager: TintedIconManager,
    iconContainer: StatusIconContainer,
    update: (StatusIconContainer) -> Unit,
    keepIconGroupRegistered: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = {
            // Only register if this manager is not already registered with a controller: when
            // [keepIconGroupRegistered] is true, the group stays registered while the view
            // hosting it is not composed (see onRelease), so re-hosting the same container must
            // not re-add the group.
            if (iconManager.controller == null) {
                statusBarIconController.addIconGroup(iconManager)
            }
            iconContainer
        },
        onRelease = {
            if (!keepIconGroupRegistered) {
                statusBarIconController.removeIconGroup(iconManager)
            }
            // Otherwise, keep the icon group registered when this AndroidView is released, e.g.
            // when the QS scene header stops being composed while resting in the Shade scene.
            // This keeps the detached container populated and continuously updated, so composing
            // the header again (when expanding the shade) re-hosts the same icons instead of
            // emptying the container and re-inflating all of them, which would make the icons
            // blink.
            //
            // The registration is not leaked: with keepIconGroupRegistered=true, managers are
            // only ever created (and cached) by the StatusIconContext, which lives as long as the
            // window hosting the scene container.
        },
        update = update,
        modifier = modifier,
    )
}
