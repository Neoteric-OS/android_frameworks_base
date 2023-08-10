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

package android.security.keystore2.task;

import android.os.SystemClock;
import android.security.keystore2.TestConfigs;
import android.security.keystore2.keygen.KeystoreKeyGenerator;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * This runnable deletes all the keys from the given set of batch keys.
 */
public class StressTestDeleteKeysTask implements Runnable {

    private static final String TAG = StressTestDeleteKeysTask.class.getSimpleName();
    private final int mBatchKeysSize;
    private final int mBatchCount;
    KeystoreKeyGenerator mKeystoreKeyGenerator;
    AtomicInteger mKeyCounter;

    public StressTestDeleteKeysTask(int batchCount, KeystoreKeyGenerator keyGenerator,
            AtomicInteger keyCounter) {
        mKeystoreKeyGenerator = keyGenerator;
        mKeyCounter = keyCounter;
        mBatchCount = batchCount;
        mBatchKeysSize = TestConfigs.getBatchKeysSize();
    }

    @Override
    public void run() {
        try {
            int count = mKeyCounter.incrementAndGet();
            while (count <= mBatchKeysSize) {
                String alias = KeystoreKeyGenerator.ALIAS_PREFIX + ((mBatchCount * mBatchKeysSize)
                        + count);
                mKeystoreKeyGenerator.deleteKey(alias);
                count = mKeyCounter.incrementAndGet();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Delete batch keys failed: count: " + mKeyCounter.get() + " Batch: "
                            + mBatchCount + " Thread id: " + Thread.currentThread().getId(), e);
        }
    }

    private long now() {
        return SystemClock.elapsedRealtimeNanos();
    }
}
