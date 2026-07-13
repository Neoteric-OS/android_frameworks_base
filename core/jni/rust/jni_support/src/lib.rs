//! Android JNI support library.
//!
//! Provides safe Rust abstractions for common JNI patterns found in the
//! Android framework's native code:
//!
//! - [`JniError`] — Typed error enum mapping to Java exceptions
//! - [`jni_call`] — Wrapper that catches errors and throws Java exceptions
//!
//! A panic that reaches a JNI boundary aborts the process. Android platform
//! binaries are built with `panic = "abort"`, and even in unwinding builds a
//! panic crossing an `extern "system"` function aborts. This matches the C++
//! `LOG_ALWAYS_FATAL` convention: a native method that cannot uphold its
//! contract takes down the process loudly rather than continuing in a corrupt
//! state. Use [`jni_call`] or [`throw!`](crate::throw) for recoverable errors,
//! and reserve panics for genuinely fatal conditions.

pub mod error;
pub mod throw;

pub use error::{jni_call, JniError};
pub use throw::JavaException;
