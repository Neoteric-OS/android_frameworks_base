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
// src/sysprop_change.rs; see that file for the semantics.
namespace android_runtime {

// Registers android_runtime's Rust sysprop-change callback (the cxx-exposed
// on_sysprop_change()) with libutils at the given priority.
void register_sysprop_change_callback(int32_t priority);

// Forwards to android::report_sysprop_change() in libutils.
void report_sysprop_change();

}  // namespace android_runtime
