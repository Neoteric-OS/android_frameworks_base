LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	DrmConstraints.cpp \
	DrmConvertedStatus.cpp \
	DrmEngineBase.cpp \
	DrmInfo.cpp \
	DrmInfoRequest.cpp \
	DrmInfoStatus.cpp \
	DrmRights.cpp \
	DrmSupportInfo.cpp \
	IDrmIOService.cpp \
	IDrmManagerService.cpp \
	IDrmServiceListener.cpp \
	InfoEvent.cpp \
	ReadWriteUtils.cpp

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/../../include \
	$(LOCAL_PATH)/../libdrmframework/include \
	$(LOCAL_PATH)/../libdrmframework/plugins/common/include

LOCAL_MODULE:= libdrmframeworkcommon

include $(BUILD_STATIC_LIBRARY)
