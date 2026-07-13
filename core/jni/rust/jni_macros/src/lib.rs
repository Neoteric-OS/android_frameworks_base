//! Android JNI proc-macro crate.
//!
//! Provides attribute macros for generating JNI boilerplate. This first
//! slice contains the signature-derivation tables the macros are built on:
//! mappings from Rust JNI types to the JNI descriptor strings that
//! `RegisterNatives` expects.

// The signature tables are consumed by the attribute macros built on top of
// them; allow dead code until the first consumer lands.
#[allow(dead_code)]
pub(crate) mod sig;
