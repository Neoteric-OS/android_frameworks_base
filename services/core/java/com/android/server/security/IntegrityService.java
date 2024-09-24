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

package com.android.server.security;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.security.IIntegrityService;

import android.annotation.NonNull;
import android.security.IntegrityProviderManager;
import android.util.Log;

import com.android.server.LocalServices;
import com.android.server.SystemService;

/**
 * A {@link SystemService} that provides integrity related operations.
 * @hide
 */
public class IntegrityService extends SystemService {
    private static final String TAG = "IntegrityService";

    private final IBinder mService;
    @NonNull private final Context mContext;

    public IntegrityService(final Context context) {
        super(context);
        mContext = context;
        mService = new BinderService(context);
        LocalServices.addService(IntegrityService.class, this);
    }

    /** Gets the instance of the service */
    public static IntegrityService getService() {
        return LocalServices.getService(IntegrityService.class);
    }

    private final class BinderService extends IIntegrityService.Stub {
        BinderService(Context context) {
            super();
        }

        @Override
        public String generateToken(String req) throws RemoteException {
            Log.i(TAG, "***Nea-integrity service inside generateToken ");
            return mContext.getSystemService(IntegrityProviderManager.class).requestToken(req);
        }
    }

    @Override
    public void onStart() {
        publishBinderService(Context.INTEGRITY_SERVICE, mService);
    }
}
