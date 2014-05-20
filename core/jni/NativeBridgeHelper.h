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

#ifndef _NATIVE_BRIDGE_HELPER_H_
#define _NATIVE_BRIDGE_HELPER_H_

namespace nativebridgehelper {

#include "jni.h"
#include <JNIHelp.h>

#define SYS_LIB_PATH        "/system/lib/"
#define SYS_LIB_PATH_LEN    (sizeof(SYS_LIB_PATH) - 1)

#define SYS_LIB64_PATH      "/system/lib64/"
#define SYS_LIB64_PATH_LEN  (sizeof(SYS_LIB64_PATH) - 1)

#define IS_64BIT_PROC()     ((sizeof(void*) == 8)?true:false)

#define NBH_ITF_SYM     "native_bridge_helper"
#define PROP_LIB_NBH    "persist.sys.native.bridge.helper"

typedef void (*NBH_ITF_INIT)(JNIEnv *env, jint uid, jstring pkgName);

typedef void (*NBH_ITF_PREPARE)();

typedef void (*NBH_ITF_REPLACE)(JNIEnv *env, jstring pkgName);

typedef void (*NBH_ITF_REMOVE)(jint uid);

typedef void (*NBH_ITF_INSTALL)(JNIEnv *env, jint uid, jstring pkgName, jstring nbhStr);

typedef jint (*NBH_ITF_ADJUST_ABI)(JNIEnv *env, jstring pkgPath, jint nbhInt);

typedef struct nbh_itf_s {
    NBH_ITF_INIT        init;
    NBH_ITF_PREPARE     prepare;
    NBH_ITF_REPLACE     notifyReplacePkg;
    NBH_ITF_REMOVE      notifyRemovePkg;
    NBH_ITF_INSTALL     notifyInstallPkg;
    NBH_ITF_ADJUST_ABI  adjustAbiDecision;
} nbh_itf_t;

};

#endif
