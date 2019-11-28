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

import android.os.Build;

import com.android.internal.compat.IAndroidBuildClassifier;

/**
 * Implementation of the IAndroidBuildClassifier Binder interface. Offers information about the
 * type of build installed.
 */
public class AndroidBuildClassifierImpl extends IAndroidBuildClassifier.Stub {

    @Override
    public boolean isDebuggableBuild() {
        return Build.IS_DEBUGGABLE;
    }

    @Override
    public boolean isFinalBuild() {
        return "REL".equals(Build.VERSION.CODENAME);
    }
}
