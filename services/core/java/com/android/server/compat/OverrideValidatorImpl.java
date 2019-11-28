/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.compat;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;;
import android.os.RemoteException;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.compat.IAndroidBuildClassifier;
import com.android.internal.compat.IOverrideValidator;

/**
 * Implementation of the policy for allowing compat change overrides.
 */
public class OverrideValidatorImpl extends IOverrideValidator.Stub {

    private IAndroidBuildClassifier mAndroidBuildClassifier;
    private PackageManager mPackageManager;
    private CompatConfig mCompatConfig;

    @VisibleForTesting
    OverrideValidatorImpl(IAndroidBuildClassifier androidBuildClassifier,
                          PackageManager packageManager, CompatConfig config) {
        mAndroidBuildClassifier = androidBuildClassifier;
        mPackageManager = packageManager;
        mCompatConfig = config;
    }

    @Override
    public boolean allowOverride(long changeId, String packageName) {
        boolean debuggableBuild = false;
        boolean finalBuild = false;
        try {
            debuggableBuild = mAndroidBuildClassifier.isDebuggableBuild();
            finalBuild = mAndroidBuildClassifier.isFinalBuild();
        } catch (RemoteException e) {
            // Should never happen, as they're in the same process.
            throw new RuntimeException("Could not call build classifier!", e);
        }
        // Allow any override for userdebug or eng builds.
        if (debuggableBuild) {
            return true;
        }

        ApplicationInfo applicationInfo;
        try {
            applicationInfo = mPackageManager.getApplicationInfo(packageName, 0);
        } catch (NameNotFoundException e) {
            return false;
        }

        // Only allow overriding debuggable apps.
        if ((applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            return false;
        }
        int minTargetSdk = mCompatConfig.minTargetSdkForChangeId(changeId);
        // Do not allow overriding non-target sdk gated changes on user builds
        if (minTargetSdk == -1) {
            return false;
        }
        // Allow overriding any change for debuggable apps on non-final builds.
        if (!finalBuild) {
            return true;
        }
        // Only allow to opt-in for a targetSdk gated change.
        if (applicationInfo.targetSdkVersion < minTargetSdk) {
            return true;
        }
        return false;
    }
}
