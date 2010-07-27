LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

# Set up the DRM variables
include $(LOCAL_PATH)/Config.mk

LOCAL_SRC_FILES:= \
	DrmManagerClientImpl.cpp \
	DrmManagerClient.cpp

LOCAL_MODULE:= libdrmframework

LOCAL_SHARED_LIBRARIES := \
	libutils \
	libbinder \
	libdl

LOCAL_STATIC_LIBRARIES := \
	libdrmframeworkcommon

LOCAL_C_INCLUDES += \
	$(DRM_TOP)/include \
	$(DRM_TOP)/../../include

LOCAL_PRELINK_MODULE := false

include $(BUILD_SHARED_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))
