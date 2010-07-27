LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
		/src/DrmPassthruPlugIn.cpp

LOCAL_MODULE := libdrmpassthruplugin

LOCAL_STATIC_LIBRARIES := libdrmframeworkcommon

LOCAL_SHARED_LIBRARIES := \
		libutils \
		libdl

LOCAL_PRELINK_MODULE := false

LOCAL_C_INCLUDES += \
		$(DRM_TOP)/include \
		$(DRM_TOP)/plugins/passthru/include \
		$(DRM_TOP)/plugins/common/include \
		$(DRM_TOP)/../../frameworks/base/include

# Set the following flag to enable the decryption passthru flow
#LOCAL_CFLAGS += -DENABLE_PASSTHRU_DECRYPTION

PRODUCT_COPY_FILES +=  \
		 $(TARGET_OUT_SHARED_LIBRARIES)/libdrmpassthruplugin.so:system/lib/drm/plugins/native/libdrmpassthruplugin.so

include $(BUILD_SHARED_LIBRARY)
