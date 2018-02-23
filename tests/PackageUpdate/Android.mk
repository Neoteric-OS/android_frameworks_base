LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

PKGUP_OUT_ROOT := $(call intermediates-dir-for,PACKAGING,PackageUpdate)

################################
# package_update_testapps:
#   create a lot of test applications

PKGUP_ZIPPED_TESTAPPS := $(PKGUP_OUT_ROOT)/testapps/testapps.zip
# PKGUP_NUMBER_OF_PACKAGES should be must be less than max number of __PACKAGE_SUFFIX
__PACKAGE_SUFFIX := 001 002 003 004 005 006 007 008 009 010 011 012 013 014 015 016 017 018 019 020 \
                    021 022 023 024 025 026 027 028 029 030 031 032 033 034 035 036 037 038 039 040 \
                    041 042 043 044 045 046 047 048 049 050 051 052 053 054 055 056 057 058 059 060 \
                    061 062 063 064 065 066 067 068 069 070 071 072 073 074 075 076 077 078 079 080 \
                    081 082 083 084 085 086 087 088 089 090 091 092 093 094 095 096 097 098 099 100

PKGUP_NUMBER_OF_PACKAGES := 50
PKGUP_MAX_APP_VERSION := 10
PKGUP_LOCAL_SDK_VERSION := 26

ALL_PACKAGE_UPDATE_TESTS :=

# $(1): pkgnum
# $(2): pkgver
define package_update_one_testapp
include $$(CLEAR_VARS)
LOCAL_PACKAGE_NAME := TestApp$(1)ver$(2)
LOCAL_MODULE_TAGS := tests
LOCAL_SDK_VERSION := $(PKGUP_LOCAL_SDK_VERSION)
LOCAL_JNI_SHARED_LIBRARIES := libTestApp$(1)ver$(2)
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/resources/testapp_template/res
LOCAL_USE_AAPT2 := true
LOCAL_USE_EMBEDDED_NATIVE_LIBS := false
manifest_xml := $$(call intermediates-dir-for,APPS,$$(LOCAL_PACKAGE_NAME))/AndroidManifest.xml
$$(manifest_xml) : $(LOCAL_PATH)/resources/testapp_template/AndroidManifest.xml
	echo "Generating $$@"
	mkdir -p $$(dir $$<)
	cat $$< | \
	    sed -e "s/%(package_name)s/com.android.tests.pkgup$(1)/g" | \
	    sed -e "s/%(version_code)s/$(2)/g" | \
	    sed -e "s/%(version_name)s/$(2)/g" | \
	    sed -e "s/%(install_loc)s/auto/g" | \
	    sed -e "s/%(min_sdk_version)s/26/g" | \
	    sed -e "s/%(target_sdk_version)s/29/g" | \
	    sed -e "s/%(app_name)s/TestApp$(1)/g" | \
	    sed -e "s/%(activity_name)s/TestApp/g"  > $$@
java_src := $$(call intermediates-dir-for,APPS,$$(LOCAL_PACKAGE_NAME))/com/android/tests/pkgup$(1)/TestApp.java
$$(java_src) : $(LOCAL_PATH)/resources/testapp_template/src/template.txt
	echo "Generating $$@"
	mkdir -p $$(dir $$<)
	cat $$< | \
	    sed -e "s/%(packagename)s/com.android.tests.pkgup$(1)/g" | \
	    sed -e "s/%(classname)s/TestApp/g" | \
	    sed -e "s/%(libname)s/TestApp$(1)ver$(2)/g" | \
	    sed -e "s/%%/%/g" > $$@
testapp$(1)ver$(2)_apk := $$(call intermediates-dir-for,APPS,$$(LOCAL_PACKAGE_NAME))/package.apk
ALL_PACKAGE_UPDATE_TESTS += $$(testapp$(1)ver$(2)_apk)
LOCAL_SOURCE_FILES_ALL_GENERATED := true
LOCAL_GENERATED_SOURCES := $$(java_src)
LOCAL_FULL_MANIFEST_FILE := $$(manifest_xml)
include $$(BUILD_PACKAGE)
include $$(CLEAR_VARS)
LOCAL_MODULE := libTestApp$(1)ver$(2)
LOCAL_MODULE_TAGS := tests
LOCAL_SDK_VERSION := $(PKGUP_LOCAL_SDK_VERSION)
LOCAL_CFLAGS := -Wall -Werror \
    -DFUNC=Java_com_android_tests_pkgup$(1)_TestApp_getLibraryVersion \
    -DVERSION=\"$(2)\"
LOCAL_SRC_FILES := resources/testapp_template/jni/template.c
testapp$(1)ver$(2)_so := $$(call intermediates-dir-for,SHARED_LIBRARIES,$$(LOCAL_MODULE))/libTestApp$(1)ver$(2).so
ALL_PACKAGE_UPDATE_TESTS += $$(testapp$(1)ver$(2)_so)
include $$(BUILD_SHARED_LIBRARY)
endef

$(foreach pkgnum,$(wordlist 1,$(PKGUP_NUMBER_OF_PACKAGES),$(__PACKAGE_SUFFIX)), \
    $(foreach pkgver,$(call int_range_list,1,$(PKGUP_MAX_APP_VERSION)), \
        $(eval $(call package_update_one_testapp,$(pkgnum),$(pkgver)))))

$(PKGUP_ZIPPED_TESTAPPS) : $(ALL_PACKAGE_UPDATE_TESTS) $(SOONG_ZIP)
	rm -rf $(dir $@)
	mkdir -p $(dir $@)
	$(foreach pkgnum,$(wordlist 1,$(PKGUP_NUMBER_OF_PACKAGES),$(__PACKAGE_SUFFIX)), \
	    $(foreach pkgver,$(call int_range_list,1,$(PKGUP_MAX_APP_VERSION)), \
	        $(eval apk_dir := $(dir $@)/tmp/com.android.tests.pkgup$(pkgnum)/ver$(pkgver)) \
	        mkdir -p $(apk_dir); \
	        cp $(testapp$(pkgnum)ver$(pkgver)_apk) $(apk_dir)/TestApp$(pkgnum).apk$(newline) \
	        $(eval libs_dir := $(apk_dir)/libs/$(TARGET_CPU_ABI)) \
	        mkdir -p $(libs_dir); \
	        cp $(testapp$(pkgnum)ver$(pkgver)_so) $(libs_dir)$(newline)))
	$(SOONG_ZIP) -d -o $@ -C $(dir $@)/tmp -D $(dir $@)/tmp
	rm -rf $(dir $@)/tmp

.PHONY : package_update_testapps
package_update_testapps : $(PKGUP_ZIPPED_TESTAPPS)

################################
PKGUP_SCRIPTS := \
    $(LOCAL_PATH)/build-image \
    $(LOCAL_PATH)/flash \
    $(LOCAL_PATH)/get_component_pathes_by_pkg.sh \
    $(LOCAL_PATH)/resources/report_template/report.tmpl \
    $(LOCAL_PATH)/testcases \
    $(LOCAL_PATH)/harnesses/tradefed/res \
    $(wildcard $(LOCAL_PATH)/*.bash) \
    $(wildcard $(LOCAL_PATH)/python/*.py)

.PHONY : package_update_harnesses
package_update_harnesses : $(PKGUP_SCRIPTS) $(HOST_OUT_EXECUTABLES)/simg2img $(HOST_OUT_EXECUTABLES)/ext2simg
	$(call copy-files-with-structure,$(PKGUP_SCRIPTS),$(dir $<),$(PKGUP_OUT_ROOT))

################################
# package_update_zip:
#   create standalone package

PKGUP_TF_SCRIPT := $(LOCAL_PATH)/harnesses/tradefed/bin/pkgup_tradefed
TF_JARS = $(HOST_OUT)/tradefed/loganalysis.jar \
    $(HOST_OUT)/tradefed/tradefed-contrib.jar \
    $(HOST_OUT)/tradefed/tradefed.jar
PKGUP_TOOLS_LIBS := $(HOST_OUT_SHARED_LIBRARIES)/libbase.so \
    $(HOST_OUT_SHARED_LIBRARIES)/libext2_com_err-host.so \
    $(HOST_OUT_SHARED_LIBRARIES)/liblog.so \
    $(HOST_OUT_SHARED_LIBRARIES)/libz-host.so \
    $(HOST_OUT_SHARED_LIBRARIES)/libc++.so \
    $(HOST_OUT_SHARED_LIBRARIES)/libext2fs-host.so \
    $(HOST_OUT_SHARED_LIBRARIES)/libsparse-host.so

PKGUP_ZIP := $(PKGUP_OUT_ROOT)/PackageUpdate.zip
$(PKGUP_ZIP) : $(PKGUP_SCRIPTS) $(PKGUP_ZIPPED_TESTAPPS) \
    $(PKGUP_TF_SCRIPT) $(TF_JARS) \
    $(HOST_OUT_EXECUTABLES)/simg2img $(HOST_OUT_EXECUTABLES)/ext2simg \
    $(PKGUP_TOOLS_LIBS) $(SOONG_ZIP)
	rm -rf $(PKGUP_OUT_ROOT)/PackageUpdate
	mkdir -p $(PKGUP_OUT_ROOT)/PackageUpdate
	$(call copy-files-with-structure,$(PKGUP_SCRIPTS),$(dir $<),$(PKGUP_OUT_ROOT)/PackageUpdate)
	mkdir $(PKGUP_OUT_ROOT)/PackageUpdate/testapps
	cp $(PKGUP_ZIPPED_TESTAPPS) $(PKGUP_OUT_ROOT)/PackageUpdate/testapps
	cp $(PKGUP_TF_SCRIPT) $(PKGUP_OUT_ROOT)/PackageUpdate
	mkdir $(PKGUP_OUT_ROOT)/PackageUpdate/tradefed
	cp $(TF_JARS) $(PKGUP_OUT_ROOT)/PackageUpdate/tradefed
	mkdir $(PKGUP_OUT_ROOT)/PackageUpdate/tools
	cp $(HOST_OUT_EXECUTABLES)/simg2img $(HOST_OUT_EXECUTABLES)/ext2simg $(PKGUP_OUT_ROOT)/PackageUpdate/tools
	mkdir $(PKGUP_OUT_ROOT)/PackageUpdate/tools/$(notdir $(HOST_OUT_SHARED_LIBRARIES))
	cp $(PKGUP_TOOLS_LIBS) $(PKGUP_OUT_ROOT)/PackageUpdate/tools/$(notdir $(HOST_OUT_SHARED_LIBRARIES))
	$(SOONG_ZIP) -d -o $@ -C $(dir $@) -D $(dir $@)/PackageUpdate
	rm -rf $(PKGUP_OUT_ROOT)/PackageUpdate

.PHONY : package_update_zip
package_update_zip : $(PKGUP_ZIP)

################################
.PHONY : package_update_clean
package_update_clean :
	rm -rf $(PKGUP_OUT_ROOT)
