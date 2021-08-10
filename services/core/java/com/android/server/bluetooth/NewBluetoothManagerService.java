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

import android.bluetooth.IBluetooth;
import android.bluetooth.IBluetoothGatt;
import android.bluetooth.IBluetoothManager;
import android.bluetooth.IBluetoothManagerCallback;
import android.bluetooth.IBluetoothStateChangeCallback;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.IoThread;

import java.util.HashMap;

/**
 * Entry instance of the Bluetooth Manager Service.
 * This class is responsible for monitoring bluetooth state changes.
 */
@VisibleForTesting
public class NewBluetoothManagerService {
    // Bluetooth persisted setting is off
    static final int BLUETOOTH_OFF = 0;
    // Bluetooth persisted setting is on
    static final int BLUETOOTH_ON = 1;

    private static final String SECURE_SETTINGS_ADDR_VALID = "bluetooth_addr_valid";
    private static final String SECURE_SETTINGS_ADDRESS = "bluetooth_address";
    private static final String SECURE_SETTINGS_NAME = "bluetooth_name";

    private final ContentResolver mContentResolver;
    private final Context mContext;

    private final BluetoothManagerImpl mManagerImpl;
    private final BluetoothBroadcastReceiver mReceiver;

    // Resource properties
    private final boolean mWirelessConsentRequired;
    private final boolean mIsHearingAidProfileSupported;
    private final boolean mNoHomeScreen;
    private final boolean mIsPersistedStateSupported;
    private final boolean mAddressValidation;

    NewBluetoothManagerService(Context context) {
        mContext = context;
        mContentResolver = mContext.getContentResolver();

        // Collect resources
        mWirelessConsentRequired = mContext.getResources()
                .getBoolean(R.bool.config_wirelessConsentRequired);
        mIsHearingAidProfileSupported = mContext.getResources()
                .getBoolean(com.android.internal.R.bool.config_hearing_aid_profile_supported);
        mNoHomeScreen = mContext.getResources().getBoolean(R.bool.config_noHomeScreen);
        mIsPersistedStateSupported = mContext.getResources()
                .getBoolean(R.bool.config_supportBluetoothPersistedState);
        mAddressValidation = mContext.getResources()
                .getBoolean(com.android.internal.R.bool.config_bluetooth_address_validation);

        // Helper instances
        Looper looper = IoThread.get().getLooper();
        BluetoothHandler handler = new BluetoothHandler(looper, this,
                new RemoteCallbackList<IBluetoothManagerCallback>(),
                new RemoteCallbackList<IBluetoothStateChangeCallback>(),
                new HashMap<Integer, BluetoothProfileProxyManager>());
        mReceiver = new BluetoothBroadcastReceiver(context, handler);
        mManagerImpl = new BluetoothManagerImpl(context, handler);
    }


    IBluetoothManager getMenager() {
        return mManagerImpl;
    }

    @VisibleForTesting
    public boolean isWirelessConsentRequired() {
        return mWirelessConsentRequired;
    }

    @VisibleForTesting
    public boolean isHearingAidProfileSupported() {
        return mIsHearingAidProfileSupported;
    }

    /**
     * Check if device is configured with no home screen, which implies no SystemUI.
     */
    @VisibleForTesting
    public boolean isNoHomeScreen() {
        return mNoHomeScreen;
    }

    @VisibleForTesting
    public boolean isPersistedStateSupported() {
        return mIsPersistedStateSupported;
    }

    @VisibleForTesting
    public boolean isAddressValidation() {
        return mAddressValidation;
    }

    /**
     * Bind and start up the Bluetooth service
     */
    @VisibleForTesting
    public boolean bindAdapter(ServiceConnection conn) {
        Intent intent = new Intent(IBluetooth.class.getName());
        return doBind(intent, conn, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
    }

    /**
     * Bind and start up the BluetoothGatt profile service
     */
    @VisibleForTesting
    public boolean bindGatt(ServiceConnection conn) {
        Intent intent = new Intent(IBluetoothGatt.class.getName());
        return doBind(intent, conn, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
    }

    /**
     * Bind to a specific Bluetooth profile service
     */
    @VisibleForTesting
    public boolean bindProfile(Intent intent, ServiceConnection conn) {
        return doBind(intent, conn, 0);
    }

    private boolean doBind(Intent intent, ServiceConnection conn, int flags) {
        ComponentName comp = intent.resolveSystemService(mContext.getPackageManager(), 0);
        intent.setComponent(comp);
        if (comp == null || !mContext.bindServiceAsUser(intent, conn, flags,
                  UserHandle.CURRENT)) {
            return false;
        }
        return true;
    }

    /**
     * Get a binder interface of the Bluetooth service
     */
    @VisibleForTesting
    public IBluetooth getBluetoothInterface(IBinder service) {
        return IBluetooth.Stub.asInterface(Binder.allowBlocking(service));
    }

    /**
     * Write persisted Bluetooth state to Settings.Global
     */
    @VisibleForTesting
    public void writeBluetoothState(int value) {
        // waive WRITE_SECURE_SETTINGS permission check
        long callingIdentity = Binder.clearCallingIdentity();
        Settings.Global.putInt(mContentResolver, Settings.Global.BLUETOOTH_ON, value);
        Binder.restoreCallingIdentity(callingIdentity);
    }

    /**
     * Write local Bluetooth name to Settings.Global
     */
    @VisibleForTesting
    public void writeName(String name) {
        Settings.Secure.putString(mContentResolver, SECURE_SETTINGS_NAME, name);
    }

    /**
     * Write local Bluetooth address to Settings.Global
     */
    @VisibleForTesting
    public void writeAddress(String address) {
        Settings.Secure.putString(mContentResolver, SECURE_SETTINGS_ADDRESS, address);
    }

    /**
     * Write local Bluetooth address validation to Settings.Global
     */
    @VisibleForTesting
    public void writeAddrValid(int valid) {
        Settings.Secure.putInt(mContentResolver, SECURE_SETTINGS_ADDR_VALID, valid);
    }

    /**
     * Read local Bluetooth name from Settings.Global
     */
    @VisibleForTesting
    public String readName() {
        return Settings.Secure.getString(mContentResolver, SECURE_SETTINGS_NAME);
    }

    /**
     * Read local Bluetooth address from Settings.Global
     */
    @VisibleForTesting
    public String readAddress() {
        return Settings.Secure.getString(mContentResolver, SECURE_SETTINGS_ADDRESS);
    }

    /**
     * Read validation of the local Bluetooth address from Settings.Global
     */
    @VisibleForTesting
    public int readAddrValid() {
        return Settings.Secure.getInt(mContentResolver, SECURE_SETTINGS_ADDR_VALID, 0);
    }

    /**
     * Get stored persisted Bluetooth setting
     */
    @VisibleForTesting
    public int readBluetoothState() {
        return Settings.Global.getInt(mContentResolver, Settings.Global.BLUETOOTH_ON,
                BLUETOOTH_ON);
    }
}
