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

import static android.security.keystore2.measurable.MeasurableFactory.MeasurableType.RSA_KEYGEN;

import android.os.SystemClock;
import android.security.keystore2.StressTestResult;
import android.security.keystore2.TestConfigs;
import android.security.keystore2.keygen.KeystoreKeyGenerator;
import android.security.keystore2.measurable.Measurable;
import android.security.keystore2.measurable.MeasurableFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This runnable responsible generating keys and collecting the performance results for the keys
 * generated in this task thread.
 */
public class StressTestKeyGenTask implements Runnable {

    private static final String TAG = StressTestKeyGenTask.class.getSimpleName();
    private final int mBatchKeysSize;
    private final int mBatchCount;
    private final int mOperationsCount;
    private final List<StressTestResult> mResults;
    KeystoreKeyGenerator mKeystoreKeyGenerator;
    AtomicInteger mKeyCounter;
    MeasurableFactory mMeasurableFactory;

    public StressTestKeyGenTask(int batchCount, KeystoreKeyGenerator keyGenerator,
            AtomicInteger keyCounter,
            List<StressTestResult> results) {
        mResults = results;
        mKeystoreKeyGenerator = keyGenerator;
        mKeyCounter = keyCounter;
        mBatchCount = batchCount;
        mOperationsCount = TestConfigs.getOperationCount();
        mBatchKeysSize = TestConfigs.getBatchKeysSize();
        mMeasurableFactory = new MeasurableFactory();
    }

    @Override
    public void run() {
        try {
            StressTestResult result = new StressTestResult();
            result.setThreadId(Thread.currentThread().getId());

            int count = mKeyCounter.incrementAndGet();
            while (count <= mBatchKeysSize) {
                String alias = KeystoreKeyGenerator.ALIAS_PREFIX + ((mBatchCount * mBatchKeysSize)
                        + count);
                Measurable measurable = mMeasurableFactory.createMeasurable(RSA_KEYGEN,
                        mKeystoreKeyGenerator);
                measurable.getGenerator().initialize(alias);

                long setupBegin = now();
                measurable.setUp();
                result.addSetupTime(now() - setupBegin);

                long runBegin = now();
                measurable.measure();
                result.addMeasurement(now() - runBegin);

                long tearDownBegin = now();
                measurable.tearDown(alias);
                result.addTeardownTime(now() - tearDownBegin);
                count = mKeyCounter.incrementAndGet();
            }
            mResults.add(result);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Key-Pair generation failed: count: " + mKeyCounter.get() + " Batch: "
                            + mBatchCount + " Thread id: " + Thread.currentThread().getId(), e);
        }
    }

    private long now() {
        return SystemClock.elapsedRealtimeNanos();
    }
}
