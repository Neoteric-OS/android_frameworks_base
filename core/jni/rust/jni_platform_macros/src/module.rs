//! Implementation of the `#[jni_module("...")]` attribute macro.
//!
//! Processes a module block, collecting `#[jni_method]` functions, and emits a
//! `register()` function that registers each as a native method with the JVM.
//!
//! Regular and `@FastNative` methods go through jni-rs's `native_method!` macro.
//! For each, this macro generates a private inner impl fn whose parameters are
//! the raw `jni::sys`/`jni::objects` values the JVM passes and whose body does
//! the Rust-friendly bridging (primitives, including `bool`) before and after
//! calling the user's function. `native_method!` wraps that
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
    parse2, punctuated::Punctuated, Attribute, FnArg, Item, ItemFn, ItemMod, Lit, Meta, MetaList,
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

/// How one user parameter is bridged from the value the JVM passes to the type
/// the user's function declares.
struct ParamBridge {
    /// The conversion strategy; it also determines the shim parameter's type.
    kind: ParamBridgeKind,
}

/// Conversion strategies for [`ParamBridge`].
///
/// Every shim parameter is an FFI-safe `jni::sys` primitive, passed through
/// unchanged.
enum ParamBridgeKind {
    /// ABI-identical primitive (`jint`, `jlong`, …); passed through unchanged.
    /// Holds the `jni::sys` type of the shim parameter. `bool` maps here too:
    /// jni-sys 0.4 aliases `jboolean = bool`, so it is the same shim type and
    /// needs no conversion.
    Primitive(TokenStream),
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

        _ => return None,
    };
    Some(ParamBridge { kind })
}

/// How the user's return value is converted to the value the shim returns.
///
/// At this layer every return is an ABI-identical primitive or void, so the
/// shim returns the value unchanged. `bool` classifies as a primitive: jni-sys
/// 0.4 aliases `jboolean = bool`.
enum ReturnBridge {
    /// No return value (`()` / no declared return).
    Void,
    /// An ABI-identical primitive; passed through. Holds the `jni::sys` type.
    Primitive { sys_ty: TokenStream },
}

/// Drops a trailing lifetime-only generic argument, so an owned object return
/// written with the lifetime its type requires (`JString<'local>`) classifies
/// the same as the bare wrapper name [`param_bridge`] recognizes (`JString`).
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

impl ReturnBridge {
    /// Classifies a user return type (textual form).
    fn parse(ty: &str) -> Result<Self, String> {
        // An owned wrapper return carries the lifetime its type needs
        // (`JString<'local>`); strip it so it classifies as the bare wrapper.
        let ty = strip_lifetime_arg(ty);
        let bridge = match ty {
            "()" | "" => ReturnBridge::Void,
            _ => {
                let kind =
                    param_bridge(ty).ok_or_else(|| format!("Unknown JNI type: '{}'", ty))?.kind;
                match kind {
                    ParamBridgeKind::Primitive(sys_ty) => ReturnBridge::Primitive { sys_ty },
                }
            }
        };
        Ok(bridge)
    }

    /// True for a `()` / absent return.
    fn is_void(&self) -> bool {
        matches!(self, ReturnBridge::Void)
    }

    /// The shim's return type tokens (empty for void), tied to `lifetime`.
    fn output_tokens(&self, _lifetime: &TokenStream) -> TokenStream {
        match self {
            ReturnBridge::Void => quote! {},
            ReturnBridge::Primitive { sys_ty } => quote! { -> #sys_ty },
        }
    }

    /// The `Ok` type of the `with_env` closure's `Result` (empty tuple for void),
    /// tied to `lifetime`.
    fn closure_ok_ty(&self, _lifetime: &TokenStream) -> TokenStream {
        match self {
            ReturnBridge::Void => quote! { () },
            ReturnBridge::Primitive { sys_ty } => quote! { #sys_ty },
        }
    }

    /// Wraps the user's return value (bound to `value`) as the `Ok` the closure
    /// yields. Not called for [`ReturnBridge::Void`].
    fn convert_ok(&self, value: &syn::Ident) -> TokenStream {
        match self {
            ReturnBridge::Void => quote! { ::core::result::Result::Ok(()) },
            ReturnBridge::Primitive { .. } => quote! { ::core::result::Result::Ok(#value) },
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
    fn parse(func: &ItemFn) -> Result<Self, TokenStream> {
        let jni_attr = find_jni_method_attr(&func.attrs)
            .expect("JniMethod::parse called without jni_method attr");

        let mode = parse_jni_mode(&jni_attr);

        validate_leading_params(func, &mode)
            .map_err(|e| syn::Error::new_spanned(func, e).to_compile_error())?;

        let java_name = parse_java_name(&jni_attr)
            .unwrap_or_else(|| derive_java_name(&func.sig.ident.to_string()));

        let method_const = format_ident!("__NATIVE_METHOD_{}", func.sig.ident);
        let is_critical = mode == JniMode::Critical;

        let generated_items = if is_critical {
            generate_critical_method(func, &java_name, &method_const)
        } else {
            generate_native_method(func, &java_name, &method_const)
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
                    match JniMethod::parse(func) {
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

/// Parses the contents of a `#[jni_method(...)]` attribute as a comma-separated
/// list of `Meta` items (e.g., `critical`, `fast`, `name = "foo"`).
///
/// Returns an empty list for bare `#[jni_method]`.
fn parse_jni_method_args(attr: &Attribute) -> Vec<Meta> {
    if let Meta::List(MetaList { tokens, .. }) = &attr.meta {
        if let Ok(args) = syn::parse::Parser::parse2(
            Punctuated::<Meta, Token![,]>::parse_terminated,
            tokens.clone(),
        ) {
            return args.into_iter().collect();
        }
    }
    Vec::new()
}

/// Parses the JNI mode from `#[jni_method(critical)]` or `#[jni_method(fast)]`.
fn parse_jni_mode(attr: &Attribute) -> JniMode {
    for meta in parse_jni_method_args(attr) {
        if let Meta::Path(path) = &meta {
            if path.is_ident("critical") {
                return JniMode::Critical;
            }
            if path.is_ident("fast") {
                return JniMode::Fast;
            }
        }
    }
    JniMode::Regular
}

/// Parses an explicit Java method name from `#[jni_method(name = "foo")]`.
fn parse_java_name(attr: &Attribute) -> Option<String> {
    for meta in parse_jni_method_args(attr) {
        if let Meta::NameValue(MetaNameValue {
            path,
            value: syn::Expr::Lit(syn::ExprLit { lit: Lit::Str(s), .. }),
            ..
        }) = &meta
        {
            if path.is_ident("name") {
                return Some(s.value());
            }
        }
    }
    None
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

/// The `jni_sig!` type token that `native_method!` uses to derive the JNI
/// descriptor and the wrapper parameter type for one user parameter.
///
/// Every arm agrees with the corresponding [`param_bridge`] classification: the
/// Rust type `native_method!` derives from this token is exactly the type
/// [`bridge_user_param`] declares for the matching inner-impl-fn parameter, so
/// `native_method!` type-checks the two against each other.
fn param_sig_token(ty: &str) -> Result<TokenStream, String> {
    let token = match ty {
        "jint" | "i32" => quote! { jint },
        "jlong" | "i64" => quote! { jlong },
        "jfloat" | "f32" => quote! { jfloat },
        "jdouble" | "f64" => quote! { jdouble },
        "jbyte" | "i8" => quote! { jbyte },
        "jchar" | "u16" => quote! { jchar },
        "jshort" | "i16" => quote! { jshort },
        "jboolean" | "bool" => quote! { jboolean },

        _ => return Err(format!("Unknown JNI type: '{}'", ty)),
    };
    Ok(token)
}

/// The `jni_sig!` return-type token for a user return type.
///
/// Primitives and `bool` defer to [`param_sig_token`]; `void` is the empty
/// return.
fn return_sig_token(ty: &str) -> Result<TokenStream, String> {
    // An owned wrapper return keeps the lifetime its type needs
    // (`JString<'local>`); strip it so its descriptor matches the bare wrapper.
    let ty = strip_lifetime_arg(ty);
    let token = match ty {
        "()" | "" | "void" => quote! { void },
        _ => param_sig_token(ty)?,
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

/// Generates the shim parameter and call argument for one user parameter.
/// `index` positions the shim parameter name (`__arg0`, `__arg1`, ...). At this
/// layer every parameter is an ABI-identical primitive, passed through unchanged.
fn bridge_user_param(
    pat_type: &PatType,
    index: usize,
    _lifetime: &TokenStream,
) -> Result<BridgedParam, String> {
    let ty = type_to_string(&pat_type.ty);
    let raw_ident = format_ident!("__arg{}", index);
    let bridge = param_bridge(&ty).ok_or_else(|| format!("Unknown JNI type: '{}'", ty))?;

    Ok(match bridge.kind {
        ParamBridgeKind::Primitive(sys_ty) => BridgedParam {
            shim_param: quote! { #raw_ident: #sys_ty },
            prelude: None,
            call_arg: quote! { #raw_ident },
        },
    })
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
    method_const: &proc_macro2::Ident,
) -> Result<TokenStream, String> {
    let user_fn_name = &func.sig.ident;
    let impl_ident = format_ident!("__jni_impl_{}", user_fn_name);
    let inputs: Vec<&FnArg> = func.sig.inputs.iter().collect();
    let lifetime = quote! { 'local };

    let return_type = return_type_str(&func.sig.output);
    let ret = ReturnBridge::parse(&return_type)?;
    let ok_ty = ret.closure_ok_ty(&lifetime);
    let ret_token = return_sig_token(&return_type)?;

    // Receiver (2nd parameter): a `jclass`-family type registers as a static
    // native (JClass), a `jobject`-family type as an instance native (JObject).
    // `native_method!`'s `static` flag must match the type `bridge`s.
    let receiver_ty = match inputs[1] {
        FnArg::Typed(pat_type) => type_to_string(&pat_type.ty),
        FnArg::Receiver(_) => return Err("JNI methods cannot take self".to_string()),
    };
    let is_static = matches!(receiver_ty.as_str(), "jclass" | "JClass" | "&JClass");
    let (this_shim_ty, this_arg) = this_binding(inputs[1], &lifetime)?;

    // Bridge each user parameter after env/this into an inner-fn parameter, a
    // call argument, and the matching signature token.
    let mut impl_params: Vec<TokenStream> = Vec::new();
    let mut sig_tokens: Vec<TokenStream> = Vec::new();
    let mut preludes: Vec<TokenStream> = Vec::new();
    let mut call_args: Vec<TokenStream> = Vec::new();
    for (index, arg) in inputs[2..].iter().enumerate() {
        if let FnArg::Typed(pat_type) = arg {
            let bridged = bridge_user_param(pat_type, index, &lifetime)?;
            impl_params.push(bridged.shim_param);
            if let Some(prelude) = bridged.prelude {
                preludes.push(prelude);
            }
            call_args.push(bridged.call_arg);

            let ty = type_to_string(&pat_type.ty);
            sig_tokens.push(param_sig_token(&ty)?);
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
        quote! { #(#preludes)* #call; ::core::result::Result::Ok(()) }
    } else {
        let result_ident = format_ident!("__result");
        let convert = ret.convert_ok(&result_ident);
        quote! { #(#preludes)* let #result_ident = #call; #convert }
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

    // `abi_check = UnsafeNever` matches these object-free primitive shims: the
    // only check thereby forgone is static-vs-instance, which is instead derived
    // structurally (`jclass` receiver => static, `jobject` => instance `this`).
    Ok(quote! {
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

    let ret = ReturnBridge::parse(&return_type_str(&func.sig.output))?;
    let output = ret.output_tokens(&lifetime);
    let jni_sig = derive_critical_signature(func)?;

    let mut shim_params: Vec<TokenStream> = Vec::new();
    let mut call_args: Vec<TokenStream> = Vec::new();
    for (index, arg) in func.sig.inputs.iter().enumerate() {
        if let FnArg::Typed(pat_type) = arg {
            let bridged = bridge_user_param(pat_type, index, &lifetime)?;
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
        let func = parse_fn(func);
        let name = func.sig.ident.to_string();
        let method_const = format_ident!("__NATIVE_METHOD_{}", func.sig.ident);
        generate_native_method(&func, &name, &method_const).unwrap().to_string()
    }

    fn gen_critical(func: TokenStream) -> String {
        let func = parse_fn(func);
        let name = func.sig.ident.to_string();
        let method_const = format_ident!("__NATIVE_METHOD_{}", func.sig.ident);
        generate_critical_method(&func, &name, &method_const).unwrap().to_string()
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
    fn derive_java_name_is_verbatim() {
        assert_eq!(derive_java_name("println_native"), "println_native");
        assert_eq!(derive_java_name("elapsedRealtime"), "elapsedRealtime");
    }

    #[test]
    fn parse_jni_mode_recognizes_each_mode() {
        let mode = |tokens| {
            let attr = syn::parse2::<ItemFn>(tokens).unwrap().attrs[0].clone();
            parse_jni_mode(&attr)
        };
        assert_eq!(mode(quote! { #[jni_method(critical)] fn t() {} }), JniMode::Critical);
        assert_eq!(mode(quote! { #[jni_method(fast)] fn t() {} }), JniMode::Fast);
        assert_eq!(mode(quote! { #[jni_method] fn t() {} }), JniMode::Regular);
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
    fn void_method_has_no_result_binding() {
        let s = gen_regular(quote! { fn f(env: &mut jni::Env<'_>, clazz: jclass, ptr: jlong) {} });
        assert!(!s.contains("__result"), "{s}");
        assert!(s.replace(' ', "").contains("->void"), "{s}");
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
}
