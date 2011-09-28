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

#include <dirent.h>
#include <signal.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/time.h>
#include <sys/stat.h>
#include <sys/types.h>
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
    static const char* SINGLE_SKIN_APK = "/vendor/overlay/framework/framework-res.apk";
    static const char* SINGLE_IDMAP_FILE =
        "/data/resource-cache/vendor@overlay@framework@framework-res.apk@idmap";
    static const char* IDMAP_BIN = "/system/bin/idmap";
    static const char* ORIG_APK = "/system/framework/framework-res.apk";
    static const char* MULTIPLE_IDMAP_PREFIX =
        "/data/resource-cache/vendor@overlay@framework@framework-res.apk@" ;
    static const char* MULTIPLE_IDMAP_SUFFIX = "@idmap";

    void exec_idmap(const char* idmap_bin, const char* orig_apk,
            const char* skin_apk, const char* idmap_file) {
        execl(idmap_bin, idmap_bin, "--path", orig_apk, skin_apk, idmap_file, (char*)NULL);
        ALOGE("execl(%s) failed: %s\n", idmap_bin, strerror(errno));
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

    int idmap(const char* idmap_bin, const char* orig_apk,
            const char* skin_apk, const char* idmap_file) {
        int retval = -1;

        pid_t pid = fork();
        if (pid == 0) {
            // child
            exec_idmap(idmap_bin, orig_apk, skin_apk, idmap_file);
            exit(1);
        } else {
            // parent
            retval = wait_idmap(pid);
        }
        return retval;
    }

    int verify_system_idmap_file_is_up_to_date() {
        if (getuid() != AID_SYSTEM || getgid() != AID_SYSTEM) {
            // not user system -> nothing we can do
            ALOGE("Not user system: uid=%d, gid=%d\n", getuid(), getgid());
            return -1;
        }

        struct stat st;

        if (stat(SINGLE_SKIN_APK, &st) == -1) {
            if (errno == ENOENT) { // No overlay file. Do not create any idmap files.
                return 0;
            }
            ALOGE("Failed to stat %s: %s\n", SINGLE_SKIN_APK, strerror(errno));
            return -1;
        }
        if (S_ISREG(st.st_mode)) {
            // A single overlay file. Create a single idmap file.
            (void)idmap(IDMAP_BIN, ORIG_APK, SINGLE_SKIN_APK, SINGLE_IDMAP_FILE);
        } else if (S_ISDIR(st.st_mode)) {
            // Several overlay files. SINGLE_SKIN_APK is a directory which contains all
            // overlay files for framework-res.apk. Create an idmap for each overlay file.
            DIR* dir = NULL;
            struct dirent* dirent;
            char skin_apk[PATH_MAX + 1];
            char idmap_file[PATH_MAX + 1];

            skin_apk[PATH_MAX] = '\0';
            idmap_file[PATH_MAX] = '\0';

            if ((dir = opendir(SINGLE_SKIN_APK)) == NULL) {
                ALOGE("Failed to opendir %s: %s\n", SINGLE_SKIN_APK, strerror(errno));
                return -1;
            }
            while ((dirent = readdir(dir)) != NULL) {
                struct stat st;
                snprintf(skin_apk, PATH_MAX, "%s/%s", SINGLE_SKIN_APK, dirent->d_name);
                if (stat(skin_apk, &st) < 0) { // stat will follow symlink
                    continue;
                }
                if (!S_ISREG(st.st_mode)) {
                    continue;
                }
                snprintf(skin_apk, PATH_MAX, "%s/%s", SINGLE_SKIN_APK, dirent->d_name);
                snprintf(idmap_file, PATH_MAX, "%s%s%s",
                        MULTIPLE_IDMAP_PREFIX, dirent->d_name, MULTIPLE_IDMAP_SUFFIX);
                (void)idmap(IDMAP_BIN, ORIG_APK, skin_apk, idmap_file);
            }
            closedir(dir);
        }

        return 0;
    }
}

extern "C" status_t system_init()
{
    ALOGI("Entered system_init()");

    if (verify_system_idmap_file_is_up_to_date() < 0) {
        ALOGI("System server: failed to verify idmap file(s) for framework-res.apk.\n");
    }

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
