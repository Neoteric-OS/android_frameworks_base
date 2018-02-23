/**
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

package com.android.tests.pkgup;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.testtype.IRemoteTest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PackageUpdateTest implements IRemoteTest {

    private static final String DIFF_JSON = "reports/diff/diff.json";

    private static final String[][] DIFF_KEYS = {
            {"1_result-ROM1.txt", "ROM1"},
            {"2_result-ROM1_Op1.txt", "ROM1_Op1"},
            {"3_result-ROM1_Op2.txt", "ROM1_Op2"},
            {"4_result-ROM2.txt", "ROM2"},
            {"5_result-ROM2_Op1.txt", "ROM2_Op1"},
            {"6_result-ROM2_Op2.txt", "ROM2_Op2"}
    };

    private static final String RESULT_KEY = "result";
    private static final String PASS = "1";
    private static final String FAIL = "0";

    @Option(name="testcase-name",
            description="name of testcase",
            importance= Option.Importance.IF_UNSET)
    private String mTestCaseName;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        long startTime = System.currentTimeMillis();
        JSONObject jsonObject = readDiff();
        if (jsonObject == null) {
            throw new RuntimeException("failed to read diff.json");
        }
        List<String> testNames = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Iterator<String> ite = jsonObject.keys();
        while (ite.hasNext()) {
            String testName = ite.next();
            testNames.add(testName);
        }
        listener.testRunStarted(mTestCaseName, testNames.size());

        Collections.sort(testNames);
        for (String testName : testNames) {
            TestDescription test = new TestDescription(getClass().getCanonicalName(), testName);
            listener.testStarted(test);
            StringBuilder trace = new StringBuilder();
            String result = PASS;
            try {
                JSONObject diffObject = jsonObject.getJSONObject(testName);
                for (String[] key : DIFF_KEYS) {
                    String diff = diffObject.getString(key[0]);
                    if (diff != null && diff.length() > 0) {
                        if (trace.length() > 0) {
                            trace.append('\n');
                        }
                        trace.append(key[1]).append('\n').append(diff);
                    }
                }
                if (trace.length() > 0) {
                    result = FAIL;
                    listener.testFailed(test, trace.toString());
                }
            } catch (JSONException e) {
                CLog.e(e);
                result = FAIL;
                listener.testFailed(test, "could not get result");
            }
            Map<String, String> testMetrics = new HashMap<>();
            testMetrics.put(RESULT_KEY, result);
            listener.testEnded(test, testMetrics);
        }
        Map<String, String> emptyMap = Collections.emptyMap();
        listener.testRunEnded(System.currentTimeMillis() - startTime, emptyMap);
    }

    private static JSONObject readDiff() {
        File file = PkgupPathUtil.getAbsoluteFile(new File(DIFF_JSON));
        BufferedReader reader = null;

        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[1024];
        try {
            reader = new BufferedReader(new FileReader(file));
            int count = reader.read(buffer);
            while (count >= 0) {
                sb.append(buffer, 0, count);
                count = reader.read(buffer);
            }
        } catch (IOException e) {
            CLog.e(e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    CLog.e(e);
                }
            }
        }
        try {
            return new JSONObject(sb.toString());
        } catch (JSONException e) {
            CLog.e(e);
            return null;
        }
    }
}
