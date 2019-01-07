/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.server;

import android.os.IAndroidOnTapService;
import android.os.ServiceManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;

public class AndroidOnTapServiceTest extends AndroidTestCase {
    private static final String TAG = "AndroidOnTapServiceTests";
    private IAndroidOnTapService mAndroidOnTapService;

    @Override
    protected void setUp() throws Exception {
        mAndroidOnTapService =
                IAndroidOnTapService.Stub.asInterface(ServiceManager.getService("android_on_tap"));
    }

    @LargeTest
    public void test1() {
        assertTrue("android_on_tap service available", mAndroidOnTapService != null);
        try {
            int num = mAndroidOnTapService.start(1 << 20);
            fail("AndroidOnTapService did not throw SecurityException as expected");
        } catch (SecurityException e) {
            // expected
        } catch (Exception e) {
            fail(e.toString());
        }
    }
}
