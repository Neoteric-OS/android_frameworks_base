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

import android.app.AppGlobals;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.IZRAMService;
import android.util.Slog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Hashtable;

public class ZRAMService extends IZRAMService.Stub {
    private static final boolean DEBUG = false;
    private static final String TAG = "ZRAMService";
    private static final String HOTADD = "/sys/class/zram-control/hot_add";
    private static final String HOTREMOVE = "/sys/class/zram-control/hot_remove";
    private static final String PRIVILEGED_PACKAGE = "com.android.zram";

    private Context mContext;
    private Hashtable<Integer, InputStream> mInputStreamCache;
    private Hashtable<Integer, OutputStream> mOutputStreamCache;
    ZRAMService(Context context) {
        mContext = context;
        mInputStreamCache = new Hashtable<Integer, InputStream>();
        mOutputStreamCache = new Hashtable<Integer, OutputStream>();
    }
    private void checkPermission() {
        if (DEBUG) {
            return;
        }
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.ZRAM)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires ZRAM permission");
        }
        final int uid = Binder.getCallingUid();
        final PackageManager pm = mContext.getPackageManager();
        final String[] packages = pm.getPackagesForUid(uid);
        final int N = packages.length;
        for (int i=0; i<N; i++) {
            if (packages[i].equals(PRIVILEGED_PACKAGE)) {
                return;
            }
        }
        throw new SecurityException("The client does not belong to com.android.zram");
    }
    @Override
    public synchronized int hotAdd(int size) {
        checkPermission();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(HOTADD)));
            int num = Integer.parseInt(reader.readLine());
            if (num < 0)
                return -1;
            String p = String.format("/sys/block/zram%d/disksize", num);
            PrintWriter writer = new PrintWriter(new FileOutputStream(p));
            writer.println(""+size);
            return num;
        } catch (Exception e) {
            Slog.w(TAG, e.toString());
            return -1;
        }
    }
    @Override
    public synchronized boolean hotRemove(int num) {
        checkPermission();
        try {
            PrintWriter writer = new PrintWriter(new FileOutputStream(HOTREMOVE));
            writer.println(""+num);
            return true;
        } catch (IOException e) {
            Slog.w(TAG, e.toString());
            return false;
        }
    }
    @Override
    public boolean open(int num) {
        checkPermission();
        synchronized (mInputStreamCache) {
            if (mInputStreamCache.get(new Integer(num)) != null)
                return true;
            // This file should has been created by ueventd right after hotAdd
            File f = new File("/dev/block/zram"+num);
            if (!f.exists()) {
                return false;
            }
            try {
                mInputStreamCache.put(new Integer(num), new FileInputStream(f));
                mOutputStreamCache.put(new Integer(num), new FileOutputStream(f));
            } catch (IOException e) {
                Slog.w(TAG, e.toString());
                return false;
            }
        }
        return true;
    }
    @Override
    public int read(int num, byte[] buf) {
        checkPermission();
        InputStream in = mInputStreamCache.get(new Integer(num));
        if (in == null) {
            return -1;
        }
        try {
            synchronized (in) {
                return in.read(buf);
            }
        } catch (IOException e) {
            Slog.w(TAG, e.toString());
            return -1;
        }
    }
    @Override
    public boolean write(int num, byte[] buf) {
        checkPermission();
        OutputStream out = mOutputStreamCache.get(new Integer(num));
        if (out == null) {
            return false;
        }
        try {
            synchronized (out) {
                out.write(buf);
                out.flush();
            }
            return true;
        } catch (IOException e) {
            Slog.w(TAG, e.toString());
            return false;
        }
    }
    @Override
    public void close(int num) {
        checkPermission();
        Integer key = new Integer(num);
        InputStream in = null;
        OutputStream out = null;
        synchronized (mInputStreamCache) {
            in = mInputStreamCache.get(key);
            out = mOutputStreamCache.get(key);
            if (in == null)
                return;
            mInputStreamCache.remove(key);
            mOutputStreamCache.remove(key);
        }
        synchronized (in) {
            try {
                in.close();
            } catch (IOException e) {
                Slog.w(TAG, e.toString());
            }
        }
        synchronized (out) {
            try {
                out.close();
            } catch (IOException e) {
                Slog.w(TAG, e.toString());
            }
        }
    }
}
