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

//! libinput's key-code table lookups and input-event id generator.
//!
//! `KeyEvent::getLabel`, `KeyEvent::getKeyCodeFromLabel`, and
//! `InputEvent::nextId` are C++-only, so this module reaches them through a
//! small cxx bridge whose C++ half lives in `ffi/input_event.cpp` (compiled
//! into the `libandroid_runtime_rs_bridge` static library, which links
//! libinput the same way libandroid_runtime does: shared on device, static
//! on host).

use std::ffi::CStr;

#[cxx::bridge(namespace = "android_runtime")]
mod ffi {
    unsafe extern "C++" {
        include!("input_event.h");

        /// `KeyEvent::getLabel`: the symbolic label for an Android key code,
        /// or null when the key code has none. Non-null labels point into
        /// libinput's static keycode table.
        unsafe fn key_event_get_label(key_code: i32) -> *const c_char;

        /// `KeyEvent::getKeyCodeFromLabel` with the JNI's `AKEYCODE_UNKNOWN`
        /// fallback folded in. `label` must be a valid NUL-terminated string.
        unsafe fn key_event_get_key_code_from_label(label: *const c_char) -> i32;

        /// `InputEvent::nextId()`: a fresh id for a new input event.
        fn input_event_next_id() -> i32;
    }
}

/// Returns the symbolic label for `key_code` (e.g. `HOME`), or `None` for
/// key codes libinput has no label for. Labels are static and pure ASCII.
pub(crate) fn key_event_label(key_code: i32) -> Option<&'static CStr> {
    // SAFETY: getLabel takes any key code and returns null or a pointer into
    // libinput's static keycode table.
    let label = unsafe { ffi::key_event_get_label(key_code) };
    if label.is_null() {
        None
    } else {
        // SAFETY: Non-null labels are NUL-terminated entries of the static
        // table, valid for the life of the process.
        Some(unsafe { CStr::from_ptr(label) })
    }
}

/// Returns the key code for a symbolic label, or `AKEYCODE_UNKNOWN` (0) when
/// the label is not recognized.
pub(crate) fn key_code_from_label(label: &CStr) -> i32 {
    // SAFETY: `label` is a valid NUL-terminated string for the duration of
    // the call, as the shim requires.
    unsafe { ffi::key_event_get_key_code_from_label(label.as_ptr()) }
}

/// Returns a fresh globally-unique id for a new input event.
pub(crate) fn next_input_event_id() -> i32 {
    ffi::input_event_next_id()
}
