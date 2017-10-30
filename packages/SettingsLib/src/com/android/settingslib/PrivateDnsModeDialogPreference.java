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
package com.android.settingslib;

import static android.net.ConnectivityManager.PRIVATE_DNS_MODE_OFF;
import static android.net.ConnectivityManager.PRIVATE_DNS_MODE_OPPORTUNISTIC;
import static android.net.ConnectivityManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v14.preference.PreferenceDialogFragment;
import android.support.v7.preference.DialogPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

// XXX
import android.util.Log;

public class PrivateDnsModeDialogPreference extends CustomDialogPreference {
    private static final String TAG = "XXX";

    private static final String DEFAULT_MODE = PRIVATE_DNS_MODE_OPPORTUNISTIC;
    private String mSettingsValue;
    private String mDialogValue;

    public PrivateDnsModeDialogPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public PrivateDnsModeDialogPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public PrivateDnsModeDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PrivateDnsModeDialogPreference(Context context) {
        super(context);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder,
            DialogInterface.OnClickListener listener) {
        Log.w(TAG, "onPrepareDialogBuilder");
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        Log.w(TAG, "onDialogClosed: " + positiveResult);
    }

    @Override
    protected void onClick(DialogInterface dialog, int which) {
        Log.w(TAG, "onClick: " + which);
    }

    @Override
    protected void onBindDialogView(View view) {
        Log.w(TAG, "onBindDialogView");

        loadSettingsValue();
    }

    private void loadSettingsValue() {
        mSettingsValue = Settings.Global.getString(
                Settings.Global.PRIVATE_DNS_MODE,
                DEFAULT_MODE);

        if (!isValidMode(mSettingsValue)) {
            mSettingsValue = DEFAULT_MODE;
        }
    }

    private void saveDialogValue() {
    }

    private static boolean isValidMode(String mode) {
        return !TextUtils.isEmpty(mode) && (
                mode.equals(PRIVATE_DNS_MODE_OFF) ||
                mode.equals(PRIVATE_DNS_MODE_OPPORTUNISTIC) ||
                mode.startsWith(PRIVATE_DNS_MODE_PROVIDER_HOSTNAME));
    }
}
