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

import android.util.Slog;

final class BluetoothLog {
    private static final String TAG = "BluetoothManagerService";

    static void d(String log) {
        Slog.d(TAG, log);
    }

    static void i(String log) {
        Slog.i(TAG, log);
    }

    static void w(String log) {
        Slog.w(TAG, log);
    }

    static void e(String log) {
        Slog.e(TAG, log);
    }
}
