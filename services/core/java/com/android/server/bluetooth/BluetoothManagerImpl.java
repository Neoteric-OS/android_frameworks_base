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
import android.bluetooth.IBluetooth;
import android.bluetooth.IBluetoothGatt;
import android.bluetooth.IBluetoothManager;
import android.bluetooth.IBluetoothManagerCallback;
import android.bluetooth.IBluetoothProfileServiceConnection;
import android.bluetooth.IBluetoothStateChangeCallback;
import android.content.Context;
import android.os.IBinder;

import java.io.FileDescriptor;
import java.io.PrintWriter;

// Implementation of the IBluetoothManager.aidl
class BluetoothManagerImpl extends IBluetoothManager.Stub {
    private BluetoothHandler mHandler;
    private final BluetoothBroadcastReceiver mReceiver;

    BluetoothManagerImpl(Context context, BluetoothHandler handler) {
        // Initialize helper instances
        mHandler = handler;
        mReceiver = new BluetoothBroadcastReceiver(context, handler);
    }

    /**
     * Send enable message and set adapter name and address. Called when the boot phase
     * becomes PHASE_SYSTEM_SERVICES_READY.
     */
    public void handleOnBootSystemServicesReady() {
        // TODO(fatehpuria): Implement this method.
    }

    /**
     * Called after we switch to a different foreground user.
     */
    public void handleOnUserSwitched(int userHandle) {
        // TODO(fatehpuria): Implement this method.
    }

    /**
     * Called when user is unlocked.
     */
    public void handleOnUserUnlocked(int userHandle) {
        // TODO(fatehpuria): Implement this method.
    }


    @Override
    public IBluetooth registerAdapter(IBluetoothManagerCallback callback) {
        // TODO(fatehpuria): Implement this method.
        return null;
    }

    @Override
    public void unregisterAdapter(IBluetoothManagerCallback callback) {
        // TODO(fatehpuria): Implement this method.
    }

    @Override
    public void registerStateChangeCallback(IBluetoothStateChangeCallback callback) {
        // TODO(fatehpuria): Implement this method.
    }

    @Override
    public void unregisterStateChangeCallback(IBluetoothStateChangeCallback callback) {
        // TODO(fatehpuria): Implement this method.
    }

    @Override
    public boolean enable(String packageName) {
        // TODO(fatehpuria): Implement this method.
        return false;
    }

    @Override
    public boolean enableNoAutoConnect(String packageName) {
        // TODO(fatehpuria): Implement this method.
        return false;
    }

    @Override
    public boolean disable(String packageName, boolean persist) {
        // TODO(fatehpuria): Implement this method.
        return false;
    }

    @Override
    public int getState() {
        // TODO(fatehpuria): Implement this method.
        return BluetoothAdapter.STATE_OFF;
    }

    @Override
    public IBluetoothGatt getBluetoothGatt() {
        // TODO(fatehpuria): Implement this method.
        return null;
    }


    @Override
    public boolean bindBluetoothProfileService(int bluetoothProfile,
            IBluetoothProfileServiceConnection proxy) {
        // TODO(fatehpuria): Implement this method.
        return false;
    }

    @Override
    public void unbindBluetoothProfileService(int bluetoothProfile,
            IBluetoothProfileServiceConnection proxy) {
        // TODO(fatehpuria): Implement this method.
    }

    @Override
    public String getAddress() {
        // TODO(fatehpuria): Implement this method.
        return null;
    }

    @Override
    public String getName() {
        // TODO(fatehpuria): Implement this method.
        return null;
    }

    // @Override
    public boolean onFactoryReset() {
        // TODO(fatehpuria): Implement this method.
        return false;
    }

    @Override
    public boolean isBleScanAlwaysAvailable() {
        // TODO(fatehpuria): Implement this method.
        return false;
    }

    @Override
    public boolean enableBle(String packageName, IBinder token) {
        // TODO(fatehpuria): Implement this method.
        return false;
    }

    @Override
    public boolean disableBle(String packageName, IBinder token) {
        // TODO(fatehpuria): Implement this method.
        return false;
    }

    @Override
    public boolean isBleAppPresent() {
        // TODO(fatehpuria): Implement this method.
        return false;
    }

    @Override
    public boolean isHearingAidProfileSupported() {
        // TODO(fatehpuria): Implement this method.
        return false;
    }

    @Override
    public java.util.List<String> getSystemConfigEnabledProfilesForPackage(String packageName) {
        // TODO(fatehpuria): Implement this method.
        return null;
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        // TODO(fatehpuria): Implement this method.
    }

}
