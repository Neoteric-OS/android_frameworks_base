/*
 * Copyright 2024, The Android Open Source Project
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

#include <media/AudioCapabilities.h>
#include <media/CodecCapabilities.h>
#include <media/EncoderCapabilities.h>
#include <media/VideoCapabilities.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedLocalRef.h>
#include <utils/Log.h>

using namespace android;

// ----------------------------------------------------------------------------

struct fields_t {
    jfieldID audioCapsContext;
    jfieldID performancePointContext;
    jfieldID videoCapsContext;
    jfieldID encoderCapsContext;
    jfieldID codecCapsContext;
};
static fields_t fields;

// Utils

static jobject getJavaIntRangeFromNative(JNIEnv *env, const Range<int>& range) {
    // Get Integer Objects
    jclass integerClazz = env->FindClass("java/lang/Integer");
    jmethodID integerConstructID = env->GetMethodID(integerClazz, "<init>", "(I)V");
    jobject jLower = env->NewObject(integerClazz, integerConstructID, range.lower());
    jobject jUpper = env->NewObject(integerClazz, integerConstructID, range.upper());

    // Get Integer Range
    jclass helperClazz = env->FindClass("android/media/MediaCodecInfo$GenericHelper");
    jmethodID getIntegerRangeID = env->GetStaticMethodID(helperClazz, "getIntegerRange",
            "(Ljava/lang/Integer;Ljava/lang/Integer;)Landroid/util/Range;");
    jobject jRange = env->CallStaticObjectMethod(helperClazz, getIntegerRangeID, jLower, jUpper);

    env->DeleteLocalRef(jLower);
    jLower = NULL;
    env->DeleteLocalRef(jUpper);
    jUpper = NULL;

    return jRange;
}

static jobject getJavaDoubleRangeFromNative(JNIEnv *env, const Range<double>& range) {
    // Get Double Objects
    jclass doubleClazz = env->FindClass("java/lang/Double");
    jmethodID doubleConstructID = env->GetMethodID(doubleClazz, "<init>", "(D)V");
    jobject jLower = env->NewObject(doubleClazz, doubleConstructID, range.lower());
    jobject jUpper = env->NewObject(doubleClazz, doubleConstructID, range.upper());

    // Get Double Range
    jclass helperClazz = env->FindClass("android/media/MediaCodecInfo$GenericHelper");
    jmethodID getDoubleRangeID = env->GetStaticMethodID(helperClazz, "getDoubleRange",
            "(Ljava/lang/Double;Ljava/lang/Double;)Landroid/util/Range;");
    jobject jRange = env->CallStaticObjectMethod(helperClazz, getDoubleRangeID, jLower, jUpper);

    env->DeleteLocalRef(jLower);
    jLower = NULL;
    env->DeleteLocalRef(jUpper);
    jUpper = NULL;

    return jRange;
}

static jobjectArray getJavaIntRangeArrayFromNative(JNIEnv *env, const std::vector<Range<int>>& ranges) {
    jclass rangeClazz = env->FindClass("android/util/Range");
    CHECK(rangeClazz != NULL);
    jobjectArray jRanges = env->NewObjectArray(ranges.size(), rangeClazz, NULL);
    for (int i = 0; i < ranges.size(); i++) {
        Range<int> range = ranges.at(i);
        jobject jRange = getJavaIntRangeFromNative(env, range);
        env->SetObjectArrayElement(jRanges, i, jRange);
        env->DeleteLocalRef(jRange);
        jRange = NULL;
    }
    return jRanges;
}

static std::shared_ptr<AudioCapabilities> getAudioCapabilities(JNIEnv *env, jobject thiz) {
    AudioCapabilities* const p = (AudioCapabilities*)env->GetLongField(thiz, fields.audioCapsContext);
    return std::shared_ptr<AudioCapabilities>(p);
}

static VideoCapabilities::PerformancePoint& getPerformancePoints(JNIEnv *env, jobject thiz) {
    VideoCapabilities::PerformancePoint* const p
            = (VideoCapabilities::PerformancePoint*)env->GetLongField(thiz, fields.performancePointContext);
    return *p;
}

static std::shared_ptr<VideoCapabilities> getVideoCapabilities(JNIEnv *env, jobject thiz) {
    VideoCapabilities* const p = (VideoCapabilities*)env->GetLongField(thiz, fields.videoCapsContext);
    return std::shared_ptr<VideoCapabilities>(p);
}

static std::shared_ptr<EncoderCapabilities> getEncoderCapabilities(JNIEnv *env, jobject thiz) {
    EncoderCapabilities* const p = (EncoderCapabilities*)env->GetLongField(thiz, fields.encoderCapsContext);
    return std::shared_ptr<EncoderCapabilities>(p);
}

static std::shared_ptr<CodecCapabilities> getCodecCapabilities(JNIEnv *env, jobject thiz) {
    CodecCapabilities* const p = (CodecCapabilities*)env->GetLongField(thiz, fields.codecCapsContext);
    return std::shared_ptr<CodecCapabilities>(p);
}

// The Java AudioCapabilities object keep bitrateRange, sampleRates, sampleRateRanges
// and inputChannelRanges in it to prevent reconstruction when called the getters functions.
static jobject getJavaAudioCapabilitiesFromNative(
        JNIEnv *env, std::shared_ptr<AudioCapabilities> audioCaps) {
    if (audioCaps == nullptr) {
        ALOGE("audioCaps is nullptr");
        return NULL;
    }

    // construct Java bitrateRange
    const Range<int>& bitrateRange = audioCaps->getBitrateRange();
    jobject jBitrateRange = getJavaIntRangeFromNative(env, bitrateRange);

    // construct Java sampleRates array
    const std::vector<int>& sampleRates = audioCaps->getSupportedSampleRates();
    jintArray jSampleRates = env->NewIntArray(sampleRates.size());
    for (size_t i = 0; i < sampleRates.size(); ++i) {
        jint val = sampleRates.at(i);
        env->SetIntArrayRegion(jSampleRates, i, 1, &val);
    }

    // construct Java sampleRateRanges
    const std::vector<Range<int>>& sampleRateRanges = audioCaps->getSupportedSampleRateRanges();
    jobjectArray jSampleRateRanges = getJavaIntRangeArrayFromNative(env, sampleRateRanges);

    // construct Java inputChannelRanges
    const std::vector<Range<int>>& inputChannelRanges = audioCaps->getInputChannelCountRanges();
    jobjectArray jInputChannelRanges = getJavaIntRangeArrayFromNative(env, inputChannelRanges);

    // construct Java AudioCapabilities
    jclass audioCapsClazz
            = env->FindClass("android/media/MediaCodecInfo$AudioCapabilities");
    CHECK(audioCapsClazz != NULL);
    jmethodID audioCapsConstructID = env->GetMethodID(audioCapsClazz, "<init>",
            "(Landroid/util/Range;"
            "[I"
            "[Landroid/util/Range;"
            "[Landroid/util/Range;)V");
    jobject jAudioCaps = env->NewObject(audioCapsClazz, audioCapsConstructID, jBitrateRange, jSampleRates,
            jSampleRateRanges, jInputChannelRanges);

    env->DeleteLocalRef(jBitrateRange);
    jBitrateRange = NULL;

    env->DeleteLocalRef(jSampleRates);
    jSampleRates = NULL;

    env->DeleteLocalRef(jSampleRateRanges);
    jSampleRateRanges = NULL;

    env->DeleteLocalRef(jInputChannelRanges);
    jInputChannelRanges = NULL;

    env->SetLongField(jAudioCaps, fields.audioCapsContext, (jlong)audioCaps.get());

    return jAudioCaps;
}

static jobject convertPerformancePointVectorToList(JNIEnv *env,
        const std::vector<VideoCapabilities::PerformancePoint>& performancePoints) {
    jclass performancePointClazz = env->FindClass(
            "android/media/MediaCodecInfo$VideoCapabilities$PerformancePoint");
    CHECK(performancePointClazz != NULL);
    jmethodID performancePointConstructID = env->GetMethodID(performancePointClazz, "<init>",
            "(IIIJII)V");

    jobjectArray jPerformancePoints = env->NewObjectArray(performancePoints.size(),
            performancePointClazz, NULL);
    for (int i = 0; i < performancePoints.size(); i++) {
        VideoCapabilities::PerformancePoint performancePoint = performancePoints.at(i);
        jobject jPerformancePoint = env->NewObject(performancePointClazz,
                performancePointConstructID, performancePoint.getWidth(),
                performancePoint.getHeight(), performancePoint.getMaxFrameRate(),
                performancePoint.getMaxMacroBlockRate(), performancePoint.getBlockSize().getWidth(),
                performancePoint.getBlockSize().getHeight());

        env->SetLongField(jPerformancePoint, fields.performancePointContext, (jlong)(&performancePoint));

        env->SetObjectArrayElement(jPerformancePoints, i, jPerformancePoint);

        env->DeleteLocalRef(jPerformancePoint);
        jPerformancePoint = NULL;
    }

    jclass helperClazz = env->FindClass("android/media/MediaCodecInfo$GenericHelper");
    CHECK(helperClazz != NULL);
    jmethodID asListID = env->GetStaticMethodID(helperClazz, "ConvertPerformancePointArrayToList",
            "([Landroid/media/MediaCodecInfo$VideoCapabilities$PerformancePoint;)Ljava/util/List;");
    CHECK(asListID != NULL);
    jobject jList = env->CallStaticObjectMethod(helperClazz, asListID, jPerformancePoints);

    return jList;
}

jobject getJavaVideoCapabilitiesFromNative(JNIEnv *env,
        std::shared_ptr<VideoCapabilities> videoCaps) {
    if (videoCaps == nullptr) {
        return NULL;
    }

    // get Java bitrateRange
    const Range<int>& bitrateRange = videoCaps->getBitrateRange();
    jobject jBitrateRange = getJavaIntRangeFromNative(env, bitrateRange);

    // get Java widthRange
    const Range<int>& widthRange = videoCaps->getSupportedWidths();
    jobject jWidthRange = getJavaIntRangeFromNative(env, widthRange);

    // get Java heightRange
    const Range<int>& heightRange = videoCaps->getSupportedHeights();
    jobject jHeightRange = getJavaIntRangeFromNative(env, heightRange);

    // get Java frameRateRange
    const Range<int>& frameRateRange = videoCaps->getSupportedFrameRates();
    jobject jFrameRateRange = getJavaIntRangeFromNative(env, frameRateRange);

    // get Java performancePoints
    const std::vector<VideoCapabilities::PerformancePoint>& performancePoints
            = videoCaps->getSupportedPerformancePoints();
    // jobjectArray jPerformancePoints = getJavaPerformancePointArrayFromNative(
    //         env, performancePoints);
    jobject jPerformancePoints = convertPerformancePointVectorToList(env, performancePoints);

    // get width alignment
    int widthAlignment = videoCaps->getWidthAlignment();

    // get height alignment
    int heightAlignment = videoCaps->getHeightAlignment();

    // get Java VideoCapabilities
    jclass videoCapsClazz =
        env->FindClass("android/media/MediaCodecInfo$VideoCapabilities");
    CHECK(videoCapsClazz != NULL);
    jmethodID videoCapsConstructID = env->GetMethodID(videoCapsClazz, "<init>",
            "(Landroid/util/Range;"
            "Landroid/util/Range;"
            "Landroid/util/Range;"
            "Landroid/util/Range;"
            "Ljava/util/List;II)V");
    jobject jVideoCaps = env->NewObject(videoCapsClazz, videoCapsConstructID, jBitrateRange,
            jWidthRange, jHeightRange, jFrameRateRange, jPerformancePoints, widthAlignment,
            heightAlignment);

    env->DeleteLocalRef(jBitrateRange);
    jBitrateRange = NULL;

    env->DeleteLocalRef(jWidthRange);
    jWidthRange = NULL;

    env->DeleteLocalRef(jHeightRange);
    jHeightRange = NULL;

    env->DeleteLocalRef(jFrameRateRange);
    jFrameRateRange = NULL;

    env->DeleteLocalRef(jPerformancePoints);
    jPerformancePoints = NULL;

    env->SetLongField(jVideoCaps, fields.videoCapsContext, (jlong)videoCaps.get());

    return jVideoCaps;
}

jobject getJavaEncoderCapabilitiesFromNative(JNIEnv *env,
        std::shared_ptr<EncoderCapabilities> encoderCaps) {
    if (encoderCaps == nullptr) {
        return NULL;
    }

    // get quality range
    const Range<int>& qualityRange = encoderCaps->getQualityRange();
    jobject jQualityRange = getJavaIntRangeFromNative(env, qualityRange);

    // get complexity range
    const Range<int>& complexityRange = encoderCaps->getComplexityRange();
    jobject jComplexityRange = getJavaIntRangeFromNative(env, complexityRange);

    // construct java EncoderCapabilities object
    jclass encoderCapsClazz
            = env->FindClass("android/media/MediaCodecInfo$EncoderCapabilities");
    CHECK(encoderCapsClazz != NULL);
    jmethodID encoderCapsConstructID = env->GetMethodID(encoderCapsClazz, "<init>",
            "(Landroid/util/Range;Landroid/util/Range;)V");
    jobject jEncoderCaps = env->NewObject(encoderCapsClazz, encoderCapsConstructID,
            jQualityRange, jComplexityRange);

    env->DeleteLocalRef(jQualityRange);
    jQualityRange = NULL;

    env->DeleteLocalRef(jComplexityRange);
    jComplexityRange = NULL;

    env->SetLongField(jEncoderCaps, fields.encoderCapsContext, (jlong)encoderCaps.get());

    return jEncoderCaps;
}

// Java CodecCapabilities keeps the defaultFormat, profileLevels, colorFormats, audioCapabilities,
// videoCapabilities and encoderCapabilities in it to prevent reconsturction when called by getter functions.
jobject constructJavaCodecCapabilitiesFromNative(
        JNIEnv *env, std::shared_ptr<CodecCapabilities> codecCaps) {
    if (codecCaps == nullptr) {
        return NULL;
    }

    // Construct defaultFormat
    sp<AMessage> defaultFormat = codecCaps->getDefaultFormat();

    jobject formatMap = NULL;
    if (ConvertMessageToMap(env, defaultFormat, &formatMap)) {
        return NULL;
    }

    ScopedLocalRef<jclass> mediaFormatClass{env, env->FindClass("android/media/MediaFormat")};
    ScopedLocalRef<jobject> jDefaultFormat{env, env->NewObject(
            mediaFormatClass.get(),
            env->GetMethodID(mediaFormatClass.get(), "<init>", "(Ljava/util/Map;)V"),
            formatMap)};

    env->DeleteLocalRef(formatMap);
    formatMap = NULL;

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

    // Construct and set AudioCapabilities
    jobject jAudioCaps = NULL;
    std::shared_ptr<AudioCapabilities> audioCaps = codecCaps->getAudioCapabilities();
    jAudioCaps = getJavaAudioCapabilitiesFromNative(env, audioCaps);

    // Set VideoCapabilities
    jobject jVideoCaps = NULL;
    std::shared_ptr<VideoCapabilities> videoCaps = codecCaps->getVideoCapabilities();
    jVideoCaps = getJavaVideoCapabilitiesFromNative(env, videoCaps);

    // Set EncoderCapabilities
    jobject jEncoderCaps = NULL;
    if (codecCaps->isEncoder()) {
        std::shared_ptr<EncoderCapabilities> encoderCaps = codecCaps->getEncoderCapabilities();
        jEncoderCaps = getJavaEncoderCapabilitiesFromNative(env, encoderCaps);
    }

    // Construct CodecCapabilities
    jclass capsClazz =
        env->FindClass("android/media/MediaCodecInfo$CodecCapabilities");
    CHECK(capsClazz != NULL);

    jmethodID capsConstructID = env->GetMethodID(capsClazz, "<init>",
                "([Landroid/media/MediaCodecInfo$CodecProfileLevel;[I"
                "Landroid/media/MediaFormat;"
                "Landroid/media/MediaCodecInfo$AudioCapabilities;"
                "Landroid/media/MediaCodecInfo$VideoCapabilities;"
                "Landroid/media/MediaCodecInfo$EncoderCapabilities;)V");

    jobject jCodecCaps = env->NewObject(capsClazz, capsConstructID,
            profileLevelArray, colorFormatsArray, jDefaultFormat.get(),
            jAudioCaps, jVideoCaps, jEncoderCaps);

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

    env->SetLongField(jCodecCaps, fields.codecCapsContext, (jlong)codecCaps.get());

    return jCodecCaps;
}

// ----------------------------------------------------------------------------

// AudioCapabilities

static void android_media_AudioCapabilities_native_init(JNIEnv *env) {
    jclass audioCapsClazz
            = env->FindClass("android/media/MediaCodecInfo$AudioCapabilities");
    if (audioCapsClazz == NULL) {
        return;
    }

    fields.audioCapsContext = env->GetFieldID(audioCapsClazz, "mNativeContext", "J");
    if (fields.audioCapsContext == NULL) {
        return;
    }

    env->DeleteLocalRef(audioCapsClazz);
}

static jint android_media_AudioCapabilities_getMaxInputChannelCount(JNIEnv *env, jobject thiz) {
    std::shared_ptr<AudioCapabilities> audioCaps = getAudioCapabilities(env, thiz);
    if (audioCaps == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return 0;
    }

    int maxInputChannelCount = audioCaps->getMaxInputChannelCount();
    return maxInputChannelCount;
}

static jint android_media_AudioCapabilities_getMinInputChannelCount(JNIEnv *env, jobject thiz) {
    std::shared_ptr<AudioCapabilities> audioCaps = getAudioCapabilities(env, thiz);
    if (audioCaps == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return 0;
    }

    int minInputChannelCount = audioCaps->getMinInputChannelCount();
    return minInputChannelCount;
}

static jboolean android_media_AudioCapabilities_isSampleRateSupported(JNIEnv *env, jobject thiz, int sampleRate) {
    std::shared_ptr<AudioCapabilities> audioCaps = getAudioCapabilities(env, thiz);
    if (audioCaps == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return 0;
    }

    bool res = audioCaps->isSampleRateSupported(sampleRate);
    return res;
}

// PerformancePoint

static void android_media_VideoCapabilities_PerformancePoint_native_init(JNIEnv *env) {
    jclass clazz = env->FindClass(
            "android/media/MediaCodecInfo$VideoCapabilities$PerformancePoint");
    if (clazz == NULL) {
        return;
    }

    fields.performancePointContext = env->GetFieldID(clazz, "mNativeContext", "J");
    if (fields.performancePointContext == NULL) {
        return;
    }

    env->DeleteLocalRef(clazz);
}

static VideoCapabilities::PerformancePoint GetNativePerformancePointFromJava(JNIEnv *env,
        jobject pp) {
    jclass clazz = env->FindClass(
            "android/media/MediaCodecInfo$VideoCapabilities$PerformancePoint");
    CHECK(clazz != NULL);
    CHECK(env->IsInstanceOf(pp, clazz));

    jmethodID getWidthID = env->GetMethodID(clazz, "getWidth", "()I");
    CHECK(getWidthID != NULL);
    jint width = env->CallIntMethod(pp, getWidthID);

    jmethodID getHeightID = env->GetMethodID(clazz, "getHeight", "()I");
    CHECK(getHeightID != NULL);
    jint height = env->CallIntMethod(pp, getHeightID);

    jmethodID getMaxFrameRateID = env->GetMethodID(clazz, "getMaxFrameRate", "()I");
    CHECK(getMaxFrameRateID != NULL);
    jint maxFrameRate = env->CallIntMethod(pp, getMaxFrameRateID);

    jmethodID getMaxMacroBlockRateID = env->GetMethodID(clazz, "getMaxMacroBlockRate", "()J");
    CHECK(getMaxMacroBlockRateID != NULL);
    jlong maxMacroBlockRate = env->CallLongMethod(pp, getMaxMacroBlockRateID);

    jmethodID getBlockWidthID = env->GetMethodID(clazz, "getBlockWidth", "()I");
    CHECK(getBlockWidthID != NULL);
    jint blockWidth = env->CallIntMethod(pp, getBlockWidthID);

    jmethodID getBlockHeightID = env->GetMethodID(clazz, "getBlockHeight", "()I");
    CHECK(getBlockHeightID != NULL);
    jint blockHeight = env->CallIntMethod(pp, getBlockHeightID);

    VideoCapabilities::PerformancePoint res = VideoCapabilities::PerformancePoint(
            VideoSize(blockWidth, blockHeight), width, height, maxFrameRate, maxMacroBlockRate);

    return res;
}

static jboolean android_media_VideoCapabilities_PerformancePoint_covers(JNIEnv *env, jobject thiz,
        jobject other) {
    VideoCapabilities::PerformancePoint& pp0 = getPerformancePoints(env, thiz);
    VideoCapabilities::PerformancePoint pp1 = GetNativePerformancePointFromJava(env, other);

    bool res = pp0.covers(pp1);

    return res;
}

static jboolean android_media_VideoCapabilities_PerformancePoint_equals(JNIEnv *env, jobject thiz,
        jobject other) {
    VideoCapabilities::PerformancePoint& pp0 = getPerformancePoints(env, thiz);
    VideoCapabilities::PerformancePoint pp1 = GetNativePerformancePointFromJava(env, other);

    bool res = pp0.equals(pp1);

    return res;
}

static jstring android_media_VideoCapabilities_PerformancePoint_toString(JNIEnv *env,
        jobject thiz) {
    VideoCapabilities::PerformancePoint& pp = getPerformancePoints(env, thiz);
    std::string str = pp.toString();
    return env->NewStringUTF(str.c_str());
}

// VideoCapabilities

static void android_media_VideoCapabilities_native_init(JNIEnv *env) {
    jclass clazz
            = env->FindClass("android/media/MediaCodecInfo$VideoCapabilities");
    if (clazz == NULL) {
        return;
    }

    fields.videoCapsContext = env->GetFieldID(clazz, "mNativeContext", "J");
    if (fields.videoCapsContext == NULL) {
        return;
    }

    env->DeleteLocalRef(clazz);
}

static jboolean android_media_VideoCapabilities_areSizeAndRateSupported(JNIEnv *env, jobject thiz,
        int width, int height, double frameRate) {
    std::shared_ptr<VideoCapabilities> videoCaps = getVideoCapabilities(env, thiz);
    if (videoCaps == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return 0;
    }

    bool res = videoCaps->areSizeAndRateSupported(width, height, frameRate);
    return res;
}

static jboolean android_media_VideoCapabilities_isSizeSupported(JNIEnv *env, jobject thiz,
        int width, int height) {
    std::shared_ptr<VideoCapabilities> videoCaps = getVideoCapabilities(env, thiz);
    if (videoCaps == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return 0;
    }

    bool res = videoCaps->isSizeSupported(width, height);
    return res;
}

static jobject android_media_VideoCapabilities_getAchievableFrameRatesFor(JNIEnv *env, jobject thiz,
        int width, int height) {
    std::shared_ptr<VideoCapabilities> videoCaps = getVideoCapabilities(env, thiz);
    if (videoCaps == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return NULL;
    }

    std::optional<Range<double>> frameRates = videoCaps->getAchievableFrameRatesFor(width, height);
    if (!frameRates) {
        return NULL;
    }
    jobject jFrameRates = getJavaDoubleRangeFromNative(env, frameRates.value());

    return jFrameRates;
}

static jobject android_media_VideoCapabilities_getSupportedFrameRatesFor(JNIEnv *env, jobject thiz,
        int width, int height) {
    std::shared_ptr<VideoCapabilities> videoCaps = getVideoCapabilities(env, thiz);
    if (videoCaps == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return NULL;
    }

    Range<double> frameRates = videoCaps->getSupportedFrameRatesFor(width, height);
    jobject jFrameRates = getJavaDoubleRangeFromNative(env, frameRates);

    return jFrameRates;
}

static jobject android_media_VideoCapabilities_getSupportedWidthsFor(JNIEnv *env, jobject thiz,
        int height) {
    std::shared_ptr<VideoCapabilities> videoCaps = getVideoCapabilities(env, thiz);
    if (videoCaps == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return NULL;
    }

    std::optional<Range<int>> supportedWidths = videoCaps->getSupportedWidthsFor(height);
    if (!supportedWidths) {
        return NULL;
    }
    jobject jSupportedWidths = getJavaIntRangeFromNative(env, supportedWidths.value());

    return jSupportedWidths;
}

static jobject android_media_VideoCapabilities_getSupportedHeightsFor(JNIEnv *env, jobject thiz,
        int width) {
    std::shared_ptr<VideoCapabilities> videoCaps = getVideoCapabilities(env, thiz);
    if (videoCaps == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return NULL;
    }

    std::optional<Range<int>> supportedHeights = videoCaps->getSupportedHeightsFor(width);
    if (!supportedHeights) {
        return NULL;
    }
    jobject jSupportedHeights = getJavaIntRangeFromNative(env, supportedHeights.value());

    return jSupportedHeights;
}

// EncoderCapabilities

static void android_media_EncoderCapabilities_native_init(JNIEnv *env) {
    jclass clazz
            = env->FindClass("android/media/MediaCodecInfo$EncoderCapabilities");
    if (clazz == NULL) {
        return;
    }

    fields.encoderCapsContext = env->GetFieldID(clazz, "mNativeContext", "J");
    if (fields.encoderCapsContext == NULL) {
        return;
    }

    env->DeleteLocalRef(clazz);
}

static jboolean android_media_EncoderCapabilities_isBitrateModeSupported(JNIEnv *env, jobject thiz,
        int mode) {
    std::shared_ptr<EncoderCapabilities> encoderCaps = getEncoderCapabilities(env, thiz);
    if (encoderCaps == NULL) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return 0;
    }

    bool res = encoderCaps->isBitrateModeSupported(mode);
    return res;
}

// CodecCapabilities

static void android_media_CodecCapabilities_native_init(JNIEnv *env) {
    jclass codecCapsClazz
            = env->FindClass("android/media/MediaCodecInfo$CodecCapabilities");
    if (codecCapsClazz == NULL) {
        return;
    }

    fields.codecCapsContext = env->GetFieldID(codecCapsClazz, "mNativeContext", "J");
    if (fields.codecCapsContext == NULL) {
        return;
    }

    env->DeleteLocalRef(codecCapsClazz);
}

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

    std::string mediaType = codecCaps->getMediaType();
    return env->NewStringUTF(mediaType.c_str());
}

static jboolean android_media_CodecCapabilities_isFeatureRequired(
        JNIEnv *env, jobject thiz, jstring name) {
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

static jboolean android_media_CodecCapabilities_isFeatureSupported(
        JNIEnv *env, jobject thiz, jstring name) {
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

static jobject android_media_CodecCapabilities_CreateFromProfileLevel(JNIEnv *env,
        jobject /* thiz */, jstring mediaType, jint profile, jint level) {
    if (mediaType == NULL) {
        jniThrowException(env, "java/lang/IllegalArgumentException", NULL);
        return NULL;
    }

    const char *mediaTypeStr = env->GetStringUTFChars(mediaType, NULL);
    if (mediaTypeStr == NULL) {
        return NULL;
    }

    std::shared_ptr<CodecCapabilities> codecCaps = CodecCapabilities::CreateFromProfileLevel(
            mediaTypeStr, profile, level);

    jobject jCodecCaps = constructJavaCodecCapabilitiesFromNative(env, codecCaps);

    return jCodecCaps;
}

// ----------------------------------------------------------------------------

static const JNINativeMethod gAudioCapsMethods[] = {
    {"native_init", "()V", (void *)android_media_AudioCapabilities_native_init},
    {"native_getMaxInputChannelCount", "()I", (void *)android_media_AudioCapabilities_getMaxInputChannelCount},
    {"native_getMinInputChannelCount", "()I", (void *)android_media_AudioCapabilities_getMinInputChannelCount},
    {"native_isSampleRateSupported", "(I)Z", (void *)android_media_AudioCapabilities_isSampleRateSupported}
};

static const JNINativeMethod gPerformancePointMethods[] = {
    {"native_init", "()V", (void *)android_media_VideoCapabilities_PerformancePoint_native_init},
    {"native_covers", "(Landroid/media/MediaCodecInfo$VideoCapabilities$PerformancePoint;)Z", (void *)android_media_VideoCapabilities_PerformancePoint_covers},
    {"native_equals", "(Landroid/media/MediaCodecInfo$VideoCapabilities$PerformancePoint;)Z", (void *)android_media_VideoCapabilities_PerformancePoint_equals},
    {"native_toString", "()Ljava/lang/String;", (void *)android_media_VideoCapabilities_PerformancePoint_toString}
};

static const JNINativeMethod gVideoCapsMethods[] = {
    {"native_init", "()V", (void *)android_media_VideoCapabilities_native_init},
    {"native_areSizeAndRateSupported", "(IID)Z", (void *)android_media_VideoCapabilities_areSizeAndRateSupported},
    {"native_isSizeSupported", "(II)Z", (void *)android_media_VideoCapabilities_isSizeSupported},
    {"native_getAchievableFrameRatesFor", "(II)Landroid/util/Range;", (void *)android_media_VideoCapabilities_getAchievableFrameRatesFor},
    {"native_getSupportedFrameRatesFor", "(II)Landroid/util/Range;", (void *)android_media_VideoCapabilities_getSupportedFrameRatesFor},
    {"native_getSupportedWidthsFor", "(I)Landroid/util/Range;", (void *)android_media_VideoCapabilities_getSupportedWidthsFor},
    {"native_getSupportedHeightsFor", "(I)Landroid/util/Range;", (void *)android_media_VideoCapabilities_getSupportedHeightsFor}
};

static const JNINativeMethod gEncoderCapsMethods[] = {
    {"native_init", "()V", (void *)android_media_EncoderCapabilities_native_init},
    {"native_isBitrateModeSupported", "(I)Z", (void *)android_media_EncoderCapabilities_isBitrateModeSupported}
};

static const JNINativeMethod gCodecCapsMethods[] = {
    {"native_init", "()V", (void *)android_media_CodecCapabilities_native_init},
    { "native_getMaxSupportedInstances", "()I", (void *)android_media_CodecCapabilities_getMaxSupportedInstances },
    { "native_getMimeType", "()Ljava/lang/String;", (void *)android_media_CodecCapabilities_getMimeType },
    { "native_isFeatureRequired", "(Ljava/lang/String;)Z", (void *)android_media_CodecCapabilities_isFeatureRequired },
    { "native_isFeatureSupported", "(Ljava/lang/String;)Z", (void *)android_media_CodecCapabilities_isFeatureSupported },
    { "native_isFormatSupported", "([Ljava/lang/String;[Ljava/lang/Object;)Z", (void *)android_media_CodecCapabilities_isFormatSupported},
    { "native_CreateFromProfileLevel", "(Ljava/lang/String;II)Landroid/media/MediaCodecInfo$CodecCapabilities;", (void *)android_media_CodecCapabilities_CreateFromProfileLevel},
};

int register_android_media_CodecCapabilities(JNIEnv *env) {
    int result = AndroidRuntime::registerNativeMethods(env, "android/media/MediaCodecInfo$AudioCapabilities",
            gAudioCapsMethods, NELEM(gAudioCapsMethods));
    if (result != JNI_OK) {
        return result;
    }

    result = AndroidRuntime::registerNativeMethods(env,
            "android/media/MediaCodecInfo$VideoCapabilities$PerformancePoint",
            gPerformancePointMethods, NELEM(gPerformancePointMethods));
    if (result != JNI_OK) {
        return result;
    }

    result = AndroidRuntime::registerNativeMethods(env, "android/media/MediaCodecInfo$VideoCapabilities",
            gVideoCapsMethods, NELEM(gVideoCapsMethods));
    if (result != JNI_OK) {
        return result;
    }

    result = AndroidRuntime::registerNativeMethods(env, "android/media/MediaCodecInfo$EncoderCapabilities",
            gEncoderCapsMethods, NELEM(gEncoderCapsMethods));
    if (result != JNI_OK) {
        return result;
    }

    result = AndroidRuntime::registerNativeMethods(env, "android/media/MediaCodecInfo$CodecCapabilities",
            gCodecCapsMethods, NELEM(gCodecCapsMethods));
    return result;
}