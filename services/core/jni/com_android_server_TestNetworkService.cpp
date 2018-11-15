/*
 * Copyright (C) 2011 The Android Open Source Project
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

#define LOG_NDEBUG 0

#define LOG_TAG "TestNetworkServiceJni"

#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/if.h>
#include <linux/if_tun.h>
#include <linux/route.h>
#include <linux/ipv6_route.h>
#include <netinet/in.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <log/log.h>

#include "netutils/ifc.h"

#include "jni.h"
#include <nativehelper/JNIHelp.h>

namespace android
{

static int inet4 = -1;

//------------------------------------------------------------------------------

#define SYSTEM_ERROR (-1)
#define BAD_ARGUMENT (-2)

static int create_tun_interface(const char *name)
{
    int tun = open("/dev/tun", O_RDWR | O_NONBLOCK);

    ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));

    // Allocate interface.
    ifr.ifr_flags = IFF_TUN | IFF_NO_PI;
    strncpy(ifr.ifr_name, name, IFNAMSIZ);
    if (ioctl(tun, TUNSETIFF, &ifr)) {
        ALOGE("Cannot allocate TUN: %s", strerror(errno));
        goto error;
    }

    // Activate interface.
    ifr.ifr_flags = IFF_UP;
    if (ioctl(inet4, SIOCSIFFLAGS, &ifr)) {
        ALOGE("Cannot activate %s: %s", ifr.ifr_name, strerror(errno));
        goto error;
    }

    return tun;

error:
    close(tun);
    return SYSTEM_ERROR;
}

static int reset_tun_interface(const char *name)
{
    ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));

    ifr.ifr_flags = 0;
    strncpy(ifr.ifr_name, name, IFNAMSIZ);
    if (ioctl(inet4, SIOCSIFFLAGS, &ifr) && errno != ENODEV) {
        ALOGE("Cannot reset %s: %s", name, strerror(errno));
        return SYSTEM_ERROR;
    }
    return 0;
}

//------------------------------------------------------------------------------

static void throwException(JNIEnv *env, int error, const char *message)
{
    if (error == SYSTEM_ERROR) {
        jniThrowException(env, "java/lang/IllegalStateException", message);
    } else {
        jniThrowException(env, "java/lang/IllegalArgumentException", message);
    }
}

static jint create(JNIEnv *env, jobject /* thiz */, jstring jName)
{
    const char *name = jName ? env->GetStringUTFChars(jName, NULL) : NULL;
    if (!name) {
        jniThrowNullPointerException(env, "name");
        return -1;
    }

    int tun = create_tun_interface(name);
    if (tun < 0) {
        throwException(env, tun, "Cannot create interface");
        return -1;
    }
    return tun;
}

static void reset(JNIEnv *env, jobject /* thiz */, jstring jName)
{
    const char *name = jName ? env->GetStringUTFChars(jName, NULL) : NULL;
    if (!name) {
        jniThrowNullPointerException(env, "name");
        return;
    }
    if (reset_tun_interface(name) < 0) {
        throwException(env, SYSTEM_ERROR, "Cannot reset interface");
    }
    env->ReleaseStringUTFChars(jName, name);
}

//------------------------------------------------------------------------------

static const JNINativeMethod gMethods[] = {
    {"jniCreateTun", "(Ljava/lang/String;)I", (void *)create},
    {"jniTeardownTun", "(Ljava/lang/String;)V", (void *)reset},
};

int register_android_server_TestNetworkService(JNIEnv *env)
{
    if (inet4 == -1) {
        inet4 = socket(AF_INET, SOCK_DGRAM, 0);
    }

    return jniRegisterNativeMethods(env, "com/android/server/TestNetworkService",
            gMethods, NELEM(gMethods));
}

};
