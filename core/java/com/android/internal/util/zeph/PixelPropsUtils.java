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
package com.android.internal.util.zeph;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PixelPropsUtils {

    private static final String TAG = PixelPropsUtils.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final String SAMSUNG = "com.samsung.";

    private static final Map<String, Object> propsToChangeGeneric;

    private static final Map<String, Object> propsToChangePixel7Pro;
    private static final Set<String> packagesToChangePixel7Pro = Set.of(
        "com.google.android.apps.wallpaper",
        "com.google.android.apps.privacy.wildlife"
    );

    private static final Map<String, Object> propsToChangePixel6Pro;
    private static final Set<String> packagesToChangePixel6Pro = Set.of(
        "com.google.android.wallpaper.effects",
        "com.google.android.apps.emojiwallpaper"
    );

    private static final Map<String, Object> propsToChangePixel5;

    private static final Map<String, Object> propsToChangePixelXL;
    private static final Set<String> packagesToChangePixelXL = Set.of(
        "com.google.android.apps.photos",
        "com.google.android.inputmethod.latin"
    );

    private static final Set<String> extraPackagesToChange = Set.of(
        "com.android.chrome",
        "com.android.vending",
        "com.breel.wallpapers20",
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
        "com.google.android.apps.subscriptions.red",
        "com.google.android.apps.tachyon",
        "com.google.android.apps.tycho"
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

    private static final Map<String, Object> propsToChangeK30U;
    private static final Set<String> packagesToChangeK30U = Set.of(
        "com.pubg.imobile"
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
        propsToChangePixel6Pro = new HashMap<>();
        propsToChangePixel6Pro.put("BRAND", "google");
        propsToChangePixel6Pro.put("MANUFACTURER", "Google");
        propsToChangePixel6Pro.put("DEVICE", "raven");
        propsToChangePixel6Pro.put("PRODUCT", "raven");
        propsToChangePixel6Pro.put("MODEL", "Pixel 6 Pro");
        propsToChangePixel6Pro.put("FINGERPRINT", "google/raven/raven:13/TQ3A.230805.001/10316531:user/release-keys");
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
        propsToChangeK30U = new HashMap<>();
        propsToChangeK30U.put("MODEL", "M2006J10C");
        propsToChangeK30U.put("MANUFACTURER", "Xiaomi");
    }

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();

        if (packageName == null) {
            return;
        }
        if (packagesToKeep.contains(packageName)) {
            return;
        }
        if (packageName.startsWith("com.google.android.GoogleCamera")
                || customGoogleCameraPackages.contains(packageName)) {
            return;
        }

        propsToChangeGeneric.forEach((k, v) -> setPropValue(k, v));

        if (packageName.equals("com.google.android.gms")) {
            if (processName != null && processName.equals("com.google.android.gms.unstable")) {
                sIsGms = true;
                setPropValue("FINGERPRINT", "google/marlin/marlin:7.1.2/NJH47F/4146041:user/release-keys");
                setPropValue("PRODUCT", "marlin");
                setPropValue("DEVICE", "marlin");
                setPropValue("MODEL", "Pixel XL");
                setVersionValue("DEVICE_INITIAL_SDK_INT", Build.VERSION_CODES.N_MR1);
            }
            return;
        }
        if (packageName.equals("com.android.vending")) {
            sIsFinsky = true;
            return;
        }

        final boolean isSamsung = packageName.startsWith(SAMSUNG);
        final boolean isExtraPackage = extraPackagesToChange.contains(packageName);

        Map<String, Object> propsToChange = new HashMap<>();
        if (packageName.startsWith("com.google.") || isSamsung || isExtraPackage) {
            if ((packagesToChangePixel7Pro.contains(packageName))) {
                propsToChange.putAll(propsToChangePixel7Pro);
            } else if (packagesToChangePixel6Pro.contains(packageName)) {
                propsToChange.putAll(propsToChangePixel6Pro);
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
            } else if (packagesToChangeK30U.contains(packageName)) {
                propsToChange.putAll(propsToChangeK30U);
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

    private static void setVersionValue(String key, Object value) {
        try {
            if (DEBUG) Log.d(TAG, "Defining version " + key + " to " + value.toString());
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set version " + key, e);
        }
    }
}

