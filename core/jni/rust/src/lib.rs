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

mod android_app_admin_security_log;
mod android_os_system_clock;
mod android_os_system_properties;
mod android_os_trace;
mod android_util_event_log;
mod android_util_log;
mod android_view_key_event;
mod event_log_helper;
mod event_wire_format;
mod input_event_compat;
mod input_event_ffi;
mod libbase_parse;
mod sysprop_change;

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
    init_logging();
    // SAFETY: The caller (AndroidRuntime::startReg) passes a valid JNIEnv.
    let mut env = unsafe { jni::JNIEnv::from_raw(env) }.expect("null JNIEnv");
    android_os_system_clock::register(&mut env);
    0
}

/// Installs the process-wide `log` backend for this crate's diagnostics, once.
///
/// Called from the first registration entry, which runs during the zygote's
/// `startReg` — before any app code, and long before any diagnostic fires at
/// runtime. libandroid_runtime is mapped into every app process, so its Rust
/// routes through the `log` facade like the rest of the platform rather than
/// calling liblog directly. No fixed tag is set, so each diagnostic's `target`
/// becomes its logcat tag, preserving the per-site tags the C++ used.
fn init_logging() {
    static INIT: std::sync::Once = std::sync::Once::new();
    INIT.call_once(|| {
        logger::init(logger::Config::default().with_max_level(log::LevelFilter::Info));
    });
}

/// Called from AndroidRuntime.cpp's gRegJNI table.
///
/// Registers `android.os.SystemProperties`'s native methods and returns 0.
/// Panics if registration fails, matching the C++ `RegisterMethodsOrDie`
/// semantics.
///
/// # Safety
///
/// `env` must be a valid, non-null `JNIEnv` pointer for the current thread.
#[no_mangle]
pub unsafe extern "C" fn register_android_os_SystemProperties(
    env: *mut jni::sys::JNIEnv,
) -> jni::sys::jint {
    // SAFETY: The caller (AndroidRuntime::startReg) passes a valid JNIEnv.
    let mut env = unsafe { jni::JNIEnv::from_raw(env) }.expect("null JNIEnv");
    android_os_system_properties::register(&mut env);
    0
}

/// Called from AndroidRuntime.cpp's gRegJNI table.
///
/// Registers `android.os.Trace`'s native methods and returns 0. Panics if
/// registration fails, matching the C++ `RegisterMethodsOrDie` semantics.
///
/// # Safety
///
/// `env` must be a valid, non-null `JNIEnv` pointer for the current thread.
#[no_mangle]
pub unsafe extern "C" fn register_android_os_Trace(env: *mut jni::sys::JNIEnv) -> jni::sys::jint {
    // SAFETY: The caller (AndroidRuntime::startReg) passes a valid JNIEnv.
    let mut env = unsafe { jni::JNIEnv::from_raw(env) }.expect("null JNIEnv");
    android_os_trace::register(&mut env);
    0
}

/// Called from AndroidRuntime.cpp's gRegJNI table (device-only; the host
/// runtime does not register SecurityLog).
///
/// Registers `android.app.admin.SecurityLog`'s native methods, caches its
/// class IDs, and returns 0. Panics if registration fails, matching the C++
/// `RegisterMethodsOrDie` semantics.
///
/// # Safety
///
/// `env` must be a valid, non-null `JNIEnv` pointer for the current thread.
#[no_mangle]
pub unsafe extern "C" fn register_android_app_admin_SecurityLog(
    env: *mut jni::sys::JNIEnv,
) -> jni::sys::jint {
    // SAFETY: The caller (AndroidRuntime::startReg) passes a valid JNIEnv.
    let mut env = unsafe { jni::JNIEnv::from_raw(env) }.expect("null JNIEnv");
    android_app_admin_security_log::register(&mut env);
    0
}

/// Called from the gRegJNI tables of AndroidRuntime.cpp and HostRuntime.cpp.
///
/// Registers `android.util.EventLog`'s native methods, caches its class IDs,
/// and returns 0. Panics if registration fails, matching the C++
/// `RegisterMethodsOrDie` semantics.
///
/// # Safety
///
/// `env` must be a valid, non-null `JNIEnv` pointer for the current thread.
#[no_mangle]
pub unsafe extern "C" fn register_android_util_EventLog(
    env: *mut jni::sys::JNIEnv,
) -> jni::sys::jint {
    // SAFETY: The caller (AndroidRuntime::startReg) passes a valid JNIEnv.
    let mut env = unsafe { jni::JNIEnv::from_raw(env) }.expect("null JNIEnv");
    android_util_event_log::register(&mut env);
    0
}

/// Called from AndroidRuntime.cpp's gRegJNI table.
///
/// Registers `android.util.Log`'s native methods, caches `Log.VERBOSE` for
/// [`android_util_log::android_util_Log_isVerboseLogEnabled`], and returns 0.
/// Panics if registration fails, matching the C++ `RegisterMethodsOrDie`
/// semantics.
///
/// # Safety
///
/// `env` must be a valid, non-null `JNIEnv` pointer for the current thread.
#[no_mangle]
pub unsafe extern "C" fn register_android_util_Log(env: *mut jni::sys::JNIEnv) -> jni::sys::jint {
    // SAFETY: The caller (AndroidRuntime::startReg) passes a valid JNIEnv.
    let mut env = unsafe { jni::JNIEnv::from_raw(env) }.expect("null JNIEnv");
    android_util_log::register(&mut env);
    0
}

/// Called from the gRegJNI tables of AndroidRuntime.cpp and HostRuntime.cpp.
///
/// Registers `android.view.KeyEvent`'s native methods, caches its class IDs,
/// and returns 0. Panics if registration fails, matching the C++
/// `RegisterMethodsOrDie` semantics.
///
/// # Safety
///
/// `env` must be a valid, non-null `JNIEnv` pointer for the current thread.
#[no_mangle]
pub unsafe extern "C" fn register_android_view_KeyEvent(
    env: *mut jni::sys::JNIEnv,
) -> jni::sys::jint {
    // SAFETY: The caller (AndroidRuntime::startReg) passes a valid JNIEnv.
    let mut env = unsafe { jni::JNIEnv::from_raw(env) }.expect("null JNIEnv");
    android_view_key_event::register(&mut env);
    0
}
