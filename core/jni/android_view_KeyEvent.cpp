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
// rust/android_runtime/src/android_view_key_event.rs (which also owns the
// nativeKeyCodeToString/nativeKeyCodeFromString/nativeNextId natives and
// register_android_view_KeyEvent).
//
// The helpers below keep their original mangled C++ signatures because
// external consumers — most importantly libandroid.so's NDK surface
// (AKeyEvent_fromJava / AInputEvent_toJava in native/android/input.cpp) —
// need real android::KeyEvent C++ objects. Only this file touches
// android::KeyEvent; events cross the language boundary as plain
// KeyEventData field bundles (rust/android_runtime/ffi/input_event_compat.h,
// whose static_asserts pin the shared layout).

#include <algorithm>
#include <array>
#include <cstdint>

#include <input/Input.h>
#include <input_event_compat.h>
#include <nativehelper/scoped_local_ref.h>

#include "android_view_KeyEvent.h"

// Implemented in Rust (android_view_key_event.rs), linked into
// libandroid_runtime via libandroid_runtime_rs.
extern "C" jobject rs_keyEvent_toJava(JNIEnv* env, const android_runtime::KeyEventData* data);
extern "C" void rs_keyEvent_fromJava(JNIEnv* env, jobject eventObj,
                                     android_runtime::KeyEventData* outData);
extern "C" int32_t rs_keyEvent_recycle(JNIEnv* env, jobject eventObj);

namespace android {

ScopedLocalRef<jobject> android_view_KeyEvent_obtainAsCopy(JNIEnv* env, const KeyEvent& event) {
    const std::array<uint8_t, 32> hmac = event.getHmac();
    android_runtime::KeyEventData data;
    data.down_time = event.getDownTime();
    data.event_time = event.getEventTime();
    data.id = event.getId();
    data.device_id = event.getDeviceId();
    data.source = static_cast<int32_t>(event.getSource());
    data.display_id = event.getDisplayId().val();
    data.action = event.getAction();
    data.flags = event.getFlags();
    data.key_code = event.getKeyCode();
    data.scan_code = event.getScanCode();
    data.meta_state = event.getMetaState();
    data.repeat_count = event.getRepeatCount();
    std::copy(hmac.begin(), hmac.end(), data.hmac);
    return ScopedLocalRef<jobject>(env, rs_keyEvent_toJava(env, &data));
}

KeyEvent android_view_KeyEvent_obtainAsCopy(JNIEnv* env, jobject eventObj) {
    android_runtime::KeyEventData data;
    rs_keyEvent_fromJava(env, eventObj, &data);
    std::array<uint8_t, 32> hmac;
    std::copy(std::begin(data.hmac), std::end(data.hmac), hmac.begin());
    KeyEvent event;
    event.initialize(data.id, data.device_id, static_cast<uint32_t>(data.source),
                     ui::LogicalDisplayId{data.display_id}, hmac, data.action, data.flags,
                     data.key_code, data.scan_code, data.meta_state, data.repeat_count,
                     data.down_time, data.event_time);
    return event;
}

status_t android_view_KeyEvent_recycle(JNIEnv* env, jobject eventObj) {
    return static_cast<status_t>(rs_keyEvent_recycle(env, eventObj));
}

} // namespace android
