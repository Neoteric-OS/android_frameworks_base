// Copyright (C) 2026 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Native method for `com.android.internal.os.ClassLoaderFactory`.
//!
//! The single native, `createClassloaderNamespace`, hands its arguments to
//! libnativeloader's `CreateClassLoaderNamespace`, which builds the linker
//! namespace for a freshly created class loader and returns `null` on success
//! or a Java `String` describing the failure. Every argument — the class
//! loader, the search / permitted / dex paths, and the `:`-joined soname list —
//! passes through as the raw JNI reference the JVM supplied; libnativeloader
//! does all the string handling, so nothing is copied on the Rust side.
//!
//! Device-only: `libnativeloader` is linked into the runtime only on Android
//! (`libnativeloader_lazy` in `core/jni/Android.bp`'s `target.android`
//! sources), so the whole module is gated behind `#[cfg(target_os =
//! "android")]` at its `mod` declaration in `lib.rs`.

use jni::objects::{JObject, JString};
use jni::sys::{jint, jobject, jstring};

extern "C" {
    /// libnativeloader's `android::CreateClassLoaderNamespace`, exposed with C
    /// linkage and provided on device by `libnativeloader_lazy`. Declared
    /// exactly as in `nativeloader/native_loader.h`: `target_sdk_version` is a
    /// C `int32_t` and `is_shared` a C++ `bool`, both ABI-identical to the Rust
    /// `jint` and `bool` used here. Returns a new local reference to a Java
    /// error `String`, or null on success.
    fn CreateClassLoaderNamespace(
        env: *mut jni::sys::JNIEnv,
        target_sdk_version: jint,
        class_loader: jobject,
        is_shared: bool,
        dex_path: jstring,
        library_path: jstring,
        permitted_path: jstring,
        uses_library_list: jstring,
    ) -> jstring;
}

/// Creates and initializes the native linker namespace for a class loader.
///
/// Thin binding over libnativeloader's `CreateClassLoaderNamespace`, keeping
/// its argument order. `env` is the current thread's `JNIEnv`; `class_loader`
/// and the string arguments are the raw references the JVM passed to the native
/// method and may each be null, which libnativeloader tolerates. Returns null
/// on success, or a new local reference to a Java `String` describing why
/// namespace creation failed.
// Mirrors libnativeloader's C signature one-for-one.
#[allow(clippy::too_many_arguments)]
fn create_class_loader_namespace<'local>(
    env: &mut jni::Env<'local>,
    target_sdk_version: i32,
    class_loader: JObject,
    is_shared: bool,
    dex_path: JString,
    library_path: JString,
    permitted_path: JString,
    uses_library_list: JString,
) -> JString<'local> {
    // SAFETY: `env.get_raw()` yields the live JNIEnv the JVM handed this native
    // call. The object and string arguments are the raw references the JVM
    // passed in (valid or null), and CreateClassLoaderNamespace accepts null
    // for each. Its result is a new local reference (or null).
    let result = unsafe {
        CreateClassLoaderNamespace(
            env.get_raw(),
            target_sdk_version,
            class_loader.as_raw(),
            is_shared,
            dex_path.as_raw(),
            library_path.as_raw(),
            permitted_path.as_raw(),
            uses_library_list.as_raw(),
        )
    };
    // SAFETY: `result` is the new local reference (or null) CreateClassLoaderNamespace
    // returned on this thread's frame; wrapping it ties it to `env`'s local frame.
    unsafe { JString::from_raw(env, result) }
}

/// Registers `com.android.internal.os.ClassLoaderFactory`'s native method.
///
/// Call during JNI startup; panics if the class or method is missing;
/// registration failures are fatal.
pub fn register(env: &mut jni::Env<'_>) {
    class_loader_factory::register(env);
}

/// The `com.android.internal.os.ClassLoaderFactory` native method.
#[jni_platform_macros::jni_module("com/android/internal/os/ClassLoaderFactory")]
pub mod class_loader_factory {
    use jni::objects::{JClass, JObject, JString};

    /// Creates the native linker namespace for a freshly created class loader.
    ///
    /// Shuffles its Java arguments into libnativeloader's parameter order and
    /// forwards them. Returns null on success or a Java error `String` on
    /// failure.
    #[jni_method]
    #[allow(clippy::too_many_arguments)] // Mirrors the Java native's parameters.
    fn createClassloaderNamespace<'local>(
        env: &mut jni::Env<'local>,
        _clazz: JClass,
        #[class = "java/lang/ClassLoader"] class_loader: JObject,
        target_sdk_version: i32,
        library_search_path: JString,
        library_permitted_path: JString,
        is_namespace_shared: bool,
        dex_path: JString,
        soname_list: JString,
    ) -> JString<'local> {
        super::create_class_loader_namespace(
            env,
            target_sdk_version,
            class_loader,
            is_namespace_shared,
            dex_path,
            library_search_path,
            library_permitted_path,
            soname_list,
        )
    }
}
