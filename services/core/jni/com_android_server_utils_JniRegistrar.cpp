/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include <nativehelper/JNIHelp.h>

#include "jni.h"

namespace android {

// Forward declared per-class registration methods.
int register_android_server_ConsumerIrService(JNIEnv* env);
int register_android_server_SerialService(JNIEnv* env);
int register_android_server_vr_VrManagerService(JNIEnv* env);

namespace {

// TODO: Remove these trampoline methods after finalizing the registrar
// implementation, instead just update the called methods to take a class arg
// and hand them to jniRegisterNativeMethods directly.
void nativeRegisterConsumerIrService(JNIEnv* env, jclass) {
    register_android_server_ConsumerIrService(env);
}

void nativeRegisterSerialService(JNIEnv* env, jclass) {
    register_android_server_SerialService(env);
}

void nativeRegisterVrManagerService(JNIEnv* env, jclass) {
    register_android_server_vr_VrManagerService(env);
}

static const JNINativeMethod sJniRegistrarMethods[] = {
        {"nativeRegisterConsumerIrService", "()V", (void*)nativeRegisterConsumerIrService},
        {"nativeRegisterSerialService", "()V;", (void*)nativeRegisterSerialService},
        {"nativeRegisterVrManagerService", "()V", (void*)nativeRegisterVrManagerService},
};

} // namespace

int register_android_server_utils_JniRegistrar(JNIEnv* env) {
    return jniRegisterNativeMethods(env, "com/android/server/utils/JniRegistrar",
                                    sJniRegistrarMethods, NELEM(sJniRegistrarMethods));
}

} // namespace android
