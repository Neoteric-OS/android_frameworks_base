/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.compat;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.pm.PackageManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.compat.IAndroidBuildClassifier;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class OverrideValidatorImplTest {
    private static final String PACKAGE_NAME = "my.package";
    private static final int TARGET_SDK = 10;
    private static final int TARGET_SDK_BEFORE = 9;
    private static final int TARGET_SDK_AFTER = 11;

    @Mock
    private PackageManager mPackageManager;
    @Mock
    CompatChange.ChangeListener mListener1, mListener2;
    @Mock
    IAndroidBuildClassifier mBuildClassifier;
    OverrideValidatorImpl mOverrideValidator;
    CompatConfig mCompatConfig;

    private void setDebuggableBuild() throws Exception {
        when(mBuildClassifier.isDebuggableBuild()).thenReturn(true);
    }

    private void setBetaBuild() throws Exception {
        when(mBuildClassifier.isDebuggableBuild()).thenReturn(false);
        when(mBuildClassifier.isFinalBuild()).thenReturn(false);
    }

    private void setFinalBuild() throws Exception {
        when(mBuildClassifier.isDebuggableBuild()).thenReturn(false);
        when(mBuildClassifier.isFinalBuild()).thenReturn(true);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mCompatConfig = new CompatConfig();
        mOverrideValidator = new OverrideValidatorImpl(mBuildClassifier, mPackageManager,
                                                       mCompatConfig);
    }

    @Test
    public void testAllowAnyOverrideForDebuggableBuild() throws Exception {
        setDebuggableBuild();
        ApplicationBuilder.create()
                .withPackageName(PACKAGE_NAME)
                .withTargetSdk(TARGET_SDK)
                .debuggable()
                .inject(mPackageManager);
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(TARGET_SDK_BEFORE, 1)
                .targetSdkChangeWithId(TARGET_SDK, 2)
                .targetSdkChangeWithId(TARGET_SDK_AFTER, 3)
                .enabledChangeWithId(4)
                .disabledChangeWithId(5)
                .addAll(mCompatConfig);

        assertThat(mOverrideValidator.allowOverride(1, PACKAGE_NAME)).isTrue();
        assertThat(mOverrideValidator.allowOverride(2, PACKAGE_NAME)).isTrue();
        assertThat(mOverrideValidator.allowOverride(3, PACKAGE_NAME)).isTrue();
        assertThat(mOverrideValidator.allowOverride(4, PACKAGE_NAME)).isTrue();
        assertThat(mOverrideValidator.allowOverride(5, PACKAGE_NAME)).isTrue();
    }

    @Test
    public void testAllowAnyTargetSdkForDebuggableAppBetaBuild() throws Exception {
        setBetaBuild();
        ApplicationBuilder.create()
                .withPackageName(PACKAGE_NAME)
                .withTargetSdk(TARGET_SDK)
                .debuggable()
                .inject(mPackageManager);
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(TARGET_SDK_BEFORE, 1)
                .targetSdkChangeWithId(TARGET_SDK, 2)
                .targetSdkChangeWithId(TARGET_SDK_AFTER, 3)
                .addAll(mCompatConfig);

        assertThat(mOverrideValidator.allowOverride(1, PACKAGE_NAME)).isTrue();
        assertThat(mOverrideValidator.allowOverride(2, PACKAGE_NAME)).isTrue();
        assertThat(mOverrideValidator.allowOverride(3, PACKAGE_NAME)).isTrue();
    }

    @Test
    public void testRejectAnyNonTargetSdkForDebuggableAppBetaBuild() throws Exception {
        setBetaBuild();
        ApplicationBuilder.create()
                .withPackageName(PACKAGE_NAME)
                .withTargetSdk(TARGET_SDK)
                .debuggable()
                .inject(mPackageManager);
        ChangeConfigBuilder.create()
                .enabledChangeWithId(1)
                .disabledChangeWithId(2)
                .addAll(mCompatConfig);

        assertThat(mOverrideValidator.allowOverride(1, PACKAGE_NAME)).isFalse();
        assertThat(mOverrideValidator.allowOverride(2, PACKAGE_NAME)).isFalse();
    }

    @Test
    public void testRejectAnyOverrideForNonDebuggableAppBetaBuild() throws Exception {
        setBetaBuild();
        ApplicationBuilder.create()
                .withPackageName(PACKAGE_NAME)
                .withTargetSdk(10)
                .inject(mPackageManager);
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(TARGET_SDK_BEFORE, 1)
                .targetSdkChangeWithId(TARGET_SDK, 2)
                .targetSdkChangeWithId(TARGET_SDK_AFTER, 3)
                .enabledChangeWithId(4)
                .disabledChangeWithId(5)
                .addAll(mCompatConfig);

        assertThat(mOverrideValidator.allowOverride(1, PACKAGE_NAME)).isFalse();
        assertThat(mOverrideValidator.allowOverride(2, PACKAGE_NAME)).isFalse();
        assertThat(mOverrideValidator.allowOverride(3, PACKAGE_NAME)).isFalse();
        assertThat(mOverrideValidator.allowOverride(4, PACKAGE_NAME)).isFalse();
        assertThat(mOverrideValidator.allowOverride(5, PACKAGE_NAME)).isFalse();
    }

    @Test
    public void testAllowTargetSdkOptinForDebuggableAppFinalBuild() throws Exception {
        setFinalBuild();
        ApplicationBuilder.create()
                .withPackageName(PACKAGE_NAME)
                .withTargetSdk(TARGET_SDK)
                .debuggable()
                .inject(mPackageManager);
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(TARGET_SDK_AFTER, 1)
                .addAll(mCompatConfig);

        assertThat(mOverrideValidator.allowOverride(1, PACKAGE_NAME)).isTrue();
    }

    @Test
    public void testRejectTargetSdkOptoutForDebuggableAppFinalBuild() throws Exception {
        setFinalBuild();
        ApplicationBuilder.create()
                .withPackageName(PACKAGE_NAME)
                .withTargetSdk(TARGET_SDK)
                .debuggable()
                .inject(mPackageManager);
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(TARGET_SDK_BEFORE, 1)
                .targetSdkChangeWithId(TARGET_SDK, 2)
                .addAll(mCompatConfig);

        assertThat(mOverrideValidator.allowOverride(1, PACKAGE_NAME)).isFalse();
        assertThat(mOverrideValidator.allowOverride(2, PACKAGE_NAME)).isFalse();
    }

    @Test
    public void testRejectAnyNonTargetSdkForDebuggableAppFinalBuild() throws Exception {
        setFinalBuild();
        ApplicationBuilder.create()
                .withPackageName(PACKAGE_NAME)
                .withTargetSdk(TARGET_SDK)
                .debuggable()
                .inject(mPackageManager);
        ChangeConfigBuilder.create()
                .enabledChangeWithId(1)
                .disabledChangeWithId(2)
                .addAll(mCompatConfig);

        assertThat(mOverrideValidator.allowOverride(1, PACKAGE_NAME)).isFalse();
        assertThat(mOverrideValidator.allowOverride(2, PACKAGE_NAME)).isFalse();
    }

    @Test
    public void testRejectAnyOverrideForNonDebuggableAppFinalBuild() throws Exception {
        setFinalBuild();
        ApplicationBuilder.create()
                .withPackageName(PACKAGE_NAME)
                .withTargetSdk(10)
                .inject(mPackageManager);
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(TARGET_SDK_BEFORE, 1)
                .targetSdkChangeWithId(TARGET_SDK, 2)
                .targetSdkChangeWithId(TARGET_SDK_AFTER, 3)
                .enabledChangeWithId(4)
                .disabledChangeWithId(5)
                .addAll(mCompatConfig);

        assertThat(mOverrideValidator.allowOverride(1, PACKAGE_NAME)).isFalse();
        assertThat(mOverrideValidator.allowOverride(2, PACKAGE_NAME)).isFalse();
        assertThat(mOverrideValidator.allowOverride(3, PACKAGE_NAME)).isFalse();
        assertThat(mOverrideValidator.allowOverride(4, PACKAGE_NAME)).isFalse();
        assertThat(mOverrideValidator.allowOverride(5, PACKAGE_NAME)).isFalse();
    }
}
