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

package android.security;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.annotation.SystemService;
import android.content.Context;
import android.os.RemoteException;

@SystemService(Context.INTEGRITY_SERVICE)
//@FlaggedApi(Flags.FLAG_ENFORCE_INTENT_FILTER_MATCH)
@SuppressLint("UnflaggedApi")
public class IntegrityManager {
    @NonNull private final IIntegrityService mService;
    @NonNull private final Context mContext;

    /** @hide */
    public IntegrityManager(@NonNull Context context, @NonNull IIntegrityService service) {
        mContext = context;
        mService = service;
    }

    @NonNull
    @SuppressLint("UnflaggedApi")
    public String generateToken(@NonNull String req) {
        try {
            // Go through the service just to avoid exposing the vendor controlled system property
            // to all apps.
            return mService.generateToken(req);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
