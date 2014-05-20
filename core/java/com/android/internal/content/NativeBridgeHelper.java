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

    /** @hide */
    public static final boolean ENABLE_NBH =
        SystemProperties.getBoolean("persist.enable.native.bridge", false);

    public static native void prepare();

    public static native void init(int uid, String pkgName);

    public static native void notifyRemovePkg(int uid);

    public static native void notifyInstallPkg(int uid, String pkgName, String nbhStr);

    public static native void notifyReplacePkg(String pkgName);

    public static native int adjustAbiDecision(String pkgPath, int nbhInt);
}
