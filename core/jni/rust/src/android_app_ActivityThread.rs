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

//! Native methods for `android.app.ActivityThread`.
//!
//! Two regular natives, each forwarding to a bionic allocator control and
//! discarding the result. `nPurgePendingResources` calls `mallopt(M_PURGE, 0)`
//! to release unused heap pages back to the kernel, and
//! `nInitZygoteChildHeapProfiling` calls
//! `android_mallopt(M_INIT_ZYGOTE_CHILD_PROFILING, ...)` to mark the process
//! as a profileable zygote child.
//!
//! Device-only: `mallopt`'s `M_PURGE` option and `android_mallopt` are
//! bionic-specific, so the whole module is gated behind
//! `#[cfg(target_os = "android")]` at its `mod` declaration in `lib.rs`.

extern "C" {
    /// Tiny C++ shim that obtains `M_INIT_ZYGOTE_CHILD_PROFILING` directly
    /// from bionic's private allocator header.
    fn android_runtime_init_zygote_child_heap_profiling();
}

/// Releases any heap memory not in use back to the kernel.
///
/// Thin wrapper over bionic `mallopt(M_PURGE)`; the success/failure result is
/// discarded.
fn purge_pending_resources() {
    // SAFETY: `mallopt` is a bionic libc function with no preconditions on
    // these arguments; `M_PURGE` ignores the value. The return value is
    // deliberately discarded.
    unsafe {
        libc::mallopt(libc::M_PURGE, 0);
    }
}

/// Marks this process as a profileable zygote child, initializing profiling
/// infrastructure if needed.
///
/// Thin wrapper over bionic `android_mallopt(M_INIT_ZYGOTE_CHILD_PROFILING)`;
/// the opcode takes no argument buffer and the result is discarded.
fn init_zygote_child_heap_profiling() {
    // SAFETY: the shim takes no arguments and uses the no-argument bionic
    // opcode directly from the platform header.
    unsafe {
        android_runtime_init_zygote_child_heap_profiling();
    }
}

/// Registers `android.app.ActivityThread`'s native methods.
///
/// Call during JNI startup; panics if the class or a method is missing;
/// registration failures are fatal.
pub fn register(env: &mut jni::Env<'_>) {
    activity_thread::register(env);
}

/// The `android.app.ActivityThread` native methods.
#[jni_platform_macros::jni_module("android/app/ActivityThread")]
pub mod activity_thread {
    use jni::objects::JObject;

    /// Releases unused heap pages back to the kernel.
    #[jni_method]
    fn nPurgePendingResources(_env: &mut jni::Env<'_>, _this: JObject) {
        super::purge_pending_resources();
    }

    /// Marks the process as a profileable zygote child.
    #[jni_method]
    fn nInitZygoteChildHeapProfiling(_env: &mut jni::Env<'_>, _this: JObject) {
        super::init_zygote_child_heap_profiling();
    }
}
