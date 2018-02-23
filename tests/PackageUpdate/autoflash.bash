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

# This script flashes test image to the target device using fastboot
# -- Brief usage --
# autoflash.bash [ANDROID_SERIAL] [path/to/the/img]
# see usage_exist for details
if [ "$PKGUP_DEBUG" == "true" ]; then
    echo "enabled PKGUP_DEBUG" 1>&2
    set -x
fi

# ================================================
# Command line options:
#   Override environemnt variables
# ================================================
function usage_exit() {
    echo "Usage: $0 " 1>&2
    echo "default: require 2 arguments: device_id, images_dir" 1>&2
    echo "[-k: keep userdata flag]" 1>&2
    echo "[-s: specify slot name to flash (a, b, other)]" 1>&2
    echo "[-v: disable dm-verity before flash]" 1>&2
    exit 1
}
while getopts ks:v: OPT
do
    case $OPT in
        "k")  KEEP_USERDATA="true"
              echo "[$0] set KEEP_USERDATA=true"
              ;;
        "s")  SLOT="$OPTARG"
              echo "[$0] set SLOT=$OPTARG"
              ;;
        "v")  DISABLE_DMVERITY="$OPTARG"
              echo "[$0] set DISABLE_DMVERITY=$OPTARG"
              ;;
        \?) usage_exit
            ;;
    esac
done
shift $((OPTIND - 1))

# ================================================
#  Mode configularations
# ================================================
: ${KEEP_USERDATA:="false"}
: ${DISABLE_DMVERITY:="false"}

test $# -eq 2 || \
    { echo "[$0] required 2 parameter: <device_id> <images_dir>"; exit 1; }
DEVICE=$1
IMAGES_DIR=$2

# ================================================
# Include common functions
# ================================================
SCRIPT_D=$(cd $(dirname ${BASH_SOURCE:-$0}); pwd)
UTIL="${SCRIPT_D}/util.bash"
source $UTIL || { echo "could not load common functions."; exit 1; }

FASTBOOT=$(type -p fastboot)
if [ -z "$FASTBOOT" ]; then
    throw "[$tag] fastboot command not found"
fi

# ================================================
#   Functions
# ================================================
function flashall() {
    local tag="flashall"

    pushd "$IMAGES_DIR"

    local flash_options="-s $DEVICE"
    if [ "$SLOT" ]; then
        flash_options="$flash_options --slot $SLOT"
    fi
    local reboot_options="-s $DEVICE"

    local bootloader_img=$(find . -maxdepth 1 \( -name "bootloader.img" -or -name "bootloader-*.img" \))
    if [ -n "$bootloader_img" ]; then
        $FASTBOOT $flash_options flash bootloader "$bootloader_img" || \
            { throw "[$tag] failed to flash device"; }
        $FASTBOOT $reboot_options reboot-bootloader
        sleep 5
    fi

    local radio_img=$(find . -maxdepth 1 \( -name "radio.img" -or -name "radio-*.img" \))
    if [ -n "$radio_img" ]; then
        $FASTBOOT $flash_options flash radio "$radio_img" || \
            { throw "[$tag] failed to flash device"; }
        $FASTBOOT $reboot_options reboot-bootloader
        sleep 5
    fi

    flash_options="$flash_options --disable-verity"
    if [ "$KEEP_USERDATA" == "false" ]; then
        flash_options="$flash_options -w"
    fi

    local image_zip=$(find . -maxdepth 1 -name "*.zip")
    if [ -n "$image_zip" ]; then
        $FASTBOOT $flash_options update "$image_zip" || \
            { throw "[$tag] failed to flash device"; }
    fi

    popd
}

# ---------------------------------------------------------------

exit_if_not_dir "$IMAGES_DIR"
if [ -z "$(ls $IMAGES_DIR)" ]; then
    throw "$IMAGES_DIR is empty"
fi

if [ "$DISABLE_DMVERITY" == "true" ]; then
    logi "[$0] disabling dm-verity before flash"
    disable_verity
fi

adb -s "$DEVICE" reboot bootloader || \
    { throw "[$0] failed to reboot device"; }
sleep 5
flashall
