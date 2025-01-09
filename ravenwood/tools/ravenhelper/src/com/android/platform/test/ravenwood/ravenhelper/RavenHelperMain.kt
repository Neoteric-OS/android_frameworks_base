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
@file:JvmName("RavenHelperMain")
package com.android.platform.test.ravenwood.ravenhelper


import com.android.hoststubgen.LogLevel
import com.android.hoststubgen.executableName
import com.android.hoststubgen.filters.FilterPolicy
import com.android.hoststubgen.filters.FilterPolicyWithReason
import com.android.hoststubgen.filters.PolicyFileProcessor
import com.android.hoststubgen.filters.SpecialClass
import com.android.hoststubgen.filters.TextFileFilterPolicyParser
import com.android.hoststubgen.filters.TextFilePolicyMethodReplaceFilter
import com.android.hoststubgen.log
import com.android.hoststubgen.runMainWithBoilerplate
import com.android.platform.test.ravenwood.ravenhelper.psi.createUastEnvironment
import com.android.platform.test.ravenwood.ravenhelper.sourcemap.AllClassInfo
import com.android.platform.test.ravenwood.ravenhelper.sourcemap.ClassInfo
import com.android.platform.test.ravenwood.ravenhelper.sourcemap.SourceLoader
import com.android.tools.lint.UastEnvironment
import java.io.FileReader
import java.util.regex.Pattern

fun main(args: Array<String>) {
    executableName = "RavenHelper"
    log.setConsoleLogLevel(LogLevel.Verbose)

    runMainWithBoilerplate {
        log.i("$executableName started")

        // val filePath = "/android/aosp-main-with-vendor-blobs2/frameworks/base/core/java/android/content/Intent.java"

        if (args.size == 0) {
            println("Usage: ravenwhelper SOURCE...")
            return
        }
        val sourceFiles = args.toList()

        // /android/aosp-main-with-vendor-blobs1/frameworks/base/ravenwood/tools/hoststubgen/test-tiny-framework/policy-override-tiny-framework.txt
        val policyFiles = listOf("/android/aosp-main-with-vendor-blobs1/frameworks/base/ravenwood/tools/hoststubgen/test-tiny-framework/policy-override-tiny-framework.txt")

        val dumpPsiResult = true

        TextPolicyToAnnotationConverter(
            policyFiles,
            sourceFiles,
            "/android/aosp-main-with-vendor-blobs1/frameworks/base/ravenwood/tools/hoststubgen/test-tiny-framework/annotation-allowed-classes-tiny-framework.txt",
            Annotations(),
            dumpPsiResult,
            ).process()

        UastEnvironment.disposeApplicationEnvironment()
    }
}

enum class SourceOperationType {
    Insert,
    Delete,
    Change,
    Prepend,
}

data class SourceOperation(
    val sourceFile: String,

    /** 1-based line number. Use -1 to add at the end of the file. */
    val lineNumber: Int,

    val type: SourceOperationType,

    val text: String = "",

    val description: String,
) {
    override fun toString(): String {
        return "SourceOperation(sourceFile='$sourceFile', " +
            "lineNumber=$lineNumber, type=$type, text='$text' desc='$description')"
    }
}

class SourceOperations {
    private val fileOperations = mutableMapOf<String, MutableList<SourceOperation>>()

    fun add(op: SourceOperation) {
        log.forVerbose {
            log.v("Adding operation: $op")
        }
        fileOperations.get(op.sourceFile)?.let { ops ->
            ops.add(op)
            return
        }
        fileOperations.put(op.sourceFile, mutableListOf(op))
    }

    fun getOperations(): MutableMap<String, MutableList<SourceOperation>> {
        return fileOperations
    }
}

class Annotations {
    enum class Targert {
        Class,
        Field,
        Method,
    }

    fun get(policy: FilterPolicy, target: Targert): String? {
        return when (policy) {
            FilterPolicy.Keep ->
                if (target == Targert.Class) {
                    "@android.ravenwood.annotation.RavenwoodKeepPartialClass"
                } else {
                    "@android.ravenwood.annotation.RavenwoodKeep"
                }
            FilterPolicy.KeepClass ->
                "@android.ravenwood.annotation.RavenwoodKeepWholeClass"
            FilterPolicy.Substitute ->
                "@android.ravenwood.annotation.RavenwoodReplace"
            FilterPolicy.Redirect ->
                "@android.ravenwood.annotation.RavenwoodRedirect"
            FilterPolicy.Throw ->
                "@android.ravenwood.annotation.RavenwoodThrow"
            FilterPolicy.Ignore -> null // Ignore has no annotation. (because it's not very safe.)
            FilterPolicy.Remove ->
                "@android.ravenwood.annotation.RavenwoodRemove"
        }
    }

    private fun getAnnotationWithArg(annot: String, arg: String): String {
        return "@$annot(\"$arg\")"
    }

    fun getClassLoadHookAnnotation(arg: String): String {
        return getAnnotationWithArg(
            "android.ravenwood.annotation.RavenwoodClassLoadHook", arg)
    }

    fun getRedirectionClassAnnotation(arg: String): String {
        return getAnnotationWithArg(
            "android.ravenwood.annotation.RavenwoodRedirectionClass", arg)
    }

//    fun get(policy: FilterPolicy, target: Targert, arg: String = ""): String? {
//        val annot = get(policy, target)
//        if (annot == null) {
//            return null
//        } else if (arg.isEmpty()) {
//            return annot
//        } else {
//            return annot + "(\"" + arg + "\")"
//        }
//    }

}

class TextPolicyToAnnotationConverter(
    val policyFiles: List<String>,
    val sourceFilesOrDirectories: List<String>,
    val annotationAllowedListFile: String,
    val annotations: Annotations,
    val reallyVerbose: Boolean,
) {
    private val resultOperations = SourceOperations()
    private val classes = AllClassInfo()
    private val policyParser = TextFileFilterPolicyParser()
    private val annotationAddedClasses = mutableSetOf<String>()

    fun process() {
        // First, load
        val env = createUastEnvironment()
        try {
            loadSources()

//            if (reallyVerbose) {
//                log.withIndent {
//                    classes.dump()
//                }
//            }

            processPolicies()
            if (reallyVerbose) {
                log.withIndent {
                    resultOperations.getOperations().toSortedMap().forEach { file, ops ->
                        log.i("ops: $file")
                        ops.forEach { op ->
                            log.i("  line: ${op.lineNumber}: ${op.type}: \"${op.text}\" " +
                                "(${op.description})")
                        }
                    }
                }
            }
        } finally {
            env.dispose()
        }
    }

    private fun loadSources() {
        val env = createUastEnvironment()
        try {
            val loader = SourceLoader(env)
            loader.load(sourceFilesOrDirectories, classes)
        } finally {
            env.dispose()
        }
    }

    private fun processPolicies() {
        policyFiles.forEach { policyFile ->
            policyParser.parse(FileReader(policyFile), policyFile, Processor())
        }
    }

    private fun warnUnsupported(message: String) {
        log.w(message)
        log.w("  policy: ${policyParser.currentLineText}")
        log.w("  at ${policyParser.filename}:${policyParser.lineNumber}")
    }

    private inner class Processor : PolicyFileProcessor {
        override fun onPackage(name: String, policy: FilterPolicyWithReason) {
            warnUnsupported("'package' directive isn't supported (yet).")
        }

        override fun onRename(pattern: Pattern, prefix: String) {
            // Rename will never be supported.
        }

        var classPolicyText = ""
        var classPolicyLine = -1
        var classLineConverted = false
        var classHasMember = false

        private fun commentOutPolicy(lineNumber: Int, description: String) {
            resultOperations.add(
                SourceOperation(
                    policyParser.filename,
                    lineNumber,
                    SourceOperationType.Prepend,
                    "# ", // comment out.
                    description,
                )
            )
        }

        override fun onSimpleClassStart(className: String) {
            classLineConverted = false
            classHasMember = false
            classPolicyLine = policyParser.lineNumber
            classPolicyText = policyParser.currentLineText
        }

        override fun onSimpleClassEnd(className: String) {
            if (!classLineConverted) {
                // Class line is still needed in the policy file.
                // (Because the source file wasn't found.)
                return
            }
            if (classLineConverted && !classHasMember) {
                commentOutPolicy(classPolicyLine, "remove class policy on $className")
            } else {
                log.w("XXX add message")
            }
        }

        private fun findClass(className: String): ClassInfo? {
            val ci = classes.findClass(className)
            if (ci == null) {
                log.w("Class $className not found.")
            }
            return ci
        }

        private fun addClassAnnotation(
            className: String,
            annotation: String,
        ): Boolean {
            val ci = findClass(className) ?: return false

            // Add the annotation to the source file.
            resultOperations.add(
                SourceOperation(
                    ci.location.file,
                    ci.location.line,
                    SourceOperationType.Insert,
                    ci.location.getIndent() + annotation,
                    "add class annotation to $className"
                )
            )
            annotationAddedClasses.add(className)
            return true
        }

        override fun onSimpleClassPolicy(className: String, policy: FilterPolicyWithReason) {
            log.v("Found simple class policy: $className - ${policy.policy}")

            val annot = annotations.get(policy.policy, Annotations.Targert.Class)!!
            if (addClassAnnotation(className, annot)) {
                classLineConverted = true
            }
        }

        override fun onSubClassPolicy(superClassName: String, policy: FilterPolicyWithReason) {
            warnUnsupported("Subclass policies isn't supported (yet).")
        }

        override fun onRedirectionClass(fromClassName: String, toClassName: String) {
            log.v("Found class redirection: $fromClassName - $toClassName")

            if (addClassAnnotation(
                fromClassName,
                annotations.getRedirectionClassAnnotation(toClassName),
                )) {
                commentOutPolicy(policyParser.lineNumber,
                    "remove class redirection policy on $fromClassName")
            }
        }

        override fun onClassLoadHook(className: String, callback: String) {
            log.v("Found class load hook: $className - $callback")

            if (addClassAnnotation(
                    className,
                    annotations.getClassLoadHookAnnotation(callback),
                )) {
                commentOutPolicy(policyParser.lineNumber,
                    "remove class load hook policy on $className")
            }
        }

        override fun onSpecialClassPolicy(type: SpecialClass, policy: FilterPolicyWithReason) {
            // This can't be converted to an annotation.
        }

        override fun onField(className: String, fieldName: String, policy: FilterPolicyWithReason) {
            log.v("Found field policy: $className.$fieldName - ${policy.policy}")

            val ci = findClass(className) ?: return

            ci.findField(fieldName)?.let { fi ->
                val annot = annotations.get(policy.policy, Annotations.Targert.Field)!!

                resultOperations.add(
                    SourceOperation(
                        fi.location.file,
                        fi.location.line,
                        SourceOperationType.Insert,
                        fi.location.getIndent() + annot,
                        "add annotation to field $className.$fieldName",
                    )
                )
                commentOutPolicy(policyParser.lineNumber,
                    "remove field policy $className.$fieldName")

                annotationAddedClasses.add(className)
            }
        }

        override fun onSimpleMethodPolicy(
            className: String,
            methodName: String,
            methodDesc: String,
            policy: FilterPolicyWithReason
        ) {
            log.v("Found simple method policy: " +
                "$className.$methodName$methodDesc - ${policy.policy}")

            val origClassHasMember = classHasMember

            // We may not be able to convert this method to an annotation, so we proactively
            // set 'true' here. We change it back to [origClassHasMember] when we're sure
            // we can convert it to an annotation.
            classHasMember = true

            val ci = findClass(className) ?: return
            val methods = ci.findMethods(methodName, methodDesc) ?: return

            // If the policy is "ignore", we can't convert it to an annotation, in which case
            // annotations.get() will return null.
            val annot = annotations.get(policy.policy, Annotations.Targert.Method) ?: return

            // Okay, we can convert it, so we can restore classHasMember.
            classHasMember = origClassHasMember

            methods.forEach { mi ->
                resultOperations.add(
                    SourceOperation(
                        mi.location.file,
                        mi.location.line,
                        SourceOperationType.Insert,
                        mi.location.getIndent() + annot,
                        "add annotation to method $className.$methodName",
                    )
                )
            }
            commentOutPolicy(policyParser.lineNumber,
                "remove method policy $className.$methodName")

            annotationAddedClasses.add(className)
        }

        override fun onMethodInClassReplace(
            className: String,
            methodName: String,
            methodDesc: String,
            targetName: String,
            policy: FilterPolicyWithReason
        ) {
            log.v("Found method replace: $className.$methodName$methodDesc - $targetName")
        }

        override fun onMethodOutClassReplace(
            className: String,
            methodName: String,
            methodDesc: String,
            replaceSpec: TextFilePolicyMethodReplaceFilter.MethodCallReplaceSpec,
            policy: FilterPolicyWithReason
        ) {
            // This can't be converted to an annotation.
            classHasMember = true
        }
    }
}