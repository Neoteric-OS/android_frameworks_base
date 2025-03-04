/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.processor

import android.annotation.IntDef
import java.io.IOException
import java.io.Writer
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.tools.Diagnostic.Kind
import javax.tools.StandardLocation

/**
 * The IntDefProcessor is intended to generate a mapping from ints to their respective string
 * identifier for each IntDef for use by Winscope or any other tool which requires such a mapping.
 *
 * The processor will run when building :framework-minus-apex-intdefs and dump all the IntDef
 * mappings found in the files that make up the build target as json to outputPath.
 */
class IntDefProcessor : AbstractProcessor() {
  private val outputName = "intDefMapping.json"
  private lateinit var elementUtils: javax.lang.model.util.Elements // Helper for elements

  override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

  // Define what the annotation we care about are for compiler optimization
  override fun getSupportedAnnotationTypes() =
      LinkedHashSet<String>().apply { add(IntDef::class.java.name) }

  override fun init(processingEnv: ProcessingEnvironment) {
    super.init(processingEnv)
    elementUtils = processingEnv.elementUtils
  }

  override fun process(annotations: Set<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
    // There should only be one matching annotation definition for intDef
    val annotationType = annotations.firstOrNull() ?: return false
    val annotatedElements = roundEnv.getElementsAnnotatedWith(annotationType)

    val annotationTypeToIntDefMapping =
        annotatedElements.associate { annotatedElement ->
          val type = (annotatedElement as TypeElement).qualifiedName.toString()
          val mapping = generateIntDefMapping(annotatedElement) // Simplified call
          val intDef = annotatedElement.getAnnotation(IntDef::class.java)
          type to IntDefMapping(mapping, intDef.flag)
        }

    try {
      outputToFile(annotationTypeToIntDefMapping)
    } catch (e: IOException) {
      error("Failed to write IntDef mappings :: $e") // Better error message
    }

    return true // We processed the annotation.
  }

  private fun generateIntDefMapping(annotatedElement: TypeElement): Map<Int, String> {
    val mapping = LinkedHashMap<Int, String>()

    val annotationMirror =
        annotatedElement.annotationMirrors.firstOrNull {
          it.annotationType.asElement().simpleName.toString() == "IntDef"
        } // More precise check

    if (annotationMirror == null) {
      return mapping // Or perhaps throw an exception if @IntDef is REALLY required.
    }

    val valueAttribute =
        annotationMirror.elementValues.entries
            .firstOrNull { entry -> entry.key.simpleName.contentEquals("value") }
            ?.value ?: return mapping

    val annotationValues =
        (valueAttribute.value as? List<*>)?.filterIsInstance<AnnotationValue>() ?: emptyList()
    for (annotationValue in annotationValues) {

      // Direct handling, not visitor use:
      val constVal = annotationValue.value
      when (constVal) {
        is VariableElement -> {

          val constValValue = constVal.constantValue

          if (constValValue is Int) {
            mapping[constValValue] = constVal.simpleName.toString()
          } else {
            error(
                "Invalid value in annotation IntDef, only int constants expected ",
                annotatedElement)
          }
        }
        is DeclaredType -> {
          val element = constVal.asElement()
          if (element is VariableElement) {

            val constValValue = element.constantValue
            if (constValValue is Int) { // Changed verification also

              mapping[constValValue] = element.simpleName.toString()
            } else {
              error(
                  "Invalid value in annotation IntDef, only int constants expected",
                  element) // Added element, to more appropiate error pointing.
            }
          }
        }
        is Int -> { // Direct usage of the Constant Value,
          mapping[constVal] = constVal.toString()
        }
        else -> {

          error(
              "Unexpected value type: " + constVal?.javaClass?.name, annotatedElement) // Put error
        }
      }
    }
    return mapping
  }

  @Throws(IOException::class)
  private fun outputToFile(annotationTypeToIntDefMapping: Map<String, IntDefMapping>) {
    val resource =
        processingEnv.filer.createResource(
            StandardLocation.SOURCE_OUTPUT, "com.android.winscope", outputName)
    val writer = resource.openWriter()
    serializeTo(annotationTypeToIntDefMapping, writer)
    writer.close()
  }

  private fun error(message: String) {
    processingEnv.messager.printMessage(Kind.ERROR, message)
  }

  // Overload for printing with element location:
  private fun error(message: String, element: Element) {
    processingEnv.messager.printMessage(Kind.ERROR, message, element)
  }

  private fun note(message: String) {
    processingEnv.messager.printMessage(Kind.NOTE, message)
  }

  class IntDefMapping(val mapping: Map<Int, String>, val flag: Boolean) {
    val size
      get() = this.mapping.size

    val entries
      get() = this.mapping.entries
  }

  companion object {
    fun serializeTo(annotationTypeToIntDefMapping: Map<String, IntDefMapping>, writer: Writer) {
      val indent = "  "
      writer.appendLine("{")
      val intDefTypesCount = annotationTypeToIntDefMapping.size
      var currentIntDefTypesCount = 0
      for ((field, intDefMapping) in annotationTypeToIntDefMapping) {
        writer.appendln("""$indent"$field": {""")

        // Start IntDef

        writer.appendln("""$indent$indent"flag": ${intDefMapping.flag},""")

        writer.appendln("""$indent$indent"values": {""")
        intDefMapping.mapping.entries.joinTo(writer, separator = ",\n") { (value, identifier) ->
          """$indent$indent$indent"$value": "$identifier""""
        }
        writer.appendln()
        writer.appendln("$indent$indent}")

        // End IntDef

        writer.append("$indent}") // Keep expected identation.
        if (++currentIntDefTypesCount < intDefTypesCount) {
          writer.appendln(",")
        } else {
          writer.appendln("") // Avoids last comma
        }
      }

      writer.appendln("}")
    }
  }
}
