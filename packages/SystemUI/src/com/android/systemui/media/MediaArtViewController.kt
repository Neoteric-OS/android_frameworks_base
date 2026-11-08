/*
 * Copyright (C) 2025 The AxionAOSP Project
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
package com.android.systemui.media

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.internal.graphics.ColorUtils
import com.android.systemui.application.impl.SystemUIApplicationImpl
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.WakefulnessLifecycle
import com.android.systemui.media.controls.domain.pipeline.MediaDataManager
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.remedia.data.repository.MediaPipelineRepository
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.NotificationShadeWindowController
import com.android.systemui.statusbar.StatusBarState
import com.android.systemui.statusbar.policy.KeyguardStateController
import dagger.Lazy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

enum class MediaScrimState {
    STATE_SCRIM_VISIBLE,
    STATE_SCRIM_HIDDEN
}

@SysUISingleton
class MediaArtViewController @Inject constructor(
    private val context: Context,
    private val sceneInteractor: Lazy<SceneInteractor>,
    private val mediaManager: Lazy<NotificationMediaManager>,
    private val mediaDataManager: Lazy<MediaDataManager>,
    private val mediaPipelineRepository: Lazy<MediaPipelineRepository>,
    private val notificationShadeWindowController: Lazy<NotificationShadeWindowController>,
    private val keyguardStateController: Lazy<KeyguardStateController>,
    private val statusBarStateController: Lazy<StatusBarStateController>,
    private val wakefulnessLifecycle: Lazy<WakefulnessLifecycle>,
) : NotificationMediaManager.MediaListener,
    KeyguardStateController.Callback,
    StatusBarStateController.StateListener,
    WakefulnessLifecycle.Observer {

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var scrimState = MediaScrimState.STATE_SCRIM_HIDDEN

    private var listening = false
    private var featureEnabled = false
    private var artworkDrawable: Drawable? = null
    private var isMediaPlaying = false
    private var sceneJob: Job? = null
    private var mediaPipelineJob: Job? = null

    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            updateSettings()
        }

        override fun onChange(selfChange: Boolean) {
            updateSettings()
        }
    }

    private val mediaDataListener = object : MediaDataManager.Listener {
        override fun onMediaDataLoaded(
            key: String,
            oldKey: String?,
            data: MediaData,
            immediately: Boolean,
        ) {
            if (data.active || data.isPlaying == true) {
                isMediaPlaying = data.isPlaying ?: data.active
                val icon = data.artwork
                if (icon != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val drawable = try {
                            icon.loadDrawable(context)
                        } catch (e: Exception) {
                            null
                        }
                        withContext(Dispatchers.Main) {
                            if (drawable != null) {
                                artworkDrawable = drawable
                                onMediaStateChanged()
                                if (scrimState == MediaScrimState.STATE_SCRIM_VISIBLE) {
                                    updateMediaArt()
                                }
                            }
                        }
                    }
                }
            } else if (data.isPlaying == false && !data.active) {
                isMediaPlaying = false
                coroutineScope.launch {
                    onMediaStateChanged()
                }
            }
        }

        override fun onMediaDataRemoved(key: String, userInitiated: Boolean) {
            artworkDrawable = null
            isMediaPlaying = false
            coroutineScope.launch {
                onMediaStateChanged()
            }
        }

        override fun onCurrentActiveMediaChanged(key: String?, data: MediaData?) {
            if (data == null) {
                artworkDrawable = null
                isMediaPlaying = false
                coroutineScope.launch {
                    onMediaStateChanged()
                }
            } else if (data.active) {
                isMediaPlaying = data.isPlaying ?: true
                val icon = data.artwork
                if (icon != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val drawable = icon.loadDrawable(context)
                        withContext(Dispatchers.Main) {
                            if (drawable != null) {
                                artworkDrawable = drawable
                                onMediaStateChanged()
                                if (scrimState == MediaScrimState.STATE_SCRIM_VISIBLE) {
                                    updateMediaArt()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private val mediaScrim = ImageView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = View.GONE
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private var mediaArtJob: Job? = null
    private val mediaFadeLevel = 40

    init {
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.LS_MEDIA_ART_ENABLED),
            false,
            settingsObserver,
            UserHandle.USER_ALL
        )

        updateSettings()
    }

    private fun updateSettings() {
        featureEnabled = Settings.System.getIntForUser(
            context.contentResolver,
            Settings.System.LS_MEDIA_ART_ENABLED,
            0,
            UserHandle.USER_CURRENT
        ) == 1

        if (featureEnabled && !listening) {
            mediaManager.get().addCallback(this)
            mediaDataManager.get().addListener(mediaDataListener)
            keyguardStateController.get().addCallback(this)
            statusBarStateController.get().addCallback(this)
            wakefulnessLifecycle.get().addObserver(this)

            mediaPipelineJob?.cancel()
            mediaPipelineJob = coroutineScope.launch {
                combine(
                    mediaPipelineRepository.get().currentUserEntries,
                    mediaPipelineRepository.get().allMediaEntries
                ) { userEntries, allEntries ->
                    userEntries.values.ifEmpty { allEntries.values }
                }.collect { entries ->
                    val activePlaying = entries.firstOrNull { it.active && it.isPlaying == true }
                        ?: entries.firstOrNull { it.isPlaying == true }
                        ?: entries.firstOrNull { it.active }
                    if (activePlaying != null) {
                        isMediaPlaying = activePlaying.isPlaying ?: activePlaying.active
                        val artworkIcon = activePlaying.artwork
                        if (artworkIcon != null) {
                            coroutineScope.launch(Dispatchers.IO) {
                                val drawable = try {
                                    artworkIcon.loadDrawable(context)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to load artwork drawable", e)
                                    null
                                }
                                withContext(Dispatchers.Main) {
                                    if (drawable != null) {
                                        artworkDrawable = drawable
                                        onMediaStateChanged()
                                        if (scrimState == MediaScrimState.STATE_SCRIM_VISIBLE) {
                                            updateMediaArt()
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (mediaManager.get().mediaMetadata == null) {
                            artworkDrawable = null
                            isMediaPlaying = false
                        }
                    }
                    onMediaStateChanged()
                }
            }

            if (SceneContainerFlag.isEnabled) {
                sceneJob?.cancel()
                sceneJob = coroutineScope.launch {
                    launch {
                        sceneInteractor.get().transitionStateFlow.collect {
                            onMediaStateChanged()
                        }
                    }
                    launch {
                        sceneInteractor.get().currentOverlays.collect {
                            onMediaStateChanged()
                        }
                    }
                    launch {
                        sceneInteractor.get().currentScene.collect {
                            onMediaStateChanged()
                        }
                    }
                }
            }
            listening = true
        } else if (!featureEnabled && listening) {
            mediaManager.get().removeCallback(this)
            mediaDataManager.get().removeListener(mediaDataListener)
            keyguardStateController.get().removeCallback(this)
            statusBarStateController.get().removeCallback(this)
            wakefulnessLifecycle.get().removeObserver(this)
            mediaPipelineJob?.cancel()
            mediaPipelineJob = null
            sceneJob?.cancel()
            sceneJob = null
            listening = false
            cleanupResources()
        }
        coroutineScope.launch {
            onMediaStateChanged()
        }
    }

    private fun shouldShowMediaArt(): Boolean {
        val isPortrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val isAwake = wakefulnessLifecycle.get().wakefulness == WakefulnessLifecycle.WAKEFULNESS_AWAKE ||
            wakefulnessLifecycle.get().wakefulness == WakefulnessLifecycle.WAKEFULNESS_WAKING
        val isDozing = statusBarStateController.get().isDozing

        val isKeyguard: Boolean
        val isPanelFullyCollapsed: Boolean
        val notBouncerOrDismissing: Boolean

        if (SceneContainerFlag.isEnabled) {
            val scene = sceneInteractor.get()
            val currentScene = scene.currentScene.value
            val transitionState = scene.transitionStateFlow.value
            val kg = keyguardStateController.get()

            val hasCoveringOverlay = scene.currentOverlays.value.any {
                it == Overlays.Bouncer ||
                    it == Overlays.NotificationsShade ||
                    it == Overlays.QuickSettingsShade
            }

            val isIdleOnLockscreen = when (transitionState) {
                is ObservableTransitionState.Idle -> {
                    (transitionState.currentScene == Scenes.Lockscreen || currentScene == Scenes.Lockscreen) && !hasCoveringOverlay
                }
                is ObservableTransitionState.Transition -> {
                    val toLockscreen = transitionState.toContent == Scenes.Lockscreen
                    val leavingLockscreen = transitionState.toContent in listOf(
                        Scenes.Gone, Scenes.Shade, Scenes.QuickSettings, Scenes.Communal
                    ) || transitionState.toContent in listOf(
                        Overlays.Bouncer, Overlays.NotificationsShade, Overlays.QuickSettingsShade
                    )
                    (toLockscreen || (transitionState.fromContent == Scenes.Lockscreen && !leavingLockscreen)) &&
                        !hasCoveringOverlay
                }
            }

            val isOnLockscreen = isIdleOnLockscreen || (currentScene == Scenes.Lockscreen && !hasCoveringOverlay)

            isKeyguard = isOnLockscreen
            isPanelFullyCollapsed = isOnLockscreen
            notBouncerOrDismissing = isOnLockscreen && (Overlays.Bouncer !in scene.currentOverlays.value)
        } else {
            val kg = keyguardStateController.get()
            val sb = statusBarStateController.get()
            isKeyguard = kg.isShowing && sb.state == StatusBarState.KEYGUARD
            isPanelFullyCollapsed = !sb.isExpanded
            notBouncerOrDismissing = !kg.isPrimaryBouncerShowing &&
                !kg.isKeyguardGoingAway &&
                !kg.isKeyguardFadingAway
        }

        return featureEnabled && artworkDrawable != null && isAwake && !isDozing &&
            isPortrait && isKeyguard &&
            isMediaPlaying && isPanelFullyCollapsed &&
            notBouncerOrDismissing
    }

    private fun ensureAttached() {
        val windowRootView = notificationShadeWindowController.get().windowRootView ?: return
        if (SceneContainerFlag.isEnabled) {
            if (mediaScrim.parent != windowRootView) {
                (mediaScrim.parent as? ViewGroup)?.removeView(mediaScrim)
                windowRootView.addView(mediaScrim, 0)
            } else if (windowRootView.indexOfChild(mediaScrim) != 0) {
                windowRootView.removeView(mediaScrim)
                windowRootView.addView(mediaScrim, 0)
            }
        } else {
            val targetView = windowRootView.findViewById<ViewGroup>(R.id.notification_container_parent) ?: windowRootView
            if (mediaScrim.parent != targetView) {
                (mediaScrim.parent as? ViewGroup)?.removeView(mediaScrim)
                targetView.addView(mediaScrim, 0)
            }
        }
    }

    private fun showMediaArt() {
        ensureAttached()
        notificationShadeWindowController.get().setBackdropShowing(true)
        updateMediaArt()
        mediaScrim.animate().cancel()
        mediaScrim.apply {
            alpha = 0f
            visibility = View.VISIBLE
            scrimState = MediaScrimState.STATE_SCRIM_VISIBLE
            animate()
                .alpha(1f)
                .setDuration(300)
                .setListener(null)
                .start()
        }
    }

    private fun updateMediaArt() {
        mediaArtJob?.cancel()
        mediaArtJob = coroutineScope.launch {
            processArtwork().let { drawable ->
                mediaScrim.setImageDrawable(drawable)
            }
        }
    }

    private fun processArtwork(): LayerDrawable {
        val drawable = artworkDrawable ?: return LayerDrawable(arrayOf())

        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        val drawableWidth = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else mediaScrim.width.takeIf { it > 0 } ?: screenWidth
        val drawableHeight = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else mediaScrim.height.takeIf { it > 0 } ?: screenHeight

        val fadeColor = ColorUtils.blendARGB(
            Color.TRANSPARENT,
            Color.BLACK,
            mediaFadeLevel / 100f
        )
        val fadeOverlay = ColorDrawable(fadeColor)

        val clonedDrawable = drawable.constantState?.newDrawable(context.resources)?.mutate() ?: drawable
        clonedDrawable.setBounds(0, 0, drawableWidth, drawableHeight)
        fadeOverlay.setBounds(0, 0, screenWidth, screenHeight)

        return LayerDrawable(arrayOf(clonedDrawable, fadeOverlay)).apply {
            setBounds(0, 0, screenWidth, screenHeight)
        }
    }

    fun cleanupResources() {
        mediaArtJob?.cancel()
        notificationShadeWindowController.get().setBackdropShowing(false)
        mediaScrim.animate().cancel()
        mediaScrim.animate()
            .alpha(0f)
            .setDuration(100)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (scrimState == MediaScrimState.STATE_SCRIM_HIDDEN) {
                        mediaScrim.setImageDrawable(null)
                        mediaScrim.visibility = View.GONE
                    }
                }
            })
            .start()
        scrimState = MediaScrimState.STATE_SCRIM_HIDDEN
    }

    fun setAlbumArt(drawable: Drawable?) {
        coroutineScope.launch {
            artworkDrawable = drawable
            if (drawable != null) {
                isMediaPlaying = true
            }
            onMediaStateChanged()
            if (scrimState == MediaScrimState.STATE_SCRIM_VISIBLE) {
                updateMediaArt()
            }
        }
    }

    override fun onPrimaryMetadataOrStateChanged(metadata: MediaMetadata?, state: Int) {
        if (state != PlaybackState.STATE_NONE) {
            isMediaPlaying = NotificationMediaManager.isPlayingState(state)
        }
        if (metadata == null && state == PlaybackState.STATE_NONE) {
            artworkDrawable = null
            isMediaPlaying = false
        } else if (metadata != null) {
            val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            if (art != null) {
                artworkDrawable = BitmapDrawable(context.resources, art)
                if (scrimState == MediaScrimState.STATE_SCRIM_VISIBLE) {
                    updateMediaArt()
                }
            } else {
                val uriStr = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                if (uriStr != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val uri = Uri.parse(uriStr)
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                                if (bitmap != null) {
                                    withContext(Dispatchers.Main) {
                                        artworkDrawable = BitmapDrawable(context.resources, bitmap)
                                        onMediaStateChanged()
                                        if (scrimState == MediaScrimState.STATE_SCRIM_VISIBLE) {
                                            updateMediaArt()
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to load artwork from URI: $uriStr", e)
                        }
                    }
                }
            }
        }
        coroutineScope.launch {
            onMediaStateChanged()
        }
    }

    override fun onKeyguardShowingChanged() {
        coroutineScope.launch {
            onMediaStateChanged()
        }
    }

    override fun onKeyguardGoingAwayChanged() {
        if (!SceneContainerFlag.isEnabled && keyguardStateController.get().isKeyguardGoingAway) {
            cleanupResources()
        }
        retry()
    }

    override fun onKeyguardFadingAwayChanged() {
        if (!SceneContainerFlag.isEnabled && keyguardStateController.get().isKeyguardFadingAway) {
            cleanupResources()
        }
        retry()
    }

    override fun onPrimaryBouncerShowingChanged() {
        if (!SceneContainerFlag.isEnabled && keyguardStateController.get().isPrimaryBouncerShowing) {
            cleanupResources()
        }
        retry()
    }

    override fun onDozingChanged(isDozing: Boolean) {
        coroutineScope.launch {
            onMediaStateChanged()
        }
    }

    override fun onStateChanged(newState: Int) {
        coroutineScope.launch {
            onMediaStateChanged()
        }
    }

    override fun onExpandedChanged(isExpanded: Boolean) {
        coroutineScope.launch {
            onMediaStateChanged()
        }
    }

    override fun onStartedGoingToSleep() {
        cleanupResources()
    }

    override fun onStartedWakingUp() {
        coroutineScope.launch {
            onMediaStateChanged()
        }
        retry()
    }

    override fun onFinishedWakingUp() {
        coroutineScope.launch {
            onMediaStateChanged()
        }
        retry()
    }

    fun retry() {
        mediaScrim.postDelayed({
            coroutineScope.launch {
                if (shouldShowMediaArt()) {
                    onMediaStateChanged()
                }
            }
        }, 500)
    }

    private suspend fun onMediaStateChanged() {
        val show = shouldShowMediaArt()
        if (show && scrimState == MediaScrimState.STATE_SCRIM_HIDDEN) {
            showMediaArt()
        } else if (!show && scrimState == MediaScrimState.STATE_SCRIM_VISIBLE) {
            cleanupResources()
        }
    }

    fun getMediaArtScrim() = mediaScrim

    companion object {
        private const val TAG = "MediaArtViewController"

        @JvmStatic
        fun get(context: Context): MediaArtViewController {
            val app = context.applicationContext as? SystemUIApplicationImpl
                ?: throw IllegalStateException("Application is not SystemUIApplicationImpl")
            return app.getSysUIComponent().mediaArtViewController()
        }
    }
}
