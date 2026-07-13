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

//! Rust conversion of `android_app_admin_SecurityLog.cpp`: the
//! `android.app.admin.SecurityLog` natives over the security buffer. All
//! logic lives in [`crate::event_log_helper`]; this module contributes the
//! `SecurityLog$SecurityEvent` IDs, `isLoggingEnabled`, and the registration
//! table. Like its C++ predecessor it is only registered on device
//! (`AndroidRuntime.cpp`); the host runtime never mentions SecurityLog.

use crate::event_log_helper::{self, EventLog};
use jni::objects::JMethodID;
use jni::sys::jmethodID;
use log_buffer::LogBuffer;

/// Cached IDs for `android.app.admin.SecurityLog$SecurityEvent`, the class
/// read entries are returned as.
#[jni_macros::java_class("android/app/admin/SecurityLog$SecurityEvent")]
pub struct SecurityEvent {
    #[method("(byte[]) -> void", name = "<init>")]
    ctor: jmethodID,
}

/// The security buffer bound to `SecurityLog$SecurityEvent` — the C++
/// `EventLogHelper<LOG_ID_SECURITY, kSecurityLogEventClass>` instantiation.
fn security() -> EventLog {
    let ids = SecurityEvent::get();
    // SAFETY: `ctor` was cached by SecurityEvent::init, so it is a valid,
    // non-null method ID for the lifetime of the process.
    EventLog::new(LogBuffer::Security, &ids.class, unsafe { JMethodID::from_raw(ids.ctor) })
}

/// Registers `android.app.admin.SecurityLog`'s native methods and caches
/// every class and ID the implementation needs.
///
/// Call during JNI startup. Panics if a class or member is missing, matching
/// the C++ `RegisterMethodsOrDie` semantics.
pub fn register(env: &mut jni::JNIEnv<'_>) {
    event_log_helper::init(env);
    SecurityEvent::init(env);
    security_log::register(env);
}

/// The `android.app.admin.SecurityLog` native methods.
#[jni_macros::jni_module("android/app/admin/SecurityLog")]
pub mod security_log {
    use super::security;
    use jni::objects::{JObject, JObjectArray};
    use jni::sys::jclass;
    use jni_support::JniError;
    use log_reader::ReadMode;
    use std::os::raw::c_int;

    extern "C" {
        // From liblog; the C++ used it via <private/android_logger.h>.
        fn __android_log_security() -> c_int;
    }

    /// Regular: "()Z"
    ///
    /// Whether security logging is enabled: liblog's cached view of the
    /// device-owner-controlled `persist.logd.security` property.
    #[jni_method]
    fn isLoggingEnabled(_env: &mut jni::JNIEnv<'_>, _clazz: jclass) -> bool {
        // SAFETY: No preconditions; reads liblog's cached property state.
        (unsafe { __android_log_security() }) != 0
    }

    /// Regular: "(I[Ljava/lang/Object;)I"
    #[jni_method]
    fn writeEvent(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        tag: i32,
        #[class = "java/lang/Object"] payloads: &JObjectArray,
    ) -> i32 {
        security().write_array(env, tag, payloads)
    }

    /// Regular: "(Ljava/util/Collection;)V"
    #[jni_method]
    fn readEvents(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        #[class = "java/util/Collection"] out: &JObject,
    ) {
        if out.as_raw().is_null() {
            JniError::NullPointer("").throw_on(env);
            return;
        }
        security().read_events(env, ReadMode::non_blocking(), None, 0, out);
    }

    /// Regular: "(JLjava/util/Collection;)V"
    #[jni_method]
    fn readEventsSince(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        timestamp: i64,
        #[class = "java/util/Collection"] out: &JObject,
    ) {
        if out.as_raw().is_null() {
            JniError::NullPointer("").throw_on(env);
            return;
        }
        security().read_events(env, ReadMode::non_blocking(), None, timestamp, out);
    }

    /// Regular: "(Ljava/util/Collection;)V"
    ///
    /// Reads the entries preserved in the pstore from before the last reboot.
    #[jni_method]
    fn readPreviousEvents(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        #[class = "java/util/Collection"] out: &JObject,
    ) {
        if out.as_raw().is_null() {
            JniError::NullPointer("").throw_on(env);
            return;
        }
        security().read_events(env, ReadMode::non_blocking().from_pstore(), None, 0, out);
    }

    /// Regular: "(JLjava/util/Collection;)V"
    ///
    /// Blocks until events logged after `timestamp` are about to be
    /// overwritten (or liblog's two-hour wrap timeout expires), then dumps.
    #[jni_method]
    fn readEventsOnWrapping(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        timestamp: i64,
        #[class = "java/util/Collection"] out: &JObject,
    ) {
        if out.as_raw().is_null() {
            JniError::NullPointer("").throw_on(env);
            return;
        }
        security().read_events(env, ReadMode::non_blocking().wake_on_wrap(), None, timestamp, out);
    }
}
