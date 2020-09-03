#!/bin/bash
#
# Copyright (C) 2020 The Android Open Source Project
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

set -e

echo "WARNING, this assumes:"
echo "- you are lunched/etc"
echo "- hwuimacro/block_suspend is already built in the configuration you want to test"
echo "- device system has verity disabled and is writable (needed for init service)"
read -p "ENTER to continue"

cd "$(dirname "$0")"

adb push power-test.rc /system/etc/init/
adb push power-test.sh /system/bin/

adb shell mkdir -p /data/benchmarktest64/hwuimacro
adb push {$ANDROID_PRODUCT_OUT,}/data/benchmarktest64/hwuimacro/hwuimacro
adb push {$ANDROID_PRODUCT_OUT,}/data/nativetest64/block_suspend/block_suspend

adb reboot
adb wait-for-device
adb root

./rock_bottom_blueline.sh
../scripts/prep_generic.sh

adb shell setprop ctl.start hwuimacro.power-test

echo "Disconnect device and leave alone for 30min after:"
date
echo "Output will be in /data/power-test*."

