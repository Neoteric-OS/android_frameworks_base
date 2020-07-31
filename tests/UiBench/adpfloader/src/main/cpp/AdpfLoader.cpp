#include "AdpfLoader.h"

#include <dlfcn.h>
#include <memory>

#include <android/log.h>

#define LOG_TAG "AdpfLoader"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

template<typename FP>
void load(void *libAdpf, FP &target, const char *name) {
    target = reinterpret_cast<FP>(dlsym(libAdpf, name));
    if (!target) {
        ALOGE("failed to load %s", name);
    }
}

AdpfLoader::AdpfLoader() {
    const char *const filename = "libAdpf.so";
    void *libAdpf = dlopen(filename, RTLD_LAZY);
    if (!libAdpf) {
        ALOGE("failed to load %s", filename);
        return;
    }

    ALOGI("successfully loaded %s", filename);

    load(libAdpf, createStage, "createStage");
    load(libAdpf, destroyStage, "destroyStage");
    load(libAdpf, reportCpuCompletionTime, "reportCpuCompletionTime");
    load(libAdpf, hintLowLatency, "hintLowLatency");
    load(libAdpf, hintLoadChange, "hintLoadChange");
    load(libAdpf, hintMode, "hintMode");
    load(libAdpf, allowAppSpecificOptimizations, "allowAppSpecificOptimizations");
    load(libAdpf, allowFidelityDegradation, "allowFidelityDegradation");
}

AdpfLoader *AdpfLoader::getInstance() {
    static auto sLoader = std::make_unique<AdpfLoader>();
    return sLoader.get();
}

extern "C" {
jlong Java_com_android_adpfloader_AdpfLoader_nCreateStage(JNIEnv *env, jobject /*thiz*/,
                                                          jintArray threadIds,
                                                          jlong desiredDurationMicros) {
    ALOGI("in nCreateStage");
    if (auto createStage = AdpfLoader::getInstance()->createStage) {
        jint *firstId = env->GetIntArrayElements(threadIds, nullptr);
        jsize idsSize = env->GetArrayLength(threadIds);
        ALOGI("calling libAdpf createStage");
        int64_t stageId = createStage(firstId, static_cast<uint32_t>(idsSize),
                                      desiredDurationMicros);
        env->ReleaseIntArrayElements(threadIds, firstId, 0);
        return stageId;
    }
    return -1;
}

void
Java_com_android_adpfloader_AdpfLoader_destroyStage(JNIEnv * /*env*/, jobject /*thiz*/, jlong id) {
    if (auto destroyStage = AdpfLoader::getInstance()->destroyStage) {
        destroyStage(id);
    }
}

void
Java_com_android_adpfloader_AdpfLoader_reportCpuCompletionTime(JNIEnv * /*env*/, jobject /*thiz*/,
                                                               jlong stageId,
                                                               jlong actualDurationMicros) {
    if (auto reportCpuCompletionTime = AdpfLoader::getInstance()->reportCpuCompletionTime) {
        reportCpuCompletionTime(stageId, actualDurationMicros);
    }
}

void
Java_com_android_adpfloader_AdpfLoader_nHintLowLatency(JNIEnv *env, jobject /*thiz*/,
                                                       jintArray threadIds) {
    if (auto hintLowLatency = AdpfLoader::getInstance()->hintLowLatency) {
        jint *firstId = env->GetIntArrayElements(threadIds, nullptr);
        jsize idsSize = env->GetArrayLength(threadIds);
        hintLowLatency(firstId, static_cast<uint32_t>(idsSize));
        env->ReleaseIntArrayElements(threadIds, firstId, 0);
    }
}

void Java_com_android_adpfloader_AdpfLoader_hintLoadChange(JNIEnv * /*env*/,
                                                           jobject /*thiz*/,
                                                           jint unit, jint direction) {
    if (auto hintLoadChange = AdpfLoader::getInstance()->hintLoadChange) {
        hintLoadChange(unit, direction);
    }
}

void Java_com_android_adpfloader_AdpfLoader_hintMode(JNIEnv * /*env*/,
                                                     jobject /*thiz*/,
                                                     jint mode, jlong majorPhase,
                                                     jlong minorPhase) {
    if (auto hintMode = AdpfLoader::getInstance()->hintMode) {
        hintMode(mode, majorPhase, minorPhase);
    }
}

void Java_com_android_adpfloader_AdpfLoader_allowAppSpecificOptimizations(JNIEnv * /*env*/,
                                                                          jobject /*thiz*/,
                                                                          jboolean enable) {
    if (auto allowAppSpecificOptimizations = AdpfLoader::getInstance()->allowAppSpecificOptimizations) {
        allowAppSpecificOptimizations(enable);
    }
}

void Java_com_android_adpfloader_AdpfLoader_allowFidelityDegradation(JNIEnv * /*env*/,
                                                                     jobject /*thiz*/,
                                                                     jboolean enable) {
    if (auto allowFidelityDegradation = AdpfLoader::getInstance()->allowFidelityDegradation) {
        allowFidelityDegradation(enable);
    }
}
}