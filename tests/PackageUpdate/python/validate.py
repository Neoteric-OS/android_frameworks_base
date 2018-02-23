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


class PreInstallPartitionHandler(OptionValidator):

    SUPPORTED_PARTITIONS = ["system", "oem", "odm", "vendor", "product"]
    ENABLE_SHORT_NOTATION = True


    def validate(self, userinput):
        pts = []

        for pt in userinput.split(OptionValidator.SEPARATOR):
            if pt in PreInstallPartitionHandler.SUPPORTED_PARTITIONS:
                pts.append(pt)
            elif PreInstallPartitionHandler.ENABLE_SHORT_NOTATION:
                for spt in PreInstallPartitionHandler.SUPPORTED_PARTITIONS:
                    if pt == spt[0:3]:
                        pts.append(spt)
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


class OsHandler(OptionValidator):

    SDK_VERSION_OMR0 = 26
    SDK_VERSION_PMR0 = 28

    SUPPORTED_VERSIONS = {"o": SDK_VERSION_OMR0, "p": SDK_VERSION_PMR0}


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
