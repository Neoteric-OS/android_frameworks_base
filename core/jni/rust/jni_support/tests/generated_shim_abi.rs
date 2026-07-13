//! Compile-time exercise of the code `#[jni_module]` generates.
//!
//! There is no JVM available in tests, so nothing here calls the shims.
//! Instead, expanding a module against the real `jni`/`jni_support`
//! dependency graph proves the generated code compiles, and coercing each
//! shim to an explicit `unsafe extern "system"` function-pointer type proves
//! it has exactly the raw-typed ABI the JVM will call it with.

// JNI methods use Java camelCase names.
#![allow(non_snake_case)]

#[jni_macros::jni_module("com/example/Widget")]
pub mod widget {
    use jni::strings::JavaStr;
    use jni::sys::{jclass, jint, jlong, jstring};
    use jni_support::JniError;

    /// Result with a primitive Ok type: Err must become a pending exception
    /// plus a zero return.
    #[jni_method]
    fn nativeGetCount(
        _env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        ptr: jlong,
    ) -> Result<jint, JniError> {
        if ptr == 0 {
            return Err(JniError::NullPointer("ptr"));
        }
        Ok(1)
    }

    /// Result with a pointer Ok type: Err must return null.
    #[jni_method]
    fn nativeGetName(
        _env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        ptr: jlong,
    ) -> Result<jstring, JniError> {
        if ptr == 0 {
            return Err(JniError::IllegalArgument("ptr must not be 0".to_string()));
        }
        Ok(std::ptr::null_mut())
    }

    /// Result with a void Ok type.
    #[jni_method]
    fn nativeApply(
        _env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        _ptr: jlong,
    ) -> Result<(), JniError> {
        Ok(())
    }

    /// String and bool conversions combined with a primitive return.
    #[jni_method(fast)]
    fn println_native(
        _env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        tag: Option<&str>,
        msg: &str,
        enabled: bool,
    ) -> jint {
        let _ = (tag, msg, enabled);
        0
    }

    /// A &str failure path combined with a pointer return (null zero value).
    #[jni_method]
    fn nativeIntern(_env: &mut jni::JNIEnv<'_>, _clazz: jclass, name: &str) -> jstring {
        let _ = name;
        std::ptr::null_mut()
    }

    /// Borrowed zero-allocation strings: &JavaStr derefs to &CStr for free
    /// and converts to Cow<str> without copying valid-UTF-8 content.
    #[jni_method(fast)]
    fn nativeLogBorrowed(
        _env: &mut jni::JNIEnv<'_>,
        _clazz: jclass,
        tag: Option<&JavaStr>,
        msg: &JavaStr,
    ) -> jint {
        let _tag_cstr: Option<&std::ffi::CStr> = tag.map(|t| -> &std::ffi::CStr { t });
        let _msg_cstr: &std::ffi::CStr = msg;
        let _msg_text: std::borrow::Cow<'_, str> = msg.into();
        0
    }

    /// Critical mode: no env/this anywhere in the shim.
    #[jni_method(critical)]
    fn nativeGetId(ptr: jlong) -> jint {
        ptr as jint
    }

    #[test]
    fn shims_have_extern_system_raw_abi() {
        let _: unsafe extern "system" fn(
            *mut jni::sys::JNIEnv,
            jni::sys::jobject,
            jni::sys::jlong,
        ) -> jni::sys::jint = __jni_nativeGetCount;
        let _: unsafe extern "system" fn(
            *mut jni::sys::JNIEnv,
            jni::sys::jobject,
            jni::sys::jlong,
        ) -> jni::sys::jstring = __jni_nativeGetName;
        let _: unsafe extern "system" fn(
            *mut jni::sys::JNIEnv,
            jni::sys::jobject,
            jni::sys::jlong,
        ) = __jni_nativeApply;
        let _: unsafe extern "system" fn(
            *mut jni::sys::JNIEnv,
            jni::sys::jobject,
            jni::sys::jstring,
            jni::sys::jstring,
            jni::sys::jboolean,
        ) -> jni::sys::jint = __jni_println_native;
        let _: unsafe extern "system" fn(
            *mut jni::sys::JNIEnv,
            jni::sys::jobject,
            jni::sys::jstring,
        ) -> jni::sys::jstring = __jni_nativeIntern;
        let _: unsafe extern "system" fn(
            *mut jni::sys::JNIEnv,
            jni::sys::jobject,
            jni::sys::jstring,
            jni::sys::jstring,
        ) -> jni::sys::jint = __jni_nativeLogBorrowed;
        // This test compiles for the host, where the critical shim carries the
        // ignored (JNIEnv, jclass) prefix that non-ART JVMs always pass; the
        // Android variant of the same shim takes only the jlong.
        let _: unsafe extern "system" fn(
            *mut jni::sys::JNIEnv,
            jni::sys::jclass,
            jni::sys::jlong,
        ) -> jni::sys::jint = __jni_nativeGetId;
    }
}

#[test]
fn register_fn_is_generated() {
    let _: fn(&mut jni::JNIEnv<'_>) = widget::register;
}
