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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.ITargetPreparer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class PackageUpdateSetup implements ITargetPreparer {

    private static final String TEST_SCRIPT = "run_test.bash";

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) {
        PackageUpdateBuildInfo packageUpdateBuildInfo = (PackageUpdateBuildInfo)buildInfo;
        List<String> command = new ArrayList<>();
        command.add("bash");
        String script = new File(TEST_SCRIPT).getPath();
        command.add(script);
        command.add("-s");
        command.add(device.getSerialNumber());
        if (packageUpdateBuildInfo.getReference() != null) {
            command.add("-r");
            command.add(packageUpdateBuildInfo.getReference().getPath());
        }
        command.add(packageUpdateBuildInfo.getTestCase().getPath());
        command.add(packageUpdateBuildInfo.getTestApksZip().getPath());
        command.add(packageUpdateBuildInfo.getRom1Zip().getPath());
        command.add(packageUpdateBuildInfo.getRom2Zip().getPath());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        BufferedReader reader = null;
        try {
            Process p = pb.start();
            reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while(true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                CLog.i(line);
            }
            p.waitFor();
        } catch (IOException e) {
            CLog.e(e);
        } catch (InterruptedException e) {
            CLog.e(e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    CLog.e(e);
                }
            }
        }
    }
}
