#pragma once

#include <jni.h>

class AdpfLoader {
public:
    AdpfLoader();

    static AdpfLoader* getInstance();

    void permitFidelityDegradation(bool enable);

private:
    using VoidTakesBool = void (*) (bool);
    VoidTakesBool mPermitFidelityDegradation = nullptr;
};

extern "C" {
void Java_com_android_adpfloader_AdpfLoader_nPermitFidelityDegradation(JNIEnv * /*env*/,
                                                                       jobject /*thiz*/,
                                                                       jboolean enable) {
    AdpfLoader::getInstance()->permitFidelityDegradation(enable);
}
}