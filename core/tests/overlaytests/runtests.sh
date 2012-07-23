#!/bin/bash

adb="adb"
if [[ $# -gt 0 ]]; then
	adb="adb $*" # for setting -e, -d or -s <serial>
fi

function atexit()
{
	local retval=$?

	if [[ $retval -eq 0 ]]; then
		rm $log
	else
		echo "There were errors, please check log at $log"
	fi
}

log=$(mktemp)
trap "atexit" EXIT

function compile_module()
{
	local android_mk="$1"

	echo "Compiling .${android_mk:${#PWD}}"
	ONE_SHOT_MAKEFILE="$android_mk" make -C "../../../../../" files | tee -a $log
	if [[ ${PIPESTATUS[0]} -ne 0 ]]; then
		exit 1
	fi
}

function wait_for_boot_completed()
{
	echo "Rebooting device"
	$adb wait-for-device logcat -c
	$adb wait-for-device logcat | grep -m 1 -e 'PowerManagerService.*bootCompleted' >/dev/null
}

function mkdir_if_needed()
{
	local path="$1"

	if [[ "${path:0:1}" != "/" ]]; then
		echo "mkdir_if_needed: error: path '$path' does not begin with /" | tee -a $log
		exit 1
	fi

	local basename=$(basename "$path")
	local dirname=$(dirname "$path")
	local t=$($adb shell ls -l $dirname | tr -d '\r' | grep -e "${basename}$" | grep -oe '^.')

	case "$t" in
		d) # File exists, and is a directory ...
			# do nothing
			;;
		l) # ... (or symbolic link possibly to a directory).
			# do nothing
			;;
		"") # File does not exist.
			mkdir_if_needed "$dirname"
			$adb shell mkdir "$path"
			;;
		*) # File exists, but is not a directory.
			echo "mkdir_if_needed: file '$path' exists, but is not a directory" | tee -a $log
			exit 1
			;;
	esac
}

function rm_if_needed()
{
	local path="$1"

	if [[ "${path:0:1}" != "/" ]]; then
		echo "rm_if_needed: error: path '$path' does not begin with /" | tee -a $log
		exit 1
	fi
	local t="$($adb shell ls $path | tr -d '\r' | grep -v 'No such file or directory')"

	if [[ "$t" ]]; then
		$adb shell rm -r "$path"
	fi
}

function disable_overlay()
{
	echo "Disabling all overlays"
	rm_if_needed "/vendor/overlay/framework/framework-res.apk"
	rm_if_needed "/vendor/overlay/framework/framework-res.apk"
	rm_if_needed "/data/resource-cache/vendor@overlay@framework@framework-res.apk@idmap"
	rm_if_needed "/data/resource-cache/vendor@overlay@framework@framework-res.apk@1.apk@idmap"
	rm_if_needed "/data/resource-cache/vendor@overlay@framework@framework-res.apk@2.apk@idmap"
}

function enable_overlay()
{
	disable_overlay
	echo "Enabling single overlay"
	mkdir_if_needed "/system/vendor"
	mkdir_if_needed "/vendor/overlay/framework"
	$adb shell ln -s /data/app/com.android.overlaytest.overlay.apk /vendor/overlay/framework/framework-res.apk
}

function enable_multiple_overlays()
{
	disable_overlay
	echo "Enabling multiple overlays"
	mkdir_if_needed "/system/vendor"
	mkdir_if_needed "/vendor/overlay/framework/framework-res.apk"
	$adb shell ln -s /data/app/com.android.overlaytest.overlay.apk /vendor/overlay/framework/framework-res.apk/1.apk
	$adb shell ln -s /data/app/com.android.overlaytest.multipleoverlays.apk /vendor/overlay/framework/framework-res.apk/2.apk
}

function instrument()
{
	local class="$1"

	echo "Instrumenting $class"
	$adb shell am instrument -w -e class $class com.android.overlaytest/android.test.InstrumentationTestRunner | tee -a $log
}

function remount()
{
	echo "Remounting file system writable"
	$adb remount | tee -a $log
}

function sync()
{
	local files=""
	files+=" /data/app/OverlayTest.apk"
	files+=" /data/app/OverlayTest.odex"
	files+=" /data/app/com.android.overlaytest.overlay.apk"
	files+=" /data/app/com.android.overlaytest.multipleoverlays.apk"

	for i in $files; do
		echo "Syncing $i" | tee -a $log
		$adb push $OUT/$i $i
	done
}

# paths below are given relative to this script
cd $(dirname $(readlink -f "$0"))

# some commands require write access, remount once and for all
remount

# build and sync
compile_module "$PWD/OverlayTest/Android.mk"
compile_module "$PWD/OverlayTestOverlay/Android.mk"
compile_module "$PWD/OverlayTestMultipleOverlays/Android.mk"
sync

# instrument test (without overlay)
$adb shell stop
disable_overlay
$adb shell start
wait_for_boot_completed
instrument "com.android.overlaytest.WithoutOverlayTest"

# instrument test (with overlay)
$adb shell stop
enable_overlay
$adb shell start
wait_for_boot_completed
instrument "com.android.overlaytest.WithOverlayTest"

# instrument test (with multiple overlays)
$adb shell stop
enable_multiple_overlays
$adb shell start
wait_for_boot_completed
instrument "com.android.overlaytest.WithMultipleOverlaysTest"

# cleanup
exit $(grep -c -e '^FAILURES' $log)
