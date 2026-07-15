//! JNI type-name helpers for the `#[jni_module]` macro.
//!
//! `#[jni_module]` derives regular/fast method signatures through jni-rs's
//! `native_method!` (from the Rust parameter types), but still builds the
//! primitive-only descriptor for `@CriticalNative` methods here, via
//! [`primitive_sig`], and resolves object class names for the signature tokens
//! it hands to `native_method!` via [`resolve_class`]. [`extract_result_inner`]
//! unwraps `Result<T, _>` return types for the return-value bridging.

/// Returns the JNI signature character for a primitive JNI type, or `None` if
/// `ty` is not a primitive the macros accept.
///
/// `u8` is deliberately not mapped: in jni-sys 0.4 `jboolean` is an alias for
/// `bool`, so a bare `u8` no longer denotes any JNI type.
pub fn primitive_sig(ty: &str) -> Option<&'static str> {
    match ty {
        "jint" | "i32" => Some("I"),
        "jlong" | "i64" => Some("J"),
        "jfloat" | "f32" => Some("F"),
        "jdouble" | "f64" => Some("D"),
        "jboolean" | "bool" => Some("Z"),
        "jbyte" | "i8" => Some("B"),
        "jchar" | "u16" => Some("C"),
        "jshort" | "i16" => Some("S"),
        "()" => Some("V"),
        "void" => Some("V"),
        _ => None,
    }
}

/// Resolves a class name, converting dots to slashes and prepending
/// `module_package` if the class name is relative (contains no `/` or `.`).
pub fn resolve_class(class: &str, module_package: Option<&str>) -> String {
    let normalized = class.replace('.', "/");

    if normalized.contains('/') {
        // Already fully qualified
        normalized
    } else if let Some(pkg) = module_package {
        // Relative: prepend module package
        if pkg.is_empty() {
            normalized
        } else {
            format!("{}/{}", pkg, normalized)
        }
    } else {
        normalized
    }
}

/// Extracts the inner type `T` from `Result<T, ...>`.
///
/// Handles common patterns like:
/// - `Result<jint, JniError>`
/// - `Result<JString, JniError>`
/// - `Result<(), JniError>`
pub(crate) fn extract_result_inner(ty: &str) -> Option<String> {
    let ty = ty.trim();
    if !ty.starts_with("Result<") && !ty.starts_with("Result <") {
        return None;
    }

    // Find the opening '<' and matching '>'
    let start = ty.find('<')? + 1;
    let end = ty.rfind('>')?;
    let inner = &ty[start..end];

    // Split on ',' to get the Ok type (first part)
    // Need to handle nested generics, so count angle brackets
    let mut depth = 0;
    let mut split_pos = None;
    for (i, ch) in inner.char_indices() {
        match ch {
            '<' => depth += 1,
            '>' => depth -= 1,
            ',' if depth == 0 => {
                split_pos = Some(i);
                break;
            }
            _ => {}
        }
    }

    if let Some(pos) = split_pos {
        Some(inner[..pos].trim().to_string())
    } else {
        // No comma found — might be just `Result<T>`
        Some(inner.trim().to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // ---- primitive_sig tests ----

    #[test]
    fn test_primitive_sig_all() {
        assert_eq!(primitive_sig("jint"), Some("I"));
        assert_eq!(primitive_sig("jlong"), Some("J"));
        assert_eq!(primitive_sig("jfloat"), Some("F"));
        assert_eq!(primitive_sig("jdouble"), Some("D"));
        assert_eq!(primitive_sig("jboolean"), Some("Z"));
        assert_eq!(primitive_sig("jbyte"), Some("B"));
        assert_eq!(primitive_sig("jchar"), Some("C"));
        assert_eq!(primitive_sig("jshort"), Some("S"));
        assert_eq!(primitive_sig("()"), Some("V"));
        assert_eq!(primitive_sig("void"), Some("V"));
        assert_eq!(primitive_sig("String"), None);
        assert_eq!(primitive_sig("JObject"), None);
    }

    #[test]
    fn test_primitive_sig_rust_types() {
        assert_eq!(primitive_sig("i32"), Some("I"));
        assert_eq!(primitive_sig("i64"), Some("J"));
        assert_eq!(primitive_sig("f32"), Some("F"));
        assert_eq!(primitive_sig("f64"), Some("D"));
        // `u8` has no JNI mapping: jni-sys 0.4's `jboolean` is `bool`, not `u8`.
        assert_eq!(primitive_sig("u8"), None);
        assert_eq!(primitive_sig("i8"), Some("B"));
        assert_eq!(primitive_sig("u16"), Some("C"));
        assert_eq!(primitive_sig("i16"), Some("S"));
    }

    #[test]
    fn test_primitive_sig_bool() {
        assert_eq!(primitive_sig("bool"), Some("Z"));
    }

    // ---- resolve_class tests ----

    #[test]
    fn test_resolve_class_fully_qualified_slash() {
        assert_eq!(resolve_class("android/view/KeyEvent", None), "android/view/KeyEvent");
    }

    #[test]
    fn test_resolve_class_dotted() {
        assert_eq!(resolve_class("android.view.KeyEvent", None), "android/view/KeyEvent");
    }

    #[test]
    fn test_resolve_class_relative_with_package() {
        assert_eq!(
            resolve_class("MotionEvent$PointerCoords", Some("android/view")),
            "android/view/MotionEvent$PointerCoords"
        );
    }

    #[test]
    fn test_resolve_class_relative_no_package() {
        assert_eq!(resolve_class("MotionEvent$PointerCoords", None), "MotionEvent$PointerCoords");
    }

    #[test]
    fn test_resolve_class_relative_empty_package() {
        assert_eq!(resolve_class("KeyEvent", Some("")), "KeyEvent");
    }

    #[test]
    fn test_resolve_class_various_packages() {
        assert_eq!(resolve_class("KeyEvent", Some("android/view")), "android/view/KeyEvent");
        assert_eq!(resolve_class("SystemClock", Some("android/os")), "android/os/SystemClock");
        assert_eq!(resolve_class("Log", Some("android/util")), "android/util/Log");
    }

    // ---- extract_result_inner tests ----

    #[test]
    fn test_extract_result_inner_primitives() {
        assert_eq!(extract_result_inner("Result<jint, JniError>"), Some("jint".to_string()));
        assert_eq!(extract_result_inner("Result<jlong, JniError>"), Some("jlong".to_string()));
    }

    #[test]
    fn test_extract_result_inner_unit() {
        assert_eq!(extract_result_inner("Result<(), JniError>"), Some("()".to_string()));
    }

    #[test]
    fn test_extract_result_inner_object() {
        assert_eq!(extract_result_inner("Result<JString, JniError>"), Some("JString".to_string()));
        assert_eq!(extract_result_inner("Result<JObject, JniError>"), Some("JObject".to_string()));
    }

    #[test]
    fn test_extract_result_inner_not_result() {
        assert_eq!(extract_result_inner("jint"), None);
        assert_eq!(extract_result_inner("JString"), None);
        assert_eq!(extract_result_inner("()"), None);
    }
}
