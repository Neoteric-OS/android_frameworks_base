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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

class ApplicationBuilder {
    private boolean mIsDebuggable;
    private int mTargetSdk;
    private String mPackageName;

    private ApplicationBuilder() {
        mTargetSdk = -1;
    }

    static ApplicationBuilder create() {
        return new ApplicationBuilder();
    }

    ApplicationBuilder withTargetSdk(int targetSdk) {
        mTargetSdk = targetSdk;
        return this;
    }

    ApplicationBuilder debuggable() {
        mIsDebuggable = true;
        return this;
    }

    ApplicationBuilder withPackageName(String packageName) {
        mPackageName = packageName;
        return this;
    }

    ApplicationInfo toApplicationInfo() {
        final ApplicationInfo applicationInfo = new ApplicationInfo();
        if (mIsDebuggable) {
            applicationInfo.flags |= ApplicationInfo.FLAG_DEBUGGABLE;
        }
        applicationInfo.packageName = mPackageName;
        applicationInfo.targetSdkVersion = mTargetSdk;
        return applicationInfo;
    }

    void inject(PackageManager pm) throws PackageManager.NameNotFoundException {
        final ApplicationInfo applicationInfo = toApplicationInfo();
        when(pm.getApplicationInfo(eq(mPackageName), anyInt()))
                .thenReturn(applicationInfo);
    }
}
