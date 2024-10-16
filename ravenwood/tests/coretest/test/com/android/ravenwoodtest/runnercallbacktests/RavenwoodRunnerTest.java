/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.ravenwoodtest.runnercallbacktests;

import static org.junit.Assert.assertFalse;

import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.NoRavenizer;
import android.platform.test.ravenwood.RavenwoodAwareTestRunner.RavenwoodTestRunnerInitializing;
import android.platform.test.ravenwood.RavenwoodRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@NoRavenizer // This class shouldn't be executed with RavenwoodAwareTestRunner.
public class RavenwoodRunnerTest extends RavenwoodRunnerTestBase {
    @Override
    public Class<?>[] getTestClasses() {
        // Hmm Class.getNestMembers() don't work on ART??
        return new Class[]{
                TestSimple.class,
                RavenwoodAwareTestRunnerTest.class,
                RavenwoodImplicitClassRuleDeviceOnlyTest.class,
        };
    }

    @RunWith(AndroidJUnit4.class)
    // CHECKSTYLE:OFF Generated code
    @Expected("""
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$TestSimple
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$TestSimple)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$TestSimple)
    testStarted: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$TestSimple)
    testFinished: test2(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$TestSimple)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$TestSimple
    testSuiteFinished: classes
    testRunFinished: 2,0,0,0
    """)
    // CHECKSTYLE:ON
    public static class TestSimple {
        @AfterClass
        public static void afterClass() {
        }

        @Test
        public void test1() {
        }

        @Test
        public void test2() {
        }

    }

    @RunWith(JUnitParamsRunner.class)
    // CHECKSTYLE:OFF Generated code
    @Expected("""
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest
    testStarted: testDeviceOnly(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest)
    testAssumptionFailure: got: <false>, expected: is <true>
    testFinished: testDeviceOnly(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest)
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest)
    testStarted: testWithParams[0](com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest)
    testFinished: testWithParams[0](com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest)
    testStarted: testWithParams[1](com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest)
    testFinished: testWithParams[1](com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest
    testSuiteFinished: classes
    testRunFinished: 4,0,1,0
    """)
    // Because of testDeviceOnly(), we expect a different result.
    @ExpectedOnDevice("""
    testRunStarted: classes
    testSuiteStarted: classes
    testSuiteStarted: com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest
    testStarted: testDeviceOnly(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest)
    testFinished: testDeviceOnly(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest)
    testStarted: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest)
    testFinished: test1(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest)
    testStarted: testWithParams[0](com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest)
    testFinished: testWithParams[0](com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest)
    testStarted: testWithParams[1](com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest)
    testFinished: testWithParams[1](com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest)
    testSuiteFinished: com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodAwareTestRunnerTest
    testSuiteFinished: classes
    testRunFinished: 4,0,0,0
    """)
    // CHECKSTYLE:ON
    public static class RavenwoodAwareTestRunnerTest {
        @RavenwoodTestRunnerInitializing
        public static void ravenwoodRunnerInitializing() {
        }

        @BeforeClass
        public static void beforeClass() {
        }

        @Test
        public void test1() {
        }

        @Test
        @Parameters({"foo", "bar"})
        public void testWithParams(String arg) {
        }

        @Test
        @DisabledOnRavenwood
        public void testDeviceOnly() {
            assertFalse(RavenwoodRule.isOnRavenwood());
        }

        @AfterClass
        public static void afterClass() {
        }
    }

    @RunWith(AndroidJUnit4.class)
    @DisabledOnRavenwood
    @Expected("""
    TODO
    """)
    @ExpectedOnDevice("""
    TODO
    """)
/*
Somehow we get it on the device.
     testSuiteStarted: classes
     testStarted: initializationError(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodImplicitClassRuleDeviceOnlyTest)
     testFailure: Failed to instantiate test runner class androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner


     testFinished: initializationError(com.android.ravenwoodtest.runnercallbacktests.RavenwoodRunnerTest$RavenwoodImplicitClassRuleDeviceOnlyTest)
     testSuiteFinished: classes
     testRunFinished: 1,1,0,0

 */
    public class RavenwoodImplicitClassRuleDeviceOnlyTest {
        @BeforeClass
        public static void beforeClass() {
        }

        @Test
        public void testDeviceOnly() {
        }

        @AfterClass
        public static void afterClass() {
        }
    }
}
