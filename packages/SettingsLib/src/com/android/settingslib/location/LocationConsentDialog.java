/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.settingslib.location;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.android.settingslib.R;

/**
 * This class provides a Dialog for requesting user consent before enabling Location and
 * handling thereof.
 */
public class LocationConsentDialog {

    private static final String SHARED_PREFERENCES = "location_consent";
    private static final String DO_NOT_ASK_AGAIN = "do_not_ask_again";

    /**
     * Determines whether or not a Location consent dialog needs to be shown
     */
    public static boolean shouldShowConsentDialog(final Context context) {
        return context.getResources().getBoolean(
                R.bool.config_showEnableLocationConsentDialog) &&
                !getSharedPrefs(context).getBoolean(DO_NOT_ASK_AGAIN, false);
    }

    /**
     * Show the Location consent dialog
     *
     * @param context Context
     * @param userId The current user ID
     * @param consentCallback A callback called if/when the user consents to enabling Location
     */
    public static void showConsentDialog(final Context context, final int userId,
            final Runnable consentCallback) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.location_consent_dialog, null);

        Dialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .setTitle(R.string.location_consent_dialog_title)
                .setPositiveButton(R.string.location_consent_button_positive,
                        (dlg, which) -> {
                            handleConsent(context, view, userId, consentCallback);
                        })
                .setNegativeButton(R.string.location_consent_button_negative, null)
                .create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL);
        dialog.show();
    }

    private static void handleConsent(final Context context, final View view, final int userId,
            final Runnable consentCallback) {
        // Invoke callback to enable Location
        if (consentCallback != null) {
            consentCallback.run();
        }

        // Send a broadcast, if configured
        String consentAction = context.getResources().
                getString(R.string.config_location_consented_intent_action);
        if (!TextUtils.isEmpty(consentAction)) {
            Intent intent = new Intent(consentAction);
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            intent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
            context.sendBroadcastAsUser(intent, UserHandle.of(userId),
                    android.Manifest.permission.WRITE_SECURE_SETTINGS);
        }

        // Handle the 'Do not ask again' component
        CompoundButton doNotAskAgain = view.findViewById(R.id.do_not_ask_again);
        if (doNotAskAgain != null && doNotAskAgain.isChecked()) {
            getSharedPrefs(context).edit().putBoolean(DO_NOT_ASK_AGAIN, true).apply();
        }
    }

    private static SharedPreferences getSharedPrefs(final Context context) {
        return context.getSharedPreferences(SHARED_PREFERENCES, Context.MODE_PRIVATE);
    }
}
