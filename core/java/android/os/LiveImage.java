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
import android.util.Log;

/**
 * The wrapper class for LiveImageService
 * @hide
 */
@SystemService("liveimage")
public class LiveImage {
    private static final String TAG = "LIVEIMAGE";

    private final ILiveImageService mService;

    public LiveImage() {
        mService = ILiveImageService.Stub.asInterface(
                        ServiceManager.getService("liveimage"));
    }

    /**
     * start a live image procedure
     * @param size image size in bytes
     * @param size userdata size in bytes
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_LIVE_IMAGE)
    public void start(long size, long userdataSize) {
        if (mService == null) {
            Log.w(TAG, "no live image service.");
            return;
        }
        try {
            mService.start(size, userdataSize);
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * true if the device is running a live image
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_LIVE_IMAGE)
    public boolean isInUse() {
        if (mService == null) {
            Log.w(TAG, "no live image service.");
            return false;
        }
        try {
            return mService.isInUse();
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /** remove live image if presents */
    @RequiresPermission(android.Manifest.permission.MANAGE_LIVE_IMAGE)
    public void remove() {
        if (mService == null) {
            Log.w(TAG, "no live image service.");
            return;
        }
        try {
            mService.remove();
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * write the live image content
     *
     * @return success
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_LIVE_IMAGE)
    public boolean write(byte[] buf) {
        if (mService == null) {
            Log.w(TAG, "no live image service.");
            return false;
        }
        try {
            return mService.write(buf);
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /** finish write and makte device to boot into the it after reboot. */
    @RequiresPermission(android.Manifest.permission.MANAGE_LIVE_IMAGE)
    public void commit() {
        if (mService == null) {
            Log.w(TAG, "no live image service.");
            return;
        }
        try {
            mService.commit();
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }
}
