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

//! Layout-compatible mirrors of libinput's `android::PointerCoords` and
//! `android::PointerProperties` (input/Input.h), plus a Rust implementation
//! of the packed-axis storage they use.
//!
//! Unlike [`crate::input_event_ffi::KeyEventData`], these are not new
//! bundle types: they replicate the real C++ structs field for field so that
//! values can cross the cxx bridge by value and by slice
//! ([`crate::motion_event_ffi`] declares them as trivial `ExternType`s, and
//! `ffi/motion_event.cpp` carries the matching C++ `static_assert`s). The
//! axis get/set logic is reimplemented here so the MotionEvent JNI can
//! marshal Java `PointerCoords` objects without a C++ call per axis; a
//! parity test in [`crate::motion_event_ffi`] runs the same operations
//! through the real `PointerCoords::setAxisValue` to keep the two
//! implementations in lock step.
//!
//! The module also carries the pure pieces of the Java `PointerCoords`
//! packing protocol — the `mPackedAxisBits`/`mPackedAxisValues` transfer
//! ([`PointerCoords::set_packed_axes`], [`PointerCoords::export_packed_axes`],
//! [`packed_axis_values_capacity`]) and the [`HISTORY_CURRENT`] sentinel — so
//! that [`crate::android_view_motion_event`] keeps only the JNI field traffic.

/// Java `MotionEvent.HISTORY_CURRENT` (-0x80000000): the `historyPos` value
/// that addresses the current sample rather than a historical one.
pub const HISTORY_CURRENT: i32 = i32::MIN;

/// The number of axis values a [`PointerCoords`] can hold (C++ `MAX_AXES`,
/// chosen so that `sizeof(PointerCoords) == 136`).
pub const MAX_AXES: usize = 30;

/// The `AMOTION_EVENT_AXIS_*` ids that `MotionEvent$PointerCoords` stores in
/// dedicated Java float fields (X, Y, PRESSURE, SIZE, TOUCH/TOOL major & minor,
/// ORIENTATION, RELATIVE_X, RELATIVE_Y). Every other axis travels in the
/// packed words, so the JNI marshaling pairs these ids with their field IDs
/// and treats the rest as the packed remainder ([`PointerCoords::packed_remainder_bits`]).
pub const FIELD_BACKED_AXES: [i32; 11] = [0, 1, 2, 3, 4, 5, 6, 7, 8, 27, 28];

/// A set of 64 bits with libutils `BitSet64` semantics (utils/BitSet.h):
/// bit `n` is stored **MSB-first**, at `0x8000_0000_0000_0000 >> n`, so
/// "first marked bit" means the *lowest index*, found from the most
/// significant end. Porting this as LSB-first is the classic mistake; the
/// tests below pin the orientation.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct BitSet64(u64);

impl BitSet64 {
    /// Creates a set from its raw MSB-first representation.
    pub const fn from_bits(bits: u64) -> Self {
        Self(bits)
    }

    /// Returns the raw MSB-first representation.
    pub const fn bits(self) -> u64 {
        self.0
    }

    /// The value with only bit `n` set: `0x8000_0000_0000_0000 >> n`
    /// (`BitSet64::valueForBit`). Panics if `n >= 64`, where the C++ shift
    /// would be undefined behavior.
    pub const fn value_for_bit(n: u32) -> u64 {
        assert!(n < 64, "bit index out of range");
        0x8000_0000_0000_0000_u64 >> n
    }

    /// Returns true if no bit is marked.
    pub const fn is_empty(self) -> bool {
        self.0 == 0
    }

    /// Returns the number of marked bits.
    pub const fn count(self) -> u32 {
        self.0.count_ones()
    }

    /// Returns true if bit `n` is marked.
    pub const fn has_bit(self, n: u32) -> bool {
        self.0 & Self::value_for_bit(n) != 0
    }

    /// Marks bit `n`.
    pub const fn mark_bit(&mut self, n: u32) {
        self.0 |= Self::value_for_bit(n);
    }

    /// Clears bit `n`.
    pub const fn clear_bit(&mut self, n: u32) {
        self.0 &= !Self::value_for_bit(n);
    }

    /// Returns the lowest marked bit index, or `None` when the set is empty
    /// (`BitSet64::firstMarkedBit`, whose result is undefined when empty).
    /// MSB-first storage makes this `leading_zeros`.
    pub const fn first_marked_bit(self) -> Option<u32> {
        if self.is_empty() {
            None
        } else {
            Some(self.0.leading_zeros())
        }
    }

    /// Clears the lowest marked bit and returns its index, or `None` when
    /// the set is empty (`BitSet64::clearFirstMarkedBit`). Calling this in a
    /// loop visits marked bits in ascending index order.
    pub const fn clear_first_marked_bit(&mut self) -> Option<u32> {
        match self.first_marked_bit() {
            Some(n) => {
                self.clear_bit(n);
                Some(n)
            }
            None => None,
        }
    }

    /// Yields the marked bit indices in ascending order — the order axes are
    /// packed into a [`PointerCoords`]. Takes a copy, so the set itself is
    /// left unchanged.
    pub fn iter_marked(self) -> impl Iterator<Item = u32> {
        let mut remaining = self;
        std::iter::from_fn(move || remaining.clear_first_marked_bit())
    }

    /// Returns the number of marked bits with index lower than `n`
    /// (`BitSet64::getIndexOfBit`): the packing index axis `n` has, or would
    /// take, in a [`PointerCoords::values`] array. Panics if `n >= 64`.
    pub const fn index_of_bit(self, n: u32) -> u32 {
        assert!(n < 64, "bit index out of range");
        (self.0 & !(u64::MAX >> n)).count_ones()
    }
}

/// Why [`PointerCoords::set_axis_value`] rejected a value.
#[derive(Clone, Copy, Debug, PartialEq, Eq, thiserror::Error)]
pub enum SetAxisValueError {
    /// The axis id is outside `0..=63`, the range the 64-bit presence
    /// bitfield can index (C++ `NAME_NOT_FOUND`).
    #[error("axis outside the 0..=63 range")]
    AxisOutOfRange,
    /// All [`MAX_AXES`] value slots are already in use (C++ `NO_MEMORY`,
    /// where `PointerCoords::tooManyAxes` also logs a warning; callers that
    /// want the log line have to emit it themselves).
    #[error("PointerCoords has no free axis-value slots")]
    Full,
}

/// Mirror of `android::PointerCoords`: one pointer's axis values, packed in
/// ascending axis order into `values` with the presence bitfield `bits`
/// ([`BitSet64`], MSB-first) saying which axes are stored.
///
/// The layout is pinned to the C++ struct by the const assertions below and
/// the `static_assert`s in `ffi/motion_event.cpp`; `align(8)` mirrors the
/// `__attribute__((aligned(8)))` on the C++ `bits` field, which fixes the
/// alignment at 8 even on 32-bit targets. Entries of `values` at or beyond
/// `bits.count_ones()` are meaningless; [`PointerCoords::cleared`] zeroes
/// them only so that the Rust value starts fully initialized.
#[repr(C, align(8))]
#[derive(Clone, Copy, Debug)]
pub struct PointerCoords {
    /// Presence bitfield of stored axes, with [`BitSet64`] bit order.
    pub bits: u64,
    /// Values of the present axes, packed in ascending axis order.
    pub values: [f32; MAX_AXES],
    /// Whether these coordinates were generated by resampling.
    pub is_resampled: bool,
    /// Explicit tail padding, mirroring the C++ `empty` field.
    pub empty: [u8; 7],
}

impl PointerCoords {
    /// A coordinate set holding no axes, with every byte zeroed. The JNI
    /// uses this before filling in axes.
    pub const fn cleared() -> Self {
        PointerCoords { bits: 0, values: [0.0; MAX_AXES], is_resampled: false, empty: [0; 7] }
    }

    /// Forgets all stored axes and the resampled flag; `values` is left
    /// untouched, it is meaningless once no bit refers to it.
    // Part of the C++ mirror surface; currently exercised only by the tests.
    #[allow(dead_code)]
    pub fn clear(&mut self) {
        self.bits = 0;
        self.is_resampled = false;
    }

    /// Returns true if no axis is stored.
    // Part of the C++ mirror surface; currently exercised only by the tests.
    #[allow(dead_code)]
    pub fn is_empty(&self) -> bool {
        BitSet64(self.bits).is_empty()
    }

    /// The presence word of the axes that are *not* stored in a dedicated Java
    /// field — the axes that travel through `mPackedAxisValues`. The JNI
    /// read-back copies exactly these into the Java `PointerCoords`.
    pub fn packed_remainder_bits(&self) -> BitSet64 {
        let mut bits = BitSet64(self.bits);
        for axis in FIELD_BACKED_AXES {
            bits.clear_bit(axis as u32);
        }
        bits
    }

    /// Returns the value stored for `axis`, or `0.0` when the axis is
    /// absent or outside `0..=63` (C++ `getAxisValue`).
    pub fn axis_value(&self, axis: i32) -> f32 {
        if !(0..=63).contains(&axis) {
            return 0.0;
        }
        let bits = BitSet64(self.bits);
        let axis = axis as u32;
        if !bits.has_bit(axis) {
            return 0.0;
        }
        self.values[bits.index_of_bit(axis) as usize]
    }

    /// Stores `value` for `axis`, shifting later axes' packed values up to
    /// make room when the axis was absent (C++ `setAxisValue`).
    ///
    /// Quirks preserved from the C++: storing `0.0` (or `-0.0`, which
    /// compares equal) into an *absent* axis is a successful no-op, since
    /// absent axes already read as zero — but a stored axis is overwritten
    /// even with zero, and NaN is always stored because `NaN == 0.0` is
    /// false.
    pub fn set_axis_value(&mut self, axis: i32, value: f32) -> Result<(), SetAxisValueError> {
        if !(0..=63).contains(&axis) {
            return Err(SetAxisValueError::AxisOutOfRange);
        }
        let axis = axis as u32;
        let mut bits = BitSet64(self.bits);
        let index = bits.index_of_bit(axis) as usize;
        if !bits.has_bit(axis) {
            if value == 0.0 {
                return Ok(()); // Axes with value 0 do not need to be stored.
            }
            let count = bits.count() as usize;
            if count >= MAX_AXES {
                return Err(SetAxisValueError::Full);
            }
            bits.mark_bit(axis);
            self.bits = bits.0;
            for i in (index + 1..=count).rev() {
                self.values[i] = self.values[i - 1];
            }
        }
        self.values[index] = value;
        Ok(())
    }

    /// Applies the packed-axis words of a Java `PointerCoords` — the
    /// `mPackedAxisBits` presence word and the `mPackedAxisValues` array — on
    /// top of whatever axes are already stored, visiting marked axes in
    /// ascending order (the order the Java side packs values in). `values`
    /// must hold at least `BitSet64(bits).count()` elements — a shortfall is a
    /// caller bug the function asserts against. Stores that fail (more than
    /// [`MAX_AXES`] axes in total) are silently dropped.
    pub fn set_packed_axes(&mut self, bits: u64, values: &[f32]) {
        assert!(
            values.len() >= BitSet64(bits).count() as usize,
            "set_packed_axes: values holds {} elements, need at least {}",
            values.len(),
            BitSet64(bits).count(),
        );
        for (axis, &value) in BitSet64(bits).iter_marked().zip(values) {
            let _ = self.set_axis_value(axis as i32, value);
        }
    }

    /// Writes the values of the axes marked in `axes` into `out_values` in
    /// ascending axis order — packing order — and returns the matching Java
    /// `mPackedAxisBits` presence word (Java mirrors [`BitSet64`]'s MSB-first
    /// layout). Absent axes read as `0.0`. `out_values` must hold at least
    /// `axes.count()` elements — a shortfall would leave the returned
    /// presence word describing more values than were written, so it is a
    /// caller bug the function asserts against.
    pub fn export_packed_axes(&self, axes: BitSet64, out_values: &mut [f32]) -> u64 {
        assert!(
            out_values.len() >= axes.count() as usize,
            "export_packed_axes: out_values holds {} elements, need at least {}",
            out_values.len(),
            axes.count(),
        );
        for (out, axis) in out_values.iter_mut().zip(axes.iter_marked()) {
            *out = self.axis_value(axis as i32);
        }
        axes.bits()
    }
}

/// The capacity a Java `PointerCoords.mPackedAxisValues` array must be grown
/// to for `min_size` packed values: the first power-of-two size, starting at
/// 8, that fits.
pub fn packed_axis_values_capacity(min_size: u32) -> u32 {
    min_size.max(8).next_power_of_two()
}

/// Mirror of `android::PointerProperties`: a pointer's stable id and tool
/// type. The C++ `toolType` field is `enum class ToolType : int` (values
/// `AMOTION_EVENT_TOOL_TYPE_*`, 0 = unknown); it crosses as the raw `i32`
/// the Java `PointerProperties.toolType` field round-trips through.
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct PointerProperties {
    /// The id of the pointer.
    pub id: i32,
    /// The pointer tool type as a raw `AMOTION_EVENT_TOOL_TYPE_*` value.
    pub tool_type: i32,
}

impl PointerProperties {
    /// The C++ `clear()` state: no pointer (`id` -1), unknown tool.
    // Part of the C++ mirror surface; currently exercised only by the tests.
    #[allow(dead_code)]
    pub const fn cleared() -> Self {
        PointerProperties { id: -1, tool_type: 0 }
    }
}

// The layout contract with android::PointerCoords and
// android::PointerProperties; ffi/motion_event.cpp carries the identical
// static_asserts against the real C++ structs, so a change to either side
// fails to compile until both agree. All values are fixed across 32- and
// 64-bit targets: the C++ bits field is explicitly aligned(8) (unlike a
// plain int64, which i386 would align to 4), and everything else is 4 bytes
// or smaller.
const _: () = {
    use std::mem::{align_of, offset_of, size_of};
    assert!(size_of::<PointerCoords>() == 136);
    assert!(align_of::<PointerCoords>() == 8);
    assert!(offset_of!(PointerCoords, bits) == 0);
    assert!(offset_of!(PointerCoords, values) == 8);
    assert!(offset_of!(PointerCoords, is_resampled) == 128);
    assert!(offset_of!(PointerCoords, empty) == 129);
    assert!(size_of::<PointerProperties>() == 8);
    assert!(align_of::<PointerProperties>() == align_of::<i32>());
    assert!(offset_of!(PointerProperties, id) == 0);
    assert!(offset_of!(PointerProperties, tool_type) == 4);
};

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::BTreeMap;

    /// Deterministic 64-bit LCG (Knuth's MMIX constants) so the model tests
    /// need no external randomness.
    struct Lcg(u64);

    impl Lcg {
        fn next(&mut self) -> u64 {
            self.0 = self.0.wrapping_mul(6364136223846793005).wrapping_add(1442695040888963407);
            self.0
        }
    }

    #[test]
    fn bitset_is_msb_first() {
        assert_eq!(BitSet64::value_for_bit(0), 0x8000_0000_0000_0000);
        assert_eq!(BitSet64::value_for_bit(1), 0x4000_0000_0000_0000);
        assert_eq!(BitSet64::value_for_bit(63), 1);
    }

    #[test]
    fn bitset_mark_clear_roundtrip_every_bit() {
        for n in 0..64 {
            let mut set = BitSet64::default();
            assert!(!set.has_bit(n));
            set.mark_bit(n);
            assert!(set.has_bit(n));
            assert_eq!(set.count(), 1);
            assert_eq!(set.first_marked_bit(), Some(n));
            set.clear_bit(n);
            assert!(set.is_empty());
        }
    }

    #[test]
    fn bitset_first_marked_bit_is_lowest_index() {
        let mut set = BitSet64::default();
        set.mark_bit(40);
        set.mark_bit(7);
        set.mark_bit(63);
        assert_eq!(set.first_marked_bit(), Some(7));
        assert_eq!(BitSet64::default().first_marked_bit(), None);
    }

    #[test]
    fn bitset_clear_first_marked_bit_drains_in_ascending_order() {
        let indices = [0, 3, 17, 31, 32, 62, 63];
        let mut set = BitSet64::default();
        for &n in indices.iter().rev() {
            set.mark_bit(n);
        }
        let mut drained = Vec::new();
        while let Some(n) = set.clear_first_marked_bit() {
            drained.push(n);
        }
        assert_eq!(drained, indices);
        assert!(set.is_empty());
    }

    #[test]
    fn bitset_iter_marked_yields_ascending_indices_without_consuming() {
        let indices = [0, 3, 17, 31, 32, 62, 63];
        let mut set = BitSet64::default();
        for &n in &indices {
            set.mark_bit(n);
        }
        assert_eq!(set.iter_marked().collect::<Vec<_>>(), indices);
        // The set is untouched: iter_marked drains a copy.
        assert_eq!(set.count(), indices.len() as u32);
        assert_eq!(BitSet64::default().iter_marked().count(), 0);
    }

    #[test]
    fn bitset_index_of_bit_matches_naive_count() {
        let mut rng = Lcg(0x1234_5678);
        for _ in 0..200 {
            let value = rng.next();
            let set = BitSet64(value);
            for n in 0..64 {
                let naive = (0..n).filter(|&i| set.has_bit(i)).count() as u32;
                assert_eq!(set.index_of_bit(n), naive, "value {value:#x} bit {n}");
            }
        }
    }

    #[test]
    fn bitset_index_of_bit_ignores_the_bit_itself_and_higher() {
        let mut set = BitSet64::default();
        set.mark_bit(5);
        set.mark_bit(10);
        assert_eq!(set.index_of_bit(5), 0);
        assert_eq!(set.index_of_bit(10), 1);
        assert_eq!(set.index_of_bit(63), 2);
        assert_eq!(set.index_of_bit(0), 0);
    }

    #[test]
    fn coords_roundtrip_each_axis_individually() {
        for axis in 0..=63 {
            let mut coords = PointerCoords::cleared();
            coords.set_axis_value(axis, 42.5).unwrap();
            assert_eq!(coords.axis_value(axis), 42.5);
            assert_eq!(coords.bits, BitSet64::value_for_bit(axis as u32));
            assert_eq!(coords.values[0], 42.5);
        }
    }

    #[test]
    fn coords_rejects_out_of_range_axes() {
        let mut coords = PointerCoords::cleared();
        for axis in [-1, 64, i32::MIN, i32::MAX] {
            assert_eq!(coords.set_axis_value(axis, 1.0), Err(SetAxisValueError::AxisOutOfRange));
            assert_eq!(coords.axis_value(axis), 0.0);
        }
        assert!(coords.is_empty());
    }

    #[test]
    fn coords_zero_into_absent_axis_is_not_stored() {
        let mut coords = PointerCoords::cleared();
        coords.set_axis_value(3, 0.0).unwrap();
        coords.set_axis_value(4, -0.0).unwrap(); // -0.0 == 0.0
        assert!(coords.is_empty());
    }

    #[test]
    fn coords_zero_overwrites_a_stored_axis() {
        let mut coords = PointerCoords::cleared();
        coords.set_axis_value(3, 5.0).unwrap();
        coords.set_axis_value(3, 0.0).unwrap();
        assert!(BitSet64(coords.bits).has_bit(3));
        assert_eq!(coords.axis_value(3), 0.0);
    }

    #[test]
    fn coords_nan_is_stored() {
        let mut coords = PointerCoords::cleared();
        coords.set_axis_value(9, f32::NAN).unwrap();
        assert!(BitSet64(coords.bits).has_bit(9));
        assert!(coords.axis_value(9).is_nan());
    }

    #[test]
    fn coords_insertion_shifts_later_axes() {
        let mut coords = PointerCoords::cleared();
        coords.set_axis_value(10, 10.0).unwrap();
        coords.set_axis_value(3, 3.0).unwrap(); // Inserts before axis 10.
        coords.set_axis_value(30, 30.0).unwrap(); // Appends at the end.
        coords.set_axis_value(4, 4.0).unwrap(); // Inserts in the middle.
        assert_eq!(&coords.values[..4], &[3.0, 4.0, 10.0, 30.0]);
        for (axis, value) in [(3, 3.0), (4, 4.0), (10, 10.0), (30, 30.0)] {
            assert_eq!(coords.axis_value(axis), value);
        }
    }

    #[test]
    fn coords_capacity_is_max_axes() {
        let mut coords = PointerCoords::cleared();
        for axis in 0..MAX_AXES as i32 {
            coords.set_axis_value(axis, axis as f32 + 1.0).unwrap();
        }
        let before = (coords.bits, coords.values);
        assert_eq!(coords.set_axis_value(45, 1.0), Err(SetAxisValueError::Full));
        assert_eq!((coords.bits, coords.values), before, "failed insert must not change state");
        // Overwriting a stored axis still works at capacity, and zero into
        // an absent axis is still the successful no-op.
        coords.set_axis_value(0, -7.0).unwrap();
        assert_eq!(coords.axis_value(0), -7.0);
        coords.set_axis_value(45, 0.0).unwrap();
        assert!(!BitSet64(coords.bits).has_bit(45));
    }

    #[test]
    fn coords_clear_forgets_axes_and_resampling_only() {
        let mut coords = PointerCoords::cleared();
        coords.set_axis_value(2, 8.0).unwrap();
        coords.is_resampled = true;
        coords.clear();
        assert!(coords.is_empty());
        assert!(!coords.is_resampled);
        assert_eq!(coords.values[0], 8.0, "clear() leaves the value array untouched");
    }

    /// Drives random set/get operations against a naive `BTreeMap` model of
    /// "axis -> value" and checks the packed representation after every
    /// step: every axis reads back the model value, and the packed array
    /// lists the model's values in ascending axis order.
    #[test]
    fn coords_random_ops_match_naive_model() {
        for seed in 0..8u64 {
            let mut rng = Lcg(0x9E37_79B9 ^ seed);
            let mut coords = PointerCoords::cleared();
            let mut model: BTreeMap<i32, f32> = BTreeMap::new();
            for step in 0..2000 {
                let axis = (rng.next() % 70) as i32 - 3; // Some out-of-range axes.
                let value = match rng.next() % 5 {
                    0 => 0.0,
                    1 => -0.0,
                    2 => -1.5,
                    3 => (rng.next() % 1000) as f32 / 8.0,
                    _ => f32::MAX,
                };
                let result = coords.set_axis_value(axis, value);
                let expected = if !(0..=63).contains(&axis) {
                    Err(SetAxisValueError::AxisOutOfRange)
                } else if !model.contains_key(&axis) && value == 0.0 {
                    Ok(()) // Not stored.
                } else if !model.contains_key(&axis) && model.len() >= MAX_AXES {
                    Err(SetAxisValueError::Full)
                } else {
                    model.insert(axis, value);
                    Ok(())
                };
                assert_eq!(result, expected, "seed {seed} step {step} axis {axis}");

                assert_eq!(BitSet64(coords.bits).count() as usize, model.len());
                for probe in 0..=63 {
                    let expected = model.get(&probe).copied().unwrap_or(0.0);
                    assert_eq!(
                        coords.axis_value(probe).to_bits(),
                        expected.to_bits(),
                        "seed {seed} step {step} probe axis {probe}"
                    );
                }
                for (index, (&axis, &value)) in model.iter().enumerate() {
                    assert!(BitSet64(coords.bits).has_bit(axis as u32));
                    assert_eq!(BitSet64(coords.bits).index_of_bit(axis as u32) as usize, index);
                    assert_eq!(coords.values[index].to_bits(), value.to_bits());
                }
            }
        }
    }

    #[test]
    fn pointer_properties_cleared_matches_cpp() {
        assert_eq!(PointerProperties::cleared(), PointerProperties { id: -1, tool_type: 0 });
    }

    /// The sentinel must be Java's -0x80000000 literal; writing it as a
    /// negated hex literal is the classic overflow mistake this test pins.
    #[test]
    fn history_current_is_javas_sentinel() {
        assert_eq!(HISTORY_CURRENT, -0x8000_0000_i64 as i32);
        assert_eq!(HISTORY_CURRENT, i32::MIN);
    }

    #[test]
    fn packed_axis_values_capacity_grows_in_powers_of_two_from_eight() {
        for (min_size, capacity) in
            [(0, 8), (1, 8), (8, 8), (9, 16), (16, 16), (17, 32), (33, 64), (64, 64)]
        {
            assert_eq!(packed_axis_values_capacity(min_size), capacity, "min_size {min_size}");
        }
    }

    #[test]
    fn set_packed_axes_unpacks_in_ascending_axis_order() {
        let mut coords = PointerCoords::cleared();
        let mut bits = BitSet64::default();
        for axis in [9, 20, 63] {
            bits.mark_bit(axis);
        }
        coords.set_packed_axes(bits.0, &[9.0, 20.0, 63.0]);
        assert_eq!(coords.axis_value(9), 9.0);
        assert_eq!(coords.axis_value(20), 20.0);
        assert_eq!(coords.axis_value(63), 63.0);
        assert_eq!(BitSet64(coords.bits).count(), 3);
    }

    #[test]
    fn set_packed_axes_interleaves_with_directly_stored_axes() {
        // Mimics pointerCoordsToNative: direct axes first, then the packed
        // remainder, which must insert between them by axis order.
        let mut coords = PointerCoords::cleared();
        coords.set_axis_value(0, 100.0).unwrap();
        coords.set_axis_value(8, 108.0).unwrap();
        let mut bits = BitSet64::default();
        bits.mark_bit(4);
        bits.mark_bit(30);
        coords.set_packed_axes(bits.0, &[4.5, 30.5]);
        assert_eq!(&coords.values[..4], &[100.0, 4.5, 108.0, 30.5]);
        assert_eq!(coords.axis_value(4), 4.5);
        assert_eq!(coords.axis_value(30), 30.5);
    }

    #[test]
    fn set_packed_axes_reads_only_count_values() {
        // The extra array capacity beyond the marked-bit count (Java grows
        // mPackedAxisValues in chunks) must be ignored.
        let mut coords = PointerCoords::cleared();
        let mut bits = BitSet64::default();
        bits.mark_bit(2);
        coords.set_packed_axes(bits.0, &[2.5, 99.0, 99.0]);
        assert_eq!(coords.axis_value(2), 2.5);
        assert_eq!(BitSet64(coords.bits).count(), 1);
    }

    #[test]
    fn export_packed_axes_writes_packing_order_and_returns_the_bits_word() {
        let mut coords = PointerCoords::cleared();
        for (axis, value) in [(1, 1.5), (5, 5.5), (40, 40.5)] {
            coords.set_axis_value(axis, value).unwrap();
        }
        let mut axes = BitSet64::default();
        axes.mark_bit(5);
        axes.mark_bit(40);
        axes.mark_bit(63); // Absent from the coords: exported as 0.0.
        let mut out = [f32::NAN; 4];
        let out_bits = coords.export_packed_axes(axes, &mut out);
        assert_eq!(out_bits, axes.0);
        assert_eq!(&out[..3], &[5.5, 40.5, 0.0]);
        assert!(out[3].is_nan(), "entries past the axis count are untouched");
    }

    #[test]
    fn export_packed_axes_of_empty_set_is_a_no_op() {
        let coords = PointerCoords::cleared();
        let mut out = [f32::NAN; 1];
        assert_eq!(coords.export_packed_axes(BitSet64::default(), &mut out), 0);
        assert!(out[0].is_nan());
    }

    #[test]
    #[should_panic(expected = "at least")]
    fn set_packed_axes_shorter_than_count_is_a_caller_bug() {
        let mut coords = PointerCoords::cleared();
        let mut bits = BitSet64::default();
        bits.mark_bit(1);
        bits.mark_bit(2);
        // Two axes marked but only one value supplied: violates the contract.
        coords.set_packed_axes(bits.0, &[1.0]);
    }

    #[test]
    #[should_panic(expected = "at least")]
    fn export_packed_axes_shorter_than_count_is_a_caller_bug() {
        let coords = PointerCoords::cleared();
        let mut axes = BitSet64::default();
        axes.mark_bit(1);
        axes.mark_bit(2);
        // Room for one value but two axes requested: violates the contract.
        let mut out = [0.0; 1];
        coords.export_packed_axes(axes, &mut out);
    }

    /// The transfer the JNI performs must round-trip: exporting a coords'
    /// packed remainder and re-applying it onto direct axes reproduces the
    /// original values.
    #[test]
    fn packed_axes_round_trip_through_export_and_set() {
        let mut rng = Lcg(0xDEAD_BEEF);
        for _ in 0..200 {
            let mut original = PointerCoords::cleared();
            let mut axes = BitSet64::default();
            for _ in 0..(rng.next() % 20) {
                let axis = (rng.next() % 64) as u32;
                let value = (rng.next() % 1000) as f32 / 4.0;
                if original.set_axis_value(axis as i32, value).is_ok() && value != 0.0 {
                    axes.mark_bit(axis);
                }
            }
            let mut packed = [0.0; 64];
            let bits = original.export_packed_axes(axes, &mut packed);

            let mut restored = PointerCoords::cleared();
            restored.set_packed_axes(bits, &packed);
            for axis in 0..64 {
                let expected =
                    if axes.has_bit(axis) { original.axis_value(axis as i32) } else { 0.0 };
                assert_eq!(restored.axis_value(axis as i32).to_bits(), expected.to_bits());
            }
        }
    }

    #[test]
    fn packed_remainder_excludes_the_field_backed_axes() {
        let mut coords = PointerCoords::cleared();
        // A field-backed axis (X=0), a field-backed axis (ORIENTATION=8), and
        // two axes that are not field-backed (HAT_X=15, GENERIC_1=32).
        for axis in [0, 8, 15, 32] {
            coords.set_axis_value(axis, 1.0).unwrap();
        }
        let remainder = coords.packed_remainder_bits();
        assert!(!remainder.has_bit(0), "X is field-backed");
        assert!(!remainder.has_bit(8), "ORIENTATION is field-backed");
        assert!(remainder.has_bit(15), "HAT_X travels packed");
        assert!(remainder.has_bit(32), "GENERIC_1 travels packed");
    }

    #[test]
    fn packed_remainder_of_only_field_backed_axes_is_empty() {
        let mut coords = PointerCoords::cleared();
        for axis in FIELD_BACKED_AXES {
            coords.set_axis_value(axis, 2.0).unwrap();
        }
        assert!(coords.packed_remainder_bits().is_empty());
    }
}
