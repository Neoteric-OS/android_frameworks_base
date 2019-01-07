/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ILiveImageService;

public class LiveImageService extends ILiveImageService.Stub {
    private static final boolean DEBUG = false;
    private static final String TAG = "LiveImageService";

    private Context mContext;

    LiveImageService(Context context) {
        mContext = context;
    }

    private void checkPermission() {
        if (DEBUG) {
            return;
        }
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.LIVE_IMAGE)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires LiveImage permission");
        }
    }

    @Override
    public synchronized void start(long size) {
        checkPermission();
    }

    @Override
    public synchronized void remove() {
        checkPermission();
    }

    @Override
    public boolean write(int num, byte[] buf) {
        checkPermission();
        return true;
    }

    @Override
    public void commit() {
        checkPermission();
    }
}
