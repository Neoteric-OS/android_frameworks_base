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
 * limitations under the License.
 */

package com.android.server.recoverysystem;

import android.os.IRecoverySystem;
import android.os.RemoteException;
import android.os.ShellCommand;

import java.io.PrintWriter;

/**
 * Shell commands to call to {@link RecoverySystemService} from ADB.
 */
public class RecoverySystemShellCommand extends ShellCommand {
    private final IRecoverySystem mService;

    public RecoverySystemShellCommand(RecoverySystemService service) {
        mService = service;
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(cmd);
        }
        try {
            switch (cmd) {
                case "request-lskf":
                    return requestLskf();
                case "clear-lskf":
                    return clearLskf();
                case "reboot-and-apply":
                    return rebootAndApply();
                default:
                    return handleDefaultCommands(cmd);
            }
        } catch (Exception e) {
            getErrPrintWriter().println("Error while executing command: " + cmd);
            e.printStackTrace(getErrPrintWriter());
            return -1;
        }
    }

    private int requestLskf() throws RemoteException {
        String callerId = getNextArgRequired();
        boolean success = mService.requestLskf(callerId, null);
        PrintWriter pw = getOutPrintWriter();
        pw.printf("Request LSKF for callerId: %s, status: %s\n", callerId,
                success ? "success" : "failure");
        return 0;
    }

    private int clearLskf() throws RemoteException {
        String callerId = getNextArgRequired();
        boolean success = mService.clearLskf(callerId);
        PrintWriter pw = getOutPrintWriter();
        pw.printf("Clear LSKF for callerId: %s, status: %s\n", callerId,
                success ? "success" : "failure");
        return 0;
    }

    private int rebootAndApply() throws RemoteException {
        String callerId = getNextArgRequired();
        String rebootReason = getNextArgRequired();
        boolean success = mService.rebootWithLskf(callerId, rebootReason, true);
        PrintWriter pw = getOutPrintWriter();
        pw.printf("Reboot and apply for callerId: %s, status: %s\n", callerId,
                success ? "success" : "failure");
        return 0;
    }

    @Override
    public void onHelp() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Recovery system commands:");
        pw.println("  request-lskf <token>");
        pw.println("  clear-lskf");
        pw.println("  reboot-and-apply <token> <reason>");
    }
}
