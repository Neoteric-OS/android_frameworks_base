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

import android.content.res.Configuration;
import android.content.res.Resources;
import android.test.AndroidTestCase;

import java.util.Locale;

public abstract class BaseTestCase extends AndroidTestCase {
    static int sSetup = 0;

    protected Resources mResources;

    private void setLocale(Locale locale) {
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        mResources.updateConfiguration(config, mResources.getDisplayMetrics());
    }

    protected void setEnglishLocale() {
        setLocale(new Locale("en", "US"));
    }

    protected void setSwedishLocale() {
        setLocale(new Locale("sv", "SE"));
    }

    protected void assertResource(int resid, int[] expected) {
        assertEquals(expected[sSetup], mResources.getInteger(resid));
    }

    protected void assertResource(int resid, String[] expected) {
        assertEquals(expected[sSetup], mResources.getString(resid));
    }

    protected void assertResource(int resid, boolean[] expected) {
        assertEquals(expected[sSetup], mResources.getBoolean(resid));
    }

    protected void assertResource(int resid, int[][] expected) {
        int[] a = expected[sSetup];
        int[] b = mResources.getIntArray(resid);
        assertEquals(a.length, b.length);
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i]);
        }
    }

    protected void setUp() {
        mResources = getContext().getResources();
        setEnglishLocale();
    }
}
