#include <jni.h>
// get latest error
#include <cerrno>
#include <string>
#include <list>
// send logs to android logcat
#include <android/log.h>
// defines O_RDWR
#include <fcntl.h>
// defines close(fd)
#include <unistd.h>
// defines NELEM({...})
#include <nativehelper/JNIHelp.h>
#include <linux/input.h>
// read file content into stream
#include <fstream>
// thread wait
#include <thread>
// get current time
#include <chrono>


#define DEVICE_PATH_TOUCHSCREEN "/dev/input/event2"
#define DEVICE_PATH_POWER_KEY "/dev/input/event13"
#define RECORDS_FILE_PATH "/data/data/input_spy_manager_service_data/records.txt"
#define TAG "InputSpyManagerService-JNI"


void log_v(const char *msg) {
    __android_log_print(ANDROID_LOG_VERBOSE, TAG, "%s", msg);
}

void log_v(const std::string &msg) {
    __android_log_print(ANDROID_LOG_VERBOSE, TAG, "%s", msg.c_str());
}

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

long long getCurrentMicroSeconds() {
     // get current time
     auto now = std::chrono::high_resolution_clock::now();
     auto timeSinceEpoch = now.time_since_epoch();
     // convert to micro seconds
     auto microseconds_since_epoch = std::chrono::duration_cast<std::chrono::microseconds>(
             timeSinceEpoch).count();
     return microseconds_since_epoch;
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

    std::list <input_event> events = {event1, event2, event3, event4};

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


struct record_unit {
    long long microsecond;
    // events which happened at the same [microsecond].
    std::list<input_event> events;
};

std::list<record_unit> readRecords() {
    std::list<record_unit> records;

    // open the file

    //    Christopher Tate
    //unread,
    //        Sep 17, 2010, 10:44:03 PM
    //        to android-...@googlegroups.com
    //The system process is explicitly forbidden to open files on SD because
    //        those devices are frequently removable during runtime. If a process
    //        holds open file descriptors when the file system is unmounted, that
    //        process will be killed by the kernel. In the case of the system
    //        process, that would bring down the whole Android runtime; hence the
    //        restriction.
    //        (Applications resident on SD complicate this a bit, but that's the
    //        basic problem in a nutshell, and is the reason for the explicit policy
    //        that you're seeing in action.)
    //
    //        For your test suite, you might consider using a two-process
    //        architecture in which the system-process test code communicates via
    //        the Binder or other IPC mechanism with another process whose job is
    //        mostly to handle the file I/O.
    //
    //        --
    //        chris tate
    //        android framework engineer

    std::ifstream file(RECORDS_FILE_PATH);
    if (!file.is_open()) {
        std::string reason = strerror(errno);
        log_e("Failed to open records.txt, reason = " + reason);
        return {};
    }
    log_d("file is opened.");

    // read file content line by line
    std::string line;
    while (std::getline(file, line)) {
        if (line[0] != '[') { // invalid line
            continue;
        }

        // read event time
        std::string rawTime = line.substr(6, 10);
        double time = std::stod(rawTime);
        auto microsecond = static_cast<long long>(time * 1000 * 1000);
        log_v("microsecond = " + std::to_string(microsecond));

        // read event type
        std::string rawType = line.substr(37, 4);
        int type = std::stoi(rawType, nullptr, 16);
        log_v("type = " + std::to_string(type));

        // read event code
        std::string rawCode = line.substr(42, 4);
        int code = std::stoi(rawCode, nullptr, 16);
        log_v("code = " + std::to_string(code));

        // read event value
        std::string rawValue = line.substr(47, 8);
        long long value = std::stoll(rawValue, nullptr, 16);
        log_v("value = " + std::to_string(value));

        // create input_event
        input_event ev{};
        ev.type = type;
        ev.code = code;
        ev.value = static_cast<__s32>(value);

        if (!records.empty() && records.end()->microsecond == microsecond) {
            // append to previous record
            records.end()->events.push_back(ev);
        } else {
            // create new record
            record_unit record{};
            record.microsecond = microsecond;
            record.events.push_back(ev);
            records.push_back(record);
        }
    }
    file.close();

    log_d("records size = " + std::to_string(records.size()));
    return records;
}

int injectEvents(int fd, const std::list<input_event> &events) {
    for (const auto &item: events) {
        if (write(fd, &item, sizeof(item)) == -1) {
            log_e("Error when writing input event!");
            close(fd);
            return -1;
        }
    }
    return 0;
}

void replay() {
    int fd = open(DEVICE_PATH_TOUCHSCREEN, O_RDWR);
    if (fd == -1) {
        std::string reason = strerror(errno);
        log_e("Failed to open file, reason = " + reason);
        return;
    }
    std::list<record_unit> records = readRecords();
    long long playStartTime = getCurrentMicroSeconds();

    // Write the input event to the device
    record_unit firstRecord = records.front();
    for (const record_unit &record: records) {
        long long shouldPlayAt = playStartTime + record.microsecond - firstRecord.microsecond;
        long long now = getCurrentMicroSeconds();
        long long waitTime = shouldPlayAt - now;
        if (waitTime > 0) {
            // wait
            log_v("wait for " + std::to_string(waitTime) + " micro seconds");
            std::this_thread::sleep_for(std::chrono::microseconds(waitTime));
        }
        injectEvents(fd, record.events);
    }

    // Close the input device file
    close(fd);
}

static void android_server_inputspy_InputSpyManagerService_nativeTest(JNIEnv *, jobject) {
    injectToPowerKey();
}

static void android_server_inputspy_InputSpyManagerService_nativeStartPlaying(JNIEnv *, jobject) {
    replay();
}


// jni静态注册
/*extern "C" jstring
Java_android_server_inputspy_InputSpyManagerService_nativeTest(JNIEnv *env, jclass thiz) {
     injectToPowerKey();
}*/

/*extern "C" jstring
Java_android_server_inputspy_InputSpyManagerService_nativeStartPlaying(JNIEnv *env, jclass thiz) {
     replay();
}*/


// jni动态注册
namespace android {

    static const JNINativeMethod sMethods[] = {
            /* name, signature, funcPtr */
            {
                    "nativeTest",
                    "()V",
                    (void *) android_server_inputspy_InputSpyManagerService_nativeTest
            },
            {
                    "nativeStartPlaying",
                    "()V",
                    (void *) android_server_inputspy_InputSpyManagerService_nativeStartPlaying
            },
    };

    int register_android_server_inputspy_InputSpyManagerService(JNIEnv *env) {
        log_d("register native methods of InputSpyManagerService.java");
        return jniRegisterNativeMethods(
                env,
                "com/android/server/inputspy/InputSpyManagerService",
                sMethods,
                NELEM(sMethods)
        );
    }

    jint JNI_OnLoad(JavaVM *vm, void * /* reserved */) {
        log_d("JNI_OnLoad is invoked");
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