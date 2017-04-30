/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include <jni.h>
#include <JNIHelp.h>
#include <linux/netlink.h>
#include <sys/socket.h>
#include <android-base/unique_fd.h>
#include <android/hardware/tetheroffload/config/1.0/IOffloadConfig.h>

#define LOG_TAG "OffloadController"
#include <utils/Log.h>

namespace android {

namespace {

inline const sockaddr * asSockaddr(const sockaddr_nl *nladdr) {
    return reinterpret_cast<const sockaddr *>(nladdr);
}

int makeConntrackSocket(unsigned groups) {
    base::unique_fd s(socket(AF_NETLINK, SOCK_DGRAM, NETLINK_NETFILTER));
    if (s.get() < 0) return -errno;

    const struct sockaddr_nl bind_addr = {
        .nl_family = AF_NETLINK,
        .nl_pad = 0,
        .nl_pid = 0,
        .nl_groups = groups,
    };
    if (bind(s.get(), asSockaddr(&bind_addr), sizeof(bind_addr)) != 0) {
        return -errno;
    }

    const struct sockaddr_nl kernel_addr = {
        .nl_family = AF_NETLINK,
        .nl_pad = 0,
        .nl_pid = 0,
        .nl_groups = groups,
    };
    if (connect(s.get(), asSockaddr(&kernel_addr), sizeof(kernel_addr)) != 0) {
        return -errno;
    }

    return s.release();
}

}  // namespace

static jboolean android_server_connectivity_tethering_OffloadController_configOffload(
        JNIEnv* /* env */) {
#if 0
interface IOffloadConfig {
   * @return success true if successful, false otherwise
   * @return errMsg a human readable string if eror has occured.
setHandles(handle fd1, handle fd2) generates (bool success, string errMsg);
#endif
    // fd1   A file descriptor bound to the following netlink groups
    //       (NF_NETLINK_CONNTRACK_NEW | NF_NETLINK_CONNTRACK_DESTROY).
    // fd2   A file descriptor bound to the following netlink groups
    //       (NF_NETLINK_CONNTRACK_UPDATE | NF_NETLINK_CONNTRACK_DESTROY).
    base::unique_fd
            fd1(makeConntrackSocket(0)),
            fd2(makeConntrackSocket(0));
    if (fd1.get() < 0 || fd2.get() < 0) {
        ALOGE("Unable to create conntrack handles");
        return false;
    }
    // Connect to the kernel? (or close)
    // call setHandles or return false (and close)
    return false;
}

/*
 * JNI registration.
 */
static const JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "configOffload", "()Z",
      (void*) android_server_connectivity_tethering_OffloadController_configOffload },
};

int register_android_server_connectivity_tethering_OffloadController(JNIEnv* env) {
    return jniRegisterNativeMethods(env,
            "com/android/server/connectivity/tethering/OffloadController",
            gMethods, NELEM(gMethods));
}

}; // namespace android
