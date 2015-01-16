/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.internal.os;

import android.os.Process;
import android.util.Slog;

import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;

import libcore.io.IoUtils;

/**
 * Startup class for the wrapper process.
 * @hide
 */
public class WrapperInit {
    private final static String TAG = "AndroidRuntime";

    /**
     * Class not instantiable.
     */
    private WrapperInit() {
    }

    /**
     * The main function called when starting a runtime application through a
     * wrapper process instead of by forking Zygote.
     *
     * The first argument specifies the file descriptor for a pipe that should receive
     * the pid of this process, or 0 if none.
     *
     * The second argument is the target SDK version for the app.
     *
     * The remaining arguments are passed to the runtime.
     *
     * @param args The command-line arguments.
     */
    public static void main(String[] args) {
        try {
            // Parse our mandatory arguments.
            int targetSdkVersion;
            try {
                targetSdkVersion = Integer.parseInt(args[0], 10);
            } catch (NumberFormatException nfe) {
                Slog.e(TAG, "Invalid targetSdkVersion: " + args[0]);
                throw nfe;
            }

            // Mimic Zygote preloading.
            ZygoteInit.preload();

            // Launch the application.
            String[] runtimeArgs = new String[args.length - 2];
            System.arraycopy(args, 2, runtimeArgs, 0, runtimeArgs.length);
            RuntimeInit.wrapperInit(targetSdkVersion, runtimeArgs);
        } catch (ZygoteInit.MethodAndArgsCaller caller) {
            caller.run();
        }
    }

    /**
     * Executes a runtime application with a wrapper command.
     * This method never returns.
     *
     * @param invokeWith The wrapper command.
     * @param niceName The nice name for the application, or null if none.
     * @param targetSdkVersion The target SDK version for the app.
     * @param pipeFd The pipe to which the application's pid should be written, or null if none.
     * @param args Arguments for {@link RuntimeInit#main}.
     */
    public static void execApplication(String invokeWith, String niceName,
            int targetSdkVersion, FileDescriptor pipeFd, String[] args) {
        sendPidToParent(pipeFd);
        StringBuilder command = new StringBuilder(invokeWith);
        command.append(" /system/bin/app_process /system/bin --application");
        if (niceName != null) {
            command.append(" '--nice-name=").append(niceName).append("'");
        }
        command.append(" com.android.internal.os.WrapperInit ");
        command.append(targetSdkVersion);
        Zygote.appendQuotedShellArgs(command, args);
        Zygote.execShell(command.toString());
    }

    /**
     * Tell the zygote about or PID before we exec, at which point the FD will be
     * close since it's marked O_CLOEXEC. Calls to exec() won't change our PID.
     */
    private static void sendPidToParent(FileDescriptor pipeFd) {
        try {
            DataOutputStream os = new DataOutputStream(new FileOutputStream(pipeFd));
            os.writeInt(Process.myPid());
            os.close();
        } catch (IOException ex) {
            Slog.d(TAG, "Could not write pid of wrapped process to Zygote pipe.", ex);
        } finally {
            IoUtils.closeQuietly(pipeFd);
        }
    }

    /**
     * Executes a standalone application with a wrapper command.
     * This method never returns.
     *
     * @param invokeWith The wrapper command.
     * @param classPath The class path.
     * @param className The class name to invoke.
     * @param args Arguments for the main() method of the specified class.
     */
    public static void execStandalone(String invokeWith, String classPath, String className,
            String[] args) {
        StringBuilder command = new StringBuilder(invokeWith);
        command.append(" /system/bin/dalvikvm -classpath '").append(classPath);
        command.append("' ").append(className);
        Zygote.appendQuotedShellArgs(command, args);
        Zygote.execShell(command.toString());
    }
}
