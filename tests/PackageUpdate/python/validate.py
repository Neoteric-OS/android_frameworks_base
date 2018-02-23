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

from common import TestFormat


class OptionValidator(object):

    SEPARATOR = "2"

    def validate(self, userinput):
        pass


class PartitionInfo(object):

    # available from android P
    PRIVATE_FLAG_OEM = 1 << 17
    PRIVATE_FLAG_VENDOR = 1 << 18
    PRIVATE_FLAG_PRODUCT = 1 << 19
    TARGET_PRIVATE_FLAGS_PARTITION = PRIVATE_FLAG_OEM | PRIVATE_FLAG_VENDOR | PRIVATE_FLAG_PRODUCT

    def __init__(self, path, private_flag_value=0, private_flag_partition_name=None):
        self.path = path
        self.private_flag_value = private_flag_value
        self.private_flag_partition_name = private_flag_partition_name


class PreInstallPartitionHandler(OptionValidator):

    SUPPORTED_PARTITIONS = {
        "system": PartitionInfo("system"),
        "oem": PartitionInfo("oem", PartitionInfo.PRIVATE_FLAG_OEM, "OEM"),
        "vendor": PartitionInfo("vendor", PartitionInfo.PRIVATE_FLAG_VENDOR, "VENDOR"),
        "product": PartitionInfo("product", PartitionInfo.PRIVATE_FLAG_PRODUCT, "PRODUCT"),
        "system_product": PartitionInfo("system/product", PartitionInfo.PRIVATE_FLAG_PRODUCT, "PRODUCT")
    }
    ENABLE_SHORT_NOTATION = True


    def validate(self, userinput):
        pts = []

        partitions = PreInstallPartitionHandler.SUPPORTED_PARTITIONS.keys()
        partitions_for_short_notation = [x for x in partitions if '_' not in x]
        for pt in userinput.split(OptionValidator.SEPARATOR):
            if pt in partitions:
                pts.append(pt)
            elif PreInstallPartitionHandler.ENABLE_SHORT_NOTATION:
                for spt in partitions_for_short_notation:
                    if pt == spt[0:3]:
                        pts.append(spt)
                        break
            else:
                raise ValueError("Unsupported partition specified: %s (%s) " % (userinput, pt))

        if not pts:
            raise ValueError("Unable to find supported partition: %s" % userinput)

        if len(TestFormat.GROUP_BUILD_ACTION) != len(pts):
            raise ValueError("inconsistent between the build action and partition specification")

        return pts

    def create_build_action_to_partition_map(self, userinput):
        partitions = self.validate(userinput)
        return dict(zip(TestFormat.GROUP_BUILD_ACTION, partitions))

    def create_action_to_partition_map(self, userinput):
        partitions = self.validate(userinput)
        map = {}
        for i, partition in enumerate(partitions):
            for action in TestFormat.GROUP_ACTION_PER_ROM[i]:
                map[action] = partition
        return map

    def get_partition_info(self, partition):
        return PreInstallPartitionHandler.SUPPORTED_PARTITIONS[partition]


class OsHandler(OptionValidator):

    SDK_VERSION_OMR0 = 26
    SDK_VERSION_PMR0 = 28
    SDK_VERSION_QMR0 = 29

    SUPPORTED_VERSIONS = {"o": SDK_VERSION_OMR0, "p": SDK_VERSION_PMR0, "q": SDK_VERSION_QMR0}


    def validate(self, update_pattern):
        os_list = []

        for os in update_pattern.split(OptionValidator.SEPARATOR):
            if os in OsHandler.SUPPORTED_VERSIONS:
                os_list.append(os)
            else:
                raise ValueError("Unsupported os version specified: %s (%s) " % (update_pattern, os))

        if not os_list:
            raise ValueError("Unable to find supported os version: %s" % update_pattern)

        if len(TestFormat.GROUP_BUILD_ACTION) != len(os_list):
            raise ValueError("inconsistent between the build action and update_pattern: %s" % update_pattern)

        return os_list

    def create_build_action_to_sdk_version_map(self, update_pattern):
        os_list = self.validate(update_pattern)
        sdk_version_list = []
        for os in os_list:
            sdk_version_list.append(OsHandler.SUPPORTED_VERSIONS[os])
        return dict(zip(TestFormat.GROUP_BUILD_ACTION, sdk_version_list))

    def create_action_to_sdk_version_map(self, update_pattern):
        os_list = self.validate(update_pattern)
        map = {}
        for i, os in enumerate(os_list):
            for action in TestFormat.GROUP_ACTION_PER_ROM[i]:
                map[action] = OsHandler.SUPPORTED_VERSIONS[os]
        return map
