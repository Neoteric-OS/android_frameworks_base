//! Android JNI proc-macro crate.
//!
//! This layer provides the JNI type-name helpers used to derive method
//! signatures. The attribute macros (`#[jni_module]` / `#[jni_method]`) that
//! consume them are built on top of this foundation.

// The signature helpers are a leaf utility library: their only consumers are the
// attribute macros added in later layers, so nothing in this crate reaches them
// yet. Allow the otherwise-unavoidable `dead_code` until that consumer exists.
#[allow(dead_code)]
mod sig;
