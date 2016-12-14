/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.carrierdefaultapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.android.internal.telephony.TelephonyIntents;

public class CarrierDefaultBroadcastReceiver extends BroadcastReceiver{
    private static final String TAG = "CarrierDefaultApp";

    @Override
    public void onReceive(Context context, Intent intent) {
        logd("onReceive intent: " + intent.getAction());
        switch (intent.getAction()) {
            case TelephonyIntents.ACTION_CARRIER_SIGNAL_REDIRECTED:
                // TODO carrier customization, loading configured carrier actions
                CarrierActionUtils.onShowCaptivePortalNotification(intent, context);
                CarrierActionUtils.onDisableAllMeteredApns(intent, context);
                break;
            default:
        }
    }
    private static void logd(String s) {
        Log.d(TAG, s);
    }
}
