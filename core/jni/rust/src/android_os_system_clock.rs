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

//! `android.os.SystemClock`'s JNI natives.
//!
//! Every method here backs an `@CriticalNative` Java method on
//! `android.os.SystemClock`: it receives no `JNIEnv` and no `this`, takes and
//! returns primitives only, and must not block or allocate.
//!
//! Clock semantics: `uptimeMillis`/`uptimeNanos` read `CLOCK_MONOTONIC`, which
//! stops while the device is in deep sleep; `elapsedRealtime`/
//! `elapsedRealtimeNanos` read `CLOCK_BOOTTIME`, which keeps counting through
//! deep sleep; `currentThreadTimeMillis`/`currentThreadTimeMicro` read
//! `CLOCK_THREAD_CPUTIME_ID`, the CPU time consumed by the calling thread;
//! and `currentTimeMicro` reads the wall clock, `CLOCK_REALTIME`.

/// The clock behind `elapsedRealtime`/`elapsedRealtimeNanos`:
/// `CLOCK_BOOTTIME` where available, the monotonic clock elsewhere.
///
/// On non-Linux hosts this is the real monotonic clock rather than
/// `gettimeofday` wall-clock time, which is what callers of
/// `uptimeMillis`/`currentThreadTime*` actually expect.
#[cfg(any(target_os = "android", target_os = "linux"))]
const ELAPSED_REALTIME_CLOCK: libc::clockid_t = libc::CLOCK_BOOTTIME;
#[cfg(not(any(target_os = "android", target_os = "linux")))]
const ELAPSED_REALTIME_CLOCK: libc::clockid_t = libc::CLOCK_MONOTONIC;

/// Reads `clock` and returns its value in nanoseconds, or 0 if the clock is
/// unavailable. Does not allocate, so it is safe to call from
/// `@CriticalNative` methods.
#[allow(clippy::unnecessary_cast)] // time_t/c_long are 32-bit on 32-bit host targets.
fn clock_nanos(clock: libc::clockid_t) -> i64 {
    let mut ts = libc::timespec { tv_sec: 0, tv_nsec: 0 };
    // SAFETY: `ts` is a valid, writable timespec and `clock` is a libc clock
    // ID constant; clock_gettime writes `ts` only on success.
    let rc = unsafe { libc::clock_gettime(clock, &mut ts) };
    if rc != 0 {
        return 0;
    }
    (ts.tv_sec as i64) * 1_000_000_000 + (ts.tv_nsec as i64)
}

/// Registers `android.os.SystemClock`'s native methods with the JVM.
///
/// Call during JNI startup, before any Java code uses `SystemClock`. Panics
/// if the class or a method is missing; registration failures are fatal.
pub fn register(env: &mut jni::Env<'_>) {
    system_clock::register(env);
}

/// The `android.os.SystemClock` native methods.
#[jni_platform_macros::jni_module("android/os/SystemClock")]
pub mod system_clock {
    use super::{clock_nanos, ELAPSED_REALTIME_CLOCK};
    use libc::{CLOCK_MONOTONIC, CLOCK_REALTIME, CLOCK_THREAD_CPUTIME_ID};

    /// Milliseconds since boot, not counting time spent in deep sleep.
    #[jni_method(critical)]
    pub fn uptimeMillis() -> i64 {
        clock_nanos(CLOCK_MONOTONIC) / 1_000_000
    }

    /// Nanoseconds since boot, not counting time spent in deep sleep.
    #[jni_method(critical)]
    pub fn uptimeNanos() -> i64 {
        clock_nanos(CLOCK_MONOTONIC)
    }

    /// Milliseconds since boot, including time spent in deep sleep.
    #[jni_method(critical)]
    pub fn elapsedRealtime() -> i64 {
        clock_nanos(ELAPSED_REALTIME_CLOCK) / 1_000_000
    }

    /// Nanoseconds since boot, including time spent in deep sleep.
    #[jni_method(critical)]
    pub fn elapsedRealtimeNanos() -> i64 {
        clock_nanos(ELAPSED_REALTIME_CLOCK)
    }

    /// CPU time consumed by the calling thread, in milliseconds.
    #[jni_method(critical)]
    pub fn currentThreadTimeMillis() -> i64 {
        clock_nanos(CLOCK_THREAD_CPUTIME_ID) / 1_000_000
    }

    /// CPU time consumed by the calling thread, in microseconds.
    #[jni_method(critical)]
    pub fn currentThreadTimeMicro() -> i64 {
        clock_nanos(CLOCK_THREAD_CPUTIME_ID) / 1_000
    }

    /// Wall-clock time (`CLOCK_REALTIME`) in microseconds since the epoch.
    #[jni_method(critical)]
    pub fn currentTimeMicro() -> i64 {
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
