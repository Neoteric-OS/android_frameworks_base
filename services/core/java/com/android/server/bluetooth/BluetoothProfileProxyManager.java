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

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

/**
 * This class manages the clients connected to a given ProfileService
 * and maintains the connection with that service.
 */
public final class BluetoothProfileProxyManager
        implements ServiceConnection, IBinder.DeathRecipient {
    private final BluetoothHandler mHandler;

    BluetoothProfileProxyManager(Intent intent, BluetoothHandler handler) {
        mHandler = handler;
    }

    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
        // TODO(fatehpuria): Implement this method.
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        // TODO(fatehpuria): Implement this method.
    }

    @Override
    public void binderDied() {
        // TODO(fatehpuria): Implement this method.
    }
}
