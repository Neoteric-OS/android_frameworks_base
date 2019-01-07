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
import android.gsi.GsiProgress;
import android.gsi.IGsiService;
import android.os.IAndroidOnTapService;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;

/**
 * AndroidOnTapService implements IAndroidOnTapService.
 * It provides permission check before proxy requests to gsid
 */
public class AndroidOnTapService extends IAndroidOnTapService.Stub {
    private static final String TAG = "AndroidOnTapService";
    private static final String NO_SERVICE_ERROR = "no gsiservice";

    private Context mContext;
    private volatile IGsiService mGsiService;

    AndroidOnTapService(Context context) {
        mContext = context;
        connect();
    }

    private void connect() {
        IBinder binder = ServiceManager.getService("gsiservice");
        if (binder != null) {
            try {
                /**
                 * The init will restart gsiservice if it crashed and the proxy object will need to
                 * be re-initialized in this case.
                 */
                binder.linkToDeath(
                        new DeathRecipient() {
                            @Override
                            public void binderDied() {
                                Slog.w(TAG, "gsiservice died; reconnecting");
                                mGsiService = null;
                                connect();
                            }
                        },
                        0);
            } catch (RemoteException e) {
                binder = null;
            }
        }
        if (binder != null) {
            mGsiService = IGsiService.Stub.asInterface(binder);
        }
    }

    private void checkPermission() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires AndroidOnTap permission");
        }
    }

    @Override
    public boolean start(long size, long userdataSize) throws RemoteException {
        checkPermission();
        if (mGsiService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            return false;
        }
        return mGsiService.startGsiInstall(size, userdataSize, true);
    }

    @Override
    public GsiProgress getStartProgress() throws RemoteException {
        checkPermission();
        if (mGsiService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            GsiProgress status = new GsiProgress();
            status.status = IGsiService.STATUS_NO_OPERATION;
            status.bytes_processed = 0;
            status.total_bytes = 0;
            return status;
        }
        return mGsiService.getInstallProgress();
    }

    @Override
    public boolean abort() throws RemoteException {
        checkPermission();
        if (mGsiService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            return false;
        }
        return mGsiService.cancelGsiInstall();
    }

    @Override
    public boolean isInUse() throws RemoteException {
        checkPermission();
        if (mGsiService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            return false;
        }
        return mGsiService.isGsiRunning();
    }

    @Override
    public boolean isInstalled() throws RemoteException {
        checkPermission();
        if (mGsiService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            return false;
        }
        return mGsiService.isGsiInstalled();
    }

    @Override
    public boolean remove() throws RemoteException {
        checkPermission();
        if (mGsiService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            return false;
        }
        return mGsiService.removeGsiInstall();
    }

    @Override
    public boolean toggle() throws RemoteException {
        checkPermission();
        if (mGsiService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            return false;
        }
        if (mGsiService.isGsiRunning()) {
            return mGsiService.disableGsiInstall();
        } else {
            return mGsiService.setGsiBootable();
        }
    }

    @Override
    public boolean write(byte[] buf) throws RemoteException {
        checkPermission();
        if (mGsiService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            return false;
        }
        return mGsiService.commitGsiChunkFromMemory(buf);
    }

    @Override
    public boolean commit() throws RemoteException {
        checkPermission();
        if (mGsiService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            return false;
        }
        return mGsiService.setGsiBootable();
    }
}
