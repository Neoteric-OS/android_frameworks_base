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

package com.android.systemui.util

import android.util.Log

class JankMonitorUtils private constructor() {
    companion object {
        @JvmStatic
        val instance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            JankMonitorUtils()
        }

        private const val TAG = "JankMonitorUtils"
    }

    private val callbacks = LinkedHashSet<MonitorCallback>()

    var isInJankMonitor = false
        private set(value) {
            field = value
        }

    fun updateJunkMonitorScene(isMonitor: Boolean, reason : String) {
        Log.d(TAG, "updateJunkMonitorScene: $isMonitor, reason: $reason")
        isInJankMonitor = isMonitor
        if (isMonitor) {
            callbacks.forEach {
                it.onJankMonitorStart()
            }
        } else {
            callbacks.forEach {
                it.onJankMonitorEnd()
            }
        }
    }

    @Synchronized
    fun addCallback(callback: MonitorCallback) {
        callbacks.add(callback)
    }

    @Synchronized
    fun removeCallback(callback: MonitorCallback) {
        callbacks.remove(callback)
    }

    interface MonitorCallback {
        fun onJankMonitorStart()
        fun onJankMonitorEnd()
    }
}
