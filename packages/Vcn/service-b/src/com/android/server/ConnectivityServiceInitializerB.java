/*
 * Copyright (C) 2024 The Android Open Source Project
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
import android.net.vcn.VcnManager;
import android.util.Log;

/**
 * Service initializer for VCN. This is called by system server to create a new instance of
 * VcnManagementService.
 */
// TODO: b/366598445 Move this file to frameworks/base/packages/Vcn/
// TODO: After VCN modularization is released, merge this class with ConnectivityServiceInitializer
public final class ConnectivityServiceInitializerB extends SystemService {
    private static final String TAG = ConnectivityServiceInitializerB.class.getSimpleName();
    private final VcnManagementService mVcnManagementService;

    public ConnectivityServiceInitializerB(Context context) {
        super(context);
        mVcnManagementService = VcnManagementService.create(context);
    }

    @Override
    public void onStart() {
        if (mVcnManagementService != null) {
            Log.i(TAG, "Registering " + VcnManager.VCN_MANAGEMENT_SERVICE_STRING);
            publishBinderService(
                    VcnManager.VCN_MANAGEMENT_SERVICE_STRING,
                    mVcnManagementService,
                    /* allowIsolated= */ false);
        }
    }

    @Override
    public void onBootPhase(int phase) {}
}
