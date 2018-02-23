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

COMMON_SCRIPT_DIR=$(cd $(dirname ${BASH_SOURCE:-$0}); pwd)
ADB=adb
: ${WORKSPACE:="./"}
# ================================================
#  Console utilities
# ================================================
function logi () {
    local msg="$@"
    echo "$(date "+%Y-%m-%d %T") INFO: $msg" 1>&2
}
function loge () {
    local msg="$@"
    echo "$(date "+%Y-%m-%d %T") ERROR: $msg" 1>&2
}
function exit_if_not_found () {
    declare -i exit_code=1 # default

    test $# -le 2 || { loge "[exit_if_not_found] Argument error: $@ ($#)"; exit 1; }
    test $# -eq 2 && { exit_code=$2; }

    local what=$1
    test -f "$what" || { loge "not found: $what"; exit $exit_code; }
}
function exit_if_not_dir () {
    declare -i exit_code=1 # default

    test $# -le 2 || { loge "[exit_if_not_dir] Argument error: $@ ($#)"; exit 1; }
    test $# -eq 2 && { exit_code=$2; }

    local what=$1
    test -d "$what" || { loge "not directory: $what"; exit $exit_code; }
}
function mkdir_if_not_found () {
    local tag="mkdir_if_not_found"
    test $# -eq 1 || { throw "[$tag] Argument error: $@ ($#)"; }
    local dest=$1
    if [ ! -d $dest ]; then
        mkdir -p $dest || { throw "[$tag] mkdir failed: $dest"; }
    fi
}
function throw () {
    declare -i exit_code=1 # default

    test $# -le 2 || { loge "[throw] Argument error: $@ ($#)"; exit 1; }
    test $# -eq 2 && { exit_code=$2; }

    local msg=$1
    loge "$msg"
    exit $exit_code
}
# ================================================
# Device operation utilities.
#  $ANDROID_SERIAL required.
# ================================================
function adb_shell () {
    # tentative
    # examine: http://v8.googlecode.com/svn/trunk/tools/android-run.py
    : ${ANDROID_SERIAL:?}
    local cmd="$@"
    local marker="oooPASSooo"
    local ret=$($ADB shell "$cmd && { echo $marker; }")
    echo $ret | grep $marker > /dev/null
    return $?
}
function root_and_remount () {
    : ${ANDROID_SERIAL:?}
    : ${UTIL_ROOT_AND_REMOUNT_TIMEOUT:=120}

    local args=$@
    local adb_cmd="$ADB $args"
    timeout -s KILL $UTIL_ROOT_AND_REMOUNT_TIMEOUT $adb_cmd wait-for-device root ||\
        { throw "failed to root device"; }
    sleep 1
    timeout -s KILL $UTIL_ROOT_AND_REMOUNT_TIMEOUT $adb_cmd wait-for-device remount ||\
        { throw "failed to remount device"; }
}
function disable_verity () {
    : ${ANDROID_SERIAL:?}
    : ${UTIL_DISABLE_VERITY_TIMEOUT:=120}
    local tag="disable_verity"
    if [ $# -eq 1 ]; then
        local do_not_reboot=$1
    fi
    timeout -s KILL $UTIL_DISABLE_VERITY_TIMEOUT $ADB wait-for-device root ||\
        { throw "[$tag] failed to root device"; }
    sleep 1
    timeout -s KILL $UTIL_DISABLE_VERITY_TIMEOUT $ADB wait-for-device disable-verity
    if [ $? -eq 0 ]; then
        if [ "$do_not_reboot" == "true" ]; then
            logi "disable-verity succeed."
        else
            logi "disable-verity succeed. rebooting..."
            $ADB reboot
            wait_for_device
        fi
    else
        logi "disable-verity failed. maybe target device does not support dm-verity. keep setup."
    fi
}
function wait_for_boot_complete () {
    : ${ANDROID_SERIAL:?}
    local timeout=$1
    local wait_period=5
    local wait_all_period=0
    local boot_completed=""

    logi "wait for boot complete..."
    wait_for_device
    while [ $wait_all_period -le $timeout ];
    do
        boot_completed=$($ADB shell getprop sys.boot_completed)
        if [ "$boot_completed" = "1" ]; then
            break;
        fi
        sleep $wait_period
        wait_all_period=$(($wait_all_period+$wait_period))
    done

    if [ "$boot_completed" != "1" ]; then
        loge "Target device did not boot within $timeout seconds. Aborting..."
        exit 1
    fi
}
function wait_for_device () {
    local tag="wait_for_device"
    : ${ANDROID_SERIAL:?}
    logi "[$tag] START ($ANDROID_SERIAL)..."
    timeout -s SIGKILL 300s $ADB wait-for-device || \
        { loge "[$tag] Timeout. Aborting..."; exit 1; }
}
function clear_logbuffer () {
    : ${ANDROID_SERIAL:?}
    logi "clear logbuffer"
    $ADB logcat -c -b system -b main -b events -b radio
}
# Retries a command on failure.
# $1 - the max number of attempts
# $2 - duration between attempts (seconds)
# $3... - the command to run
function retry () {
    local max_trial="$1"; shift
    local duration="$1"; shift
    local cmd="$@"
    local i=
    for i in $(seq 1 $max_trial); do
        $cmd && { return 0; }
        loge "failed to execute: $cmd"
        loge "retry after $duration sec. ($i/$max_trial)"
        sleep $duration
    done
    loge "retry failed"
    return 1
}
function pm_run () {
    # pm command always returns 0!
    local cmd="$@"
    local ret=$($ADB shell "pm $cmd")
    echo $ret | grep "Success" > /dev/null
    return $?
}
: ${ALLOW_OPERATION_FAILURE:="true"}
function fail () {
    local msg="$@"
    if [ "$ALLOW_OPERATION_FAILURE" == "true" ]; then
        loge "$msg (continue test script.)"
    else
        throw "$msg"
    fi
}
function check () {
    # temp. solution for evaluating adb install
    local cmd="$@"
    local ret=$($cmd 2>&1)
    echo $ret | grep "Success" > /dev/null
    local status=$?
    echo "(output)> $ret"
    return $status
}
