/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

class FlagsDetectorTest : LintDetectorTest() {
  override fun getIssues(): List<Issue> = listOf(FlagsDetector.ISSUE)

  override fun getDetector(): Detector {
    return FlagsDetector()
  }

  override fun lint(): TestLintTask {
    return super.lint().allowMissingSdk()
  }

  fun testDocumentationExample() {
    lint()
      .files(
        java(
            """
            package test.api;
            import android.annotation.FlaggedApi;
            import com.example.foobar.Flags;

            @FlaggedApi(Flags.FLAG_FOOBAR)
            public class MyApi {
              public void apiMethod() { }
              public int apiField = 42;
            }
            """
          )
          .indented(),
        java(
            """
            package test.pkg;
            import test.api.MyApi;
            import com.example.foobar.Flags;

            public class Test {
              public void test(MyApi api) {
                if (Flags.foobar()) {
                  api.apiMethod(); // OK
                  int val = api.apiField; // OK
                }
                api.apiMethod(); // ERROR 1
                int val = api.apiField; // ERROR 2
                Object o = MyApi.class; // ERROR 3
              }
            }
            """
          )
          .indented(),
        // Generated
        java(
            """
            package com.example.foobar;

            public class Flags {
                public static final String FLAG_FOOBAR = "foobar";
                public static boolean foobar() { return true; }
            }
            """
          )
          .indented(),
        flaggedApiAnnotationStub,
      )
      .run()
      .expect(
        """
        src/test/pkg/Test.java:11: Error: Method apiMethod() is a flagged API and should be surrounded with an if (Flags.foobar()) check (or annotate surrounding method) [FlaggedApi]
            api.apiMethod(); // ERROR 1
            ~~~~~~~~~~~~~~~
        src/test/pkg/Test.java:12: Error: Field apiField() is a flagged API and should be surrounded with an if (Flags.foobar()) check (or annotate surrounding method) [FlaggedApi]
            int val = api.apiField; // ERROR 2
                          ~~~~~~~~
        src/test/pkg/Test.java:13: Error: Class MyApi is a flagged API and should be surrounded with an if (Flags.foobar()) check (or annotate surrounding method) [FlaggedApi]
            Object o = MyApi.class; // ERROR 3
                       ~~~~~~~~~~~
        3 errors, 0 warnings
        """
      )
  }

  fun testCamelCaseFlagName() {
    lint()
      .files(
        java(
            """
            package test.pkg;
            import test.api.MyApi;
            import com.android.aconfig.test.Flags;

            public class Test {
              public void test(MyApi api) {
                if (Flags.enabledFixedRo()) {
                  api.apiMethod(); // OK
                }
                api.apiMethod(); // ERROR 1
              }
            }
            """
          )
          .indented(),
        java(
            """
            package test.api;
            import android.annotation.FlaggedApi;
            import com.android.aconfig.test.Flags;

            public class MyApi {
              @FlaggedApi(Flags.FLAG_ENABLED_FIXED_RO)
              public void apiMethod() { }
            }
            """
          )
          .indented(),
        // Generated code:
        java(
            """
            package com.android.aconfig.test;
            public final class Flags {
                public static final String FLAG_DISABLED_RO = "com.android.aconfig.test.disabled_ro";
                public static final String FLAG_DISABLED_RW = "com.android.aconfig.test.disabled_rw";
                public static final String FLAG_ENABLED_FIXED_RO = "com.android.aconfig.test.enabled_fixed_ro";
                public static final String FLAG_ENABLED_RO = "com.android.aconfig.test.enabled_ro";
                public static final String FLAG_ENABLED_RW = "com.android.aconfig.test.enabled_rw";

                public static boolean disabledRo() {
                    return FEATURE_FLAGS.disabledRo();
                }

                public static boolean disabledRw() {
                    return FEATURE_FLAGS.disabledRw();
                }

                public static boolean enabledFixedRo() {
                    return FEATURE_FLAGS.enabledFixedRo();
                }

                public static boolean enabledRo() {
                    return FEATURE_FLAGS.enabledRo();
                }
                public static boolean enabledRw() {
                    return FEATURE_FLAGS.enabledRw();
                }
            }
            """
          )
          .indented(),
        flaggedApiAnnotationStub,
      )
      .run()
      .expect(
        """
        src/test/pkg/Test.java:10: Error: Method apiMethod() is a flagged API and should be surrounded with an if (Flags.enabledFixedRo()) check (or annotate surrounding method) [FlaggedApi]
            api.apiMethod(); // ERROR 1
            ~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """
      )
  }

  fun testPartOfApi() {
    // Make sure we don't flag calls to APIs from within other parts of the
    // same API (e.g. also annotated with the same annotation)
    lint()
      .files(
        java(
            """
            package test.api;
            import android.annotation.FlaggedApi;
            import com.example.foobar.Flags;

            @FlaggedApi(Flags.FLAG_FOOBAR)
            public class MyApi {
              public void apiMethod() { }
            }
            """
          )
          .indented(),
        java(
            """
            package test.api;
            import android.annotation.FlaggedApi;
            import com.example.foobar.Flags;

            @FlaggedApi(Flags.FLAG_FOOBAR)
            public class MyApi2 {
              public void apiMethod(MyApi api) {
                  api.apiMethod(); // OK
              }
            }
            """
          )
          .indented(),
        java(
            """
            package test.api;
            import android.annotation.FlaggedApi;
            import com.example.foobar.Flags;

            @FlaggedApi(Flags.FLAG_UNRELATED)
            public class Test {
              public void apiMethod(MyApi api) {
                  api.apiMethod(); // ERROR: Flagged, but different API so still an error
              }
            }
            """
          )
          .indented(),
        // Generated
        java(
            """
            package com.example.foobar;

            public class Flags {
                public static final String FLAG_FOOBAR = "foobar";
                public static final String FLAG_UNRELATED = "unrelated";
                public static boolean foobar() { return true; }
                public static boolean unrelated() { return true; }
            }
            """
          )
          .indented(),
        flaggedApiAnnotationStub,
      )
      .run()
      .expect(
        """
        src/test/api/Test.java:8: Error: Method apiMethod() is a flagged API and should be surrounded with an if (Flags.foobar()) check (or annotate surrounding method) [FlaggedApi]
              api.apiMethod(); // ERROR: Flagged, but different API so still an error
              ~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """
      )
  }

  fun testBasic() {
    // Test case from b/303434307#comment2
    lint()
      .files(
        java(
          """
          package test.pkg;

          public final class Flags {
              public static final String FLAG_MY_FLAG = "myFlag";
              public static boolean myFlag() { return true; }
          }
          """
        ),
        java(
            """
            package test.pkg;

            import android.annotation.FlaggedApi;

            public class JavaTest {
                @FlaggedApi(Flags.FLAG_MY_FLAG)
                class Foo {
                    public void someMethod() { }
                }

                public void testValid1() {
                    if (Flags.myFlag()) {
                        Foo f = new Foo(); // OK 1
                        f.someMethod();    // OK 2
                    }
                }
            }
            """
          )
          .indented(),
        flaggedApiAnnotationStub,
      )
      .run()
      .expectClean()
  }

  fun testInterprocedural() {
    // Test case from b/303434307#comment2
    lint()
      .files(
        java(
          """
          package test.pkg;

          public final class Flags {
              public static final String FLAG_MY_FLAG = "myFlag";
              public static boolean myFlag() { return true; }
          }
          """
        ),
        java(
            """
            package test.pkg;

            import android.annotation.FlaggedApi;

            public class JavaTest {
                static class Foo {
                    @FlaggedApi(Flags.FLAG_MY_FLAG)
                    static void flaggedApi() {
                    }
                }

                void outer() {
                    if (Flags.myFlag()) {
                        inner();
                    }
                }

                void inner() {
                    // In theory valid because FLAG_MY_FLAG was checked earlier in the call-chain,
                    // but we don't do inter procedural analysis
                    Foo.flaggedApi(); // ERROR
                }
            }
            """
          )
          .indented(),
        flaggedApiAnnotationStub,
      )
      .run()
      .expect(
        """
        src/test/pkg/JavaTest.java:21: Error: Method flaggedApi() is a flagged API and should be surrounded with an if (Flags.myFlag()) check (or annotate surrounding method) [FlaggedApi]
                Foo.flaggedApi(); // ERROR
                ~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """
      )
  }

  fun testApiGating() {
    // Test case from b/303434307#comment2
    lint()
      .files(
        java(
          """
          package test.pkg;

          public final class Flags {
              public static final String FLAG_MY_FLAG = "myFlag";
              public static boolean myFlag() { return true; }
          }
          """
        ),
        java(
            """
            package test.pkg;

            import android.annotation.FlaggedApi;

            public class JavaTest {
                interface MyInterface {
                    void bar();
                }

                static class OldImpl implements MyInterface {
                    @Override
                    public void bar() {
                    }
                }

                @FlaggedApi(Flags.FLAG_MY_FLAG)
                static class NewImpl implements MyInterface {
                    @Override
                    public void bar() {
                    }
                 }

                 void test(MyInterface f) {
                     MyInterface obj = null;
                     if (Flags.myFlag()) {
                         obj = new NewImpl();
                     } else {
                         obj = new OldImpl();
                     }
                     f.bar();
                 }
            }
            """
          )
          .indented(),
        flaggedApiAnnotationStub,
      )
      .run()
      .expectClean()
  }

  fun testFinalFields() {
    // Test case from b/303434307#comment2
    lint()
      .files(
        java(
          """
          package test.pkg;

          public final class Flags {
              public static final String FLAG_MY_FLAG = "myFlag";
              public static boolean myFlag() { return true; }
          }
          """
        ),
        java(
            """
            package test.pkg;

            import android.annotation.FlaggedApi;

            public class JavaTest {
                static class Bar {
                    @FlaggedApi(Flags.FLAG_MY_FLAG)
                    public void bar() { }
                }
                static class Foo {
                    private static final boolean useNewStuff = Flags.myFlag();
                    private final Bar mBar = new Bar();

                    void someMethod() {
                        if (useNewStuff) {
                            // OK because flags can't change value without a reboot, though this might change in
                            // the future and in that case caching the flag value would be an error. We can restart
                            // apps due to a server push of new flag values but restarting the framework would be
                            // too disruptive
                            mBar.bar(); // OK
                        }
                    }
                }
            }
            """
          )
          .indented(),
        flaggedApiAnnotationStub,
      )
      .run()
      .expectClean()
  }

  fun testInverseLogic() {
    lint()
      .files(
        java(
          """
          package test.pkg;

          public final class Flags {
              public static final String FLAG_MY_FLAG = "myFlag";
              public static boolean myFlag() { return true; }
          }
          """
        ),
        java(
            """
            package test.pkg;

            import android.annotation.FlaggedApi;

            public class JavaTest {
                @FlaggedApi(Flags.FLAG_MY_FLAG)
                class Foo {
                    public void someMethod() { }
                }

                public void testInverse() {
                    if (!Flags.myFlag()) {
                        // ...
                    } else {
                        Foo f = new Foo(); // OK 1
                        f.someMethod();    // OK 2
                    }
                }
            }
            """
          )
          .indented(),
        flaggedApiAnnotationStub,
      )
      .run()
      .expectClean()
  }

  fun testAnded() {
    lint()
      .files(
        java(
          """
          package test.pkg;

          public final class Flags {
              public static final String FLAG_MY_FLAG = "myFlag";
              public static boolean myFlag() { return true; }
          }
          """
        ),
        java(
            """
            package test.pkg;

            import android.annotation.FlaggedApi;

            public class JavaTest {
                @FlaggedApi(Flags.FLAG_MY_FLAG)
                class Foo {
                    public void someMethod() { }
                }

                public void testValid1(boolean something) {
                    if (true && something && Flags.myFlag()) {
                        Foo f = new Foo(); // OK 1
                        f.someMethod();    // OK 2
                    }
                }
            }
            """
          )
          .indented(),
        flaggedApiAnnotationStub,
      )
      .run()
      .expectClean()
  }

  fun testEarlyReturns() {
    lint()
      .files(
        java(
          """
          package test.pkg;

          public final class Flags {
              public static final String FLAG_MY_FLAG = "myFlag";
              public static boolean myFlag() { return true; }
          }
          """
        ),
        java(
            """
            package test.pkg;

            import android.annotation.FlaggedApi;

            public class JavaTest {
                @FlaggedApi(Flags.FLAG_MY_FLAG)
                class Foo {
                    public void someMethod() { }
                }

                public void testSimpleEarlyReturn() {
                    if (!Flags.myFlag()) {
                        return;
                    }
                    Foo f = new Foo(); // OK 1
                    f.someMethod();    // OK 2
                }

                public void testEarlyReturn() {
                    int log;
                    {
                        if (!Flags.myFlag()) {
                            return;
                        }
                    }
                    // These are fine -- but we don't do more complex
                    // flow analysis here as in the SDK_INT version checker
                    // here, we only check very simple scenarios
                    Foo f = new Foo(); // ERROR 1
                    f.someMethod();    // ERROR 2
                }
            }
            """
          )
          .indented(),
        flaggedApiAnnotationStub,
      )
      .run()
      .expect(
        """
        src/test/pkg/JavaTest.java:29: Error: Method null() is a flagged API and should be surrounded with an if (Flags.myFlag()) check (or annotate surrounding method) [FlaggedApi]
                Foo f = new Foo(); // ERROR 1
                        ~~~~~~~~~
        src/test/pkg/JavaTest.java:30: Error: Method someMethod() is a flagged API and should be surrounded with an if (Flags.myFlag()) check (or annotate surrounding method) [FlaggedApi]
                f.someMethod();    // ERROR 2
                ~~~~~~~~~~~~~~
        2 errors, 0 warnings
        """
      )
  }

  fun testIgnoringStringFlags() {
    lint()
      .files(
        java(
            """
            package test.pkg;

            import android.annotation.FlaggedApi;

            public class JavaTest {
                @FlaggedApi("flag.package.flag.name")
                class Foo {
                    public void someMethod() { }
                }

                public void testValid1(boolean something) {
                    f.someMethod();    // OK: String flags are ignored for now
                }
            }
            """
          )
          .indented(),
        flaggedApiAnnotationStub,
      )
      .run()
      .expectClean()
  }
}

private val flaggedApiAnnotationStub: TestFile =
  java(
      """
      package android.annotation; // HIDE-FROM-DOCUMENTATION

      import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
      import static java.lang.annotation.ElementType.CONSTRUCTOR;
      import static java.lang.annotation.ElementType.FIELD;
      import static java.lang.annotation.ElementType.METHOD;
      import static java.lang.annotation.ElementType.TYPE;

      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;

      @Target({TYPE, METHOD, CONSTRUCTOR, FIELD, ANNOTATION_TYPE})
      @Retention(RetentionPolicy.SOURCE)
      public @interface FlaggedApi {
          String value();
      }
      """
    )
    .indented()
