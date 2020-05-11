/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server;

import static com.android.internal.util.FrameworkStatsLog.USERSPACE_REBOOT_REPORTED__OUTCOME__FAILED_SHUTDOWN_SEQUENCE_ABORTED;
import static com.android.internal.util.FrameworkStatsLog.USERSPACE_REBOOT_REPORTED__OUTCOME__FAILED_USERDATA_REMOUNT;
import static com.android.internal.util.FrameworkStatsLog.USERSPACE_REBOOT_REPORTED__OUTCOME__FAILED_USERSPACE_REBOOT_WATCHDOG_TRIGGERED;
import static com.android.internal.util.FrameworkStatsLog.USERSPACE_REBOOT_REPORTED__OUTCOME__OUTCOME_UNKNOWN;
import static com.android.internal.util.FrameworkStatsLog.USERSPACE_REBOOT_REPORTED__OUTCOME__SUCCESS;
import static com.android.internal.util.FrameworkStatsLog.USERSPACE_REBOOT_REPORTED__USER_ENCRYPTION_STATE__LOCKED;
import static com.android.internal.util.FrameworkStatsLog.USERSPACE_REBOOT_REPORTED__USER_ENCRYPTION_STATE__UNLOCKED;

import android.content.Context;
import android.os.FileUtils;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Slog;

import com.android.internal.os.BackgroundThread;
import com.android.internal.util.FrameworkStatsLog;
import com.android.internal.util.IndentingPrintWriter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Utility class to help abstract logging {@code UserspaceRebootReported} atom.
 */
public final class UserspaceRebootLogger {

    private static final String TAG = "UserspaceRebootLogger";

    private static final String USERSPACE_REBOOT_SHOULD_LOG_PROPERTY =
            "persist.sys.userspace_reboot.log.should_log";
    private static final String USERSPACE_REBOOT_LAST_STARTED_PROPERTY =
            "sys.userspace_reboot.log.last_started";
    private static final String USERSPACE_REBOOT_LAST_FINISHED_PROPERTY =
            "sys.userspace_reboot.log.last_finished";
    private static final String LAST_BOOT_REASON_PROPERTY = "sys.boot.reason.last";

    private static final String LOGGING_DIRECTORY = "/metadata/userspacereboot";
    private static final long RETENTION_THRESHOLD = TimeUnit.DAYS.toMillis(3);

    private UserspaceRebootLogger() {}

    /**
     * Modifies internal state to note that {@code UserspaceRebootReported} atom needs to be
     * logged on the next successful boot.
     *
     * <p>This call should only be made on devices supporting userspace reboot.
     */
    public static void noteUserspaceRebootWasRequested() {
        if (!PowerManager.isRebootingUserspaceSupportedImpl()) {
            Slog.wtf(TAG, "Userspace reboot is not supported.");
            return;
        }

        SystemProperties.set(USERSPACE_REBOOT_SHOULD_LOG_PROPERTY, "1");
        SystemProperties.set(USERSPACE_REBOOT_LAST_STARTED_PROPERTY,
                String.valueOf(SystemClock.elapsedRealtime()));
    }

    /**
     * Updates internal state on boot after successful userspace reboot.
     *
     * <p>Should be called right before framework sets {@code sys.boot_completed} property.
     *
     * <p>This call should only be made on devices supporting userspace reboot.
     */
    public static void noteUserspaceRebootSuccess() {
        if (!PowerManager.isRebootingUserspaceSupportedImpl()) {
            Slog.wtf(TAG, "Userspace reboot is not supported.");
            return;
        }

        SystemProperties.set(USERSPACE_REBOOT_LAST_FINISHED_PROPERTY,
                String.valueOf(SystemClock.elapsedRealtime()));
    }

    /**
     * Returns {@code true} if {@code UserspaceRebootReported} atom should be logged.
     *
     * <p>This call should only be made on devices supporting userspace reboot.
     */
    public static boolean shouldLogUserspaceRebootEvent() {
        if (!PowerManager.isRebootingUserspaceSupportedImpl()) {
            Slog.wtf(TAG, "Userspace reboot is not supported.");
            return false;
        }

        return SystemProperties.getBoolean(USERSPACE_REBOOT_SHOULD_LOG_PROPERTY, false);
    }

    /**
     * Asynchronously logs {@code UserspaceRebootReported} on the given {@code executor}.
     *
     * <p>Should be called in the end of {@link
     * com.android.server.am.ActivityManagerService#finishBooting()} method, after framework have
     * tried to proactivelly unlock storage of the primary user.
     *
     * <p>This call should only be made on devices supporting userspace reboot.
     */
    public static void logEventAsync(boolean userUnlocked, Executor executor) {
        if (!PowerManager.isRebootingUserspaceSupportedImpl()) {
            Slog.wtf(TAG, "Userspace reboot is not supported.");
            return;
        }

        final int outcome = computeOutcome();
        final long durationMillis;
        if (outcome == USERSPACE_REBOOT_REPORTED__OUTCOME__SUCCESS) {
            durationMillis = SystemProperties.getLong(USERSPACE_REBOOT_LAST_FINISHED_PROPERTY, 0)
                    - SystemProperties.getLong(USERSPACE_REBOOT_LAST_STARTED_PROPERTY, 0);
        } else {
            durationMillis = 0;
        }
        final int encryptionState =
                userUnlocked
                    ? USERSPACE_REBOOT_REPORTED__USER_ENCRYPTION_STATE__UNLOCKED
                    : USERSPACE_REBOOT_REPORTED__USER_ENCRYPTION_STATE__LOCKED;
        executor.execute(
                () -> {
                    Slog.i(TAG, "Logging UserspaceRebootReported atom: { outcome: " + outcome
                            + " durationMillis: " + durationMillis + " encryptionState: "
                            + encryptionState + " }");
                    FrameworkStatsLog.write(FrameworkStatsLog.USERSPACE_REBOOT_REPORTED, outcome,
                            durationMillis, encryptionState);
                    SystemProperties.set(USERSPACE_REBOOT_SHOULD_LOG_PROPERTY, "");
                });
    }

    /**
     * Handles the grouping or deletion of userspace reboot logging files on a background thread,
     * in order to not block PowerManager from performing other work upon boot completion.
     *
     * <p>This call should only be made on devices supporting userspace reboot.
     */
    public static void onBootCompleted() {
        if (!PowerManager.isRebootingUserspaceSupportedImpl()) {
            Slog.wtf(TAG, "Userspace reboot is not supported.");
            return;
        }

        BackgroundThread.getExecutor().execute(() -> {
            groupOrDeleteLoggingFiles();
        });
    }


    /**
     * Dumps any information related to previous failed userspace reboot attempts.
     *
     *<p>This call should only be made on devices supporting userspace reboot.
     */
    public static void dump(Context context, PrintWriter pw) {
        if (!PowerManager.isRebootingUserspaceSupportedImpl()) {
            Slog.wtf(TAG, "Userspace reboot is not supported.");
            return;
        }
        IndentingPrintWriter ipw = new IndentingPrintWriter(pw, "  ");
        File loggingDir = new File(LOGGING_DIRECTORY);
        int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE;
        File[] files = null;
        if (loggingDir.isDirectory()) {
            files = loggingDir.listFiles();
        }
        if (files != null && files.length > 0) {
            ipw.println("Failed Userspace Reboot Events:");
            ipw.increaseIndent();
            for (File file: files) {
                if (file.isDirectory()) {
                    ipw.println("Event ID: " + file.getName() + "  Time: "
                            + DateUtils.formatDateTime(context, file.lastModified(), flags));
                    ipw.increaseIndent();
                    for (File loggingFile: file.listFiles()) {
                        try (BufferedReader reader = new BufferedReader(
                                new FileReader(loggingFile.getAbsolutePath()))) {
                            ipw.println(loggingFile.getName() + ":");
                            ipw.increaseIndent();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                ipw.println(line);
                            }
                            ipw.decreaseIndent();
                        } catch (Exception e) {
                            Slog.e(TAG, "Error reading file: " + e.getMessage());
                        }
                    }
                    ipw.decreaseIndent();
                }
            }
            ipw.decreaseIndent();
        }
    }

    /**
     * If there are any new logging files in the logging directory, group them together in a
     * new directory with a newly-allocated event ID. Remove any directories which refer to events
     * which occurred outside of the retention window.
     */
    private static void groupOrDeleteLoggingFiles() {
        File loggingDir = new File(LOGGING_DIRECTORY);
        long deletionThreshold = System.currentTimeMillis() - RETENTION_THRESHOLD;
        boolean newFilesExist = false;
        File[] files = null;
        if (loggingDir.isDirectory()) {
            files = loggingDir.listFiles();
        }
        if (files != null) {
            File tempDir = new File(LOGGING_DIRECTORY, "placeholder");
            tempDir.mkdir();
            for (File file : files) {
                if (file.isDirectory()) {
                    if (file.lastModified() < deletionThreshold) {
                        FileUtils.deleteContentsAndDir(file);
                    }
                } else {
                    file.renameTo(new File(tempDir.getAbsolutePath(), file.getName()));
                    newFilesExist = true;
                }
            }
            if (newFilesExist) {
                boolean uuidAllocated = false;
                do {
                    String eventUuid = UUID.randomUUID().toString();
                    if (!new File(LOGGING_DIRECTORY, eventUuid).exists()) {
                        tempDir.renameTo(new File(LOGGING_DIRECTORY, eventUuid));
                        uuidAllocated = true;
                    }
                } while (!uuidAllocated);
            } else {
                FileUtils.deleteContentsAndDir(tempDir);
            }
        }
    }

    private static int computeOutcome() {
        if (SystemProperties.getLong(USERSPACE_REBOOT_LAST_STARTED_PROPERTY, -1) != -1) {
            return USERSPACE_REBOOT_REPORTED__OUTCOME__SUCCESS;
        }
        String reason = TextUtils.emptyIfNull(SystemProperties.get(LAST_BOOT_REASON_PROPERTY, ""));
        if (reason.startsWith("reboot,")) {
            reason = reason.substring("reboot".length());
        }
        if (reason.startsWith("userspace_failed,watchdog_fork")) {
            return USERSPACE_REBOOT_REPORTED__OUTCOME__FAILED_SHUTDOWN_SEQUENCE_ABORTED;
        }
        if (reason.startsWith("userspace_failed,shutdown_aborted")) {
            return USERSPACE_REBOOT_REPORTED__OUTCOME__FAILED_SHUTDOWN_SEQUENCE_ABORTED;
        }
        if (reason.startsWith("mount_userdata_failed")) {
            return USERSPACE_REBOOT_REPORTED__OUTCOME__FAILED_USERDATA_REMOUNT;
        }
        if (reason.startsWith("userspace_failed,init_user0")) {
            return USERSPACE_REBOOT_REPORTED__OUTCOME__FAILED_USERDATA_REMOUNT;
        }
        if (reason.startsWith("userspace_failed,enablefilecrypto")) {
            return USERSPACE_REBOOT_REPORTED__OUTCOME__FAILED_USERDATA_REMOUNT;
        }
        if (reason.startsWith("userspace_failed,watchdog_triggered")) {
            return USERSPACE_REBOOT_REPORTED__OUTCOME__FAILED_USERSPACE_REBOOT_WATCHDOG_TRIGGERED;
        }
        return USERSPACE_REBOOT_REPORTED__OUTCOME__OUTCOME_UNKNOWN;
    }
}
