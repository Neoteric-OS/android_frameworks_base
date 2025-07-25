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

package com.android.systemui.brightness.ui.compose

import android.content.Context
import android.graphics.PorterDuff
import android.view.MotionEvent
import android.widget.ImageButton
import android.widget.ImageView
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.compose.ui.graphics.drawInOverlay
import com.android.systemui.Flags
import com.android.systemui.biometrics.Utils.toBitmap
import com.android.systemui.brightness.shared.model.GammaBrightness
import com.android.systemui.brightness.ui.compose.Dimensions.IconSize
import com.android.systemui.brightness.ui.compose.Dimensions.SliderBackgroundFrameSize
import com.android.systemui.brightness.ui.compose.Dimensions.SliderTrackRoundedCorner
import com.android.systemui.brightness.ui.compose.Dimensions.ThumbSize
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.brightness.ui.viewmodel.Drag
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.haptics.slider.SeekableSliderTrackerConfig
import com.android.systemui.haptics.slider.SliderHapticFeedbackConfig
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.ui.compose.borderOnFocus
import com.android.systemui.res.R
import com.android.systemui.utils.PolicyRestriction

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
@VisibleForTesting
fun BrightnessSlider(
    gammaValue: Int,
    valueRange: IntRange,
    autoMode: Boolean,
    iconResProvider: (Float) -> Int,
    imageLoader: suspend (Int, Context) -> Icon.Loaded,
    restriction: PolicyRestriction,
    onRestrictedClick: (PolicyRestriction.Restricted) -> Unit,
    onDrag: (Int) -> Unit,
    onStop: (Int) -> Unit,
    onIconClick: suspend () -> Unit,
    overriddenByAppState: Boolean,
    modifier: Modifier = Modifier,
    showToast: () -> Unit = {},
    hapticsViewModelFactory: SliderHapticsViewModel.Factory,
) {
    var value by remember(gammaValue) { mutableIntStateOf(gammaValue) }
    val animatedValue by
        animateFloatAsState(targetValue = value.toFloat(), label = "BrightnessSliderAnimatedValue")
    val floatValueRange = valueRange.first.toFloat()..valueRange.last.toFloat()
    val isRestricted = restriction is PolicyRestriction.Restricted
    val enabled = !isRestricted
    val interactionSource = remember { MutableInteractionSource() }
    val hapticsViewModel: SliderHapticsViewModel? =
        if (Flags.hapticsForComposeSliders()) {
            rememberViewModel(traceName = "SliderHapticsViewModel") {
                hapticsViewModelFactory.create(
                    interactionSource,
                    floatValueRange,
                    Orientation.Horizontal,
                    SliderHapticFeedbackConfig(
                        maxVelocityToScale = 1f /* slider progress(from 0 to 1) per sec */
                    ),
                    SeekableSliderTrackerConfig(),
                )
            }
        } else {
            null
        }
    val colors = colors()

    // The value state is recreated every time gammaValue changes, so we recreate this derivedState
    // We have to use value as that's the value that changes when the user is dragging (gammaValue
    // is always the starting value: actual (not temporary) brightness).
    val iconRes by
        remember(gammaValue, valueRange) {
            derivedStateOf {
                val percentage =
                    (value - valueRange.first) * 100f / (valueRange.last - valueRange.first)
                iconResProvider(percentage)
            }
        }
    val context = LocalContext.current
    val painter: Painter by
        produceState<Painter>(
            initialValue = ColorPainter(Color.Transparent),
            key1 = iconRes,
            key2 = context,
        ) {
            val icon = imageLoader(iconRes, context)
            // toBitmap is Drawable?.() -> Bitmap? and handles null internally.
            val bitmap = icon.drawable.toBitmap()!!.asImageBitmap()
            this@produceState.value = BitmapPainter(bitmap)
        }

    val hasAutoBrightness = context.resources.getBoolean(
        com.android.internal.R.bool.config_automatic_brightness_available
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Slider(
            value = animatedValue,
            valueRange = floatValueRange,
            enabled = enabled,
            colors = colors,
            onValueChange = {
                if (enabled) {
                    if (!overriddenByAppState) {
                        hapticsViewModel?.onValueChange(it)
                        value = it.toInt()
                        onDrag(value)
                    }
                }
            },
            onValueChangeFinished = {
                if (enabled) {
                    if (!overriddenByAppState) {
                        hapticsViewModel?.onValueChangeEnded()
                        onStop(value)
                    }
                }
            },
            modifier = modifier
                .weight(1f)
                .sysuiResTag("slider")
                .clickable(enabled = isRestricted) {
                    if (restriction is PolicyRestriction.Restricted) {
                        onRestrictedClick(restriction)
                    }
                },
            interactionSource = interactionSource,
            thumb = {
                 Box(modifier = Modifier.size(ThumbSize))
            },
            track = { sliderState ->
                val activeTrackColor = MaterialTheme.colorScheme.primary
                val inactiveTrackColor = LocalAndroidColorScheme.current.surfaceEffect2
                val density = LocalDensity.current

                Layout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ThumbSize)
                        .clip(RoundedCornerShape(SliderTrackRoundedCorner)),
                    content = {
                        Box(Modifier.background(inactiveTrackColor)) // Inactive track
                        Box(Modifier
                                .clip(RoundedCornerShape(SliderTrackRoundedCorner))
                                .background(activeTrackColor),
                            contentAlignment = Alignment.CenterEnd
                        ) { // Active track with icon
                            Box(
                                modifier = Modifier.size(ThumbSize),
                                contentAlignment = Alignment.Center
                            ) {
                                M3Icon(
                                    painter = painter,
                                    contentDescription = null,
                                    modifier = Modifier.size(IconSize),
                                    tint = colors.activeTickColor,
                                )
                            }
                        }
                    },
                    measurePolicy = { measurables, constraints ->
                        val thumbSizePx = density.run { ThumbSize.toPx() }
                        val width = constraints.maxWidth + thumbSizePx.toInt()

                        val trackWidth = (sliderState.coercedValueAsFraction * constraints.maxWidth.toFloat())
                            .toInt()
                        val thumbWidth = if (sliderState.coercedValueAsFraction == 0f) {
                            thumbSizePx.toInt() - trackWidth
                        } else {
                            thumbSizePx.toInt()
                        }
                        val activeTrackWidth = trackWidth + thumbWidth

                        val inactiveTrackPlaceable = measurables[0].measure(
                            Constraints.fixed(width, thumbSizePx.toInt())
                        )

                        val activeTrackPlaceable = measurables[1].measure(
                            Constraints.fixed(activeTrackWidth, thumbSizePx.toInt())
                        )

                        layout(width, thumbSizePx.toInt()) {
                            inactiveTrackPlaceable.place(0, 0)
                            activeTrackPlaceable.place(0, 0)
                        }
                    }
                )
            }
        )

        if (hasAutoBrightness) {
            Spacer(modifier = Modifier.width(8.dp))

            val coroutineScope = rememberCoroutineScope()
            val autoBrightnessBackgroundColor by animateColorAsState(
                targetValue = if (autoMode) MaterialTheme.colorScheme.primary else LocalAndroidColorScheme.current.surfaceEffect2
            )
            val autoBrightnessIconTint by animateColorAsState(
                targetValue = if (autoMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )

            AndroidView(
                factory = { factoryContext ->
                    ImageButton(factoryContext).apply {
                        setBackgroundResource(0)
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        val drawable = factoryContext.getDrawable(R.drawable.ic_qs_brightness_auto)
                        setImageDrawable(drawable)
                    }
                },
                modifier = Modifier
                    .size(ThumbSize)
                    .clip(CircleShape)
                    .background(autoBrightnessBackgroundColor),
                update = { button ->
                    val targetState = if (autoMode) {
                        intArrayOf(android.R.attr.state_checked)
                    } else {
                        intArrayOf()
                    }
                    button.setImageState(targetState, false)
                    button.setColorFilter(autoBrightnessIconTint.toArgb(), PorterDuff.Mode.SRC_IN)
                    button.setOnClickListener {
                        coroutineScope.launch {
                            onIconClick()
                        }
                    }
                }
            )
        }
    }

    val currentShowToast by rememberUpdatedState(showToast)
    // Showing the warning toast if the current running app window has controlled the
    // brightness value.
    if (Flags.showToastWhenAppControlBrightness()) {
        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start && overriddenByAppState) {
                    currentShowToast()
                }
            }
        }
    }
}

private fun Modifier.sliderBackground(color: Color) = drawWithCache {
    val offsetAround = SliderBackgroundFrameSize.toPx()
    val newSize = Size(size.width + 2 * offsetAround, size.height + 2 * offsetAround)
    val offset = Offset(-offsetAround, -offsetAround)
    val cornerRadius = CornerRadius(offsetAround + size.height / 2)
    onDrawBehind {
        drawRoundRect(color = color, topLeft = offset, size = newSize, cornerRadius = cornerRadius)
    }
}

@Composable
fun BrightnessSliderContainer(
    viewModel: BrightnessSliderViewModel,
    modifier: Modifier = Modifier,
    containerColors: ContainerColors,
) {
    val gamma = viewModel.currentBrightness.value
    if (gamma == BrightnessSliderViewModel.initialValue.value) { // Ignore initial negative value.
        return
    }
    val autoMode = viewModel.autoMode
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val restriction by
        viewModel.policyRestriction.collectAsStateWithLifecycle(
            initialValue = PolicyRestriction.NoRestriction
        )
    val overriddenByAppState by
        if (Flags.showToastWhenAppControlBrightness()) {
            viewModel.brightnessOverriddenByWindow.collectAsStateWithLifecycle()
        } else {
            remember { mutableStateOf(false) }
        }

    DisposableEffect(Unit) { onDispose { viewModel.setIsDragging(false) } }

    var dragging by remember { mutableStateOf(false) }

    // Use dragging instead of viewModel.showMirror so the color starts changing as soon as the
    // dragging state changes. If not, we may be waiting for the background to finish fading in
    // when stopping dragging
    val containerColor by
        animateColorAsState(
            if (dragging) containerColors.mirrorColor else containerColors.idleColor
        )

    Box(modifier = modifier.fillMaxWidth().sysuiResTag("brightness_slider")) {
        BrightnessSlider(
            gammaValue = gamma,
            valueRange = viewModel.minBrightness.value..viewModel.maxBrightness.value,
            autoMode = autoMode,
            iconResProvider = BrightnessSliderViewModel::getIconForPercentage,
            imageLoader = viewModel::loadImage,
            restriction = restriction,
            onRestrictedClick = viewModel::showPolicyRestrictionDialog,
            onDrag = {
                viewModel.setIsDragging(true)
                dragging = true
                coroutineScope.launch { viewModel.onDrag(Drag.Dragging(GammaBrightness(it))) }
            },
            onStop = {
                viewModel.setIsDragging(false)
                dragging = false
                coroutineScope.launch { viewModel.onDrag(Drag.Stopped(GammaBrightness(it))) }
            },
            onIconClick = { viewModel.onIconClick() },
            modifier =
                Modifier.borderOnFocus(
                        color = MaterialTheme.colorScheme.secondary,
                        cornerSize = CornerSize(SliderTrackRoundedCorner),
                    )
                    .then(if (viewModel.showMirror) Modifier.drawInOverlay() else Modifier)
                    .sliderBackground(containerColor)
                    .fillMaxWidth()
                    .pointerInteropFilter {
                        if (
                            it.actionMasked == MotionEvent.ACTION_UP ||
                                it.actionMasked == MotionEvent.ACTION_CANCEL
                        ) {
                            viewModel.emitBrightnessTouchForFalsing()
                        }
                        false
                    },
            hapticsViewModelFactory = viewModel.hapticsViewModelFactory,
            overriddenByAppState = overriddenByAppState,
            showToast = {
                viewModel.showToast(context, R.string.quick_settings_brightness_unable_adjust_msg)
            },
        )
    }
}

data class ContainerColors(val idleColor: Color, val mirrorColor: Color) {
    companion object {
        fun singleColor(color: Color) = ContainerColors(color, color)

        val defaultContainerColor: Color
            @Composable @ReadOnlyComposable get() = colorResource(R.color.shade_panel_fallback)
    }
}

private object Dimensions {
    val IconSize = 28.dp
    val SliderBackgroundFrameSize = 8.dp
    val SliderTrackRoundedCorner = 32.dp
    val ThumbSize = 56.dp
}

@Composable
private fun colors(): SliderColors {
    return SliderDefaults.colors()
        .copy(
            inactiveTrackColor = LocalAndroidColorScheme.current.surfaceEffect2,
            activeTickColor = MaterialTheme.colorScheme.onPrimary,
            inactiveTickColor = MaterialTheme.colorScheme.onSurface,
        )
}
