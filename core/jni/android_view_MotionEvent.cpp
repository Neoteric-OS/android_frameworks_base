/*
 * Copyright (C) 2010 The Android Open Source Project
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

// Permanent facade over the Rust implementation in
// rust/android_runtime/src/android_view_motion_event.rs (which owns the
// android.view.MotionEvent natives and register_android_view_MotionEvent).
//
// The helpers below keep their original mangled C++ signatures because
// external consumers — most importantly libandroid.so's NDK surface
// (AMotionEvent_fromJava / AInputEvent_toJava in native/android/input.cpp) —
// need real android::MotionEvent C++ objects. Events cross the language
// boundary as raw MotionEvent* pointers: every one is allocated with C++
// new (here or in the Rust module's bridge shims) and freed with C++
// delete, and a Java event's mNativePtr owns exactly one of them.

#include "android_view_MotionEvent.h"

#include <input/Input.h>
#include <nativehelper/scoped_local_ref.h>

#include <memory>

// Implemented in Rust (android_view_motion_event.rs), linked into
// libandroid_runtime via libandroid_runtime_rs.
extern "C" __attribute__((visibility("hidden"))) android::MotionEvent*
rs_motionEvent_getNativePtr(JNIEnv* env, jobject eventObj);
extern "C" __attribute__((visibility("hidden"))) jobject
rs_motionEvent_obtainFromNative(JNIEnv* env, android::MotionEvent* event);

namespace android {

MotionEvent* android_view_MotionEvent_getNativePtr(JNIEnv* env, jobject eventObj) {
    return rs_motionEvent_getNativePtr(env, eventObj);
}

ScopedLocalRef<jobject> android_view_MotionEvent_obtainAsCopy(JNIEnv* env,
                                                              const MotionEvent& event) {
    std::unique_ptr<MotionEvent> destEvent = std::make_unique<MotionEvent>();
    destEvent->copyFrom(&event, true);
    return android_view_MotionEvent_obtainFromNative(env, std::move(destEvent));
}

ScopedLocalRef<jobject> android_view_MotionEvent_obtainFromNative(
        JNIEnv* env, std::unique_ptr<MotionEvent> event) {
    // The Rust side handles a null event (returns null) and aborts, like the
    // old LOG_ALWAYS_FATAL, if MotionEvent.obtain() fails.
    return ScopedLocalRef<jobject>(env, rs_motionEvent_obtainFromNative(env, event.release()));
}

} // namespace android
