/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;

class BluetoothBroadcastReceiver extends BroadcastReceiver {
    private final BluetoothHandler mHandler;

    BluetoothBroadcastReceiver(Context context, BluetoothHandler handler) {
        BluetoothLog.i("registerIntentFilter: " + context);
        mHandler = handler;

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_BLUETOOTH_ADDRESS_CHANGED);
        filter.addAction(Intent.ACTION_SETTING_RESTORED);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        context.registerReceiver(this, filter);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED:
                handleLocalNameChangedIntent(intent);
                break;
            case BluetoothAdapter.ACTION_BLUETOOTH_ADDRESS_CHANGED:
                handleAddressChangedIntent(intent);
                break;
            case Intent.ACTION_SETTING_RESTORED:
                handleSettingsRestoredIntent(intent);
                break;
            default:
                break;
        }
    }

    private void handleLocalNameChangedIntent(Intent intent) {
        String newName = intent.getStringExtra(BluetoothAdapter.EXTRA_LOCAL_NAME);
        BluetoothLog.d("Bluetooth Adapter name changed to " + newName);
        mHandler.postLocalNameChanged(newName);
    }

    private void handleAddressChangedIntent(Intent intent) {
        String newAddress = intent.getStringExtra(BluetoothAdapter.EXTRA_BLUETOOTH_ADDRESS);
        BluetoothLog.d("Bluetooth Adapter address changed to " + newAddress);
        if (newAddress == null) {
            return;
        }
        mHandler.postAddressChanged(newAddress);
    }

    private void handleSettingsRestoredIntent(Intent intent) {
        final String name = intent.getStringExtra(Intent.EXTRA_SETTING_NAME);
        if (name != Settings.Global.BLUETOOTH_ON) {
            return;
        }
        final String prevValue =
                intent.getStringExtra(Intent.EXTRA_SETTING_PREVIOUS_VALUE);
        final String newValue = intent.getStringExtra(Intent.EXTRA_SETTING_NEW_VALUE);
        BluetoothLog.d("ACTION_SETTING_RESTORED with BLUETOOTH_ON, prevValue=" + prevValue
                + ", newValue=" + newValue);

        if (!prevValue.equals(newValue) && (newValue.equals("0") || newValue.equals("1"))) {
            mHandler.postSettingsRestored(Integer.valueOf(newValue));
        }
    }
}
