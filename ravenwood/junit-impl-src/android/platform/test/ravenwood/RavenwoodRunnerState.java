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
package android.platform.test.ravenwood;

import static android.platform.test.ravenwood.RavenwoodSystemServer.ANDROID_PACKAGE_NAME;

import static com.android.ravenwood.common.RavenwoodCommonUtils.RAVENWOOD_EMPTY_RESOURCES_APK;
import static com.android.ravenwood.common.RavenwoodCommonUtils.RAVENWOOD_INST_RESOURCE_APK;
import static com.android.ravenwood.common.RavenwoodCommonUtils.RAVENWOOD_RESOURCE_APK;
import static com.android.ravenwood.common.RavenwoodCommonUtils.ensureIsPublicMember;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import android.annotation.Nullable;
import android.app.Instrumentation;
import android.app.ResourcesManager;
import android.app.UiAutomation;
import android.content.res.Resources;
import android.os.HandlerThread;
import android.util.Log;
import android.view.DisplayAdjustments;

import com.android.hoststubgen.hosthelper.HostTestUtils;
import com.android.ravenwood.common.RavenwoodRuntimeException;
import com.android.ravenwood.common.SneakyThrow;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Used to store various states associated with the current test runner that's inly needed
 * in junit-impl.
 *
 * We don't want to put it in junit-src to avoid having to recompile all the downstream
 * dependencies after changing this class.
 *
 * All members must be called from the runner's main thread.
 */
public final class RavenwoodRunnerState {
    private static final String TAG = "RavenwoodRunnerState";
    private static final String MAIN_THREAD_NAME = "RavenwoodMain";

    private final RavenwoodAwareTestRunner mRunner;
    /** Map from path -> resources. */
    private final HashMap<File, Resources> mCachedResources = new HashMap<>();

    /**
     * Ctor.
     */
    public RavenwoodRunnerState(RavenwoodAwareTestRunner runner) {
        mRunner = runner;
    }

    /**
     * The RavenwoodConfig used to configure the current Ravenwood environment.
     * This can either come from mConfig or mRule.
     */
    private RavenwoodConfig mCurrentConfig;
    /**
     * The RavenwoodConfig declared in the test class
     */
    private RavenwoodConfig mConfig;
    /**
     * The RavenwoodRule currently in effect, declared in the test class
     */
    private RavenwoodRule mRule;
    private boolean mHasRavenwoodRule;
    private Description mMethodDescription;

    Instrumentation mInstrumentation;
    RavenwoodContext mInstContext;
    RavenwoodContext mTargetContext;
    RavenwoodContext mSystemServerContext;

    public RavenwoodConfig getConfig() {
        return mCurrentConfig;
    }

    private void init() {
        try {
            initInner();
        } catch (Exception th) {
            Log.e(TAG, "init() failed", th);
            SneakyThrow.sneakyThrow(th);
        }
    }

    private void initInner() throws IOException {
        final var config = Objects.requireNonNull(mCurrentConfig);

        final boolean isSelfInstrumenting =
                Objects.equals(config.mTestPackageName, config.mTargetPackageName);

        // This will load the resources from the apk set to `resource_apk` in the build file.
        // This is supposed to be the "target app"'s resources.
        final Supplier<Resources> targetResourcesLoader = () -> {
            var file = new File(RAVENWOOD_RESOURCE_APK);
            return loadResources(file.exists() ? file : null);
        };

        // Set up test context's (== instrumentation context's) resources.
        // If the target package name == test package name, then we use the main resources.
        final Supplier<Resources> instResourcesLoader;
        if (isSelfInstrumenting) {
            instResourcesLoader = targetResourcesLoader;
        } else {
            instResourcesLoader = () -> {
                var file = new File(RAVENWOOD_INST_RESOURCE_APK);
                return loadResources(file.exists() ? file : null);
            };
        }

        final var main = new HandlerThread(MAIN_THREAD_NAME);
        main.start();
        Objects.requireNonNull(main.getLooper());

        var instContext = new RavenwoodContext(
                config.mTestPackageName, main, instResourcesLoader);
        var targetContext = new RavenwoodContext(
                config.mTargetPackageName, main, targetResourcesLoader);

        // Set up app context.
        var appContext = new RavenwoodContext(
                config.mTargetPackageName, main, targetResourcesLoader);
        appContext.setApplicationContext(appContext);
        if (isSelfInstrumenting) {
            instContext.setApplicationContext(appContext);
            targetContext.setApplicationContext(appContext);
        } else {
            // When instrumenting into another APK, the test context doesn't have an app context.
            targetContext.setApplicationContext(appContext);
        }
        mInstContext = instContext;
        mTargetContext = targetContext;

        final Supplier<Resources> systemResourcesLoader = () -> loadResources(null);
        mSystemServerContext =
                new RavenwoodContext(ANDROID_PACKAGE_NAME, main, systemResourcesLoader);

        // Prepare other fields.
        mInstrumentation = new Instrumentation();
        mInstrumentation.basicInit(instContext, targetContext, createMockUiAutomation());
    }

    /**
     * Load {@link Resources} from an APK, with cache.
     */
    private Resources loadResources(@Nullable File apkPath) {
        var cached = mCachedResources.get(apkPath);
        if (cached != null) {
            return cached;
        }

        var fileToLoad = apkPath != null ? apkPath : new File(RAVENWOOD_EMPTY_RESOURCES_APK);

        assertTrue("File " + fileToLoad + " doesn't exist.", fileToLoad.isFile());

        final String path = fileToLoad.getAbsolutePath();
        final var emptyPaths = new String[0];

        ResourcesManager.getInstance().initializeApplicationPaths(path, emptyPaths);

        final var ret = ResourcesManager.getInstance().getResources(null, path,
                emptyPaths, emptyPaths, emptyPaths,
                emptyPaths, null, null,
                new DisplayAdjustments().getCompatibilityInfo(),
                RavenwoodRuntimeEnvironmentController.class.getClassLoader(), null);

        assertNotNull(ret);

        mCachedResources.put(apkPath, ret);
        return ret;
    }

    // TODO: use the real UiAutomation class instead of a mock
    private static UiAutomation createMockUiAutomation() {
        final Set[] adoptedPermission = { Collections.emptySet() };
        var mock = mock(UiAutomation.class, inv -> {
            HostTestUtils.onThrowMethodCalled();
            return null;
        });
        doAnswer(inv -> {
            adoptedPermission[0] = UiAutomation.ALL_PERMISSIONS;
            return null;
        }).when(mock).adoptShellPermissionIdentity();
        doAnswer(inv -> {
            if (inv.getArgument(0) == null) {
                adoptedPermission[0] = UiAutomation.ALL_PERMISSIONS;
            } else {
                adoptedPermission[0] = Set.of(inv.getArguments());
            }
            return null;
        }).when(mock).adoptShellPermissionIdentity(any());
        doAnswer(inv -> {
            adoptedPermission[0] = Collections.emptySet();
            return null;
        }).when(mock).dropShellPermissionIdentity();
        doAnswer(inv -> adoptedPermission[0]).when(mock).getAdoptedShellPermissions();
        return mock;
    }

    private void reset() {
        mInstContext.getMainLooper().quit();
        mInstContext.cleanUp();
        mTargetContext.cleanUp();
        mSystemServerContext.cleanUp();
        mInstContext = null;
        mTargetContext = null;
        mSystemServerContext = null;
        mInstrumentation = null;
    }

    public void enterTestRunner() {
        Log.i(TAG, "enterTestRunner: " + mRunner);

        mHasRavenwoodRule = hasRavenwoodRule(mRunner.mTestJavaClass);
        mConfig = extractConfiguration(mRunner.mTestJavaClass);

        if (mConfig != null) {
            if (mHasRavenwoodRule) {
                fail("RavenwoodConfig and RavenwoodRule cannot be used in the same class."
                        + " Suggest migrating to RavenwoodConfig.");
            }
            mCurrentConfig = mConfig;
        } else if (!mHasRavenwoodRule) {
            // If no RavenwoodConfig and no RavenwoodRule, use a default config
            mCurrentConfig = new RavenwoodConfig.Builder().build();
        }

        if (mCurrentConfig != null) {
            init();
            RavenwoodRuntimeEnvironmentController.init(this);
        }
    }

    public void enterTestClass() {
        Log.i(TAG, "enterTestClass: " + mRunner.mTestJavaClass.getName());

        if (mCurrentConfig != null) {
            RavenwoodRuntimeEnvironmentController.init(this);
        }
    }

    public void exitTestClass() {
        Log.i(TAG, "exitTestClass: " + mRunner.mTestJavaClass.getName());
        try {
            if (mCurrentConfig != null) {
                RavenwoodRuntimeEnvironmentController.reset();
                reset();
            }
        } finally {
            mConfig = null;
            mRule = null;
        }
    }

    public void enterTestMethod(Description description) {
        mMethodDescription = description;
    }

    public void exitTestMethod() {
        mMethodDescription = null;
        RavenwoodRuntimeEnvironmentController.reinit();
    }

    public void enterRavenwoodRule(RavenwoodRule rule) {
        if (!mHasRavenwoodRule) {
            fail("If you have a RavenwoodRule in your test, make sure the field type is"
                    + " RavenwoodRule so Ravenwood can detect it.");
        }
        if (mRule != null) {
            fail("Multiple nesting RavenwoodRule's are detected in the same class,"
                    + " which is not supported.");
        }
        mRule = rule;
        if (mCurrentConfig == null) {
            mCurrentConfig = rule.getConfiguration();
            init();
        }
        RavenwoodRuntimeEnvironmentController.init(this);
    }

    public void exitRavenwoodRule(RavenwoodRule rule) {
        if (mRule != rule) {
            fail("RavenwoodRule did not take effect.");
        }
        mRule = null;
    }

    /**
     * @return a configuration from a test class, if any.
     */
    @Nullable
    private static RavenwoodConfig extractConfiguration(Class<?> testClass) {
        var field = findConfigurationField(testClass);
        if (field == null) {
            return null;
        }

        try {
            return (RavenwoodConfig) field.get(null);
        } catch (IllegalAccessException e) {
            throw new RavenwoodRuntimeException("Failed to fetch from the configuration field", e);
        }
    }

    /**
     * @return true if the current target class (or its super classes) has any @Rule / @ClassRule
     * fields of type RavenwoodRule.
     *
     * Note, this check won't detect cases where a Rule is of type
     * {@link TestRule} and still be a {@link RavenwoodRule}. But that'll be detected at runtime
     * as a failure, in {@link #enterRavenwoodRule}.
     */
    private static boolean hasRavenwoodRule(Class<?> testClass) {
        for (var field : testClass.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Rule.class)
                    && !field.isAnnotationPresent(ClassRule.class)) {
                continue;
            }
            if (field.getType().equals(RavenwoodRule.class)) {
                return true;
            }
        }
        // JUnit supports rules as methods, so we need to check them too.
        for (var method : testClass.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Rule.class)
                    && !method.isAnnotationPresent(ClassRule.class)) {
                continue;
            }
            if (method.getReturnType().equals(RavenwoodRule.class)) {
                return true;
            }
        }
        // Look into the super class.
        if (!testClass.getSuperclass().equals(Object.class)) {
            return hasRavenwoodRule(testClass.getSuperclass());
        }
        return false;
    }

    /**
     * Find and return a field with @RavenwoodConfig.Config, which must be of type
     * RavenwoodConfig.
     */
    @Nullable
    private static Field findConfigurationField(Class<?> testClass) {
        Field foundField = null;

        for (var field : testClass.getDeclaredFields()) {
            final var hasAnot = field.isAnnotationPresent(RavenwoodConfig.Config.class);
            final var isType = field.getType().equals(RavenwoodConfig.class);

            if (hasAnot) {
                if (isType) {
                    // Good, use this field.
                    if (foundField != null) {
                        fail(String.format(
                                "Class %s has multiple fields with %s",
                                testClass.getCanonicalName(),
                                "@RavenwoodConfig.Config"));
                    }
                    // Make sure it's static public
                    ensureIsPublicMember(field, true);

                    foundField = field;
                } else {
                    fail(String.format(
                            "Field %s.%s has %s but type is not %s",
                            testClass.getCanonicalName(),
                            field.getName(),
                            "@RavenwoodConfig.Config",
                            "RavenwoodConfig"));
                    return null; // unreachable
                }
            } else {
                if (isType) {
                    fail(String.format(
                            "Field %s.%s does not have %s but type is %s",
                            testClass.getCanonicalName(),
                            field.getName(),
                            "@RavenwoodConfig.Config",
                            "RavenwoodConfig"));
                    return null; // unreachable
                } else {
                    // Unrelated field, ignore.
                    continue;
                }
            }
        }
        if (foundField != null) {
            return foundField;
        }
        if (!testClass.getSuperclass().equals(Object.class)) {
            return findConfigurationField(testClass.getSuperclass());
        }
        return null;
    }
}
