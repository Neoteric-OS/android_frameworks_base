LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# TODO
pkgup_dest_dir := $(HOST_OUT)/framework/PackageUpdate

################################
# testapps

pkgup_testapps_zip := $(pkgup_dest_dir)/testapps/testapps.zip

number_of_packages := 40
max_app_version := 10

ALL_PACKAGE_UPDATE_TESTS :=

# $(1): pkgnum
# $(2): pkgver
define package_update_testapp
include $$(CLEAR_VARS)
LOCAL_PACKAGE_NAME := TestApp$(1)ver$(2)
ALL_PACKAGE_UPDATE_TESTS += $$(LOCAL_PACKAGE_NAME)
LOCAL_MODULE_TAGS := tests
LOCAL_SDK_VERSION := 26
LOCAL_JNI_SHARED_LIBRARIES := libTestApp$(1)ver$(2)
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/template/res
LOCAL_USE_AAPT2 := true
manifest_xml := $$(call intermediates-dir-for,APPS,$$(LOCAL_PACKAGE_NAME))/AndroidManifest.xml
$$(manifest_xml) : $(LOCAL_PATH)/template/AndroidManifest.xml
	echo "Generating $$@"
	mkdir -p $$(dir $$<)
	cat $$< | \
		sed -e "s/%(package_name)s/com.android.tests.pkgup$(1)/g" | \
		sed -e "s/%(version_code)s/$(2)/g" | \
		sed -e "s/%(version_name)s/$(2)/g" | \
		sed -e "s/%(install_loc)s/auto/g" | \
		sed -e "s/%(min_sdk_version)s/26/g" | \
		sed -e "s/%(target_sdk_version)s/27/g" | \
		sed -e "s/%(activity_name)s/TestApp/g"  > $$@
java_src := $$(call intermediates-dir-for,APPS,$$(LOCAL_PACKAGE_NAME))/com/android/tests/pkgup$(1)/TestApp.java
$$(java_src) : $(LOCAL_PATH)/template/src/template.txt
	echo "Generating $$@"
	mkdir -p $$(dir $$<)
	cat $$< | \
		sed -e "s/%(packagename)s/com.android.tests.pkgup$(1)/g" | \
		sed -e "s/%(classname)s/TestApp/g" | \
		sed -e "s/%(libname)s/TestApp$(1)ver$(2)/g" | \
		sed -e "s/%%/%/g" > $$@
LOCAL_SOURCE_FILES_ALL_GENERATED := true
LOCAL_GENERATED_SOURCES := $$(java_src)
LOCAL_FULL_MANIFEST_FILE := $$(manifest_xml)
include $$(BUILD_PACKAGE)
include $$(CLEAR_VARS)
LOCAL_MODULE := libTestApp$(1)ver$(2)
LOCAL_MODULE_TAGS := tests
LOCAL_SDK_VERSION := 26
LOCAL_CFLAGS := -Wall -Werror \
	-DFUNC=Java_com_android_tests_pkgup$(1)_TestApp_getLibraryVersion \
	-DVERSION=\"$(2)\"
LOCAL_SRC_FILES := template/jni/template.c
include $$(BUILD_SHARED_LIBRARY)
endef

package_nums := $(shell seq -f %03g $(number_of_packages))
$(foreach pkgnum,$(package_nums),\
	$(foreach pkgver,$(wordlist 1,$(max_app_version),$(__MATH_NUMBERS)), \
		$(eval $(call package_update_testapp,$(pkgnum),$(pkgver)))))

$(pkgup_testapps_zip) : $(ALL_PACKAGE_UPDATE_TESTS)
	rm -rf $(dir $@)
	mkdir -p $(dir $@)
	$(foreach pkgnum,$(package_nums),\
		$(foreach pkgver,$(wordlist 1,$(max_app_version),$(__MATH_NUMBERS)),\
			$(eval apk_dir := $(dir $@)/com.android.tests.pkgup$(pkgnum)/ver$(pkgver))\
			mkdir -p $(apk_dir);\
			cp $(call intermediates-dir-for,APPS,TestApp$(pkgnum)ver$(pkgver))/package.apk $(apk_dir)/TestApp$(pkgnum).apk$(newline)\
			$(eval libs_dir := $(apk_dir)/libs/$(TARGET_CPU_ABI))\
			mkdir -p $(libs_dir);\
			cp $(call intermediates-dir-for,SHARED_LIBRARIES,libTestApp$(pkgnum)ver$(pkgver))/libTestApp$(pkgnum)ver$(pkgver).so $(libs_dir)$(newline)))
	soong_zip -d -o $@ -C $(dir $@) -D $(dir $@)
	rm -rf $(dir $@)/com.android.tests.pkgup*

PHONY : package_update-testapps
package_update-testapps : $(pkgup_testapps_zip)

################################

pkgup_src := \
	$(LOCAL_PATH)/README \
	$(LOCAL_PATH)/README.version \
	$(LOCAL_PATH)/autoflash.bash \
	$(LOCAL_PATH)/build-image \
	$(LOCAL_PATH)/get_component_pathes_by_pkg.sh \
	$(LOCAL_PATH)/reporting \
	$(LOCAL_PATH)/run_test.bash \
	$(LOCAL_PATH)/testcase_parser.py \
	$(LOCAL_PATH)/testcases \
	$(LOCAL_PATH)/util.bash \
	$(LOCAL_PATH)/harnesses/tradefed/res

.PHONY : package_update-all
package_update-all : $(pkgup_src) simg2img ext2simg
	$(call copy-files-with-structure,$(pkgup_src),$(dir $<),$(pkgup_dest_dir))

.PHONY : package_update-clean
package_update-clean :
	rm -rf $(pkgup_dest_dir)

include $(call all-makefiles-under,$(LOCAL_PATH))
