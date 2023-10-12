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

package com.google.android.lint

import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.getMethodName
import com.android.utils.SdkUtils.constantNameToCamelCase
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USwitchClauseExpressionWithBody
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UYieldExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.tryResolve

/**
 * Enforced flag checking in the Android platform; see go/android-flagged-apis.
 *
 * TODO: Quickfixes?
 */
class FlagsDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    private val IMPLEMENTATION = Implementation(FlagsDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Accessing flagged api without check. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "FlaggedApi",
        explanation =
          """
          This lint check looks for accesses of APIs marked with `@FlaggedApi(X)` without \
          a guarding `if (Flags.X)` check. See go/android-flagged-apis.
          """,
        briefDescription = "FlaggedApi access without check",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION
      )

    private const val FLAGGED_API_ANNOTATION = "android.annotation.FlaggedApi"
  }

  override fun applicableAnnotations(): List<String> {
    return listOf(FLAGGED_API_ANNOTATION)
  }

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
    return when (type) {
      AnnotationUsageType.METHOD_CALL,
      AnnotationUsageType.METHOD_REFERENCE,
      AnnotationUsageType.FIELD_REFERENCE,
      AnnotationUsageType.CLASS_REFERENCE,
      AnnotationUsageType.ANNOTATION_REFERENCE,
      AnnotationUsageType.EXTENDS,
      AnnotationUsageType.DEFINITION -> true
      else -> false
    }
  }

  override fun inheritAnnotation(annotation: String): Boolean {
    return false
  }

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo
  ) {
    val flag = getFlaggedApi(annotationInfo.annotation) ?: return
    if (alreadyAnnotated(context.evaluator, element, flag)) {
      return
    }

    val flagClass = flag.containingClass ?: return
    val flagName = flag.name
    val flagPresent = constantNameToCamelCase(flagName.removePrefix("FLAG_"))

    if (isFlagChecked(element, flagClass, flagPresent)) {
      return
    }

    val referenced = element.tryResolve()
    val description =
      if (referenced is PsiMethod) {
        "Method `${referenced.name}()`"
      } else if (element is UCallExpression) {
        "Method `${getMethodName(element)}()`"
      } else if (referenced is PsiField) {
        "Field `${referenced.name}()`"
      } else if (referenced is PsiParameter) {
        "Parameter `${referenced.name}()`"
      } else if (referenced is PsiClass) {
        "Parameter `${referenced.name}()`"
      } else if (referenced is PsiVariable) {
        "Variable `${referenced.name}()`"
      } else if (referenced is PsiNamedElement) {
        "Reference `${referenced.name}()`"
      } else if (element is UClassLiteralExpression) {
        "Class `${element.expression?.sourcePsi?.text}`"
      } else {
        "This"
      }
    val message =
      "$description is a flagged API and should be surrounded with an `if (${flagClass.name}.$flagPresent())` check (or annotate surrounding method)"
    context.report(ISSUE, element, context.getLocation(element), message)
  }

  /** Given a `@FlaggedApi` annotation, returns the resolved field. */
  private fun getFlaggedApi(annotation: UAnnotation): PsiField? {
    return annotation.attributeValues.firstOrNull()?.expression?.tryResolve() as? PsiField
  }

  /**
   * Is the given [element] within a code block already annotated with the same flagged api as
   * [flag].
   */
  private fun alreadyAnnotated(
    evaluator: JavaEvaluator,
    element: UElement?,
    flag: PsiField
  ): Boolean {
    var current = element
    while (current != null) {
      if (current is UAnnotated) {
        //noinspection AndroidLintExternalAnnotations
        for (annotation in current.uAnnotations) {
          val api = getFlaggedApi(annotation) ?: continue
          if (api.isEquivalentTo(flag)) {
            return true
          }
        }
      }
      if (current is UFile) {
        // Also consult any package annotations
        val pkg = evaluator.getPackage(current.javaPsi ?: current.sourcePsi)
        if (pkg != null) {
          for (psiAnnotation in pkg.annotations) {
            val annotation =
              UastFacade.convertElement(psiAnnotation, null) as? UAnnotation ?: continue
            val api = getFlaggedApi(annotation) ?: continue
            if (api.isEquivalentTo(flag)) {
              return true
            }
          }
        }

        break
      }
      current = current.uastParent
    }

    return false
  }

  private fun isFlagChecked(element: UElement, flagClass: PsiClass, flagPresent: String): Boolean {
    var curr = element.uastParent ?: return false

    var prev = element
    while (curr !is UFile) {
      if (curr is UIfExpression) {
        val condition = curr.condition
        if (prev !== condition) {
          val fromThen = prev == curr.thenExpression
          if (fromThen) {
            if (isFlagCheck(condition, flagClass, flagPresent)) {
              return true
            }
          } else {
            // Handle "if (!Flags.X) else <CALL>"
            val op = condition.skipParenthesizedExprDown()
            if (
              op is UUnaryExpression &&
                op.operator == UastPrefixOperator.LOGICAL_NOT &&
                isFlagCheck(op.operand, flagClass, flagPresent)
            ) {
              return true
            }
          }
        }
      } else if (curr is UMethod) {
        // See if there's an early return. We *only* handle a very simple canonical format here;
        // must be first statement in method.
        val body = curr.uastBody
        if (body is UBlockExpression && body.expressions.size > 1) {
          val first = body.expressions[0]
          if (first is UIfExpression) {
            val condition = first.condition.skipParenthesizedExprDown()
            if (
              condition is UUnaryExpression &&
                condition.operator == UastPrefixOperator.LOGICAL_NOT &&
                isFlagCheck(condition.operand, flagClass, flagPresent)
            ) {
              // It's a flag check; make sure we just return
              val then = first.thenExpression?.skipParenthesizedExprDown()
              if (then != null && isUnconditionalReturn(then)) {
                return true
              }
            }
          }
        }
      }

      prev = curr
      curr = curr.uastParent ?: break
    }

    return false
  }

  private fun isFlagCheck(element: UElement, flagClass: PsiClass, flagMethodName: String): Boolean {
    if (element is UUnaryExpression && element.operator == UastPrefixOperator.LOGICAL_NOT) {
      return !isFlagCheck(element.operand, flagClass, flagMethodName)
    } else if (element is UReferenceExpression || element is UCallExpression) {
      val resolved = element.tryResolve()
      if (resolved is PsiMethod) {
        if (resolved.name == flagMethodName) {
          val cls = resolved.containingClass
          if (flagClass.isEquivalentTo(cls)) {
            return true
          }
        }
      } else if (resolved is PsiField) {
        // TODO: check final?
        val initializer = UastFacade.getInitializerBody(resolved)
        if (initializer != null) {
          return isFlagCheck(initializer, flagClass, flagMethodName)
        }
      }
    } else if (element is UParenthesizedExpression) {
      return isFlagCheck(element.expression, flagClass, flagMethodName)
    } else if (element is UPolyadicExpression) {
      if (element.operator == UastBinaryOperator.LOGICAL_AND) {
        for (operand in element.operands) {
          if (isFlagCheck(operand, flagClass, flagMethodName)) {
            return true
          }
        }
      }
    }
    return false
  }

  // Copied from VersionChecks; let's move this to a utility function
  private fun isUnconditionalReturn(statement: UExpression): Boolean {
    @Suppress("UnstableApiUsage") // UYieldExpression not yet stable
    if (statement is UBlockExpression) {
      statement.expressions.lastOrNull()?.let {
        return isUnconditionalReturn(it)
      }
    } else if (statement is UExpressionList) {
      statement.expressions.lastOrNull()?.let {
        return isUnconditionalReturn(it)
      }
    } else if (statement is UYieldExpression) {
      // (Kotlin when statements will sometimes be represented using yields in the UAST
      // representation)
      val yieldExpression = statement.expression
      if (yieldExpression != null) {
        return isUnconditionalReturn(yieldExpression)
      }
    } else if (statement is UParenthesizedExpression) {
      return isUnconditionalReturn(statement.expression)
    } else if (statement is UIfExpression) {
      val thenExpression = statement.thenExpression
      val elseExpression = statement.elseExpression
      if (thenExpression != null && elseExpression != null) {
        return isUnconditionalReturn(thenExpression) && isUnconditionalReturn(elseExpression)
      }
      return false
    } else if (statement is USwitchExpression) {
      for (case in statement.body.expressions) {
        if (case is USwitchClauseExpressionWithBody) {
          if (!isUnconditionalReturn(case.body)) {
            return false
          }
        }
      }
      return true
    }

    if (statement is UReturnExpression || statement is UThrowExpression) {
      return true
    } else if (statement is UCallExpression) {
      val methodName = getMethodName(statement)
      // Look for Kotlin runtime library methods that unconditionally exit
      if ("error" == methodName || "TODO" == methodName) {
        return true
      }
    }

    return false
  }
}
