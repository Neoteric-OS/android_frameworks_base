/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.platform.test.ravenwood.ravenhelper.psi

import com.android.tools.lint.UastEnvironment

// References on PSI / Lint:
//
// - We stole code from Metalava, but the latest version of Metalava is too complicated,
//   so code was stolen from an older version:
// https://android.git.corp.google.com/platform/tools/metalava/+/refs/heads/android13-dev
//
// - PSI is source code is available in IntelliJ's code base:
//   https://github.com/JetBrains/intellij-community.git
//
// - Lint is in Android studio
// https://android.googlesource.com/platform/tools/base/+/studio-master-dev/source.md


/**
 * Create [UastEnvironment] with the "standard" options.
 */
fun createUastEnvironment(): UastEnvironment {
    val config = UastEnvironment.Configuration.create(
        enableKotlinScripting = false,
        useFirUast = false,
    )

    config.javaLanguageLevel = com.intellij.pom.java.LanguageLevel.JDK_21

    // The following code exists in Metalava, but we don't seem to need it.
//        config.kotlinLanguageLevel = kotlinLanguageLevel
//        config.addSourceRoots(listOf(File(root)))
//        config.addClasspathRoots(classpath.map { it.absoluteFile })
//        options.jdkHome?.let {
//            if (options.isJdkModular(it)) {
//                config.kotlinCompilerConfig.put(JVMConfigurationKeys.JDK_HOME, it)
//                config.kotlinCompilerConfig.put(JVMConfigurationKeys.NO_JDK, false)
//            }
//        }

    return UastEnvironment.create(config)
}
