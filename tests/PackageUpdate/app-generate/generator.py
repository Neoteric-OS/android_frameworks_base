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
        self.class_name = "Main"
        self.package_name = "com.android.tests.pkgup"
        self.version_code = 1
        self.version_name = "1.0"
        self.root = os.path.abspath(self.package_name)
        self.app_name = self.class_name
        self.lib_name = self.class_name
        self.usercwd = os.path.abspath(os.path.dirname(__file__))
        self.template_dir = os.path.join(self.usercwd, "template/")
        self.target_sdk_version = "27"
        self.min_sdk_version = "26"
        self.target_abi_list = []
        self.install_loc = "auto"
        self.gradle_build = False

    def prepare_src_dir(self):
        os.mkdir(os.path.join(self.root, "jni"))

        t = os.path.join(self.template_dir, "src/template.txt")
        s = "".join(open(t).readlines()) % {"packagename": self.package_name, "classname": self.class_name,
            "libname": self.lib_name}

        loc = "src/"
        if self.gradle_build:
            loc = "src/main/java"

        srcpath = os.path.join(self.root, loc, self.package_name.replace(".", "/"))
        os.makedirs(srcpath)
        java_filename = "%s.java" % self.class_name

        with open(os.path.join(srcpath, java_filename), "w") as f:
            f.write(s)
        return

    def generate_manifest(self):
        t = os.path.join(self.template_dir, "AndroidManifest.xml")

        s = "".join(open(t).readlines()) % {
            "install_loc": self.install_loc,
            "version_code": self.version_code,
            "version_name": self.version_name,
            "package_name": self.package_name,
            "activity_name": self.class_name,
            "target_sdk_version" : self.target_sdk_version,
            "min_sdk_version" : self.min_sdk_version
        }
        dest = os.path.join(self.root, "AndroidManifest.xml")
        if self.gradle_build:
            dest = os.path.join(self.root, "src/main", "AndroidManifest.xml")

        with open(dest, "w") as f:
            f.write(s)
        return

    def prepare_jni_dir(self):
        # jni/Android.mk
        mk_tmp = os.path.join(self.template_dir, "jni/Android_mk.txt")
        mk_src = "".join(open(mk_tmp).readlines()) % {"libname": self.lib_name}
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

        dest = os.path.join(self.root, "res")
        if self.gradle_build:
            dest = os.path.join(self.root, "src/main/res")
        shutil.copytree(os.path.join(self.template_dir, "res"), dest)

        t = os.path.join(self.template_dir, "strings.xml")
        s = "".join(open(t).readlines()) % {"app_name": self.app_name,
                "versioncode": self.version_code,
                "versionname": self.version_name }
        with open(os.path.join(dest, "values/strings.xml"), "w") as f:
            f.write(s)

        return

    def prepare_misc_files(self):

        if self.gradle_build:
            gradle_dir = os.path.join(self.usercwd, "../../gradle")
            path_in = os.path.join(gradle_dir, "build.gradle.template")
            path_out = os.path.join(self.root, "build.gradle")
            with open(path_in) as fin, open(path_out, 'w') as fout:
                fout.write(fin.read().replace('PROJECT_NAME', self.app_name))
            shutil.copyfile(os.path.join(gradle_dir, "pkgup.jks"), "template/pkgup.jks")

        else:
            with open(os.path.join(self.template_dir, "Android.mk.txt"), "r") as f:
                modulename = self.app_name + "ver" + str(self.version_code)
                makefile = "".join(f.readlines()) % {"package_name": modulename, "libname": self.lib_name}
                with open(os.path.join(self.root, "Android.mk"), "w") as f:
                    f.write(makefile)
        return

    def generate_tree(self):
        os.makedirs(self.root)
        self.prepare_src_dir()
        self.generate_manifest()
        self.prepare_resource_dir()
        self.prepare_jni_dir()
        self.prepare_misc_files()

    def run(self):
        if not os.path.isdir(self.template_dir):
            raise ValueError("Resource dir: %s is not exists." % self.template_dir)
        self.generate_tree()

def get_optionparser ():
    usage = "Usage: %prog [opts]"
    parser = OptionParser(usage=usage)
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
            default="Main",
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
    parser.add_option("--template-dir", dest="template_dir",
            type="string",
            action="store")
    parser.add_option("--target-sdk-version", dest="target_sdk_version",
            action="store", type="string")
    parser.add_option("--min-sdk-version", dest="min_sdk_version",
            action="store", type="string")
    parser.add_option("--abi", dest="target_abi_list",
            action="append", type="string")
    parser.add_option("--gradle", dest="gradle", action="store_true")
    parser.add_option("--generate-tree", dest="generate_tree", action="store_true")

    parser.add_option("--max-pkg-suffix", dest="max_pkg_suffix",
            action="store", type="int", default=40)
    parser.add_option("--max-app-version", dest="max_app_version",
            action="store", type="int", default=10)

    return parser


def generate_trees (generator, opts):
    """
        com.android.tests.pkgup/
        +-- Android.mk
        +-- TestApp001ver1
        +-- TestApp001ver2
        +-- TestApp001ver3
        ...
    """
    max_pkg_suffix = opts.max_pkg_suffix
    max_app_version = opts.max_app_version

    package_name_base = opts.package
    app_name_base = opts.app_name
    lib_name_base = opts.lib_name

    root_package_dir = os.path.abspath(package_name_base)
    if opts.destination:
        root_package_dir = opts.destination
    if os.path.exists(root_package_dir):
        shutil.rmtree(root_package_dir)
    os.makedirs(root_package_dir)

    app_names = []
    versions = []

    for suffix in range(max_pkg_suffix):
        pkg_suffix = suffix + 1

        for version in range(max_app_version):
            generator.version_code = version + 1
            generator.version_name = str(generator.version_code)
            generator.package_name = "%s%03d" % (package_name_base, pkg_suffix)
            generator.app_name = "%s%03d" % (app_name_base, pkg_suffix)
            if generator.gradle_build:
                generator.lib_name = "%s%03d" % (lib_name_base, pkg_suffix)
            else:
                generator.lib_name = "%s%03dver%d" % (lib_name_base, pkg_suffix, generator.version_code)
            generator.root = os.path.join(root_package_dir, "%sver%d" % (generator.app_name, generator.version_code))
            generator.run()
            app_names.append(generator.app_name)
            versions.append(str(generator.version_code))

    app_names = ["\t%s \\" % x for x in sorted(list(set(app_names)))]
    app_names = "\n".join(app_names)[1:-1] # remove first tab and last "\"
    versions = sorted(list(set(versions)), key=lambda x: int(x))

    # create root makefile
    with open(os.path.join(generator.template_dir, "PackageUpdateTestApps.mk.txt"), "r") as f:
        makefile = "".join(f.readlines()) % {
                "testapps": app_names,
                "versions": " ".join(versions),
                "packagename": package_name_base}
        with open(os.path.join(root_package_dir, "Android.mk"), "w") as f:
            f.write(makefile)


if __name__ == "__main__":
    option_parser = get_optionparser()
    generator = Generator()

    try:
        opts, args = option_parser.parse_args()
        if opts.install_loc:
            if not opts.install_loc in ["auto", "internalOnly", "preferExternal"]:
                raise ValueError("Unsupported install location %s" % opts.install_loc)
            generator.install_loc = opts.install_loc
        if opts.template_dir:
            generator.template_dir = opts.template_dir
        if opts.target_sdk_version:
            generator.target_sdk_version = opts.target_sdk_version
        if opts.min_sdk_version:
            generator.min_sdk_version = opts.min_sdk_version
        if opts.target_abi_list:
            generator.target_abi_list = [x.rstrip() for x in opts.target_abi_list]
        if opts.classname:
            generator.class_name = opts.classname
            generator.lib_name = opts.classname
        generator.gradle_build = opts.gradle

        if opts.generate_tree:
            generate_trees (generator, opts)

        else:
            if opts.destination:
                generator.root = os.path.abspath(opts.destination)
            if opts.app_name:
                generator.app_name = opts.app_name
            if opts.package:
                generator.package_name = opts.package
                generator.root = os.path.abspath(generator.package_name)
            if opts.lib_name:
                generator.lib_name = opts.lib_name
            if opts.version_code:
                generator.version_code = opts.version_code
            if opts.version_name:
                generator.version_name = opts.version_name
            generator.run()

    except Exception, e:
        print traceback.format_exc()

