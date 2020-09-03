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




function charge-level() {
    echo "$(cat /sys/class/power_supply/battery/charge_counter) mAh"
}

function power-test() {
    OUT=/data/power-test-output.txt
    JUNK=/data/power-test-junk.txt

    echo "START" > $OUT
    echo "START" > $JUNK

    /data/nativetest64/block_suspend/block_suspend&

    getprop ro.build.fingerprint >> $OUT
    date >> $OUT
    echo "INITIAL CHARGE LEVEL:" >> $OUT
    charge-level >> $OUT

    local now_time=$(date +"%s")
    local end_time=$(($now_time + 60 * 30))

    while [[ $(date +"%s") -lt $end_time ]]; do
        /data/benchmarktest64/hwuimacro/hwuimacro >> $JUNK 2>&1
    done

    date >> $OUT
    echo "FINAL CHARGE LEVEL:" >> $OUT
    charge-level >> $OUT

    echo "END" >> $OUT
    echo "END" >> $JUNK
}

power-test

