LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
    bootanimation_main.cpp \
    AudioPlayer.cpp \
    BootAnimation.cpp

LOCAL_CFLAGS += -DGL_GLEXT_PROTOTYPES -DEGL_EGLEXT_PROTOTYPES

LOCAL_CFLAGS += -Wall -Werror -Wunused -Wunreachable-code

LOCAL_C_INCLUDES += external/tinyalsa/include

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    liblog \
    libandroidfw \
    libutils \
    libbinder \
    libui \
    libskia \
    libEGL \
    libGLESv1_CM \
    libgui \
    libtinyalsa

LOCAL_MODULE:= bootanimation

LOCAL_REQUIRED_MODULES := bootanim.rc

ifdef TARGET_32_BIT_SURFACEFLINGER
LOCAL_32_BIT_ONLY := true
endif

include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)

LOCAL_MODULE := bootanim.rc
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT)/init

include $(BUILD_PREBUILT)
