package com.android.liveimage;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import static android.os.LiveImageManager.KEY_SYSTEM_SIZE;
import static android.os.LiveImageManager.KEY_USERDATA_SIZE;

public class VerificationActivity extends Activity {

    private static final int REQUEST_CODE = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

        if (km != null) {
            Intent intent = km.createConfirmDeviceCredentialIntent(null, null);
            startActivityForResult(intent, REQUEST_CODE);
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

            // tell LiveImageService this URL is verified
            LiveImageService.setVerifiedUrl(url);

            // start service
            Intent intent = new Intent(this, LiveImageService.class);
            // todo: use android.content.Intent.ACTION_INSTALL_LIVEIMAGE
            intent.setAction("android.intent.action.INSTALL_LIVEIMAGE");
            intent.setData(Uri.parse(url));
            intent.putExtra(KEY_SYSTEM_SIZE, systemSize);
            intent.putExtra(KEY_USERDATA_SIZE, userdataSize);

            startService(intent);
        }

        finish();
    }
}
