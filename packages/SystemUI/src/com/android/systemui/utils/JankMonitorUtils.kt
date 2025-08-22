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
