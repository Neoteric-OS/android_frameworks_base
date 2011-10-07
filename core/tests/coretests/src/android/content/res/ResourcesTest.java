/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.content.res;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.content.res.Configuration;
import android.content.res.Resources;
import com.android.frameworks.coretests.R;

public class ResourcesTest extends AndroidTestCase {
    private Resources mResources;
    private Configuration mStartConfig;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResources = mContext.getResources();
        mStartConfig = mResources.getConfiguration();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mResources.updateConfiguration(mStartConfig, mResources.getDisplayMetrics());
    }

    @SmallTest
    public void testMncZero() {
        Configuration config = mResources.getConfiguration();
        config.mnc = Configuration.MNC_ZERO;
        mResources.updateConfiguration(config, mResources.getDisplayMetrics());

        Configuration configAfter = mResources.getConfiguration();
        assertEquals("Unable to set MNC zero configuration",
                Configuration.MNC_ZERO, configAfter.mnc);

        String text = mResources.getString(R.string.mnc_string);
        assertEquals("Got wrong resource for mnc00", "mnc00", text);
    }

    @SmallTest
    public void testMncDefault() {
        Configuration config = mResources.getConfiguration();
        config.mnc = 0;
        mResources.updateConfiguration(config, mResources.getDisplayMetrics());

        Configuration configAfter = mResources.getConfiguration();
        assertEquals("Unable to set default mnc configuration", 0, configAfter.mnc);

        String text = mResources.getString(R.string.mnc_string);
        assertEquals("Got wrong resource for default mnc", "mncDefault", text);
    }

    @SmallTest
    public void testMnc01() {
        Configuration config = mResources.getConfiguration();
        config.mnc = 1;
        mResources.updateConfiguration(config, mResources.getDisplayMetrics());

        Configuration configAfter = mResources.getConfiguration();
        assertEquals("Unable to set mnc01 configuration", 1, configAfter.mnc);

        String text = mResources.getString(R.string.mnc_string);
        assertEquals("Got wrong resource for mnc01", "mnc01", text);
    }
}

