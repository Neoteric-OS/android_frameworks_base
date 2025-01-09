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
package com.android.platform.test.ravenwood.ravenhelper.policytoannot

import com.android.hoststubgen.log
import java.io.BufferedWriter
import java.io.File

enum class SourceOperationType {
    Insert,
    Delete,
    Prepend,
}

data class SourceOperation(
    /** Target file to edit. */
    val sourceFile: String,

    /** 1-based line number. Use -1 to add at the end of the file. */
    val lineNumber: Int,

    val type: SourceOperationType,

    val text: String = "",

    /** Human-readable description of why this operation was created */
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
        fileOperations[op.sourceFile]?.let { ops ->
            ops.add(op)
            return
        }
        fileOperations[op.sourceFile] = mutableListOf(op)
    }

    fun getOperations(): MutableMap<String, MutableList<SourceOperation>> {
        return fileOperations
    }
}

/**
 * Create a shell script to apply all the operations.
 */
fun createShellScript(ops: SourceOperations, writer: BufferedWriter) {
    // File header
    writer.write(
        """
        #!/bin/bash
        
        set -e # Finish when any command fails.
        """.trimIndent()
    )

    ops.getOperations().toSortedMap().forEach { (origFile, ops) ->
        val file = File(origFile).absolutePath

        writer.write("\n\nFILE='$file'\n")

        writer.write("sed -i -e '\n")
        toSedScript(ops, writer)
        writer.write("    ' \"\$FILE\"\n")
    }

    writer.flush()
}

private fun toSedScript(ops: List<SourceOperation>, writer: BufferedWriter) {
    ops.forEach { op ->
        writer.write("\n")
        writer.write("# ${op.description}\n")
        if (op.lineNumber >= 0) {
            writer.write(op.lineNumber.toString())
        } else {
            writer.write("$")
        }
        when (op.type) {
            SourceOperationType.Insert -> {
                writer.write("i\n")
                writer.write(op.text)
                writer.write("\n")
            }
            SourceOperationType.Delete -> {
                writer.write("d\n")
            }
            SourceOperationType.Prepend -> {
                // TODO: Escape
                writer.write("s!^!${op.text}!\n")
            }
        }
    }
}