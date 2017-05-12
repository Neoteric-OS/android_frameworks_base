/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.net.util;

import android.util.LocalLog;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.StringJoiner;


/**
 * Class to centralize logging functionality for tethering.
 *
 * All access to class methods other than dump() must be on the same thread.
 *
 * @hide
 */
public class SharedLog {
    private final static int DEFAULT_MAX_RECORDS = 500;
    private final static String NO_SUBSYSTEM = null;
    private final static String NO_CATEGORY = null;
    private final static String ERROR = "ERROR";
    private final static String EVENT = "EVENT";
    private final static String MARK = "MARK";

    private final LocalLog mLocalLog;
    private final String mTag;
    private final String mSubSystem;

    public SharedLog(String tag) {
        this(DEFAULT_MAX_RECORDS, tag);
    }

    public SharedLog(int maxRecords, String tag) {
        this(new LocalLog(maxRecords), tag, NO_SUBSYSTEM);
    }

    private SharedLog(LocalLog localLog, String tag, String subsystem) {
        mLocalLog = localLog;
        mTag = tag;
        mSubSystem = (subsystem != null) ? ("[" + subsystem + "]") : null;
    }

    public SharedLog forSubSystem(String subsystem) {
        return new SharedLog(mLocalLog, mTag, subsystem);
    }

    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        mLocalLog.readOnlyLocalLog().dump(fd, writer, args);
    }

    public void error(Exception e) {
        recordAndEmit(ERROR, e.toString());
    }

    public void error(String msg) {
        recordAndEmit(ERROR, msg);
    }

    public void event(String msg) {
        record(EVENT, msg);
    }

    public void log(String msg) {
        record(NO_CATEGORY, msg);
    }

    public void logAndEmit(String msg) {
        recordAndEmit(NO_CATEGORY, msg);
    }

    public void mark(String msg) {
        record(MARK, msg);
    }

    private void record(String category, String msg) {
        mLocalLog.log(logLine(category, msg));
    }

    private void recordAndEmit(String category, String msg) {
        final String entry = logLine(category, msg);
        mLocalLog.log(entry);

        if (ERROR.equals(category)) {
            Log.e(mTag, entry);
        } else {
            Log.d(mTag, entry);
        }
    }

    private String logLine(String category, String msg) {
        final StringJoiner sj = new StringJoiner(" ");
        if (mSubSystem != null) sj.add(mSubSystem);
        if (category != null) sj.add(category);
        return sj.add(msg).toString();
    }
}
