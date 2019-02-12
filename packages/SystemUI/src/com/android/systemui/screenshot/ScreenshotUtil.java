/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.screenshot;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.TextUtils;

import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.util.Locale;

/**
 * Utility methods for setting up a screenshot
 */
public class ScreenshotUtil {

    static String getEnglishAppLabel(Context context) {
        String appLabel = null;
        ActivityManager.RunningTaskInfo runningTask =
                ActivityManagerWrapper.getInstance().getRunningTask();
        final String packageName = runningTask != null
                ? runningTask.baseActivity.getPackageName()
                : null;
        if (packageName != null) {
            PackageManager pm = context.getPackageManager();
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName,
                        PackageManager.GET_META_DATA);
                if (appInfo != null) {
                    final Configuration config = new Configuration();
                    config.locale = new Locale("en");
                    final Resources res = pm.getResourcesForApplication(packageName);
                    res.updateConfiguration(config, context.getResources().getDisplayMetrics());
                    appLabel = res.getString(appInfo.labelRes);
                    // Sanitise the application label string.
                    appLabel = appLabel.replaceAll("\\s+", "_");
                    appLabel = appLabel.replaceAll("[^a-zA-Z0-9_\\-]", "");
                }
            } catch (PackageManager.NameNotFoundException ignore) {
            }
        }
        return !TextUtils.isEmpty(appLabel) ? appLabel : "unknown_app";
    }
}
