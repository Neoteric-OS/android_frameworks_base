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
package com.android.platform.test.ravenwood.ravenhelper.sourcemap

import com.android.hoststubgen.log
import com.android.tools.lint.UastEnvironment
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNameIdentifierOwner
import java.io.File


/**
 * Represents the location of an item. (class, field or method)
 */
data class Location (
    /** Full path filename. */
    val file: String,

    /** 1-based line number */
    val line: Int,
)

/**
 * Represents the type of item.
 */
enum class ItemType {
    Class,
    Field,
    Method,
}

data class FieldInfo (
    val name: String,
    val location: Location,
)

data class MethodInfo (
    val name: String,
    /** "Simplified" description. */
    val simpleDesc: String,
    val location: Location,
)

data class ClassInfo (
    val fullName: String,
    val location: Location,
    val fields: MutableMap<String, FieldInfo> = mutableMapOf(),
    val methods: MutableMap<String, MutableList<MethodInfo>> = mutableMapOf(),
) {
    fun add(fi: FieldInfo) {
        fields.put(fi.name, fi)
    }

    fun add(mi: MethodInfo) {
        val list = methods.get(mi.name)
        list?.add(mi) ?: {
            methods.put(mi.name, mutableListOf(mi))
        }
    }
}

data class AllClassInfo (
    val classes: MutableMap<String, ClassInfo> = mutableMapOf(),
) {
    fun add(ci: ClassInfo) {
        classes.put(ci.fullName, ci)
    }
}

fun typeToSimpleDesc(origType: String): String {
    var type = origType

    // Detect arrays.
    var arrayPrefix = ""
    while (type.endsWith("[]")) {
        arrayPrefix += "["
        type = type.substring(0, type.length - 2)
    }

    // Delete generic parameters. (delete everything after '<')
    type.indexOf('<').let { pos ->
        if (pos >= 0) {
            type = type.substring(0, pos)
        }
    }

    // Handle builtins.
    val builtinType = when (type) {
        "byte" -> "B"
        "short" -> "S"
        "int" -> "I"
        "long" -> "J"
        "float" -> "F"
        "double" -> "D"
        "boolean" -> "Z"
        "char" -> "C"
        "void" -> "V"
        else -> null
    }

    builtinType?.let {
        return arrayPrefix + builtinType
    }

    return arrayPrefix + "L" + type + ";"
}

/**
 * Get a "simple" description of a method.
 *
 * "Simple" descriptions are similar to "real" ones, except:
 * - No return type.
 * - No package names in type names.
 */
fun getSimpleDesc(method: PsiMethod): String {
    val sb = StringBuilder()

    sb.append("(")

    val params = method.parameterList
    for (i in 0..<params.parametersCount) {
        val param = params.getParameter(i)

        val type = param?.type?.presentableText

        if (type == null) {
            throw RuntimeException(
                "Unable to decode parameter list from method from ${params.parent}")
        }

        sb.append(typeToSimpleDesc(type))
    }

    sb.append(")")

    return sb.toString()
}


class SourceMapGenerator(
    val environment: UastEnvironment,
) {
    private val fileSystem = StandardFileSystems.local()
    private val manager = PsiManager.getInstance(environment.ideaProject)

    private fun loadFileOrDirectory(file: File, result: MutableList<PsiFile>) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                loadFileOrDirectory(child, result)
            }
            return
        }

        // It's a file
        when (file.extension) {
            "java" -> {
                // Load it.
                log.forVerbose {
                    log.v("Loading ${file.path} ...")
                }
            }
            "kt" ->  {
                log.w("Kotlin not supported, not loading ${file.path}")
                return
            }
            else -> {
                return // Silently skip
            }
        }
        fileSystem.findFileByPath(file.path)?.let { virtualFile ->
            manager.findFile(virtualFile)?.let { psiFile ->
                result.add(psiFile)
            }
        }
    }

    fun loadFilesOrDirectories(sources: List<File>): List<PsiFile> {
        val result: MutableList<PsiFile> = ArrayList(sources.size)
        sources.forEach {
            loadFileOrDirectory(it, result)
        }
        return result
    }

    fun loadClasses(sources: List<String>): AllClassInfo {
        log.i("Discovering source files...")
        val psiFiles = loadFilesOrDirectories(sources.map { File(it) })

        val ret = AllClassInfo()

        log.i("Parsing source files...")
        for (file in psiFiles.asSequence().distinct()) {
            log.v("Loading ${file} ...")

            val classes = (file as? PsiClassOwner)?.classes?.toList() ?: emptyList()
            classes.forEach { clazz ->
                loadClass(clazz)?.let { ret.add(it) }

                clazz.innerClasses.forEach { inner ->
                    loadClass(inner)?.let { ret.add(it) }
                }
            }
        }
        return ret
    }

    fun loadClass(clazz: PsiClass): ClassInfo? {
        log.forVerbose {
            log.v("Found: ${clazz.qualifiedName}")
        }
        val ci = ClassInfo(
            clazz.qualifiedName!!,
            getLocation(clazz) ?: return null,
        )
        clazz.fields.forEach {
            ci.add(FieldInfo(
                it.name,
                getLocation(it) ?: return@forEach,
            ))
        }
        clazz.methods.forEach {
            ci.add(MethodInfo(
                it.name,
                getSimpleDesc(it),
                getLocation(it) ?: return@forEach,
            ))
        }
        return ci
    }

    fun dumpSources(sources: List<String>) {
        loadClasses(sources).classes.values.sortedBy { it.fullName }.forEach { dumpClass(it) }
    }

    fun dumpClass(ci: ClassInfo) {
        log.i("Class: ${ci.fullName}")
        dumpPosition(ci.location)

        ci.fields.values.sortedBy { it.name } .forEach {
            log.i("  Field: ${it.name}")
            dumpPosition(it.location, "  ")
        }

        ci.methods.keys.sorted().forEach { name ->
            ci.methods[name]!!.sortedBy { it.simpleDesc }.forEach {
                log.i("  Method: ${it.name}")
                dumpPosition(it.location, "  ")
                log.i("    desc: ${it.simpleDesc}")
            }
        }
    }

    fun getLocation(elem: PsiElement): Location? {
        val line = getLineNumber(elem)
        if (line == null) {
            log.w("Unable to determine location of ${elem}")
            return null
        }
        return Location(
            elem.containingFile.originalFile.virtualFile.path,
            line,
        )
    }

    fun dumpPosition(loc: Location, prefix: String = "") {
        log.i("$prefix  Location: ${loc.file}:${loc.line}")
    }

    fun getLineNumber(element: PsiElement): Int? {
        // Actual elements such as PsiClass, PsiMethod and PsiField contains the leading
        // javadoc, etc, so use the "identifier"'s element, if available.
        val e = (element as PsiNameIdentifierOwner).nameIdentifier ?: element

        val psiFile: PsiFile = e.containingFile ?: return null
        val document: Document = psiFile.viewProvider.document ?: return null
        val textRange = e.textRange

        // Line numbers are 0-based, add 1 for human-readable format
        return document.getLineNumber(textRange.startOffset) + 1
    }
}