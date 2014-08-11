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

#define LOG_TAG "NativeBridgeHelper"

#include "jni.h"
#include <JNIHelp.h>
#include <cutils/properties.h>
#include <utils/Mutex.h>
#include "android_runtime/AndroidRuntime.h"
#include <dlfcn.h>
#include <string.h>

namespace android {

// Native bridge helper interfaces
struct NativeBridgeHelperCallbacks {
    // Prepare the system environment for native bridge during system
    // initialization.
    //
    // Parameters:
    //      None
    // Returns:
    //      None
    void (*prepare          )();

    // Load and initialize the native bridge if need to launch the app with
    // native bridge support.
    //
    // Parameters:
    //      env             [IN] pointer to JNIEnv
    //      uid             [IN] the app uid
    //      abiStr          [IN] the app required Abi string value
    //      pkgName         [IN] the app package name
    //      niceName        [IN] the process nice name
    //      privateDirPath  [IN] the app private dir path for native bridge
    // Returns:
    //      None
    void (*init             )(JNIEnv* env, jint uid, jstring abiStr,
            jstring pkgName, jstring niceName, jstring privateDirPath);

    // Apply additional considerations when selecting which ABI version
    // of library to install from package
    //
    // Parameters:
    //      env             [IN] pointer to JNIEnv
    //      apkHandle       [IN] the app apk handle
    //      abiInt          [IN] index to ABI chosen thus far or error code
    // Returns:
    //      index to adjusted Abi or error code
    jint (*adjustAbiDecision)(JNIEnv* env, jlong apkHandle, jint abiInt);
};

static struct NativeBridgeHelperCallbacks* gNativeBridgeHelperCallbacks = NULL;

static bool loadNativeBridgeHelper()
{
    static Mutex callbacksLock;
    static bool Unavailable = false;

    Mutex::Autolock l(callbacksLock);
    if (gNativeBridgeHelperCallbacks != NULL)
        return true;

    if (Unavailable)
        return false;

    char propBuf[PROPERTY_VALUE_MAX];
    property_get("persist.enable.native.bridge", propBuf, "false");
    if (strcmp(propBuf, "true") != 0) {
        Unavailable = true;
        return false;
    }

    void* handle = dlopen("libnativebridgehelper.so", RTLD_LAZY);
    if (handle == NULL) {
        Unavailable = true;
        return false;
    }

    gNativeBridgeHelperCallbacks =
        reinterpret_cast<NativeBridgeHelperCallbacks*>(dlsym(handle, "NativeBridgeHelperItf"));
    if (gNativeBridgeHelperCallbacks == NULL) {
        Unavailable = true;
        dlclose(handle);
        return false;
    }

    return true;
}

static void com_android_internal_content_NativeBridgeHelper_prepare(JNIEnv* env, jclass clazz)
{
    if (!loadNativeBridgeHelper())
        return;

    gNativeBridgeHelperCallbacks->prepare();
}

static void com_android_internal_content_NativeBridgeHelper_init(JNIEnv* env, jclass clazz,
        jint uid, jstring abiStr, jstring pkgName, jstring niceName, jstring privateDirPath)
{
    if (!loadNativeBridgeHelper())
        return;

    gNativeBridgeHelperCallbacks->init(env, uid, abiStr, pkgName, niceName, privateDirPath);
}

static jint com_android_internal_content_NativeBridgeHelper_adjustAbiDecision(JNIEnv* env,
        jclass clazz, jlong apkHandle, jint abiInt)
{
    if (!loadNativeBridgeHelper())
        return abiInt;

    return gNativeBridgeHelperCallbacks->adjustAbiDecision(env, apkHandle, abiInt);
}

/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "prepare", "()V",
        (void*) com_android_internal_content_NativeBridgeHelper_prepare },
    { "init", "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;;Ljava/lang/String;)V",
        (void*) com_android_internal_content_NativeBridgeHelper_init },
    { "adjustAbiDecision", "(LI)I",
        (void*) com_android_internal_content_NativeBridgeHelper_adjustAbiDecision }
};

int register_com_android_internal_content_NativeBridgeHelper(JNIEnv* env)
{
    return AndroidRuntime::registerNativeMethods(env,
                "com/android/internal/content/NativeBridgeHelper", gMethods, NELEM(gMethods));
}

}; // namespace android
