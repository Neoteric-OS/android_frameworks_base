LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := PackageUpdate

pkgup_dest_dir := $(HOST_OUT)/$(LOCAL_PACKAGE_NAME)

pkgup_testapps_dest_dir := $(pkgup_dest_dir)/testapps
pkgup_testapps_zip := $(pkgup_testapps_dest_dir)/testapps.zip

pkgup_dest_to_zip_dir := $(pkgup_dest_dir)/$(LOCAL_PACKAGE_NAME)
package_update-zip := $(pkgup_dest_dir)/$(LOCAL_PACKAGE_NAME).zip

################################
# tradefed

pkgup_tf_src := $(LOCAL_PATH)/harnesses/tradefed/src
pkgup_tf_config_files := \
	$(LOCAL_PATH)/harnesses/tradefed/config/basic-scope.xml \
	$(LOCAL_PATH)/harnesses/tradefed/config/demo.xml
tradefed_dir := tools/tradefederation/core/prod-tests

.PHONY : package_update-tradefed
package_update-tradefed : $(pkgup_tf_src) $(pkgup_tf_config_files)
	cp -Rf $(pkgup_tf_src) $(tradefed_dir)
	mkdir -p $(tradefed_dir)/res/config/pkgup
	cp $(pkgup_tf_config_files) $(tradefed_dir)/res/config/pkgup

################################
# tools

pkgup_tools_ext2simg := $(HOST_OUT_EXECUTABLES)/ext2simg
pkgup_tools_simg2img := $(HOST_OUT_EXECUTABLES)/simg2img

pkgup_tools_libs := $(HOST_OUT_SHARED_LIBRARIES)/libbase.so \
	$(HOST_OUT_SHARED_LIBRARIES)/libext2_com_err-host.so \
	$(HOST_OUT_SHARED_LIBRARIES)/liblog.so \
	$(HOST_OUT_SHARED_LIBRARIES)/libz-host.so \
	$(HOST_OUT_SHARED_LIBRARIES)/libc++.so \
	$(HOST_OUT_SHARED_LIBRARIES)/libext2fs-host.so \
	$(HOST_OUT_SHARED_LIBRARIES)/libsparse-host.so

pkgup_tools_dest_dir := $(pkgup_dest_to_zip_dir)/tools/
pkgup_tools_lib_dest_dir := $(pkgup_tools_dest_dir)/$(notdir $(HOST_OUT_SHARED_LIBRARIES))

.PHONY : package_update-tools
package_update-tools : ext2simg $(pkgup_tools_simg2img) $(pkgup_tools_libs)
	mkdir -p $(pkgup_tools_dest_dir)
	cp $(pkgup_tools_ext2simg) $(pkgup_tools_simg2img) $(pkgup_tools_dest_dir)
	mkdir -p  $(pkgup_tools_lib_dest_dir)
	cp $(pkgup_tools_libs) $(pkgup_tools_lib_dest_dir)

################################
# testapps

pkgup_app-generator := $(LOCAL_PATH)/app-generate/generator.py
pkgup_testapps_src_dir := $(LOCAL_PATH)/testapps

number_of_packages := 40
max_app_version := 10
generate_args := -d $(pkgup_testapps_src_dir) \
	--min-sdk-version=26 \
	--target-sdk-version=27 \
	--classname=TestApp \
	--app-name=TestApp \
	--lib-name=TestApp \
	--package=com.android.tests.pkgup \
	--generate-tree \
	--max-pkg-suffix=$(number_of_packages) \
	--max-app-version=$(max_app_version)

.PHONY : package_update-testapps-src
package_update-testapps-src :
	python $(pkgup_app-generator) $(generate_args)

################################
# zip

pkgup_zip_targets := \
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
pkgup_zip_target_dir := $(LOCAL_PATH)

$(package_update-zip) : $(pkgup_zip_targets) package_update-tools $(testapps_dist_zip)
	$(call copy-files-with-structure,$(pkgup_zip_targets),$(pkgup_zip_target_dir)/,$(pkgup_dest_to_zip_dir))
	cd $(pkgup_dest_dir) && zip -rq $(notdir $@) $(notdir $(pkgup_dest_to_zip_dir))
	echo "Output: $@"

################################

.PHONY : package_update-all
package_update-all : package_update-tradefed $(package_update-zip)

.PHONY : package_update-clean
package_update-clean :
	rm -rf $(tradefed_dir)/res/config/pkgup/
	rm -rf $(tradefed_dir)/src/com/android/tests/pkgup
	rm -rf $(pkgup_dest_dir)
	rm -rf $(pkgup_intermediates)

include $(call all-makefiles-under,$(LOCAL_PATH))
