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

import static android.security.keystore2.measurable.MeasurableFactory.MeasurableType.RSA_DECRYPT;

import android.os.SystemClock;
import android.security.keystore2.StressTestResult;
import android.security.keystore2.TestConfigs;
import android.security.keystore2.keygen.KeystoreKeyGenerator;
import android.security.keystore2.measurable.Measurable;
import android.security.keystore2.measurable.MeasurableFactory;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This runnable is responsible for picking the keys randomly in the given set of batch keys
 * and performing the keystore operations. Collects performance results for the keys generated in
 * this task thread.
 */
public class StressTestOperationTask implements Runnable {

    private static final String TAG = StressTestOperationTask.class.getSimpleName();
    private final int mBatchKeysSize;

    private final int mBatchCount;
    private final int mOperationsCount;
    private final List<StressTestResult> mResults;
    KeystoreKeyGenerator mKeystoreKeyGenerator;
    AtomicInteger mOpCounter;

    MeasurableFactory mMeasurableFactory;

    public StressTestOperationTask(int batchCount, KeystoreKeyGenerator keyGenerator,
            AtomicInteger opCounter,
            List<StressTestResult> results) {
        mResults = results;
        mKeystoreKeyGenerator = keyGenerator;
        mOpCounter = opCounter;
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
            int count = mOpCounter.incrementAndGet();
            while (count <= mOperationsCount) {
                int randomKeyIndex = ThreadLocalRandom.current().nextInt(
                        (mBatchCount * mBatchKeysSize) + 1,
                        (mBatchCount * mBatchKeysSize) + mBatchKeysSize);
                String alias = KeystoreKeyGenerator.ALIAS_PREFIX + randomKeyIndex;
                Measurable measurable = mMeasurableFactory.createMeasurable(RSA_DECRYPT,
                        mKeystoreKeyGenerator);
                measurable.initialSetUp(alias);
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
                count = mOpCounter.incrementAndGet();
            }
            mResults.add(result);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Random key operation failed: count: " + mOpCounter.get() + " Batch: "
                            + mBatchCount + " Thread id: " + Thread.currentThread().getId(), e);
        }
    }

    private long now() {
        return SystemClock.elapsedRealtimeNanos();
    }
}
