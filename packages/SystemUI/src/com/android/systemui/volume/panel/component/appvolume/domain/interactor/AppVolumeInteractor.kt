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

package com.android.systemui.volume.panel.component.appvolume.domain.interactor

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.AppVolume
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn

@SysUISingleton
class AppVolumeInteractor
@Inject
constructor(
    @Application private val context: Context,
    @Application private val coroutineScope: CoroutineScope,
    @Background private val backgroundHandler: Handler,
    private val audioManager: AudioManager,
    private val packageManager: PackageManager,
) {
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val playbackUpdatesFlow: Flow<Unit> =
        callbackFlow {
            val callback =
                object : AudioManager.AudioPlaybackCallback() {
                    override fun onPlaybackConfigChanged(
                        configs: MutableList<AudioPlaybackConfiguration>?
                    ) {
                        trySend(Unit)
                    }
                }
            audioManager.registerAudioPlaybackCallback(callback, backgroundHandler)
            trySend(Unit)
            awaitClose { audioManager.unregisterAudioPlaybackCallback(callback) }
        }

    val activeAppVolumes: Flow<List<AppVolume>> =
        merge(playbackUpdatesFlow, refreshTrigger)
            .map { getFilteredAppVolumes() }
            .flowOn(Dispatchers.IO)
            .shareIn(coroutineScope, SharingStarted.WhileSubscribed(), replay = 1)

    val isSettingEnabled: Flow<Boolean> =
        conflatedCallbackFlow {
            val uri = Settings.System.getUriFor(Settings.System.SHOW_APP_VOLUME)
            val observer =
                object : ContentObserver(backgroundHandler) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(isSettingEnabled())
                    }
                }
            context.contentResolver.registerContentObserver(
                uri,
                false,
                observer,
                UserHandle.USER_CURRENT,
            )
            trySend(isSettingEnabled())
            awaitClose { context.contentResolver.unregisterContentObserver(observer) }
        }
        .flowOn(Dispatchers.IO)
        .shareIn(coroutineScope, SharingStarted.WhileSubscribed(), replay = 1)

    val isAvailable: Flow<Boolean> =
        combine(isSettingEnabled, activeAppVolumes) { settingEnabled, appVolumes ->
            settingEnabled && appVolumes.isNotEmpty()
        }
        .distinctUntilChanged()

    fun isSettingEnabled(): Boolean {
        return Settings.System.getIntForUser(
            context.contentResolver,
            Settings.System.SHOW_APP_VOLUME,
            0,
            UserHandle.USER_CURRENT,
        ) == 1
    }

    private fun getFilteredAppVolumes(): List<AppVolume> {
        val list = audioManager.listAppVolumes()
        return list.filter { av ->
            av.isActive &&
                runCatching { packageManager.getApplicationInfo(av.packageName, 0) }.isSuccess
        }
    }

    fun setAppVolume(packageName: String, volume: Float) {
        audioManager.setAppVolume(packageName, volume)
        refreshTrigger.tryEmit(Unit)
    }

}
