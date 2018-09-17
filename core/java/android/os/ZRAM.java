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

import android.annotation.IntDef;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.content.Context;
import android.media.AudioAttributes;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The wrapper class for ZRAMService
 * @hide
 */
@SystemService("zram")
@SystemApi
public class ZRAM {
    private static final String TAG = "ZRAM";

    private final IZRAMService mService;

    public ZRAM() {
        mService = IZRAMService.Stub.asInterface(ServiceManager.getService("zram"));
    }

    /**
     * hot add a zram slot
     * @param size
     * @return number of the zram slot or -1 on failure.
     */
    @RequiresPermission(android.Manifest.permission.ZRAM)
    public int hotAdd(int size) {
        if (mService == null) {
            Log.w(TAG, "no zram service.");
            return -1;
        }
        try {
            return mService.hotAdd(size);
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * hot remove a zram slot
     * @return success.
     */
    @RequiresPermission(android.Manifest.permission.ZRAM)
    public boolean hotRemove(int num) {
        if (mService == null) {
            Log.w(TAG, "no zram service.");
            return false;
        }
        try {
            return mService.hotRemove(num);
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * open a zram slot for read & write
     * @return success
     */
    @RequiresPermission(android.Manifest.permission.ZRAM)
    public boolean open(int num) {
        if (mService == null) {
            Log.w(TAG, "no zram service.");
            return false;
        }
        try {
            return mService.open(num);
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * read from zram
     * @return number of bytes read or -1 on failure
     */
    @RequiresPermission(android.Manifest.permission.ZRAM)
    public int read(int num, byte[] buf) {
        if (mService == null) {
            Log.w(TAG, "no zram service.");
            return -1;
        }
        try {
            return mService.read(num, buf);
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * write to zram
     * @return success
     */
    @RequiresPermission(android.Manifest.permission.ZRAM)
    public boolean write(int num, byte[] buf) {
        if (mService == null) {
            Log.w(TAG, "no zram service.");
            return false;
        }
        try {
            return mService.write(num, buf);
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }

    /**
     * finish read/write
     */
    @RequiresPermission(android.Manifest.permission.ZRAM)
    void close(int num) {
        if (mService == null) {
            Log.w(TAG, "no zram service.");
            return;
        }
        try {
            mService.close(num);
        } catch (RemoteException e) {
            throw new RuntimeException(e.toString());
        }
    }
}
