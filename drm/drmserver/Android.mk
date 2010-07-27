LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	main_drmserver.cpp \
	DrmManager.cpp \
	DrmManagerService.cpp \
	StringTokenizer.cpp

LOCAL_SHARED_LIBRARIES := \
	libutils \
	libbinder \
	libdl

LOCAL_STATIC_LIBRARIES := libdrmframeworkcommon

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/../../include \
	$(LOCAL_PATH)/../libdrmframework/include \
	$(LOCAL_PATH)/../libdrmframework/plugins/common/include

LOCAL_MODULE:= drmserver

include $(BUILD_EXECUTABLE)
