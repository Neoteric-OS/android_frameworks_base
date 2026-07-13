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

#pragma once

#include <cstddef>
#include <cstdint>
#include <type_traits>

// C++ mirror of src/input_event_compat.rs: plain-data
// bundles that cross the C++/Rust boundary by layout, not through the cxx
// bridge. Both declarations carry the same layout assertions, so a change to
// either side fails to compile until both agree.
namespace android_runtime {

// Every field of an android::KeyEvent, flattened so the permanent JNI facade
// (core/jni/android_view_KeyEvent.cpp) and the Rust implementation
// (src/android_view_key_event.rs) can exchange events
// without sharing libinput's C++ types. The 64-bit times lead so the struct
// packs without padding; `source` is InputEvent's uint32_t source
// reinterpreted as the Java int it round-trips through, and the times are in
// nanoseconds.
struct KeyEventData {
    int64_t down_time;
    int64_t event_time;
    int32_t id;
    int32_t device_id;
    int32_t source;
    int32_t display_id;
    int32_t action;
    int32_t flags;
    int32_t key_code;
    int32_t scan_code;
    int32_t meta_state;
    int32_t repeat_count;
    uint8_t hmac[32];
};

static_assert(std::is_trivial_v<KeyEventData>);
static_assert(std::is_standard_layout_v<KeyEventData>);
static_assert(sizeof(KeyEventData) == 88);
// i386 aligns int64_t to 4, so pin the alignment to the ABI rather than a
// fixed value; Rust's repr(C) follows the same platform rule.
static_assert(alignof(KeyEventData) == alignof(int64_t));
static_assert(offsetof(KeyEventData, down_time) == 0);
static_assert(offsetof(KeyEventData, event_time) == 8);
static_assert(offsetof(KeyEventData, id) == 16);
static_assert(offsetof(KeyEventData, device_id) == 20);
static_assert(offsetof(KeyEventData, source) == 24);
static_assert(offsetof(KeyEventData, display_id) == 28);
static_assert(offsetof(KeyEventData, action) == 32);
static_assert(offsetof(KeyEventData, flags) == 36);
static_assert(offsetof(KeyEventData, key_code) == 40);
static_assert(offsetof(KeyEventData, scan_code) == 44);
static_assert(offsetof(KeyEventData, meta_state) == 48);
static_assert(offsetof(KeyEventData, repeat_count) == 52);
static_assert(offsetof(KeyEventData, hmac) == 56);

}  // namespace android_runtime
