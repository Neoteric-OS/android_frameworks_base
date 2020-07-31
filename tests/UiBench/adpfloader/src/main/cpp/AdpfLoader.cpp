#include "AdpfLoader.h"

#include <dlfcn.h>
#include <memory>

#include <android/log.h>

#define LOG_TAG "AdpfLoader"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

AdpfLoader::AdpfLoader() {
    const char *const filename = "libAdpf.so";
    void *libAdpf = dlopen(filename, RTLD_LAZY);
    if (!libAdpf) {
        ALOGE("failed to load %s", filename);
        return;
    }

    ALOGI("successfully loaded %s", filename);
    mPermitFidelityDegradation = reinterpret_cast<VoidTakesBool>(dlsym(libAdpf,
                                                                       "permitFidelityDegradation"));
    if (!mPermitFidelityDegradation) {
        ALOGE("failed to load permitFidelityDegradation");
    }
}

AdpfLoader *AdpfLoader::getInstance() {
    static auto sLoader = std::make_unique<AdpfLoader>();
    return sLoader.get();
}

void AdpfLoader::permitFidelityDegradation(bool enable) {
    ALOGI("permitFidelityDegradation from native");
    if (mPermitFidelityDegradation) {
        mPermitFidelityDegradation(enable);
    }
}