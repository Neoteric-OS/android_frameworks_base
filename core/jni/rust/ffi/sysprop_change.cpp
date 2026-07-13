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

#include "sysprop_change.h"

// cxx-generated declarations for the bridge in
// src/sysprop_change.rs, including on_sysprop_change().
#include "sysprop_change.rs.h"

namespace android_runtime {
namespace {

// libutils takes a plain function pointer; this trampoline calls the
// cxx-exposed Rust callback.
void callOnSyspropChange() {
    on_sysprop_change();
}

}  // namespace

void register_sysprop_change_callback(int32_t priority) {
    android::add_sysprop_change_callback(&callOnSyspropChange, priority);
}

}  // namespace android_runtime
