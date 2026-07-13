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

//! Bridges libutils' sysprop-change callback machinery to
//! `android.os.SystemProperties`.
//!
//! libutils keeps a process-wide list of plain C callbacks
//! (`add_sysprop_change_callback`) that `report_sysprop_change()` runs
//! whenever some component decides system properties may have changed. The
//! cxx bridge binds `android::report_sysprop_change()` directly; only the
//! callback registration needs a small C++ trampoline
//! (`ffi/sysprop_change.cpp`, compiled into the `libandroid_runtime_rs_bridge`
//! static library), because libutils takes a plain function pointer, which
//! cannot point at a Rust function.
//!
//! [`add_java_change_listener`] wires the Java side up once per process:
//! it caches the `JavaVM`, a global reference to the `SystemProperties`
//! class, and the `callChangeCallbacks()V` method ID, then registers
//! [`on_sysprop_change`] with libutils. `report_sysprop_change()` may run on
//! any thread, so the callback cannot cache a `JNIEnv`; instead it asks the
//! cached `JavaVM` — valid process-wide and safe to share across threads —
//! for the calling thread's env and silently skips threads that are not
//! attached to the VM. The global class reference and the method ID are
//! likewise valid from any thread.

use jni::objects::{JClass, JStaticMethodID};
use jni::refs::Global;
use jni::signature::{Primitive, ReturnType};
use jni::JavaVM;
use std::sync::OnceLock;

#[cxx::bridge(namespace = "android_runtime")]
mod ffi {
    unsafe extern "C++" {
        include!("sysprop_change.h");

        /// Registers a libutils sysprop-change callback that invokes
        /// [`on_sysprop_change`]. `priority` orders the process's callbacks;
        /// higher priorities run first. Needs the C++ trampoline in
        /// `ffi/sysprop_change.cpp`: libutils takes a plain function pointer,
        /// which cannot point at a Rust function directly.
        fn register_sysprop_change_callback(priority: i32);

        /// libutils' `android::report_sysprop_change()` (`utils/misc.h`),
        /// bound directly: it runs every callback registered in this process
        /// (on device, also the copy of libutils loaded in the sphal linker
        /// namespace).
        #[namespace = "android"]
        fn report_sysprop_change();
    }

    extern "Rust" {
        fn on_sysprop_change();
    }
}

/// The Java-side target of a sysprop-change report, cached once by
/// [`add_java_change_listener`].
struct JavaChangeListener {
    vm: JavaVM,
    /// Global reference to the `android.os.SystemProperties` class.
    class: Global<JClass<'static>>,
    /// `SystemProperties.callChangeCallbacks()V`.
    call_change_callbacks: JStaticMethodID,
}

static LISTENER: OnceLock<JavaChangeListener> = OnceLock::new();

/// Priority -10000 so the Java broadcast runs after the native listeners.
const CALLBACK_PRIORITY: i32 = -10000;

/// Registers `class`'s static `callChangeCallbacks()V` to be invoked on
/// every sysprop-change report. Only the first call has any effect; the
/// `OnceLock` makes this safe even without the Java-side lock the caller
/// holds.
pub(crate) fn add_java_change_listener(env: &mut jni::Env<'_>, class: &JClass<'_>) {
    if LISTENER.get().is_some() {
        return;
    }
    let (Ok(vm), Ok(class)) = (env.get_java_vm(), env.new_global_ref(class)) else {
        return;
    };
    let Ok(call_change_callbacks) = env.get_static_method_id(
        &class,
        jni::jni_str!("callChangeCallbacks"),
        jni::signature::RuntimeMethodSignature::from_str("()V")
            .expect("valid method signature")
            .method_signature(),
    )
    else {
        // Leaves the NoSuchMethodError pending; no listener is installed.
        return;
    };

    let listener = JavaChangeListener { vm, class, call_change_callbacks };
    if LISTENER.set(listener).is_ok() {
        ffi::register_sysprop_change_callback(CALLBACK_PRIORITY);
    }
}

/// Forwards to libutils' `report_sysprop_change()`, which fans out to every
/// registered callback in the process, including [`on_sysprop_change`].
pub(crate) fn report_sysprop_change() {
    ffi::report_sysprop_change();
}

/// The libutils sysprop-change callback: invokes
/// `SystemProperties.callChangeCallbacks()` on whichever thread reported the
/// change, skipping threads that are not attached to the VM. Never lets an
/// exception escape, since libutils runs the remaining callbacks afterwards.
fn on_sysprop_change() {
    let Some(listener) = LISTENER.get() else {
        return;
    };
    // `with_top_local_frame` fails with ThreadDetached on unattached threads;
    // skip those (ignoring that error) rather than attach, and run on the
    // current top frame without pushing a new one.
    let _ = listener.vm.with_top_local_frame(|env| -> jni::errors::Result<()> {
        // SAFETY: `call_change_callbacks` was resolved from this very class with
        // signature ()V, and both the global reference and the method ID stay
        // valid for the life of the process.
        let result = unsafe {
            env.call_static_method_unchecked(
                &listener.class,
                listener.call_change_callbacks,
                ReturnType::Primitive(Primitive::Void),
                &[],
            )
        };
        // There should not be any exceptions, but we must guarantee none are
        // pending on return.
        if result.is_err() || env.exception_check() {
            env.exception_clear();
            log::error!(target: "SysPropJNI", "Exception pending after sysprop_change!");
        }
        Ok(())
    });
}
