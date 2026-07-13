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

//! Rust conversion of `android_os_SystemProperties.cpp`.
//!
//! `android.os.SystemProperties` overloads several native names — `native_get`
//! and the `native_get_{int,long,boolean}` family each exist with a
//! `String`-keyed and a handle (`long`)-keyed variant — so the Rust functions
//! carry distinct names and register under the shared Java name with
//! `name = "..."`; `RegisterNatives` tells the overloads apart by signature.
//!
//! A handle is the raw `prop_info` pointer produced by `native_find`, held by
//! `SystemProperties.Handle` as a Java `long`. Property records are never
//! freed or moved (see [`rustutils::system_properties::PropertyHandle`]), so
//! round-tripping the pointer through Java is sound; the Java side only
//! constructs a `Handle` for a non-zero value.
//!
//! The int/long/boolean getters fall back to the caller's default whenever
//! [`libbase_parse`](crate::libbase_parse) — the exact port of the libbase
//! parsers the C++ used — rejects the value, and `native_set` reproduces the
//! C++ error path: on failure it throws `RuntimeException`, blaming a failed
//! system call when `errno` was set and init otherwise.

use std::ffi::CStr;

/// Property store access. On Android this is rustutils' `system_properties`
/// (bionic's `__system_property_*` underneath); rustutils only compiles that
/// module for Android, so host builds declare the same C symbols directly,
/// resolved by libbase's fallback implementation
/// (`system/libbase/properties.cpp`), the same one the C++ JNI used on host.
#[cfg(target_os = "android")]
mod props {
    use jni::sys::jlong;
    use rustutils::system_properties::{self, PropertyHandle};

    /// Calls `f` with `name`'s current value, without copying it. Errors
    /// (absent property, a name with an interior NUL, a non-UTF-8 value) all
    /// read as "no value", matching the C++'s find-nothing path.
    pub(super) fn read_with<T>(name: &str, f: impl FnOnce(&str) -> T) -> Option<T> {
        let handle = system_properties::find(name).ok().flatten()?;
        handle.read_with(f).ok()
    }

    /// Looks up `name`, returning its `prop_info` pointer as the Java handle
    /// value, or 0 if the property does not exist.
    pub(super) fn find(name: &str) -> jlong {
        match system_properties::find(name) {
            Ok(Some(handle)) => handle.as_raw() as jlong,
            _ => 0,
        }
    }

    /// Calls `f` with the current value of the property behind `handle`,
    /// without copying it — the getters behind `@CriticalNative` only parse
    /// the value, so nothing here may allocate.
    ///
    /// # Safety
    ///
    /// `handle` must be 0 or a value produced by [`find`].
    pub(super) unsafe fn read_handle_with<T>(
        handle: jlong,
        f: impl FnOnce(&str) -> T,
    ) -> Option<T> {
        // SAFETY: Per this function's contract, the pointer came from
        // __system_property_find (via find), as from_raw requires.
        let handle = unsafe { PropertyHandle::from_raw(handle as *const _) }?;
        handle.read_with(f).ok()
    }

    /// Sets `name` to `value`. The error carries no detail; the caller
    /// inspects `errno` to attribute the failure.
    pub(super) fn set(name: &str, value: &str) -> Result<(), ()> {
        system_properties::write(name, value).map_err(|_| ())
    }
}

#[cfg(not(target_os = "android"))]
mod props {
    use jni::sys::jlong;
    use std::ffi::{c_char, c_int, c_uint, c_void, CStr, CString};

    /// An opaque `prop_info` record. libbase's host records live in a
    /// `std::set` that is never cleared, so, like bionic's, a pointer stays
    /// valid for the life of the process.
    type PropInfo = c_void;

    extern "C" {
        fn __system_property_find(name: *const c_char) -> *const PropInfo;
        fn __system_property_read_callback(
            pi: *const PropInfo,
            callback: unsafe extern "C" fn(*mut c_void, *const c_char, *const c_char, c_uint),
            cookie: *mut c_void,
        );
        fn __system_property_set(name: *const c_char, value: *const c_char) -> c_int;
    }

    /// State shared with the `__system_property_read_callback` trampoline:
    /// the visitor to run (taken on first invocation) and where its result
    /// goes.
    struct ReadCookie<'out, F, T> {
        f: Option<F>,
        out: &'out mut Option<T>,
    }

    /// `__system_property_read_callback` trampoline: runs the visitor behind
    /// `cookie` on the borrowed value. Mirrors rustutils in treating a
    /// non-UTF-8 value as unreadable.
    unsafe extern "C" fn visit_value<F: FnOnce(&str) -> T, T>(
        cookie: *mut c_void,
        _name: *const c_char,
        value: *const c_char,
        _serial: c_uint,
    ) {
        // SAFETY: `cookie` is the `&mut ReadCookie<F, T>` passed to
        // __system_property_read_callback below, and `value` is a
        // NUL-terminated string valid for the duration of the callback.
        unsafe {
            let cookie = &mut *cookie.cast::<ReadCookie<F, T>>();
            if value.is_null() {
                return;
            }
            if let (Ok(value), Some(f)) = (CStr::from_ptr(value).to_str(), cookie.f.take()) {
                *cookie.out = Some(f(value));
            }
        }
    }

    pub(super) fn read_with<T>(name: &str, f: impl FnOnce(&str) -> T) -> Option<T> {
        let handle = find(name);
        // SAFETY: The handle was just returned by find.
        unsafe { read_handle_with(handle, f) }
    }

    pub(super) fn find(name: &str) -> jlong {
        let Ok(name) = CString::new(name) else {
            return 0;
        };
        // SAFETY: `name` is a valid NUL-terminated string, only read during
        // the call; the result is null or a process-lifetime prop_info.
        (unsafe { __system_property_find(name.as_ptr()) }) as jlong
    }

    /// # Safety
    ///
    /// `handle` must be 0 or a value produced by [`find`].
    pub(super) unsafe fn read_handle_with<F: FnOnce(&str) -> T, T>(
        handle: jlong,
        f: F,
    ) -> Option<T> {
        if handle == 0 {
            return None;
        }
        let mut out: Option<T> = None;
        let mut cookie = ReadCookie { f: Some(f), out: &mut out };
        // SAFETY: Per this function's contract, `handle` is a live prop_info
        // pointer; `visit_value` only writes through the cookie we pass.
        unsafe {
            __system_property_read_callback(
                handle as *const PropInfo,
                visit_value::<F, T>,
                (&mut cookie as *mut ReadCookie<'_, F, T>).cast(),
            );
        }
        out
    }

    pub(super) fn set(name: &str, value: &str) -> Result<(), ()> {
        let (Ok(name), Ok(value)) = (CString::new(name), CString::new(value)) else {
            return Err(());
        };
        // SAFETY: Both are valid NUL-terminated strings, only read during
        // the call.
        if unsafe { __system_property_set(name.as_ptr(), value.as_ptr()) } == 0 {
            Ok(())
        } else {
            Err(())
        }
    }
}

/// Sets the calling thread's `errno` to 0, so a later failure can be
/// attributed: the C++ cleared errno before `__system_property_set` to tell
/// failed system calls (errno set) apart from init rejecting the request
/// (errno untouched).
fn clear_errno() {
    // SAFETY: The libc errno accessor returns the calling thread's errno
    // slot, valid for the thread's lifetime.
    unsafe {
        #[cfg(target_os = "android")]
        {
            *libc::__errno() = 0;
        }
        #[cfg(target_os = "linux")]
        {
            *libc::__errno_location() = 0;
        }
        #[cfg(target_vendor = "apple")]
        {
            *libc::__error() = 0;
        }
    }
}

/// `strerror(errno)`, matching the C++'s `%m` format expansion.
fn errno_message(errno: i32) -> String {
    // SAFETY: strerror returns a pointer to a string with process lifetime.
    // It is not strictly thread-safe, but neither was the C++'s %m.
    unsafe { CStr::from_ptr(libc::strerror(errno)) }.to_string_lossy().into_owned()
}

/// Converts `env.new_string`'s result to the raw jstring a native method
/// returns: null (with the exception ART raised, e.g. OOM) on failure.
fn new_java_string(env: &jni::JNIEnv<'_>, value: &str) -> jni::sys::jstring {
    match env.new_string(value) {
        Ok(string) => string.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Registers `android.os.SystemProperties`'s native methods with the JVM.
///
/// Call during JNI startup, before any Java code uses `SystemProperties`.
/// Panics if the class or a method is missing, matching the C++
/// `RegisterMethodsOrDie` semantics.
pub fn register(env: &mut jni::JNIEnv<'_>) {
    system_properties::register(env);
}

/// The `android.os.SystemProperties` native methods.
#[jni_macros::jni_module("android/os/SystemProperties")]
pub mod system_properties {
    use super::{clear_errno, errno_message, new_java_string, props};
    use crate::libbase_parse;
    use crate::sysprop_change;
    use jni::strings::JavaStr;
    use jni::sys::{jclass, jstring};
    use jni_support::JniError;
    use std::borrow::Cow;

    /// @FastNative: "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
    ///
    /// An absent property or one holding the empty value yields `def`
    /// untouched; a null `def` becomes `""` unless an exception is already
    /// pending (the C++'s legacy behavior).
    #[jni_method(fast, name = "native_get")]
    fn native_get_string(
        env: &mut jni::JNIEnv<'_>,
        _class: jclass,
        key: &JavaStr,
        def: jstring,
    ) -> jstring {
        let value = props::read_with(&Cow::from(&**key), |value| {
            if value.is_empty() {
                None
            } else {
                Some(new_java_string(env, value))
            }
        })
        .flatten();
        if let Some(value) = value {
            return value;
        }
        if def.is_null() && !env.exception_check().unwrap_or(false) {
            return new_java_string(env, "");
        }
        def
    }

    /// @FastNative: "(Ljava/lang/String;I)I"
    #[jni_method(fast, name = "native_get_int")]
    fn native_get_int(_env: &mut jni::JNIEnv<'_>, _class: jclass, key: &JavaStr, def: i32) -> i32 {
        props::read_with(&Cow::from(&**key), libbase_parse::parse_int).flatten().unwrap_or(def)
    }

    /// @FastNative: "(Ljava/lang/String;J)J"
    #[jni_method(fast, name = "native_get_long")]
    fn native_get_long(_env: &mut jni::JNIEnv<'_>, _class: jclass, key: &JavaStr, def: i64) -> i64 {
        props::read_with(&Cow::from(&**key), libbase_parse::parse_long).flatten().unwrap_or(def)
    }

    /// @FastNative: "(Ljava/lang/String;Z)Z"
    #[jni_method(fast, name = "native_get_boolean")]
    fn native_get_boolean(
        _env: &mut jni::JNIEnv<'_>,
        _class: jclass,
        key: &JavaStr,
        def: bool,
    ) -> bool {
        props::read_with(&Cow::from(&**key), libbase_parse::parse_bool).flatten().unwrap_or(def)
    }

    /// @FastNative: "(Ljava/lang/String;)J"
    ///
    /// Returns the property's `prop_info` pointer as a long, or 0 if the
    /// property does not exist; `SystemProperties.find` maps 0 to null and
    /// only wraps non-zero values in a `Handle`.
    #[jni_method(fast)]
    fn native_find(_env: &mut jni::JNIEnv<'_>, _class: jclass, name: &JavaStr) -> i64 {
        props::find(&Cow::from(&**name))
    }

    /// @FastNative: "(J)Ljava/lang/String;"
    ///
    /// Unlike the C++, a zero handle or unreadable value returns null instead
    /// of undefined behavior; unreachable anyway, since `Handle` guarantees a
    /// valid handle.
    #[jni_method(fast, name = "native_get")]
    fn native_get_string_handle(env: &mut jni::JNIEnv<'_>, _class: jclass, handle: i64) -> jstring {
        // SAFETY: `handle` came from native_find: SystemProperties.Handle is
        // private to SystemProperties and only constructed around a non-zero
        // native_find result.
        unsafe { props::read_handle_with(handle, |value| new_java_string(env, value)) }
            .unwrap_or(std::ptr::null_mut())
    }

    /// @CriticalNative: "(JI)I"
    #[jni_method(critical, name = "native_get_int")]
    fn native_get_int_handle(handle: i64, def: i32) -> i32 {
        // SAFETY: `handle` came from native_find; see native_get_string_handle.
        unsafe { props::read_handle_with(handle, libbase_parse::parse_int) }
            .flatten()
            .unwrap_or(def)
    }

    /// @CriticalNative: "(JJ)J"
    #[jni_method(critical, name = "native_get_long")]
    fn native_get_long_handle(handle: i64, def: i64) -> i64 {
        // SAFETY: `handle` came from native_find; see native_get_string_handle.
        unsafe { props::read_handle_with(handle, libbase_parse::parse_long) }
            .flatten()
            .unwrap_or(def)
    }

    /// @CriticalNative: "(JZ)Z"
    #[jni_method(critical, name = "native_get_boolean")]
    fn native_get_boolean_handle(handle: i64, def: bool) -> bool {
        // SAFETY: `handle` came from native_find; see native_get_string_handle.
        unsafe { props::read_handle_with(handle, libbase_parse::parse_bool) }
            .flatten()
            .unwrap_or(def)
    }

    /// Regular native (it IPCs to init and can block): "(Ljava/lang/String;Ljava/lang/String;)V"
    ///
    /// A null `value` sets the empty string — the property service has no
    /// delete. On failure throws `RuntimeException`, blaming a failed system
    /// call when `errno` is set and init (which logs its own reason)
    /// otherwise, with the C++'s exact messages.
    #[jni_method]
    fn native_set(
        env: &mut jni::JNIEnv<'_>,
        _class: jclass,
        key: &JavaStr,
        value: Option<&JavaStr>,
    ) {
        let key = Cow::from(&**key);
        let value = value.map_or(Cow::Borrowed(""), |value| Cow::from(&**value));
        clear_errno();
        if props::set(&key, &value).is_err() {
            let errno = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
            let message = if errno != 0 {
                format!(
                    "failed to set system property \"{key}\" to \"{value}\": {}",
                    errno_message(errno)
                )
            } else {
                format!(
                    "failed to set system property \"{key}\" to \"{value}\" (check logcat for reason)"
                )
            };
            JniError::Runtime(message).throw_on(env);
        }
    }

    /// Regular native: "()V"
    ///
    /// Called with the Java-side lock held; only the first call registers.
    #[jni_method]
    fn native_add_change_callback(env: &mut jni::JNIEnv<'_>, class: jclass) {
        // SAFETY: `class` is the live jclass the JVM passed to this native
        // method, valid for the duration of the call.
        unsafe { sysprop_change::add_java_change_listener(env, class) };
    }

    /// Regular native: "()V"
    #[jni_method]
    fn native_report_sysprop_change(_env: &mut jni::JNIEnv<'_>, _class: jclass) {
        sysprop_change::report_sysprop_change();
    }
}
