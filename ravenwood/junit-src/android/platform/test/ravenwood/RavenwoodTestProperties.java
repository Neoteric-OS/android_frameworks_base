/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.platform.test.ravenwood;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A class to store system properties defined by tests.
 */
public class RavenwoodTestProperties {
    private final boolean mIsImmutable;

    private final Map<String, String> mValues;

    /** Set of additional keys that should be considered readable */
    private final Set<String> mKeyReadable;

    /** Set of additional keys that should be considered writable */
    private final Set<String> mKeyWritable;

    public RavenwoodTestProperties() {
        mValues = new HashMap<>();
        mKeyReadable = new HashSet<>();
        mKeyWritable = new HashSet<>();
        mIsImmutable = false;
    }

    /** Copy constructor */
    public RavenwoodTestProperties(RavenwoodTestProperties source, boolean immutable) {
        if (immutable) {
            mValues = Map.copyOf(source.mValues);
            mKeyReadable = Set.copyOf(source.mKeyReadable);
            mKeyWritable = Set.copyOf(source.mKeyWritable);
        } else {
            mValues = new HashMap<>(source.mValues);
            mKeyReadable = new HashSet<>(source.mKeyReadable);
            mKeyWritable = new HashSet<>(source.mKeyWritable);
        }
        mIsImmutable = immutable;
    }

    public Map<String, String> getValues() {
        return Map.copyOf(mValues);
    }

    public boolean isKeyReadable(String key) {
        return mKeyReadable.contains(key);
    }

    public boolean isKeyWritable(String key) {
        return mKeyWritable.contains(key);
    }

    private void ensureNotImmutable() {
        if (mIsImmutable) {
            throw new RuntimeException("Unable to update immutable instance");
        }
    }

    public void setValue(String key, Object value) {
        ensureNotImmutable();

        final String valueString = (value == null) ? null : String.valueOf(value);
        if ((valueString == null) || valueString.isEmpty()) {
            mValues.remove(key);
        } else {
            mValues.put(key, valueString);
        }
    }

    public void setAccessNone(String key) {
        ensureNotImmutable();
        mKeyReadable.remove(key);
        mKeyWritable.remove(key);
    }

    public void setAccessReadOnly(String key) {
        ensureNotImmutable();
        mKeyReadable.add(key);
        mKeyWritable.remove(key);
    }

    public void setAccessReadWrite(String key) {
        ensureNotImmutable();
        mKeyReadable.add(key);
        mKeyWritable.add(key);
    }
}
