/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.internal.util.test;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

public class IdleableHandlerThreadTest extends AndroidTestCase {

    private static final int TIMEOUT_MS = 200;
    private TestHandler mHandler;

    @Override
    public void setUp() throws Exception {
        IdleableHandlerThread t = new IdleableHandlerThread("IdleableHandlerThreadTest");
        t.start();
        mHandler = new TestHandler(t);
    }

    @Override
    public void tearDown() throws Exception {
        mHandler.stop();
    }

    public class TestHandler extends Handler {

        private volatile int mLastMessage;
        private IdleableHandlerThread mHandlerThread;

        public TestHandler(IdleableHandlerThread thread) {
            super(thread.getLooper());
            mHandlerThread = thread;
        }

        @Override
        public void handleMessage(Message msg) {
            mLastMessage = msg.what;
        }

        public int getLastMessage() {
            return mLastMessage;
        }

        public void waitForIdle(int timeout) {
            mHandlerThread.waitForIdle(timeout);
        }

        public void stop() {
            mHandlerThread.quitSafely();
        }
    }

    @SmallTest
    public void testWaitingForIdlePreventsRaceConditions() {
        // Causes the test to take about 7s on bullhead-eng.
        final int attempts = 5000;

        // Tests that waitForIdle returns immediately if the service is already idle.
        for (int i = 0; i < attempts; i++) {
            mHandler.waitForIdle(TIMEOUT_MS);
        }

        for (int i = 0; i < attempts; i++) {
            mHandler.sendEmptyMessage(i);
            mHandler.waitForIdle(TIMEOUT_MS);
            assertEquals(i, mHandler.getLastMessage());
        }

        for (int i = 0; i < attempts; i += 3) {
            mHandler.sendEmptyMessage(i);
            mHandler.sendEmptyMessage(i + 1);
            mHandler.sendEmptyMessage(i + 2);
            mHandler.waitForIdle(TIMEOUT_MS);
            assertEquals(i + 2, mHandler.getLastMessage());
        }
    }

    @SmallTest
    public void testNotWaitingForIdleCausesRaceConditions() {
        // This number can be set as high as we want without increasing test runtime because we
        // typically see a race condition immediately.
        final int attempts = 5000;

        // Ensure that not calling waitForIdle causes a race condition.
        for (int i = 0; i < attempts; i++) {
            mHandler.sendEmptyMessage(i);
            if (i != mHandler.getLastMessage()) {
                // We hit a race condition, as expected. Pass the test.
                return;
            }
        }

        // No race? There is a bug in this test.
        fail("expected race condition at least once in " + attempts + " attempts");
    }
}
