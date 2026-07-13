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

//! Rust conversion of `android_view_KeyEvent.cpp`.
//!
//! The three natives are `android.view.KeyEvent`'s key-code table lookups
//! and id generation, backed by the libinput bindings in
//! [`crate::input_event_ffi`]. The Java ↔ native conversion helpers the C++
//! file exposed to the rest of libandroid_runtime survive as the
//! `rs_keyEvent_*` C exports below: `core/jni/android_view_KeyEvent.cpp` is
//! now a permanent facade that keeps the old mangled helper signatures
//! (libandroid's `AKeyEvent_fromJava` needs real `android::KeyEvent` C++
//! objects) and moves events across the language boundary as
//! [`KeyEventData`] field bundles.

use crate::input_event_compat::{hmac_from_java_field, KeyEventData};
use jni::objects::{JByteArray, JFieldID, JMethodID, JObject, JStaticMethodID};
use jni::signature::{Primitive, ReturnType};
use jni::sys::{jfieldID, jmethodID, jobject, jvalue};

const LOG_TAG: &str = "KeyEvent-JNI";

/// `android::OK` and `android::UNKNOWN_ERROR` (utils/Errors.h), the two
/// status_t values [`rs_keyEvent_recycle`] returns.
const OK: i32 = 0;
const UNKNOWN_ERROR: i32 = i32::MIN;

/// Cached JNI IDs for `android.view.KeyEvent`, the Rust
/// `gKeyEventClassInfo`. `mCharacters` is unused (the obtain call always
/// passes a null `characters`) but stays cached like the C++ did.
#[jni_macros::java_class("android/view/KeyEvent")]
pub struct KeyEventClassInfo {
    #[static_method("(int, long, long, int, int, int, int, int, int, int, int, int, byte[], String) -> android.view.KeyEvent")]
    obtain: jmethodID,
    #[method("() -> void")]
    recycle: jmethodID,

    #[field("int", name = "mId")]
    m_id: jfieldID,
    #[field("int", name = "mDeviceId")]
    m_device_id: jfieldID,
    #[field("int", name = "mSource")]
    m_source: jfieldID,
    #[field("int", name = "mDisplayId")]
    m_display_id: jfieldID,
    #[field("byte[]", name = "mHmac")]
    m_hmac: jfieldID,
    #[field("int", name = "mMetaState")]
    m_meta_state: jfieldID,
    #[field("int", name = "mAction")]
    m_action: jfieldID,
    #[field("int", name = "mKeyCode")]
    m_key_code: jfieldID,
    #[field("int", name = "mScanCode")]
    m_scan_code: jfieldID,
    #[field("int", name = "mRepeatCount")]
    m_repeat_count: jfieldID,
    #[field("int", name = "mFlags")]
    m_flags: jfieldID,
    #[field("long", name = "mDownTime")]
    m_down_time: jfieldID,
    #[field("long", name = "mEventTime")]
    m_event_time: jfieldID,
    #[field("String", name = "mCharacters")]
    m_characters: jfieldID,
}

/// Logs `message` under the C++'s `KeyEvent-JNI` tag, dumps the pending
/// exception (if any), and clears it. The counterpart of the C++'s
/// ALOG + LOGE_EX + ExceptionClear sequence; the exception's own stack trace
/// goes through JNI ExceptionDescribe (logcat's `System.err` tag) rather
/// than nativehelper's jniLogException.
fn log_and_clear_exception(env: &mut jni::JNIEnv<'_>, level: log::Level, message: &str) {
    log::log!(target: LOG_TAG, level, "{message}");
    let _ = env.exception_describe();
    let _ = env.exception_clear();
}

/// Reads an `int` field of `event` through a cached field ID. Infallible for
/// the IDs [`KeyEventClassInfo::init`] resolved, like the C++ GetIntField.
fn int_field(env: &mut jni::JNIEnv<'_>, event: &JObject<'_>, field: jfieldID) -> i32 {
    // SAFETY: `field` is an int field ID of android.view.KeyEvent resolved
    // by KeyEventClassInfo::init, and `event` is a KeyEvent.
    unsafe {
        env.get_field_unchecked(
            event,
            JFieldID::from_raw(field),
            ReturnType::Primitive(Primitive::Int),
        )
    }
    .and_then(|value| value.i())
    .expect("GetIntField on a cached KeyEvent field ID cannot fail")
}

/// Reads a `long` field of `event` through a cached field ID.
fn long_field(env: &mut jni::JNIEnv<'_>, event: &JObject<'_>, field: jfieldID) -> i64 {
    // SAFETY: `field` is a long field ID of android.view.KeyEvent resolved
    // by KeyEventClassInfo::init, and `event` is a KeyEvent.
    unsafe {
        env.get_field_unchecked(
            event,
            JFieldID::from_raw(field),
            ReturnType::Primitive(Primitive::Long),
        )
    }
    .and_then(|value| value.j())
    .expect("GetLongField on a cached KeyEvent field ID cannot fail")
}

/// Reads `event`'s `mHmac` field and resolves it to the native hmac: a null
/// or non-32-byte array falls back to `INVALID_HMAC`, with the C++'s error
/// log for the wrong-length case.
fn hmac_field(env: &mut jni::JNIEnv<'_>, event: &JObject<'_>) -> [u8; 32] {
    let info = KeyEventClassInfo::get();
    // SAFETY: `m_hmac` is the byte[] field ID of android.view.KeyEvent
    // resolved by KeyEventClassInfo::init, and `event` is a KeyEvent.
    let hmac_obj = unsafe {
        env.get_field_unchecked(event, JFieldID::from_raw(info.m_hmac), ReturnType::Object)
    }
    .and_then(|value| value.l())
    .expect("GetObjectField(mHmac) cannot fail");

    let bytes = if hmac_obj.is_null() {
        None
    } else {
        let array = JByteArray::from(hmac_obj);
        let bytes = env.convert_byte_array(&array).ok();
        // The C++ left this local reference for the frame; release it
        // eagerly instead, since a caller may convert many events within one
        // native frame.
        let _ = env.delete_local_ref(array);
        bytes
    };
    if let Some(bytes) = &bytes {
        if bytes.len() != 32 {
            log::error!(
                target: LOG_TAG,
                "Could not initialize array from java object, expected length 32 but got {}",
                bytes.len()
            );
        }
    }
    hmac_from_java_field(bytes.as_deref())
}

impl KeyEventData {
    /// Builds a Java `KeyEvent` copy of this event via `KeyEvent.obtain`,
    /// returning a new local reference, or null when the Java side threw
    /// (the exception is logged and cleared, matching the C++).
    pub(crate) fn obtain_java_copy(&self, env: &mut jni::JNIEnv<'_>) -> jobject {
        let info = KeyEventClassInfo::get();
        let Ok(hmac) = env.byte_array_from_slice(&self.hmac) else {
            // Allocation failed with an OutOfMemoryError pending; the C++
            // reached the same null-with-cleared-exception result through
            // its post-call ExceptionCheck.
            log_and_clear_exception(
                env,
                log::Level::Error,
                "An exception occurred while obtaining a key event.",
            );
            return std::ptr::null_mut();
        };
        // SAFETY: `obtain` is KeyEvent.obtain with signature
        // (IJJIIIIIIIII[BLjava/lang/String;)Landroid/view/KeyEvent;, resolved
        // by KeyEventClassInfo::init; the jvalues below match it in order and
        // type, with a null reference for the String parameter.
        let obtained = unsafe {
            env.call_static_method_unchecked(
                &info.class,
                JStaticMethodID::from_raw(info.obtain),
                ReturnType::Object,
                &[
                    jvalue { i: self.id },
                    jvalue { j: self.down_time },
                    jvalue { j: self.event_time },
                    jvalue { i: self.action },
                    jvalue { i: self.key_code },
                    jvalue { i: self.repeat_count },
                    jvalue { i: self.meta_state },
                    jvalue { i: self.device_id },
                    jvalue { i: self.scan_code },
                    jvalue { i: self.flags },
                    jvalue { i: self.source },
                    jvalue { i: self.display_id },
                    jvalue { l: hmac.as_raw() },
                    jvalue { l: std::ptr::null_mut() },
                ],
            )
        };
        // The C++ scoped the hmac array; release it eagerly like it did,
        // since a caller may obtain many events within one native frame.
        let _ = env.delete_local_ref(hmac);
        match obtained.and_then(|value| value.l()) {
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

    /// Reads every field of a Java `KeyEvent` into a `KeyEventData`,
    /// applying the hmac fallback (a null or malformed `mHmac` becomes
    /// `INVALID_HMAC`).
    pub(crate) fn from_java(env: &mut jni::JNIEnv<'_>, event: &JObject<'_>) -> KeyEventData {
        let info = KeyEventClassInfo::get();
        KeyEventData {
            down_time: long_field(env, event, info.m_down_time),
            event_time: long_field(env, event, info.m_event_time),
            id: int_field(env, event, info.m_id),
            device_id: int_field(env, event, info.m_device_id),
            source: int_field(env, event, info.m_source),
            display_id: int_field(env, event, info.m_display_id),
            action: int_field(env, event, info.m_action),
            flags: int_field(env, event, info.m_flags),
            key_code: int_field(env, event, info.m_key_code),
            scan_code: int_field(env, event, info.m_scan_code),
            meta_state: int_field(env, event, info.m_meta_state),
            repeat_count: int_field(env, event, info.m_repeat_count),
            hmac: hmac_field(env, event),
        }
    }
}

/// Calls the Java event's `recycle()`, logging and clearing any exception it
/// throws. Returns `OK` or `UNKNOWN_ERROR`, like the C++
/// `android_view_KeyEvent_recycle`.
fn recycle(env: &mut jni::JNIEnv<'_>, event: &JObject<'_>) -> i32 {
    let info = KeyEventClassInfo::get();
    // SAFETY: `recycle` is KeyEvent.recycle()V resolved by
    // KeyEventClassInfo::init, and `event` is a KeyEvent; no arguments.
    let result = unsafe {
        env.call_method_unchecked(
            event,
            JMethodID::from_raw(info.recycle),
            ReturnType::Primitive(Primitive::Void),
            &[],
        )
    };
    if result.is_err() {
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
    let mut env = unsafe { jni::JNIEnv::from_raw(env) }.expect("null JNIEnv");
    // SAFETY: Per this function's contract, `data` is valid for the call.
    let data = unsafe { &*data };
    data.obtain_java_copy(&mut env)
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
    let mut env = unsafe { jni::JNIEnv::from_raw(env) }.expect("null JNIEnv");
    // SAFETY: Per this function's contract, `event_obj` is a live KeyEvent
    // reference; the JObject wrapper borrows it without deleting it.
    let event = unsafe { JObject::from_raw(event_obj) };
    let data = KeyEventData::from_java(&mut env, &event);
    // SAFETY: Per this function's contract, `out_data` is valid for writes.
    unsafe { *out_data = data };
}

/// C export behind the facade's `android_view_KeyEvent_recycle`: calls the
/// Java event's `recycle()`, swallowing (logging + clearing) any exception.
/// Returns `OK` (0) or `UNKNOWN_ERROR` as a status_t.
///
/// # Safety
///
/// `env` must be a valid `JNIEnv` pointer for the current thread and
/// `event_obj` must be a live `android.view.KeyEvent` reference.
#[no_mangle]
pub unsafe extern "C" fn rs_keyEvent_recycle(
    env: *mut jni::sys::JNIEnv,
    event_obj: jobject,
) -> i32 {
    // SAFETY: The facade passes the live JNIEnv of the calling thread.
    let mut env = unsafe { jni::JNIEnv::from_raw(env) }.expect("null JNIEnv");
    // SAFETY: Per this function's contract, `event_obj` is a live KeyEvent
    // reference; the JObject wrapper borrows it without deleting it.
    let event = unsafe { JObject::from_raw(event_obj) };
    recycle(&mut env, &event)
}

/// Registers `android.view.KeyEvent`'s native methods and caches its JNI
/// IDs. Call during JNI startup; panics if the class or any ID is missing,
/// matching the C++ `RegisterMethodsOrDie` semantics.
pub fn register(env: &mut jni::JNIEnv<'_>) {
    KeyEventClassInfo::init(env);
    key_event::register(env);
}

/// The `android.view.KeyEvent` native methods (all regular natives, matching
/// the un-annotated declarations in KeyEvent.java).
#[jni_macros::jni_module("android/view/KeyEvent")]
pub mod key_event {
    use crate::input_event_ffi;
    use jni::strings::JavaStr;
    use jni::sys::{jclass, jstring};

    /// Regular native: "(I)Ljava/lang/String;"
    ///
    /// Key codes without a label return null, preserving the C++'s
    /// `NewStringUTF(nullptr)` behavior. Labels are ASCII, so the UTF-8 →
    /// Modified UTF-8 conversion in `new_string` is the identity.
    #[jni_method]
    fn nativeKeyCodeToString(env: &mut jni::JNIEnv<'_>, _clazz: jclass, key_code: i32) -> jstring {
        let Some(label) = input_event_ffi::key_event_label(key_code) else {
            return std::ptr::null_mut();
        };
        match env.new_string(label.to_string_lossy()) {
            Ok(label) => label.into_raw(),
            // Allocation failed; the exception is pending for Java, as it
            // would have been after NewStringUTF.
            Err(_) => std::ptr::null_mut(),
        }
    }

    /// Regular native: "(Ljava/lang/String;)I"
    ///
    /// The label reaches libinput as the same NUL-terminated Modified UTF-8
    /// bytes ScopedUtfChars produced for the C++. A null label throws
    /// NullPointerException and returns 0 (the C++ threw the same NPE, then
    /// kept going and crashed reading the null string).
    #[jni_method]
    fn nativeKeyCodeFromString(_env: &mut jni::JNIEnv<'_>, _clazz: jclass, label: &JavaStr) -> i32 {
        input_event_ffi::key_code_from_label(label)
    }

    /// Regular native: "()I"
    #[jni_method]
    fn nativeNextId(_env: &mut jni::JNIEnv<'_>, _clazz: jclass) -> i32 {
        input_event_ffi::next_input_event_id()
    }
}
