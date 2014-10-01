/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.rrotests;

import android.test.InstrumentationTestRunner;
import android.test.InstrumentationTestSuite;

import junit.framework.TestSuite;

public class TestRunner extends InstrumentationTestRunner {
    @Override
    public TestSuite getAllTests() {
        InstrumentationTestSuite suite = new InstrumentationTestSuite(this);
        BaseTestCase.sSetup = Integer.valueOf(getArguments().getString("setup", "0"));
        suite.addTestSuite(TypeTests.class);
        suite.addTestSuite(MatrixTests.class);
        return suite;
    }

    @Override
    public ClassLoader getLoader() {
        return TestRunner.class.getClassLoader();
    }
}
