#!/usr/bin/python
# -*- coding: utf-8 -*-

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

import difflib
import glob
import json
import os
import sys
import traceback

from optparse import OptionParser
from collections import defaultdict

from common import TestFormat
from common import RawTestOutput
from validate import PartitionInfo
from validate import PreInstallPartitionHandler
from validate import OsHandler

import logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
log = logging.getLogger("Checker")


class ResultReader(object):

    def __init__(self):
        pass

    def read(self, result_d):
        fpaths = self._get_file_paths(result_d)
        if not fpaths:
            raise ValueError("no test result found in %s" % result_d)

        results = defaultdict(lambda: defaultdict(list))

        for fpath in fpaths:
            test_step = self._get_test_step(fpath)
            self._start_test_step(test_step)

            with open(fpath, "rU") as lines:
                buf = []

                for line in lines:
                    if RawTestOutput.RE_START_PACKAGE_ENTRY.match(line):
                        m = RawTestOutput.RE_SERIAL_NUMBERED_PACKAGES.search(line)
                        if not m:
                            raise ValueError("unable to find package name from start tag.")
                        self._start_package(m.group(1))
                    buf.append(self._line_filter(line))

                    if RawTestOutput.RE_END_PACKAGE_ENTRY.match(line):
                        m = RawTestOutput.RE_SERIAL_NUMBERED_PACKAGES.search(line)
                        if not m:
                            raise ValueError("unable to find package name from end tag.")
                        results[test_step][m.group(1)] = buf
                        self._end_package(m.group(1), buf)
                        buf = []

            self._end_test_step(test_step, results[test_step])
        return results

    def _get_file_paths(self, result_d):
        return glob.glob(os.path.join(result_d, "[0-9]_result-*.txt"))

    def _get_test_step(self, fpath):
        return fpath.split('/')[-1]

    def _start_test_step(self, test_step):
        # called before analyzing test step
        pass

    def _end_test_step(self, test_step, test_step_results):
        # called after analyzing test step
        pass

    def _start_package(self, package):
        # called before analyzing package
        pass

    def _end_package(self, package, package_results):
        # called after analyzing package
        pass

    def _line_filter(self, line):
        if RawTestOutput.RE_START_PACKAGE_ENTRY.match(line) or RawTestOutput.RE_END_PACKAGE_ENTRY.match(line):
            return line.replace("-" * 34, "-" * 12)

        m = RawTestOutput.RE_DATA_APP_PATH_WITH_HASH.search(line)
        if m:
            line = line.replace(m.group(1), "[hash]")

        parts = line.split(":")
        if len(parts) > 1:
            if line.find("diag") != -1:
                return "Alert: %s\n" % parts[-1].strip()
            elif line.find("Exist:") != -1:
                return "Exist: %s\n" % line[line.find("/"):].strip()
        return line


class ReferenceReader(ResultReader):

    # -- ApplicationInfo.flags --
    FLAG_SYSTEM = 1 << 0
    FLAG_UPDATED_SYSTEM_APP = 1 << 7
    FLAG_USES_CLEARTEXT_TRAFFIC = 1 << 27

    # -- ApplicationInfo.privateFlags --
    PRIVATE_FLAG_PRIVILEGED = 1 << 3
    # available from android Q
    PRIVATE_FLAG_ALLOW_CLEAR_USER_DATA_ON_FAILED_RESTORE = 1 << 26
    PRIVATE_FLAG_ALLOW_AUDIO_PLAYBACK_CAPTURE = 1 << 27
    PRIVATE_FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE = 1 << 29

    TARGET_PRIVATE_FLAGS = PartitionInfo.TARGET_PRIVATE_FLAGS_PARTITION | \
        PRIVATE_FLAG_ALLOW_CLEAR_USER_DATA_ON_FAILED_RESTORE
    """Bit mask for ApplicationInfo.privateFlags

    If ReferenceReader._enabled_correction is set to True, ReferenceReader once reset these flags and construct value according to API level."""

    def __init__(self, location=None, update_pattern=None):
        super(ReferenceReader, self).__init__()
        self._partition_handler = None
        self._partition_map = None
        self._sdk_version_map = None

        if location:
            self._partition_handler = PreInstallPartitionHandler()
            self._partition_map = self._partition_handler.create_action_to_partition_map(location)
        if update_pattern:
            self._sdk_version_map = OsHandler().create_action_to_sdk_version_map(update_pattern)

        self._enabled_correction = self._partition_map and self._sdk_version_map
        self._partition_info_in_current_teststep = None
        self._sdk_version_in_current_teststep = None
        self._parsing_system_app_entry = False

    def _start_test_step(self, test_step):
        if not self._enabled_correction:
            return

        m = RawTestOutput.RE_TEST_STEP.match(test_step)
        if not m:
            raise ValueError("unable to find action for % s." % test_step)

        action = m.group(1)
        if action not in TestFormat.GROUP_ACTION:
            raise ValueError("unable to find action for % s." % test_step)

        self._partition_info_in_current_teststep = self._partition_handler.get_partition_info(self._partition_map[action])
        self._sdk_version_in_current_teststep = self._sdk_version_map[action]

    def _start_package(self, package):
        self._parsing_system_app_entry = False

    def _line_filter(self, line):
        line = super(ReferenceReader, self)._line_filter(line)
        if not self._enabled_correction:
            return line

        m = RawTestOutput.RE_PREINSTALL_PATH.search(line)
        if m:
            return line.replace(m.group(1), self._partition_info_in_current_teststep.path)

        m = RawTestOutput.RE_APP_FLAGS.match(line)
        if m:
            app_flags = int(m.group(1), 16)
            if app_flags & ReferenceReader.FLAG_SYSTEM:
                self._parsing_system_app_entry = True

            if self._sdk_version_in_current_teststep >= OsHandler.SDK_VERSION_PMR0:
                # FLAG_USES_CLEARTEXT_TRAFFIC is no longer enabled by default on the package with target sdk version 29.
                app_flags &= ~ReferenceReader.FLAG_USES_CLEARTEXT_TRAFFIC

            if not self._parsing_system_app_entry:
                if self._sdk_version_in_current_teststep >= OsHandler.SDK_VERSION_QMR0:
                    app_flags &= ~ReferenceReader.FLAG_UPDATED_SYSTEM_APP

            return line.replace(m.group(1), "0x" + format(app_flags, "08X"))

        m = RawTestOutput.RE_FLAG_UPDATED_SYSTEM_APP.match(line)
        if m:
            if not self._parsing_system_app_entry:
                if self._sdk_version_in_current_teststep >= OsHandler.SDK_VERSION_QMR0:
                    line = line.replace(m.group(1), "false")
            return line

        # Private applciation flag related with pre-installed partition (hex)
        m = RawTestOutput.RE_PRIVATE_FLAGS.match(line)
        if m:
            private_flags = int(m.group(1), 16)
            private_flags = private_flags & ~ReferenceReader.TARGET_PRIVATE_FLAGS

            if self._parsing_system_app_entry:
                if self._sdk_version_in_current_teststep >= OsHandler.SDK_VERSION_PMR0:
                    private_flags |= self._partition_info_in_current_teststep.private_flag_value
            else:
                if self._sdk_version_in_current_teststep >= OsHandler.SDK_VERSION_QMR0:
                    private_flags &= ~ReferenceReader.PRIVATE_FLAG_PRIVILEGED

            if self._sdk_version_in_current_teststep >= OsHandler.SDK_VERSION_QMR0:
                private_flags |= self.PRIVATE_FLAG_ALLOW_CLEAR_USER_DATA_ON_FAILED_RESTORE
                private_flags |= self.PRIVATE_FLAG_ALLOW_AUDIO_PLAYBACK_CAPTURE
                # Backward compatibility for scoped storage access that introduced by android Q won't be enabled on the package with target sdk version 29.
                private_flags &= ~self.PRIVATE_FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE

            return line.replace(m.group(1), "0x" + format(private_flags, '08X'))

        m = RawTestOutput.RE_PRIVATE_FLAG_PRIVILEGED.match(line)
        if m:
            if not self._parsing_system_app_entry:
                if self._sdk_version_in_current_teststep >= OsHandler.SDK_VERSION_QMR0:
                    line = line.replace(m.group(1), "false")
            return line

        if not self._parsing_system_app_entry:
            return line

        # Private applciation flag related with pre-installed partition (boolean)
        m = RawTestOutput.RE_PRIVATE_FLAG_PARTITION.match(line)
        if m:
            flg_value = "false"
            if self._sdk_version_in_current_teststep >= OsHandler.SDK_VERSION_PMR0:
                if self._partition_info_in_current_teststep.private_flag_partition_name == m.group(1):
                    flg_value = "true"
            else:
                pass

            return line.replace(m.group(2), flg_value)

        return line


class ResultChecker(object):
    """
    Create side-by-side diff between references and test results.

    * expect following directory structure
    reference_d/
        +-- 1_result-ROM1.txt
        +-- 2_result-ROM1_Op1.txt
        +-- 3_result-ROM1_Op2.txt
        +-- 4_result-ROM2.txt
        +-- 5_result-ROM2_Op1.txt
        +-- 6_result-ROM2_Op2.txt
    """

    def __init__(self):
        self.enable_context_diff = False
        self.context_lines = 3

        self.reference_d = None
        self.report_d = "."
        self.export_data_path = None

        self.location = None
        self.update_pattern = None

    def _to_smaller_font(self, diff):
        orig_css = "table.diff {font-family:Courier; border:medium;}"
        mod_css = "table.diff {font-family:Courier; border:medium; font-size:small}"
        return diff.replace(orig_css, mod_css)

    def run(self, reference_d, result_d):
        log.info("reference: %s" % reference_d)
        log.info("test_result: %s" % result_d)
        try:
            if not os.path.isdir(self.report_d):
                os.makedirs(self.report_d)

            reference = ReferenceReader(self.location, self.update_pattern).read(reference_d)
            test_result = ResultReader().read(result_d)

            only_reference_has_files = list(set(reference.keys()) - set(test_result.keys()))
            if only_reference_has_files:
                log.warn("Some files are not contained on test results")
                for f in only_reference_has_files:
                    log.warn(" - %s" % f)

            only_test_result_has_files = list(set(test_result.keys()) - set(reference.keys()))
            if only_test_result_has_files:
                log.warn("Some files are not contained on reference results")
                for f in only_test_result_has_files:
                    log.warn(" - %s" % f)

            failure_pkgs = defaultdict(lambda: defaultdict(str))
            log.info("Checking test steps")
            for test_step in sorted(reference.keys()):
                if test_step not in test_result:
                    log.error("step:%s not in test result" % test_step)

                from_lines = []
                from_title = "reference_result"
                to_lines = []
                to_title = "test_result"

                for pkg in sorted(reference[test_step].keys()):
                    ref = reference[test_step][pkg]
                    target = test_result[test_step][pkg]
                    udiff = difflib.unified_diff(ref, target, from_title, to_title, n=0)
                    failure_pkgs[pkg][test_step] = "".join(udiff).strip()
                    from_lines.extend(ref)
                    to_lines.extend(target)

                html_diff = difflib.HtmlDiff().make_file(
                    from_lines, to_lines,
                    from_title,
                    to_title,
                    numlines=self.context_lines,
                    context=self.enable_context_diff)

                if html_diff.find("No Differences Found") != -1:
                    log.info("[OK] %s" % test_step)
                else:
                    fname = test_step.replace("txt", "html")
                    dest = os.path.join(self.report_d, fname)
                    with open(dest, "w") as f:
                        f.write(self._to_smaller_font(html_diff))

                    log.info("[NG] %s -> diff: %s" % (test_step, dest))

            if self.export_data_path:
                log.info("writing diff from expected result to: %s" % self.export_data_path)
                with open(self.export_data_path, "w") as f:
                    f.write(json.dumps(failure_pkgs, sort_keys=True, indent=4))

        except Exception:
            log.error(traceback.format_exc())
            log.error("Failed to compare test results")
            return False

        return True

if __name__ == "__main__":
    checker = ResultChecker()

    usage = "%s [opts] path/to/the/reference path/to/the/test_result" % os.path.basename(sys.argv[0])
    parser = OptionParser(usage=usage)
    parser.add_option("--enable-context-diff", dest="enable_context_diff", action="store_true")
    parser.add_option("--context_lines", dest="context_lines", action="store", type="int")
    parser.add_option("--report-d", dest="report_d", action="store", type="string")
    parser.add_option("--logfile", dest="logfile", action="store", type="string")
    parser.add_option("--export-data-path", dest="export_data_path", type="string", default="comparation.json")
    parser.add_option("--location", dest="location", action="store", type="string")
    parser.add_option("--update-pattern", dest="update_pattern", action="store", type="string")

    try:
        opts, args = parser.parse_args()
        if len(args) != 2:
            raise ValueError("illegal argument: %s" % args)

        if opts.enable_context_diff:
            checker.enable_context_diff = True
        if opts.context_lines:
            checker.context_lines = opts.context_lines
        if opts.report_d:
            checker.report_d = opts.report_d
        if opts.logfile:
            h = logging.FileHandler(opts.logfile, "a+")
            h.level = logging.INFO
            log.addHandler(h)
        if opts.export_data_path:
            checker.export_data_path = opts.export_data_path
        if opts.location:
            checker.location = opts.location
        if opts.update_pattern:
            checker.update_pattern = opts.update_pattern
    except:
        log.error(traceback.format_exc())
        parser.print_help()
        sys.exit(1)

    if not checker.run(args[0], args[1]):
        sys.exit(1)
