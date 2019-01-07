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

package android.os;

import android.annotation.RequiresPermission;
import android.annotation.SystemService;
import android.gsi.GsiProgress;
import android.gsi.IGsiService;
import android.util.Slog;

/**
 * The wrapper class for AndroidOnTapService
 *
 * @hide
 */
@SystemService("android_on_tap")
public class AndroidOnTap {
    private static final String TAG = "ANDROID_ON_TAP";
    private static final String NO_SERVICE_ERROR = "no android_on_tap service";

    private final IAndroidOnTapService mService;

    public AndroidOnTap() {
        mService =
                IAndroidOnTapService.Stub.asInterface(ServiceManager.getService("android_on_tap"));
    }

    /**
     * start an AndroidOnTap procedure
     *
     * @param size image size in bytes
     * @param userdataSize userdata size in bytes
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
    public boolean start(long size, long userdataSize) {
        if (mService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            return false;
        }
        try {
            return mService.start(size, userdataSize);
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * Query the progress of the current asynchronous install operation. This can be called while
     * another operation is in progress.
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
    public GsiProgress getStartProgress() {
        if (mService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            GsiProgress status = new GsiProgress();
            status.status = IGsiService.STATUS_NO_OPERATION;
            status.bytes_processed = 0;
            status.total_bytes = 0;
            return status;
        }
        try {
            return mService.getStartProgress();
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * Abort the start process. Note the abort call must be in a thread other than the one call
     * start() given the start won't return until it's finished.
     *
     * @return success
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
    public boolean abort() {
        if (mService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            return false;
        }
        try {
            return mService.abort();
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /** @return true if the device is running an android on tap */
    @RequiresPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
    public boolean isInUse() {
        if (mService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            return false;
        }
        try {
            return mService.isInUse();
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /** @return true if the device has an android on tap installed */
    @RequiresPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
    public boolean isInstalled() {
        if (mService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            return false;
        }
        try {
            return mService.isInstalled();
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * remove android_on_tap if presents
     *
     * @return success
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
    public boolean remove() {
        if (mService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            return false;
        }
        try {
            return mService.remove();
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * Enable GSI when it's not enabled, otherwise, disable it.
     *
     * @return success
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
    public boolean toggle() {
        if (mService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            return false;
        }
        try {
            return mService.toggle();
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * write android_on_tap image
     *
     * @return success
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
    public boolean write(byte[] buf) {
        if (mService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            return false;
        }
        try {
            return mService.write(buf);
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * finish write and make device to boot into the it after reboot.
     *
     * @return success
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
    public boolean commit() {
        if (mService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            return false;
        }
        try {
            return mService.commit();
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }
}
