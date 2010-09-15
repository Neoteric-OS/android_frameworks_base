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

package android.core;

import junit.framework.Assert;
import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.test.AndroidTestCase;
import android.util.Log;
import java.io.File;

public class SharedPreferencesTest extends AndroidTestCase {

    public void testCreateAndReadSharedPreferences() {
        // Creates a shared preference file, and adds a key to it.
        Editor editor = mContext.getSharedPreferences("testPref", Context.MODE_PRIVATE).edit();
        editor.putString("test", "Value");
        editor.commit();

        // Making sure it's possible to read the value of the key
        assertEquals("Value", mContext.getSharedPreferences("testPref",
                Context.MODE_PRIVATE).getString("test", "Default"));
    }

    public void testDeleteAndReadSharedPreferences() {
        // Creates a shared preference file, and adds a key to it.
        Editor editor = mContext.getSharedPreferences("testPref", Context.MODE_PRIVATE).edit();
        editor.putString("test", "Value");
        editor.commit();

        File f = new File("/data/data/" + mContext.getPackageName() +
                "/shared_prefs", "testPref.xml");

        // Deleting the preference file
        assertTrue(f.delete());

        // Making sure it's not possible to read the values of the key
        // after the preference file has been deleted
        if (mContext.getSharedPreferences("testPref",
                Context.MODE_PRIVATE).getString("test", "Default") == "Value") {
            fail("It should not be possible to read the value " +
                    "after the shared preference file has been deleted");
        }
    }
}
