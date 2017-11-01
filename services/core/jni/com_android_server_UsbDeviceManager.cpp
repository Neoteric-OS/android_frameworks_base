/*
 * Copyright (C) 2010 The Android Open Source Project
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

#define LOG_TAG "UsbDeviceManagerJNI"
#include "utils/Log.h"

#include "jni.h"
#include <nativehelper/JNIHelp.h>
#include "android_runtime/AndroidRuntime.h"
#include "android_runtime/Log.h"

#include <stdio.h>
#include <asm/byteorder.h>
#include <sys/epoll.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <linux/usb/f_accessory.h>

#define DRIVER_NAME "/dev/usb_accessory"

namespace android
{

static struct parcel_file_descriptor_offsets_t
{
    jclass mClass;
    jmethodID mConstructor;
} gParcelFileDescriptorOffsets;

static jmethodID method_usbStateUpdate;

static void set_accessory_string(JNIEnv *env, int fd, int cmd, jobjectArray strArray, int index)
{
    char buffer[256];

    buffer[0] = 0;
    ioctl(fd, cmd, buffer);
    if (buffer[0]) {
        jstring obj = env->NewStringUTF(buffer);
        env->SetObjectArrayElement(strArray, index, obj);
        env->DeleteLocalRef(obj);
    }
}


static jobjectArray android_server_UsbDeviceManager_getAccessoryStrings(JNIEnv *env,
                                                                        jobject /* thiz */)
{
    int fd = open(DRIVER_NAME, O_RDWR);
    if (fd < 0) {
        ALOGE("could not open %s", DRIVER_NAME);
        return NULL;
    }
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray strArray = env->NewObjectArray(6, stringClass, NULL);
    if (!strArray) goto out;
    set_accessory_string(env, fd, ACCESSORY_GET_STRING_MANUFACTURER, strArray, 0);
    set_accessory_string(env, fd, ACCESSORY_GET_STRING_MODEL, strArray, 1);
    set_accessory_string(env, fd, ACCESSORY_GET_STRING_DESCRIPTION, strArray, 2);
    set_accessory_string(env, fd, ACCESSORY_GET_STRING_VERSION, strArray, 3);
    set_accessory_string(env, fd, ACCESSORY_GET_STRING_URI, strArray, 4);
    set_accessory_string(env, fd, ACCESSORY_GET_STRING_SERIAL, strArray, 5);

out:
    close(fd);
    return strArray;
}

static jobject android_server_UsbDeviceManager_openAccessory(JNIEnv *env, jobject /* thiz */)
{
    int fd = open(DRIVER_NAME, O_RDWR);
    if (fd < 0) {
        ALOGE("could not open %s", DRIVER_NAME);
        return NULL;
    }
    jobject fileDescriptor = jniCreateFileDescriptor(env, fd);
    if (fileDescriptor == NULL) {
        return NULL;
    }
    return env->NewObject(gParcelFileDescriptorOffsets.mClass,
        gParcelFileDescriptorOffsets.mConstructor, fileDescriptor);
}

static jboolean android_server_UsbDeviceManager_isStartRequested(JNIEnv* /* env */,
                                                                 jobject /* thiz */)
{
    int fd = open(DRIVER_NAME, O_RDWR);
    if (fd < 0) {
        ALOGE("could not open %s", DRIVER_NAME);
        return false;
    }
    int result = ioctl(fd, ACCESSORY_IS_START_REQUESTED);
    close(fd);
    return (result == 1);
}

static jint android_server_UsbDeviceManager_getAudioMode(JNIEnv* /* env */, jobject /* thiz */)
{
    int fd = open(DRIVER_NAME, O_RDWR);
    if (fd < 0) {
        ALOGE("could not open %s", DRIVER_NAME);
        return false;
    }
    int result = ioctl(fd, ACCESSORY_GET_AUDIO_MODE);
    close(fd);
    return result;
}

static void  android_server_UsbDeviceManager_monitorUsbGadget(JNIEnv* env,
                                                             jobject thiz,
                                                             jstring path)
{
    int notifyfd = 0, epollfd = 0;
    const char *devicePath = env->GetStringUTFChars(path, NULL);
    struct epoll_event ev;

    if (!devicePath) {
        ALOGE("Device path is null");
        goto end;
    }

    notifyfd = open(devicePath, O_RDONLY);
    if (notifyfd < 0) {
        ALOGE("Unable to open state path %s errno=%d", devicePath, errno);
        goto end;
    }

    ev.events = EPOLLPRI|EPOLLERR|EPOLLET;
    ev.data.ptr = NULL;

    epollfd = epoll_create(1);
    if (epollfd == -1) {
        ALOGE("epoll_create failed; errno=%d", errno);
        goto end;
    }

    if (epoll_ctl(epollfd, EPOLL_CTL_ADD, notifyfd, &ev) == -1) {
        ALOGE("epoll_ctl failed; errno=%d", errno);
        goto end;
    }

    while (true) {
        struct epoll_event events[1];
        if (epoll_wait(epollfd, events, 1, -1) == -1) {
            if (errno == EINTR)
                continue;
            ALOGE("usb epoll_wait failed; errno=%d", errno);
            break;
        }
        FILE *fp = fopen(devicePath, "r");
        if (fp != NULL) {
            char state[20];
            if (fgets(state, sizeof(state), fp) != NULL) {
                jstring usbState = env->NewStringUTF(state);
                env->CallVoidMethod(thiz, method_usbStateUpdate, usbState);
                env->DeleteLocalRef(usbState);
            }
            fclose(fp);
        }
    }

end:
   close(epollfd);
   close(notifyfd);
   env->ReleaseStringUTFChars(path, devicePath);
   return;
}

static const JNINativeMethod method_table[] = {
    { "nativeGetAccessoryStrings",  "()[Ljava/lang/String;",
                                    (void*)android_server_UsbDeviceManager_getAccessoryStrings },
    { "nativeOpenAccessory",        "()Landroid/os/ParcelFileDescriptor;",
                                    (void*)android_server_UsbDeviceManager_openAccessory },
    { "nativeIsStartRequested",     "()Z",
                                    (void*)android_server_UsbDeviceManager_isStartRequested },
    { "nativeGetAudioMode",         "()I",
                                    (void*)android_server_UsbDeviceManager_getAudioMode },
    { "nativeMonitorUsbGadget",      "(Ljava/lang/String;)V",
                                    (void*)android_server_UsbDeviceManager_monitorUsbGadget },
};

int register_android_server_UsbDeviceManager(JNIEnv *env)
{
    jclass clazz = env->FindClass("com/android/server/usb/UsbDeviceManager");
    if (clazz == NULL) {
        ALOGE("Can't find com/android/server/usb/UsbDeviceManager");
        return -1;
    }

    method_usbStateUpdate = env->GetMethodID(clazz, "usbStateUpdate",
            "(Ljava/lang/String;)V");
    if (method_usbStateUpdate == NULL) {
        ALOGE("Can't find usbStateUpdate");
        return -1;
    }

    clazz = env->FindClass("android/os/ParcelFileDescriptor");
    LOG_FATAL_IF(clazz == NULL, "Unable to find class android.os.ParcelFileDescriptor");
    gParcelFileDescriptorOffsets.mClass = (jclass) env->NewGlobalRef(clazz);
    gParcelFileDescriptorOffsets.mConstructor = env->GetMethodID(clazz, "<init>", "(Ljava/io/FileDescriptor;)V");
    LOG_FATAL_IF(gParcelFileDescriptorOffsets.mConstructor == NULL,
                 "Unable to find constructor for android.os.ParcelFileDescriptor");

    return jniRegisterNativeMethods(env, "com/android/server/usb/UsbDeviceManager",
            method_table, NELEM(method_table));
}

};
