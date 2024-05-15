/*
 * Copyright 2012, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "MediaCodec-JNI"

#include "android_media_Streams.h"
#include "android_runtime/AndroidRuntime.h"
#include "jni.h"

#include <utils/Log.h>
#include <media/CodecCapabilities.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <nativehelper/JNIHelp.h>

using namespace android;

// ----------------------------------------------------------------------------

struct fields_t {
    jfieldID context;
};
static fields_t fields;

static Mutex sLock;

// ----------------------------------------------------------------------------

static std::shared_ptr<CodecCapabilities> getCodecCapabilities(JNIEnv *env, jobject thiz) {
    // Mutex::Autolock l(sLock);
    CodecCapabilities* const p = (CodecCapabilities*)env->GetLongField(thiz, fields.context);
    return std::shared_ptr<CodecCapabilities>(p);
}

jobject constructJavaCodecCapabilitiesFromNative(JNIEnv *env, std::shared_ptr<CodecCapabilities> codecCaps) {
    if (codecCaps == nullptr) {
        return NULL;
    }

    // Construct defaultFormat
    sp<AMessage> defaultFormat = codecCaps->getDefaultFormat();

    jobject defaultFormatObj = NULL;
    if (ConvertMessageToMap(env, defaultFormat, &defaultFormatObj)) {
        return NULL;
    }

    // Construct Java ProfileLevelArray
    std::vector<ProfileLevel> profileLevels = codecCaps->getProfileLevels();

    jclass profileLevelClazz =
        env->FindClass("android/media/MediaCodecInfo$CodecProfileLevel");
    CHECK(profileLevelClazz != NULL);

    jobjectArray profileLevelArray =
            env->NewObjectArray(profileLevels.size(), profileLevelClazz, NULL);

    jfieldID profileField =
            env->GetFieldID(profileLevelClazz, "profile", "I");
    jfieldID levelField =
            env->GetFieldID(profileLevelClazz, "level", "I");

    for (size_t i = 0; i < profileLevels.size(); ++i) {
        const ProfileLevel &src = profileLevels.at(i);

        jobject profileLevelObj = env->AllocObject(profileLevelClazz);

        env->SetIntField(profileLevelObj, profileField, src.mProfile);
        env->SetIntField(profileLevelObj, levelField, src.mLevel);

        env->SetObjectArrayElement(profileLevelArray, i, profileLevelObj);

        env->DeleteLocalRef(profileLevelObj);
        profileLevelObj = NULL;
    }

    // Construct ColorFormatArray
    std::vector<uint32_t> colorFormats = codecCaps->getColorFormats();

    jintArray colorFormatsArray = env->NewIntArray(colorFormats.size());
    for (size_t i = 0; i < colorFormats.size(); ++i) {
        jint val = colorFormats.at(i);
        env->SetIntArrayRegion(colorFormatsArray, i, 1, &val);
    }

    // Set AudioCapabilities
    jobject jAudioCaps = NULL;
    if (codecCaps->isAudio()) {
        std::shared_ptr<AudioCapabilities> audioCaps = codecCaps->getAudioCapabilities();

        jclass audioCapsClazz =
            env->FindClass("android/media/MediaCodecInfo$AudioCapabilities");
        CHECK(audioCapsClazz != NULL);
        jmethodID audioCapsConstructID = env->GetMethodID(audioCapsClazz, "<init>", "()V");
        jAudioCaps = env->NewObject(audioCapsClazz, audioCapsConstructID);

        env->SetLongField(jAudioCaps, fields.context, (jlong)audioCaps.get());
    }

    // Set VideoCapabilities
    jobject jVideoCaps = NULL;
    if (codecCaps->isVideo()) {
        std::shared_ptr<VideoCapabilities> videoCaps = codecCaps->getVideoCapabilities();
        
        jclass videoCapsClazz =
            env->FindClass("android/media/MediaCodecInfo$VideoCapabilities");
        CHECK(videoCapsClazz != NULL);
        jmethodID videoCapsConstructID = env->GetMethodID(videoCapsClazz, "<init>", "()V");
        jVideoCaps = env->NewObject(videoCapsClazz, videoCapsConstructID);

        env->SetLongField(jVideoCaps, fields.context, (jlong)videoCaps.get());
    }

    // Set EncoderCapabilities
    jobject jEncoderCaps = NULL;
    if (codecCaps->isEncoder()) {
        std::shared_ptr<EncoderCapabilities> encoderCaps = codecCaps->getEncoderCapabilities();

        jclass encoderCapsClazz =
            env->FindClass("android/media/MediaCodecInfo$EncoderCapabilities");
        CHECK(encoderCapsClazz != NULL);
        jmethodID encoderCapsConstructID = env->GetMethodID(encoderCapsClazz, "<init>", "()V");
        jEncoderCaps = env->NewObject(encoderCapsClazz, encoderCapsConstructID);

        env->SetLongField(jEncoderCaps, fields.context, (jlong)encoderCaps.get());
    }

    // Construct CodecCapabilities
    jclass capsClazz =
        env->FindClass("android/media/MediaCodecInfo$CodecCapabilities");
    CHECK(capsClazz != NULL);

    jmethodID capsConstructID = env->GetMethodID(capsClazz, "<init>",
                "([Landroid/media/MediaCodecInfo$CodecProfileLevel;[I"
                "Ljava/util/Map;Landroid/media/MediaCodecInfo$AudioCapabilities"
                "Landroid/media/MediaCodecInfo$VideoCapabilities"
                "Landroid/media/MediaCodecInfo$EncoderCapabilities;)V");

    jobject jCodecCaps = env->NewObject(capsClazz, capsConstructID,
            profileLevelArray, colorFormatsArray, defaultFormatObj,
            jAudioCaps, jVideoCaps, jEncoderCaps);


    env->DeleteLocalRef(defaultFormatObj);
    defaultFormatObj = NULL;

    env->DeleteLocalRef(profileLevelArray);
    profileLevelArray = NULL;

    env->DeleteLocalRef(colorFormatsArray);
    colorFormatsArray = NULL;

    env->DeleteLocalRef(jAudioCaps);
    jAudioCaps = NULL;

    env->DeleteLocalRef(jVideoCaps);
    jVideoCaps = NULL;

    env->DeleteLocalRef(jEncoderCaps);
    jEncoderCaps = NULL;


	env->SetLongField(jCodecCaps, fields.context, (jlong)codecCaps.get());

    return jCodecCaps;
}

// static void setCodecCapabilities(JNIEnv *env, jobject thiz,
//         const std::shared_ptr<CodecCapabilities>& codecCaps) {
//     env->SetLongField(thiz, fields.context, (jlong)codecCaps.get());
// }

// static std::shared_ptr<AudioCapabilities> getAudioCapabilities(JNIEnv *env, jobject thiz) {
//     AudioCapabilities* const p = (AudioCapabilities*)env->GetLongField(thiz, fields.context);
//     return std::shared_ptr<AudioCapabilities>(p);
// }

// ----------------------------------------------------------------------------

static jint android_media_CodecCapabilities_getMaxSupportedInstances(JNIEnv *env, jobject thiz) {
    std::shared_ptr<CodecCapabilities> codecCaps = getCodecCapabilities(env, thiz);
    if (codecCaps == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return 0;
    }

    int maxSupportedInstances = codecCaps->getMaxSupportedInstances();
    return maxSupportedInstances;
}

static jstring android_media_CodecCapabilities_getMimeType(JNIEnv *env, jobject thiz) {
    std::shared_ptr<CodecCapabilities> codecCaps = getCodecCapabilities(env, thiz);
    if (codecCaps == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return NULL;
    }

    const char *mediaType = codecCaps->getMediaType().c_str();
    return env->NewStringUTF(mediaType);
}

static jboolean android_media_CodecCapabilities_isFeatureRequired(JNIEnv *env, jobject thiz, jstring name) {
    std::shared_ptr<CodecCapabilities> codecCaps = getCodecCapabilities(env, thiz);
    if (codecCaps == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return false;
    }
    
    if (name == NULL) {
        jniThrowException(env, "java/lang/IllegalArgumentException", NULL);
        return -ENOENT;
    }

    const char *nameStr = env->GetStringUTFChars(name, NULL);
    if (nameStr == NULL) {
        // Out of memory exception already pending.
        return -ENOENT;
    }

    bool isFeatureRequired = codecCaps->isFeatureRequired(std::string(nameStr));

    env->ReleaseStringUTFChars(name, nameStr);

    return isFeatureRequired;
}

static jboolean android_media_CodecCapabilities_isFeatureSupported(JNIEnv *env, jobject thiz, jstring name) {
    std::shared_ptr<CodecCapabilities> codecCaps = getCodecCapabilities(env, thiz);
    if (codecCaps == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return false;
    }
    
    if (name == NULL) {
        jniThrowException(env, "java/lang/IllegalArgumentException", NULL);
        return -ENOENT;
    }

    const char *nameStr = env->GetStringUTFChars(name, NULL);
    if (nameStr == NULL) {
        // Out of memory exception already pending.
        return -ENOENT;
    }

    bool isFeatureSupported = codecCaps->isFeatureSupported(std::string(nameStr));

    env->ReleaseStringUTFChars(name, nameStr);

    return isFeatureSupported;
}

static jboolean android_media_CodecCapabilities_isFormatSupported(JNIEnv *env, jobject thiz,
        jobjectArray keys, jobjectArray values) {
    std::shared_ptr<CodecCapabilities> codecCaps = getCodecCapabilities(env, thiz);
    if (codecCaps == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return false;
    }

    sp<AMessage> format;
    status_t err = ConvertKeyValueArraysToMessage(env, keys, values, &format);
    if (err != OK) {
        jniThrowException(env, "java/lang/IllegalArgumentException", NULL);
        return -ENOENT;;
    }

    return codecCaps->isFormatSupported(format);
}

static jobject android_media_CodecCapabilities_CreateFromProfileLevel(JNIEnv *env, jobject /* thiz */,
        jstring mediaType, jint profile, jint level) {
    if (mediaType == NULL) {
        jniThrowException(env, "java/lang/IllegalArgumentException", NULL);
        return NULL;
    }

    const char *mediaTypeStr = env->GetStringUTFChars(mediaType, NULL);
    if (mediaTypeStr == NULL) {
        // Out of memory exception already pending.
        return NULL;
    }

    std::shared_ptr<CodecCapabilities> codecCaps = CodecCapabilities::CreateFromProfileLevel(
            AString(mediaTypeStr), profile, level);

    jobject jCodecCaps = constructJavaCodecCapabilitiesFromNative(env, codecCaps);

    return jCodecCaps;
}

// static jobject android_media_CodecCapabilities_getAudioCapabilities(JNIEnv *env, jobject thiz) {
//     std::shared_ptr<CodecCapabilities> codecCaps = getCodecCapabilities(env, thiz);
//     if (codecCaps == NULL) {
//         jniThrowException(env, "java/lang/IllegalStateException", NULL);
//         return NULL;
//     }
//     std::shared_ptr<AudioCapabilities> audioCaps = codecCaps->getAudioCapabilities();
//     return constructJavaAudioCapabilities(env, audioCaps);
// }

// ----------------------------------------------------------------------------

static const JNINativeMethod gAudioCapsMethods[] = {
    {},
};

static const JNINativeMethod gCodecCapsMethods[] = {
    { "native_getMaxSupportedInstances", "()I", (void *)android_media_CodecCapabilities_getMaxSupportedInstances },
    { "native_getMimeType", "()Ljava/lang/String", (void *)android_media_CodecCapabilities_getMimeType },
    { "native_isFeatureRequired", "(Ljava/lang/String)Z", (void *)android_media_CodecCapabilities_isFeatureRequired },
    { "native_isFeatureSupported", "(Ljava/lang/String)Z", (void *)android_media_CodecCapabilities_isFeatureSupported },
    { "native_isFormatSupported", "([Ljava/lang/String;[Ljava/lang/Object)Z", (void *)android_media_CodecCapabilities_isFormatSupported},
    { "native_CreateFromProfileLevel", "(Ljava/lang/String;I;I)Landroid/media/MediaCodecInfo$CodecCapabilities", (void *)android_media_CodecCapabilities_CreateFromProfileLevel},
};

int register_android_media_CodecCapabilities(JNIEnv *env) {
    int result = AndroidRuntime::registerNativeMethods(env,
            "android/media/MediaCodecInfo$AudioCapabilities", gAudioCapsMethods, NELEM(gAudioCapsMethods));
    if (result != JNI_OK) {
        return result;
    }
    result = AndroidRuntime::registerNativeMethods(env,
            "android/media/MediaCodecInfo$CodecCapabilities", gCodecCapsMethods, NELEM(gCodecCapsMethods));
    return result;
}