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

#ifndef _UI_MOUSE_CURSOR_CONTROLLER_H
#define _UI_MOUSE_CURSOR_CONTROLLER_H

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

#include "PointerController.h"
#include "SpriteController.h"

namespace android {

/*
 * Helper class for PointerController that specifically handles
 * mouse cursor resources and actions.
 */
class MouseCursorController {
public:
    MouseCursorController(sp<SpriteController> mSpriteController,
                          sp<PointerControllerPolicyInterface> mPolicy);
    ~MouseCursorController();

    bool getBounds(float* outMinX, float* outMinY, float* outMaxX, float* outMaxY) const;
    void move(float deltaX, float deltaY, PointerController* mPointerController);
    void setButtonState(int32_t buttonState);
    int32_t getButtonState() const;
    void setPosition(float x, float y, PointerController* mPointerController);
    void getPosition(float* outX, float* outY) const;
    int32_t getDisplayId() const;
    void fade(PointerController::Transition transition, PointerController* mPointerController);
    void unfade(PointerController::Transition transition, PointerController* mPointerController);
    void setDisplayViewport(const DisplayViewport& viewport, PointerController* mPointerController);

    void updatePointerIcon(int32_t iconId, PointerController* mPointerController);
    void setCustomPointerIcon(const SpriteIcon& icon, PointerController* mPointerController);
    void reloadPointerResources(PointerController* mPointerController);

    void getAdditionalMouseResources(PointerController* mPointerController);
    bool viewportIsValid();

    bool doBitmapAnimation(nsecs_t timestamp, sp<SpriteController> mSpriteController);
    bool doFadingAnimation(nsecs_t timestamp, bool keepAnimating,
                           PointerController* mPointerController);

private:
    mutable Mutex mLock;

    PointerResources* mResources;

    struct Locked {
        DisplayViewport viewport;

        size_t animationFrameIndex;
        nsecs_t lastFrameUpdatedTime;

        int32_t pointerFadeDirection;
        float pointerX;
        float pointerY;
        float pointerAlpha;
        sp<Sprite> pointerSprite;
        SpriteIcon pointerIcon;
        bool pointerIconChanged;

        std::map<int32_t, SpriteIcon> additionalMouseResources;
        std::map<int32_t, PointerAnimation> animationResources;

        int32_t requestedPointerType;

        int32_t buttonState;

    } mLocked GUARDED_BY(mLock);

    bool getBoundsLocked(float* outMinX, float* outMinY, float* outMaxX, float* outMaxY) const;
    void setPositionLocked(float x, float y, PointerController* mPointerController);

    void updatePointerLocked(PointerController* mPointerController);

    void loadResourcesLocked(sp<PointerControllerPolicyInterface> mPolicy,
                             PointerController::Presentation presentation);
};

} // namespace android

#endif // _UI_MOUSE_CURSOR_CONTROLLER_H
