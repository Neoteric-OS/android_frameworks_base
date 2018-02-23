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

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.BuildSerializedVersion;

import java.io.File;


public class PackageUpdateBuildInfo extends BuildInfo {

    private static final long serialVersionUID = BuildSerializedVersion.VERSION;

    private File mReference;

    private String mTestCaseName;

    private File mTestCase;

    private File mTestApksZip;

    private File mRom1Zip;

    private File mRom2Zip;

    /**
     * Creates a {@link PackageUpdateBuildInfo}
     */
    public PackageUpdateBuildInfo() {
        super();
    }

    /**
     * Creates a {@link PackageUpdateBuildInfo}
     */
    public PackageUpdateBuildInfo(String buildId, String buildTargetName) {
        super(buildId, buildTargetName);
    }

    /**
     * Returns the path to reference_directory
     */
    public File getReference() {
        return mReference;
    }

    public void setReference(File reference) {
        mReference = reference;
    }

    public String getTestCaseName() {
        return mTestCaseName;
    }

    public void setTestCaseName(String testCaseName) {
        mTestCaseName = testCaseName;
    }

    public File getTestCase() {
        return mTestCase;
    }

    public void setTestCase(File testCase) {
        mTestCase = testCase;
    }

    public File getTestApksZip() {
        return mTestApksZip;
    }

    public void setTestApksZip(File testApksZip) {
        mTestApksZip = testApksZip;
    }

    public File getRom1Zip() {
        return mRom1Zip;
    }

    public void setRom1Zip(File rom1Zip) {
        mRom1Zip = rom1Zip;
    }

    public void setRom2Zip(File rom2Zip) {
        mRom2Zip = rom2Zip;
    }

    public File getRom2Zip() {
        return mRom2Zip;
    }
}
