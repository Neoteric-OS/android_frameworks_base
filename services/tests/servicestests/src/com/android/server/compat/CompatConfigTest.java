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

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.compat.AndroidBuildClassifier;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class CompatConfigTest {

    @Mock
    private Context mContext;
    @Mock
    private AndroidBuildClassifier mBuildClassifier;

    private File createTempDir() {
        String base = System.getProperty("java.io.tmpdir");
        File dir = new File(base, UUID.randomUUID().toString());
        assertThat(dir.mkdirs()).isTrue();
        return dir;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        // Assume userdebug/eng non-final build
        when(mBuildClassifier.isDebuggableBuild()).thenReturn(true);
        when(mBuildClassifier.isFinalBuild()).thenReturn(false);
    }

    @Test
    public void testUnknownChangeEnabled() throws Exception {
        ApplicationInfo app = ApplicationInfoBuilder.create()
                .withPackageName("com.some.package")
                .build();
        CompatConfig compatConfig = new CompatConfig(mBuildClassifier, mContext);
        assertThat(compatConfig.isChangeEnabled(1234L, app)).isTrue();
    }

    @Test
    public void testDisabledChangeDisabled() throws Exception {
        final long changeId = 1234L;
        CompatConfig compatConfig = CompatConfigBuilder.create(mBuildClassifier, mContext)
                .addDisabledChangeWithId(changeId)
                .build();
        ApplicationInfo app = ApplicationInfoBuilder.create()
                .withPackageName("com.some.package")
                .build();

        assertThat(compatConfig.isChangeEnabled(changeId, app)).isFalse();
    }

    @Test
    public void testTargetSdkChangeDisabled() throws Exception {
        final long changeId = 1234L;
        final int targetSdk = 2;
        CompatConfig compatConfig = CompatConfigBuilder.create(mBuildClassifier, mContext)
                .addTargetSdkChangeWithId(targetSdk, changeId)
                .build();
        ApplicationInfo app = ApplicationInfoBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(targetSdk)
                .build();

        assertThat(compatConfig.isChangeEnabled(changeId, app)).isFalse();
    }

    @Test
    public void testTargetSdkChangeEnabled() throws Exception {
        final long changeId = 1234L;
        final int targetSdk = 2;
        final int targetSdkHigher = 3;
        CompatConfig compatConfig = CompatConfigBuilder.create(mBuildClassifier, mContext)
                .addTargetSdkChangeWithId(targetSdk, changeId)
                .build();
        ApplicationInfo app = ApplicationInfoBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(targetSdkHigher)
                .build();

        assertThat(compatConfig.isChangeEnabled(changeId, app)).isTrue();
    }

    @Test
    public void testDisabledOverrideTargetSdkChange() throws Exception {
        final long changeId = 1234L;
        final int targetSdk = 2;
        final int targetSdkHigher = 3;
        CompatConfig compatConfig = CompatConfigBuilder.create(mBuildClassifier, mContext)
                .addTargetSdkDisabledChangeWithId(targetSdk, changeId)
                .build();
        ApplicationInfo app = ApplicationInfoBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(targetSdkHigher)
                .build();

        assertThat(compatConfig.isChangeEnabled(changeId, app)).isFalse();
    }

    @Test
    public void testGetDisabledChanges() throws Exception {
        final long disabledChangeId = 1234L;
        final long otherChangeId = 2345L;
        CompatConfig compatConfig = CompatConfigBuilder.create(mBuildClassifier, mContext)
                .addDisabledChangeWithId(disabledChangeId)
                .addEnabledChangeWithId(otherChangeId)
                .build();
        ApplicationInfo app = ApplicationInfoBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(2)
                .build();

        assertThat(compatConfig.getDisabledChanges(app)).asList().containsExactly(
                disabledChangeId);
    }

    @Test
    public void testGetDisabledChangesSorted() throws Exception {
        final long highestChangeId = 1234L;
        final long middleChangeId = 123L;
        final long lowestChangeId = 12L;
        CompatConfig compatConfig = CompatConfigBuilder.create(mBuildClassifier, mContext)
                .addDisabledChangeWithId(highestChangeId)
                .addDisabledChangeWithId(middleChangeId)
                .addDisabledChangeWithId(lowestChangeId)
                .build();
        ApplicationInfo app = ApplicationInfoBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(2)
                .build();

        assertThat(compatConfig.getDisabledChanges(app)).asList()
                .containsExactly(lowestChangeId, middleChangeId, highestChangeId);
    }

    @Test
    public void testPackageOverrideEnabled() throws Exception {
        final long changeId = 1234L;
        final String appPackage = "com.some.package";
        final String otherAppPackage = "com.other.package";
        CompatConfig compatConfig = CompatConfigBuilder.create(mBuildClassifier, mContext)
                .addDisabledChangeWithId(changeId)
                .build();
        ApplicationInfo app = ApplicationInfoBuilder.create()
                .withPackageName(appPackage)
                .withTargetSdk(2)
                .build();
        ApplicationInfo otherApp = ApplicationInfoBuilder.create()
                .withPackageName(otherAppPackage)
                .withTargetSdk(2)
                .build();
        OverridesBuilder.create()
                .enable(changeId)
                .toPackage(appPackage)
                .override(compatConfig);

        assertThat(compatConfig.isChangeEnabled(changeId, app)).isTrue();
        assertThat(compatConfig.isChangeEnabled(changeId, otherApp)).isFalse();
    }

    @Test
    public void testPackageOverrideDisabled() throws Exception {
        final long changeId = 1234L;
        final String appPackage = "com.some.package";
        final String otherAppPackage = "com.other.package";
        CompatConfig compatConfig = CompatConfigBuilder.create(mBuildClassifier, mContext)
                .addEnabledChangeWithId(changeId)
                .build();
        ApplicationInfo app = ApplicationInfoBuilder.create()
                .withPackageName(appPackage)
                .build();
        ApplicationInfo otherApp = ApplicationInfoBuilder.create()
                .withPackageName(otherAppPackage)
                .build();
        OverridesBuilder.create()
                .disable(changeId)
                .toPackage(appPackage)
                .override(compatConfig);
        assertThat(compatConfig.isChangeEnabled(changeId, app)).isFalse();
        assertThat(compatConfig.isChangeEnabled(changeId, otherApp)).isTrue();
    }

    @Test
    public void testPackageOverrideUnknownPackage() throws Exception {
        final long changeId = 1234L;
        final String appPackage = "com.some.package";
        final String otherAppPackage = "com.other.package";
        ApplicationInfo app = ApplicationInfoBuilder.create()
                .withPackageName(appPackage)
                .withTargetSdk(2)
                .build();
        ApplicationInfo otherApp = ApplicationInfoBuilder.create()
                .withPackageName(otherAppPackage)
                .withTargetSdk(2)
                .build();
        CompatConfig compatConfig = new CompatConfig(mBuildClassifier, mContext);
        OverridesBuilder.create()
                .disable(changeId)
                .toPackage(appPackage)
                .override(compatConfig);

        assertThat(compatConfig.isChangeEnabled(changeId, app)).isFalse();
        assertThat(compatConfig.isChangeEnabled(changeId, otherApp)).isTrue();
    }

    @Test
    public void testPreventAddOverride() throws Exception {
        final long changeId = 1234L;
        CompatConfig compatConfig = CompatConfigBuilder.create(mBuildClassifier, mContext)
                .addDisabledChangeWithId(changeId)
                .build();
        ApplicationInfo app = ApplicationInfoBuilder.create()
                .withPackageName("com.some.package")
                .build();
        PackageManager packageManager = mock(PackageManager.class);
        when(mContext.getPackageManager()).thenReturn(packageManager);
        when(packageManager.getApplicationInfo(anyString(), anyInt())).thenReturn(app);
        // Force the validator to prevent overriding the change by using a user build.
        when(mBuildClassifier.isDebuggableBuild()).thenReturn(false);
        when(mBuildClassifier.isFinalBuild()).thenReturn(true);

        assertThrows(SecurityException.class,
                () -> OverridesBuilder.create()
                    .enable(changeId)
                    .toPackage("com.some.package")
                    .override(compatConfig)
        );

        assertThat(compatConfig.isChangeEnabled(1234L, app)).isFalse();
    }

    @Test
    public void testPreventRemoveOverride() throws Exception {
        final long changeId = 1234L;
        final String packageName = "com.some.package";
        CompatConfig compatConfig = CompatConfigBuilder.create(mBuildClassifier, mContext)
                .addDisabledChangeWithId(changeId)
                .build();
        ApplicationInfo app = ApplicationInfoBuilder.create()
                .withPackageName(packageName)
                .build();
        PackageManager packageManager = mock(PackageManager.class);
        when(mContext.getPackageManager()).thenReturn(packageManager);
        when(packageManager.getApplicationInfo(anyString(), anyInt())).thenReturn(app);
        // Assume the override was allowed to be added.
        OverridesBuilder.create()
                .enable(changeId)
                .toPackage(packageName)
                .override(compatConfig);
        // Validator allows turning on the change.
        assertThat(compatConfig.isChangeEnabled(changeId, app)).isTrue();

        // Reject all override attempts.
        // Force the validator to prevent overriding the change by using a user build.
        when(mBuildClassifier.isDebuggableBuild()).thenReturn(false);
        when(mBuildClassifier.isFinalBuild()).thenReturn(true);
        // Try to turn off change, but validator prevents it.
        assertThrows(SecurityException.class,
                () -> compatConfig.removeOverride(changeId, packageName));
        assertThat(compatConfig.isChangeEnabled(changeId, app)).isTrue();
    }

    @Test
    public void testPackageOverrideUnknownChange() throws Exception {
        ApplicationInfo app = ApplicationInfoBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(1)
                .build();
        CompatConfig compatConfig = new CompatConfig(mBuildClassifier, mContext);
        assertThat(compatConfig.isChangeEnabled(1234L, app)).isTrue();
    }

    @Test
    public void testRemovePackageOverride() throws Exception {
        final long changeId = 1234L;
        final String packageName = "com.some.package";
        CompatConfig compatConfig = CompatConfigBuilder.create(mBuildClassifier, mContext)
                .addEnabledChangeWithId(changeId)
                .build();
        ApplicationInfo app = ApplicationInfoBuilder.create()
                .withPackageName(packageName)
                .build();

        assertThat(compatConfig.addOverride(changeId, packageName, false))
                .isTrue();
        assertThat(compatConfig.isChangeEnabled(changeId, app)).isFalse();

        compatConfig.removeOverride(changeId, packageName);
        assertThat(compatConfig.isChangeEnabled(changeId, app)).isTrue();
    }

    @Test
    public void testLookupChangeId() throws Exception {
        final long changeId = 1234L;
        final String changeName = "MY_CHANGE";
        final long otherChangeId = 2345L;
        final String otherChangeName = "MY_CHANGE";
        CompatConfig compatConfig = CompatConfigBuilder.create(mBuildClassifier, mContext)
                .addEnabledChangeWithIdAndName(changeId, changeName)
                .addEnabledChangeWithIdAndName(otherChangeId, otherChangeName)
                .build();

        assertThat(compatConfig.lookupChangeId(changeName)).isEqualTo(changeId);
    }

    @Test
    public void testLookupChangeIdNotPresent() throws Exception {
        CompatConfig compatConfig = new CompatConfig(mBuildClassifier, mContext);
        assertThat(compatConfig.lookupChangeId("MY_CHANGE")).isEqualTo(-1L);
    }

    @Test
    public void testReadConfig() throws Exception {
        File dir = createTempDir();
        CompatConfigBuilder.create(mBuildClassifier, mContext)
                .addTargetSdkChangeWithId(2, 1234L)
                .addDisabledChangeWithId(1235L)
                .addEnabledChangeWithId(1236)
                .saveToFile(dir, "/platform_compat_config.xml");
        CompatConfig compatConfig = new CompatConfig(mBuildClassifier, mContext);
        compatConfig.initConfigFromLib(dir);

        ApplicationInfo appTargetSdk1 = ApplicationInfoBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(1)
                .build();
        ApplicationInfo appTargetSdk3 = ApplicationInfoBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(3)
                .build();
        ApplicationInfo appTargetSdk5 = ApplicationInfoBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(5)
                .build();

        assertThat(compatConfig.isChangeEnabled(1234L, appTargetSdk1)).isFalse();
        assertThat(compatConfig.isChangeEnabled(1234L, appTargetSdk3)).isTrue();
        assertThat(compatConfig.isChangeEnabled(1235L, appTargetSdk5)).isFalse();
        assertThat(compatConfig.isChangeEnabled(1236L, appTargetSdk1)).isTrue();
    }

    @Test
    public void testReadConfigMultipleFiles() throws Exception {
        File dir = createTempDir();
        CompatConfigBuilder.create(mBuildClassifier, mContext)
                .addTargetSdkChangeWithId(2, 1234L)
                .saveToFile(dir, "/libcore_platform_compat_config.xml");
        CompatConfigBuilder.create(mBuildClassifier, mContext)
                .addDisabledChangeWithId(1235L)
                .saveToFile(dir, "/frameworks_platform_compat_config.xml");
        CompatConfig compatConfig = new CompatConfig(mBuildClassifier, mContext);
        compatConfig.initConfigFromLib(dir);

        ApplicationInfo appTargetSdk1 = ApplicationInfoBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(1)
                .build();
        ApplicationInfo appTargetSdk3 = ApplicationInfoBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(3)
                .build();
        ApplicationInfo appTargetSdk5 = ApplicationInfoBuilder.create()
                .withPackageName("com.some.package")
                .withTargetSdk(5)
                .build();

        assertThat(compatConfig.isChangeEnabled(1234L, appTargetSdk1)).isFalse();
        assertThat(compatConfig.isChangeEnabled(1234L, appTargetSdk3)).isTrue();
        assertThat(compatConfig.isChangeEnabled(1235L, appTargetSdk5)).isFalse();
        assertThat(compatConfig.isChangeEnabled(1236L, appTargetSdk1)).isTrue();
    }
}
