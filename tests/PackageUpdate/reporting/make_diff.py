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

import collections
import difflib
import glob
import json
import os
import re
import sys
import traceback

from optparse import OptionParser
from collections import defaultdict

import logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
log=logging.getLogger("Checker")

class ResultChecker():

    def __init__ (self):
        self.enable_context_diff = False
        self.enable_multi_user = False
        self.context_lines = 3

        """
        * expect following directory structure
        reference_d/
            +-- 1_result-ROM1.txt
            +-- 2_result-ROM1_Op1.txt
            +-- 3_result-ROM1_Op2.txt
            +-- 4_result-ROM2.txt
            +-- 5_result-ROM2_Op1.txt
            +-- 6_result-ROM2_Op2.txt
        """
        self.reference_d = None
        self.report_d = "."
        self.export_data_path = None
        self.base_pkg = "com.android.tests.pkgup"

        self._failed = False
        self._re_start_tag = re.compile("^(%s[0-9]+)\.Host : #\[START\]-+#$" % re.escape(self.base_pkg))
        self._re_end_tag = re.compile("^(%s[0-9]+)\.Host : #\[END\]-+#$" % re.escape(self.base_pkg))
        self._re_serial_numbered_packages = re.compile(r"(%s[0-9]+)" % self.base_pkg)
        self._re_data_app_path_with_hash = re.compile("/data/app/%s[0-9]+([_\-0-9a-zA-Z]+)==" % self.base_pkg)

        self._failed_packages = collections.defaultdict(list)
        self._total_packages = collections.defaultdict(list)

    def _line_filter(self, line):
        if self._re_end_tag.match(line) or self._re_end_tag.match(line):
            return line.replace("-" * 34, "-" * 12)

        m = self._re_data_app_path_with_hash.search(line)
        if m:
            line = line.replace(m.group(1), "[hash]")

        parts = line.split(":")
        if len(parts) > 1:
            if line.find("diag") != -1:
                return "Alert: %s\n" % parts[-1].strip()
            elif line.find("Exist:") != -1:
                return "Exist: %s\n" % line[line.find("/"):].strip()
        return line

    def _read_test_results(self, d):
        if self.enable_multi_user:
            fpathes = glob.glob(os.path.join(d, "*/[0-9]_result-*.txt"))
        else:
            fpathes = glob.glob(os.path.join(d, "[0-9]_result-*.txt"))

        if not fpathes:
            raise ValueError("no test result found in %s" % d)

        results = defaultdict(lambda : defaultdict(list))
        for fpath in fpathes:
            if self.enable_multi_user:
                test_step = "_".join(fpath.split('/')[-2:])
            else:
                test_step = fpath.split('/')[-1]

            with open(fpath, "rU") as f:
                lines = []
                for line in f.readlines():
                    if self._re_end_tag.match(line):
                        m = self._re_serial_numbered_packages.search(line)
                        if not m:
                            raise ValueError ("unable to find package name from end tag.")
                        results[test_step][m.group(1)] = lines
                        lines = []
                    lines.append(self._line_filter(line))

        return results

    def _to_smaller_font(self, diff):
        orig_css = "table.diff {font-family:Courier; border:medium;}"
        mod_css = "table.diff {font-family:Courier; border:medium; font-size:small}"
        return diff.replace(orig_css, mod_css)

    def run (self, reference_d, result_d):
        log.info("reference: %s" % reference_d)
        log.info("test_result: %s" % result_d)
        try:
            if not os.path.isdir(self.report_d):
                os.makedirs(self.report_d)

            reference = self._read_test_results(reference_d)
            test_result = self._read_test_results(result_d)

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

            failure_pkgs = defaultdict(lambda : defaultdict(str))
            log.info ("Checking test steps")
            for test_step in sorted(reference.keys()):
                if test_step not in test_result:
                    log.warn ("")
                    continue

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

                html_diff = difflib.HtmlDiff().make_file(from_lines, to_lines,
                    from_title,
                    to_title,
                    numlines = self.context_lines,
                    context  = self.enable_context_diff)

                if html_diff.find("No Differences Found") != -1:
                    log.info ("[OK] %s" % test_step)
                else:
                    fname = test_step.replace("txt", "html")
                    dest = os.path.join(self.report_d, fname)
                    with open(dest, "w") as f:
                        f.write(self._to_smaller_font(html_diff))

                    log.info("[NG] %s -> diff: %s" % (test_step, dest))

            if self.export_data_path:
                log.info("writing diff from expected result to: %s" %  self.export_data_path)
                with open (self.export_data_path, "w") as f:
                    f.write(json.dumps(failure_pkgs, sort_keys=True, indent=4))

        except Exception, e:
            log.error (traceback.format_exc())
            log.error ("Failed to compare test results")

        return False

if __name__=="__main__":
    checker = ResultChecker()

    usage = "%s [opts] path/to/the/reference path/to/the/test_result" % os.path.basename(sys.argv[0])
    parser = OptionParser(usage="usage")
    parser.add_option("--enable-context-diff", dest="enable_context_diff", action="store_true")
    parser.add_option("--context_lines", dest="context_lines",action="store", type="int")
    parser.add_option("--report-d", dest="report_d",action="store", type="string")
    parser.add_option("--logfile", dest="logfile",action="store", type="string")
    parser.add_option("--enable-multi-user", dest="enable_multi_user", action="store_true")
    parser.add_option("--export-data-path", dest="export_data_path", type="string", default="comparation.json")

    opts, args = parser.parse_args()
    if len(args) != 2:
        print usage
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
    if opts.enable_multi_user:
        checker.enable_multi_user = True
    if opts.export_data_path:
        checker.export_data_path = opts.export_data_path

    if not checker.run(args[0], args[1]):
        sys.exit(1)
