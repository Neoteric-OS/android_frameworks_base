#! /usr/bin/python

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

import re


class Constant(object):

    BASE_PACKAGE_NAME = "com.android.tests.pkgup"
    BASE_APP_NAME = "TestApp"


class TestFormat(object):

    TEST_ROM1 = "ROM1"
    TEST_ROM1_OP1 = "ROM1_Op1"
    TEST_ROM1_OP2 = "ROM1_Op2"
    TEST_ROM2 = "ROM2"
    TEST_ROM2_OP1 = "ROM2_Op1"
    TEST_ROM2_OP2 = "ROM2_Op2"

    GROUP_ACTION = [
        TEST_ROM1, TEST_ROM1_OP1, TEST_ROM1_OP2,
        TEST_ROM2, TEST_ROM2_OP1, TEST_ROM2_OP2]
    HEADER = ["id"] + GROUP_ACTION
    GROUP_BUILD_ACTION = [TEST_ROM1, TEST_ROM2]
    GROUP_TEST_ACTION = [
        TEST_ROM1_OP1, TEST_ROM1_OP2,
        TEST_ROM2_OP1, TEST_ROM2_OP2]
    GROUP_ACTION_PER_ROM = [
        [TEST_ROM1, TEST_ROM1_OP1, TEST_ROM1_OP2],
        [TEST_ROM2, TEST_ROM2_OP1, TEST_ROM2_OP2]]


class RawTestOutput(object):

    RE_TEST_STEP = re.compile("^[0-9]_result-(\S*).txt$")

    RE_START_PACKAGE_ENTRY = re.compile("^(%s[0-9]+)\.Host : #\[START\]-+#$" % re.escape(Constant.BASE_PACKAGE_NAME))
    RE_END_PACKAGE_ENTRY = re.compile("^(%s[0-9]+)\.Host : #\[END\]-+#$" % re.escape(Constant.BASE_PACKAGE_NAME))
    RE_SERIAL_NUMBERED_PACKAGES = re.compile(r"(%s[0-9]+)" % Constant.BASE_PACKAGE_NAME)
    RE_DATA_APP_PATH_WITH_HASH = re.compile("/data/app/%s[0-9]+([_\-0-9a-zA-Z]+)==" % Constant.BASE_PACKAGE_NAME)

    RE_PREINSTALL_PATH = re.compile("[^_\-=0-9a-zA-Z]/([a-z]+)/(app|priv-app)/%s[0-9]+" % Constant.BASE_APP_NAME)
    RE_APP_FLAGS = re.compile("%s[0-9]+.%s : flags:(0x[0-9a-fA-F]+)" % (Constant.BASE_PACKAGE_NAME, Constant.BASE_APP_NAME))
    RE_FLAG_UPDATED_SYSTEM_APP = re.compile("%s[0-9]+.%s : +FLAG_UPDATED_SYSTEM_APP : (true|false)" % (Constant.BASE_PACKAGE_NAME, Constant.BASE_APP_NAME))
    RE_PRIVATE_FLAGS = re.compile("%s[0-9]+.%s : privateFlags:(0x[0-9a-fA-F]+)" % (Constant.BASE_PACKAGE_NAME, Constant.BASE_APP_NAME))
    RE_PRIVATE_FLAG_PRIVILEGED = re.compile("%s[0-9]+.%s : +PRIVATE_FLAG_PRIVILEGED : (true|false)" % (Constant.BASE_PACKAGE_NAME, Constant.BASE_APP_NAME))
    RE_PRIVATE_FLAG_PARTITION = re.compile("%s[0-9]+.%s : +PRIVATE_FLAG_(OEM|VENDOR|PRODUCT) : (true|false)" % (Constant.BASE_PACKAGE_NAME, Constant.BASE_APP_NAME))
