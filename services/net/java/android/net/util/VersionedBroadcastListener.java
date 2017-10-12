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

package android.net.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * A utility class that runs the provided callback on the provided handler when
 * intents matching the provided filter arrive. Intents received by a stale
 * receiver are safely ignored.
 *
 * Calls to startListening() and stopListening() must happen on the same thread.
 *
 * @hide
 */
public class VersionedBroadcastListener {
    private static final String TAG = VersionedBroadcastListener.class.getSimpleName();
    private static final boolean DBG = false;

    public interface IntentCallback {
        public void run(Intent intent);
    }

    private final Context mContext;
    private final Handler mHandler;
    private final IntentFilter mFilter;
    private final IntentCallback mCallback;
    private final AtomicInteger mGenerationNumber;
    private BroadcastReceiver mReceiver;

    public VersionedBroadcastListener(
            Context ctx, Handler handler, IntentFilter filter, IntentCallback callback) {
        mContext = ctx;
        mHandler = handler;
        mFilter = filter;
        mCallback = callback;
        mGenerationNumber = new AtomicInteger(0);
    }

    public int generationNumber() {
        return mGenerationNumber.get();
    }

    public void startListening() {
        if (DBG) Log.d(TAG, "startListening");
        if (mReceiver != null) return;

        mReceiver = new Receiver(mGenerationNumber.incrementAndGet());
        mContext.registerReceiver(mReceiver, mFilter, null, mHandler);
    }

    public void stopListening() {
        if (DBG) Log.d(TAG, "stopListening");
        if (mReceiver == null) return;

        mGenerationNumber.incrementAndGet();
        mContext.unregisterReceiver(mReceiver);
        mReceiver = null;
    }

    private class Receiver extends BroadcastReceiver {
        // used to verify this receiver is still current
        final public int generationNumber;

        public Receiver(int generationNumber) {
            this.generationNumber = generationNumber;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final int currentGenerationNumber = mGenerationNumber.get();

            if (DBG) {
                Log.d(TAG, "receiver generationNumber=" + this.generationNumber +
                        ", current generationNumber=" + currentGenerationNumber);
            }
            if (this.generationNumber != currentGenerationNumber) return;

            mCallback.run(intent);
        }
    }
}
