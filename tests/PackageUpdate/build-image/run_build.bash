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
    echo "[-a: output all image to dest_dir]" 1>&2
    echo "[-f: update build fingerprint]" 1>&2
    exit 1
}

while getopts afh OPT
do
    case $OPT in
        "a")  OUT_ALL_IMAGE="true"
              echo "[$0] set OUT_ALL_IMAGE=true"
            ;;
        "f")  UPDATE_FINGERPRINT="true"
              echo "[$0] set UPDATE_FINGERPRINT=true"
            ;;
        "h")  usage_exit
            ;;
        \?) usage_exit
            ;;
    esac
done
shift $((OPTIND - 1))

test $# -eq 4 || \
    { echo "[$0] required 4 parameter: <preinstall_filelist> <apk_dir> <src_dir> <dest_dir>"; exit 1;}

: ${OUT_ALL_IMAGE:="false"}
: ${UPDATE_FINGERPRINT:="false"}
PREINSTALL_FILES=( $(echo "$1" | tr -s ':' ' ') )
APK_D="$2"
SRC_D="$3"
DEST_D="$4"

# ================================================
# Include common functions
# ================================================
SCRIPT_D=$(cd $(dirname ${BASH_SOURCE:-$0}); pwd)
UTIL="${SCRIPT_D}/../util.bash"
source $UTIL || { echo "could not load common functions: $UTIL" ; exit 1; }

# ================================================
# Configurations
# ================================================
SIMG2IMG=$(which simg2img) || { throw "simg2img does not exist on the PATH."; }
EXT2SIMG=$(which ext2simg) || { throw "ext2simg does not exist on the PATH."; }

# create directories for build image
WORK_D="$SCRIPT_D/work"
if [ -d $WORK_D ]; then
    sudo rm -rf $WORK_D
fi
mkdir -p $WORK_D

MOUNT_D=$WORK_D/ext4_sys
: ${SLEEP_TIME_FOR_UMOUNT:=5}

# ================================================
#  Functions
# ================================================
function add_apk () {
    local src_file=$1
    local dest_root=$2
    local dest_path=$3
    local ref=$4

    sudo mkdir -p "$dest_root/$dest_path"
    sudo cp "$src_file" "$dest_root/$dest_path"
    find "$dest_root/${dest_path%%/*}" -type d | xargs sudo chmod 755
    find "$dest_root/${dest_path%%/*}" -type d | xargs sudo chown --reference="$ref"
    sudo chmod 644 "$dest_root/$dest_path/$(basename ${src_file})"
    sudo chcon -R --reference="$ref" "$dest_root/${dest_path%%/*}"
}

function add_all_apks () {
    local tag="add_all_apks"

    local preinstall_file=$1
    local src_root=$2
    local dest_path=$(echo $(basename $1) | cut -d '_' -f 2)/$(echo $(basename $1 .txt) | cut -d '_' -f 3)
    local dest_dir=$3/$dest_path
    local ref=$(dirname $dest_dir)/app

    if [ ! -d "$ref" ]; then
        { loge "[$tag] /$(dirname $dest_path)/app not not exit"; return 1; }
    fi
    if [ ! -d "$dest_dir" ]; then
        sudo mkdir -p "$dest_dir"
        sudo chmod 755 "$dest_dir"
        sudo chown --reference="$ref" "$dest_dir"
        sudo chcon --reference="$ref" "$dest_dir"
    fi

    IFS=','
    while read line
    do
        set $line
        local src_file=${src_root}/$1
        if [ ! -f "$src_file" ]; then
            { loge "[$tag] apk not found: $src_file"; return 1; }
        fi
        add_apk "$src_file" "$dest_dir" "$2" "$ref"
        if [ $? -eq  0 ]; then
            logi "add $1 to /${dest_path}/$2"
        else
            return 1
        fi
    done < "$preinstall_file"
}

function copy_images () {
    local src_d=$1
    local dst_d=$2

    cp $src_d/*.img "$dst_d"
    local src_update_img_d=$(find $src_d -mindepth 1 -maxdepth 1 -type d)
    local dst_update_img_d=$dst_d/$(basename $src_update_img_d)
    mkdir -p "$dst_update_img_d"
    cp $src_update_img_d/* "$dst_update_img_d"
}

function zip_update_images () {
    local update_img_d=$1
    zip -j "$(dirname $update_img_d)/$(basename $update_img_d).zip" $update_img_d/*
    rm -rf "$update_img_d"
}

function set_adb_no_secure () {
    local default_prop=$(readlink -f $1)
    local modified_prop=$WORK_D/default.prop

    sudo grep -v "ro.adb.secure=" "$default_prop" > "$modified_prop"
    echo ro.adb.secure=0 >> "$modified_prop"
    sudo cp "$modified_prop" "$default_prop"
}

function umount_ext4 () {
    sleep ${SLEEP_TIME_FOR_UMOUNT}
    for file in $WORK_D/*.ext4; do
        if [ "$(basename $file .ext4)" != "system" ]; then
            sudo umount $MOUNT_D/$(basename $file .ext4)
        fi
    done
    sudo umount "$MOUNT_D"
}

# ================================================

exit_if_not_dir "$APK_D"
exit_if_not_dir "$SRC_D"
mkdir -p "$DEST_D"

# system img
SYSTEM_IMG=$(find $SRC_D -type f -name "system.img")
if [ -z "$SYSTEM_IMG" -o $(echo $SYSTEM_IMG | wc -l) -ne 1 ]; then
    { loge "not found exactly one system.img"; exit 1; }
fi
UPDATE_IMG_DIR=$(dirname "$SYSTEM_IMG")

# simg2img, mount
SYSTEM_EXT4=$WORK_D/system.ext4
$SIMG2IMG "$SYSTEM_IMG" "$SYSTEM_EXT4" || { throw "failed to simg2img"; }
sudo mkdir -p "$MOUNT_D"
sudo mount -w -o loop "$SYSTEM_EXT4" "$MOUNT_D"

trap umount_ext4 EXIT

for preinstall_file in "${PREINSTALL_FILES[@]}"; do
    partition=$(echo $(basename $preinstall_file) | cut -d '_' -f 2)
    if [ -f $UPDATE_IMG_DIR/${partition}.img \
            -a ! -f $WORK_D/${partition}.ext4 ]; then
        $SIMG2IMG $UPDATE_IMG_DIR/${partition}.img $WORK_D/${partition}.ext4 || \
            { throw "failed to simg2img"; }
        sudo mount -w -o loop $WORK_D/${partition}.ext4 $MOUNT_D/$partition
    fi
done

# add apk
for preinstall_file in "${PREINSTALL_FILES[@]}"; do
    logi "preinstall_file: $preinstall_file"
    test -f "$preinstall_file" || { throw "not found: $preinstall_file"; }
    add_all_apks "$preinstall_file" "$APK_D" "$MOUNT_D" || { exit 1; }
done

# set adb no_secure
set_adb_no_secure $MOUNT_D/default.prop

# update fingerprint
if [ "$UPDATE_FINGERPRINT" == "true" ]; then
    bash ${SCRIPT_D}/update_fingerprint.bash $MOUNT_D/system/build.prop
fi

# umount, ext2simg
umount_ext4

trap EXIT

if [ "$OUT_ALL_IMAGE" == "true" ]; then
    copy_images "$SRC_D" "$DEST_D"
fi

for file in $WORK_D/*.ext4; do
    if [ "$OUT_ALL_IMAGE" == "true" ]; then
        dest_img=$DEST_D/$(basename $UPDATE_IMG_DIR)/$(basename $file .ext4).img
    else
        dest_img=$DEST_D/$(basename $file .ext4).img
    fi
    if [ -f "$dest_img" ]; then
        rm "$dest_img"
    fi
    $EXT2SIMG "$file" "$dest_img" || { throw "failed to ext2simg"; }
done

if [ "$OUT_ALL_IMAGE" == "true" ]; then
    zip_update_images $DEST_D/$(basename $UPDATE_IMG_DIR)
fi

sudo rm -rf $WORK_D
