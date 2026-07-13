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

//! Rust conversion of `android_util_EventLog.cpp`: the `android.util.EventLog`
//! natives over the events buffer. All logic lives in
//! [`crate::event_log_helper`]; this module contributes the `EventLog$Event`
//! IDs and the registration table. The five `writeEvent` overloads share
//! their Java name and are told apart by signature, so the Rust functions
//! carry the C++ helper's names and register with `name = "writeEvent"`.

use crate::event_log_helper::{self, EventLog};
use jni::objects::JMethodID;
use jni::sys::jmethodID;
use log_buffer::LogBuffer;

/// Cached IDs for `android.util.EventLog$Event`, the class read entries are
/// returned as.
#[jni_macros::java_class("android/util/EventLog$Event")]
pub struct EventLogEvent {
    #[method("(byte[]) -> void", name = "<init>")]
    ctor: jmethodID,
}

/// The events buffer bound to `EventLog$Event` — the C++
/// `EventLogHelper<LOG_ID_EVENTS, kEventLogEventClass>` instantiation.
fn events() -> EventLog {
    let ids = EventLogEvent::get();
    // SAFETY: `ctor` was cached by EventLogEvent::init, so it is a valid,
    // non-null method ID for the lifetime of the process.
    EventLog::new(LogBuffer::Events, &ids.class, unsafe { JMethodID::from_raw(ids.ctor) })
}

/// Registers `android.util.EventLog`'s native methods and caches every class
/// and ID the implementation needs.
///
/// Call during JNI startup. Panics if a class or member is missing, matching
/// the C++ `RegisterMethodsOrDie` semantics.
pub fn register(env: &mut jni::JNIEnv<'_>) {
    event_log_helper::init(env);
    EventLogEvent::init(env);
    event_log::register(env);
}

/// The `android.util.EventLog` native methods.
#[jni_macros::jni_module("android/util/EventLog")]
pub mod event_log {
    use super::events;
    use jni::objects::{JIntArray, JObject, JObjectArray};
    use jni::strings::JavaStr;
    use jni::sys::jclass;
    use jni_support::JniError;
    use log_reader::ReadMode;

    /// Regular: "(II)I"
    #[jni_method(name = "writeEvent")]
    fn writeEventInteger(_env: &mut jni::JNIEnv<'_>, _clazz: jclass, tag: i32, value: i32) -> i32 {
        events().write_integer(tag, value)
    }

    /// Regular: "(IJ)I"
    #[jni_method(name = "writeEvent")]
    fn writeEventLong(_env: &mut jni::JNIEnv<'_>, _clazz: jclass, tag: i32, value: i64) -> i32 {
        events().write_long(tag, value)
    }

    /// Regular: "(IF)I"
    #[jni_method(name = "writeEvent")]
    fn writeEventFloat(_env: &mut jni::JNIEnv<'_>, _clazz: jclass, tag: i32, value: f32) -> i32 {
        events().write_float(tag, value)
    }

    /// Regular: "(ILjava/lang/String;)I"
    ///
    /// A null string logs the literal `"NULL"` instead of throwing (see
    /// [`super::EventLog::write_string`]).
    #[jni_method(name = "writeEvent")]
    fn writeEventString(
        _env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        tag: i32,
        value: Option<&JavaStr>,
    ) -> i32 {
        events().write_string(tag, value)
    }

    /// Regular: "(I[Ljava/lang/Object;)I"
    #[jni_method(name = "writeEvent")]
    fn writeEventArray(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        tag: i32,
        #[class = "java/lang/Object"] value: &JObjectArray,
    ) -> i32 {
        events().write_array(env, tag, value)
    }

    /// Regular: "([ILjava/util/Collection;)V"
    #[jni_method]
    fn readEvents(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        tags: &JIntArray,
        #[class = "java/util/Collection"] out: &JObject,
    ) {
        if tags.as_raw().is_null() || out.as_raw().is_null() {
            JniError::NullPointer("").throw_on(env);
            return;
        }
        events().read_events(env, ReadMode::non_blocking(), Some(tags), 0, out);
    }

    /// Regular: "([IJLjava/util/Collection;)V"
    ///
    /// Blocks until events logged after `timestamp` are about to be
    /// overwritten (or liblog's two-hour wrap timeout expires), then dumps.
    #[jni_method]
    fn readEventsOnWrapping(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        tags: &JIntArray,
        timestamp: i64,
        #[class = "java/util/Collection"] out: &JObject,
    ) {
        if tags.as_raw().is_null() || out.as_raw().is_null() {
            JniError::NullPointer("").throw_on(env);
            return;
        }
        events().read_events(
            env,
            ReadMode::non_blocking().wake_on_wrap(),
            Some(tags),
            timestamp,
            out,
        );
    }
}
