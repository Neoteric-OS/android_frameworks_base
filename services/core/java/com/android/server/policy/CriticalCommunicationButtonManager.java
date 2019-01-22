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

import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.UserHandle;
import android.util.Log;
import android.view.KeyEvent;

final class CriticalCommunicationButtonManager {
    private static String sTAG = "CriticalCommunicationButtonManager";
    private static boolean sDEBUG = true;

    private Context mContext;

    CriticalCommunicationButtonManager(Context context) {
        mContext = context;
    }

    /**
     * Broadcasts an intent if the keycode is critical communication button
     *
     * @param context context used to broadcast the event
     * @param keyCode keyCode which triggered this function
     * @param event keyEvent which trigged this function
     * @return {@code true} if this was handled
     */
    boolean handleCriticalCommunicationButton(int keyCode, KeyEvent event) {
        String action;
        switch (keyCode) {
            case KeyEvent.KEYCODE_CRITICAL_COMMUNICATION_CONTROL_BUTTON:
                action = Intent.ACTION_CRITICAL_COMMUNICATION_CONTROL_BUTTON;
                break;
            case KeyEvent.KEYCODE_CRITICAL_COMMUNICATION_SOS_BUTTON:
                action = Intent.ACTION_CRITICAL_COMMUNICATION_SOS_BUTTON;
                break;
            default:
                return false;
        }

        /* clone event object to avoid overwriting content by next event */
        Parcel p = Parcel.obtain();
        event.writeToParcel(p, 0);
        p.setDataPosition(0);
        KeyEvent cloned = event.CREATOR.createFromParcel(p);

        /* broadcast intent */
        Intent intent = new Intent(action)
                .setFlags(Intent.FLAG_RECEIVER_FOREGROUND
                        | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND
                        | Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtra(Intent.EXTRA_KEY_EVENT, cloned);
        if (sDEBUG) Log.d(sTAG, "action = " + action + ", event = " + cloned);
        mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT, null);
        return true;
    }
}
