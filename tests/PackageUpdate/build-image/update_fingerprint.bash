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

# This script modifies build.prop in order to update build fingerprint.
# It is intended to be used by autoflash.bash
if [ "$PKGUP_DEBUG" == "true" ]; then
    echo "enabled PKGUP_DEBUG" 1>&2
    export PS4='+[$(basename ${BASH_SOURCE}):${LINENO}]: '
    set -x
fi

# ================================================
# Include common functions
# ================================================
SCRIPT_D=$(cd $(dirname ${BASH_SOURCE:-$0}); pwd)
UTIL="${SCRIPT_D}/../util.bash"
source $UTIL || { echo "could not load common functions."; exit 1; }

# ================================================
# Parameter
# ================================================
function usage_exit () {
    echo "Usage: $0 'BUILD_PROP'" 1>&2
    exit 1
}

test $# -eq 1 || { usage_exit; }
BUILD_PROP=$1
exit_if_not_found "$BUILD_PROP"

# ================================================
# Configurations
# ================================================
SCRIPT_D=$(cd $(dirname ${BASH_SOURCE:-$0}); pwd)
LOCAL_BUILD_PROP=$SCRIPT_D/build.prop
MODIFIED_BUILD_PROP=$SCRIPT_D/modified.prop

# ================================================
# Functions
# ================================================
function count_up  () {
    local num=$1
    local next_num=$(expr $num + 1)

    if [ ${#next_num} -lt ${#num} ]; then
        for i in $(seq $(expr ${#num} - ${#next_num})); do
            next_num=0${next_num}
        done
    fi
    echo $next_num
}

function on_exit () {
    if [ -f "$LOCAL_BUILD_PROP" ]; then
        rm -f "$LOCAL_BUILD_PROP"
    fi
    if [ -f "$MODIFIED_BUILD_PROP" ]; then
        rm -f "$MODIFIED_BUILD_PROP"
    fi
}

trap on_exit EXIT

# ================================================

sudo cp "$BUILD_PROP" "$LOCAL_BUILD_PROP"
sudo chmod a+r "$LOCAL_BUILD_PROP"
if [ -f "$MODIFIED_BUILD_PROP" ]; then
    rm -f "$MODIFIED_BUILD_PROP"
fi

while read line
do
    if [ $(echo "$line" | grep "^#" | wc -l) -gt 0 ] || [ "$line" == "" ]; then
        continue
    fi
    key=$(echo "$line" | cut -d '=' -f 1)
    value=$(echo "$line" | cut -d '=' -f 2)
    if [ "${key}" == "ro.build.id" ]; then
        BUILD_ID=${value}
    elif [ "${key}" == "ro.build.version.incremental" ]; then
        BUILD_NUMBER=${value}
    elif [ "${key}" == "ro.build.version.release" ]; then
        PLATFORM_VERSION=${value}
    elif [ "${key}" == "ro.build.type" ]; then
        BUILD_TYPE=${value}
    elif [ "${key}" == "ro.build.tags" ]; then
        BUILD_VERSION_TAGS=${value}
    elif [ "${key}" == "ro.product.brand" ]; then
        PRODUCT_BRAND=${value}
    elif [ "${key}" == "ro.product.name" ]; then
        PRODUCT_NAME=${value}
    elif [ "${key}" == "ro.product.device" ]; then
        PRODUCT_DEVICE=${value}
    fi
done < "$LOCAL_BUILD_PROP"

NEW_BUILD_ID=${BUILD_ID%.*}.$(count_up ${BUILD_ID##*.})
if echo "$BUILD_NUMBER" | grep eng.* > /dev/null; then
    NEW_BUILD_NUMBER=eng.${USER:0:6}.$(date +%Y%m%d.%H%M%S)
    NEW_BF_BUILD_NUMBER=${USER:0:6}$(date +%m%d%H%M)
else
    NEW_BUILD_NUMBER=$(count_up $BUILD_NUMBER)
    NEW_BF_BUILD_NUMBER=$NEW_BUILD_NUMBER
fi
NEW_DISPLAY_ID="${PRODUCT_NAME}-${BUILD_TYPE} ${PLATFORM_VERSION} ${NEW_BUILD_ID} ${NEW_BUILD_NUMBER} ${BUILD_VERSION_TAGS}"
NEW_BUILD_DESCRIPTION=$NEW_DISPLAY_ID
NEW_BUILD_FINGERPRINT=${PRODUCT_BRAND}/${PRODUCT_NAME}/${PRODUCT_DEVICE}:${PLATFORM_VERSION}/${NEW_BUILD_ID}/${NEW_BF_BUILD_NUMBER}:${BUILD_TYPE}/${BUILD_VERSION_TAGS}

while read line
do
    if [ $(echo "$line" | grep "^#" | wc -l) -gt 0 ] || [ "$line" == "" ]; then
        echo "$line" >> "$MODIFIED_BUILD_PROP"
        continue
    fi
    key=$(echo "$line" | cut -d '=' -f 1)
    if [ "${key}" == "ro.build.id" ]; then
        echo "${key}=${NEW_BUILD_ID}" >> "$MODIFIED_BUILD_PROP"
    elif [ "${key}" == "ro.build.display.id" ]; then
        echo "${key}=${NEW_DISPLAY_ID}" >> "$MODIFIED_BUILD_PROP"
    elif [ "${key}" == "ro.build.version.incremental" ]; then
        echo "${key}=${NEW_BUILD_NUMBER}" >> "$MODIFIED_BUILD_PROP"
    elif [ "${key}" == "ro.build.date" ]; then
        echo "${key}=$(date)" >> "$MODIFIED_BUILD_PROP"
    elif [ "${key}" == "ro.build.date.utc" ]; then
        echo "${key}=$(date +%s)" >> "$MODIFIED_BUILD_PROP"
    elif [ "${key}" == "ro.build.description" ]; then
        echo "${key}=${NEW_BUILD_DESCRIPTION}" >> "$MODIFIED_BUILD_PROP"
    elif [ "${key}" == "ro.build.fingerprint" ]; then
        echo "${key}=${NEW_BUILD_FINGERPRINT}" >> "$MODIFIED_BUILD_PROP"
    else
        echo "$line" >> "$MODIFIED_BUILD_PROP"
    fi
done < "$LOCAL_BUILD_PROP"

sudo cp "$MODIFIED_BUILD_PROP" "$BUILD_PROP"

