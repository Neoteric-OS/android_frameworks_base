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

//! Rust side of `ffi/input_event_compat.h`: plain-data bundles that cross
//! the C++/Rust boundary by layout, not through the cxx bridge.
//!
//! `core/jni/android_view_KeyEvent.cpp` is a permanent facade — libandroid's
//! NDK surface (`AKeyEvent_fromJava`) needs real `android::KeyEvent` C++
//! objects, so the old mangled helper signatures stay in C++ — and it
//! exchanges events with the Rust implementation
//! ([`crate::android_view_key_event`]) as [`KeyEventData`] values. The two
//! declarations of the struct are pinned to each other by the const
//! assertions below and the matching `static_assert`s in the header.

/// Every field of an `android::KeyEvent`, flattened so the C++ facade and the
/// Rust JNI code can exchange events without sharing libinput's C++ types.
///
/// `ffi/input_event_compat.h` declares the C++ mirror. The 64-bit times lead
/// so the struct packs without padding; `source` is `InputEvent`'s `uint32_t`
/// source reinterpreted as the Java `int` it round-trips through, and the
/// times are in nanoseconds, as in both `android::KeyEvent` and the Java
/// event's `mDownTime`/`mEventTime` fields.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct KeyEventData {
    pub down_time: i64,
    pub event_time: i64,
    pub id: i32,
    pub device_id: i32,
    pub source: i32,
    pub display_id: i32,
    pub action: i32,
    pub flags: i32,
    pub key_code: i32,
    pub scan_code: i32,
    pub meta_state: i32,
    pub repeat_count: i32,
    pub hmac: [u8; 32],
}

/// The all-zero HMAC libinput assigns to events that were not signed by the
/// system (`attestation/HmacKeyManager.h`). Java `KeyEvent`s built in-process
/// carry a null `mHmac`, which maps to this value.
pub const INVALID_HMAC: [u8; 32] = [0; 32];

/// Resolves the contents of a Java `KeyEvent.mHmac` field (`None` when the
/// field is null) to the hmac for a native event: only a 32-byte array
/// passes through, anything else falls back to [`INVALID_HMAC`]. The caller
/// logs the wrong-length case, matching the C++ JNI.
pub fn hmac_from_java_field(bytes: Option<&[u8]>) -> [u8; 32] {
    bytes.and_then(|bytes| bytes.try_into().ok()).unwrap_or(INVALID_HMAC)
}

// The layout contract with ffi/input_event_compat.h; the header carries the
// identical static_asserts, so a change to either side fails to compile
// until both agree.
const _: () = {
    use std::mem::{align_of, offset_of, size_of};
    assert!(size_of::<KeyEventData>() == 88);
    // i386 aligns i64 to 4, so pin the alignment to the ABI rather than a
    // fixed value; the C++ header asserts the same expression.
    assert!(align_of::<KeyEventData>() == align_of::<i64>());
    assert!(offset_of!(KeyEventData, down_time) == 0);
    assert!(offset_of!(KeyEventData, event_time) == 8);
    assert!(offset_of!(KeyEventData, id) == 16);
    assert!(offset_of!(KeyEventData, device_id) == 20);
    assert!(offset_of!(KeyEventData, source) == 24);
    assert!(offset_of!(KeyEventData, display_id) == 28);
    assert!(offset_of!(KeyEventData, action) == 32);
    assert!(offset_of!(KeyEventData, flags) == 36);
    assert!(offset_of!(KeyEventData, key_code) == 40);
    assert!(offset_of!(KeyEventData, scan_code) == 44);
    assert!(offset_of!(KeyEventData, meta_state) == 48);
    assert!(offset_of!(KeyEventData, repeat_count) == 52);
    assert!(offset_of!(KeyEventData, hmac) == 56);
};

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn null_hmac_field_falls_back_to_invalid() {
        assert_eq!(hmac_from_java_field(None), INVALID_HMAC);
    }

    #[test]
    fn wrong_length_hmac_falls_back_to_invalid() {
        assert_eq!(hmac_from_java_field(Some(&[])), INVALID_HMAC);
        assert_eq!(hmac_from_java_field(Some(&[0xab; 31])), INVALID_HMAC);
        assert_eq!(hmac_from_java_field(Some(&[0xab; 33])), INVALID_HMAC);
    }

    #[test]
    fn valid_hmac_passes_through() {
        let hmac: [u8; 32] = std::array::from_fn(|i| i as u8);
        assert_eq!(hmac_from_java_field(Some(&hmac)), hmac);
    }
}
