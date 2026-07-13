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

#include "input_event.h"

#include <android/keycodes.h>
#include <input/Input.h>

namespace android_runtime {

const char* key_event_get_label(int32_t key_code) {
    return android::KeyEvent::getLabel(key_code);
}

int32_t key_event_get_key_code_from_label(const char* label) {
    return android::KeyEvent::getKeyCodeFromLabel(label).value_or(AKEYCODE_UNKNOWN);
}

int32_t input_event_next_id() {
    return android::InputEvent::nextId();
}

}  // namespace android_runtime
