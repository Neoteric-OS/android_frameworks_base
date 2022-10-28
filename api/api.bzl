# Copyright 2022 Google Inc. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
The intention behind this macro is to generate a number of "merged"
API-related modules that would otherwise require a large amount of very
similar Android.bp boilerplate to define. For example, the merged current.txt
API definitions (created by merging the non-updatable current.txt with all
the module current.txts). This simplifies the addition of new android
modules, by reducing the number of genrules etc a new module must be added to.
"""

load("@soong_extension//:soong.bzl", "soong")

_art = "art.module.public.api"
_conscrypt = "conscrypt.module.public.api"
_i18n = "i18n.module.public.api"

_core_libraries_modules = [_art, _conscrypt, _i18n]

def _merged_txt_definition(txt_filename, dist_filename, base_txt, modules, module_tag, scope):
    return struct(txt_filename = txt_filename, dist_filename = dist_filename, base_txt = base_txt, modules = modules, module_tag = module_tag, scope = scope)

def _create_merged_txt(module_name, txt):
    metalava_cmd = "$(location metalava)"

    # Silence reflection warnings. See b/168689341
    metalava_cmd += " -J--add-opens=java.base/java.util=ALL-UNNAMED "
    metalava_cmd += " --quiet --no-banner --format=v2 "

    filename = txt.txt_filename
    if txt.scope != "public":
        filename = txt.scope + "-" + filename

    srcs = [txt.base_txt]
    srcs.extend(_create_srcs(txt.modules, txt.module_tag))

    soong.modules.genrule(
        name = module_name + "-" + filename,
        tools = ["metalava"],
        out = [filename],
        cmd = metalava_cmd + "$(in) --api $(out)",
        srcs = srcs,
        dists = [
            struct(
                targets = ["droidcore"],
                dir = "api",
                dest = filename,
            ),
            struct(
                targets = ["api_txt", "sdk"],
                dir = "apistubs/android/" + txt.scope + "/api",
                dest = txt.dist_filename,
            ),
        ],
        visibility = ["//visibility:public"],
    )

def _create_merged_stubs_src_jar(name, modules):
    srcs = [":api-stubs-docs-non-updatable"]
    srcs.extend(_create_srcs(modules, "{.public.stubs.source}"))
    soong.modules.genrule(
        name = name + "-current.srcjar",
        tools = ["merge_zips"],
        out = ["current.srcjar"],
        cmd = "$(location merge_zips) $(out) $(in)",
        srcs = srcs,
        # Used by make module in //development, mind
        visibility = ["//visibility:private"],
    )

def _create_merged_annotations_filegroups(modules, system_server_modules):
    for i in [
        struct(
            name = "all-modules-public-annotations",
            tag = "{.public.annotations.zip}",
            modules = modules,
        ),
        struct(
            name = "all-modules-system-annotations",
            tag = "{.system.annotations.zip}",
            modules = modules,
        ),
        struct(
            name = "all-modules-module-lib-annotations",
            tag = "{.module-lib.annotations.zip}",
            modules = modules,
        ),
        struct(
            name = "all-modules-system-server-annotations",
            tag = "{.system-server.annotations.zip}",
            modules = system_server_modules,
        ),
    ]:
        soong.modules.filegroup(
            name = i.name,
            srcs = _create_srcs(i.modules, i.tag),
        )

def _create_filtered_api_versions(modules):
    # For the filtered api versions, we prune all APIs except art module's APIs. because
    # 1) ART apis are available by default to all modules, while other module-to-module deps are
    #    explicit and probably receive more scrutiny anyway
    # 2) The number of ART/libcore APIs is large, so not linting them would create a large gap
    # 3) It's a compromise. Ideally we wouldn't be filtering out any module APIs, and have
    #    per-module lint databases that excludes just that module's APIs. Alas, that's more
    #    difficult to achieve.
    modules = [m for m in modules if m != _art]

    for i in [
        struct(
            # We shouldn't need public-filtered or system-filtered.
            # public-filtered is currently used to lint things that
            # use the module sdk or the system server sdk, but those
            # should be switched over to module-filtered and
            # system-server-filtered, and then public-filtered can
            # be removed.
            name = "api-versions-xml-public-filtered",
            out = "api-versions-public-filtered.xml",
            src = ":api_versions_public{.api_versions.xml}",
        ),
        struct(
            name = "api-versions-xml-module-lib-filtered",
            out = "api-versions-module-lib-filtered.xml",
            src = ":api_versions_module_lib{.api_versions.xml}",
        ),
        struct(
            name = "api-versions-xml-system-server-filtered",
            out = "api-versions-system-server-filtered.xml",
            src = ":api_versions_system_server{.api_versions.xml}",
        ),
    ]:
        # Note: order matters: first parameter is the full api-versions.xml
        # after that the stubs files in any order
        # stubs files are all modules that export API surfaces EXCEPT ART
        srcs = [i.src]
        srcs.extend(_create_srcs(modules, ".stubs{.jar}"))
        soong.modules.genrule(
            name = i.name,
            out = [i.out],
            srcs = srcs,
            tools = ["api_versions_trimmer"],
            cmd = "$(location api_versions_trimmer) $(out) $(in)",
            dists = [
                struct(
                    targets = ["sdk"],
                ),
            ],
        )

def _create_merged_public_stubs(modules):
    soong.modules.java_library(
        name = "all-modules-public-stubs",
        static_libs = _transform_array(modules, "", ".stubs"),
        sdk_version = "module_current",
        visibility = ["//frameworks/base"],
    )

def _create_merged_system_stubs(modules):
    soong.modules.java_library(
        name = "all-modules-system-stubs",
        static_libs = _transform_array(modules, "", ".stubs.system"),
        sdk_version = "module_current",
        visibility = ["//frameworks/base"],
    )

def _create_merged_framework_impl(modules):
    # This module is for the "framework-all" module, which should not include the core libraries.
    modules = [m for m in modules if m not in _core_libraries_modules]
    soong.modules.java_library(
        name = "all-framework-module-impl",
        static_libs = _transform_array(modules, "", ".impl"),
        sdk_version = "module_current",
        visibility = ["//frameworks/base"],
    )

def _create_merged_framework_module_lib_stubs(modules):
    # The user of this module compiles against the "core" SDK, so remove core libraries to avoid dupes.
    modules = [m for m in modules if m not in _core_libraries_modules]
    soong.modules.java_library(
        name = "framework-updatable-stubs-module_libs_api",
        static_libs = _transform_array(modules, "", ".stubs.module_lib"),
        sdk_version = "module_current",
        visibility = ["//frameworks/base"],
    )

def _create_public_stubs_source_filegroup(modules):
    soong.modules.filegroup(
        name = "all-modules-public-stubs-source",
        srcs = _create_srcs(modules, "{.public.stubs.source}"),
        visibility = ["//frameworks/base"],
    )

def _create_merged_txts(name, bootclasspath, system_server_classpath):
    textFiles = []

    tagSuffix = [".api.txt}", ".removed-api.txt}"]
    distFilename = ["android.txt", "android-removed.txt"]
    for i, f in enumerate(["current.txt", "removed.txt"]):
        textFiles.append(_merged_txt_definition(
            txt_filename = f,
            dist_filename = distFilename[i],
            base_txt = ":non-updatable-" + f,
            modules = bootclasspath,
            module_tag = "{.public" + tagSuffix[i],
            scope = "public",
        ))
        textFiles.append(_merged_txt_definition(
            txt_filename = f,
            dist_filename = distFilename[i],
            base_txt = ":non-updatable-system-" + f,
            modules = bootclasspath,
            module_tag = "{.system" + tagSuffix[i],
            scope = "system",
        ))
        textFiles.append(_merged_txt_definition(
            txt_filename = f,
            dist_filename = distFilename[i],
            base_txt = ":non-updatable-module-lib-" + f,
            modules = bootclasspath,
            module_tag = "{.module-lib" + tagSuffix[i],
            scope = "module-lib",
        ))
        textFiles.append(_merged_txt_definition(
            txt_filename = f,
            dist_filename = distFilename[i],
            base_txt = ":non-updatable-system-server-" + f,
            modules = system_server_classpath,
            module_tag = "{.system-server" + tagSuffix[i],
            scope = "system-server",
        ))

    for txt in textFiles:
        _create_merged_txt(name, txt)

def combined_apis(name, bootclasspath, system_server_classpath):
    # TODO
    #    if ctx.Config().VendorConfig("ANDROID").Bool("include_nonpublic_framework_api") {
    #        bootclasspath = append(bootclasspath, a.properties.Conditional_bootclasspath...)
    #        sort.Strings(bootclasspath)
    #    }
    _create_merged_txts(name, bootclasspath, system_server_classpath)

    _create_merged_stubs_src_jar(name, bootclasspath)

    _create_merged_public_stubs(bootclasspath)
    _create_merged_system_stubs(bootclasspath)
    _create_merged_framework_module_lib_stubs(bootclasspath)
    _create_merged_framework_impl(bootclasspath)

    _create_merged_annotations_filegroups(bootclasspath, system_server_classpath)

    _create_filtered_api_versions(bootclasspath)

    _create_public_stubs_source_filegroup(bootclasspath)

# Various utility methods below.

def _create_srcs(modules, tag):
    """Creates an array of ":<m><tag>" for each m in <modules>."""
    return _transform_array(modules, ":", tag)

def _transform_array(modules, prefix, suffix):
    """Creates an array of "<prefix><m><suffix>", for each m in <modules>."""
    return [prefix + m + suffix for m in modules]
