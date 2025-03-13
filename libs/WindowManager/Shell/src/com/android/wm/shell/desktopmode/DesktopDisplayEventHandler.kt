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

package com.android.wm.shell.desktopmode

import android.content.Context
import android.view.Display.DEFAULT_DISPLAY
import android.window.DesktopExperienceFlags
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayController.OnDisplaysChangedListener
import com.android.wm.shell.desktopmode.multidesks.OnDeskRemovedListener
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus
import com.android.wm.shell.sysui.ShellInit

/** Handles display events in desktop mode */
class DesktopDisplayEventHandler(
    private val context: Context,
    shellInit: ShellInit,
    private val displayController: DisplayController,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val desktopTasksController: DesktopTasksController,
    private val desktopDisplayModeController: DesktopDisplayModeController,
) : OnDisplaysChangedListener, OnDeskRemovedListener {

    private val desktopRepository: DesktopRepository
        get() = desktopUserRepositories.current

    init {
        shellInit.addInitCallback({ onInit() }, this)
    }

    private fun onInit() {
        displayController.addDisplayWindowListener(this)

        if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue()) {
            desktopTasksController.onDeskRemovedListener = this
        }
    }

    override fun onDisplayAdded(displayId: Int) {
        if (displayId != DEFAULT_DISPLAY) {
            desktopDisplayModeController.refreshDisplayWindowingMode()
        }

        if (!supportsDesks(displayId)) {
            logV("Display #$displayId does not support desks")
            return
        }
        logV("Creating new desk in new display#$displayId")
        // TODO: b/362720497 - when SystemUI crashes with a freeform task open for any reason, the
        //  task is recreated and received in [FreeformTaskListener] before this display callback
        //  is invoked, which results in the repository trying to add the task to a desk before the
        //  desk has been recreated here, which may result in a crash-loop if the repository is
        //  checking that the desk exists before adding a task to it. See b/391984373.
        desktopTasksController.createDesk(displayId)
        // TODO: b/393978539 - consider activating the desk on creation when applicable, such as
        //  for connected displays.
    }

    override fun onDisplayRemoved(displayId: Int) {
        if (displayId != DEFAULT_DISPLAY) {
            desktopDisplayModeController.refreshDisplayWindowingMode()
        }

        // TODO: b/362720497 - move desks in closing display to the remaining desk.
    }

    override fun onDeskRemoved(lastDisplayId: Int, deskId: Int) {
        val remainingDesks = desktopRepository.getNumberOfDesks(lastDisplayId)
        if (remainingDesks == 0) {
            logV("All desks removed from display#$lastDisplayId, creating empty desk")
            desktopTasksController.createDesk(lastDisplayId)
        }
    }

    // TODO: b/362720497 - connected/projected display considerations.
    private fun supportsDesks(displayId: Int): Boolean =
        DesktopModeStatus.canEnterDesktopMode(context)

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "DesktopDisplayEventHandler"
    }
}
