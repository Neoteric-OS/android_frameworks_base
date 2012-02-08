/*
 * System server main initialization.
 *
 * The system server is responsible for becoming the Binder
 * context manager, supplying the root ServiceManager object
 * through which other services can be found.
 */

#define LOG_TAG "sysproc"

#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <binder/IServiceManager.h>
#include <utils/TextOutput.h>
#include <utils/Log.h>

#include <SurfaceFlinger.h>
#include <AudioFlinger.h>
#include <CameraService.h>
#include <AudioPolicyService.h>
#include <MediaPlayerService.h>
#include <SensorService.h>

#include <android_runtime/AndroidRuntime.h>

#include <signal.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/time.h>
#include <sys/wait.h>
#include <cutils/properties.h>
#include <private/android_filesystem_config.h>

using namespace android;

namespace android {
/**
 * This class is used to kill this process when the runtime dies.
 */
class GrimReaper : public IBinder::DeathRecipient {
public:
    GrimReaper() { }

    virtual void binderDied(const wp<IBinder>& who)
    {
        ALOGI("Grim Reaper killing system_server...");
        kill(getpid(), SIGKILL);
    }
};

} // namespace android

namespace {
    void exec_idmap() {
        static const char* IDMAP_BIN = "/system/bin/idmap";
        static const char* ORIG_APK = "/system/framework/framework-res.apk";
        static const char* SKIN_APK = "/vendor/overlay/framework/framework-res.apk";
        static const char* IDMAP_FILE =
            "/data/resource-cache/vendor@overlay@framework@framework-res.apk@idmap";
        execl(IDMAP_BIN, IDMAP_BIN, "--path", ORIG_APK, SKIN_APK, IDMAP_FILE,
                (char*)NULL);
        ALOGE("execl(%s) failed: %s\n", IDMAP_BIN, strerror(errno));
    }

    int wait_idmap(pid_t pid) {
        int status;
        pid_t got_pid;

        while (1) {
            got_pid = waitpid(pid, &status, 0);
            if (got_pid == -1 && errno == EINTR) {
                continue;
            } else {
                break;
            }
        }
        if (got_pid != pid) {
            return -1;
        }
        if (WIFEXITED(status) && WEXITSTATUS(status) == 0) {
            return 0;
        } else {
            return status;
        }
    }

    int verify_system_idmap_file_is_up_to_date() {
        if (getuid() != AID_SYSTEM || getgid() != AID_SYSTEM) {
            // not user system -> nothing we can do
            return 0;
        }
        int retval = -1;
        pid_t pid = fork();
        if (pid == 0) {
            // child
            exec_idmap();
            exit(1);
        } else {
            // parent
            retval = wait_idmap(pid);
        }
        return retval;
    }
}


extern "C" status_t system_init()
{
    ALOGI("Entered system_init()");

    verify_system_idmap_file_is_up_to_date();

    sp<ProcessState> proc(ProcessState::self());

    sp<IServiceManager> sm = defaultServiceManager();
    ALOGI("ServiceManager: %p\n", sm.get());

    sp<GrimReaper> grim = new GrimReaper();
    sm->asBinder()->linkToDeath(grim, grim.get(), 0);

    char propBuf[PROPERTY_VALUE_MAX];
    property_get("system_init.startsurfaceflinger", propBuf, "1");
    if (strcmp(propBuf, "1") == 0) {
        // Start the SurfaceFlinger
        SurfaceFlinger::instantiate();
    }

    property_get("system_init.startsensorservice", propBuf, "1");
    if (strcmp(propBuf, "1") == 0) {
        // Start the sensor service
        SensorService::instantiate();
    }

    // And now start the Android runtime.  We have to do this bit
    // of nastiness because the Android runtime initialization requires
    // some of the core system services to already be started.
    // All other servers should just start the Android runtime at
    // the beginning of their processes's main(), before calling
    // the init function.
    ALOGI("System server: starting Android runtime.\n");
    AndroidRuntime* runtime = AndroidRuntime::getRuntime();

    ALOGI("System server: starting Android services.\n");
    JNIEnv* env = runtime->getJNIEnv();
    if (env == NULL) {
        return UNKNOWN_ERROR;
    }
    jclass clazz = env->FindClass("com/android/server/SystemServer");
    if (clazz == NULL) {
        return UNKNOWN_ERROR;
    }
    jmethodID methodId = env->GetStaticMethodID(clazz, "init2", "()V");
    if (methodId == NULL) {
        return UNKNOWN_ERROR;
    }
    env->CallStaticVoidMethod(clazz, methodId);

    ALOGI("System server: entering thread pool.\n");
    ProcessState::self()->startThreadPool();
    IPCThreadState::self()->joinThreadPool();
    ALOGI("System server: exiting thread pool.\n");

    return NO_ERROR;
}
