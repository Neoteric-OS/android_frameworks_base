/*
 * Copyright (C) 2019 The Android Open Source Project
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
        if (mService == null) {
            Slog.w(TAG, NO_SERVICE_ERROR);
            throw new RuntimeException(NO_SERVICE_ERROR);
        }
    }

    /**
     * Start AndroidOnTap installation. This call may take 60~90 seconds. The
     * caller may use another thread to call the getStartProgress() to get the progress.
     *
     * @param systemSize system size in bytes
     * @param userdataSize userdata size in bytes
     * @return true if the call succeeds
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
    public boolean startInstallation(long systemSize, long userdataSize) {
        try {
            return mService.startInstallation(systemSize, userdataSize);
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * Query the progress of the current installation operation. This can be called while
     * the installation is in progress.
     *
     * @return GsiProgress
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
    public GsiProgress getInstallationProgress() {
        try {
            return mService.getInstallationProgress();
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * Abort the installation process. Note this method must be called in a thread other
     * than the one calling the startInstallation method as the startInstallation
     * method will not return until it is finished.
     *
     * @return true if the call succeeds
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
    public boolean abort() {
        try {
            return mService.abort();
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /** @return true if the device is running an android on tap */
    @RequiresPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
    public boolean isInUse() {
        try {
            return mService.isInUse();
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /** @return true if the device has an android on tap installed */
    @RequiresPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
    public boolean isInstalled() {
        try {
            return mService.isInstalled();
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * Remove AndroidOnTap installation if present
     *
     * @return true if the call succeeds
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
    public boolean remove() {
        try {
            return mService.remove();
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * Enable AndroidOnTap when it's not enabled, otherwise, disable it.
     *
     * @return true if the call succeeds
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
    public boolean toggle() {
        try {
            return mService.toggle();
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * Write a chunk of the AndroidOnTap system image
     *
     * @return true if the call succeeds
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
    public boolean write(byte[] buf) {
        try {
            return mService.write(buf);
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * Finish write and make device to boot into the it after reboot.
     *
     * @return true if the call succeeds
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ANDROID_ON_TAP)
    public boolean commit() {
        try {
            return mService.commit();
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }
}
