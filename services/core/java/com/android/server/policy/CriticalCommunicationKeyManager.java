/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.policy;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Parcel;
import android.os.UserHandle;
import android.util.Log;
import android.view.KeyEvent;

final class CriticalCommunicationKeyManager {
    private final static String TAG = "CriticalCommunicationKeyManager";
    private final static boolean DEBUG = false;

    private final Context mContext;
    private boolean mEmergencyMode;
    private CriticalBroadcastReceiver mReceiver;

    CriticalCommunicationKeyManager(Context context) {
        mContext = context;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CRITICAL_COMMUNICATION_APP_SOS);
        filter.addAction(Intent.ACTION_CRITICAL_COMMUNICATION_APP_SOS_CANCEL);
        CriticalBroadcastReceiver mReceiver = new CriticalBroadcastReceiver();
        context.registerReceiver(mReceiver, filter,
                Manifest.permission.CRITICAL_COMMUNICATION, null, 0);
    }

    /**
     * Broadcasts an intent if the keycode is critical communication key
     *
     * @param context context used to broadcast the event
     * @param keyCode keyCode which triggered this function
     * @param event keyEvent which trigged this function
     * @return {@code true} if this was handled
     */
    boolean handleCriticalCommunicationKey(int keyCode, KeyEvent event) {
        /* clone event object to avoid overwriting content by next key event */
        Parcel p = Parcel.obtain();
        event.writeToParcel(p, 0);
        p.setDataPosition(0);
        KeyEvent clonedEvent = event.CREATOR.createFromParcel(p);

        Intent intent = new Intent()
                .setFlags(Intent.FLAG_RECEIVER_FOREGROUND
                        | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND
                        | Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtra(Intent.EXTRA_KEY_EVENT, clonedEvent);

        switch (keyCode) {
            case KeyEvent.KEYCODE_CRITICAL_COMMUNICATION_CONTROL:
                intent.setAction(Intent.ACTION_CRITICAL_COMMUNICATION_CONTROL_KEY);
                break;
            case KeyEvent.KEYCODE_CRITICAL_COMMUNICATION_SOS:
                intent.setAction((mEmergencyMode) ?
                        Intent.ACTION_CRITICAL_COMMUNICATION_SOS_CANCEL :
                        Intent.ACTION_CRITICAL_COMMUNICATION_SOS_KEY);
                break;
            default:
                return false;
        }

        if (DEBUG) Log.d(TAG, "critical intent: action = " + intent.getAction() + ", event = " + intent);
        mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT,
                Manifest.permission.CRITICAL_COMMUNICATION);
        return true;
    }

    /**
      * Handle intents from critical applications to enable or disable emergency mode
      */

    class CriticalBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_CRITICAL_COMMUNICATION_APP_SOS)) {
                mEmergencyMode = true;
            }
            else if (intent.getAction().equals(Intent.ACTION_CRITICAL_COMMUNICATION_APP_SOS_CANCEL)) {
                mEmergencyMode = false;
            }
            if (DEBUG) Log.d(TAG, "emergency mode = " + mEmergencyMode);
        }
    }

}
