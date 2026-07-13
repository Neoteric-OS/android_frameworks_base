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

//! Rust conversion of `android_os_SystemClock.cpp`.
//!
//! Every method here backs an `@CriticalNative` Java method on
//! `android.os.SystemClock`: it receives no `JNIEnv` and no `this`, takes and
//! returns primitives only, and must not block or allocate.
//!
//! Clock semantics match the C++ originals in libutils (`SystemClock.cpp`,
//! `Timers.cpp`): `uptimeMillis`/`uptimeNanos` read `CLOCK_MONOTONIC`, which
//! stops while the device is in deep sleep; `elapsedRealtime`/
//! `elapsedRealtimeNanos` read `CLOCK_BOOTTIME`, which keeps counting through
//! deep sleep; `currentThreadTimeMillis`/`currentThreadTimeMicro` read
//! `CLOCK_THREAD_CPUTIME_ID`, the CPU time consumed by the calling thread;
//! and `currentTimeMicro` reads the wall clock, `CLOCK_REALTIME`.

use jni::sys::jlong;

/// The clock behind `elapsedRealtime`/`elapsedRealtimeNanos`:
/// `CLOCK_BOOTTIME` where available, the monotonic clock elsewhere.
///
/// On non-Linux hosts the C++ (libutils `Timers.cpp`) fell back to
/// `gettimeofday` wall-clock time for *every* clock; here non-Linux hosts get
/// the real monotonic and thread clocks instead, which is what callers of
/// `uptimeMillis`/`currentThreadTime*` actually expect.
#[cfg(any(target_os = "android", target_os = "linux"))]
const ELAPSED_REALTIME_CLOCK: libc::clockid_t = libc::CLOCK_BOOTTIME;
#[cfg(not(any(target_os = "android", target_os = "linux")))]
const ELAPSED_REALTIME_CLOCK: libc::clockid_t = libc::CLOCK_MONOTONIC;

/// Reads `clock` and returns its value in nanoseconds, or 0 if the clock is
/// unavailable — the same fallback as the C++ implementation. Does not
/// allocate, so it is safe to call from `@CriticalNative` methods.
fn clock_nanos(clock: libc::clockid_t) -> jlong {
    let mut ts = libc::timespec { tv_sec: 0, tv_nsec: 0 };
    // SAFETY: `ts` is a valid, writable timespec and `clock` is a libc clock
    // ID constant; clock_gettime writes `ts` only on success.
    let rc = unsafe { libc::clock_gettime(clock, &mut ts) };
    if rc != 0 {
        return 0;
    }
    (ts.tv_sec as jlong) * 1_000_000_000 + (ts.tv_nsec as jlong)
}

/// Registers `android.os.SystemClock`'s native methods with the JVM.
///
/// Call during JNI startup, before any Java code uses `SystemClock`. Panics
/// if the class or a method is missing, matching the C++
/// `RegisterMethodsOrDie` semantics.
pub fn register(env: &mut jni::JNIEnv<'_>) {
    system_clock::register(env);
}

/// The `android.os.SystemClock` native methods.
#[jni_macros::jni_module("android/os/SystemClock")]
pub mod system_clock {
    use super::{clock_nanos, ELAPSED_REALTIME_CLOCK};
    use jni::sys::jlong;
    use libc::{CLOCK_MONOTONIC, CLOCK_REALTIME, CLOCK_THREAD_CPUTIME_ID};

    /// @CriticalNative — milliseconds of `CLOCK_MONOTONIC`; stops in deep sleep.
    /// C++ original: uptimeMillis() from SystemClock.h
    /// JNI sig: "()J"
    #[jni_method(critical)]
    pub fn uptimeMillis() -> jlong {
        clock_nanos(CLOCK_MONOTONIC) / 1_000_000
    }

    /// @CriticalNative — nanoseconds of `CLOCK_MONOTONIC`; stops in deep sleep.
    /// C++ original: uptimeNanos() from SystemClock.h
    /// JNI sig: "()J"
    #[jni_method(critical)]
    pub fn uptimeNanos() -> jlong {
        clock_nanos(CLOCK_MONOTONIC)
    }

    /// @CriticalNative — milliseconds of `CLOCK_BOOTTIME`; counts through deep
    /// sleep.
    /// C++ original: elapsedRealtime() from SystemClock.h
    /// JNI sig: "()J"
    #[jni_method(critical)]
    pub fn elapsedRealtime() -> jlong {
        clock_nanos(ELAPSED_REALTIME_CLOCK) / 1_000_000
    }

    /// @CriticalNative — nanoseconds of `CLOCK_BOOTTIME`; counts through deep
    /// sleep. Note: the C++ function is `elapsedRealtimeNano` (no 's'), but
    /// the Java method is `elapsedRealtimeNanos`.
    /// JNI sig: "()J"
    #[jni_method(critical)]
    pub fn elapsedRealtimeNanos() -> jlong {
        clock_nanos(ELAPSED_REALTIME_CLOCK)
    }

    /// @CriticalNative — CPU time consumed by the calling thread, in
    /// milliseconds.
    /// C++ original: nanoseconds_to_milliseconds(systemTime(SYSTEM_TIME_THREAD))
    /// JNI sig: "()J"
    #[jni_method(critical)]
    pub fn currentThreadTimeMillis() -> jlong {
        clock_nanos(CLOCK_THREAD_CPUTIME_ID) / 1_000_000
    }

    /// @CriticalNative — CPU time consumed by the calling thread, in
    /// microseconds.
    /// C++ original: nanoseconds_to_microseconds(systemTime(SYSTEM_TIME_THREAD))
    /// JNI sig: "()J"
    #[jni_method(critical)]
    pub fn currentThreadTimeMicro() -> jlong {
        clock_nanos(CLOCK_THREAD_CPUTIME_ID) / 1_000
    }

    /// @CriticalNative — wall-clock time (`CLOCK_REALTIME`) in microseconds
    /// since the epoch.
    /// C++ original: gettimeofday-based
    /// JNI sig: "()J"
    #[jni_method(critical)]
    pub fn currentTimeMicro() -> jlong {
        clock_nanos(CLOCK_REALTIME) / 1_000
    }
}

#[cfg(test)]
mod tests {
    use super::system_clock::*;

    #[test]
    fn uptime_nanos_is_monotonic() {
        let first = uptimeNanos();
        let second = uptimeNanos();
        assert!(first > 0, "uptimeNanos should be positive, got {first}");
        assert!(second >= first, "uptimeNanos went backwards: {first} -> {second}");
    }

    #[test]
    fn uptime_millis_matches_nanos_scale() {
        let millis = uptimeMillis();
        let nanos = uptimeNanos();
        assert!(
            millis * 1_000_000 <= nanos,
            "uptimeMillis ({millis}) is ahead of a later uptimeNanos ({nanos})"
        );
    }

    #[test]
    fn elapsed_realtime_includes_uptime() {
        // BOOTTIME counts everything MONOTONIC does plus time spent suspended,
        // so an earlier uptime reading can never exceed a later elapsed one.
        let uptime = uptimeNanos();
        let elapsed = elapsedRealtimeNanos();
        assert!(elapsed >= uptime, "elapsedRealtimeNanos ({elapsed}) < uptimeNanos ({uptime})");
    }

    #[test]
    fn thread_time_advances_with_cpu_work() {
        let start_millis = currentThreadTimeMillis();
        assert!(start_millis >= 0);
        let start_micros = currentThreadTimeMicro();
        // Burn CPU until the thread clock advances two full milliseconds,
        // which guarantees the millisecond reading crosses a boundary. Bound
        // the loop by wall-clock time so a stuck thread clock fails instead
        // of hanging the suite.
        let deadline = std::time::Instant::now() + std::time::Duration::from_secs(30);
        let mut acc: u64 = 0;
        while currentThreadTimeMicro() < start_micros + 2_000 {
            assert!(std::time::Instant::now() < deadline, "thread CPU clock never advanced");
            acc = std::hint::black_box(acc.wrapping_mul(6364136223846793005).wrapping_add(1));
        }
        assert!(currentThreadTimeMillis() > start_millis);
    }

    #[test]
    fn current_time_micro_is_past_2020() {
        // 2020-01-01T00:00:00Z in microseconds since the epoch.
        const JAN_1_2020_MICROS: i64 = 1_577_836_800_000_000;
        assert!(
            currentTimeMicro() > JAN_1_2020_MICROS,
            "currentTimeMicro implausibly early: {}",
            currentTimeMicro()
        );
    }
}
