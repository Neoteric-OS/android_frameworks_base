/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.os.BatteryStats;
import android.os.Handler;
import android.os.IVibratorService;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
import android.os.IBinder;
import android.os.Binder;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.Vibrator;
import android.os.VibratorEvent;
import android.os.WorkSource;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Slog;
import android.view.InputDevice;
import android.media.AudioAttributes;

import com.android.internal.app.IAppOpsService;
import com.android.internal.app.IBatteryStats;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

public class VibratorService extends IVibratorService.Stub
        implements InputManager.InputDeviceListener {
    private static final String TAG = "VibratorService";
    private static final boolean DEBUG = false;
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    private final LinkedList<Vibration> mVibrations;
    private final LinkedList<VibrationInfo> mPreviousVibrations;
    private final int mPreviousVibrationsLimit;
    private Vibration mCurrentVibration;
    private final WorkSource mTmpWorkSource = new WorkSource();
    private final Handler mH = new Handler();

    private final Context mContext;
    private final PowerManager.WakeLock mWakeLock;
    private final IAppOpsService mAppOpsService;
    private final IBatteryStats mBatteryStatsService;
    private PowerManagerInternal mPowerManagerInternal;
    private InputManager mIm;

    volatile VibrateThread mThread;

    // mInputDeviceVibrators lock should be acquired after mVibrations lock, if both are
    // to be acquired
    private final ArrayList<Vibrator> mInputDeviceVibrators = new ArrayList<Vibrator>();
    private boolean mVibrateInputDevicesSetting; // guarded by mInputDeviceVibrators
    private boolean mInputDeviceListenerRegistered; // guarded by mInputDeviceVibrators

    private int mCurVibUid = -1;
    private boolean mLowPowerMode;
    private SettingsObserver mSettingObserver;

    native static boolean vibratorExists();
    native static void vibratorInit();
    native static void vibratorOn(long milliseconds);
    native static void vibratorOff();

    private class Vibration implements IBinder.DeathRecipient {
        private final IBinder         mToken;
        private final VibratorEvent   mEvent;
        private final long            mStartTime;
        private final VibratorEvent[] mEvents;
        private final int             mRepeat;
        private final int             mUsageHint;
        private final int             mUid;
        private final String          mOpPkg;

        Vibration(IBinder token, VibratorEvent event, int usageHint, int uid, String opPkg) {
            this(token, event, null, 0, usageHint, uid, opPkg);
        }

        Vibration(IBinder token, VibratorEvent[] events, int repeat, int usageHint, int uid,
                String opPkg) {
            this(token, null, events, repeat, usageHint, uid, opPkg);
        }

        private Vibration(IBinder token, VibratorEvent event, VibratorEvent[] events,
                int repeat, int usageHint, int uid, String opPkg) {
            mToken = token;
            mStartTime = SystemClock.uptimeMillis();
            mEvent = event;
            mEvents = events;
            mRepeat = repeat;
            mUsageHint = usageHint;
            mUid = uid;
            mOpPkg = opPkg;
        }

        public void binderDied() {
            synchronized (mVibrations) {
                mVibrations.remove(this);
                if (this == mCurrentVibration) {
                    doCancelVibrateLocked();
                    startNextVibrationLocked();
                }
            }
        }

        public boolean hasLongerTimeout(long millis) {
            if (mEvent == null) {
                // This is a pattern, return false to play the simple
                // vibration.
                return false;
            }
            if ((mStartTime + mEvent.duration)
                    < (SystemClock.uptimeMillis() + millis)) {
                // If this vibration will end before the time passed in, let
                // the new vibration play.
                return false;
            }
            return true;
        }

        public boolean isSystemHapticFeedback() {
            return (mUid == Process.SYSTEM_UID || mUid == 0 || SYSTEM_UI_PACKAGE.equals(mOpPkg))
                    && mRepeat < 0;
        }
    }

    private static class VibrationInfo {
        VibratorEvent event;
        long startTime;
        VibratorEvent[] events;
        int repeat;
        int usageHint;
        int uid;
        String opPkg;

        public VibrationInfo(VibratorEvent event, long startTime, VibratorEvent[] events,
                int repeat, int usageHint, int uid, String opPkg) {
            this.startTime = startTime;
            this.event = event;
            this.events = events;
            this.repeat = repeat;
            this.usageHint = usageHint;
            this.uid = uid;
            this.opPkg = opPkg;
        }

        @Override
        public String toString() {
            return new StringBuilder()
                    .append("single event: ")
                    .append(event.toString())
                    .append(", startTime: ")
                    .append(startTime)
                    .append(", events: ")
                    .append(Arrays.toString(events))
                    .append(", repeat: ")
                    .append(repeat)
                    .append(", usageHint: ")
                    .append(usageHint)
                    .append(", uid: ")
                    .append(uid)
                    .append(", opPkg: ")
                    .append(opPkg)
                    .toString();
        }
    }

    VibratorService(Context context) {
        vibratorInit();
        // Reset the hardware to a default state, in case this is a runtime
        // restart instead of a fresh boot.
        vibratorOff();

        mContext = context;
        PowerManager pm = (PowerManager)context.getSystemService(
                Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "*vibrator*");
        mWakeLock.setReferenceCounted(true);

        mAppOpsService = IAppOpsService.Stub.asInterface(ServiceManager.getService(Context.APP_OPS_SERVICE));
        mBatteryStatsService = IBatteryStats.Stub.asInterface(ServiceManager.getService(
                BatteryStats.SERVICE_NAME));

        mPreviousVibrationsLimit = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_previousVibrationsDumpLimit);

        mVibrations = new LinkedList<>();
        mPreviousVibrations = new LinkedList<>();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(mIntentReceiver, filter);
    }

    public void systemReady() {
        mIm = mContext.getSystemService(InputManager.class);
        mSettingObserver = new SettingsObserver(mH);

        mPowerManagerInternal = LocalServices.getService(PowerManagerInternal.class);
        mPowerManagerInternal.registerLowPowerModeObserver(
                new PowerManagerInternal.LowPowerModeListener() {
            @Override
            public void onLowPowerModeChanged(boolean enabled) {
                updateInputDeviceVibrators();
            }
        });

        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.VIBRATE_INPUT_DEVICES),
                true, mSettingObserver, UserHandle.USER_ALL);

        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateInputDeviceVibrators();
            }
        }, new IntentFilter(Intent.ACTION_USER_SWITCHED), null, mH);

        updateInputDeviceVibrators();
    }

    private final class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean SelfChange) {
            updateInputDeviceVibrators();
        }
    }

    @Override // Binder call
    public boolean hasVibrator() {
        return doVibratorExists();
    }

    private void verifyIncomingUid(int uid) {
        if (uid == Binder.getCallingUid()) {
            return;
        }
        if (Binder.getCallingPid() == Process.myPid()) {
            return;
        }
        mContext.enforcePermission(android.Manifest.permission.UPDATE_APP_OPS_STATS,
                Binder.getCallingPid(), Binder.getCallingUid(), null);
    }

    @Override // Binder call
    public void vibrate(int uid, String opPkg, VibratorEvent event, int usageHint,
            IBinder token) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.VIBRATE)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires VIBRATE permission");
        }
        verifyIncomingUid(uid);
        // We're running in the system server so we cannot crash. Check if event
        // vibrates.  This will ensure that a vibration has either a timeout of
        // > 0 or a non-null pattern.
        if (!event.vibrates() || (mCurrentVibration != null
                && mCurrentVibration.hasLongerTimeout(event.duration))) {
            // Ignore this vibration since the current vibration will play for
            // longer than milliseconds.
            return;
        }

        if (DEBUG) {
            Slog.d(TAG, "Vibrating for " + event.duration + " ms.");
        }

        Vibration vib = new Vibration(token, event, usageHint, uid, opPkg);

        final long ident = Binder.clearCallingIdentity();
        try {
            synchronized (mVibrations) {
                removeVibrationLocked(token);
                doCancelVibrateLocked();
                addToPreviousVibrationsLocked(vib);
                startVibrationLocked(vib);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override // Binder call
    public void vibratePattern(int uid, String packageName, VibratorEvent[] events, int repeat,
            int usageHint, IBinder token) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.VIBRATE)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires VIBRATE permission");
        }
        verifyIncomingUid(uid);
        // so wakelock calls will succeed
        long identity = Binder.clearCallingIdentity();
        try {
            if (DEBUG) {
                Slog.d(TAG, "Vibrating with pattern:" + Arrays.toString(events));
            }

            // we're running in the server so we can't fail
            if (!VibratorEvent.isValidPattern(events)
                    || repeat >= events.length
                    || token == null) {
                return;
            }

            Vibration vib = new Vibration(token, events, repeat, usageHint, uid, packageName);
            try {
                token.linkToDeath(vib, 0);
            } catch (RemoteException e) {
                return;
            }

            synchronized (mVibrations) {
                removeVibrationLocked(token);
                doCancelVibrateLocked();
                if (repeat >= 0) {
                    mVibrations.addFirst(vib);
                    startNextVibrationLocked();
                } else {
                    // A negative repeat means that this pattern is not meant
                    // to repeat. Treat it like a simple vibration.
                    startVibrationLocked(vib);
                }
                addToPreviousVibrationsLocked(vib);
            }
        }
        finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private void addToPreviousVibrationsLocked(Vibration vib) {
        if (mPreviousVibrations.size() > mPreviousVibrationsLimit) {
            mPreviousVibrations.removeFirst();
        }
        mPreviousVibrations.addLast(new VibratorService.VibrationInfo(vib.mEvent, vib.mStartTime,
                vib.mEvents, vib.mRepeat, vib.mUsageHint, vib.mUid, vib.mOpPkg));
    }

    @Override // Binder call
    public void cancelVibrate(IBinder token) {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.VIBRATE,
                "cancelVibrate");

        // so wakelock calls will succeed
        long identity = Binder.clearCallingIdentity();
        try {
            synchronized (mVibrations) {
                final Vibration vib = removeVibrationLocked(token);
                if (vib == mCurrentVibration) {
                    if (DEBUG) {
                        Slog.d(TAG, "Canceling vibration.");
                    }
                    doCancelVibrateLocked();
                    startNextVibrationLocked();
                }
            }
        }
        finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private final Runnable mVibrationRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mVibrations) {
                doCancelVibrateLocked();
                startNextVibrationLocked();
            }
        }
    };

    // Lock held on mVibrations
    private void doCancelVibrateLocked() {
        if (mThread != null) {
            synchronized (mThread) {
                mThread.mDone = true;
                mThread.notify();
            }
            mThread = null;
        }
        doVibratorOff();
        mH.removeCallbacks(mVibrationRunnable);
        reportFinishVibrationLocked();
    }

    // Lock held on mVibrations
    private void startNextVibrationLocked() {
        if (mVibrations.size() <= 0) {
            reportFinishVibrationLocked();
            mCurrentVibration = null;
            return;
        }
        startVibrationLocked(mVibrations.getFirst());
    }

    // Lock held on mVibrations
    private void startVibrationLocked(final Vibration vib) {
        try {
            if (mLowPowerMode
                    && vib.mUsageHint != AudioAttributes.USAGE_NOTIFICATION_RINGTONE) {
                return;
            }

            if (vib.mUsageHint == AudioAttributes.USAGE_NOTIFICATION_RINGTONE &&
                    !shouldVibrateForRingtone()) {
                return;
            }

            int mode = mAppOpsService.checkAudioOperation(AppOpsManager.OP_VIBRATE,
                    vib.mUsageHint, vib.mUid, vib.mOpPkg);
            if (mode == AppOpsManager.MODE_ALLOWED) {
                mode = mAppOpsService.startOperation(AppOpsManager.getToken(mAppOpsService),
                    AppOpsManager.OP_VIBRATE, vib.mUid, vib.mOpPkg);
            }
            if (mode == AppOpsManager.MODE_ALLOWED) {
                mCurrentVibration = vib;
            } else {
                if (mode == AppOpsManager.MODE_ERRORED) {
                    Slog.w(TAG, "Would be an error: vibrate from uid " + vib.mUid);
                }
                mH.post(mVibrationRunnable);
                return;
            }
        } catch (RemoteException e) {
        }
        if (vib.mEvent != null) {
            doVibratorEvent(vib.mEvent, vib.mUid, vib.mUsageHint);
            mH.postDelayed(mVibrationRunnable, vib.mEvent.duration);
        } else {
            // mThread better be null here. doCancelVibrate should always be
            // called before startNextVibrationLocked or startVibrationLocked.
            mThread = new VibrateThread(vib);
            mThread.start();
        }
    }

    private boolean shouldVibrateForRingtone() {
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = audioManager.getRingerModeInternal();
        // "Also vibrate for calls" Setting in Sound
        if (Settings.System.getInt(
                mContext.getContentResolver(), Settings.System.VIBRATE_WHEN_RINGING, 0) != 0) {
            return ringerMode != AudioManager.RINGER_MODE_SILENT;
        } else {
            return ringerMode == AudioManager.RINGER_MODE_VIBRATE;
        }
    }

    private void reportFinishVibrationLocked() {
        if (mCurrentVibration != null) {
            try {
                mAppOpsService.finishOperation(AppOpsManager.getToken(mAppOpsService),
                        AppOpsManager.OP_VIBRATE, mCurrentVibration.mUid,
                        mCurrentVibration.mOpPkg);
            } catch (RemoteException e) {
            }
            mCurrentVibration = null;
        }
    }

    // Lock held on mVibrations
    private Vibration removeVibrationLocked(IBinder token) {
        ListIterator<Vibration> iter = mVibrations.listIterator(0);
        while (iter.hasNext()) {
            Vibration vib = iter.next();
            if (vib.mToken == token) {
                iter.remove();
                unlinkVibration(vib);
                return vib;
            }
        }
        // We might be looking for a simple vibration which is only stored in
        // mCurrentVibration.
        if (mCurrentVibration != null && mCurrentVibration.mToken == token) {
            unlinkVibration(mCurrentVibration);
            return mCurrentVibration;
        }
        return null;
    }

    private void unlinkVibration(Vibration vib) {
        if (vib.mEvents != null) {
            // If Vibration object has a pattern,
            // the Vibration object has also been linkedToDeath.
            vib.mToken.unlinkToDeath(vib, 0);
        }
    }

    private void updateInputDeviceVibrators() {
        synchronized (mVibrations) {
            doCancelVibrateLocked();

            synchronized (mInputDeviceVibrators) {
                mVibrateInputDevicesSetting = false;
                try {
                    mVibrateInputDevicesSetting = Settings.System.getIntForUser(
                            mContext.getContentResolver(),
                            Settings.System.VIBRATE_INPUT_DEVICES, UserHandle.USER_CURRENT) > 0;
                } catch (SettingNotFoundException snfe) {
                }

                mLowPowerMode = mPowerManagerInternal.getLowPowerModeEnabled();

                if (mVibrateInputDevicesSetting) {
                    if (!mInputDeviceListenerRegistered) {
                        mInputDeviceListenerRegistered = true;
                        mIm.registerInputDeviceListener(this, mH);
                    }
                } else {
                    if (mInputDeviceListenerRegistered) {
                        mInputDeviceListenerRegistered = false;
                        mIm.unregisterInputDeviceListener(this);
                    }
                }

                mInputDeviceVibrators.clear();
                if (mVibrateInputDevicesSetting) {
                    int[] ids = mIm.getInputDeviceIds();
                    for (int i = 0; i < ids.length; i++) {
                        InputDevice device = mIm.getInputDevice(ids[i]);
                        Vibrator vibrator = device.getVibrator();
                        if (vibrator.hasVibrator()) {
                            mInputDeviceVibrators.add(vibrator);
                        }
                    }
                }
            }

            startNextVibrationLocked();
        }
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        updateInputDeviceVibrators();
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        updateInputDeviceVibrators();
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        updateInputDeviceVibrators();
    }

    private boolean doVibratorExists() {
        // For now, we choose to ignore the presence of input devices that have vibrators
        // when reporting whether the device has a vibrator.  Applications often use this
        // information to decide whether to enable certain features so they expect the
        // result of hasVibrator() to be constant.  For now, just report whether
        // the device has a built-in vibrator.
        //synchronized (mInputDeviceVibrators) {
        //    return !mInputDeviceVibrators.isEmpty() || vibratorExists();
        //}
        return vibratorExists();
    }

    private void doVibratorEvent(VibratorEvent event, int uid, int usageHint) {
        if (!event.vibrates()) {
            return;
        }

        synchronized (mInputDeviceVibrators) {
            if (DEBUG) {
                Slog.d(TAG, "Turning vibrator on for " + event.duration + " ms.");
            }
            try {
                mBatteryStatsService.noteVibratorOn(uid, event.duration);
                mCurVibUid = uid;
            } catch (RemoteException e) {
            }
            final int vibratorCount = mInputDeviceVibrators.size();
            if (vibratorCount != 0) {
                final AudioAttributes attributes = new AudioAttributes.Builder().setUsage(usageHint)
                        .build();
                for (int i = 0; i < vibratorCount; i++) {
                    mInputDeviceVibrators.get(i).vibrateEvent(event, attributes);
                }
            } else {
                // until vibratorOn/Off support magnitude
                vibratorOn(event.duration);
            }
        }
    }

    private void doVibratorOff() {
        synchronized (mInputDeviceVibrators) {
            if (DEBUG) {
                Slog.d(TAG, "Turning vibrator off.");
            }
            if (mCurVibUid >= 0) {
                try {
                    mBatteryStatsService.noteVibratorOff(mCurVibUid);
                } catch (RemoteException e) {
                }
                mCurVibUid = -1;
            }
            final int vibratorCount = mInputDeviceVibrators.size();
            if (vibratorCount != 0) {
                for (int i = 0; i < vibratorCount; i++) {
                    mInputDeviceVibrators.get(i).cancel();
                }
            } else {
                vibratorOff();
            }
        }
    }

    private class VibrateThread extends Thread {
        final Vibration mVibration;
        boolean mDone;

        VibrateThread(Vibration vib) {
            mVibration = vib;
            mTmpWorkSource.set(vib.mUid);
            mWakeLock.setWorkSource(mTmpWorkSource);
            mWakeLock.acquire();
        }

        private void delay(long duration) {
            if (duration > 0) {
                long bedtime = duration + SystemClock.uptimeMillis();
                do {
                    try {
                        this.wait(duration);
                    }
                    catch (InterruptedException e) {
                    }
                    if (mDone) {
                        break;
                    }
                    duration = bedtime - SystemClock.uptimeMillis();
                } while (duration > 0);
            }
        }

        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
            synchronized (this) {
                final VibratorEvent[] events = mVibration.mEvents;
                final int len = events.length;
                final int repeat = mVibration.mRepeat;
                final int uid = mVibration.mUid;
                final int usageHint = mVibration.mUsageHint;
                int index = 0;

                while (!mDone) {
                    VibratorEvent event = events[index++];
                    VibratorService.this.doVibratorEvent(event, uid, usageHint);

                    if (index >= len) {
                        if (repeat < 0) {
                            break;
                        } else {
                            index = repeat;
                        }
                    }

                    if (event.duration > 0) {
                        delay(event.duration);
                    }
                }
                mWakeLock.release();
            }
            synchronized (mVibrations) {
                if (mThread == this) {
                    mThread = null;
                }
                if (!mDone) {
                    // If this vibration finished naturally, start the next
                    // vibration.
                    unlinkVibration(mVibration);
                    startNextVibrationLocked();
                }
            }
        }
    }

    BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                synchronized (mVibrations) {
                    // When the system is entering a non-interactive state, we want
                    // to cancel vibrations in case a misbehaving app has abandoned
                    // them.  However it may happen that the system is currently playing
                    // haptic feedback as part of the transition.  So we don't cancel
                    // system vibrations.
                    if (mCurrentVibration != null
                            && !mCurrentVibration.isSystemHapticFeedback()) {
                        doCancelVibrateLocked();
                    }

                    // Clear all remaining vibrations.
                    Iterator<Vibration> it = mVibrations.iterator();
                    while (it.hasNext()) {
                        Vibration vibration = it.next();
                        if (vibration != mCurrentVibration) {
                            unlinkVibration(vibration);
                            it.remove();
                        }
                    }
                }
            }
        }
    };

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {

            pw.println("Permission Denial: can't dump vibrator service from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }
        pw.println("Previous vibrations:");
        synchronized (mVibrations) {
            for (VibrationInfo info : mPreviousVibrations) {
                pw.print("  ");
                pw.println(info.toString());
            }
        }
    }
}
