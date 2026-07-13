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

//! `android.os.Trace`'s JNI natives.
//!
//! Every method forwards to `tracing_perfetto`, the framework tracing facade,
//! except `nativeSetAppTracingAllowed`, which pokes the legacy atrace tag
//! state (see the method). Tracing is a hot path, so name handling is
//! allocation-free and bounded: [`BoundedUtfChars`] copies at most 1024
//! UTF-16 code units of each Java string into a stack buffer — longer names
//! truncate silently — and the bytes are sanitized
//! in place because `'\n'` and `'|'` are markup in the atrace `trace_marker`
//! format. A null name becomes `"(null)"`.

use jni_support::BoundedUtfChars;
use std::ffi::CStr;
use tracing_perfetto::Category;

/// Capacity for a copied trace name: 1024 UTF-16 code units at 3 bytes of
/// Modified UTF-8 each, plus the NUL terminator.
const TRACE_NAME_CAP: usize = 3073;

/// Replaces the bytes the atrace `trace_marker` format reserves — `'\n'` and
/// `'|'` — with spaces. ASCII-for-ASCII, so multi-byte sequences are safe.
fn sanitize(bytes: &mut [u8]) {
    for byte in bytes {
        if *byte == b'\n' || *byte == b'|' {
            *byte = b' ';
        }
    }
}

/// Converts the raw `TRACE_TAG_*` bits Java passes as a signed long into the
/// equivalent tracing_perfetto category.
fn category(tag: i64) -> Category {
    Category::from_bits(tag as u64)
}

/// Runs `f` with the sanitized, NUL-terminated contents of `name`, copied
/// into a stack buffer capped at 1024 UTF-16 code units. A `None` `name`
/// (the null Java string) yields the literal `"(null)"`. `f` receives the env
/// back so two-string methods can nest a second call.
fn with_trace_name<R>(
    env: &mut jni::Env<'_>,
    name: Option<&jni::objects::JString<'_>>,
    f: impl FnOnce(&mut jni::Env<'_>, &CStr) -> R,
) -> Result<R, jni_support::JniError> {
    match name {
        Some(name) => {
            let mut copied = BoundedUtfChars::<TRACE_NAME_CAP>::copy_from(env, name)?;
            sanitize(copied.as_bytes_mut());
            Ok(f(env, copied.as_cstr()))
        }
        None => Ok(f(env, c"(null)")),
    }
}

/// Registers `android.os.Trace`'s native methods with the JVM.
///
/// Call during JNI startup, before any Java code uses `Trace`. Panics if the
/// class or a method is missing; registration failures are fatal.
pub fn register(env: &mut jni::Env<'_>) {
    trace::register(env);
}

/// The `android.os.Trace` native methods.
#[jni_platform_macros::jni_module("android/os/Trace")]
pub mod trace {
    use super::{category, with_trace_name};
    use jni::objects::{JClass, JString};

    /// Refreshes the process's cached atrace tag enablement.
    ///
    /// Ignores `allowed`: the caller has already changed the
    /// process's tracing permissions, and this re-reads the resulting state.
    #[jni_method]
    fn nativeSetAppTracingAllowed(_env: &mut jni::Env<'_>, _class: JClass, _allowed: bool) {
        // TODO(b/331916606): this is load-bearing for an app to notice that it is
        // traced after post-zygote-fork specialisation.
        atrace::atrace_update_tags();
    }

    /// No-op: the tracing-enabled flag is tracked entirely in Java.
    #[jni_method]
    fn nativeSetTracingEnabled(_env: &mut jni::Env<'_>, _class: JClass, _enabled: bool) {
        // no-op
    }

    /// Emits a counter sample of `value` named `name` on trace `tag`.
    #[jni_method(fast)]
    fn nativeTraceCounter(
        env: &mut jni::Env<'_>,
        _class: JClass,
        tag: i64,
        name: Option<&JString>,
        value: i64,
    ) -> Result<(), jni_support::JniError> {
        with_trace_name(env, name, |_env, name| {
            tracing_perfetto::trace_counter(category(tag), name, value)
        })
    }

    /// Begins a synchronous trace slice named `name` on trace `tag`.
    #[jni_method(fast)]
    fn nativeTraceBegin(
        env: &mut jni::Env<'_>,
        _class: JClass,
        tag: i64,
        name: Option<&JString>,
    ) -> Result<(), jni_support::JniError> {
        with_trace_name(env, name, |_env, name| tracing_perfetto::trace_begin(category(tag), name))
    }

    /// Begins an async trace slice named `name` (keyed by `cookie`) on trace `tag`.
    #[jni_method(fast)]
    fn nativeAsyncTraceBegin(
        env: &mut jni::Env<'_>,
        _class: JClass,
        tag: i64,
        name: Option<&JString>,
        cookie: i32,
    ) -> Result<(), jni_support::JniError> {
        with_trace_name(env, name, |_env, name| {
            tracing_perfetto::trace_async_begin(category(tag), name, cookie)
        })
    }

    /// Ends the async trace slice named `name` (keyed by `cookie`) on trace `tag`.
    #[jni_method(fast)]
    fn nativeAsyncTraceEnd(
        env: &mut jni::Env<'_>,
        _class: JClass,
        tag: i64,
        name: Option<&JString>,
        cookie: i32,
    ) -> Result<(), jni_support::JniError> {
        with_trace_name(env, name, |_env, name| {
            tracing_perfetto::trace_async_end(category(tag), name, cookie)
        })
    }

    /// Begins an async trace slice named `name` on the named track `track_name`.
    #[jni_method(fast)]
    fn nativeAsyncTraceForTrackBegin(
        env: &mut jni::Env<'_>,
        _class: JClass,
        tag: i64,
        track_name: Option<&JString>,
        name: Option<&JString>,
        cookie: i32,
    ) -> Result<(), jni_support::JniError> {
        with_trace_name(env, track_name, |env, track_name| {
            with_trace_name(env, name, |_env, name| {
                tracing_perfetto::trace_async_begin_for_track(
                    category(tag),
                    name,
                    track_name,
                    cookie,
                )
            })
        })??;
        Ok(())
    }

    /// Ends the async trace slice (keyed by `cookie`) on the named track `track_name`.
    #[jni_method(fast)]
    fn nativeAsyncTraceForTrackEnd(
        env: &mut jni::Env<'_>,
        _class: JClass,
        tag: i64,
        track_name: Option<&JString>,
        cookie: i32,
    ) -> Result<(), jni_support::JniError> {
        with_trace_name(env, track_name, |_env, track_name| {
            tracing_perfetto::trace_async_end_for_track(category(tag), track_name, cookie)
        })
    }

    /// Emits an instant trace event named `name` on trace `tag`.
    #[jni_method(fast)]
    fn nativeInstant(
        env: &mut jni::Env<'_>,
        _class: JClass,
        tag: i64,
        name: Option<&JString>,
    ) -> Result<(), jni_support::JniError> {
        with_trace_name(env, name, |_env, name| {
            tracing_perfetto::trace_instant(category(tag), name)
        })
    }

    /// Emits an instant trace event named `name` on the named track `track_name`.
    #[jni_method(fast)]
    fn nativeInstantForTrack(
        env: &mut jni::Env<'_>,
        _class: JClass,
        tag: i64,
        track_name: Option<&JString>,
        name: Option<&JString>,
    ) -> Result<(), jni_support::JniError> {
        with_trace_name(env, track_name, |env, track_name| {
            with_trace_name(env, name, |_env, name| {
                tracing_perfetto::trace_instant_for_track(category(tag), track_name, name)
            })
        })??;
        Ok(())
    }

    /// Whether the given trace `tag` is currently enabled.
    #[jni_method(critical)]
    fn nativeIsTagEnabled(tag: i64) -> bool {
        tracing_perfetto::is_tag_enabled(category(tag))
    }

    /// Ends the current synchronous trace slice on trace `tag`.
    #[jni_method(critical)]
    fn nativeTraceEnd(tag: i64) {
        tracing_perfetto::trace_end(category(tag));
    }
}

#[cfg(test)]
mod tests {
    use super::{category, sanitize};

    // The JNI methods need a live JVM and are exercised on-device; these
    // tests cover the pure helpers. They run in the Soong host test
    // `android_runtime_rs_test`.

    #[test]
    fn sanitize_replaces_newline_and_pipe_with_spaces() {
        let mut name = *b"a|b\nc";
        sanitize(&mut name);
        assert_eq!(&name, b"a b c");
    }

    #[test]
    fn sanitize_preserves_everything_else() {
        // Includes multi-byte UTF-8 ("é"), whose bytes have the high bit set.
        let mut name = *b"swapper/0 \xC3\xA9";
        let original = name;
        sanitize(&mut name);
        assert_eq!(name, original);
    }

    #[test]
    fn sanitize_of_empty_is_a_no_op() {
        sanitize(&mut []);
    }

    #[test]
    fn category_preserves_the_tag_bits() {
        // TRACE_TAG_NOT_READY is 1 << 63, negative as a Java long; the cast
        // must keep the bit pattern.
        assert_eq!(category(1 << 12).bits(), 1 << 12);
        assert_eq!(category(i64::MIN).bits(), 1 << 63);
    }
}
