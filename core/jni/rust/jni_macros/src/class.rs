//! Shared Java class path parsing used by both `#[java_class]` and `#[jni_module]`.

use proc_macro2::TokenStream;
use syn::{parse2, Lit};

/// A parsed Java class path (e.g., "android/view/MotionEvent").
///
/// Normalizes dots to slashes at parse time so all consumers get a
/// consistent slash-separated path.
#[derive(Debug)]
pub(crate) struct JavaClass {
    path: String,
}

impl JavaClass {
    /// Parses a string literal from attribute tokens.
    ///
    /// `"android/view/MotionEvent"` → JavaClass { path: "android/view/MotionEvent" }
    /// `"android.view.MotionEvent"` → JavaClass { path: "android/view/MotionEvent" }
    /// `42` → Err(syn::Error)
    pub fn parse(attr: TokenStream, macro_name: &str) -> Result<Self, syn::Error> {
        let lit: Lit = parse2(attr)?;
        if let Lit::Str(s) = lit {
            Ok(JavaClass { path: s.value().replace('.', "/") })
        } else {
            Err(syn::Error::new_spanned(
                lit,
                format!("{} expects a string literal class path", macro_name),
            ))
        }
    }

    /// Full slash-separated class path.
    pub fn path(&self) -> &str {
        &self.path
    }

    /// Package prefix: "android/view/MotionEvent" → Some("android/view"), "Simple" → None
    pub fn package(&self) -> Option<&str> {
        self.path.rfind('/').map(|pos| &self.path[..pos])
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use quote::quote;

    #[test]
    fn test_parse_slash_path() {
        let java_class = JavaClass::parse(quote! { "android/view/MotionEvent" }, "test").unwrap();
        assert_eq!(java_class.path(), "android/view/MotionEvent");
    }

    #[test]
    fn test_parse_dotted_path() {
        let java_class = JavaClass::parse(quote! { "android.view.MotionEvent" }, "test").unwrap();
        assert_eq!(java_class.path(), "android/view/MotionEvent");
    }

    #[test]
    fn test_parse_simple_name() {
        let java_class = JavaClass::parse(quote! { "Simple" }, "test").unwrap();
        assert_eq!(java_class.path(), "Simple");
    }

    #[test]
    fn test_parse_non_string_error() {
        let result = JavaClass::parse(quote! { 42 }, "java_class");
        assert!(result.is_err());
    }

    #[test]
    fn test_parse_error_message_includes_macro_name() {
        let err = JavaClass::parse(quote! { 42 }, "jni_module").unwrap_err();
        assert!(err.to_string().contains("jni_module"));
    }

    #[test]
    fn test_package_with_slashes() {
        let java_class = JavaClass::parse(quote! { "android/view/MotionEvent" }, "test").unwrap();
        assert_eq!(java_class.package(), Some("android/view"));
    }

    #[test]
    fn test_package_with_dots() {
        let java_class = JavaClass::parse(quote! { "android.view.MotionEvent" }, "test").unwrap();
        assert_eq!(java_class.package(), Some("android/view"));
    }

    #[test]
    fn test_package_deep() {
        let java_class = JavaClass::parse(quote! { "android/os/SystemClock" }, "test").unwrap();
        assert_eq!(java_class.package(), Some("android/os"));
    }

    #[test]
    fn test_package_simple_name() {
        let java_class = JavaClass::parse(quote! { "Simple" }, "test").unwrap();
        assert_eq!(java_class.package(), None);
    }
}
