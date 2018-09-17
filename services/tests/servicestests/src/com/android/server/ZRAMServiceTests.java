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

import android.content.Context;
import android.test.AndroidTestCase;
import android.test.ComparisonFailure;
import android.test.suitebuilder.annotation.LargeTest;
import android.os.ServiceManager;
import android.util.Log;
import android.os.IZRAMService;

import com.android.frameworks.servicestests.R;

import java.io.File;
import java.io.InputStream;

public class ZRAMServiceTests extends AndroidTestCase {
    private static final String TAG = "ZRAMServiceTests";
    private IZRAMService mZRAMService;

    @Override
    protected void setUp() throws Exception {
        mZRAMService = IZRAMService.Stub.asInterface(
                ServiceManager.getService("zram"));
    }
    @LargeTest
    public void test1() {
        assertTrue("zramservice available", mZRAMService != null);
        try {
            int num = mZRAMService.hotAdd(1<<20);
            fail("ZRAMService did not throw SecurityException as expected");
        } catch (SecurityException e) {
            // expected
        } catch (Exception e) {
            fail(e.toString());
        }
    }
}
