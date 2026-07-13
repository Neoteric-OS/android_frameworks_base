//! Android JNI proc-macro crate.
//!
//! Provides attribute macros for generating JNI boilerplate:
//!
//! - `#[java_class("...")]` — Cached JNI field/method ID structs

extern crate proc_macro;

pub(crate) mod class;
pub(crate) mod java_class;
// The raw-type signature tables are consumed by the upcoming #[jni_module];
// allow dead code until it lands.
#[allow(dead_code)]
pub(crate) mod sig;

use proc_macro::TokenStream;

/// Attribute macro for declaring a cached JNI ID struct.
///
/// Transforms a struct annotated with `#[field(...)]`, `#[method(...)]`, and
/// `#[static_method(...)]` fields into a lazily-initialized cache of JNI
/// field and method IDs.
///
/// # Example
///
/// ```ignore
/// #[java_class("android/view/KeyEvent")]
/// struct KeyEventOffsets {
///     #[field("int")]
///     m_device_id: jfieldID,
///     #[method("() -> void")]
///     recycle: jmethodID,
///     #[static_method("(int, long) -> android.view.KeyEvent")]
///     obtain: jmethodID,
/// }
/// ```
///
/// Generates:
/// - The struct with a `class` field for the GlobalRef
/// - `KeyEventOffsets::init(env)` to initialize the cache
/// - `KeyEventOffsets::get()` to retrieve the cached values
#[proc_macro_attribute]
pub fn java_class(attr: TokenStream, item: TokenStream) -> TokenStream {
    java_class::expand_java_class(attr.into(), item.into()).into()
}
