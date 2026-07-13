//! Compile-time and descriptor-level exercise of the code `#[jni_module]` generates.
//!
//! There is no JVM available in tests, so nothing here calls the shims. Two
//! things are checked instead:
//!
//! 1. Expanding a module against the real `jni`/`jni_support` dependency graph
//!    proves the generated code compiles. For regular/fast methods that is
//!    itself load-bearing: `native_method!` builds each `extern "system"` shim
//!    and TYPE-CHECKS it against the JNI signature it DERIVES from the
//!    signature tokens, so a shim registered under a mismatched signature would
//!    fail to compile.
//! 2. Every generated `const NativeMethod` is introspected and asserted to carry
//!    the EXACT JNI descriptor the method should register under (e.g.
//!    `println_native => "(Ljava/lang/String;Ljava/lang/String;Z)I"`). A wrong
//!    derived signature — the failure this whole rework exists to prevent —
//!    would make that assertion fail.
//!
//! The `@CriticalNative` shim keeps a hand-rolled ABI, so its host variant is
//! additionally coerced to the explicit `extern "system"` fn-pointer type that
//! non-ART JVMs call it with.

// JNI methods use Java camelCase names.
#![allow(non_snake_case)]

#[jni_platform_macros::jni_module("com/example/Widget")]
pub mod widget {
    use jni::objects::{JFloatArray, JIntArray, JObject, JObjectArray};
    use jni::strings::JNIStr;
    use jni::sys::{jclass, jint, jlong, jobject, jstring};
    use jni_support::JniError;

    jni::bind_java_type! {
        TestItem => "com.example.Outer$Inner"
    }

    /// Result with a primitive Ok type: Err must become a pending exception
    /// plus a zero return.
    #[jni_method]
    fn nativeGetCount(
        _env: &mut jni::Env<'_>,
        _clazz: jclass,
        ptr: jlong,
    ) -> Result<jint, JniError> {
        if ptr == 0 {
            return Err(JniError::NullPointer("ptr"));
        }
        Ok(1)
    }

    /// Result with a raw object Ok type: Err must return the null reference.
    #[jni_method]
    fn nativeGetName(
        _env: &mut jni::Env<'_>,
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
    fn nativeApply(_env: &mut jni::Env<'_>, _clazz: jclass, _ptr: jlong) -> Result<(), JniError> {
        Ok(())
    }

    /// String and bool conversions combined with a primitive return.
    #[jni_method(fast)]
    fn println_native(
        _env: &mut jni::Env<'_>,
        _clazz: jclass,
        tag: Option<&str>,
        msg: &str,
        enabled: bool,
    ) -> jint {
        let _ = (tag, msg, enabled);
        0
    }

    /// A `&str` failure path combined with a raw object return.
    #[jni_method]
    fn nativeIntern(_env: &mut jni::Env<'_>, _clazz: jclass, name: &str) -> jstring {
        let _ = name;
        std::ptr::null_mut()
    }

    /// Borrowed zero-allocation strings: `&JNIStr` exposes the Modified UTF-8
    /// bytes as a `&CStr` for free and converts to `Cow<str>` without copying
    /// valid-UTF-8 content.
    #[jni_method(fast)]
    fn nativeLogBorrowed(
        _env: &mut jni::Env<'_>,
        _clazz: jclass,
        tag: Option<&JNIStr>,
        msg: &JNIStr,
    ) -> jint {
        let _tag_cstr: Option<&std::ffi::CStr> = tag.map(|t| t.as_cstr());
        let _msg_cstr: &std::ffi::CStr = msg.as_cstr();
        let _msg_text: std::borrow::Cow<'_, str> = msg.to_str();
        0
    }

    /// Critical mode: no env/this anywhere in the shim.
    #[jni_method(critical)]
    fn nativeGetId(ptr: jlong) -> jint {
        ptr as jint
    }

    // The methods below pin the one derivation the compiler CANNOT check: the
    // Java class string a `#[class]` object/object-array carries into the
    // descriptor. The impl fn keeps `JObject`/`JObjectArray` (the `type_map`
    // alias resolves to `JObject`), so only asserting the registered descriptor
    // catches a wrong `#[class]` string.

    /// `#[class]` scalar object: descriptor must be the named class, not the
    /// `java/lang/Object` its `JObject` param would imply.
    #[jni_method]
    fn nativeAddTo(
        _env: &mut jni::Env<'_>,
        _clazz: jclass,
        #[class = "java/util/Collection"] out: &JObject,
    ) {
        let _ = out;
    }

    /// Object array of a non-core inner class: locks the `$` inner-class
    /// separator and dot→slash packaging through the array-element path.
    #[jni_method]
    fn nativeVisit(
        _env: &mut jni::Env<'_>,
        _clazz: jclass,
        #[class = "com/example/Outer$Inner"] items: &JObjectArray<TestItem>,
    ) {
        let _ = items;
    }

    /// Core-class object array: `java/lang/Object` stays a literal element type
    /// (never aliased), wrapped by the array.
    #[jni_method]
    fn nativeWriteAll(
        _env: &mut jni::Env<'_>,
        _clazz: jclass,
        #[class = "java/lang/Object"] values: &JObjectArray,
    ) {
        let _ = values;
    }

    /// Primitive arrays: distinct element types must stay distinct (`[I` vs
    /// `[F`); the `JPrimitiveArray<T>` element type also compile-checks these.
    #[jni_method]
    fn nativeSums(_env: &mut jni::Env<'_>, _clazz: jclass, ints: &JIntArray, floats: &JFloatArray) {
        let _ = (ints, floats);
    }

    /// A primitive array, a primitive, and a `#[class]` object interleaved —
    /// EventLog's `readEventsOnWrapping` shape.
    #[jni_method]
    fn nativeDrain(
        _env: &mut jni::Env<'_>,
        _clazz: jclass,
        tags: &JIntArray,
        ptr: jlong,
        #[class = "java/util/Collection"] out: &JObject,
    ) {
        let _ = (tags, ptr, out);
    }

    /// Instance native: a `jobject` receiver registers as non-static (`this`,
    /// not a class), pinning the instance-method path end-to-end.
    #[jni_method]
    fn nativeInstance(_env: &mut jni::Env<'_>, _this: jobject, ptr: jlong) {
        let _ = ptr;
    }

    /// Reads the `(name, signature)` a `NativeMethod` will hand `RegisterNatives`.
    ///
    /// `NativeMethod` is `#[repr(transparent)]` over `jni::sys::JNINativeMethod`,
    /// whose `name`/`signature` are non-null, NUL-terminated Modified-UTF-8 C
    /// strings that live as long as the `const` (the `jni_str!` data is
    /// `'static`). Reading the descriptor is the only way to observe the
    /// signature `native_method!` DERIVED, which is the property under test.
    fn descriptor(method: &jni::NativeMethod<'_>) -> (String, String) {
        // SAFETY: `NativeMethod` is a `#[repr(transparent)]` wrapper around
        // `JNINativeMethod` (plus a ZST `PhantomData`), so it has the same
        // layout; both `name` and `signature` are valid NUL-terminated strings.
        let raw: &jni::sys::JNINativeMethod =
            unsafe { &*std::ptr::from_ref(method).cast::<jni::sys::JNINativeMethod>() };
        // SAFETY: as above — the pointers are valid NUL-terminated C strings.
        let name = unsafe { std::ffi::CStr::from_ptr(raw.name.cast_const()) };
        // SAFETY: as above.
        let signature = unsafe { std::ffi::CStr::from_ptr(raw.signature.cast_const()) };
        (
            name.to_str().expect("method name is ASCII").to_string(),
            signature.to_str().expect("descriptor is ASCII").to_string(),
        )
    }

    #[test]
    fn generated_methods_register_expected_descriptors() {
        // The registered (name, descriptor) each method MUST carry. Because
        // `native_method!` derives the descriptor from — and type-checks the
        // fn pointer against — the same signature tokens, a wrong entry here
        // would mean the shim's ABI and its registered signature disagree.
        let expected: [(&str, &str, &jni::NativeMethod<'_>); 13] = [
            ("nativeGetCount", "(J)I", &__NATIVE_METHOD_nativeGetCount),
            ("nativeGetName", "(J)Ljava/lang/String;", &__NATIVE_METHOD_nativeGetName),
            ("nativeApply", "(J)V", &__NATIVE_METHOD_nativeApply),
            (
                "println_native",
                "(Ljava/lang/String;Ljava/lang/String;Z)I",
                &__NATIVE_METHOD_println_native,
            ),
            (
                "nativeIntern",
                "(Ljava/lang/String;)Ljava/lang/String;",
                &__NATIVE_METHOD_nativeIntern,
            ),
            (
                "nativeLogBorrowed",
                "(Ljava/lang/String;Ljava/lang/String;)I",
                &__NATIVE_METHOD_nativeLogBorrowed,
            ),
            ("nativeGetId", "(J)I", &__NATIVE_METHOD_nativeGetId),
            ("nativeAddTo", "(Ljava/util/Collection;)V", &__NATIVE_METHOD_nativeAddTo),
            ("nativeVisit", "([Lcom/example/Outer$Inner;)V", &__NATIVE_METHOD_nativeVisit),
            ("nativeWriteAll", "([Ljava/lang/Object;)V", &__NATIVE_METHOD_nativeWriteAll),
            ("nativeSums", "([I[F)V", &__NATIVE_METHOD_nativeSums),
            ("nativeDrain", "([IJLjava/util/Collection;)V", &__NATIVE_METHOD_nativeDrain),
            ("nativeInstance", "(J)V", &__NATIVE_METHOD_nativeInstance),
        ];

        for (name, sig, method) in expected {
            let (got_name, got_sig) = descriptor(method);
            assert_eq!(got_name, name, "wrong Java name for {name}");
            assert_eq!(got_sig, sig, "wrong JNI descriptor for {name}");
        }
    }

    #[test]
    fn critical_shim_has_host_abi() {
        // This test compiles for the host, where the critical shim carries the
        // ignored (JNIEnv, jclass) prefix that non-ART JVMs always pass; the
        // Android variant of the same shim takes only the jlong. Coercing the
        // hand-rolled shim to the exact fn-pointer type pins that ABI.
        let _: extern "system" fn(
            *mut jni::sys::JNIEnv,
            jni::sys::jclass,
            jni::sys::jlong,
        ) -> jni::sys::jint = __jni_nativeGetId;
    }
}

#[test]
fn register_fn_is_generated() {
    let _: fn(&mut jni::Env<'_>) = widget::register;
}
