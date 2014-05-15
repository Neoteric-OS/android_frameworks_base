/*
 * Copyright (C) 2011 The Android Open Source Project
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

#define LOG_TAG "InputListener"

//#define LOG_NDEBUG 0

#include "InputListener.h"

#include <cutils/log.h>
#include <dlfcn.h>

namespace android {

// --- Event Processing ---

typedef int (*EventProcessingInit)();
typedef int (*EventProcessingFini)();
typedef NotifyMotionArgs* (*EventProcessing)(const NotifyMotionArgs&);
static void* event_processing_handle = NULL;
EventProcessing event_processing = NULL;

/*  Initializes event processing module. */
static void event_processing_init_impl() {
    const char* so_name = "/vendor/lib/libeventprocessing.so";
    event_processing_handle = event_processing_handle ?
        event_processing_handle : dlopen(so_name, RTLD_LAZY);
    if (event_processing_handle == NULL) {
        ALOGE("Missing module %s required for event processing: %s", so_name, dlerror());
        return;
    }
    // Initialize event processing in the loaded module.
    EventProcessingInit event_processing_initialize = reinterpret_cast<EventProcessingInit>(
        dlsym(event_processing_handle, "event_processing_initialize"));
    if (event_processing_initialize == NULL) {
        ALOGE("Initialization routine is not found in %s\n", so_name);
        dlclose(event_processing_handle);
        return;
    }
    if (event_processing_initialize() == -1) {
        dlclose(event_processing_handle);
        return;
    }

    event_processing = reinterpret_cast<EventProcessing>(dlsym(event_processing_handle,
                                                               "event_processing"));
    if (event_processing == NULL) {
        ALOGE("dlsym(\"event_processing\") failed");
    }
}

static void event_processing_fini_impl() {
    if (event_processing_handle != NULL) {
        EventProcessingFini event_processing_finalize =
            reinterpret_cast<EventProcessingFini>(dlsym(event_processing_handle,
                                                        "event_processing_finalize"));
        if (event_processing_finalize != NULL) {
            event_processing_finalize();
        }
    }
}

// --- NotifyConfigurationChangedArgs ---

NotifyConfigurationChangedArgs::NotifyConfigurationChangedArgs(nsecs_t eventTime) :
        eventTime(eventTime) {
}

NotifyConfigurationChangedArgs::NotifyConfigurationChangedArgs(
        const NotifyConfigurationChangedArgs& other) :
        eventTime(other.eventTime) {
}

void NotifyConfigurationChangedArgs::notify(const sp<InputListenerInterface>& listener) const {
    listener->notifyConfigurationChanged(this);
}


// --- NotifyKeyArgs ---

NotifyKeyArgs::NotifyKeyArgs(nsecs_t eventTime, int32_t deviceId, uint32_t source,
        uint32_t policyFlags,
        int32_t action, int32_t flags, int32_t keyCode, int32_t scanCode,
        int32_t metaState, nsecs_t downTime) :
        eventTime(eventTime), deviceId(deviceId), source(source), policyFlags(policyFlags),
        action(action), flags(flags), keyCode(keyCode), scanCode(scanCode),
        metaState(metaState), downTime(downTime) {
}

NotifyKeyArgs::NotifyKeyArgs(const NotifyKeyArgs& other) :
        eventTime(other.eventTime), deviceId(other.deviceId), source(other.source),
        policyFlags(other.policyFlags),
        action(other.action), flags(other.flags),
        keyCode(other.keyCode), scanCode(other.scanCode),
        metaState(other.metaState), downTime(other.downTime) {
}

void NotifyKeyArgs::notify(const sp<InputListenerInterface>& listener) const {
    listener->notifyKey(this);
}


// --- NotifyMotionArgs ---

NotifyMotionArgs::NotifyMotionArgs(nsecs_t eventTime, int32_t deviceId, uint32_t source,
        uint32_t policyFlags,
        int32_t action, int32_t flags, int32_t metaState, int32_t buttonState,
        int32_t edgeFlags, int32_t displayId, uint32_t pointerCount,
        const PointerProperties* pointerProperties, const PointerCoords* pointerCoords,
        float xPrecision, float yPrecision, nsecs_t downTime) :
        eventTime(eventTime), deviceId(deviceId), source(source), policyFlags(policyFlags),
        action(action), flags(flags), metaState(metaState), buttonState(buttonState),
        edgeFlags(edgeFlags), displayId(displayId), pointerCount(pointerCount),
        xPrecision(xPrecision), yPrecision(yPrecision), downTime(downTime) {
    for (uint32_t i = 0; i < pointerCount; i++) {
        this->pointerProperties[i].copyFrom(pointerProperties[i]);
        this->pointerCoords[i].copyFrom(pointerCoords[i]);
    }
}

NotifyMotionArgs::NotifyMotionArgs(const NotifyMotionArgs& other) :
        eventTime(other.eventTime), deviceId(other.deviceId), source(other.source),
        policyFlags(other.policyFlags),
        action(other.action), flags(other.flags),
        metaState(other.metaState), buttonState(other.buttonState),
        edgeFlags(other.edgeFlags), displayId(other.displayId),
        pointerCount(other.pointerCount),
        xPrecision(other.xPrecision), yPrecision(other.yPrecision), downTime(other.downTime) {
    for (uint32_t i = 0; i < pointerCount; i++) {
        pointerProperties[i].copyFrom(other.pointerProperties[i]);
        pointerCoords[i].copyFrom(other.pointerCoords[i]);
    }
}

void NotifyMotionArgs::notify(const sp<InputListenerInterface>& listener) const {
    listener->notifyMotion(this);
}


// --- NotifySwitchArgs ---

NotifySwitchArgs::NotifySwitchArgs(nsecs_t eventTime, uint32_t policyFlags,
        uint32_t switchValues, uint32_t switchMask) :
        eventTime(eventTime), policyFlags(policyFlags),
        switchValues(switchValues), switchMask(switchMask) {
}

NotifySwitchArgs::NotifySwitchArgs(const NotifySwitchArgs& other) :
        eventTime(other.eventTime), policyFlags(other.policyFlags),
        switchValues(other.switchValues), switchMask(other.switchMask) {
}

void NotifySwitchArgs::notify(const sp<InputListenerInterface>& listener) const {
    listener->notifySwitch(this);
}


// --- NotifyDeviceResetArgs ---

NotifyDeviceResetArgs::NotifyDeviceResetArgs(nsecs_t eventTime, int32_t deviceId) :
        eventTime(eventTime), deviceId(deviceId) {
}

NotifyDeviceResetArgs::NotifyDeviceResetArgs(const NotifyDeviceResetArgs& other) :
        eventTime(other.eventTime), deviceId(other.deviceId) {
}

void NotifyDeviceResetArgs::notify(const sp<InputListenerInterface>& listener) const {
    listener->notifyDeviceReset(this);
}


// --- QueuedInputListener ---

QueuedInputListener::QueuedInputListener(const sp<InputListenerInterface>& innerListener) :
        mInnerListener(innerListener) {
    event_processing_init_impl();
}

QueuedInputListener::~QueuedInputListener() {
    size_t count = mArgsQueue.size();
    for (size_t i = 0; i < count; i++) {
        delete mArgsQueue[i];
    }
    event_processing_fini_impl();
}

void QueuedInputListener::notifyConfigurationChanged(
        const NotifyConfigurationChangedArgs* args) {
    mArgsQueue.push(new NotifyConfigurationChangedArgs(*args));
}

void QueuedInputListener::notifyKey(const NotifyKeyArgs* args) {
    mArgsQueue.push(new NotifyKeyArgs(*args));
}

void QueuedInputListener::notifyMotion(const NotifyMotionArgs* args) {
    if (event_processing == NULL) {
        mArgsQueue.push(new NotifyMotionArgs(*args));
    } else {
        mArgsQueue.push(event_processing(*args));
    }
}

void QueuedInputListener::notifySwitch(const NotifySwitchArgs* args) {
    mArgsQueue.push(new NotifySwitchArgs(*args));
}

void QueuedInputListener::notifyDeviceReset(const NotifyDeviceResetArgs* args) {
    mArgsQueue.push(new NotifyDeviceResetArgs(*args));
}

void QueuedInputListener::flush() {
    size_t count = mArgsQueue.size();
    for (size_t i = 0; i < count; i++) {
        NotifyArgs* args = mArgsQueue[i];
        args->notify(mInnerListener);
        delete args;
    }
    mArgsQueue.clear();
}


} // namespace android
