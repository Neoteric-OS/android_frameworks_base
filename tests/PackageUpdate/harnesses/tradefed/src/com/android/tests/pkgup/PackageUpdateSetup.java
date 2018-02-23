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
import com.android.tradefed.config.Option;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class PackageUpdateSetup implements ITargetPreparer {

    @Option(name="reference",
            description="path to reference_directory",
            importance= Option.Importance.IF_UNSET)
    protected File mReference;

    @Option(name="strict-compare",
            description="compare strictly with reference",
            importance= Option.Importance.IF_UNSET)
    protected boolean mStrictCompare = false;

    @Option(name="preinstall-loc",
            description="location to pre-install test applications",
            importance= Option.Importance.IF_UNSET)
    protected String mPreInstallLoc;

    @Option(name="update-pattern",
            description="os versions of ROM1 and ROM2",
            importance= Option.Importance.IF_UNSET)
    protected String mUpdatePattern;

    @Option(name="testcase",
            description="path to the testcase definition",
            importance= Option.Importance.IF_UNSET)
    protected File mTestCase;

    @Option(name="testapks-zip",
            description="path to the zip archive of test applications",
            importance= Option.Importance.ALWAYS)
    protected File mTestApksZip;

    @Option(name="rom1-zip",
            description="path to the zip archive of base images of ROM1",
            importance= Option.Importance.ALWAYS)
    private File mRom1Zip;

    @Option(name="rom2-zip",
            description="path to the zip archive of base images of ROM2",
            importance= Option.Importance.ALWAYS)
    private File mRom2Zip;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError {
        List<String> command = buildPackageUpdateCommand(device, buildInfo);

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
            int exitValue = p.exitValue();
            if (exitValue != 0) {
                throw new TargetSetupError("PackageUpdate test was finished with non-zero status code. exitValue=" + exitValue,
                        device.getDeviceDescriptor());
            }
        } catch (IOException e) {
            throw new TargetSetupError("PackageUpdate test was aborted due to IOException", e,
                    device.getDeviceDescriptor());
        } catch (InterruptedException e) {
            throw new TargetSetupError("PackageUpdate test was aborted due to InterruptedException", e,
                    device.getDeviceDescriptor());
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

    protected List<String> buildPackageUpdateCommand(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError {
        File script = PkgupPathUtil.getAbsoluteFile("run_test.bash");
        if (!script.exists()) {
            throw new TargetSetupError("run_test.bash not found: " + script.getAbsolutePath(),
                    device.getDeviceDescriptor());
        }
        List<String> command = new ArrayList<>();
        command.add("bash");
        command.add(script.getAbsolutePath());
        command.add("-s");
        command.add(device.getSerialNumber());
        if (mReference != null) {
            command.add("-r");
            command.add(PkgupPathUtil.getAbsolutePath(mReference));
        }
        if (mPreInstallLoc != null) {
            command.add("-l");
            command.add(mPreInstallLoc);
        }
        if (mUpdatePattern != null) {
            command.add("-o");
            command.add(mUpdatePattern);
        }
        if (mStrictCompare) {
            command.add("-c");
        }
        command.add(PkgupPathUtil.getAbsolutePath(mTestCase));
        command.add(PkgupPathUtil.getAbsolutePath(mTestApksZip));
        command.add(mRom1Zip.getPath());
        command.add(mRom2Zip.getPath());
        return command;
    }
}
