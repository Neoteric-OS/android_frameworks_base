package com.android.server.security;

import android.os.ServiceManager;
import android.os.SystemService;
import android.security.AndroidKeyMintTestDevice;

public class KeyMintTestDeviceSystemService extends SystemService {

    private static final String TAG = "KeyMintTestDeviceSystemService";

    public KeyMintTestDeviceSystemService(final Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        IntentFilter packageFilter = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addDataScheme("package");
        try {
            AndroidKeyMintTestDevice keymintTestDevice = new AndoirdKeyMintTestDevice(keyMintDevice);
            ServiceManager.addService("android_keymint_test_device", keymintTestDevice);
        } catch (RuntimeException e) {
            Slog.w(TAG, "Unable to register for package removed broadcast", e);
        }
    }
}
