LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := tests

# Include all test java files.
LOCAL_SRC_FILES := \
	$(call all-java-files-under, src)

LOCAL_JAVA_LIBRARIES := android.test.runner
LOCAL_PACKAGE_NAME := NotificationStressTests

LOCAL_STATIC_JAVA_LIBRARIES := \
    junit \
    legacy-android-test \
    ub-uiautomator

LOCAL_IS_INSTRUMENTATION_TEST := true

include $(BUILD_PACKAGE)
include $(BUILD_AUTOGEN_TEST_CONFIG)

include $(call all-makefiles-under,$(LOCAL_PATH))
