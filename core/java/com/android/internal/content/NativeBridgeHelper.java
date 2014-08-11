/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.internal.content;

import android.os.SystemProperties;
import android.util.Slog;

/**
 * Native bridge helper.
 *
 * @hide
 */
public final class NativeBridgeHelper {
    private static final String TAG = "NativeBridgeHelper";

    /**
     * The constant initialized by a persist property to enable/disable
     * native bridge.
     * @hide
     */
    public static final boolean ENABLE_NBH =
        SystemProperties.getBoolean("persist.enable.native.bridge", false);

    /**
     * Prepare the system environment for native bridge during system
     * initialization.
     * @hide
     */
    public static native void prepare();

    /**
     * Load and initialize the native bridge if need to launch the app
     * with native bridge support.
     * @hide
     */
    public static native void init(int uid, String abiStr, String pkgName,
            String niceName,String privateDirPath);

    /**
     * Apply additional considerations when selecting which ABI version
     * of library to install from package
     * @hide
     */
    public static native int adjustAbiDecision(long apkHandle, int abiInt);
}
