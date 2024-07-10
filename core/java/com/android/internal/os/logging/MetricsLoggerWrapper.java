/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.internal.os.logging;

import android.app.Application;
import android.os.Process;
import android.util.Log;
import android.view.WindowManager.LayoutParams;

import com.android.internal.os.ProcfsMemoryUtil;
import com.android.internal.util.FrameworkStatsLog;
import java.util.Map;
import libcore.util.NativeAllocationRegistry;

/**
 * Used to wrap different logging calls in one, so that client side code base is clean and more
 * readable.
 */
public class MetricsLoggerWrapper {

    private static final String TAG = "MetricsLoggerWrapper";

    public static void logAppOverlayEnter(int uid, String packageName, boolean changed, int type, boolean usingAlertWindow) {
        if (changed) {
            if (type != LayoutParams.TYPE_APPLICATION_OVERLAY) {
                FrameworkStatsLog.write(FrameworkStatsLog.OVERLAY_STATE_CHANGED, uid, packageName,
                        true, FrameworkStatsLog.OVERLAY_STATE_CHANGED__STATE__ENTERED);
            } else if (!usingAlertWindow){
                FrameworkStatsLog.write(FrameworkStatsLog.OVERLAY_STATE_CHANGED, uid, packageName,
                        false, FrameworkStatsLog.OVERLAY_STATE_CHANGED__STATE__ENTERED);
            }
        }
    }

    public static void logAppOverlayExit(int uid, String packageName, boolean changed, int type, boolean usingAlertWindow) {
        if (changed) {
            if (type != LayoutParams.TYPE_APPLICATION_OVERLAY) {
                FrameworkStatsLog.write(FrameworkStatsLog.OVERLAY_STATE_CHANGED, uid, packageName,
                        true, FrameworkStatsLog.OVERLAY_STATE_CHANGED__STATE__EXITED);
            } else if (!usingAlertWindow){
                FrameworkStatsLog.write(FrameworkStatsLog.OVERLAY_STATE_CHANGED, uid, packageName,
                        false, FrameworkStatsLog.OVERLAY_STATE_CHANGED__STATE__EXITED);
            }
        }
    }

    public static void logPostGcMemorySnapshot() {
        int pid = Process.myPid();
        String packageName = Application.getProcessName();
        Map<Class, NativeAllocationRegistry.Metrics> metrics = NativeAllocationRegistry.getMetrics();
        int nMetrics = metrics.size();

        String[] class_names = new String[nMetrics];
        long[] count_malloced = new long[nMetrics];
        long[] count_nonmalloced = new long[nMetrics];
        long[] bytes_malloced = new long[nMetrics];
        long[] bytes_nonmalloced = new long[nMetrics];

        int i = 0;
        for (Class cls : metrics.keySet()) {
            NativeAllocationRegistry.Metrics m = metrics.get(cls);
            class_names[i] = cls.getName();
            count_malloced[i] = m.count_malloced;
            bytes_malloced[i] = m.bytes_malloced;
            count_nonmalloced[i] = m.count_nonmalloced;
            bytes_nonmalloced[i] = m.bytes_nonmalloced;
            i++;

            Log.i(TAG, "NativeAllocationRegistry.Metrics class=" + cls.getName()
                + ", malloced (" + m.count_malloced + ", " + m.bytes_malloced + ")"
                + ", nonmalloced (" + m.count_nonmalloced + ", " + m.bytes_nonmalloced + ")");
        }

        ProcfsMemoryUtil.MemorySnapshot snapshot = ProcfsMemoryUtil.readMemorySnapshotFromProcfs(pid);
        FrameworkStatsLog.write(FrameworkStatsLog.POSTGC_MEMORY_SNAPSHOT,
            snapshot.uid, packageName, pid,
            snapshot.rssHighWaterMarkInKilobytes,
            snapshot.rssInKilobytes,
            snapshot.anonRssInKilobytes,
            snapshot.rssShmemKilobytes,
            snapshot.swapInKilobytes,
            0,
            false,
            0,
            class_names,
            count_malloced,
            bytes_malloced,
            count_nonmalloced,
            bytes_nonmalloced);
    }
}
