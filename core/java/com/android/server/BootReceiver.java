/*
 * Copyright (C) 2009 The Android Open Source Project
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

import static android.system.OsConstants.O_RDONLY;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageManager;
import android.os.Build;
import android.os.DropBoxManager;
import android.os.Environment;
import android.os.FileUtils;
import android.os.RecoverySystem;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.storage.StorageManager;
import android.provider.Downloads;
import android.system.Os;
import android.text.TextUtils;
import android.util.AtomicFile;
import android.util.EventLog;
import android.util.Slog;
import android.util.Xml;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.FrameworkStatsLog;
import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Performs a number of miscellaneous, non-system-critical actions
 * after the system has finished booting.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    private static final String TAG_TRUNCATED = "[[TRUNCATED]]\n";

    // Maximum size of a logged event (files get truncated if they're longer).
    // Give userdebug builds a larger max to capture extra debug, esp. for last_kmsg.
    private static final int LOG_SIZE =
        SystemProperties.getInt("ro.debuggable", 0) == 1 ? 98304 : 65536;
    private static final int LASTK_LOG_SIZE =
        SystemProperties.getInt("ro.debuggable", 0) == 1 ? 196608 : 65536;
    private static final int GMSCORE_LASTK_LOG_SIZE = 196608;

    private static final String TAG_TOMBSTONE = "SYSTEM_TOMBSTONE";

    // The pre-froyo package and class of the system updater, which
    // ran in the system process.  We need to remove its packages here
    // in order to clean up after a pre-froyo-to-froyo update.
    private static final String OLD_UPDATER_PACKAGE =
        "com.google.android.systemupdater";
    private static final String OLD_UPDATER_CLASS =
        "com.google.android.systemupdater.SystemUpdateReceiver";

    private static final String LOG_FILES_FILE = "log-files.xml";
    private static final AtomicFile sFile = new AtomicFile(new File(
            Environment.getDataSystemDirectory(), LOG_FILES_FILE), "log-files");
    private static final String LAST_HEADER_FILE = "last-header.txt";
    private static final File lastHeaderFile = new File(
            Environment.getDataSystemDirectory(), LAST_HEADER_FILE);

    // example: fs_stat,/dev/block/platform/soc/by-name/userdata,0x5
    private static final String FS_STAT_PATTERN = "fs_stat,[^,]*/([^/,]+),(0x[0-9a-fA-F]+)";
    private static final int FS_STAT_FS_FIXED = 0x400; // should match with fs_mgr.cpp:FsStatFlags
    private static final String FSCK_PASS_PATTERN = "Pass ([1-9]E?):";
    private static final String FSCK_TREE_OPTIMIZATION_PATTERN =
            "Inode [0-9]+ extent tree.*could be shorter";
    private static final String FSCK_FS_MODIFIED = "FILE SYSTEM WAS MODIFIED";
    // ro.boottime.init.mount_all. + postfix for mount_all duration
    private static final String[] MOUNT_DURATION_PROPS_POSTFIX =
            new String[] { "early", "default", "late" };
    // for reboot, fs shutdown time is recorded in last_kmsg.
    private static final String[] LAST_KMSG_FILES =
            new String[] { "/sys/fs/pstore/console-ramoops", "/proc/last_kmsg" };
    // first: fs shutdown time in ms, second: umount status defined in init/reboot.h
    private static final String LAST_SHUTDOWN_TIME_PATTERN =
            "powerctl_shutdown_time_ms:([0-9]+):([0-9]+)";
    private static final int UMOUNT_STATUS_NOT_AVAILABLE = 4; // should match with init/reboot.h

    // Location of file with metrics recorded during shutdown
    private static final String SHUTDOWN_METRICS_FILE = "/data/system/shutdown-metrics.txt";

    private static final String SHUTDOWN_TRON_METRICS_PREFIX = "shutdown_";
    private static final String METRIC_SYSTEM_SERVER = "shutdown_system_server";
    private static final String METRIC_SHUTDOWN_TIME_START = "begin_shutdown";

    // Location of ftrace pipe for notifications from kernel memory tools like KFENCE and KASAN.
    private static final String ERROR_REPORT_TRACE_PIPE =
            "/sys/kernel/tracing/instances/bootreceiver/trace_pipe";

    @Override
    public void onReceive(final Context context, Intent intent) {
        // Log boot events in the background to avoid blocking the main thread with I/O
        new Thread() {
            @Override
            public void run() {
                try {
                    logBootEvents(context);
                } catch (Exception e) {
                    Slog.e(TAG, "Can't log boot events", e);
                }
                try {
                    boolean onlyCore = false;
                    try {
                        onlyCore = IPackageManager.Stub.asInterface(ServiceManager.getService(
                                "package")).isOnlyCoreApps();
                    } catch (RemoteException e) {
                    }
                    if (!onlyCore) {
                        removeOldUpdatePackages(context);
                    }
                } catch (Exception e) {
                    Slog.e(TAG, "Can't remove old update packages", e);
                }

            }
        }.start();

        /*
         * Polling thread to watch for memory tool error reports.
         * We read from /sys/kernel/tracing/instances/bootreceiver/trace_pipe (set up by the
         * system), which will print an ftrace event when a memory corruption is detected in the
         * kernel.
         * When an error is detected, run the dmesg shell command and process its output.
         */
        new Thread() {
            @Override
            public void run() {
                Slog.i(TAG, "Started a thread to watch " + ERROR_REPORT_TRACE_PIPE);
                File reportsFile = new File(ERROR_REPORT_TRACE_PIPE);
                if (!reportsFile.exists()) {
                    Slog.e(TAG, ERROR_REPORT_TRACE_PIPE + " does not exist.");
                    return;
                }
                try {
                    final FileDescriptor fd = Os.open(ERROR_REPORT_TRACE_PIPE, O_RDONLY, 0600);
                    final int bufferSize = 1024;
                    byte[] traceBuffer = new byte[bufferSize];

                    while (true) {
                        /*
                         * Read from the tracing pipe set up to monitor the error_report_end events.
                         * The read blocks until there is data in the pipe. When a tracing event
                         * occurs, the kernel writes a short (~100 bytes) line to the pipe, e.g.:
                         *      [004] d..1   285.322307: error_report_end: [kfence] ffffff8938a05000
                         * The buffer size we use for reading should be enough to read the whole
                         * line, but to be on the safe side we read until the buffer contains a '\n'
                         * character. In the unlikely case of a very buggy kernel the buffer may
                         * contain multiple tracing events that cannot be attributed to particular
                         * error reports. In that case the latest error report residing in dmesg is
                         * picked.
                         */
                        int nbytes = Os.read(fd, traceBuffer, 0, bufferSize);
                        if (nbytes > 0) {
                            boolean newline = false;
                            for (int i = 0; i < nbytes; i++) {
                                if (traceBuffer[i] == '\n') {
                                    newline = true;
                                }
                            }
                            if (!newline) continue;
                            List<String> dmesgLines = new ArrayList<String>();
                            Process p = Runtime.getRuntime().exec("dmesg");
                            BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(p.getInputStream()));
                            String line = null;
                            while ((line = reader.readLine()) != null) {
                                dmesgLines.add(line);
                            }
                            processDmesg(context, dmesgLines);
                        }
                    }
                } catch (Exception e) {
                    Slog.e(TAG, "Error polling for reports", e);
                    return;
                }
            }
        }.start();
    }

    /*
     * Search dmesg output for the last error report from KFENCE or KASAN and copy it to Dropbox.
     *
     * Example report printed by the kernel (redacted to fit into 100 column limit):
     *   [   69.236673] [ T6006]c7   6006  =========================================================
     *   [   69.245688] [ T6006]c7   6006  BUG: KFENCE: out-of-bounds in kfence_handle_page_fault
     *   [   69.245688] [ T6006]c7   6006
     *   [   69.257816] [ T6006]c7   6006  Out-of-bounds access at 0xffffffca75c45000 (...)
     *   [   69.267102] [ T6006]c7   6006   kfence_handle_page_fault+0x1bc/0x208
     *   [   69.273536] [ T6006]c7   6006   __do_kernel_fault+0xa8/0x11c
     *   ...
     *   [   69.355427] [ T6006]c7   6006  kfence-#2 [0xffffffca75c46f30-0xffffffca75c46fff, ...
     *   [   69.366938] [ T6006]c7   6006   __d_alloc+0x3c/0x1b4
     *   [   69.371946] [ T6006]c7   6006   d_alloc_parallel+0x48/0x538
     *   [   69.377578] [ T6006]c7   6006   __lookup_slow+0x60/0x15c
     *   ...
     *   [   69.547684] [ T6006]c7   6006  CPU: 7 PID: 6006 Comm: sh Tainted: G S       C O      ...
     *   [   69.558923] [ T6006]c7   6006  Hardware name: <REDACTED>
     *   [   69.567059] [ T6006]c7   6006  =========================================================
     *
     *   We rely on the kernel printing task/CPU ID for every log line (CONFIG_PRINTK_CALLER=y).
     *   E.g. for the above report the task ID is T6006. Report lines may interleave with lines
     *   printed by other kernel tasks, which will have different task IDs, so in order to collect
     *   the report we:
     *    - find that last occurrence of the "BUG: " line in the kernel log, parse it to obtain the
     *      task ID and the tool name;
     *    - scan the rest of dmesg output and pick every line that has the same task ID, until we
     *      encounter a horizontal ruler, i.e.:
     *      [   69.567059] [ T6006]c7   6006  ======================================================
     *    - add that line to the error report, unless it contains "Comm: " or "Hardware name: "
     *      (these denote that the line may contain sensitive information).
     */
    private void processDmesg(Context ctx, List<String> lines) throws IOException {
        ArrayList<String> report = new ArrayList<String>();
        /*
         * Only SYSTEM_KASAN_ERROR_REPORT and SYSTEM_KFENCE_ERROR_REPORT are supported at the
         * moment.
         */
        final String[] bugTypes = new String[] { "KASAN", "KFENCE" };
        final String tsRegex = "^\\[[^]]+\\] ";
        String bugRegex = tsRegex + "\\[([^]]+)\\].*BUG: (" + String.join("|", bugTypes) + "):";
        Pattern bugPattern = Pattern.compile(bugRegex);
        int start = -1;
        String task = "";
        String tool = "";
        for (int i = lines.size() - 1; i > 0; i--) {
            String line = lines.get(i);
            Matcher m = bugPattern.matcher(line);
            if (m.find()) {
                task = m.group(1);
                tool = m.group(2);
                start = i;
                break;
            }
        }
        if (start == -1) {
            Slog.w(TAG, "Could not find report in dmesg.");
            return;
        }
        String reportRegex = tsRegex + "\\[" + task + "\\].*";
        Pattern reportPattern = Pattern.compile(reportRegex);
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = reportPattern.matcher(line);
            if (m.matches()) {
                if (line.contains(" Comm: ") || line.contains("Hardware name: ")) continue;
                if (line.contains("================================")) break;
                report.add(line);
            }
        }

        final String reportTag = "SYSTEM_" + tool + "_ERROR_REPORT";
        final DropBoxManager db = ctx.getSystemService(DropBoxManager.class);
        final String headers = getCurrentBootHeaders();
        final String reportText = headers + String.join("\n", report);
        addTextToDropBox(db, reportTag, reportText, "/dev/kmsg", LOG_SIZE);
    }

    private void removeOldUpdatePackages(Context context) {
        Downloads.removeAllDownloadsByPackage(context, OLD_UPDATER_PACKAGE, OLD_UPDATER_CLASS);
    }

    private static String getPreviousBootHeaders() {
        try {
            return FileUtils.readTextFile(lastHeaderFile, 0, null);
        } catch (IOException e) {
            return null;
        }
    }

    private static String getCurrentBootHeaders() throws IOException {
        return new StringBuilder(512)
            .append("Build: ").append(Build.FINGERPRINT).append("\n")
            .append("Hardware: ").append(Build.BOARD).append("\n")
            .append("Revision: ")
            .append(SystemProperties.get("ro.revision", "")).append("\n")
            .append("Bootloader: ").append(Build.BOOTLOADER).append("\n")
            .append("Radio: ").append(Build.getRadioVersion()).append("\n")
            .append("Kernel: ")
            .append(FileUtils.readTextFile(new File("/proc/version"), 1024, "...\n"))
            .append("\n").toString();
    }


    private static String getBootHeadersToLogAndUpdate() throws IOException {
        final String oldHeaders = getPreviousBootHeaders();
        final String newHeaders = getCurrentBootHeaders();

        try {
            FileUtils.stringToFile(lastHeaderFile, newHeaders);
        } catch (IOException e) {
            Slog.e(TAG, "Error writing " + lastHeaderFile, e);
        }

        if (oldHeaders == null) {
            // If we failed to read the old headers, use the current headers
            // but note this in the headers so we know
            return "isPrevious: false\n" + newHeaders;
        }

        return "isPrevious: true\n" + oldHeaders;
    }

    private void logBootEvents(Context ctx) throws IOException {
        final DropBoxManager db = (DropBoxManager) ctx.getSystemService(Context.DROPBOX_SERVICE);
        final String headers = getBootHeadersToLogAndUpdate();
        final String bootReason = SystemProperties.get("ro.boot.bootreason", null);

        String recovery = RecoverySystem.handleAftermath(ctx);
        if (recovery != null && db != null) {
            db.addText("SYSTEM_RECOVERY_LOG", headers + recovery);
        }

        String lastKmsgFooter = "";
        if (bootReason != null) {
            lastKmsgFooter = new StringBuilder(512)
                .append("\n")
                .append("Boot info:\n")
                .append("Last boot reason: ").append(bootReason).append("\n")
                .toString();
        }

        HashMap<String, Long> timestamps = readTimestamps();

        if (SystemProperties.getLong("ro.runtime.firstboot", 0) == 0) {
            if (StorageManager.inCryptKeeperBounce()) {
                // Encrypted, first boot to get PIN/pattern/password so data is tmpfs
                // Don't set ro.runtime.firstboot so that we will do this again
                // when data is properly mounted
            } else {
                String now = Long.toString(System.currentTimeMillis());
                SystemProperties.set("ro.runtime.firstboot", now);
            }
            if (db != null) db.addText("SYSTEM_BOOT", headers);

            // Negative sizes mean to take the *tail* of the file (see FileUtils.readTextFile())
            addLastkToDropBox(db, timestamps, headers, lastKmsgFooter,
                    "/proc/last_kmsg", -LASTK_LOG_SIZE, "SYSTEM_LAST_KMSG");
            addLastkToDropBox(db, timestamps, headers, lastKmsgFooter,
                    "/sys/fs/pstore/console-ramoops", -LASTK_LOG_SIZE, "SYSTEM_LAST_KMSG");
            addLastkToDropBox(db, timestamps, headers, lastKmsgFooter,
                    "/sys/fs/pstore/console-ramoops-0", -LASTK_LOG_SIZE, "SYSTEM_LAST_KMSG");
            addFileToDropBox(db, timestamps, headers, "/cache/recovery/log", -LOG_SIZE,
                    "SYSTEM_RECOVERY_LOG");
            addFileToDropBox(db, timestamps, headers, "/cache/recovery/last_kmsg",
                    -LOG_SIZE, "SYSTEM_RECOVERY_KMSG");
            addAuditErrorsToDropBox(db, timestamps, headers, -LOG_SIZE, "SYSTEM_AUDIT");
        } else {
            if (db != null) db.addText("SYSTEM_RESTART", headers);
        }
        // log always available fs_stat last so that logcat collecting tools can wait until
        // fs_stat to get all file system metrics.
        logFsShutdownTime();
        logFsMountTime();
        addFsckErrorsToDropBoxAndLogFsStat(db, timestamps, headers, -LOG_SIZE, "SYSTEM_FSCK");
        logSystemServerShutdownTimeMetrics();
        writeTimestamps(timestamps);
    }

    /**
     * Add a tombstone to the DropBox.
     *
     * @param ctx Context
     * @param tombstone path to the tombstone
     */
    public static void addTombstoneToDropBox(Context ctx, File tombstone) {
        final DropBoxManager db = ctx.getSystemService(DropBoxManager.class);
        final String bootReason = SystemProperties.get("ro.boot.bootreason", null);
        HashMap<String, Long> timestamps = readTimestamps();
        try {
            final String headers = getBootHeadersToLogAndUpdate();
            addFileToDropBox(db, timestamps, headers, tombstone.getPath(), LOG_SIZE,
                    TAG_TOMBSTONE);
        } catch (IOException e) {
            Slog.e(TAG, "Can't log tombstone", e);
        }
        writeTimestamps(timestamps);
    }

    private static void addLastkToDropBox(
            DropBoxManager db, HashMap<String, Long> timestamps,
            String headers, String footers, String filename, int maxSize,
            String tag) throws IOException {
        int extraSize = headers.length() + TAG_TRUNCATED.length() + footers.length();
        // GMSCore will do 2nd truncation to be 192KiB
        // LASTK_LOG_SIZE + extraSize must be less than GMSCORE_LASTK_LOG_SIZE
        if (LASTK_LOG_SIZE + extraSize > GMSCORE_LASTK_LOG_SIZE) {
          if (GMSCORE_LASTK_LOG_SIZE > extraSize) {
            maxSize = -(GMSCORE_LASTK_LOG_SIZE - extraSize);
          } else {
            maxSize = 0;
          }
        }
        addFileWithFootersToDropBox(db, timestamps, headers, footers, filename, maxSize, tag);
    }

    private static void addFileToDropBox(
            DropBoxManager db, HashMap<String, Long> timestamps,
            String headers, String filename, int maxSize, String tag) throws IOException {
        addFileWithFootersToDropBox(db, timestamps, headers, "", filename, maxSize, tag);
    }

    private static void addFileWithFootersToDropBox(
            DropBoxManager db, HashMap<String, Long> timestamps,
            String headers, String footers, String filename, int maxSize,
            String tag) throws IOException {
        if (db == null || !db.isTagEnabled(tag)) return;  // Logging disabled

        File file = new File(filename);
        long fileTime = file.lastModified();
        if (fileTime <= 0) return;  // File does not exist

        if (timestamps.containsKey(filename) && timestamps.get(filename) == fileTime) {
            return;  // Already logged this particular file
        }

        timestamps.put(filename, fileTime);


        String fileContents = FileUtils.readTextFile(file, maxSize, TAG_TRUNCATED);
        String text = headers + fileContents + footers;
        // Create an additional report for system server native crashes, with a special tag.
        if (tag.equals(TAG_TOMBSTONE) && fileContents.contains(">>> system_server <<<")) {
            addTextToDropBox(db, "system_server_native_crash", text, filename, maxSize);
        }
        if (tag.equals(TAG_TOMBSTONE)) {
            FrameworkStatsLog.write(FrameworkStatsLog.TOMB_STONE_OCCURRED);
        }
        addTextToDropBox(db, tag, text, filename, maxSize);
    }

    private static void addTextToDropBox(DropBoxManager db, String tag, String text,
            String filename, int maxSize) {
        Slog.i(TAG, "Copying " + filename + " to DropBox (" + tag + ")");
        db.addText(tag, text);
        EventLog.writeEvent(DropboxLogTags.DROPBOX_FILE_COPY, filename, maxSize, tag);
    }

    private static void addAuditErrorsToDropBox(DropBoxManager db,
            HashMap<String, Long> timestamps, String headers, int maxSize, String tag)
            throws IOException {
        if (db == null || !db.isTagEnabled(tag)) return;  // Logging disabled
        Slog.i(TAG, "Copying audit failures to DropBox");

        File file = new File("/proc/last_kmsg");
        long fileTime = file.lastModified();
        if (fileTime <= 0) {
            file = new File("/sys/fs/pstore/console-ramoops");
            fileTime = file.lastModified();
            if (fileTime <= 0) {
                file = new File("/sys/fs/pstore/console-ramoops-0");
                fileTime = file.lastModified();
            }
        }

        if (fileTime <= 0) return;  // File does not exist

        if (timestamps.containsKey(tag) && timestamps.get(tag) == fileTime) {
            return;  // Already logged this particular file
        }

        timestamps.put(tag, fileTime);

        String log = FileUtils.readTextFile(file, maxSize, TAG_TRUNCATED);
        StringBuilder sb = new StringBuilder();
        for (String line : log.split("\n")) {
            if (line.contains("audit")) {
                sb.append(line + "\n");
            }
        }
        Slog.i(TAG, "Copied " + sb.toString().length() + " worth of audits to DropBox");
        db.addText(tag, headers + sb.toString());
    }

    private static void addFsckErrorsToDropBoxAndLogFsStat(DropBoxManager db,
            HashMap<String, Long> timestamps, String headers, int maxSize, String tag)
            throws IOException {
        boolean uploadEnabled = true;
        if (db == null || !db.isTagEnabled(tag)) {
            uploadEnabled = false;
        }
        boolean uploadNeeded = false;
        Slog.i(TAG, "Checking for fsck errors");

        File file = new File("/dev/fscklogs/log");
        long fileTime = file.lastModified();
        if (fileTime <= 0) return;  // File does not exist

        String log = FileUtils.readTextFile(file, maxSize, TAG_TRUNCATED);
        Pattern pattern = Pattern.compile(FS_STAT_PATTERN);
        String lines[] = log.split("\n");
        int lineNumber = 0;
        int lastFsStatLineNumber = 0;
        for (String line : lines) { // should check all lines
            if (line.contains(FSCK_FS_MODIFIED)) {
                uploadNeeded = true;
            } else if (line.contains("fs_stat")){
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    handleFsckFsStat(matcher, lines, lastFsStatLineNumber, lineNumber);
                    lastFsStatLineNumber = lineNumber;
                } else {
                    Slog.w(TAG, "cannot parse fs_stat:" + line);
                }
            }
            lineNumber++;
        }

        if (uploadEnabled && uploadNeeded ) {
            addFileToDropBox(db, timestamps, headers, "/dev/fscklogs/log", maxSize, tag);
        }

        // Remove the file so we don't re-upload if the runtime restarts.
        file.delete();
    }

    private static void logFsMountTime() {
        for (String propPostfix : MOUNT_DURATION_PROPS_POSTFIX) {
            int duration = SystemProperties.getInt("ro.boottime.init.mount_all." + propPostfix, 0);
            if (duration != 0) {
                int eventType;
                switch (propPostfix) {
                    case "early":
                        eventType =
                                FrameworkStatsLog
                                        .BOOT_TIME_EVENT_DURATION__EVENT__MOUNT_EARLY_DURATION;
                        break;
                    case "default":
                        eventType =
                                FrameworkStatsLog
                                        .BOOT_TIME_EVENT_DURATION__EVENT__MOUNT_DEFAULT_DURATION;
                        break;
                    case "late":
                        eventType =
                                FrameworkStatsLog
                                        .BOOT_TIME_EVENT_DURATION__EVENT__MOUNT_LATE_DURATION;
                        break;
                    default:
                        continue;
                }
                FrameworkStatsLog.write(FrameworkStatsLog.BOOT_TIME_EVENT_DURATION_REPORTED,
                        eventType, duration);
            }
        }
    }

    // TODO b/64815357 Move to bootstat.cpp and log AbsoluteRebootTime
    private static void logSystemServerShutdownTimeMetrics() {
        File metricsFile = new File(SHUTDOWN_METRICS_FILE);
        String metricsStr = null;
        if (metricsFile.exists()) {
            try {
                metricsStr = FileUtils.readTextFile(metricsFile, 0, null);
            } catch (IOException e) {
                Slog.e(TAG, "Problem reading " + metricsFile, e);
            }
        }
        if (!TextUtils.isEmpty(metricsStr)) {
            String reboot = null;
            String reason = null;
            String start_time = null;
            String duration = null;
            String[] array = metricsStr.split(",");
            for (String keyValueStr : array) {
                String[] keyValue = keyValueStr.split(":");
                if (keyValue.length != 2) {
                    Slog.e(TAG, "Wrong format of shutdown metrics - " + metricsStr);
                    continue;
                }
                // Ignore keys that are not indended for tron
                if (keyValue[0].startsWith(SHUTDOWN_TRON_METRICS_PREFIX)) {
                    logTronShutdownMetric(keyValue[0], keyValue[1]);
                    if (keyValue[0].equals(METRIC_SYSTEM_SERVER)) {
                        duration = keyValue[1];
                    }
                }
                if (keyValue[0].equals("reboot")) {
                    reboot = keyValue[1];
                } else if (keyValue[0].equals("reason")) {
                    reason = keyValue[1];
                } else if (keyValue[0].equals(METRIC_SHUTDOWN_TIME_START)) {
                    start_time = keyValue[1];
                }
            }
            logStatsdShutdownAtom(reboot, reason, start_time, duration);
        }
        metricsFile.delete();
    }

    private static void logTronShutdownMetric(String metricName, String valueStr) {
        int value;
        try {
            value = Integer.parseInt(valueStr);
        } catch (NumberFormatException e) {
            Slog.e(TAG, "Cannot parse metric " + metricName + " int value - " + valueStr);
            return;
        }
        if (value >= 0) {
            MetricsLogger.histogram(null, metricName, value);
        }
    }

    private static void logStatsdShutdownAtom(
            String rebootStr, String reasonStr, String startStr, String durationStr) {
        boolean reboot = false;
        String reason = "<EMPTY>";
        long start = 0;
        long duration = 0;

        if (rebootStr != null) {
            if (rebootStr.equals("y")) {
                reboot = true;
            } else if (!rebootStr.equals("n")) {
                Slog.e(TAG, "Unexpected value for reboot : " + rebootStr);
            }
        } else {
            Slog.e(TAG, "No value received for reboot");
        }

        if (reasonStr != null) {
            reason = reasonStr;
        } else {
            Slog.e(TAG, "No value received for shutdown reason");
        }

        if (startStr != null) {
            try {
                start = Long.parseLong(startStr);
            } catch (NumberFormatException e) {
                Slog.e(TAG, "Cannot parse shutdown start time: " + startStr);
            }
        } else {
            Slog.e(TAG, "No value received for shutdown start time");
        }

        if (durationStr != null) {
            try {
                duration = Long.parseLong(durationStr);
            } catch (NumberFormatException e) {
                Slog.e(TAG, "Cannot parse shutdown duration: " + startStr);
            }
        } else {
            Slog.e(TAG, "No value received for shutdown duration");
        }

        FrameworkStatsLog.write(FrameworkStatsLog.SHUTDOWN_SEQUENCE_REPORTED, reboot, reason, start,
                duration);
    }

    private static void logFsShutdownTime() {
        File f = null;
        for (String fileName : LAST_KMSG_FILES) {
            File file = new File(fileName);
            if (!file.exists()) continue;
            f = file;
            break;
        }
        if (f == null) { // no last_kmsg
            return;
        }

        final int maxReadSize = 16*1024;
        // last_kmsg can be very big, so only parse the last part
        String lines;
        try {
            lines = FileUtils.readTextFile(f, -maxReadSize, null);
        } catch (IOException e) {
            Slog.w(TAG, "cannot read last msg", e);
            return;
        }
        Pattern pattern = Pattern.compile(LAST_SHUTDOWN_TIME_PATTERN, Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(lines);
        if (matcher.find()) {
            FrameworkStatsLog.write(FrameworkStatsLog.BOOT_TIME_EVENT_DURATION_REPORTED,
                    FrameworkStatsLog.BOOT_TIME_EVENT_DURATION__EVENT__SHUTDOWN_DURATION,
                    Integer.parseInt(matcher.group(1)));
            FrameworkStatsLog.write(FrameworkStatsLog.BOOT_TIME_EVENT_ERROR_CODE_REPORTED,
                    FrameworkStatsLog.BOOT_TIME_EVENT_ERROR_CODE__EVENT__SHUTDOWN_UMOUNT_STAT,
                    Integer.parseInt(matcher.group(2)));
            Slog.i(TAG, "boot_fs_shutdown," + matcher.group(1) + "," + matcher.group(2));
        } else { // not found
            // This can happen when a device has too much kernel log after file system unmount
            // ,exceeding maxReadSize. And having that much kernel logging can affect overall
            // performance as well. So it is better to fix the kernel to reduce the amount of log.
            FrameworkStatsLog.write(FrameworkStatsLog.BOOT_TIME_EVENT_ERROR_CODE_REPORTED,
                    FrameworkStatsLog.BOOT_TIME_EVENT_ERROR_CODE__EVENT__SHUTDOWN_UMOUNT_STAT,
                    UMOUNT_STATUS_NOT_AVAILABLE);
            Slog.w(TAG, "boot_fs_shutdown, string not found");
        }
    }

    /**
     * Fix fs_stat from e2fsck.
     * For now, only handle the case of quota warning caused by tree optimization. Clear fs fix
     * flag (=0x400) caused by that.
     *
     * @param partition partition name
     * @param statOrg original stat reported from e2fsck log
     * @param lines e2fsck logs broken down into lines
     * @param startLineNumber start line to parse
     * @param endLineNumber end line. exclusive.
     * @return updated fs_stat. For tree optimization, will clear bit 0x400.
     */
    @VisibleForTesting
    public static int fixFsckFsStat(String partition, int statOrg, String[] lines,
            int startLineNumber, int endLineNumber) {
        int stat = statOrg;
        if ((stat & FS_STAT_FS_FIXED) != 0) {
            // fs was fixed. should check if quota warning was caused by tree optimization.
            // This is not a real fix but optimization, so should not be counted as a fs fix.
            Pattern passPattern = Pattern.compile(FSCK_PASS_PATTERN);
            Pattern treeOptPattern = Pattern.compile(FSCK_TREE_OPTIMIZATION_PATTERN);
            String currentPass = "";
            boolean foundTreeOptimization = false;
            boolean foundQuotaFix = false;
            boolean foundTimestampAdjustment = false;
            boolean foundOtherFix = false;
            String otherFixLine = null;
            for (int i = startLineNumber; i < endLineNumber; i++) {
                String line = lines[i];
                if (line.contains(FSCK_FS_MODIFIED)) { // no need to parse above this
                    break;
                } else if (line.startsWith("Pass ")) {
                    Matcher matcher = passPattern.matcher(line);
                    if (matcher.find()) {
                        currentPass = matcher.group(1);
                    }
                } else if (line.startsWith("Inode ")) {
                    Matcher matcher = treeOptPattern.matcher(line);
                    if (matcher.find() && currentPass.equals("1")) {
                        foundTreeOptimization = true;
                        Slog.i(TAG, "fs_stat, partition:" + partition + " found tree optimization:"
                                + line);
                    } else {
                        foundOtherFix = true;
                        otherFixLine = line;
                        break;
                    }
                } else if (line.startsWith("[QUOTA WARNING]") && currentPass.equals("5")) {
                    Slog.i(TAG, "fs_stat, partition:" + partition + " found quota warning:"
                            + line);
                    foundQuotaFix = true;
                    if (!foundTreeOptimization) { // only quota warning, this is real fix.
                        otherFixLine = line;
                        break;
                    }
                } else if (line.startsWith("Update quota info") && currentPass.equals("5")) {
                    // follows "[QUOTA WARNING]", ignore
                } else if (line.startsWith("Timestamp(s) on inode") &&
                        line.contains("beyond 2310-04-04 are likely pre-1970") &&
                        currentPass.equals("1")) {
                    Slog.i(TAG, "fs_stat, partition:" + partition + " found timestamp adjustment:"
                            + line);
                    // followed by next line, "Fix? yes"
                    if (lines[i + 1].contains("Fix? yes")) {
                        i++;
                    }
                    foundTimestampAdjustment = true;
                } else {
                    line = line.trim();
                    // ignore empty msg or any msg before Pass 1
                    if (!line.isEmpty() && !currentPass.isEmpty()) {
                        foundOtherFix = true;
                        otherFixLine = line;
                        break;
                    }
                }
            }
            if (foundOtherFix) {
                if (otherFixLine != null) {
                    Slog.i(TAG, "fs_stat, partition:" + partition + " fix:" + otherFixLine);
                }
            } else if (foundQuotaFix && !foundTreeOptimization) {
                Slog.i(TAG, "fs_stat, got quota fix without tree optimization, partition:" +
                        partition);
            } else if ((foundTreeOptimization && foundQuotaFix) || foundTimestampAdjustment) {
                // not a real fix, so clear it.
                Slog.i(TAG, "fs_stat, partition:" + partition + " fix ignored");
                stat &= ~FS_STAT_FS_FIXED;
            }
        }
        return stat;
    }

    private static void handleFsckFsStat(Matcher match, String[] lines, int startLineNumber,
            int endLineNumber) {
        String partition = match.group(1);
        int stat;
        try {
            stat = Integer.decode(match.group(2));
        } catch (NumberFormatException e) {
            Slog.w(TAG, "cannot parse fs_stat: partition:" + partition + " stat:" + match.group(2));
            return;
        }
        stat = fixFsckFsStat(partition, stat, lines, startLineNumber, endLineNumber);
        if ("userdata".equals(partition) || "data".equals(partition)) {
            FrameworkStatsLog.write(FrameworkStatsLog.BOOT_TIME_EVENT_ERROR_CODE_REPORTED,
                    FrameworkStatsLog
                            .BOOT_TIME_EVENT_ERROR_CODE__EVENT__FS_MGR_FS_STAT_DATA_PARTITION,
                    stat);
        }
        Slog.i(TAG, "fs_stat, partition:" + partition + " stat:0x" + Integer.toHexString(stat));
    }

    private static HashMap<String, Long> readTimestamps() {
        synchronized (sFile) {
            HashMap<String, Long> timestamps = new HashMap<String, Long>();
            boolean success = false;
            try (final FileInputStream stream = sFile.openRead()) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(stream, StandardCharsets.UTF_8.name());

                int type;
                while ((type = parser.next()) != XmlPullParser.START_TAG
                        && type != XmlPullParser.END_DOCUMENT) {
                    ;
                }

                if (type != XmlPullParser.START_TAG) {
                    throw new IllegalStateException("no start tag found");
                }

                int outerDepth = parser.getDepth();  // Skip the outer <log-files> tag.
                while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                        && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                    if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                        continue;
                    }

                    String tagName = parser.getName();
                    if (tagName.equals("log")) {
                        final String filename = parser.getAttributeValue(null, "filename");
                        final long timestamp = Long.valueOf(parser.getAttributeValue(
                                    null, "timestamp"));
                        timestamps.put(filename, timestamp);
                    } else {
                        Slog.w(TAG, "Unknown tag: " + parser.getName());
                        XmlUtils.skipCurrentTag(parser);
                    }
                }
                success = true;
            } catch (FileNotFoundException e) {
                Slog.i(TAG, "No existing last log timestamp file " + sFile.getBaseFile() +
                        "; starting empty");
            } catch (IOException e) {
                Slog.w(TAG, "Failed parsing " + e);
            } catch (IllegalStateException e) {
                Slog.w(TAG, "Failed parsing " + e);
            } catch (NullPointerException e) {
                Slog.w(TAG, "Failed parsing " + e);
            } catch (XmlPullParserException e) {
                Slog.w(TAG, "Failed parsing " + e);
            } finally {
                if (!success) {
                    timestamps.clear();
                }
            }
            return timestamps;
        }
    }

    private static void writeTimestamps(HashMap<String, Long> timestamps) {
        synchronized (sFile) {
            final FileOutputStream stream;
            try {
                stream = sFile.startWrite();
            } catch (IOException e) {
                Slog.w(TAG, "Failed to write timestamp file: " + e);
                return;
            }

            try {
                XmlSerializer out = new FastXmlSerializer();
                out.setOutput(stream, StandardCharsets.UTF_8.name());
                out.startDocument(null, true);
                out.startTag(null, "log-files");

                Iterator<String> itor = timestamps.keySet().iterator();
                while (itor.hasNext()) {
                    String filename = itor.next();
                    out.startTag(null, "log");
                    out.attribute(null, "filename", filename);
                    out.attribute(null, "timestamp", timestamps.get(filename).toString());
                    out.endTag(null, "log");
                }

                out.endTag(null, "log-files");
                out.endDocument();

                sFile.finishWrite(stream);
            } catch (IOException e) {
                Slog.w(TAG, "Failed to write timestamp file, using the backup: " + e);
                sFile.failWrite(stream);
            }
        }
    }
}
