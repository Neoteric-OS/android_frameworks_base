/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.server.display;

import com.android.internal.util.DumpUtils;
import com.android.internal.util.IndentingPrintWriter;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;

import java.util.ArrayList;

/**
 * A display adapter that handles virtual displays each of which gets placed atop a
 * Surface provided by a client.  These displays are private to a particular UID and
 * are not visible or accessible to any other UIDs.
 * <p>
 * Display adapters are guarded by the {@link DisplayManagerService.SyncRoot} lock.
 * </p>
 */
final class SurfaceDisplayAdapter extends DisplayAdapter {
    static final String TAG = "SurfaceDisplayAdapter";

    private final SparseArray<SurfaceDisplayDevice> mSurfaceDisplays =
            new SparseArray<SurfaceDisplayDevice>();

    // Called with SyncRoot lock held.
    public SurfaceDisplayAdapter(DisplayManagerService.SyncRoot syncRoot,
            Context context, Handler handler, Listener listener) {
        super(syncRoot, context, handler, listener, TAG);
    }

    private final class SurfaceDisplayDevice extends DisplayDevice {
        private final int mIndex;
        private final String mName;
        private final int mWidth;
        private final int mHeight;
        private final float mRefreshRate;
        private final float mXdpi;
        private final float mYdpi;
        private final float mDensity;

        private Surface mSurface;
        private DisplayDeviceInfo mInfo;

        public SurfaceDisplayDevice(IBinder displayToken, 
                int index, String name,
                int width, int height, float refreshRate, 
                float xdpi, float ydpi, float density,
                Surface surface) {
            super(SurfaceDisplayAdapter.this, displayToken);
            mIndex = index;
            mName = name;
            mWidth = width;
            mHeight = height;
            mRefreshRate = refreshRate;
            mXdpi = xdpi;
            mYdpi = ydpi;
            mDensity = density;
            mSurface = surface;
        }

        @Override
        public void performTraversalInTransactionLocked() {
            if (mSurface != null) {
                setSurfaceInTransactionLocked(mSurface);
            } else {
                setSurfaceInTransactionLocked(null);
            }
        }

        public void clearSurfaceLocked() {
            mSurface = null;
            sendTraversalRequestLocked();
        }

        public void releaseLocked() {
            mSurface.release();
            clearSurfaceLocked();
            mSurfaceDisplays.delete(mIndex);
        }

        public boolean isValid() {
            if (mSurface != null) {
                return mSurface.isValid();
            }
            return false;
        }

        @Override
        public DisplayDeviceInfo getDisplayDeviceInfoLocked() {
            if (mInfo == null) {
                mInfo = new DisplayDeviceInfo();
                mInfo.name = mName;
                mInfo.width = mWidth;
                mInfo.height = mHeight;
                mInfo.refreshRate = mRefreshRate;
                mInfo.densityDpi = (int)mDensity;
                mInfo.xDpi = mXdpi;
                mInfo.yDpi = mYdpi;
                mInfo.flags = 0;
                mInfo.type = Display.TYPE_SURFACE;
                mInfo.touch = DisplayDeviceInfo.TOUCH_EXTERNAL;
            }
            return mInfo;
        }
    }

    private int findNextIndexLocked() {
        int i=0;
        while (mSurfaceDisplays.get(i)!=null) i++;
        return i;
    }

    public void removeDeadDisplaysLocked() {
        int count = mSurfaceDisplays.size();
        for (int i=0;i<count;) {
            SurfaceDisplayDevice device = mSurfaceDisplays.get(i);
            if (device.isValid()) {
                i++;
            } else {
                mSurfaceDisplays.remove(i);
                count--;
            }
        }
    }

    public String addSurfaceDisplayLocked(int width, int height, float xdpi, float ydpi, float density, Surface surface, int owningUid, int creatorPid) {
        int index = findNextIndexLocked();
        String name = "surface display #"+index;
        IBinder displayToken = Surface.createDisplay(name, false);
        SurfaceDisplayDevice device = new SurfaceDisplayDevice(displayToken, index, name, width, height, 60.0f, xdpi, ydpi, density, surface);
        device.setOwningUidLocked(owningUid);
        device.setCreatorPidLocked(creatorPid);
        mSurfaceDisplays.put(index,device);
        sendDisplayDeviceEventLocked(device, DISPLAY_DEVICE_EVENT_ADDED);
        return name;
    }

    public int removeSurfaceDisplayLocked(DisplayDevice device) {
        if (device instanceof SurfaceDisplayDevice) {
            SurfaceDisplayDevice surfaceDisplayDevice = (SurfaceDisplayDevice)device;
            sendDisplayDeviceEventLocked(device, DISPLAY_DEVICE_EVENT_REMOVED);
            return 0;
        }
        return -1;
    }

}
