/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server

import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.os.SystemClock
import android.util.SparseBooleanArray
import androidx.test.InstrumentationRegistry
import com.android.internal.util.WakeupMessage
import org.junit.Assert.fail

/**
 * A [ConnectivityService.Dependencies] class that provides common mock mechanics for tests.
 */
open class TestConnectivityServiceDependencies: ConnectivityService.Dependencies() {
    override fun getNextAvailableNetId(nextNetId: Int, netIdInUse: SparseBooleanArray): Int {
        var idToTest = nextNetId
        while (true) {
            val netId = super.getNextAvailableNetId(idToTest, netIdInUse)

            // Don't overlap test NetIDs with real NetIDs as binding sockets to real networks
            // can have odd side-effects, like network validations succeeding.
            val context = InstrumentationRegistry.getContext()
            val overlaps = ConnectivityManager.from(context).allNetworks.any { netId == it.netId }
            if (!overlaps) {
                return netId
            }

            idToTest = getNextNetId(netId)
        }
    }

    override fun makeWakeupMessage(context: Context, handler: Handler, cmdName: String,
                                   cmd: Int, obj: Any): WakeupMessage {
        return FakeWakeupMessage(context, handler, cmdName, cmd, 0, 0, obj)
    }

    private class FakeWakeupMessage internal constructor(
            context: Context,
            handler: Handler,
            cmdName: String,
            cmd: Int,
            arg1: Int,
            arg2: Int,
            obj: Any) : WakeupMessage(context, handler, cmdName, cmd, arg1, arg2, obj) {

        override fun schedule(scheduleTime: Long) {
            var delayMs = scheduleTime - SystemClock.elapsedRealtime()
            if (delayMs < 0) delayMs = 0
            if (delayMs > UNREASONABLY_LONG_WAIT_MS) {
                fail("Attempting to send msg more than $UNREASONABLY_LONG_WAIT_MS "
                        + "ms into the future: $delayMs")
            }
            val msg = mHandler.obtainMessage(mCmd, mArg1, mArg2, mObj)
            mHandler.sendMessageDelayed(msg, delayMs)
        }

        override fun cancel() {
            mHandler.removeMessages(mCmd, mObj)
        }

        override fun onAlarm() {
            throw AssertionError("Should never happen. Update this fake.")
        }

        companion object {
            private const val UNREASONABLY_LONG_WAIT_MS = 1000
        }
    }
}
