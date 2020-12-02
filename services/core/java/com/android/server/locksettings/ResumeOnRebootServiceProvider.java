/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.locksettings;

import android.Manifest;
import android.annotation.Nullable;
import android.app.AppGlobals;
import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.RemoteException;
import android.service.resumeonreboot.ResumeOnRebootService;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

/** @hide */
public class ResumeOnRebootServiceProvider {

    private static final String PROVIDER_PACKAGE = "com.google.android.gms";
    private static final String PROVIDER_REQUIRED_PERMISSION = Manifest.permission.RECOVERY;
    private static final String TAG = "ResumeOnRebootServiceProvider";

    private final Context mContext;
    private final IPackageManager mPackageManager;

    public ResumeOnRebootServiceProvider(Context context) {
        this(context, AppGlobals.getPackageManager());
    }

    @VisibleForTesting
    public ResumeOnRebootServiceProvider(Context context, IPackageManager iPackageManager) {
        this.mContext = context;
        this.mPackageManager = iPackageManager;
    }

    @Nullable
    private ServiceInfo resolveService() {
        Intent intent = new Intent();
        intent.setAction(ResumeOnRebootService.SERVICE_INTERFACE);
        intent.setPackage(PROVIDER_PACKAGE);

        int permissionCheckResult;
        try {
            permissionCheckResult = mPackageManager.checkPermission(
                    PROVIDER_REQUIRED_PERMISSION,
                    PROVIDER_PACKAGE, mContext.getUserId());
        } catch (RemoteException e) {
            Slog.i(TAG, "Unable to verify package permission.");
            return null;
        }
        if (permissionCheckResult != PackageManager.PERMISSION_GRANTED) {
            Slog.i(TAG, "Package doesn't have required permission");
            return null;
        }
        ParceledListSlice<ResolveInfo> resolvedIntent = null;
        try {
            resolvedIntent =
                    mPackageManager.queryIntentServices(intent,
                            null, 0, mContext.getUserId());
        } catch (RemoteException e) {
            Slog.e(TAG, "Unable to query service.", e);
        }
        if (resolvedIntent == null || resolvedIntent.getList().isEmpty()) {
            Slog.i(TAG, "Resolved service list is empty.");
            return null;
        }
        for (ResolveInfo resolvedInfo : resolvedIntent.getList()) {
            if (resolvedInfo.serviceInfo != null &&
                    PROVIDER_REQUIRED_PERMISSION.equals(resolvedInfo.serviceInfo.permission)) {
                return resolvedInfo.serviceInfo;
            }
        }
        return null;
    }

    @Nullable
    public ResumeOnRebootServiceConnection getServiceConnection() {
        ServiceInfo serviceInfo = resolveService();
        if (serviceInfo == null) {
            return null;
        }
        return new ResumeOnRebootServiceConnection(mContext, serviceInfo.getComponentName());
    }
}
