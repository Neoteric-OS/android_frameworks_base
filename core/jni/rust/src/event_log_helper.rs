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

//! Shared implementation of the `android.util.EventLog` and
//! `android.app.admin.SecurityLog` natives. Each log is an [`EventLog`] value,
//! built by the thin per-class modules that pass in a constructor for their
//! bound event class; the box classes (Integer/Long/Float/String) and
//! `Collection.add` are process-wide `bind_java_type!` bindings shared by both.
//!
//! The write paths are allocation-free: strings arrive as borrowed `&JNIStr`
//! and reach liblog as exact-length Modified UTF-8 bytes (including the
//! two-byte Modified UTF-8 encoding of U+0000). [`PendingEvent`] preserves
//! liblog's original positive byte count or negative errno, and an earlier
//! append error trumps a later transport error.

use jni::objects::{JByteArray, JClass, JIntArray, JObject, JObjectArray, JString};
use jni::refs::{LoaderContext, Reference};
use jni::Env;
use jni_support::JniError;
use log_buffer::LogBuffer;
use log_event_list::{LogContext, LogContextError};
use log_reader::{LogReader, ReadError, ReadMode, StartPosition};

// The `java.lang.Integer`/`Long`/`Float` box's private `value` field is read
// directly, sparing an `intValue()` call per array item. Only the read-only
// `value` getter is bound; the `IsInstanceOf` dispatch in
// [`EventLog::write_array`] uses each type's cached class via [`is_instance`].
jni::bind_java_type! {
    IntegerBox => java.lang.Integer,
    fields { value { sig = jint, get = value } },
}
jni::bind_java_type! {
    LongBox => java.lang.Long,
    fields { value { sig = jlong, get = value } },
}
jni::bind_java_type! {
    FloatBox => java.lang.Float,
    fields { value { sig = jfloat, get = value } },
}

// `java.util.Collection`, the sink `readEvents` fills via `add`.
jni::bind_java_type! {
    pub(crate) Collection => java.util.Collection,
    methods {
        /// `Collection.add(Object)`.
        fn add(value: java.lang.Object) -> jboolean,
    },
}

/// Caches the classes and IDs shared by both event logs. Each register
/// function calls this; the first call wins and later calls are no-ops.
/// Panics if a class or member is missing — a missing binding is fatal.
pub(crate) fn init(env: &mut Env<'_>) {
    IntegerBoxAPI::get(env, &LoaderContext::None)
        .expect("Failed to initialize java.lang.Integer binding");
    LongBoxAPI::get(env, &LoaderContext::None)
        .expect("Failed to initialize java.lang.Long binding");
    FloatBoxAPI::get(env, &LoaderContext::None)
        .expect("Failed to initialize java.lang.Float binding");
    CollectionAPI::get(env, &LoaderContext::None)
        .expect("Failed to initialize java.util.Collection binding");
    // `write_array`'s `IsInstanceOf` dispatch needs the java.lang.String class
    // cached too; force the built-in `JString` class lookup here so a missing
    // class fails fast alongside the others.
    JString::lookup_class(env, &LoaderContext::None)
        .expect("Failed to initialize java.lang.String binding");
}

/// Constructs one instance of a bound Java event class (`EventLog$Event` or
/// `SecurityLog$SecurityEvent`) from an entry's serialized `byte[]`. This
/// type-erases the two distinct `bind_java_type!` event classes behind a
/// single [`EventLog`], each built through its cached `<init>([B)V`
/// constructor.
pub(crate) type ConstructEvent =
    for<'local> fn(&mut Env<'local>, &JByteArray<'local>) -> jni::errors::Result<JObject<'local>>;

/// One Java-visible binary event log: the liblog buffer it writes and reads,
/// bound to a constructor for the Java class read entries are returned as
/// (`EventLog$Event` or `SecurityLog$SecurityEvent`).
pub(crate) struct EventLog {
    buffer: LogBuffer,
    construct_event: ConstructEvent,
}

impl EventLog {
    /// Binds `buffer` to its Java event class. `construct_event` must build
    /// an instance of that class from an entry's `byte[]` (its `<init>([B)V`
    /// constructor).
    pub(crate) fn new(buffer: LogBuffer, construct_event: ConstructEvent) -> EventLog {
        EventLog {
            buffer,
            construct_event,
        }
    }

    /// Logs a single int under `tag`, returning the payload byte count on
    /// success or a negative errno on failure.
    pub(crate) fn write_integer(&self, tag: i32, value: i32) -> i32 {
        let mut event = PendingEvent::new(self.buffer, tag);
        event.append_i32(value);
        event.write()
    }

    /// Logs a single long under `tag`.
    pub(crate) fn write_long(&self, tag: i32, value: i64) -> i32 {
        let mut event = PendingEvent::new(self.buffer, tag);
        event.append_i64(value);
        event.write()
    }

    /// Logs a single float under `tag`.
    pub(crate) fn write_float(&self, tag: i32, value: f32) -> i32 {
        let mut event = PendingEvent::new(self.buffer, tag);
        event.append_f32(value);
        event.write()
    }

    /// Logs a single string under `tag`. A null string logs the literal
    /// `"NULL"` rather than throwing — a logging call should not crash its
    /// caller.
    pub(crate) fn write_string(&self, tag: i32, value: Option<&[u8]>) -> i32 {
        let mut event = PendingEvent::new(self.buffer, tag);
        match value {
            Some(value) => event.append_bytes(value),
            None => event.append_str("NULL"),
        }
        event.write()
    }

    /// Logs up to 255 items of `values` under `tag`, dispatching each item on
    /// its class: String/Integer/Long/Float are serialized, a null item logs
    /// `"NULL"`, a null array logs `"[NULL]"`, and any other type throws
    /// IllegalArgumentException and returns -1 without writing.
    pub(crate) fn write_array(
        &self,
        env: &mut Env<'_>,
        tag: i32,
        values: Option<&JObjectArray<'_>>,
    ) -> Result<i32, JniError> {
        let mut event = PendingEvent::new(self.buffer, tag);
        let Some(values) = values else {
            event.append_str("[NULL]");
            return Ok(event.write());
        };

        // Infallible for a non-null array.
        let count = env.get_array_length(values).unwrap_or(0);
        for index in 0..count.min(255) {
            if event.status() != 0 {
                break;
            }
            // In bounds, so this only fails with a pending JNI exception, which
            // `?` propagates for Java.
            let item = values.get_element(env, index as usize)?;
            let item = env.auto_local(item);
            if item.as_raw().is_null() {
                event.append_str("NULL");
                continue;
            }
            if is_instance::<JString>(env, &item) {
                // SAFETY: `item` was just verified to be a java.lang.String.
                let string = unsafe { JString::from_raw(env, item.as_raw()) };
                // A failure here (normally OOM) leaves its exception pending.
                let chars = string.mutf8_chars(env)?;
                event.append_bytes(chars.as_cstr().to_bytes());
                continue;
            }
            if is_instance::<IntegerBox>(env, &item) {
                // SAFETY: `item` was just verified to be a java.lang.Integer.
                let boxed = unsafe { IntegerBox::from_raw(env, item.as_raw()) };
                event.append_i32(
                    boxed
                        .value(env)
                        .expect("GetIntField(Integer.value) cannot fail"),
                );
            } else if is_instance::<LongBox>(env, &item) {
                // SAFETY: `item` was just verified to be a java.lang.Long.
                let boxed = unsafe { LongBox::from_raw(env, item.as_raw()) };
                event.append_i64(
                    boxed
                        .value(env)
                        .expect("GetLongField(Long.value) cannot fail"),
                );
            } else if is_instance::<FloatBox>(env, &item) {
                // SAFETY: `item` was just verified to be a java.lang.Float.
                let boxed = unsafe { FloatBox::from_raw(env, item.as_raw()) };
                event.append_f32(
                    boxed
                        .value(env)
                        .expect("GetFloatField(Float.value) cannot fail"),
                );
            } else {
                return Err(JniError::IllegalArgument("Invalid payload item type".to_owned()));
            }
        }
        Ok(event.write())
    }

    /// Reads entries from the buffer into `out` (a `java.util.Collection`
    /// receiving instances of the bound event class), oldest first, optionally
    /// restricted to the given event tags, starting at `start_ns` nanoseconds
    /// since the epoch (0 starts at the oldest retained entry).
    /// All values use a signed quotient/remainder followed by a cast to `u32`,
    /// including unusual wrapping for negative values.
    ///
    /// liblog failures throw IOException (`strerror` text, or "Event too short"
    /// for a malformed entry), and the loop stops silently on the first JNI
    /// failure or exception raised by `Collection.add`, leaving that exception
    /// pending for Java.
    pub(crate) fn read_events(
        &self,
        env: &mut Env<'_>,
        mode: ReadMode,
        tags: Option<&JIntArray<'_>>,
        start_ns: i64,
        collection: &Collection<'_>,
    ) -> Result<(), JniError> {
        let start = if start_ns == 0 {
            StartPosition::Oldest
        } else {
            StartPosition::SinceUnixEpochNanos(start_ns)
        };
        let Ok(mut reader) = LogReader::new(mode, start, None) else {
            // Allocation failed; surface its errno as an IOException.
            return Err(JniError::Io(errno_message(libc::ENOMEM)));
        };
        reader.open_buffer(self.buffer);

        let tags: Option<Vec<i32>> = match tags {
            None => None,
            Some(tags) => match read_int_array(env, tags) {
                Ok(tags) => Some(tags),
                // The region matches the array, so a failure here leaves a JNI
                // exception pending; stop and let it propagate.
                Err(_) => return Ok(()),
            },
        };
        // Only one buffer is open here, but a session can multiplex several, so
        // the filter keeps a per-entry buffer check alongside the tag check.
        let filter = EventEntryFilter::new(self.buffer.as_raw(), tags);

        loop {
            let entry = match reader.next_entry() {
                Ok(Some(entry)) => entry,
                Ok(None) => return Ok(()),
                Err(ReadError::InvalidEntry) => {
                    return Err(JniError::Io("Event too short".to_owned()));
                }
                Err(error) => {
                    return Err(JniError::Io(errno_message(error.errno())));
                }
            };

            if !filter.accepts(entry.buffer_id() as i32, entry.payload()) {
                continue;
            }

            let Ok(array) = env.byte_array_from_slice(entry.as_bytes()) else {
                // Allocation failed; the exception is pending for Java.
                return Ok(());
            };
            // A dump can hold thousands of entries; release each iteration's
            // local references (RAII) so they don't accumulate.
            let array = env.auto_local(array);
            // Build an instance of the bound event class from the entry
            // bytes via its `<init>([B)V` constructor.
            let Ok(event) = (self.construct_event)(env, &array) else {
                // Construction failed; the exception is pending for Java.
                return Ok(());
            };
            let event = env.auto_local(event);
            if collection.add(env, &*event).is_err() {
                // `add` raised an exception; stop and leave it pending for Java.
                return Ok(());
            }
        }
    }
}

/// A pending event under construction: the liblog builder and its first error.
/// `write` lets an earlier append error trump a later transmission error.
struct PendingEvent {
    ctx: Option<LogContext>,
    status: i32,
}

impl PendingEvent {
    fn new(buffer: LogBuffer, tag: i32) -> PendingEvent {
        let ctx = LogContext::new(buffer.as_raw() as u32, tag as u32);
        let status = if ctx.is_some() {
            0
        } else {
            context_unavailable(buffer)
        };
        PendingEvent { ctx, status }
    }

    /// The first error so far, 0 when none; the array loop breaks once this is
    /// non-zero.
    fn status(&self) -> i32 {
        self.status
    }

    fn append_i32(&mut self, value: i32) {
        self.append(|ctx| ctx.try_append_i32(value));
    }

    fn append_i64(&mut self, value: i64) {
        self.append(|ctx| ctx.try_append_i64(value));
    }

    fn append_f32(&mut self, value: f32) {
        self.append(|ctx| ctx.try_append_f32(value));
    }

    fn append_str(&mut self, value: &str) {
        self.append(|ctx| ctx.try_append_str(value));
    }

    fn append_bytes(&mut self, value: &[u8]) {
        self.append(|ctx| ctx.try_append_bytes(value));
    }

    /// Appends one element, retaining the context when liblog reports an
    /// error so `write` can still transmit the successfully serialized prefix.
    fn append(&mut self, append: impl FnOnce(&mut LogContext) -> Result<(), LogContextError>) {
        record_append(&mut self.status, self.ctx.as_mut(), |ctx| {
            append(ctx).map_err(LogContextError::code)
        });
    }

    /// Writes the event and returns the payload byte count on success,
    /// otherwise the first error. An append error still transmits the
    /// (truncated) list first and is then returned in place of the
    /// transmission result.
    fn write(self) -> i32 {
        finish_event(self.status, self.ctx, |ctx| {
            ctx.write().unwrap_or_else(LogContextError::code)
        })
    }
}

/// Applies one state transition of the event-list builder. Kept generic so
/// the error-precedence behavior can be tested without JNI or a live logd.
fn record_append<C>(
    status: &mut i32,
    ctx: Option<&mut C>,
    append: impl FnOnce(&mut C) -> Result<(), i32>,
) {
    if *status != 0 {
        return;
    }
    let Some(ctx) = ctx else {
        return;
    };
    if let Err(error) = append(ctx) {
        *status = error;
    }
}

/// Always transmits an available serialized prefix, then gives an earlier
/// append error precedence over the transport result, as liblog's event-list
/// builder does.
fn finish_event<C>(status: i32, ctx: Option<C>, write: impl FnOnce(C) -> i32) -> i32 {
    let Some(ctx) = ctx else {
        return status;
    };
    let write_result = write(ctx);
    if status != 0 {
        status
    } else {
        write_result
    }
}

/// The status when no `LogContext` could be created. For the security buffer
/// the overwhelmingly likely cause is security logging being disabled, which
/// surfaces as `-EPERM`; otherwise only allocation failure remains, and a null
/// context makes every liblog call — including the final write — return
/// `-EBADF`.
fn context_unavailable(buffer: LogBuffer) -> i32 {
    match buffer {
        LogBuffer::Security => -libc::EPERM,
        _ => -libc::EBADF,
    }
}

/// `IsInstanceOf` against the cached class of the bound Java type `T`; JNI
/// reports no errors here. Callers filter `null` before dispatching (JNI's
/// `IsInstanceOf` treats `null` as an instance of every class).
fn is_instance<T: Reference>(env: &mut Env<'_>, obj: &JObject<'_>) -> bool {
    let Ok(class) = T::lookup_class(env, &LoaderContext::None) else {
        return false;
    };
    let class: &JClass = class.as_ref();
    env.is_instance_of(obj, class).unwrap_or(false)
}

/// The four-byte native-endian event type tag prefixed to every payload.
const TAG_SIZE: usize = 4;

/// The event type tag opening a binary buffer entry's payload: four
/// native-endian bytes. `None` when the payload is too short to carry one.
fn event_tag(payload: &[u8]) -> Option<i32> {
    Some(i32::from_ne_bytes(payload.get(..TAG_SIZE)?.try_into().ok()?))
}

/// Decides which entries a `readEvents` call emits: those from the requested
/// buffer, and — when a tag filter is set — those whose event tag is in it.
///
/// The buffer id and tags are raw `i32`s (matching `log_id_t` and the
/// Java-supplied tag array) so the filter stays free of JNI and `log_buffer`
/// and is unit-testable on its own. It short-circuits buffer first, then tag.
struct EventEntryFilter {
    buffer_id: i32,
    tags: Option<Vec<i32>>,
}

impl EventEntryFilter {
    /// A filter for `buffer_id`; `tags` of `None` accepts every tag.
    fn new(buffer_id: i32, tags: Option<Vec<i32>>) -> EventEntryFilter {
        EventEntryFilter { buffer_id, tags }
    }

    /// Whether an entry read from `entry_buffer_id` carrying `payload` is
    /// emitted. An entry whose payload is too short to hold a tag is rejected
    /// when a tag filter is set.
    fn accepts(&self, entry_buffer_id: i32, payload: &[u8]) -> bool {
        entry_buffer_id == self.buffer_id
            && match &self.tags {
                None => true,
                Some(tags) => event_tag(payload).is_some_and(|tag| tags.contains(&tag)),
            }
    }
}

/// Reads a Java `int[]` into an owned `Vec`.
fn read_int_array(
    env: &mut Env<'_>,
    array: &JIntArray<'_>,
) -> Result<Vec<i32>, jni::errors::Error> {
    let mut values = vec![0; env.get_array_length(array)? as usize];
    env.get_int_array_region(array, 0, &mut values)?;
    Ok(values)
}

/// `strerror(errno)` as a message string.
fn errno_message(errno: i32) -> String {
    std::io::Error::from_raw_os_error(errno).to_string()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::cell::Cell;

    #[test]
    fn successful_append_returns_write_length() {
        let mut status = 0;
        let mut ctx = ();
        record_append(&mut status, Some(&mut ctx), |_| Ok(()));
        assert_eq!(finish_event(status, Some(ctx), |_| 37), 37);
    }

    #[test]
    fn write_error_is_returned_without_append_error() {
        assert_eq!(finish_event(0, Some(()), |_| -libc::EIO), -libc::EIO);
    }

    #[test]
    fn first_append_error_stops_later_appends() {
        let mut status = 0;
        let mut ctx = ();
        record_append(&mut status, Some(&mut ctx), |_| Err(-libc::E2BIG));
        record_append(&mut status, Some(&mut ctx), |_| panic!("later append ran"));
        assert_eq!(status, -libc::E2BIG);
    }

    #[test]
    fn append_error_wins_but_write_still_runs() {
        let wrote = Cell::new(false);
        let result = finish_event(-libc::E2BIG, Some(()), |_| {
            wrote.set(true);
            37
        });
        assert!(wrote.get());
        assert_eq!(result, -libc::E2BIG);
    }

    #[test]
    fn append_error_wins_over_write_error() {
        assert_eq!(
            finish_event(-libc::E2BIG, Some(()), |_| -libc::EIO),
            -libc::E2BIG
        );
    }

    #[test]
    fn missing_context_skips_append_and_write() {
        let mut status = -libc::EBADF;
        record_append::<()>(&mut status, None, |_| panic!("append ran"));
        assert_eq!(
            finish_event::<()>(status, None, |_| panic!("write ran")),
            -libc::EBADF
        );
    }

    #[test]
    fn unavailable_context_matches_legacy_statuses() {
        assert_eq!(context_unavailable(LogBuffer::Security), -libc::EPERM);
        assert_eq!(context_unavailable(LogBuffer::Events), -libc::EBADF);
    }

    /// Builds a payload whose leading tag is `tag`.
    fn payload_for_tag(tag: i32) -> Vec<u8> {
        let mut payload = tag.to_ne_bytes().to_vec();
        payload.extend_from_slice(b"body");
        payload
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
