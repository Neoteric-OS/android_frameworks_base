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

import android.annotation.NonNull;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Slog;
import android.util.StatsLog;

import java.util.concurrent.Executor;

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
    private static final String BOOT_REASON_PROPERTY = "sys.boot.reason";

    private static final int OUTCOME_UNKNOWN = 0;
    private static final int OUTCOME_SUCCESS = 1;
    private static final int OUTCOME_FAILED_SHUTDOWN_SEQUENCE_ABORTED = 2;
    private static final int OUTCOME_FAILED_USERDATA_REMOUNT = 3;
    private static final int OUTCOME_FAILED_USERSPACE_REBOOT_WATCHDOG_TRIGGERED = 4;

    private static final int ENCRYPTION_STATE_UNLOCKED = 1;
    private static final int ENCRYPTION_STATE_LOCKED = 2;

    private final boolean mShouldLog;
    private final long mUserspaceRebootLastStarted;
    private final long mUserspaceRebootLastFinished;
    private final String mBootReason;

    private UserspaceRebootLogger(boolean shouldLog, long userspaceRebootLastStarted,
            long userspaceRebootLastFinished, @NonNull String bootReason) {
        mShouldLog = shouldLog;
        mUserspaceRebootLastStarted = userspaceRebootLastStarted;
        mUserspaceRebootLastFinished = userspaceRebootLastFinished;
        mBootReason = bootReason;
    }

    /**
     * Creates a new instance of {@link UserspaceRebootLogger}.
     */
    public static UserspaceRebootLogger create() {
        return new UserspaceRebootLogger(
                SystemProperties.getBoolean(USERSPACE_REBOOT_SHOULD_LOG_PROPERTY, false),
                SystemProperties.getLong(USERSPACE_REBOOT_LAST_STARTED_PROPERTY, -1),
                SystemProperties.getLong(USERSPACE_REBOOT_LAST_FINISHED_PROPERTY, -1),
                SystemProperties.get(BOOT_REASON_PROPERTY, ""));
    }

    /**
     * Modifies internal state to note that {@code UserspaceRebootReported} atom needs to be
     * logged on the next successful boot.
     */
    public static void noteUserspaceRebootWasRequested() {
        Slog.i(TAG, "noteUserspaceRebootWasRequested");
        SystemProperties.set(USERSPACE_REBOOT_SHOULD_LOG_PROPERTY, "1");
        SystemProperties.set(USERSPACE_REBOOT_LAST_STARTED_PROPERTY,
                String.valueOf(SystemClock.elapsedRealtime()));
    }

    /**
     * Updates internal state on boot after successful userspace reboot.
     */
    public static void noteUserspaceRebootSuccess() {
        Slog.i(TAG, "noteUserspaceRebootSuccess");
        SystemProperties.set(USERSPACE_REBOOT_LAST_FINISHED_PROPERTY,
                String.valueOf(SystemClock.elapsedRealtime()));
    }

    /**
     * Returns {@code true} if {@code UserspaceRebootReported} atom should be logged.
     */
    public boolean shouldLogUserspaceRebootEvent() {
        return mShouldLog;
    }

    /**
     * Asynchronously logs {@code UserspaceRebootReported} on the given {@code executor}.
     */
    public void logEventAsync(boolean userUnlocked, Executor executor) {
        Slog.i(TAG, "logEventAsync");
        final int outcome = computeOutcome();
        final long durationMillis =
                outcome == OUTCOME_SUCCESS ? mUserspaceRebootLastFinished
                        - mUserspaceRebootLastStarted : 0;
        final int encryptionState =
                userUnlocked ? ENCRYPTION_STATE_UNLOCKED : ENCRYPTION_STATE_LOCKED;
        executor.execute(
                () -> {
                    Slog.i(TAG, "Logging UserspaceRebootReported atom, outcome: " + outcome
                            + " durationMillis " + durationMillis + " encryptionState = "
                            + encryptionState);
                    StatsLog.write(StatsLog.USERSPACE_REBOOT_REPORTED, outcome, durationMillis,
                            encryptionState);
                    SystemProperties.set(USERSPACE_REBOOT_SHOULD_LOG_PROPERTY, "");
                });
    }

    private int computeOutcome() {
        if (mUserspaceRebootLastStarted != -1) {
            return OUTCOME_SUCCESS;
        }
        final String reason = mBootReason.startsWith("reboot,") ? mBootReason.substring(
                "reboot,".length()) : mBootReason;
        switch (reason) {
            case "userspace_failed,watchdog_fork":
                // Since fork happens before shutdown sequence, attribute it to
                // OUTCOME_FAILED_SHUTDOWN_SEQUENCE_ABORTED.
            case "userspace_failed,shutdown_aborted":
                return OUTCOME_FAILED_SHUTDOWN_SEQUENCE_ABORTED;
            case "userspace_failed,init_user0_failed":
                // init_user0 will fail if userdata wasn't remounted correctly, attribute to
                // OUTCOME_FAILED_USERDATA_REMOUNT.
            case "mount_userdata_failed":
                return OUTCOME_FAILED_USERDATA_REMOUNT;
            case "userspace_failed,watchdog_triggered":
                return OUTCOME_FAILED_USERSPACE_REBOOT_WATCHDOG_TRIGGERED;
            default:
                return OUTCOME_UNKNOWN;
        }
    }
}
