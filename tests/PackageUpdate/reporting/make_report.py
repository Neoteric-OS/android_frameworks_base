#!/usr/bin/env python
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

import sys
import json
import os
import glob
import re
from collections import defaultdict

import logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
log=logging.getLogger("make_report")

from optparse import OptionParser

HTML_CLASS_TABLE_TESTRESULT="testcase_result"
HTML_CLASS_ROW_SUBTABLE="result_regular_message"
HTML_CLASS_ROW_FILE_CHECK="result_file_check"
HTML_CLASS_ROW_ALERT="result_alert"

global version_checker
version_checker = None

class VersionChecker(object):

    def __init__(self, path_json):
        if not os.path.isfile(path_json):
            raise ValueError("%s is not file" % path_json)
        with open(path_json, "rU") as f:
            self._matrix = json.load(f)

    def check (self, pkg, action):
        action = action.split("_")[1]
        action = action.replace("-after-reboot", "")
        if action:
            if self._matrix.has_key(pkg):
                if self._matrix[pkg].has_key(action):
                    return str(self._matrix[pkg][action])

        return False

class ExpectedResult (object):

    def __init__ (self, fpath):
        self._matrix = None
        with open(fpath, "rU") as f:
            self._matrix = json.load(f)

    def get_diff_from_reference_by_step (self, pkg, action):
        return self._matrix[pkg][action]

    def get_test_status_by_pkg (self, pkg):
        if not self._matrix.get(pkg):
            return "N/A"
        elif [x for x in self._matrix[pkg].values() if x]:
            return "NG"
        else:
            return "OK"


class ReportWriter (object):

    DUPLICATE_LOGENTRY_DISALLOWED = 0
    DUPLICATE_LOGENTRY_FIRSTWIN = 1
    DUPLICATE_LOGENTRY_LASTWIN = 2

    def __init__(self):
        self.testresult_matrix = defaultdict(lambda : defaultdict(list))
        self.testcase_definition = []

        self.actions = []
        self.template_d = "report_template"

        self.description = "Package update result"
        self.allow_duplicate_logentry = ReportWriter.DUPLICATE_LOGENTRY_DISALLOWED

        self.fota_osupdate_type = "kk2kk"
        self.fota_root_partition = "sys2sys"

        self.PACKAGE_BASE = "com.android.tests.pkgup"
        self.REG_START_TAG = re.compile("^(%s[0-9]+)\.Host : #\[START\]-+#$" % re.escape(self.PACKAGE_BASE))
        self.REG_END_TAG = re.compile("^(%s[0-9]+)\.Host : #\[END\]-+#$" % re.escape(self.PACKAGE_BASE))
        self.display_keys = [
            "Package",
            "VersionCode",
            "SourceDir",
            "flags",
            "FLAG_SYSTEM",
            "privateFlags",
            "PRIVATE_FLAG_PRIVILEGED",
            "DataDir",
            "UserDataHistory",
            "NativeLibDir",
            "NativeLibVersion",
            "Exists",
            "Alert",
        ]
        self.show_file_existence = False
        self.show_assert_message = False
        self.diff_from_reference = None

    def run(self):
        self.actions = sorted(self.actions, key=lambda x: int(x.split("_")[0]))
        table_header, table_rows = self._create_main_table(self.testresult_matrix, self.actions, self.testcase_definition)

        tmpl = os.path.join(self.template_d, "report.tmpl")
        with open(tmpl, "rU") as template:
            t = "".join(template.readlines()) % {
                "table_header": table_header,
                "table_content": "\n".join(table_rows),
                "description": self.description}

            with open("out.html", "w") as f:
                f.write(t)

    def read_testcase_definition (self, fpath):

        delimiter = "\t"
        comment_char = "#"

        testcase_definition = []
        with open(fpath, "rU") as testcase_file:
            for row in testcase_file:
                if row.strip().startswith(comment_char):
                    continue
                testcase_definition.append(row.rstrip().split(delimiter)[1:])

        self.testcase_definition = testcase_definition

    def read_one_teststep(self, fname):
        log.debug("reading result file : %s ..." % fname)
        try:
            if not os.path.isfile(fname):
                raise ValueError("%s is not file" % fname)

            with open(fname, "rU") as f:
                lines = f.readlines()
            if not lines:
                raise ValueError("empty file: %s" % fname)

            fname = os.path.basename(fname)
            idx = fname.split("_")[0]

            if fname in self.actions:
                raise ValueError("File:%s is already added")
            self.actions.append(fname)

            reading_pkg = None

            for l in lines:
                l = l.rstrip()

                start = self.REG_START_TAG.match(l)
                end = self.REG_END_TAG.match(l)

                if start:

                    if reading_pkg:
                        err = "package: %s end tag not found. now: %s" % (reading_pkg, start.group(1))
                        log.error(err)
                        self.testresult_matrix[reading_pkg][fname].append(err)
                        break

                    # start reading one test package
                    reading_pkg = start.group(1)
                    if self.testresult_matrix[reading_pkg].has_key(fname):
                        log.error("[duplicate logentry] package: %s (action %s)" % (reading_pkg, fname))
                        if self.allow_duplicate_logentry == ReportWriter.DUPLICATE_LOGENTRY_DISALLOWED:
                            self.testresult_matrix[reading_pkg][fname].append(err)
                            break
                        elif self.allow_duplicate_logentry == ReportWriter.DUPLICATE_LOGENTRY_FIRSTWIN:
                            log.error("firstwin: skip to next start tag.")
                            reading_pkg = None
                        elif self.allow_duplicate_logentry == ReportWriter.DUPLICATE_LOGENTRY_LASTWIN:
                            log.error("lastwin: clear exsisting information.")
                            self.testresult_matrix[reading_pkg][fname] = []
                        else:
                            raise ValueError("Unsupported operation: --allow-duplicate-logentry=%s" % self.allow_duplicate_logentry)

                    continue

                elif end:
                    reading_pkg = None
                    continue

                elif reading_pkg:
                    self.testresult_matrix[reading_pkg][fname].append(l)
                    continue

                else:
                    pass

            return True

        except Exception, e:
            log.error (traceback.format_exc())

        return False

    def _create_main_table(self, results, filepathes, testcase_definition):

        # 5_result-ROM2_Op1.txt -> (5) ROM2_Op1
        filename_to_caption = lambda x: re.sub(r'([0-9]+)_result-(.+).txt', r'(\g<1>) \g<2>', x)
        table_header = ["<th>Test Case</th>"]
        if self.diff_from_reference:
            table_header.append("<th>Result</th>")
        table_header += ["<th>%s</th>" % filename_to_caption(x) for x in filepathes]
        table_header = "".join(table_header)
        table_rows = []

        sorted_packages = sorted(results.keys(), key=lambda x:int(x.replace(self.PACKAGE_BASE, "")))

        log.info("== Found %s test cases ==" % len(sorted_packages))
        for (i, pkg) in enumerate (sorted_packages):
            row = ["<td>%s</td>" % pkg]
            if self.diff_from_reference:
                status = self.diff_from_reference.get_test_status_by_pkg(pkg)
                log.info ("[%s] %s" % (status, pkg))
                row.append("<td>%s</td>" % status)

            idx_action_without_reboot = 0
            for filepath in filepathes:
                col = "<td>"
                test_action = "(reboot)"
                if filepath.find("reboot") == -1:
                    try:
                        test_action = testcase_definition[i+1][idx_action_without_reboot]
                    except:
                        test_action = "test action not defined"
                    idx_action_without_reboot += 1

                lines = results[pkg].get(filepath, "")
                sub_table = self._create_sub_table(lines, filepath, test_action, pkg)
                col += "\n%s</td>\n" % sub_table
                row.append(col)

            row =  "<tr>\n%s</tr>\n" % "\n".join(row)
            table_rows.append(row)
        return (table_header, table_rows)

    def _line_to_subtable_row(self, line):
        # returns (key, value, html_class)
        line = line.rstrip()
        if not line:
            return False
        elif line.find("flags:") >= 0 and line.find("flags:0x") < 0:
            # (skip) com.android.tests.pkgup1.TestApp : flags:8961605
            # (keep) com.android.tests.pkgup1.TestApp : flags:0x0088BE45
            return False

        parts = line.split(":")
        if len(parts) >= 1:
            if line.find("Exist") >= 0:
                if self.show_file_existence:
                    fpath = line[line.find("/"):].strip()
                    return ("Exists", fpath, HTML_CLASS_ROW_FILE_CHECK)
                return False
            elif line.find("diag") >= 0:
                if self.show_assert_message:
                    return ("Alert", parts[-1].strip(), HTML_CLASS_ROW_ALERT)
                return False
            else:
                return (parts[-2].strip(), parts[-1].strip(), HTML_CLASS_ROW_SUBTABLE)

        else:
            log.error("should not happen %s" % line)

        raise ValueError("unhandled line: %s" % line)

    def _create_sub_table (self, lines, test_stage, test_operation, pkgname):
        """
        @lines: one raw resut e.g:
        com.android.tests.pkgup018.TestApp : Package :com.android.tests.pkgup018
        com.android.tests.pkgup018.TestApp : VersionName :5
        com.android.tests.pkgup018.TestApp : VersionCode :5
        com.android.tests.pkgup018.TestApp : ResourceVersion:5
        com.android.tests.pkgup018.TestApp : SourceDir   :/oem/priv-app/TestApp018/TestApp018.apk
        com.android.tests.pkgup018.TestApp : publicSourceDir   :/oem/priv-app/TestApp018/TestApp018.apk
        com.android.tests.pkgup018.TestApp : DataDir     :/data/user/0/com.android.tests.pkgup018
        com.android.tests.pkgup018.TestApp : NativeLibDir:/oem/priv-app/TestApp018/lib/arm64
        @test_stage: e.g: 6_op2-after-reboot
        @test_operation: e.g: Install-Keep/NA/(reboot)/testcase not found
        """
        rows = []
        for line in lines:
            row = self._line_to_subtable_row(line)
            if row and row[0] in self.display_keys:
                rows.append(row)

        rows = ['<tr class="%s"><td>%s</td><td>%s</td></tr>' % (r[2], r[0], r[1])
            for r in sorted(rows, key = lambda x: self.display_keys.index(x[0]))]

        if self.diff_from_reference:
            row_template = '<tr class="%s"><td colspan="2"><pre>%s</pre></td></tr>'
            try:
                msg = self.diff_from_reference.get_diff_from_reference_by_step(pkgname, test_stage)
            except Exception as e:
                msg = "Failed to take diff from reference: %s" % e
                log.warn(msg)
                log.warn(traceback.format_exc())
            finally:
                if msg:
                    rows.append(row_template % (HTML_CLASS_ROW_ALERT, msg))

        table_header = '<thead><th>%s</th><th /></thead><colgroup><col style="width:30%%;"><col style="width:70%%;"></colgroup>' % test_operation
        return '<table class="%s">%s\n<tbody>%s</tbody></table>\n' % (HTML_CLASS_TABLE_TESTRESULT, table_header, "\n".join(rows))

    def _get_android_version(self, action):
        if self.fota_osupdate_type == "kk2kk":
            android_version = "kitkat"
        elif self.fota_osupdate_type == "kk2l":
            phase = int(action.split("_")[0])
            if phase <= 3:
                android_version = "kitkat"
            elif 3 < phase:
                android_version = "lolipop"
            else:
                raise ValueError("action:%s is invalid" % action)
        elif self.fota_osupdate_type == "l2l":
            android_version = "lolipop"
        else:
            raise ValueError("fota_osupdate_type:%s is unsupported" % self.fota_osupdate_type)
        return android_version

    def _get_root_partition (self, action):

        def _helper(formar, latter):
            phase = int(action.split("_")[0])
            if phase <= 3:
                return formar
            elif 3 < phase:
                return latter
            else:
                raise ValueError("action:%s is invalid" % action)

        if self.fota_root_partition == "sys2sys":
            p = "/system"
        elif self.fota_root_partition == "oem2oem":
            p = "/oem"
        elif self.fota_root_partition == "sys2oem":
            p = _helper("/system", "/oem")
        elif self.fota_root_partition == "oem2sys":
            p = _helper("/oem", "/system")
        else:
            raise ValueError("fota_root_partition_type:%s is unsupported" % self.fota_root_partition)
        return p


if __name__ == "__main__":
    usage = "%prog [options] /path/to/result/dir/ /path/to/testcase"

    parser = OptionParser(usage=usage)
    parser.add_option("--description", dest="description",action="store", type="string")
    parser.add_option("--logfile", dest="logfile",action="store", type="string")
    parser.add_option("--fota-osupdate-type", dest="fota_osupdate_type",action="store", type="string")
    parser.add_option("--fota-root-partition", dest="fota_root_partition",action="store", type="string")
    parser.add_option("--expected-version-matrix", dest="path_expected_ver_json",action="store", type="string")
    parser.add_option("--diff-from-reference", dest="diff_from_reference",action="store", type="string")
    parser.add_option("--allow-duplicate-entry", dest="allow_duplicate_entry",action="store", type="string")
    parser.add_option("--show-file-existence", dest="show_file_existence",action="store_true")
    parser.add_option("--show-assert-message", dest="show_assert_message",action="store_true")

    opts, args = parser.parse_args()
    if len(args) != 2:
        raise ValueError("illegal arguments: %s" % args)

    root_dir = args[0]
    testcase = args[1]
    if not os.path.isdir(root_dir): raise ValueError ("%s is not directory." % root_dir)
    if not os.path.isfile(testcase): raise ValueError ("%s is not file." % testcase)

    logfile_pattern = os.path.join(root_dir, "[0-9]*_result-*.txt")
    logpaths = glob.glob(logfile_pattern)
    if not logpaths:
        raise ValueError("No result files found in %s" % root_dir)

    writer = ReportWriter()
    writer.read_testcase_definition(testcase)

    log.info("Generating test report...")
    log.info("- testcase file: %s" % testcase)
    log.info("- test results directory: %s" % root_dir)

    if opts.description:
        writer.description = opts.description
    if opts.logfile:
        h = logging.FileHandler(opts.logfile, "a+")
        h.level = logging.INFO
        log.addHandler(h)
    if opts.fota_osupdate_type:
        writer.fota_osupdate_type = opts.fota_osupdate_type
    if opts.fota_root_partition:
        writer.fota_root_partition = opts.fota_root_partition
    if opts.path_expected_ver_json:
        version_checker = VersionChecker(opts.path_expected_ver_json)
    if opts.diff_from_reference:
        log.info("- reference file: %s" % opts.diff_from_reference)
        writer.diff_from_reference = ExpectedResult(opts.diff_from_reference)
    if opts.allow_duplicate_entry:
        writer.allow_duplicate_logentry = int(opts.allow_duplicate_entry)
    writer.show_file_existence = opts.show_file_existence

    for f in logpaths:
        writer.read_one_teststep(f)

    writer.run()
