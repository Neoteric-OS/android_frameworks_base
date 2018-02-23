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

import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.util.StreamUtil;

import org.kxml2.io.KXmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ResultReporter extends CollectingTestListener implements ILogSaverListener {

    private static final String LOG_TAG = "ResultReporter";

    private static final String TEST_RESULT_ZIP_PREFIX = "test_result";

    private static final String TEST_RESULT_DIR = "results";
    private static final String TEST_RESULT_XML = "test_result.xml";
    private static final String TEST_RESULT_XSL = "test_result.xsl";
    private static final String TEST_RESULT_CSS = "test_result.css";

    private static final String RESOURCE_DIR = "harnesses/tradefed/res";

    /** the XML namespace */
    private static final String NS = null;

    private static final String RESULT = "Result";
    private static final String BUILD = "Build";
    private static final String SUMMARY = "Summary";
    private static final String MODULE = "Module";
    private static final String TESTCASE = "TestCase";
    private static final String TEST = "Test";
    private static final String FAILURE = "Failure";
    private static final String STACKTRACE = "StackTrace";

    private static final String ATTR_PASS = "pass";
    private static final String ATTR_FAILED = "failed";
    private static final String ATTR_NOT_EXECUTED = "not_executed";
    private static final String ATTR_MODULES_DONE = "modules_done";
    private static final String ATTR_MODULES_TOTAL = "modules_total";
    private static final String ATTR_DONE = "done";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_RESULT = "result";
    private static final String ATTR_MESSAGE = "message";

    private ILogSaver mLogSaver;

    /**
     * {@inheritDoc}
     */
    @Override
    public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
        super.testEnded(test, testMetrics);
        logStatus(test);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testEnded(TestIdentifier test, long endTime, Map<String, String> testMetrics) {
        super.testEnded(test, endTime, testMetrics);
        logStatus(test);
    }

    private void logStatus(TestIdentifier test) {
        TestResult result = getCurrentRunResults().getTestResults().get(test);
        String status = getResultString(result.getStatus());
        if (status != null) {
            Log.logAndDisplay(Log.LogLevel.INFO, LOG_TAG, String.format("%s : %s",
                    test.getTestName(), status));
        }
    }

    private String getResultString(TestResult.TestStatus status) {
        if (TestResult.TestStatus.PASSED.equals(status)) {
            return "pass";
        }
        if (TestResult.TestStatus.FAILURE.equals(status)) {
            return "fail";
        }
        return null;
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        super.invocationEnded(elapsedTime);
        generateResultReport();
    }

    private void generateResultReport() {
        ZipOutputStream zipOut = null;
        InputStream in = null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            zipOut = new ZipOutputStream(out);
            writeResult(zipOut);
            zipOut.close();
            in = new ByteArrayInputStream(out.toByteArray());
            LogFile log = mLogSaver.saveLogData(TEST_RESULT_ZIP_PREFIX, LogDataType.ZIP, in);
            String path = new File(log.getPath()).getCanonicalPath();
            String msg = String.format("Test result file generated at %s. Total tests %d, " +
                    "Failed %d", path, getNumTotalTests(), getNumAllFailedTests());
            Log.logAndDisplay(Log.LogLevel.INFO, LOG_TAG, msg);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to generate result data");
        } finally {
            StreamUtil.close(zipOut);
            StreamUtil.close(in);
        }
    }

    private void writeResult(ZipOutputStream out) throws IOException {
        out.putNextEntry(new ZipEntry(TEST_RESULT_DIR + "/" + TEST_RESULT_XML));
        writeResultXml(out);
        out.closeEntry();

        out.putNextEntry(new ZipEntry(TEST_RESULT_DIR + "/" + TEST_RESULT_XSL));
        File xslFile = new File(RESOURCE_DIR, TEST_RESULT_XSL);
        writeFile(xslFile, out);
        out.closeEntry();

        out.putNextEntry(new ZipEntry(TEST_RESULT_DIR + "/" + TEST_RESULT_CSS));
        File cssFile = new File(RESOURCE_DIR, TEST_RESULT_CSS);
        writeFile(cssFile, out);
        out.closeEntry();
    }

    private void writeResultXml(OutputStream out) throws IOException {
        PackageUpdateBuildInfo buildInfo = (PackageUpdateBuildInfo)getInvocationContext()
                .getBuildInfos().get(0);

        KXmlSerializer serializer = new KXmlSerializer();
        serializer.setOutput(out, "UTF-8");
        serializer.startDocument("UTF-8", null);
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
        serializer.processingInstruction("xml-stylesheet type=\"text/xsl\" " +
                "href=\"" + TEST_RESULT_XSL + "\"");

        serializer.startTag(NS, RESULT);
        serializer.startTag(NS, BUILD);
        serializer.endTag(NS, BUILD);
        serializer.startTag(NS, SUMMARY);
        serializer.attribute(NS, ATTR_PASS,
                Integer.toString(getNumTestsInState(TestResult.TestStatus.PASSED)));
        serializer.attribute(NS, ATTR_FAILED,
                Integer.toString(getNumTestsInState(TestResult.TestStatus.FAILURE)));
        serializer.attribute(NS, ATTR_NOT_EXECUTED, "0");
        serializer.attribute(NS, ATTR_MODULES_DONE, "1");
        serializer.attribute(NS, ATTR_MODULES_TOTAL, "1");
        serializer.endTag(NS, SUMMARY);

        serializer.startTag(NS, MODULE);
        serializer.attribute(NS, ATTR_NAME, buildInfo.getTestCaseName());
        serializer.attribute(NS, ATTR_DONE, "true");
        serializer.attribute(NS, ATTR_NOT_EXECUTED, "0");
        serializer.attribute(NS, ATTR_PASS,
                Integer.toString(getNumTestsInState(TestResult.TestStatus.PASSED)));

        serializer.startTag(NS, TESTCASE);
        serializer.attribute(NS, ATTR_NAME, buildInfo.getTestCaseName());

        for (TestRunResult runResult : getRunResults()) {
            Map<TestIdentifier, TestResult> testResults = runResult.getTestResults();
            for (Map.Entry<TestIdentifier, TestResult> testEntry : testResults.entrySet()) {
                serializer.startTag(NS, TEST);
                serializer.attribute(NS, ATTR_NAME, testEntry.getKey().getTestName());
                TestResult testResult = testEntry.getValue();
                String result = getResultString(testResult.getStatus());
                if (result != null) {
                    serializer.attribute(NS, ATTR_RESULT, result);
                }
                if (TestResult.TestStatus.FAILURE.equals(testResult.getStatus())) {
                    String stackText = testResult.getStackTrace();
                    serializer.startTag(NS, FAILURE);
                    int index = stackText.indexOf('\n');
                    if (index >= 0) {
                        index = stackText.indexOf('\n', index + 1);
                    }
                    if (index >= 0) {
                        serializer.attribute(NS, ATTR_MESSAGE, stackText.substring(0, index));
                    } else {
                        serializer.attribute(NS, ATTR_MESSAGE, stackText);
                    }
                    serializer.startTag(NS, STACKTRACE);
                    serializer.text(stackText);
                    serializer.endTag(NS, STACKTRACE);
                    serializer.endTag(NS, FAILURE);
                }
                serializer.endTag(NS, TEST);
            }
        }
        serializer.endTag(NS, TESTCASE);
        serializer.endTag(NS, MODULE);
        serializer.endTag(NS, RESULT);
        serializer.endDocument();

        serializer.flush();
    }

    private void writeFile(File file, OutputStream out) throws IOException {
        if (!file.exists() || !file.isFile()) {
            Log.logAndDisplay(Log.LogLevel.WARN, LOG_TAG, String.format("File not found: %s",
                    file.getPath()));
            return;
        }
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            StreamUtil.copyStreams(in, out);
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    @Override
    public void testLogSaved(String dataName, LogDataType logDataType, InputStreamSource inputStreamSource,
                             LogFile logFile) {
        String path = logFile.getPath();
        try {
            path = new File(path).getCanonicalPath();
        } catch (IOException e) {
        }
        Log.logAndDisplay(Log.LogLevel.INFO, LOG_TAG, String.format("Saved %s log to %s", dataName,
                path));
    }

    @Override
    public void setLogSaver(ILogSaver logSaver) {
        mLogSaver = logSaver;
    }
}
