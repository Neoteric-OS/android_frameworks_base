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

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.config.Option;

import java.io.File;

/**
 * A {@link PackageUpdateBuildProvider} that constructs a {@link PackageUpdateBuildInfo}
 * based on a provided local values from options.
 */
public class PackageUpdateBuildProvider implements IBuildProvider {

    @Option(name="build-id",
            description="build id to supply.")
    private String mBuildId = "0";

    @Option(name="build-target",
            description="build target name to supply.")
    private String mBuildTargetName = "stub";

    @Option(name="reference",
            description="path to reference_directory",
            importance= Option.Importance.IF_UNSET)
    private File mReference;

    @Option(name="testcase-name",
            description="name of testcase",
            importance= Option.Importance.IF_UNSET)
    private String mTestCaseName;

    @Option(name="testcase",
            description="path to the testcase definition",
            importance= Option.Importance.IF_UNSET)
    private File mTestCase;

    @Option(name="testapks-zip",
            description="path to the zip archive of test applications",
            importance= Option.Importance.ALWAYS)
    private File mTestApksZip;

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
    public IBuildInfo getBuild() throws BuildRetrievalError {
        if (mReference == null) {
            throw new BuildRetrievalError("reference is not specified.");
        }
        if (mTestCase == null) {
            throw new BuildRetrievalError("testcase is not specified.");
        }
        PackageUpdateBuildInfo buildInfo = new PackageUpdateBuildInfo(mBuildId, mBuildTargetName);
        buildInfo.setReference(mReference);
        buildInfo.setTestCaseName(mTestCaseName);
        buildInfo.setTestCase(mTestCase);
        buildInfo.setTestApksZip(mTestApksZip);
        buildInfo.setRom1Zip(mRom1Zip);
        buildInfo.setRom2Zip(mRom2Zip);
        return buildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void buildNotTested(IBuildInfo info) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        // ignore
    }
}
