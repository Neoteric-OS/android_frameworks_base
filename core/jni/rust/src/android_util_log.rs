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

//! Rust conversion of `android_util_Log.cpp`.
//!
//! Logging is a hot path, so the string handling is allocation-free:
//! `&JavaStr` parameters borrow the Java string via `GetStringUTFChars` and
//! deref to the `&CStr` that liblog takes, exactly the C++ `ScopedUtfChars`
//! flow. The bytes are Modified UTF-8 either way; liblog treats tags and
//! messages as raw bytes, so they pass through unchanged. Only `isLoggable`'s
//! tag → `&str` conversion can allocate, and only for non-ASCII tags (the
//! `Cow` stays borrowed for ASCII).
//!
//! Priorities and buffer IDs arrive from Java as raw ints that
//! `android.util.Log` does not validate, so the write path uses
//! `log_buffer`'s raw-priority form rather than the typed [`Priority`] enum.

use jni::strings::JavaStr;
use log_buffer::Priority;
use std::borrow::Cow;
use std::ffi::{c_char, CStr};
use std::sync::atomic::{AtomicI32, Ordering};

/// Cached value of `android.util.Log.VERBOSE`, read once during registration
/// so that [`android_util_Log_isVerboseLogEnabled`] needs no `JNIEnv`.
///
/// The C++ cached all six level constants but only ever read the verbose one;
/// this keeps just that.
static VERBOSE_LEVEL: AtomicI32 = AtomicI32::new(0);

/// Reports whether a message with the given raw priority would be logged for
/// `tag`, using `android.util.Log`'s default threshold of `INFO`.
fn is_loggable(tag: &str, level: i32) -> bool {
    log_buffer::is_loggable_raw(level, tag, Priority::Info)
}

/// C ABI export of the C++ helper `android_util_Log_isVerboseLogEnabled`,
/// declared in `android_util_Log.h` and used by `android_database_SQLiteGlobal.cpp`
/// to decide whether SQLite verbose logging is enabled.
///
/// Returns `false` for a null `tag` (the C++ would have crashed).
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
    is_loggable(&String::from_utf8_lossy(tag.to_bytes()), VERBOSE_LEVEL.load(Ordering::Relaxed))
}

/// Registers `android.util.Log`'s native methods and caches `Log.VERBOSE`.
///
/// Call during JNI startup. Panics if the class, the field, or a method is
/// missing, matching the C++ `FindClassOrDie`/`RegisterMethodsOrDie`
/// semantics.
pub fn register(env: &mut jni::JNIEnv<'_>) {
    let verbose = env
        .get_static_field("android/util/Log", "VERBOSE", "I")
        .expect("Failed to read Log.VERBOSE")
        .i()
        .expect("Log.VERBOSE is not an int");
    VERBOSE_LEVEL.store(verbose, Ordering::Relaxed);

    log::register(env);
}

/// The `android.util.Log` native methods.
#[jni_macros::jni_module("android/util/Log")]
pub mod log {
    use super::{is_loggable, Cow, JavaStr};
    use jni::sys::jclass;
    use jni_support::JniError;
    use std::ffi::CStr;

    /// @FastNative: "(Ljava/lang/String;I)Z"
    ///
    /// A null tag returns `false` without an exception, matching the C++.
    /// `level` is an arbitrary app-supplied int that liblog compares
    /// numerically, so it stays untyped.
    #[jni_method(fast)]
    fn isLoggable(
        _env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        tag: Option<&JavaStr>,
        level: i32,
    ) -> bool {
        match tag {
            Some(tag) => is_loggable(&Cow::from(&**tag), level),
            None => false,
        }
    }

    /// Regular: "(IILjava/lang/String;Ljava/lang/String;)I"
    ///
    /// The borrowed `&JavaStr` strings deref to the `&CStr`s liblog takes, so
    /// nothing is copied. `msg` is taken as an `Option` only so the null case
    /// can throw with the C++'s exact message and -1 return.
    ///
    /// Deliberate fix over the C++: an out-of-range `bufID` now throws
    /// `IllegalArgumentException` instead of `NullPointerException`, and the
    /// unwritable kernel buffer ID (7) is rejected too.
    #[jni_method]
    fn println_native(
        env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        buf_id: i32,
        priority: i32,
        tag: Option<&JavaStr>,
        msg: Option<&JavaStr>,
    ) -> i32 {
        let Some(msg) = msg else {
            JniError::NullPointer("println needs a message").throw_on(env);
            return -1;
        };
        let Some(buffer) = log_buffer::LogBuffer::from_raw(buf_id) else {
            JniError::IllegalArgument("bad bufID".to_owned()).throw_on(env);
            return -1;
        };

        match buffer.write_raw_priority(priority, tag.map(|t| -> &CStr { t }), msg) {
            Ok(()) => 1,
            Err(e) => e.code(),
        }
    }

    /// Regular: "()I"
    #[jni_method]
    fn logger_entry_max_payload_native(_env: &mut jni::JNIEnv<'_>, _clazz: jclass) -> i32 {
        log_buffer::LOGGER_ENTRY_MAX_PAYLOAD as i32
    }
}
