//! Implementation of the `#[jni_module("...")]` attribute macro.
//!
//! Processes a module block, collecting `#[jni_method]` functions, and emits a
//! `register()` function that registers each as a native method with the JVM.
//!
//! Regular and `@FastNative` methods go through jni-rs's `native_method!` macro.
//! For each, this macro generates a private inner impl fn whose parameters are
//! the raw `jni::sys`/`jni::objects` values the JVM passes and whose body does
//! the Rust-friendly bridging (`&str`, `&JNIStr`, `bool`, arrays, `Result`, …)
//! before and after calling the user's function. `native_method!` wraps that
//! inner fn in the `extern "system"` shim the JVM calls (upgrading `EnvUnowned`
//! to `&mut Env`, catching panics, and resolving an `Err` to the matching
//! pending Java exception via `jni_support::ThrowJniError`), and — the reason
//! for routing through it — DERIVES the JNI signature from the signature tokens
//! and TYPE-CHECKS the inner fn's parameter and return types against it. The
//! registered fn pointer and the JNI descriptor it is registered under are
//! therefore produced together and cannot silently diverge.
//!
//! `@CriticalNative` methods cannot be expressed with `native_method!` (their
//! ABI drops the `JNIEnv`/`jclass` prefix), so they keep a hand-rolled,
//! primitive-only `extern "system"` shim and a `NativeMethod::from_raw_parts`
//! descriptor built from [`sig::primitive_sig`].

use proc_macro2::TokenStream;
use quote::{format_ident, quote};
use syn::{
    parse2, punctuated::Punctuated, Attribute, FnArg, Item, ItemFn, ItemMod, Lit, Meta,
    MetaNameValue, PatType, ReturnType, Token, Type,
};

use crate::class::JavaClass;
use crate::sig;

/// A fully processed JNI method: the module-level items it contributes plus the
/// `const NativeMethod` that `register()` collects.
///
/// Created by [`JniMethod::parse`] from a `#[jni_method]`-annotated function.
struct JniMethod {
    /// Identifier of the module-level `const NativeMethod` describing this method.
    method_const: proc_macro2::Ident,
    /// Whether this is an `@CriticalNative` method. Regular/fast methods pull
    /// `jni_support::ThrowJniError` into module scope; critical ones do not.
    is_critical: bool,
    /// The generated items placed at module scope: for regular/fast, the inner
    /// impl fn plus the `native_method!`-built `const NativeMethod`; for
    /// critical, the hand-rolled shim plus its `from_raw_parts` const.
    generated_items: TokenStream,
    /// The cleaned user function with JNI attributes stripped.
    cleaned_fn: TokenStream,
}

/// JNI method calling convention.
#[derive(Clone, Debug, PartialEq)]
enum JniMode {
    /// Regular JNI method (receives JNIEnv, jobject/jclass)
    Regular,
    /// @FastNative — receives JNIEnv, jobject/jclass but with reduced overhead
    Fast,
    /// @CriticalNative — no JNIEnv, no jobject; only primitives
    Critical,
}

/// Validated options from one `#[jni_method(...)]` attribute.
#[derive(Debug)]
struct JniMethodOptions {
    mode: JniMode,
    java_name: Option<String>,
}

/// How one user parameter is bridged from the value the JVM passes to the type
/// the user's function declares.
struct ParamBridge {
    /// The conversion strategy; it also determines the shim parameter's type.
    kind: ParamBridgeKind,
}

/// Conversion strategies for [`ParamBridge`].
///
/// The shim parameter is either an FFI-safe `jni::sys` primitive or, for object
/// arguments, the `jni::objects` wrapper itself — a `#[repr(transparent)]` value
/// the JVM's reference is captured into directly across the boundary, so no
/// `from_raw` is needed.
enum ParamBridgeKind {
    /// ABI-identical primitive (`jint`, `jlong`, …); passed through unchanged.
    /// Holds the `jni::sys` type of the shim parameter. `bool` maps here too:
    /// jni-sys 0.4 aliases `jboolean = bool`, so it is the same shim type and
    /// needs no conversion.
    Primitive(TokenStream),
    /// `&str` / `Option<&str>` from a `JString`: extracted into an owned
    /// `String` (one heap allocation per call). Non-nullable throws
    /// NullPointerException on a null reference.
    OwnedString { nullable: bool },
    /// `&JNIStr` / `Option<&JNIStr>` borrowed from a `JString` via
    /// `GetStringUTFChars` (zero copies; released on return). The bytes are
    /// Modified UTF-8. Non-nullable throws NullPointerException on a null
    /// reference.
    BorrowedString { nullable: bool },
    /// A `jni::objects` wrapper (`JByteArray`, `JObject`, …) passed to the user
    /// function by value or by reference. Holds the wrapper path.
    Wrapped { wrapper: TokenStream, by_ref: bool },
    /// The user declared a raw `jni::sys` object handle; the wrapper shim
    /// parameter is unwrapped with `.as_raw()`. Holds the wrapper path.
    Raw { wrapper: TokenStream },
}

/// Maps a user parameter type (textual form) to its bridging strategy.
///
/// Returns `None` for types outside the supported set; signature derivation
/// reports those as `Unknown JNI type` first.
fn param_bridge(ty: &str) -> Option<ParamBridge> {
    let kind = match ty {
        "jint" | "i32" => ParamBridgeKind::Primitive(quote! { jni::sys::jint }),
        "jlong" | "i64" => ParamBridgeKind::Primitive(quote! { jni::sys::jlong }),
        "jfloat" | "f32" => ParamBridgeKind::Primitive(quote! { jni::sys::jfloat }),
        "jdouble" | "f64" => ParamBridgeKind::Primitive(quote! { jni::sys::jdouble }),
        "jboolean" | "bool" => ParamBridgeKind::Primitive(quote! { jni::sys::jboolean }),
        "jbyte" | "i8" => ParamBridgeKind::Primitive(quote! { jni::sys::jbyte }),
        "jchar" | "u16" => ParamBridgeKind::Primitive(quote! { jni::sys::jchar }),
        "jshort" | "i16" => ParamBridgeKind::Primitive(quote! { jni::sys::jshort }),

        "&str" => ParamBridgeKind::OwnedString { nullable: false },
        "Option<&str>" => ParamBridgeKind::OwnedString { nullable: true },

        "&JNIStr" => ParamBridgeKind::BorrowedString { nullable: false },
        "Option<&JNIStr>" => ParamBridgeKind::BorrowedString { nullable: true },

        "jstring" => ParamBridgeKind::Raw { wrapper: quote! { jni::objects::JString } },
        "JString" => {
            ParamBridgeKind::Wrapped { wrapper: quote! { jni::objects::JString }, by_ref: false }
        }
        "&JString" => {
            ParamBridgeKind::Wrapped { wrapper: quote! { jni::objects::JString }, by_ref: true }
        }

        "jbyteArray" => ParamBridgeKind::Raw { wrapper: quote! { jni::objects::JByteArray } },
        "JByteArray" => {
            ParamBridgeKind::Wrapped { wrapper: quote! { jni::objects::JByteArray }, by_ref: false }
        }
        "&JByteArray" => {
            ParamBridgeKind::Wrapped { wrapper: quote! { jni::objects::JByteArray }, by_ref: true }
        }
        "jintArray" => ParamBridgeKind::Raw { wrapper: quote! { jni::objects::JIntArray } },
        "JIntArray" => {
            ParamBridgeKind::Wrapped { wrapper: quote! { jni::objects::JIntArray }, by_ref: false }
        }
        "&JIntArray" => {
            ParamBridgeKind::Wrapped { wrapper: quote! { jni::objects::JIntArray }, by_ref: true }
        }
        "jfloatArray" => ParamBridgeKind::Raw { wrapper: quote! { jni::objects::JFloatArray } },
        "JFloatArray" => ParamBridgeKind::Wrapped {
            wrapper: quote! { jni::objects::JFloatArray },
            by_ref: false,
        },
        "&JFloatArray" => {
            ParamBridgeKind::Wrapped { wrapper: quote! { jni::objects::JFloatArray }, by_ref: true }
        }
        "jlongArray" => ParamBridgeKind::Raw { wrapper: quote! { jni::objects::JLongArray } },
        "JLongArray" => {
            ParamBridgeKind::Wrapped { wrapper: quote! { jni::objects::JLongArray }, by_ref: false }
        }
        "&JLongArray" => {
            ParamBridgeKind::Wrapped { wrapper: quote! { jni::objects::JLongArray }, by_ref: true }
        }
        "jshortArray" => ParamBridgeKind::Raw { wrapper: quote! { jni::objects::JShortArray } },
        "JShortArray" => ParamBridgeKind::Wrapped {
            wrapper: quote! { jni::objects::JShortArray },
            by_ref: false,
        },
        "&JShortArray" => {
            ParamBridgeKind::Wrapped { wrapper: quote! { jni::objects::JShortArray }, by_ref: true }
        }
        "jdoubleArray" => ParamBridgeKind::Raw { wrapper: quote! { jni::objects::JDoubleArray } },
        "JDoubleArray" => ParamBridgeKind::Wrapped {
            wrapper: quote! { jni::objects::JDoubleArray },
            by_ref: false,
        },
        "&JDoubleArray" => ParamBridgeKind::Wrapped {
            wrapper: quote! { jni::objects::JDoubleArray },
            by_ref: true,
        },
        "jbooleanArray" => ParamBridgeKind::Raw { wrapper: quote! { jni::objects::JBooleanArray } },
        "JBooleanArray" => ParamBridgeKind::Wrapped {
            wrapper: quote! { jni::objects::JBooleanArray },
            by_ref: false,
        },
        "&JBooleanArray" => ParamBridgeKind::Wrapped {
            wrapper: quote! { jni::objects::JBooleanArray },
            by_ref: true,
        },
        "jcharArray" => ParamBridgeKind::Raw { wrapper: quote! { jni::objects::JCharArray } },
        "JCharArray" => {
            ParamBridgeKind::Wrapped { wrapper: quote! { jni::objects::JCharArray }, by_ref: false }
        }
        "&JCharArray" => {
            ParamBridgeKind::Wrapped { wrapper: quote! { jni::objects::JCharArray }, by_ref: true }
        }

        "jobject" => ParamBridgeKind::Raw { wrapper: quote! { jni::objects::JObject } },
        "JObject" => {
            ParamBridgeKind::Wrapped { wrapper: quote! { jni::objects::JObject }, by_ref: false }
        }
        "&JObject" => {
            ParamBridgeKind::Wrapped { wrapper: quote! { jni::objects::JObject }, by_ref: true }
        }
        "jobjectArray" => ParamBridgeKind::Raw { wrapper: quote! { jni::objects::JObjectArray } },
        "JObjectArray" => ParamBridgeKind::Wrapped {
            wrapper: quote! { jni::objects::JObjectArray },
            by_ref: false,
        },
        "&JObjectArray" => ParamBridgeKind::Wrapped {
            wrapper: quote! { jni::objects::JObjectArray },
            by_ref: true,
        },

        _ => return None,
    };
    Some(ParamBridge { kind })
}

/// Extracts the inner type `W` from `Option<W>` (e.g. `Option<&JIntArray>` ->
/// `&JIntArray`), mirroring [`sig::extract_result_inner`]. Returns `None` when
/// `ty` is not an `Option<...>`.
///
/// Used to recognize a nullable object parameter, whose inner object wrapper
/// determines both the bridge and the JNI descriptor. String options
/// (`Option<&str>`/`Option<&JNIStr>`) also match here, but their inner types
/// are not objects, so callers gate on [`param_bridge`] classifying the inner
/// as `Wrapped`/`Raw`.
fn strip_option(ty: &str) -> Option<String> {
    let ty = ty.trim();
    if !ty.starts_with("Option<") && !ty.starts_with("Option <") {
        return None;
    }
    let start = ty.find('<')? + 1;
    let end = ty.rfind('>')?;
    Some(ty[start..end].trim().to_string())
}

/// Drops a trailing lifetime-only generic argument, so an owned object return
/// or parameter written with the lifetime its type requires (`JString<'local>`)
/// classifies the same as the bare wrapper name [`param_bridge`] recognizes
/// (`JString`).
///
/// Only a single lifetime argument (`Foo<'x>`) is stripped — anything whose
/// generic content does not begin with `'` (a real type argument, `[jbyte]`,
/// `Result<..>` already unwrapped by the caller) is returned unchanged, so the
/// JNI descriptor a wrapper derives is unaffected.
fn strip_lifetime_arg(ty: &str) -> &str {
    let ty = ty.trim();
    if let Some(open) = ty.find('<') {
        if ty.ends_with('>') {
            // Strip only a lone lifetime argument: the content between the
            // angle brackets begins with `'` and holds no second argument (no
            // top-level comma), so `Foo<'a, Bar>` and `Foo<Bar>` are left alone.
            let inner = ty[open + 1..ty.len() - 1].trim();
            if inner.starts_with('\'') && !inner.contains(',') {
                return ty[..open].trim_end();
            }
        }
    }
    ty
}

/// How the user's return value is converted to the value the shim returns.
///
/// Object returns become `jni::objects` wrappers (not raw pointers): a wrapper
/// is `#[repr(transparent)]` so the ABI is unchanged, and it implements
/// `Default` (a null reference), which [`EnvOutcome::resolve`](jni::EnvOutcome::resolve)
/// needs for the error/panic path.
enum ReturnBridge {
    /// No return value (`()` / no declared return).
    Void,
    /// An ABI-identical primitive; passed through. Holds the `jni::sys` type.
    Primitive { sys_ty: TokenStream },
    /// `String` → `JString` via `Env::new_string`.
    StringToJString,
    /// The user returns an owned `jni::objects` wrapper directly.
    /// Holds the wrapper path.
    OwnedWrapper { wrapper: TokenStream },
    /// The user returns a raw `jni::sys` object handle; wrap it back up with
    /// `from_raw`. Holds the wrapper path.
    RawObject { wrapper: TokenStream },
}

impl ReturnBridge {
    /// Classifies a user return type (textual form). `Result<T, E>` is
    /// unwrapped to `T`; the second tuple element reports whether the
    /// original type was a `Result`.
    fn parse(ty: &str) -> Result<(Self, bool), String> {
        if let Some(inner) = sig::extract_result_inner(ty) {
            let (bridge, _) = Self::parse(&inner)?;
            return Ok((bridge, true));
        }

        // An owned wrapper return carries the lifetime its type needs
        // (`JString<'local>`); strip it so it classifies as the bare wrapper.
        let ty = strip_lifetime_arg(ty);
        let bridge = match ty {
            "()" | "" => ReturnBridge::Void,
            "String" => ReturnBridge::StringToJString,
            _ => {
                let kind =
                    param_bridge(ty).ok_or_else(|| format!("Unknown JNI type: '{}'", ty))?.kind;
                match kind {
                    ParamBridgeKind::Primitive(sys_ty) => ReturnBridge::Primitive { sys_ty },
                    ParamBridgeKind::Wrapped { wrapper, by_ref: false } => {
                        ReturnBridge::OwnedWrapper { wrapper }
                    }
                    ParamBridgeKind::Raw { wrapper } => ReturnBridge::RawObject { wrapper },
                    ParamBridgeKind::OwnedString { .. }
                    | ParamBridgeKind::BorrowedString { .. }
                    | ParamBridgeKind::Wrapped { by_ref: true, .. } => {
                        return Err(format!("Unsupported JNI return type: '{}'", ty))
                    }
                }
            }
        };
        Ok((bridge, false))
    }

    /// True for a `()` / absent return.
    fn is_void(&self) -> bool {
        matches!(self, ReturnBridge::Void)
    }

    /// The shim's return type tokens (empty for void), tied to `lifetime`.
    fn output_tokens(&self, lifetime: &TokenStream) -> TokenStream {
        match self {
            ReturnBridge::Void => quote! {},
            ReturnBridge::Primitive { sys_ty } => quote! { -> #sys_ty },
            ReturnBridge::StringToJString => quote! { -> jni::objects::JString<#lifetime> },
            ReturnBridge::OwnedWrapper { wrapper } | ReturnBridge::RawObject { wrapper } => {
                quote! { -> #wrapper<#lifetime> }
            }
        }
    }

    /// The `Ok` type of the `with_env` closure's `Result` (empty tuple for void),
    /// tied to `lifetime`.
    fn closure_ok_ty(&self, lifetime: &TokenStream) -> TokenStream {
        match self {
            ReturnBridge::Void => quote! { () },
            ReturnBridge::Primitive { sys_ty } => quote! { #sys_ty },
            ReturnBridge::StringToJString => quote! { jni::objects::JString<#lifetime> },
            ReturnBridge::OwnedWrapper { wrapper } | ReturnBridge::RawObject { wrapper } => {
                quote! { #wrapper<#lifetime> }
            }
        }
    }

    /// Wraps the user's return value (bound to `value`) as the `Ok` the closure
    /// yields. Not called for [`ReturnBridge::Void`].
    fn convert_ok(&self, value: &syn::Ident) -> TokenStream {
        match self {
            ReturnBridge::Void => quote! { ::core::result::Result::Ok(()) },
            ReturnBridge::Primitive { .. } => quote! { ::core::result::Result::Ok(#value) },
            ReturnBridge::StringToJString => {
                quote! { ::core::result::Result::Ok(env.new_string(&#value)?) }
            }
            ReturnBridge::OwnedWrapper { .. } => quote! { ::core::result::Result::Ok(#value) },
            ReturnBridge::RawObject { wrapper } => {
                // SAFETY: the user returned a raw handle from a JNI call on this
                // thread's frame; wrapping it ties it to `env`'s local frame.
                quote! {
                    ::core::result::Result::Ok(unsafe { #wrapper::from_raw(env, #value) })
                }
            }
        }
    }
}

/// Validates that a non-critical JNI method has the required leading parameters
/// (JNIEnv + jobject/jclass). Should be called once during initial processing.
///
/// # Examples
///
/// ```text
/// // Ok — valid regular method with JNIEnv + jclass:
/// fn test(env: &mut JNIEnv, clazz: jclass, x: jint) {}  → Ok(())
///
/// // Ok — critical mode skips validation:
/// fn test(x: jlong) -> jint { 0 }  (mode=Critical)      → Ok(())
///
/// // Err — too few params:
/// fn test(env: &mut JNIEnv) {}  (mode=Regular)           → Err("...at least two parameters...")
///
/// // Err — wrong first param:
/// fn test(x: jint, clazz: jclass) {}  (mode=Regular)     → Err("...first parameter must be a JNIEnv...")
/// ```
fn validate_leading_params(func: &ItemFn, mode: &JniMode) -> Result<(), String> {
    if *mode == JniMode::Critical {
        return Ok(());
    }

    let inputs: Vec<&FnArg> = func.sig.inputs.iter().collect();

    if inputs.len() < 2 {
        return Err(
            "non-critical JNI methods must have at least two parameters (JNIEnv, jobject/jclass)"
                .to_string(),
        );
    }

    if let FnArg::Typed(pat_type) = &inputs[0] {
        let ty_str = type_to_string(&pat_type.ty);
        if !is_env_type(&ty_str) {
            return Err(format!("first parameter must be a JNIEnv type, found '{}'", ty_str));
        }
    }

    if let FnArg::Typed(pat_type) = &inputs[1] {
        let ty_str = type_to_string(&pat_type.ty);
        if !is_this_type(&ty_str) {
            return Err(format!(
                "second parameter must be a jobject/jclass type, found '{}'",
                ty_str
            ));
        }
    }

    Ok(())
}

/// Extracts the return type as a string from a function's output declaration.
fn return_type_str(output: &ReturnType) -> String {
    match output {
        ReturnType::Default => "()".to_string(),
        ReturnType::Type(_, ty) => type_to_string(ty),
    }
}

impl JniMethod {
    /// Parses a `#[jni_method]`-annotated function into a fully processed `JniMethod`.
    ///
    /// Validates parameters, generates the method's module-level items (inner
    /// impl fn + `const NativeMethod` for regular/fast; hand-rolled shim +
    /// `const NativeMethod` for critical), and strips JNI attributes from the
    /// user function.
    ///
    /// # Example
    ///
    /// Given:
    /// ```text
    /// #[jni_method]
    /// fn nativeGetValue(env: &mut Env, clazz: jclass, ptr: jlong) -> jint { 0 }
    /// ```
    ///
    /// Returns `Ok(JniMethod)` whose `generated_items` hold a
    /// `fn __jni_impl_nativeGetValue(env, __this: JClass, __arg0: jlong) ->
    /// Result<jint, JniError>` and a `const __NATIVE_METHOD_nativeGetValue:
    /// jni::NativeMethod = jni::native_method! { ... }` whose derived descriptor
    /// is `"(J)I"`, and whose `method_const` names that const.
    ///
    /// Returns `Err(compile_error TokenStream)` if validation fails (e.g., missing
    /// env/this parameters, unknown types).
    fn parse(func: &ItemFn, module_package: Option<&str>) -> Result<Self, TokenStream> {
        let jni_attr = find_jni_method_attr(&func.attrs)
            .expect("JniMethod::parse called without jni_method attr");

        let options = parse_jni_method_options(&jni_attr).map_err(|e| e.to_compile_error())?;
        let mode = options.mode;

        validate_leading_params(func, &mode)
            .map_err(|e| syn::Error::new_spanned(func, e).to_compile_error())?;

        let java_name =
            options.java_name.unwrap_or_else(|| derive_java_name(&func.sig.ident.to_string()));
        let returns_attr = find_returns_attr(&func.attrs);

        let method_const = format_ident!("__NATIVE_METHOD_{}", func.sig.ident);
        let is_critical = mode == JniMode::Critical;

        let generated_items = if is_critical {
            generate_critical_method(func, &java_name, &method_const)
        } else {
            generate_native_method(
                func,
                &java_name,
                module_package,
                returns_attr.as_deref(),
                &method_const,
            )
        }
        .map_err(|e| syn::Error::new_spanned(func, e).to_compile_error())?;

        let cleaned = strip_jni_attrs(func);

        Ok(JniMethod {
            method_const,
            is_critical,
            generated_items,
            cleaned_fn: quote! { #cleaned },
        })
    }
}

/// Generates the `register()` function that registers all native methods with the JVM.
///
/// # Example
///
/// Given `class_path = "android/view/MotionEvent"` and one method whose
/// `const NativeMethod` is `__NATIVE_METHOD_nativeGetId`, generates:
///
/// ```text
/// pub fn register(env: &mut jni::Env<'_>) {
///     let class = env.find_class("android/view/MotionEvent").expect(...);
///     let methods = [__NATIVE_METHOD_nativeGetId];
///     env.register_native_methods(&class, &methods).expect(...);
/// }
/// ```
///
/// With an empty method list, generates a no-op `register(_env)` function.
fn generate_register_fn(class_path: &str, methods: &[JniMethod]) -> TokenStream {
    if methods.is_empty() {
        return quote! {
            /// Registers all native methods in this module with the JVM.
            ///
            /// Must be called during JNI_OnLoad or equivalent initialization.
            pub fn register(_env: &mut jni::Env<'_>) {
            }
        };
    }

    let consts: Vec<&proc_macro2::Ident> = methods.iter().map(|m| &m.method_const).collect();

    quote! {
        /// Registers all native methods in this module with the JVM.
        ///
        /// Must be called during JNI_OnLoad or equivalent initialization.
        pub fn register(env: &mut jni::Env<'_>) {
            let class = env.find_class(jni::jni_str!(#class_path))
                .expect("Failed to find JNI class");
            let methods = [
                #(#consts),*
            ];
            // SAFETY: every entry is a `NativeMethod` produced together with its
            // fn pointer — by `native_method!`, which type-checks the pointer
            // against its own derived JNI signature, or (for @CriticalNative) by
            // `from_raw_parts` over a primitive-only shim matching its descriptor
            // — so a pointer can never be registered under a mismatched signature.
            unsafe { env.register_native_methods(&class, &methods) }
                .expect("Failed to register native methods");
        }
    }
}

/// Processes a `#[jni_module("android/view/MotionEvent")]` annotated module.
///
/// Collects all `#[jni_method]` functions and generates, for each, the module
/// items that register it (an inner impl fn and a `const NativeMethod` built by
/// `native_method!`, or a hand-rolled critical shim), plus a module-level
/// `register()` function.
///
/// # Example
///
/// ```text
/// // Input:
/// #[jni_module("android/util/Log")]
/// mod log {
///     #[jni_method]
///     fn println_native(env: &mut jni::Env, clazz: jclass, tag: &str, level: i32) -> i32 {
///         0
///     }
/// }
///
/// // Output:
/// mod log {
///     use jni_support::ThrowJniError as __JniErrorPolicy;
///
///     fn __jni_impl_println_native<'local>(
///         env: &mut jni::Env<'local>,
///         __this: jni::objects::JClass<'local>,
///         __arg0: jni::objects::JString<'local>,
///         __arg1: jni::sys::jint,
///     ) -> Result<jni::sys::jint, jni_support::JniError> {
///         // ... jstring extraction, null check ...
///         let __result = println_native(env, __this.as_raw(), tag, __arg1);
///         Ok(__result)
///     }
///     const __NATIVE_METHOD_println_native: jni::NativeMethod = jni::native_method! {
///         name = "println_native",
///         sig = (JString, jint) -> jint,   // derives "(Ljava/lang/String;I)I"
///         fn = __jni_impl_println_native,
///         static = true,
///         error_policy = __JniErrorPolicy,
///         abi_check = UnsafeNever,
///     };
///     fn println_native(env: &mut jni::Env, clazz: jclass, tag: &str, level: i32) -> i32 {
///         0
///     }
///
///     pub fn register(env: &mut jni::Env<'_>) { // generated
///         let class = env.find_class("android/util/Log").expect(...);
///         let methods = [__NATIVE_METHOD_println_native];
///         env.register_native_methods(&class, &methods).expect(...);
///     }
/// }
/// ```
///
/// Returns a compile error token stream if the attribute is not a string literal
/// or the input is not a module with a body.
pub fn expand_jni_module(attr: TokenStream, item: TokenStream) -> TokenStream {
    let java_class = match JavaClass::parse(attr, "jni_module") {
        Ok(c) => c,
        Err(e) => return e.to_compile_error(),
    };

    let input = match parse2::<ItemMod>(item) {
        Ok(m) => m,
        Err(e) => return e.to_compile_error(),
    };

    let module_package = java_class.package();
    let mod_name = &input.ident;
    let vis = &input.vis;

    let (_, items) = match &input.content {
        Some(content) => content,
        None => {
            return syn::Error::new_spanned(&input, "jni_module requires a module with a body")
                .to_compile_error();
        }
    };

    let mut output_items = Vec::new();
    let mut jni_methods = Vec::new();

    for item in items {
        match item {
            Item::Fn(func) => {
                if find_jni_method_attr(&func.attrs).is_some() {
                    match JniMethod::parse(func, module_package) {
                        Ok(method) => {
                            output_items.push(method.generated_items.clone());
                            output_items.push(method.cleaned_fn.clone());
                            jni_methods.push(method);
                        }
                        Err(err) => {
                            output_items.push(err);
                            continue;
                        }
                    }
                } else {
                    output_items.push(quote! { #func });
                }
            }
            other => {
                output_items.push(quote! { #other });
            }
        }
    }

    let register_fn = generate_register_fn(java_class.path(), &jni_methods);

    // `native_method!` names the error policy by bare identifier, so pull
    // `ThrowJniError` into module scope under a mangled alias. Only regular/fast
    // methods route through `native_method!`; skip the import for critical-only
    // modules so it doesn't trip `unused_imports` under `-D warnings`.
    let policy_import = if jni_methods.iter().any(|m| !m.is_critical) {
        quote! {
            use jni_support::ThrowJniError as __JniErrorPolicy;
        }
    } else {
        quote! {}
    };

    quote! {
        #vis mod #mod_name {
            #policy_import

            #(#output_items)*

            #register_fn
        }
    }
}

/// Finds the `#[jni_method(...)]` attribute on a function.
fn find_jni_method_attr(attrs: &[Attribute]) -> Option<Attribute> {
    attrs.iter().find(|a| a.path().is_ident("jni_method")).cloned()
}

/// Finds the `#[returns = "..."]` attribute on a function.
fn find_returns_attr(attrs: &[Attribute]) -> Option<String> {
    for attr in attrs {
        if attr.path().is_ident("returns") {
            if let Meta::NameValue(MetaNameValue {
                value: syn::Expr::Lit(syn::ExprLit { lit: Lit::Str(s), .. }),
                ..
            }) = &attr.meta
            {
                return Some(s.value());
            }
        }
    }
    None
}

/// Parses and validates one `#[jni_method(...)]` attribute.
///
/// Unknown, malformed, duplicate, and contradictory options are errors. In
/// particular, silently treating a misspelled `critical` as a regular native
/// would generate the wrong ABI for a Java `@CriticalNative` declaration.
fn parse_jni_method_options(attr: &Attribute) -> syn::Result<JniMethodOptions> {
    let args = match &attr.meta {
        Meta::Path(_) => Punctuated::new(),
        Meta::List(list) => syn::parse::Parser::parse2(
            Punctuated::<Meta, Token![,]>::parse_terminated,
            list.tokens.clone(),
        )?,
        Meta::NameValue(_) => {
            return Err(syn::Error::new_spanned(
                attr,
                "jni_method must be bare or use parenthesized options",
            ));
        }
    };

    let mut mode = JniMode::Regular;
    let mut saw_mode = false;
    let mut java_name = None;

    for meta in args {
        match meta {
            Meta::Path(path) if path.is_ident("critical") || path.is_ident("fast") => {
                if saw_mode {
                    return Err(syn::Error::new_spanned(
                        path,
                        "jni_method accepts at most one of `fast` or `critical`",
                    ));
                }
                mode = if path.is_ident("critical") { JniMode::Critical } else { JniMode::Fast };
                saw_mode = true;
            }
            Meta::NameValue(MetaNameValue {
                path,
                value: syn::Expr::Lit(syn::ExprLit { lit: Lit::Str(value), .. }),
                ..
            }) if path.is_ident("name") => {
                if java_name.is_some() {
                    return Err(syn::Error::new_spanned(path, "duplicate jni_method `name`"));
                }
                java_name = Some(value.value());
            }
            Meta::NameValue(MetaNameValue { path, .. }) if path.is_ident("name") => {
                return Err(syn::Error::new_spanned(path, "jni_method `name` must be a string"));
            }
            other => {
                return Err(syn::Error::new_spanned(
                    other,
                    "unknown jni_method option; expected `fast`, `critical`, or `name = \"...\"`",
                ));
            }
        }
    }

    Ok(JniMethodOptions { mode, java_name })
}

/// Derives the Java method name from a Rust function name.
///
/// The Rust function name is used verbatim. Many real Android natives have
/// underscores in their Java names (`println_native`), so any renaming here
/// would silently register the wrong name and abort at boot. Use
/// `#[jni_method(name = "...")]` when the Rust name cannot match the Java
/// name exactly.
fn derive_java_name(fn_name: &str) -> String {
    fn_name.to_string()
}

/// Collects the object classes a method's signature references so
/// `native_method!` derives the right JNI descriptor for a generic `JObject`
/// parameter.
///
/// The friendly surface pairs a generic `JObject`/`jobject`/`JObjectArray` with
/// a `#[class = "..."]`, decoupling the Rust wrapper type (`JObject`) from the
/// Java descriptor. But `native_method!` derives the wrapper type *from* the
/// class: a well-known class such as `java.util.Collection` would resolve to a
/// specific wrapper (`JCollection`), not `JObject`, and fail to type-check
/// against the inner impl fn's `JObject` parameter.
///
/// To keep the wrapper `JObject` while still emitting the right descriptor, each
/// non-core class is bound — via a `native_method!` `type_map` entry — to a
/// fresh, `JObject`-typed alias defined by [`Self::type_alias_items`]. The four
/// core classes (which cannot be remapped) are emitted as their dotted literal;
/// only `java.lang.Object` is meaningful with a generic `JObject` parameter, and
/// it already resolves to `JObject`.
struct ObjectClassMappings {
    /// The method name, used to make alias identifiers unique across the module.
    fn_prefix: String,
    /// Allocated `(alias ident, dotted java class)` pairs, deduplicated by class.
    entries: Vec<(proc_macro2::Ident, String)>,
}

/// The four Java classes `jni_sig!` treats as core; user code cannot remap them.
const CORE_JAVA_CLASSES: [&str; 4] =
    ["java.lang.Object", "java.lang.Class", "java.lang.String", "java.lang.Throwable"];

impl ObjectClassMappings {
    fn new(fn_prefix: &str) -> Self {
        ObjectClassMappings { fn_prefix: fn_prefix.to_string(), entries: Vec::new() }
    }

    /// The `jni_sig!` token for an object type of the `#[class]`/`#[returns]`
    /// class `class`, resolved against `module_package`.
    fn object_token(&mut self, class: &str, module_package: Option<&str>) -> TokenStream {
        let slash = sig::resolve_class(class, module_package);
        let dotted = slash.replace('/', ".");

        if CORE_JAVA_CLASSES.contains(&dotted.as_str()) {
            // `java.lang.Object` -> JObject (what a generic JObject param wants);
            // the other three cannot be remapped, so they stay literal.
            let lit = syn::LitStr::new(&dotted, proc_macro2::Span::call_site());
            return quote! { #lit };
        }

        // A default-package class has no dot; `jni_sig!` spells it with a leading
        // one (e.g. `.Foo`). Store the dot-safe literal for the `type_map`.
        let class_literal = if dotted.contains('.') { dotted } else { format!(".{}", dotted) };

        if let Some((alias, _)) = self.entries.iter().find(|(_, c)| *c == class_literal) {
            return quote! { #alias };
        }
        let alias = format_ident!("__JniClass_{}_{}", self.fn_prefix, self.entries.len());
        self.entries.push((alias.clone(), class_literal));
        quote! { #alias }
    }

    /// The `type_map = { ... }` block binding each allocated alias to its class,
    /// or empty tokens if no non-core object classes were referenced.
    fn type_map_tokens(&self) -> TokenStream {
        if self.entries.is_empty() {
            return quote! {};
        }
        let pairs = self.entries.iter().map(|(alias, dotted)| {
            let lit = syn::LitStr::new(dotted, proc_macro2::Span::call_site());
            quote! { #alias => #lit }
        });
        quote! { type_map = { #(#pairs),* }, }
    }

    /// The `type <alias><'local> = JObject<'local>;` definitions the `type_map`
    /// aliases refer to, placed at module scope alongside the method's items.
    fn type_alias_items(&self) -> Vec<TokenStream> {
        self.entries
            .iter()
            .map(|(alias, _)| {
                quote! {
                    #[allow(non_camel_case_types)]
                    type #alias<'local> = jni::objects::JObject<'local>;
                }
            })
            .collect()
    }
}

/// The `jni_sig!` type token that `native_method!` uses to derive the JNI
/// descriptor and the wrapper parameter type for one user parameter.
///
/// Every arm agrees with the corresponding [`param_bridge`] classification: the
/// Rust type `native_method!` derives from this token is exactly the type
/// [`bridge_user_param`] declares for the matching inner-impl-fn parameter, so
/// `native_method!` type-checks the two against each other. That cross-check —
/// not a hand-maintained second table — is what keeps the fn pointer and its
/// registered signature in agreement.
///
/// Object and object-array parameters take their class from the `#[class =
/// "..."]` attribute; see [`ObjectClassMappings`] for how the class becomes a
/// token that still yields a `JObject` wrapper.
fn param_sig_token(
    ty: &str,
    class_attr: Option<&str>,
    module_package: Option<&str>,
    objects: &mut ObjectClassMappings,
) -> Result<TokenStream, String> {
    // An owned wrapper parameter keeps the lifetime its type needs
    // (`JString<'local>`); strip it so its descriptor matches the bare wrapper.
    let ty = strip_lifetime_arg(ty);

    // A nullable object parameter (`Option<&W>`/`Option<W>`) carries the SAME
    // JNI descriptor as its inner object, so emit the inner's token. String
    // options keep the `JString` arm below (their inner `&str`/`&JNIStr`
    // bridges to a string, not an object), so only object inners short-circuit
    // here; the `#[class = "..."]` still applies to the inner object type.
    if let Some(inner) = strip_option(ty) {
        if matches!(
            param_bridge(&inner).map(|b| b.kind),
            Some(ParamBridgeKind::Wrapped { .. }) | Some(ParamBridgeKind::Raw { .. })
        ) {
            return param_sig_token(&inner, class_attr, module_package, objects);
        }
    }

    let token = match ty {
        "jint" | "i32" => quote! { jint },
        "jlong" | "i64" => quote! { jlong },
        "jfloat" | "f32" => quote! { jfloat },
        "jdouble" | "f64" => quote! { jdouble },
        "jbyte" | "i8" => quote! { jbyte },
        "jchar" | "u16" => quote! { jchar },
        "jshort" | "i16" => quote! { jshort },
        "jboolean" | "bool" => quote! { jboolean },

        // Every string flavour describes a java.lang.String argument.
        "&str" | "Option<&str>" | "&JNIStr" | "Option<&JNIStr>" | "JString" | "&JString"
        | "jstring" => quote! { JString },

        "JByteArray" | "&JByteArray" | "jbyteArray" => quote! { [jbyte] },
        "JIntArray" | "&JIntArray" | "jintArray" => quote! { [jint] },
        "JFloatArray" | "&JFloatArray" | "jfloatArray" => quote! { [jfloat] },
        "JLongArray" | "&JLongArray" | "jlongArray" => quote! { [jlong] },
        "JShortArray" | "&JShortArray" | "jshortArray" => quote! { [jshort] },
        "JDoubleArray" | "&JDoubleArray" | "jdoubleArray" => quote! { [jdouble] },
        "JBooleanArray" | "&JBooleanArray" | "jbooleanArray" => quote! { [jboolean] },
        "JCharArray" | "&JCharArray" | "jcharArray" => quote! { [jchar] },

        "JObject" | "&JObject" | "jobject" => {
            let class = class_attr.ok_or_else(|| {
                format!(
                    "JObject parameter requires #[class = \"...\"] annotation, got type '{}'",
                    ty
                )
            })?;
            objects.object_token(class, module_package)
        }
        "JObjectArray" | "&JObjectArray" | "jobjectArray" => {
            let class = class_attr.ok_or_else(|| {
                format!(
                    "JObjectArray parameter requires #[class = \"...\"] annotation, got type '{}'",
                    ty
                )
            })?;
            let elem = objects.object_token(class, module_package);
            quote! { [ #elem ] }
        }

        _ => {
            let class = class_attr.ok_or_else(|| format!("Unknown JNI type: '{}'", ty))?;
            objects.object_token(class, module_package)
        }
    };
    Ok(token)
}

/// The `jni_sig!` return-type token for a user return type.
///
/// `Result<T, _>` is unwrapped to `T`; object returns take their class from the
/// `#[returns = "..."]` attribute. Primitives and typed arrays defer to
/// [`param_sig_token`].
fn return_sig_token(
    ty: &str,
    returns_attr: Option<&str>,
    module_package: Option<&str>,
    objects: &mut ObjectClassMappings,
) -> Result<TokenStream, String> {
    if let Some(inner) = sig::extract_result_inner(ty) {
        return return_sig_token(&inner, returns_attr, module_package, objects);
    }

    // An owned wrapper return keeps the lifetime its type needs
    // (`JString<'local>`); strip it so its descriptor matches the bare wrapper.
    let ty = strip_lifetime_arg(ty);
    let token = match ty {
        "()" | "" | "void" => quote! { void },
        // `String` is the friendly return form; the wrapper type
        // `bridge_user_param`/`ReturnBridge` produces is `JString`. `bool` is a
        // primitive and defers to `param_sig_token` via the fallback below.
        "String" | "JString" | "&JString" | "jstring" => quote! { JString },

        "JObject" | "&JObject" | "jobject" => {
            let class = returns_attr.ok_or_else(|| {
                "JObject return type requires #[returns = \"...\"] annotation".to_string()
            })?;
            objects.object_token(class, module_package)
        }
        "JObjectArray" | "&JObjectArray" | "jobjectArray" => {
            let class = returns_attr.ok_or_else(|| {
                "JObjectArray return type requires #[returns = \"...\"] annotation".to_string()
            })?;
            let elem = objects.object_token(class, module_package);
            quote! { [ #elem ] }
        }

        // Primitives and typed primitive arrays need no class attribute.
        _ => param_sig_token(ty, None, module_package, objects)?,
    };
    Ok(token)
}

/// Builds the primitive-only JNI descriptor for an `@CriticalNative` method.
///
/// Critical natives take and return only primitives (already enforced by
/// [`validate_critical_native`] for strings/`Result`); any non-primitive here is
/// rejected. `native_method!` cannot express the critical ABI, so this is the
/// one signature the macro still derives by hand — over the narrow, safe
/// primitive alphabet of [`sig::primitive_sig`].
fn derive_critical_signature(func: &ItemFn) -> Result<String, String> {
    let mut descriptor = String::from("(");
    for arg in &func.sig.inputs {
        if let FnArg::Typed(pat_type) = arg {
            let ty = type_to_string(&pat_type.ty);
            let ch = sig::primitive_sig(&ty).ok_or_else(|| {
                format!("@CriticalNative parameter type '{}' must be a primitive", ty)
            })?;
            descriptor.push_str(ch);
        }
    }
    descriptor.push(')');

    let ret = return_type_str(&func.sig.output);
    let ch = sig::primitive_sig(&ret)
        .ok_or_else(|| format!("@CriticalNative return type '{}' must be a primitive", ret))?;
    descriptor.push_str(ch);

    Ok(descriptor)
}

/// Validates that a `@CriticalNative` method doesn't use types requiring JNIEnv.
///
/// # Examples
///
/// ```text
/// fn test(x: jlong) -> jint { 0 }  → Ok(())
/// fn test(tag: &str) -> jint { 0 } → Err("@CriticalNative method 'test' cannot use '&str'...")
/// fn test(x: jint) -> String { … } → Err("@CriticalNative method 'test' cannot return String...")
/// fn test(x: jint) -> Result<jint, E> { … } → Err("...cannot return Result...")
/// ```
fn validate_critical_native(func: &ItemFn) -> Result<(), String> {
    let fn_name = &func.sig.ident;
    let inputs: Vec<&FnArg> = func.sig.inputs.iter().collect();

    for arg in &inputs {
        if let FnArg::Typed(pat_type) = arg {
            let ty = type_to_string(&pat_type.ty);
            if matches!(ty.as_str(), "&str" | "Option<&str>" | "&JNIStr" | "Option<&JNIStr>") {
                return Err(format!(
                    "@CriticalNative method '{}' cannot use '{}' \
                     (no JNIEnv available for string conversion)",
                    fn_name, ty
                ));
            }
        }
    }

    let ret = return_type_str(&func.sig.output);
    if ret == "String" {
        return Err(format!(
            "@CriticalNative method '{}' cannot return String \
             (no JNIEnv available for string conversion)",
            fn_name
        ));
    }
    if sig::extract_result_inner(&ret).is_some() {
        return Err(format!(
            "@CriticalNative method '{}' cannot return Result \
             (no JNIEnv available to throw the error as a Java exception)",
            fn_name
        ));
    }

    Ok(())
}

/// The generated code for bridging one shim parameter to the user's declared type.
struct BridgedParam {
    /// The parameter declaration in the shim signature.
    shim_param: TokenStream,
    /// Conversion statements executed (inside the `with_env` closure, so `env`
    /// is in scope) before calling the user's function.
    prelude: Option<TokenStream>,
    /// The argument expression passed to the user's function.
    call_arg: TokenStream,
}

/// Generates the shim parameter, conversion statements, and call argument for
/// one user parameter. `index` positions the shim parameter name (`__arg0`,
/// `__arg1`, ...); `lifetime` is the shim's `'local` for object wrapper types.
///
/// Conversions run inside the `with_env` closure and report failure by
/// returning `Err(JniError)` (a null non-nullable reference becomes a
/// NullPointerException; a failed string read propagates via `?`); the shim's
/// `.resolve::<ThrowJniError>()` turns that into the pending exception.
fn bridge_user_param(
    pat_type: &PatType,
    index: usize,
    lifetime: &TokenStream,
    class_attr: Option<&str>,
) -> Result<BridgedParam, String> {
    let pat = &pat_type.pat;
    // An owned wrapper parameter may carry the lifetime its type needs
    // (`JString<'local>`, tying it to the shim's frame so the user can hand it
    // straight back as the return); strip it so it classifies the same as a
    // bare-lifetime `JString` parameter.
    let ty = strip_lifetime_arg(&type_to_string(&pat_type.ty)).to_string();
    let raw_ident = format_ident!("__arg{}", index);
    let npe_msg = format!("{} must not be null", quote! { #pat });

    // A project-defined wrapper produced by `bind_java_type!`. The Java class
    // comes from `#[class = "..."]`; the JVM therefore guarantees the runtime
    // reference has that type, and the generated shim performs the single
    // audited `Reference::from_raw` conversion before calling the user body.
    // A borrowed wrapper is non-null; use `Option<&Wrapper>` when Java null is
    // part of the contract. Owned wrapper values retain the JNI crate's normal
    // nullable-wrapper semantics.
    let known_nullable_wrapper = strip_option(&ty).is_some_and(|inner| {
        matches!(
            param_bridge(&inner).map(|bridge| bridge.kind),
            Some(ParamBridgeKind::Wrapped { .. }) | Some(ParamBridgeKind::Raw { .. })
        )
    });
    if class_attr.is_some() && param_bridge(&ty).is_none() && !known_nullable_wrapper {
        let custom = custom_wrapper_type(&pat_type.ty)
            .ok_or_else(|| format!("unsupported #[class] wrapper parameter type '{}'", ty))?;
        let wrapper = custom.wrapper;
        let converted_ident = format_ident!("__arg{}_typed", index);
        let convert = quote! { unsafe { #wrapper::from_raw(env, #raw_ident.as_raw()) } };
        let (prelude, call_arg) = match (custom.nullable, custom.by_ref) {
            (false, true) => (
                quote! {
                    if #raw_ident.is_null() {
                        return ::core::result::Result::Err(
                            jni_support::JniError::NullPointer(#npe_msg));
                    }
                    // SAFETY: the registered JNI descriptor names the class in
                    // `#[class]`, so a non-null argument is that class or a subclass.
                    let #converted_ident: #wrapper = #convert;
                },
                quote! { &#converted_ident },
            ),
            (false, false) => (
                quote! {
                    // SAFETY: the registered JNI descriptor names the class in
                    // `#[class]`; owned JNI wrappers may also represent null.
                    let #converted_ident: #wrapper = #convert;
                },
                quote! { #converted_ident },
            ),
            (true, true) => (
                quote! {
                    let #converted_ident: Option<#wrapper> = if #raw_ident.is_null() {
                        None
                    } else {
                        // SAFETY: the registered JNI descriptor names the class
                        // in `#[class]`.
                        Some(#convert)
                    };
                },
                quote! { #converted_ident.as_ref() },
            ),
            (true, false) => (
                quote! {
                    let #converted_ident: Option<#wrapper> = if #raw_ident.is_null() {
                        None
                    } else {
                        // SAFETY: the registered JNI descriptor names the class
                        // in `#[class]`.
                        Some(#convert)
                    };
                },
                quote! { #converted_ident },
            ),
        };
        return Ok(BridgedParam {
            shim_param: quote! { #raw_ident: jni::objects::JObject<#lifetime> },
            prelude: Some(prelude),
            call_arg,
        });
    }

    // A nullable object parameter: `Option<&W>` or `Option<W>` where `W` is an
    // object wrapper (`JObject`, a typed array, `JString`, ...). The shim still
    // captures the JVM's reference in the bare wrapper; the prelude maps a null
    // reference to `None` rather than throwing NullPointerException. String
    // options (`Option<&str>`/`Option<&JNIStr>`) fall through to the match
    // below, because `param_bridge` classifies their inner types as
    // owned/borrowed strings, not `Wrapped`/`Raw` objects.
    if let Some(inner) = strip_option(&ty) {
        if let Some(ParamBridge { kind }) = param_bridge(&inner) {
            match kind {
                ParamBridgeKind::Wrapped { wrapper, by_ref } => {
                    let prelude = if by_ref {
                        quote! {
                            let #pat: Option<&#wrapper> =
                                if #raw_ident.is_null() { None } else { Some(&#raw_ident) };
                        }
                    } else {
                        quote! {
                            let #pat: Option<#wrapper> =
                                if #raw_ident.is_null() { None } else { Some(#raw_ident) };
                        }
                    };
                    return Ok(BridgedParam {
                        shim_param: quote! { #raw_ident: #wrapper<#lifetime> },
                        prelude: Some(prelude),
                        call_arg: quote! { #pat },
                    });
                }
                ParamBridgeKind::Raw { wrapper } => {
                    return Ok(BridgedParam {
                        shim_param: quote! { #raw_ident: #wrapper<#lifetime> },
                        prelude: Some(quote! {
                            let #pat =
                                if #raw_ident.is_null() { None } else { Some(#raw_ident.as_raw()) };
                        }),
                        call_arg: quote! { #pat },
                    });
                }
                // A non-object inner (`&str`, `&JNIStr`, a primitive, `bool`)
                // is not a nullable-object parameter; fall through to the match
                // below, which handles `Option<&str>`/`Option<&JNIStr>`.
                _ => {}
            }
        }
    }

    let bridge = param_bridge(&ty).ok_or_else(|| format!("Unknown JNI type: '{}'", ty))?;
    Ok(match bridge.kind {
        ParamBridgeKind::Primitive(sys_ty) => BridgedParam {
            shim_param: quote! { #raw_ident: #sys_ty },
            prelude: None,
            call_arg: quote! { #raw_ident },
        },
        ParamBridgeKind::OwnedString { nullable: false } => {
            let string_ident = format_ident!("__arg{}_string", index);
            BridgedParam {
                shim_param: quote! { #raw_ident: jni::objects::JString<#lifetime> },
                prelude: Some(quote! {
                    if #raw_ident.is_null() {
                        return ::core::result::Result::Err(
                            jni_support::JniError::NullPointer(#npe_msg));
                    }
                    let #string_ident: String = #raw_ident.try_to_string(env)?;
                    let #pat: &str = &#string_ident;
                }),
                call_arg: quote! { #pat },
            }
        }
        ParamBridgeKind::OwnedString { nullable: true } => {
            let string_ident = format_ident!("__arg{}_string", index);
            BridgedParam {
                shim_param: quote! { #raw_ident: jni::objects::JString<#lifetime> },
                prelude: Some(quote! {
                    let #string_ident: Option<String> = if #raw_ident.is_null() {
                        None
                    } else {
                        Some(#raw_ident.try_to_string(env)?)
                    };
                    let #pat: Option<&str> = #string_ident.as_deref();
                }),
                call_arg: quote! { #pat },
            }
        }
        ParamBridgeKind::BorrowedString { nullable: false } => {
            let chars_ident = format_ident!("__arg{}_chars", index);
            BridgedParam {
                shim_param: quote! { #raw_ident: jni::objects::JString<#lifetime> },
                prelude: Some(quote! {
                    if #raw_ident.is_null() {
                        return ::core::result::Result::Err(
                            jni_support::JniError::NullPointer(#npe_msg));
                    }
                    let #chars_ident = #raw_ident.mutf8_chars(env)?;
                    let #pat: &jni::strings::JNIStr = &#chars_ident;
                }),
                call_arg: quote! { #pat },
            }
        }
        ParamBridgeKind::BorrowedString { nullable: true } => {
            let chars_ident = format_ident!("__arg{}_chars", index);
            BridgedParam {
                shim_param: quote! { #raw_ident: jni::objects::JString<#lifetime> },
                prelude: Some(quote! {
                    let #chars_ident = if #raw_ident.is_null() {
                        None
                    } else {
                        Some(#raw_ident.mutf8_chars(env)?)
                    };
                    let #pat: Option<&jni::strings::JNIStr> = #chars_ident.as_deref();
                }),
                call_arg: quote! { #pat },
            }
        }
        ParamBridgeKind::Wrapped { wrapper, by_ref: false } => BridgedParam {
            shim_param: quote! { #raw_ident: #wrapper<#lifetime> },
            prelude: None,
            call_arg: quote! { #raw_ident },
        },
        ParamBridgeKind::Wrapped { wrapper, by_ref: true } => BridgedParam {
            shim_param: quote! { #raw_ident: #wrapper<#lifetime> },
            prelude: Some(quote! {
                if #raw_ident.is_null() {
                    return ::core::result::Result::Err(
                        jni_support::JniError::NullPointer(#npe_msg));
                }
            }),
            call_arg: quote! { &#raw_ident },
        },
        ParamBridgeKind::Raw { wrapper } => BridgedParam {
            shim_param: quote! { #raw_ident: #wrapper<#lifetime> },
            prelude: None,
            call_arg: quote! { #raw_ident.as_raw() },
        },
    })
}

struct CustomWrapperType<'a> {
    wrapper: &'a Type,
    by_ref: bool,
    nullable: bool,
}

/// Extracts a project-defined wrapper from `Wrapper`, `&Wrapper`,
/// `Option<Wrapper>`, or `Option<&Wrapper>` syntax.
fn custom_wrapper_type(ty: &Type) -> Option<CustomWrapperType<'_>> {
    match ty {
        Type::Reference(reference) => {
            Some(CustomWrapperType { wrapper: &reference.elem, by_ref: true, nullable: false })
        }
        Type::Path(path) if path.qself.is_none() => {
            let segment = path.path.segments.last()?;
            if segment.ident == "Option" {
                let syn::PathArguments::AngleBracketed(args) = &segment.arguments else {
                    return None;
                };
                let inner = args.args.iter().find_map(|arg| match arg {
                    syn::GenericArgument::Type(ty) => Some(ty),
                    _ => None,
                })?;
                let mut custom = custom_wrapper_type(inner)?;
                custom.nullable = true;
                Some(custom)
            } else {
                Some(CustomWrapperType { wrapper: ty, by_ref: false, nullable: false })
            }
        }
        Type::Group(group) => custom_wrapper_type(&group.elem),
        Type::Paren(paren) => custom_wrapper_type(&paren.elem),
        _ => None,
    }
}

/// The shim's `this`/`class` parameter type and the argument passed to the user
/// function for it.
///
/// The shim captures the JVM's reference directly into a `JClass`/`JObject`
/// wrapper — both `#[repr(transparent)]` over the raw handle, so the ABI is
/// unchanged and no `from_raw` is needed. A user parameter typed as the raw
/// `jclass`/`jobject` gets `.as_raw()`.
fn this_binding(arg: &FnArg, lifetime: &TokenStream) -> Result<(TokenStream, TokenStream), String> {
    let FnArg::Typed(pat_type) = arg else {
        return Err("JNI methods cannot take self".to_string());
    };
    let ty = type_to_string(&pat_type.ty);
    Ok(match ty.as_str() {
        "jclass" => (quote! { jni::objects::JClass<#lifetime> }, quote! { __this.as_raw() }),
        "JClass" => (quote! { jni::objects::JClass<#lifetime> }, quote! { __this }),
        "&JClass" => (quote! { jni::objects::JClass<#lifetime> }, quote! { &__this }),
        "jobject" => (quote! { jni::objects::JObject<#lifetime> }, quote! { __this.as_raw() }),
        "JObject" => (quote! { jni::objects::JObject<#lifetime> }, quote! { __this }),
        "&JObject" => (quote! { jni::objects::JObject<#lifetime> }, quote! { &__this }),
        other => {
            return Err(format!(
                "second parameter must be a jobject/jclass type, found '{}'",
                other
            ))
        }
    })
}

/// Generates the module items for a regular / `@FastNative` method: a private
/// inner impl fn plus the `const NativeMethod` `native_method!` builds from it.
///
/// The inner fn receives the raw values the JVM passes — `jni::sys` primitives
/// and `jni::objects` wrappers, the same types [`bridge_user_param`] declares —
/// does the Rust-friendly bridging, calls the user's function, and returns
/// `Result<raw_ret, JniError>`. `native_method!` supplies the `extern "system"`
/// wrapper (`EnvUnowned::with_env(..).resolve::<ThrowJniError>()`) and derives
/// the JNI signature from the `sig = (...)` tokens, type-checking the inner fn
/// against it.
///
/// # Example
///
/// Given `fn isTouchEvent(env: &mut Env, clazz: jclass, ptr: jlong) -> bool { ... }`
/// with `method_const = __NATIVE_METHOD_isTouchEvent`, generates:
/// ```text
/// fn __jni_impl_isTouchEvent<'local>(
///     env: &mut jni::Env<'local>,
///     __this: jni::objects::JClass<'local>,
///     __arg0: jni::sys::jlong,
/// ) -> Result<jni::sys::jboolean, jni_support::JniError> {
///     let __result = isTouchEvent(env, __this.as_raw(), __arg0);
///     Ok(__result)
/// }
/// const __NATIVE_METHOD_isTouchEvent: jni::NativeMethod = jni::native_method! {
///     name = "isTouchEvent",
///     sig = (jlong) -> jboolean,   // derives "(J)Z"
///     fn = __jni_impl_isTouchEvent,
///     static = true,
///     error_policy = __JniErrorPolicy,
///     abi_check = UnsafeNever,
/// };
/// ```
/// The `#[doc]` attribute placed on a generated shim, recording the
/// preconditions the JVM upholds when it invokes it. For a native backed by an
/// `unsafe fn` it also notes that the user function's own safety contract still
/// applies — the honest counterpart to the retained
/// `undocumented_unsafe_blocks` allow, which a proc-macro cannot replace with a
/// `// SAFETY:` comment.
fn shim_doc_attr(
    java_name: &str,
    user_fn_name: &proc_macro2::Ident,
    is_unsafe: bool,
    is_critical: bool,
) -> TokenStream {
    let base = if is_critical {
        format!(
            "JNI @CriticalNative shim for `{java_name}`. The JVM invokes it with no environment \
             or receiver argument, over primitives only."
        )
    } else {
        format!(
            "JNI shim for `{java_name}`. The JVM guarantees `env` is a valid `JNIEnv` for the \
             calling thread and (for a non-static native) `__this` is a live instance of the \
             registered class."
        )
    };
    let doc = if is_unsafe {
        format!(
            "{base} Callers must additionally uphold the safety preconditions of `{user_fn_name}`."
        )
    } else {
        base
    };
    quote! { #[doc = #doc] }
}

fn generate_native_method(
    func: &ItemFn,
    java_name: &str,
    module_package: Option<&str>,
    returns_attr: Option<&str>,
    method_const: &proc_macro2::Ident,
) -> Result<TokenStream, String> {
    let user_fn_name = &func.sig.ident;
    let impl_ident = format_ident!("__jni_impl_{}", user_fn_name);
    let inputs: Vec<&FnArg> = func.sig.inputs.iter().collect();
    let lifetime = quote! { 'local };

    // Object classes referenced by the signature; see `ObjectClassMappings`.
    let mut objects = ObjectClassMappings::new(&user_fn_name.to_string());

    let return_type = return_type_str(&func.sig.output);
    let (ret, is_result) = ReturnBridge::parse(&return_type)?;
    let ok_ty = ret.closure_ok_ty(&lifetime);
    let ret_token = return_sig_token(&return_type, returns_attr, module_package, &mut objects)?;

    // Receiver (2nd parameter): a `jclass`-family type registers as a static
    // native (JClass), a `jobject`-family type as an instance native (JObject).
    // `native_method!`'s `static` flag must match the type `bridge`s.
    let receiver_ty = match inputs[1] {
        FnArg::Typed(pat_type) => type_to_string(&pat_type.ty),
        FnArg::Receiver(_) => return Err("JNI methods cannot take self".to_string()),
    };
    let is_static = matches!(receiver_ty.as_str(), "jclass" | "JClass" | "&JClass");
    let (this_shim_ty, this_arg) = this_binding(inputs[1], &lifetime)?;

    // Bridge each user parameter after env/this into an inner-fn parameter, an
    // optional conversion, a call argument, and the matching signature token.
    let mut impl_params: Vec<TokenStream> = Vec::new();
    let mut sig_tokens: Vec<TokenStream> = Vec::new();
    let mut preludes: Vec<TokenStream> = Vec::new();
    let mut call_args: Vec<TokenStream> = Vec::new();
    for (index, arg) in inputs[2..].iter().enumerate() {
        if let FnArg::Typed(pat_type) = arg {
            let class = find_class_attr_on_pat(pat_type);
            let bridged = bridge_user_param(pat_type, index, &lifetime, class.as_deref())?;
            impl_params.push(bridged.shim_param);
            if let Some(prelude) = bridged.prelude {
                preludes.push(prelude);
            }
            call_args.push(bridged.call_arg);

            let ty = type_to_string(&pat_type.ty);
            sig_tokens.push(param_sig_token(&ty, class.as_deref(), module_package, &mut objects)?);
        }
    }

    let call = quote! { #user_fn_name(env, #this_arg, #(#call_args),*) };
    // An `unsafe fn` native's call must live in an `unsafe` block; the user
    // opted into the obligation by marking the fn `unsafe`.
    let call = if func.sig.unsafety.is_some() {
        quote! { unsafe { #call } }
    } else {
        call
    };
    let body = if ret.is_void() {
        if is_result {
            quote! { #(#preludes)* #call?; ::core::result::Result::Ok(()) }
        } else {
            quote! { #(#preludes)* #call; ::core::result::Result::Ok(()) }
        }
    } else {
        let result_ident = format_ident!("__result");
        let convert = ret.convert_ok(&result_ident);
        if is_result {
            quote! { #(#preludes)* let #result_ident = #call?; #convert }
        } else {
            quote! { #(#preludes)* let #result_ident = #call; #convert }
        }
    };

    let lint_allows = quote! {
        #[allow(non_snake_case, clippy::too_many_arguments, clippy::undocumented_unsafe_blocks)]
    };
    let shim_doc = shim_doc_attr(java_name, user_fn_name, func.sig.unsafety.is_some(), false);
    let static_lit = if is_static {
        quote! { true }
    } else {
        quote! { false }
    };

    // `JObject`-typed aliases + the `type_map` binding them to their Java class,
    // so `native_method!` derives each object descriptor while keeping the
    // wrapper type `JObject`.
    let type_aliases = objects.type_alias_items();
    let type_map = objects.type_map_tokens();

    // `#type_map` must precede `sig` in the `native_method!` invocation below:
    // an object-array element of a non-core `#[class]` type is resolved through
    // its `type_map` alias while `sig` is parsed, so the mapping has to be in
    // scope first (nativeInitialize/nativeAddBatch rely on this).
    //
    // `abi_check = UnsafeNever` is REQUIRED here, not a legacy default — do not
    // "upgrade" it to `Always`. Any checked setting makes `native_method!` emit
    // a runtime assertion that each `#[class]` object's `Reference::class_name()`
    // equals its declared Java class; but the `type_map` binds every such object
    // to a `JObject` alias whose `class_name()` is `java/lang/Object`, so the
    // assertion would fail and abort on the first call to any method taking a
    // `#[class]` object (readEvents, nativeInitialize, ...). The only check
    // thereby forgone is static-vs-instance, which is instead derived
    // structurally (`jclass` receiver => static, `jobject` => instance `this`).
    Ok(quote! {
        #(#type_aliases)*

        #shim_doc
        #lint_allows
        fn #impl_ident<#lifetime>(
            env: &mut jni::Env<#lifetime>,
            __this: #this_shim_ty,
            #(#impl_params),*
        ) -> ::core::result::Result<#ok_ty, jni_support::JniError> {
            #body
        }

        #[allow(non_upper_case_globals, clippy::undocumented_unsafe_blocks)]
        const #method_const: jni::NativeMethod<'static> = jni::native_method! {
            #type_map
            name = #java_name,
            sig = ( #(#sig_tokens),* ) -> #ret_token,
            fn = #impl_ident,
            static = #static_lit,
            error_policy = __JniErrorPolicy,
            abi_check = UnsafeNever,
        };
    })
}

/// Generates the module items for an `@CriticalNative` method: the hand-rolled
/// `extern "system"` shim and a `from_raw_parts` `const NativeMethod`.
///
/// ART invokes `@CriticalNative` methods without the `JNIEnv`/`jclass` prefix,
/// over primitives only — an ABI `native_method!` cannot express — so the shim
/// is a bare `extern "system"` function with no `Env`. Host JVMs (Ravenwood's
/// OpenJDK, layoutlib's Studio JVM) ignore the annotation and always pass the
/// prefix, so a cfg'd host variant accepts and discards the two leading
/// arguments — the Rust equivalent of core_jni_helpers.h's
/// `CRITICAL_JNI_PARAMS_COMMA`.
///
/// Returns `Err` if the function uses types that require a `JNIEnv` (`&str`,
/// `String`, `Result`) or a non-primitive parameter/return.
fn generate_critical_method(
    func: &ItemFn,
    java_name: &str,
    method_const: &proc_macro2::Ident,
) -> Result<TokenStream, String> {
    validate_critical_native(func)?;

    let user_fn_name = &func.sig.ident;
    let shim_name = format_ident!("__jni_{}", user_fn_name);
    let lifetime = quote! { 'local };

    let (ret, _is_result) = ReturnBridge::parse(&return_type_str(&func.sig.output))?;
    let output = ret.output_tokens(&lifetime);
    let jni_sig = derive_critical_signature(func)?;

    let mut shim_params: Vec<TokenStream> = Vec::new();
    let mut call_args: Vec<TokenStream> = Vec::new();
    for (index, arg) in func.sig.inputs.iter().enumerate() {
        if let FnArg::Typed(pat_type) = arg {
            let bridged = bridge_user_param(pat_type, index, &lifetime, None)?;
            shim_params.push(bridged.shim_param);
            call_args.push(bridged.call_arg);
        }
    }

    let call = quote! { #user_fn_name(#(#call_args),*) };
    // An `unsafe fn` native's call must live in an `unsafe` block; the user
    // opted into the obligation by marking the fn `unsafe`.
    let call = if func.sig.unsafety.is_some() {
        quote! { unsafe { #call } }
    } else {
        call
    };
    let body = match &ret {
        ReturnBridge::Void => quote! { #call; },
        _ => quote! { #call },
    };

    let lint_allows = quote! {
        #[allow(non_snake_case, clippy::too_many_arguments, clippy::undocumented_unsafe_blocks)]
    };
    let shim_doc = shim_doc_attr(java_name, user_fn_name, func.sig.unsafety.is_some(), true);

    Ok(quote! {
        #[cfg(target_os = "android")]
        #shim_doc
        #lint_allows
        extern "system" fn #shim_name(#(#shim_params),*) #output {
            #body
        }
        #[cfg(not(target_os = "android"))]
        #shim_doc
        #lint_allows
        extern "system" fn #shim_name(
            _critical_env: *mut jni::sys::JNIEnv,
            _critical_class: jni::sys::jclass,
            #(#shim_params),*
        ) #output {
            #body
        }

        #[allow(non_upper_case_globals, clippy::undocumented_unsafe_blocks)]
        const #method_const: jni::NativeMethod<'static> = unsafe {
            // SAFETY: `#shim_name` is a bare `extern "system"` fn whose
            // primitive-only parameters and return type match this
            // hand-derived descriptor; @CriticalNative registers with the same
            // (name, sig, fn-ptr) triple as any other native.
            jni::NativeMethod::from_raw_parts(
                jni::jni_str!(#java_name),
                jni::jni_str!(#jni_sig),
                #shim_name as *mut core::ffi::c_void,
            )
        };
    })
}

/// Checks if a structurally normalized type represents a JNI environment parameter.
fn is_env_type(ty: &str) -> bool {
    matches!(ty, "&mut Env" | "&mut JNIEnv" | "Env" | "JNIEnv")
}

/// Checks if a type string represents a `this` or `class` parameter.
fn is_this_type(ty: &str) -> bool {
    ty == "JClass"
        || ty == "jclass"
        || ty == "JObject"
        || ty == "jobject"
        || ty == "&JClass"
        || ty == "&JObject"
}

/// Finds a `#[class = "..."]` attribute on a function parameter.
fn find_class_attr_on_pat(pat_type: &PatType) -> Option<String> {
    for attr in &pat_type.attrs {
        if attr.path().is_ident("class") {
            if let Meta::NameValue(MetaNameValue {
                value: syn::Expr::Lit(syn::ExprLit { lit: Lit::Str(s), .. }),
                ..
            }) = &attr.meta
            {
                return Some(s.value());
            }
        }
    }
    None
}

/// Converts a `syn::Type` to the small JNI type language this macro accepts.
///
/// This intentionally walks the syntax tree rather than matching a rendered
/// token string. Qualified paths are reduced to their final type name,
/// lifetimes are ignored, and generic type arguments are normalized
/// recursively, so `jni::objects::JString<'local>` and `JString` classify the
/// same way. Rust aliases cannot be resolved by a procedural macro and remain
/// unsupported.
fn type_to_string(ty: &Type) -> String {
    match ty {
        Type::Reference(reference) => {
            let mutability = if reference.mutability.is_some() { "mut " } else { "" };
            format!("&{}{}", mutability, type_to_string(&reference.elem))
        }
        Type::Path(path) if path.qself.is_none() => {
            let Some(segment) = path.path.segments.last() else {
                return "<empty path>".to_string();
            };
            let ident = segment.ident.to_string();
            match &segment.arguments {
                syn::PathArguments::None => ident,
                syn::PathArguments::AngleBracketed(arguments) => {
                    let types: Vec<String> = arguments
                        .args
                        .iter()
                        .filter_map(|argument| match argument {
                            syn::GenericArgument::Type(ty) => Some(type_to_string(ty)),
                            // Wrapper lifetimes are tied to the generated JNI
                            // frame and do not affect the JNI descriptor.
                            syn::GenericArgument::Lifetime(_) => None,
                            _ => Some("<unsupported generic argument>".to_string()),
                        })
                        .collect();
                    if types.is_empty() {
                        ident
                    } else {
                        format!("{}<{}>", ident, types.join(", "))
                    }
                }
                syn::PathArguments::Parenthesized(_) => {
                    format!("{}<unsupported parenthesized arguments>", ident)
                }
            }
        }
        Type::Tuple(tuple) if tuple.elems.is_empty() => "()".to_string(),
        Type::Group(group) => type_to_string(&group.elem),
        Type::Paren(paren) => type_to_string(&paren.elem),
        _ => "<unsupported type>".to_string(),
    }
}

/// Strips JNI-specific attributes from a function, leaving the pure Rust function.
///
/// Removes `#[jni_method]`, `#[returns = "..."]`, and `#[class = "..."]` attributes
/// from the function and its parameters.
///
/// # Example
///
/// ```text
/// // Input:
/// #[jni_method(fast)]
/// #[returns = "android.view.KeyEvent"]
/// fn obtain(env: &mut JNIEnv, clazz: jclass, #[class = "KeyEvent"] event: JObject) -> JObject { ... }
///
/// // Output:
/// fn obtain(env: &mut JNIEnv, clazz: jclass, event: JObject) -> JObject { ... }
/// ```
fn strip_jni_attrs(func: &ItemFn) -> TokenStream {
    let mut clean_func = func.clone();

    // Remove jni_method, returns, and class attributes from the function
    clean_func.attrs.retain(|a| {
        !a.path().is_ident("jni_method")
            && !a.path().is_ident("returns")
            && !a.path().is_ident("class")
    });

    // Remove class attributes from parameters
    for arg in &mut clean_func.sig.inputs {
        if let FnArg::Typed(pat_type) = arg {
            pat_type.attrs.retain(|a| !a.path().is_ident("class"));
        }
    }

    quote! { #clean_func }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn lifetime() -> TokenStream {
        quote! { 'local }
    }

    fn parse_fn(tokens: TokenStream) -> ItemFn {
        syn::parse2(tokens).unwrap()
    }

    // The generated code is compiled and its DERIVED JNI descriptors are checked
    // end-to-end by `jni_support/tests/generated_shim_abi.rs`. `native_method!`
    // derives the descriptor from the signature tokens at its own expansion
    // time, so it is not present in the tokens these unit tests inspect; they
    // pin the inner impl fn, the `native_method!` invocation, and the critical
    // shim instead.

    /// Expands a regular/fast method to a string of the emitted tokens.
    fn gen_regular(func: TokenStream) -> String {
        gen_regular_full(func, None, None)
    }

    fn gen_regular_full(func: TokenStream, pkg: Option<&str>, returns: Option<&str>) -> String {
        let func = parse_fn(func);
        let name = func.sig.ident.to_string();
        let method_const = format_ident!("__NATIVE_METHOD_{}", func.sig.ident);
        generate_native_method(&func, &name, pkg, returns, &method_const).unwrap().to_string()
    }

    fn gen_critical(func: TokenStream) -> String {
        let func = parse_fn(func);
        let name = func.sig.ident.to_string();
        let method_const = format_ident!("__NATIVE_METHOD_{}", func.sig.ident);
        generate_critical_method(&func, &name, &method_const).unwrap().to_string()
    }

    fn gen_critical_err(func: TokenStream) -> String {
        let func = parse_fn(func);
        let method_const = format_ident!("__NATIVE_METHOD_t");
        generate_critical_method(&func, "t", &method_const).unwrap_err()
    }

    #[test]
    fn derive_java_name_is_verbatim() {
        assert_eq!(derive_java_name("println_native"), "println_native");
        assert_eq!(derive_java_name("elapsedRealtime"), "elapsedRealtime");
    }

    #[test]
    fn parse_jni_method_options_recognizes_each_mode() {
        let options = |tokens| {
            let attr = syn::parse2::<ItemFn>(tokens).unwrap().attrs[0].clone();
            parse_jni_method_options(&attr).unwrap()
        };
        assert_eq!(options(quote! { #[jni_method(critical)] fn t() {} }).mode, JniMode::Critical);
        assert_eq!(options(quote! { #[jni_method(fast)] fn t() {} }).mode, JniMode::Fast);
        assert_eq!(options(quote! { #[jni_method] fn t() {} }).mode, JniMode::Regular);
        assert_eq!(
            options(quote! { #[jni_method(name = "native_name")] fn t() {} }).java_name.as_deref(),
            Some("native_name")
        );
    }

    #[test]
    fn parse_jni_method_options_rejects_invalid_options() {
        let error = |tokens| {
            let attr = syn::parse2::<ItemFn>(tokens).unwrap().attrs[0].clone();
            parse_jni_method_options(&attr).unwrap_err().to_string()
        };
        assert!(error(quote! { #[jni_method(critcal)] fn t() {} }).contains("unknown"));
        assert!(error(quote! { #[jni_method(fast, critical)] fn t() {} }).contains("at most one"));
        assert!(error(quote! { #[jni_method(name = 42)] fn t() {} }).contains("must be a string"));
        assert!(
            error(quote! { #[jni_method(name = "a", name = "b")] fn t() {} }).contains("duplicate")
        );
    }

    #[test]
    fn type_normalization_is_structural() {
        let ty: Type = syn::parse2(quote! { &mut jni::Env<'local> }).unwrap();
        assert_eq!(type_to_string(&ty), "&mut Env");
        assert!(is_env_type(&type_to_string(&ty)));

        let ty: Type = syn::parse2(quote! { Option<&jni::objects::JString<'local>> }).unwrap();
        assert_eq!(type_to_string(&ty), "Option<&JString>");

        let ty: Type = syn::parse2(quote! { &mut MyEnvironment }).unwrap();
        assert_eq!(type_to_string(&ty), "&mut MyEnvironment");
        assert!(!is_env_type(&type_to_string(&ty)));
    }

    #[test]
    fn param_bridge_classifies_each_kind() {
        assert!(matches!(param_bridge("jint").unwrap().kind, ParamBridgeKind::Primitive(_)));
        assert!(matches!(param_bridge("i64").unwrap().kind, ParamBridgeKind::Primitive(_)));
        assert!(matches!(param_bridge("bool").unwrap().kind, ParamBridgeKind::Primitive(_)));
        assert!(matches!(
            param_bridge("&str").unwrap().kind,
            ParamBridgeKind::OwnedString { nullable: false }
        ));
        assert!(matches!(
            param_bridge("Option<&str>").unwrap().kind,
            ParamBridgeKind::OwnedString { nullable: true }
        ));
        assert!(matches!(
            param_bridge("&JNIStr").unwrap().kind,
            ParamBridgeKind::BorrowedString { nullable: false }
        ));
        assert!(matches!(
            param_bridge("Option<&JNIStr>").unwrap().kind,
            ParamBridgeKind::BorrowedString { nullable: true }
        ));
        assert!(matches!(
            param_bridge("JString").unwrap().kind,
            ParamBridgeKind::Wrapped { by_ref: false, .. }
        ));
        assert!(matches!(
            param_bridge("&JByteArray").unwrap().kind,
            ParamBridgeKind::Wrapped { by_ref: true, .. }
        ));
        assert!(matches!(param_bridge("jstring").unwrap().kind, ParamBridgeKind::Raw { .. }));
    }

    #[test]
    fn borrowed_wrappers_are_checked_for_null() {
        let generated = gen_regular(quote! {
            fn f(
                env: &mut jni::Env<'_>,
                clazz: jclass,
                values: &JIntArray,
            ) {}
        });
        assert!(generated.contains("values must not be null"), "{generated}");
        assert!(generated.contains("is_null"), "{generated}");
    }

    #[test]
    fn class_attribute_bridges_custom_wrapper() {
        let generated = gen_regular(quote! {
            fn f(
                env: &mut jni::Env<'_>,
                clazz: jclass,
                #[class = "android/view/MotionEvent$PointerCoords"] coords: &JPointerCoords,
            ) {}
        });
        assert!(generated.contains("JPointerCoords :: from_raw"), "{generated}");
        assert!(generated.contains("let __arg0_typed : JPointerCoords"), "{generated}");
        assert!(generated.contains("coords must not be null"), "{generated}");
    }

    #[test]
    fn param_bridge_rejects_unsupported_types() {
        assert!(param_bridge("FooBar").is_none());
        assert!(param_bridge("String").is_none());
    }

    #[test]
    fn param_bridge_no_longer_maps_u8() {
        // jni-sys 0.4's `jboolean` is `bool`; a bare `u8` denotes no JNI type.
        assert!(param_bridge("u8").is_none());
    }

    #[test]
    fn return_bridge_classifies_and_unwraps_result() {
        let lt = lifetime();

        let (ret, is_result) = ReturnBridge::parse("()").unwrap();
        assert!(ret.is_void() && !is_result);
        assert!(ret.output_tokens(&lt).is_empty());

        let (ret, is_result) = ReturnBridge::parse("jint").unwrap();
        assert!(matches!(ret, ReturnBridge::Primitive { .. }) && !is_result);
        assert_eq!(ret.output_tokens(&lt).to_string(), quote! { -> jni::sys::jint }.to_string());

        assert!(matches!(ReturnBridge::parse("bool").unwrap().0, ReturnBridge::Primitive { .. }));
        assert!(matches!(ReturnBridge::parse("String").unwrap().0, ReturnBridge::StringToJString));
        assert!(matches!(
            ReturnBridge::parse("JObject").unwrap().0,
            ReturnBridge::OwnedWrapper { .. }
        ));
        assert!(matches!(
            ReturnBridge::parse("jstring").unwrap().0,
            ReturnBridge::RawObject { .. }
        ));

        let (ret, is_result) = ReturnBridge::parse("Result<jint, JniError>").unwrap();
        assert!(is_result && matches!(ret, ReturnBridge::Primitive { .. }));

        let (ret, is_result) = ReturnBridge::parse("Result<(), JniError>").unwrap();
        assert!(is_result && ret.is_void());
    }

    // ---- param_sig_token / return_sig_token / ObjectClassMappings ----
    //
    // Each token is what `native_method!` uses to derive the JNI descriptor; the
    // Rust type it derives from the token must equal the type `bridge_user_param`
    // declares, which is what makes the fn pointer and its signature agree.

    fn ptoken(ty: &str, class: Option<&str>, pkg: Option<&str>) -> String {
        let mut objects = ObjectClassMappings::new("t");
        param_sig_token(ty, class, pkg, &mut objects).unwrap().to_string().replace(' ', "")
    }

    #[test]
    fn param_sig_token_primitives() {
        assert_eq!(ptoken("jint", None, None), "jint");
        assert_eq!(ptoken("i32", None, None), "jint");
        assert_eq!(ptoken("jlong", None, None), "jlong");
        assert_eq!(ptoken("i64", None, None), "jlong");
        assert_eq!(ptoken("jfloat", None, None), "jfloat");
        assert_eq!(ptoken("jdouble", None, None), "jdouble");
        assert_eq!(ptoken("jbyte", None, None), "jbyte");
        assert_eq!(ptoken("jchar", None, None), "jchar");
        assert_eq!(ptoken("jshort", None, None), "jshort");
    }

    #[test]
    fn param_sig_token_bool_is_jboolean() {
        assert_eq!(ptoken("bool", None, None), "jboolean");
        assert_eq!(ptoken("jboolean", None, None), "jboolean");
    }

    #[test]
    fn param_sig_token_strings_are_jstring() {
        for ty in
            ["&str", "Option<&str>", "&JNIStr", "Option<&JNIStr>", "JString", "&JString", "jstring"]
        {
            assert_eq!(ptoken(ty, None, None), "JString", "{ty}");
        }
    }

    #[test]
    fn param_sig_token_typed_primitive_arrays() {
        assert_eq!(ptoken("JByteArray", None, None), "[jbyte]");
        assert_eq!(ptoken("&JByteArray", None, None), "[jbyte]");
        assert_eq!(ptoken("jbyteArray", None, None), "[jbyte]");
        assert_eq!(ptoken("JIntArray", None, None), "[jint]");
        assert_eq!(ptoken("JFloatArray", None, None), "[jfloat]");
        assert_eq!(ptoken("JLongArray", None, None), "[jlong]");
        assert_eq!(ptoken("JShortArray", None, None), "[jshort]");
        assert_eq!(ptoken("JDoubleArray", None, None), "[jdouble]");
        assert_eq!(ptoken("JBooleanArray", None, None), "[jboolean]");
        assert_eq!(ptoken("JCharArray", None, None), "[jchar]");
    }

    #[test]
    fn strip_option_unwraps_only_option() {
        assert_eq!(strip_option("Option<&JIntArray>").as_deref(), Some("&JIntArray"));
        assert_eq!(strip_option("Option<JObject>").as_deref(), Some("JObject"));
        assert_eq!(strip_option("Option<&str>").as_deref(), Some("&str"));
        assert_eq!(strip_option("&JIntArray"), None);
        assert_eq!(strip_option("jint"), None);
    }

    #[test]
    fn option_object_param_shares_inner_descriptor() {
        // A nullable object parameter derives the SAME JNI descriptor as the
        // non-optional wrapper: `Option<&JIntArray>` and `&JIntArray` are both
        // `[jint]` (i.e. `[I`). The `#[class]` still applies to the inner
        // object for a generic `Option<&JObject>`.
        assert_eq!(ptoken("Option<&JIntArray>", None, None), "[jint]");
        assert_eq!(ptoken("Option<&JIntArray>", None, None), ptoken("&JIntArray", None, None));
        assert_eq!(ptoken("Option<&JFloatArray>", None, None), "[jfloat]");
        // A string option is NOT an object option; it keeps the JString token.
        assert_eq!(ptoken("Option<&str>", None, None), "JString");
        assert_eq!(ptoken("Option<&JNIStr>", None, None), "JString");
        // `Option<&JObject>` takes its class from `#[class = "..."]`, like
        // `&JObject`.
        let mut objects = ObjectClassMappings::new("f");
        let tok =
            param_sig_token("Option<&JObject>", Some("java/util/Collection"), None, &mut objects)
                .unwrap();
        assert!(tok.to_string().contains("__JniClass_f_0"), "{tok}");
    }

    #[test]
    fn option_object_param_binds_null_to_none() {
        // The shim keeps the bare wrapper parameter (same as `&JIntArray`) and
        // maps a null reference to `None` without a NullPointerException.
        let s = gen_regular(quote! {
            fn f(env: &mut jni::Env<'_>, clazz: jclass, tags: Option<&JIntArray>) {}
        });
        assert!(s.contains("__arg0 : jni :: objects :: JIntArray"), "{s}");
        assert!(s.contains("is_null"), "null -> None branch missing: {s}");
        assert!(s.contains("None") && s.contains("Some"), "{s}");
        assert!(!s.contains("must not be null"), "nullable must not null-check: {s}");
        // Same descriptor token as the non-optional form.
        assert!(s.replace(' ', "").contains("sig=([jint])->void"), "{s}");
    }

    #[test]
    fn param_sig_token_object_records_a_class_mapping() {
        // A non-core class becomes a fresh JObject-typed alias plus a type_map
        // entry, so native_method! derives the descriptor while the wrapper
        // stays JObject (matching the friendly `&JObject` surface).
        let mut objects = ObjectClassMappings::new("f");
        let tok =
            param_sig_token("&JObject", Some("java/util/Collection"), None, &mut objects).unwrap();
        assert!(tok.to_string().contains("__JniClass_f_0"), "{tok}");
        assert!(objects
            .type_map_tokens()
            .to_string()
            .replace(' ', "")
            .contains("__JniClass_f_0=>\"java.util.Collection\""));
    }

    #[test]
    fn param_sig_token_object_array_wraps_a_core_element_literally() {
        // java.lang.Object is a core class -> literal element, no alias.
        let mut objects = ObjectClassMappings::new("f");
        let tok = param_sig_token("&JObjectArray", Some("java/lang/Object"), None, &mut objects)
            .unwrap()
            .to_string()
            .replace(' ', "");
        assert_eq!(tok, "[\"java.lang.Object\"]");
        assert!(objects.type_map_tokens().is_empty());
    }

    #[test]
    fn param_sig_token_object_without_class_is_err() {
        let mut objects = ObjectClassMappings::new("f");
        assert!(param_sig_token("JObject", None, None, &mut objects)
            .unwrap_err()
            .contains("#[class = \"...\"]"));
        assert!(param_sig_token("JObjectArray", None, None, &mut objects).is_err());
    }

    #[test]
    fn param_sig_token_unknown_type_is_err() {
        let mut objects = ObjectClassMappings::new("f");
        assert!(param_sig_token("FooBar", None, None, &mut objects)
            .unwrap_err()
            .contains("Unknown JNI type"));
    }

    fn rtoken(ty: &str) -> String {
        let mut objects = ObjectClassMappings::new("t");
        return_sig_token(ty, None, None, &mut objects).unwrap().to_string().replace(' ', "")
    }

    #[test]
    fn return_sig_token_covers_each_shape() {
        assert_eq!(rtoken("()"), "void");
        assert_eq!(rtoken(""), "void");
        assert_eq!(rtoken("void"), "void");
        assert_eq!(rtoken("jint"), "jint");
        assert_eq!(rtoken("bool"), "jboolean");
        assert_eq!(rtoken("String"), "JString");
        assert_eq!(rtoken("jstring"), "JString");
        assert_eq!(rtoken("JString"), "JString");
        assert_eq!(rtoken("JByteArray"), "[jbyte]");
        // Result<T, _> unwraps to T.
        assert_eq!(rtoken("Result<jint, JniError>"), "jint");
        assert_eq!(rtoken("Result<(), JniError>"), "void");
        assert_eq!(rtoken("Result<JString, JniError>"), "JString");
    }

    #[test]
    fn return_sig_token_object_uses_returns_attr() {
        // java.lang.String is core -> emitted literally (not via an alias).
        let mut objects = ObjectClassMappings::new("f");
        let tok = return_sig_token("JObject", Some("java/lang/String"), None, &mut objects)
            .unwrap()
            .to_string()
            .replace(' ', "");
        assert_eq!(tok, "\"java.lang.String\"");
        let mut missing = ObjectClassMappings::new("f");
        assert!(return_sig_token("JObject", None, None, &mut missing)
            .unwrap_err()
            .contains("#[returns"));
    }

    #[test]
    fn object_mappings_core_class_is_literal() {
        let mut objects = ObjectClassMappings::new("t");
        let tok = objects.object_token("java/lang/Object", None).to_string().replace(' ', "");
        assert_eq!(tok, "\"java.lang.Object\"");
        assert!(objects.type_map_tokens().is_empty());
        assert!(objects.type_alias_items().is_empty());
    }

    #[test]
    fn object_mappings_noncore_class_binds_a_jobject_alias() {
        let mut objects = ObjectClassMappings::new("readEvents");
        let tok = objects.object_token("java/util/Collection", None).to_string();
        assert!(tok.contains("__JniClass_readEvents_0"), "{tok}");
        let type_map = objects.type_map_tokens().to_string().replace(' ', "");
        assert!(
            type_map.contains("__JniClass_readEvents_0=>\"java.util.Collection\""),
            "{type_map}"
        );
        let alias = objects.type_alias_items()[0].to_string().replace(' ', "");
        assert!(
            alias.contains("type__JniClass_readEvents_0<'local>=jni::objects::JObject<'local>"),
            "{alias}"
        );
    }

    #[test]
    fn object_mappings_dedupe_and_relative_resolution() {
        let mut objects = ObjectClassMappings::new("t");
        let a = objects.object_token("android/os/Parcel", None).to_string();
        let b = objects.object_token("android/os/Parcel", None).to_string();
        assert_eq!(a, b, "the same class must reuse one alias");
        assert_eq!(objects.entries.len(), 1);
        // A relative name is resolved against the module package, then dotted.
        objects.object_token("MotionEvent$PointerCoords", Some("android/view"));
        let type_map = objects.type_map_tokens().to_string().replace(' ', "");
        assert!(type_map.contains("\"android.view.MotionEvent$PointerCoords\""), "{type_map}");
    }

    #[test]
    fn object_mappings_default_package_uses_leading_dot() {
        let mut objects = ObjectClassMappings::new("t");
        objects.object_token("Foo", None);
        let type_map = objects.type_map_tokens().to_string().replace(' ', "");
        assert!(type_map.contains("\".Foo\""), "{type_map}");
    }

    #[test]
    fn derive_critical_signature_is_primitive_only() {
        let sig = |tokens| derive_critical_signature(&parse_fn(tokens));
        assert_eq!(sig(quote! { fn t(ptr: jlong) -> jint { 0 } }).unwrap(), "(J)I");
        assert_eq!(sig(quote! { fn t(a: jint, b: jlong) {} }).unwrap(), "(IJ)V");
        assert_eq!(sig(quote! { fn t() -> jlong { 0 } }).unwrap(), "()J");
        // A non-primitive parameter has no critical descriptor.
        assert!(sig(quote! { fn t(x: jobject) -> jint { 0 } }).unwrap_err().contains("primitive"));
    }

    #[test]
    fn regular_method_emits_inner_impl_and_native_method_const() {
        let s = gen_regular(
            quote! { fn nativeGetValue(env: &mut jni::Env<'_>, clazz: jclass, ptr: jlong) -> jint { 0 } },
        );
        // The inner impl fn: raw params in, `Result` out — no `extern "system"`.
        assert!(s.contains("fn __jni_impl_nativeGetValue"), "{s}");
        assert!(!s.contains("extern \"system\""), "the inner fn is not extern: {s}");
        assert!(s.contains("__arg0 : jni :: sys :: jlong"), "{s}");
        assert!(s.contains("jni_support :: JniError"), "{s}");
        // The native_method! invocation carries the signature tokens + policy.
        assert!(s.contains("jni :: native_method !"), "{s}");
        assert!(s.contains("name = \"nativeGetValue\""), "{s}");
        assert!(s.contains("fn = __jni_impl_nativeGetValue"), "{s}");
        assert!(s.contains("error_policy = __JniErrorPolicy"), "{s}");
        assert!(s.contains("abi_check = UnsafeNever"), "{s}");
        // jclass receiver => static native; the signature return token is jint.
        assert!(s.contains("static = true"), "{s}");
        assert!(s.replace(' ', "").contains("sig=(jlong)->jint"), "{s}");
    }

    #[test]
    fn instance_method_registers_as_non_static() {
        let s = gen_regular(quote! { fn f(env: &mut jni::Env<'_>, this: jobject, x: jint) {} });
        assert!(s.contains("static = false"), "{s}");
        assert!(s.contains("__this : jni :: objects :: JObject"), "{s}");
    }

    #[test]
    fn regular_method_carries_lint_allows() {
        let s = gen_regular(quote! { fn f(env: &mut jni::Env<'_>, clazz: jclass) {} });
        assert!(s.contains("non_snake_case") && s.contains("undocumented_unsafe_blocks"), "{s}");
        assert!(s.contains("non_upper_case_globals"), "{s}");
    }

    #[test]
    fn regular_method_carries_shim_doc() {
        let s = gen_regular(quote! { fn nativeGetValue(env: &mut jni::Env<'_>, clazz: jclass) {} });
        // A `#[doc]` records the shim's preconditions alongside the lint allows.
        assert!(s.contains("# [doc ="), "{s}");
        assert!(s.contains("JNI shim for `nativeGetValue`"), "{s}");
        // A safe native's doc says nothing about extra caller obligations.
        assert!(!s.contains("Callers must additionally uphold"), "{s}");
    }

    #[test]
    fn unsafe_native_wraps_call_and_notes_user_safety() {
        let s = gen_regular(
            quote! { unsafe fn f(env: &mut jni::Env<'_>, clazz: jclass, ptr: jlong) -> jint { 0 } },
        );
        // The user call is wrapped in `unsafe { .. }`.
        assert!(s.replace(' ', "").contains("unsafe{f(env,__this.as_raw(),__arg0)}"), "{s}");
        // The generated doc points at the user fn's own safety contract.
        assert!(
            s.contains("Callers must additionally uphold the safety preconditions of `f`"),
            "{s}"
        );
    }

    #[test]
    fn safe_native_does_not_wrap_the_call() {
        let s = gen_regular(
            quote! { fn f(env: &mut jni::Env<'_>, clazz: jclass, ptr: jlong) -> jint { 0 } },
        );
        assert!(
            !s.replace(' ', "").contains("unsafe{f("),
            "safe native must not wrap its call: {s}"
        );
    }

    #[test]
    fn unsafe_critical_wraps_call_and_carries_doc() {
        let s = gen_critical(quote! { unsafe fn nativeGetId(ptr: jlong) -> jint { 0 } });
        assert!(s.replace(' ', "").contains("unsafe{nativeGetId(__arg0)}"), "{s}");
        assert!(s.contains("@CriticalNative shim for `nativeGetId`"), "{s}");
        assert!(s.contains("Callers must additionally uphold"), "{s}");
    }

    #[test]
    fn owned_jstring_return_derives_the_string_descriptor() {
        // A native returning the owned `JString` wrapper (with the lifetime its
        // type requires) classifies as OwnedWrapper: the shim returns the
        // wrapper directly (no `from_raw`) and derives `Ljava/lang/String;`,
        // the same descriptor as a raw `-> jstring`.
        let s = gen_regular(quote! {
            fn f<'local>(env: &mut jni::Env<'local>, clazz: jclass, code: jint) -> JString<'local> {
                JString::null()
            }
        });
        assert!(s.replace(' ', "").contains("sig=(jint)->JString"), "{s}");
        assert!(!s.contains("from_raw"), "owned wrapper return passes through, no from_raw: {s}");
    }

    #[test]
    fn strip_lifetime_arg_strips_only_a_lone_lifetime() {
        // A lone lifetime argument is dropped so the bare wrapper name classifies.
        assert_eq!(strip_lifetime_arg("JString<'local>"), "JString");
        assert_eq!(strip_lifetime_arg("JObjectArray<'a>"), "JObjectArray");
        // Anything else is returned unchanged: a real type argument, a
        // lifetime-plus-type list, or a bare name.
        assert_eq!(strip_lifetime_arg("Foo<Bar>"), "Foo<Bar>");
        assert_eq!(strip_lifetime_arg("Foo<'a, Bar>"), "Foo<'a, Bar>");
        assert_eq!(strip_lifetime_arg("jstring"), "jstring");
        assert_eq!(strip_lifetime_arg("[jbyte]"), "[jbyte]");
    }

    #[test]
    fn owned_wrapper_param_lifetime_classifies_as_bare_wrapper() {
        // A parameter written with the lifetime its wrapper needs to be handed
        // back as the return (`JString<'local>`) bridges identically to a bare
        // `JString`: same shim parameter, same `JString` descriptor, and it is
        // not rejected as an unknown type.
        let s = gen_regular(quote! {
            fn passthrough<'local>(
                env: &mut jni::Env<'local>,
                clazz: jclass,
                def: JString<'local>,
            ) -> JString<'local> {
                def
            }
        });
        assert!(s.contains("__arg0 : jni :: objects :: JString"), "{s}");
        assert!(s.replace(' ', "").contains("sig=(JString)->JString"), "{s}");
    }

    #[test]
    fn owned_str_param_extracts_and_null_checks() {
        let s = gen_regular(quote! {
            fn isLoggable(env: &mut jni::Env<'_>, clazz: jclass, tag: &str, level: i32) -> bool {
                true
            }
        });
        assert!(s.contains("__arg0 : jni :: objects :: JString"), "{s}");
        assert!(s.contains("try_to_string"), "{s}");
        assert!(s.contains("NullPointer"), "{s}");
        assert!(s.contains("tag must not be null"), "{s}");
        // `&str` + `i32` describe (Ljava/lang/String;I) — token form `(JString, jint)`.
        assert!(s.replace(' ', "").contains("sig=(JString,jint)->jboolean"), "{s}");
    }

    #[test]
    fn option_str_param_is_nullable_without_a_null_check() {
        let s = gen_regular(
            quote! { fn f(env: &mut jni::Env<'_>, clazz: jclass, tag: Option<&str>) {} },
        );
        assert!(s.contains("as_deref"), "{s}");
        assert!(!s.contains("must not be null"), "nullable param must not null-check: {s}");
    }

    #[test]
    fn borrowed_str_param_uses_jni_str_without_allocating() {
        let s = gen_regular(
            quote! { fn f(env: &mut jni::Env<'_>, clazz: jclass, msg: &JNIStr) -> jint { 0 } },
        );
        assert!(s.contains("__arg0 : jni :: objects :: JString"), "{s}");
        assert!(s.contains("mutf8_chars"), "{s}");
        assert!(s.contains("jni :: strings :: JNIStr"), "{s}");
        // Borrowed: no owned `String` extraction.
        assert!(!s.contains("try_to_string"), "{s}");
    }

    #[test]
    fn string_return_builds_a_jstring() {
        let s = gen_regular(quote! {
            fn f(env: &mut jni::Env<'_>, clazz: jclass, code: jint) -> String { String::new() }
        });
        assert!(s.contains("new_string"), "{s}");
        // Return descriptor Ljava/lang/String; — token form `-> JString`.
        assert!(s.replace(' ', "").contains("->JString"), "{s}");
    }

    #[test]
    fn result_return_unwraps_with_question_mark() {
        let s = gen_regular(quote! {
            fn f(env: &mut jni::Env<'_>, clazz: jclass, ptr: jlong) -> Result<jint, JniError> {
                Ok(0)
            }
        });
        assert!(s.contains("error_policy = __JniErrorPolicy"), "{s}");
        assert!(s.replace(' ', "").contains("sig=(jlong)->jint"), "{s}");
        // The user function is `?`-propagated inside the inner impl fn.
        assert!(s.contains("?"), "{s}");
    }

    #[test]
    fn void_method_has_no_result_binding() {
        let s = gen_regular(quote! { fn f(env: &mut jni::Env<'_>, clazz: jclass, ptr: jlong) {} });
        assert!(!s.contains("__result"), "{s}");
        assert!(s.replace(' ', "").contains("->void"), "{s}");
    }

    #[test]
    fn object_param_binds_class_via_type_map_alias() {
        let s = gen_regular(quote! {
            fn f(env: &mut jni::Env<'_>, clazz: jclass, #[class = "java/util/Collection"] out: &JObject) {}
        });
        // The inner impl fn still takes a generic JObject.
        assert!(s.contains("__arg0 : jni :: objects :: JObject"), "{s}");
        let compact = s.replace(' ', "");
        // A JObject-typed alias is defined and bound to the class via type_map,
        // and the signature references that alias.
        assert!(
            compact.contains("type__JniClass_f_0<'local>=jni::objects::JObject<'local>"),
            "{s}"
        );
        assert!(compact.contains("type_map={__JniClass_f_0=>\"java.util.Collection\"}"), "{s}");
        assert!(compact.contains("sig=(__JniClass_f_0)->void"), "{s}");
    }

    #[test]
    fn critical_method_is_cfg_paired_and_registers_a_descriptor() {
        let s = gen_critical(quote! { fn nativeGetId(ptr: jlong) -> jint { 0 } });
        assert!(s.contains("extern \"system\" fn __jni_nativeGetId"), "{s}");
        assert!(s.contains("cfg (target_os = \"android\")"), "{s}");
        // The android variant, before the host cfg, takes only the primitive.
        let android = s.split("cfg (not (target_os = \"android\"))").next().unwrap();
        assert!(!android.contains("JNIEnv"), "android critical shim takes no JNIEnv: {s}");
        // The host variant discards the ignored (JNIEnv*, jclass) prefix.
        assert!(s.contains("_critical_env : * mut jni :: sys :: JNIEnv"), "{s}");
        assert!(s.contains("_critical_class : jni :: sys :: jclass"), "{s}");
        // Critical keeps the hand-rolled from_raw_parts path with its descriptor.
        assert!(s.contains("from_raw_parts"), "{s}");
        assert!(s.contains("(J)I"), "{s}");
        assert!(s.contains("__jni_nativeGetId as * mut core :: ffi :: c_void"), "{s}");
    }

    #[test]
    fn critical_rejects_types_that_need_an_env() {
        assert!(
            gen_critical_err(quote! { fn t(tag: &str) -> jint { 0 } }).contains("@CriticalNative")
        );
        assert!(gen_critical_err(quote! { fn t(tag: &JNIStr) -> jint { 0 } })
            .contains("@CriticalNative"));
        assert!(gen_critical_err(quote! { fn t(x: jint) -> String { String::new() } })
            .contains("@CriticalNative"));
        let result_err =
            gen_critical_err(quote! { fn t(x: jint) -> Result<jint, JniError> { Ok(0) } });
        assert!(result_err.contains("cannot return Result"), "{result_err}");
    }

    #[test]
    fn this_binding_maps_receiver_type() {
        let lt = lifetime();
        let this = |func: TokenStream| {
            let func = parse_fn(func);
            let inputs: Vec<&FnArg> = func.sig.inputs.iter().collect();
            let (shim_ty, call_arg) = this_binding(inputs[1], &lt).unwrap();
            (shim_ty.to_string(), call_arg.to_string())
        };
        let (ty, arg) = this(quote! { fn t(env: &mut jni::Env<'_>, clazz: jclass) {} });
        assert!(ty.contains("JClass"), "{ty}");
        assert_eq!(arg, "__this . as_raw ()");
        let (ty, arg) = this(quote! { fn t(env: &mut jni::Env<'_>, obj: JObject) {} });
        assert!(ty.contains("JObject"), "{ty}");
        assert_eq!(arg, "__this");
    }

    #[test]
    fn validate_leading_params_enforces_env_and_receiver() {
        let check = |func: TokenStream, mode| validate_leading_params(&parse_fn(func), &mode);
        assert!(check(quote! { fn t(env: &mut jni::Env<'_>) {} }, JniMode::Regular)
            .unwrap_err()
            .contains("at least two parameters"));
        assert!(check(quote! { fn t(x: jint, clazz: jclass) {} }, JniMode::Regular)
            .unwrap_err()
            .contains("first parameter must be a JNIEnv"));
        assert!(check(quote! { fn t(env: &mut jni::Env<'_>, x: jint) {} }, JniMode::Regular)
            .unwrap_err()
            .contains("second parameter must be a jobject/jclass"));
        assert!(check(
            quote! { fn t(env: &mut jni::Env<'_>, clazz: jclass, x: jint) {} },
            JniMode::Regular
        )
        .is_ok());
        // Critical methods take no env/receiver.
        assert!(check(quote! { fn t(x: jlong) -> jint { 0 } }, JniMode::Critical).is_ok());
    }

    #[test]
    fn return_type_str_stringifies_the_output() {
        let out = |func: TokenStream| return_type_str(&parse_fn(func).sig.output);
        assert_eq!(out(quote! { fn t() {} }), "()");
        assert_eq!(out(quote! { fn t() -> bool { true } }), "bool");
        assert_eq!(out(quote! { fn t() -> String { String::new() } }), "String");
    }

    #[test]
    fn register_fn_collects_the_method_consts() {
        let methods = vec![
            JniMethod {
                method_const: format_ident!("__NATIVE_METHOD_nativeGetId"),
                is_critical: true,
                generated_items: quote! {},
                cleaned_fn: quote! {},
            },
            JniMethod {
                method_const: format_ident!("__NATIVE_METHOD_nativeGetName"),
                is_critical: false,
                generated_items: quote! {},
                cleaned_fn: quote! {},
            },
        ];
        let s = generate_register_fn("android/view/MotionEvent", &methods).to_string();
        assert!(s.contains("pub fn register"));
        assert!(s.contains("find_class"));
        assert!(s.contains("android/view/MotionEvent"));
        assert!(s.contains("register_native_methods"));
        assert!(s.contains("__NATIVE_METHOD_nativeGetId"), "{s}");
        assert!(s.contains("__NATIVE_METHOD_nativeGetName"), "{s}");
        assert!(!s.contains(":: init"));
    }

    #[test]
    fn register_fn_is_a_noop_for_an_empty_module() {
        let s = generate_register_fn("android/os/SystemClock", &[]).to_string();
        assert!(s.contains("pub fn register") && s.contains("_env"));
        assert!(!s.contains("find_class") && !s.contains("register_native_methods"));
    }

    #[test]
    fn expand_passes_non_method_items_through_and_skips_policy_for_critical_only() {
        let out = expand_jni_module(
            quote! { "android/view/MotionEvent" },
            quote! {
                pub mod motion_event {
                    pub struct SomeHelper {
                        pub x: i32,
                    }
                    #[jni_method(critical)]
                    fn nativeGetId(ptr: jlong) -> jint {
                        0
                    }
                }
            },
        )
        .to_string();
        assert!(out.contains("SomeHelper") && out.contains("pub x : i32"));
        assert!(out.contains("pub fn register") && out.contains("__jni_nativeGetId"));
        // A critical-only module routes nothing through native_method!, so it
        // must not import the error policy (unused_imports would be an error).
        assert!(!out.contains("__JniErrorPolicy"), "{out}");
    }

    #[test]
    fn expand_registers_verbatim_and_overridden_names() {
        let out = expand_jni_module(
            quote! { "android/util/Log" },
            quote! {
                pub mod log {
                    #[jni_method]
                    fn println_native(env: &mut jni::Env<'_>, clazz: jclass, priority: jint) -> jint {
                        0
                    }
                    #[jni_method(fast, name = "logger_entry_max_payload_native")]
                    fn max_payload(env: &mut jni::Env<'_>, clazz: jclass) -> jint {
                        0
                    }
                }
            },
        )
        .to_string();
        // Java names are verbatim / from `name = "..."` — never camelized.
        assert!(
            out.contains("name = \"println_native\"") && !out.contains("printlnNative"),
            "{out}"
        );
        assert!(out.contains("name = \"logger_entry_max_payload_native\""), "{out}");
        // Registration is by const, and the module imports the error policy.
        assert!(out.contains("__NATIVE_METHOD_max_payload"), "{out}");
        assert!(out.contains("fn = __jni_impl_max_payload"), "{out}");
        assert!(out.contains("use jni_support :: ThrowJniError as __JniErrorPolicy"), "{out}");
    }

    #[test]
    fn expand_derives_signature_tokens_and_shares_overloaded_names() {
        // SystemProperties overloads native_get by signature; two Rust methods
        // register under one Java name. The DERIVED descriptors are checked in
        // `generated_shim_abi.rs`; here we pin the signature tokens the two
        // native_method! invocations carry.
        let out = expand_jni_module(
            quote! { "android/os/SystemProperties" },
            quote! {
                pub mod system_properties {
                    #[jni_method(fast, name = "native_get")]
                    fn native_get_string(
                        env: &mut jni::Env<'_>,
                        clazz: jclass,
                        key: &JNIStr,
                        def: jstring,
                    ) -> jstring {
                        def
                    }
                    #[jni_method(fast, name = "native_get")]
                    fn native_get_string_handle(
                        env: &mut jni::Env<'_>,
                        clazz: jclass,
                        handle: i64,
                    ) -> jstring {
                        std::ptr::null_mut()
                    }
                }
            },
        )
        .to_string();
        assert_eq!(out.matches("\"native_get\"").count(), 2, "{out}");
        let compact = out.replace(' ', "");
        assert!(compact.contains("sig=(JString,JString)->JString"), "{out}");
        assert!(compact.contains("sig=(jlong)->JString"), "{out}");
    }
}
