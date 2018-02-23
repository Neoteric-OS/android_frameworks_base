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

# This script creates a set of test applications.
# -- Brief usage --
# cd app-generate/
# export SDK_PATH=<path/to/sdk>
# export NDK_PATH=<path/to/ndk>
# bash app-generate.bash
#
# -- Required Parameters -------
# SDK_ROOT : Absolute path of Android SDK
# NDK_ROOT : Absolute path of Android NDK
# TESTCASE_COUNT : (default.10)
# MIN_VERSION : (default. 1)
# MAX_VERSION : (default. 10)
# PACKAGE_BASE : (default. com.android.tests.pkgup)
# CLASSNAME : (default. TestApp)
# ABI_LIST : (default. "armeabi armeabi-v7a arm64-v8a")
# MIN_SDK_VERSION : (default. 14)
# TARGET_SDK_VERSION : (default. 26)
# -- Options --------------------
# OPT_SAVE_SRC : (default. false)

if [ "$PKGUP_DEBUG" == "true" ]; then
    echo "enabled PKGUP_DEBUG" 1>&2
    export PS4='+[$(basename ${BASH_SOURCE}):${LINENO}]: '
    set -x
fi
# ================================================
# Include common functions
# ================================================
SCRIPT_D=$(cd $(dirname ${BASH_SOURCE:-$0}); pwd)

# default test parameters
: ${TESTCASE_COUNT:=40}
: ${MIN_VERSION:=1}
: ${MAX_VERSION:=10}
: ${PACKAGE_BASE:="com.android.tests.pkgup"}
: ${CLASSNAME:="TestApp"}
: ${ABI_LIST:="armeabi armeabi-v7a arm64-v8a"}
: ${MIN_SDK_VERSION:=14}
: ${TARGET_SDK_VERSION:=26}
: ${OPT_SAVE_SRC:="false"}

# mandatory parameters
: ${SDK_ROOT:?}
: ${NDK_ROOT:?}

export ANDROID_HOME=$SDK_ROOT
export PATH="$PATH:$SDK_ROOT:$NDK_ROOT"

function deploy_gradlew(){
    local GRADLE_D=$SCRIPT_D/bin
    local GRADLE_ARCHIVE=gradle-3.3-bin.zip
    local GRADLE_ARCHIVE_BIN_D=gradle-3.3/bin

    rm -rf $GRADLE_D
    mkdir $GRADLE_D
    wget -nv -nc -O $GRADLE_D/$GRADLE_ARCHIVE https://services.gradle.org/distributions/$GRADLE_ARCHIVE
    unzip -q -d $GRADLE_D $GRADLE_D/$GRADLE_ARCHIVE
    pushd $GRADLE_D
    ./$GRADLE_ARCHIVE_BIN_D/gradle wrapper
    popd
    export GRADLEW=$GRADLE_D/gradlew
    rm $GRADLE_D/$GRADLE_ARCHIVE
}

deploy_gradlew
: ${GRADLEW:?}
echo $GRADLEW
test -e $GRADLEW || { echo '$GRADLEW does not exists'; exit 1; }
which ndk-build || { echo 'ndk-build does not exists in $PATH'; exit 1; }

function on_exit(){
    local status=$?
    echo "exit $status"
}
function on_error(){
    local status=$?
    echo "error line $1: command exited with status $status"
    exit 1
}

trap 'on_exit' EXIT
trap 'on_error $LINENO' ERR
if [ -d $SCRIPT_D/results ]; then
    echo "$(date) output directory: $SCRIPT_D/results already exists."
    exit 1
fi
mkdir $SCRIPT_D/results

echo "### $(date) build start"

work_dir=$SCRIPT_D/tmp_

# Generator base arguments
base_arg=" --min-sdk-version=$MIN_SDK_VERSION"
base_arg=" $base_arg --target-sdk-version=$TARGET_SDK_VERSION"
base_arg=" $base_arg --classname=$CLASSNAME"
base_arg=" $base_arg -d $work_dir"

for x in $(seq 1 $TESTCASE_COUNT); do
    x=$(printf "%03d" $x)
    pkg="$PACKAGE_BASE$x"

    for ver in $(seq $MIN_VERSION $MAX_VERSION); do
        echo "### $(date) Building ${CLASSNAME}$x version $ver"

        rel_path="results/${pkg}/ver${ver}"
        dest="$SCRIPT_D/$rel_path"

        args="$base_arg --app-name=${CLASSNAME}$x --lib-name=${CLASSNAME}$x"
        args=" $args --package=$pkg --version-code=$ver --version-name=$ver"
        python $SCRIPT_D/generator.py $args
        mkdir -p $dest

        # archive source code
        if [ "$OPT_SAVE_SRC" == "true" ]; then
            tar cvfz src.tar.gz $work_dir
            mv src.tar.gz $dest
        fi
        pushd $work_dir

        ndk-build APP_ABI="${ABI_LIST}" &> ndk-build.log
        $GRADLEW clean assembleRelease &> gradlew-assemble.log
        apkname="$(ls $work_dir/build/outputs/apk/*.apk)"
        mv $apkname $dest
        libname="$work_dir/libs"
        mv $libname $dest

        echo "### $(date) done"
        popd
        rm -rf $work_dir
    done
done

rm -rf $SCRIPT_D/bin
