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

package com.android.mediastresstest;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CodecTestActivity extends Activity implements SurfaceHolder.Callback {
    private static final String LOG_TAG = CodecTestActivity.class.getSimpleName();
    static final int NUM_SURFACE = 4;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mHolder;
    private Surface mSurface;
    private final Lock mLock = new ReentrantLock();
    private final Condition mCondition = mLock.newCondition();
    private ArrayList<Surface> mSurfaces = new ArrayList<>();
    private final Lock[] mLocks = new ReentrantLock[NUM_SURFACE];
    private final Condition[] mConditions = new Condition[NUM_SURFACE];
    private boolean[] mIsSurfaceInUse = new boolean[NUM_SURFACE];
    static final Lock[] mSurfaceLock = new ReentrantLock[NUM_SURFACE];
    private HashMap<Integer, Integer>[] mThreadRetryCountMap = (HashMap<Integer, Integer>[]) new HashMap[NUM_SURFACE];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_decoder_surface_layout);
        for (int i = 0; i < NUM_SURFACE; i++) {
            mLocks[i] = new ReentrantLock();
            mConditions[i] = mLocks[i].newCondition();
            mSurfaceLock[i] = new ReentrantLock();
            mThreadRetryCountMap[i] = new HashMap<Integer, Integer>();
        }
        mSurfaceView = findViewById(R.id.surface);
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(LOG_TAG, "surface created");
        mLock.lock();
        mSurface = mHolder.getSurface();
        mLock.unlock();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.v(LOG_TAG, "surface changed " + format + " " + width + " " + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(LOG_TAG, "surface deleted");
        mLock.lock();
        mSurface = null;
        mLock.unlock();
    }

    public void waitTillSurfaceIsCreated() throws InterruptedException {
        final long mWaitTimeMs = 1000;
        final int retries = 3;
        mLock.lock();
        final long start = SystemClock.elapsedRealtime();
        while ((SystemClock.elapsedRealtime() - start) < (retries * mWaitTimeMs) &&
                mSurface == null) {
            mCondition.await(mWaitTimeMs, TimeUnit.MILLISECONDS);
        }
        mLock.unlock();
        if (mSurface == null) {
            throw new InterruptedException("Taking too long to attach a SurfaceView to a window.");
        }
    }

    public void waitTillSurfacesAreCreated() throws InterruptedException {
        waitTillSurfaceIsCreated();
        createMultipleSurfaces();
    }

    public void waitTillSurfaceIsFree(int index) throws InterruptedException {
        int retries = 0;
        final long mWaitTimeMs = 3000;
        final int maxRetry = 20;
        final int threadId = (int) Thread.currentThread().getId();
        mLocks[index].lock();
        while ((retries < maxRetry) && mIsSurfaceInUse[index] == true) {
            // TODO: find a better way for retries
            mConditions[index].await(mWaitTimeMs, TimeUnit.MILLISECONDS);
            if (mThreadRetryCountMap[index].containsKey(threadId)) {
                retries = mThreadRetryCountMap[index].get(threadId);
            }
            retries++;
            mThreadRetryCountMap[index].put(threadId, retries);
        }
        if (mIsSurfaceInUse[index] == true) {
            mLocks[index].unlock();
            throw new InterruptedException("Taking too long to reAttach to a Surface.");
        } else {
            mIsSurfaceInUse[index] = true;
            mThreadRetryCountMap[index].replaceAll((key, value) -> 0);
            mLocks[index].unlock();
        }
    }

    public Surface getSurface() {
        return mSurface;
    }

    public ArrayList<Surface> getSurfaces() {
        return mSurfaces;
    }

    public boolean getSurfaceStatus(int index) {
        return mIsSurfaceInUse[index];
    }

    public void setSurfaceStatus(boolean surfaceInUse, int index) {
        mLocks[index].lock();
        mIsSurfaceInUse[index] = surfaceInUse;
        mConditions[index].signalAll();
        mLocks[index].unlock();
    }

    public void setScreenParams(int width, int height, boolean noStretch) {
        ViewGroup.LayoutParams lp = mSurfaceView.getLayoutParams();
        final DisplayMetrics dm = getResources().getDisplayMetrics();
        if (noStretch && width <= dm.widthPixels && height <= dm.heightPixels) {
            lp.width = width;
            lp.height = height;
        } else {
            int a = dm.widthPixels * height / width;
            if (a <= dm.heightPixels) {
                lp.width = dm.widthPixels;
                lp.height = a;
            } else {
                lp.width = dm.heightPixels * width / height;
                lp.height = dm.heightPixels;
            }
        }
        runOnUiThread(() -> mSurfaceView.setLayoutParams(lp));
    }

    private void createMultipleSurfaces() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = dm.widthPixels;
        int height = dm.heightPixels;

        SurfaceView firstHalfSurface = (SurfaceView)findViewById(R.id.surface1);
        ViewGroup.LayoutParams lp1 = firstHalfSurface.getLayoutParams();
        lp1.width = (int)(width);
        lp1.height = height / NUM_SURFACE;
        runOnUiThread(() -> firstHalfSurface.setLayoutParams(lp1));
        SurfaceHolder mHolder1 = firstHalfSurface.getHolder();
        runOnUiThread(() -> mHolder1.setFixedSize(lp1.width, lp1.height));
        mSurfaces.add(mHolder1.getSurface());

        SurfaceView secondHalfSurface = (SurfaceView)findViewById(R.id.surface2);
        ViewGroup.LayoutParams lp2 = secondHalfSurface.getLayoutParams();
        lp2.width = (int)(width);
        lp2.height = height / NUM_SURFACE;
        runOnUiThread(() -> secondHalfSurface.setLayoutParams(lp2));
        SurfaceHolder mHolder2 = secondHalfSurface.getHolder();
        runOnUiThread(() ->mHolder2.setFixedSize(lp2.width, lp2.height));
        mSurfaces.add(mHolder2.getSurface());

        SurfaceView thirdHalfSurface = (SurfaceView)findViewById(R.id.surface3);
        ViewGroup.LayoutParams lp3 = thirdHalfSurface.getLayoutParams();
        lp3.width = (int)(width);
        lp3.height = height / NUM_SURFACE;
        runOnUiThread(() -> thirdHalfSurface.setLayoutParams(lp3));
        SurfaceHolder mHolder3 = thirdHalfSurface.getHolder();
        runOnUiThread(() ->mHolder3.setFixedSize(lp3.width, lp3.height));
        mSurfaces.add(mHolder3.getSurface());

        SurfaceView forthHalfSurface = (SurfaceView)findViewById(R.id.surface4);
        ViewGroup.LayoutParams lp4 = forthHalfSurface.getLayoutParams();
        lp4.width = (int)(width);
        lp4.height = height / NUM_SURFACE;
        runOnUiThread(() -> forthHalfSurface.setLayoutParams(lp4));
        SurfaceHolder mHolder4 = forthHalfSurface.getHolder();
        runOnUiThread(() ->mHolder4.setFixedSize(lp4.width, lp4.height));
        mSurfaces.add(mHolder4.getSurface());
    }
}
