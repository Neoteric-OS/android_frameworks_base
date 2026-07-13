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

//! `android.util.Log`'s JNI natives.
//!
//! Logging is a hot path, so the string handling stays allocation-free:
//! `&JNIStr` parameters borrow the Java string via `GetStringUTFChars` and
//! expose the `&CStr` that liblog takes via `as_cstr`. The bytes are Modified
//! UTF-8 either way; liblog treats tags and messages as raw bytes, so they pass
//! through unchanged. Only `isLoggable` converts its tag to a `&str`, and that
//! borrows for every code point in U+0001..=U+FFFF — it allocates only for a
//! supplementary-plane character or an embedded NUL, where Modified UTF-8
//! diverges from UTF-8.
//!
//! Priorities and buffer IDs arrive from Java as raw ints that
//! `android.util.Log` does not validate, so the write path uses
//! `log_buffer`'s raw-priority form rather than the typed [`Priority`] enum.

use jni::strings::JNIStr;
use log_buffer::Priority;
use std::ffi::{c_char, CStr};

/// Reports whether a message with the given raw priority would be logged for
/// `tag`, using `android.util.Log`'s default threshold of `INFO`.
fn is_loggable(tag: &str, level: i32) -> bool {
    log_buffer::is_loggable_raw(level, tag, Priority::Info)
}

/// C ABI export declared in `android_util_Log.h` and used by
/// `android_database_SQLiteGlobal.cpp` to decide whether SQLite verbose
/// logging is enabled.
///
/// Returns `false` for a null `tag`.
///
/// # Safety
///
/// `tag` must be null or a valid NUL-terminated C string that outlives the
/// call.
#[no_mangle]
pub unsafe extern "C" fn android_util_Log_isVerboseLogEnabled(tag: *const c_char) -> bool {
    if tag.is_null() {
        return false;
    }
    // SAFETY: The caller guarantees `tag` is a valid NUL-terminated string;
    // null was handled above.
    let tag = unsafe { CStr::from_ptr(tag) };
    is_loggable(&String::from_utf8_lossy(tag.to_bytes()), Priority::Verbose.as_raw())
}

/// Registers `android.util.Log`'s native methods.
///
/// Call during JNI startup. Panics if the class or a method is missing;
/// registration failures are fatal.
pub fn register(env: &mut jni::Env<'_>) {
    log::register(env);
}

/// The `android.util.Log` native methods.
#[jni_platform_macros::jni_module("android/util/Log")]
pub mod log {
    use super::{is_loggable, JNIStr};
    use jni::objects::JClass;
    use jni_support::JniError;

    /// Whether a message at raw priority `level` would be logged for `tag`.
    ///
    /// A null tag returns `false` without an exception.
    /// `level` is an arbitrary app-supplied int that liblog compares
    /// numerically, so it stays untyped.
    #[jni_method(fast)]
    fn isLoggable(
        _env: &mut jni::Env<'_>,
        _clazz: JClass,
        tag: Option<&JNIStr>,
        level: i32,
    ) -> bool {
        match tag {
            Some(tag) => is_loggable(&tag.to_str(), level),
            None => false,
        }
    }

    /// Writes `msg` to log buffer `buf_id` at `priority` under `tag`.
    ///
    /// The borrowed `&JNIStr` strings expose the `&CStr`s liblog takes via
    /// `as_cstr`, so nothing is copied. A null `msg` throws
    /// `NullPointerException` and returns -1; a `buf_id` that is not a writable
    /// log buffer (out of range, or the kernel buffer id 7) throws
    /// `IllegalArgumentException`.
    ///
    /// Returns `1` for a loggable message — the positive value `Log.println`
    /// reports on success — or liblog's negative status when the message is
    /// filtered out.
    #[jni_method]
    fn println_native(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        buf_id: i32,
        priority: i32,
        tag: Option<&JNIStr>,
        msg: Option<&JNIStr>,
    ) -> i32 {
        let Some(msg) = msg else {
            JniError::NullPointer("println needs a message").throw_on(env);
            return -1;
        };
        let Some(buffer) = log_buffer::LogBuffer::from_raw(buf_id) else {
            JniError::IllegalArgument("bad bufID".to_owned()).throw_on(env);
            return -1;
        };

        match buffer.write_raw_priority(priority, tag.map(|t| t.as_cstr()), msg.as_cstr()) {
            Ok(()) => 1,
            Err(e) => e.code(),
        }
    }

    /// Returns the maximum payload size, in bytes, of a single log entry.
    #[jni_method]
    fn logger_entry_max_payload_native(_env: &mut jni::Env<'_>, _clazz: JClass) -> i32 {
        log_buffer::LOGGER_ENTRY_MAX_PAYLOAD as i32
    }
}
