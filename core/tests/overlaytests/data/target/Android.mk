LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_PACKAGE_NAME := rro_tests_target

LOCAL_DEX_PREOPT := false

LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_NATIVE_TESTS)/rro_tests/data

LOCAL_JAVA_LIBRARIES := android.test.runner

LOCAL_SRC_FILES := $(call all-java-files-under, src)

include $(BUILD_PACKAGE)
