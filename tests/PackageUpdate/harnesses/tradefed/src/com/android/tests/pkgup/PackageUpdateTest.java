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

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PackageUpdateTest implements IRemoteTest, IBuildReceiver {

    private static final String DIFF_JSON = "reports/diff/diff.json";

    private static final String[][] RESULT_KEYS = {
            {"1_result-ROM1.txt", "ROM1"},
            {"2_result-ROM1_Op1.txt", "ROM1_Op1"},
            {"3_result-ROM1_Op2.txt", "ROM1_Op2"},
            {"4_result-ROM2.txt", "ROM2"},
            {"5_result-ROM2_Op1.txt", "ROM2_Op1"},
            {"6_result-ROM2_Op2.txt", "ROM2_Op2"}
    };

    private static final String MESSAGE = "See the following operations in analysis.html:";

    private PackageUpdateBuildInfo mBuildInfo;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = (PackageUpdateBuildInfo)buildInfo;
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        long startTime = System.currentTimeMillis();
        JSONObject jsonObject = readDiff();
        if (jsonObject == null) {
            throw new RuntimeException("failed to read diff.json");
        }
        List<String> testcases = new ArrayList<>();
        for (Iterator<String> ite = jsonObject.keys(); ite.hasNext(); ) {
            String testcase = ite.next();
            testcases.add(testcase);
        }
        listener.testRunStarted(mBuildInfo.getTestCaseName(), testcases.size());

        Collections.sort(testcases);
        Map<String, String> emptyMap = Collections.emptyMap();
        for (String testcase : testcases) {
            TestIdentifier testId = new TestIdentifier(getClass().getCanonicalName(), testcase);
            listener.testStarted(testId);
            StringBuilder trace = new StringBuilder();
            try {
                JSONObject result = jsonObject.getJSONObject(testcase);
                for (String[] key : RESULT_KEYS) {
                    String diff = result.getString(key[0]);
                    if (diff != null && diff.length() > 0) {
                        if (trace.length() == 0) {
                            trace.append(MESSAGE);
                        }
                        trace.append('\n').append(key[1]);
                    }
                }
                if (trace.length() > 0) {
                    listener.testFailed(testId, trace.toString());
                }
            } catch (JSONException e) {
                CLog.e(e);
                listener.testFailed(testId, "could not get result");
            }
            listener.testEnded(testId, emptyMap);
        }
        listener.testRunEnded(System.currentTimeMillis() - startTime, emptyMap);
    }

    private static JSONObject readDiff() {
        File file = new File(DIFF_JSON);
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
