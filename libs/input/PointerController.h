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

#ifndef _UI_POINTER_CONTROLLER_H
#define _UI_POINTER_CONTROLLER_H

#include <PointerControllerInterface.h>
#include <gui/DisplayEventReceiver.h>
#include <input/DisplayViewport.h>
#include <input/Input.h>
#include <ui/DisplayInfo.h>
#include <utils/BitSet.h>
#include <utils/Looper.h>
#include <utils/RefBase.h>

#include <map>
#include <memory>
#include <vector>

#include "SpriteController.h"

namespace android {

class MouseCursorController;
class TouchSpotController;
class PointerController;

/*
 * Pointer resources.
 */
struct PointerResources {
    SpriteIcon spotHover;
    SpriteIcon spotTouch;
    SpriteIcon spotAnchor;
};

struct PointerAnimation {
    std::vector<SpriteIcon> animationFrames;
    nsecs_t durationPerFrame;
};

enum class InactivityTimeout {
    NORMAL = 0,
    SHORT = 1,
};

/*
 * Pointer controller policy interface.
 *
 * The pointer controller policy is used by the pointer controller to interact with
 * the Window Manager and other system components.
 *
 * The actual implementation is partially supported by callbacks into the DVM
 * via JNI.  This interface is also mocked in the unit tests.
 */
class PointerControllerPolicyInterface : public virtual RefBase {
protected:
    PointerControllerPolicyInterface() { }
    virtual ~PointerControllerPolicyInterface() { }

public:
    virtual void loadPointerIcon(SpriteIcon* icon, int32_t displayId) = 0;
    virtual void loadPointerResources(PointerResources* outResources, int32_t displayId) = 0;
    virtual void loadAdditionalMouseResources(std::map<int32_t, SpriteIcon>* outResources,
            std::map<int32_t, PointerAnimation>* outAnimationResources, int32_t displayId) = 0;
    virtual int32_t getDefaultPointerIconId() = 0;
    virtual int32_t getCustomPointerIconId() = 0;
};

/*
 * Contains logic and resources shared among PointerController,
 * MouseCursorController, and TouchSpotController.
 */

class PointerControllerContext {
public:
    PointerControllerContext(const sp<PointerControllerPolicyInterface>& policy,
                             const sp<Looper>& looper, const sp<SpriteController>& spriteController,
                             std::shared_ptr<PointerController> controller);
    ~PointerControllerContext();

    void removeInactivityTimeout();
    void resetInactivityTimeout();
    void startAnimation();
    void setInactivityTimeout(InactivityTimeout inactivityTimeout);

    void setAnimationPending(bool animationPending);
    nsecs_t getAnimationTime();

    void clearSpotsByDisplay(int32_t displayId);

    void setHandlerController(std::shared_ptr<PointerController> controller);
    void setCallbackController(std::shared_ptr<PointerController> controller);

    sp<PointerControllerPolicyInterface> getPolicy();
    sp<SpriteController> getSpriteController();

    void initializeDisplayEventReceiver();
    void handleDisplayEvents();

    class MessageHandler : public virtual android::MessageHandler {
    public:
        enum {
            MSG_INACTIVITY_TIMEOUT,
        };

        void handleMessage(const Message& message) override;
        std::weak_ptr<PointerController> pointerController;
    };

    class LooperCallback : public virtual android::LooperCallback {
    public:
        int handleEvent(int fd, int events, void* data) override;
        std::weak_ptr<PointerController> pointerController;
    };

private:
    sp<PointerControllerPolicyInterface> mPolicy;
    sp<Looper> mLooper;
    sp<SpriteController> mSpriteController;
    sp<MessageHandler> mHandler;
    sp<LooperCallback> mCallback;

    DisplayEventReceiver mDisplayEventReceiver;

    std::weak_ptr<PointerController> mController;

    mutable Mutex mLock;

    struct Locked {
        bool animationPending;
        nsecs_t animationTime;

        InactivityTimeout inactivityTimeout;
    } mLocked GUARDED_BY(mLock);

    void resetInactivityTimeoutLocked();
};

/*
 * Tracks pointer movements and draws the pointer sprite to a surface.
 *
 * Handles pointer acceleration and animation.
 */
class PointerController : public PointerControllerInterface {
public:
    static std::shared_ptr<PointerController> create(
            const sp<PointerControllerPolicyInterface>& policy, const sp<Looper>& looper,
            const sp<SpriteController>& spriteController);

    virtual ~PointerController();

    virtual bool getBounds(float* outMinX, float* outMinY, float* outMaxX, float* outMaxY) const;
    virtual void move(float deltaX, float deltaY);
    virtual void setButtonState(int32_t buttonState);
    virtual int32_t getButtonState() const;
    virtual void setPosition(float x, float y);
    virtual void getPosition(float* outX, float* outY) const;
    virtual int32_t getDisplayId() const;
    virtual void fade(Transition transition);
    virtual void unfade(Transition transition);
    virtual void setDisplayViewport(const DisplayViewport& viewport);

    virtual void setPresentation(Presentation presentation);
    virtual void setSpots(const PointerCoords* spotCoords, const uint32_t* spotIdToIndex,
                          BitSet32 spotIdBits, int32_t displayId);
    virtual void clearSpots();

    void updatePointerIcon(int32_t iconId);
    void setCustomPointerIcon(const SpriteIcon& icon);
    void setInactivityTimeout(InactivityTimeout inactivityTimeout);
    void doInactivityTimeout();
    void doAnimate(nsecs_t timestamp);
    void reloadPointerResources();
    void onDisplayViewportsUpdated(std::vector<DisplayViewport>& viewports);

    // allows PointerControllerContext to use a weak_ptr for mController
    std::shared_ptr<PointerController> mSelf;

    PointerControllerContext mContext;

private:
    mutable Mutex mLock;

    std::shared_ptr<MouseCursorController> mCursorController;

    struct Locked {
        Presentation presentation;

        std::unordered_map<int32_t /* displayId */, std::shared_ptr<TouchSpotController>>
                spotControllers;
    } mLocked GUARDED_BY(mLock);

    PointerController(const sp<PointerControllerPolicyInterface>& policy, const sp<Looper>& looper,
                      const sp<SpriteController>& spriteController);
    void clearSpotsLocked();
};

} // namespace android

#endif // _UI_POINTER_CONTROLLER_H
