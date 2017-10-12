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

package com.android.server.connectivity.tethering;

import static android.telephony.CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * A utility class that runs the provided callback on the provided handler when
 * observing a changes in carrier configuration.
 *
 * @hide
 */
public class CarrierConfigChangeListener {
    private static final String TAG = CarrierConfigChangeListener.class.getSimpleName();
    private static final boolean DBG = false;

    private final Context mContext;
    private final Handler mHandler;
    private final AtomicInteger mGenerationNumber;
    private final Runnable mCallback;
    private BroadcastReceiver mReceiver;

    public CarrierConfigChangeListener(Context ctx, Handler handler, Runnable callback) {
        mContext = ctx;
        mHandler = handler;
        mCallback = callback;
        mGenerationNumber = new AtomicInteger(0);
    }

    public int generationNumber() {
        return mGenerationNumber.get();
    }

    public void startListening() {
        if (DBG) Log.d(TAG, "startListening for carrier configuration changes");

        if (mReceiver != null) return;

        mReceiver = new CarrierConfigChangeReceiver(mGenerationNumber.incrementAndGet());
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_CARRIER_CONFIG_CHANGED);

        mContext.registerReceiver(mReceiver, filter, null, mHandler);
    }

    public void stopListening() {
        if (DBG) Log.d(TAG, "stopListening for carrier configuration changes");

        if (mReceiver == null) return;

        mGenerationNumber.incrementAndGet();
        mContext.unregisterReceiver(mReceiver);
        mReceiver = null;
    }

    private class CarrierConfigChangeReceiver extends BroadcastReceiver {
        // used to verify this receiver is still current
        final public int generationNumber;

        public CarrierConfigChangeReceiver(int generationNumber) {
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

            mCallback.run();
        }
    }
}
