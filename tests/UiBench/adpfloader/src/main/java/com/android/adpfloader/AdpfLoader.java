/*
 * Copyright 2020 The Android Open Source Project
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

package com.android.adpfloader;

import java.util.List;

public class AdpfLoader {
    private static final String LOG_TAG = "AdpfLoader";

    public AdpfLoader() {
        System.loadLibrary("adpf-native");
    }

    public long createStage(List<Integer> threadIds, long desiredDurationMicros) {
        int[] threadArray = threadIds.stream().mapToInt(i -> i).toArray();
        return nCreateStage(threadArray, desiredDurationMicros);
    }

    public void hintLowLatency(List<Integer> threadIds) {
        int[] threadArray = threadIds.stream().mapToInt(i -> i).toArray();
        nHintLowLatency(threadArray);
    }

    public static final int UNIT_CPU = 1;
    public static final int UNIT_GPU = 2;

    public static final int DIRECTION_LOWER = 1;
    public static final int DIRECTION_HIGHER = 2;
    public static final int DIRECTION_MUCH_HIGHER = 3;

    public static final int MODE_UNSPECIFIED = 0;
    public static final int MODE_LOADING = 1;
    public static final int MODE_RUNNING = 2;
    public static final int MODE_PAUSED = 3;

    public native long nCreateStage(int[] threadIds, long desiredDurationMicros);
    public native void destroyStage(long id);
    public native void reportCpuCompletionTime(long stageId, long actualDurationMicros);
    public native void nHintLowLatency(int[] threadIds);
    public native void hintLoadChange(int unit, int direction);
    public native void hintMode(int mode, long majorPhase, long minorPhase);
    public native void allowAppSpecificOptimizations(boolean enable);
    public native void allowFidelityDegradation(boolean enable);
}
