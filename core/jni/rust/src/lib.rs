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
//! Each module holds the JNI natives for one framework class (e.g.
//! [`android_os_system_clock`] backs `android.os.SystemClock`). The
//! `extern "C"` entry points below are called from the `gRegJNI` table in
//! `AndroidRuntime.cpp` (and its host counterpart) and must keep the exact
//! symbol names the C++ runtime expects.

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
mod input_event_ffi;
mod libbase_parse;
mod sysprop_change;

/// Shared body of every `register_*` entry point: bootstraps this crate's
/// logging, borrows the `Env` from the caller's JNI attachment, and runs the
/// module's `register`.
///
/// `register` panics if registration fails; registration failures are fatal,
/// and `with_env_no_catch` lets that panic abort the process rather than
/// swallowing it the way the catching `with_env` would.
fn register_natives(
    mut env: jni::EnvUnowned<'_>,
    register: fn(&mut jni::Env<'_>),
) -> jni::sys::jint {
    init_logging();
    // The closure never returns `Err`; a failed registration panics inside
    // `register`, and `with_env_no_catch` propagates that panic to abort.
    let _ = env.with_env_no_catch(|env| {
        register(env);
        Ok::<(), jni::errors::Error>(())
    });
    0
}

/// Registers `android.os.SystemClock`'s native methods. See
/// `register_natives`.
#[no_mangle]
pub extern "C" fn register_android_os_SystemClock(env: jni::EnvUnowned<'_>) -> jni::sys::jint {
    register_natives(env, android_os_system_clock::register)
}

/// Installs the process-wide `log` backend for this crate's diagnostics, once.
///
/// Called from `register_natives`, which runs on every registration entry
/// during the zygote's `startReg` — before any app code, and long before any
/// diagnostic fires at runtime. libandroid_runtime is mapped into every app
/// process, so its Rust routes through the `log` facade like the rest of the
/// platform rather than calling liblog directly. No fixed tag is set, so each
/// diagnostic's `target` becomes its logcat tag, giving each call site its own
/// tag.
fn init_logging() {
    static INIT: std::sync::Once = std::sync::Once::new();
    INIT.call_once(|| {
        logger::init(logger::Config::default().with_max_level(log::LevelFilter::Info));
    });
}

/// Registers `android.os.SystemProperties`'s native methods. See
/// `register_natives`.
#[no_mangle]
pub extern "C" fn register_android_os_SystemProperties(env: jni::EnvUnowned<'_>) -> jni::sys::jint {
    register_natives(env, android_os_system_properties::register)
}

/// Registers `android.os.Trace`'s native methods. See `register_natives`.
#[no_mangle]
pub extern "C" fn register_android_os_Trace(env: jni::EnvUnowned<'_>) -> jni::sys::jint {
    register_natives(env, android_os_trace::register)
}

/// Registers `android.app.admin.SecurityLog`'s native methods and caches its
/// class IDs. Device-only: the host runtime does not register SecurityLog.
/// See `register_natives`.
#[no_mangle]
pub extern "C" fn register_android_app_admin_SecurityLog(
    env: jni::EnvUnowned<'_>,
) -> jni::sys::jint {
    register_natives(env, android_app_admin_security_log::register)
}

/// Registers `android.util.EventLog`'s native methods and caches its class
/// IDs. Called from the gRegJNI tables of AndroidRuntime.cpp and
/// HostRuntime.cpp. See `register_natives`.
#[no_mangle]
pub extern "C" fn register_android_util_EventLog(env: jni::EnvUnowned<'_>) -> jni::sys::jint {
    register_natives(env, android_util_event_log::register)
}

/// Registers `android.util.Log`'s native methods. See `register_natives`.
#[no_mangle]
pub extern "C" fn register_android_util_Log(env: jni::EnvUnowned<'_>) -> jni::sys::jint {
    register_natives(env, android_util_log::register)
}

/// Registers `android.view.KeyEvent`'s native methods and caches its class
/// IDs. Called from the gRegJNI tables of AndroidRuntime.cpp and
/// HostRuntime.cpp. See `register_natives`.
#[no_mangle]
pub extern "C" fn register_android_view_KeyEvent(env: jni::EnvUnowned<'_>) -> jni::sys::jint {
    register_natives(env, android_view_key_event::register)
}
