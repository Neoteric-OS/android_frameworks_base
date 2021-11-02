/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.util;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.compat.annotation.UnsupportedAppUsage;
import android.os.Build;
import android.os.SystemClock;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * @hide
 */
public final class LocalLog {

    private final Deque<LogEntry> mLog;
    private final int mMaxLines;

    /**
     * {@code true} to use log timestamps expressed in local date/time, {@code false} to use log
     * timestamped expressed with the elapsed realtime clock and UTC system clock. {@code false} is
     * useful when logging behavior that modifies device time zone or system clock.
     */
    private final boolean mUseLocalTimestamps;

    @UnsupportedAppUsage
    public LocalLog(int maxLines) {
        this(maxLines, true /* useLocalTimestamps */);
    }

    public LocalLog(int maxLines, boolean useLocalTimestamps) {
        mMaxLines = Math.max(0, maxLines);
        mLog = new ArrayDeque<>(mMaxLines);
        mUseLocalTimestamps = useLocalTimestamps;
    }

    @UnsupportedAppUsage
    public void log(@NonNull String msg) {
        logObject(msg);
    }

    /**
     * Adds the entry object to the log.
     *
     * <p>To help to avoid pinning large strings in memory, with this method the conversion to a
     * {@link String} takes place at dump time, not log time, i.e. object will have
     * {@link Object#toString()} called each time that {@link #dump} or similar methods are called.
     *
     * <p>Where this is not the desired behavior, callers should use {@link #log(String)} or call
     * {@link String#valueOf(Object)} (or {@link Object#toString()} when {@code null} is impossible)
     * to capture the string value at logging time. Callers should be careful logging objects,
     * particularly instances of inner classes that may pin other objects in memory, or mutable
     * objects that could be changed after logging.
     */
    public void logObject(@NonNull Object entry) {
        if (mMaxLines <= 0) {
            return;
        }
        final String logTimestamp;
        if (mUseLocalTimestamps) {
            logTimestamp = LocalDateTime.now().toString();
        } else {
            logTimestamp = SystemClock.elapsedRealtime() + " / " + Instant.now();
        }
        append(logTimestamp, entry);
    }

    private synchronized void append(@NonNull String timestamp, @NonNull Object entry) {
        LogEntry logEntry = null;
        while (mLog.size() >= mMaxLines) {
            logEntry = mLog.remove();
        }
        if (logEntry == null) {
            logEntry = new LogEntry();
        }

        // (Re)populate the LogEntry object.
        logEntry.mTimestamp = timestamp;
        logEntry.mEntry = entry;

        mLog.add(logEntry);
    }

    @UnsupportedAppUsage
    public synchronized void dump(@Nullable FileDescriptor fd, @NonNull PrintWriter pw,
            @Nullable String[] args) {
        dump(pw);
    }

    /**
     * Dumps the content of the log to the supplied {@link PrintWriter} with the oldest entry first.
     */
    public synchronized void dump(@NonNull PrintWriter pw) {
        dump("", pw);
    }

    /**
     * Dumps the content of the log to the supplied {@link PrintWriter} with the oldest entry first.
     *
     * @param indent indent that precedes each log entry
     * @param pw printer writer to write into
     */
    public synchronized void dump(@NonNull String indent, @NonNull PrintWriter pw) {
        Iterator<LogEntry> itr = mLog.iterator();
        while (itr.hasNext()) {
            itr.next().println(pw, indent);
        }
    }

    /**
     * Dumps the content of the log to the supplied {@link PrintWriter} with the newest entry first.
     */
    public synchronized void reverseDump(@Nullable FileDescriptor fd, @NonNull PrintWriter pw,
            @Nullable String[] args) {
        reverseDump(pw);
    }

    /**
     * Dumps the content of the log to the supplied {@link PrintWriter} with the newest entry first.
     */
    public synchronized void reverseDump(@NonNull PrintWriter pw) {
        Iterator<LogEntry> itr = mLog.descendingIterator();
        while (itr.hasNext()) {
            itr.next().println(pw, "");
        }
    }

    public static class ReadOnlyLocalLog {
        private final LocalLog mLog;
        ReadOnlyLocalLog(LocalLog log) {
            mLog = log;
        }

        /**
         * Dumps the content of the log to the supplied {@link PrintWriter} with the oldest entry
         * first.
         */
        @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
        public void dump(@Nullable FileDescriptor fd, @NonNull PrintWriter pw,
                @Nullable String[] args) {
            mLog.dump(pw);
        }

        /**
         * Dumps the content of the log to the supplied {@link PrintWriter} with the oldest entry
         * first.
         */
        public void dump(@NonNull PrintWriter pw) {
            mLog.dump(pw);
        }

        /**
         * Dumps the content of the log to the supplied {@link PrintWriter} with the newest entry
         * first.
         */
        public void reverseDump(@Nullable FileDescriptor fd, @NonNull PrintWriter pw,
                @Nullable String[] args) {
            mLog.reverseDump(pw);
        }

        /**
         * Dumps the content of the log to the supplied {@link PrintWriter} with the newest entry
         * first.
         */
        public void reverseDump(@NonNull PrintWriter pw) {
            mLog.reverseDump(pw);
        }
    }

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    @NonNull
    public ReadOnlyLocalLog readOnlyLocalLog() {
        return new ReadOnlyLocalLog(this);
    }

    /**
     * A mutable (recyclable) entry in the log. The entry is an object which will have {@link
     * Object#toString()} called on it.
     *
     * <p>{@link Object} is supported because often objects are more compact than the strings that
     * they generate, and {@link LocalLog#dump} is a comparatively rare, not performance critical,
     * operation. The {@link Object} can, of course, be a {@link String}, leaving the caller to
     * choose between {@link LocalLog#log(String)} and {@link LocalLog#logObject(Object)} depending
     * on their needs.
     */
    private static class LogEntry {
        @NonNull String mTimestamp;
        @Nullable Object mEntry;

        LogEntry() {
        }

        void println(@NonNull PrintWriter pw, @NonNull String indent) {
            pw.print(indent);
            pw.print(mTimestamp);
            pw.print(" - ");
            pw.println(mEntry);
        }
    }
}
