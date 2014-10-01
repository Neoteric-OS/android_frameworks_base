LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_MODULE := rro_tests

LOCAL_SRC_FILES := \
    RROBase.cpp \
    RRONativeTests.cpp \
    RROJavaTests.cpp

LOCAL_SHARED_LIBRARIES := \
    libandroidfw \
    libutils \
    libcutils

include $(BUILD_NATIVE_TEST)

include $(call all-makefiles-under, $(LOCAL_PATH)/data)
