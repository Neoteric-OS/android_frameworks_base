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
from optparse import OptionParser
from collections import defaultdict
from collections import OrderedDict

import pprint
pp = pprint.PrettyPrinter(indent=2)
P=pp.pprint

import logging
logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(levelname)s] %(message)s')

log=logging.getLogger("TestcaseParser")

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
    def get_group_buildaction(self): return set(self.group_buildaction)
    def get_group_testaction(self): return set(self.group_testaction)

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

        self._operations = defaultdict(list)           # adb install scripts
        self._paths_system_app = defaultdict(list)
        self._paths_priv_app = defaultdict(list)

    def run(self):

        d = self.read_testcase(self.path_testcase)
        self.parse_testcases(d, self.test_format)

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
            cmd.append(": ${SCRIPT_D:=$WORKSPACE}") #TODO
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

        def create_file(slot, parsed_dict, file_suffix, usage, filelist):
            if len(parsed_dict[slot]) > 0:
                fname = "%s%s.txt" % (slot, file_suffix)
                with open(fname, "w") as f:
                    f.write("\n".join(parsed_dict[slot]))
                    f.write("\n")
                filelist.append(fname)
                log.info("Generate list of %s at %s: %s" % (usage, slot, fname))

        for slot in sorted(self.test_format.get_group_buildaction()):
            filelist=[]
            create_file(slot, self._paths_system_app, "", "SYSTEM APPs", filelist)
            create_file(slot, self._paths_priv_app, "_priv_app", "PRIV APPs", filelist)
            artifact_filelists.append(':'.join(filelist))

        with open ("expected_version.json", "w") as f:
            f.write(json.dumps(self._expected_version_matrix, sort_keys=True, indent=4))

        with open ("testcase_parser_out.txt", "a") as prop:
            prop.write("PREINSTALL_FILELISTS=%s\n" % ",".join(artifact_filelists))
            prop.write("INSTALL_SCRIPTS=%s\n" % ",".join(artifact_install_script))
            prop.write("TESTCASE_COUNT=%s\n" % len(d))

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
            # Return relative path
            return relpath
        else:
            raise ValueError("file not found: %s" % abspath)

    def read_testcase (self, path):

        if not os.path.isfile (path):
            raise ValueError("testcase: %s is not found" % path)

        testcases = []
        with open(path, "rU") as f:
            lines = [x for x in f if not x.strip().startswith(self.COMMENT_MARKER)]
            reader = csv.reader(lines, delimiter="\t")
            header = reader.next()
            if header != self.test_format.get_header():
                raise ValueError("does not match header")

            for row in reader:
                if len(row) != len(header):
                    raise ValueError("Row size doesn't equal to header size: %s" % row)
                test = dict(zip(header, row))
                testcases.append(test)

        return testcases

    def parse_testcases (self, testcases, format):

        testcases = testcases
        for (i, one_testcase) in enumerate(testcases):
            # Reset initial sw version
            self._current_version = self.initial_version
            pkg = "%s%03d" % (self.pkg_basename, i + 1)
            self.analyze_one_testcase (pkg, one_testcase, format)


    def analyze_one_testcase(self, pkg, one_testcase, format):
        log.info("-" * 50)
        log.info("Package: %s" % pkg)
        log.info("%s" % one_testcase)
        build_path_abi="arm64-v8a" #TODO
        source_abi="arm64" #TODO

        for slot in self.test_format.get_header():
            if slot == "id":
                continue

            elif slot in format.get_group_buildaction():

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
                    self._paths_system_app[slot].append(content)
                    # lib
                    libsource_d = "%s/lib/%s" % (source_d, source_abi)
                    libpath = self.find_testlib_in_apkpath(pkg, path, build_path_abi, self._current_version)
                    content = "%s,%s" % (libpath, libsource_d)
                    self._paths_system_app[slot].append(content)

            elif slot in format.get_group_testaction():

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

    usage = "Usage: %prog [options] -d /path/to/testapk-root /path/to/testcase\nType: -h or --help"

    parser = OptionParser(usage=usage)
    parser.add_option("-p", "--package", dest="package", action="store", type="string")
    parser.add_option("-a", "--activity", dest="app_name_base", action="store", type="string")
    parser.add_option("-d", "--dest", dest="testapks_root",action="store", type="string")
    parser.add_option("--interval-adb-install", dest="interval_adb_install",action="store", type="float")
    opts, args = parser.parse_args()

    try:

        testcase_parser = Parser()

        if not len(args) == 1:
            print usage
            raise ValueError("require 1 arguments: %s" % usage)

        testcase_parser.path_testcase = args[0]
        if not os.path.isfile(testcase_parser.path_testcase):
            raise ValueError ("Testcase does not exists: %s" % testcase_parser.path_testcase)

        # Options.
        if opts.package:
            testcase_parser.pkg_basename = opts.package
        if opts.app_name_base:
            testcase_parser.app_name_base = opts.app_name_base
        if opts.testapks_root:
            testcase_parser.testapks_root = os.path.abspath(opts.testapks_root)
        if opts.interval_adb_install:
            testcase_parser.interval_adb_install = float(opts.interval_adb_install)

        testcase_parser.run()

    except ValueError, e:
        log.error(e)
        sys.exit(1)
