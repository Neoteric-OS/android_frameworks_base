/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.security.keystore2;

import android.content.Context;
import android.security.tests.R;

import androidx.test.InstrumentationRegistry;

public class TestConfigs {
    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    public static int getBatchKeysSize() {
        return getContext().getResources().getInteger(R.integer.batch_key_size);
    }

    public static int getIterations() {
        return getContext().getResources().getInteger(R.integer.iterations);
    }

    public static int getOperationCount() {
        return getContext().getResources().getInteger(R.integer.operation_count);
    }

    public static int getThreadCount() {
        return getContext().getResources().getInteger(R.integer.thread_count);
    }

    public static boolean getDeleteKeys() {
        return getContext().getResources().getBoolean(R.bool.delete_key);
    }

    public static boolean getDeleteBatchKeys() {
        return getContext().getResources().getBoolean(R.bool.delete_batch_keys);
    }
}
