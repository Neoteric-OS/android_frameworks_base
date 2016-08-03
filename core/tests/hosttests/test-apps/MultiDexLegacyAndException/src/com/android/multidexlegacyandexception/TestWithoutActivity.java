/*
 * Copyright (C) 2014 The Android Open Source Project
 *
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
package com.android.multidexlegacyandexception;

import android.support.test.runner.AndroidJUnit4;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import junit.framework.Assert;
import org.junit.runner.RunWith;

/**
 * Run the tests with: <code>adb shell am instrument -w
 com.android.multidexlegacyandexception/android.test.InstrumentationTestRunner
</code>
 */
@SuppressWarnings("deprecation")
@RunWith(AndroidJUnit4.class)
@SmallTest
public class TestWithoutActivity extends InstrumentationTestCase {

    @org.junit.Test
    public void testExceptionInMainDex() {
        Assert.assertEquals(10, TestApplication.get(true));
    }

    @org.junit.Test
    public void testExceptionInIntermediate() {
        Assert.assertEquals(11, IntermediateClass.get3(true));
        Assert.assertEquals(11, MiniIntermediateClass.get3(true));
        Assert.assertEquals(11, IntermediateClass.get4(true));
        Assert.assertEquals(1, IntermediateClass.get5(null));
        Assert.assertEquals(10, IntermediateClass.get5(new ExceptionInMainDex()));
        Assert.assertEquals(11, IntermediateClass.get5(new ExceptionInSecondaryDex()));
        Assert.assertEquals(12, IntermediateClass.get5(new ExceptionInMainDex2()));
        Assert.assertEquals(13, IntermediateClass.get5(new ExceptionInSecondaryDex2()));
        Assert.assertEquals(14, IntermediateClass.get5(new OutOfMemoryError()));
        Assert.assertEquals(17, IntermediateClass.get5(new CaughtOnlyException()));
        Assert.assertEquals(39, IntermediateClass.get5(new ExceptionInSecondaryDexWithSuperInMain()));
        Assert.assertEquals(23, IntermediateClass.get5(new SuperExceptionInSecondaryDex()));
        Assert.assertEquals(23, IntermediateClass.get5(new SuperExceptionInMainDex()));
        Assert.assertEquals(23, IntermediateClass.get5(new CaughtOnlyByIntermediateException()));
        Assert.assertEquals(37, IntermediateClass.get5(new ArrayIndexOutOfBoundsException()));
    }

}
