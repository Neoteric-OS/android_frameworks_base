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

package com.android.systemui.volume.dialog.sliders.ui

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.core.view.doOnPreDraw
import androidx.compose.ui.util.fastForEachIndexed
import com.android.app.animation.Interpolators
import com.android.app.tracing.coroutines.launchInTraced
import com.android.app.tracing.coroutines.launchTraced
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.ExpandableIndicator
import com.android.systemui.util.children
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialogScope
import com.android.systemui.volume.dialog.sliders.dagger.VolumeDialogSliderComponent
import com.android.systemui.volume.dialog.sliders.ui.viewmodel.VolumeDialogSlidersViewModel
import com.android.systemui.volume.dialog.ui.binder.ViewBinder
import com.android.systemui.volume.dialog.ui.viewmodel.VolumeDialogViewModel
import com.android.systemui.window.domain.interactor.WindowRootViewBlurInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val EXPAND_DURATION_MS = 400L
private const val COLLAPSE_DURATION_MS = 250L

@VolumeDialogScope
class VolumeDialogSlidersViewBinder
@Inject
constructor(
    private val viewModel: VolumeDialogSlidersViewModel,
    private val dialogViewModel: VolumeDialogViewModel,
    private val windowRootViewBlurInteractor: WindowRootViewBlurInteractor,
) : ViewBinder {

    override fun CoroutineScope.bind(view: View) {

        val floatingSlidersContainer: ViewGroup =
            view.requireViewById(R.id.volume_dialog_floating_sliders_container)
        val mainSliderContainer: View =
            view.requireViewById(R.id.volume_dialog_main_slider_container)
        val background: View = view.requireViewById(R.id.volume_dialog_background)
        val bottomSection: View = view.requireViewById(R.id.volume_dialog_bottom_section_container)
        val topSection: View = view.requireViewById(R.id.volume_dialog_top_section_container)
        val expandContainer: View? = view.findViewById(R.id.volume_dialog_expand_container)

        launchTraced("VDSVB#addTouchableBounds") {
            dialogViewModel.addTouchableBounds(mainSliderContainer, floatingSlidersContainer)
        }

        view.findViewById<ExpandableIndicator>(R.id.volume_dialog_expand)?.let { button ->
            if (button.layoutDirection == View.LAYOUT_DIRECTION_RTL) button.rotation = -90f
            launchTraced("VDSVB#addExpandTouchableBounds") {
                dialogViewModel.addTouchableBounds(button)
            }
            val expandView = expandContainer ?: button
            viewModel.isExpandButtonVisible
                .onEach { expandView.visibility = if (it) View.VISIBLE else View.GONE }
                .launchInTraced("VDSVB#isExpandButtonVisible", this)
            viewModel.isExpanded
                .onEach { button.setExpanded(it) }
                .launchInTraced("VDSVB#isExpanded", this)
            button.setOnClickListener { viewModel.onExpandButtonClicked() }
        }

        var animateFloatingSliders = false
        viewModel.sliders
            .onEach { uiModel ->
                bindSlider(
                    uiModel.sliderComponent,
                    mainSliderContainer,
                    listOfNotNull(
                            mainSliderContainer,
                            background,
                            bottomSection,
                            topSection,
                            expandContainer,
                        )
                        .toTypedArray(),
                )

                val floatingSliderViewBinders = uiModel.floatingSliderComponent
                val floatingSliderViewLayoutId =
                    if (viewModel.isVolumeDialogVertical) {
                        R.layout.volume_dialog_slider_floating
                    } else {
                        R.layout.volume_dialog_slider_floating_horizontal
                    }
                if (animateFloatingSliders) {
                    floatingSlidersContainer.animateChildCount(
                        viewLayoutId = floatingSliderViewLayoutId,
                        count = floatingSliderViewBinders.size,
                        anchor = mainSliderContainer,
                    )
                } else {
                    floatingSlidersContainer.ensureChildCount(
                        viewLayoutId = floatingSliderViewLayoutId,
                        count = floatingSliderViewBinders.size,
                    )
                    animateFloatingSliders = true
                }
                floatingSliderViewBinders.fastForEachIndexed { index, sliderComponent ->
                    val sliderContainer = floatingSlidersContainer.getChildAt(index)
                    if (viewModel.showBlur) {
                        sliderContainer.updateBackground()
                    }
                    bindSlider(sliderComponent, sliderContainer, arrayOf(sliderContainer))
                }
            }
            .launchInTraced("VDSVB#sliders", this)

        if (viewModel.showBlur) {
            launchTraced("VDSVB#isBlurCurrentlySupported") {
                windowRootViewBlurInteractor.isBlurCurrentlySupported.collect { supported ->
                    for (child in floatingSlidersContainer.children) {
                        child.setIsBlurSupported(supported)
                    }
                }
            }
        }
    }

    private fun View.updateBackground() {
        if (background is LayerDrawable) {
            return
        }
        val surfaceEffect = background as GradientDrawable
        val blurDrawable = viewRootImpl.createBackgroundBlurDrawable()
        val dialogCornerRadius: Int =
            context.resources.getDimensionPixelSize(
                R.dimen.volume_dialog_floating_slider_background_corner_radius
            )
        blurDrawable.setCornerRadius(dialogCornerRadius.toFloat())
        blurDrawable.setBlurRadius(0)
        background = LayerDrawable(arrayOf<Drawable>(blurDrawable, surfaceEffect))
        setIsBlurSupported(windowRootViewBlurInteractor.isBlurCurrentlySupported.value)
    }

    private fun View.setIsBlurSupported(supported: Boolean) {
        val layers = (background as LayerDrawable)
        (layers.getDrawable(0) as BackgroundBlurDrawable).setBlurRadius(
            if (supported) {
                context.resources.getDimensionPixelSize(
                    R.dimen.volume_dialog_background_surface_blur_radius
                )
            } else {
                0
            }
        )
        (layers.getDrawable(1) as GradientDrawable).setColor(
            context.getColor(
                if (supported) {
                    R.color.volume_dialog_view_background_blur
                } else {
                    R.color.volume_dialog_view_background_blur_fallback
                }
            )
        )
    }

    private val boundSliders = mutableMapOf<View, Pair<VolumeDialogSliderComponent, Job>>()

    private fun CoroutineScope.bindSlider(
        component: VolumeDialogSliderComponent,
        sliderContainer: View,
        viewsToAnimate: Array<View>,
    ) {
        val bound = boundSliders[sliderContainer]
        if (bound?.first === component) return
        bound?.second?.cancel()
        with(component.sliderViewBinder()) { bind(sliderContainer) }
        boundSliders[sliderContainer] =
            component to
                launch {
                    with(component.overscrollViewBinder()) { bind(sliderContainer, viewsToAnimate) }
                }
    }
}

/**
 * Like [ensureChildCount], but slides added children out from behind [anchor], slides removed
 * children back behind it and moves the remaining ones to their new positions.
 */
private fun ViewGroup.animateChildCount(@LayoutRes viewLayoutId: Int, count: Int, anchor: View) {
    if (childCount == count) return
    // Positions are relative to the anchor since the window resizes around it
    val oldPositions =
        children.associateWith { child ->
            child.animate().cancel()
            left + child.left + child.translationX - anchor.left
        }
    val removed = if (childCount > count) children.drop(count).toList() else emptyList()
    val isCollapsing = removed.isNotEmpty()
    // Hold the width until every slider settled so the shrinking window doesn't clip them
    minimumWidth = if (isCollapsing) width else 0
    removed.forEach { child ->
        startViewTransition(child)
        removeView(child)
    }
    ensureChildCount(viewLayoutId, count)

    doOnPreDraw {
        val anchorCenter = anchor.width / 2f
        val entering = mutableListOf<View>()
        for (child in children) {
            val newPosition = left + child.left - anchor.left
            val oldPosition = oldPositions[child]
            if (oldPosition == null) {
                child.translationX = anchorCenter - (newPosition + child.width / 2f)
                child.alpha = 0f
                entering += child
            } else {
                child.translationX = oldPosition - newPosition
            }
        }
        removed.forEach { child ->
            child.translationX =
                oldPositions.getValue(child) - (left + child.left - anchor.left)
        }

        var runningAnimations = 0
        val onAnimationEnd: () -> Unit = {
            if (--runningAnimations == 0 && isCollapsing) minimumWidth = 0
        }
        val duration = if (isCollapsing) COLLAPSE_DURATION_MS else EXPAND_DURATION_MS
        val startAnimations: () -> Unit = {
            for (child in children) {
                runningAnimations++
                child
                    .animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(duration)
                    .setInterpolator(
                        if (child in entering) {
                            Interpolators.EMPHASIZED_DECELERATE
                        } else {
                            Interpolators.EMPHASIZED
                        }
                    )
                    .withEndAction { onAnimationEnd() }
                    .start()
            }
            removed.forEach { child ->
                runningAnimations++
                val position = left + child.left - anchor.left
                child
                    .animate()
                    .translationX(anchorCenter - (position + child.width / 2f))
                    .alpha(0f)
                    .setDuration(duration)
                    // Same curve as the remaining sliders so they keep their order and spacing
                    .setInterpolator(Interpolators.EMPHASIZED)
                    // Removed children don't invalidate their old parent on their own
                    .setUpdateListener { invalidate() }
                    .withEndAction {
                        endViewTransition(child)
                        onAnimationEnd()
                    }
                    .start()
            }
        }
        // New sliders compose their icons a frame after their first layout, start once they did
        if (entering.isEmpty()) startAnimations() else postOnAnimation { startAnimations() }
    }
}

private fun ViewGroup.ensureChildCount(@LayoutRes viewLayoutId: Int, count: Int) {
    val childCountDelta = childCount - count
    when {
        childCountDelta > 0 -> {
            removeViews(0, childCountDelta)
        }
        childCountDelta < 0 -> {
            val inflater = LayoutInflater.from(context)
            repeat(-childCountDelta) { inflater.inflate(viewLayoutId, this, true) }
        }
    }
}
