#!/bin/bash
#
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
#
cd "${ANDROID_BUILD_TOP}/frameworks/base"

CLANG_FORMAT="${ANDROID_BUILD_TOP}/tools/repohooks/tools/clang-format.py"
CPPLINT="${ANDROID_BUILD_TOP}/tools/repohooks/tools/cpplint.py"
LOCAL_DIR="$( dirname ${BASH_SOURCE} )"
COMMIT="${1:-HEAD}"
ALL_FILES="$(git show --diff-filter=d --name-only --pretty=format: "$COMMIT" -- "$LOCAL_DIR")"
CPP_FILES="$(echo "$ALL_FILES" | grep -e '\.cpp' -e '\.h')"

if [ -z "$ALL_FILES" ]; then
    exit 0
fi

# cpplint (caveat: will always check version in working directory)
if [ "$CPP_FILES" ]; then
    $CPPLINT --quiet $CPP_FILES
    if [ $? -ne 0 ]; then
        exit 1
    fi
fi

# clang-format
$CLANG_FORMAT --style=file --commit "$COMMIT"
if [ $? -ne 0 ]; then
    exit 1
fi
