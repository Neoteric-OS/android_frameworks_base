/*
 * Copyright (C) 2026 The Android Open Source Project
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

#include <jni.h>

#include <cstdint>

#include <input/Input.h>

#include "motion_event.h"

namespace android {
// Declared in core/jni/android_os_Parcel.h and defined in libandroid_runtime,
// which this static library links into. The bridge cannot include that
// header without a circular dependency on libandroid_runtime's exported
// include directories, so the (long-stable) signature is repeated here.
extern Parcel* parcelForJavaObject(JNIEnv* env, jobject obj);
}  // namespace android

// This translation unit must stay separate from motion_event.cpp: it is the
// only part of the bridge that references parcelForJavaObject, and keeping
// it in its own object file means binaries that never call these functions
// (the android_runtime_rs_test host binary) do not pull in an undefined
// reference to libandroid_runtime.
namespace android_runtime {

extern "C" int32_t android_runtime_motion_event_read_from_parcel(android::MotionEvent* event,
                                                                 JNIEnv* env, jobject parcel_obj) {
    android::Parcel* parcel = android::parcelForJavaObject(env, parcel_obj);
    return event->readFromParcel(parcel);
}

extern "C" int32_t android_runtime_motion_event_write_to_parcel(const android::MotionEvent* event,
                                                                JNIEnv* env, jobject parcel_obj) {
    android::Parcel* parcel = android::parcelForJavaObject(env, parcel_obj);
    return event->writeToParcel(parcel);
}

}  // namespace android_runtime
