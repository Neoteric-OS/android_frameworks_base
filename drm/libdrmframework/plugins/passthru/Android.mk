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
		$(TOP)/frameworks/base/drm/libdrmframework/include \
		$(TOP)/frameworks/base/drm/libdrmframework/plugins/passthru/include \
		$(TOP)/frameworks/base/drm/libdrmframework/plugins/common/include \
		$(TOP)/frameworks/base/include

# Set the following flag to enable the decryption passthru flow
#LOCAL_CFLAGS += -DENABLE_PASSTHRU_DECRYPTION

PRODUCT_COPY_FILES +=  \
		 $(TARGET_OUT_SHARED_LIBRARIES)/libdrmpassthruplugin.so:system/lib/drm/plugins/native/libdrmpassthruplugin.so

include $(BUILD_SHARED_LIBRARY)
