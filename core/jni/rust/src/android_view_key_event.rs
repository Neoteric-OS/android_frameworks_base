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

//! `android.view.KeyEvent`'s three natives: key-code table lookups and id
//! generation, backed by the libinput bindings in
//! [`crate::input_event_ffi`]. The Java ↔ native conversion helpers that the
//! rest of libandroid_runtime needs are the `rs_keyEvent_*` C exports below:
//! `core/jni/android_view_KeyEvent.cpp` is a permanent facade that keeps the
//! mangled helper signatures (libandroid's `AKeyEvent_fromJava` needs real
//! `android::KeyEvent` C++ objects) and moves events across the language
//! boundary as [`KeyEventData`] field bundles.

// The generated `KeyEvent::obtain` mirrors the 14-parameter Java
// `KeyEvent.obtain(...)`, so it necessarily exceeds Clippy's argument limit.
#![allow(clippy::too_many_arguments)]

use crate::input_event_ffi::KeyEventData;
use jni::objects::{JObject, JString};
use jni::refs::LoaderContext;
use jni::sys::jobject;

const LOG_TAG: &str = "KeyEvent-JNI";

/// `android::OK` and `android::UNKNOWN_ERROR` (utils/Errors.h), the two
/// status_t values [`rs_keyEvent_recycle`] returns.
const OK: i32 = 0;
const UNKNOWN_ERROR: i32 = i32::MIN;

jni::bind_java_type! {
    pub(crate) KeyEvent => android.view.KeyEvent,

    methods {
        /// `KeyEvent.obtain(...)`: allocates a Java `KeyEvent` copy. `mCharacters`
        /// is always passed null, so it is not bound as a field below.
        static fn obtain {
            sig = (
                id: jint,
                down_time: jlong,
                event_time: jlong,
                action: jint,
                code: jint,
                repeat: jint,
                meta_state: jint,
                device_id: jint,
                scancode: jint,
                flags: jint,
                source: jint,
                display_id: jint,
                hmac: jbyte[],
                characters: JString
            ) -> KeyEvent,
            name = "obtain",
        },
        /// `KeyEvent.recycle()`.
        fn recycle(),
    },

    // Every field is read-only: the natives only read Java KeyEvents; new
    // events are built by `obtain`. The Java names and signatures match the
    // actual `android.view.KeyEvent` fields, so the cached IDs resolve to
    // them.
    fields {
        id { sig = jint, name = "mId", get = id },
        device_id { sig = jint, name = "mDeviceId", get = device_id },
        source { sig = jint, name = "mSource", get = source },
        display_id { sig = jint, name = "mDisplayId", get = display_id },
        hmac { sig = jbyte[], name = "mHmac", get = hmac },
        meta_state { sig = jint, name = "mMetaState", get = meta_state },
        action { sig = jint, name = "mAction", get = action },
        key_code { sig = jint, name = "mKeyCode", get = key_code },
        scan_code { sig = jint, name = "mScanCode", get = scan_code },
        repeat_count { sig = jint, name = "mRepeatCount", get = repeat_count },
        flags { sig = jint, name = "mFlags", get = flags },
        down_time { sig = jlong, name = "mDownTime", get = down_time },
        event_time { sig = jlong, name = "mEventTime", get = event_time },
    },
}

/// Logs `message` under the `KeyEvent-JNI` tag, dumps the pending exception
/// (if any), and clears it. The exception's own stack trace goes through JNI
/// ExceptionDescribe (logcat's `System.err` tag).
fn log_and_clear_exception(env: &mut jni::Env<'_>, level: log::Level, message: &str) {
    log::log!(target: LOG_TAG, level, "{message}");
    env.exception_describe();
    env.exception_clear();
}

/// The all-zero HMAC libinput assigns to events that were not signed by the
/// system (`attestation/HmacKeyManager.h`). Java `KeyEvent`s built in-process
/// carry a null `mHmac`, which maps to this value.
const INVALID_HMAC: [u8; 32] = [0; 32];

/// Resolves the contents of a Java `KeyEvent.mHmac` field (`None` when the
/// field is null) to the hmac for a native event: only a 32-byte array
/// passes through, anything else falls back to [`INVALID_HMAC`]. The caller
/// logs the wrong-length case.
fn hmac_from_java_field(bytes: Option<&[u8]>) -> [u8; 32] {
    bytes.and_then(|bytes| bytes.try_into().ok()).unwrap_or(INVALID_HMAC)
}

/// Reads `event`'s `mHmac` field and resolves it to the native hmac: a null
/// or non-32-byte array falls back to `INVALID_HMAC`, logging the
/// wrong-length case.
fn hmac_field(env: &mut jni::Env<'_>, event: &KeyEvent<'_>) -> [u8; 32] {
    // GetObjectField on a cached field ID cannot fail for a valid KeyEvent.
    let hmac_obj = event.hmac(env).expect("GetObjectField(mHmac) cannot fail");

    let bytes = if hmac_obj.is_null() {
        None
    } else {
        // Release this local reference eagerly (RAII), since a caller may
        // convert many events within one native frame.
        let hmac_obj = env.auto_local(hmac_obj);
        env.convert_byte_array(&*hmac_obj).ok()
    };
    if let Some(bytes) = &bytes {
        if bytes.len() != INVALID_HMAC.len() {
            log::error!(
                target: LOG_TAG,
                "Could not initialize array from java object, expected length {} but got {}",
                INVALID_HMAC.len(),
                bytes.len()
            );
        }
    }
    hmac_from_java_field(bytes.as_deref())
}

/// Builds a Java `KeyEvent` copy of `data` via `KeyEvent.obtain`, returning a
/// new local reference, or null when the Java side threw (the exception is
/// logged and cleared).
fn obtain_java_copy(env: &mut jni::Env<'_>, data: &KeyEventData) -> jobject {
    let Ok(hmac) = env.byte_array_from_slice(&data.hmac) else {
        // Allocation failed with an OutOfMemoryError pending; log and clear
        // it and return null.
        log_and_clear_exception(
            env,
            log::Level::Error,
            "An exception occurred while obtaining a key event.",
        );
        return std::ptr::null_mut();
    };
    // Release the hmac array eagerly (RAII), since a caller may obtain many
    // events within one native frame.
    let hmac = env.auto_local(hmac);
    // The arguments match KeyEvent.obtain
    // (IJJIIIIIIIII[BLjava/lang/String;)Landroid/view/KeyEvent; in order and
    // type, with a null reference for the `characters` String parameter.
    let obtained = KeyEvent::obtain(
        env,
        data.id,
        data.down_time,
        data.event_time,
        data.action,
        data.key_code,
        data.repeat_count,
        data.meta_state,
        data.device_id,
        data.scan_code,
        data.flags,
        data.source,
        data.display_id,
        &*hmac,
        JString::null(),
    );
    match obtained {
        Ok(event) => event.into_raw(),
        Err(_) => {
            log_and_clear_exception(
                env,
                log::Level::Error,
                "An exception occurred while obtaining a key event.",
            );
            std::ptr::null_mut()
        }
    }
}

/// Reads every field of a Java `KeyEvent` into a `KeyEventData`, applying the
/// hmac fallback (a null or malformed `mHmac` becomes `INVALID_HMAC`). Keeping
/// this at the JNI edge leaves the cxx-shared data type free of Java concerns.
fn key_event_data_from_java(env: &mut jni::Env<'_>, event: &KeyEvent<'_>) -> KeyEventData {
    // GetIntField/GetLongField on a cached field ID cannot fail for a
    // valid KeyEvent.
    KeyEventData {
        down_time: event.down_time(env).expect("GetLongField(mDownTime) cannot fail"),
        event_time: event.event_time(env).expect("GetLongField(mEventTime) cannot fail"),
        id: event.id(env).expect("GetIntField(mId) cannot fail"),
        device_id: event.device_id(env).expect("GetIntField(mDeviceId) cannot fail"),
        source: event.source(env).expect("GetIntField(mSource) cannot fail"),
        display_id: event.display_id(env).expect("GetIntField(mDisplayId) cannot fail"),
        action: event.action(env).expect("GetIntField(mAction) cannot fail"),
        flags: event.flags(env).expect("GetIntField(mFlags) cannot fail"),
        key_code: event.key_code(env).expect("GetIntField(mKeyCode) cannot fail"),
        scan_code: event.scan_code(env).expect("GetIntField(mScanCode) cannot fail"),
        meta_state: event.meta_state(env).expect("GetIntField(mMetaState) cannot fail"),
        repeat_count: event.repeat_count(env).expect("GetIntField(mRepeatCount) cannot fail"),
        hmac: hmac_field(env, event),
    }
}

/// Calls the Java event's `recycle()`, logging and clearing any exception it
/// throws. Returns `OK` or `UNKNOWN_ERROR`.
fn recycle(env: &mut jni::Env<'_>, event: &KeyEvent<'_>) -> i32 {
    if event.recycle(env).is_err() {
        log_and_clear_exception(
            env,
            log::Level::Warn,
            "An exception occurred while recycling a key event.",
        );
        return UNKNOWN_ERROR;
    }
    OK
}

/// C export behind the facade's
/// `android_view_KeyEvent_obtainAsCopy(JNIEnv*, const KeyEvent&)`: builds a
/// Java `KeyEvent` from `*data` and returns a new local reference, or null
/// when the Java side threw (logged and cleared).
///
/// # Safety
///
/// `env` must be a valid `JNIEnv` pointer for the current thread, and `data`
/// must point to a valid `KeyEventData` for the duration of the call.
#[no_mangle]
pub unsafe extern "C" fn rs_keyEvent_toJava(
    env: *mut jni::sys::JNIEnv,
    data: *const KeyEventData,
) -> jobject {
    // SAFETY: The facade passes the live JNIEnv of the calling thread.
    let mut guard = unsafe { jni::AttachGuard::from_unowned(env) };
    let env = guard.borrow_env_mut();
    // SAFETY: Per this function's contract, `data` is valid for the call.
    let data = unsafe { &*data };
    obtain_java_copy(env, data)
}

/// C export behind the facade's
/// `android_view_KeyEvent_obtainAsCopy(JNIEnv*, jobject)`: reads every field
/// of the Java `KeyEvent` into `*out_data`.
///
/// # Safety
///
/// `env` must be a valid `JNIEnv` pointer for the current thread,
/// `event_obj` must be a live `android.view.KeyEvent` reference, and
/// `out_data` must be valid for writes.
#[no_mangle]
pub unsafe extern "C" fn rs_keyEvent_fromJava(
    env: *mut jni::sys::JNIEnv,
    event_obj: jobject,
    out_data: *mut KeyEventData,
) {
    // SAFETY: The facade passes the live JNIEnv of the calling thread.
    let mut guard = unsafe { jni::AttachGuard::from_unowned(env) };
    let env = guard.borrow_env_mut();
    // SAFETY: Per this function's contract, `event_obj` is a live KeyEvent
    // reference; the wrapper borrows it without deleting it, and we already
    // know its type, so the unchecked `from_raw` is correct.
    let event = unsafe { KeyEvent::from_raw(env, event_obj) };
    let data = key_event_data_from_java(env, &event);
    // SAFETY: Per this function's contract, `out_data` is valid for writes.
    unsafe { *out_data = data };
}

/// C export behind the facade's `android_view_KeyEvent_recycle`: calls the
/// Java event's `recycle()`, swallowing (logging + clearing) any exception.
/// Returns `OK` (0) or `UNKNOWN_ERROR` as a status_t.
///
/// # Safety
///
/// `event_obj` must be a live `android.view.KeyEvent` reference; the JVM
/// attachment behind `env` is upheld by `EnvUnowned`.
#[no_mangle]
pub unsafe extern "C" fn rs_keyEvent_recycle(
    mut env: jni::EnvUnowned<'_>,
    event_obj: JObject<'_>,
) -> i32 {
    match env
        .with_env_no_catch(|env| {
            // SAFETY: the facade's contract requires `event_obj` be a live
            // KeyEvent reference; the wrapper borrows it without deleting it.
            let event = unsafe { KeyEvent::from_raw(env, event_obj.as_raw()) };
            Ok::<i32, jni::errors::Error>(recycle(env, &event))
        })
        .into_outcome()
    {
        jni::Outcome::Ok(status) => status,
        // `with_env_no_catch` never catches a panic, and the closure never
        // returns `Err`, so these arms are unreachable.
        jni::Outcome::Err(_) | jni::Outcome::Panic(_) => UNKNOWN_ERROR,
    }
}

/// Registers `android.view.KeyEvent`'s native methods and caches its JNI
/// IDs. Call during JNI startup; panics if the class or any ID is missing;
/// registration failures are fatal.
pub fn register(env: &mut jni::Env<'_>) {
    // Force the class + field/method IDs to be cached eagerly at startup,
    // failing fast if the class or any ID is missing.
    KeyEventAPI::get(env, &LoaderContext::None)
        .expect("Failed to initialize android.view.KeyEvent bindings");
    key_event::register(env);
}

/// The `android.view.KeyEvent` native methods (all regular natives, matching
/// the un-annotated declarations in KeyEvent.java).
#[jni_platform_macros::jni_module("android/view/KeyEvent")]
pub mod key_event {
    use crate::input_event_ffi;
    use jni::objects::{JClass, JString};
    use jni::strings::JNIStr;

    /// Returns the symbolic name of key code `key_code`, or null if unknown.
    ///
    /// Key codes without a label return null. Labels are ASCII, so the
    /// UTF-8 → Modified UTF-8 conversion in `new_string` is the identity.
    #[jni_method]
    fn nativeKeyCodeToString<'local>(
        env: &mut jni::Env<'local>,
        _clazz: JClass,
        key_code: i32,
    ) -> JString<'local> {
        let Some(label) = input_event_ffi::key_event_label(key_code) else {
            return JString::null();
        };
        match env.new_string(label.to_string_lossy()) {
            Ok(label) => label,
            // Allocation failed; the exception is pending for Java.
            Err(_) => JString::null(),
        }
    }

    /// Returns the key code for symbolic name `label`.
    ///
    /// The label reaches libinput as NUL-terminated Modified UTF-8 bytes.
    /// A null label throws NullPointerException and returns 0.
    #[jni_method]
    fn nativeKeyCodeFromString(_env: &mut jni::Env<'_>, _clazz: JClass, label: &JNIStr) -> i32 {
        input_event_ffi::key_code_from_label(label.as_cstr())
    }

    /// Returns a fresh, process-unique input event id.
    #[jni_method]
    fn nativeNextId(_env: &mut jni::Env<'_>, _clazz: JClass) -> i32 {
        input_event_ffi::next_input_event_id()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn null_hmac_field_falls_back_to_invalid() {
        assert_eq!(hmac_from_java_field(None), INVALID_HMAC);
    }

    #[test]
    fn wrong_length_hmac_falls_back_to_invalid() {
        assert_eq!(hmac_from_java_field(Some(&[])), INVALID_HMAC);
        assert_eq!(hmac_from_java_field(Some(&[0xab; 31])), INVALID_HMAC);
        assert_eq!(hmac_from_java_field(Some(&[0xab; 33])), INVALID_HMAC);
    }

    #[test]
    fn valid_hmac_passes_through() {
        let hmac: [u8; 32] = std::array::from_fn(|i| i as u8);
        assert_eq!(hmac_from_java_field(Some(&hmac)), hmac);
    }
}
