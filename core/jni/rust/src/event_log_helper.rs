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
//! `android.app.admin.SecurityLog` natives, replacing the C++
//! `EventLogHelper<LogID, EventClassDescriptor>` template
//! (`core/jni/eventlog_helper.h`). The two template instantiations become two
//! [`EventLog`] values, built by the thin per-class modules from their cached
//! `#[java_class]` IDs; the box classes (Integer/Long/Float/String) and
//! `Collection.add` are process-wide singletons shared by both.
//!
//! The write paths stay allocation-free like the C++: strings arrive as
//! borrowed `&JavaStr` and reach liblog through a `Cow` that only allocates
//! when the Modified UTF-8 is not already valid UTF-8. The C++ returned
//! liblog's write result to Java — the payload byte count or a negative
//! errno — which the Rust `log_event_list` wrapper discards, so
//! [`PendingEvent`] reconstructs it: the count from
//! [`EventListLayout`]'s exact mirror of liblog's accounting, the errno from
//! the failure cases documented on [`PendingEvent::write`].

use crate::event_wire_format::{EventEntryFilter, EventListLayout, Overflow};
use jni::objects::{GlobalRef, JFieldID, JIntArray, JMethodID, JObject, JObjectArray, JString};
use jni::signature::{Primitive, ReturnType};
use jni::strings::JavaStr;
use jni::sys::{jfieldID, jmethodID, jvalue};
use jni::JNIEnv;
use jni_support::JniError;
use log_buffer::LogBuffer;
use log_event_list::__private_api::{LogContext, LogContextError};
use log_reader::{LogReader, ReadError, ReadMode, StartPosition};
use std::borrow::Cow;
use std::time::{Duration, SystemTime};

/// Cached IDs for the `java.lang.Integer` box; `value` is read directly, as
/// the C++ did, sparing an `intValue()` call per array item.
#[jni_macros::java_class("java/lang/Integer")]
struct IntegerBox {
    #[field("int")]
    value: jfieldID,
}

/// Cached IDs for the `java.lang.Long` box.
#[jni_macros::java_class("java/lang/Long")]
struct LongBox {
    #[field("long")]
    value: jfieldID,
}

/// Cached IDs for the `java.lang.Float` box.
#[jni_macros::java_class("java/lang/Float")]
struct FloatBox {
    #[field("float")]
    value: jfieldID,
}

/// Only the class reference is cached; it anchors the `IsInstanceOf` dispatch
/// in [`EventLog::write_array`].
#[jni_macros::java_class("java/lang/String")]
struct StringClass {}

/// Cached IDs for `java.util.Collection`, the sink `readEvents` fills.
#[jni_macros::java_class("java/util/Collection")]
struct Collection {
    #[method("(java.lang.Object) -> boolean")]
    add: jmethodID,
}

/// Caches the classes and IDs shared by both event logs. Each register
/// function calls this; the first call wins and later calls are no-ops.
/// Panics if a class or member is missing, matching the C++ `FindClassOrDie`
/// semantics.
pub(crate) fn init(env: &mut JNIEnv<'_>) {
    IntegerBox::init(env);
    LongBox::init(env);
    FloatBox::init(env);
    StringClass::init(env);
    Collection::init(env);
}

/// One Java-visible binary event log: the liblog buffer it writes and reads,
/// bound to the Java class read entries are returned as (`EventLog$Event` or
/// `SecurityLog$SecurityEvent`).
pub(crate) struct EventLog {
    buffer: LogBuffer,
    event_class: &'static GlobalRef,
    event_ctor: JMethodID,
}

impl EventLog {
    /// Binds `buffer` to its Java event class. `event_ctor` must be the
    /// cached `<init>([B)V` constructor of `event_class`.
    pub(crate) fn new(
        buffer: LogBuffer,
        event_class: &'static GlobalRef,
        event_ctor: JMethodID,
    ) -> EventLog {
        EventLog { buffer, event_class, event_ctor }
    }

    /// Logs a single int under `tag`, returning the byte count or negative
    /// errno the C++ `writeEvent(int, int)` returned.
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
    /// `"NULL"`: the C++ deliberately did not throw NullPointerException,
    /// finding it "sort of mean for a logging function to be all crashy".
    pub(crate) fn write_string(&self, tag: i32, value: Option<&JavaStr>) -> i32 {
        let mut event = PendingEvent::new(self.buffer, tag);
        match value {
            Some(value) => event.append_str(&Cow::from(&**value)),
            None => event.append_str("NULL"),
        }
        event.write()
    }

    /// Logs up to 255 items of `values` under `tag`, dispatching each item on
    /// its class like the C++: String/Integer/Long/Float are serialized, a
    /// null item logs `"NULL"`, a null array logs `"[NULL]"`, and any other
    /// type throws IllegalArgumentException and returns -1 without writing.
    pub(crate) fn write_array(
        &self,
        env: &mut JNIEnv<'_>,
        tag: i32,
        values: &JObjectArray<'_>,
    ) -> i32 {
        let mut event = PendingEvent::new(self.buffer, tag);
        if values.as_raw().is_null() {
            event.append_str("[NULL]");
            return event.write();
        }

        // Infallible for a non-null array.
        let count = env.get_array_length(values).unwrap_or(0);
        for index in 0..count.min(255) {
            if event.status() != 0 {
                break;
            }
            let Ok(item) = env.get_object_array_element(values, index) else {
                // Unreachable (the index is in bounds); an exception is
                // pending for Java.
                return -1;
            };
            if item.as_raw().is_null() {
                event.append_str("NULL");
                continue;
            }
            if is_instance(env, &item, &StringClass::get().class) {
                let string = JString::from(item);
                // SAFETY: `string` was just verified to be a java.lang.String.
                if let Ok(chars) = unsafe { env.get_string_unchecked(&string) } {
                    event.append_str(&Cow::from(&*chars));
                }
                let _ = env.delete_local_ref(string);
                continue;
            }
            if is_instance(env, &item, &IntegerBox::get().class) {
                event.append_i32(int_field(env, &item, IntegerBox::get().value));
            } else if is_instance(env, &item, &LongBox::get().class) {
                event.append_i64(long_field(env, &item, LongBox::get().value));
            } else if is_instance(env, &item, &FloatBox::get().class) {
                event.append_f32(float_field(env, &item, FloatBox::get().value));
            } else {
                JniError::IllegalArgument("Invalid payload item type".to_owned()).throw_on(env);
                return -1;
            }
            let _ = env.delete_local_ref(item);
        }
        event.write()
    }

    /// Reads entries from the buffer into `out` (a `java.util.Collection`
    /// receiving instances of the bound event class), oldest first, optionally
    /// restricted to the given event tags, starting at `start_ns` nanoseconds
    /// since the epoch (0 starts at the oldest retained entry).
    ///
    /// Mirrors the C++ readEvents loop: liblog failures throw IOException
    /// (`strerror` text, or "Event too short" for a malformed entry), and the
    /// loop stops silently on the first JNI failure or exception raised by
    /// `Collection.add`, leaving that exception pending for Java.
    pub(crate) fn read_events(
        &self,
        env: &mut JNIEnv<'_>,
        mode: ReadMode,
        tags: Option<&JIntArray<'_>>,
        start_ns: i64,
        out: &JObject<'_>,
    ) {
        let start = if start_ns == 0 {
            StartPosition::Oldest
        } else {
            StartPosition::Since(SystemTime::UNIX_EPOCH + Duration::from_nanos(start_ns as u64))
        };
        let Ok(mut reader) = LogReader::new(mode, start, None) else {
            // The C++ threw the allocation failure's errno.
            throw_io_errno(env, libc::ENOMEM);
            return;
        };
        reader.open_buffer(self.buffer);

        let tags: Option<Vec<i32>> = match tags {
            None => None,
            Some(tags) => match read_int_array(env, tags) {
                Ok(tags) => Some(tags),
                // Unreachable (the region matches the array); an exception is
                // pending for Java.
                Err(_) => return,
            },
        };
        // Only one buffer is open here, but a session can multiplex several, so
        // the filter keeps the C++'s per-entry buffer check alongside the tag
        // check.
        let filter = EventEntryFilter::new(self.buffer.as_raw(), tags);

        // SAFETY: `add` was produced by Collection::init, so it is a valid,
        // non-null method ID for the lifetime of the process.
        let add = unsafe { JMethodID::from_raw(Collection::get().add) };

        loop {
            let entry = match reader.next_entry() {
                Ok(Some(entry)) => entry,
                Ok(None) => return,
                Err(ReadError::InvalidEntry) => {
                    throw_io_message(env, "Event too short");
                    return;
                }
                Err(error) => {
                    throw_io_errno(env, error.errno());
                    return;
                }
            };

            if !filter.accepts(entry.buffer_id() as i32, entry.payload()) {
                continue;
            }

            let Ok(array) = env.byte_array_from_slice(entry.as_bytes()) else {
                // Allocation failed; the exception is pending for Java.
                return;
            };
            // SAFETY: `event_ctor` is the `<init>([B)V` constructor of
            // `event_class` (see EventLog::new) and `array` is the byte[] it
            // takes.
            let constructed = unsafe {
                env.new_object_unchecked(
                    self.event_class,
                    self.event_ctor,
                    &[jvalue { l: array.as_raw() }],
                )
            };
            let Ok(event) = constructed else {
                // Construction failed; the exception is pending for Java.
                return;
            };
            // SAFETY: `add` is Collection.add(Object), `out` is a Collection,
            // and `event` is an Object.
            let added = unsafe {
                env.call_method_unchecked(
                    out,
                    add,
                    ReturnType::Primitive(Primitive::Boolean),
                    &[jvalue { l: event.as_raw() }],
                )
            };
            if added.is_err() {
                // The C++ stopped on ExceptionCheck after the add.
                return;
            }
            // A dump can hold thousands of entries; release each iteration's
            // local references like the C++ ScopedLocalRefs did.
            let _ = env.delete_local_ref(event);
            let _ = env.delete_local_ref(array);
        }
    }
}

/// A pending event under construction: the liblog builder, the mirrored size
/// accounting, and the first error — reproducing the C++
/// `android_log_event_list`, whose `write` let existing errors trump
/// transmission errors.
struct PendingEvent {
    buffer: LogBuffer,
    ctx: Option<LogContext>,
    layout: EventListLayout,
    status: i32,
}

impl PendingEvent {
    fn new(buffer: LogBuffer, tag: i32) -> PendingEvent {
        let ctx = LogContext::new(buffer.as_raw() as u32, tag as u32);
        let status = if ctx.is_some() { 0 } else { context_unavailable(buffer) };
        PendingEvent { buffer, ctx, layout: EventListLayout::new(), status }
    }

    /// The first error so far, 0 when none — the C++ `ctx.status()` that the
    /// array loop breaks on.
    fn status(&self) -> i32 {
        self.status
    }

    fn append_i32(&mut self, value: i32) {
        self.append(EventListLayout::push_i32, |ctx| ctx.append_i32(value));
    }

    fn append_i64(&mut self, value: i64) {
        self.append(EventListLayout::push_i64, |ctx| ctx.append_i64(value));
    }

    fn append_f32(&mut self, value: f32) {
        self.append(EventListLayout::push_f32, |ctx| ctx.append_f32(value));
    }

    fn append_str(&mut self, value: &str) {
        self.append(|layout| layout.push_str(value), |ctx| ctx.append_str(value));
    }

    /// Appends one element, gated on the mirrored accounting: when the
    /// element would not fit, liblog would report `-EIO` while keeping the
    /// elements already serialized, so the layout rejects it before liblog
    /// sees it and the context survives to transmit the truncated list —
    /// exactly the C++ overflow behavior.
    fn append(
        &mut self,
        push: impl FnOnce(&mut EventListLayout) -> Result<(), Overflow>,
        append: impl FnOnce(LogContext) -> Result<LogContext, LogContextError>,
    ) {
        if self.status != 0 {
            return;
        }
        let Some(ctx) = self.ctx.take() else {
            return;
        };
        match push(&mut self.layout) {
            Err(Overflow) => {
                self.status = -libc::EIO;
                self.ctx = Some(ctx);
            }
            Ok(()) => match append(ctx) {
                Ok(ctx) => self.ctx = Some(ctx),
                // Unreachable while the layout mirrors liblog exactly; the
                // consumed context makes write() report the error without
                // transmitting.
                Err(LogContextError) => self.status = -libc::EIO,
            },
        }
    }

    /// Writes the event and returns what the C++ returned to Java: the
    /// payload byte count on success, otherwise the first error. Like
    /// `android_log_event_list::write`, an append error still transmits the
    /// (truncated) list first and is then returned in place of the
    /// transmission result.
    fn write(self) -> i32 {
        let Some(ctx) = self.ctx else {
            return self.status;
        };
        let result = ctx.write();
        if self.status != 0 {
            return self.status;
        }
        match result {
            Ok(()) => self.layout.written_bytes() as i32,
            Err(LogContextError) => transmission_failure(self.buffer),
        }
    }
}

/// The status when no `LogContext` could be created. For the security buffer
/// the overwhelmingly likely cause is security logging being disabled, which
/// the C++ surfaced as `write_to_log`'s `-EPERM`; otherwise only allocation
/// failure remains, where the C++'s null context made every liblog call —
/// including the final write — return `-EBADF`.
fn context_unavailable(buffer: LogBuffer) -> i32 {
    match buffer {
        LogBuffer::Security => -libc::EPERM,
        _ => -libc::EBADF,
    }
}

/// The status when liblog refused the final write. The Rust wrapper discards
/// the negated errno the C++ passed to Java, so this reconstructs it:
/// security writes are refused with `-EPERM` (logd's uid check or logging
/// being disabled, neither of which involves errno), while events-buffer
/// failures come from the writev to logd's socket, whose errno is normally
/// still in the thread's errno slot.
fn transmission_failure(buffer: LogBuffer) -> i32 {
    if buffer == LogBuffer::Security {
        return -libc::EPERM;
    }
    match std::io::Error::last_os_error().raw_os_error() {
        Some(errno) if errno > 0 => -errno,
        _ => -libc::EIO,
    }
}

/// `IsInstanceOf` against a cached class; JNI reports no errors here.
fn is_instance(env: &mut JNIEnv<'_>, obj: &JObject<'_>, class: &GlobalRef) -> bool {
    env.is_instance_of(obj, class).unwrap_or(false)
}

/// Reads `Integer.value` through its cached field ID — the C++ `GetIntField`.
fn int_field(env: &mut JNIEnv<'_>, obj: &JObject<'_>, field: jfieldID) -> i32 {
    // SAFETY: `field` is IntegerBox's cached `value` ID and `obj` is an
    // Integer (checked by the caller).
    let field = unsafe { JFieldID::from_raw(field) };
    env.get_field_unchecked(obj, field, ReturnType::Primitive(Primitive::Int))
        .and_then(|value| value.i())
        .expect("GetIntField cannot fail")
}

/// Reads `Long.value` through its cached field ID.
fn long_field(env: &mut JNIEnv<'_>, obj: &JObject<'_>, field: jfieldID) -> i64 {
    // SAFETY: `field` is LongBox's cached `value` ID and `obj` is a Long
    // (checked by the caller).
    let field = unsafe { JFieldID::from_raw(field) };
    env.get_field_unchecked(obj, field, ReturnType::Primitive(Primitive::Long))
        .and_then(|value| value.j())
        .expect("GetLongField cannot fail")
}

/// Reads `Float.value` through its cached field ID.
fn float_field(env: &mut JNIEnv<'_>, obj: &JObject<'_>, field: jfieldID) -> f32 {
    // SAFETY: `field` is FloatBox's cached `value` ID and `obj` is a Float
    // (checked by the caller).
    let field = unsafe { JFieldID::from_raw(field) };
    env.get_field_unchecked(obj, field, ReturnType::Primitive(Primitive::Float))
        .and_then(|value| value.f())
        .expect("GetFloatField cannot fail")
}

/// Copies a Java `int[]` into a `Vec` — the C++ `ScopedIntArrayRO`.
fn read_int_array(
    env: &mut JNIEnv<'_>,
    array: &JIntArray<'_>,
) -> Result<Vec<i32>, jni::errors::Error> {
    let mut values = vec![0; env.get_array_length(array)? as usize];
    env.get_int_array_region(array, 0, &mut values)?;
    Ok(values)
}

/// nativehelper's `jniThrowIOException` parity: an IOException whose message
/// is the `strerror` text for `errno`.
fn throw_io_errno(env: &mut JNIEnv<'_>, errno: i32) {
    throw_io_message(env, &errno_message(errno));
}

fn throw_io_message(env: &mut JNIEnv<'_>, message: &str) {
    let _ = env.throw_new("java/io/IOException", message);
}

/// `strerror(errno)`, the message nativehelper produced.
fn errno_message(errno: i32) -> String {
    // SAFETY: strerror returns a pointer to a string with process lifetime.
    // It is not strictly thread-safe, but neither was nativehelper's use.
    unsafe { std::ffi::CStr::from_ptr(libc::strerror(errno)) }.to_string_lossy().into_owned()
}
