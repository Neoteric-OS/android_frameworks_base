#!/bin/bash

ADB=/usr/bin/adb
echo "root device"
adb root
sleep 1
adb wait-for-device

adb shell am start -a com.android.setupwizard.EXIT
sleep 8

adb shell input keyevent 26
sleep 1
adb shell input keyevent 82
sleep 1

adb shell am broadcast -a 'com.google.gservices.intent.action.GSERVICES_OVERRIDE' -e 'location:enable_location_off_warning_dialog' 'false'
sleep 1

adb shell settings put global airplane_mode_on 1
sleep 1

adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true
sleep 1

adb shell settings put system screen_brightness 111
sleep 1

adb shell settings put system notification_light_pulse 0
sleep 1

adb shell settings put system screen_off_timeout 1800000
sleep 1

adb shell settings put system auto_time 0
sleep 1

adb shell settings put system auto_timezone 0
sleep 1

adb shell settings put system accelerometer_rotation 0
sleep 1

adb shell settings put system screen_brightness_mode 0
sleep 1

adb shell settings put secure screensaver_enabled 0
sleep 1

adb shell settings put secure doze_pulse_on_pick_up 0
sleep 1

adb shell settings put secure assist_gesture_enabled 0
sleep 1

adb shell settings put secure system_navigation_keys_enabled 0
sleep 1

adb shell settings put secure camera_lift_trigger_enabled 0
sleep 1

adb shell settings put secure assist_gesture_wake_enabled 0
sleep 1

adb shell settings put secure doze_always_on 0
sleep 1

adb shell settings put secure camera_double_twist_to_flip_enabled 0
sleep 1

adb shell settings put secure location_providers_allowed -gps
sleep 1

adb shell settings put secure location_providers_allowed -network
sleep 1

adb shell settings put secure assist_gesture_silence_alerts_enabled 0
sleep 1

adb shell settings put secure camera_double_tap_power_gesture_disabled 1
sleep 1

adb shell settings put secure doze_enabled 0
sleep 1

adb shell settings put secure wake_gesture_enabled 0
sleep 1

adb shell settings put secure doze_pulse_on_double_tap 0
sleep 1

adb shell settings put global low_power_trigger_level 0
sleep 1

adb shell settings put global wifi_on 0
sleep 1

adb shell settings put global low_power 0
sleep 1

adb shell am broadcast -a 'com.google.gservices.intent.action.GSERVICES_OVERRIDE' -e 'gms:magictether:enable' 'false'
sleep 1

adb shell am broadcast -a 'com.google.gservices.intent.action.GSERVICES_OVERRIDE' -e 'location:collection_enabled' '0'
sleep 1

adb shell am broadcast -a 'com.google.gservices.intent.action.GSERVICES_OVERRIDE' -e 'location:compact_log_enabled' 'true'
sleep 1

adb shell am broadcast -a 'com.google.gservices.intent.action.GSERVICES_OVERRIDE' -e 'gms:cast:mdns_device_scanner:is_enabled' 'false'
sleep 1

adb shell am broadcast -a 'com.google.gservices.intent.action.GSERVICES_OVERRIDE' -e 'gms_icing_extension_download_enabled' 'false'
sleep 1

adb shell pm disable-user com.google.intelligence.sense
sleep 1

adb shell 'echo 1 > /d/clk/debug_suspend'
sleep 1

adb shell svc wifi disable
sleep 1

adb shell service call bluetooth_manager 8
sleep 1

adb shell svc nfc disable
sleep 1

adb shell dumpsys batterystats --enable full-history
sleep 1

adb shell dumpsys deviceidle disable
sleep 1

adb shell pm disable-user com.android.vending
sleep 1

adb shell pm disable-user com.google.android.volta
sleep 1
adb shell am start -a com.android.setupwizard.EXIT
