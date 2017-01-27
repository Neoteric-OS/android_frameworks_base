/*
 * Copyright (C) 2016 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "android_os_vintf_VendorManifest"
#include <android-base/logging.h>

#include "android_os_vintf_VendorManifest.h"

#include <JNIHelp.h>
#include <nativehelper/ScopedLocalRef.h>

#include "core_jni_helpers.h"

using android::AndroidRuntime;

#define PACKAGE_PATH    "android/os/vintf"
#define CLASS_NAME      "VendorManifest"
#define CLASS_PATH      PACKAGE_PATH "/" CLASS_NAME

namespace android {

static jclass gArrayListClass;
static struct {
    jmethodID constructWithCapacity;
    jmethodID add;
} gArrayListMethods;

static jclass gVersionClass;
static struct {
    jmethodID construct;
} gVersionMethods;

static struct fields_t {
    jfieldID contextID;
} gFields;

// static
void JVendorManifest::InitClass(JNIEnv *env) {
    ScopedLocalRef<jclass> clazz(
            env, FindClassOrDie(env, CLASS_PATH));

    gFields.contextID =
        GetFieldIDOrDie(env, clazz.get(), "mNativeContext", "J");
}

// static
sp<JVendorManifest> JVendorManifest::SetNativeContext(
        JNIEnv *env, jobject thiz, const sp<JVendorManifest> &context) {
    sp<JVendorManifest> old =
        (JVendorManifest *)env->GetLongField(thiz, gFields.contextID);

    if (context != NULL) {
        context->incStrong(NULL /* id */);
    }

    if (old != NULL) {
        old->decStrong(NULL /* id */);
    }

    env->SetLongField(thiz, gFields.contextID, (long)context.get());

    return old;
}

// static
sp<JVendorManifest> JVendorManifest::GetNativeContext(
        JNIEnv *env, jobject thiz) {
    return (JVendorManifest *)env->GetLongField(thiz, gFields.contextID);
}

JVendorManifest::JVendorManifest(JNIEnv *env, jobject thiz) {
    jclass clazz = env->GetObjectClass(thiz);
    CHECK(clazz != NULL);

    mClass = (jclass)env->NewGlobalRef(clazz);
    mObject = env->NewWeakGlobalRef(thiz);
}

JVendorManifest::~JVendorManifest() {
    JNIEnv *env = AndroidRuntime::getJNIEnv();

    env->DeleteWeakGlobalRef(mObject);
    mObject = NULL;

    env->DeleteGlobalRef(mClass);
    mClass = NULL;
}

void JVendorManifest::setNativeInstance(const VendorManifest *vm) {
    CHECK(vm != nullptr) << "Cannot setNativeInstance as nullptr";
    mNativeInstance = vm;
}

// static
const VendorManifest *JVendorManifest::GetNativeInstance(
        JNIEnv *env, jobject thiz) {
    sp<JVendorManifest> vm = GetNativeContext(env, thiz);
    CHECK(vm->mNativeInstance != nullptr) << "native instance is not set yet";
    return vm->mNativeInstance;
}

}  // namespace android

////////////////////////////////////////////////////////////////////////////////

using namespace android;

static void releaseNativeContext(void *nativeContext) {
    sp<JVendorManifest> vm = (JVendorManifest *)nativeContext;

    if (vm != NULL) {
        vm->decStrong(NULL /* id */);
    }
}

static jlong JVendorManifest_native_init(JNIEnv *env) {
    JVendorManifest::InitClass(env);

    return reinterpret_cast<jlong>(&releaseNativeContext);
}

static void JVendorManifest_native_setup(JNIEnv *env, jobject thiz) {
    sp<JVendorManifest> context = new JVendorManifest(env, thiz);

    JVendorManifest::SetNativeContext(env, thiz, context);
}


static jlong JVendorManifest_getManifestVersionMajor(JNIEnv *) {
    return static_cast<jlong>(VendorManifest::kVersion.majorVer);
}

static jlong JVendorManifest_getManifestVersionMinor(JNIEnv *) {
    return static_cast<jlong>(VendorManifest::kVersion.minorVer);
}

static jboolean JVendorManifest_associateGlobalNativeInstance(JNIEnv *env, jobject thiz) {
    const VendorManifest *vm = VendorManifest::Get();
    if (vm == nullptr) {
        return false;
    }
    JVendorManifest::GetNativeContext(env, thiz)->setNativeInstance(vm);
    return true;
}

static inline std::string toStdString(JNIEnv *env, jstring name) {
    const char *nativeName = env->GetStringUTFChars(name, nullptr);
    std::string nativeNameStr{nativeName};
    env->ReleaseStringUTFChars(name, nativeName);
    return nativeNameStr;
}

static jlong JVendorManifest_getTransportNative(JNIEnv *env, jobject thiz, jstring name) {
    if (name == NULL) {
        jniThrowException(env, "java/lang/NullPointerException", NULL);
        return 0;
    }
    std::string nativeNameStr = toStdString(env, name);

    const VendorManifest *vm = JVendorManifest::GetNativeInstance(env, thiz);
    return static_cast<jlong>(vm->getTransport(nativeNameStr));
}

static jobject JVendorManifest_getSupportedVersions(JNIEnv *env, jobject thiz, jstring name) {
    using Version = ::android::vintf::Version;
    if (name == NULL) {
        jniThrowException(env, "java/lang/NullPointerException", NULL);
        return NULL;
    }
    std::string nativeNameStr = toStdString(env, name);

    const VendorManifest *vm = JVendorManifest::GetNativeInstance(env, thiz);

    const std::vector<Version> versions = vm->getSupportedVersions(nativeNameStr);
    jint size = static_cast<jint>(versions.size());
    if (size < 0 || static_cast<size_t>(size) != versions.size()) {
        jniThrowException(env, "java/lang/OutOfMemoryError", NULL);
    }
    jobject list = env->NewObject(gArrayListClass, gArrayListMethods.constructWithCapacity, size);
    for (const Version &nativeVersion : versions) {
        jobject version = env->NewObject(gVersionClass, gVersionMethods.construct,
                nativeVersion.majorVer, nativeVersion.minorVer);
        env->CallBooleanMethod(list, gArrayListMethods.add, version);
    }
    return list;
}

static JNINativeMethod gMethods[] = {
    { "native_init", "()J", (void *)JVendorManifest_native_init },
    { "native_setup", "()V", (void *)JVendorManifest_native_setup },
    { "getManifestVersionMajor", "()J", (void *)JVendorManifest_getManifestVersionMajor },
    { "getManifestVersionMinor", "()J", (void *)JVendorManifest_getManifestVersionMinor },
    { "associateGlobalNativeInstance", "()Z",
            (void *)JVendorManifest_associateGlobalNativeInstance },
    { "getTransportNative", "(Ljava/lang/String;)J",
            (void *)JVendorManifest_getTransportNative },
    { "getSupportedVersions", "(Ljava/lang/String;)Ljava/lang/Object;",
            (void *)JVendorManifest_getSupportedVersions },
};

namespace android {

int register_android_os_vintf_VendorManifest(JNIEnv *env) {
    jclass arrayListClass = FindClassOrDie(env, "java/util/ArrayList");
    gArrayListClass = MakeGlobalRefOrDie(env, arrayListClass);
    gArrayListMethods.constructWithCapacity =
            GetMethodIDOrDie(env, arrayListClass, "<init>", "(I)V");
    gArrayListMethods.add = GetMethodIDOrDie(env, arrayListClass, "add", "(Ljava/lang/Object;)Z");

    jclass versionClass = FindClassOrDie(env, PACKAGE_PATH "/Version");
    gVersionClass = MakeGlobalRefOrDie(env, versionClass);
    gVersionMethods.construct =
            GetMethodIDOrDie(env, gVersionClass, "<init>", "(JJ)V");

    return RegisterMethodsOrDie(env, CLASS_PATH, gMethods, NELEM(gMethods));
}

}  // namespace android
