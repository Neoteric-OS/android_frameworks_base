//! Implementation of the `#[java_class("...")]` attribute macro.
//!
//! Transforms a struct with field/method attribute annotations into a cached
//! JNI ID lookup structure, replacing the C++ pattern of static `gFields` structs.

use proc_macro2::TokenStream;
use quote::{format_ident, quote};
use syn::{parse2, Attribute, Expr, ExprLit, Fields, ItemStruct, Lit, Meta, MetaList};

use crate::class::JavaClass;
use crate::sig;

/// Processes a `#[java_class("com/example/MyClass")]` annotated struct.
///
/// Given:
/// ```ignore
/// #[java_class("android/view/KeyEvent")]
/// struct KeyEventOffsets {
///     #[field("int")]
///     m_device_id: jfieldID,
///     #[static_field("int", name = "VERBOSE")]
///     verbose: jfieldID,
///     #[method("() -> void")]
///     recycle: jmethodID,
///     #[static_method("(int, long) -> android.view.KeyEvent")]
///     obtain: jmethodID,
/// }
/// ```
///
/// Generates:
/// - The struct with a `class: GlobalRef` field prepended
/// - `unsafe impl Send for T {}`
/// - `unsafe impl Sync for T {}`
/// - `static KEY_EVENT_OFFSETS_CACHE: std::sync::OnceLock<T>`
/// - `T::init(env)` — finds class, caches all field/method IDs
/// - `T::get() -> &'static T` — panics if not initialized
///
/// Returns a compile error token stream if the attribute is not a string literal
/// or the input is not a struct with named fields.
pub fn expand_java_class(attr: TokenStream, item: TokenStream) -> TokenStream {
    let java_class = match JavaClass::parse(attr, "java_class") {
        Ok(c) => c,
        Err(e) => return e.to_compile_error(),
    };
    let class_name = java_class.path();

    let input = match parse2::<ItemStruct>(item) {
        Ok(s) => s,
        Err(e) => return e.to_compile_error(),
    };

    let struct_name = &input.ident;
    let vis = &input.vis;

    let fields = match &input.fields {
        Fields::Named(named) => &named.named,
        _ => {
            return syn::Error::new_spanned(
                &input,
                "java_class requires a struct with named fields",
            )
            .to_compile_error();
        }
    };

    // Parse field attributes to determine what kind of ID each field caches
    let mut field_defs = Vec::new();
    let mut init_stmts = Vec::new();
    let mut errors: Option<syn::Error> = None;

    for field in fields {
        let field_name = field.ident.as_ref().unwrap();
        let field_ty = &field.ty;

        let parsed = match parse_jni_field_attr(&field.attrs, &field_name.to_string()) {
            Ok(parsed) => parsed,
            Err(err) => {
                match &mut errors {
                    Some(existing) => existing.combine(err),
                    None => errors = Some(err),
                }
                continue;
            }
        };

        if let Some(jni_field) = parsed {
            match jni_field {
                JniFieldKind::Field(java_name, sig) => {
                    field_defs.push(quote! { pub #field_name: #field_ty });
                    init_stmts.push(quote! {
                        #field_name: env.get_field_id(&cls, #java_name, #sig)
                            .expect(concat!("Failed to find field ", #java_name))
                            .into_raw()
                    });
                }
                JniFieldKind::StaticField(java_name, sig) => {
                    field_defs.push(quote! { pub #field_name: #field_ty });
                    init_stmts.push(quote! {
                        #field_name: env.get_static_field_id(&cls, #java_name, #sig)
                            .expect(concat!("Failed to find static field ", #java_name))
                            .into_raw()
                    });
                }
                JniFieldKind::Method(java_name, sig) => {
                    field_defs.push(quote! { pub #field_name: #field_ty });
                    init_stmts.push(quote! {
                        #field_name: env.get_method_id(&cls, #java_name, #sig)
                            .expect(concat!("Failed to find method ", #java_name))
                            .into_raw()
                    });
                }
                JniFieldKind::StaticMethod(java_name, sig) => {
                    field_defs.push(quote! { pub #field_name: #field_ty });
                    init_stmts.push(quote! {
                        #field_name: env.get_static_method_id(&cls, #java_name, #sig)
                            .expect(concat!("Failed to find static method ", #java_name))
                            .into_raw()
                    });
                }
            }
        } else {
            // No JNI attribute — keep as-is
            field_defs.push(quote! { pub #field_name: #field_ty });
            init_stmts.push(quote! {
                #field_name: unsafe { core::mem::zeroed() }
            });
        }
    }

    if let Some(errors) = errors {
        return errors.to_compile_error();
    }

    let cache_name = format_ident!("{}_CACHE", to_screaming_snake_case(&struct_name.to_string()));

    quote! {
        #vis struct #struct_name {
            /// Cached global reference to the Java class.
            pub class: jni::objects::GlobalRef,
            #(#field_defs,)*
        }

        unsafe impl Send for #struct_name {}
        unsafe impl Sync for #struct_name {}

        static #cache_name: std::sync::OnceLock<#struct_name> = std::sync::OnceLock::new();

        impl #struct_name {
            /// Initializes the cached JNI IDs by looking up the Java class and
            /// all its fields/methods.
            ///
            /// Must be called once during JNI_OnLoad or native method registration.
            /// Panics if the class or any field/method is not found.
            pub fn init(env: &mut jni::JNIEnv<'_>) {
                let cls = env.find_class(#class_name)
                    .expect(concat!("Failed to find class ", #class_name));
                let global_ref = env.new_global_ref(&cls)
                    .expect(concat!("Failed to create global ref for ", #class_name));
                #cache_name.get_or_init(|| {
                    #struct_name {
                        class: global_ref,
                        #(#init_stmts,)*
                    }
                });
            }

            /// Returns a reference to the cached IDs.
            ///
            /// Panics if `init` has not been called yet.
            pub fn get() -> &'static #struct_name {
                #cache_name.get().expect(
                    concat!(stringify!(#struct_name), " not initialized; call init() first")
                )
            }
        }
    }
}

/// The kind of JNI ID a field caches.
enum JniFieldKind {
    /// `#[field("int")]` or `#[field("int", name = "mDeviceId")]` — instance field ID
    Field(String, String),
    /// `#[static_field("int")]` or `#[static_field("int", name = "VERBOSE")]` — static field ID
    StaticField(String, String),
    /// `#[method("() -> void")]` or `#[method("() -> void", name = "recycle")]` — instance method ID
    Method(String, String),
    /// `#[static_method("(int) -> String")]` — static method ID
    StaticMethod(String, String),
}

/// Parses field attributes to find `#[field(...)]`, `#[static_field(...)]`,
/// `#[method(...)]`, or `#[static_method(...)]`.
///
/// Syntax:
/// - `#[field("int")]` — type DSL, Java name is the Rust field name verbatim
/// - `#[field("int", name = "mDeviceId")]` — type DSL, explicit name
/// - `#[static_field("int")]` — static field, name inferred
/// - `#[static_field("int", name = "VERBOSE")]` — static field, explicit name
/// - `#[method("() -> void")]` — method sig DSL, name inferred
/// - `#[method("() -> void", name = "recycle")]` — method sig DSL, explicit name
/// - `#[static_method("(int, long) -> void")]` — same for static methods
///
/// Returns `Ok(None)` for fields without any of these attributes. Any
/// malformed attribute — unparsable arguments or an invalid type/signature
/// spec — is an error naming the field and the problem, so mistakes surface
/// at compile time instead of silently degrading to an uninitialized field.
fn parse_jni_field_attr(
    attrs: &[Attribute],
    rust_field_name: &str,
) -> Result<Option<JniFieldKind>, syn::Error> {
    for attr in attrs {
        let path = attr.path();
        let Some(kind) = path.get_ident().map(ToString::to_string) else {
            continue;
        };
        let is_method = match kind.as_str() {
            "field" | "static_field" => false,
            "method" | "static_method" => true,
            _ => continue,
        };

        let attr_error = |message: String| {
            syn::Error::new_spanned(
                attr,
                format!("#[{}] on field `{}`: {}", kind, rust_field_name, message),
            )
        };

        let (spec, name_override) = parse_dsl_attr(attr).map_err(attr_error)?;
        let java_name = name_override.unwrap_or_else(|| rust_field_name.to_string());
        let jni_sig = if is_method {
            sig::friendly_method_sig(&spec)
        } else {
            sig::friendly_type_to_sig(&spec)
        }
        .map_err(attr_error)?;

        return Ok(Some(match kind.as_str() {
            "field" => JniFieldKind::Field(java_name, jni_sig),
            "static_field" => JniFieldKind::StaticField(java_name, jni_sig),
            "method" => JniFieldKind::Method(java_name, jni_sig),
            _ => JniFieldKind::StaticMethod(java_name, jni_sig),
        }));
    }
    Ok(None)
}

/// Parses an attribute with the DSL syntax:
/// - `#[field("int")]` → ("int", None)
/// - `#[field("int", name = "mDeviceId")]` → ("int", Some("mDeviceId"))
/// - `#[method("() -> void", name = "recycle")]` → ("() -> void", Some("recycle"))
///
/// Returns `(type_or_method_spec, optional_name_override)`, or an error
/// message describing the malformation.
fn parse_dsl_attr(attr: &Attribute) -> Result<(String, Option<String>), String> {
    const EXPECTED: &str =
        "expected #[attr(\"TYPE-OR-SIG\")] or #[attr(\"TYPE-OR-SIG\", name = \"javaName\")]";

    let Meta::List(MetaList { tokens, .. }) = &attr.meta else {
        return Err(format!("missing argument list; {}", EXPECTED));
    };

    use syn::punctuated::Punctuated;
    use syn::token::Comma;

    let args: Vec<Expr> =
        syn::parse::Parser::parse2(Punctuated::<Expr, Comma>::parse_terminated, tokens.clone())
            .map_err(|e| format!("unparsable arguments ({}); {}", e, EXPECTED))?
            .into_iter()
            .collect();

    if args.is_empty() {
        return Err(format!("missing the type/signature string; {}", EXPECTED));
    }
    if args.len() > 2 {
        return Err(format!("too many arguments; {}", EXPECTED));
    }

    // First arg must be a string literal (the type/method spec)
    let spec = match &args[0] {
        Expr::Lit(ExprLit { lit: Lit::Str(s), .. }) => s.value(),
        _ => {
            return Err(format!(
                "first argument must be a string literal type/signature; {}",
                EXPECTED
            ))
        }
    };

    // Optional second arg: `name = "..."`
    let mut name_override = None;
    if let Some(arg) = args.get(1) {
        let parsed_name = match arg {
            Expr::Assign(assign) => match (&*assign.left, &*assign.right) {
                (Expr::Path(path), Expr::Lit(ExprLit { lit: Lit::Str(s), .. }))
                    if path.path.is_ident("name") =>
                {
                    Some(s.value())
                }
                _ => None,
            },
            _ => None,
        };
        match parsed_name {
            Some(name) => name_override = Some(name),
            None => {
                return Err(format!("second argument must be `name = \"javaName\"`; {}", EXPECTED))
            }
        }
    }

    Ok((spec, name_override))
}

/// Converts a PascalCase or camelCase identifier to SCREAMING_SNAKE_CASE.
fn to_screaming_snake_case(s: &str) -> String {
    let mut result = String::new();
    for (i, ch) in s.chars().enumerate() {
        if ch.is_uppercase() && i > 0 {
            result.push('_');
        }
        result.push(ch.to_ascii_uppercase());
    }
    result
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_to_screaming_snake_case() {
        assert_eq!(to_screaming_snake_case("KeyEventOffsets"), "KEY_EVENT_OFFSETS");
        assert_eq!(to_screaming_snake_case("MotionEvent"), "MOTION_EVENT");
        assert_eq!(to_screaming_snake_case("Simple"), "SIMPLE");
        assert_eq!(to_screaming_snake_case("ABC"), "A_B_C");
    }

    #[test]
    fn test_static_field_codegen() {
        let attr = quote! { "android/util/Log" };
        let item = quote! {
            pub struct LogLevels {
                #[static_field("int", name = "VERBOSE")]
                verbose: jfieldID,
            }
        };
        let output = expand_java_class(attr, item);
        let code = output.to_string();
        assert!(code.contains("get_static_field_id"), "expected get_static_field_id in:\n{code}");
        assert!(code.contains("\"VERBOSE\""), "expected field name VERBOSE in:\n{code}");
    }

    #[test]
    fn test_static_field_name_inferred() {
        let attr = quote! { "android/util/Log" };
        let item = quote! {
            pub struct LogLevels {
                #[static_field("int")]
                verbose: jfieldID,
            }
        };
        let output = expand_java_class(attr, item);
        let code = output.to_string();
        assert!(code.contains("get_static_field_id"), "expected get_static_field_id in:\n{code}");
        // snake_to_camel_case("verbose") -> "verbose" (no underscores, stays as-is)
        assert!(code.contains("\"verbose\""), "expected inferred name 'verbose' in:\n{code}");
    }

    #[test]
    fn test_static_field_vs_instance_field() {
        let attr = quote! { "android/view/Foo" };
        let item = quote! {
            pub struct Foo {
                #[field("int")]
                instance_val: jfieldID,
                #[static_field("int", name = "STATIC_VAL")]
                static_val: jfieldID,
            }
        };
        let output = expand_java_class(attr, item);
        let code = output.to_string();
        // instance field uses get_field_id
        assert!(
            code.contains("get_field_id"),
            "expected get_field_id for instance field in:\n{code}"
        );
        // static field uses get_static_field_id
        assert!(
            code.contains("get_static_field_id"),
            "expected get_static_field_id for static field in:\n{code}"
        );
    }

    #[test]
    fn test_field_name_inferred_verbatim() {
        let attr = quote! { "android/view/KeyEvent" };
        let item = quote! {
            pub struct KeyEventInfo {
                #[field("int")]
                m_device_id: jfieldID,
            }
        };
        let output = expand_java_class(attr, item);
        let code = output.to_string();
        // The Rust field name is used verbatim; no camelization.
        assert!(code.contains("\"m_device_id\""), "expected verbatim name in:\n{code}");
        assert!(!code.contains("mDeviceId"), "name must not be camelized in:\n{code}");
    }

    #[test]
    fn test_malformed_field_type_is_compile_error() {
        let attr = quote! { "android/view/KeyEvent" };
        let item = quote! {
            pub struct KeyEventInfo {
                #[field("in t")]
                m_device_id: jfieldID,
            }
        };
        let code = expand_java_class(attr, item).to_string();
        assert!(code.contains("compile_error"), "{code}");
        assert!(code.contains("m_device_id"), "error must name the field: {code}");
        assert!(code.contains("invalid Java class name"), "{code}");
    }

    #[test]
    fn test_malformed_method_sig_is_compile_error() {
        let attr = quote! { "android/view/KeyEvent" };
        let item = quote! {
            pub struct KeyEventInfo {
                #[method("(int) void")]
                recycle: jmethodID,
            }
        };
        let code = expand_java_class(attr, item).to_string();
        assert!(code.contains("compile_error"), "{code}");
        assert!(code.contains("recycle"), "error must name the field: {code}");
        assert!(code.contains("must contain '->'"), "{code}");
    }

    #[test]
    fn test_bare_attribute_is_compile_error() {
        let attr = quote! { "android/view/KeyEvent" };
        let item = quote! {
            pub struct KeyEventInfo {
                #[field]
                m_device_id: jfieldID,
            }
        };
        let code = expand_java_class(attr, item).to_string();
        assert!(code.contains("compile_error"), "{code}");
        assert!(code.contains("m_device_id"), "{code}");
        assert!(code.contains("missing argument list"), "{code}");
    }

    #[test]
    fn test_non_string_spec_is_compile_error() {
        let attr = quote! { "android/view/KeyEvent" };
        let item = quote! {
            pub struct KeyEventInfo {
                #[field(42)]
                m_device_id: jfieldID,
            }
        };
        let code = expand_java_class(attr, item).to_string();
        assert!(code.contains("compile_error"), "{code}");
        assert!(code.contains("must be a string literal"), "{code}");
    }

    #[test]
    fn test_bad_name_argument_is_compile_error() {
        let attr = quote! { "android/view/KeyEvent" };
        let item = quote! {
            pub struct KeyEventInfo {
                #[field("int", java_name = "mDeviceId")]
                m_device_id: jfieldID,
            }
        };
        let code = expand_java_class(attr, item).to_string();
        assert!(code.contains("compile_error"), "{code}");
        assert!(code.contains("name = \\\"javaName\\\""), "{code}");
    }

    #[test]
    fn test_too_many_arguments_is_compile_error() {
        let attr = quote! { "android/view/KeyEvent" };
        let item = quote! {
            pub struct KeyEventInfo {
                #[static_method("() -> void", name = "obtain", extra = "boom")]
                obtain: jmethodID,
            }
        };
        let code = expand_java_class(attr, item).to_string();
        assert!(code.contains("compile_error"), "{code}");
        assert!(code.contains("too many arguments"), "{code}");
    }

    #[test]
    fn test_multiple_malformed_fields_all_reported() {
        let attr = quote! { "android/view/KeyEvent" };
        let item = quote! {
            pub struct KeyEventInfo {
                #[field("in t")]
                m_device_id: jfieldID,
                #[method("(int) void")]
                recycle: jmethodID,
            }
        };
        let code = expand_java_class(attr, item).to_string();
        assert!(code.contains("m_device_id"), "{code}");
        assert!(code.contains("recycle"), "{code}");
    }
}
