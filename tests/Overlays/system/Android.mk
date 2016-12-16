LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_PACKAGE_NAME := OverlaysSystem

LOCAL_MODULE_TAGS := tests

LOCAL_CERTIFICATE := platform

include $(BUILD_RRO_PACKAGE)
