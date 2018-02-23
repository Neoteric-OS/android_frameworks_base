#!/bin/bash

# Copyright (C) 2018 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This script is the entry point of PackageUpdate test.
#
# -- Requirements --
# PackageUpdate test requires at least
# - adb and fastboot environment
# - an unlocked android device that is able to operate via adb
# It also requires some pre-built resources
# - userdebug factory images
# - test applications
#
# -- Brief usage --
# 1. Connect the target device to the host machine.
# 2. bash run_test.bash [-s <android_serial>] [-r <path/to/the/reference>] \
#      [-l sys2sys|sys2vendor|vendor2sys|vendor2vendor] \
#      testcases/01_basic-scope/testcase.txt <path/to/the/test_apps> \
#      <path/to/the/first_rom> <path/to/the/secondary_rom>
if [ "$PKGUP_DEBUG" == "true" ]; then
    echo "enabled PKGUP_DEBUG" 1>&2
    export PS4='+[$(basename ${BASH_SOURCE}):${LINENO}]: '
    set -x
fi
# ================================================
# Include common functions
# ================================================
SCRIPT_D=$(cd $(dirname ${BASH_SOURCE:-$0}); pwd)
UTIL="${SCRIPT_D}/util.bash"
source $UTIL || { echo "could not load common functions."; exit 1; }
# ================================================
# Command line options
# ================================================
function usage_exit() {
    echo "Usage: $0 [OPTION] 'TESTCASE_FILE' 'TESTAPKS_ZIP' 'ROM1_BASE_IMAGES_ZIP' 'ROM2_BASE_IMAGES_ZIP'" 1>&2
    echo "- OPTION: [-s ANDROID_SERIAL]" 1>&2
    echo "          [-r REFERENCE_D: path to reference_directory]" 1>&2
    echo "          [-l sys2sys|sys2vendor|vendor2sys|vendor2vendor: location to pre-install test applications]" 1>&2
    echo "- TESTCASE_FILE: path to the testcase definition" 1>&2
    echo "- TESTAPKS_ZIP : path to the zip archive of test applications" 1>&2
    echo "- ROM1_BASE_IMAGES_ZIP : path to the zip archive of base images of ROM1" 1>&2
    echo "- ROM2_BASE_IMAGES_ZIP : path to the zip archive of base images of ROM2" 1>&2
    exit 1
}
while getopts "s:r:l:h-:" OPT
do
    case $OPT in
        "-")
            case "${OPTARG}" in
                "help")
                    usage_exit
                    ;;
            esac
            ;;
        "s")  DEVICE=$OPTARG
              echo "[getopts] set DEVICE=$DEVICE"
            ;;
        "h")  usage_exit
            ;;
        "r")  REFERENCE_D=$(readlink -f $(eval echo $OPTARG))
              echo "[getopts] set REFERENCE_D=$REFERENCE_D"
            ;;
        "l")  PRE_INSTALL_LOCATION=$OPTARG
              echo "[getopts] set PRE_INSTALL_LOCATION=$PRE_INSTALL_LOCATION"
            ;;
        \?) usage_exit
            ;;
    esac
done
shift $((OPTIND - 1))
test $# -eq 4 || { usage_exit; }

TESTCASE_FILE=$(readlink -f $(eval echo $1)); shift
TESTAPKS_ZIP=$(readlink -f $(eval echo $1)); shift
ROM1_BASE_IMAGES_ZIP=$(readlink -f $(eval echo $1)); shift
ROM2_BASE_IMAGES_ZIP=$(readlink -f $(eval echo $1)); shift

logi 'This test require root privilege in order to compose mounted android images'
sudo echo ''

# ================================================
# Test parameters
# ================================================
: ${TESTCASE_FILE:?}
exit_if_not_found $TESTCASE_FILE

: ${TESTAPKS_ZIP:?}
exit_if_not_found $TESTAPKS_ZIP

: ${ROM1_BASE_IMAGES_ZIP:?}
exit_if_not_found $ROM1_BASE_IMAGES_ZIP

: ${ROM2_BASE_IMAGES_ZIP:?}
exit_if_not_found $ROM2_BASE_IMAGES_ZIP

: ${DEVICE:?}
export ANDROID_SERIAL=$DEVICE

: ${REFERENCE_D:=""}
: ${BASE_PACKAGE_NAME:="com.android.tests.pkgup"}
: ${BASE_APP_NAME:=TestApp}

: ${PRE_INSTALL_LOCATION:=""}

: ${ADB:="adb"}
type -p $ADB > /dev/null 2>&1 || \
    throw "[$0] adb command not found"

# ================================================
# Constants
# ================================================
REPORT_TOOL="${SCRIPT_D}/reporting/make_report.py"
DIFF_TOOL="${SCRIPT_D}/reporting/make_diff.py"
FILE_CHECKER="${SCRIPT_D}/get_component_pathes_by_pkg.sh"
TESTCASE_PARSER="${SCRIPT_D}/testcase_parser.py"
TESTAPKS_D="${SCRIPT_D}/tmp_testapks"
TIMEOUT_FOR_BOOT_COMPLETE=900
INTERVAL_DEVICE_FILECHECK=0.3

# directory structures
REPORT_D="${SCRIPT_D}/reports"
test -d $REPORT_D || { mkdir -p $REPORT_D; }
LOG_D="${REPORT_D}/logs"
test -d $LOG_D || { mkdir -p $LOG_D; }
BOOTLOG_D="${LOG_D}/boot"
test -d $BOOTLOG_D || { mkdir -p $BOOTLOG_D; }
PMS_DUMP_D="${REPORT_D}/dumps"
test -d $PMS_DUMP_D || { mkdir -p $PMS_DUMP_D; }
RAW_RESULT_D="${REPORT_D}/raw_results"
test -d $RAW_RESULT_D || { mkdir -p $RAW_RESULT_D; }
HTML_RESULT_D="${REPORT_D}/html"
test -d $HTML_RESULT_D || { mkdir -p $HTML_RESULT_D; }

IMAGES_ROOT_D="${SCRIPT_D}/tmp_image"
IMAGES_TMP_D="${IMAGES_ROOT_D}/ROM"
ROM1_IMAGES_D="${IMAGES_ROOT_D}/ROM1"
ROM2_IMAGES_D="${IMAGES_ROOT_D}/ROM2"

# ================================================
# Test functions
# ================================================

PID_LOGGER=""
function logger_start () {
    local log_path=$1
    test -n $log_path || { loge "[logger_start] log_path not found"; exit 1; }

    if [ -n "$PID_LOGGER" ]; then
        logger_stop
    fi
    $ADB logcat -v time -b main -b system -b crash > $log_path &
    PID_LOGGER=$!
}
function logger_stop () {
    if [ -n "$PID_LOGGER" ]; then
        kill $PID_LOGGER
        wait $PID_LOGGER 2> /dev/null
        PID_LOGGER=""
    fi
}
function run_autoflash () {
    local tag=run_autoflash
    test $# -eq 4 || \
        { throw "[$tag]required 4 parameter: <device_id> <rom directory> <flg_keep_userdata> <disable_verity>"; }
    local device_id=$1
    local rom_d=$2
    local keep_userdata=$3
    local disable_verity=$4

    local flash_options=""
    if [ $disable_verity == "true" ]; then
        flash_options="$flash_options -v true"
    else
        flash_options="$flash_options -v false"
    fi
    if [ $keep_userdata == "true" ]; then
        flash_options="$flash_options -k"
    fi
    bash $SCRIPT_D/autoflash.bash $flash_options $device_id $rom_d || \
        { throw "[$tag]failed to flash target device: param $flash_options"; }
}
# [Arguments]
# $1: directory to store test results.
# $2: (optional) additional install script
function run_test_apps () {

    local slot_name="$1"
    local path_install_script="$2"      # optional

    local localpath_testapks_root=$TESTAPKS_D
    local slot_dump_d=$PMS_DUMP_D/$slot_name
    test -d $slot_dump_d || { mkdir -p $slot_dump_d; }
    local result_filepath="$RAW_RESULT_D/${slot_name}.txt"
    local mainlog_filepath="$LOG_D/${slot_name}.log"

    $ADB shell dumpsys package > ${slot_dump_d}/dumpsys_package_before.txt

    wait_for_device
    $ADB push "$FILE_CHECKER" "/data/local/tmp/"
    clear_logbuffer
    logger_start $mainlog_filepath

    wait_for_boot_complete ${TIMEOUT_FOR_BOOT_COMPLETE}

    if [ -n "$path_install_script" ]; then
        logi "###################################################"
        logi "RUN $(basename $path_install_script)"
        logi "###################################################"
        bash -e $path_install_script $localpath_testapks_root || \
            { throw "failed to run install script: $path_install_script"; }
        sleep 10
    fi

    logi "###################################################"
    logi "Start test activities"
    logi "###################################################"
    start_test_activities $TESTCASE_COUNT "current"
    sleep 10
    logger_stop
    $ADB shell dumpsys package > ${slot_dump_d}/dumpsys_package.txt
    parse_result $mainlog_filepath $result_filepath
    sleep 5
}
function start_test_activities () {
    test $# -eq 2 || { throw "required: number of testcase, --user uid|current"; }
    local testcase_count=$1
    local am_user_args=$2
    local script_path="/data/local/tmp/$(basename $FILE_CHECKER)"
    local base_args="-s $INTERVAL_DEVICE_FILECHECK -p $BASE_PACKAGE_NAME -a $BASE_APP_NAME"

    for i in $(seq 1 $testcase_count); do
        i=$(printf "%03d" $i)
        local pkg="${BASE_PACKAGE_NAME}${i}"
        local args="$base_args -n $i"
        logi "am start ${pkg}/${pkg}.TestApp --activity-clear-top"
        $ADB shell "log -p d -t ${pkg}.Host '#[START]---------------------------------#'"
        $ADB shell "am start -n ${pkg}/${pkg}.TestApp --activity-clear-top"
        sleep 4
        $ADB shell "su root sh -c '$script_path $args'"
        $ADB shell "log -p d -t ${pkg}.Host '#[END]-----------------------------------#'"
    done
}
function parse_result () {
    local input="$1"
    local dest="$2"
    local escaped_pkg=$(echo $BASE_PACKAGE_NAME | perl -pwe 's/\./\\\./g')
    # TODO : embebedded literal "TestApp"
    grep "D\/${BASE_PACKAGE_NAME}" $input | \
        perl -pwe "s/^.+D\/(${escaped_pkg}[0-9]+\.(TestApp(\.diag)?|Host))\( *[0-9]+\): (.+)/\$1 : \$4/" | \
        tee $dest
}
function compare_with_reference () {
    local diff_tool="${SCRIPT_D}/reporting/make_diff.py"
    local reference_d="$1"
    local target_d="$2"
    local output_d=$REPORT_D/diff
    local options="--report-d $output_d "
    options="$options --enable-context-diff"
    options="$options --logfile=$output_d/analysis.log"
    options="$options --export-data-path=$output_d/diff.json"
    if [ "$ENABLE_MULTIUSER_MODE" == "true" ]; then
        options="$options --enable-multi-user"
    fi
    mkdir_if_not_found "$output_d"
    python $diff_tool $reference_d $target_d $options
}
function unzip_image() {
    local image_zip=$1
    local dest_dir=$2

    if [ -d "$dest_dir" ]; then
        logi "removing destination directory to extract zip file..."
        rm -rf "$dest_dir"
    fi
    mkdir -p $dest_dir

    unzip $image_zip -d $dest_dir
    local count=$(find "$dest_dir" -mindepth 1 -maxdepth 1 -type d | wc -l)
    if [ $count -eq 1 ]; then
        local dir=$(find "$dest_dir" -mindepth 1 -maxdepth 1 -type d)
        mv $dir/*.img "$dest_dir"
        mv $dir/*.zip "$dest_dir"
        logi "removing temporary directory in $dest_dir..."
        rm -rf "$dir"
    fi
    count=$(find "$dest_dir" -maxdepth 1 -name "*.zip" -type f | wc -l)
    if [ $count -ne 1 ]; then
        throw "update.zip not found in image zip"
    fi
    local update_zip=$(find "$dest_dir" -maxdepth 1 -name "*.zip" -type f)
    local update_img_dir="${dest_dir}/$(basename $update_zip .zip)"
    mkdir -p "$update_img_dir"
    unzip "$update_zip" -d "$update_img_dir"
}
function unzip_testapks () {
    local uri_testapks=$1
    local localpath_testapks_root=$2
    logi "copy apks from $uri_testapks..."
    exit_if_not_found "$uri_testapks"
    unzip -qq "$uri_testapks" -d "$localpath_testapks_root"
    test $? -eq 0 || \
        { throw "could not unzip archive of test apk"; }
}
function on_exit() {
    if [ -d "$IMAGES_TMP_D" ]; then
        logi "removing temporary ROM directory..."
        rm -rf "$IMAGES_TMP_D"
    fi
    if [ -d "$TESTAPKS_D" ]; then
        logi "removing temporary test applications..."
        rm -rf "$TESTAPKS_D"
    fi
}

trap on_exit EXIT
# ================================================
# Prepare test resources
# ================================================
# Extract test appications
if [ -d $TESTAPKS_D ]; then
    logi "removing existing test applications..."
    rm -rf "$TESTAPKS_D"
fi
unzip_testapks $TESTAPKS_ZIP $TESTAPKS_D
parser_arg="$TESTCASE_FILE -d $TESTAPKS_D"
if [ -n "$PRE_INSTALL_LOCATION" ]; then
    parser_arg="$parser_arg -l $PRE_INSTALL_LOCATION"
fi
if [ -n "$INTERVAL_ADB_INSTALL" ]; then
    parser_arg="$parser_arg --interval-adb-install $INTERVAL_ADB_INSTALL"
fi
# generate install script and ROM configurationfrom the testcase.
python $TESTCASE_PARSER $parser_arg || { throw "Failed to parse testcase"; }
TEST_PROPERTIES="packageupdate.properties"
test -f $TEST_PROPERTIES || { throw '$TEST_PROPERTIES does not exist'; }

PREINSTALL_FILELISTS=()
for i in $(seq 1 2); do
    files=($(cat $TEST_PROPERTIES | grep -oP '(?<=PREINSTALL_FILELISTS=)\S+' | cut -d, -f$i | tr -s ':' ' '))
    filelist=""
    for file in "${files[@]}"; do
        mv "$file" "$REPORT_D"
        if [ -n "$filelist" ]; then
            filelist+=":"
        fi
        filelist+=$REPORT_D/$(basename "$file")
    done
    PREINSTALL_FILELISTS+=("$filelist")
done

INSTALL_SCRIPTS=( $(cat $TEST_PROPERTIES | grep -oP '(?<=INSTALL_SCRIPTS=)\S+' | tr -s ',' ' ') )
for install_script in "${INSTALL_SCRIPTS[@]}"; do
    chmod 775 $install_script
    mv $install_script $REPORT_D
done
TESTCASE_COUNT=$(cat $TEST_PROPERTIES | grep -oP '(?<=TESTCASE_COUNT=)[0-9]+')
mv expected_version.json $REPORT_D
mv $TEST_PROPERTIES $REPORT_D

logi "[$0] Finished successfully. Waiting for rom build completed."

# Prepare ROM
if [ -d "$IMAGES_ROOT_D" ]; then
    logi "removing ROM directory..."
    rm -rf "$IMAGES_ROOT_D"
fi
mkdir -p $IMAGES_ROOT_D

# ================================================
# Run Test
# ================================================
logi "========================================================="
logi "Flash ROM1"
logi "========================================================="

clear_logbuffer
unzip_image $ROM1_BASE_IMAGES_ZIP $IMAGES_TMP_D
bash $SCRIPT_D/build-image/run_build.bash -a "${PREINSTALL_FILELISTS[0]}" "$TESTAPKS_D" "$IMAGES_TMP_D" "$ROM1_IMAGES_D" || \
    { throw "Failed to execute run_build: ROM1"; }
run_autoflash "$DEVICE" "$ROM1_IMAGES_D" "false" "true" || \
    { throw "AutoFlash failed at 'ROM1'"; }

wait_for_device
logger_start "$BOOTLOG_D/1_ROM1_1stboot_main.log"
wait_for_boot_complete ${TIMEOUT_FOR_BOOT_COMPLETE}
logger_stop

run_test_apps "1_result-ROM1"

logi "========================================================="
logi "Run TestApps : Operation1 for ROM1"
logi "========================================================="
run_test_apps "2_result-ROM1_Op1" "$REPORT_D/${INSTALL_SCRIPTS[0]}"

logi "========================================================="
logi "Run TestApps : Operation2 for ROM1"
logi "========================================================="
run_test_apps "3_result-ROM1_Op2" "$REPORT_D/${INSTALL_SCRIPTS[1]}"

logi "========================================================="
logi "Flash ROM2"
logi "========================================================="

clear_logbuffer

unzip_image $ROM2_BASE_IMAGES_ZIP $IMAGES_TMP_D
bash $SCRIPT_D/build-image/run_build.bash -a "${PREINSTALL_FILELISTS[1]}" "$TESTAPKS_D" "$IMAGES_TMP_D" "$ROM2_IMAGES_D" || \
    { throw "Failed to execute run_build: ROM2"; }
run_autoflash "$DEVICE" "$ROM2_IMAGES_D" "true" "false" || \
    { throw "AutoFlash failed at 'ROM2'"; }

wait_for_device
logger_start "$BOOTLOG_D/6_ROM2_1stboot_main.log"
wait_for_boot_complete ${TIMEOUT_FOR_BOOT_COMPLETE}
logger_stop

logi "wait 60 seconds for all boot receivers to finish"
sleep 60
run_test_apps "4_result-ROM2"

logi "========================================================="
logi "Run TestApps : Operation1 for ROM2"
logi "========================================================="
run_test_apps "5_result-ROM2_Op1" "$REPORT_D/${INSTALL_SCRIPTS[2]}"

logi "========================================================="
logi "Run TestApps : Operation2 for ROM2"
logi "========================================================="
run_test_apps "6_result-ROM2_Op2" "$REPORT_D/${INSTALL_SCRIPTS[3]}"

logi "========================================================="
logi "Test finished"
logi "========================================================="

# ================================================
# Make Test Report
# ================================================

report_opts=
if [ "$REFERENCE_D" != "" ]; then
    compare_with_reference "$REFERENCE_D" "$RAW_RESULT_D"
    if [ -f "$REPORT_D/diff/diff.json" ]; then
        report_opts="$report_opts --diff-from-reference=$REPORT_D/diff/diff.json"
    fi
fi
report_opts="$report_opts --fota-osupdate-type=l2l"
report_opts="$report_opts --fota-root-partition=sys2sys"
if [ -f "$REPORT_D/expected_version.json" ]; then
    report_opts="$report_opts --expected-version-matrix $REPORT_D/expected_version.json"
fi
report_opts="$report_opts --logfile=$HTML_RESULT_D/analysis.log"

pushd ${SCRIPT_D}/reporting
python make_report.py "$RAW_RESULT_D" "$TESTCASE_FILE" $report_opts
mv out.html $HTML_RESULT_D/analysis.html
popd

cp $TESTCASE_FILE $REPORT_D/
