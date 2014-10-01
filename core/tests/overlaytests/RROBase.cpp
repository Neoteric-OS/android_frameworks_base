/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
#include <errno.h>
#include <fcntl.h>
#include <ftw.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include <cutils/properties.h>

#include "RROBase.h"

const String8 RROBase::PATH_ROOT_DIR = String8("/data/nativetest/rro_tests");

const String8 RROBase::PATH_TARGET_APK =
PATH_ROOT_DIR.appendPathCopy("data/rro_tests_target/rro_tests_target.apk");

const String8 RROBase::PATH_APP_OVERLAY_1_APK =
PATH_ROOT_DIR.appendPathCopy("data/rro_tests_app_overlay_1/rro_tests_app_overlay_1.apk");

const String8 RROBase::PATH_APP_OVERLAY_2_APK =
PATH_ROOT_DIR.appendPathCopy("data/rro_tests_app_overlay_2/rro_tests_app_overlay_2.apk");

const String8 RROBase::PATH_ANDROID_APK = String8("/system/framework/framework-res.apk");

const String8 RROBase::PATH_SYSTEM_OVERLAY_1_APK =
PATH_ROOT_DIR.appendPathCopy("data/rro_tests_system_overlay_1/rro_tests_system_overlay_1.apk");

const String8 RROBase::PATH_SYSTEM_OVERLAY_2_APK =
PATH_ROOT_DIR.appendPathCopy("data/rro_tests_system_overlay_2/rro_tests_system_overlay_2.apk");

status_t RROBase::cp(const String8& src, const String8& dest) {
    char buf[1024];
    int s = 0, d = 0;
    ssize_t r;
    status_t retval = UNKNOWN_ERROR;

    if ((s = open(src.string(), O_RDONLY)) < 0) {
        goto bail;
    }

    if (mkdir_p(dest.getPathDir()) != NO_ERROR) {
        goto bail;
    }

    if ((d = open(dest.string(), O_RDWR | O_CREAT | O_TRUNC, 0644)) < 0) {
        goto bail;
    }

    r = read(s, buf, sizeof(buf));
    while (r != 0) {
        if (r < 0) {
            goto bail;
        }
        if (write(d, buf, r) < 0) {
            goto bail;
        }
        r = read(s, buf, sizeof(buf));
    }

    retval = NO_ERROR;
bail:
    close(d);
    close(s);
    return retval;
}

status_t RROBase::mkdir_p(const String8& path) {
    if (path == "/") {
        return NO_ERROR;
    }

    struct stat st;
    const String8 parent = path.getPathDir();
    if (stat(parent.string(), &st) < 0 && mkdir_p(parent) != NO_ERROR) {
        return UNKNOWN_ERROR;
    }
    if (stat(path.string(), &st) < 0 && mkdir(path.string(), 0755) < 0) {
        return UNKNOWN_ERROR;
    }
    return NO_ERROR;
}

static int nftw_cb(const char *path, const struct stat *, int, struct FTW *) {
    return remove(path);
}

status_t RROBase::rm_rf(const String8& path) {
    return nftw(path.string(), nftw_cb, 16, FTW_DEPTH) < 0 ?
        UNKNOWN_ERROR : NO_ERROR;
}

status_t RROBase::exec(const char *argv[], String8 *out) {
    pid_t pid;
    int pipefd[2] = { 0, 0 };
    int retval = UNKNOWN_ERROR;

    if (pipe(pipefd) < 0) {
        goto bail;
    }

    switch (pid = fork()) {
        case -1: {
            goto bail;
        }
        case 0: // child
        {
            close(pipefd[0]);
            dup2(pipefd[1], fileno(stdout));
            execv(argv[0], (char * const *)argv);
            exit(1);
        }
        default:
        {
            ssize_t r;
            char buf[1024];
            int status;

            close(pipefd[1]);
            waitpid(pid, &status, 0);
            if (!WIFEXITED(status) || WEXITSTATUS(status) != 0) {
                goto bail;
            }

            r = read(pipefd[0], buf, sizeof(buf));
            while (r != 0) {
                if (r < 0) {
                    goto bail;
                }
                if (out) {
                    out->append(buf, r);
                }
                r = read(pipefd[0], buf, sizeof(buf));
            }
        }
    }

    retval = NO_ERROR;
bail:
    close(pipefd[0]);
    close(pipefd[1]);
    return retval;
}

status_t RROBase::execInstrumentation(int whichSetup) {
    const char *argv[] = { "/system/bin/am", "instrument", "-r", "-w",
        "-e", "setup", String8::format("%d", whichSetup).string(),
        "com.android.rrotests/com.android.rrotests.TestRunner", NULL };
    String8 output;
    if (exec(argv, &output) != NO_ERROR) {
        return UNKNOWN_ERROR;
    }
    // A successful instrumentation run ends with /^OK (x tests)$/.
    // Check logcat -s 'TestRunner:*' output for details on what test cases failed.
    return output.find("\nOK (") < 0 ? UNKNOWN_ERROR : NO_ERROR;
}

#define SYSPROP_KEY "dev.bootcomplete"
status_t RROBase::stopDevice() {
    static const char *argv[] = { "/system/bin/stop", NULL };

    if (exec(argv) != NO_ERROR) {
        return UNKNOWN_ERROR;
    }
    usleep(500 * 1000);
    if (property_set(SYSPROP_KEY, "0") < 0) {
        return UNKNOWN_ERROR;
    }
    return NO_ERROR;
}

status_t RROBase::startDevice() {
    static const char *argv[] = { "/system/bin/start", NULL };
    int32_t sysprop = 0;

    if (exec(argv) != NO_ERROR) {
        return UNKNOWN_ERROR;
    }
    while (sysprop != 1) {
        usleep(1 * 1000 * 1000);
        sysprop = property_get_int32(SYSPROP_KEY, 0);
    }
    return NO_ERROR;
}

status_t RROBase::readFile(const String8& path, String8& out) {
    int fd = 0;
    int retval = UNKNOWN_ERROR;
    ssize_t r;
    char buf[1024];

    if ((fd = open(path.string(), O_RDONLY)) < 0) {
        goto bail;
    }

    r = read(fd, buf, sizeof(buf));
    while (r != 0) {
        if (r < 0) {
            goto bail;
        }
        out.append(buf, r);
        r = read(fd, buf, sizeof(buf));
    }

    retval = NO_ERROR;
bail:
    close(fd);
    return retval;
}
