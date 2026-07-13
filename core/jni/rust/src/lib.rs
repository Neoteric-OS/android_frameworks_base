// Copyright (C) 2026 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Rust implementations of `libandroid_runtime`'s JNI methods.
//!
//! Components are converted from C++ one at a time; each module mirrors the
//! C++ file it replaces (e.g. [`android_os_system_clock`] replaces
//! `android_os_SystemClock.cpp`). The `extern "C"` entry points below are
//! called from the `gRegJNI` table in `AndroidRuntime.cpp` (and its host
//! counterpart) and must keep the exact symbol names the C++ runtime expects.

// JNI registration entry points and native methods use Java-style names.
#![allow(non_snake_case)]

mod android_os_system_clock;

/// Called from AndroidRuntime.cpp's gRegJNI table.
///
/// Registers `android.os.SystemClock`'s native methods and returns 0. Panics
/// if registration fails, matching the C++ `RegisterMethodsOrDie` semantics.
///
/// # Safety
///
/// `env` must be a valid, non-null `JNIEnv` pointer for the current thread.
#[no_mangle]
pub unsafe extern "C" fn register_android_os_SystemClock(
    env: *mut jni::sys::JNIEnv,
) -> jni::sys::jint {
    // SAFETY: The caller (AndroidRuntime::startReg) passes a valid JNIEnv.
    let mut env = unsafe { jni::JNIEnv::from_raw(env) }.expect("null JNIEnv");
    android_os_system_clock::register(&mut env);
    0
}
