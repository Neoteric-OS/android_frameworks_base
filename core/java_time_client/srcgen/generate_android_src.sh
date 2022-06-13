#!/bin/bash
# Copyright (C) 2022 The Android Open Source Project
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

if [[ -z "${ANDROID_BUILD_TOP}" ]]; then
    echo "Missing environment variables. Did you run build/envsetup.sh and lunch?" >&2
    exit 1
fi

PROJECT_DIR=frameworks/base/core/java_time_client

PACKAGE_TRANSFORMATIONS="\
    com.google.time.client:com.android.time.client \
"

MODULE_DIRS="\
    android \
"


SOURCE_DIRS="\
    java \
    javatests \
"

# Repackage the project's source.
source ${ANDROID_BUILD_TOP}/tools/currysrc/scripts/repackage-common.sh

#echo "Reformatting generated code to adhere to format required by the preupload check"
#cd ${PROJECT_DIR}
#CLANG_STABLE_BIN=${ANDROID_BUILD_TOP}/prebuilts/clang/host/linux-x86/clang-stable/bin
#${ANDROID_BUILD_TOP}/tools/repohooks/tools/clang-format.py --fix \
#  --clang-format ${CLANG_STABLE_BIN}/clang-format \
#  --git-clang-format ${CLANG_STABLE_BIN}/git-clang-format \
#  --style file \
#  --working-tree \
#  $(git diff --name-only HEAD | grep -E "^repackaged/")
