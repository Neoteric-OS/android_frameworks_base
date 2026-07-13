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

//! A model of liblog's binary event wire format: the size accounting of
//! `android_log_context` (`system/logging/liblog/log_event_list.cpp`) and the
//! event tag that opens every binary buffer entry.
//!
//! `android.util.EventLog.writeEvent` promises Java "the number of bytes
//! written", which the C++ JNI took from liblog's return value. The Rust
//! `log_event_list` wrapper reports only success or failure, so
//! [`EventListLayout`], tracked alongside the appends, reconstructs the
//! count. liblog's arithmetic is deterministic given the append sequence,
//! including its quirk of advancing the write position by a string's
//! untruncated size, so the reconstruction is exact.

/// liblog's `LOGGER_ENTRY_MAX_PAYLOAD`: the largest payload (event tag plus
/// serialized list) the transport delivers for one entry.
const LOGGER_ENTRY_MAX_PAYLOAD: usize = 4068;

/// liblog's `MAX_EVENT_PAYLOAD`: the serialization buffer — the payload minus
/// the tag.
const MAX_EVENT_PAYLOAD: usize = LOGGER_ENTRY_MAX_PAYLOAD - TAG_SIZE;

/// The four-byte native-endian event type tag prefixed to every payload.
const TAG_SIZE: usize = 4;

// Serialized element sizes, from the `android_event_*_t` structs in
// `log/log.h`: a one-byte type marker plus the value; a string's bytes follow
// a four-byte length; a list holds a one-byte element count.
const LIST_HEADER_SIZE: usize = 2;
const INT_SIZE: usize = 5;
const LONG_SIZE: usize = 9;
const FLOAT_SIZE: usize = 5;
const STRING_HEADER_SIZE: usize = 5;

/// The error when an element does not fit in the event: liblog reports `-EIO`
/// and refuses every later append.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct Overflow;

/// The predicted layout of one pending event list, mirroring the write
/// position and element count of liblog's `android_log_context`.
///
/// Call the `push_*` method matching each `LogContext` append, then
/// [`EventListLayout::written_bytes`] for the count a successful write
/// reports. A push mirrors liblog's fit check exactly, so it returns
/// [`Overflow`] precisely when liblog's own append would.
pub(crate) struct EventListLayout {
    pos: usize,
    element_count: u32,
    overflow: bool,
}

impl EventListLayout {
    /// The layout of a fresh `create_android_logger` context: an empty
    /// implicit list.
    pub(crate) fn new() -> EventListLayout {
        EventListLayout { pos: LIST_HEADER_SIZE, element_count: 0, overflow: false }
    }

    /// Accounts for `android_log_write_int32`.
    pub(crate) fn push_i32(&mut self) -> Result<(), Overflow> {
        self.push_fixed(INT_SIZE)
    }

    /// Accounts for `android_log_write_int64`.
    pub(crate) fn push_i64(&mut self) -> Result<(), Overflow> {
        self.push_fixed(LONG_SIZE)
    }

    /// Accounts for `android_log_write_float32`.
    pub(crate) fn push_f32(&mut self) -> Result<(), Overflow> {
        self.push_fixed(FLOAT_SIZE)
    }

    /// Accounts for `android_log_write_string8_len`.
    pub(crate) fn push_str(&mut self, value: &str) -> Result<(), Overflow> {
        if self.overflow {
            return Err(Overflow);
        }
        // liblog receives a pointer and measures it with strnlen, so a NUL
        // byte inside `value` ends the logged string.
        let len = value.bytes().position(|byte| byte == 0).unwrap_or(value.len());
        let needed = STRING_HEADER_SIZE + len;
        if self.pos + needed > MAX_EVENT_PAYLOAD {
            // liblog truncates the copied bytes to the remaining room but
            // still advances its position by the untruncated `needed`,
            // spoiling every later append; only "no room for a single byte"
            // is an error.
            if MAX_EVENT_PAYLOAD as i64 - self.pos as i64 - 1 - 4 <= 0 {
                self.overflow = true;
                return Err(Overflow);
            }
        }
        self.element_count += 1;
        self.pos += needed;
        Ok(())
    }

    fn push_fixed(&mut self, needed: usize) -> Result<(), Overflow> {
        if self.overflow || self.pos + needed > MAX_EVENT_PAYLOAD {
            self.overflow = true;
            return Err(Overflow);
        }
        self.element_count += 1;
        self.pos += needed;
        Ok(())
    }

    /// The byte count a successful write reports to Java: the four-byte tag
    /// plus the serialized list — sent bare, without the list header, when it
    /// holds at most one element — capped at the largest payload the
    /// transport delivers.
    pub(crate) fn written_bytes(&self) -> usize {
        let list = if self.element_count <= 1 { self.pos - LIST_HEADER_SIZE } else { self.pos };
        (TAG_SIZE + list).min(LOGGER_ENTRY_MAX_PAYLOAD)
    }
}

/// The event type tag opening a binary buffer entry's payload: four
/// native-endian bytes. `None` when the payload is too short to carry one.
pub(crate) fn event_tag(payload: &[u8]) -> Option<i32> {
    Some(i32::from_ne_bytes(payload.get(..TAG_SIZE)?.try_into().ok()?))
}

/// Decides which entries a `readEvents` call emits: those from the requested
/// buffer, and — when a tag filter is set — those whose event tag is in it.
///
/// The buffer id and tags are raw `i32`s (matching `log_id_t` and the
/// Java-supplied tag array) so the filter stays free of JNI and `log_buffer`
/// and is unit-testable on its own. It reproduces the C++ readEvents
/// short-circuit order exactly: buffer first, then tag.
pub(crate) struct EventEntryFilter {
    buffer_id: i32,
    tags: Option<Vec<i32>>,
}

impl EventEntryFilter {
    /// A filter for `buffer_id`; `tags` of `None` accepts every tag.
    pub(crate) fn new(buffer_id: i32, tags: Option<Vec<i32>>) -> EventEntryFilter {
        EventEntryFilter { buffer_id, tags }
    }

    /// Whether an entry read from `entry_buffer_id` carrying `payload` is
    /// emitted. An entry whose payload is too short to hold a tag is rejected
    /// when a tag filter is set (the C++ compared against uninitialized bytes;
    /// this rejects it instead).
    pub(crate) fn accepts(&self, entry_buffer_id: i32, payload: &[u8]) -> bool {
        entry_buffer_id == self.buffer_id
            && match &self.tags {
                None => true,
                Some(tags) => event_tag(payload).is_some_and(|tag| tags.contains(&tag)),
            }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // The largest string that fits a fresh event untruncated:
    // MAX_EVENT_PAYLOAD - LIST_HEADER_SIZE - STRING_HEADER_SIZE.
    const LARGEST_STRING: usize = 4057;

    #[test]
    fn empty_event_is_just_the_tag() {
        assert_eq!(EventListLayout::new().written_bytes(), 4);
    }

    #[test]
    fn single_values_are_sent_bare() {
        // A lone element loses the two-byte list header, so an int event is
        // tag + type byte + four value bytes.
        let mut layout = EventListLayout::new();
        layout.push_i32().unwrap();
        assert_eq!(layout.written_bytes(), 9);

        let mut layout = EventListLayout::new();
        layout.push_i64().unwrap();
        assert_eq!(layout.written_bytes(), 13);

        let mut layout = EventListLayout::new();
        layout.push_f32().unwrap();
        assert_eq!(layout.written_bytes(), 9);

        let mut layout = EventListLayout::new();
        layout.push_str("hello").unwrap();
        assert_eq!(layout.written_bytes(), 14);
    }

    #[test]
    fn multiple_values_keep_the_list_header() {
        let mut layout = EventListLayout::new();
        layout.push_i32().unwrap();
        layout.push_i32().unwrap();
        assert_eq!(layout.written_bytes(), 4 + 2 + 5 + 5);
    }

    #[test]
    fn interior_nul_ends_a_string() {
        // liblog measures the string with strnlen.
        let mut layout = EventListLayout::new();
        layout.push_str("ab\0cd").unwrap();
        assert_eq!(layout.written_bytes(), 4 + 5 + 2);
    }

    #[test]
    fn the_write_event_array_item_cap_never_overflows_on_ints() {
        // The JNI caps arrays at 255 items; 255 ints must serialize cleanly.
        let mut layout = EventListLayout::new();
        for _ in 0..255 {
            layout.push_i32().unwrap();
        }
        assert_eq!(layout.written_bytes(), 4 + 2 + 255 * 5);
    }

    #[test]
    fn largest_string_fills_the_event_exactly() {
        let mut layout = EventListLayout::new();
        layout.push_str(&"x".repeat(LARGEST_STRING)).unwrap();
        assert_eq!(layout.written_bytes(), 4 + STRING_HEADER_SIZE + LARGEST_STRING);
        // The buffer is now full; nothing else fits.
        assert_eq!(layout.push_i32(), Err(Overflow));
    }

    #[test]
    fn oversized_string_reports_the_transport_cap() {
        // liblog truncates the copied bytes but advances by the untruncated
        // size, and the transport caps the payload, so the reported count is
        // the cap — matching what the C++ returned.
        let mut layout = EventListLayout::new();
        layout.push_str(&"x".repeat(5000)).unwrap();
        assert_eq!(layout.written_bytes(), LOGGER_ENTRY_MAX_PAYLOAD);
        // The overshoot position rejects everything afterwards.
        assert_eq!(layout.push_str("y"), Err(Overflow));
        assert_eq!(layout.push_i64(), Err(Overflow));
    }

    #[test]
    fn overflow_is_sticky() {
        let mut layout = EventListLayout::new();
        layout.push_str(&"x".repeat(LARGEST_STRING)).unwrap();
        assert_eq!(layout.push_i32(), Err(Overflow));
        // Even an element that would fit on its own stays rejected.
        assert_eq!(layout.push_str(""), Err(Overflow));
    }

    #[test]
    fn event_tag_reads_the_native_endian_prefix() {
        let mut payload = 0x12345678i32.to_ne_bytes().to_vec();
        payload.extend_from_slice(b"rest of the event");
        assert_eq!(event_tag(&payload), Some(0x12345678));
        assert_eq!(event_tag(&(-7i32).to_ne_bytes()), Some(-7));
    }

    #[test]
    fn event_tag_rejects_short_payloads() {
        assert_eq!(event_tag(&[]), None);
        assert_eq!(event_tag(&[1, 2, 3]), None);
    }

    /// Builds a payload whose leading tag is `tag`.
    fn payload_for_tag(tag: i32) -> Vec<u8> {
        let mut payload = tag.to_ne_bytes().to_vec();
        payload.extend_from_slice(b"body");
        payload
    }

    #[test]
    fn filter_without_tags_accepts_only_the_buffer() {
        let filter = EventEntryFilter::new(2, None);
        assert!(filter.accepts(2, &payload_for_tag(42)));
        assert!(!filter.accepts(3, &payload_for_tag(42)));
        // Buffer is checked before the tag, so a short payload still passes
        // when it is on the right buffer and no tag filter is set.
        assert!(filter.accepts(2, &[]));
    }

    #[test]
    fn filter_with_tags_requires_buffer_and_membership() {
        let filter = EventEntryFilter::new(2, Some(vec![10, 20]));
        assert!(filter.accepts(2, &payload_for_tag(10)));
        assert!(filter.accepts(2, &payload_for_tag(20)));
        assert!(!filter.accepts(2, &payload_for_tag(30)));
        // Right tag, wrong buffer: rejected by the buffer check first.
        assert!(!filter.accepts(5, &payload_for_tag(10)));
        // Payload too short to carry a tag: rejected once a filter is set.
        assert!(!filter.accepts(2, &[1, 2]));
    }
}
