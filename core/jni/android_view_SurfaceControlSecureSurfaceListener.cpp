/*
 * Copyright 2024 The Android Open Source Project
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
#undef ANDROID_UTILS_REF_BASE_DISABLE_IMPLICIT_CONSTRUCTION

#define LOG_TAG "SurfaceControlSecureSurfaceListener"

#include <android/gui/BnSecureSurfaceListener.h>
#include <android_runtime/Log.h>
#include <gui/ISurfaceComposer.h>
#include <gui/SurfaceComposerClient.h>
#include <log/log.h>
#include <nativehelper/JNIHelp.h>
#include <utils/RefBase.h>

#include "android_util_Binder.h"
#include "core_jni_helpers.h"

namespace android {

namespace {

struct {
    jclass mClass;
    jmethodID mOnSecureSurfaceChanged;
} gListenerClassInfo;

struct SurfaceControlSecureSurfaceListener : public gui::BnSecureSurfaceListener {
    SurfaceControlSecureSurfaceListener(JNIEnv* env, jobject listener, jobject displayToken)
          : mListener(env->NewGlobalRef(listener)), mDisplayToken(env->NewGlobalRef(displayToken)) {
        LOG_ALWAYS_FATAL_IF(env->GetJavaVM(&mVm) != JNI_OK, "Failed to GetJavaVm");
    }

    binder::Status onSecureSurfaceChanged(bool hasSecureSurface) override {
        ALOGD("onSecureSurfaceChanged: Callback invoked by native, hasSecureSurface=%d, displayToken=%p", hasSecureSurface, mDisplayToken);
        JNIEnv* env = requireEnv();

        env->CallVoidMethod(mListener, gListenerClassInfo.mOnSecureSurfaceChanged, mDisplayToken,
                            hasSecureSurface ? JNI_TRUE : JNI_FALSE);

        if (env->ExceptionCheck()) {
            ALOGE("SurfaceControlSecureSurfaceListener.onSecureSurfaceChanged() failed.");
            LOGE_EX(env);
            env->ExceptionClear();
        }
        return binder::Status::ok();
    }

    status_t startListening() {
        auto token = ibinderForJavaObject(requireEnv(), mDisplayToken);
        return SurfaceComposerClient::addSecureSurfaceListener(token, this);
    }

    status_t stopListening() {
        auto token = ibinderForJavaObject(requireEnv(), mDisplayToken);
        return SurfaceComposerClient::removeSecureSurfaceListener(token, this);
    }

protected:
    virtual ~SurfaceControlSecureSurfaceListener() {
        JNIEnv* env = requireEnv();
        env->DeleteGlobalRef(mListener);
        env->DeleteGlobalRef(mDisplayToken);
    }

    JNIEnv* requireEnv() {
        JNIEnv* env = nullptr;
        if (mVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
            if (mVm->AttachCurrentThreadAsDaemon(&env, nullptr) != JNI_OK) {
                LOG_ALWAYS_FATAL("Failed to AttachCurrentThread!");
            }
        }
        return env;
    }

private:
    jobject mListener;
    jobject mDisplayToken;
    JavaVM* mVm;
};

jlong nRegister(JNIEnv* env, jobject jthis, jobject jbinderToken) {
    auto callback = sp<SurfaceControlSecureSurfaceListener>::make(env, jthis, jbinderToken);
    status_t err = callback->startListening();
    if (err != OK) {
        auto errStr = statusToString(err);
        ALOGW("Failed to register SecureSurfaceListener, err = %d (%s)", err, errStr.c_str());
        return 0;
    }
    SurfaceControlSecureSurfaceListener* ret = callback.get();
    ret->incStrong(0);
    return static_cast<jlong>(reinterpret_cast<intptr_t>(ret));
}

static void destroy(SurfaceControlSecureSurfaceListener* listener) {
    listener->stopListening();
    listener->decStrong(0);
}

static jlong nGetDestructor(JNIEnv* env, jobject clazz) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(&destroy));
}

const JNINativeMethod gMethods[] = {
        /* name, signature, funcPtr */
        {"nGetDestructor", "()J", (void*)nGetDestructor},
        {"nRegister", "(Landroid/os/IBinder;)J", (void*)nRegister}};

} // namespace

int register_android_view_SurfaceControlSecureSurfaceListener(JNIEnv* env) {
    int res = jniRegisterNativeMethods(env, "android/view/SurfaceControlSecureSurfaceListener",
                                       gMethods, NELEM(gMethods));
    LOG_ALWAYS_FATAL_IF(res < 0, "Unable to register native methods.");

    jclass clazz = env->FindClass("android/view/SurfaceControlSecureSurfaceListener");
    gListenerClassInfo.mClass = MakeGlobalRefOrDie(env, clazz);
    gListenerClassInfo.mOnSecureSurfaceChanged =
            env->GetMethodID(clazz, "onSecureSurfaceChanged", "(Landroid/os/IBinder;Z)V");
    return 0;
}

} // namespace android
