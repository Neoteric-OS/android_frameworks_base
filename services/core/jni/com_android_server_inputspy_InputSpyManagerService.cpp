#include <jni.h>
#include <cerrno>
#include <string>
#include <list>
#include <android/log.h>
// defines O_RDWR
#include <fcntl.h>
// defines close(fd)
#include <unistd.h>
// defines NELEM({...})
#include <nativehelper/JNIHelp.h>
#include <linux/input.h>


#define DEVICE_PATH_TOUCHSCREEN "/dev/input/event2"
#define DEVICE_PATH_POWER_KEY "/dev/input/event13"
#define TAG "InputSpyManagerService-JNI"


// jni静态注册
/*extern "C" jstring
Java_com_android_server_keepalive_KeepAliveManagerService_onResumeNative(JNIEnv *env, jclass thiz, jlong value) {
    // 进行本地处理，生成返回值
    std::string hello = "Hello from C++";
    jstring result = env->NewStringUTF(hello.c_str());
    return result;
}*/


void log_d(const char *msg) {
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s", msg);
}

void log_d(const std::string &msg) {
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s", msg.c_str());
}

void log_e(const char *msg) {
    __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", msg);
}

void log_e(const std::string &msg) {
    __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", msg.c_str());
}

static int injectToPowerKey() {
    // Open the input device file
    int fd = open(DEVICE_PATH_POWER_KEY, O_RDWR);
    if (fd == -1) {
        std::string reason = strerror(errno);
        log_e("Failed to open file, reason = " + reason);
        return 1;
    }

    input_event event1{}, event2{}, event3{}, event4{};
    event1.type = EV_KEY;
    event1.code = KEY_POWER;
    event1.value = 1;

    event2.type = EV_SYN;
    event2.code = SYN_REPORT;
    event2.value = 0;

    event3.type = EV_KEY;
    event3.code = KEY_POWER;
    event3.value = 0;

    event4.type = EV_SYN;
    event4.code = SYN_REPORT;
    event4.value = 0;

    std::list<input_event> events = {event1, event2, event3, event4};

    // Write the input event to the device
    for (const input_event &event: events) {
        if (write(fd, &event, sizeof(event)) == -1) {
            log_e("Error when writing input event!");
            close(fd);
            return 1;
        }
    }

    // Close the input device file
    close(fd);

    return 0;
}

static void inject(){

}

static void android_server_inputspy_InputSpyManagerService_nativeTest(JNIEnv *, jobject) {
    injectToPowerKey();
}


// jni动态注册
namespace android {

    static const JNINativeMethod sMethods[] = {
            /* name, signature, funcPtr */
            {
                    "nativeTest",
                    "()V",
                    (void *) android_server_inputspy_InputSpyManagerService_nativeTest
            },
    };

    int register_android_server_inputspy_InputSpyManagerService(JNIEnv *env) {
        return jniRegisterNativeMethods(
        env,
        "com/android/server/inputspy/InputSpyManagerService",
        sMethods,
        NELEM(sMethods)
        );
    }

    jint JNI_OnLoad(JavaVM *vm, void * /* reserved */) {
        JNIEnv *env = NULL;
        jint result = -1;

        if (vm->GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK) {
            log_e("ERROR: GetEnv failed");
            goto bail;
        }
        assert(env != NULL);

        if (register_android_server_inputspy_InputSpyManagerService(env) < 0) {
            log_e("ERROR: InputSpyManagerService native registration failed");
            goto bail;
        }

        /* success -- return valid version number */
        result = JNI_VERSION_1_4;

        bail:
        return result;
    }
}/* namespace android*/