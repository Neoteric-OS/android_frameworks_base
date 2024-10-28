/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.utils;

import com.android.internal.annotations.Keep;

/**
 * Utility class for lazily registering native methods for a given class.
 *
 * <p><strong>Note: </strong>Most native methods are registered eagerly via
 * `JNI_OnLoad` when system server loads its primary native lib. However, some
 * classes within system server may be stripped if unused. This class offers a
 * way to selectively register their native methods. Such register calls should
 * typically be done from that class's `static {}` init block.
 */
@Keep
public final class JniRegistrar {

    // Note: `{@link SystemServer#run} is responsible for loading "android_servers", so no need
    // to do so here. Classes that use this registration should never be initialized before this.

    /** Registers native methods for ConsumerIrService. */
    public static native void registerConsumerIrService();

    /** Registers native methods for SerialService. */
    public static native void registerSerialService();

    /** Registers native methods for VrManagerService. */
    public static native void registerVrManagerService();
}
