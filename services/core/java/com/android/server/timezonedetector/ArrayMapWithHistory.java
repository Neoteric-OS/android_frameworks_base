/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.server.timezonedetector;

import android.annotation.Nullable;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;

import java.util.LinkedList;
import java.util.Map;

/**
 * A partial decorator for {@link ArrayMap} that records historic values for each mapping for
 * debugging later with {@link #dump(IndentingPrintWriter)}.
 *
 * <p>This class is only intended for use in {@link TimeZoneDetectorStrategy} and
 * {@link com.android.server.timedetector.TimeDetectorStrategy} so only provides the parts of the
 * {@link ArrayMap} API needed. List {@link ArrayMap} it is not thread-safe.
 *
 * @param <K> the type of the key
 * @param <V> the type of the value
 */
public class ArrayMapWithHistory<K, V> {
    private static final String TAG = "ArrayMapWithHistory";

    /** The size the linked list against each value is allowed to grow to. */
    private final int mHistorySize;
    private final ArrayMap<K, LinkedList<V>> mMap = new ArrayMap<>();

    /**
     * Creates an instance that records, at most, the specified number of values against each key.
     */
    public ArrayMapWithHistory(int history) {
        if (history < 1) {
            throw new IllegalArgumentException("history ==" + history);
        }
        mHistorySize = history;
    }

    /**
     * See {@link ArrayMap#put(K, V)}.
     */
    public V put(K key, V value) {
        V previous;
        LinkedList<V> values = mMap.get(key);
        if (values == null) {
            values = new LinkedList<>();
            mMap.put(key, values);
            previous = null;
        } else if (values.isEmpty()) {
            Log.w(TAG, "LinkedList for \"" + key + "\" was unexpectedly empty");
            previous = null;
        } else {
            previous = values.getFirst();
        }

        values.addFirst(value);
        if (values.size() > mHistorySize) {
            values.removeLast();
        }
        return previous;
    }

    /**
     * See {@link ArrayMap#get(Object)}.
     */
    public V get(Object key) {
        LinkedList<V> values = mMap.get(key);
        if (values == null) {
            return null;
        } else if (values.isEmpty()) {
            Log.w(TAG, "LinkedList for \"" + key + "\" was unexpectedly empty");
            return null;
        }
        return values.getFirst();
    }

    /**
     * See {@link ArrayMap#size()}.
     */
    public int size() {
        return mMap.size();
    }

    /**
     * See {@link ArrayMap#keyAt(int)}.
     */
    public K keyAt(int index) {
        return mMap.keyAt(index);
    }

    /**
     * See {@link ArrayMap#valueAt(int)}.
     */
    public V valueAt(int index) {
        LinkedList<V> values = mMap.valueAt(index);
        if (values == null || values.isEmpty()) {
            Log.w(TAG, "valueAt(" + index + ") was unexpectedly null or empty");
            return null;
        }
        return values.getFirst();
    }

    /**
     * Dumps the content of the map, including history values, using the supplied writer.
     */
    public void dump(IndentingPrintWriter ipw) {
        for (Map.Entry<K, LinkedList<V>> entry : mMap.entrySet()) {
            ipw.println(entry.getKey());

            ipw.increaseIndent();
            for (V value : entry.getValue()) {
                ipw.println(value);
            }
            ipw.decreaseIndent();
        }
        ipw.flush();
    }

    /**
     * Internal method intended for tests that returns the length of the linked list associated with
     * the supplied key. If there is no mapping for the key then a {@code 0} is returned.
     */
    @VisibleForTesting
    @Nullable
    public int getValuesSizeForKeyForTests(K key) {
        LinkedList<V> values = mMap.get(key);
        if (values == null) {
            return 0;
        } else if (values.isEmpty()) {
            Log.w(TAG, "getValuesSizeForKeyForTests(\"" + key + "\") was unexpectedly empty");
            return 0;
        } else {
            return values.size();
        }
    }

    @Override
    public String toString() {
        return "HistoryMap{"
                + "mHistorySize=" + mHistorySize
                + ", mMap=" + mMap
                + '}';
    }
}
