//! Android JNI support library.
//!
//! Provides safe Rust abstractions for common JNI patterns found in the
//! Android framework's native code:
//!
//! - [`JniError`] — Typed error enum mapping to Java exceptions
//! - [`jni_call`] — Wrapper that catches errors and throws Java exceptions
//! - [`NativeHandle`] — Safe jlong ↔ Box<T> conversions
//! - [`BoundedUtfChars`] — Allocation-free, truncating copy of a Java string
//!
//! A panic that reaches a JNI boundary aborts the process. Android platform
//! binaries are built with `panic = "abort"`, and even in unwinding builds a
//! panic crossing an `extern "system"` function aborts. This matches the C++
//! `LOG_ALWAYS_FATAL` convention: a native method that cannot uphold its
//! contract takes down the process loudly rather than continuing in a corrupt
//! state. Use [`jni_call`] or [`throw!`](crate::throw) for recoverable errors,
//! and reserve panics for genuinely fatal conditions.

pub mod bounded_utf_chars;
pub mod error;
pub mod native_handle;
pub mod throw;

pub use bounded_utf_chars::BoundedUtfChars;
pub use error::{jni_call, JniError};
pub use native_handle::{JLong, NativeHandle};
pub use throw::JavaException;
