/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net;

import static com.android.testutils.MiscAsserts.assertThrows;
import static com.android.testutils.ParcelUtils.assertParcelSane;

import static junit.framework.Assert.assertFalse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import androidx.test.filters.SmallTest;

import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@IgnoreUpTo(Build.VERSION_CODES.R)
@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
public class OemNetworkPreferencesTest {

    private static final int TEST_PREF = OemNetworkPreferences.OEM_NETWORK_PREFERENCE_DEFAULT;
    private static final String TEST_PACKAGE = "com.google.apps.contacts";

    private final List<String> mPackages = new ArrayList<>();
    private final OemNetworkPreferences.Builder mBuilder = new OemNetworkPreferences.Builder();

    @Before
    public void beforeEachTestMethod() {
        mPackages.add(TEST_PACKAGE);
    }

    @Test
    public void testBuilderAddNetworkPreferenceRequiresNonNullPackages() {
        assertThrows(NullPointerException.class,
                () -> mBuilder.addNetworkPreference(TEST_PREF, null));
    }

    @Test
    public void testGetNetworkPreferencesReturnsCorrectValue() {
        final int expectedNumberOfMappings = 1;
        mBuilder.addNetworkPreference(TEST_PREF, mPackages);

        final Map<String, Integer> networkPreferences =
                mBuilder.build().getNetworkPreferences();

        assertEquals(expectedNumberOfMappings, networkPreferences.size());
        assertTrue(networkPreferences.containsKey(mPackages.get(0)));
    }

    @Test
    public void testGetNetworkPreferencesReturnsUnmodifiableValue() {
        final String newPackage = "new.com.google.apps.contacts";
        mBuilder.addNetworkPreference(TEST_PREF, mPackages);

        final Map<String, Integer> networkPreferences =
                mBuilder.build().getNetworkPreferences();

        assertThrows(UnsupportedOperationException.class,
                () -> networkPreferences.put(newPackage, TEST_PREF));

        assertThrows(UnsupportedOperationException.class,
                () -> networkPreferences.remove(TEST_PACKAGE));

    }

    @Test
    public void testToStringReturnsCorrectValue() {
        mBuilder.addNetworkPreference(TEST_PREF, mPackages);

        final String networkPreferencesString = mBuilder.build().getNetworkPreferences().toString();

        assertTrue(networkPreferencesString.contains(Integer.toString(TEST_PREF)));
        assertTrue(networkPreferencesString.contains(TEST_PACKAGE));
    }

    @Test
    public void testOemNetworkPreferencesParcelable() {
        mBuilder.addNetworkPreference(TEST_PREF, mPackages);

        final OemNetworkPreferences prefs = mBuilder.build();

        assertParcelSane(prefs, 1 /* fieldCount */);
    }

    @Test
    public void testOemNetworkPreferencesOverwritesPriorPreferences() {
        mBuilder.addNetworkPreference(TEST_PREF, mPackages);
        Map<String, Integer> networkPreferences =
                mBuilder.build().getNetworkPreferences();

        assertTrue(networkPreferences.containsKey(TEST_PACKAGE));

        final String newPackage = "new.com.google.apps.contacts";
        mPackages.clear();
        mPackages.add(newPackage);
        mBuilder.addNetworkPreference(TEST_PREF, mPackages);
        networkPreferences = mBuilder.build().getNetworkPreferences();

        assertTrue(networkPreferences.containsKey(newPackage));
        assertFalse(networkPreferences.containsKey(TEST_PACKAGE));
        assertEquals(networkPreferences.size(), mPackages.size());
    }
}
