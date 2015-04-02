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

package com.android.server;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IAutoTimeoutManager;
import android.os.IAutoTimeoutManagerCallbacks;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.TimeoutManagerInternal;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;


public final class TimeoutManagerService extends SystemService {
    private static final String TAG = "TimeoutMngrSrvc";

    // Enable logging with 'adb shell setprop persist.log.tag.TimeoutMngrSrvc DEBUG' and reboot
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public static final String SERVICE_INTERFACE =
                "android.service.autotimeoutmanager";

    // Default timeout in milliseconds.  This is only used until the settings
    // provider populates the actual default value (R.integer.def_screen_off_timeout).
    private static final int DEFAULT_SCREEN_OFF_TIMEOUT = 15 * 1000;
    private static final int DEFAULT_SCREEN_DIM_DURATION = 7 * 1000;
    private static final float DEFAULT_SCREEN_DIM_RATIO = 0.20f;

    private IAutoTimeoutManager mIAutoTimeoutManager;
    private TimeoutManagerInternal.TimeoutManagerCallbacks mCallbacks;
    private final IAutoTimeoutManagerCallbacks.Stub mAutoCallback = new AutoTimeoutCallback();
    private final ServiceConnection mConnection = new AutoServiceConnection();
    private final UserSwitchedReceiver mUserSwitchedReceiver = new UserSwitchedReceiver();

    private final Context mContext;
    private final Object mLock = new Object();

    // Current timeout
    private int mTimeout = DEFAULT_SCREEN_OFF_TIMEOUT;

    // The maximum allowable screen off timeout according to the device
    // administration policy.  Overrides other settings.
    private int mMaximumScreenOffTimeoutFromDeviceAdmin = Integer.MAX_VALUE;

    // The user activity timeout override from the window manager
    // to allow the current foreground activity to override the user activity timeout.
    // Use -1 to disable.
    private long mUserActivityTimeoutOverrideFromWindowManager = -1;

    // The minimum screen off timeout, in milliseconds.
    private int mMinimumScreenOffTimeoutConfig;

    // The screen dim duration, in milliseconds.
    // This is subtracted from the end of the screen off timeout so the
    // minimum screen off timeout should be longer than this.
    private int mMaximumScreenDimDurationConfig;

    // The maximum screen dim time expressed as a ratio relative to the screen
    // off timeout.  If the screen off timeout is very short then we want the
    // dim timeout to also be quite short so that most of the time is spent on.
    // Otherwise the user won't get much screen on time before dimming occurs.
    private float mMaximumScreenDimRatioConfig;

    private boolean mAutoTimeoutEnabled = false;
    private ComponentName mAutoTimeoutComponent = null;
    private boolean mBound = false;

    public TimeoutManagerService(final Context context) {
        super(context);

        mContext = context;
    }

    @Override
    public void onStart() {
        publishLocalService(TimeoutManagerInternal.class, new LocalService());
    }

    @Override
    public void onBootPhase(final int phase) {
        // TODO: use boot phase
    }

    public void systemReady() {
        synchronized (mLock) {
            if (DEBUG) Slog.d(TAG, "systemReady()");

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_USER_SWITCHED);
            mContext.registerReceiver(mUserSwitchedReceiver, filter);

            final SettingsObserver mSettingsObserver = new SettingsObserver(new Handler());
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SCREEN_OFF_TIMEOUT),
                    false, mSettingsObserver, UserHandle.USER_ALL);

            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SCREEN_TIMEOUT_MODE),
                    false, mSettingsObserver, UserHandle.USER_ALL);

            readConfigurationLocked();
            updateSettingsLocked();
        }
    }

    private int getTimeoutInternal(final long lastUserActivityTime) {
        int timeout;
        if (isAutoTimeoutEnabled()) {
            try {
                timeout = mIAutoTimeoutManager.getScreenTimeout(lastUserActivityTime);
            } catch (RemoteException e) {
                Slog.w(TAG, "Caught RemoteException" + e);
                mAutoTimeoutEnabled = false;
                timeout = DEFAULT_SCREEN_OFF_TIMEOUT;
            }
        } else {
            timeout = Math.max(mTimeout, mMinimumScreenOffTimeoutConfig);
        }

        if (isMaximumScreenOffTimeoutFromDeviceAdminEnforcedInternal()) {
            timeout = Math.min(timeout, mMaximumScreenOffTimeoutFromDeviceAdmin);
        }
        if (mUserActivityTimeoutOverrideFromWindowManager >= 0) {
            timeout = (int)Math.min(timeout, mUserActivityTimeoutOverrideFromWindowManager);
        }

        return timeout;
    }

    private int getScreenDimDurationInternal() {
        int dimDuration = DEFAULT_SCREEN_DIM_DURATION;
        if (isAutoTimeoutEnabled()) {
            try {
                dimDuration = mIAutoTimeoutManager.getDimDuration();
            } catch (RemoteException e) {
                Slog.w(TAG, "Caught RemoteException" + e);
                mAutoTimeoutEnabled = false;
            }
        } else {
            dimDuration = Math.min(mMaximumScreenDimDurationConfig,
                    (int) (mTimeout * mMaximumScreenDimRatioConfig));
        }

        return dimDuration;
    }

    private void setUserActivityTimeoutOverrideFromWindowManagerInternal(long timeoutMillis) {
        boolean shouldNotifyCallbacks = false;
        synchronized (mLock) {
            if (mUserActivityTimeoutOverrideFromWindowManager != timeoutMillis) {
                mUserActivityTimeoutOverrideFromWindowManager = timeoutMillis;
                shouldNotifyCallbacks = true;
            }
        }

        if (shouldNotifyCallbacks) {
            notifyCallbacks();
        }
    }

    private void setMaximumScreenOffTimeoutFromDeviceAdminInternal(int timeoutMillis) {
        synchronized (mLock) {
            mMaximumScreenOffTimeoutFromDeviceAdmin = timeoutMillis;
        }

        notifyCallbacks();
    }

    private boolean isMaximumScreenOffTimeoutFromDeviceAdminEnforcedLocked() {
        return mMaximumScreenOffTimeoutFromDeviceAdmin > 0
                && mMaximumScreenOffTimeoutFromDeviceAdmin < Integer.MAX_VALUE;
    }

    private boolean isMaximumScreenOffTimeoutFromDeviceAdminEnforcedInternal() {
        synchronized (mLock) {
            return isMaximumScreenOffTimeoutFromDeviceAdminEnforcedLocked();
        }
    }

    private boolean isAutoTimeoutEnabled() {
        if (!mAutoTimeoutEnabled) return false;
        if (mAutoTimeoutComponent == null) return false;
        if (mIAutoTimeoutManager == null) return false;
        return true;
    }

    private void notifyCallbacks() {
        if (mCallbacks != null) {
            mCallbacks.onTimeoutChanged();
        }
    }

    private void updateSettingsLocked() {
        final ContentResolver resolver = mContext.getContentResolver();

        if (DEBUG) Slog.d(TAG, "updateSettingsLocked()");

        final int timeoutMode = Settings.System.getIntForUser(resolver,
                Settings.System.SCREEN_TIMEOUT_MODE,
                Settings.System.SCREEN_TIMEOUT_MODE_MANUAL,
                UserHandle.USER_CURRENT);

        if (timeoutMode == Settings.System.SCREEN_TIMEOUT_MODE_AUTOMATIC
                && mAutoTimeoutComponent != null
                && validateTimeoutManager(mAutoTimeoutComponent)) {
            if (DEBUG) Slog.d(TAG, "AutoTimeoutManager=" + mAutoTimeoutComponent);
            mAutoTimeoutEnabled = connectToService(mAutoTimeoutComponent);
        } else {
            mAutoTimeoutEnabled = false;
            mTimeout = Settings.System.getIntForUser(resolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    DEFAULT_SCREEN_OFF_TIMEOUT,
                    UserHandle.USER_CURRENT);
            disconnectFromService();
        }

        if (DEBUG) {
            Slog.d(TAG, "mAutoTimeoutEnabled=" + mAutoTimeoutEnabled
                    + ", mAutoTimeoutComponent=" + mAutoTimeoutComponent
                    + ", mIAutoTimeoutManager=" + mIAutoTimeoutManager
                    + ", timeoutMode=" + timeoutMode);
        }
    }

    private void readConfigurationLocked() {
        final Resources resources = mContext.getResources();

        if (DEBUG) Slog.d(TAG, "readConfigurationLocked()");
        mMinimumScreenOffTimeoutConfig = resources.getInteger(
                com.android.internal.R.integer.config_minimumScreenOffTimeout);
        mMaximumScreenDimDurationConfig = resources.getInteger(
                com.android.internal.R.integer.config_maximumScreenDimDuration);
        mMaximumScreenDimRatioConfig = resources.getFraction(
                com.android.internal.R.fraction.config_maximumScreenDimRatio, 1, 1);
        String name = resources.getString(
                com.android.internal.R.string.config_autoTimeoutComponent);
        mAutoTimeoutComponent =
                TextUtils.isEmpty(name) ? null : ComponentName.unflattenFromString(name);

    }

    private boolean validateTimeoutManager(final ComponentName component) {
        if (component == null) return false;
        final ServiceInfo serviceInfo = getServiceInfo(component);
        if (serviceInfo == null) {
            Slog.w(TAG, "TimeoutManager " + component + " does not exist.");
            return false;
        }

        return true;
    }

    private boolean connectToService(final ComponentName name) {
        if (mBound != true) {
            Intent intent = new Intent(SERVICE_INTERFACE);
            intent.setComponent(name);
            mBound = mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
        return mBound;
    }

    private void disconnectFromService() {
        if (mBound) {
            mContext.unbindService(mConnection);
            mBound = false;
        }
    }

    private ServiceInfo getServiceInfo(final ComponentName name) {
        try {
            return name != null ? mContext.getPackageManager().getServiceInfo(name, 0) : null;
        } catch (NameNotFoundException e) {
            return null;
        }
    }

    private final class SettingsObserver extends ContentObserver {
        public SettingsObserver(final Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(final boolean selfChange, final Uri uri) {
            synchronized (mLock) {
                updateSettingsLocked();
            }
        }
    }

    private final class AutoServiceConnection implements ServiceConnection {
        public void onServiceDisconnected(final ComponentName name) {
            if (DEBUG) Slog.d(TAG, "Service " + name + " disconnected!");
            mIAutoTimeoutManager = null;
        }

        public void onServiceConnected(final ComponentName name, final  IBinder service) {
            if (DEBUG) Slog.d(TAG, "Service " + name + " connected!");
            mIAutoTimeoutManager = IAutoTimeoutManager.Stub.asInterface(service);
            try {
                mIAutoTimeoutManager.init(mAutoCallback);
            } catch (RemoteException e) {
                Slog.e(TAG, "RemoteException caught!!! " + e);
                mIAutoTimeoutManager = null;
            }
        }
    }

    private final class LocalService extends TimeoutManagerInternal {
        @Override
        public void init(final TimeoutManagerInternal.TimeoutManagerCallbacks callbacks) {
            mCallbacks = callbacks;
        }

        @Override
        public int getTimeout(final long lastUserActivityTime) {
            return getTimeoutInternal(lastUserActivityTime);
        }

        @Override
        public int getScreenDimDuration() {
            return getScreenDimDurationInternal();
        }

        @Override
        public boolean isMaximumScreenOffTimeoutFromDeviceAdminEnforced() {
            return isMaximumScreenOffTimeoutFromDeviceAdminEnforcedInternal();
        }

        @Override
        public void setUserActivityTimeoutOverrideFromWindowManager(long timeoutMillis) {
            setUserActivityTimeoutOverrideFromWindowManagerInternal(timeoutMillis);
        }

        @Override
        public void setMaximumScreenOffTimeoutFromDeviceAdmin(int timeoutMillis) {
            setMaximumScreenOffTimeoutFromDeviceAdminInternal(timeoutMillis);
        }
    }

    private final class AutoTimeoutCallback extends IAutoTimeoutManagerCallbacks.Stub {
        public void onTimeoutChanged() {
            notifyCallbacks();
        }
    }

    private final class UserSwitchedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            synchronized (mLock) {
                updateSettingsLocked();
            }
        }
    }
}

