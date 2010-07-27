LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
		android_drm_DrmManagerClient.cpp

LOCAL_MODULE:= libdrmframework_jni

LOCAL_SHARED_LIBRARIES :=  \
		libdrmframework \
		libutils \
		libandroid_runtime \
		libnativehelper \
		libbinder \
		libdl

LOCAL_STATIC_LIBRARIES :=

LOCAL_C_INCLUDES += \
		$(JNI_H_INCLUDE) \
		$(LOCAL_PATH)/../libdrmframework/include \
		$(LOCAL_PATH)/../libdrmframework/plugins/common/include \
		$(LOCAL_PATH)/../../include

LOCAL_PRELINK_MODULE := false

include $(BUILD_SHARED_LIBRARY)

