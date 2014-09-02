LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := signed_release

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_CERTIFICATE := $(LOCAL_PATH)/../../certs/key_b

include $(FrameworkServicesTests_BUILD_PACKAGE)
