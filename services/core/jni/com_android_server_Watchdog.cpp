/*
 * Copyright (C) 2021 The Android Open Source Project
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

#define LOG_TAG "Watchdog"

#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedUtfChars.h>
#include "android-base/logging.h"
#include "android_runtime/AndroidRuntime.h"
#include "core_jni_helpers.h"
#include "jni.h"

#include <android/userpanic.h>

#include <utils/Log.h>
#include <utils/misc.h>

namespace android {

static void nativePanic(JNIEnv *env, jobject /* clazz */, jstring jreason) {
    ScopedUtfChars reasonStr(env, jreason);
    const char *reason = reasonStr.c_str() ?: "watchdog:reason=unknown";

    android_panic_kernel(reason);
}

static const JNINativeMethod method_table[] = {
    {"panic", "(Ljava/lang/String;)V", (void*) nativePanic},
};

int register_android_server_Watchdog(JNIEnv *env) {
    return jniRegisterNativeMethods(env, "com/android/server/Watchdog",
                                    method_table, NELEM(method_table));
}

}; // namespace android
