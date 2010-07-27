LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	main_drmioserver.cpp \
	DrmIOService.cpp

LOCAL_SHARED_LIBRARIES := \
	libutils \
	libbinder \
	libdl

LOCAL_STATIC_LIBRARIES := libdrmframeworkcommon

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/../libdrmframework/include \
	$(LOCAL_PATH)/../../include

LOCAL_MODULE:= drmioserver

include $(BUILD_EXECUTABLE)
