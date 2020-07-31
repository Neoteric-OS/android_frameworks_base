#pragma once

#include <vector>

#include <jni.h>

class AdpfLoader {
public:
    AdpfLoader();

    static AdpfLoader *getInstance();

    using CreateStage = int64_t (*)(const int* threadIds, uint32_t threadIdsSize,
            int64_t desiredDurationMicros);
    CreateStage createStage = nullptr;

    using DestroyStage = void (*)(int64_t);
    DestroyStage destroyStage = nullptr;

    using ReportCompletionTime = void (*)(int64_t, int64_t);
    ReportCompletionTime reportCpuCompletionTime = nullptr;

    using HintLowLatency = void (*)(int*, uint32_t);
    HintLowLatency hintLowLatency = nullptr;

    using HintLoadChange = void (*)(int32_t, int32_t);
    HintLoadChange hintLoadChange = nullptr;

    using HintMode = void (*)(int32_t, int64_t, int64_t);
    HintMode hintMode = nullptr;

    using VoidTakesBool = void (*)(bool);
    VoidTakesBool allowAppSpecificOptimizations = nullptr;
    VoidTakesBool allowFidelityDegradation = nullptr;
};