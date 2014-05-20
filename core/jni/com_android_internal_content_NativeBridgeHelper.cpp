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
#include "NativeBridgeHelper.h"
#include <cutils/properties.h>
#include "android_runtime/AndroidRuntime.h"
#include <sys/types.h>
#include <dlfcn.h>
#include <string.h>

namespace android {

#ifdef WITH_NATIVE_BRIDGE

/*
 * Static variables.
 */
static nativebridgehelper::nbh_itf_t * nbh_itf = NULL;

/*
 * Local functions.
 */
static bool loadNativeBridgeHelper ()
{
    if (IS_64BIT_PROC())
        return false;

    if (nbh_itf != NULL)
        return true;

    char libNbhName[PROPERTY_VALUE_MAX];
    int libNbhNameLen = property_get(PROP_LIB_NBH, libNbhName, "");
    if (libNbhNameLen <= 0)
        return false;

    char * libNbhPath = new char[libNbhNameLen + SYS_LIB_PATH_LEN + 1];
    strncpy (libNbhPath, SYS_LIB_PATH, SYS_LIB_PATH_LEN);
    strncpy (libNbhPath + SYS_LIB_PATH_LEN, libNbhName, libNbhNameLen);
    libNbhPath[libNbhNameLen + SYS_LIB_PATH_LEN] = '\0';

    void * handle = dlopen(libNbhPath, RTLD_LAZY);
    delete (libNbhPath);
    if (handle == NULL)
        return false;

    nbh_itf = (nativebridgehelper::nbh_itf_t*)dlsym(handle, NBH_ITF_SYM);
    if (nbh_itf == NULL)
        return false;

    return true;
}


/*
 * JNI functions.
 */
static void com_android_internal_content_NativeBridgeHelper_init (JNIEnv *env, jclass clazz,
        jint uid, jstring pkgName)
{
    if (!loadNativeBridgeHelper())
        return;

    nbh_itf->init(env, uid, pkgName);
}


static void com_android_internal_content_NativeBridgeHelper_prepare (JNIEnv *env, jclass clazz)
{
    if (!loadNativeBridgeHelper())
        return;

    nbh_itf->prepare();
}


static void com_android_internal_content_NativeBridgeHelper_notifyReplacePkg (JNIEnv *env, jclass clazz,
        jstring pkgName)
{
    if (!loadNativeBridgeHelper())
        return;

    nbh_itf->notifyReplacePkg(env, pkgName);
}


static void com_android_internal_content_NativeBridgeHelper_notifyRemovePkg (JNIEnv *env, jclass clazz, jint uid)
{
    if (!loadNativeBridgeHelper())
        return;

    nbh_itf->notifyRemovePkg(uid);
}


static void com_android_internal_content_NativeBridgeHelper_notifyInstallPkg (JNIEnv *env, jclass clazz, jint uid,
        jstring pkgName, jstring nbhStr)
{
    if (!loadNativeBridgeHelper())
        return;

    nbh_itf->notifyInstallPkg(env, uid, pkgName, nbhStr);
}


static jint com_android_internal_content_NativeBridgeHelper_adjustAbiDecision (JNIEnv *env, jclass clazz,
        jstring pkgPath, jint nbhInt)
{
    if (!loadNativeBridgeHelper())
        return nbhInt;

    return nbh_itf->adjustAbiDecision(env, pkgPath, nbhInt);
}


/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "adjustAbiDecision", "(Ljava/lang/String;I)I",
        (void *) com_android_internal_content_NativeBridgeHelper_adjustAbiDecision },
    { "notifyReplacePkg", "(Ljava/lang/String;)V",
        (void *) com_android_internal_content_NativeBridgeHelper_notifyReplacePkg },
    { "notifyRemovePkg", "(I)V",
        (void *) com_android_internal_content_NativeBridgeHelper_notifyRemovePkg },
    { "notifyInstallPkg", "(ILjava/lang/String;Ljava/lang/String;)V",
        (void *) com_android_internal_content_NativeBridgeHelper_notifyInstallPkg },
    { "prepare", "()V",
        (void *) com_android_internal_content_NativeBridgeHelper_prepare },
    { "init", "(ILjava/lang/String;)V",
        (void *) com_android_internal_content_NativeBridgeHelper_init }
};


int register_com_android_internal_content_NativeBridgeHelper(JNIEnv* env)
{
    return AndroidRuntime::registerNativeMethods(env,
                "com/android/internal/content/NativeBridgeHelper", gMethods, NELEM(gMethods));
}

#else // WITH_NATIVE_BRIDGE

int register_com_android_internal_content_NativeBridgeHelper(JNIEnv* env)
{
    return JNI_OK;
}

#endif

}; // namespace android
