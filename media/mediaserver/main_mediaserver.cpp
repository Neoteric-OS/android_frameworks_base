/*
**
** Copyright 2008, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

// System headers required for setgroups, etc.
#include <sys/types.h>
#include <unistd.h>
#include <grp.h>
#include <sys/prctl.h>
#include <linux/capability.h>
#include <string.h>

#define LOG_TAG "mediaserver"

#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <binder/IServiceManager.h>
#include <utils/Log.h>

#include <AudioFlinger.h>
#include <CameraService.h>
#include <MediaPlayerService.h>
#include <AudioPolicyService.h>
#include <private/android_filesystem_config.h>

using namespace android;

static void setup_capabilities()
{
    struct __user_cap_header_struct header;
    struct __user_cap_data_struct data;
    struct __user_cap_data_struct data_backup;

    header.version = _LINUX_CAPABILITY_VERSION;
    header.pid = getpid();

    if (capget(&header, &data_backup)) {
        LOGE("capget failed : %s\n", strerror(errno));
    }

    // Request not clear capabilities when dropping root
    // This has the following effect:
    //   A thread's effective capability set is always cleared when such a credential
    //   change is made, regardless of the setting of the "keep capabilities" flag.
    if (prctl(PR_SET_KEEPCAPS, 1, 0, 0, 0)) {
        LOGE("PR_SET_KEEPCAPS failed for service : %s\n", strerror(errno));
    }

    // Request back capabilities we had
    data.effective = data_backup.effective;
    data.permitted = data_backup.permitted;
    data.inheritable = data_backup.inheritable;

    if (capset(&header, &data)) {
        LOGE("capset backup failed : %s\n", strerror(errno));
    }

    // switch user to "media"; this may fail if we are not root or "media"
    if (setuid(AID_MEDIA)) {
        LOGE("setuid failed : %s\n", strerror(errno));
    }

    if (data_backup.inheritable) {
        // Setup the expected final capabilities
        data.effective = data.permitted = data.inheritable = data_backup.inheritable;
        if (capset(&header, &data)) {
            LOGE("capset failed : %s\n", strerror(errno));
        }
    }
}

int main(int argc, char** argv)
{
    setup_capabilities();
    sp<ProcessState> proc(ProcessState::self());
    sp<IServiceManager> sm = defaultServiceManager();
    LOGI("ServiceManager: %p", sm.get());
    AudioFlinger::instantiate();
    MediaPlayerService::instantiate();
    CameraService::instantiate();
    AudioPolicyService::instantiate();
    ProcessState::self()->startThreadPool();
    IPCThreadState::self()->joinThreadPool();
}
