/*
 * Copyright (C) 2010 The Android Open Source Project
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

#define LOG_TAG "PointerController"
//#define LOG_NDEBUG 0

// Log debug messages about pointer updates
#define DEBUG_POINTER_UPDATES 0

#include "PointerController.h"
#include "MouseCursorController.h"
#include "TouchSpotController.h"

#include <log/log.h>

#include <SkBitmap.h>
#include <SkBlendMode.h>
#include <SkCanvas.h>
#include <SkColor.h>
#include <SkPaint.h>

namespace android {

// Time to wait before starting the fade when the pointer is inactive.
static const nsecs_t INACTIVITY_TIMEOUT_DELAY_TIME_NORMAL = 15 * 1000 * 1000000LL; // 15 seconds
static const nsecs_t INACTIVITY_TIMEOUT_DELAY_TIME_SHORT = 3 * 1000 * 1000000LL; // 3 seconds

// The number of events to be read at once for DisplayEventReceiver.
static const int EVENT_BUFFER_SIZE = 100;

// --- PointerController ---

std::shared_ptr<PointerController> PointerController::create(
        const sp<PointerControllerPolicyInterface>& policy, const sp<Looper>& looper,
        const sp<SpriteController>& spriteController) {
    std::shared_ptr<PointerController> controller = std::shared_ptr<PointerController>(
            new PointerController(policy, looper, spriteController));

    /*
     * Now we need to hook up the constructed PointerController object to its callbacks.
     *
     * This must be executed after the constructor but before any other methods on PointerController
     * in order to ensure that the fully constructed object is visible on the Looper thread, since
     * that may be a different thread than where the PointerController is initially constructed.
     *
     * Unfortunately, this cannot be done as part of the constructor since we need to hand out
     * weak_ptr's which themselves cannot be constructed until there's at least one shared_ptr.
     */

    controller->mHandler->pointerController = controller;
    controller->mCallback->pointerController = controller;
    if (controller->mDisplayEventReceiver.initCheck() == NO_ERROR) {
        controller->mLooper->addFd(controller->mDisplayEventReceiver.getFd(), Looper::POLL_CALLBACK,
                                   Looper::EVENT_INPUT, controller->mCallback, nullptr);
    } else {
        ALOGE("Failed to initialize DisplayEventReceiver.");
    }
    return controller;
}

PointerController::PointerController(const sp<PointerControllerPolicyInterface>& policy,
                                     const sp<Looper>& looper,
                                     const sp<SpriteController>& spriteController)
      : mPolicy(policy),
        mLooper(looper),
        mSpriteController(spriteController),
        mHandler(new MessageHandler()),
        mCallback(new LooperCallback()),
        mCursorController(std::shared_ptr<MouseCursorController>(
                new MouseCursorController(mSpriteController, mPolicy))) {
    AutoMutex _l(mLock);

    mLocked.inactivityTimeout = PointerController::InactivityTimeout::NORMAL;

    mLocked.animationPending = false;

    mLocked.presentation = Presentation::POINTER;
    mLocked.presentationChanged = false;
}

PointerController::~PointerController() {
    mLooper->removeMessages(mHandler);

    AutoMutex _l(mLock);
    mLocked.spotControllers.clear();
}

bool PointerController::getBounds(float* outMinX, float* outMinY, float* outMaxX,
                                  float* outMaxY) const {
    return mCursorController->getBounds(outMinX, outMinY, outMaxX, outMaxY);
}

void PointerController::move(float deltaX, float deltaY) {
    mCursorController->move(deltaX, deltaY, this);
}

void PointerController::setButtonState(int32_t buttonState) {
    mCursorController->setButtonState(buttonState);
}

int32_t PointerController::getButtonState() const {
    return mCursorController->getButtonState();
}

void PointerController::setPosition(float x, float y) {
    AutoMutex _l(mLock);
    mCursorController->setPosition(x, y, this);
}

void PointerController::getPosition(float* outX, float* outY) const {
    mCursorController->getPosition(outX, outY);
}

int32_t PointerController::getDisplayId() const {
    return mCursorController->getDisplayId();
}

void PointerController::fade(Transition transition) {
    AutoMutex _l(mLock);
    mCursorController->fade(transition, this);
}

void PointerController::unfade(Transition transition) {
    AutoMutex _l(mLock);
    mCursorController->unfade(transition, this);
}

void PointerController::setPresentation(Presentation presentation) {
    AutoMutex _l(mLock);

    if (mLocked.presentation == presentation) {
        return;
    }

    mLocked.presentation = presentation;
    mLocked.presentationChanged = true;

    if (!mCursorController->viewportIsValid()) {
        return;
    }

    if (presentation == Presentation::POINTER) {
        mCursorController->getAdditionalMouseResources(this);
        clearSpotsLocked();
    }
}

void PointerController::setSpots(const PointerCoords* spotCoords,
        const uint32_t* spotIdToIndex, BitSet32 spotIdBits, int32_t displayId) {
    std::shared_ptr<TouchSpotController> mSpotController;
    AutoMutex _l(mLock);
    auto it = mLocked.spotControllers.find(displayId);
    if (it != mLocked.spotControllers.end()) {
        mSpotController = it->second;
    } else {
        mSpotController =
                std::shared_ptr<TouchSpotController>(new TouchSpotController(displayId, mPolicy));
        mLocked.spotControllers[displayId] = mSpotController;
    }

    mSpotController->setSpots(spotCoords, spotIdToIndex, spotIdBits, this);
}

void PointerController::clearSpots() {
    AutoMutex _l(mLock);
    clearSpotsLocked();
}

void PointerController::clearSpotsLocked() {
    for (auto it : mLocked.spotControllers) {
        std::shared_ptr<TouchSpotController> mSpotController = it.second;
        mSpotController->clearSpots(this);
    }
}

void PointerController::setInactivityTimeout(
        PointerController::InactivityTimeout inactivityTimeout) {
    AutoMutex _l(mLock);

    if (mLocked.inactivityTimeout != inactivityTimeout) {
        mLocked.inactivityTimeout = inactivityTimeout;
        resetInactivityTimeoutLocked();
    }
}

void PointerController::reloadPointerResources() {
    AutoMutex _l(mLock);

    for (auto it = mLocked.spotControllers.begin(); it != mLocked.spotControllers.end(); it++) {
        std::shared_ptr<TouchSpotController> mSpotController = it->second;
        mSpotController->reloadSpotResources(mPolicy);
    }

    mCursorController->reloadPointerResources(this);
}

void PointerController::setDisplayViewport(const DisplayViewport& viewport) {
    AutoMutex _l(mLock);
    mCursorController->setDisplayViewport(viewport, this);
}

void PointerController::updatePointerIcon(int32_t iconId) {
    AutoMutex _l(mLock);
    mCursorController->updatePointerIcon(iconId, this);
}

void PointerController::setCustomPointerIcon(const SpriteIcon& icon) {
    AutoMutex _l(mLock);
    mCursorController->setCustomPointerIcon(icon, this);
}

void PointerController::MessageHandler::handleMessage(const Message& message) {
    std::shared_ptr<PointerController> controller = pointerController.lock();

    if (controller == nullptr) {
        ALOGE("PointerController instance was released before processing message: what=%d",
              message.what);
        return;
    }
    switch (message.what) {
    case MSG_INACTIVITY_TIMEOUT:
        controller->doInactivityTimeout();
        break;
    }
}

int PointerController::LooperCallback::handleEvent(int /* fd */, int events, void* /* data */) {
    std::shared_ptr<PointerController> controller = pointerController.lock();
    if (controller == nullptr) {
        ALOGW("PointerController instance was released with pending callbacks.  events=0x%x",
              events);
        return 0; // Remove the callback, the PointerController is gone anyways
    }
    if (events & (Looper::EVENT_ERROR | Looper::EVENT_HANGUP)) {
        ALOGE("Display event receiver pipe was closed or an error occurred.  events=0x%x", events);
        return 0; // remove the callback
    }

    if (!(events & Looper::EVENT_INPUT)) {
        ALOGW("Received spurious callback for unhandled poll event.  events=0x%x", events);
        return 1; // keep the callback
    }

    bool gotVsync = false;
    ssize_t n;
    nsecs_t timestamp;
    DisplayEventReceiver::Event buf[EVENT_BUFFER_SIZE];
    while ((n = controller->mDisplayEventReceiver.getEvents(buf, EVENT_BUFFER_SIZE)) > 0) {
        for (size_t i = 0; i < static_cast<size_t>(n); ++i) {
            if (buf[i].header.type == DisplayEventReceiver::DISPLAY_EVENT_VSYNC) {
                timestamp = buf[i].header.timestamp;
                gotVsync = true;
            }
        }
    }
    if (gotVsync) {
        controller->doAnimate(timestamp);
    }
    return 1;  // keep the callback
}

void PointerController::doAnimate(nsecs_t timestamp) {
    AutoMutex _l(mLock);

    mLocked.animationPending = false;

    bool keepFading = false;
    keepFading = mCursorController->doFadingAnimation(timestamp, keepFading, this);

    for (auto it = mLocked.spotControllers.begin(); it != mLocked.spotControllers.end();) {
        std::shared_ptr<TouchSpotController> mSpotController = it->second;
        keepFading =
                mSpotController->doFadingAnimation(timestamp, keepFading, mLocked.animationTime);
        if (mSpotController->numSpots() == 0) {
            it = mLocked.spotControllers.erase(it);
        } else {
            ++it;
        }
    }

    bool keepBitmapFlipping = mCursorController->doBitmapAnimation(timestamp, mSpriteController);
    if (keepFading || keepBitmapFlipping) {
        startAnimationLocked();
    }
}

void PointerController::doInactivityTimeout() {
    fade(Transition::GRADUAL);
}

void PointerController::startAnimationLocked() {
    if (!mLocked.animationPending) {
        mLocked.animationPending = true;
        mLocked.animationTime = systemTime(SYSTEM_TIME_MONOTONIC);
        mDisplayEventReceiver.requestNextVsync();
    }
}

void PointerController::resetInactivityTimeoutLocked() {
    mLooper->removeMessages(mHandler, MSG_INACTIVITY_TIMEOUT);

    nsecs_t timeout = mLocked.inactivityTimeout == PointerController::InactivityTimeout::SHORT
            ? INACTIVITY_TIMEOUT_DELAY_TIME_SHORT
            : INACTIVITY_TIMEOUT_DELAY_TIME_NORMAL;
    mLooper->sendMessageDelayed(timeout, mHandler, MSG_INACTIVITY_TIMEOUT);
}

void PointerController::removeInactivityTimeoutLocked() {
    mLooper->removeMessages(mHandler, MSG_INACTIVITY_TIMEOUT);
}

} // namespace android