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

import csv
import json
import os
import sys
import traceback

from optparse import OptionParser
from collections import defaultdict
from collections import OrderedDict

import logging
logging.basicConfig(level = logging.DEBUG, format='%(asctime)s [%(levelname)s] %(message)s')

log = logging.getLogger("TestcaseParser")

class TestAction(object):

    APP_UPGRADE = "Install-Up"
    APP_DOWNGRADE = "Install-Down"
    APP_KEEP = "Install-Keep"

    APP_UNINSTALL = "Uninstall"
    DO_NOTHING = "NA"

    GROUP_INSTALL_APPS = [
                # /data/app
                APP_UPGRADE, APP_DOWNGRADE, APP_KEEP]

    # re-install
    GROUP_NEEDS_R_FLG = [
                # /data/app
                APP_UPGRADE, APP_DOWNGRADE, APP_KEEP]

    GROUP_VERSION_INCREMENT = [APP_UPGRADE]
    GROUP_VERSION_DECREMENT = [APP_DOWNGRADE]

    GROUP_ALL = GROUP_INSTALL_APPS + [APP_UNINSTALL, DO_NOTHING]

class BuildAction(object):

    APP_NOT_INSTALLED = "No-exist"

    # -------------------------------------------
    # PreInstall as system application
    # (used for stage: precondition)
    # -------------------------------------------
    APP_PRE_INSTALLED = "Pre-installed"
    APP_INSTALL_SAME_VERSION = "Version-Keep"
    APP_UPGRADE = "Version-Up"
    APP_DOWNGRADE = "Version-Down"

    # -------------------------------------------
    # PreInstall as privileged application.
    # This option is not supported before kitkat.
    # -------------------------------------------
    PRIVAPP_PRE_INSTALLED = "Priv-installed"
    PRIVAPP_INSTALL_SAME_VERSION = "Priv-Keep"
    PRIVAPP_UPGRADE = "Priv-Up"
    PRIVAPP_DOWNGRADE = "Priv-Down"

    GROUP_VERSION_INCREMENT = [APP_UPGRADE, PRIVAPP_UPGRADE]
    GROUP_VERSION_DECREMENT = [APP_DOWNGRADE, PRIVAPP_DOWNGRADE]
    GROUP_INSTALL_APPS = [
        APP_PRE_INSTALLED, APP_INSTALL_SAME_VERSION, APP_UPGRADE, APP_DOWNGRADE,
        PRIVAPP_PRE_INSTALLED, PRIVAPP_INSTALL_SAME_VERSION, PRIVAPP_UPGRADE, PRIVAPP_DOWNGRADE]
    GROUP_PRIVILEGE_APPLICATION = [
        PRIVAPP_PRE_INSTALLED, PRIVAPP_INSTALL_SAME_VERSION, PRIVAPP_UPGRADE, PRIVAPP_DOWNGRADE]
    GROUP_ALL = GROUP_INSTALL_APPS + [APP_NOT_INSTALLED]

class TestFormat(object):

    TYPE_NO_ACTION = 1
    TYPE_BUILD_ACTION = 2
    TYPE_TEST_ACTION = 3
    TEST_FORMAT = OrderedDict([
                   ('id', TYPE_NO_ACTION),
                   ('ROM1', TYPE_BUILD_ACTION),
                   ('ROM1_Op1', TYPE_TEST_ACTION),
                   ('ROM1_Op2', TYPE_TEST_ACTION),
                   ('ROM2', TYPE_BUILD_ACTION),
                   ('ROM2_Op1', TYPE_TEST_ACTION),
                   ('ROM2_Op2', TYPE_TEST_ACTION)])

    def __init__(self):
        self.group_buildaction = []
        self.group_testaction = []
        for key in self.TEST_FORMAT.keys():
            if self.TEST_FORMAT[key] == self.TYPE_BUILD_ACTION:
                self.group_buildaction.append(key)
            elif self.TEST_FORMAT[key] == self.TYPE_TEST_ACTION:
                self.group_testaction.append(key)

    def get_header(self): return self.TEST_FORMAT.keys()
    def get_group_buildaction(self): return self.group_buildaction
    def get_group_testaction(self): return self.group_testaction


class PreInstallPartitionHandler (object):

    def __init__ (self):
        self.supported_partitions = ["system", "oem", "odm", "vendor", "product"]
        self.enable_short_notation = True
        self.separator = "2"

    def _validate (self, userinput):
        pts = []

        for pt in userinput.split(self.separator):
            if pt in self.supported_partitions:
                pts.append(pt)
            elif self.enable_short_notation:
                for spt in self.supported_partitions:
                    if pt == spt[0:3]:
                        pts.append (spt)
            else:
                raise ValueError ("Unsupported partition specified: %s (%s) " % (userinput, pt))

        if not pts:
            raise ValueError ("Unable to find supported partition: %s" % userinput)
        return pts

    def create_map (self, build_action, userinput):
        partitions = self._validate (userinput)
        if len (build_action) != len (partitions):
            raise ValueError ("inconsistent between the build action and partition specification")
        return dict(zip(build_action, partitions))


class Parser(object):

    COMMENT_MARKER = "#"

    def __init__(self):
        self.pkg_basename = "com.android.tests.pkgup"
        self.app_name_base = "TestApp"
        self.initial_version = 5
        self.testapks_root = "./"
        self.path_testcase = ""
        self.interval_adb_install = 0

        self.test_format = TestFormat()
        self._current_version = self.initial_version
        self._expected_version_matrix = defaultdict(dict)

        self.pre_install_location = "sys2sys"

        # install scripts
        self._operations = defaultdict(list)
        self._paths_app = defaultdict(list)
        self._paths_priv_app = defaultdict(list)

    def run(self):

        list_of_testcase = self.read_testcase_from_file(self.path_testcase)
        self.analyze_testcases(list_of_testcase)

        log.info ("-*" * 25)
        artifact_filelists = []
        artifact_install_script = []

        for slot in sorted(self._operations.keys()):
            cmd = []
            cmd.append("#!/bin/bash")
            cmd.append('if [ "$PKGUP_DEBUG" == "true" ]; then')
            cmd.append('    export PS4="+[$(basename ${BASH_SOURCE}):${LINENO}]: ";')
            cmd.append('    set -x;')
            cmd.append('fi')
            cmd.append(": ${WORKSPACE:=.}")
            cmd.append(": ${SCRIPT_D:=$WORKSPACE}")
            cmd.append(": ${INSTALL_RETRY_COUNT:=5}")
            cmd.append(": ${INSTALL_RETRY_DURATION:=5}")
            cmd.append("source $SCRIPT_D/util.bash")
            cmd.append('test $# -eq 1 || { throw "Usage: $0 TESTAPKS_ROOT"; }')

            cmd.append("TESTAPKS_ROOT=$1")
            cmd.append("exit_if_not_dir $TESTAPKS_ROOT")
            cmd.extend(self._operations[slot])
            fname = "%s.bash" % slot

            with open(fname, "w") as f:
                f.write("\n".join(cmd))

            log.info("Generate install script at %s : %s" % (slot, fname))
            artifact_install_script.append(fname)


        partition_map = PreInstallPartitionHandler().create_map(
                self.test_format.get_group_buildaction(), self.pre_install_location)

        for slot in self.test_format.get_group_buildaction():
            list_of_filelist = []
            partition = partition_map[slot]

            # /<partition>/app/
            system_apps = self._paths_app[slot]
            if system_apps:
                fname = "%s_%s_%s.txt" % (slot, partition, "app")
                with open (fname, "w") as f:
                    f.write("\n".join(system_apps))
                    f.write("\n")
                list_of_filelist.append(fname)
                log.info("Generate list of APPs at %s: %s" % (slot, fname))

            # /<partition>/priv-app/
            priv_apps = self._paths_priv_app[slot]
            if priv_apps:
                fname = "%s_%s_%s.txt" % (slot, partition, "priv-app")
                with open (fname, "w") as f:
                    f.write("\n".join(priv_apps))
                    f.write("\n")
                list_of_filelist.append(fname)
                log.info("Generate list of PRIV_APPs at %s: %s" % (slot, fname))

            artifact_filelists.append(':'.join(list_of_filelist))

        with open ("expected_version.json", "w") as f:
            f.write(json.dumps(self._expected_version_matrix, sort_keys = True, indent = 4))

        # Example of packageupdate.properties:
        # ----
        # PREINSTALL_FILELISTS=ROM1_system_app.txt:ROM1_system_priv-app.txt,ROM2_system_app.txt:ROM2_system_priv-app.txt
        # INSTALL_SCRIPTS=ROM1_Op1.bash,ROM1_Op2.bash,ROM2_Op1.bash,ROM2_Op2.bash
        # TESTCASE_COUNT=40
        with open ("packageupdate.properties", "w") as prop:
            prop.write("PREINSTALL_FILELISTS=%s\n" % ",".join(artifact_filelists))
            prop.write("INSTALL_SCRIPTS=%s\n" % ",".join(artifact_install_script))
            prop.write("TESTCASE_COUNT=%s\n" % len(list_of_testcase))

        log.info ("-*" * 25)

    def find_testapk_from_artifacts (self, pkg, ver):
        """
        Assume the following directory structure

        root-aritifact-dir/
            +--- com.android.tests.pkgup001/ver5/TestApp001.apk
            |                              /ver6/TestApp001.apk
            ...
            +--- com.android.tests.pkgup002/ver5/TestApp002.apk
            ...
            +--- com.android.tests.pkgup005/ver9/TestApp005.apk
        """

        suffix = pkg.replace(self.pkg_basename, "")
        dpath = os.path.join(pkg, "ver%s" % ver)
        fpath = "%s%s.apk" % (self.app_name_base, suffix)
        relpath = os.path.join(dpath, fpath)
        abspath = os.path.join(self.testapks_root, relpath)
        if os.path.isfile(abspath):
            # Return relative path
            return relpath
        else:
            raise ValueError("file not found: %s" % abspath)

    def find_testlib_in_apkpath (self, pkg, apk_path, abi, ver):

        suffix = pkg.replace(self.pkg_basename, "")
        fpath = "lib%s%sver%s.so" % (self.app_name_base, suffix, ver)
        relpathlist = [os.path.dirname(apk_path), "libs", abi, fpath]
        relpath = os.path.join(*relpathlist)
        abspath = os.path.join(self.testapks_root, relpath)
        if os.path.isfile(abspath):
            return relpath
        else:
            raise ValueError("file not found: %s" % abspath)

    def read_testcase_from_file (self, path):
        testcases = []
        with open(path, "rU") as f:
            csvlines = csv.reader(
                [x for x in f if not x.strip().startswith(self.COMMENT_MARKER)],
                delimiter="\t")

            header = csvlines.next()
            if header != self.test_format.get_header():
                raise ValueError("does not match expected header")

            for csvline in csvlines:
                log.info(csvline)
                if len(csvline) != len(header):
                    raise ValueError("Row size doesn't equal to header size: %s" % row)
                testcase = dict(zip(header, csvline))
                testcases.append(testcase)

        return testcases

    def analyze_testcases (self, list_of_testcase):
        for (i, csv_testcase) in enumerate(list_of_testcase):
            self._current_version = self.initial_version
            test_package = "%s%03d" % (self.pkg_basename, i + 1)
            self.analyze_one_testcase (test_package, csv_testcase)

    def analyze_one_testcase(self, pkg, one_testcase):
        log.info("-" * 50)
        log.info("Package: %s" % pkg)
        log.info("%s" % one_testcase)
        build_path_abi="arm64-v8a" #TODO
        source_abi="arm64" #TODO

        for slot in self.test_format.get_header():
            if slot == "id":
                continue

            elif slot in self.test_format.get_group_buildaction():

                action = one_testcase[slot]
                if not action in BuildAction.GROUP_ALL:
                    raise ValueError("Unknown action('%s') is found in row: %s" % (action, one_testcase))

                path = self.make_apk_filelist(action, pkg)
                if not path:
                    continue

                if action in BuildAction.GROUP_PRIVILEGE_APPLICATION:
                    log.info("- ROM: add %s to Cluster Privleged App" % os.path.basename(path))
                    # clustered app experimental.
                    # ex: com.android.tests.pkgup001/ver1/TestApp001.apk, TestApp001
                    source_d = os.path.splitext(os.path.basename(path))[0]
                    content = "%s,%s" % (path, source_d)
                    self._paths_priv_app[slot].append(content)
                    # lib
                    libsource_d = "%s/lib/%s" % (source_d, source_abi)
                    libpath = self.find_testlib_in_apkpath(pkg, path, build_path_abi, self._current_version)
                    content = "%s,%s" % (libpath, libsource_d)
                    self._paths_priv_app[slot].append(content)


                else:
                    log.info("- ROM: add %s to Cluster System App" % os.path.basename(path))
                    # clustered app experimental.
                    # ex: com.android.tests.pkgup001/ver1/TestApp001.apk, TestApp001
                    source_d = os.path.splitext(os.path.basename(path))[0]
                    content = "%s,%s" % (path, source_d)
                    self._paths_app[slot].append(content)
                    # lib
                    libsource_d = "%s/lib/%s" % (source_d, source_abi)
                    libpath = self.find_testlib_in_apkpath(pkg, path, build_path_abi, self._current_version)
                    content = "%s,%s" % (libpath, libsource_d)
                    self._paths_app[slot].append(content)

            elif slot in self.test_format.get_group_testaction():

                action = one_testcase[slot]
                if not action in TestAction.GROUP_ALL:
                    raise ValueError("Unknown action('%s') is found in row: %s" % (action, one_testcase))

                cmd = self.make_install_cmd(action, pkg)
                if cmd:
                    self._operations[slot].append(cmd)

            else:
                log.info("unknown slot: %s" % slot)

            self._expected_version_matrix[pkg][slot] = self._current_version


    def make_install_cmd(self, action, pkg):
        # update app version according to test scenalio.
        if action in TestAction.GROUP_VERSION_INCREMENT:
            self._current_version += 1
            log.info("- Ops: pkg: %s up:(%s) action: %s" % (pkg, self._current_version, action))

        elif action in TestAction.GROUP_VERSION_DECREMENT:
            self._current_version -= 1
            log.info("- Ops: pkg: %s down:(%s) action: %s" % (pkg, self._current_version, action))

        cmds = [""]
        cmds.append('logi "%s pkg:%s version:%d"' % (action, pkg, self._current_version))
        cmds.append('$ADB shell log -p d -t %s.Host "action: %s version: %d"' % (pkg, action, self._current_version))

        if action in TestAction.GROUP_INSTALL_APPS:
            apk_path = self.find_testapk_from_artifacts(pkg, self._current_version)
            apk_path = "$TESTAPKS_ROOT/%s" % apk_path
            cmds.append("exit_if_not_found %s" % apk_path)
            install = []

            install.append("$ADB")
            install.append("install")
            install.append("-f")
            if action in TestAction.GROUP_NEEDS_R_FLG:
                install.append("-r")

            install.append(apk_path)
            cmd = 'retry $INSTALL_RETRY_COUNT $INSTALL_RETRY_DURATION '
            cmd += 'check "%s" ' % " ".join(install)
            cmd += '|| { loge "failed to execute install command."; }'
            cmds.append(cmd)

            if self.interval_adb_install and self.interval_adb_install != 0:
                cmds.append("sleep %s" % self.interval_adb_install)

        elif action == TestAction.APP_UNINSTALL:
            cmds.append("$ADB uninstall %s" % pkg)

        else:
            log.info("- Ops: pkg: %s skipped:(%s) action: %s" % (pkg, self._current_version, action))

        return "\n".join(cmds)

    def make_apk_filelist(self, action, pkg):

        if action in BuildAction.GROUP_VERSION_INCREMENT:
            self._current_version += 1
            log.info("- ROM: pkg: %s up:(%s) action: %s" % (pkg, self._current_version, action))

        elif action in BuildAction.GROUP_VERSION_DECREMENT:
            self._current_version -= 1
            log.info("- ROM: pkg: %s down:(%s) action: %s" % (pkg, self._current_version, action))

        if not action in BuildAction.GROUP_INSTALL_APPS:
            log.info("- ROM: pkg: %s skipped:(%s) action: %s" % (pkg, self._current_version, action))
            return False

        path = self.find_testapk_from_artifacts(pkg, self._current_version)
        return path

if __name__ == "__main__":

    usage = "Usage: %prog [options] /path/to/the/testcase/"
    parser = OptionParser(usage = usage)
    parser.add_option("-p", "--package", dest="package", action="store",
        type="string", default="com.android.tests.pkgup", help="[default:%default]")
    parser.add_option("-a", "--activity", dest="app_name_base", action="store",
        type="string", default="TestApp", help="[default:%default]")
    parser.add_option("-d", "--dest", dest="testapks_root",action="store",
        type="string", default="./", help="root directory of test applications [default: current directory]")
    parser.add_option("-l", "--location", dest="location",action="store",type="string", default="sys2sys",
        help="location to pre-install test applications: sys2sys, sys2vendor, vendor2sys, vendor2vendor [default:%default]")
    parser.add_option("--interval-adb-install", dest="interval_adb_install",action="store", type="float")
    opts, args = parser.parse_args()

    try:
        testcase_parser = Parser()

        if not len(args) == 1:
            raise ValueError ("require 1 arguments")

        if opts.package:
            testcase_parser.pkg_basename = opts.package
        if opts.app_name_base:
            testcase_parser.app_name_base = opts.app_name_base
        if opts.testapks_root:
            testcase_parser.testapks_root = os.path.abspath(opts.testapks_root)
        if opts.interval_adb_install:
            testcase_parser.interval_adb_install = float(opts.interval_adb_install)
        if opts.location:
            testcase_parser.pre_install_location = opts.location

        testcase_parser.path_testcase = args[0]
        testcase_parser.run()

    except Exception, e:
        print traceback.format_exc()
        parser.print_help()
