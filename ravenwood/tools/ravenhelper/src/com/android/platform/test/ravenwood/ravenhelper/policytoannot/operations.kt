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

enum class SourceOperationType {
    Insert,
    Delete,
    Change,
    Prepend,
}

data class SourceOperation(
    /** Target file to edit. */
    val sourceFile: String,

    /** 1-based line number. Use -1 to add at the end of the file. */
    val lineNumber: Int,

    val type: SourceOperationType,

    val text: String = "",

    /** Human readable description of why this operation was created */
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

/**
 * Create a shell script to apply all the operations.
 */
fun createShellScript(ops: SourceOperations, writer: BufferedWriter) {
}
