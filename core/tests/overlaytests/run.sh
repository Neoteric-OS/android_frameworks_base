#!/bin/bash
set -e

help()
{
    cat <<EOF
NAME
    run.sh - Execute RRO test cases

SYNOPSIS
    run.sh [(-c | -C | --color | --no-color)]
           [(-r | -R | --rebuild | --no-rebuild)]
           [(-j | -n | -f <pattern> | --java-only | --native-only | --filter=<pattern>)]
    run.sh (-l | --list)
    run.sh (-h | --help)

DESCRIPTION
    Execute Runtime Resource Overlay tests.

    The tests are divided into native and Java tests. The Java tests will
    prepare the file system, reboot the device, and trigger Java instrumentation
    tests. This is time consuming but will verify the entire Android boot ->
    package manager -> application launch chain. Lower layers can be tested
    much quicker by only running the native tests.

OPTIONS
    -h, --help
        Display this text and exit.

    -c, --color
        Show colored output. Default if connected to a terminal.

    -C, --no-color
        Turn off colored output. Default if not connected to a terminal.

    -r, --rebuild
        Trigger a rebuild and sync of test sources. This is the default.

    -R, --no-rebuild
        Do not rebuild and sync the tests before running them. The default is
        to build all tests.

    -l, --list
        Show a list of available test cases. Useful for creating filters.

    -f <pattern>, --filter=<pattern>
        Limit the set of tests to run as specified by <pattern>. See the gtest
        manual on '--gtest_filter' for the format and possibilities of
        <pattern>.

    -j, --java-only
        Limit the set of tests to only include the Java tests. The default is
        to run all tests.

    -n, --native-only
        Limit the set of tests to only include the native tests. The default is
        to run all tests.
EOF
}

prepare_device()
{
    if [[ "$(adb root)" != "adbd is already running as root" ]]; then
        sleep 2
        adb wait-for-device
    fi
    adb remount
}

build()
{
    local makefile="$(readlink -f $(dirname $0))/Android.mk"
    cd "${ANDROID_BUILD_TOP}" 2>&1 >/dev/null
    ONE_SHOT_MAKEFILE="${makefile}" make -j24 -f build/core/main.mk all_modules
}

sync()
{
    prepare_device
    adb push "${OUT}/data/nativetest/rro_tests" /data/nativetest/rro_tests
}

list_tests()
{
    adb shell /data/nativetest/rro_tests/rro_tests --gtest_list_tests
}

run()
{
    prepare_device
    adb shell /data/nativetest/rro_tests/rro_tests $color $filter
}

parse_args()
{
    rebuild=1

    if [[ -t 1 ]]; then
        color='--gtest_color=yes'
    else
        color='--gtest_color=no'
    fi

    filter=''

    local args="$(getopt -o hcCrRjnlf: -l help,color,no-color,rebuild,no-rebuild,java-only,native-only,list,filter: -- "$@")"
    eval set -- "$args"
    while true; do
        case "$1" in
            --) break;;
            -h|--help) help; exit 0;;
            -c|--color) color='--gtest_color=yes';;
            -C|--no-color) color='--gtest_color=no';;
            -r|--rebuild) rebuild=1;;
            -R|--no-rebuild) rebuild=0;;
            -j|--java-only) filter='--gtest_filter=*Java*.*';;
            -n|--native-only) filter='--gtest_filter=*Native*.*';;
            -l|--list) list_tests; exit 0;;
            -f|--filter) filter="--gtest_filter=$2"; shift;;
        esac
        shift
    done
}

if [[ -z "${ANDROID_BUILD_TOP}" ]]; then
    echo "error: please run lunch before running this script" >&2
    exit 1
fi

parse_args $@
if [[ $rebuild -eq 1 ]]; then
    build
    sync
fi
run
