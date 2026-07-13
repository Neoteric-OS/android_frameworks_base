//! Android JNI proc-macro crate.
//!
//! Provides attribute macros for generating JNI boilerplate:
//!
//! - `#[java_class("...")]` — Cached JNI field/method ID structs
//! - `#[jni_module("...")]` — Module-level native method collection and registration
//! - `#[jni_method(...)]` — Marks a function as a JNI native method (consumed by jni_module)
//!
//! For every `#[jni_method]` function, `#[jni_module]` generates an
//! `unsafe extern "system"` shim whose parameters and return type are raw
//! `jni::sys` types — the calling convention the JVM actually uses — and
//! registers the shim (never the user's function) with `RegisterNatives`.
//! The shim rebuilds a safe `jni::JNIEnv`, converts arguments, calls the
//! user's function, and converts the return value.
//!
//! Panics do not unwind into the JVM: Android platform binaries are built
//! with `panic = "abort"`, and even in unwinding builds a panic crossing the
//! generated `extern "system"` shim aborts the process. A panic in a native
//! method is therefore process-fatal, equivalent to the C++
//! `LOG_ALWAYS_FATAL` convention. The generated code deliberately adds no
//! `catch_unwind`; write method bodies that report recoverable errors via
//! `jni_call` or `throw!` instead of panicking.

extern crate proc_macro;

pub(crate) mod class;
pub(crate) mod java_class;
pub(crate) mod module;
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

/// Attribute macro for declaring a JNI module with native method registration.
///
/// Processes a module block, collecting all `#[jni_method]` functions, and
/// generates a `register(env)` function that registers them as native methods
/// with the JVM.
///
/// For modules that also use `#[java_class]` structs, write a top-level
/// registration function that calls both `init()` and `register()`:
///
/// ```ignore
/// pub fn register_all(env: &mut jni::JNIEnv<'_>) {
///     MyClassInfo::init(env);
///     my_module::register(env);
/// }
/// ```
///
/// # Example
///
/// ```ignore
/// #[jni_module("android/os/SystemClock")]
/// mod system_clock {
///     #[jni_method(critical)]
///     fn elapsedRealtime() -> jlong {
///         ffi::elapsedRealtime()
///     }
///
///     #[jni_method(fast)]
///     fn currentNetworkTimeClock(env: &mut JNIEnv, _this: JClass) -> jlong {
///         // ...
///     }
/// }
/// ```
#[proc_macro_attribute]
pub fn jni_module(attr: TokenStream, item: TokenStream) -> TokenStream {
    module::expand_jni_module(attr.into(), item.into()).into()
}

/// Marker attribute for JNI native methods within a `#[jni_module]`.
///
/// This is an identity transform — it simply passes the function through
/// unchanged. The actual processing happens when `#[jni_module]` scans
/// the module's items.
///
/// The registered Java method name is the Rust function name, verbatim.
/// Use `#[jni_method(name = "...")]` when the Java name is not a legal or
/// desirable Rust identifier (e.g. it collides with another item).
///
/// # Modes
///
/// - `#[jni_method]` — Regular JNI method
/// - `#[jni_method(fast)]` — @FastNative method
/// - `#[jni_method(critical)]` — @CriticalNative method
#[proc_macro_attribute]
pub fn jni_method(_attr: TokenStream, item: TokenStream) -> TokenStream {
    // Identity transform — consumed by jni_module
    item
}
