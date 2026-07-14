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

//! JNI native method implementations for `android.view.MotionEvent`.
//!
//! `android.view.MotionEvent` keeps the C++ `android::MotionEvent` (libinput)
//! as the single source of truth: the Java event's `mNativePtr` always holds
//! a C++-`new`-allocated `MotionEvent*`, reached through the cxx bridge in
//! [`crate::motion_event_ffi`]. Rust never boxes one — events are allocated
//! by `motion_event_new` (dropping the `UniquePtr`, or `motion_event_destroy`
//! for raw pointers, is the only way one is freed), and
//! `nativeInitialize`/`nativeCopy`/`nativeSplit`/`nativeReadFromParcel` reuse
//! a non-zero incoming pointer instead of reallocating. `recycle()` is a
//! Java-pool concern only: the native event is retained and reinitialized in
//! place when the Java object is reused.
//!
//! The `rs_motionEvent_*` C exports below back the permanent `core/jni/
//! android_view_MotionEvent.cpp` facade, whose mangled signatures the rest of
//! libandroid_runtime calls (libandroid's `AMotionEvent_fromJava` and friends
//! need real `android::MotionEvent` C++ objects), with events crossing the
//! language boundary as raw `MotionEvent*`.
//!
//! Java `PointerCoords` marshalling splits each pointer's axes into the
//! eleven axes stored as Java float fields and the packed remainder carried
//! by `mPackedAxisBits`/`mPackedAxisValues`; the pure packing logic lives in
//! [`crate::motion_event_compat`], and this module only moves the values
//! across JNI (copying the packed array in and out with the
//! `Get`/`SetFloatArrayRegion` region calls).

use crate::motion_event_compat::{
    packed_axis_values_capacity, BitSet64, PointerCoords, PointerProperties, FIELD_BACKED_AXES,
    HISTORY_CURRENT,
};
use crate::motion_event_ffi::{ffi, MotionEvent};
use jni::objects::{JFloatArray, JObject, JObjectArray};
use jni::refs::LoaderContext;
use jni::sys::jobject;
use jni_support::{JLongHandle, JniError};

/// libinput's cap on pointers per event (input.h `MAX_POINTERS`); sizes the
/// stack buffer of `nativeAddBatch`.
const MAX_POINTERS: usize = 16;

// The Java `android.view.MotionEvent` and its `PointerCoords`/`PointerProperties`
// inner classes, as `bind_java_type!` bindings. Field Java names and signatures
// match the actual Java class fields exactly so the cached IDs resolve to the
// right fields. `recycle` is not bound: nothing here calls it. The `J` prefix
// distinguishes these Java-object wrappers from the native
// `MotionEvent`/`PointerCoords`/`PointerProperties` types.

// `android.view.MotionEvent`. `mNativePtr` is read and written; `obtain()`
// is the pool allocator.
jni::bind_java_type! {
    pub(crate) JMotionEvent => android.view.MotionEvent,
    methods {
        /// `MotionEvent.obtain()` — pool-allocates an uninitialized event.
        static fn obtain() -> JMotionEvent,
    },
    fields {
        native_ptr { sig = jlong, name = "mNativePtr" },
    },
}

// `android.view.MotionEvent$PointerCoords`. The eleven field-backed axes are
// read/written through `FIELD_BACKED_JAVA_AXES`; the packed words and resampled
// flag through their own accessors.
jni::bind_java_type! {
    pub(crate) JPointerCoords => "android.view.MotionEvent$PointerCoords",
    fields {
        packed_axis_bits { sig = jlong, name = "mPackedAxisBits" },
        packed_axis_values { sig = jfloat[], name = "mPackedAxisValues" },
        x { sig = jfloat, name = "x" },
        y { sig = jfloat, name = "y" },
        pressure { sig = jfloat, name = "pressure" },
        size { sig = jfloat, name = "size" },
        touch_major { sig = jfloat, name = "touchMajor" },
        touch_minor { sig = jfloat, name = "touchMinor" },
        tool_major { sig = jfloat, name = "toolMajor" },
        tool_minor { sig = jfloat, name = "toolMinor" },
        orientation { sig = jfloat, name = "orientation" },
        relative_x { sig = jfloat, name = "relativeX" },
        relative_y { sig = jfloat, name = "relativeY" },
        is_resampled { sig = jboolean, name = "isResampled" },
    },
}

// `android.view.MotionEvent$PointerProperties`.
jni::bind_java_type! {
    pub(crate) JPointerProperties => "android.view.MotionEvent$PointerProperties",
    fields {
        id { sig = jint, name = "id" },
        tool_type { sig = jint, name = "toolType" },
    },
}

fn validate_pointer_count(pointer_count: i32) -> Result<(), JniError> {
    if pointer_count < 1 {
        return Err(JniError::IllegalArgument("pointerCount must be at least 1".to_string()));
    }
    Ok(())
}

fn validate_pointer_properties_array<'array, 'local>(
    env: &mut jni::Env<'_>,
    array: Option<&'array JObjectArray<'local, JPointerProperties<'local>>>,
    pointer_count: usize,
) -> Result<&'array JObjectArray<'local, JPointerProperties<'local>>, JniError> {
    let Some(array) = array else {
        return Err(JniError::IllegalArgument(
            "pointerProperties array must not be null".to_string(),
        ));
    };
    let length = env.get_array_length(array).expect("GetArrayLength cannot fail");
    if (length as usize) < pointer_count {
        return Err(JniError::IllegalArgument(
            "pointerProperties array must be large enough to hold all pointers".to_string(),
        ));
    }
    Ok(array)
}

fn validate_pointer_coords_obj_array<'array, 'local>(
    env: &mut jni::Env<'_>,
    array: Option<&'array JObjectArray<'local, JPointerCoords<'local>>>,
    pointer_count: usize,
) -> Result<&'array JObjectArray<'local, JPointerCoords<'local>>, JniError> {
    let Some(array) = array else {
        return Err(JniError::IllegalArgument("pointerCoords array must not be null".to_string()));
    };
    let length = env.get_array_length(array).expect("GetArrayLength cannot fail");
    if (length as usize) < pointer_count {
        return Err(JniError::IllegalArgument(
            "pointerCoords array must be large enough to hold all pointers".to_string(),
        ));
    }
    Ok(array)
}

fn validate_pointer_index(pointer_index: i32, event: &MotionEvent) -> Result<usize, JniError> {
    if pointer_index < 0 || pointer_index as usize >= event.pointer_count() {
        // The event dump is built on the error path only.
        let message = format!(
            "invalid pointerIndex {pointer_index} for {}",
            ffi::motion_event_to_string(event)
        );
        return Err(JniError::IllegalArgument(message));
    }
    Ok(pointer_index as usize)
}

fn validate_history_pos(history_pos: i32, event: &MotionEvent) -> Result<usize, JniError> {
    if history_pos < 0 || history_pos as usize >= event.history_size() {
        let message = format!(
            "historyPos {history_pos} out of range for {}",
            ffi::motion_event_to_string(event)
        );
        return Err(JniError::IllegalArgument(message));
    }
    Ok(history_pos as usize)
}

/// One of the eleven axes `MotionEvent$PointerCoords` stores as a Java float
/// field, paired with the typed getter and setter for that field on
/// [`JPointerCoords`].
///
/// Pairing each axis constant with a typed accessor rather than a raw
/// `jfieldID` (see [`FIELD_BACKED_JAVA_AXES`]) makes it impossible to pair an
/// axis with the wrong field's ID.
struct FieldBackedAxis {
    /// The `AMOTION_EVENT_AXIS_*` constant this field carries.
    axis: i32,
    /// Reads the axis's Java float field (`GetFloatField`).
    get: for<'l, 'e> fn(&JPointerCoords<'l>, &jni::Env<'e>) -> jni::errors::Result<f32>,
    /// Writes the axis's Java float field (`SetFloatField`).
    set: for<'l, 'e> fn(&JPointerCoords<'l>, &jni::Env<'e>, f32) -> jni::errors::Result<()>,
}

/// The eleven axes `MotionEvent$PointerCoords` stores as Java float fields
/// (every other axis travels packed), in `FIELD_BACKED_AXES` order, each
/// paired with its [`JPointerCoords`] getter/setter.
///
/// This MUST stay aligned with `FIELD_BACKED_AXES`
/// (`[0, 1, 2, 3, 4, 5, 6, 7, 8, 27, 28]`): entry `i` pairs
/// `FIELD_BACKED_AXES[i]` with its Java field (`x, y, pressure, size,
/// touchMajor, touchMinor, toolMajor, toolMinor, orientation, relativeX,
/// relativeY`).
const FIELD_BACKED_JAVA_AXES: [FieldBackedAxis; FIELD_BACKED_AXES.len()] = [
    FieldBackedAxis {
        axis: FIELD_BACKED_AXES[0],
        get: |c, env| c.x(env),
        set: |c, env, v| c.set_x(env, v),
    },
    FieldBackedAxis {
        axis: FIELD_BACKED_AXES[1],
        get: |c, env| c.y(env),
        set: |c, env, v| c.set_y(env, v),
    },
    FieldBackedAxis {
        axis: FIELD_BACKED_AXES[2],
        get: |c, env| c.pressure(env),
        set: |c, env, v| c.set_pressure(env, v),
    },
    FieldBackedAxis {
        axis: FIELD_BACKED_AXES[3],
        get: |c, env| c.size(env),
        set: |c, env, v| c.set_size(env, v),
    },
    FieldBackedAxis {
        axis: FIELD_BACKED_AXES[4],
        get: |c, env| c.touch_major(env),
        set: |c, env, v| c.set_touch_major(env, v),
    },
    FieldBackedAxis {
        axis: FIELD_BACKED_AXES[5],
        get: |c, env| c.touch_minor(env),
        set: |c, env, v| c.set_touch_minor(env, v),
    },
    FieldBackedAxis {
        axis: FIELD_BACKED_AXES[6],
        get: |c, env| c.tool_major(env),
        set: |c, env, v| c.set_tool_major(env, v),
    },
    FieldBackedAxis {
        axis: FIELD_BACKED_AXES[7],
        get: |c, env| c.tool_minor(env),
        set: |c, env, v| c.set_tool_minor(env, v),
    },
    FieldBackedAxis {
        axis: FIELD_BACKED_AXES[8],
        get: |c, env| c.orientation(env),
        set: |c, env, v| c.set_orientation(env, v),
    },
    FieldBackedAxis {
        axis: FIELD_BACKED_AXES[9],
        get: |c, env| c.relative_x(env),
        set: |c, env, v| c.set_relative_x(env, v),
    },
    FieldBackedAxis {
        axis: FIELD_BACKED_AXES[10],
        get: |c, env| c.relative_y(env),
        set: |c, env, v| c.set_relative_y(env, v),
    },
];

/// Reads a Java `PointerCoords` into a native one: the eleven field-backed
/// axes, the resampled flag, then the packed remainder (copied out of the Java
/// array with `GetFloatArrayRegion`).
fn pointer_coords_to_native(env: &mut jni::Env<'_>, coords: &JPointerCoords<'_>) -> PointerCoords {
    let mut out = PointerCoords::cleared();
    // The only reachable failure of set_axis_value is running out of
    // packed-value slots, which silently drops the axis.
    for axis in &FIELD_BACKED_JAVA_AXES {
        let value =
            (axis.get)(coords, env).expect("GetFloatField on a cached field ID cannot fail");
        let _ = out.set_axis_value(axis.axis, value);
    }
    out.is_resampled =
        coords.is_resampled(env).expect("GetBooleanField on a cached field ID cannot fail");

    let bits =
        coords.packed_axis_bits(env).expect("GetLongField on a cached field ID cannot fail") as u64;
    let packed = BitSet64::from_bits(bits);
    if !packed.is_empty() {
        let values_array = coords
            .packed_axis_values(env)
            .expect("GetObjectField on a cached field ID cannot fail");
        if !values_array.is_null() {
            let values_array = env.auto_local(values_array);
            // Read only the packed count; Java grows mPackedAxisValues in
            // chunks, so the array may be longer, and set_packed_axes reads
            // only that many.
            let mut values = vec![0f32; packed.count() as usize];
            // A failed region read is treated as "no packed values".
            if env.get_float_array_region(&*values_array, 0, &mut values).is_ok() {
                out.set_packed_axes(bits, &values);
            }
        }
    }
    out
}

/// Returns `mPackedAxisValues` if it can hold `min_size` values, growing it
/// (powers of two from 8) otherwise. `None` means the allocation failed with
/// an exception pending, and the failed null result has already been stored
/// into the field.
fn obtain_packed_axis_values_array<'local>(
    env: &mut jni::Env<'local>,
    min_size: u32,
    coords: &JPointerCoords<'_>,
) -> Option<JFloatArray<'local>> {
    let existing =
        coords.packed_axis_values(env).expect("GetObjectField on a cached field ID cannot fail");
    if !existing.is_null() {
        let existing = env.auto_local(existing);
        let size = env.get_array_length(&*existing).expect("GetArrayLength cannot fail") as u32;
        if min_size <= size {
            // Large enough to reuse: hand ownership back without deleting.
            return Some(existing.unwrap());
        }
        // Too small: `existing` is dropped here (RAII), freeing the old array
        // before a larger one is allocated below.
    }
    match env.new_float_array(packed_axis_values_capacity(min_size) as usize) {
        Ok(array) => {
            coords
                .set_packed_axis_values(env, &array)
                .expect("SetObjectField on a cached field ID cannot fail");
            Some(array)
        }
        Err(_) => {
            // OOM: store the null result into the field before bailing, so the
            // field state stays consistent with the pending exception.
            coords
                .set_packed_axis_values(env, JFloatArray::null())
                .expect("SetObjectField on a cached field ID cannot fail");
            None
        }
    }
}

/// Writes the packed remainder of a native `PointerCoords` into a Java one:
/// the values of `axes_to_copy` go into `mPackedAxisValues` (grown on
/// demand) and the presence word into `mPackedAxisBits`. On allocation
/// failure the bits word is left untouched.
fn pointer_coords_from_native(
    env: &mut jni::Env<'_>,
    raw_pointer_coords: &PointerCoords,
    axes_to_copy: BitSet64,
    coords: &JPointerCoords<'_>,
) {
    let mut out_bits = 0u64;
    if !axes_to_copy.is_empty() {
        let Some(out_values_array) =
            obtain_packed_axis_values_array(env, axes_to_copy.count(), coords)
        else {
            return; // OOM.
        };
        let out_values_array = env.auto_local(out_values_array);
        // export_packed_axes writes exactly axes_to_copy.count() values, in
        // packing order (it asserts the buffer holds at least that many).
        let mut values = vec![0f32; axes_to_copy.count() as usize];
        out_bits = raw_pointer_coords.export_packed_axes(axes_to_copy, &mut values);
        // Copy them to the front of mPackedAxisValues. A failed region write
        // leaves the bits word untouched.
        if env.set_float_array_region(&*out_values_array, 0, &values).is_err() {
            return;
        }
    }
    coords
        .set_packed_axis_bits(env, out_bits as i64)
        .expect("SetLongField on a cached field ID cannot fail");
}

/// Reads a Java `PointerProperties` into a native one.
fn pointer_properties_to_native(
    env: &mut jni::Env<'_>,
    props: &JPointerProperties<'_>,
) -> PointerProperties {
    PointerProperties {
        id: props.id(env).expect("GetIntField on a cached field ID cannot fail"),
        tool_type: props.tool_type(env).expect("GetIntField on a cached field ID cannot fail"),
    }
}

/// Writes a native `PointerProperties` into a Java one.
fn pointer_properties_from_native(
    env: &mut jni::Env<'_>,
    pointer_properties: &PointerProperties,
    props: &JPointerProperties<'_>,
) {
    props.set_id(env, pointer_properties.id).expect("SetIntField on a cached field ID cannot fail");
    props
        .set_tool_type(env, pointer_properties.tool_type)
        .expect("SetIntField on a cached field ID cannot fail");
}

/// Reads a Java event's `mNativePtr`.
fn get_native_ptr(env: &mut jni::Env<'_>, event_obj: &JObject<'_>) -> *mut MotionEvent {
    // SAFETY: `event_obj` is a MotionEvent (checked by the callers); the
    // wrapper borrows it and we know its type.
    let event = unsafe { JMotionEvent::from_raw(env, event_obj.as_raw()) };
    JLongHandle::from_jlong(event.native_ptr(env).expect("GetLongField(mNativePtr) cannot fail"))
        .as_ptr()
}

/// Obtains a Java `MotionEvent` from the Java pool and hands it ownership of
/// `event`, deleting whatever native event the pooled object still carried.
/// Returns a new local reference. Aborts if `MotionEvent.obtain()` throws or
/// returns null.
///
/// # Safety
///
/// `event` must be a live `MotionEvent*` allocated via the bridge, not used
/// again by the caller: ownership moves to the returned Java object.
unsafe fn obtain_from_native(env: &mut jni::Env<'_>, event: *mut MotionEvent) -> jobject {
    // `MotionEvent.obtain()` takes no arguments and returns a pooled event.
    let event_obj = match JMotionEvent::obtain(env) {
        Ok(event_obj) if !event_obj.is_null() => event_obj,
        _ => {
            // Describe the pending exception, then panic; under the platform's
            // panic=abort this aborts the process.
            env.exception_describe();
            panic!("An exception occurred while obtaining a Java motion event.");
        }
    };
    let old_event = JLongHandle::from_jlong(
        event_obj.native_ptr(env).expect("GetLongField(mNativePtr) cannot fail"),
    )
    .as_ptr();
    // SAFETY: A pooled event's mNativePtr is null or a live event owned by
    // the Java object; it is replaced below and never referenced again.
    unsafe { ffi::motion_event_destroy(old_event) };
    event_obj
        .set_native_ptr(env, JLongHandle::from_ptr(event).as_jlong())
        .expect("SetLongField(mNativePtr) cannot fail");
    event_obj.into_raw()
}

/// C export behind the facade's `android_view_MotionEvent_getNativePtr`:
/// reads the `MotionEvent*` out of a Java event's `mNativePtr`. Returns null
/// for a null `event_obj` or an uninitialized event.
///
/// # Safety
///
/// `event_obj` must be null or a live `android.view.MotionEvent` reference;
/// the JVM attachment behind `env` is upheld by `EnvUnowned`.
#[no_mangle]
pub unsafe extern "C" fn rs_motionEvent_getNativePtr(
    mut env: jni::EnvUnowned<'_>,
    event_obj: JObject<'_>,
) -> *mut MotionEvent {
    if event_obj.is_null() {
        return std::ptr::null_mut();
    }
    match env
        .with_env_no_catch(|env| {
            Ok::<*mut MotionEvent, jni::errors::Error>(get_native_ptr(env, &event_obj))
        })
        .into_outcome()
    {
        jni::Outcome::Ok(ptr) => ptr,
        // `with_env_no_catch` never catches a panic, and the closure never
        // returns `Err`, so these arms are unreachable.
        jni::Outcome::Err(_) | jni::Outcome::Panic(_) => std::ptr::null_mut(),
    }
}

/// C export behind the facade's `android_view_MotionEvent_obtainFromNative`:
/// obtains a Java `MotionEvent` owning `event` and returns a new local
/// reference. A null `event` returns null; a failing `MotionEvent.obtain()`
/// aborts.
///
/// # Safety
///
/// `event` must be null or a live `MotionEvent*` (C++-new-allocated) whose
/// ownership transfers to the returned Java object; the JVM attachment behind
/// `env` is upheld by `EnvUnowned`.
#[no_mangle]
pub unsafe extern "C" fn rs_motionEvent_obtainFromNative(
    mut env: jni::EnvUnowned<'_>,
    event: *mut MotionEvent,
) -> jobject {
    if event.is_null() {
        return std::ptr::null_mut();
    }
    match env
        .with_env_no_catch(|env| {
            // SAFETY: Per this function's contract, `event` is live and owned by
            // the caller until this call, which transfers it to the Java object.
            Ok::<jobject, jni::errors::Error>(unsafe { obtain_from_native(env, event) })
        })
        .into_outcome()
    {
        jni::Outcome::Ok(obj) => obj,
        // `with_env_no_catch` never catches a panic, and the closure never
        // returns `Err`, so these arms are unreachable.
        jni::Outcome::Err(_) | jni::Outcome::Panic(_) => std::ptr::null_mut(),
    }
}

/// Registers `android.view.MotionEvent`'s native methods and caches its (and
/// its inner classes') JNI IDs. Call during JNI startup; panics if the class
/// or any ID is missing — registration failures are fatal.
pub fn register(env: &mut jni::Env<'_>) {
    // Force the classes + field/method IDs to be cached eagerly at startup,
    // failing fast if the class or any ID is missing.
    JMotionEventAPI::get(env, &LoaderContext::None)
        .expect("Failed to initialize android.view.MotionEvent binding");
    JPointerCoordsAPI::get(env, &LoaderContext::None)
        .expect("Failed to initialize android.view.MotionEvent$PointerCoords binding");
    JPointerPropertiesAPI::get(env, &LoaderContext::None)
        .expect("Failed to initialize android.view.MotionEvent$PointerProperties binding");
    motion_event::register(env);
}

/// The `android.view.MotionEvent` native methods: 9 regular, 7 @FastNative,
/// and 36 @CriticalNative, matching the annotations in MotionEvent.java.
#[jni_platform_macros::jni_module("android/view/MotionEvent")]
pub mod motion_event {
    use super::{
        ffi, pointer_coords_from_native, pointer_coords_to_native, pointer_properties_from_native,
        pointer_properties_to_native, validate_history_pos, validate_pointer_coords_obj_array,
        validate_pointer_count, validate_pointer_index, validate_pointer_properties_array,
        JPointerCoords, JPointerProperties, JniError, MotionEvent, PointerCoords,
        FIELD_BACKED_JAVA_AXES, HISTORY_CURRENT, MAX_POINTERS,
    };
    use crate::input_event_ffi;
    use crate::motion_event_ffi::{self, MotionEventInitArgs};
    use jni::objects::{JClass, JObject, JObjectArray, JString};
    use jni::strings::JNIStr;
    use jni_support::{ForeignPeer, ForeignPeerMut, JLongHandle};
    use std::pin::Pin;

    /// Populates a native `MotionEvent` from the Java constructor arguments,
    /// reusing `native_ptr` when non-zero and allocating one otherwise.
    ///
    /// The validation exceptions follow a fixed order: the pointer count and
    /// both arrays are checked (IllegalArgumentException) before any array
    /// element is touched; only a null `pointerCoords` element then throws
    /// NullPointerException, while a null `pointerProperties` element
    /// silently returns 0. Once validation has passed, a non-zero incoming
    /// event is owned: the `pointerProperties` element failure returns 0
    /// without an exception, so Java overwrites `mNativePtr` and it's safe
    /// to delete that reused event; the `pointerCoords` element failure
    /// throws instead, so Java keeps the old `mNativePtr` and the reused
    /// event must be released back to it rather than deleted.
    #[allow(clippy::too_many_arguments)]
    #[jni_method]
    fn nativeInitialize(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        native_ptr: i64,
        device_id: i32,
        source: i32,
        display_id: i32,
        action: i32,
        flags: i32,
        edge_flags: i32,
        meta_state: i32,
        button_state: i32,
        classification: i32,
        x_offset: f32,
        y_offset: f32,
        x_precision: f32,
        y_precision: f32,
        down_time_nanos: i64,
        event_time_nanos: i64,
        pointer_count: i32,
        #[class = "android/view/MotionEvent$PointerProperties"]
        pointer_properties_obj_array: Option<&JObjectArray<JPointerProperties>>,
        #[class = "android/view/MotionEvent$PointerCoords"] pointer_coords_obj_array: Option<
            &JObjectArray<JPointerCoords>,
        >,
    ) -> Result<i64, JniError> {
        validate_pointer_count(pointer_count)?;
        let pointer_properties_obj_array = validate_pointer_properties_array(
            env,
            pointer_properties_obj_array,
            pointer_count as usize,
        )?;
        let pointer_coords_obj_array = validate_pointer_coords_obj_array(
            env,
            pointer_coords_obj_array,
            pointer_count as usize,
        )?;

        // From here the event is owned (the `UniquePtr` below): a freshly
        // allocated event is deleted by every early return below. A reused
        // incoming pointer is deleted too, except on the pointerCoords-null
        // path, which throws instead of returning cleanly — Java won't
        // store our 0 over a pending exception, so mNativePtr keeps pointing
        // at it and it must stay alive.
        let mut event = if native_ptr != 0 {
            // SAFETY: A non-zero mNativePtr is a live event owned by the Java
            // object, which expects this native to consume and replace it.
            unsafe {
                cxx::UniquePtr::from_raw(
                    JLongHandle::<MotionEvent>::from_jlong(native_ptr).as_ptr(),
                )
            }
        } else {
            ffi::motion_event_new()
        };

        let mut pointer_properties = Vec::with_capacity(pointer_count as usize);
        let mut raw_pointer_coords = Vec::with_capacity(pointer_count as usize);
        for i in 0..pointer_count {
            let pointer_properties_obj = pointer_properties_obj_array
                .get_element(env, i as usize)
                .expect("GetObjectArrayElement within a validated range cannot fail");
            if pointer_properties_obj.is_null() {
                // A null pointerProperties element returns 0 without an exception.
                return Ok(0);
            }
            let pointer_properties_obj = env.auto_local(pointer_properties_obj);
            pointer_properties.push(pointer_properties_to_native(env, &pointer_properties_obj));

            let pointer_coords_obj = pointer_coords_obj_array
                .get_element(env, i as usize)
                .expect("GetObjectArrayElement within a validated range cannot fail");
            if pointer_coords_obj.is_null() {
                if native_ptr != 0 {
                    // Java won't store our 0 while an exception is pending, so it keeps
                    // mNativePtr = native_ptr; leave that reused event live rather than
                    // freeing it out from under the field.
                    let _ = event.into_raw();
                }
                return Err(JniError::NullPointer("pointerCoords"));
            }
            let pointer_coords_obj = env.auto_local(pointer_coords_obj);
            raw_pointer_coords.push(pointer_coords_to_native(env, &pointer_coords_obj));
        }

        let args = MotionEventInitArgs {
            down_time: down_time_nanos,
            event_time: event_time_nanos,
            id: input_event_ffi::next_input_event_id(),
            device_id,
            source: source as u32,
            display_id,
            action,
            action_button: 0,
            flags,
            edge_flags,
            meta_state,
            button_state,
            classification,
            x_offset,
            y_offset,
            x_precision,
            y_precision,
        };
        // The bridge owns the rest of nativeInitialize's work: orientation
        // private-flag promotion, the window transform, and the in-place
        // inverse mapping of the raw coordinates.
        ffi::motion_event_initialize(
            event.pin_mut(),
            &args,
            &pointer_properties,
            &mut raw_pointer_coords,
        );
        Ok(JLongHandle::from_ptr(event.into_raw()).as_jlong())
    }

    /// Frees the native `MotionEvent` behind `native_ptr`.
    #[jni_method]
    fn nativeDispose(_env: &mut jni::Env<'_>, _clazz: JClass, native_ptr: i64) {
        // SAFETY: mNativePtr is null or a live event owned by the Java
        // object; finalize() zeroes the field after this call.
        unsafe {
            ffi::motion_event_destroy(JLongHandle::<MotionEvent>::from_jlong(native_ptr).as_ptr())
        };
    }

    /// Appends a batched coordinate sample to the event's history.
    #[jni_method]
    fn nativeAddBatch(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        #[jlong_mut] mut event: Pin<&mut MotionEvent>,
        event_time_nanos: i64,
        #[class = "android/view/MotionEvent$PointerCoords"] pointer_coords_obj_array: Option<
            &JObjectArray<JPointerCoords>,
        >,
        meta_state: i32,
    ) -> Result<(), JniError> {
        let pointer_count = event.pointer_count();
        let pointer_coords_obj_array =
            validate_pointer_coords_obj_array(env, pointer_coords_obj_array, pointer_count)?;

        // Events are capped at MAX_POINTERS everywhere in libinput, so the
        // hot path fills a stack buffer; the heap fallback tolerates oversized
        // Java events.
        let mut stack_coords = [PointerCoords::cleared(); MAX_POINTERS];
        let mut heap_coords;
        let raw_pointer_coords: &mut [PointerCoords] = if pointer_count <= MAX_POINTERS {
            &mut stack_coords[..pointer_count]
        } else {
            heap_coords = vec![PointerCoords::cleared(); pointer_count];
            &mut heap_coords
        };
        for (i, raw_coords) in raw_pointer_coords.iter_mut().enumerate() {
            let pointer_coords_obj = pointer_coords_obj_array
                .get_element(env, i)
                .expect("GetObjectArrayElement within a validated range cannot fail");
            if pointer_coords_obj.is_null() {
                return Err(JniError::NullPointer("pointerCoords"));
            }
            let pointer_coords_obj = env.auto_local(pointer_coords_obj);
            *raw_coords = pointer_coords_to_native(env, &pointer_coords_obj);
        }

        // The bridge maps the coordinates through the inverse event
        // transform in place and appends the sample under the current id.
        ffi::motion_event_add_sample_transformed(
            event.as_mut(),
            event_time_nanos,
            raw_pointer_coords,
        );
        let meta_state = event.meta_state() | meta_state;
        event.set_meta_state(meta_state);
        Ok(())
    }

    /// Copies a pointer's coordinates into a Java `PointerCoords`.
    #[jni_method]
    fn nativeGetPointerCoords(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        #[jlong_ref] event: &MotionEvent,
        pointer_index: i32,
        history_pos: i32,
        #[class = "android/view/MotionEvent$PointerCoords"] out_pointer_coords_obj: Option<
            &JPointerCoords,
        >,
    ) -> Result<(), JniError> {
        let index = validate_pointer_index(pointer_index, event)?;
        let coords = out_pointer_coords_obj.ok_or_else(|| {
            JniError::IllegalArgument("pointerCoords must not be null".to_string())
        })?;
        let history_index = if history_pos == HISTORY_CURRENT {
            None
        } else {
            Some(validate_history_pos(history_pos, event)?)
        };
        // The eleven field-backed axes come from the MotionEvent itself
        // (transformed values), not from the raw PointerCoords.
        for axis in &FIELD_BACKED_JAVA_AXES {
            let value = history_index.map_or_else(
                || event.axis_value(axis.axis, index),
                |history_index| event.historical_axis_value(axis.axis, index, history_index),
            );
            (axis.set)(coords, env, value).expect("SetFloatField on a cached field ID cannot fail");
        }

        let raw_pointer_coords = history_index.map_or_else(
            || ffi::motion_event_raw_pointer_coords(event, index),
            |history_index| {
                ffi::motion_event_historical_raw_pointer_coords(event, index, history_index)
            },
        );
        // Everything but the field-backed axes travels through the packed words.
        let axes_to_copy = raw_pointer_coords.packed_remainder_bits();
        pointer_coords_from_native(env, raw_pointer_coords, axes_to_copy, coords);

        let is_resampled = history_index.map_or_else(
            || event.is_resampled(index, event.history_size()),
            |history_index| event.is_resampled(index, history_index),
        );
        coords
            .set_is_resampled(env, is_resampled)
            .expect("SetBooleanField on a cached field ID cannot fail");
        Ok(())
    }

    /// Copies a pointer's properties into a Java `PointerProperties`.
    #[jni_method]
    fn nativeGetPointerProperties(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        #[jlong_ref] event: &MotionEvent,
        pointer_index: i32,
        #[class = "android/view/MotionEvent$PointerProperties"] out_pointer_properties_obj: Option<
            &JPointerProperties,
        >,
    ) -> Result<(), JniError> {
        let pointer_index = validate_pointer_index(pointer_index, event)?;
        let out_pointer_properties_obj = out_pointer_properties_obj.ok_or_else(|| {
            JniError::IllegalArgument("pointerProperties must not be null".to_string())
        })?;
        let pointer_properties = ffi::motion_event_pointer_properties(event, pointer_index);
        pointer_properties_from_native(env, pointer_properties, out_pointer_properties_obj);
        Ok(())
    }

    /// Reads a `MotionEvent` from a `Parcel`.
    ///
    /// Reuses a non-zero incoming event; allocates otherwise, and on a failed
    /// read deletes the event only if this call allocated it, then throws
    /// RuntimeException.
    #[jni_method]
    fn nativeReadFromParcel(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        native_ptr: i64,
        #[class = "android/os/Parcel"] parcel_obj: &JObject,
    ) -> Result<i64, JniError> {
        let allocated = native_ptr == 0;
        let event = if allocated {
            ffi::motion_event_new().into_raw()
        } else {
            JLongHandle::<MotionEvent>::from_jlong(native_ptr).as_ptr()
        };
        // SAFETY: `event` is live, `env` is the current thread's JNIEnv, and
        // `parcel_obj` is the caller's android.os.Parcel.
        let status = unsafe {
            motion_event_ffi::android_runtime_motion_event_read_from_parcel(
                event,
                env.get_raw(),
                parcel_obj.as_raw(),
            )
        };
        if status != 0 {
            if allocated {
                // SAFETY: The event was allocated above and escapes nowhere.
                unsafe { ffi::motion_event_destroy(event) };
            }
            return Err(JniError::Runtime("Failed to read MotionEvent parcel.".to_string()));
        }
        Ok(JLongHandle::from_ptr(event).as_jlong())
    }

    /// Writes the event to a `Parcel`.
    #[jni_method]
    fn nativeWriteToParcel(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        #[jlong_ref] event: &MotionEvent,
        #[class = "android/os/Parcel"] parcel_obj: &JObject,
    ) -> Result<(), JniError> {
        // SAFETY: mNativePtr always holds a live event, `env` is the current
        // thread's JNIEnv, and `parcel_obj` is the caller's Parcel.
        let status = unsafe {
            motion_event_ffi::android_runtime_motion_event_write_to_parcel(
                event as *const MotionEvent,
                env.get_raw(),
                parcel_obj.as_raw(),
            )
        };
        if status != 0 {
            return Err(JniError::Runtime("Failed to write MotionEvent parcel.".to_string()));
        }
        Ok(())
    }

    /// Returns the symbolic name of axis constant `axis`, or null if unknown.
    ///
    /// Axes without a label return null. Labels are ASCII, so the UTF-8 →
    /// Modified UTF-8 conversion in `new_string` is the identity.
    #[jni_method]
    fn nativeAxisToString<'local>(
        env: &mut jni::Env<'local>,
        _clazz: JClass,
        axis: i32,
    ) -> JString<'local> {
        let Some(label) = motion_event_ffi::axis_label(axis) else {
            return JString::null();
        };
        match env.new_string(label.to_string_lossy()) {
            Ok(label) => label,
            // Allocation failed; the exception is pending for Java.
            Err(_) => JString::null(),
        }
    }

    /// Returns the axis constant for symbolic name `label`.
    ///
    /// The label reaches libinput as NUL-terminated Modified UTF-8 bytes. A
    /// null label throws NullPointerException and returns 0.
    #[jni_method]
    fn nativeAxisFromString(_env: &mut jni::Env<'_>, _clazz: JClass, label: &JNIStr) -> i32 {
        motion_event_ffi::axis_from_label(label.as_cstr())
    }

    /// Returns the pointer id at `pointer_index`, or -1 if out of range.
    #[jni_method(fast)]
    fn nativeGetPointerId(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        #[jlong_ref] event: &MotionEvent,
        pointer_index: i32,
    ) -> i32 {
        let pointer_index = match validate_pointer_index(pointer_index, event) {
            Ok(pointer_index) => pointer_index,
            Err(error) => {
                error.throw_on(env);
                return -1;
            }
        };
        // SAFETY: `pointer_index` was validated against the pointer count.
        unsafe { event.pointer_id(pointer_index) }
    }

    /// Returns the tool type at `pointer_index`, or -1 if out of range.
    #[jni_method(fast)]
    fn nativeGetToolType(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        #[jlong_ref] event: &MotionEvent,
        pointer_index: i32,
    ) -> i32 {
        let pointer_index = match validate_pointer_index(pointer_index, event) {
            Ok(pointer_index) => pointer_index,
            Err(error) => {
                error.throw_on(env);
                return -1;
            }
        };
        ffi::motion_event_get_tool_type(event, pointer_index)
    }

    /// Returns a sample's event time in nanoseconds.
    #[jni_method(fast)]
    fn nativeGetEventTimeNanos(
        _env: &mut jni::Env<'_>,
        _clazz: JClass,
        #[jlong_ref] event: &MotionEvent,
        history_pos: i32,
    ) -> Result<i64, JniError> {
        if history_pos == HISTORY_CURRENT {
            // SAFETY: Java never exposes an uninitialized event, so it holds
            // at least one sample.
            Ok(unsafe { event.event_time() })
        } else {
            let history_pos = validate_history_pos(history_pos, event)?;
            // SAFETY: `history_pos` was validated against the history size.
            Ok(unsafe { event.historical_event_time(history_pos) })
        }
    }

    /// Returns the untransformed value of `axis` for a pointer sample.
    #[jni_method(fast)]
    fn nativeGetRawAxisValue(
        _env: &mut jni::Env<'_>,
        _clazz: JClass,
        #[jlong_ref] event: &MotionEvent,
        axis: i32,
        pointer_index: i32,
        history_pos: i32,
    ) -> Result<f32, JniError> {
        let pointer_index = validate_pointer_index(pointer_index, event)?;
        if history_pos == HISTORY_CURRENT {
            Ok(event.raw_axis_value(axis, pointer_index))
        } else {
            let history_pos = validate_history_pos(history_pos, event)?;
            Ok(event.historical_raw_axis_value(axis, pointer_index, history_pos))
        }
    }

    /// Returns the transformed value of `axis` for a pointer sample.
    #[jni_method(fast)]
    fn nativeGetAxisValue(
        _env: &mut jni::Env<'_>,
        _clazz: JClass,
        #[jlong_ref] event: &MotionEvent,
        axis: i32,
        pointer_index: i32,
        history_pos: i32,
    ) -> Result<f32, JniError> {
        let pointer_index = validate_pointer_index(pointer_index, event)?;
        if history_pos == HISTORY_CURRENT {
            Ok(event.axis_value(axis, pointer_index))
        } else {
            let history_pos = validate_history_pos(history_pos, event)?;
            Ok(event.historical_axis_value(axis, pointer_index, history_pos))
        }
    }

    /// Replaces the event's coordinate transform with a Java `Matrix`.
    #[jni_method(fast)]
    fn nativeTransform(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        #[jlong_mut] mut event: Pin<&mut MotionEvent>,
        #[class = "android/graphics/Matrix"] matrix_obj: &JObject,
    ) {
        let mut matrix = [0.0f32; 9];
        // SAFETY: `env` is the current thread's JNIEnv, `matrix_obj` is the
        // caller's android.graphics.Matrix, and the buffer holds the nine
        // floats AMatrix_getContents writes.
        unsafe {
            super::AMatrix_getContents(env.get_raw(), matrix_obj.as_raw(), matrix.as_mut_ptr())
        };
        event.as_mut().transform(&matrix);
    }

    /// Composes a Java `Matrix` onto the event's coordinate transform.
    #[jni_method(fast)]
    fn nativeApplyTransform(
        env: &mut jni::Env<'_>,
        _clazz: JClass,
        #[jlong_mut] mut event: Pin<&mut MotionEvent>,
        #[class = "android/graphics/Matrix"] matrix_obj: &JObject,
    ) {
        let mut matrix = [0.0f32; 9];
        // SAFETY: As in nativeTransform.
        unsafe {
            super::AMatrix_getContents(env.get_raw(), matrix_obj.as_raw(), matrix.as_mut_ptr())
        };
        event.as_mut().apply_transform(&matrix);
    }

    /// Copies `source_native_ptr` into a destination event.
    ///
    /// Reuses a non-zero destination event; allocates otherwise.
    #[jni_method(critical)]
    fn nativeCopy(dest_native_ptr: i64, source_native_ptr: i64, keep_history: bool) -> i64 {
        if dest_native_ptr != 0 && dest_native_ptr == source_native_ptr {
            return dest_native_ptr;
        }
        let dest_native_ptr = if dest_native_ptr != 0 {
            dest_native_ptr
        } else {
            JLongHandle::from_ptr(ffi::motion_event_new().into_raw()).as_jlong()
        };
        // SAFETY: destination is the caller's distinct live peer or was just
        // allocated; source is its live mNativePtr peer for this call.
        let mut dest =
            unsafe { ForeignPeerMut::from_handle(JLongHandle::from_jlong(dest_native_ptr)) }
                .expect("destination MotionEvent must not be null");
        // SAFETY: source is live and was proven distinct from destination.
        let source =
            unsafe { ForeignPeer::from_handle(JLongHandle::from_jlong(source_native_ptr)) }
                .expect("source MotionEvent must not be null");
        ffi::motion_event_copy_from(dest.pin(), source.get(), keep_history);
        dest_native_ptr
    }

    /// Builds an event holding only the pointers selected by `id_bits`.
    ///
    /// Reuses a non-zero destination event; allocates otherwise. Bit `n` of
    /// `id_bits` selects pointer id `n`.
    #[jni_method(critical)]
    fn nativeSplit(dest_native_ptr: i64, source_native_ptr: i64, id_bits: i32) -> i64 {
        if dest_native_ptr != 0 && dest_native_ptr == source_native_ptr {
            // Split into a fresh object before retiring the aliased source;
            // constructing simultaneous &mut/& references would be Rust UB.
            let mut replacement = ffi::motion_event_new();
            {
                // SAFETY: source is the live peer and replacement is distinct.
                let source =
                    unsafe { ForeignPeer::from_handle(JLongHandle::from_jlong(source_native_ptr)) }
                        .expect("source MotionEvent must not be null");
                ffi::motion_event_split_from(
                    replacement.pin_mut(),
                    source.get(),
                    id_bits as u32,
                    input_event_ffi::next_input_event_id(),
                );
            }
            // SAFETY: the source/destination Java object will replace this
            // pointer with `replacement` when the native returns.
            unsafe {
                ffi::motion_event_destroy(
                    JLongHandle::<MotionEvent>::from_jlong(source_native_ptr).as_ptr(),
                )
            };
            return JLongHandle::from_ptr(replacement.into_raw()).as_jlong();
        }
        let dest_native_ptr = if dest_native_ptr != 0 {
            dest_native_ptr
        } else {
            JLongHandle::from_ptr(ffi::motion_event_new().into_raw()).as_jlong()
        };
        // SAFETY: the two non-null peer pointers are live and distinct.
        let mut dest =
            unsafe { ForeignPeerMut::from_handle(JLongHandle::from_jlong(dest_native_ptr)) }
                .expect("destination MotionEvent must not be null");
        // SAFETY: source is live and distinct from destination.
        let source =
            unsafe { ForeignPeer::from_handle(JLongHandle::from_jlong(source_native_ptr)) }
                .expect("source MotionEvent must not be null");
        ffi::motion_event_split_from(
            dest.pin(),
            source.get(),
            id_bits as u32,
            input_event_ffi::next_input_event_id(),
        );
        dest_native_ptr
    }

    /// Returns the event's identifier.
    #[jni_method(critical)]
    fn nativeGetId(#[jlong_ref] event: &MotionEvent) -> i32 {
        event.id()
    }

    /// Returns the id of the input device that produced the event.
    #[jni_method(critical)]
    fn nativeGetDeviceId(#[jlong_ref] event: &MotionEvent) -> i32 {
        event.device_id()
    }

    /// Returns the event's input source.
    #[jni_method(critical)]
    fn nativeGetSource(#[jlong_ref] event: &MotionEvent) -> i32 {
        event.source() as i32
    }

    /// Sets the event's input source.
    #[jni_method(critical)]
    fn nativeSetSource(#[jlong_mut] mut event: Pin<&mut MotionEvent>, source: i32) {
        event.as_mut().set_source(source as u32);
    }

    /// Returns the id of the display the event targets.
    #[jni_method(critical)]
    fn nativeGetDisplayId(#[jlong_ref] event: &MotionEvent) -> i32 {
        ffi::motion_event_get_display_id(event)
    }

    /// Sets the id of the display the event targets.
    #[jni_method(critical)]
    fn nativeSetDisplayId(#[jlong_mut] mut event: Pin<&mut MotionEvent>, display_id: i32) {
        ffi::motion_event_set_display_id(event.as_mut(), display_id);
    }

    /// Returns the event's action code.
    #[jni_method(critical)]
    fn nativeGetAction(#[jlong_ref] event: &MotionEvent) -> i32 {
        event.action()
    }

    /// Sets the event's action code.
    #[jni_method(critical)]
    fn nativeSetAction(#[jlong_mut] mut event: Pin<&mut MotionEvent>, action: i32) {
        event.as_mut().set_action(action);
    }

    /// Returns the button tied to the current action.
    #[jni_method(critical)]
    fn nativeGetActionButton(#[jlong_ref] event: &MotionEvent) -> i32 {
        event.action_button()
    }

    /// Sets the button tied to the current action.
    #[jni_method(critical)]
    fn nativeSetActionButton(#[jlong_mut] mut event: Pin<&mut MotionEvent>, button: i32) {
        event.as_mut().set_action_button(button);
    }

    /// Whether the event originates from a touch device.
    #[jni_method(critical)]
    fn nativeIsTouchEvent(#[jlong_ref] event: &MotionEvent) -> bool {
        event.is_touch_event()
    }

    /// Returns the event's public flags.
    ///
    /// Private flags are masked out so they never reach Java.
    #[jni_method(critical)]
    fn nativeGetFlags(#[jlong_ref] event: &MotionEvent) -> i32 {
        let flags = ffi::motion_event_get_flags(event);
        flags & !ffi::motion_event_private_flag_mask()
    }

    /// Sets the event's public flags.
    ///
    /// The event's private flags are preserved and Java-supplied private
    /// bits discarded, so they cannot be set from Java.
    #[jni_method(critical)]
    fn nativeSetFlags(#[jlong_mut] mut event: Pin<&mut MotionEvent>, flags: i32) {
        let mask = ffi::motion_event_private_flag_mask();
        let private_flags = ffi::motion_event_get_flags(event.as_ref().get_ref()) & mask;
        ffi::motion_event_set_flags(event.as_mut(), (flags & !mask) | private_flags);
    }

    /// Returns the edge flags marking which screen edges the event touched.
    #[jni_method(critical)]
    fn nativeGetEdgeFlags(#[jlong_ref] event: &MotionEvent) -> i32 {
        event.edge_flags()
    }

    /// Sets the edge flags marking which screen edges the event touched.
    #[jni_method(critical)]
    fn nativeSetEdgeFlags(#[jlong_mut] mut event: Pin<&mut MotionEvent>, edge_flags: i32) {
        event.as_mut().set_edge_flags(edge_flags);
    }

    /// Returns the meta key state active during the event.
    #[jni_method(critical)]
    fn nativeGetMetaState(#[jlong_ref] event: &MotionEvent) -> i32 {
        event.meta_state()
    }

    /// Returns the state of the buttons pressed during the event.
    #[jni_method(critical)]
    fn nativeGetButtonState(#[jlong_ref] event: &MotionEvent) -> i32 {
        event.button_state()
    }

    /// Sets the state of the buttons pressed during the event.
    #[jni_method(critical)]
    fn nativeSetButtonState(#[jlong_mut] mut event: Pin<&mut MotionEvent>, button_state: i32) {
        event.as_mut().set_button_state(button_state);
    }

    /// Returns the event's motion classification.
    #[jni_method(critical)]
    fn nativeGetClassification(#[jlong_ref] event: &MotionEvent) -> i32 {
        ffi::motion_event_get_classification(event)
    }

    /// Shifts every pointer's location by (`delta_x`, `delta_y`).
    #[jni_method(critical)]
    fn nativeOffsetLocation(
        #[jlong_mut] mut event: Pin<&mut MotionEvent>,
        delta_x: f32,
        delta_y: f32,
    ) {
        event.as_mut().offset_location(delta_x, delta_y);
    }

    /// Returns the X offset applied to raw coordinates.
    #[jni_method(critical)]
    fn nativeGetRawXOffset(#[jlong_ref] event: &MotionEvent) -> f32 {
        event.raw_x_offset()
    }

    /// Returns the Y offset applied to raw coordinates.
    #[jni_method(critical)]
    fn nativeGetRawYOffset(#[jlong_ref] event: &MotionEvent) -> f32 {
        event.raw_y_offset()
    }

    /// Returns the precision of the reported X coordinates.
    #[jni_method(critical)]
    fn nativeGetXPrecision(#[jlong_ref] event: &MotionEvent) -> f32 {
        event.x_precision()
    }

    /// Returns the precision of the reported Y coordinates.
    #[jni_method(critical)]
    fn nativeGetYPrecision(#[jlong_ref] event: &MotionEvent) -> f32 {
        event.y_precision()
    }

    /// Returns the cursor's X coordinate, if the event has one.
    #[jni_method(critical)]
    fn nativeGetXCursorPosition(#[jlong_ref] event: &MotionEvent) -> f32 {
        event.x_cursor_position()
    }

    /// Returns the cursor's Y coordinate, if the event has one.
    #[jni_method(critical)]
    fn nativeGetYCursorPosition(#[jlong_ref] event: &MotionEvent) -> f32 {
        event.y_cursor_position()
    }

    /// Sets the cursor position to (`x`, `y`).
    #[jni_method(critical)]
    fn nativeSetCursorPosition(#[jlong_mut] mut event: Pin<&mut MotionEvent>, x: f32, y: f32) {
        event.as_mut().set_cursor_position(x, y);
    }

    /// Returns the gesture's down time in nanoseconds.
    #[jni_method(critical)]
    fn nativeGetDownTimeNanos(#[jlong_ref] event: &MotionEvent) -> i64 {
        event.down_time()
    }

    /// Sets the gesture's down time in nanoseconds.
    #[jni_method(critical)]
    fn nativeSetDownTimeNanos(#[jlong_mut] mut event: Pin<&mut MotionEvent>, down_time_nanos: i64) {
        event.as_mut().set_down_time(down_time_nanos);
    }

    /// Returns the number of pointers in the event.
    #[jni_method(critical)]
    fn nativeGetPointerCount(#[jlong_ref] event: &MotionEvent) -> i32 {
        event.pointer_count() as i32
    }

    /// Returns the pointer index for pointer id `pointer_id`, or -1.
    #[jni_method(critical)]
    fn nativeFindPointerIndex(#[jlong_ref] event: &MotionEvent, pointer_id: i32) -> i32 {
        event.find_pointer_index(pointer_id) as i32
    }

    /// Returns the number of historical samples in the event.
    #[jni_method(critical)]
    fn nativeGetHistorySize(#[jlong_ref] event: &MotionEvent) -> i32 {
        event.history_size() as i32
    }

    /// Scales the event's coordinates and precision by `scale`.
    #[jni_method(critical)]
    fn nativeScale(#[jlong_mut] mut event: Pin<&mut MotionEvent>, scale: f32) {
        event.as_mut().scale(scale);
    }

    /// Returns the surface rotation encoded in the event's transform.
    ///
    /// Returns -1 when the transform is not a pure surface rotation.
    #[jni_method(critical)]
    fn nativeGetSurfaceRotation(#[jlong_ref] event: &MotionEvent) -> i32 {
        ffi::motion_event_get_surface_rotation(event)
    }
}

extern "C" {
    /// hwui's matrix helper (android/graphics/matrix.h): copies the Java
    /// `android.graphics.Matrix`'s nine values (row-major, SkMatrix layout)
    /// into `values`. Already `extern "C"`, so no cxx bridge is needed.
    fn AMatrix_getContents(
        env: *mut jni::sys::JNIEnv,
        matrix_obj: jobject,
        values: *mut f32,
    ) -> bool;
}
