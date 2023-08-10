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

import android.security.keystore2.keygen.KeystoreKeyGenerator;
import android.security.keystore2.task.StressTestDeleteKeysTask;
import android.security.keystore2.task.StressTestKeyGenTask;
import android.security.keystore2.task.StressTestOperationTask;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class performs stress testing using multiple threads.
 *
 * This class provides APIs to
 * - generate keys in batches and iterate this task for the given number of times.
 * - perform crypto operations by randomly choosing already generated keys from the set of batch
 * keys.
 * - collect the test performance results
 * - create device report logs
 */

public abstract class StressTestBase {
    private static final String TAG = StressTestBase.class.getSimpleName();

    private <T extends KeystoreKeyGenerator> T createKeystoreKeyGenerator(Class<T> keyGenClass,
            String algorithm, int keySize, int purpose) throws Exception {
        Class[] args = new Class[3];
        args[0] = String.class;
        args[1] = int.class;
        args[2] = int.class;
        return keyGenClass.getDeclaredConstructor(args).newInstance(algorithm, keySize, purpose);
    }

    /**
     * This method performs keystore operations in multiple threads and collects the performance
     * results from each thread.
     */
    protected <T extends KeystoreKeyGenerator> List<StressTestResult> runKeystoreOperations(
            String algorithm, int keySize, int purpose, int batchCount, Class<T> keyGenClass)
            throws Exception {
        AtomicInteger opCounter = new AtomicInteger(0);
        ArrayList<Thread> threads = new ArrayList<>();
        List<StressTestResult> results = Collections.synchronizedList(new ArrayList<>());
        int operationsCount = TestConfigs.getOperationCount();

        for (int i = 0; i < TestConfigs.getThreadCount(); ++i) {
            StressTestOperationTask task = new StressTestOperationTask(batchCount,
                    createKeystoreKeyGenerator(keyGenClass, algorithm, keySize, purpose),
                    opCounter, results);
            Thread thread = new Thread(task);
            threads.add(thread);
        }

        for (int i = 0; i < threads.size(); ++i) {
            threads.get(i).start();
        }
        for (int i = 0; i < threads.size(); ++i) {
            threads.get(i).join();
        }

        return results;
    }

    /**
     * This method performs key generation operation in multiple threads and collects the
     * performance results from each thread.
     */
    protected <T extends KeystoreKeyGenerator> List<StressTestResult> runBatchKeyPairGeneration(
            String algorithm, int keySize, int purpose,
            int batchCount, Class<T> keyGenClass)
            throws Exception {
        AtomicInteger keyCounter = new AtomicInteger(0);
        ArrayList<Thread> threads = new ArrayList<>();
        List<StressTestResult> results = Collections.synchronizedList(new ArrayList<>());
        int batchKeysSize = TestConfigs.getBatchKeysSize();

        for (int i = 0; i < TestConfigs.getThreadCount(); ++i) {
            StressTestKeyGenTask task = new StressTestKeyGenTask(batchCount,
                    createKeystoreKeyGenerator(keyGenClass, algorithm, keySize, purpose),
                    keyCounter, results);
            Thread thread = new Thread(task);
            threads.add(thread);
        }

        for (int i = 0; i < threads.size(); ++i) {
            threads.get(i).start();
        }
        for (int i = 0; i < threads.size(); ++i) {
            threads.get(i).join();
        }

        return results;
    }

    /**
     * This method helps to delete the keys generated in the given batch.
     */
    protected <T extends KeystoreKeyGenerator> void runDeleteBatchKeys(String algorithm,
            int keySize, int purpose,
            int batchCount, Class<T> keyGenClass) throws Exception {
        AtomicInteger keyCounter = new AtomicInteger(0);
        ArrayList<Thread> threads = new ArrayList<>();

        for (int i = 0; i < TestConfigs.getThreadCount(); ++i) {
            StressTestDeleteKeysTask task = new StressTestDeleteKeysTask(batchCount,
                    createKeystoreKeyGenerator(keyGenClass, algorithm, keySize, purpose),
                    keyCounter);
            Thread thread = new Thread(task);
            threads.add(thread);
        }

        for (int i = 0; i < threads.size(); ++i) {
            threads.get(i).start();
        }
        for (int i = 0; i < threads.size(); ++i) {
            threads.get(i).join();
        }
    }

    /**
     * This is the entry method for running the stress test.
     * This method will perform batch keys generation and picks the keys randomly from each set of
     * batch keys and performs keystore operations. Collects performance results for all the
     * operations performed in each batch.
     */
    protected <T extends KeystoreKeyGenerator> void runStressTest(String algorithm,
            int keySize, int purpose, Class<T> keyGenClass) throws Exception {
        List<StressTestResult> stressTestResults = Collections.synchronizedList(
                new ArrayList<>());
        for (int batchCount = 0; batchCount < TestConfigs.getIterations(); batchCount++) {
            stressTestResults.addAll(
                    runBatchKeyPairGeneration(algorithm, keySize, purpose, batchCount,
                            keyGenClass));
            Log.d(TAG, "Completed " + batchCount + " batch keys generation");
            stressTestResults.addAll(
                    runKeystoreOperations(algorithm, keySize, purpose, batchCount, keyGenClass));
            Log.d(TAG, "Completed " + batchCount + " batch keys operations");

            if (TestConfigs.getDeleteBatchKeys()) {
                runDeleteBatchKeys(algorithm, keySize, purpose, batchCount, keyGenClass);
                Log.d(TAG, "Deleted " + batchCount + " batch keys.");
            }

            StressTestReportLog.logCumulativePerformance(stressTestResults,
                    algorithm + "/" + keySize);
        }

        StressTestReportLog.logCumulativePerformance(stressTestResults, algorithm + "/" + keySize);
    }
}
