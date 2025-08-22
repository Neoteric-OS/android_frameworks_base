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
 * limitations under the License
 */

package com.android.systemui.statusbar

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.PluginManager
import com.android.systemui.statusbar.domain.interactor.SilentNotificationStatusIconsVisibilityInteractor
import com.android.systemui.statusbar.notification.collection.NotifCollection
import com.android.systemui.util.JankMonitorUtils
import com.android.systemui.util.time.SystemClock
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
@SuppressLint("OverrideAbstract")
class AsyncNotificationListener @Inject constructor(
    private val context: Context,
    private val notificationManager: NotificationManager,
    private val statusIconInteractor: SilentNotificationStatusIconsVisibilityInteractor,
    private val systemClock: SystemClock,
    @Main private val mainExecutor: Executor,
    private val pluginManager: PluginManager
) : NotificationListener (context, notificationManager, statusIconInteractor,
    systemClock, mainExecutor, pluginManager) {
    companion object {
        private const val TAG = "AsyncNotificationListener"
        private const val REASON_TIME = "BARRIER_MAX_TIME"
        private const val MSG_TIME_INTERVAL = 50L
        private const val MAX_BARRIER_TIME = 2000L
    }
    private val notificationQueue : MutableList<NotificationRecord> = ArrayList()
    private val thread : HandlerThread by lazy {
        HandlerThread("AsyncNotificationListener")
    }
    private val handler : Handler by lazy {
        thread.start()
        Handler(thread.looper)
    }
    private val processRunnable = Runnable {
        processNotificationQueue()
    }

    private val barrierRunnable = Runnable {
        JankMonitorUtils.instance.updateJunkMonitorScene(false, REASON_TIME)
    }

    private val monitorCallback = object : JankMonitorUtils.MonitorCallback {
        override fun onJankMonitorEnd() {
            handler.removeCallbacks(processRunnable)
            handler.removeCallbacks(barrierRunnable)
            handler.postDelayed(processRunnable, MSG_TIME_INTERVAL)
        }

        override fun onJankMonitorStart() {
            handler.removeCallbacks(processRunnable)
            handler.removeCallbacks(barrierRunnable)
            handler.postDelayed(barrierRunnable, MAX_BARRIER_TIME)
        }
    }

    init {
        JankMonitorUtils.instance.addCallback(monitorCallback)
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int
    ) {
        handler.post {
            val record = NotificationRecord()
            record.sbn = sbn
            record.rankingMap = rankingMap
            record.reason = reason
            record.type = ActionType.REMOVE
            notificationQueue.add(record)
        }
        handler.removeCallbacks(processRunnable)
        if(!JankMonitorUtils.instance.isInJankMonitor) {
            handler.postDelayed(processRunnable, MSG_TIME_INTERVAL)
        }
        //super.onNotificationRemoved(sbn, rankingMap, reason)
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?
    ) {
        handler.post {
            val record = NotificationRecord()
            record.sbn = sbn
            record.rankingMap = rankingMap
            record.type = ActionType.POST
            notificationQueue.add(record)
        }
        handler.removeCallbacks(processRunnable)
        if(!JankMonitorUtils.instance.isInJankMonitor) {
            handler.postDelayed(processRunnable, MSG_TIME_INTERVAL)
        }
        //super.onNotificationPosted(sbn, rankingMap)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        handler.post {
            val record = NotificationRecord()
            record.rankingMap = rankingMap
            record.type = ActionType.UPDATE
            notificationQueue.add(record)
        }
        handler.removeCallbacks(processRunnable)
        if(!JankMonitorUtils.instance.isInJankMonitor) {
            handler.postDelayed(processRunnable, MSG_TIME_INTERVAL)
        }
        //super.onNotificationRankingUpdate(rankingMap)
    }

    override fun onNotificationChannelModified(
        pkgName: String?,
        user: UserHandle?,
        channel: NotificationChannel?,
        modificationType: Int
    ) {
        handler.post {
            val record = NotificationRecord()
            record.pkgName = pkgName
            record.user = user
            record.channel = channel
            record.modificationType = modificationType
            record.type = ActionType.CHANNEL_MODIFIED
            notificationQueue.add(record)
        }
        handler.removeCallbacks(processRunnable)
        if(!JankMonitorUtils.instance.isInJankMonitor) {
            handler.postDelayed(processRunnable, MSG_TIME_INTERVAL)
        }
        //super.onNotificationChannelModified(pkgName, user, channel, modificationType)
    }

    private fun processNotificationQueue() {
        if(!JankMonitorUtils.instance.isInJankMonitor
                        && notificationQueue.isNotEmpty()) {
            val record = notificationQueue.removeAt(0)
            Log.d(TAG, "processNotificationQueue record type ${record.type}")
            if (record.type == ActionType.POST) {
                super.onNotificationPosted(record.sbn, record.rankingMap)
            } else if (record.type == ActionType.REMOVE) {
                super.onNotificationRemoved(record.sbn, record.rankingMap, record.reason)
            } else if (record.type == ActionType.UPDATE) {
                super.onNotificationRankingUpdate(record.rankingMap)
            } else if (record.type == ActionType.CHANNEL_MODIFIED) {
                super.onNotificationChannelModified(
                    record.pkgName, record.user,
                    record.channel, record.modificationType
                )
            }
            handler.removeCallbacks(processRunnable)
            handler.postDelayed(processRunnable, MSG_TIME_INTERVAL)
        } else {
            handler.removeCallbacks(processRunnable)
        }
    }

    inner class NotificationRecord {
        var sbn: StatusBarNotification? = null
        var rankingMap: RankingMap? = null
        var reason: Int = NotifCollection.REASON_UNKNOWN
        var pkgName: String? = null
        var user: UserHandle? = null
        var channel: NotificationChannel? = null
        var modificationType: Int = 0
        var type: ActionType = ActionType.POST
    }

    enum class ActionType {
        POST,
        REMOVE,
        UPDATE,
        CHANNEL_MODIFIED
    }
}