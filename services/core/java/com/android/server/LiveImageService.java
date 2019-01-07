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
import android.os.IBinder;
import android.os.ILiveImageService;
import android.os.IVold;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;

public class LiveImageService extends ILiveImageService.Stub {
    private static final boolean DEBUG = false;
    private static final String TAG = "LiveImageService";

    private Context mContext;
    private volatile IVold mVold;

    LiveImageService(Context context) {
        mContext = context;
        connect();
    }

    private void connect() {
        IBinder binder = ServiceManager.getService("vold");
        if (binder != null) {
            try {
                binder.linkToDeath(new DeathRecipient() {
                    @Override
                    public void binderDied() {
                        Slog.w(TAG, "vold died; reconnecting");
                        mVold = null;
                        connect();
                    }
                }, 0);
            } catch (RemoteException e) {
                binder = null;
            }
        }

        if (binder != null) {
            mVold = IVold.Stub.asInterface(binder);
        }
    }

    private void checkPermission() {
        if (DEBUG) {
            return;
        }
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.MANAGE_LIVE_IMAGE)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires LiveImage permission");
        }
    }

    @Override
    public void start(long size, long userdataSize) throws RemoteException {
        checkPermission();
        mVold.startGsiInstall(size, userdataSize);
    }

    @Override
    public void remove() throws RemoteException {
        checkPermission();
        mVold.removeGsiInstall();
    }

    @Override
    public boolean isInUse() throws RemoteException {
        checkPermission();
        return mVold.isUsingGsi();
    }

    @Override
    public boolean write(byte[] buf) throws RemoteException {
        checkPermission();
        return mVold.writeGsi(buf);
    }

    @Override
    public void commit() throws RemoteException {
        checkPermission();
        mVold.commitGsiChunk();
    }
}
