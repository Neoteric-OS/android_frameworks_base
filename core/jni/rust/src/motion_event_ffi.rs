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

//! cxx bridge to libinput's `android::MotionEvent`.
//!
//! The MotionEvent JNI keeps the C++ object as the single source of truth (a
//! `MotionEvent*` lives in the Java event's `mNativePtr`), so this module
//! binds the C++ member functions on an opaque type instead of porting the
//! event. Member functions whose signatures cxx can express are bound
//! directly; the rest go through the handwritten shims in
//! `ffi/motion_event.{h,cpp}` (types like `ftl::Flags`, `ui::Transform`,
//! `std::bitset`, `std::optional`, and composite operations that own the
//! transform loops of the old JNI). `PointerCoords` and `PointerProperties`
//! cross by layout as the trivial mirrors defined in
//! [`crate::motion_event_compat`].
//!
//! Ownership: every `MotionEvent` is allocated by C++ `new` (via
//! [`ffi::motion_event_new`]) and freed by C++ `delete` (dropping the
//! `UniquePtr`, or [`ffi::motion_event_destroy`] for pointers that round-trip
//! through Java's `mNativePtr` as a `jlong`). Rust never boxes one.
//!
//! Safety policy: an event from [`ffi::motion_event_new`] is
//! default-constructed and must be initialized (`motion_event_initialize`,
//! `motion_event_copy_from`, `motion_event_split_from`, or a successful
//! parcel read) before its accessors are meaningful. Bindings are declared
//! `unsafe fn` exactly when the C++ side indexes unchecked storage from its
//! arguments or requires an initialized event to avoid undefined behavior;
//! bindings whose C++ bounds checks abort the process on misuse (the
//! `LOG(FATAL)` accessors) stay safe.

use crate::motion_event_compat::{PointerCoords, PointerProperties};
use std::ffi::CStr;

// SAFETY: PointerCoords is a #[repr(C, align(8))] field-for-field mirror of
// the trivially-copyable, trivially-destructible android::PointerCoords; the
// const assertions in motion_event_compat.rs and the static_asserts in
// ffi/motion_event.cpp pin size, alignment, and field offsets to each other.
unsafe impl cxx::ExternType for PointerCoords {
    type Id = cxx::type_id!("android::PointerCoords");
    type Kind = cxx::kind::Trivial;
}

// SAFETY: PointerProperties is a #[repr(C)] mirror of the trivially-copyable,
// trivially-destructible android::PointerProperties (two 32-bit fields; the
// C++ enum class ToolType has underlying type int); the const assertions in
// motion_event_compat.rs and the static_asserts in ffi/motion_event.cpp pin
// the layouts to each other.
unsafe impl cxx::ExternType for PointerProperties {
    type Id = cxx::type_id!("android::PointerProperties");
    type Kind = cxx::kind::Trivial;
}

// cxx 1.0.160 tags the inherent impl blocks it generates for member
// functions with `#[automatically_derived]`, which current rustc flags as an
// unused attribute (a future-incompatibility warning); allow it here until
// the cxx prebuilt catches up.
#[allow(unused_attributes)]
#[cxx::bridge(namespace = "android_runtime")]
pub(crate) mod ffi {
    /// The scalar arguments of `MotionEvent::initialize`, bundled so the
    /// bridge call stays readable. Times are in nanoseconds; `flags` is the
    /// raw Java flag word (the shim adds the orientation private flags when
    /// a pointer carries a non-zero orientation, exactly like the old
    /// `nativeInitialize`); `x_offset`/`y_offset` describe the window
    /// transform the shim builds; the cursor position starts invalid and the
    /// raw transform starts as identity, as in the old JNI.
    #[derive(Clone, Copy)]
    pub struct MotionEventInitArgs {
        /// Time of the initial down event, in nanoseconds.
        pub down_time: i64,
        /// Time of this event, in nanoseconds.
        pub event_time: i64,
        /// Event id (callers mint one via `InputEvent::nextId`).
        pub id: i32,
        /// Device id the event came from.
        pub device_id: i32,
        /// Input source (`AINPUT_SOURCE_*` bits).
        pub source: u32,
        /// Logical display id.
        pub display_id: i32,
        /// Action word, including the pointer-index bits.
        pub action: i32,
        /// Button for `ACTION_BUTTON_PRESS`/`RELEASE` (0 otherwise).
        pub action_button: i32,
        /// Raw `AMOTION_EVENT_FLAG_*` word before orientation promotion.
        pub flags: i32,
        /// Edge flags (`AMOTION_EVENT_EDGE_FLAG_*`).
        pub edge_flags: i32,
        /// Meta key state (`AMETA_*`).
        pub meta_state: i32,
        /// Pressed button state (`AMOTION_EVENT_BUTTON_*`).
        pub button_state: i32,
        /// Gesture classification (`AMOTION_EVENT_CLASSIFICATION_*`).
        pub classification: i32,
        /// X translation of the event transform (window offset).
        pub x_offset: f32,
        /// Y translation of the event transform (window offset).
        pub y_offset: f32,
        /// X axis precision of the source device.
        pub x_precision: f32,
        /// Y axis precision of the source device.
        pub y_precision: f32,
    }

    unsafe extern "C++" {
        include!("motion_event.h");

        /// libinput's `android::MotionEvent` (input/Input.h), opaque to
        /// Rust. Allocate with [`motion_event_new`]; see the module docs for
        /// the initialize-before-use contract.
        #[namespace = "android"]
        type MotionEvent;

        /// `android::PointerCoords`, crossing by layout as the trivial
        /// mirror [`crate::motion_event_compat::PointerCoords`].
        #[namespace = "android"]
        type PointerCoords = crate::motion_event_compat::PointerCoords;

        /// `android::PointerProperties`, crossing by layout as the trivial
        /// mirror [`crate::motion_event_compat::PointerProperties`].
        #[namespace = "android"]
        type PointerProperties = crate::motion_event_compat::PointerProperties;

        /// The real C++ `PointerCoords::setAxisValue`, kept callable as the
        /// parity oracle for the Rust port
        /// (`PointerCoords::set_axis_value`). Returns a `status_t`: 0 on
        /// success, `NAME_NOT_FOUND` for an out-of-range axis, `NO_MEMORY`
        /// when full.
        #[cxx_name = "setAxisValue"]
        fn cpp_set_axis_value(self: &mut PointerCoords, axis: i32, value: f32) -> i32;

        /// The real C++ `PointerCoords::getAxisValue`, the parity oracle for
        /// `PointerCoords::axis_value`.
        #[cxx_name = "getAxisValue"]
        fn cpp_axis_value(self: &PointerCoords, axis: i32) -> f32;

        /// `InputEvent::getId`.
        #[cxx_name = "getId"]
        fn id(self: &MotionEvent) -> i32;

        /// `InputEvent::getDeviceId`.
        #[cxx_name = "getDeviceId"]
        fn device_id(self: &MotionEvent) -> i32;

        /// `InputEvent::getSource` (`AINPUT_SOURCE_*` bits).
        #[cxx_name = "getSource"]
        fn source(self: &MotionEvent) -> u32;

        /// `InputEvent::setSource`.
        #[cxx_name = "setSource"]
        fn set_source(self: Pin<&mut MotionEvent>, source: u32);

        /// `MotionEvent::getAction`: the action word, including pointer
        /// index bits.
        #[cxx_name = "getAction"]
        fn action(self: &MotionEvent) -> i32;

        /// `MotionEvent::setAction`.
        #[cxx_name = "setAction"]
        fn set_action(self: Pin<&mut MotionEvent>, action: i32);

        /// `MotionEvent::getActionButton`.
        #[cxx_name = "getActionButton"]
        fn action_button(self: &MotionEvent) -> i32;

        /// `MotionEvent::setActionButton`.
        #[cxx_name = "setActionButton"]
        fn set_action_button(self: Pin<&mut MotionEvent>, button: i32);

        /// `MotionEvent::isTouchEvent`: true when the source and action
        /// describe a touch.
        #[cxx_name = "isTouchEvent"]
        fn is_touch_event(self: &MotionEvent) -> bool;

        /// `MotionEvent::getEdgeFlags`.
        #[cxx_name = "getEdgeFlags"]
        fn edge_flags(self: &MotionEvent) -> i32;

        /// `MotionEvent::setEdgeFlags`.
        #[cxx_name = "setEdgeFlags"]
        fn set_edge_flags(self: Pin<&mut MotionEvent>, edge_flags: i32);

        /// `MotionEvent::getMetaState`.
        #[cxx_name = "getMetaState"]
        fn meta_state(self: &MotionEvent) -> i32;

        /// `MotionEvent::setMetaState`.
        #[cxx_name = "setMetaState"]
        fn set_meta_state(self: Pin<&mut MotionEvent>, meta_state: i32);

        /// `MotionEvent::getButtonState`.
        #[cxx_name = "getButtonState"]
        fn button_state(self: &MotionEvent) -> i32;

        /// `MotionEvent::setButtonState`.
        #[cxx_name = "setButtonState"]
        fn set_button_state(self: Pin<&mut MotionEvent>, button_state: i32);

        /// `MotionEvent::getXPrecision`.
        #[cxx_name = "getXPrecision"]
        fn x_precision(self: &MotionEvent) -> f32;

        /// `MotionEvent::getYPrecision`.
        #[cxx_name = "getYPrecision"]
        fn y_precision(self: &MotionEvent) -> f32;

        /// `MotionEvent::getXCursorPosition`: the cursor position in
        /// transformed coordinates, NaN when invalid.
        #[cxx_name = "getXCursorPosition"]
        fn x_cursor_position(self: &MotionEvent) -> f32;

        /// `MotionEvent::getYCursorPosition`.
        #[cxx_name = "getYCursorPosition"]
        fn y_cursor_position(self: &MotionEvent) -> f32;

        /// `MotionEvent::setCursorPosition`, taking transformed coordinates.
        #[cxx_name = "setCursorPosition"]
        fn set_cursor_position(self: Pin<&mut MotionEvent>, x: f32, y: f32);

        /// `MotionEvent::getDownTime`, in nanoseconds.
        #[cxx_name = "getDownTime"]
        fn down_time(self: &MotionEvent) -> i64;

        /// `MotionEvent::setDownTime`.
        #[cxx_name = "setDownTime"]
        fn set_down_time(self: Pin<&mut MotionEvent>, down_time: i64);

        /// `MotionEvent::getPointerCount`.
        #[cxx_name = "getPointerCount"]
        fn pointer_count(self: &MotionEvent) -> usize;

        /// `MotionEvent::getPointerId`.
        ///
        /// # Safety
        ///
        /// `pointer_index` must be less than `pointer_count()`; the C++
        /// indexes without a bounds check.
        #[cxx_name = "getPointerId"]
        unsafe fn pointer_id(self: &MotionEvent, pointer_index: usize) -> i32;

        /// `MotionEvent::getEventTime`: the time of the latest sample, in
        /// nanoseconds.
        ///
        /// # Safety
        ///
        /// The event must have been initialized (it holds at least one
        /// sample); on a default-constructed event the C++ indexes an empty
        /// vector.
        #[cxx_name = "getEventTime"]
        unsafe fn event_time(self: &MotionEvent) -> i64;

        /// `MotionEvent::getHistorySize`: the number of historical samples
        /// (not counting the current one).
        #[cxx_name = "getHistorySize"]
        fn history_size(self: &MotionEvent) -> usize;

        /// `MotionEvent::getHistoricalEventTime`, in nanoseconds.
        ///
        /// # Safety
        ///
        /// `historical_index` must be at most `history_size()` (the value
        /// `history_size()` itself addresses the current sample); the C++
        /// indexes without a bounds check.
        #[cxx_name = "getHistoricalEventTime"]
        unsafe fn historical_event_time(self: &MotionEvent, historical_index: usize) -> i64;

        /// `MotionEvent::getAxisValue`: the axis value of the latest sample
        /// in transformed coordinates. Invalid indices abort (C++
        /// `LOG(FATAL)`); unknown axes read as 0.
        #[cxx_name = "getAxisValue"]
        fn axis_value(self: &MotionEvent, axis: i32, pointer_index: usize) -> f32;

        /// `MotionEvent::getRawAxisValue`: the axis value of the latest
        /// sample in "compat raw" coordinates. Invalid indices abort.
        #[cxx_name = "getRawAxisValue"]
        fn raw_axis_value(self: &MotionEvent, axis: i32, pointer_index: usize) -> f32;

        /// `MotionEvent::getHistoricalAxisValue`. Invalid indices abort.
        #[cxx_name = "getHistoricalAxisValue"]
        fn historical_axis_value(
            self: &MotionEvent,
            axis: i32,
            pointer_index: usize,
            historical_index: usize,
        ) -> f32;

        /// `MotionEvent::getHistoricalRawAxisValue`. Invalid indices abort.
        #[cxx_name = "getHistoricalRawAxisValue"]
        fn historical_raw_axis_value(
            self: &MotionEvent,
            axis: i32,
            pointer_index: usize,
            historical_index: usize,
        ) -> f32;

        /// `MotionEvent::isResampled`: whether the given sample of the given
        /// pointer was synthesized by resampling. Invalid indices abort.
        #[cxx_name = "isResampled"]
        fn is_resampled(self: &MotionEvent, pointer_index: usize, historical_index: usize) -> bool;

        /// `MotionEvent::findPointerIndex`: the index of the pointer with
        /// the given id, or -1 if there is none.
        #[cxx_name = "findPointerIndex"]
        fn find_pointer_index(self: &MotionEvent, pointer_id: i32) -> isize;

        /// `MotionEvent::offsetLocation`: shifts the event transform by the
        /// given deltas.
        #[cxx_name = "offsetLocation"]
        fn offset_location(self: Pin<&mut MotionEvent>, x_offset: f32, y_offset: f32);

        /// `MotionEvent::scale`: scales positions, offsets, and precision by
        /// a global factor.
        #[cxx_name = "scale"]
        fn scale(self: Pin<&mut MotionEvent>, global_scale_factor: f32);

        /// `MotionEvent::transform`: applies a row-major 3x3 matrix
        /// (SkMatrix layout) to the event transform.
        fn transform(self: Pin<&mut MotionEvent>, matrix: &[f32; 9]);

        /// `MotionEvent::applyTransform`: applies a row-major 3x3 matrix to
        /// the coordinate content only, leaving the event transform alone.
        #[cxx_name = "applyTransform"]
        fn apply_transform(self: Pin<&mut MotionEvent>, matrix: &[f32; 9]);

        /// Allocates a default-constructed `MotionEvent` with C++ `new`. It
        /// must be initialized before its accessors are meaningful.
        fn motion_event_new() -> UniquePtr<MotionEvent>;

        /// Deletes an event with C++ `delete`, for pointers that were
        /// released to Java's `mNativePtr` (null is a no-op, like `delete`).
        ///
        /// # Safety
        ///
        /// `event` must be null or a pointer obtained from
        /// [`motion_event_new`] (directly or via `UniquePtr::into_raw`) that
        /// is not used again afterwards.
        unsafe fn motion_event_destroy(event: *mut MotionEvent);

        /// `MotionEvent::copyFrom`: reinitializes `dest` as a copy of
        /// `source`, with or without the historical samples.
        fn motion_event_copy_from(
            dest: Pin<&mut MotionEvent>,
            source: &MotionEvent,
            keep_history: bool,
        );

        /// `MotionEvent::splitFrom`: reinitializes `dest` with only the
        /// pointers of `source` whose ids are in `split_pointer_ids` (bit
        /// `n` of the word selects pointer id `n`), under a fresh event id.
        /// On an unsplittable combination the C++ logs an error and falls
        /// back to a full copy.
        fn motion_event_split_from(
            dest: Pin<&mut MotionEvent>,
            source: &MotionEvent,
            split_pointer_ids: u32,
            new_event_id: i32,
        );

        /// `MotionEvent::initialize`, owning the transform setup of the old
        /// `nativeInitialize`: builds the window transform from
        /// `args.x_offset`/`args.y_offset`, promotes the orientation private
        /// flags for pointers with a non-zero orientation axis, and maps
        /// each raw coordinate through the inverse transform in place
        /// (`raw_pointer_coords` is mutated). The slices must be the same
        /// length, which becomes the pointer count; a mismatch aborts.
        fn motion_event_initialize(
            event: Pin<&mut MotionEvent>,
            args: &MotionEventInitArgs,
            pointer_properties: &[PointerProperties],
            raw_pointer_coords: &mut [PointerCoords],
        );

        /// `MotionEvent::addSample` plus the transform loop of the old
        /// `nativeAddBatch`: maps each coordinate through the inverse of the
        /// event transform in place, then appends the sample under the
        /// event's current id. `raw_pointer_coords.len()` must equal
        /// `pointer_count()`; a mismatch aborts.
        fn motion_event_add_sample_transformed(
            event: Pin<&mut MotionEvent>,
            event_time: i64,
            raw_pointer_coords: &mut [PointerCoords],
        );

        /// `InputEvent::getDisplayId`, unwrapped from `ui::LogicalDisplayId`
        /// to the raw Java int.
        fn motion_event_get_display_id(event: &MotionEvent) -> i32;

        /// `InputEvent::setDisplayId` from a raw Java int.
        fn motion_event_set_display_id(event: Pin<&mut MotionEvent>, display_id: i32);

        /// `MotionEvent::getFlags` as the raw flag word, private flags
        /// included; callers mask with [`motion_event_private_flag_mask`]
        /// before handing the value to Java.
        fn motion_event_get_flags(event: &MotionEvent) -> i32;

        /// `MotionEvent::setFlags` from a raw flag word. Callers preserve
        /// the event's private flags, as the old `nativeSetFlags` did.
        fn motion_event_set_flags(event: Pin<&mut MotionEvent>, flags: i32);

        /// `AMOTION_EVENT_PRIVATE_FLAG_MASK`: the flag bits that must never
        /// reach or arrive from Java, exposed as a function so the mask
        /// stays in sync with the C++ definition.
        fn motion_event_private_flag_mask() -> i32;

        /// `MotionEvent::getClassification` as its raw
        /// `AMOTION_EVENT_CLASSIFICATION_*` value.
        fn motion_event_get_classification(event: &MotionEvent) -> i32;

        /// `MotionEvent::getToolType` as its raw `AMOTION_EVENT_TOOL_TYPE_*`
        /// value. An out-of-range `pointer_index` aborts (the shim adds the
        /// bounds check the C++ member lacks).
        fn motion_event_get_tool_type(event: &MotionEvent, pointer_index: usize) -> i32;

        /// `MotionEvent::getSurfaceRotation`: the surface rotation as a Java
        /// `Surface.ROTATION_*` value, or -1 when the transform is not a
        /// pure rotation (the C++ `std::nullopt`).
        fn motion_event_get_surface_rotation(event: &MotionEvent) -> i32;

        /// `MotionEvent::getRawPointerCoords`: the stored (untransformed)
        /// coordinates of the latest sample. Invalid indices abort (C++
        /// `LOG(FATAL)`).
        fn motion_event_raw_pointer_coords(
            event: &MotionEvent,
            pointer_index: usize,
        ) -> &PointerCoords;

        /// `MotionEvent::getHistoricalRawPointerCoords`. Invalid indices
        /// abort.
        fn motion_event_historical_raw_pointer_coords(
            event: &MotionEvent,
            pointer_index: usize,
            historical_index: usize,
        ) -> &PointerCoords;

        /// `MotionEvent::getPointerProperties` for one pointer. An
        /// out-of-range `pointer_index` aborts (the shim adds the bounds
        /// check the C++ member lacks).
        fn motion_event_pointer_properties(
            event: &MotionEvent,
            pointer_index: usize,
        ) -> &PointerProperties;

        /// `operator<<(std::ostream&, const MotionEvent&)`: the debug dump
        /// used in exception messages (error paths only; invalid UTF-8 is
        /// replaced rather than propagated).
        fn motion_event_to_string(event: &MotionEvent) -> String;

        /// `MotionEvent::getLabel`: the symbolic label for an axis, or null
        /// when the axis has none. Non-null labels point into libinput's
        /// static axis table.
        unsafe fn motion_event_axis_label(axis: i32) -> *const c_char;

        /// `MotionEvent::getAxisFromLabel` with the JNI's -1 fallback folded
        /// in. `label` must be a valid NUL-terminated string.
        unsafe fn motion_event_axis_from_label(label: *const c_char) -> i32;
    }
}

// MotionEventInitArgs has no consumer outside the tests until the
// android.view.MotionEvent JNI conversion lands.
#[allow(unused_imports)]
pub(crate) use ffi::{MotionEvent, MotionEventInitArgs};

extern "C" {
    /// `MotionEvent::readFromParcel` through `parcelForJavaObject`, which
    /// needs a live `JNIEnv` and therefore cannot cross the cxx bridge; the
    /// C++ side lives in `ffi/motion_event_parcel.cpp`. Returns a
    /// `status_t` (0 on success). `env` must be a valid `JNIEnv` for the
    /// current thread and `parcel_obj` a non-null `android.os.Parcel`.
    pub(crate) fn android_runtime_motion_event_read_from_parcel(
        event: *mut MotionEvent,
        env: *mut jni::sys::JNIEnv,
        parcel_obj: jni::sys::jobject,
    ) -> i32;

    /// `MotionEvent::writeToParcel` through `parcelForJavaObject`; see
    /// [`android_runtime_motion_event_read_from_parcel`].
    pub(crate) fn android_runtime_motion_event_write_to_parcel(
        event: *const MotionEvent,
        env: *mut jni::sys::JNIEnv,
        parcel_obj: jni::sys::jobject,
    ) -> i32;
}

/// Returns the symbolic label for an axis (e.g. `X` for axis 0), or `None`
/// for axes libinput has no label for. Labels are static and pure ASCII.
pub(crate) fn axis_label(axis: i32) -> Option<&'static CStr> {
    // SAFETY: getLabel takes any axis value and returns null or a pointer
    // into libinput's static axis table.
    let label = unsafe { ffi::motion_event_axis_label(axis) };
    if label.is_null() {
        None
    } else {
        // SAFETY: Non-null labels are NUL-terminated entries of the static
        // table, valid for the life of the process.
        Some(unsafe { CStr::from_ptr(label) })
    }
}

/// Returns the axis for a symbolic label, or -1 when the label is not
/// recognized.
pub(crate) fn axis_from_label(label: &CStr) -> i32 {
    // SAFETY: `label` is a valid NUL-terminated string for the duration of
    // the call, as the shim requires.
    unsafe { ffi::motion_event_axis_from_label(label.as_ptr()) }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::motion_event_compat::{BitSet64, SetAxisValueError, MAX_AXES};

    // A few input constants (android/input.h), enough for the tests.
    const AXIS_X: i32 = 0;
    const AXIS_Y: i32 = 1;
    const AXIS_PRESSURE: i32 = 2;
    const AXIS_ORIENTATION: i32 = 8;
    const ACTION_DOWN: i32 = 0;
    const ACTION_MOVE: i32 = 2;
    const SOURCE_TOUCHSCREEN: u32 = 0x1002;
    const TOOL_TYPE_FINGER: i32 = 1;
    const CLASSIFICATION_DEEP_PRESS: i32 = 2;

    /// The `status_t` the C++ `setAxisValue` returns for a Rust result:
    /// `OK` (0), `NAME_NOT_FOUND` (-ENOENT) or `NO_MEMORY` (-ENOMEM), per
    /// utils/Errors.h; errno values are 2 and 12 on both glibc and bionic.
    fn expected_status(result: Result<(), SetAxisValueError>) -> i32 {
        match result {
            Ok(()) => 0,
            Err(SetAxisValueError::AxisOutOfRange) => -2,
            Err(SetAxisValueError::Full) => -12,
        }
    }

    /// Deterministic 64-bit LCG (Knuth's MMIX constants), same generator as
    /// the model tests in motion_event_compat.rs.
    struct Lcg(u64);

    impl Lcg {
        fn next(&mut self) -> u64 {
            self.0 = self.0.wrapping_mul(6364136223846793005).wrapping_add(1442695040888963407);
            self.0
        }
    }

    fn assert_same_packed_state(rust: &PointerCoords, cpp: &PointerCoords, context: &str) {
        assert_eq!(rust.bits, cpp.bits, "{context}: presence bits diverged");
        let count = BitSet64(rust.bits).count() as usize;
        for i in 0..count {
            assert_eq!(
                rust.values[i].to_bits(),
                cpp.values[i].to_bits(),
                "{context}: packed value {i} diverged"
            );
        }
        assert_eq!(rust.is_resampled, cpp.is_resampled, "{context}: isResampled diverged");
    }

    /// Every single-axis store, on the Rust port and the real C++ member
    /// function, must produce the same status, bits, packing, and read-back
    /// — with each side also read through the *other* side's getter, which
    /// pins the struct layout at runtime.
    #[test]
    fn pointer_coords_parity_single_ops() {
        for axis in -2..=65 {
            let mut rust = PointerCoords::cleared();
            let mut cpp = PointerCoords::cleared();
            let rust_status = expected_status(rust.set_axis_value(axis, 42.5));
            let cpp_status = cpp.cpp_set_axis_value(axis, 42.5);
            assert_eq!(rust_status, cpp_status, "status diverged for axis {axis}");
            assert_same_packed_state(&rust, &cpp, &format!("axis {axis}"));
            for probe in -2..=65 {
                assert_eq!(
                    rust.cpp_axis_value(probe).to_bits(),
                    cpp.axis_value(probe).to_bits(),
                    "cross-read diverged for axis {axis} probe {probe}"
                );
            }
        }
    }

    /// The C++ packs values in the same order the Rust port does.
    #[test]
    fn pointer_coords_parity_insertion_order() {
        let mut rust = PointerCoords::cleared();
        let mut cpp = PointerCoords::cleared();
        for (axis, value) in [(10, 10.0), (3, 3.0), (30, 30.0), (4, 4.0), (10, -1.0)] {
            rust.set_axis_value(axis, value).unwrap();
            assert_eq!(cpp.cpp_set_axis_value(axis, value), 0);
        }
        assert_same_packed_state(&rust, &cpp, "insertion order");
        assert_eq!(&cpp.values[..4], &[3.0, 4.0, -1.0, 30.0]);
    }

    /// Randomized op-for-op parity against the real C++ implementation,
    /// covering zero skips, overwrites, NaN, and the 30-axis capacity limit.
    #[test]
    fn pointer_coords_parity_random_ops() {
        for seed in 0..8u64 {
            let mut rng = Lcg(0xC0FF_EE00 ^ seed);
            let mut rust = PointerCoords::cleared();
            let mut cpp = PointerCoords::cleared();
            for step in 0..1500 {
                let axis = (rng.next() % 70) as i32 - 3;
                let value = match rng.next() % 6 {
                    0 => 0.0,
                    1 => -0.0,
                    2 => f32::NAN,
                    3 => -2.25,
                    4 => f32::MIN_POSITIVE,
                    _ => (rng.next() % 10_000) as f32 / 16.0,
                };
                let context = format!("seed {seed} step {step} axis {axis} value {value}");
                let rust_status = expected_status(rust.set_axis_value(axis, value));
                let cpp_status = cpp.cpp_set_axis_value(axis, value);
                assert_eq!(rust_status, cpp_status, "{context}: status diverged");
                assert_same_packed_state(&rust, &cpp, &context);
            }
            // Both sides agree on every axis through both getters.
            for probe in 0..=63 {
                assert_eq!(rust.axis_value(probe).to_bits(), cpp.cpp_axis_value(probe).to_bits());
            }
        }
    }

    /// Filling all thirty slots behaves identically on both sides.
    #[test]
    fn pointer_coords_parity_at_capacity() {
        let mut rust = PointerCoords::cleared();
        let mut cpp = PointerCoords::cleared();
        for axis in 0..MAX_AXES as i32 {
            rust.set_axis_value(axis * 2, 1.5).unwrap();
            assert_eq!(cpp.cpp_set_axis_value(axis * 2, 1.5), 0);
        }
        assert_eq!(expected_status(rust.set_axis_value(61, 9.0)), cpp.cpp_set_axis_value(61, 9.0));
        assert_eq!(expected_status(rust.set_axis_value(61, 0.0)), cpp.cpp_set_axis_value(61, 0.0));
        assert_eq!(expected_status(rust.set_axis_value(0, 7.0)), cpp.cpp_set_axis_value(0, 7.0));
        assert_same_packed_state(&rust, &cpp, "at capacity");
    }

    fn test_args() -> MotionEventInitArgs {
        MotionEventInitArgs {
            down_time: 1_000_000,
            event_time: 2_000_000,
            id: 77,
            device_id: 4,
            source: SOURCE_TOUCHSCREEN,
            display_id: 0,
            action: ACTION_DOWN,
            action_button: 0,
            flags: 0,
            edge_flags: 0,
            meta_state: 0,
            button_state: 0,
            classification: 0,
            x_offset: 5.0,
            y_offset: -3.0,
            x_precision: 1.5,
            y_precision: 2.5,
        }
    }

    fn coords_xy(x: f32, y: f32) -> PointerCoords {
        let mut coords = PointerCoords::cleared();
        coords.set_axis_value(AXIS_X, x).unwrap();
        coords.set_axis_value(AXIS_Y, y).unwrap();
        coords.set_axis_value(AXIS_PRESSURE, 1.0).unwrap();
        coords
    }

    /// A one-pointer touch event at (10, 20) in a window offset by (5, -3).
    fn test_event() -> cxx::UniquePtr<MotionEvent> {
        let mut event = ffi::motion_event_new();
        let properties = [PointerProperties { id: 7, tool_type: TOOL_TYPE_FINGER }];
        let mut coords = [coords_xy(10.0, 20.0)];
        ffi::motion_event_initialize(event.pin_mut(), &test_args(), &properties, &mut coords);
        event
    }

    #[test]
    fn initialize_populates_scalar_fields() {
        let mut event = test_event();
        assert_eq!(event.id(), 77);
        assert_eq!(event.device_id(), 4);
        assert_eq!(event.source(), SOURCE_TOUCHSCREEN);
        assert_eq!(event.action(), ACTION_DOWN);
        assert_eq!(event.down_time(), 1_000_000);
        assert_eq!(event.x_precision(), 1.5);
        assert_eq!(event.y_precision(), 2.5);
        assert_eq!(ffi::motion_event_get_display_id(&event), 0);
        assert_eq!(ffi::motion_event_get_classification(&event), 0);
        assert!(event.is_touch_event());
        assert_eq!(event.history_size(), 0);
        // SAFETY: The event was initialized, so it holds one sample.
        assert_eq!(unsafe { event.event_time() }, 2_000_000);

        event.pin_mut().set_action(ACTION_MOVE);
        assert_eq!(event.action(), ACTION_MOVE);
        event.pin_mut().set_down_time(42);
        assert_eq!(event.down_time(), 42);
        event.pin_mut().set_source(0x2002);
        assert_eq!(event.source(), 0x2002);
        event.pin_mut().set_meta_state(0x11);
        assert_eq!(event.meta_state(), 0x11);
        event.pin_mut().set_button_state(0x2);
        assert_eq!(event.button_state(), 0x2);
        event.pin_mut().set_edge_flags(0x4);
        assert_eq!(event.edge_flags(), 0x4);
        event.pin_mut().set_action_button(0x8);
        assert_eq!(event.action_button(), 0x8);
        ffi::motion_event_set_display_id(event.pin_mut(), 3);
        assert_eq!(ffi::motion_event_get_display_id(&event), 3);
    }

    #[test]
    fn initialize_transforms_coordinates_through_the_window_offset() {
        let event = test_event();
        assert_eq!(event.pointer_count(), 1);
        // The transformed values round-trip back to the Java-visible ones...
        assert_eq!(event.axis_value(AXIS_X, 0), 10.0);
        assert_eq!(event.axis_value(AXIS_Y, 0), 20.0);
        assert_eq!(event.axis_value(AXIS_PRESSURE, 0), 1.0);
        // ...while the stored raw coordinates carry the inverse offset.
        let raw = ffi::motion_event_raw_pointer_coords(&event, 0);
        assert_eq!(raw.axis_value(AXIS_X), 5.0);
        assert_eq!(raw.axis_value(AXIS_Y), 23.0);
        assert!(BitSet64(raw.bits).has_bit(AXIS_X as u32));
        assert!(!raw.is_resampled);
        assert_eq!(event.raw_axis_value(AXIS_X, 0), 5.0);
    }

    #[test]
    fn pointer_accessors_expose_properties() {
        let event = test_event();
        // SAFETY: pointer index 0 is within the event's single pointer.
        assert_eq!(unsafe { event.pointer_id(0) }, 7);
        assert_eq!(ffi::motion_event_get_tool_type(&event, 0), TOOL_TYPE_FINGER);
        let properties = ffi::motion_event_pointer_properties(&event, 0);
        assert_eq!(*properties, PointerProperties { id: 7, tool_type: TOOL_TYPE_FINGER });
        assert_eq!(event.find_pointer_index(7), 0);
        assert_eq!(event.find_pointer_index(8), -1);
    }

    #[test]
    fn flags_round_trip_and_orientation_promotes_private_flags() {
        let mask = ffi::motion_event_private_flag_mask();
        assert_ne!(mask, 0);

        let event = test_event();
        assert_eq!(ffi::motion_event_get_flags(&event) & mask, 0);

        // A non-zero orientation axis promotes the private orientation
        // flags, exactly like the old nativeInitialize.
        let mut event = ffi::motion_event_new();
        let properties = [PointerProperties { id: 1, tool_type: TOOL_TYPE_FINGER }];
        let mut coords = [coords_xy(1.0, 2.0)];
        coords[0].set_axis_value(AXIS_ORIENTATION, 0.5).unwrap();
        ffi::motion_event_initialize(event.pin_mut(), &test_args(), &properties, &mut coords);
        assert_ne!(ffi::motion_event_get_flags(&event) & mask, 0);

        ffi::motion_event_set_flags(event.pin_mut(), 0x3);
        assert_eq!(ffi::motion_event_get_flags(&event), 0x3);
    }

    #[test]
    fn classification_passes_through() {
        let mut event = ffi::motion_event_new();
        let mut args = test_args();
        args.classification = CLASSIFICATION_DEEP_PRESS;
        let properties = [PointerProperties { id: 1, tool_type: TOOL_TYPE_FINGER }];
        let mut coords = [coords_xy(1.0, 2.0)];
        ffi::motion_event_initialize(event.pin_mut(), &args, &properties, &mut coords);
        assert_eq!(ffi::motion_event_get_classification(&event), CLASSIFICATION_DEEP_PRESS);
    }

    #[test]
    fn surface_rotation_is_zero_for_a_translation_only_transform() {
        let event = test_event();
        assert_eq!(ffi::motion_event_get_surface_rotation(&event), 0);
    }

    #[test]
    fn add_sample_appends_history_with_the_event_transform() {
        let mut event = test_event();
        let mut coords = [coords_xy(11.0, 21.0)];
        coords[0].is_resampled = true;
        ffi::motion_event_add_sample_transformed(event.pin_mut(), 3_000_000, &mut coords);

        assert_eq!(event.history_size(), 1);
        // SAFETY: historical index 0 < the sample count of 2.
        assert_eq!(unsafe { event.historical_event_time(0) }, 2_000_000);
        // SAFETY: the event holds two samples.
        assert_eq!(unsafe { event.event_time() }, 3_000_000);
        assert_eq!(event.axis_value(AXIS_X, 0), 11.0);
        assert_eq!(event.historical_axis_value(AXIS_X, 0, 0), 10.0);
        assert_eq!(event.historical_raw_axis_value(AXIS_X, 0, 0), 5.0);
        let historical = ffi::motion_event_historical_raw_pointer_coords(&event, 0, 0);
        assert_eq!(historical.axis_value(AXIS_X), 5.0);
        // The resampled bit crossed the boundary with the sample.
        assert!(event.is_resampled(0, 1));
        assert!(!event.is_resampled(0, 0));
    }

    #[test]
    fn copy_from_preserves_or_drops_history() {
        let mut event = test_event();
        let mut coords = [coords_xy(11.0, 21.0)];
        ffi::motion_event_add_sample_transformed(event.pin_mut(), 3_000_000, &mut coords);

        let mut copy = ffi::motion_event_new();
        ffi::motion_event_copy_from(copy.pin_mut(), &event, true);
        assert_eq!(copy.history_size(), 1);
        assert_eq!(copy.axis_value(AXIS_X, 0), 11.0);
        assert_eq!(copy.historical_axis_value(AXIS_X, 0, 0), 10.0);

        let mut latest_only = ffi::motion_event_new();
        ffi::motion_event_copy_from(latest_only.pin_mut(), &event, false);
        assert_eq!(latest_only.history_size(), 0);
        assert_eq!(latest_only.axis_value(AXIS_X, 0), 11.0);
    }

    #[test]
    fn split_from_keeps_only_the_selected_pointer_ids() {
        let mut event = ffi::motion_event_new();
        let mut args = test_args();
        args.action = ACTION_MOVE;
        let properties = [
            PointerProperties { id: 5, tool_type: TOOL_TYPE_FINGER },
            PointerProperties { id: 9, tool_type: TOOL_TYPE_FINGER },
        ];
        let mut coords = [coords_xy(10.0, 20.0), coords_xy(30.0, 40.0)];
        ffi::motion_event_initialize(event.pin_mut(), &args, &properties, &mut coords);
        assert_eq!(event.find_pointer_index(9), 1);

        let mut split = ffi::motion_event_new();
        ffi::motion_event_split_from(split.pin_mut(), &event, 1 << 9, 123);
        assert_eq!(split.id(), 123);
        assert_eq!(split.pointer_count(), 1);
        // SAFETY: pointer index 0 is within the split event's single pointer.
        assert_eq!(unsafe { split.pointer_id(0) }, 9);
        assert_eq!(split.axis_value(AXIS_X, 0), 30.0);
    }

    #[test]
    fn offset_scale_and_transform_move_coordinates() {
        let mut event = test_event();
        event.pin_mut().offset_location(2.0, 1.0);
        assert_eq!(event.axis_value(AXIS_X, 0), 12.0);
        assert_eq!(event.axis_value(AXIS_Y, 0), 21.0);

        let mut event = test_event();
        event.pin_mut().scale(2.0);
        assert_eq!(event.axis_value(AXIS_X, 0), 20.0);
        assert_eq!(event.x_precision(), 3.0);

        let mut event = test_event();
        // Row-major translation by (100, 200), SkMatrix layout.
        event.pin_mut().transform(&[1.0, 0.0, 100.0, 0.0, 1.0, 200.0, 0.0, 0.0, 1.0]);
        assert_eq!(event.axis_value(AXIS_X, 0), 110.0);
        assert_eq!(event.axis_value(AXIS_Y, 0), 220.0);

        let mut event = test_event();
        event.pin_mut().apply_transform(&[1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0]);
        assert_eq!(event.axis_value(AXIS_X, 0), 10.0);
    }

    #[test]
    fn cursor_position_round_trips_through_the_transform() {
        let mut event = test_event();
        assert!(event.x_cursor_position().is_nan(), "cursor starts invalid");
        event.pin_mut().set_cursor_position(15.0, 25.0);
        assert_eq!(event.x_cursor_position(), 15.0);
        assert_eq!(event.y_cursor_position(), 25.0);
    }

    #[test]
    fn to_string_dumps_the_action() {
        let event = test_event();
        let dump = ffi::motion_event_to_string(&event);
        assert!(dump.contains("action="), "unexpected dump: {dump}");
    }

    #[test]
    fn axis_labels_round_trip() {
        assert_eq!(axis_label(AXIS_X), Some(c"X"));
        assert_eq!(axis_from_label(c"X"), AXIS_X);
        assert_eq!(axis_from_label(c"PRESSURE"), AXIS_PRESSURE);
        assert_eq!(axis_label(-1), None);
        assert_eq!(axis_from_label(c"NOT_AN_AXIS"), -1);
    }

    #[test]
    fn destroy_frees_a_released_event() {
        let event = test_event();
        let raw = event.into_raw();
        // SAFETY: `raw` came from UniquePtr::into_raw and is not used again.
        unsafe { ffi::motion_event_destroy(raw) };
        // SAFETY: null is documented as a no-op.
        unsafe { ffi::motion_event_destroy(std::ptr::null_mut()) };
    }
}
