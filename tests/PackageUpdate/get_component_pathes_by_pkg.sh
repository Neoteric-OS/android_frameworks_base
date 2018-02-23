#!/system/bin/sh

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

# This script searches for components (like apk, so, etc...) of one test package.
# It is intended to run on android device.
# @input: one package information
# @output: file path of the components (written in logcat)
# == Usage ==
# adb shell push ls_app.sh /data/local/tmp/
# adb shell "su root sh -c '/data/local/tmp/ls_app.sh -n testCaseNumber
#                       [-s SleepTime] [-p PackageName] [-a AppName]
# ex: adb shell "su root sh -c '/data/local/tmp/ls_app.sh -p com.android.tests.pkgup -a TestApp -n 002'"
# ================================================
# Command line options:
# ================================================
function usage_exit(){
    echo "Usage: $0 -n testCaseNumber [-s SleepTime<sec>] [-p PackageName] [-a AppName]"
    exit 1
}
while getopts n:s:p:a:v OPT
do
    case $OPT in
        "n" ) TESTCASE_NUMBER="$OPTARG" ;;
        "s" ) SLEEP_TIME="$OPTARG" ;;
        "p" ) BASE_PACKAGE_NAME="$OPTARG" ;;
        "a" ) BASE_APP_NAME="$OPTARG" ;;
        "v" ) VERBOSE_LOGGING="true" ;;
        * ) usage_exit ;;
    esac
done
shift $((OPTIND - 1))

# ================================================
# Configurations
# ================================================
# ---- Mandatory parameters ----
# index of testcase aka package suffix
: ${TESTCASE_NUMBER:?} # suppose 3 digits
: ${BASE_PACKAGE_NAME:?}
: ${BASE_APP_NAME:?}
: ${VERBOSE_LOGGING:="false"}
# ---- Optional parameters ----
# duration of file check
: ${SLEEP_TIME:=0}


function is_file(){
    local path=$1
    local tag=$2
    if [ "$VERBOSE_LOGGING" == "true" ]; then
        log -p v -t "${tag}.DEBUG" "checking $path"
    fi
    if [ -f $path ] ; then
        list=$(ls -l $path)
        log -p d -t "${tag}.Host" "Exist: ${list}"
        sleep $SLEEP_TIME
    fi
}

function run(){

    local pkg="${BASE_PACKAGE_NAME}${TESTCASE_NUMBER}"
    local appname="${BASE_APP_NAME}${TESTCASE_NUMBER}.apk"
    local libname="lib${BASE_APP_NAME}${TESTCASE_NUMBER}ver*.so"

    INSTALL_ROOTS=(
        "/system/app"
        "/system/priv-app"
        "/vendor/app"
        "/vendor/priv-app"
        # "/oem/app"
        # "/system/vendor/app"
    )
    for install_root in ${INSTALL_ROOTS[@]}; do
        is_file "${install_root}/${appname}" ${pkg}
        is_file "${install_root}/${appname%.*}/${appname}" ${pkg}
        is_file "${install_root}/${appname%.*}/lib/arm/$libname" ${pkg}
        is_file "${install_root}/${appname%.*}/lib/arm64/$libname" ${pkg}
    done

    local P="${pkg}*"
    is_file "/data/app/${P}.apk" ${pkg}
    is_file "/data/app/${P}/base.apk" ${pkg}
    is_file "/data/app/${P}/${P}.apk" ${pkg}
    is_file "/data/app/${P}/lib/arm/$libname" ${pkg}
    is_file "/data/app/${P}/lib/arm64/$libname" ${pkg}
    is_file "/data/app/${P}.zip" ${pkg}

    is_file "/data/app-lib/${P}/$libname" ${pkg}
    is_file "/data/app-asec/${P}.asec" ${pkg}
    is_file "/data/app-private/${P}.apk" ${pkg}

    is_file "/mnt/asec/${P}/base.apk" ${pkg}
    is_file "/mnt/asec/${P}/base.zip" ${pkg}
    is_file "/mnt/asec/${P}/pkg.apk" ${pkg}
    is_file "/mnt/asec/${P}/res.zip" ${pkg}
    is_file "/mnt/asec/${P}/lib/$libname" ${pkg}
    is_file "/mnt/asec/${P}/lib/arm/$libname" ${pkg}
    is_file "/mnt/asec/${P}/lib/arm64/$libname" ${pkg}
    is_file "/mnt/int_storage/.android_secure/${P}.asec" ${pkg}
    is_file "/mnt/secure/asec/${P}.asec" ${pkg}

    is_file "/sdcard/.android_secure/${P}.asec" ${pkg}
    is_file "/sdcard1/.android_secure/${P}.asec" ${pkg}

    is_file "/data/data/${pkg}/lib/$libname" ${pkg}
    is_file "/data/data/${pkg}/shared_prefs/data_history.xml" ${pkg}
}

run
