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

import android.content.pm.ApplicationInfo;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PlatformCompatTest {

    private ApplicationInfo makeAppInfo(String pName, int targetSdkVersion) {
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = pName;
        ai.targetSdkVersion = targetSdkVersion;
        return ai;
    }

    @Test
    public void testUnknownChangeEnabled() {
        PlatformCompat pc = new PlatformCompat();
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 1))).isTrue();
    }

    @Test
    public void testDisabledChangeDisabled() {
        PlatformCompat pc = new PlatformCompat();
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", -1, true));
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 1))).isFalse();
    }

    @Test
    public void testTargetSdkChangeDisabled() {
        PlatformCompat pc = new PlatformCompat();
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", 2, false));
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 2))).isFalse();
    }

    @Test
    public void testTargetSdkChangeEnabled() {
        PlatformCompat pc = new PlatformCompat();
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", 2, false));
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 3))).isTrue();
    }

    @Test
    public void testDisabledOverrideTargetSdkChange() {
        PlatformCompat pc = new PlatformCompat();
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", 2, true));
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 3))).isFalse();
    }

    @Test
    public void testGetDisabledChanges() {
        PlatformCompat pc = new PlatformCompat();
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", -1, true));
        pc.addChange(new CompatChange(2345L, "OTHER_CHANGE", -1, false));
        assertThat(pc.getDisabledChanges(
                makeAppInfo("com.some.package", 2))).asList().containsExactly(1234L);
    }

    @Test
    public void testGetDisabledChangesSorted() {
        PlatformCompat pc = new PlatformCompat();
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", 2, true));
        pc.addChange(new CompatChange(123L, "OTHER_CHANGE", 2, true));
        pc.addChange(new CompatChange(12L, "THIRD_CHANGE", 2, true));
        assertThat(pc.getDisabledChanges(
                makeAppInfo("com.some.package", 2))).asList().containsExactly(12L, 123L, 1234L);
    }

    @Test
    public void testPackageOverrideEnabled() {
        PlatformCompat pc = new PlatformCompat();
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", -1, true)); // disabled
        pc.addOverride(1234L, "com.some.package", true);
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 2))).isTrue();
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.other.package", 2))).isFalse();
    }

    @Test
    public void testPackageOverrideDisabled() {
        PlatformCompat pc = new PlatformCompat();
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", -1, false));
        pc.addOverride(1234L, "com.some.package", false);
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 2))).isFalse();
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.other.package", 2))).isTrue();
    }

    @Test
    public void testPackageOverrideUnknownPackage() {
        PlatformCompat pc = new PlatformCompat();
        pc.addOverride(1234L, "com.some.package", false);
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 2))).isFalse();
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.other.package", 2))).isTrue();
    }

    @Test
    public void testPackageOverrideUnknownChange() {
        PlatformCompat pc = new PlatformCompat();
        assertThat(pc.isChangeEnabled(1234L, makeAppInfo("com.some.package", 1))).isTrue();
    }

    @Test
    public void testLookupChangeId() {
        PlatformCompat pc = new PlatformCompat();
        pc.addChange(new CompatChange(1234L, "MY_CHANGE", -1, false));
        pc.addChange(new CompatChange(2345L, "ANOTHER_CHANGE", -1, false));
        assertThat(pc.lookupChangeId("MY_CHANGE")).isEqualTo(1234L);
    }

    @Test
    public void testLookupChangeIdNotPresent() {
        PlatformCompat pc = new PlatformCompat();
        assertThat(pc.lookupChangeId("MY_CHANGE")).isEqualTo(-1L);
    }
}
