LOCAL_PATH:= $(call my-dir)

# 32-bit app_process
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	app_main.cpp

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	liblog \
	libbinder \
	libandroid_runtime

LOCAL_MODULE:= app_process
LOCAL_MULTILIB := both
LOCAL_MODULE_STEM_32 := app_process32
LOCAL_MODULE_STEM_64 := app_process64
include $(BUILD_EXECUTABLE)

SYMLINK := $(addprefix $(TARGET_OUT)/bin/, $(LOCAL_MODULE))

# create app_process to link to the one used for primary zygote app_process64
ifneq ($(TARGET_PREFER_32_BIT_APPS),true)
  $(SYMLINK): APP_PROCESS_BINARY := $(LOCAL_MODULE_STEM_64)
else
  $(SYMLINK): APP_PROCESS_BINARY := $(LOCAL_MODULE_STEM_32)
endif

$(SYMLINK): $(LOCAL_INSTALLED_MODULE) $(LOCAL_PATH)/Android.mk
	@echo "Symlink: $@ -> $(APP_PROCESS_BINARY)"
	@mkdir -p $(dir $@)
	@rm -rf $@
	$(hide) ln -sf $(APP_PROCESS_BINARY) $@

# We need this so that the installed files could be picked up based on the
# local module name
ALL_MODULES.$(LOCAL_MODULE).INSTALLED := \
    $(ALL_MODULES.$(LOCAL_MODULE).INSTALLED) $(SYMLINK)

# Build a variant of app_process binary linked with ASan runtime.
# ARM-only at the moment.
ifeq ($(TARGET_ARCH),arm)

include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	app_main.cpp

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	liblog \
	libbinder \
	libandroid_runtime

LOCAL_MODULE := app_process__asan
LOCAL_MODULE_TAGS := eng
LOCAL_MODULE_PATH := $(TARGET_OUT_EXECUTABLES)/asan
LOCAL_MODULE_STEM := app_process
LOCAL_ADDRESS_SANITIZER := true

include $(BUILD_EXECUTABLE)

endif # ifeq($(TARGET_ARCH),arm)
