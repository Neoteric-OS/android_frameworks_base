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

namespace {
// Time to wait before starting the fade when the pointer is inactive.
const nsecs_t INACTIVITY_TIMEOUT_DELAY_TIME_NORMAL = 15 * 1000 * 1000000LL; // 15 seconds
const nsecs_t INACTIVITY_TIMEOUT_DELAY_TIME_SHORT = 3 * 1000 * 1000000LL;   // 3 seconds

// The number of events to be read at once for DisplayEventReceiver.
const int EVENT_BUFFER_SIZE = 100;
} // namespace

namespace android {

// --- PointerControllerContext ---

PointerControllerContext::PointerControllerContext(
        const sp<PointerControllerPolicyInterface>& policy, const sp<Looper>& looper,
        const sp<SpriteController>& spriteController, std::shared_ptr<PointerController> controller)
      : mPolicy(policy),
        mLooper(looper),
        mSpriteController(spriteController),
        mHandler(new MessageHandler()),
        mCallback(new LooperCallback()),
        mController(controller) {
    AutoMutex _l(mLock);
    mLocked.inactivityTimeout = InactivityTimeout::NORMAL;
    mLocked.animationPending = false;
}

PointerControllerContext::~PointerControllerContext() {
    mLooper->removeMessages(mHandler);
}

void PointerControllerContext::setInactivityTimeout(InactivityTimeout inactivityTimeout) {
    AutoMutex _l(mLock);

    if (mLocked.inactivityTimeout != inactivityTimeout) {
        mLocked.inactivityTimeout = inactivityTimeout;
        resetInactivityTimeoutLocked();
    }
}

void PointerControllerContext::startAnimation() {
    AutoMutex _l(mLock);
    if (!mLocked.animationPending) {
        mLocked.animationPending = true;
        mLocked.animationTime = systemTime(SYSTEM_TIME_MONOTONIC);
        mDisplayEventReceiver.requestNextVsync();
    }
}

void PointerControllerContext::resetInactivityTimeout() {
    AutoMutex _l(mLock);
    resetInactivityTimeoutLocked();
}

void PointerControllerContext::resetInactivityTimeoutLocked() REQUIRES(mLock) {
    mLooper->removeMessages(mHandler, MessageHandler::MSG_INACTIVITY_TIMEOUT);

    nsecs_t timeout = mLocked.inactivityTimeout == InactivityTimeout::SHORT
            ? INACTIVITY_TIMEOUT_DELAY_TIME_SHORT
            : INACTIVITY_TIMEOUT_DELAY_TIME_NORMAL;
    mLooper->sendMessageDelayed(timeout, mHandler, MessageHandler::MSG_INACTIVITY_TIMEOUT);
}

void PointerControllerContext::removeInactivityTimeout() {
    AutoMutex _l(mLock);
    mLooper->removeMessages(mHandler, MessageHandler::MSG_INACTIVITY_TIMEOUT);
}

void PointerControllerContext::setAnimationPending(bool animationPending) {
    AutoMutex _l(mLock);
    mLocked.animationPending = animationPending;
}

nsecs_t PointerControllerContext::getAnimationTime() {
    AutoMutex _l(mLock);
    return mLocked.animationTime;
}

void PointerControllerContext::setHandlerController(std::shared_ptr<PointerController> controller) {
    mHandler->pointerController = controller;
}

void PointerControllerContext::setCallbackController(
        std::shared_ptr<PointerController> controller) {
    mCallback->pointerController = controller;
}

sp<PointerControllerPolicyInterface> PointerControllerContext::getPolicy() {
    return mPolicy;
}

sp<SpriteController> PointerControllerContext::getSpriteController() {
    return mSpriteController;
}

void PointerControllerContext::initializeDisplayEventReceiver() {
    if (mDisplayEventReceiver.initCheck() == NO_ERROR) {
        mLooper->addFd(mDisplayEventReceiver.getFd(), Looper::POLL_CALLBACK, Looper::EVENT_INPUT,
                       mCallback, nullptr);
    } else {
        ALOGE("Failed to initialize DisplayEventReceiver.");
    }
}

void PointerControllerContext::handleDisplayEvents() {
    bool gotVsync = false;
    ssize_t n;
    nsecs_t timestamp;
    DisplayEventReceiver::Event buf[EVENT_BUFFER_SIZE];
    while ((n = mDisplayEventReceiver.getEvents(buf, EVENT_BUFFER_SIZE)) > 0) {
        for (size_t i = 0; i < static_cast<size_t>(n); ++i) {
            if (buf[i].header.type == DisplayEventReceiver::DISPLAY_EVENT_VSYNC) {
                timestamp = buf[i].header.timestamp;
                gotVsync = true;
            }
        }
    }
    if (gotVsync) {
        mController.lock()->doAnimate(timestamp);
    }
}

void PointerControllerContext::MessageHandler::handleMessage(const Message& message) {
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

int PointerControllerContext::LooperCallback::handleEvent(int /* fd */, int events,
                                                          void* /* data */) {
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

    controller->mContext.handleDisplayEvents();
    return 1;  // keep the callback
}

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

    controller->mContext.setHandlerController(controller);
    controller->mContext.setCallbackController(controller);
    controller->mContext.initializeDisplayEventReceiver();
    return controller;
}

PointerController::PointerController(const sp<PointerControllerPolicyInterface>& policy,
                                     const sp<Looper>& looper,
                                     const sp<SpriteController>& spriteController)
      : mSelf(std::shared_ptr<PointerController>(this)),
        mContext(policy, looper, spriteController, mSelf),
        mCursorController(std::make_shared<MouseCursorController>(mContext)) {
    AutoMutex _l(mLock);
    mLocked.presentation = Presentation::SPOT;
}

PointerController::~PointerController() {
    AutoMutex _l(mLock);
    mLocked.spotControllers.clear();
}

bool PointerController::getBounds(float* outMinX, float* outMinY, float* outMaxX,
                                  float* outMaxY) const {
    return mCursorController->getBounds(outMinX, outMinY, outMaxX, outMaxY);
}

void PointerController::move(float deltaX, float deltaY) {
    mCursorController->move(deltaX, deltaY);
}

void PointerController::setButtonState(int32_t buttonState) {
    mCursorController->setButtonState(buttonState);
}

int32_t PointerController::getButtonState() const {
    return mCursorController->getButtonState();
}

void PointerController::setPosition(float x, float y) {
    AutoMutex _l(mLock);
    mCursorController->setPosition(x, y);
}

void PointerController::getPosition(float* outX, float* outY) const {
    mCursorController->getPosition(outX, outY);
}

int32_t PointerController::getDisplayId() const {
    return mCursorController->getDisplayId();
}

void PointerController::fade(Transition transition) {
    AutoMutex _l(mLock);
    mCursorController->fade(transition);
}

void PointerController::unfade(Transition transition) {
    AutoMutex _l(mLock);
    mCursorController->unfade(transition);
}

void PointerController::setPresentation(Presentation presentation) {
    AutoMutex _l(mLock);

    if (mLocked.presentation == presentation) {
        return;
    }

    mLocked.presentation = presentation;

    if (!mCursorController->isViewportValid()) {
        return;
    }

    if (presentation == Presentation::POINTER) {
        mCursorController->getAdditionalMouseResources();
        clearSpotsLocked();
    }
}

void PointerController::setSpots(const PointerCoords* spotCoords, const uint32_t* spotIdToIndex,
                                 BitSet32 spotIdBits, int32_t displayId) {
    std::shared_ptr<TouchSpotController> spotController;
    AutoMutex _l(mLock);
    auto it = mLocked.spotControllers.find(displayId);
    if (it != mLocked.spotControllers.end()) {
        spotController = it->second;
    } else {
        spotController = std::make_shared<TouchSpotController>(displayId, mContext);
        mLocked.spotControllers[displayId] = spotController;
    }

    spotController->setSpots(spotCoords, spotIdToIndex, spotIdBits);
}

void PointerController::clearSpots() {
    AutoMutex _l(mLock);
    clearSpotsLocked();
}

void PointerController::clearSpotsLocked() REQUIRES(mLock) {
    for (auto& [displayID, spotController] : mLocked.spotControllers) {
        spotController->clearSpots();
    }
}

void PointerController::setInactivityTimeout(InactivityTimeout inactivityTimeout) {
    mContext.setInactivityTimeout(inactivityTimeout);
}

void PointerController::reloadPointerResources() {
    AutoMutex _l(mLock);

    for (auto& [displayID, spotController] : mLocked.spotControllers) {
        spotController->reloadSpotResources();
    }

    if (mCursorController->resourcesLoaded()) {
        bool getAdditionalMouseResources = false;
        if (mLocked.presentation == PointerController::Presentation::POINTER)
            getAdditionalMouseResources = true;
        mCursorController->reloadPointerResources(getAdditionalMouseResources);
    }
}

void PointerController::setDisplayViewport(const DisplayViewport& viewport) {
    AutoMutex _l(mLock);

    bool getAdditionalMouseResources = false;
    if (mLocked.presentation == PointerController::Presentation::POINTER)
        getAdditionalMouseResources = true;
    mCursorController->setDisplayViewport(viewport, getAdditionalMouseResources);
}

void PointerController::updatePointerIcon(int32_t iconId) {
    AutoMutex _l(mLock);
    mCursorController->updatePointerIcon(iconId);
}

void PointerController::setCustomPointerIcon(const SpriteIcon& icon) {
    AutoMutex _l(mLock);
    mCursorController->setCustomPointerIcon(icon);
}

void PointerController::doAnimate(nsecs_t timestamp) {
    AutoMutex _l(mLock);

    mContext.setAnimationPending(false);

    bool keepFading = false;
    keepFading = mCursorController->doFadingAnimation(timestamp, keepFading);

    for (auto& [displayID, spotController] : mLocked.spotControllers) {
        keepFading = spotController->doFadingAnimation(timestamp, keepFading);
    }

    bool keepBitmapFlipping = mCursorController->doBitmapAnimation(timestamp);
    if (keepFading || keepBitmapFlipping) {
        mContext.startAnimation();
    }
}

void PointerController::doInactivityTimeout() {
    fade(Transition::GRADUAL);
}

void PointerController::onDisplayViewportsUpdated(std::vector<DisplayViewport>& viewports) {
    std::unordered_set<int32_t> displayIdSet;
    for (DisplayViewport viewport : viewports) {
        displayIdSet.insert(viewport.displayId);
    }

    AutoMutex _l(mLock);
    for (auto it = mLocked.spotControllers.begin(); it != mLocked.spotControllers.end();) {
        int32_t displayID = it->first;
        if (!displayIdSet.count(displayID)) {
            it = mLocked.spotControllers.erase(it);
        } else {
            ++it;
        }
    }
}

} // namespace android
