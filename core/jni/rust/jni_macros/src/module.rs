//! Implementation of the `#[jni_module("...")]` attribute macro.
//!
//! Processes a module block, collecting `#[jni_method]` functions. For every
//! such function it generates an `unsafe extern "system"` shim whose parameter
//! and return types are raw `jni::sys` types — the exact ABI the JVM uses when
//! invoking a registered native — plus a `register()` function that registers
//! the shims with the JVM. The user's function keeps its Rust-friendly
//! signature (`&mut JNIEnv`, ...) and is called by the shim after argument
//! conversion.

use proc_macro2::TokenStream;
use quote::{format_ident, quote};
use syn::{
    parse2, punctuated::Punctuated, Attribute, FnArg, Item, ItemFn, ItemMod, Lit, Meta, MetaList,
    MetaNameValue, PatType, ReturnType, Token, Type,
};

use crate::class::JavaClass;
use crate::sig;

/// A fully processed JNI method: registration metadata plus generated code.
///
/// Created by [`JniMethod::parse`] from a `#[jni_method]`-annotated function.
struct JniMethod {
    /// Name of the generated `extern "system"` shim registered with the JVM.
    shim_name: String,
    /// The Java method name (the Rust fn name verbatim, or explicit `name = "..."`).
    java_name: String,
    /// The derived JNI signature string (e.g., `"(IJ)V"`).
    jni_sig: String,
    /// The cleaned user function with JNI attributes stripped.
    cleaned_fn: TokenStream,
    /// The generated `unsafe extern "system"` shim that the JVM calls.
    shim_fn: TokenStream,
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

/// How one user parameter is bridged from the raw value the JVM passes to the
/// type the user's function declares.
struct ParamBridge {
    /// The `jni::sys` type of the corresponding shim parameter.
    raw_ty: TokenStream,
    /// The conversion strategy from the raw value to the declared type.
    kind: ParamBridgeKind,
}

/// Conversion strategies for [`ParamBridge`].
enum ParamBridgeKind {
    /// The declared type is ABI-identical to the raw type; pass it through.
    Value,
}

/// Maps a user parameter type (textual form) to its bridging strategy.
///
/// Returns `None` for types outside the supported set; signature derivation
/// reports those as `Unknown JNI type` first.
fn param_bridge(ty: &str) -> Option<ParamBridge> {
    let value = |raw: TokenStream| Some(ParamBridge { raw_ty: raw, kind: ParamBridgeKind::Value });

    match ty {
        "jint" | "i32" => value(quote! { jni::sys::jint }),
        "jlong" | "i64" => value(quote! { jni::sys::jlong }),
        "jfloat" | "f32" => value(quote! { jni::sys::jfloat }),
        "jdouble" | "f64" => value(quote! { jni::sys::jdouble }),
        "jboolean" | "u8" => value(quote! { jni::sys::jboolean }),
        "jbyte" | "i8" => value(quote! { jni::sys::jbyte }),
        "jchar" | "u16" => value(quote! { jni::sys::jchar }),
        "jshort" | "i16" => value(quote! { jni::sys::jshort }),

        "jstring" => value(quote! { jni::sys::jstring }),

        "jbyteArray" => value(quote! { jni::sys::jbyteArray }),
        "jintArray" => value(quote! { jni::sys::jintArray }),
        "jfloatArray" => value(quote! { jni::sys::jfloatArray }),
        "jlongArray" => value(quote! { jni::sys::jlongArray }),
        "jshortArray" => value(quote! { jni::sys::jshortArray }),
        "jdoubleArray" => value(quote! { jni::sys::jdoubleArray }),
        "jbooleanArray" => value(quote! { jni::sys::jbooleanArray }),
        "jcharArray" => value(quote! { jni::sys::jcharArray }),

        "jobject" => value(quote! { jni::sys::jobject }),
        "jobjectArray" => value(quote! { jni::sys::jobjectArray }),

        _ => None,
    }
}

/// How the user's return value is converted to the raw type the shim returns.
enum ReturnBridge {
    /// No return value (`()` / no declared return).
    Void,
    /// The declared type is ABI-identical to the raw type; pass it through.
    Value { raw_ty: TokenStream },
}

impl ReturnBridge {
    /// Classifies a user return type (textual form).
    fn parse(ty: &str) -> Result<Self, String> {
        if ty == "()" || ty.is_empty() {
            return Ok(ReturnBridge::Void);
        }

        let bridge = param_bridge(ty).ok_or_else(|| format!("Unknown JNI type: '{}'", ty))?;
        let ret = match bridge.kind {
            ParamBridgeKind::Value => ReturnBridge::Value { raw_ty: bridge.raw_ty },
        };
        Ok(ret)
    }

    /// The shim's return type tokens (empty for void).
    fn output_tokens(&self) -> TokenStream {
        match self {
            ReturnBridge::Void => quote! {},
            ReturnBridge::Value { raw_ty } => quote! { -> #raw_ty },
        }
    }

    /// Converts the user's return value (bound to `value`) to the raw type.
    fn convert_expr(&self, value: &syn::Ident) -> TokenStream {
        match self {
            ReturnBridge::Void | ReturnBridge::Value { .. } => quote! { #value },
        }
    }
}

/// Returns the number of leading env/this parameters to skip for signature derivation.
///
/// Returns 0 for critical mode, 2 for regular/fast mode. Call `validate_leading_params`
/// first to ensure the function has the required env/this parameters.
fn env_this_skip_count(mode: &JniMode) -> usize {
    match mode {
        JniMode::Critical => 0,
        JniMode::Fast | JniMode::Regular => 2,
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
    /// Validates parameters, derives the JNI signature, generates the
    /// `extern "system"` shim, and strips JNI attributes from the user function.
    ///
    /// # Example
    ///
    /// Given:
    /// ```text
    /// #[jni_method]
    /// fn nativeGetValue(env: &mut JNIEnv, clazz: jclass, ptr: jlong) -> jint { 0 }
    /// ```
    ///
    /// Returns `Ok(JniMethod)` with:
    /// - `shim_name`: `"__jni_nativeGetValue"`
    /// - `java_name`: `"nativeGetValue"` (the Rust fn name, verbatim)
    /// - `jni_sig`: `"(J)I"`
    /// - `cleaned_fn`: the function with `#[jni_method]` stripped
    /// - `shim_fn`: an `unsafe extern "system" fn __jni_nativeGetValue(env: *mut
    ///   jni::sys::JNIEnv, this: jni::sys::jobject, __arg0: jni::sys::jlong) ->
    ///   jni::sys::jint` that builds a `JNIEnv` and calls `nativeGetValue`
    ///
    /// Returns `Err(compile_error TokenStream)` if validation fails (e.g., missing
    /// env/this parameters, unknown types).
    fn parse(func: &ItemFn, module_package: Option<&str>) -> Result<Self, TokenStream> {
        let jni_attr = find_jni_method_attr(&func.attrs)
            .expect("JniMethod::parse called without jni_method attr");

        let mode = parse_jni_mode(&jni_attr);

        validate_leading_params(func, &mode)
            .map_err(|e| syn::Error::new_spanned(func, e).to_compile_error())?;

        let java_name = parse_java_name(&jni_attr)
            .unwrap_or_else(|| derive_java_name(&func.sig.ident.to_string()));

        let jni_sig = derive_method_signature(func, &mode, module_package)
            .map_err(|e| syn::Error::new_spanned(func, e).to_compile_error())?;

        let shim_fn = generate_shim(func, &mode, &java_name)
            .map_err(|e| syn::Error::new_spanned(func, e).to_compile_error())?;

        let shim_name = format!("__jni_{}", func.sig.ident);

        let cleaned = strip_jni_attrs(func);

        Ok(JniMethod { shim_name, java_name, jni_sig, cleaned_fn: quote! { #cleaned }, shim_fn })
    }
}

/// Generates the `register()` function that registers all native methods with the JVM.
///
/// # Example
///
/// Given `class_path = "android/view/MotionEvent"` and one method `nativeGetId` with
/// signature `"(J)I"`, generates:
///
/// ```text
/// pub fn register(env: &mut jni::JNIEnv<'_>) {
///     let class = env.find_class("android/view/MotionEvent").expect(...);
///     let methods = [
///         jni::NativeMethod { name: "nativeGetId".into(), sig: "(J)I".into(),
///                             fn_ptr: __jni_nativeGetId as *mut c_void }
///     ];
///     env.register_native_methods(&class, &methods).expect(...);
/// }
/// ```
///
/// With an empty method list, generates a no-op `register(_env)` function.
fn generate_register_fn(class_path: &str, methods: &[JniMethod]) -> TokenStream {
    let method_entries: Vec<TokenStream> = methods
        .iter()
        .map(|info| {
            let java_name = &info.java_name;
            let sig = &info.jni_sig;
            let fn_name = format_ident!("{}", &info.shim_name);
            quote! {
                jni::NativeMethod {
                    name: #java_name.into(),
                    sig: #sig.into(),
                    fn_ptr: #fn_name as *mut core::ffi::c_void,
                }
            }
        })
        .collect();

    if methods.is_empty() {
        quote! {
            /// Registers all native methods in this module with the JVM.
            ///
            /// Must be called during JNI_OnLoad or equivalent initialization.
            pub fn register(_env: &mut jni::JNIEnv<'_>) {
            }
        }
    } else {
        quote! {
            /// Registers all native methods in this module with the JVM.
            ///
            /// Must be called during JNI_OnLoad or equivalent initialization.
            pub fn register(env: &mut jni::JNIEnv<'_>) {
                let class = env.find_class(#class_path)
                    .expect("Failed to find JNI class");
                let methods = [
                    #(#method_entries),*
                ];
                env.register_native_methods(&class, &methods)
                    .expect("Failed to register native methods");
            }
        }
    }
}

/// Processes a `#[jni_module("android/view/MotionEvent")]` annotated module.
///
/// Collects all `#[jni_method]` functions and generates, for each, an
/// `unsafe extern "system"` shim plus a module-level `register()` function
/// that registers the shims as native methods with the JVM.
///
/// # Example
///
/// ```text
/// // Input:
/// #[jni_module("android/view/MotionEvent")]
/// mod motion_event {
///     #[jni_method]
///     fn nativeGetId(env: &mut JNIEnv, clazz: jclass, ptr: jlong) -> jint {
///         0
///     }
/// }
///
/// // Output:
/// mod motion_event {
///     unsafe extern "system" fn __jni_nativeGetId(
///         env: *mut jni::sys::JNIEnv,
///         this: jni::sys::jobject,
///         __arg0: jni::sys::jlong,
///     ) -> jni::sys::jint {
///         let mut env = unsafe { jni::JNIEnv::from_raw(env) }.expect(...);
///         let __result = nativeGetId(&mut env, this, __arg0);
///         __result
///     }
///     fn nativeGetId(env: &mut JNIEnv, clazz: jclass, ptr: jlong) -> jint {
///         0
///     }
///
///     pub fn register(env: &mut jni::JNIEnv<'_>) { // generated
///         let class = env.find_class("android/view/MotionEvent").expect(...);
///         let methods = [
///             NativeMethod { name: "nativeGetId", sig: "(J)I",
///                            fn_ptr: __jni_nativeGetId as *mut c_void },
///         ];
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
                            output_items.push(method.shim_fn.clone());
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

    quote! {
        #vis mod #mod_name {
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

/// Derives the JNI signature for a method based on its Rust function signature.
fn derive_method_signature(
    func: &ItemFn,
    mode: &JniMode,
    module_package: Option<&str>,
) -> Result<String, String> {
    let mut params: Vec<(&str, Option<&str>)> = Vec::new();

    let inputs: Vec<&FnArg> = func.sig.inputs.iter().collect();
    let skip = env_this_skip_count(mode);

    // We need to hold the type strings so we can reference them
    let type_strings: Vec<String> = inputs[skip..]
        .iter()
        .map(|arg| {
            if let FnArg::Typed(pat_type) = arg {
                type_to_string(&pat_type.ty)
            } else {
                "()".to_string()
            }
        })
        .collect();

    for ty in &type_strings {
        params.push((ty.as_str(), None));
    }

    let return_type = return_type_str(&func.sig.output);

    sig::build_signature(&params, &return_type, None, module_package)
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
            if matches!(ty.as_str(), "&str" | "Option<&str>" | "&JavaStr" | "Option<&JavaStr>") {
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
    /// The parameter declaration in the shim signature (raw `jni::sys` type).
    shim_param: TokenStream,
    /// Conversion statements executed before calling the user's function.
    prelude: Option<TokenStream>,
    /// The argument expression passed to the user's function.
    call_arg: TokenStream,
}

/// Generates the shim parameter, conversion statements, and call argument for
/// one user parameter. `index` positions the raw parameter name (`__arg0`,
/// `__arg1`, ...).
fn bridge_user_param(pat_type: &PatType, index: usize) -> Result<BridgedParam, String> {
    let ty = type_to_string(&pat_type.ty);
    let raw_ident = format_ident!("__arg{}", index);
    let bridge = param_bridge(&ty).ok_or_else(|| format!("Unknown JNI type: '{}'", ty))?;
    let raw_ty = &bridge.raw_ty;
    let shim_param = quote! { #raw_ident: #raw_ty };

    Ok(match bridge.kind {
        ParamBridgeKind::Value => {
            BridgedParam { shim_param, prelude: None, call_arg: quote! { #raw_ident } }
        }
    })
}

/// The call argument for the this/class slot, converting the shim's raw
/// `jni::sys::jobject` to the user's declared type.
fn this_call_arg(arg: &FnArg) -> Result<TokenStream, String> {
    let FnArg::Typed(pat_type) = arg else {
        return Err("JNI methods cannot take self".to_string());
    };
    let ty = type_to_string(&pat_type.ty);
    Ok(match ty.as_str() {
        "jclass" | "jobject" => quote! { this },
        "JClass" => quote! { unsafe { jni::objects::JClass::from_raw(this) } },
        "JObject" => quote! { unsafe { jni::objects::JObject::from_raw(this) } },
        "&JClass" => quote! { &unsafe { jni::objects::JClass::from_raw(this) } },
        "&JObject" => quote! { &unsafe { jni::objects::JObject::from_raw(this) } },
        other => {
            return Err(format!(
                "second parameter must be a jobject/jclass type, found '{}'",
                other
            ))
        }
    })
}

/// Generates the `unsafe extern "system"` shim registered with the JVM.
///
/// The shim's signature uses only raw `jni::sys` types, matching the calling
/// convention ART uses to invoke natives: regular and fast natives receive
/// `(*mut JNIEnv, jobject, <args>...)`, critical natives receive only the
/// arguments. The shim rebuilds a safe `jni::JNIEnv`, passes each argument
/// to the user's function, and returns its result.
///
/// # Example
///
/// Given `fn nativeGetId(env: &mut JNIEnv, clazz: jclass, ptr: jlong) -> jint { ... }`,
/// generates:
/// ```text
/// unsafe extern "system" fn __jni_nativeGetId(
///     env: *mut jni::sys::JNIEnv,
///     this: jni::sys::jobject,
///     __arg0: jni::sys::jlong,
/// ) -> jni::sys::jint {
///     let mut env = unsafe { jni::JNIEnv::from_raw(env) }.expect(...);
///     let __result = nativeGetId(&mut env, this, __arg0);
///     __result
/// }
/// ```
///
/// Returns `Err` if the function is `@CriticalNative` and uses types that require
/// JNIEnv (`&str`, `String`, `Result`).
fn generate_shim(func: &ItemFn, mode: &JniMode, java_name: &str) -> Result<TokenStream, String> {
    if *mode == JniMode::Critical {
        validate_critical_native(func)?;
    }

    let user_fn_name = &func.sig.ident;
    let shim_name = format_ident!("__jni_{}", user_fn_name);
    let inputs: Vec<&FnArg> = func.sig.inputs.iter().collect();
    let skip = env_this_skip_count(mode);

    let ret = ReturnBridge::parse(&return_type_str(&func.sig.output))?;

    let mut shim_params: Vec<TokenStream> = Vec::new();
    let mut preludes: Vec<TokenStream> = Vec::new();
    let mut call_args: Vec<TokenStream> = Vec::new();

    if *mode != JniMode::Critical {
        shim_params.push(quote! { env: *mut jni::sys::JNIEnv });
        shim_params.push(quote! { this: jni::sys::jobject });

        let null_env_msg =
            format!("JNI method '{}': the JVM passed a null JNIEnv pointer", java_name);
        preludes.push(quote! {
            let mut env = unsafe { jni::JNIEnv::from_raw(env) }.expect(#null_env_msg);
        });
        call_args.push(quote! { &mut env });
        call_args.push(this_call_arg(inputs[1])?);
    }

    for (index, arg) in inputs[skip..].iter().enumerate() {
        if let FnArg::Typed(pat_type) = arg {
            let bridged = bridge_user_param(pat_type, index)?;
            shim_params.push(bridged.shim_param);
            if let Some(prelude) = bridged.prelude {
                preludes.push(prelude);
            }
            call_args.push(bridged.call_arg);
        }
    }

    let output = ret.output_tokens();

    let body = if matches!(ret, ReturnBridge::Void) {
        quote! {
            #user_fn_name(#(#call_args),*);
        }
    } else {
        let result_ident = format_ident!("__result");
        let convert = ret.convert_expr(&result_ident);
        quote! {
            let #result_ident = #user_fn_name(#(#call_args),*);
            #convert
        }
    };

    // ART invokes @CriticalNative methods without the JNIEnv and class
    // arguments, but host JVMs (Ravenwood's OpenJDK, layoutlib's Studio JVM)
    // ignore the annotation and always pass them. Critical shims therefore
    // get a host variant that accepts and discards the two leading arguments
    // — the Rust equivalent of core_jni_helpers.h's CRITICAL_JNI_PARAMS_COMMA.
    if *mode == JniMode::Critical {
        Ok(quote! {
            #[cfg(target_os = "android")]
            #[allow(non_snake_case, clippy::too_many_arguments, clippy::undocumented_unsafe_blocks)]
            unsafe extern "system" fn #shim_name(#(#shim_params),*) #output {
                #(#preludes)*
                #body
            }
            #[cfg(not(target_os = "android"))]
            #[allow(non_snake_case, clippy::too_many_arguments, clippy::undocumented_unsafe_blocks)]
            unsafe extern "system" fn #shim_name(
                _critical_env: *mut jni::sys::JNIEnv,
                _critical_class: jni::sys::jclass,
                #(#shim_params),*
            ) #output {
                #(#preludes)*
                #body
            }
        })
    } else {
        Ok(quote! {
            #[allow(non_snake_case, clippy::too_many_arguments, clippy::undocumented_unsafe_blocks)]
            unsafe extern "system" fn #shim_name(#(#shim_params),*) #output {
                #(#preludes)*
                #body
            }
        })
    }
}

/// Checks if a type string represents a JNI environment parameter.
fn is_env_type(ty: &str) -> bool {
    ty.contains("JNIEnv") || ty.contains("Env")
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

/// Converts a syn Type to a simplified string representation.
fn type_to_string(ty: &Type) -> String {
    use quote::ToTokens;
    let mut tokens = TokenStream::new();
    ty.to_tokens(&mut tokens);
    let s = tokens.to_string();
    // Clean up spacing artifacts from token stream
    s.replace(" < ", "<")
        .replace(" > ", ">")
        .replace(" , ", ", ")
        .replace("& ", "&")
        .replace("< ", "<")
        .replace(" >", ">")
}

/// Strips JNI-specific attributes from a function, leaving the pure Rust function.
///
/// Removes `#[jni_method]` attributes from the function.
///
/// # Example
///
/// ```text
/// // Input:
/// #[jni_method]
/// fn nativeGetId(env: &mut JNIEnv, clazz: jclass, ptr: jlong) -> jint { ... }
///
/// // Output:
/// fn nativeGetId(env: &mut JNIEnv, clazz: jclass, ptr: jlong) -> jint { ... }
/// ```
fn strip_jni_attrs(func: &ItemFn) -> TokenStream {
    let mut clean_func = func.clone();

    // Remove jni_method attributes from the function
    clean_func.attrs.retain(|a| !a.path().is_ident("jni_method"));

    quote! { #clean_func }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_derive_java_name_verbatim() {
        assert_eq!(derive_java_name("println_native"), "println_native");
        assert_eq!(
            derive_java_name("logger_entry_max_payload_native"),
            "logger_entry_max_payload_native"
        );
        assert_eq!(derive_java_name("elapsedRealtime"), "elapsedRealtime");
        assert_eq!(derive_java_name("nativeGetId"), "nativeGetId");
    }

    #[test]
    fn test_parse_jni_mode_critical() {
        let attr: Attribute = syn::parse2::<ItemFn>(quote! {
            #[jni_method(critical)]
            fn test() {}
        })
        .unwrap()
        .attrs[0]
            .clone();
        assert_eq!(parse_jni_mode(&attr), JniMode::Critical);
    }

    #[test]
    fn test_parse_jni_mode_fast() {
        let attr: Attribute = syn::parse2::<ItemFn>(quote! {
            #[jni_method(fast)]
            fn test() {}
        })
        .unwrap()
        .attrs[0]
            .clone();
        assert_eq!(parse_jni_mode(&attr), JniMode::Fast);
    }

    #[test]
    fn test_parse_jni_mode_regular() {
        let attr: Attribute = syn::parse2::<ItemFn>(quote! {
            #[jni_method]
            fn test() {}
        })
        .unwrap()
        .attrs[0]
            .clone();
        assert_eq!(parse_jni_mode(&attr), JniMode::Regular);
    }

    // ---- param_bridge tests ----

    #[test]
    fn test_param_bridge_passthrough_primitives() {
        for ty in ["jint", "i32", "jlong", "i64", "jboolean", "u8", "jstring"] {
            let bridge = param_bridge(ty).unwrap();
            assert!(matches!(bridge.kind, ParamBridgeKind::Value), "{ty} should pass through");
        }
    }

    #[test]
    fn test_param_bridge_unknown() {
        assert!(param_bridge("FooBar").is_none());
        assert!(param_bridge("String").is_none());
    }

    // ---- ReturnBridge tests ----

    #[test]
    fn test_return_bridge_void() {
        let ret = ReturnBridge::parse("()").unwrap();
        assert!(matches!(ret, ReturnBridge::Void));
        assert!(ret.output_tokens().is_empty());
    }

    #[test]
    fn test_return_bridge_primitive() {
        let ret = ReturnBridge::parse("jint").unwrap();
        assert!(matches!(ret, ReturnBridge::Value { .. }));
        assert_eq!(ret.output_tokens().to_string(), quote! { -> jni::sys::jint }.to_string());
    }

    // ---- generate_shim tests ----

    #[test]
    fn test_shim_is_extern_system_with_raw_types() {
        let func: ItemFn = syn::parse2(quote! {
            fn nativeGetValue(env: &mut jni::JNIEnv<'_>, clazz: jclass, ptr: jlong) -> jint {
                0
            }
        })
        .unwrap();
        let shim = generate_shim(&func, &JniMode::Regular, "nativeGetValue").unwrap();
        let s = shim.to_string();
        assert!(s.contains("unsafe extern \"system\" fn __jni_nativeGetValue"), "{s}");
        assert!(s.contains("env : * mut jni :: sys :: JNIEnv"), "{s}");
        assert!(s.contains("this : jni :: sys :: jobject"), "{s}");
        assert!(s.contains("__arg0 : jni :: sys :: jlong"), "{s}");
        assert!(s.contains("-> jni :: sys :: jint"), "{s}");
        assert!(s.contains("JNIEnv :: from_raw"), "{s}");
        assert!(s.contains("null JNIEnv pointer"), "{s}");
        assert!(s.contains("& mut env"), "{s}");
    }

    #[test]
    fn test_shim_critical_has_no_env_or_this() {
        let func: ItemFn = syn::parse2(quote! {
            fn nativeGetId(ptr: jlong) -> jint {
                0
            }
        })
        .unwrap();
        let shim = generate_shim(&func, &JniMode::Critical, "nativeGetId").unwrap();
        let s = shim.to_string();
        assert!(s.contains("unsafe extern \"system\" fn __jni_nativeGetId"), "{s}");
        assert!(s.contains("__arg0 : jni :: sys :: jlong"), "{s}");
        // ART calls criticals without env/class, but host JVMs ignore the
        // annotation and always pass them, so the shim comes in a cfg'd pair.
        let android_variant = s.split("cfg (not (target_os = \"android\"))").next().unwrap();
        assert!(
            !android_variant.contains("JNIEnv"),
            "the Android critical shim must not take a JNIEnv: {s}"
        );
        assert!(s.contains("cfg (target_os = \"android\")"), "{s}");
        assert!(
            s.contains("_critical_env : * mut jni :: sys :: JNIEnv"),
            "the host critical shim must accept the JNIEnv/class prefix: {s}"
        );
        assert!(s.contains("_critical_class : jni :: sys :: jclass"), "{s}");
    }

    #[test]
    fn test_shim_has_lint_allows() {
        let func: ItemFn = syn::parse2(quote! {
            fn nativeGetValue(env: &mut jni::JNIEnv<'_>, clazz: jclass, ptr: jlong) -> jint {
                0
            }
        })
        .unwrap();
        let shim = generate_shim(&func, &JniMode::Regular, "nativeGetValue").unwrap();
        let s = shim.to_string();
        assert!(s.contains("allow"), "{s}");
        assert!(s.contains("non_snake_case"), "{s}");
        assert!(s.contains("undocumented_unsafe_blocks"), "{s}");
    }

    #[test]
    fn test_shim_critical_str_error() {
        let func: ItemFn = syn::parse2(quote! {
            fn test(tag: &str) -> jint {
                0
            }
        })
        .unwrap();
        let result = generate_shim(&func, &JniMode::Critical, "test");
        assert!(result.is_err());
        assert!(result.unwrap_err().contains("@CriticalNative"));
    }

    #[test]
    fn test_shim_critical_string_return_error() {
        let func: ItemFn = syn::parse2(quote! {
            fn test(x: jint) -> String {
                String::new()
            }
        })
        .unwrap();
        let result = generate_shim(&func, &JniMode::Critical, "test");
        assert!(result.is_err());
        assert!(result.unwrap_err().contains("@CriticalNative"));
    }

    #[test]
    fn test_shim_critical_result_return_error() {
        let func: ItemFn = syn::parse2(quote! {
            fn test(x: jint) -> Result<jint, JniError> {
                Ok(0)
            }
        })
        .unwrap();
        let result = generate_shim(&func, &JniMode::Critical, "test");
        assert!(result.is_err());
        let msg = result.unwrap_err();
        assert!(msg.contains("@CriticalNative"), "{msg}");
        assert!(msg.contains("cannot return Result"), "{msg}");
    }

    #[test]
    fn test_shim_void_return_has_no_result_binding() {
        let func: ItemFn = syn::parse2(quote! {
            fn nativeDispose(env: &mut jni::JNIEnv<'_>, clazz: jclass, ptr: jlong) {
            }
        })
        .unwrap();
        let shim = generate_shim(&func, &JniMode::Regular, "nativeDispose").unwrap();
        let s = shim.to_string();
        assert!(!s.contains("__result"), "{s}");
    }

    // ---- return_type_str tests ----

    #[test]
    fn test_return_type_str_default() {
        let func: ItemFn = syn::parse2(quote! { fn test() {} }).unwrap();
        assert_eq!(return_type_str(&func.sig.output), "()");
    }

    #[test]
    fn test_return_type_str_typed() {
        let func: ItemFn = syn::parse2(quote! { fn test() -> bool { true } }).unwrap();
        assert_eq!(return_type_str(&func.sig.output), "bool");
    }

    #[test]
    fn test_return_type_str_string() {
        let func: ItemFn = syn::parse2(quote! { fn test() -> String { String::new() } }).unwrap();
        assert_eq!(return_type_str(&func.sig.output), "String");
    }

    // ---- this_call_arg tests ----

    #[test]
    fn test_this_call_arg_raw_types_pass_through() {
        let func: ItemFn = syn::parse2(quote! {
            fn test(env: &mut jni::JNIEnv<'_>, clazz: jclass) {}
        })
        .unwrap();
        let inputs: Vec<&FnArg> = func.sig.inputs.iter().collect();
        assert_eq!(this_call_arg(inputs[1]).unwrap().to_string(), "this");
    }

    #[test]
    fn test_this_call_arg_wrapper_types_convert() {
        let func: ItemFn = syn::parse2(quote! {
            fn test(env: &mut jni::JNIEnv<'_>, obj: JObject) {}
        })
        .unwrap();
        let inputs: Vec<&FnArg> = func.sig.inputs.iter().collect();
        let arg = this_call_arg(inputs[1]).unwrap().to_string();
        assert!(arg.contains("JObject :: from_raw"), "{arg}");
    }

    // ---- env_this_skip_count tests ----

    #[test]
    fn test_env_this_skip_count_regular() {
        assert_eq!(env_this_skip_count(&JniMode::Regular), 2);
    }

    #[test]
    fn test_env_this_skip_count_fast() {
        assert_eq!(env_this_skip_count(&JniMode::Fast), 2);
    }

    #[test]
    fn test_env_this_skip_count_critical() {
        assert_eq!(env_this_skip_count(&JniMode::Critical), 0);
    }

    // ---- validate_leading_params tests ----

    #[test]
    fn test_validate_leading_params_too_few() {
        let func: ItemFn = syn::parse2(quote! {
            fn test(env: &mut jni::JNIEnv<'_>) {}
        })
        .unwrap();
        let result = validate_leading_params(&func, &JniMode::Regular);
        assert!(result.is_err());
        assert!(result.unwrap_err().contains("at least two parameters"));
    }

    #[test]
    fn test_validate_leading_params_wrong_first() {
        let func: ItemFn = syn::parse2(quote! {
            fn test(x: jint, clazz: jclass) {}
        })
        .unwrap();
        let result = validate_leading_params(&func, &JniMode::Regular);
        assert!(result.is_err());
        assert!(result.unwrap_err().contains("first parameter must be a JNIEnv"));
    }

    #[test]
    fn test_validate_leading_params_wrong_second() {
        let func: ItemFn = syn::parse2(quote! {
            fn test(env: &mut jni::JNIEnv<'_>, x: jint) {}
        })
        .unwrap();
        let result = validate_leading_params(&func, &JniMode::Regular);
        assert!(result.is_err());
        assert!(result.unwrap_err().contains("second parameter must be a jobject/jclass"));
    }

    #[test]
    fn test_validate_leading_params_valid() {
        let func: ItemFn = syn::parse2(quote! {
            fn test(env: &mut jni::JNIEnv<'_>, clazz: jclass, x: jint) {}
        })
        .unwrap();
        assert!(validate_leading_params(&func, &JniMode::Regular).is_ok());
    }

    #[test]
    fn test_validate_leading_params_critical_skips() {
        let func: ItemFn = syn::parse2(quote! {
            fn test(x: jlong) -> jint { 0 }
        })
        .unwrap();
        assert!(validate_leading_params(&func, &JniMode::Critical).is_ok());
    }

    // ---- generate_register_fn tests ----

    #[test]
    fn test_generate_register_fn_with_methods() {
        let methods = vec![JniMethod {
            shim_name: "__jni_nativeGetId".to_string(),
            java_name: "nativeGetId".to_string(),
            jni_sig: "(J)I".to_string(),
            cleaned_fn: quote! {},
            shim_fn: quote! {},
        }];
        let output = generate_register_fn("android/view/MotionEvent", &methods);
        let s = output.to_string();

        assert!(s.contains("pub fn register"));
        assert!(s.contains("find_class"));
        assert!(s.contains("android/view/MotionEvent"));
        assert!(s.contains("register_native_methods"));
        assert!(s.contains("\"nativeGetId\""));
        assert!(s.contains("(J)I"));
        assert!(s.contains("__jni_nativeGetId as * mut core :: ffi :: c_void"), "{s}");
        // Should NOT contain any init() calls
        assert!(!s.contains(":: init"));
    }

    #[test]
    fn test_generate_register_fn_empty() {
        let output = generate_register_fn("android/os/SystemClock", &[]);
        let s = output.to_string();

        assert!(s.contains("pub fn register"));
        assert!(s.contains("_env"));
        // No find_class / register_native_methods for empty module
        assert!(!s.contains("find_class"));
        assert!(!s.contains("register_native_methods"));
    }

    // ---- expand_jni_module tests ----

    #[test]
    fn test_expand_jni_module_passes_structs_through() {
        let attr = quote! { "android/view/MotionEvent" };
        let item = quote! {
            pub mod motion_event {
                pub struct SomeHelper {
                    pub x: i32,
                }

                #[jni_method(critical)]
                fn nativeGetId(ptr: jlong) -> jint { 0 }
            }
        };
        let output = expand_jni_module(attr, item);
        let s = output.to_string();

        // Struct should pass through unchanged
        assert!(s.contains("SomeHelper"));
        assert!(s.contains("pub x : i32"));
        // register() should exist and register the shim
        assert!(s.contains("pub fn register"));
        assert!(s.contains("__jni_nativeGetId"));
    }

    #[test]
    fn test_expand_jni_module_registers_verbatim_name() {
        let attr = quote! { "android/util/Log" };
        let item = quote! {
            pub mod log {
                #[jni_method]
                fn println_native(env: &mut jni::JNIEnv<'_>, clazz: jclass, priority: jint) -> jint {
                    0
                }
            }
        };
        let output = expand_jni_module(attr, item);
        let s = output.to_string();

        assert!(s.contains("\"println_native\""), "{s}");
        assert!(!s.contains("printlnNative"), "name must not be camelized: {s}");
        assert!(s.contains("__jni_println_native as * mut core :: ffi :: c_void"), "{s}");
    }

    #[test]
    fn test_expand_jni_module_name_override() {
        let attr = quote! { "android/util/Log" };
        let item = quote! {
            pub mod log {
                #[jni_method(fast, name = "logger_entry_max_payload_native")]
                fn max_payload(env: &mut jni::JNIEnv<'_>, clazz: jclass) -> jint {
                    0
                }
            }
        };
        let output = expand_jni_module(attr, item);
        let s = output.to_string();

        assert!(s.contains("\"logger_entry_max_payload_native\""), "{s}");
        assert!(s.contains("__jni_max_payload as * mut core :: ffi :: c_void"), "{s}");
    }

    #[test]
    fn test_expand_jni_module_no_java_class_handling() {
        let attr = quote! { "android/view/MotionEvent" };
        let item = quote! {
            pub mod motion_event {
                #[jni_method(critical)]
                fn nativeGetId(ptr: jlong) -> jint { 0 }
            }
        };
        let output = expand_jni_module(attr, item);
        let s = output.to_string();

        // register() should NOT contain any init() calls
        assert!(!s.contains(":: init"));
    }
}
