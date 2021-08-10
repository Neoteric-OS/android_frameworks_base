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

import android.bluetooth.IBluetoothManagerCallback;
import android.bluetooth.IBluetoothStateChangeCallback;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteCallbackList;

import java.util.HashMap;

/**
 * Class that handles tasks from the BluetoothManager.
 */
public class BluetoothHandler extends Handler {
    // Message of toggling Bluetooth.
    static final int MSG_TOGGLE_BLUETOOTH = 0;
    // Messages related to error handling.
    static final int MSG_RESTART_BLUETOOTH_SERVICE = 100;
    static final int MSG_TIMEOUT_BIND = 101;
    static final int MSG_TIMEOUT_UNBIND = 102;
    // Messages related to switching modes.
    static final int MSG_USER_SWITCHED = 200;
    static final int MSG_USER_UNLOCKED = 201;
    // Messages of Bluetooth binder
    static final int MSG_REGISTER_STATE_CHANGE_CALLBACK = 300;
    static final int MSG_UNREGISTER_STATE_CHANGE_CALLBACK = 301;
    static final int MSG_BLUETOOTH_SERVICE_CONNECTED = 302;
    static final int MSG_BLUETOOTH_SERVICE_DISCONNECTED = 303;
    // Messages of Profile proxies
    static final int MSG_ADD_PROXY = 400;
    static final int MSG_BIND_PROFILE_SERVICE = 401;
    // Messages from intent receiver
    static final int MSG_LOCAL_NAME_CHANGED = 500;
    static final int MSG_BLUETOOTH_ADDRESS_CHANGED = 501;
    static final int MSG_RESTORE_USER_SETTING = 502;
    // Message from IBluetoothCallback
    static final int MSG_BLUETOOTH_STATE_CHANGE = 600;

    // Parameters of MSG_RESTORE_USER_SETTING
    private static final int RESTORE_SETTING_TO_ON = 1;
    private static final int RESTORE_SETTING_TO_OFF = 0;

    private final NewBluetoothManagerService mBluetoothContext;
    private final RemoteCallbackList<IBluetoothManagerCallback> mManagerCallbacks;
    private final RemoteCallbackList<IBluetoothStateChangeCallback> mStateChangeCallbacks;
    private final HashMap<Integer, BluetoothProfileProxyManager> mProfileProxies;

    BluetoothHandler(Looper looper, NewBluetoothManagerService bluetoothContext,
            RemoteCallbackList<IBluetoothManagerCallback> managerCallbacks,
            RemoteCallbackList<IBluetoothStateChangeCallback> stateChangeCallbacks,
            HashMap<Integer, BluetoothProfileProxyManager> profileProxies) {
        super(looper);
        mBluetoothContext = bluetoothContext;
        mManagerCallbacks = managerCallbacks;
        mStateChangeCallbacks = stateChangeCallbacks;
        mProfileProxies = profileProxies;
    }

    @Override
    public void handleMessage(Message msg) {
        // TODO(fatehpuria): Implement this method.
    }

    // Message posters
    void postBluetoothStateChange(int prevState, int newState) {
        sendMessage(obtainMessage(MSG_BLUETOOTH_STATE_CHANGE, prevState, newState));
    }

    void postLocalNameChanged(String name) {
        sendMessage(obtainMessage(MSG_LOCAL_NAME_CHANGED, name));
    }

    void postAddressChanged(String address) {
        sendMessage(obtainMessage(MSG_BLUETOOTH_ADDRESS_CHANGED, address));
    }

    void postSettingsRestored(int value) {
        sendMessage(obtainMessage(MSG_RESTORE_USER_SETTING, value, 0));
    }
}
