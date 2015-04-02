/*
 **
 ** Copyright 2015, The Android Open Source Project
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */


package com.android.autotimeoutmanager;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IAutoTimeoutManager;
import android.os.IAutoTimeoutManagerCallbacks;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.Slog;

/**
 * The AutoTimeoutManagerService class
 *      manages the display timeout automatically for the user and dynamically
 *      changes it based on how the device is being used
 */
public final class AutoTimeoutManagerService extends Service {
    private static final String TAG = "AutoTimeoutSrvc";

    // Enable logging with 'adb shell setprop persist.log.tag.AutoTimeoutSrvc DEBUG' and reboot
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private final ScreenStateReceiver mScreenStateReceiver = new ScreenStateReceiver();
    private IAutoTimeoutManagerCallbacks mCallbacks;

    // Next timeout value for when power manager service should requery for
    // display status
    private int mNextTimeout = 0;

    // Last time user interacted with device through key press or touch event.
    private long mLastUserActivityTime = 0;

    // Time at which the device last went to sleep due to timeout expiring
    private long mTimeoutExpiringTime = 0;

    // Duration that the display should remain dim before turning off.
    private int mDimDuration = 0;

    // Inactivity timeout when the device is flat
    private int mFlatTimeout = 15 * 1000;

    // Inactivity timeout when the device is angled
    private int mAngleTimeout = 60 * 1000;

    private final Object mLock = new Object();

    public AutoTimeoutManagerService() {
        if (DEBUG) Slog.i(TAG, "Creating service");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Slog.i(TAG, "System is ready");

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mScreenStateReceiver, filter);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        updateSettings();
        registerSensorListener();
        mNextTimeout = mFlatTimeout;
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        super.onStartCommand(intent, flags, startId);
        if (DEBUG) Slog.i(TAG, "Service started");

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DEBUG) Slog.i(TAG, "Service destroyed");
        unregisterSensorListener();
        unregisterReceiver(mScreenStateReceiver);
    }

    private int getScreenTimeoutLocked() {
        if (DEBUG) Slog.d(TAG, "mNextTimeout=" + mNextTimeout);
        return mNextTimeout;
    }

    private int getDimDurationLocked() {
        if (DEBUG) Slog.d(TAG, "mDimDuration=" + mDimDuration);
        return mDimDuration;
    }

    private void updateUserActivityLocked(final long lastUserActivityTime) {
        // Since the device is in use, set the longer/active timeout
        mNextTimeout = mAngleTimeout;
        mLastUserActivityTime = lastUserActivityTime;
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return mBinder;
    }

    private final IAutoTimeoutManager.Stub mBinder = new IAutoTimeoutManager.Stub() {
        public void init(final IAutoTimeoutManagerCallbacks callbacks) {
            synchronized (mLock) {
                final long ident = Binder.clearCallingIdentity();
                try {
                    mCallbacks = callbacks;
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        }

        public int getScreenTimeout(final long lastUserActivityTime) {
            synchronized (mLock) {
                final long ident = Binder.clearCallingIdentity();
                try {
                    if (lastUserActivityTime > mLastUserActivityTime) {
                        updateUserActivityLocked(lastUserActivityTime);
                    }

                    return getScreenTimeoutLocked();
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        }

        public int getDimDuration() {
            synchronized (mLock) {
                final long ident = Binder.clearCallingIdentity();
                try {
                    return getDimDurationLocked();
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        }

    };

    private void updateSettings() {
        if (DEBUG) Slog.i(TAG, "Updating settings");

        final Resources resources = getResources();
        mFlatTimeout = resources.getInteger(R.integer.config_flat_timeout);
        mAngleTimeout = resources.getInteger(R.integer.config_angle_timeout);
        mDimDuration = resources.getInteger(R.integer.config_dim_duration);
    }

    private void unregisterSensorListener() {
        mSensorManager.unregisterListener(mSensorEventListener);
        if (DEBUG) Slog.d(TAG, "Done unregistering sensor listener");
    }

    private void registerSensorListener() {
        mSensorManager.registerListener(mSensorEventListener, mSensor,
                SensorManager.SENSOR_DELAY_NORMAL);
        if (DEBUG) Slog.d(TAG, "Done registering sensor listener");
    }

    private final SensorEventListener mSensorEventListener = new SensorEventListener() {
        // Defines for the orientation of the device
        private static final int FLAT = 1;
        private static final int ANGLED = 2;

        private int mOrientation = FLAT;

        @Override
        public void onSensorChanged(final SensorEvent event) {
            final float x = event.values[0];
            final float y = event.values[1];
            final float z = event.values[2];

            // Converting orientation float to state, and setting as angled if more than 10 degrees
            final int previousOrientation = mOrientation;
            mOrientation = (x < 2.0 && y < 2.0 && z > 8.0) ? FLAT : ANGLED;

            if (DEBUG) {
                Slog.d(TAG, " x=" + x + " y=" + y + " z=" + z + ", mOrientation=" + mOrientation);
            }

            if (mOrientation != previousOrientation) {
                if (DEBUG) Slog.d(TAG, "Orientation changed!");
                mNextTimeout = (mOrientation == FLAT) ? mFlatTimeout : mAngleTimeout;

                try {
                    if (mCallbacks != null) {
                        mCallbacks.onTimeoutChanged();
                    }
                } catch (final RemoteException e) {
                    Slog.w(TAG, "RemoteException caught!!! " + e);
                }
            }
        }

        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
            // Not used.
        }
    };

    private final class ScreenStateReceiver extends BroadcastReceiver {
        public void onReceive(final Context context, final Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                unregisterSensorListener();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                registerSensorListener();
            }
        }
    }

}
