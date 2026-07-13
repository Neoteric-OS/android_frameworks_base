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

//! Rust conversion of `android_os_Trace.cpp`.
//!
//! Every method forwards to `tracing_perfetto`, the framework tracing facade,
//! except `nativeSetAppTracingAllowed`, which pokes the legacy atrace tag
//! state (see the method). Tracing is a hot path, so name handling is
//! allocation-free and bounded: [`BoundedUtfChars`] copies at most 1024
//! UTF-16 code units of each Java string into a stack buffer — longer names
//! truncate silently, the same clamp as the C++ — and the bytes are sanitized
//! in place because `'\n'` and `'|'` are markup in the atrace `trace_marker`
//! format. A null name becomes `"(null)"`, matching the C++.

use jni_support::BoundedUtfChars;
use std::ffi::CStr;
use tracing_perfetto::Category;

/// Capacity for a copied trace name: 1024 UTF-16 code units at 3 bytes of
/// Modified UTF-8 each, plus the NUL terminator. The same 1024-unit clamp as
/// the C++, which used an oversized 4097-byte buffer.
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
fn category(tag: jni::sys::jlong) -> Category {
    Category::from_bits(tag as u64)
}

/// Runs `f` with the sanitized, NUL-terminated contents of `name`, copied
/// into a stack buffer capped at 1024 UTF-16 code units. A null `name` yields
/// the literal `"(null)"`. The Rust equivalent of the C++ `withString`
/// helper; like it, `f` receives the env back so two-string methods can nest
/// a second call.
fn with_trace_name<R>(
    env: &mut jni::JNIEnv<'_>,
    name: jni::sys::jstring,
    f: impl FnOnce(&mut jni::JNIEnv<'_>, &CStr) -> R,
) -> R {
    // SAFETY: `name` is the raw jstring the JVM passed to the enclosing
    // native method, so it is null or a valid `java.lang.String` reference
    // that stays live for the duration of the method.
    match unsafe { BoundedUtfChars::<TRACE_NAME_CAP>::copy_from(env, name) } {
        Some(mut copied) => {
            sanitize(copied.as_bytes_mut());
            f(env, copied.as_cstr())
        }
        None => f(env, c"(null)"),
    }
}

/// Registers `android.os.Trace`'s native methods with the JVM.
///
/// Call during JNI startup, before any Java code uses `Trace`. Panics if the
/// class or a method is missing, matching the C++ `RegisterMethodsOrDie`
/// semantics.
pub fn register(env: &mut jni::JNIEnv<'_>) {
    trace::register(env);
}

/// The `android.os.Trace` native methods.
#[jni_macros::jni_module("android/os/Trace")]
pub mod trace {
    use super::{category, with_trace_name};
    use jni::sys::{jclass, jint, jlong, jstring};

    /// Regular: "(Z)V"
    ///
    /// Like the C++, ignores `allowed`: the caller has already changed the
    /// process's tracing permissions, and this re-reads the resulting state.
    #[jni_method]
    fn nativeSetAppTracingAllowed(_env: &mut jni::JNIEnv<'_>, _class: jclass, _allowed: bool) {
        // TODO(b/331916606): this is load-bearing for an app to notice that it is
        // traced after post-zygote-fork specialisation.
        atrace::atrace_update_tags();
    }

    /// Regular: "(Z)V"
    #[jni_method]
    fn nativeSetTracingEnabled(_env: &mut jni::JNIEnv<'_>, _class: jclass, _enabled: bool) {
        // no-op
    }

    /// @FastNative: "(JLjava/lang/String;J)V"
    #[jni_method(fast)]
    fn nativeTraceCounter(
        env: &mut jni::JNIEnv<'_>,
        _class: jclass,
        tag: jlong,
        name: jstring,
        value: jlong,
    ) {
        with_trace_name(env, name, |_env, name| {
            tracing_perfetto::trace_counter(category(tag), name, value)
        });
    }

    /// @FastNative: "(JLjava/lang/String;)V"
    #[jni_method(fast)]
    fn nativeTraceBegin(env: &mut jni::JNIEnv<'_>, _class: jclass, tag: jlong, name: jstring) {
        with_trace_name(env, name, |_env, name| tracing_perfetto::trace_begin(category(tag), name));
    }

    /// @FastNative: "(JLjava/lang/String;I)V"
    #[jni_method(fast)]
    fn nativeAsyncTraceBegin(
        env: &mut jni::JNIEnv<'_>,
        _class: jclass,
        tag: jlong,
        name: jstring,
        cookie: jint,
    ) {
        with_trace_name(env, name, |_env, name| {
            tracing_perfetto::trace_async_begin(category(tag), name, cookie)
        });
    }

    /// @FastNative: "(JLjava/lang/String;I)V"
    #[jni_method(fast)]
    fn nativeAsyncTraceEnd(
        env: &mut jni::JNIEnv<'_>,
        _class: jclass,
        tag: jlong,
        name: jstring,
        cookie: jint,
    ) {
        with_trace_name(env, name, |_env, name| {
            tracing_perfetto::trace_async_end(category(tag), name, cookie)
        });
    }

    /// @FastNative: "(JLjava/lang/String;Ljava/lang/String;I)V"
    #[jni_method(fast)]
    fn nativeAsyncTraceForTrackBegin(
        env: &mut jni::JNIEnv<'_>,
        _class: jclass,
        tag: jlong,
        track_name: jstring,
        name: jstring,
        cookie: jint,
    ) {
        with_trace_name(env, track_name, |env, track_name| {
            with_trace_name(env, name, |_env, name| {
                tracing_perfetto::trace_async_begin_for_track(
                    category(tag),
                    name,
                    track_name,
                    cookie,
                )
            })
        });
    }

    /// @FastNative: "(JLjava/lang/String;I)V"
    #[jni_method(fast)]
    fn nativeAsyncTraceForTrackEnd(
        env: &mut jni::JNIEnv<'_>,
        _class: jclass,
        tag: jlong,
        track_name: jstring,
        cookie: jint,
    ) {
        with_trace_name(env, track_name, |_env, track_name| {
            tracing_perfetto::trace_async_end_for_track(category(tag), track_name, cookie)
        });
    }

    /// @FastNative: "(JLjava/lang/String;)V"
    #[jni_method(fast)]
    fn nativeInstant(env: &mut jni::JNIEnv<'_>, _class: jclass, tag: jlong, name: jstring) {
        with_trace_name(env, name, |_env, name| {
            tracing_perfetto::trace_instant(category(tag), name)
        });
    }

    /// @FastNative: "(JLjava/lang/String;Ljava/lang/String;)V"
    #[jni_method(fast)]
    fn nativeInstantForTrack(
        env: &mut jni::JNIEnv<'_>,
        _class: jclass,
        tag: jlong,
        track_name: jstring,
        name: jstring,
    ) {
        with_trace_name(env, track_name, |env, track_name| {
            with_trace_name(env, name, |_env, name| {
                tracing_perfetto::trace_instant_for_track(category(tag), track_name, name)
            })
        });
    }

    /// @CriticalNative: "(J)Z"
    #[jni_method(critical)]
    fn nativeIsTagEnabled(tag: jlong) -> bool {
        tracing_perfetto::is_tag_enabled(category(tag))
    }

    /// @CriticalNative: "(J)V"
    #[jni_method(critical)]
    fn nativeTraceEnd(tag: jlong) {
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
