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

# This script generates test image by embedding apks to provided image.
# Currently this script requires root privilege of host machine
# in order to operate mounted images.
if [ "$PKGUP_DEBUG" == "true" ]; then
    echo "enabled PKGUP_DEBUG" 1>&2
    export PS4='+[$(basename ${BASH_SOURCE}):${LINENO}]: '
    set -x
fi

# ================================================
# Command line options
# ================================================
function usage_exit () {
    echo "Usage: $0 " 1>&2
    echo "default: require 4 arguments: preinstall_filelist, apk_dir, src_dir, dest_dir" 1>&2
    exit 1
}

while getopts h OPT
do
    case $OPT in
        "h")  usage_exit
            ;;
        \?) usage_exit
            ;;
    esac
done
shift $((OPTIND - 1))

test $# -eq 4 || \
    { echo "[$0] required 4 parameter: <preinstall_filelist> <apk_dir> <src_dir> <dest_dir>"; exit 1; }

PREINSTALL_FILES=( $(echo "$1" | tr -s ':' ' ') )
TESTAPP_ROOT_D="$2"
ORIG_ROM_ROOT_D="$3"
MODIFIED_ROM_ROOT_D="$4"

# ================================================
# Include common functions
# ================================================
SCRIPT_D=$(cd $(dirname ${BASH_SOURCE:-$0}); pwd)
UTIL="${SCRIPT_D}/../util.bash"
source $UTIL || { echo "could not load common functions: $UTIL" ; exit 1; }

# ================================================
# Configurations
# ================================================
logi "[run_build] checking configurations.."

: ${ADB:="adb"}
: ${DEVICE_FOR_COMPOSITION:=""}
if [ "$DEVICE_FOR_COMPOSITION" != "" ]; then
    logi "set device for composition: $DEVICE_FOR_COMPOSITION"
    ADB="$ADB -s $DEVICE_FOR_COMPOSITION"
fi

if [ -n "$PKGUP_ROOT" ]; then
    export PATH=${PKGUP_ROOT}/tools:$PATH
fi
SIMG2IMG=$(which simg2img) || { throw "simg2img does not exist on the PATH."; }
EXT2SIMG=$(which ext2simg) || { throw "ext2simg does not exist on the PATH."; }

exit_if_not_dir "$TESTAPP_ROOT_D"
exit_if_not_dir "$ORIG_ROM_ROOT_D"
mkdir -p "$MODIFIED_ROM_ROOT_D"

ORIG_SYSTEM_IMG=$(find $ORIG_ROM_ROOT_D -type f -name "system.img")
if [ "$ORIG_SYSTEM_IMG" == "" ]; then
    throw "Unable to found system.img in target directory"
elif [ $(echo $ORIG_SYSTEM_IMG | wc -l) -ne 1 ]; then
    throw "Expected 1 system image in target directory [actual: $(echo $ORIG_SYSTEM_IMG | wc -l)]"
fi
ORIG_IMG_DIR=$(dirname "$ORIG_SYSTEM_IMG")

# create directories for build image
WORK_D="$SCRIPT_D/work"
COMPOSITION_D="$WORK_D/compositioned"
mkdir_if_not_found $WORK_D
mkdir_if_not_found $COMPOSITION_D

MOUNT_ROOT_ON_DEVICE="/sdcard/ext4_sys"
EXT4_ROOT_ON_DEVICE="/sdcard/ext4/"

# ================================================
#  Functions
# ================================================
function setup_device_for_composition () {
    local tag="setup_device_for_composition"
    logi "[$tag] start"
    timeout -s SIGKILL 90 $ADB wait-for-device root || \
        { throw "Unable to find the device for setup"; }

    logi "[$tag] cleaning up working directory on device"
    umount_all_ext4
    $ADB shell rm -rf "$MOUNT_ROOT_ON_DEVICE"
    $ADB shell mkdir -p "$MOUNT_ROOT_ON_DEVICE"
    $ADB shell rm -rf "$EXT4_ROOT_ON_DEVICE"
    $ADB shell mkdir -p "$EXT4_ROOT_ON_DEVICE"
    logi "[$tag] completed"
}

function add_apk () {
    local src_file=$1
    local dest_root=$2
    local dest_path=$3
    local secontext=$4

    local dest_path_on_device="${dest_root}/${dest_path}"

    $ADB shell "mkdir -p $dest_path_on_device"
    $ADB push "${src_file}" "${dest_path_on_device}" >/dev/null 2>&1
    $ADB shell "find $dest_root/${dest_path%%/*} -type d | xargs chmod 755"
    $ADB shell "chmod 644 ${dest_path_on_device}/*"
    $ADB shell "chcon -R $secontext $dest_root/${dest_path%%/*}"
}

function add_all_apks () {
    local tag="add_all_apks"
    local preinstall_file=$1
    local partition=$2
    local app_root=$3

    local dest_path="${partition}/${app_root}"
    local dest_dir=$MOUNT_ROOT_ON_DEVICE/$dest_path

    local secontext=
    if [ $partition == "system" ]; then
        secontext="u:object_r:system_file"
    elif [ $partition == "vendor" ]; then
        secontext="u:object_r:vendor_app_file"
    else
        throw "[$tag] Unable to determine secontext for $partition"
    fi

    $ADB shell "test -d $dest_dir"
    if [ $? -ne 0 ]; then
        logi "[$tag] creating app root directory $dest_dir"
        $ADB shell "mkdir -p $dest_dir"
        $ADB shell "chmod 755 $dest_dir"
        $ADB shell "chcon $secontext $dest_dir"
    fi

    ORIG_IFS=$IFS
    IFS=$'\n'
    preinstall_lines=( $(cat "$preinstall_file") )

    IFS=','
    for line in "${preinstall_lines[@]}"; do
        set $line
        local src_file=$TESTAPP_ROOT_D/$1
        if [ ! -f "$src_file" ]; then
            { throw "[$tag] resource not found: $src_file"; }
        fi
        add_apk "$src_file" "$dest_dir" "$2" "$secontext"
        if [ $? -eq 0 ]; then
            logi "[$tag] added $1 to /${dest_path}/$2"
        else
            throw "[$tag] failed to add $1 to /${dest_path}/$2"
        fi
    done
    IFS=$ORIG_IFS
}

function zip_update_images () {
    local update_img_d=$1
    zip -j "$(dirname $update_img_d)/$(basename $update_img_d).zip" $update_img_d/*
    rm -rf "$update_img_d"
}

function set_adb_no_secure () {
    local default_prop=$1
    local modified_prop="/data/local/tmp/default.prop"
    $ADB shell "grep -v ro.adb.secure= $default_prop > $modified_prop"
    $ADB shell "echo ro.adb.secure=0 >> $modified_prop"
    $ADB shell "cp $modified_prop $default_prop"
}

function mount_ext4() {
    local tag="mount_ext4"
    local ext4_on_host=$1
    local mountd_on_device=$2
    local ext4_on_device="$EXT4_ROOT_ON_DEVICE/$(basename $ext4_on_host)"

    logi "[$tag] copying $ext4_on_host to the device..."
    $ADB push $ext4_on_host $EXT4_ROOT_ON_DEVICE >/dev/null 2>&1 || { throw "[tag] copy failed"; }

    if [[ $($ADB shell losetup -f 2>&1) =~ /dev/[a-zA-Z_0-9./]*loop([0-9]+) ]]; then
        local unused_loop_device="/dev/block/loop${BASH_REMATCH[1]}"
        logi "[$tag] trying to mount $ext4_on_device to $mountd_on_device (${unused_loop_device})"
        $ADB shell "losetup ${unused_loop_device} $ext4_on_device" || \
            { throw "[$tag] losetup failed"; }
        $ADB shell "mount -o loop ${unused_loop_device} $mountd_on_device" || \
            { throw "[$tag] mount failed"; }
    else
        throw "[$tag] Unable to find unused loop device"
    fi
    logi "[$tag] completed"
}

function umount_all_ext4 () {
    sleep 5
    for candidate in $($ADB shell "ls $EXT4_ROOT_ON_DEVICE/*.ext4"); do
        local cname="$(basename $candidate .ext4)"
        if [ "$cname" != "system" ]; then
            $ADB shell umount $MOUNT_ROOT_ON_DEVICE/$cname >/dev/null 2>&1
        fi
    done
    $ADB shell umount "$MOUNT_ROOT_ON_DEVICE" >/dev/null 2>&1
}
function on_exit () {
    if [ "$PKGUP_DEBUG" != "true" ]; then
        rm -rf $WORK_D
    fi
    umount_all_ext4
}
trap on_exit EXIT
# ================================================

setup_device_for_composition

# We have to modify system.img in order to add some property to default.prop.
# And we would like to mount system.img at first.
$SIMG2IMG "$ORIG_SYSTEM_IMG" "$WORK_D/system.ext4" || \
    { throw "simg2img: failed to convert system.img to ext4"; }
mount_ext4 "$WORK_D/system.ext4" "$MOUNT_ROOT_ON_DEVICE"

for preinstall_file in "${PREINSTALL_FILES[@]}"; do
    # ROM1_system_priv-app.txt
    # -> target_partition -> system
    # -> app_root -> priv-app
    logi "preinstall list: $preinstall_file"
    partition=$(basename $preinstall_file | cut -d '.' -f 1 | cut -d '_' -f 2)
    app_root=$(basename $preinstall_file | cut -d '.' -f 1 | cut -d '_' -f 3)

    # Convert original *.img to ext4 file format
    orig_img="$ORIG_IMG_DIR/${partition}.img"
    orig_ext4="$WORK_D/${partition}.ext4"
    test -f $orig_img || { throw "unable to find $orig_img on target archive."; }
    if [ -f $orig_ext4 ]; then
        logi "already exist: $orig_ext4"
        logi "skipping conversion & mount operation."
    else
        $SIMG2IMG $ORIG_IMG_DIR/${partition}.img $WORK_D/${partition}.ext4 || \
            { throw "simg2img: failedto convert ${partition}.img to ext4"; }
        mount_ext4 "$WORK_D/${partition}.ext4" "$MOUNT_ROOT_ON_DEVICE/$partition"
    fi
    # Embed test applications to ext4
    add_all_apks "$preinstall_file" "$partition" "$app_root" || \
        { throw "failed to embed test applications into the ROM"; }

done

set_adb_no_secure $MOUNT_ROOT_ON_DEVICE/default.prop

cp -a $ORIG_ROM_ROOT_D/* "$MODIFIED_ROM_ROOT_D"

logi "[run_build] composition completed. unmounting all images from the device..."
umount_all_ext4
logi "[run_build] pulling composed images from device..."
$ADB pull $EXT4_ROOT_ON_DEVICE $COMPOSITION_D >/dev/null 2>&1
pulled_images=$(find $COMPOSITION_D -type f -name "*.ext4")
logi "pulled_images: $pulled_images"

logi "[run_build] convert modified ext4 images into img format."
for ext4_image in $(echo $pulled_images); do
    fname=$(basename $ext4_image .ext4).img
    #TODO
    dest_img=$MODIFIED_ROM_ROOT_D/$(basename $ORIG_IMG_DIR)/$fname
    logi "convert($fname): -> $dest_img"
    if [ -f "$dest_img" ]; then
        logi "convert($fname): replace existing $dest_img"
        rm "$dest_img"
    fi
    $EXT2SIMG "$ext4_image" "$dest_img" || \
        { throw "convert($fname): failed to convert $ext4_image to img format"; }
    logi "convert($fname): completed"
done

# TODO
zip_update_images $MODIFIED_ROM_ROOT_D/$(basename $ORIG_IMG_DIR)
logi "[run_build] completed"
