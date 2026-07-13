//! JNI signature derivation from Rust types.
//!
//! Maps Rust JNI types (jint, jlong, JString, JObject, etc.) to their JNI
//! signature characters/strings. Used by `#[jni_module]` to auto-derive
//! method signatures.

/// Returns the JNI signature character for a primitive JNI type.
pub fn primitive_sig(ty: &str) -> Option<&'static str> {
    match ty {
        "jint" | "i32" => Some("I"),
        "jlong" | "i64" => Some("J"),
        "jfloat" | "f32" => Some("F"),
        "jdouble" | "f64" => Some("D"),
        "jboolean" | "u8" | "bool" => Some("Z"),
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

/// Converts a single parameter's Rust type to its JNI signature string.
///
/// - Primitive types map directly (jint → "I", etc.)
/// - `JString` / `jstring` → "Ljava/lang/String;"
/// - `JByteArray` → "[B", `JIntArray` → "[I", etc.
/// - `JObject` requires `class_attr` (e.g., `#[class = "android/view/KeyEvent"]`)
/// - `JObjectArray` requires `class_attr` for the element type
///
/// `module_package` is used for relative class resolution.
///
/// # Examples
///
/// ```text
/// param_to_jni_sig("jint", None, None)                                → Ok("I")
/// param_to_jni_sig("JString", None, None)                             → Ok("Ljava/lang/String;")
/// param_to_jni_sig("&str", None, None)                                → Ok("Ljava/lang/String;")
/// param_to_jni_sig("JByteArray", None, None)                          → Ok("[B")
/// param_to_jni_sig("JObject", Some("android/view/KeyEvent"), None)    → Ok("Landroid/view/KeyEvent;")
/// param_to_jni_sig("JObject", Some("KeyEvent"), Some("android/view")) → Ok("Landroid/view/KeyEvent;")
/// param_to_jni_sig("JObject", None, None)                             → Err("JObject parameter requires #[class = \"...\"] ...")
/// param_to_jni_sig("FooBar", None, None)                              → Err("Unknown JNI type: 'FooBar'")
/// ```
pub fn param_to_jni_sig(
    rust_type: &str,
    class_attr: Option<&str>,
    module_package: Option<&str>,
) -> Result<String, String> {
    let ty = rust_type.trim();

    // Check primitives first
    if let Some(sig) = primitive_sig(ty) {
        return Ok(sig.to_string());
    }

    match ty {
        // String types, including the Rust-friendly forms: &str / Option<&str>
        // (owned, allocating) and &JavaStr / Option<&JavaStr> (borrowed,
        // zero-allocation).
        "JString" | "jstring" | "&JString" | "&str" | "Option<&str>" | "&JavaStr"
        | "Option<&JavaStr>" => Ok("Ljava/lang/String;".to_string()),

        // Typed arrays
        "JByteArray" | "jbyteArray" | "&JByteArray" => Ok("[B".to_string()),
        "JIntArray" | "jintArray" | "&JIntArray" => Ok("[I".to_string()),
        "JFloatArray" | "jfloatArray" | "&JFloatArray" => Ok("[F".to_string()),
        "JLongArray" | "jlongArray" | "&JLongArray" => Ok("[J".to_string()),
        "JShortArray" | "jshortArray" | "&JShortArray" => Ok("[S".to_string()),
        "JDoubleArray" | "jdoubleArray" | "&JDoubleArray" => Ok("[D".to_string()),
        "JBooleanArray" | "jbooleanArray" | "&JBooleanArray" => Ok("[Z".to_string()),
        "JCharArray" | "jcharArray" | "&JCharArray" => Ok("[C".to_string()),

        // Object types (require class annotation)
        "JObject" | "jobject" | "&JObject" => {
            if let Some(class) = class_attr {
                let resolved = resolve_class(class, module_package);
                Ok(format!("L{};", resolved))
            } else {
                Err(format!(
                    "JObject parameter requires #[class = \"...\"] annotation, got type '{}'",
                    ty
                ))
            }
        }

        // Object array types (require class annotation for element type)
        "JObjectArray" | "jobjectArray" | "&JObjectArray" => {
            if let Some(class) = class_attr {
                let resolved = resolve_class(class, module_package);
                Ok(format!("[L{};", resolved))
            } else {
                Err(format!(
                    "JObjectArray parameter requires #[class = \"...\"] annotation, got type '{}'",
                    ty
                ))
            }
        }

        _ => Err(format!("Unknown JNI type: '{}'", ty)),
    }
}

/// Converts a return type to its JNI signature string.
///
/// Same as `param_to_jni_sig` but additionally:
/// - Unwraps `Result<T, _>` to the inner type `T`
/// - Accepts `returns_attr` for object return types (e.g., `#[returns = "java.lang.String"]`)
/// - Maps `()` / no return to `"V"`
///
/// # Examples
///
/// ```text
/// return_to_jni_sig("()", None, None)                                  → Ok("V")
/// return_to_jni_sig("jint", None, None)                                → Ok("I")
/// return_to_jni_sig("String", None, None)                              → Ok("Ljava/lang/String;")
/// return_to_jni_sig("Result<jint, JniError>", None, None)              → Ok("I")
/// return_to_jni_sig("Result<(), JniError>", None, None)                → Ok("V")
/// return_to_jni_sig("JObject", Some("android/view/KeyEvent"), None)    → Ok("Landroid/view/KeyEvent;")
/// return_to_jni_sig("JObject", None, None)                             → Err("JObject return type requires #[returns = \"...\"] ...")
/// ```
pub fn return_to_jni_sig(
    rust_type: &str,
    returns_attr: Option<&str>,
    module_package: Option<&str>,
) -> Result<String, String> {
    let ty = rust_type.trim();

    // Handle Result<T, ...> — unwrap to inner type
    if let Some(inner) = extract_result_inner(ty) {
        return return_to_jni_sig(&inner, returns_attr, module_package);
    }

    // Handle void/unit
    if ty == "()" || ty.is_empty() || ty == "void" {
        return Ok("V".to_string());
    }

    // Use returns_attr for object return types
    if ty == "JObject" || ty == "jobject" || ty == "&JObject" {
        if let Some(class) = returns_attr {
            let resolved = resolve_class(class, module_package);
            return Ok(format!("L{};", resolved));
        }
        return Err("JObject return type requires #[returns = \"...\"] annotation".to_string());
    }

    if ty == "JObjectArray" || ty == "jobjectArray" || ty == "&JObjectArray" {
        if let Some(class) = returns_attr {
            let resolved = resolve_class(class, module_package);
            return Ok(format!("[L{};", resolved));
        }
        return Err("JObjectArray return type requires #[returns = \"...\"] annotation".to_string());
    }

    // For JString/String return, auto-map
    if ty == "JString" || ty == "jstring" || ty == "&JString" || ty == "String" {
        return Ok("Ljava/lang/String;".to_string());
    }

    // Delegate to param_to_jni_sig for everything else (primitives, arrays)
    param_to_jni_sig(ty, returns_attr, module_package)
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

/// Builds a complete JNI signature string from parameters and return type.
///
/// Each param is a tuple of `(rust_type, class_attr)`.
///
/// # Examples
///
/// ```text
/// build_signature(&[("jint", None), ("jlong", None)], "()", None, None)  → Ok("(IJ)V")
/// build_signature(&[], "jlong", None, None)                              → Ok("()J")
/// build_signature(&[("JString", None)], "bool", None, None)              → Ok("(Ljava/lang/String;)Z")
/// build_signature(&[("JObject", None)], "()", None, None)                → Err("JObject parameter requires ...")
/// ```
pub fn build_signature(
    params: &[(&str, Option<&str>)],
    return_type: &str,
    returns_attr: Option<&str>,
    module_package: Option<&str>,
) -> Result<String, String> {
    let mut sig = String::from("(");

    for (ty, class_attr) in params {
        let param_sig = param_to_jni_sig(ty, *class_attr, module_package)?;
        sig.push_str(&param_sig);
    }

    sig.push(')');

    let ret_sig = return_to_jni_sig(return_type, returns_attr, module_package)?;
    sig.push_str(&ret_sig);

    Ok(sig)
}

/// Maps a friendly Java type name to its JNI signature string.
///
/// Supports:
/// - Primitives: `int` → `I`, `long` → `J`, `float` → `F`, etc.
/// - `void` → `V`
/// - `String` → `Ljava/lang/String;`
/// - Array types: `int[]` → `[I`, `byte[]` → `[B`, `String[]` → `[Ljava/lang/String;`
/// - Fully qualified classes: `android.view.KeyEvent` → `Landroid/view/KeyEvent;`
/// - Slash-separated classes: `android/view/KeyEvent` → `Landroid/view/KeyEvent;`
///
/// # Examples
///
/// ```text
/// friendly_type_to_sig("int")                     → Ok("I")
/// friendly_type_to_sig("String")                  → Ok("Ljava/lang/String;")
/// friendly_type_to_sig("int[]")                   → Ok("[I")
/// friendly_type_to_sig("android.view.KeyEvent")   → Ok("Landroid/view/KeyEvent;")
/// friendly_type_to_sig("android.view.KeyEvent[]") → Ok("[Landroid/view/KeyEvent;")
/// friendly_type_to_sig("")                         → Err("empty type string")
/// ```
pub fn friendly_type_to_sig(ty: &str) -> Result<String, String> {
    let ty = ty.trim();
    if ty.is_empty() {
        return Err("empty type string".to_string());
    }

    // Handle array types (e.g. "int[]", "String[]", "android.view.KeyEvent[]")
    if let Some(element) = ty.strip_suffix("[]") {
        let element = element.trim();
        let inner = friendly_type_to_sig(element)?;
        return Ok(format!("[{}", inner));
    }

    // Primitive types
    match ty {
        "void" => return Ok("V".to_string()),
        "int" => return Ok("I".to_string()),
        "long" => return Ok("J".to_string()),
        "float" => return Ok("F".to_string()),
        "double" => return Ok("D".to_string()),
        "boolean" | "bool" => return Ok("Z".to_string()),
        "byte" => return Ok("B".to_string()),
        "char" => return Ok("C".to_string()),
        "short" => return Ok("S".to_string()),
        _ => {}
    }

    // String special case
    if ty == "String" {
        return Ok("Ljava/lang/String;".to_string());
    }

    // Class type — contains dots or slashes, or is a simple class name
    let normalized = ty.replace('.', "/");
    validate_class_name(&normalized)?;
    Ok(format!("L{};", normalized))
}

/// Validates a slash-normalized Java class name so that malformed type specs
/// (e.g. `"in t"` or `"long["`) do not silently become nonsense signatures.
///
/// Every `/`-separated segment must be non-empty and consist of ASCII
/// alphanumerics, `_`, or `$`.
fn validate_class_name(name: &str) -> Result<(), String> {
    let valid_segment = |segment: &str| {
        !segment.is_empty()
            && segment.chars().all(|c| c.is_ascii_alphanumeric() || c == '_' || c == '$')
    };
    if name.split('/').all(valid_segment) {
        Ok(())
    } else {
        Err(format!("invalid Java class name: '{}'", name))
    }
}

/// Parses a friendly method signature string like `"(int, long) -> void"` into
/// a JNI method signature like `"(IJ)V"`.
///
/// The format is: `"(param1, param2, ...) -> return_type"`
///
/// # Examples
///
/// ```text
/// friendly_method_sig("() -> void")          → Ok("()V")
/// friendly_method_sig("(int) -> String")     → Ok("(I)Ljava/lang/String;")
/// friendly_method_sig("(int, long) -> void") → Ok("(IJ)V")
/// friendly_method_sig("(String, int) -> boolean") → Ok("(Ljava/lang/String;I)Z")
/// friendly_method_sig("(int) void")          → Err("method signature must contain '->'...")
/// friendly_method_sig("int -> void")         → Err("parameter list must be wrapped in parentheses...")
/// ```
pub fn friendly_method_sig(spec: &str) -> Result<String, String> {
    let spec = spec.trim();

    // Split on "->"
    let parts: Vec<&str> = spec.splitn(2, "->").collect();
    if parts.len() != 2 {
        return Err(format!("method signature must contain '->': got '{}'", spec));
    }

    let params_part = parts[0].trim();
    let return_part = parts[1].trim();

    // Parse params: strip parens, split on comma
    let params_inner = params_part
        .strip_prefix('(')
        .and_then(|s| s.strip_suffix(')'))
        .ok_or_else(|| {
            format!("parameter list must be wrapped in parentheses: got '{}'", params_part)
        })?
        .trim();

    let mut sig = String::from("(");

    if !params_inner.is_empty() {
        for param in params_inner.split(',') {
            let param = param.trim();
            if param.is_empty() {
                continue;
            }
            sig.push_str(&friendly_type_to_sig(param)?);
        }
    }

    sig.push(')');
    sig.push_str(&friendly_type_to_sig(return_part)?);

    Ok(sig)
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
        assert_eq!(primitive_sig("u8"), Some("Z"));
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

    // ---- param_to_jni_sig tests ----

    #[test]
    fn test_param_primitives() {
        assert_eq!(param_to_jni_sig("jint", None, None).unwrap(), "I");
        assert_eq!(param_to_jni_sig("jlong", None, None).unwrap(), "J");
        assert_eq!(param_to_jni_sig("jfloat", None, None).unwrap(), "F");
        assert_eq!(param_to_jni_sig("jdouble", None, None).unwrap(), "D");
        assert_eq!(param_to_jni_sig("jboolean", None, None).unwrap(), "Z");
        assert_eq!(param_to_jni_sig("jbyte", None, None).unwrap(), "B");
        assert_eq!(param_to_jni_sig("jchar", None, None).unwrap(), "C");
        assert_eq!(param_to_jni_sig("jshort", None, None).unwrap(), "S");
    }

    #[test]
    fn test_param_jstring() {
        assert_eq!(param_to_jni_sig("JString", None, None).unwrap(), "Ljava/lang/String;");
        assert_eq!(param_to_jni_sig("jstring", None, None).unwrap(), "Ljava/lang/String;");
        assert_eq!(param_to_jni_sig("&JString", None, None).unwrap(), "Ljava/lang/String;");
    }

    #[test]
    fn test_param_str_ref() {
        assert_eq!(param_to_jni_sig("&str", None, None).unwrap(), "Ljava/lang/String;");
    }

    #[test]
    fn test_param_option_str_ref() {
        assert_eq!(param_to_jni_sig("Option<&str>", None, None).unwrap(), "Ljava/lang/String;");
    }

    #[test]
    fn test_param_java_str_ref() {
        assert_eq!(param_to_jni_sig("&JavaStr", None, None).unwrap(), "Ljava/lang/String;");
        assert_eq!(param_to_jni_sig("Option<&JavaStr>", None, None).unwrap(), "Ljava/lang/String;");
    }

    #[test]
    fn test_param_bool() {
        assert_eq!(param_to_jni_sig("bool", None, None).unwrap(), "Z");
    }

    #[test]
    fn test_param_typed_arrays() {
        assert_eq!(param_to_jni_sig("JByteArray", None, None).unwrap(), "[B");
        assert_eq!(param_to_jni_sig("JIntArray", None, None).unwrap(), "[I");
        assert_eq!(param_to_jni_sig("JFloatArray", None, None).unwrap(), "[F");
        assert_eq!(param_to_jni_sig("JLongArray", None, None).unwrap(), "[J");
        assert_eq!(param_to_jni_sig("JShortArray", None, None).unwrap(), "[S");
        assert_eq!(param_to_jni_sig("JDoubleArray", None, None).unwrap(), "[D");
        assert_eq!(param_to_jni_sig("JBooleanArray", None, None).unwrap(), "[Z");
        assert_eq!(param_to_jni_sig("JCharArray", None, None).unwrap(), "[C");
    }

    #[test]
    fn test_param_jobject_with_class() {
        assert_eq!(
            param_to_jni_sig("JObject", Some("android/view/KeyEvent"), None).unwrap(),
            "Landroid/view/KeyEvent;"
        );
    }

    #[test]
    fn test_param_jobject_dotted_class() {
        assert_eq!(
            param_to_jni_sig("JObject", Some("android.view.KeyEvent"), None).unwrap(),
            "Landroid/view/KeyEvent;"
        );
    }

    #[test]
    fn test_param_jobject_relative_class() {
        assert_eq!(
            param_to_jni_sig("JObject", Some("MotionEvent$PointerCoords"), Some("android/view"))
                .unwrap(),
            "Landroid/view/MotionEvent$PointerCoords;"
        );
    }

    #[test]
    fn test_param_jobject_no_class_error() {
        let result = param_to_jni_sig("JObject", None, None);
        assert!(result.is_err());
        assert!(result.unwrap_err().contains("#[class = \"...\"]"));
    }

    #[test]
    fn test_param_jobject_array_with_class() {
        assert_eq!(
            param_to_jni_sig("JObjectArray", Some("android/view/KeyEvent"), None).unwrap(),
            "[Landroid/view/KeyEvent;"
        );
    }

    #[test]
    fn test_param_jobject_array_no_class_error() {
        let result = param_to_jni_sig("JObjectArray", None, None);
        assert!(result.is_err());
    }

    #[test]
    fn test_param_unknown_type_error() {
        let result = param_to_jni_sig("FooBar", None, None);
        assert!(result.is_err());
        assert!(result.unwrap_err().contains("Unknown JNI type"));
    }

    // ---- return_to_jni_sig tests ----

    #[test]
    fn test_return_void() {
        assert_eq!(return_to_jni_sig("()", None, None).unwrap(), "V");
        assert_eq!(return_to_jni_sig("", None, None).unwrap(), "V");
        assert_eq!(return_to_jni_sig("void", None, None).unwrap(), "V");
    }

    #[test]
    fn test_return_primitive() {
        assert_eq!(return_to_jni_sig("jint", None, None).unwrap(), "I");
        assert_eq!(return_to_jni_sig("jlong", None, None).unwrap(), "J");
    }

    #[test]
    fn test_return_jstring() {
        assert_eq!(return_to_jni_sig("JString", None, None).unwrap(), "Ljava/lang/String;");
    }

    #[test]
    fn test_return_string() {
        assert_eq!(return_to_jni_sig("String", None, None).unwrap(), "Ljava/lang/String;");
    }

    #[test]
    fn test_return_bool() {
        assert_eq!(return_to_jni_sig("bool", None, None).unwrap(), "Z");
    }

    #[test]
    fn test_return_jobject_with_returns_attr() {
        assert_eq!(
            return_to_jni_sig("JObject", Some("android/view/KeyEvent"), None).unwrap(),
            "Landroid/view/KeyEvent;"
        );
    }

    #[test]
    fn test_return_jobject_dotted_returns_attr() {
        assert_eq!(
            return_to_jni_sig("JObject", Some("java.lang.String"), None).unwrap(),
            "Ljava/lang/String;"
        );
    }

    #[test]
    fn test_return_jobject_no_attr_error() {
        let result = return_to_jni_sig("JObject", None, None);
        assert!(result.is_err());
    }

    #[test]
    fn test_return_result_unwrap_primitive() {
        assert_eq!(return_to_jni_sig("Result<jint, JniError>", None, None).unwrap(), "I");
        assert_eq!(return_to_jni_sig("Result<jlong, JniError>", None, None).unwrap(), "J");
    }

    #[test]
    fn test_return_result_unwrap_void() {
        assert_eq!(return_to_jni_sig("Result<(), JniError>", None, None).unwrap(), "V");
    }

    #[test]
    fn test_return_result_unwrap_jstring() {
        assert_eq!(
            return_to_jni_sig("Result<JString, JniError>", None, None).unwrap(),
            "Ljava/lang/String;"
        );
    }

    // ---- build_signature tests ----

    #[test]
    fn test_build_sig_primitive_params_void_return() {
        let result =
            build_signature(&[("jint", None), ("jlong", None), ("jfloat", None)], "()", None, None);
        assert_eq!(result.unwrap(), "(IJF)V");
    }

    #[test]
    fn test_build_sig_all_primitives() {
        let result = build_signature(
            &[
                ("jint", None),
                ("jlong", None),
                ("jfloat", None),
                ("jdouble", None),
                ("jboolean", None),
                ("jbyte", None),
                ("jchar", None),
                ("jshort", None),
            ],
            "jlong",
            None,
            None,
        );
        assert_eq!(result.unwrap(), "(IJFDZBCS)J");
    }

    #[test]
    fn test_build_sig_empty_params_primitive_return() {
        let result = build_signature(&[], "jlong", None, None);
        assert_eq!(result.unwrap(), "()J");
    }

    #[test]
    fn test_build_sig_jstring_param() {
        let result = build_signature(&[("JString", None)], "()", None, None);
        assert_eq!(result.unwrap(), "(Ljava/lang/String;)V");
    }

    #[test]
    fn test_build_sig_jstring_return() {
        let result = build_signature(&[], "JString", None, None);
        assert_eq!(result.unwrap(), "()Ljava/lang/String;");
    }

    #[test]
    fn test_build_sig_jobject_fully_qualified() {
        let result =
            build_signature(&[("JObject", Some("android/view/KeyEvent"))], "()", None, None);
        assert_eq!(result.unwrap(), "(Landroid/view/KeyEvent;)V");
    }

    #[test]
    fn test_build_sig_jobject_dotted() {
        let result =
            build_signature(&[("JObject", Some("android.view.KeyEvent"))], "()", None, None);
        assert_eq!(result.unwrap(), "(Landroid/view/KeyEvent;)V");
    }

    #[test]
    fn test_build_sig_jobject_relative() {
        let result = build_signature(
            &[("JObject", Some("MotionEvent$PointerCoords"))],
            "()",
            None,
            Some("android/view"),
        );
        assert_eq!(result.unwrap(), "(Landroid/view/MotionEvent$PointerCoords;)V");
    }

    #[test]
    fn test_build_sig_jobject_array() {
        let result =
            build_signature(&[("JObjectArray", Some("android/view/KeyEvent"))], "()", None, None);
        assert_eq!(result.unwrap(), "([Landroid/view/KeyEvent;)V");
    }

    #[test]
    fn test_build_sig_byte_array() {
        let result = build_signature(&[("JByteArray", None)], "()", None, None);
        assert_eq!(result.unwrap(), "([B)V");
    }

    #[test]
    fn test_build_sig_result_unwrap() {
        let result = build_signature(&[("jint", None)], "Result<jlong, JniError>", None, None);
        assert_eq!(result.unwrap(), "(I)J");
    }

    #[test]
    fn test_build_sig_result_void_unwrap() {
        let result = build_signature(&[], "Result<(), JniError>", None, None);
        assert_eq!(result.unwrap(), "()V");
    }

    #[test]
    fn test_build_sig_unannotated_jobject_error() {
        let result = build_signature(&[("JObject", None)], "()", None, None);
        assert!(result.is_err());
        assert!(result.unwrap_err().contains("#[class = \"...\"]"));
    }

    #[test]
    fn test_build_sig_jobject_return_with_returns() {
        let result = build_signature(&[], "JObject", Some("android/view/KeyEvent"), None);
        assert_eq!(result.unwrap(), "()Landroid/view/KeyEvent;");
    }

    #[test]
    fn test_build_sig_complex() {
        let result = build_signature(
            &[
                ("jlong", None),
                ("jint", None),
                ("JString", None),
                ("JObject", Some("android/view/MotionEvent")),
                ("jfloat", None),
            ],
            "jboolean",
            None,
            None,
        );
        assert_eq!(result.unwrap(), "(JILjava/lang/String;Landroid/view/MotionEvent;F)Z");
    }

    #[test]
    fn test_build_sig_friendly_types() {
        // &str param + bool return
        let result = build_signature(&[("&str", None), ("i32", None)], "bool", None, None);
        assert_eq!(result.unwrap(), "(Ljava/lang/String;I)Z");
    }

    #[test]
    fn test_build_sig_option_str_and_string_return() {
        let result = build_signature(&[("Option<&str>", None)], "String", None, None);
        assert_eq!(result.unwrap(), "(Ljava/lang/String;)Ljava/lang/String;");
    }

    #[test]
    fn test_build_sig_returns_dotted() {
        let result = build_signature(&[], "JObject", Some("java.lang.String"), None);
        assert_eq!(result.unwrap(), "()Ljava/lang/String;");
    }

    // ---- friendly_type_to_sig tests ----

    #[test]
    fn test_friendly_type_primitives() {
        assert_eq!(friendly_type_to_sig("void").unwrap(), "V");
        assert_eq!(friendly_type_to_sig("int").unwrap(), "I");
        assert_eq!(friendly_type_to_sig("long").unwrap(), "J");
        assert_eq!(friendly_type_to_sig("float").unwrap(), "F");
        assert_eq!(friendly_type_to_sig("double").unwrap(), "D");
        assert_eq!(friendly_type_to_sig("boolean").unwrap(), "Z");
        assert_eq!(friendly_type_to_sig("bool").unwrap(), "Z");
        assert_eq!(friendly_type_to_sig("byte").unwrap(), "B");
        assert_eq!(friendly_type_to_sig("char").unwrap(), "C");
        assert_eq!(friendly_type_to_sig("short").unwrap(), "S");
    }

    #[test]
    fn test_friendly_type_string() {
        assert_eq!(friendly_type_to_sig("String").unwrap(), "Ljava/lang/String;");
    }

    #[test]
    fn test_friendly_type_arrays() {
        assert_eq!(friendly_type_to_sig("int[]").unwrap(), "[I");
        assert_eq!(friendly_type_to_sig("byte[]").unwrap(), "[B");
        assert_eq!(friendly_type_to_sig("long[]").unwrap(), "[J");
        assert_eq!(friendly_type_to_sig("float[]").unwrap(), "[F");
        assert_eq!(friendly_type_to_sig("double[]").unwrap(), "[D");
        assert_eq!(friendly_type_to_sig("boolean[]").unwrap(), "[Z");
        assert_eq!(friendly_type_to_sig("char[]").unwrap(), "[C");
        assert_eq!(friendly_type_to_sig("short[]").unwrap(), "[S");
        assert_eq!(friendly_type_to_sig("String[]").unwrap(), "[Ljava/lang/String;");
    }

    #[test]
    fn test_friendly_type_class_dotted() {
        assert_eq!(
            friendly_type_to_sig("android.view.KeyEvent").unwrap(),
            "Landroid/view/KeyEvent;"
        );
    }

    #[test]
    fn test_friendly_type_class_slashed() {
        assert_eq!(
            friendly_type_to_sig("android/view/KeyEvent").unwrap(),
            "Landroid/view/KeyEvent;"
        );
    }

    #[test]
    fn test_friendly_type_class_array() {
        assert_eq!(
            friendly_type_to_sig("android.view.KeyEvent[]").unwrap(),
            "[Landroid/view/KeyEvent;"
        );
    }

    #[test]
    fn test_friendly_type_simple_class() {
        assert_eq!(friendly_type_to_sig("KeyEvent").unwrap(), "LKeyEvent;");
    }

    #[test]
    fn test_friendly_type_empty_error() {
        assert!(friendly_type_to_sig("").is_err());
    }

    #[test]
    fn test_friendly_type_malformed_class_error() {
        let err = friendly_type_to_sig("in t").unwrap_err();
        assert!(err.contains("invalid Java class name"), "{err}");
        assert!(friendly_type_to_sig("long[").is_err());
        assert!(friendly_type_to_sig("android..view.KeyEvent").is_err());
        assert!(friendly_type_to_sig("android.view.").is_err());
    }

    #[test]
    fn test_friendly_type_inner_class_dollar_allowed() {
        assert_eq!(
            friendly_type_to_sig("android.view.MotionEvent$PointerCoords").unwrap(),
            "Landroid/view/MotionEvent$PointerCoords;"
        );
    }

    #[test]
    fn test_friendly_type_whitespace() {
        assert_eq!(friendly_type_to_sig("  int  ").unwrap(), "I");
        assert_eq!(friendly_type_to_sig("  byte[]  ").unwrap(), "[B");
    }

    // ---- friendly_method_sig tests ----

    #[test]
    fn test_friendly_method_void_to_void() {
        assert_eq!(friendly_method_sig("() -> void").unwrap(), "()V");
    }

    #[test]
    fn test_friendly_method_void_to_long() {
        assert_eq!(friendly_method_sig("() -> long").unwrap(), "()J");
    }

    #[test]
    fn test_friendly_method_int_to_string() {
        assert_eq!(friendly_method_sig("(int) -> String").unwrap(), "(I)Ljava/lang/String;");
    }

    #[test]
    fn test_friendly_method_multiple_params() {
        assert_eq!(friendly_method_sig("(int, long) -> void").unwrap(), "(IJ)V");
    }

    #[test]
    fn test_friendly_method_complex_key_event_obtain() {
        let sig = friendly_method_sig(
            "(int, long, long, int, int, int, int, int, int, int, int, int, byte[], String) -> android.view.KeyEvent"
        ).unwrap();
        assert_eq!(sig, "(IJJIIIIIIIII[BLjava/lang/String;)Landroid/view/KeyEvent;");
    }

    #[test]
    fn test_friendly_method_string_to_int() {
        assert_eq!(friendly_method_sig("(String) -> int").unwrap(), "(Ljava/lang/String;)I");
    }

    #[test]
    fn test_friendly_method_string_params() {
        assert_eq!(
            friendly_method_sig("(int, int, String, String) -> int").unwrap(),
            "(IILjava/lang/String;Ljava/lang/String;)I"
        );
    }

    #[test]
    fn test_friendly_method_no_arrow_error() {
        assert!(friendly_method_sig("(int) void").is_err());
    }

    #[test]
    fn test_friendly_method_no_parens_error() {
        assert!(friendly_method_sig("int -> void").is_err());
    }

    #[test]
    fn test_friendly_method_boolean_return() {
        assert_eq!(
            friendly_method_sig("(String, int) -> boolean").unwrap(),
            "(Ljava/lang/String;I)Z"
        );
    }
}
