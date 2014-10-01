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

/**
 * Tests for runtime resource overlay of various resource types.
 *
 * These tests require the file system be prepared before execution: use
 * frameworks/base/core/tests/rro_tests/run.sh for this.
 */
public class TypeTests extends BaseTestCase {
    public void testBool() throws Throwable {
        assertResource(R.bool.b, new boolean[]{true, false, true});
    }

    public void testInteger() throws Throwable {
        assertResource(R.integer.i, new int[]{0, 1, 2});
    }

    public void testString() throws Throwable {
        assertResource(R.string.s, new String[]{"a", "b", "c"});
    }

    public void testIntegerArray() throws Throwable {
        assertResource(R.array.i, new int[][]{ {1, 2, 3}, {4, 5}, {6, 7, 8, 9} });
    }

    public void testStringBasedOnLocale() throws Throwable {
        assertResource(R.string.s, new String[]{"a", "b", "c"});

        setSwedishLocale();
        assertResource(R.string.s, new String[]{"A", "B", "C"});
    }

    public void testSystemResource() throws Throwable {
        assertResource(com.android.internal.R.bool.config_annoy_dianne,
                new boolean[]{true, false, true});

        setSwedishLocale();
        assertResource(com.android.internal.R.bool.config_annoy_dianne,
                new boolean[]{true, false, true});
    }
}
