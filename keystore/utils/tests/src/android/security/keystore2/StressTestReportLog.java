/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.security.keystore2;

import android.keystore.cts.util.TestUtils;
import android.os.Build;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import java.util.ArrayList;
import java.util.List;

public class StressTestReportLog {
    private static final String TAG = StressTestReportLog.class.getSimpleName();
    private static final String REPORT_LOG_NAME = StressTestReportLog.class.getSimpleName();

    public static void logStressTestResults(List<StressTestResult> results, String name) {
        for (StressTestResult result : results) {
            DeviceReportLog reportLog =
                    new DeviceReportLog(
                            REPORT_LOG_NAME, "stress_test_" + result.getThreadId(),
                            TestUtils.getFilesDir());
            reportLog.addValue(
                    "test_environment",
                    TestUtils.EXPECTED_PROVIDER_NAME + "/" + Build.CPU_ABI,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);
            reportLog.addValue(
                    "test_name", name, ResultType.NEUTRAL, ResultUnit.NONE);
            reportLog.addValue(
                    "sample_count", result.getSampleCount(), ResultType.NEUTRAL,
                    ResultUnit.COUNT);
            reportLog.addValue(
                    "setup_time", result.getSetupTime(), ResultType.LOWER_BETTER,
                    ResultUnit.MS);
            reportLog.addValue(
                    "mean_time", result.getMean(), ResultType.LOWER_BETTER, ResultUnit.MS);
            reportLog.addValue(
                    "sample_std_dev",
                    result.getSampleStdDev(),
                    ResultType.LOWER_BETTER,
                    ResultUnit.MS);
            reportLog.addValue(
                    "median_time", result.getMedian(), ResultType.LOWER_BETTER, ResultUnit
                            .MS);
            reportLog.addValue(
                    "percentile_90_time",
                    result.getPercentile(0.9),
                    ResultType.LOWER_BETTER,
                    ResultUnit.MS);
            reportLog.addValue(
                    "teardown_time",
                    result.getTearDownTime(),
                    ResultType.LOWER_BETTER,
                    ResultUnit.MS);
            reportLog.submit(InstrumentationRegistry.getInstrumentation());
        }
    }

    public static void logCumulativePerformance(List<StressTestResult> stressTestResults,
            String name) {
        StressTestResult finalStressTestResult = new StressTestResult();
        finalStressTestResult.calculateCumulativePerformance(stressTestResults);

        logStressTestResults(new ArrayList<>() {
            {
                add(finalStressTestResult);
            }
        }, name);

        Log.d(TAG, "*************** STRESS TEST REPORT ***************");
        Log.d(TAG, "Sample Size: " + finalStressTestResult.getSampleCount());
        Log.d(TAG, "Setup Time: " + finalStressTestResult.getSetupTime());
        Log.d(TAG, "Total Time: " + finalStressTestResult.getTotalTime());
        Log.d(TAG, "Average Time: " + finalStressTestResult.getMean());
        Log.d(TAG, "StdDev Time: " + finalStressTestResult.getSampleStdDev());
        Log.d(TAG, "*************** STRESS TEST REPORT ***************");
    }
}
