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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class PlatformCompatTest {

    CompatConfig mCompatConfig;
    PlatformCompat mPlatformCompat;
    @Mock
    Context mContext;
    @Mock
    PackageManager mPackageManager;
    @Mock
    PlatformCompat.AndroidBuildClassifier mBuildClassifier;

    private static final String APP_PACKAGE_NAME = "foo.bar";
    private static final int TARGET_SDK = 10;
    private static final int TARGET_SDK_BEFORE = 9;
    private static final int TARGET_SDK_AFTER = 11;

    static class MockApplicationBuilder {
        boolean mIsDebuggable;
        int mTargetSdk;
        String mPackageName;

        MockApplicationBuilder() {
            mIsDebuggable = false;
            mTargetSdk = 0;
            mPackageName = APP_PACKAGE_NAME;
        }

        static MockApplicationBuilder create() {
            return new MockApplicationBuilder();
        }

        MockApplicationBuilder withTargetSdk(int targetSdk) {
            mTargetSdk = targetSdk;
            return this;
        }

        MockApplicationBuilder debuggable() {
            mIsDebuggable = true;
            return this;
        }

        MockApplicationBuilder withPackageName(String packageName) {
            mPackageName = packageName;
            return this;
        }

        void mock(PackageManager pm) throws NameNotFoundException {
            final ApplicationInfo applicationInfo = new ApplicationInfo();
            if (mIsDebuggable) {
                applicationInfo.flags |= ApplicationInfo.FLAG_DEBUGGABLE;
            }
            applicationInfo.targetSdkVersion = mTargetSdk;
            when(pm.getApplicationInfoAsUser(eq(mPackageName), anyInt(), anyInt()))
                    .thenReturn(applicationInfo);
        }
    }

    static class ChangeConfigBuilder {
        ArrayList<CompatChange> mChanges;

        ChangeConfigBuilder() {
            mChanges = new ArrayList<>();
        }

        static ChangeConfigBuilder create() {
            return new ChangeConfigBuilder();
        }

        ChangeConfigBuilder targetSdkChangeWithId(int sdk, int id) {
            mChanges.add(new CompatChange(id, null, sdk, false));
            return this;
        }

        ChangeConfigBuilder enabledChangeWithId(int id) {
            mChanges.add(new CompatChange(id, null, -1, false));
            return this;
        }

        ChangeConfigBuilder disabledChangeWithId(int id) {
            mChanges.add(new CompatChange(id, null, -1, true));
            return this;
        }

        void inject(CompatConfig config) {
            for (CompatChange change : mChanges) {
                config.addChange(change);
            }
        }
    }

    private void setDebuggableBuild() {
        when(mBuildClassifier.isDebuggableBuild()).thenReturn(true);
    }

    private void setBetaBuild() {
        when(mBuildClassifier.isDebuggableBuild()).thenReturn(false);
        when(mBuildClassifier.isFinalBuild()).thenReturn(false);
    }

    private void setFinalBuild() {
        when(mBuildClassifier.isDebuggableBuild()).thenReturn(false);
        when(mBuildClassifier.isFinalBuild()).thenReturn(true);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mCompatConfig = new CompatConfig();
        mPlatformCompat = new PlatformCompat(mContext, mCompatConfig, mBuildClassifier);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
    }

    @Test
    public void testAllowAnyOverrideForDebuggableBuild() throws Exception {
        setDebuggableBuild();
        MockApplicationBuilder.create()
                .debuggable()
                .withTargetSdk(TARGET_SDK)
                .mock(mPackageManager);
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(TARGET_SDK_BEFORE, 1)
                .targetSdkChangeWithId(TARGET_SDK, 2)
                .targetSdkChangeWithId(TARGET_SDK_AFTER, 3)
                .enabledChangeWithId(4)
                .disabledChangeWithId(5)
                .inject(mCompatConfig);

        assertThat(mPlatformCompat.allowOverride(1, APP_PACKAGE_NAME)).isTrue();
        assertThat(mPlatformCompat.allowOverride(2, APP_PACKAGE_NAME)).isTrue();
        assertThat(mPlatformCompat.allowOverride(3, APP_PACKAGE_NAME)).isTrue();
        assertThat(mPlatformCompat.allowOverride(4, APP_PACKAGE_NAME)).isTrue();
        assertThat(mPlatformCompat.allowOverride(5, APP_PACKAGE_NAME)).isTrue();
        verify(mBuildClassifier, never()).isFinalBuild(); // Should not check if it's a final build
    }

    @Test
    public void testAllowAnyTargetSdkForDebuggableAppBetaBuild() throws Exception {
        setBetaBuild();
        MockApplicationBuilder.create()
                .debuggable()
                .withTargetSdk(TARGET_SDK)
                .mock(mPackageManager);
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(TARGET_SDK_BEFORE, 1)
                .targetSdkChangeWithId(TARGET_SDK, 2)
                .targetSdkChangeWithId(TARGET_SDK_AFTER, 3)
                .inject(mCompatConfig);

        assertThat(mPlatformCompat.allowOverride(1, APP_PACKAGE_NAME)).isTrue();
        assertThat(mPlatformCompat.allowOverride(2, APP_PACKAGE_NAME)).isTrue();
        assertThat(mPlatformCompat.allowOverride(3, APP_PACKAGE_NAME)).isTrue();
    }

    @Test
    public void testRejectAnyNonTargetSdkForDebuggableAppBetaBuild() throws Exception {
        setBetaBuild();
        MockApplicationBuilder.create()
                .debuggable()
                .withTargetSdk(TARGET_SDK)
                .mock(mPackageManager);
        ChangeConfigBuilder.create()
                .enabledChangeWithId(1)
                .disabledChangeWithId(2)
                .inject(mCompatConfig);

        assertThat(mPlatformCompat.allowOverride(1, APP_PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(2, APP_PACKAGE_NAME)).isFalse();
    }

    @Test
    public void testRejectAnyOverrideForNonDebuggableAppBetaBuild() throws Exception {
        setBetaBuild();
        MockApplicationBuilder.create()
                .withTargetSdk(10)
                .mock(mPackageManager);
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(TARGET_SDK_BEFORE, 1)
                .targetSdkChangeWithId(TARGET_SDK, 2)
                .targetSdkChangeWithId(TARGET_SDK_AFTER, 3)
                .enabledChangeWithId(4)
                .disabledChangeWithId(5)
                .inject(mCompatConfig);

        assertThat(mPlatformCompat.allowOverride(1, APP_PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(2, APP_PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(3, APP_PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(4, APP_PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(5, APP_PACKAGE_NAME)).isFalse();
    }

    @Test
    public void testAllowTargetSdkOptinForDebuggableAppFinalBuild() throws Exception {
        setFinalBuild();
        MockApplicationBuilder.create()
                .debuggable()
                .withTargetSdk(TARGET_SDK)
                .mock(mPackageManager);
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(TARGET_SDK_AFTER, 1)
                .inject(mCompatConfig);

        assertThat(mPlatformCompat.allowOverride(1, APP_PACKAGE_NAME)).isTrue();
    }

    @Test
    public void testRejectTargetSdkOptoutForDebuggableAppFinalBuild() throws Exception {
        setFinalBuild();
        MockApplicationBuilder.create()
                .debuggable()
                .withTargetSdk(TARGET_SDK)
                .mock(mPackageManager);
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(TARGET_SDK_BEFORE, 1)
                .targetSdkChangeWithId(TARGET_SDK, 2)
                .inject(mCompatConfig);

        assertThat(mPlatformCompat.allowOverride(1, APP_PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(2, APP_PACKAGE_NAME)).isFalse();
    }

    @Test
    public void testRejectAnyNonTargetSdkForDebuggableAppFinalBuild() throws Exception {
        setFinalBuild();
        MockApplicationBuilder.create()
                .debuggable()
                .withTargetSdk(TARGET_SDK)
                .mock(mPackageManager);
        ChangeConfigBuilder.create()
                .enabledChangeWithId(1)
                .disabledChangeWithId(2)
                .inject(mCompatConfig);

        assertThat(mPlatformCompat.allowOverride(1, APP_PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(2, APP_PACKAGE_NAME)).isFalse();
    }

    @Test
    public void testRejectAnyOverrideForNonDebuggableAppFinalBuild() throws Exception {
        setFinalBuild();
        MockApplicationBuilder.create()
                .withTargetSdk(10)
                .mock(mPackageManager);
        ChangeConfigBuilder.create()
                .targetSdkChangeWithId(TARGET_SDK_BEFORE, 1)
                .targetSdkChangeWithId(TARGET_SDK, 2)
                .targetSdkChangeWithId(TARGET_SDK_AFTER, 3)
                .enabledChangeWithId(4)
                .disabledChangeWithId(5)
                .inject(mCompatConfig);

        assertThat(mPlatformCompat.allowOverride(1, APP_PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(2, APP_PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(3, APP_PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(4, APP_PACKAGE_NAME)).isFalse();
        assertThat(mPlatformCompat.allowOverride(5, APP_PACKAGE_NAME)).isFalse();
    }
}
