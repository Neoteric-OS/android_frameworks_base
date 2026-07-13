//! Implementation of the `#[jni_module("...")]` attribute macro.
//!
//! Processes a module block, collecting `#[jni_method]` functions. For every
//! such function it generates an `unsafe extern "system"` shim whose parameter
//! and return types are raw `jni::sys` types — the exact ABI the JVM uses when
//! invoking a registered native — plus a `register()` function that registers
//! the shims with the JVM. The user's function keeps its Rust-friendly
//! signature (`&mut JNIEnv`, `&str`, `bool`, `Result`, ...) and is called by
//! the shim after argument conversion.

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
    /// `bool` from `jboolean` (`raw != 0`).
    Bool,
    /// `&str` / `Option<&str>` from `jstring`: extracted into an owned
    /// `String` (one heap allocation per call). Non-nullable throws
    /// NullPointerException on a null jstring.
    OwnedString { nullable: bool },
    /// `&JavaStr` / `Option<&JavaStr>` from `jstring`: borrowed directly from
    /// the JVM via GetStringUTFChars (zero copies; released on return).
    /// Non-nullable throws NullPointerException on a null jstring.
    BorrowedString { nullable: bool },
    /// A `jni::objects` wrapper built with `from_raw`, passed by value or
    /// by reference (e.g., `JString`, `&JByteArray`, `JObject`).
    Wrapped { wrapper: TokenStream, by_ref: bool },
}

/// Maps a user parameter type (textual form) to its bridging strategy.
///
/// Returns `None` for types outside the supported set; signature derivation
/// reports those as `Unknown JNI type` first.
fn param_bridge(ty: &str) -> Option<ParamBridge> {
    let value = |raw: TokenStream| Some(ParamBridge { raw_ty: raw, kind: ParamBridgeKind::Value });
    let wrapped = |raw: TokenStream, wrapper: TokenStream, by_ref: bool| {
        Some(ParamBridge { raw_ty: raw, kind: ParamBridgeKind::Wrapped { wrapper, by_ref } })
    };

    match ty {
        "jint" | "i32" => value(quote! { jni::sys::jint }),
        "jlong" | "i64" => value(quote! { jni::sys::jlong }),
        "jfloat" | "f32" => value(quote! { jni::sys::jfloat }),
        "jdouble" | "f64" => value(quote! { jni::sys::jdouble }),
        "jboolean" | "u8" => value(quote! { jni::sys::jboolean }),
        "jbyte" | "i8" => value(quote! { jni::sys::jbyte }),
        "jchar" | "u16" => value(quote! { jni::sys::jchar }),
        "jshort" | "i16" => value(quote! { jni::sys::jshort }),

        "bool" => {
            Some(ParamBridge { raw_ty: quote! { jni::sys::jboolean }, kind: ParamBridgeKind::Bool })
        }

        "&str" => Some(ParamBridge {
            raw_ty: quote! { jni::sys::jstring },
            kind: ParamBridgeKind::OwnedString { nullable: false },
        }),
        "Option<&str>" => Some(ParamBridge {
            raw_ty: quote! { jni::sys::jstring },
            kind: ParamBridgeKind::OwnedString { nullable: true },
        }),

        "&JavaStr" => Some(ParamBridge {
            raw_ty: quote! { jni::sys::jstring },
            kind: ParamBridgeKind::BorrowedString { nullable: false },
        }),
        "Option<&JavaStr>" => Some(ParamBridge {
            raw_ty: quote! { jni::sys::jstring },
            kind: ParamBridgeKind::BorrowedString { nullable: true },
        }),

        "jstring" => value(quote! { jni::sys::jstring }),
        "JString" => wrapped(quote! { jni::sys::jstring }, quote! { jni::objects::JString }, false),
        "&JString" => wrapped(quote! { jni::sys::jstring }, quote! { jni::objects::JString }, true),

        "jbyteArray" => value(quote! { jni::sys::jbyteArray }),
        "JByteArray" => {
            wrapped(quote! { jni::sys::jbyteArray }, quote! { jni::objects::JByteArray }, false)
        }
        "&JByteArray" => {
            wrapped(quote! { jni::sys::jbyteArray }, quote! { jni::objects::JByteArray }, true)
        }
        "jintArray" => value(quote! { jni::sys::jintArray }),
        "JIntArray" => {
            wrapped(quote! { jni::sys::jintArray }, quote! { jni::objects::JIntArray }, false)
        }
        "&JIntArray" => {
            wrapped(quote! { jni::sys::jintArray }, quote! { jni::objects::JIntArray }, true)
        }
        "jfloatArray" => value(quote! { jni::sys::jfloatArray }),
        "JFloatArray" => {
            wrapped(quote! { jni::sys::jfloatArray }, quote! { jni::objects::JFloatArray }, false)
        }
        "&JFloatArray" => {
            wrapped(quote! { jni::sys::jfloatArray }, quote! { jni::objects::JFloatArray }, true)
        }
        "jlongArray" => value(quote! { jni::sys::jlongArray }),
        "JLongArray" => {
            wrapped(quote! { jni::sys::jlongArray }, quote! { jni::objects::JLongArray }, false)
        }
        "&JLongArray" => {
            wrapped(quote! { jni::sys::jlongArray }, quote! { jni::objects::JLongArray }, true)
        }
        "jshortArray" => value(quote! { jni::sys::jshortArray }),
        "JShortArray" => {
            wrapped(quote! { jni::sys::jshortArray }, quote! { jni::objects::JShortArray }, false)
        }
        "&JShortArray" => {
            wrapped(quote! { jni::sys::jshortArray }, quote! { jni::objects::JShortArray }, true)
        }
        "jdoubleArray" => value(quote! { jni::sys::jdoubleArray }),
        "JDoubleArray" => {
            wrapped(quote! { jni::sys::jdoubleArray }, quote! { jni::objects::JDoubleArray }, false)
        }
        "&JDoubleArray" => {
            wrapped(quote! { jni::sys::jdoubleArray }, quote! { jni::objects::JDoubleArray }, true)
        }
        "jbooleanArray" => value(quote! { jni::sys::jbooleanArray }),
        "JBooleanArray" => wrapped(
            quote! { jni::sys::jbooleanArray },
            quote! { jni::objects::JBooleanArray },
            false,
        ),
        "&JBooleanArray" => wrapped(
            quote! { jni::sys::jbooleanArray },
            quote! { jni::objects::JBooleanArray },
            true,
        ),
        "jcharArray" => value(quote! { jni::sys::jcharArray }),
        "JCharArray" => {
            wrapped(quote! { jni::sys::jcharArray }, quote! { jni::objects::JCharArray }, false)
        }
        "&JCharArray" => {
            wrapped(quote! { jni::sys::jcharArray }, quote! { jni::objects::JCharArray }, true)
        }

        "jobject" => value(quote! { jni::sys::jobject }),
        "JObject" => wrapped(quote! { jni::sys::jobject }, quote! { jni::objects::JObject }, false),
        "&JObject" => wrapped(quote! { jni::sys::jobject }, quote! { jni::objects::JObject }, true),
        "jobjectArray" => value(quote! { jni::sys::jobjectArray }),
        "JObjectArray" => {
            wrapped(quote! { jni::sys::jobjectArray }, quote! { jni::objects::JObjectArray }, false)
        }
        "&JObjectArray" => {
            wrapped(quote! { jni::sys::jobjectArray }, quote! { jni::objects::JObjectArray }, true)
        }

        _ => None,
    }
}

/// How the user's return value is converted to the raw type the shim returns.
enum ReturnBridge {
    /// No return value (`()` / no declared return).
    Void,
    /// The declared type is ABI-identical to the raw type; pass it through.
    /// `pointer` records whether the raw type is a pointer (for zero values).
    Value { raw_ty: TokenStream, pointer: bool },
    /// `bool` → `jboolean`.
    Bool,
    /// `String` → `jstring` via `env.new_string`.
    StringToJstring,
    /// An owned `jni::objects` wrapper → raw pointer via `.into_raw()`.
    WrappedValue { raw_ty: TokenStream },
    /// A borrowed `jni::objects` wrapper → raw pointer via `.as_raw()`.
    WrappedRef { raw_ty: TokenStream },
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

        if ty == "()" || ty.is_empty() {
            return Ok((ReturnBridge::Void, false));
        }
        if ty == "bool" {
            return Ok((ReturnBridge::Bool, false));
        }
        if ty == "String" {
            return Ok((ReturnBridge::StringToJstring, false));
        }

        let bridge = param_bridge(ty).ok_or_else(|| format!("Unknown JNI type: '{}'", ty))?;
        let pointer = is_pointer_raw_type(ty);
        let ret = match bridge.kind {
            ParamBridgeKind::Value => ReturnBridge::Value { raw_ty: bridge.raw_ty, pointer },
            ParamBridgeKind::Wrapped { by_ref: false, .. } => {
                ReturnBridge::WrappedValue { raw_ty: bridge.raw_ty }
            }
            ParamBridgeKind::Wrapped { by_ref: true, .. } => {
                ReturnBridge::WrappedRef { raw_ty: bridge.raw_ty }
            }
            _ => return Err(format!("Unsupported JNI return type: '{}'", ty)),
        };
        Ok((ret, false))
    }

    /// The shim's return type tokens (empty for void).
    fn output_tokens(&self) -> TokenStream {
        match self {
            ReturnBridge::Void => quote! {},
            ReturnBridge::Value { raw_ty, .. }
            | ReturnBridge::WrappedValue { raw_ty }
            | ReturnBridge::WrappedRef { raw_ty } => quote! { -> #raw_ty },
            ReturnBridge::Bool => quote! { -> jni::sys::jboolean },
            ReturnBridge::StringToJstring => quote! { -> jni::sys::jstring },
        }
    }

    /// True if the shim returns a raw pointer type.
    fn is_pointer(&self) -> bool {
        match self {
            ReturnBridge::Void | ReturnBridge::Bool => false,
            ReturnBridge::Value { pointer, .. } => *pointer,
            ReturnBridge::StringToJstring
            | ReturnBridge::WrappedValue { .. }
            | ReturnBridge::WrappedRef { .. } => true,
        }
    }

    /// The JNI zero value for this return type, as an expression.
    /// Empty for void (a block ending in a statement already yields `()`).
    fn zero_expr(&self) -> TokenStream {
        if self.is_pointer() {
            quote! { std::ptr::null_mut() }
        } else if matches!(self, ReturnBridge::Void) {
            quote! {}
        } else {
            quote! { Default::default() }
        }
    }

    /// A `return <zero value>` expression usable from failure paths.
    fn zero_return_expr(&self) -> TokenStream {
        let zero = self.zero_expr();
        quote! { return #zero }
    }

    /// Converts the user's return value (bound to `value`) to the raw type.
    fn convert_expr(&self, value: &syn::Ident) -> TokenStream {
        match self {
            ReturnBridge::Void | ReturnBridge::Value { .. } => quote! { #value },
            ReturnBridge::Bool => quote! { #value as jni::sys::jboolean },
            ReturnBridge::StringToJstring => quote! {
                match env.new_string(&#value) {
                    Ok(s) => s.into_raw(),
                    Err(_) => std::ptr::null_mut(),
                }
            },
            ReturnBridge::WrappedValue { .. } => quote! { #value.into_raw() },
            ReturnBridge::WrappedRef { .. } => quote! { #value.as_raw() },
        }
    }
}

/// True if the given user type maps to a raw pointer JNI type.
fn is_pointer_raw_type(ty: &str) -> bool {
    !matches!(
        ty,
        "jint"
            | "i32"
            | "jlong"
            | "i64"
            | "jfloat"
            | "f32"
            | "jdouble"
            | "f64"
            | "jboolean"
            | "u8"
            | "jbyte"
            | "i8"
            | "jchar"
            | "u16"
            | "jshort"
            | "i16"
    )
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
        let returns_attr = find_returns_attr(&func.attrs);

        let jni_sig = derive_method_signature(func, &mode, module_package, returns_attr.as_deref())
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
/// #[jni_module("android/util/Log")]
/// mod log {
///     #[jni_method]
///     fn println_native(env: &mut JNIEnv, clazz: jclass, tag: &str, level: i32) -> i32 {
///         0
///     }
/// }
///
/// // Output:
/// mod log {
///     unsafe extern "system" fn __jni_println_native(
///         env: *mut jni::sys::JNIEnv,
///         this: jni::sys::jobject,
///         __arg0: jni::sys::jstring,
///         __arg1: jni::sys::jint,
///     ) -> jni::sys::jint {
///         let mut env = unsafe { jni::JNIEnv::from_raw(env) }.expect(...);
///         // ... jstring extraction, null check ...
///         let __result = println_native(&mut env, this, tag, __arg1);
///         __result
///     }
///     fn println_native(env: &mut JNIEnv, clazz: jclass, tag: &str, level: i32) -> i32 {
///         0
///     }
///
///     pub fn register(env: &mut jni::JNIEnv<'_>) { // generated
///         let class = env.find_class("android/util/Log").expect(...);
///         let methods = [
///             NativeMethod { name: "println_native", sig: "(Ljava/lang/String;I)I",
///                            fn_ptr: __jni_println_native as *mut c_void },
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
    returns_attr: Option<&str>,
) -> Result<String, String> {
    let mut params: Vec<(&str, Option<&str>)> = Vec::new();

    let inputs: Vec<&FnArg> = func.sig.inputs.iter().collect();
    let skip = env_this_skip_count(mode);

    // We need to hold the type strings so we can reference them
    let type_strings: Vec<(String, Option<String>)> = inputs[skip..]
        .iter()
        .map(|arg| {
            if let FnArg::Typed(pat_type) = arg {
                let ty = type_to_string(&pat_type.ty);
                let class = find_class_attr_on_pat(pat_type);
                (ty, class)
            } else {
                ("()".to_string(), None)
            }
        })
        .collect();

    for (ty, class) in &type_strings {
        params.push((ty.as_str(), class.as_deref()));
    }

    let return_type = return_type_str(&func.sig.output);

    sig::build_signature(&params, &return_type, returns_attr, module_package)
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
/// `__arg1`, ...); `ret` supplies the zero value for failure paths.
fn bridge_user_param(
    pat_type: &PatType,
    index: usize,
    ret: &ReturnBridge,
) -> Result<BridgedParam, String> {
    let pat = &pat_type.pat;
    let ty = type_to_string(&pat_type.ty);
    let raw_ident = format_ident!("__arg{}", index);
    let bridge = param_bridge(&ty).ok_or_else(|| format!("Unknown JNI type: '{}'", ty))?;
    let raw_ty = &bridge.raw_ty;
    let shim_param = quote! { #raw_ident: #raw_ty };
    let zero_return = ret.zero_return_expr();

    Ok(match bridge.kind {
        ParamBridgeKind::Value => {
            BridgedParam { shim_param, prelude: None, call_arg: quote! { #raw_ident } }
        }
        ParamBridgeKind::Bool => BridgedParam {
            shim_param,
            prelude: Some(quote! { let #pat: bool = #raw_ident != 0; }),
            call_arg: quote! { #pat },
        },
        ParamBridgeKind::OwnedString { nullable: false } => {
            let string_ident = format_ident!("__arg{}_string", index);
            let jstr_ident = format_ident!("__arg{}_jstring", index);
            let npe_msg = format!("{} must not be null", quote! { #pat });
            BridgedParam {
                shim_param,
                prelude: Some(quote! {
                    if #raw_ident.is_null() {
                        let _ = env.throw_new("java/lang/NullPointerException", #npe_msg);
                        #zero_return;
                    }
                    let #jstr_ident = unsafe { jni::objects::JString::from_raw(#raw_ident) };
                    let #string_ident: String =
                        match unsafe { env.get_string_unchecked(&#jstr_ident) } {
                            Ok(s) => s.into(),
                            Err(_) => #zero_return,
                        };
                    let #pat: &str = &#string_ident;
                }),
                call_arg: quote! { #pat },
            }
        }
        ParamBridgeKind::OwnedString { nullable: true } => {
            let string_ident = format_ident!("__arg{}_string", index);
            let jstr_ident = format_ident!("__arg{}_jstring", index);
            let javastr_ident = format_ident!("__arg{}_javastr", index);
            BridgedParam {
                shim_param,
                prelude: Some(quote! {
                    let #string_ident: Option<String> = if #raw_ident.is_null() {
                        None
                    } else {
                        let #jstr_ident = unsafe { jni::objects::JString::from_raw(#raw_ident) };
                        let #javastr_ident =
                            match unsafe { env.get_string_unchecked(&#jstr_ident) } {
                                Ok(s) => s,
                                Err(_) => #zero_return,
                            };
                        Some(#javastr_ident.into())
                    };
                    let #pat: Option<&str> = #string_ident.as_deref();
                }),
                call_arg: quote! { #pat },
            }
        }
        ParamBridgeKind::BorrowedString { nullable: false } => {
            let jstr_ident = format_ident!("__arg{}_jstring", index);
            let javastr_ident = format_ident!("__arg{}_javastr", index);
            let npe_msg = format!("{} must not be null", quote! { #pat });
            BridgedParam {
                shim_param,
                prelude: Some(quote! {
                    if #raw_ident.is_null() {
                        let _ = env.throw_new("java/lang/NullPointerException", #npe_msg);
                        #zero_return;
                    }
                    let #jstr_ident = unsafe { jni::objects::JString::from_raw(#raw_ident) };
                    let #javastr_ident =
                        match unsafe { env.get_string_unchecked(&#jstr_ident) } {
                            Ok(s) => s,
                            Err(_) => #zero_return,
                        };
                    let #pat: &jni::strings::JavaStr = &#javastr_ident;
                }),
                call_arg: quote! { #pat },
            }
        }
        ParamBridgeKind::BorrowedString { nullable: true } => {
            let jstr_ident = format_ident!("__arg{}_jstring", index);
            let javastr_ident = format_ident!("__arg{}_javastr", index);
            BridgedParam {
                shim_param,
                prelude: Some(quote! {
                    let #jstr_ident = unsafe { jni::objects::JString::from_raw(#raw_ident) };
                    let #javastr_ident = if #raw_ident.is_null() {
                        None
                    } else {
                        match unsafe { env.get_string_unchecked(&#jstr_ident) } {
                            Ok(s) => Some(s),
                            Err(_) => #zero_return,
                        }
                    };
                    let #pat: Option<&jni::strings::JavaStr> = #javastr_ident.as_ref();
                }),
                call_arg: quote! { #pat },
            }
        }
        ParamBridgeKind::Wrapped { wrapper, by_ref: false } => BridgedParam {
            shim_param,
            prelude: Some(quote! {
                let #pat = unsafe { #wrapper::from_raw(#raw_ident) };
            }),
            call_arg: quote! { #pat },
        },
        ParamBridgeKind::Wrapped { wrapper, by_ref: true } => {
            let obj_ident = format_ident!("__arg{}_obj", index);
            BridgedParam {
                shim_param,
                prelude: Some(quote! {
                    let #obj_ident = unsafe { #wrapper::from_raw(#raw_ident) };
                }),
                call_arg: quote! { &#obj_ident },
            }
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
/// arguments. The shim rebuilds a safe `jni::JNIEnv`, converts each argument
/// to the user's declared type, calls the user's function, and converts the
/// return value back. `Err` values of `Result`-returning functions become
/// pending Java exceptions plus the JNI zero value.
///
/// # Example
///
/// Given `fn isTouchEvent(env: &mut JNIEnv, clazz: jclass, ptr: jlong) -> bool { ... }`,
/// generates:
/// ```text
/// unsafe extern "system" fn __jni_isTouchEvent(
///     env: *mut jni::sys::JNIEnv,
///     this: jni::sys::jobject,
///     __arg0: jni::sys::jlong,
/// ) -> jni::sys::jboolean {
///     let mut env = unsafe { jni::JNIEnv::from_raw(env) }.expect(...);
///     let __result = isTouchEvent(&mut env, this, __arg0);
///     __result as jni::sys::jboolean
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

    let (ret, is_result) = ReturnBridge::parse(&return_type_str(&func.sig.output))?;

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
            let bridged = bridge_user_param(pat_type, index, &ret)?;
            shim_params.push(bridged.shim_param);
            if let Some(prelude) = bridged.prelude {
                preludes.push(prelude);
            }
            call_args.push(bridged.call_arg);
        }
    }

    let output = ret.output_tokens();

    let body = if is_result {
        let ok_ident = format_ident!("__ok");
        let convert = ret.convert_expr(&ok_ident);
        let zero = ret.zero_expr();
        quote! {
            let __result = #user_fn_name(#(#call_args),*);
            match __result {
                Ok(#ok_ident) => #convert,
                Err(__err) => {
                    jni_support::throw_unless_pending(&mut env, __err);
                    #zero
                }
            }
        }
    } else if matches!(ret, ReturnBridge::Void) {
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
    fn test_param_bridge_bool() {
        let bridge = param_bridge("bool").unwrap();
        assert!(matches!(bridge.kind, ParamBridgeKind::Bool));
        assert_eq!(bridge.raw_ty.to_string(), quote! { jni::sys::jboolean }.to_string());
    }

    #[test]
    fn test_param_bridge_str_ref() {
        let bridge = param_bridge("&str").unwrap();
        assert!(matches!(bridge.kind, ParamBridgeKind::OwnedString { nullable: false }));
    }

    #[test]
    fn test_param_bridge_option_str_ref() {
        let bridge = param_bridge("Option<&str>").unwrap();
        assert!(matches!(bridge.kind, ParamBridgeKind::OwnedString { nullable: true }));
    }

    #[test]
    fn test_param_bridge_wrapper_objects() {
        let bridge = param_bridge("JString").unwrap();
        assert!(matches!(bridge.kind, ParamBridgeKind::Wrapped { by_ref: false, .. }));
        let bridge = param_bridge("&JByteArray").unwrap();
        assert!(matches!(bridge.kind, ParamBridgeKind::Wrapped { by_ref: true, .. }));
    }

    #[test]
    fn test_param_bridge_unknown() {
        assert!(param_bridge("FooBar").is_none());
        assert!(param_bridge("String").is_none());
    }

    // ---- ReturnBridge tests ----

    #[test]
    fn test_return_bridge_void() {
        let (ret, is_result) = ReturnBridge::parse("()").unwrap();
        assert!(matches!(ret, ReturnBridge::Void));
        assert!(!is_result);
        assert!(ret.output_tokens().is_empty());
    }

    #[test]
    fn test_return_bridge_primitive() {
        let (ret, is_result) = ReturnBridge::parse("jint").unwrap();
        assert!(matches!(ret, ReturnBridge::Value { pointer: false, .. }));
        assert!(!is_result);
        assert_eq!(ret.output_tokens().to_string(), quote! { -> jni::sys::jint }.to_string());
    }

    #[test]
    fn test_return_bridge_pointer() {
        let (ret, _) = ReturnBridge::parse("jstring").unwrap();
        assert!(ret.is_pointer());
        assert_eq!(ret.zero_expr().to_string(), quote! { std::ptr::null_mut() }.to_string());
    }

    #[test]
    fn test_return_bridge_result() {
        let (ret, is_result) = ReturnBridge::parse("Result<jint, JniError>").unwrap();
        assert!(is_result);
        assert!(matches!(ret, ReturnBridge::Value { pointer: false, .. }));

        let (ret, is_result) = ReturnBridge::parse("Result<(), JniError>").unwrap();
        assert!(is_result);
        assert!(matches!(ret, ReturnBridge::Void));
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
    fn test_shim_bool_return() {
        let func: ItemFn = syn::parse2(quote! {
            fn nativeIsTouchEvent(env: &mut jni::JNIEnv<'_>, clazz: jclass, ptr: jlong) -> bool {
                true
            }
        })
        .unwrap();
        let shim = generate_shim(&func, &JniMode::Regular, "nativeIsTouchEvent").unwrap();
        let s = shim.to_string();
        assert!(s.contains("__jni_nativeIsTouchEvent"), "{s}");
        assert!(s.contains("-> jni :: sys :: jboolean"), "{s}");
        assert!(s.contains("as jni :: sys :: jboolean"), "{s}");
    }

    #[test]
    fn test_shim_critical_bool_param_and_return() {
        let func: ItemFn = syn::parse2(quote! {
            fn nativeCopy(dest: jlong, keep_history: bool) -> bool {
                true
            }
        })
        .unwrap();
        let shim = generate_shim(&func, &JniMode::Critical, "nativeCopy").unwrap();
        let s = shim.to_string();
        assert!(s.contains("__arg1 : jni :: sys :: jboolean"), "{s}");
        assert!(s.contains("!= 0"), "{s}");
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
    fn test_shim_str_param() {
        let func: ItemFn = syn::parse2(quote! {
            fn isLoggable(env: &mut jni::JNIEnv<'_>, clazz: jclass, tag: &str, level: i32) -> bool {
                true
            }
        })
        .unwrap();
        let shim = generate_shim(&func, &JniMode::Regular, "isLoggable").unwrap();
        let s = shim.to_string();
        assert!(s.contains("__jni_isLoggable"), "{s}");
        assert!(s.contains("__arg0 : jni :: sys :: jstring"), "{s}");
        assert!(s.contains("NullPointerException"), "{s}");
        assert!(s.contains("tag must not be null"), "{s}");
        assert!(s.contains("get_string_unchecked"), "{s}");
    }

    #[test]
    fn test_shim_option_str_param() {
        let func: ItemFn = syn::parse2(quote! {
            fn test(env: &mut jni::JNIEnv<'_>, clazz: jclass, tag: Option<&str>) {
            }
        })
        .unwrap();
        let shim = generate_shim(&func, &JniMode::Regular, "test").unwrap();
        let s = shim.to_string();
        assert!(s.contains("None"), "{s}");
        assert!(s.contains("Some"), "{s}");
        assert!(s.contains("as_deref"), "{s}");
        assert!(!s.contains("NullPointerException"), "nullable must not throw NPE: {s}");
    }

    #[test]
    fn test_param_bridge_java_str() {
        let bridge = param_bridge("&JavaStr").unwrap();
        assert!(matches!(bridge.kind, ParamBridgeKind::BorrowedString { nullable: false }));
        assert_eq!(bridge.raw_ty.to_string(), quote! { jni::sys::jstring }.to_string());
        let bridge = param_bridge("Option<&JavaStr>").unwrap();
        assert!(matches!(bridge.kind, ParamBridgeKind::BorrowedString { nullable: true }));
    }

    #[test]
    fn test_shim_java_str_param_borrows_without_allocating() {
        let func: ItemFn = syn::parse2(quote! {
            fn println_native(env: &mut jni::JNIEnv<'_>, clazz: jclass, msg: &JavaStr) -> jint {
                0
            }
        })
        .unwrap();
        let shim = generate_shim(&func, &JniMode::Regular, "println_native").unwrap();
        let s = shim.to_string();
        assert!(s.contains("__arg0 : jni :: sys :: jstring"), "{s}");
        assert!(s.contains("get_string_unchecked"), "{s}");
        assert!(s.contains("jni :: strings :: JavaStr"), "{s}");
        assert!(s.contains("NullPointerException"), "{s}");
        assert!(s.contains("msg must not be null"), "{s}");
        // No owned-String extraction: the JavaStr is passed by reference.
        assert!(!s.contains(": String"), "{s}");
        assert!(!s.contains("into ()"), "{s}");
    }

    #[test]
    fn test_shim_option_java_str_param() {
        let func: ItemFn = syn::parse2(quote! {
            fn isLoggable(
                env: &mut jni::JNIEnv<'_>,
                clazz: jclass,
                tag: Option<&JavaStr>,
            ) -> bool {
                true
            }
        })
        .unwrap();
        let shim = generate_shim(&func, &JniMode::Regular, "isLoggable").unwrap();
        let s = shim.to_string();
        assert!(s.contains("None"), "{s}");
        assert!(s.contains("Some"), "{s}");
        assert!(s.contains("Option < & jni :: strings :: JavaStr >"), "{s}");
        assert!(s.contains("as_ref"), "{s}");
        assert!(!s.contains("NullPointerException"), "nullable must not throw NPE: {s}");
        assert!(!s.contains("as_deref"), "borrowed conversion must not build Strings: {s}");
    }

    #[test]
    fn test_shim_critical_java_str_error() {
        let func: ItemFn = syn::parse2(quote! {
            fn test(tag: &JavaStr) -> jint {
                0
            }
        })
        .unwrap();
        let result = generate_shim(&func, &JniMode::Critical, "test");
        assert!(result.is_err());
        let msg = result.unwrap_err();
        assert!(msg.contains("@CriticalNative"), "{msg}");
        assert!(msg.contains("&JavaStr"), "{msg}");
    }

    #[test]
    fn test_expand_jni_module_java_str_signature() {
        let attr = quote! { "android/util/Log" };
        let item = quote! {
            pub mod log {
                #[jni_method]
                fn println_native(
                    env: &mut jni::JNIEnv<'_>,
                    clazz: jclass,
                    msg: &JavaStr,
                ) -> jint {
                    0
                }
            }
        };
        let output = expand_jni_module(attr, item);
        let s = output.to_string();
        assert!(s.contains("(Ljava/lang/String;)I"), "{s}");
    }

    #[test]
    fn test_shim_str_param_with_pointer_return_uses_null_zero() {
        // A &str failure path in a jstring-returning method must return null,
        // not Default::default() (raw pointers do not implement Default).
        let func: ItemFn = syn::parse2(quote! {
            fn test(env: &mut jni::JNIEnv<'_>, clazz: jclass, tag: &str) -> jstring {
                std::ptr::null_mut()
            }
        })
        .unwrap();
        let shim = generate_shim(&func, &JniMode::Regular, "test").unwrap();
        let s = shim.to_string();
        assert!(s.contains("return std :: ptr :: null_mut ()"), "{s}");
    }

    #[test]
    fn test_shim_string_return() {
        let func: ItemFn = syn::parse2(quote! {
            fn test(env: &mut jni::JNIEnv<'_>, clazz: jclass, code: jint) -> String {
                String::new()
            }
        })
        .unwrap();
        let shim = generate_shim(&func, &JniMode::Regular, "test").unwrap();
        let s = shim.to_string();
        assert!(s.contains("new_string"), "{s}");
        assert!(s.contains("into_raw"), "{s}");
        assert!(s.contains("null_mut"), "{s}");
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

    #[test]
    fn test_shim_result_primitive_return() {
        let func: ItemFn = syn::parse2(quote! {
            fn nativeGetCount(env: &mut jni::JNIEnv<'_>, clazz: jclass, ptr: jlong) -> Result<jint, JniError> {
                Ok(0)
            }
        })
        .unwrap();
        let shim = generate_shim(&func, &JniMode::Regular, "nativeGetCount").unwrap();
        let s = shim.to_string();
        assert!(s.contains("-> jni :: sys :: jint"), "{s}");
        assert!(s.contains("Ok (__ok) => __ok"), "{s}");
        assert!(s.contains("throw_unless_pending"), "{s}");
        assert!(s.contains("Default :: default ()"), "{s}");
    }

    #[test]
    fn test_shim_result_pointer_return() {
        let func: ItemFn = syn::parse2(quote! {
            fn nativeGetName(env: &mut jni::JNIEnv<'_>, clazz: jclass, ptr: jlong) -> Result<jstring, JniError> {
                Ok(std::ptr::null_mut())
            }
        })
        .unwrap();
        let shim = generate_shim(&func, &JniMode::Regular, "nativeGetName").unwrap();
        let s = shim.to_string();
        assert!(s.contains("-> jni :: sys :: jstring"), "{s}");
        assert!(s.contains("throw_unless_pending"), "{s}");
        assert!(s.contains("std :: ptr :: null_mut ()"), "{s}");
    }

    #[test]
    fn test_shim_result_void_return() {
        let func: ItemFn = syn::parse2(quote! {
            fn nativeApply(env: &mut jni::JNIEnv<'_>, clazz: jclass, ptr: jlong) -> Result<(), JniError> {
                Ok(())
            }
        })
        .unwrap();
        let shim = generate_shim(&func, &JniMode::Regular, "nativeApply").unwrap();
        let s = shim.to_string();
        assert!(s.contains("throw_unless_pending"), "{s}");
        assert!(!s.contains("->"), "Result<(), _> shim must return void: {s}");
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
    fn test_expand_jni_module_overloads_share_a_registered_name() {
        // android.os.SystemProperties overloads native names (native_get
        // exists as (String,String)->String and (J)->String); RegisterNatives
        // distinguishes them by signature, so two methods may register under
        // one name as long as their Rust names — which key the shims — differ.
        let attr = quote! { "android/os/SystemProperties" };
        let item = quote! {
            pub mod system_properties {
                #[jni_method(fast, name = "native_get")]
                fn native_get_string(
                    env: &mut jni::JNIEnv<'_>,
                    clazz: jclass,
                    key: &JavaStr,
                    def: jstring,
                ) -> jstring {
                    def
                }

                #[jni_method(fast, name = "native_get")]
                fn native_get_string_handle(
                    env: &mut jni::JNIEnv<'_>,
                    clazz: jclass,
                    handle: i64,
                ) -> jstring {
                    std::ptr::null_mut()
                }
            }
        };
        let output = expand_jni_module(attr, item);
        let s = output.to_string();

        assert_eq!(s.matches("\"native_get\"").count(), 2, "{s}");
        assert!(s.contains("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"), "{s}");
        assert!(s.contains("\"(J)Ljava/lang/String;\""), "{s}");
        assert!(s.contains("__jni_native_get_string as * mut core :: ffi :: c_void"), "{s}");
        assert!(s.contains("__jni_native_get_string_handle as * mut core :: ffi :: c_void"), "{s}");
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
