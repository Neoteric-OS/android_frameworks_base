/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.hoststubgen.filters

import com.android.hoststubgen.ParseException
import com.android.hoststubgen.asm.ClassNodes
import com.android.hoststubgen.asm.splitWithLastPeriod
import com.android.hoststubgen.asm.toHumanReadableClassName
import com.android.hoststubgen.asm.toJvmClassName
import com.android.hoststubgen.log
import com.android.hoststubgen.normalizeTextLine
import com.android.hoststubgen.whitespaceRegex
import org.objectweb.asm.tree.ClassNode
import java.io.BufferedReader
import java.io.FileReader
import java.io.PrintWriter
import java.io.Reader
import java.util.regex.Pattern

/**
 * Print a class node as a "keep" policy.
 */
fun printAsTextPolicy(pw: PrintWriter, cn: ClassNode) {
    pw.printf("class %s %s\n", cn.name.toHumanReadableClassName(), "keep")

    cn.fields?.let {
        for (f in it.sortedWith(compareBy({ it.name }))) {
            pw.printf("    field %s %s\n", f.name, "keep")
        }
    }
    cn.methods?.let {
        for (m in it.sortedWith(compareBy({ it.name }, { it.desc }))) {
            pw.printf("    method %s %s %s\n", m.name, m.desc, "keep")
        }
    }
}

private const val FILTER_REASON = "file-override"

enum class SpecialClass {
    NotSpecial,
    Aidl,
    FeatureFlags,
    Sysprops,
    RFile,
}

/**
 * This receives [TextFileFilterPolicyBuilder] parsing result.
 */
interface PolicyFileVisitor {
    /** "package" diretive. */
    fun visitPackage(name: String, policy: FilterPolicyWithReason)

    /** "rename" diretive. */
    fun visitRename(pattern: Pattern, prefix: String)

    /** "class" diretive. */
    fun visitClass(): ClassPolicyVisitor
    interface ClassPolicyVisitor {
        fun visitRegularClassPolicy(name: String, policy: FilterPolicyWithReason)
        fun visitSubClassPolicy(superClass: String, policy: FilterPolicyWithReason)
        fun visitRedirectionClass(fromName: String, toClass: String)
        fun visitClassLoadHook(name: String, callback: String)
        fun visitSpecialClassPolicy(type: SpecialClass, policy: FilterPolicyWithReason)
        fun visitClassEnd()
    }

    /** "field" diretive. */
    fun visitField(className: String, name: String, policy: FilterPolicyWithReason)

    /** "method" diretive. */
    fun visitMethod(className: String, name: String, desc: String): MethodPolicyVisitor
    interface MethodPolicyVisitor {
        /** Called when a policy is not of a "replace". */
        fun visitRegularMethodPolicy(policy: FilterPolicyWithReason)
        fun visitInClassReplace(targetName: String, policy: FilterPolicyWithReason)
        fun visitOutClassReplace(
            replaceSpec: TextFilePolicyMethodReplaceFilter.MethodCallReplaceSpec,
            policy: FilterPolicyWithReason,
        )
    }
}

class TextFileFilterPolicyBuilder(
    private val classes: ClassNodes,
    fallback: OutputFilter
) {
    private val parser = TextFileFilterPolicyParser()

    private val subclassFilter = SubclassFilter(classes, fallback)
    private val packageFilter = PackageFilter(subclassFilter)
    private val imf = InMemoryOutputFilter(classes, packageFilter)
    private var aidlPolicy: FilterPolicyWithReason? = null
    private var featureFlagsPolicy: FilterPolicyWithReason? = null
    private var syspropsPolicy: FilterPolicyWithReason? = null
    private var rFilePolicy: FilterPolicyWithReason? = null
    private val typeRenameSpec = mutableListOf<TextFilePolicyRemapperFilter.TypeRenameSpec>()
    private val methodReplaceSpec =
        mutableListOf<TextFilePolicyMethodReplaceFilter.MethodCallReplaceSpec>()

    /**
     * Parse a given policy file. This method can be called multiple times to read from
     * multiple files. To get the resulting filter, use [createOutputFilter]
     */
    fun parse(file: String) {
        // We may parse multiple files, but we reuse the same parser, because the parser
        // will make sure there'll be no dupplicating "special class" policies.
        parser.parse(FileReader(file), file, Visitor())
    }

    /**
     * Generate the resulting [OutputFilter].
     */
    fun createOutputFilter(): OutputFilter {
        var ret: OutputFilter = imf
        if (typeRenameSpec.isNotEmpty()) {
            ret = TextFilePolicyRemapperFilter(typeRenameSpec, ret)
        }
        if (methodReplaceSpec.isNotEmpty()) {
            ret = TextFilePolicyMethodReplaceFilter(methodReplaceSpec, classes, ret)
        }

        // Wrap the in-memory-filter with AHF.
        ret = AndroidHeuristicsFilter(
            classes, aidlPolicy, featureFlagsPolicy, syspropsPolicy, rFilePolicy, ret
        )

        return ret
    }

    private inner class Visitor : PolicyFileVisitor {
        override fun visitPackage(name: String, policy: FilterPolicyWithReason) {
            packageFilter.addPolicy(name, policy)
        }

        override fun visitRename(pattern: Pattern, prefix: String) {
            typeRenameSpec += TextFilePolicyRemapperFilter.TypeRenameSpec(
                pattern, prefix
            )
        }

        override fun visitClass(): PolicyFileVisitor.ClassPolicyVisitor {
            return object : PolicyFileVisitor.ClassPolicyVisitor {
                override fun visitClassEnd() {
                }

                override fun visitRegularClassPolicy(
                    name: String,
                    policy: FilterPolicyWithReason,
                    ) {
                    log.i("class $name")
                    imf.setPolicyForClass(name, policy)
                }

                override fun visitSubClassPolicy(
                    superClass: String,
                    policy: FilterPolicyWithReason,
                    ) {
                    log.i("class extends $superClass")
                    subclassFilter.addPolicy( superClass, policy)
                }

                override fun visitRedirectionClass(fromName: String, toClass: String) {
                    imf.setRedirectionClass(fromName, toClass)
                }

                override fun visitClassLoadHook(name: String, callback: String) {
                    imf.setClassLoadHook(name, callback)
                }

                override fun visitSpecialClassPolicy(
                    type: SpecialClass,
                    policy: FilterPolicyWithReason,
                ) {
                    log.i("class special $type $policy")
                    when (type) {
                        SpecialClass.NotSpecial -> {} // Shouldn't happen

                        SpecialClass.Aidl -> {
                            aidlPolicy = policy
                        }

                        SpecialClass.FeatureFlags -> {
                            featureFlagsPolicy = policy
                        }

                        SpecialClass.Sysprops -> {
                            syspropsPolicy = policy
                        }

                        SpecialClass.RFile -> {
                            rFilePolicy = policy
                        }
                    }
                }
            }
        }

        override fun visitField(className: String, name: String, policy: FilterPolicyWithReason) {
            log.i("  field $className.$name $policy")
            imf.setPolicyForField(className, name, policy)
        }

        override fun visitMethod(
            className: String,
            name: String,
            desc: String
        ): PolicyFileVisitor.MethodPolicyVisitor {
            log.i("  method $className.$name $desc")
            return object : PolicyFileVisitor.MethodPolicyVisitor {
                override fun visitRegularMethodPolicy(policy: FilterPolicyWithReason) {
                    imf.setPolicyForMethod(className, name, desc, policy)
                }

                override fun visitInClassReplace(
                    targetName: String,
                    policy: FilterPolicyWithReason,
                    ) {
                    imf.setPolicyForMethod(className, name, desc, policy)
                    imf.setPolicyForMethod(className, targetName, desc, FilterPolicy.Keep.withReason(FILTER_REASON))
                    imf.setRenameTo(className, targetName, desc, name)
                }

                override fun visitOutClassReplace(
                    replaceSpec: TextFilePolicyMethodReplaceFilter.MethodCallReplaceSpec,
                    policy: FilterPolicyWithReason,
                ) {
                    imf.setPolicyForMethod(className, name, desc, policy)
                    methodReplaceSpec.add(replaceSpec)
                }
            }
        }
    }
}

class TextFileFilterPolicyParser {
    private lateinit var visitor: PolicyFileVisitor
    private var currentClassName: String? = null
    private var lastClassVisitor: PolicyFileVisitor.ClassPolicyVisitor? = null

    private var aidlPolicy: FilterPolicyWithReason? = null
    private var featureFlagsPolicy: FilterPolicyWithReason? = null
    private var syspropsPolicy: FilterPolicyWithReason? = null
    private var rFilePolicy: FilterPolicyWithReason? = null

    /**
     * Parse a given "policy" file.
     */
    fun parse(reader: Reader, inputName: String, visitor: PolicyFileVisitor) {
        log.i("Parsing text policy file $inputName ...")
        this.visitor = visitor
        BufferedReader(reader).use { rd ->
            var lineNo = 0
            try {
                while (true) {
                    var line = rd.readLine()
                    if (line == null) {
                        break
                    }
                    lineNo++
                    line = normalizeTextLine(line)
                    if (line.isEmpty()) {
                        continue
                    }
                    parseLine(line)
                }
                finishCurrentClass()
            } catch (e: ParseException) {
                throw e.withSourceInfo(inputName, lineNo)
            }
        }
    }

    private fun finishCurrentClass() {
        lastClassVisitor?.let {
            it.visitClassEnd()
            lastClassVisitor = null
            currentClassName = null
        }
    }

    private fun ensureInClass(directive: String) {
        if (lastClassVisitor == null) {
            throw ParseException("Directive '$directive' must follow a 'class' directive")
        }
    }

    private fun parseLine(line: String) {
        val fields = line.split(whitespaceRegex).toTypedArray()
        when (fields[0].lowercase()) {
            "p", "package" -> {
                finishCurrentClass()
                parsePackage(fields)
            }
            "c", "class" -> {
                finishCurrentClass()
                parseClass(fields)
            }
            "f", "field" -> {
                ensureInClass("field")
                parseField(fields)
            }
            "m", "method" -> {
                ensureInClass("method")
                parseMethod(fields)
            }
            "r", "rename" -> {
                finishCurrentClass()
                parseRename(fields)
            }
            else -> throw ParseException("Unknown directive \"${fields[0]}\"")
        }
    }

    private fun resolveSpecialClass(className: String): SpecialClass {
        if (!className.startsWith(":")) {
            return SpecialClass.NotSpecial
        }
        when (className.lowercase()) {
            ":aidl" -> return SpecialClass.Aidl
            ":feature_flags" -> return SpecialClass.FeatureFlags
            ":sysprops" -> return SpecialClass.Sysprops
            ":r" -> return SpecialClass.RFile
        }
        throw ParseException("Invalid special class name \"$className\"")
    }

    private fun resolveExtendingClass(className: String): String? {
        if (!className.startsWith("*")) {
            return null
        }
        return className.substring(1)
    }

    private fun parsePolicy(s: String): FilterPolicy {
        return when (s.lowercase()) {
            "k", "keep" -> FilterPolicy.Keep
            "t", "throw" -> FilterPolicy.Throw
            "r", "remove" -> FilterPolicy.Remove
            "kc", "keepclass" -> FilterPolicy.KeepClass
            "i", "ignore" -> FilterPolicy.Ignore
            "rdr", "redirect" -> FilterPolicy.Redirect
            else -> {
                if (s.startsWith("@")) {
                    FilterPolicy.Substitute
                } else {
                    throw ParseException("Invalid policy \"$s\"")
                }
            }
        }
    }

    private fun parsePackage(fields: Array<String>) {
        if (fields.size < 3) {
            throw ParseException("Package ('p') expects 2 fields.")
        }
        val name = fields[1]
        val rawPolicy = fields[2]
        if (resolveExtendingClass(name) != null) {
            throw ParseException("Package can't be a super class type")
        }
        if (resolveSpecialClass(name) != SpecialClass.NotSpecial) {
            throw ParseException("Package can't be a special class type")
        }
        if (rawPolicy.startsWith("!")) {
            throw ParseException("Package can't have a substitution")
        }
        if (rawPolicy.startsWith("~")) {
            throw ParseException("Package can't have a class load hook")
        }
        val policy = parsePolicy(rawPolicy)
        if (!policy.isUsableWithClasses) {
            throw ParseException("Package can't have policy '$policy'")
        }
        visitor.visitPackage(name, policy.withReason(FILTER_REASON))
    }

    private fun parseClass(fields: Array<String>) {
        if (fields.size < 3) {
            throw ParseException("Class ('c') expects 2 fields.")
        }
        val name = fields[1]
        currentClassName = name
        val cv = visitor.visitClass()
        lastClassVisitor = cv

        // superClass is set when the class name starts with a "*".
        val superClass = resolveExtendingClass(name)

        // :aidl, etc?
        val classType = resolveSpecialClass(name)

        if (fields[2].startsWith("!")) {
            if (classType != SpecialClass.NotSpecial) {
                // We could support it, but not needed at least for now.
                throw ParseException(
                    "Special class can't have a substitution"
                )
            }
            // It's a redirection class.
            val toClass = fields[2].substring(1)

            cv.visitRedirectionClass(name, toClass)
        } else if (fields[2].startsWith("~")) {
            if (classType != SpecialClass.NotSpecial) {
                // We could support it, but not needed at least for now.
                throw ParseException(
                    "Special class can't have a class load hook"
                )
            }
            // It's a class-load hook
            val callback = fields[2].substring(1)

            cv.visitClassLoadHook(name, callback)
        } else {
            val policy = parsePolicy(fields[2])
            if (!policy.isUsableWithClasses) {
                throw ParseException("Class can't have policy '$policy'")
            }

            when (classType) {
                SpecialClass.NotSpecial -> {
                    // TODO: Duplicate check, etc
                    if (superClass == null) {
                        cv.visitRegularClassPolicy(name, policy.withReason(FILTER_REASON))
                    } else {
                        cv.visitSubClassPolicy(
                            superClass,
                            policy.withReason("extends $superClass"),
                        )
                        finishCurrentClass() // Can't have members.
                    }
                }
                SpecialClass.Aidl -> {
                    if (aidlPolicy != null) {
                        throw ParseException(
                            "Policy for AIDL classes already defined"
                        )
                    }
                    val p = policy.withReason(
                        "$FILTER_REASON (special-class AIDL)"
                    )
                    cv.visitSpecialClassPolicy(classType, p)
                    aidlPolicy = p

                    finishCurrentClass() // Can't have members.
                }

                SpecialClass.FeatureFlags -> {
                    if (featureFlagsPolicy != null) {
                        throw ParseException(
                            "Policy for feature flags already defined"
                        )
                    }
                    val p = policy.withReason(
                        "$FILTER_REASON (special-class feature flags)"
                    )
                    cv.visitSpecialClassPolicy(classType, p)
                    featureFlagsPolicy = p

                    finishCurrentClass() // Can't have members.
                }

                SpecialClass.Sysprops -> {
                    if (syspropsPolicy != null) {
                        throw ParseException(
                            "Policy for sysprops already defined"
                        )
                    }
                    val p = policy.withReason(
                        "$FILTER_REASON (special-class sysprops)"
                    )
                    cv.visitSpecialClassPolicy(classType, p)
                    syspropsPolicy = p

                    finishCurrentClass() // Can't have members.
                }

                SpecialClass.RFile -> {
                    if (rFilePolicy != null) {
                        throw ParseException(
                            "Policy for R file already defined"
                        )
                    }
                    val p = policy.withReason(
                        "$FILTER_REASON (special-class R file)"
                    )
                    cv.visitSpecialClassPolicy(classType, p)
                    rFilePolicy = p

                    finishCurrentClass() // Can't have members.
                }
            }
        }
    }

    private fun parseField(fields: Array<String>) {
        if (fields.size < 3) {
            throw ParseException("Field ('f') expects 2 fields.")
        }
        val name = fields[1]
        val policy = parsePolicy(fields[2])
        if (!policy.isUsableWithFields) {
            throw ParseException("Field can't have policy '$policy'")
        }

        // TODO: Duplicate check, etc
        visitor.visitField(currentClassName!!, name, policy.withReason(FILTER_REASON))
    }

    private fun parseMethod(fields: Array<String>) {
        if (fields.size < 3 || fields.size > 4) {
            throw ParseException("Method ('m') expects 3 or 4 fields.")
        }
        val name = fields[1]
        val signature: String
        val policyStr: String
        if (fields.size <= 3) {
            signature = "*"
            policyStr = fields[2]
        } else {
            signature = fields[2]
            policyStr = fields[3]
        }

        val policy = parsePolicy(policyStr)

        if (!policy.isUsableWithMethods) {
            throw ParseException("Method can't have policy '$policy'")
        }

        val mv = visitor.visitMethod(currentClassName!!, name, signature)

        val policyWithReason = policy.withReason(FILTER_REASON)
        if (policy != FilterPolicy.Substitute) {
            mv.visitRegularMethodPolicy(policyWithReason)
        } else {
            val targetName = policyStr.substring(1)

            if (targetName == name) {
                throw ParseException(
                    "Substitution must have a different name"
                )
            }

// This was probably a bug -- we didn't need it on the "if" case
//            // Set the policy for the "from" method.
//            imf.setPolicyForMethod(
//                currentClassName, fromName, signature,
//                FilterPolicy.Keep.withReason(FILTER_REASON)
//            )

            val classAndMethod = splitWithLastPeriod(targetName)
            if (classAndMethod != null) {
                // If the substitution target contains a ".", then
                // it's a method call redirect.
                val spec = TextFilePolicyMethodReplaceFilter.MethodCallReplaceSpec(
                        currentClassName!!.toJvmClassName(),
                        name,
                        signature,
                        classAndMethod.first.toJvmClassName(),
                        classAndMethod.second,
                    )
                mv.visitOutClassReplace(spec, policyWithReason)
            } else {
                // It's an in-class replace.
                // ("@RavenwoodReplace" equivalent)
                mv.visitInClassReplace(targetName, policyWithReason)
            }
        }
    }

    private fun parseRename(fields: Array<String>) {
        if (fields.size < 3) {
            throw ParseException("Rename ('r') expects 2 fields.")
        }
        // Add ".*" to make it a prefix match.
        val pattern = Pattern.compile(fields[1] + ".*")

        // Removing the leading /'s from the prefix. This allows
        // using a single '/' as an empty suffix, which is useful to have a
        // "negative" rename rule to avoid subsequent raname's from getting
        // applied. (Which is needed for services.jar)
        val prefix = fields[2].trimStart('/')

        visitor.visitRename(
            pattern, prefix
        )
    }
}
