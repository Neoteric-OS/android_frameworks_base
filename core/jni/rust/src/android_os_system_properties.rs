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

//! `android.os.SystemProperties`'s JNI natives.
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
//! [`libbase_parse`](crate::libbase_parse) — which implements libbase's parser
//! semantics — rejects the value. On a failed set, `native_set` throws
//! `RuntimeException`, blaming a failed system call when `errno` was set and
//! init otherwise.

/// Property store access. rustutils uses bionic's property area on Android and
/// libbase's process-local `__system_property_*` fallback on host.
mod props {
    use rustutils::system_properties::{self, PropertyHandle};

    /// Calls `f` with `name`'s current value, without copying it. Errors
    /// (absent property, a name with an interior NUL, a non-UTF-8 value) all
    /// read as "no value".
    pub(super) fn read_with<T>(name: &str, f: impl FnOnce(&str) -> T) -> Option<T> {
        let handle = system_properties::find(name).ok().flatten()?;
        handle.read_with(f).ok()
    }

    /// Looks up `name`, returning its `prop_info` pointer as the Java handle
    /// value, or 0 if the property does not exist.
    pub(super) fn find(name: &str) -> i64 {
        match system_properties::find(name) {
            Ok(Some(handle)) => handle.as_raw() as i64,
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
    pub(super) unsafe fn read_handle_with<T>(handle: i64, f: impl FnOnce(&str) -> T) -> Option<T> {
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

/// Sets the calling thread's `errno` to 0 so a later failure can be
/// attributed: a set that fails in a system call leaves `errno` set, while
/// init rejecting the request leaves it untouched.
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

/// The `strerror` text for `errno`.
fn errno_message(errno: i32) -> String {
    std::io::Error::from_raw_os_error(errno).to_string()
}

/// Builds a Java string from `value`, or a null `JString` when allocation
/// fails (leaving the ART exception, e.g. OOM, pending for Java).
fn new_java_string<'local>(
    env: &mut jni::Env<'local>,
    value: &str,
) -> jni::objects::JString<'local> {
    env.new_string(value)
        .unwrap_or_else(|_| jni::objects::JString::null())
}

/// Registers `android.os.SystemProperties`'s native methods with the JVM.
///
/// Call during JNI startup, before any Java code uses `SystemProperties`.
/// Panics if the class or a method is missing; registration failures are fatal.
pub fn register(env: &mut jni::Env<'_>) {
    system_properties::register(env);
}

/// The `android.os.SystemProperties` native methods.
#[jni_platform_macros::jni_module("android/os/SystemProperties")]
pub mod system_properties {
    use super::{clear_errno, errno_message, new_java_string, props};
    use crate::libbase_parse;
    use crate::sysprop_change;
    use jni::objects::{JClass, JString};
    use jni::strings::JNIStr;
    use jni_support::JniError;
    use std::borrow::Cow;

    /// Reads the string property `key`.
    ///
    /// An absent property or one holding the empty value yields `def`
    /// untouched; a null `def` becomes `""` unless an exception is already
    /// pending.
    #[jni_method(fast, name = "native_get")]
    fn native_get_string<'local>(
        env: &mut jni::Env<'local>,
        _class: JClass,
        key: &JNIStr,
        def: JString<'local>,
    ) -> JString<'local> {
        let value = props::read_with(&key.to_str(), |value| {
            (!value.is_empty()).then(|| value.to_owned())
        })
        .flatten();
        if let Some(value) = value {
            return new_java_string(env, &value);
        }
        if def.is_null() && !env.exception_check() {
            return new_java_string(env, "");
        }
        // Property unset: hand the caller's default back unchanged. `def` is
        // tied to this call's `'local` frame, so it needs no re-wrapping.
        def
    }

    /// Reads integer property `key`, falling back to `def` if unset or unparseable.
    #[jni_method(fast, name = "native_get_int")]
    fn native_get_int(_env: &mut jni::Env<'_>, _class: JClass, key: &JNIStr, def: i32) -> i32 {
        props::read_with(&key.to_str(), libbase_parse::parse_int)
            .flatten()
            .unwrap_or(def)
    }

    /// Reads long property `key`, falling back to `def` if unset or unparseable.
    #[jni_method(fast, name = "native_get_long")]
    fn native_get_long(_env: &mut jni::Env<'_>, _class: JClass, key: &JNIStr, def: i64) -> i64 {
        props::read_with(&key.to_str(), libbase_parse::parse_long)
            .flatten()
            .unwrap_or(def)
    }

    /// Reads boolean property `key`, falling back to `def` if unset or unparseable.
    #[jni_method(fast, name = "native_get_boolean")]
    fn native_get_boolean(
        _env: &mut jni::Env<'_>,
        _class: JClass,
        key: &JNIStr,
        def: bool,
    ) -> bool {
        props::read_with(&key.to_str(), libbase_parse::parse_bool)
            .flatten()
            .unwrap_or(def)
    }

    /// Looks up `name`, returning its `prop_info` pointer as a long handle.
    ///
    /// Returns 0 if the property does not exist; `SystemProperties.find` maps 0
    /// to null and only wraps non-zero values in a `Handle`.
    #[jni_method(fast)]
    fn native_find(_env: &mut jni::Env<'_>, _class: JClass, name: &JNIStr) -> i64 {
        props::find(&name.to_str())
    }

    /// Reads the string value of the property behind `handle`.
    ///
    /// A zero handle or unreadable value returns null; unreachable anyway,
    /// since `Handle` guarantees a valid handle.
    #[jni_method(fast, name = "native_get")]
    fn native_get_string_handle<'local>(
        env: &mut jni::Env<'local>,
        _class: JClass,
        handle: i64,
    ) -> JString<'local> {
        // The Java string is built outside the read closure: a closure that
        // returned a `JString<'local>` would have to satisfy the read's
        // higher-ranked `FnOnce(&str)` bound while yielding a value that
        // escapes with `'local`, which the borrow checker rejects.
        //
        // SAFETY: `handle` came from native_find: SystemProperties.Handle is
        // private to SystemProperties and only constructed around a non-zero
        // native_find result.
        match unsafe { props::read_handle_with(handle, str::to_owned) } {
            Some(value) => new_java_string(env, &value),
            None => JString::null(),
        }
    }

    /// Reads the integer value behind `handle`, falling back to `def` if unparseable.
    #[jni_method(critical, name = "native_get_int")]
    fn native_get_int_handle(handle: i64, def: i32) -> i32 {
        // SAFETY: `handle` came from native_find; see native_get_string_handle.
        unsafe { props::read_handle_with(handle, libbase_parse::parse_int) }
            .flatten()
            .unwrap_or(def)
    }

    /// Reads the long value behind `handle`, falling back to `def` if unparseable.
    #[jni_method(critical, name = "native_get_long")]
    fn native_get_long_handle(handle: i64, def: i64) -> i64 {
        // SAFETY: `handle` came from native_find; see native_get_string_handle.
        unsafe { props::read_handle_with(handle, libbase_parse::parse_long) }
            .flatten()
            .unwrap_or(def)
    }

    /// Reads the boolean value behind `handle`, falling back to `def` if unparseable.
    #[jni_method(critical, name = "native_get_boolean")]
    fn native_get_boolean_handle(handle: i64, def: bool) -> bool {
        // SAFETY: `handle` came from native_find; see native_get_string_handle.
        unsafe { props::read_handle_with(handle, libbase_parse::parse_bool) }
            .flatten()
            .unwrap_or(def)
    }

    /// Sets property `key` to `value`, throwing `RuntimeException` on failure.
    ///
    /// A null `value` sets the empty string — the property service has no
    /// delete. On failure throws `RuntimeException`, blaming a failed system
    /// call when `errno` is set and init (which logs its own reason)
    /// otherwise.
    #[jni_method]
    fn native_set(
        _env: &mut jni::Env<'_>,
        _class: JClass,
        key: &JNIStr,
        value: Option<&JNIStr>,
    ) -> Result<(), JniError> {
        let key = key.to_str();
        let value = value.map_or(Cow::Borrowed(""), |value| value.to_str());
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
            return Err(JniError::Runtime(message));
        }
        Ok(())
    }

    /// Registers the JVM callback fired when any system property changes.
    ///
    /// Called with the Java-side lock held; only the first call registers.
    #[jni_method]
    fn native_add_change_callback(env: &mut jni::Env<'_>, class: JClass) {
        sysprop_change::add_java_change_listener(env, &class);
    }

    /// Notifies registered listeners that system properties have changed.
    #[jni_method]
    fn native_report_sysprop_change(_env: &mut jni::Env<'_>, _class: JClass) {
        sysprop_change::report_sysprop_change();
    }
}
