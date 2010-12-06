LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES :=

LOCAL_SDK_VERSION := current

LOCAL_PACKAGE_NAME := overlay-FrameworksCoreTests

LOCAL_MODULE_PATH := $(TARGET_OUT)/overlay

LOCAL_AAPT_FLAGS := -o

LOCAL_EXPORT_PACKAGE_RESOURCES := true

include $(BUILD_PACKAGE)
