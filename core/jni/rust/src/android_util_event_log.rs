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

//! The `android.util.EventLog` natives over the events buffer. All logic lives
//! in [`crate::event_log_helper`]; this module contributes the `EventLog$Event`
//! IDs and the registration table. The five `writeEvent` overloads share their
//! Java name and are told apart by signature, so the Rust functions have
//! distinct names and register with `name = "writeEvent"`.

use crate::event_log_helper::{self, EventLog};
use jni::refs::LoaderContext;
use log_buffer::LogBuffer;

// `android.util.EventLog$Event`, the class read entries are returned as. Its
// `<init>([B)V` constructor wraps an entry's serialized bytes.
jni::bind_java_type! {
    EventLogEvent => "android.util.EventLog$Event",
    constructors {
        /// `EventLog$Event(byte[])`.
        fn new(bytes: jbyte[]),
    },
}

/// The events buffer bound to `EventLog$Event`.
fn events() -> EventLog {
    EventLog::new(LogBuffer::Events, |env, bytes| EventLogEvent::new(env, bytes).map(Into::into))
}

/// Registers `android.util.EventLog`'s native methods and caches every class
/// and ID the implementation needs.
///
/// Call during JNI startup. Panics if a class or member is missing;
/// registration failures are fatal.
pub fn register(env: &mut jni::Env<'_>) {
    event_log_helper::init(env);
    EventLogEventAPI::get(env, &LoaderContext::None)
        .expect("Failed to initialize android.util.EventLog$Event binding");
    event_log::register(env);
}

/// The `android.util.EventLog` native methods.
#[jni_platform_macros::jni_module("android/util/EventLog")]
pub mod event_log {
    use super::events;
    use crate::event_log_helper::Collection;
    use jni::objects::{JClass, JIntArray, JObjectArray};
    use jni::strings::JNIStr;
    use jni_support::JniError;
    use log_reader::ReadMode;

    /// Logs an integer `value` under event `tag`.
    #[jni_method(name = "writeEvent")]
    fn writeEventInteger(_env: &mut jni::Env<'_>, _clazz: JClass, tag: i32, value: i32) -> i32 {
        events().write_integer(tag, value)
    }

    /// Logs a long `value` under event `tag`.
    #[jni_method(name = "writeEvent")]
    fn writeEventLong(_env: &mut jni::Env<'_>, _clazz: JClass, tag: i32, value: i64) -> i32 {
        events().write_long(tag, value)
    }

    /// Logs a float `value` under event `tag`.
    #[jni_method(name = "writeEvent")]
    fn writeEventFloat(_env: &mut jni::Env<'_>, _clazz: JClass, tag: i32, value: f32) -> i32 {
        events().write_float(tag, value)
    }

    /// Logs a string `value` under event `tag`.
    ///
    /// A null string logs the literal `"NULL"` instead of throwing (see
    /// [`super::EventLog::write_string`]).
    #[jni_method(name = "writeEvent")]
    fn writeEventString(
        _env: &mut jni::Env<'_>,
        _clazz: JClass,
        tag: i32,
        value: Option<&JNIStr>,
    ) -> i32 {
        events().write_string(tag, value.map(|value| value.as_cstr().to_bytes()))
    }

    /// Logs a list of typed values as a single event under `tag`.
    ///
    /// A null `value` array logs the literal `"[NULL]"`, so it is nullable:
    /// `None` reaches `write_array` as that case.
    #[jni_method(name = "writeEvent")]
    fn writeEventArray(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        tag: i32,
        #[class = "java/lang/Object"] value: Option<&JObjectArray>,
    ) -> Result<i32, JniError> {
        events().write_array(env, tag, value)
    }

    /// Reads events matching `tags` into the `out` collection.
    ///
    /// Both arguments are required: a null `tags` array or `out` collection
    /// throws NullPointerException.
    #[jni_method]
    fn readEvents(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        tags: Option<&JIntArray>,
        #[class = "java/util/Collection"] out: &Collection,
    ) -> Result<(), JniError> {
        let tags = tags.ok_or(JniError::NullPointer("tags must not be null"))?;
        events().read_events(env, ReadMode::non_blocking(), Some(tags), 0, out)
    }

    /// Reads events matching `tags` into `out` just before they wrap.
    ///
    /// Blocks until events logged after `timestamp` are about to be
    /// overwritten (or liblog's two-hour wrap timeout expires), then dumps.
    #[jni_method]
    fn readEventsOnWrapping(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        tags: Option<&JIntArray>,
        timestamp: i64,
        #[class = "java/util/Collection"] out: &Collection,
    ) -> Result<(), JniError> {
        let tags = tags.ok_or(JniError::NullPointer("tags must not be null"))?;
        events().read_events(
            env,
            ReadMode::non_blocking().wake_on_wrap(),
            Some(tags),
            timestamp,
            out,
        )
    }
}
