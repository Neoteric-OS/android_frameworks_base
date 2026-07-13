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

//! The `android.app.admin.SecurityLog` natives over the security buffer. All
//! logic lives in [`crate::event_log_helper`]; this module contributes the
//! `SecurityLog$SecurityEvent` IDs, `isLoggingEnabled`, and the registration
//! table. It is only registered on device (`AndroidRuntime.cpp`); the host
//! runtime never mentions SecurityLog.

use crate::event_log_helper::{self, EventLog};
use jni::refs::LoaderContext;
use log_buffer::LogBuffer;

// `android.app.admin.SecurityLog$SecurityEvent`, the class read entries are
// returned as. Its `<init>([B)V` constructor wraps an entry's serialized
// bytes.
jni::bind_java_type! {
    SecurityEvent => "android.app.admin.SecurityLog$SecurityEvent",
    constructors {
        /// `SecurityLog$SecurityEvent(byte[])`.
        fn new(bytes: jbyte[]),
    },
}

/// The security buffer bound to `SecurityLog$SecurityEvent`.
fn security() -> EventLog {
    EventLog::new(LogBuffer::Security, |env, bytes| SecurityEvent::new(env, bytes).map(Into::into))
}

/// Registers `android.app.admin.SecurityLog`'s native methods and caches
/// every class and ID the implementation needs.
///
/// Call during JNI startup. Panics if a class or member is missing;
/// registration failures are fatal.
pub fn register(env: &mut jni::Env<'_>) {
    event_log_helper::init(env);
    SecurityEventAPI::get(env, &LoaderContext::None)
        .expect("Failed to initialize android.app.admin.SecurityLog$SecurityEvent binding");
    security_log::register(env);
}

/// The `android.app.admin.SecurityLog` native methods.
#[jni_platform_macros::jni_module("android/app/admin/SecurityLog")]
pub mod security_log {
    use super::security;
    use crate::event_log_helper::Collection;
    use jni::objects::{JClass, JObjectArray};
    use jni_support::JniError;
    use log_reader::ReadMode;
    use std::os::raw::c_int;

    extern "C" {
        // A liblog symbol, declared in `<private/android_logger.h>`.
        fn __android_log_security() -> c_int;
    }

    /// Whether security logging is currently enabled.
    ///
    /// Reads liblog's cached view of the device-owner-controlled
    /// `persist.logd.security` property.
    #[jni_method]
    fn isLoggingEnabled(_env: &mut jni::Env<'_>, _clazz: JClass) -> bool {
        // SAFETY: No preconditions; reads liblog's cached property state.
        (unsafe { __android_log_security() }) != 0
    }

    /// Logs a security event carrying `payloads` under `tag`.
    ///
    /// A null `payloads` array logs the literal `"[NULL]"`, so it is nullable.
    #[jni_method]
    fn writeEvent(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        tag: i32,
        #[class = "java/lang/Object"] payloads: Option<&JObjectArray>,
    ) -> Result<i32, JniError> {
        security().write_array(env, tag, payloads)
    }

    /// Reads all buffered security events into the `out` collection.
    #[jni_method]
    fn readEvents(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        #[class = "java/util/Collection"] out: &Collection,
    ) -> Result<(), JniError> {
        security().read_events(env, ReadMode::non_blocking(), None, 0, out)
    }

    /// Reads security events logged at or after `timestamp` into `out`.
    #[jni_method]
    fn readEventsSince(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        timestamp: i64,
        #[class = "java/util/Collection"] out: &Collection,
    ) -> Result<(), JniError> {
        security().read_events(env, ReadMode::non_blocking(), None, timestamp, out)
    }

    /// Reads security events preserved from before the last reboot into `out`.
    ///
    /// Reads the entries preserved in the pstore from before the last reboot.
    #[jni_method]
    fn readPreviousEvents(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        #[class = "java/util/Collection"] out: &Collection,
    ) -> Result<(), JniError> {
        security().read_events(env, ReadMode::non_blocking().from_pstore(), None, 0, out)
    }

    /// Reads security events into `out` just before they wrap.
    ///
    /// Blocks until events logged after `timestamp` are about to be
    /// overwritten (or liblog's two-hour wrap timeout expires), then dumps.
    #[jni_method]
    fn readEventsOnWrapping(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        timestamp: i64,
        #[class = "java/util/Collection"] out: &Collection,
    ) -> Result<(), JniError> {
        security().read_events(env, ReadMode::non_blocking().wake_on_wrap(), None, timestamp, out)
    }
}
