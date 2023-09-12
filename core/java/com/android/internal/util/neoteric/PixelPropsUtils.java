/*
 * Copyright (C) 2020 The Pixel Experience Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.neoteric;

import android.app.ActivityTaskManager;
import android.app.Application;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.os.Process;
import android.os.SystemProperties;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PixelPropsUtils {

    private static final String TAG = PixelPropsUtils.class.getSimpleName();
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final ComponentName GMS_ADD_ACCOUNT_ACTIVITY = ComponentName.unflattenFromString(
            "com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity");
    private static final boolean DEBUG = false;

    private static final String SAMSUNG = "com.samsung.";

    private static final Map<String, Object> propsToChangeGeneric;

    private static final Map<String, Object> propsToChangePixel7Pro;
    private static final Set<String> packagesToChangePixel7Pro = Set.of(
        "com.google.android.apps.privacy.wildlife",
        "com.google.android.apps.wallpaper.pixel",
        "com.google.android.apps.wallpaper",
        "com.google.android.apps.subscriptions.red",
        "com.google.pixel.livewallpaper",
        "com.google.android.wallpaper.effects",
        "com.google.android.apps.emojiwallpaper"
    );

    private static final Map<String, Object> propsToChangePixel5;

    private static final Map<String, Object> propsToChangePixelXL;
    private static final Set<String> packagesToChangePixelXL = Set.of(
        "com.google.android.apps.photos"
    );

    private static final Set<String> extraPackagesToChange = Set.of(
        "com.android.chrome",
        "com.breel.wallpapers20",
        "com.nhs.online.nhsonline",
        "com.netflix.mediaclient",
        "com.nothing.smartcenter"
    );

    private static final Set<String> customGoogleCameraPackages = Set.of(
        "com.google.android.MTCL83",
        "com.google.android.UltraCVM",
        "com.google.android.apps.cameralite"
    );

    private static final Map<String, Set<String>> propsToKeep;
    private static final Set<String> packagesToKeep = Set.of(
        "com.google.android.dialer",
        "com.google.android.euicc",
        "com.google.ar.core",
        "com.google.android.youtube",
        "com.google.android.apps.youtube.kids",
        "com.google.android.apps.youtube.music",
        "com.google.android.apps.recorder",
        "com.google.android.apps.wearables.maestro.companion",
        "com.google.android.apps.tachyon",
        "com.google.android.apps.tycho",
        "com.google.android.as",
        "com.google.android.gms",
        "com.google.android.apps.restore"
    );

    private static final Map<String, Object> propsToChangeROG1;
    private static final Set<String> packagesToChangeROG1 = Set.of(
        "com.dts.freefireth",
        "com.dts.freefiremax",
        "com.madfingergames.legends"
    );

    private static final Map<String, Object> propsToChangeXP5;
    private static final Set<String> packagesToChangeXP5 = Set.of(
        "com.activision.callofduty.shooter",
        "com.tencent.tmgp.kr.codm",
        "com.garena.game.codm",
        "com.vng.codmvn"
    );

    private static final Map<String, Object> propsToChangeXP1M3;
    private static final Set<String> packagesToChangeXP1M3 = Set.of(
        "com.gameloft.android.ANMP.GloftA9HM"
    );

    private static final Map<String, Object> propsToChangeOP8P;
    private static final Set<String> packagesToChangeOP8P = Set.of(
        "com.tencent.ig",
        "com.pubg.imobile",
        "com.pubg.krmobile",
        "com.pubg.newstate",
        "com.vng.pubgmobile",
        "com.rekoo.pubgm",
        "com.tencent.tmgp.pubgmhd",
        "com.riotgames.league.wildrift",
        "com.riotgames.league.wildrifttw",
        "com.riotgames.league.wildriftvn",
        "com.netease.lztgglobal",
        "com.epicgames.fortnite",
        "com.epicgames.portal"
    );

    private static final Map<String, Object> propsToChangeIqoo10P;
    private static final Set<String> packagesToChangeIqoo10P = Set.of(
        "com.tencent.tmgp.sgame"
    );

    private static final Map<String, Object> propsToChangeMI11;
    private static final Set<String> packagesToChangeMI11 = Set.of(
        "com.ea.gp.apexlegendsmobilefps",
        "com.mobile.legends"
    );

    private static volatile boolean sIsGms = false;
    private static volatile boolean sIsFinsky = false;

    static {
        propsToKeep = new HashMap<>();
        propsToKeep.put("com.google.android.settings.intelligence", Set.of("FINGERPRINT"));
        propsToChangeGeneric = new HashMap<>();
        propsToChangeGeneric.put("TYPE", "user");
        propsToChangeGeneric.put("TAGS", "release-keys");
        propsToChangePixel7Pro = new HashMap<>();
        propsToChangePixel7Pro.put("BRAND", "google");
        propsToChangePixel7Pro.put("MANUFACTURER", "Google");
        propsToChangePixel7Pro.put("DEVICE", "cheetah");
        propsToChangePixel7Pro.put("PRODUCT", "cheetah");
        propsToChangePixel7Pro.put("MODEL", "Pixel 7 Pro");
        propsToChangePixel7Pro.put("FINGERPRINT", "google/cheetah/cheetah:13/TQ3A.230805.001/10316531:user/release-keys");
        propsToChangePixel5 = new HashMap<>();
        propsToChangePixel5.put("BRAND", "google");
        propsToChangePixel5.put("MANUFACTURER", "Google");
        propsToChangePixel5.put("DEVICE", "redfin");
        propsToChangePixel5.put("PRODUCT", "redfin");
        propsToChangePixel5.put("MODEL", "Pixel 5");
        propsToChangePixel5.put("FINGERPRINT", "google/redfin/redfin:13/TQ3A.230805.001/10316531:user/release-keys");
        propsToChangePixelXL = new HashMap<>();
        propsToChangePixelXL.put("BRAND", "google");
        propsToChangePixelXL.put("MANUFACTURER", "Google");
        propsToChangePixelXL.put("DEVICE", "marlin");
        propsToChangePixelXL.put("PRODUCT", "marlin");
        propsToChangePixelXL.put("MODEL", "Pixel XL");
        propsToChangePixelXL.put("FINGERPRINT", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys");
        propsToChangeROG1 = new HashMap<>();
        propsToChangeROG1.put("MODEL", "ASUS_Z01QD");
        propsToChangeROG1.put("MANUFACTURER", "asus");
        propsToChangeXP5 = new HashMap<>();
        propsToChangeXP5.put("MODEL", "SO-52A");
        propsToChangeXP5.put("MANUFACTURER", "Sony");
        propsToChangeXP1M3 = new HashMap<>();
        propsToChangeXP1M3.put("MODEL", "XQ-BC72");
        propsToChangeXP1M3.put("MANUFACTURER", "Sony");
        propsToChangeOP8P = new HashMap<>();
        propsToChangeOP8P.put("MODEL", "IN2020");
        propsToChangeOP8P.put("MANUFACTURER", "OnePlus");
        propsToChangeIqoo10P = new HashMap<>();
        propsToChangeIqoo10P.put("MODEL", "V2218A");
        propsToChangeIqoo10P.put("MANUFACTURER", "vivo");
        propsToChangeMI11 = new HashMap<>();
        propsToChangeMI11.put("BRAND", "Xiaomi");
        propsToChangeMI11.put("MANUFACTURER", "Xiaomi");
        propsToChangeMI11.put("DEVICE", "star");
        propsToChangeMI11.put("PRODUCT", "star");
        propsToChangeMI11.put("MODEL", "M2102K1G");
    }

    public static boolean setPropsForGms(String packageName) {
        if (packageName.equals("com.android.vending")) {
            sIsFinsky = true;
        }
        if (packageName.equals(PACKAGE_GMS)
                || packageName.toLowerCase().contains("androidx.test")
                || packageName.equalsIgnoreCase("com.google.android.apps.restore")) {
            final String processName = Application.getProcessName();
            if (processName.toLowerCase().contains("unstable")
                    || processName.toLowerCase().contains("pixelmigrate")
                    || processName.toLowerCase().contains("instrumentation")) {
                sIsGms = true;

                final boolean was = isGmsAddAccountActivityOnTop();
                final TaskStackListener taskStackListener = new TaskStackListener() {
                    @Override
                    public void onTaskStackChanged() {
                        final boolean is = isGmsAddAccountActivityOnTop();
                        if (is ^ was) {
                            dlog("GmsAddAccountActivityOnTop is:" + is + " was:" + was + ", killing myself!");
                            // process will restart automatically later
                            Process.killProcess(Process.myPid());
                        }
                    }
                };
                try {
                    ActivityTaskManager.getService().registerTaskStackListener(taskStackListener);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to register task stack listener!", e);
                }
                if (was) return true;

                dlog("Spoofing build for GMS");
                // Alter build parameters to pixel 2 for avoiding hardware attestation enforcement
                setBuildField("DEVICE", "walleye");
                setBuildField("FINGERPRINT", "google/walleye/walleye:8.1.0/OPM1.171019.011/4448085:user/release-keys");
                setBuildField("MODEL", "Pixel 2");
                setBuildField("PRODUCT", "walleye");
                setVersionField("DEVICE_INITIAL_SDK_INT", Build.VERSION_CODES.O);
                return true;
            }
        }
        return false;
    }

    public static void setProps(String packageName) {
        propsToChangeGeneric.forEach((k, v) -> setPropValue(k, v));

        if (packageName == null) {
            return;
        }
        if (setPropsForGms(packageName)) {
            return;
        }
        if (packagesToKeep.contains(packageName)) {
            return;
        }
        if (packageName.startsWith("com.google.android.GoogleCamera")
                || customGoogleCameraPackages.contains(packageName)) {
            return;
        }

        Map<String, Object> propsToChange = new HashMap<>();

        if (packageName.startsWith("com.google.")
                || packageName.startsWith(SAMSUNG)
                || extraPackagesToChange.contains(packageName)) {
            if ((packagesToChangePixel7Pro.contains(packageName))) {
                propsToChange.putAll(propsToChangePixel7Pro);
            } else if (packagesToChangePixelXL.contains(packageName)) {
                propsToChange.putAll(propsToChangePixelXL);
            } else {
                propsToChange.putAll(propsToChangePixel5);
            }
        } else {
            if (packagesToChangeROG1.contains(packageName)) {
                propsToChange.putAll(propsToChangeROG1);
            } else if (packagesToChangeXP5.contains(packageName)) {
                propsToChange.putAll(propsToChangeXP5);
            } else if (packagesToChangeXP1M3.contains(packageName)) {
                propsToChange.putAll(propsToChangeXP1M3);
            } else if (packagesToChangeOP8P.contains(packageName)) {
                propsToChange.putAll(propsToChangeOP8P);
            } else if (packagesToChangeIqoo10P.contains(packageName)) {
                propsToChange.putAll(propsToChangeIqoo10P);
            } else if (packagesToChangeMI11.contains(packageName)) {
                propsToChange.putAll(propsToChangeMI11);
            }
        }
        if (DEBUG) Log.d(TAG, "Defining props for: " + packageName);
        for (Map.Entry<String, Object> prop : propsToChange.entrySet()) {
            String key = prop.getKey();
            Object value = prop.getValue();
            if (propsToKeep.containsKey(packageName) && propsToKeep.get(packageName).contains(key)) {
                if (DEBUG) Log.d(TAG, "Not defining " + key + " prop for: " + packageName);
                continue;
            }
            if (DEBUG) Log.d(TAG, "Defining " + key + " prop for: " + packageName);
            setPropValue(key, value);
        }
        // Set proper indexing fingerprint
        if (packageName.equals("com.google.android.settings.intelligence")) {
            setPropValue("FINGERPRINT", Build.VERSION.INCREMENTAL);
        }
    }

    private static void setPropValue(String key, Object value) {
        try {
            if (DEBUG) Log.d(TAG, "Defining prop " + key + " to " + value.toString());
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }
    
    private static void setBuildField(String key, String value) {
        try {
            // Unlock
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);

            // Edit
            field.set(null, value);

            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static void setVersionField(String key, Object value) {
        try {
            // Unlock
            if (DEBUG) Log.d(TAG, "Defining version field " + key + " to " + value.toString());
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);

            // Edit
            field.set(null, value);

            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set version field " + key, e);
        }
    }
    
    private static boolean isGmsAddAccountActivityOnTop() {
        try {
            final ActivityTaskManager.RootTaskInfo focusedTask =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            return focusedTask != null && focusedTask.topActivity != null
                    && focusedTask.topActivity.equals(GMS_ADD_ACCOUNT_ACTIVITY);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get top activity!", e);
        }
        return false;
    }

    public static boolean shouldBypassTaskPermission(Context context) {
        // GMS doesn't have MANAGE_ACTIVITY_TASKS permission
        final int callingUid = Binder.getCallingUid();
        final int gmsUid;
        try {
            gmsUid = context.getPackageManager().getApplicationInfo(PACKAGE_GMS, 0).uid;
            dlog("shouldBypassTaskPermission: gmsUid:" + gmsUid + " callingUid:" + callingUid);
        } catch (Exception e) {
            Log.e(TAG, "shouldBypassTaskPermission: unable to get gms uid", e);
            return false;
        }
        return gmsUid == callingUid;
    }

    private static boolean isCallerSafetyNet() {
        return Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    public static void onEngineGetCertificateChain() {
        // Check stack for SafetyNet
        if (sIsGms && isCallerSafetyNet()) {
            throw new UnsupportedOperationException();
        }

        // Check stack for PlayIntegrity
        if (sIsFinsky) {
            throw new UnsupportedOperationException();
        }
    }
    
    public static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }
}
