//! Android JNI proc-macro crate.
//!
//! Provides attribute macros for generating JNI boilerplate:
//!
//! - `#[jni_module("...")]` — Module-level native method collection and registration
//! - `#[jni_method(...)]` — Marks a function as a JNI native method (consumed by jni_module)
//!
//! (Cached JNI field/method ID access now uses jni-rs's `bind_java_type!`
//! macro directly, so there is no longer a `#[java_class]` attribute here.)
//!
//! For every regular / `@FastNative` `#[jni_method]` function, `#[jni_module]`
//! emits a private inner impl fn that does the argument/return bridging and
//! hands it to jni-rs's `native_method!` macro, which builds the
//! `extern "system"` shim the JVM calls AND derives the JNI signature from — and
//! type-checks the shim against — the Rust parameter types. Registration is by
//! the `const NativeMethod` `native_method!` produces (never the user's
//! function), so the registered fn pointer and its JNI descriptor are produced
//! together and cannot diverge. The shim rebuilds a safe `jni::Env`, converts
//! arguments, calls the user's function, and converts the return value, turning
//! `Err` values of `Result`-returning methods into pending Java exceptions.
//! `@CriticalNative` methods keep a hand-rolled primitive-only shim, since their
//! ABI drops the `JNIEnv`/`jclass` prefix that `native_method!` assumes.
//!
//! String parameters come in two flavors. `&str` / `Option<&str>` extract
//! the Java string into an owned `String` (one heap allocation per call).
//! `&JNIStr` / `Option<&JNIStr>` (`jni::strings::JNIStr`) borrow the string
//! via `GetStringUTFChars` with zero copies, released when the method returns;
//! the contents are Modified UTF-8, exposed as `&CStr` (via `.as_cstr()`) or
//! decoded to `Cow<str>` (via `.to_str()`). Prefer the borrowed form on hot
//! paths.
//!
//! Panics do not unwind into the JVM. Android platform binaries are built with
//! `panic = "abort"`, so any panic in a native method aborts the process —
//! equivalent to the C++ `LOG_ALWAYS_FATAL` convention. In unwinding builds
//! (e.g. host tests) the two shim kinds differ: a regular / `@FastNative`
//! method runs its body inside `native_method!`'s `with_env`, which catches the
//! panic and raises it as a Java `RuntimeException`, whereas a `@CriticalNative`
//! shim has no wrapper and aborts even when unwinding. Either way, prefer a
//! `Result` return (or `jni_call`) for recoverable errors over panicking.

extern crate proc_macro;

pub(crate) mod class;
pub(crate) mod module;
pub(crate) mod sig;

use proc_macro::TokenStream;

/// Attribute macro for declaring a JNI module with native method registration.
///
/// Processes a module block, collecting all `#[jni_method]` functions, and
/// generates a `register(env)` function that registers them as native methods
/// with the JVM.
///
/// For modules that also cache field/method IDs via `bind_java_type!`, write a
/// top-level registration function that first forces the binding's API cache
/// (`MyTypeAPI::get(env, &LoaderContext::None)`) and then calls `register()`:
///
/// ```ignore
/// pub fn register_all(env: &mut jni::Env<'_>) {
///     MyTypeAPI::get(env, &jni::refs::LoaderContext::None).expect("init");
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
///
/// # Additional attributes
///
/// - `#[class = "..."]` on parameters for JObject/JObjectArray class specification
/// - `#[returns = "..."]` on the function for object return types
#[proc_macro_attribute]
pub fn jni_method(_attr: TokenStream, item: TokenStream) -> TokenStream {
    // Identity transform — consumed by jni_module
    item
}
