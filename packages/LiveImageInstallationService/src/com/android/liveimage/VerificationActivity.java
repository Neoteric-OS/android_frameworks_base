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

package com.android.liveimage;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import static android.os.LiveImageManager.KEY_SYSTEM_SIZE;
import static android.os.LiveImageManager.KEY_USERDATA_SIZE;

public class VerificationActivity extends Activity {

    private static final int REQUEST_CODE = 1;

    // For install request verification
    private static String sVerifiedUrl;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

        if (km != null) {
            Intent intent = km.createConfirmDeviceCredentialIntent(null, null);

            if (intent == null) {
                onActivityResult(REQUEST_CODE, RESULT_OK, null);
            } else {
                startActivityForResult(intent, REQUEST_CODE);
            }
        } else {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            // retrieve data from calling intent
            Intent callingIntent = getIntent();

            String url = callingIntent.getDataString();
            long systemSize = callingIntent.getLongExtra(KEY_SYSTEM_SIZE, 0);
            long userdataSize = callingIntent.getLongExtra(KEY_USERDATA_SIZE, 0);

            // tell LiveImageInstallationService this URL is verified
            sVerifiedUrl = url;

            // start service
            Intent intent = new Intent(this, LiveImageInstallationService.class);
            // todo: use android.content.Intent.ACTION_INSTALL_LIVEIMAGE
            intent.setAction("android.intent.action.INSTALL_LIVEIMAGE");
            intent.setData(Uri.parse(url));
            intent.putExtra(KEY_SYSTEM_SIZE, systemSize);
            intent.putExtra(KEY_USERDATA_SIZE, userdataSize);

            startService(intent);
        }

        finish();
    }

    static boolean isVerified(String url) {
        return sVerifiedUrl != null && sVerifiedUrl.equals(url);
    }
}
