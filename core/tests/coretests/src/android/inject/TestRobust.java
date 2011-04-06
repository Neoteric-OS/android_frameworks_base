/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.inject;

import android.inject.DuctTape;

import junit.framework.TestCase;

/**
 * This class contains tests that make sure that the robustness of the object
 * mesh is always reported back in the correct manner.
 */
public class TestRobust extends TestCase {

    /**
     * Make sure that the need for taping is reported back correctly when the
     * setup is altered.
     */
    public void testApplyNeed() {
        DuctTape dt = new DuctTape();

        assertTrue(dt.isRobust());

        // Make dirty
        dt.add(Object.class);
        assertTrue(!dt.isRobust());

        // Process setup
        dt.apply();
        assertTrue(dt.isRobust());
    }

}
