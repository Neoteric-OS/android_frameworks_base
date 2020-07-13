/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "mocks/MockSprite.h"
#include "mocks/MockSpriteController.h"

#include <input/CursorController.h>
#include <input/SpriteController.h>

#include <atomic>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <thread>

namespace android {

enum TestCursorType {
    CURSOR_TYPE_DEFAULT = 0,
    CURSOR_TYPE_HOVER,
    CURSOR_TYPE_TOUCH,
    CURSOR_TYPE_ANCHOR,
    CURSOR_TYPE_ADDITIONAL,
    CURSOR_TYPE_ADDITIONAL_ANIM,
    CURSOR_TYPE_CUSTOM = -1,
};

using ::testing::AllOf;
using ::testing::Field;
using ::testing::Mock;
using ::testing::NiceMock;
using ::testing::Return;
using ::testing::Test;

std::pair<float, float> getHotSpotCoordinatesForType(int32_t type) {
    return std::make_pair(type * 10, type * 10 + 5);
}

class MockCursorControllerPolicyInterface : public CursorControllerPolicyInterface {
public:
    virtual void loadPointerIcon(SpriteIcon* icon, int32_t displayId) override;
    virtual void loadPointerResources(PointerResources* outResources, int32_t displayId) override;
    virtual void loadAdditionalMouseResources(std::map<int32_t, SpriteIcon>* outResources,
            std::map<int32_t, PointerAnimation>* outAnimationResources, int32_t displayId) override;
    virtual int32_t getDefaultPointerIconId() override;
    virtual int32_t getCustomPointerIconId() override;

    bool allResourcesAreLoaded();
    bool noResourcesAreLoaded();

private:
    void loadPointerIconForType(SpriteIcon* icon, int32_t cursorType);

    bool pointerIconLoaded{false};
    bool pointerResourcesLoaded{false};
    bool additionalMouseResourcesLoaded{false};
};

void MockCursorControllerPolicyInterface::loadPointerIcon(SpriteIcon* icon, int32_t) {
    loadPointerIconForType(icon, CURSOR_TYPE_DEFAULT);
    pointerIconLoaded = true;
}

void MockCursorControllerPolicyInterface::loadPointerResources(PointerResources* outResources,
        int32_t) {
    loadPointerIconForType(&outResources->spotHover, CURSOR_TYPE_HOVER);
    loadPointerIconForType(&outResources->spotTouch, CURSOR_TYPE_TOUCH);
    loadPointerIconForType(&outResources->spotAnchor, CURSOR_TYPE_ANCHOR);
    pointerResourcesLoaded = true;
}

void MockCursorControllerPolicyInterface::loadAdditionalMouseResources(
        std::map<int32_t, SpriteIcon>* outResources,
        std::map<int32_t, PointerAnimation>* outAnimationResources,
        int32_t) {
    SpriteIcon icon;
    PointerAnimation anim;

    // CURSOR_TYPE_ADDITIONAL doesn't have animation resource.
    int32_t cursorType = CURSOR_TYPE_ADDITIONAL;
    loadPointerIconForType(&icon, cursorType);
    (*outResources)[cursorType] = icon;

    // CURSOR_TYPE_ADDITIONAL_ANIM has animation resource.
    cursorType = CURSOR_TYPE_ADDITIONAL_ANIM;
    loadPointerIconForType(&icon, cursorType);
    anim.animationFrames.push_back(icon);
    anim.durationPerFrame = 10;
    (*outResources)[cursorType] = icon;
    (*outAnimationResources)[cursorType] = anim;

    additionalMouseResourcesLoaded = true;
}

int32_t MockCursorControllerPolicyInterface::getDefaultPointerIconId() {
    return CURSOR_TYPE_DEFAULT;
}

int32_t MockCursorControllerPolicyInterface::getCustomPointerIconId() {
    return CURSOR_TYPE_CUSTOM;
}

bool MockCursorControllerPolicyInterface::allResourcesAreLoaded() {
    return pointerIconLoaded && pointerResourcesLoaded && additionalMouseResourcesLoaded;
}

bool MockCursorControllerPolicyInterface::noResourcesAreLoaded() {
    return !(pointerIconLoaded || pointerResourcesLoaded || additionalMouseResourcesLoaded);
}

void MockCursorControllerPolicyInterface::loadPointerIconForType(SpriteIcon* icon, int32_t type) {
    icon->style = type;
    std::pair<float, float> hotSpot = getHotSpotCoordinatesForType(type);
    icon->hotSpotX = hotSpot.first;
    icon->hotSpotY = hotSpot.second;
}
class CursorControllerTest : public Test {
protected:
    CursorControllerTest();
    ~CursorControllerTest();

    void ensureDisplayViewportIsSet();

    sp<MockSprite> mPointerSprite;
    sp<MockCursorControllerPolicyInterface> mPolicy;
    sp<MockSpriteController> mSpriteController;
    std::shared_ptr<CursorController> mCursorController;

private:
    void loopThread();

    std::atomic<bool> mRunning = true;
    class MyLooper : public Looper {
    public:
        MyLooper() : Looper(false) {}
        ~MyLooper() = default;
    };
    sp<MyLooper> mLooper;
    std::thread mThread;
};

CursorControllerTest::CursorControllerTest() : mPointerSprite(new NiceMock<MockSprite>),
        mLooper(new MyLooper), mThread(&CursorControllerTest::loopThread, this) {

    mSpriteController = new NiceMock<MockSpriteController>(mLooper);
    mPolicy = new MockCursorControllerPolicyInterface();

    EXPECT_CALL(*mSpriteController, createSprite())
            .WillOnce(Return(mPointerSprite));

    mCursorController = CursorController::create(mPolicy, mLooper, mSpriteController);
}

CursorControllerTest::~CursorControllerTest() {
    mRunning.store(false, std::memory_order_relaxed);
    mThread.join();
}

void CursorControllerTest::ensureDisplayViewportIsSet() {
    DisplayViewport viewport;
    viewport.displayId = ADISPLAY_ID_DEFAULT;
    viewport.logicalRight = 1600;
    viewport.logicalBottom = 1200;
    viewport.physicalRight = 800;
    viewport.physicalBottom = 600;
    viewport.deviceWidth = 400;
    viewport.deviceHeight = 300;
    mCursorController->setDisplayViewport(viewport);

    // The first call to setDisplayViewport should trigger the loading of the necessary resources.
    EXPECT_TRUE(mPolicy->allResourcesAreLoaded());
}

void CursorControllerTest::loopThread() {
    Looper::setForThread(mLooper);

    while (mRunning.load(std::memory_order_relaxed)) {
        mLooper->pollOnce(100);
    }
}

TEST_F(CursorControllerTest, useDefaultCursorTypeByDefault) {
    ensureDisplayViewportIsSet();
    mCursorController->unfade(CursorController::Transition::IMMEDIATE);

    std::pair<float, float> hotspot = getHotSpotCoordinatesForType(CURSOR_TYPE_DEFAULT);
    EXPECT_CALL(*mPointerSprite, setVisible(true));
    EXPECT_CALL(*mPointerSprite, setAlpha(1.0f));
    EXPECT_CALL(*mPointerSprite, setIcon(
            AllOf(
                    Field(&SpriteIcon::style, CURSOR_TYPE_DEFAULT),
                    Field(&SpriteIcon::hotSpotX, hotspot.first),
                    Field(&SpriteIcon::hotSpotY, hotspot.second))));
    mCursorController->reloadPointerResources();
}

TEST_F(CursorControllerTest, updatePointerIcon) {
    ensureDisplayViewportIsSet();
    mCursorController->unfade(CursorController::Transition::IMMEDIATE);

    int32_t type = CURSOR_TYPE_ADDITIONAL;
    std::pair<float, float> hotspot = getHotSpotCoordinatesForType(type);
    EXPECT_CALL(*mPointerSprite, setVisible(true));
    EXPECT_CALL(*mPointerSprite, setAlpha(1.0f));
    EXPECT_CALL(*mPointerSprite, setIcon(
            AllOf(
                    Field(&SpriteIcon::style, type),
                    Field(&SpriteIcon::hotSpotX, hotspot.first),
                    Field(&SpriteIcon::hotSpotY, hotspot.second))));
    mCursorController->updatePointerIcon(type);
}

TEST_F(CursorControllerTest, setCustomPointerIcon) {
    ensureDisplayViewportIsSet();
    mCursorController->unfade(CursorController::Transition::IMMEDIATE);

    int32_t style = CURSOR_TYPE_CUSTOM;
    float hotSpotX = 15;
    float hotSpotY = 20;

    SpriteIcon icon;
    icon.style = style;
    icon.hotSpotX = hotSpotX;
    icon.hotSpotY = hotSpotY;

    EXPECT_CALL(*mPointerSprite, setVisible(true));
    EXPECT_CALL(*mPointerSprite, setAlpha(1.0f));
    EXPECT_CALL(*mPointerSprite, setIcon(
            AllOf(
                    Field(&SpriteIcon::style, style),
                    Field(&SpriteIcon::hotSpotX, hotSpotX),
                    Field(&SpriteIcon::hotSpotY, hotSpotY))));
    mCursorController->setCustomPointerIcon(icon);
}

TEST_F(CursorControllerTest, doesNotGetResourcesBeforeSettingViewport) {
    mCursorController->setPresentation(CursorController::Presentation::POINTER);
    mCursorController->setSpots(nullptr, nullptr, BitSet32(), -1);
    mCursorController->clearSpots();
    mCursorController->setPosition(1.0f, 1.0f);
    mCursorController->move(1.0f, 1.0f);
    mCursorController->unfade(CursorController::Transition::IMMEDIATE);
    mCursorController->fade(CursorController::Transition::IMMEDIATE);

    EXPECT_TRUE(mPolicy->noResourcesAreLoaded());

    ensureDisplayViewportIsSet();
}

}  // namespace android
