/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <jni.h>

#include <cstddef>
#include <cstdint>
#include <memory>

#include <input/Input.h>

#include "rust/cxx.h"

// C++ half of the cxx bridge declared in
// src/motion_event_ffi.rs; see that file for the semantics
// of each function. These shims cover the android::MotionEvent member
// functions whose signatures cxx cannot express (ftl::Flags, ui types,
// std::bitset, std::optional) and the composite initialize/add-sample
// operations that own the ui::Transform loops.
namespace android_runtime {

// Defined by cxx in the generated motion_event_ffi.rs.h, which includes this
// header first.
struct MotionEventInitArgs;

std::unique_ptr<android::MotionEvent> motion_event_new();
void motion_event_destroy(android::MotionEvent* event);
void motion_event_copy_from(android::MotionEvent& dest, const android::MotionEvent& source,
                            bool keep_history);
void motion_event_split_from(android::MotionEvent& dest, const android::MotionEvent& source,
                             uint32_t split_pointer_ids, int32_t new_event_id);
void motion_event_initialize(android::MotionEvent& event, const MotionEventInitArgs& args,
                             rust::Slice<const android::PointerProperties> pointer_properties,
                             rust::Slice<android::PointerCoords> raw_pointer_coords);
void motion_event_add_sample_transformed(android::MotionEvent& event, int64_t event_time,
                                         rust::Slice<android::PointerCoords> raw_pointer_coords);
int32_t motion_event_get_display_id(const android::MotionEvent& event);
void motion_event_set_display_id(android::MotionEvent& event, int32_t display_id);
int32_t motion_event_get_flags(const android::MotionEvent& event);
void motion_event_set_flags(android::MotionEvent& event, int32_t flags);
int32_t motion_event_private_flag_mask();
int32_t motion_event_get_classification(const android::MotionEvent& event);
int32_t motion_event_get_tool_type(const android::MotionEvent& event, size_t pointer_index);
int32_t motion_event_get_surface_rotation(const android::MotionEvent& event);
const android::PointerCoords& motion_event_raw_pointer_coords(const android::MotionEvent& event,
                                                              size_t pointer_index);
const android::PointerCoords& motion_event_historical_raw_pointer_coords(
        const android::MotionEvent& event, size_t pointer_index, size_t historical_index);
const android::PointerProperties& motion_event_pointer_properties(
        const android::MotionEvent& event, size_t pointer_index);
rust::String motion_event_to_string(const android::MotionEvent& event);
const char* motion_event_axis_label(int32_t axis);
int32_t motion_event_axis_from_label(const char* label);

// Parcel IO needs a live JNIEnv (parcelForJavaObject), which cannot cross
// the cxx bridge, so these two are plain C ABI functions declared in Rust as
// extern "C". They live in motion_event_parcel.cpp — a separate translation
// unit so that binaries without libandroid_runtime's parcelForJavaObject
// (the host test) never pull in the reference. Both return a status_t.
extern "C" int32_t android_runtime_motion_event_read_from_parcel(android::MotionEvent* event,
                                                                 JNIEnv* env, jobject parcel_obj);
extern "C" int32_t android_runtime_motion_event_write_to_parcel(const android::MotionEvent* event,
                                                                JNIEnv* env, jobject parcel_obj);

}  // namespace android_runtime
