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
import android.gsi.IGsiService;
import android.gsi.GsiProgress;
import android.os.IBinder;
import android.os.ILiveImageService;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;

public class LiveImageService extends ILiveImageService.Stub {
    private static final String TAG = "LiveImageService";
    private static final String NO_SERVICE_ERROR = "no gsiservice";

    private Context mContext;
    private volatile IGsiService mGsiService;

    LiveImageService(Context context) {
        mContext = context;
        connect();
    }

    private void connect() {
        IBinder binder = ServiceManager.getService("gsiservice");
        if (binder != null) {
            try {
                /**
                 *  If the gsiservice is crashed, the init will restart it.
                 *  The proxy object needs to be re-initialized.
                 */
                binder.linkToDeath(new DeathRecipient() {
                    @Override
                    public void binderDied() {
                        Slog.w(TAG, "gsiservice died; reconnecting");
                        mGsiService = null;
                        connect();
                    }
                }, 0);
            } catch (RemoteException e) {
                binder = null;
            }
        }
        if (binder != null) {
            mGsiService = IGsiService.Stub.asInterface(binder);
        }
    }

    private void checkPermission() {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.MANAGE_LIVE_IMAGE)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires LiveImage permission");
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
