/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.timedetector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.util.ArrayMap;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.IndentingPrintWriter;
import com.android.server.timezonedetector.ArrayMapWithHistory;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.StringWriter;

@RunWith(AndroidJUnit4.class)
public class ArrayMapWithHistoryTest {

    @Test
    public void testValueHistoryBehavior() {
        // Create a map that will retain 2 values per key.
        ArrayMapWithHistory<String, String> historyMap = new ArrayMapWithHistory<>(2 /* history */);
        ArrayMap<String, String> arrayMap = new ArrayMap<>();

        assertEquals(arrayMap.get("K1"), historyMap.get("K1"));
        assertEquals(arrayMap.size(), historyMap.size());

        assertEquals(0, historyMap.getValuesSizeForKeyForTests("K1"));
        assertNotNull(historyMap.toString());
        assertNotNull(dumpHistoryMap(historyMap));

        assertEquals(arrayMap.put("K1", "V1"), historyMap.put("K1", "V1"));
        assertEquals(arrayMap.get("K1"), historyMap.get("K1"));
        assertEquals(arrayMap.size(), historyMap.size());
        assertEquals(arrayMap.keyAt(0), historyMap.keyAt(0));
        assertEquals(arrayMap.valueAt(0), historyMap.valueAt(0));

        assertEquals(1, historyMap.getValuesSizeForKeyForTests("K1"));
        assertNotNull(historyMap.toString());
        assertNotNull(dumpHistoryMap(historyMap));

        // put() a new value for the same key.
        assertEquals(arrayMap.put("K1", "V2"), historyMap.put("K1", "V2"));
        assertEquals(arrayMap.get("K1"), historyMap.get("K1"));
        assertEquals(arrayMap.size(), historyMap.size());
        assertEquals(arrayMap.keyAt(0), historyMap.keyAt(0));
        assertEquals(arrayMap.valueAt(0), historyMap.valueAt(0));
        assertEquals(2, historyMap.getValuesSizeForKeyForTests("K1"));

        assertNotNull(historyMap.toString());
        assertNotNull(dumpHistoryMap(historyMap));

        // put() a new value for the same key. We should have hit the limit of "2 values retained
        // per key".
        assertEquals(arrayMap.put("K1", "V3"), historyMap.put("K1", "V3"));
        assertEquals(arrayMap.get("K1"), historyMap.get("K1"));
        assertEquals(arrayMap.size(), historyMap.size());
        assertEquals(arrayMap.keyAt(0), historyMap.keyAt(0));
        assertEquals(arrayMap.valueAt(0), historyMap.valueAt(0));

        assertEquals(2, historyMap.getValuesSizeForKeyForTests("K1"));
        assertNotNull(historyMap.toString());
        assertNotNull(dumpHistoryMap(historyMap));
    }

    @Test
    public void testMapBehavior() {
        ArrayMapWithHistory<String, String> historyMap = new ArrayMapWithHistory<>(2);
        ArrayMap<String, String> arrayMap = new ArrayMap<>();

        assertEquals(arrayMap.get("K1"), historyMap.get("K1"));
        assertEquals(arrayMap.get("K2"), historyMap.get("K2"));
        assertEquals(arrayMap.size(), historyMap.size());

        assertEquals(0, historyMap.getValuesSizeForKeyForTests("K1"));
        assertEquals(0, historyMap.getValuesSizeForKeyForTests("K2"));

        assertEquals(arrayMap.put("K1", "V1"), historyMap.put("K1", "V1"));
        assertEquals(arrayMap.get("K1"), historyMap.get("K1"));
        assertEquals(arrayMap.get("K2"), historyMap.get("K2"));
        assertEquals(arrayMap.size(), historyMap.size());
        assertEquals(arrayMap.keyAt(0), historyMap.keyAt(0));
        assertEquals(arrayMap.valueAt(0), historyMap.valueAt(0));

        assertEquals(1, historyMap.getValuesSizeForKeyForTests("K1"));
        assertNotNull(historyMap.toString());
        assertNotNull(dumpHistoryMap(historyMap));

        assertEquals(arrayMap.put("K2", "V2"), historyMap.put("K2", "V2"));
        assertEquals(arrayMap.get("K1"), historyMap.get("K1"));
        assertEquals(arrayMap.get("K2"), historyMap.get("K2"));
        assertEquals(arrayMap.size(), historyMap.size());
        assertEquals(arrayMap.keyAt(0), historyMap.keyAt(0));
        assertEquals(arrayMap.valueAt(0), historyMap.valueAt(0));
        assertEquals(arrayMap.keyAt(1), historyMap.keyAt(1));
        assertEquals(arrayMap.valueAt(1), historyMap.valueAt(1));

        assertEquals(1, historyMap.getValuesSizeForKeyForTests("K1"));
        assertEquals(1, historyMap.getValuesSizeForKeyForTests("K2"));
        assertNotNull(historyMap.toString());
        assertNotNull(dumpHistoryMap(historyMap));
    }

    private static String dumpHistoryMap(ArrayMapWithHistory<?, ?> historyMap) {
        StringWriter stringWriter = new StringWriter();
        try (IndentingPrintWriter ipw = new IndentingPrintWriter(stringWriter, " ")) {
            historyMap.dump(ipw);
            return stringWriter.toString();
        }
    }
}
