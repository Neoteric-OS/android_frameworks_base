/*
 * Copyright (C) 2007 The Android Open Source Project
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

#define LOG_TAG "system_server_jni"

#include <jni.h>
#include <nativehelper/JNIHelp.h>

#include <hidl/HidlTransportSupport.h>

#include <schedulerservice/SchedulingPolicyService.h>
#include <sensorservice/SensorService.h>
#include <sensorservicehidl/SensorManager.h>

#include <cutils/properties.h>
#include <utils/Log.h>
#include <utils/misc.h>
#include <utils/AndroidThreads.h>

static struct sigaction oldTERMaction;

/* Trigger System Dump from /proc/sysrq-trigger */
static void do_system_dump() {
  int fd = TEMP_FAILURE_RETRY(open("/proc/sysrq-trigger", O_WRONLY));
  if (fd >= 0) {
    int result=0;
    if ((result = TEMP_FAILURE_RETRY(write(fd, "c", 1)) != 1)) {
      ALOGD("Sysrq-trigger is set, crash triggerred");
      close(fd);
    } else {
      ALOGE("Failed set sysrq-trigger: %d", result);
      close(fd);
    }
  } else {
    ALOGE("Failed to open sysrq-trigger");
  }
}

static void recv_sigterm_handler(int sig, siginfo_t *info, void *v)
{
    if (info != NULL) {
        ALOGE("The system_server process was killed by pid %d, code %d.\n",
              info->si_pid, info->si_code);
    } else {
        ALOGE("The system_server process was killed.\n");
    }

    do_system_dump();

    ALOGD("Do default behavior");
    sigaction(sig, &oldTERMaction, NULL);
    raise(sig);
}

namespace android {

static void android_server_SystemServer_startSensorService(JNIEnv* /* env */, jobject /* clazz */) {
    // Handle SIGTERM here to crash the phone when this process is killed.
    struct sigaction action;
    sigemptyset(&action.sa_mask);
    action.sa_sigaction = recv_sigterm_handler;
    action.sa_flags = SA_SIGINFO;
    sigaction(SIGTERM, &action, &oldTERMaction);

    char propBuf[PROPERTY_VALUE_MAX];
    property_get("system_init.startsensorservice", propBuf, "1");
    if (strcmp(propBuf, "1") == 0) {
        SensorService::instantiate();
    }

}

static void android_server_SystemServer_startHidlServices(JNIEnv* env, jobject /* clazz */) {
    using ::android::frameworks::schedulerservice::V1_0::ISchedulingPolicyService;
    using ::android::frameworks::schedulerservice::V1_0::implementation::SchedulingPolicyService;
    using ::android::frameworks::sensorservice::V1_0::ISensorManager;
    using ::android::frameworks::sensorservice::V1_0::implementation::SensorManager;
    using ::android::hardware::configureRpcThreadpool;

    status_t err;

    configureRpcThreadpool(5, false /* callerWillJoin */);

    JavaVM *vm;
    LOG_ALWAYS_FATAL_IF(env->GetJavaVM(&vm) != JNI_OK, "Cannot get Java VM");

    sp<ISensorManager> sensorService = new SensorManager(vm);
    err = sensorService->registerAsService();
    ALOGE_IF(err != OK, "Cannot register %s: %d", ISensorManager::descriptor, err);

    sp<ISchedulingPolicyService> schedulingService = new SchedulingPolicyService();
    err = schedulingService->registerAsService();
    ALOGE_IF(err != OK, "Cannot register %s: %d", ISchedulingPolicyService::descriptor, err);
}

/*
 * JNI registration.
 */
static const JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "startSensorService", "()V", (void*) android_server_SystemServer_startSensorService },
    { "startHidlServices", "()V", (void*) android_server_SystemServer_startHidlServices },
};

int register_android_server_SystemServer(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, "com/android/server/SystemServer",
            gMethods, NELEM(gMethods));
}

}; // namespace android
