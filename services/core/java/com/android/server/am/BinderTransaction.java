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

package com.android.server.am;

import android.os.Process;
import android.util.Slog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Binder transaction class.
 * Used to collect the binder transaction information for dropbox log.
 */
public class BinderTransaction {
    static final String TAG = "BinderTransaction";

    static final long BINDER_PROCS_COLLECTOR_TIMEOUT = 30000; // 30 sec.

    // binder transaction info lines are generated in the binder driver.
    // see: /kernel/drivers/staging/android/binder.c#print_binder_transaction
    private static final String REGEX_BINDER_TRANSACTION =
            "\\s*(outgoing|incoming|pending)" + //group1: direction.
            "\\s+transaction\\s+(-?\\d+):" +
            "\\s+(-?\\w+)" +
            "\\s+from\\s+(-?\\d+):(-?\\d+)" +
            "\\s+to\\s+(-?\\d+):(-?\\d+)";      //group6: to pid.
    private static final int BINDER_TRANSACTION_GROUP_DIRECTION = 1;
    private static final int BINDER_TRANSACTION_GROUP_TO_PID = 6;

    static Pattern sBinderPattern = Pattern.compile(REGEX_BINDER_TRANSACTION);

    private static final int PID_PROC_UNKNOWN = -1;
    private static final int PID_PROC_VM = 0;
    private static final int PID_PROC_NATIVE = 1;

    private static String LOG_ERROR_HEADER = "ERROR: ";
    private static String LOG_ERROR_LINE_FAILED_TO_GET_THE_BINDER_TRANSACTION =
            "failed to get the binder transaction info of pid "; // + pid
    private static String LOG_ERROR_LINE_FAILED_TO_GET_THE_PROCESS_TYPES =
            "failed to get the process types, stack traces will not be added";
    private static String LOG_ERROR_LINE_FAILED_TO_GET_WHOLE_BINDER_TRANSACTION_INFO =
            "failed to get whole binder transaction info from pid "; // + pid
    private static String LOG_ERROR_LINE_BINDER_PROC_FILE_NOT_EXIST =
            "binder proc file not exist. drop pid "; // + pid
    private static String LOG_ERROR_LINE_BINDER_PROC_FILE_IS_NOT_READABLE =
            "binder proc file is not readable. drop pid "; // + pid
    private static String LOG_ERROR_LINE_BINDER_PROC_FILE_NOT_FOUND =
            "binder proc file not found. drop pid "; // + pid
    private static String LOG_ERROR_LINE_BINDER_PROC_FILE_IO_ERROR =
            "binder proc file io error. drop pid "; // + pid
    private static String LOG_ERROR_LINE_COULD_NOT_GET_THE_PID_OF_ZYGOTE =
            "could not get the pid of zygote";
    private static String LOG_ERROR_LINE_COULD_NOT_GET_THE_PROCESS_TYPE =
            "could not get the process type. drop pid "; //+ pid

    /*
     * Used to hold the binder transaction information.
     */
    public static class BinderProcsInfo {
        public ArrayList<Integer> javaPids;
        public ArrayList<Integer> nativePids;
        public ArrayList<String> rawInfo;

        public BinderProcsInfo() {
            javaPids = new ArrayList<Integer>();
            nativePids = new ArrayList<Integer>();
            rawInfo = new ArrayList<String>();
        }
    }

    public BinderTransaction() {
    }

    /*
     * Collect the binder transaction information for dropbox log.
     *
     * @param startPid pid for starting point of collecting binder transaction
     *     information.
     * @return It should not be null. Collected binder transaction info will be stored:
     *     javaPids, list of java processes that was chained with outgoing transactions.
     *     nativePids, list of native processes that was chained with outgoing transactions.
     *     rawInfo, binder transaction info raw lines. Also including error message.
     */
    public BinderProcsInfo getInfo(final int startPid) {

        final BinderProcsInfo workInfo = new BinderProcsInfo();
        boolean isBinderProcsCollectorCompleted = false;
        Thread binderProcThread = new Thread("BinderProcsCollector") {
            public void run() {
                final LinkedList<Integer> targetPids = new LinkedList<Integer>();
                final LinkedList<Integer> scannedPids = new LinkedList<Integer>();

                // Append header line.
                workInfo.rawInfo.add(makeBinderTransactionsLogHeader(startPid));

                // Scan the outgoing binder transaction chains from startPid.
                targetPids.offer(startPid);
                while (!targetPids.isEmpty()) {
                     // Pick a first one and remove it from targetPids.
                    int currentPid = targetPids.pollFirst();

                    // targetPids, scannedPids and workInfo.rawInfo will be modified.
                    boolean successful = findBinderTransactions(
                            currentPid, targetPids, scannedPids, workInfo.rawInfo);
                    if(!successful) {
                        // Append error line but continue.
                        workInfo.rawInfo.add(makeBinderTransactionsErrorLog(
                                LOG_ERROR_LINE_FAILED_TO_GET_THE_BINDER_TRANSACTION +
                                Integer.toString(currentPid)));
                    }
                }

                // Sorting the pids by process type.
                // workInfo.javaPids and workInfo.nativePids will be filled.
                boolean successful = separatePidsByProcessType(scannedPids, workInfo);
                if(!successful) {
                    // Append error line but continue.
                    workInfo.rawInfo.add(makeBinderTransactionsErrorLog(
                            LOG_ERROR_LINE_FAILED_TO_GET_THE_PROCESS_TYPES));
                }

                // Append footer line.
                workInfo.rawInfo.add(makeBinderTransactionsLogFooter(startPid));
            }};
        binderProcThread.start();
        try {
            // Fail safe, give up if it takes too long time.
            binderProcThread.join(BINDER_PROCS_COLLECTOR_TIMEOUT);
            if (binderProcThread.isAlive()) {
                // Timed out.
                Slog.e(TAG, "ERROR! binderProc thread timed out! failed to get binder info.");
            } else {
                // Exit worker thread. The whole process is completed.
                isBinderProcsCollectorCompleted = true;
            }
        } catch (InterruptedException e) {
            Slog.w(TAG, "ERROR! binderProc thread has interrupted!");
        } finally {
        }

        // It should not be null when return.
        BinderProcsInfo resultInfo;
        if(isBinderProcsCollectorCompleted) {
            // The whole process has been completed. Successful, or minor errors
            // may be included but does not need to cancel the whole.
            resultInfo = workInfo;
        } else {
            // Worker thread has timed out or the whole process failed...
            // Return error info.
            resultInfo = new BinderProcsInfo();

            // Append header line.
            resultInfo.rawInfo.add(makeBinderTransactionsLogHeader(startPid));
            // Append error line.
            resultInfo.rawInfo.add(makeBinderTransactionsErrorLog(
                    LOG_ERROR_LINE_FAILED_TO_GET_WHOLE_BINDER_TRANSACTION_INFO +
                    Integer.toString(startPid)));
            // Append footer line.
            resultInfo.rawInfo.add(makeBinderTransactionsLogFooter(startPid));
        }
        return resultInfo;
    }

    private boolean findBinderTransactions(int pid, LinkedList<Integer> targetPids,
                LinkedList<Integer> scannedPids, ArrayList<String> rawInfo) {

        if (!scannedPids.contains(pid)) {
            scannedPids.offer(pid);
        }

        String procFileName = getBinderProcFileName(pid);
        File binderProcFile = new File(procFileName);
        if (!binderProcFile.exists()) {
            Slog.e(TAG, "Binder proc file not exist.");
            rawInfo.add(makeBinderTransactionsErrorLog(
                    LOG_ERROR_LINE_BINDER_PROC_FILE_NOT_EXIST + Integer.toString(pid)));
            return false;
        }
        if (!binderProcFile.isFile() || !binderProcFile.canRead()) {
            Slog.e(TAG, "Binder proc file is not readable.");
            rawInfo.add(makeBinderTransactionsErrorLog(
                    LOG_ERROR_LINE_BINDER_PROC_FILE_IS_NOT_READABLE + Integer.toString(pid)));
            return false;
        }

        boolean error = false;
        FileInputStream rawInputStream = null;
        InputStreamReader rawReader = null;
        BufferedReader reader = null;
        try {
            rawInputStream = new FileInputStream(binderProcFile);
            rawReader = new InputStreamReader(rawInputStream);
            reader = new BufferedReader(rawReader);
            String line;
            // Scan the binder proc strings to find the binder transactions info.
            while ((line = reader.readLine()) != null) {
                Matcher matcher = matchBinderTransactionLine(line);
                if (matcher.find()) {
                    parseBinderTransactionLine(matcher, line, targetPids, scannedPids, rawInfo);
                }
            }
        } catch (FileNotFoundException e) {
            Slog.w(TAG, "Binder proc file not found. ", e);
            rawInfo.add(makeBinderTransactionsErrorLog(
                    LOG_ERROR_LINE_BINDER_PROC_FILE_NOT_FOUND + Integer.toString(pid)));
            error = true;
        } catch (IOException e) {
            Slog.w(TAG, "Binder proc file read io error.", e);
            rawInfo.add(makeBinderTransactionsErrorLog(
                    LOG_ERROR_LINE_BINDER_PROC_FILE_IO_ERROR + Integer.toString(pid)));
            error = true;
        } catch (Exception e) {
            // Fail safe.
            Slog.w(TAG, "Unexpected error during binder proc file processing.", e);
            error = true;
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException e) {}
            }
            if (rawReader != null) {
                try { rawReader.close(); } catch (IOException e) {}
            }
            if (rawInputStream != null) {
                try { rawInputStream.close(); } catch (IOException e) {}
            }
        }

        return !error;
    }

    private Matcher matchBinderTransactionLine(String line) {
        return sBinderPattern.matcher(line);
    }

    // Note: Make sure that matcher.find() returns ture before, calling this method.
    private void parseBinderTransactionLine(Matcher matcher, String line,
            LinkedList<Integer> targetPids, LinkedList<Integer> scannedPids,
            ArrayList<String> rawInfo) {
        String direction = matcher.group(BINDER_TRANSACTION_GROUP_DIRECTION);
        // parseInt should be done successfully, source string format is ensured by regex (-?\\d+).
        Integer toPid = Integer.parseInt(matcher.group(BINDER_TRANSACTION_GROUP_TO_PID));

        // Add a raw transactions info line to log string.
        rawInfo.add(line);

        // Add a new target pid if we found a new outgoing pid.
        if (toPid != 0 && direction.equals("outgoing")) {
            if(!scannedPids.contains(toPid) && !targetPids.contains(toPid)) {
                targetPids.offer(toPid);
            }
        }
    }

    private boolean separatePidsByProcessType(LinkedList<Integer> srcPids,
            BinderProcsInfo resultInfo) {

        // Find the PIDs of zygote.
        String[] zygoteCmd = {"zygote", "zygote64"};
        int[] zygotePids = getPidsForCommands(zygoteCmd);
        if (zygotePids == null || zygotePids.length <= 0) {
            Slog.e(TAG, "Could not get the pid of zygote.");
            resultInfo.rawInfo.add(makeBinderTransactionsErrorLog(
                    LOG_ERROR_LINE_COULD_NOT_GET_THE_PID_OF_ZYGOTE));
            return false;
        }

        // Determine whether each pid is an native process or a Zygote child process.
        int ret = PID_PROC_UNKNOWN;
        for (Integer pid : srcPids) {
            ret = getProcessTypeOfPid(pid, zygotePids);
            if (ret == PID_PROC_VM) {
                resultInfo.javaPids.add(pid);
            } else if (ret == PID_PROC_NATIVE) {
                resultInfo.nativePids.add(pid);
            } else {
                // Drop this pid, append an error log.
                Slog.w(TAG, "Could not get the process type. drop pid " + pid);
                resultInfo.rawInfo.add(makeBinderTransactionsErrorLog(
                        LOG_ERROR_LINE_COULD_NOT_GET_THE_PROCESS_TYPE + Integer.toString(pid)));
            }
        }
        return true;
    }

    private int getProcessTypeOfPid(int pid, int[] zygotePids) {
        int ppid = getParentPid(pid);
        if (ppid > -1) {
            for (int zygotePid : zygotePids) {
                if (ppid == zygotePid) {
                    return PID_PROC_VM;
                }
            }
            return PID_PROC_NATIVE;
        }
        return PID_PROC_UNKNOWN;
    }

    private String makeBinderTransactionsLogHeader(int pid) {
        StringBuilder sb = new StringBuilder();
        sb.append("----- binder transactions from pid ");
        sb.append(Integer.toString(pid));
        sb.append(" -----");
        return sb.toString();
    }

    private String makeBinderTransactionsLogFooter(int pid) {
        StringBuilder sb = new StringBuilder();
        sb.append("----- end binder transactions ");
        sb.append(Integer.toString(pid));
        sb.append(" -----");
        return sb.toString();
    }

    private String makeBinderTransactionsErrorLog(String string) {
        StringBuilder sb = new StringBuilder();
        sb.append(LOG_ERROR_HEADER);
        sb.append(string);
        return sb.toString();
    }

    protected String getBinderProcFileName(int pid) {
        return "/sys/kernel/debug/binder/proc/" + Integer.toString(pid);
    }

    protected int[] getPidsForCommands(String[] cmds) {
        return Process.getPidsForCommands(cmds);
    }

    protected int getParentPid(int pid) {
        return Process.getParentPid(pid);
    }
}

