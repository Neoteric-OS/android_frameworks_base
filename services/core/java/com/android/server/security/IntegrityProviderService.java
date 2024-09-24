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
import android.security.IIntegrityProviderService;
import android.util.Log;

import com.android.server.LocalServices;
import com.android.server.SystemService;

/**
 * A {@link SystemService} that provides integrity related operations.
 * @hide
 */
public class IntegrityProviderService extends SystemService {
    private static final String TAG = "IntegrityProviderService";

    private final IBinder mService;

    public IntegrityProviderService(final Context context) {
        super(context);
        Log.i(TAG, "***Nea-integrityProvider service constructor");
        mService = new BinderService(context);
        LocalServices.addService(IntegrityProviderService.class, this);
    }

    /** Gets the instance of the service */
    public static IntegrityProviderService getService() {
        return LocalServices.getService(IntegrityProviderService.class);
    }

    private final class BinderService extends IIntegrityProviderService.Stub {
        BinderService(Context context) {
            super();
        }


        @Override
        public String requestToken(String s) throws RemoteException {
            return s+"+ tokenResponse from provider";
        }
    }

    @Override
    public void onStart() {
        publishBinderService(Context.INTEGRITY_PROVIDER_SERVICE, mService);
        Log.i(TAG, "***Nea-integrityProvider Service started");
    }
}
