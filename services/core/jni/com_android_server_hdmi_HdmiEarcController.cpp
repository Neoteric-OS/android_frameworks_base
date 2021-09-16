/*
 * Copyright (C) 2014 The Android Open Source Project
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

#define LOG_TAG "HdmiEarcControllerJni"

#define LOG_NDEBUG 0

#include <binder/IServiceManager.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedPrimitiveArray.h>

#include <aidl/vendor/hardware/tv/earc/IHdmiEarc.h>
#include <aidl/vendor/hardware/tv/earc/BnHdmiEarcCallback.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android_os_MessageQueue.h>
#include <android_runtime/AndroidRuntime.h>
#include <android_runtime/Log.h>
#include <sys/param.h>
#include <utils/Errors.h>
#include <utils/Looper.h>
#include <utils/RefBase.h>
using aidl::vendor::hardware::tv::earc::EarcCapability;
using aidl::vendor::hardware::tv::earc::EarcControl;
using aidl::vendor::hardware::tv::earc::EarcEvent;
using aidl::vendor::hardware::tv::earc::EarcEventType;
using aidl::vendor::hardware::tv::earc::EarcStatus;
using aidl::vendor::hardware::tv::earc::IHdmiEarc;
using aidl::vendor::hardware::tv::earc::BnHdmiEarcCallback;
using aidl::vendor::hardware::tv::earc::Result;

namespace android {

static struct {
    jmethodID handleEarcStatusChanged;
    jmethodID handleEarcAudioLatencyChanged;
    jmethodID handleEarcCapsChanged;
} gHdmiEarcControllerClassInfo;

class HdmiEarcController {
public:
    HdmiEarcController(auto hdmiEarc, jobject callbacksObj, const sp<Looper>& looper);
    ~HdmiEarcController();

    static void onServiceDied(void* cookie);
    void Reconnect_callback();
    bool isSupported();
    int getPortId();
    int getStatus(int port);
    jbyteArray getCapability(int port);
    uint32_t getLatency(int port);
    int controlAudioLatency(int latency);
    int controlFeature(EarcControl control);

    jobject getCallbacksObj() const {
        return mCallbacksObj;
    }

private:
    class HdmiEarcCallback : public BnHdmiEarcCallback {
    public:
        explicit HdmiEarcCallback(HdmiEarcController* controller) : mEarcController(controller) {
            if (!mEarcController) {
                ALOGE("mEarcController is null");
            }
        };

        ::ndk::ScopedAStatus notify(const EarcEvent &mEarcEvent) override;

        void onStatusChanged(const EarcEvent& event);
        void onAudioLatencyChanged(const EarcEvent& event);
        void onCapsChanged(const EarcEvent& event);
    private:
        HdmiEarcController* mEarcController;
    };

    static const int INVALID_PORT_ID = 0xF;
    static const uint32_t INVALID_LATENCY = 0xFFFFFFFF;
    mutable std::mutex mLock;

    std::shared_ptr<IHdmiEarc> mHdmiEarc;
    jobject mCallbacksObj;
    std::shared_ptr<HdmiEarcCallback> mHdmiEarcCallback GUARDED_BY(mLock);
    sp<Looper> mLooper;
    ndk::ScopedAIBinder_DeathRecipient mDeathRecipient;
};

// Handler class to delegate incoming message to service thread.
class HdmiEarcEventHandler : public MessageHandler {
public:
    enum EventType {
        EARC_STATUS_CHG,
        EARC_AUDIO_LATENCY_CHG,
        EARC_CAPS_CHG
    };

    HdmiEarcEventHandler(HdmiEarcController* controller, const EarcEvent& event)
            : mEarcController(controller),
              mEarcEvent(event) {
                 if (!mEarcController) {
                     ALOGE("mEarcController is null");
                 }
                 ALOGI("mEarcEvent:%d", mEarcEvent.type);
              }

    virtual ~HdmiEarcEventHandler() {}

    void handleMessage(const Message& message) {
        switch (mEarcEvent.type) {
        case ::aidl::vendor::hardware::tv::earc::EarcEventType::STATUS_CHG:
            propagateEarcStatusChanged(mEarcEvent);
            break;
        case ::aidl::vendor::hardware::tv::earc::EarcEventType::LATENCY_CHG:
            propagateEarcAudioLatencyChanged(mEarcEvent);
            break;
        case ::aidl::vendor::hardware::tv::earc::EarcEventType::CAPABILITY_CHG:
            propagateEarcCapsChanged(mEarcEvent);
            break;
        default:
            // TODO: add more type whenever new type is introduced.
            break;
        }

    }

private:
    HdmiEarcController* mEarcController;
    EarcEvent mEarcEvent;
    void propagateEarcStatusChanged(const EarcEvent& event) {
        // Note that this method should be called in service thread.
        JNIEnv* env = AndroidRuntime::getJNIEnv();
        jint earcStatus = static_cast<jint>(event.status);
        env->CallVoidMethod(mEarcController->getCallbacksObj(),
                gHdmiEarcControllerClassInfo.handleEarcStatusChanged, earcStatus);

        checkAndClearExceptionFromCallback(env, __FUNCTION__);
    }

    void propagateEarcAudioLatencyChanged(const EarcEvent& event) {
        // Note that this method should be called in service thread.
        JNIEnv* env = AndroidRuntime::getJNIEnv();
        jint flag = static_cast<jint>(event.status);
        env->CallVoidMethod(mEarcController->getCallbacksObj(),
                gHdmiEarcControllerClassInfo.handleEarcAudioLatencyChanged, flag);

        checkAndClearExceptionFromCallback(env, __FUNCTION__);
    }

    void propagateEarcCapsChanged(const EarcEvent& event) {
        // Note that this method should be called in service thread.
        JNIEnv* env = AndroidRuntime::getJNIEnv();
        jint flag = static_cast<jint>(event.status);
        env->CallVoidMethod(mEarcController->getCallbacksObj(),
                gHdmiEarcControllerClassInfo.handleEarcCapsChanged, flag);

        checkAndClearExceptionFromCallback(env, __FUNCTION__);
    }

    // static
    static void checkAndClearExceptionFromCallback(JNIEnv* env, const char* methodName) {
        if (env->ExceptionCheck()) {
            ALOGE("An exception was thrown by callback '%s'.", methodName);
            LOGE_EX(env);
            env->ExceptionClear();
        }
    }
};

HdmiEarcController::HdmiEarcController(auto hdmiEarc,
        jobject callbacksObj, const sp<Looper>& looper)
        : mHdmiEarc(hdmiEarc),
          mCallbacksObj(callbacksObj),
          mLooper(looper) {
    {
        std::lock_guard<std::mutex> lock(mLock);
        auto deathRecipient = ::AIBinder_DeathRecipient_new(HdmiEarcController::onServiceDied);
        auto status = ndk::ScopedAStatus::fromStatus(AIBinder_linkToDeath(mHdmiEarc->asBinder().get(), deathRecipient, this));
        mDeathRecipient = ndk::ScopedAIBinder_DeathRecipient(deathRecipient);
    }
    
    mHdmiEarcCallback = ndk::SharedRefBase::make<HdmiEarcCallback>(this);
    Result result = Result::UNKNOWN;
    auto ret = mHdmiEarc->setCallback(mHdmiEarcCallback, &result);
    if (!ret.isOk() || result != Result::OK) {
        ALOGE("Failed to set a eARC callback.");
    }
}

::ndk::ScopedAStatus HdmiEarcController::HdmiEarcCallback::notify(const EarcEvent &mEarcEvent) {
    ALOGI("EarcEvent: %d", mEarcEvent.type);
    sp<HdmiEarcEventHandler> handler(new HdmiEarcEventHandler(mEarcController, mEarcEvent));
    mEarcController->mLooper->sendMessage(handler, static_cast<int>(mEarcEvent.type));
    return ::ndk::ScopedAStatus::ok();
}

HdmiEarcController::~HdmiEarcController() {
    std::lock_guard<std::mutex> lock(mLock);
    ::AIBinder_DeathRecipient_delete(mDeathRecipient.get());
    Result result = Result::UNKNOWN;
    auto ret = mHdmiEarc->setCallback(nullptr, &result);
    if (!ret.isOk() || result != Result::OK) {
        ALOGE("Failed to set a eARC callback.");
    }
}

void HdmiEarcController::onServiceDied(void* cookie){
    ALOGE("Earc aidl service died, try reconnecting the service...");
    auto* manager = static_cast<HdmiEarcController*>(cookie);

    const auto instance = std::string() + IHdmiEarc::descriptor + "/default";
    auto gIHdmiEarcHalAidl_ = IHdmiEarc::fromBinder(ndk::SpAIBinder(AServiceManager_getService(instance.c_str())));

    if (gIHdmiEarcHalAidl_) {
        ALOGV("Successfully connected to HDMI EARC HAL AIDL service.");
    } else {
        ALOGE("Couldn't get earc service.");
        return ;
    }
    manager->mHdmiEarc = std::move(gIHdmiEarcHalAidl_);
    ALOGV("Test acquired HAL isSupported: %d", manager->isSupported());

    manager->Reconnect_callback();

    ALOGV("Reset eARC AIDL service deathRecipient...");
    binder_status_t binder_status = AIBinder_linkToDeath(manager->mHdmiEarc->asBinder().get(), manager->mDeathRecipient.get(), manager);
    if (binder_status != STATUS_OK) {
        ALOGE("Failed to linkToDeath ");
        return;
    }
    ALOGV("Finish Re-Connecting eARC service.");
}

void HdmiEarcController::Reconnect_callback(){
    std::lock_guard<std::mutex> lock(mLock);
    ALOGV("Reset eARC AIDL service callback...");
    mHdmiEarcCallback = ndk::SharedRefBase::make<HdmiEarcCallback>(this);
    Result result = Result::UNKNOWN;
    auto ret = mHdmiEarc->setCallback(mHdmiEarcCallback, &result);
    if (!ret.isOk() || result != Result::OK) {
        ALOGE("Failed to set a eARC callback.");
    }
}

bool HdmiEarcController::isSupported() {
    bool supported;
    auto ret = mHdmiEarc->isSupport(&supported);
    if (!ret.isOk()) {
        ALOGE("Failed to check if eARC is supported.");
        return false;
    }
    return supported;
}

int HdmiEarcController::getPortId() {
    int port;
    auto ret = mHdmiEarc->getPortId(&port);
    if (!ret.isOk()) {
        ALOGE("Failed to get eARC supported port.");
        return INVALID_PORT_ID;
    }
    return port;
}

int HdmiEarcController::getStatus(int port) {
    EarcStatus status;
    auto ret = mHdmiEarc->getStatus(port, &status);
    if (!ret.isOk()) {
        ALOGE("Failed to get eARC status.");
        return static_cast<int>(EarcStatus::EARC_IDLE);
    }
    return static_cast<int>((EarcStatus) status);
}

jbyteArray HdmiEarcController::getCapability(int port) {
    EarcCapability mCapability;
    auto ret = mHdmiEarc->getCapability(port, &mCapability);
    if (!ret.isOk()) {
        ALOGE("Failed to get eARC capability.");
        return NULL;
    }

    JNIEnv *env = AndroidRuntime::getJNIEnv();
    size_t length = mCapability.payload.size();
    jbyteArray result = env->NewByteArray(length);
    if (result != NULL) {
        env->SetByteArrayRegion(result, 0, length, reinterpret_cast<const jbyte *>(mCapability.payload.data()));
    }
    return result;
}

uint32_t HdmiEarcController::getLatency(int port) {
    int latency;
    auto ret = mHdmiEarc->getLatency(port, &latency);
    if (!ret.isOk()) {
        ALOGE("Failed to get eARC Audio Latency.");
        return static_cast<uint32_t>(INVALID_LATENCY);
    }
    return static_cast<uint32_t>(latency);
}

int HdmiEarcController::controlAudioLatency(int latency) {
    Result result = Result::UNKNOWN;
    auto ret = mHdmiEarc->controlAudioLatency(latency, &result);

    if (!ret.isOk() || result != Result::OK) {
        ALOGE("Failed to control Audio Latency.");
        return static_cast<int>(Result::UNKNOWN);
    }
    return ret.isOk();
}

int HdmiEarcController::controlFeature(EarcControl control) {
    ALOGE("===EARC controlFeature, control:%d===", control);
    Result result = Result::UNKNOWN;
    auto ret = mHdmiEarc->controlFeature(control, &result);

    if (!ret.isOk() || result != Result::OK) {
        ALOGE("Failed to control EARC feature");
        return static_cast<int>(Result::UNKNOWN);
    }
    return ret.isOk();
}

void HdmiEarcController::HdmiEarcCallback::onStatusChanged(const EarcEvent& event) {
    sp<HdmiEarcEventHandler> handler(new HdmiEarcEventHandler(mEarcController, event));
    mEarcController->mLooper->sendMessage(handler, HdmiEarcEventHandler::EventType::EARC_STATUS_CHG);
}

void HdmiEarcController::HdmiEarcCallback::onAudioLatencyChanged(const EarcEvent& event) {
    sp<HdmiEarcEventHandler> handler(new HdmiEarcEventHandler(mEarcController, event));
    mEarcController->mLooper->sendMessage(handler, HdmiEarcEventHandler::EventType::EARC_AUDIO_LATENCY_CHG);
}

void HdmiEarcController::HdmiEarcCallback::onCapsChanged(const EarcEvent& event) {
    sp<HdmiEarcEventHandler> handler(new HdmiEarcEventHandler(mEarcController, event));
    mEarcController->mLooper->sendMessage(handler, HdmiEarcEventHandler::EventType::EARC_CAPS_CHG);
}
//------------------------------------------------------------------------------
#define GET_METHOD_ID(var, clazz, methodName, methodDescriptor) \
        var = env->GetMethodID(clazz, methodName, methodDescriptor); \
        LOG_FATAL_IF(! (var), "Unable to find method " methodName);

static jlong nativeInit(JNIEnv* env, jclass clazz, jobject callbacksObj,
        jobject messageQueueObj) {
    const auto instance = std::string() + IHdmiEarc::descriptor + "/default";
    auto gIHdmiEarcHalAidl_ = IHdmiEarc::fromBinder(ndk::SpAIBinder(AServiceManager_getService(instance.c_str())));

    if (gIHdmiEarcHalAidl_) {
        ALOGV("Successfully connected to HDMI EARC HAL AIDL service.");
    } else {
        ALOGE("Couldn't get earc service.");
        return 0;
    }
    sp<MessageQueue> messageQueue =
            android_os_MessageQueue_getMessageQueue(env, messageQueueObj);

    HdmiEarcController* controller = new HdmiEarcController(
            gIHdmiEarcHalAidl_,
            env->NewGlobalRef(callbacksObj),
            messageQueue->getLooper());

    GET_METHOD_ID(gHdmiEarcControllerClassInfo.handleEarcStatusChanged, clazz,
            "handleEarcStatusChanged", "(I)V");
    GET_METHOD_ID(gHdmiEarcControllerClassInfo.handleEarcAudioLatencyChanged, clazz,
            "handleEarcAudioLatencyChanged", "(I)V");
    GET_METHOD_ID(gHdmiEarcControllerClassInfo.handleEarcCapsChanged, clazz,
            "handleEarcCapsChanged", "(I)V");

    return reinterpret_cast<jlong>(controller);
}


static jboolean nativeIsSupported(JNIEnv* env, jclass clazz, jlong controllerPtr) {
    HdmiEarcController* controller = reinterpret_cast<HdmiEarcController*>(controllerPtr);
    return controller->isSupported() ? JNI_TRUE : JNI_FALSE ;
}

static jint nativeGetPortId(JNIEnv* env, jclass clazz, jlong controllerPtr) {
    HdmiEarcController* controller = reinterpret_cast<HdmiEarcController*>(controllerPtr);
    return controller->getPortId();
}

static jint nativeGetStatus(JNIEnv* env, jclass clazz, jlong controllerPtr, jint port) {
    HdmiEarcController* controller = reinterpret_cast<HdmiEarcController*>(controllerPtr);
    return controller->getStatus(port);
}

static jbyteArray nativeGetCapability(JNIEnv* env, jclass clazz, jlong controllerPtr, jint port) {
    HdmiEarcController* controller = reinterpret_cast<HdmiEarcController*>(controllerPtr);
    return controller->getCapability(port);
}

static jint nativeGetLatency(JNIEnv* env, jclass clazz, jlong controllerPtr, jint port) {
    HdmiEarcController* controller = reinterpret_cast<HdmiEarcController*>(controllerPtr);
    return controller->getLatency(port);
}

static jint nativeControlAudioLatency(JNIEnv* env, jclass clazz, jlong controllerPtr, jint latency) {
    HdmiEarcController* controller = reinterpret_cast<HdmiEarcController*>(controllerPtr);
    return controller->controlAudioLatency(latency);
}

static jint nativeControlFeature(JNIEnv* env, jclass clazz, jlong controllerPtr, jint control) {
    HdmiEarcController* controller = reinterpret_cast<HdmiEarcController*>(controllerPtr);
    return controller->controlFeature(static_cast<EarcControl> (control));
}

static const JNINativeMethod sMethods[] = {
    /* name, signature, funcPtr */
    { "nativeInit",
      "(Lcom/android/server/hdmi/HdmiEarcController;Landroid/os/MessageQueue;)J",
      (void *) nativeInit },
    { "nativeIsSupported", "(J)Z", (void *) nativeIsSupported },
    { "nativeGetPortId", "(J)I", (void *) nativeGetPortId },
    { "nativeGetStatus", "(JI)I", (void *) nativeGetStatus },
    { "nativeGetCapability", "(JI)[B", (void *) nativeGetCapability },
    { "nativeGetLatency", "(JI)I", (void *) nativeGetLatency },
    { "nativeControlAudioLatency", "(JI)I", (void *) nativeControlAudioLatency },
    { "nativeControlFeature", "(JI)I", (void *) nativeControlFeature },
};

#define CLASS_PATH "com/android/server/hdmi/HdmiEarcController"

int register_android_server_hdmi_HdmiEarcController(JNIEnv* env) {
    int res = jniRegisterNativeMethods(env, CLASS_PATH, sMethods, NELEM(sMethods));
    LOG_FATAL_IF(res < 0, "Unable to register native methods.");
    (void)res; // Don't scream about unused variable in the LOG_NDEBUG case
    return 0;
}

}  /* namespace android */
