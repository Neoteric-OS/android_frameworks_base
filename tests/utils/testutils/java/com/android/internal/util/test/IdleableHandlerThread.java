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

import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.MessageQueue;
import android.os.MessageQueue.IdleHandler;

/**
 * A subclass of HandlerThread that allows callers to wait for it to become idle. waitForIdle
 * will return immediately if the handler is already idle. This allows writing realistic tests of
 * asynchronous handlers without slowing the test down with sleep statements.
 */
public class IdleableHandlerThread extends HandlerThread {
    private IdleHandler mIdleHandler;

    public IdleableHandlerThread(String name) {
        super(name);
    }

    public void waitForIdle(int timeoutMs) {
        final ConditionVariable cv = new ConditionVariable();
        final MessageQueue queue = getLooper().getQueue();

        synchronized (queue) {
            if (queue.isIdle()) {
                return;
            }

            if (mIdleHandler != null) {
                throw new IllegalStateException("BUG: only one idle handler allowed");
            }

            mIdleHandler = new IdleHandler() {
                public boolean queueIdle() {
                    synchronized (queue) {
                        cv.open();
                        mIdleHandler = null;
                        return false;  // Remove the handler.
                    }
                }
            };
            queue.addIdleHandler(mIdleHandler);
        }

        if (!cv.block(timeoutMs)) {
            synchronized (queue) {
                queue.removeIdleHandler(mIdleHandler);
            }
            throw new IllegalStateException("HandlerThread " + getName() +
                    " did not become idle after " + timeoutMs + " ms");
        }
    }
}
