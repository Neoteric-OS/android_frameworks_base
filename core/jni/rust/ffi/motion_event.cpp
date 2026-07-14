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

#include "motion_event.h"

#include <android/input.h>
#include <attestation/HmacKeyManager.h>
#include <ftl/flags.h>
#include <input/Input.h>
#include <log/log.h>
#include <ui/LogicalDisplayId.h>
#include <ui/Rotation.h>
#include <ui/Transform.h>

#include <bitset>
#include <cstddef>
#include <cstdint>
#include <optional>
#include <sstream>
#include <type_traits>

#include "motion_event_ffi.rs.h"

// The layout contract behind the trivial ExternType mirrors declared in
// src/motion_event_compat.rs, which carries the identical
// const assertions; a change to either side fails to compile until both
// agree. All values are fixed across 32- and 64-bit targets: the bits field
// is explicitly aligned(8) in Input.h (unlike a plain int64, which i386
// would align to 4), and every other field is 4 bytes or smaller.
static_assert(std::is_trivially_copyable_v<android::PointerCoords>);
static_assert(std::is_trivially_destructible_v<android::PointerCoords>);
static_assert(std::is_standard_layout_v<android::PointerCoords>);
static_assert(sizeof(android::PointerCoords) == 136);
static_assert(alignof(android::PointerCoords) == 8);
static_assert(offsetof(android::PointerCoords, bits) == 0);
static_assert(offsetof(android::PointerCoords, values) == 8);
static_assert(offsetof(android::PointerCoords, isResampled) == 128);
static_assert(offsetof(android::PointerCoords, empty) == 129);

static_assert(std::is_trivially_copyable_v<android::PointerProperties>);
static_assert(std::is_trivially_destructible_v<android::PointerProperties>);
static_assert(std::is_standard_layout_v<android::PointerProperties>);
static_assert(std::is_same_v<std::underlying_type_t<android::ToolType>, int>);
static_assert(sizeof(android::PointerProperties) == 8);
static_assert(alignof(android::PointerProperties) == alignof(int32_t));
static_assert(offsetof(android::PointerProperties, id) == 0);
static_assert(offsetof(android::PointerProperties, toolType) == 4);

namespace android_runtime {

std::unique_ptr<android::MotionEvent> motion_event_new() {
    return std::make_unique<android::MotionEvent>();
}

void motion_event_destroy(android::MotionEvent* event) {
    delete event;
}

void motion_event_copy_from(android::MotionEvent& dest, const android::MotionEvent& source,
                            bool keep_history) {
    dest.copyFrom(&source, keep_history);
}

void motion_event_split_from(android::MotionEvent& dest, const android::MotionEvent& source,
                             uint32_t split_pointer_ids, int32_t new_event_id) {
    dest.splitFrom(source, std::bitset<MAX_POINTER_ID + 1>(split_pointer_ids), new_event_id);
}

void motion_event_initialize(android::MotionEvent& event, const MotionEventInitArgs& args,
                             rust::Slice<const android::PointerProperties> pointer_properties,
                             rust::Slice<android::PointerCoords> raw_pointer_coords) {
    LOG_ALWAYS_FATAL_IF(pointer_properties.size() != raw_pointer_coords.size(),
                        "mismatched pointer slices: %zu properties, %zu coords",
                        pointer_properties.size(), raw_pointer_coords.size());

    android::ftl::Flags<android::MotionFlag> flags(static_cast<uint32_t>(args.flags));

    android::ui::Transform transform;
    transform.set(args.x_offset, args.y_offset);
    const android::ui::Transform inverseTransform = transform.inverse();

    // As in the old nativeInitialize, an orientation-carrying pointer
    // promotes the private orientation flags for itself and all later
    // pointers' transforms.
    for (android::PointerCoords& coords : raw_pointer_coords) {
        if (coords.getAxisValue(AMOTION_EVENT_AXIS_ORIENTATION) != 0.f) {
            flags |= android::MotionFlag::SUPPORTS_ORIENTATION;
            flags |= android::MotionFlag::SUPPORTS_DIRECTIONAL_ORIENTATION;
        }
        android::MotionEvent::calculateTransformedCoordsInPlace(coords, args.source, flags,
                                                                inverseTransform);
    }

    static const android::ui::Transform kIdentityTransform;
    event.initialize(args.id, args.device_id, args.source,
                     android::ui::LogicalDisplayId{args.display_id}, android::INVALID_HMAC,
                     args.action, args.action_button, flags, args.edge_flags, args.meta_state,
                     args.button_state,
                     static_cast<android::MotionClassification>(args.classification), transform,
                     args.x_precision, args.y_precision,
                     android::AMOTION_EVENT_INVALID_CURSOR_POSITION,
                     android::AMOTION_EVENT_INVALID_CURSOR_POSITION, kIdentityTransform,
                     args.down_time,
                     args.event_time, pointer_properties.size(), pointer_properties.data(),
                     raw_pointer_coords.data());
}

void motion_event_add_sample_transformed(android::MotionEvent& event, int64_t event_time,
                                         rust::Slice<android::PointerCoords> raw_pointer_coords) {
    LOG_ALWAYS_FATAL_IF(raw_pointer_coords.size() != event.getPointerCount(),
                        "expected %zu coords, got %zu", event.getPointerCount(),
                        raw_pointer_coords.size());

    const android::ui::Transform inverseTransform = event.getTransform().inverse();
    for (android::PointerCoords& coords : raw_pointer_coords) {
        android::MotionEvent::calculateTransformedCoordsInPlace(coords, event.getSource(),
                                                                event.getFlags(),
                                                                inverseTransform);
    }
    event.addSample(event_time, raw_pointer_coords.data(), event.getId());
}

int32_t motion_event_get_display_id(const android::MotionEvent& event) {
    return event.getDisplayId().val();
}

void motion_event_set_display_id(android::MotionEvent& event, int32_t display_id) {
    event.setDisplayId(android::ui::LogicalDisplayId{display_id});
}

int32_t motion_event_get_flags(const android::MotionEvent& event) {
    return static_cast<int32_t>(event.getFlags().get());
}

void motion_event_set_flags(android::MotionEvent& event, int32_t flags) {
    event.setFlags(android::ftl::Flags<android::MotionFlag>(static_cast<uint32_t>(flags)));
}

int32_t motion_event_private_flag_mask() {
    return AMOTION_EVENT_PRIVATE_FLAG_MASK;
}

int32_t motion_event_get_classification(const android::MotionEvent& event) {
    return static_cast<int32_t>(event.getClassification());
}

int32_t motion_event_get_tool_type(const android::MotionEvent& event, size_t pointer_index) {
    LOG_ALWAYS_FATAL_IF(pointer_index >= event.getPointerCount(), "invalid pointer index %zu",
                        pointer_index);
    return static_cast<int32_t>(event.getToolType(pointer_index));
}

int32_t motion_event_get_surface_rotation(const android::MotionEvent& event) {
    std::optional<android::ui::Rotation> rotation = event.getSurfaceRotation();
    return rotation.has_value() ? static_cast<int32_t>(*rotation) : -1;
}

const android::PointerCoords& motion_event_raw_pointer_coords(const android::MotionEvent& event,
                                                              size_t pointer_index) {
    return *event.getRawPointerCoords(pointer_index);
}

const android::PointerCoords& motion_event_historical_raw_pointer_coords(
        const android::MotionEvent& event, size_t pointer_index, size_t historical_index) {
    return *event.getHistoricalRawPointerCoords(pointer_index, historical_index);
}

const android::PointerProperties& motion_event_pointer_properties(
        const android::MotionEvent& event, size_t pointer_index) {
    LOG_ALWAYS_FATAL_IF(pointer_index >= event.getPointerCount(), "invalid pointer index %zu",
                        pointer_index);
    return *event.getPointerProperties(pointer_index);
}

rust::String motion_event_to_string(const android::MotionEvent& event) {
    std::ostringstream out;
    out << event;
    return rust::String::lossy(out.str());
}

const char* motion_event_axis_label(int32_t axis) {
    return android::MotionEvent::getLabel(axis);
}

int32_t motion_event_axis_from_label(const char* label) {
    return android::MotionEvent::getAxisFromLabel(label).value_or(-1);
}

}  // namespace android_runtime
