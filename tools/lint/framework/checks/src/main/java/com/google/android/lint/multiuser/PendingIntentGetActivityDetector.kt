/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.android.lint.multiuser

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

/**
 * Detector for flagging potential multiuser issues in `PendingIntent.getActivity()` calls.
 *
 * This detector checks for calls to `PendingIntent.getActivity()` where the first argument is a
 * `Context` named `mContext`. It reports a warning if such a call is found, suggesting that the
 * user context might not be specified correctly.
 */
class PendingIntentGetActivityDetector : Detector(), SourceCodeScanner {

  companion object {

    val description = """Flags potential multiuser issue in PendingIntent.getActivity() calls."""

    val EXPLANATION =
      """
**Problem:**

Calling `PendingIntent.getActivity` without specifying a user context defaults to user 0.

**Solution:**

Always use the right user context when calling `PendingIntent.getActivity`. You can achieve this by:

* **Using `PendingIntent.getActivityAsUser`:** This API allows you to explicitly specify the user for the activity.

   ```java
   PendingIntent.getActivityAsUser(
       mContext, /*requestCode=*/0, intent,
       PendingIntent.FLAG_IMMUTABLE, /*options=*/null,
       UserHandle.of(mUserId));
   ```

* **Passing the user's context to `PendingIntent.getActivity`:**  Obtain the context associated with the desired user and use it in the API call.

   ```java
   PendingIntent.getActivity(
       userAContext, /*requestCode=*/0, intent,
       PendingIntent.FLAG_IMMUTABLE, /*options=*/null
   );
   ```
    """

    val ISSUE_PENDING_INTENT_GET_ACTIVITY: Issue =
      Issue.create(
        id = "PendingIntent#getActivity",
        briefDescription = description,
        explanation = EXPLANATION,
        category = Category.SECURITY,
        priority = 8,
        severity = Severity.WARNING,
        implementation =
          Implementation(
            PendingIntentGetActivityDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
          ),
      )
  }

  override fun getApplicableMethodNames() = listOf("getActivity")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    // Check if the method call is PendingIntent.getActivity
    if (
      context.evaluator.isMemberInClass(method, "android.app.PendingIntent") &&
        method.name == "getActivity"
    ) {
      // Get the first argument (Context)
      val firstArgument = node.valueArguments.firstOrNull()

      // Check if the first argument is a Context and named "mContext"
      if (
        firstArgument is UReferenceExpression &&
          firstArgument.resolvedName == "mContext" &&
          context.evaluator.getTypeClass(firstArgument.getExpressionType())?.qualifiedName ==
            "android.content.Context"
      ) {
        context.report(
          ISSUE_PENDING_INTENT_GET_ACTIVITY,
          node,
          context.getLocation(node),
          "Using `PendingIntent.getActivity(mContext, ...)` might not be multiuser-aware. " +
            "Consider using `PendingIntent.getActivityAsUser()` or passing the user context explicitly.",
        )
      }
    }
  }
}
