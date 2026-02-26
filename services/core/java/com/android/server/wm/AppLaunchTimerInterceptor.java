/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.wm;

import static android.app.ActivityManager.INTENT_SENDER_ACTIVITY;
import static android.app.PendingIntent.FLAG_CANCEL_CURRENT;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_ONE_SHOT;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.annotation.Nullable;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.SystemProperties;

/**
 * Intercepts activity launches to show a timer screen before proceeding.
 */
public class AppLaunchTimerInterceptor extends ActivityInterceptorCallback {

    // System property to specify which package to intercept.
    // Example: setprop debug.app_launch_timer.package com.android.settings
    private static final String PROPERTY_INTERCEPT_PACKAGE = "debug.app_launch_timer.package";
    private static final String TIMER_ACTIVITY_COMPONENT = "com.android.internal.app/.AppLaunchTimerActivity";
    public static final String EXTRA_ORIGINAL_INTENT = "com.android.internal.app.EXTRA_ORIGINAL_INTENT";

    private final ActivityTaskManagerService mService;

    public AppLaunchTimerInterceptor(ActivityTaskManagerService service) {
        mService = service;
    }

    @Override
    public @Nullable ActivityInterceptResult intercept(ActivityInterceptorInfo info) {
        String targetPackage = SystemProperties.get(PROPERTY_INTERCEPT_PACKAGE);
        if (targetPackage.isEmpty()) {
            return null;
        }

        if (info.aInfo == null || !targetPackage.equals(info.aInfo.packageName)) {
            return null;
        }

        // Prevent infinite loop if the timer activity itself is being launched or is part of the package
        if (info.intent.getComponent() != null &&
                info.intent.getComponent().flattenToShortString().equals(TIMER_ACTIVITY_COMPONENT)) {
            return null;
        }

        // Create a PendingIntent for the original launch
        // We use the real calling uid to ensure the permission check passes when the timer fires it.
        IntentSender target = mService.getIntentSenderLocked(
                INTENT_SENDER_ACTIVITY, info.callingPackage, info.callingFeatureId,
                info.callingUid, info.userId, null /* token */, null /* resultCode */,
                0 /* requestCode */, new Intent[]{ info.intent },
                new String[]{ info.resolvedType },
                FLAG_CANCEL_CURRENT | FLAG_ONE_SHOT | FLAG_IMMUTABLE, null /* bOptions */);

        // Create the interception intent
        Intent newIntent = new Intent();
        newIntent.setComponent(android.content.ComponentName.unflattenFromString(TIMER_ACTIVITY_COMPONENT));
        newIntent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        newIntent.putExtra(EXTRA_ORIGINAL_INTENT, new android.app.PendingIntent(target));

        // Use basic options for the timer activity
        ActivityOptions options = info.checkedOptions != null ? info.checkedOptions : ActivityOptions.makeBasic();

        return new ActivityInterceptResult(newIntent, options);
    }
}
