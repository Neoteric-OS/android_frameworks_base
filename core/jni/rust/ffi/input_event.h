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

#include <cstdint>

// C++ half of the cxx bridge declared in
// src/input_event_ffi.rs; see that file for the semantics.
namespace android_runtime {

// KeyEvent::getLabel: the symbolic label for an Android key code, or null
// when the key code has none. Non-null labels point into libinput's static
// keycode table.
const char* key_event_get_label(int32_t key_code);

// KeyEvent::getKeyCodeFromLabel with the JNI's AKEYCODE_UNKNOWN fallback
// folded in. `label` must be a valid NUL-terminated string.
int32_t key_event_get_key_code_from_label(const char* label);

// InputEvent::nextId(): a fresh id for a new input event.
int32_t input_event_next_id();

}  // namespace android_runtime
