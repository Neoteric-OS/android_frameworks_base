/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef _ANDROID_OS_VINTF_VENDOR_MANIFEST_H
#define _ANDROID_OS_VINTF_VENDOR_MANIFEST_H

#include <android-base/macros.h>
#include <hwbinder/Binder.h>
#include <jni.h>
#include <vintf/VendorManifest.h>
#include <utils/RefBase.h>

namespace android {

using VendorManifest = ::android::vintf::VendorManifest;

struct JVendorManifest : public RefBase {
    static void InitClass(JNIEnv *env);

    static sp<JVendorManifest> SetNativeContext(
            JNIEnv *env, jobject thiz, const sp<JVendorManifest> &context);

    static sp<JVendorManifest> GetNativeContext(JNIEnv *env, jobject thiz);

    static const VendorManifest *GetNativeInstance(JNIEnv *env, jobject thiz);

    JVendorManifest(JNIEnv *env, jobject thiz);

    void setNativeInstance(const VendorManifest *vm);

protected:
    virtual ~JVendorManifest();

private:
    jclass mClass;
    jobject mObject;

    const VendorManifest *mNativeInstance = nullptr;

    DISALLOW_COPY_AND_ASSIGN(JVendorManifest);
};

int register_android_os_vintf_VendorManifest(JNIEnv *env);

}  // namespace android

#endif  // _ANDROID_OS_VINTF_VENDOR_MANIFEST_H


