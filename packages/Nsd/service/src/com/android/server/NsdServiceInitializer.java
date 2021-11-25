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

package com.android.server;

import android.content.Context;
import android.util.Log;

/**
 * Nsd service initializer for core networking. This is called by system server to create
 * a new instance of NsdService.
 *
 * TODO: Delete this file and initial nsd service from ConnectivityServiceInitializer after move
 *       NsdService to Connectivity module/
 */
public final class NsdServiceInitializer extends SystemService {
    private static final String TAG = NsdServiceInitializer.class.getSimpleName();
    private final NsdService mNsdService;

    public NsdServiceInitializer(Context context) throws InterruptedException {
        super(context);
        mNsdService = NsdService.create(context);
    }

    @Override
    public void onStart() {
        Log.i(TAG, "Registering " + Context.NSD_SERVICE);
        publishBinderService(Context.NSD_SERVICE, mNsdService, /* allowIsolated= */ false);
    }
}
