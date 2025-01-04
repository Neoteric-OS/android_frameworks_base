@file:JvmName("RavenHelperMain")
package com.android.platform.test.ravenwood.ravenhelper


import com.android.hoststubgen.LogLevel
import com.android.hoststubgen.executableName
import com.android.hoststubgen.log
import com.android.hoststubgen.runMainWithBoilerplate
import com.android.tools.lint.UastEnvironment
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import java.io.File
import com.android.tools.lint.annotations.Extractor
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.uast.kotlin.desc


// References:
// - We stole code from Metalava, but the latest version is too complecated,
// so code was stolen from an older version:
// https://android.git.corp.google.com/platform/tools/metalava/+/refs/heads/android13-dev
//
// - PSI is source code is available in IntelliJ's code base:
// https://github.com/JetBrains/intellij-community.git
//
// - Lint is in Android studio
// Read https://android.googlesource.com/platform/tools/base/+/studio-master-dev/source.md


fun main(args: Array<String>) {
    executableName = "RavenHelper"
    log.setConsoleLogLevel(LogLevel.Info)

    runMainWithBoilerplate {
        log.i("$executableName started")

        // val filePath = "/android/aosp-main-with-vendor-blobs2/frameworks/base/core/java/android/content/Intent.java"

        if (args.size <= 1) {
            println("Usage: java ClassMethodLineNumbers SOURCE...")
            return
        }
        val sourceFiles = args.toList()

        val config = UastEnvironment.Configuration.create(
            enableKotlinScripting = false,
            useFirUast = false,
        )

        config.javaLanguageLevel = com.intellij.pom.java.LanguageLevel.JDK_21
//        config.kotlinLanguageLevel = kotlinLanguageLevel
//        config.addSourceRoots()
//        config.addClasspathRoots(classpath.map { it.absoluteFile })
//        options.jdkHome?.let {
//            if (options.isJdkModular(it)) {
//                config.kotlinCompilerConfig.put(JVMConfigurationKeys.JDK_HOME, it)
//                config.kotlinCompilerConfig.put(JVMConfigurationKeys.NO_JDK, false)
//            }
//        }

        val env = UastEnvironment.create(config)

//        env.analyzeFiles(files) // needed for kotlin files

        val dumper = SourceMapDumper(env)
        dumper.dumpSources(sourceFiles)

        env.dispose()
        UastEnvironment.disposeApplicationEnvironment()

    }
}

class SourceMapDumper(
    val environment: UastEnvironment,
    ) {
    fun dumpSources(sources: List<String>) {
        val psiFiles = Extractor.createUnitsForFiles(
            environment.ideaProject,
            sources.map { File(it) },
        )

        for (file in psiFiles.asSequence().distinct()) {
            log.i("File: ${file}")

            var classes = (file as? PsiClassOwner)?.classes?.toList() ?: emptyList()
            classes.forEach { clazz ->
                dumpClass(clazz)
            }
        }
    }

    fun dumpClass(clazz: PsiClass) {
        log.i("  Class: ${clazz.qualifiedName}")
        dumpPosition(clazz)

        clazz.fields.forEach {
//            if (it.containingClass != clazz) {
//                return@forEach
//            }
            log.i("  Field: ${it.name}")
            dumpPosition(it, "  ")
        }

        clazz.methods.forEach {
//            if (it.containingClass != clazz) {
//                return@forEach
//            }
            log.i("  Method: ${it.name}")
//            log.i("  Desc: ${it.desc}")
            dumpPosition(it, "  ")
        }

        clazz.innerClasses.forEach { dumpClass(it) }
    }

    fun dumpPosition(clazz: PsiElement, prefix: String = "") {
        log.i("$prefix  - File: ${clazz.containingFile}")
        log.i("$prefix  - Offset: ${clazz.textOffset}")
    }
}