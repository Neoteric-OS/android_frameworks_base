#! /usr/bin/python
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
#

import sys
import os
import os.path
import shutil
from optparse import OptionParser
import traceback

class Generator(object):

    def __init__(self):
        # default values
        self.class_name = "Main"
        self.package_name = ""
        self.version_code = 1
        self.version_name = "1.0"  # TODO
        self.root = os.path.abspath(self.package_name)
        self.app_name = self.class_name
        self.lib_name = self.class_name
        self.eclipse_target_sdk_version = "android-16"
        self.use_template2 = True
        self.without_jni = False
        self.usercwd = os.path.abspath(os.path.dirname(__file__))
        self.template_dir = os.path.join(self.usercwd, "template/")
        self.define_version_in_res = False
        self.orig_package_name = ""
        self.target_sdk_version = "23"
        self.min_sdk_version = "23"
        self.target_abi_list = []
        self.install_loc = "auto"

    def parse_opt(self):
        usage = "Usage: %prog [opts]"
        parser = OptionParser(usage=usage)
        try:
            parser.add_option("--install-loc", dest="install_loc",
                    action="store", type="string",
                    default="auto"
                    )
            parser.add_option("-p", "--package", dest="package",
                    action="store", type="string",
                    default="com.android.tests.pkgup",
                    help="app package string. (default)%default"
                    )
            parser.add_option("--app-name", dest="app_name",
                    action="store", type="string",
                    default="PackageUpdateSample",
                    help="app(apk) name. (default)%default"
                    )
            parser.add_option("-c", "--classname", dest="classname",
                    action="store", type="string",
                    default="Main",
                    help="basename of activity/sharedobject (default)%default"
                    )
            parser.add_option("--lib-name", dest="lib_name",
                    action="store", type="string",
                    help="Name of native library. <lib-name>.so"
                    )
            parser.add_option("--version-code", dest="version_code",
                    action="store", type="int",
                    help="Application version code."
                    )
            parser.add_option("--version-name", dest="version_name",
                    action="store", type="string",
                    help="Application version name."
                    )
            parser.add_option("-d", "--dest", dest="destination",
                    action="store", type="string",
                    help="output directory"
                    )
            parser.add_option("--eclipse-target-sdk", dest="eclipse_target_sdk_version",
                    action="store", type="string",
                    help="target sdk version for eclipse"
                    )
            parser.add_option("--sdk-root", dest="sdk_root",
                    action="store", type="string",
                    help="[obsolete]This value required by build.xml"
                    )
            parser.add_option("--ndk-root", dest="ndk_root",
                    action="store", type="string",
                    help="[obsolete]This value required by build.xml"
                    )
            parser.add_option("--without-jni", dest="without_jni",
                    action="store_true")
            parser.add_option("--use-template2", dest="use_template2",
                    action="store_true")
            parser.add_option("--template-dir", dest="template_dir",
                    type="string",
                    action="store")
            parser.add_option("--define-version-in-resource", dest="define_version_in_res", action="store_true")
            parser.add_option("--target-sdk-version", dest="target_sdk_version",
                    action="store", type="string")
            parser.add_option("--min-sdk-version", dest="min_sdk_version",
                    action="store", type="string")
            parser.add_option("--abi", dest="target_abi_list",
                    action="append", type="string")

            opts, args = parser.parse_args()
            if opts.install_loc:
                if not opts.install_loc in ["auto", "internalOnly", "preferExternal"]:
                    raise ValueError("Unsupported install location %s" % opts.install_loc)
                self.install_loc = opts.install_loc
            if opts.without_jni:
                self.without_jni = True
            if opts.app_name:
                self.app_name = opts.app_name
            if opts.package:
                self.package_name = opts.package
                self.root = os.path.abspath(self.package_name)
            if opts.classname:
                self.class_name = opts.classname
                self.lib_name = opts.classname
            if opts.lib_name:
                self.lib_name = opts.lib_name
            if opts.version_code:
                self.version_code = opts.version_code
            if opts.version_name:
                self.version_name = opts.version_name
            if opts.destination:
                self.root = os.path.abspath(opts.destination)
            if opts.template_dir:
                self.template_dir = opts.template_dir
            if opts.define_version_in_res:
                self.define_version_in_res = True
            if opts.target_sdk_version:
                self.target_sdk_version = opts.target_sdk_version
            if opts.min_sdk_version:
                self.min_sdk_version = opts.min_sdk_version
            if opts.target_abi_list:
                self.target_abi_list = [x.rstrip() for x in opts.target_abi_list]

        except Exception, e:
            print e
            print usage
            sys.exit(-1)
        return

    def prepare_src_dir(self):
        os.mkdir(os.path.join(self.root, "jni"))

        t = os.path.join(self.template_dir, "src/template.txt")

        s = "".join(open(t).readlines()) % {"packagename": self.package_name, "classname": self.class_name,
            "libname": self.lib_name}

        srcpath = os.path.join(self.root, "src/main/java", self.package_name.replace(".", "/"))
        os.makedirs(srcpath)
        java_filename = "%s.java" % self.class_name

        with open(os.path.join(srcpath, java_filename), "w") as f:
            f.write(s)
        return

    def generate_manifest(self):
        t = os.path.join(self.template_dir, "AndroidManifest.xml")
        vcode = self.version_code
        vname = self.version_name
        if self.define_version_in_res:
            vcode = "@integer/versionCode"
            vname = "@string/versionName"

        s = "".join(open(t).readlines()) % {
            "install_loc": self.install_loc,
            "version_code": vcode,
            "version_name": vname,
            "package_name": self.package_name,
            "activity_name": self.class_name,
            "orig_package_name" : self.orig_package_name,
            "target_sdk_version" : self.target_sdk_version,
            "min_sdk_version" : self.min_sdk_version
        }
        with open(os.path.join(self.root, "src/main", "AndroidManifest.xml"), "w") as f:
            f.write(s)
        return

    def prepare_jni_dir(self):
        # jni/Android.mk
        mk_tmp = os.path.join(self.template_dir, "jni/Android_mk.txt")
        mk_src = "".join(open(mk_tmp).readlines()) % {"classname": self.lib_name}
        with open(os.path.join(self.root, "jni/Android.mk"), "w") as f:
            f.write(mk_src)


        # jni/<className>.c
        body_tmp = os.path.join(self.template_dir, "jni/template.c")
        method = "Java_%s_%s_getLibraryVersion" % (self.package_name.replace(".", "_"), self.class_name)
        body_src = "".join(open(body_tmp).readlines()) % {"methodname": method, "version": self.version_code }
        with open(os.path.join(self.root, "jni/%s.c" % self.lib_name), "w") as f:
            f.write(body_src)

        # jni/Application.mk
        if self.target_abi_list:
            app_mk=os.path.join(self.root, "jni/Application.mk")
            with open(app_mk, "w") as f:
                f.write("APP_ABI:=%s\n" % " ".join(self.target_abi_list))

        return

    def prepare_resource_dir(self):
        shutil.copytree(os.path.join(self.template_dir, "res"), os.path.join(self.root, "src/main/res"))
        t = os.path.join(self.template_dir, "strings.xml")
        s = "".join(open(t).readlines()) % {"app_name": self.app_name,
                "versioncode": self.version_code,
                "versionname": self.version_name }
        with open(os.path.join(self.root, "src/main/res/values/strings.xml"), "w") as f:
            f.write(s)
        return

    def prepare_misc_files(self):

        # project.properties
        with open(os.path.join(self.root, "project.properties"), "w") as f:
            f.write("target=%s" % self.eclipse_target_sdk_version)

        # build.gradle
        path_in = os.path.join(self.usercwd, "build.gradle.template")
        path_out = os.path.join(self.root, "build.gradle")
        with open(path_in) as fin, open(path_out, 'w') as fout:
            fout.write(fin.read().replace('PROJECT_NAME', self.app_name))

        return

    def generate_tree(self):
        os.makedirs(self.root)
        self.prepare_src_dir()
        self.generate_manifest()
        self.prepare_resource_dir()
        self.prepare_jni_dir()
        self.prepare_misc_files()

    def run(self):
        self.parse_opt()
        if not os.path.exists(self.template_dir) or not os.path.isdir(self.template_dir):
            print "Resource dir: %s is not exists." % self.template_dir
            sys.exit(1)
        self.generate_tree()

if __name__ == "__main__":
    t = Generator()
    t.run()

