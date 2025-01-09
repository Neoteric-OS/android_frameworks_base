#!/bin/bash
# Copyright (C) 2024 The Android Open Source Project
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

#
# Use "ravehleper pta" to create a shell script which:
# - Reads the text "policy" files
# - Convert to java annotations (using sed)
#

set -e


# ${BUILD_CMD:-m} ravenhelper

cd "${ANDROID_BUILD_TOP?\$ANDROID_BUILD_TOP must be set}"

# Add -v or -d as needed.
extra_args="$@"

OUT_SCRIPT="/tmp/pta.sh"

rm -f $OUT_SCRIPT

POLICIES=(
    frameworks/base/ravenwood/texts/ravenwood-common-policies.txt
    frameworks/base/ravenwood/texts/ravenwood-framework-policies.txt
)

SOURCES=(
    frameworks/base/core/java/
    frameworks/base/graphics/java/
)

AAC=frameworks/base/ravenwood/texts/ravenwood-annotation-allowed-classes.txt

expand() {
    local flag="$1"
    shift

    for arg in "$@"; do
        echo "$flag $arg"
    done
}

ravenhelper pta \
    --output-script $OUT_SCRIPT \
    --annotation-allowed-classes-file $AAC \
    $(expand --policy-override-file "${POLICIES[@]}") \
    $(expand --src "${SOURCES[@]}") \
    $extra_args \

if ! [[ -f $OUT_SCRIPT ]] ; then
    # no operations generated.
    exit 0
fi

echo
echo "Created script at $OUT_SCRIPT. Run it with: sh $OUT_SCRIPT"
