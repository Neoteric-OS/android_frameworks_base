/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.server.autofill;

import static com.android.server.autofill.AutofillManagerService.getAllowedCompatModePackages;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.os.UserHandle;
import android.provider.Settings;
import android.test.mock.MockContentResolver;

import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.pm.UserManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public class AutofillManagerServiceTest {

    private static final int USER_ID = 42;

    private Context mContext;
    private MockContentResolver mContentResolver;

    @Before
    public void setup() {
        mContext = mock(Context.class);
        mContentResolver = new MockContentResolver(mContext);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
    }

    @After
    public void teardown() {
        LocalServices.removeServiceForTest(UserManagerInternal.class);
    }

    @Test
    public void testGetAllowedCompatModePackages_null() {
        assertThat(getAllowedCompatModePackages(null)).isNull();
    }

    @Test
    public void testGetAllowedCompatModePackages_empty() {
        assertThat(getAllowedCompatModePackages("")).isNull();
    }

    @Test
    public void testGetAllowedCompatModePackages_onePackageNoUrls() {
        assertThat(getAllowedCompatModePackages("one_is_the_loniest_package"))
                .containsExactly("one_is_the_loniest_package", null);
    }

    @Test
    public void testGetAllowedCompatModePackages_onePackageMissingEndDelimiter() {
        assertThat(getAllowedCompatModePackages("one_is_the_loniest_package[")).isEmpty();
    }

    @Test
    public void testGetAllowedCompatModePackages_onePackageOneUrl() {
        final Map<String, String[]> result =
                getAllowedCompatModePackages("one_is_the_loniest_package[url]");
        assertThat(result).hasSize(1);
        assertThat(result.get("one_is_the_loniest_package")).asList().containsExactly("url");
    }

    @Test
    public void testGetAllowedCompatModePackages_onePackageMultipleUrls() {
        final Map<String, String[]> result =
                getAllowedCompatModePackages("one_is_the_loniest_package[4,5,8,15,16,23,42]");
        assertThat(result).hasSize(1);
        assertThat(result.get("one_is_the_loniest_package")).asList()
            .containsExactly("4", "5", "8", "15", "16", "23", "42");
    }

    @Test
    public void testGetAllowedCompatModePackages_multiplePackagesOneInvalid() {
        final Map<String, String[]> result = getAllowedCompatModePackages("one:two[");
        assertThat(result).hasSize(1);
        assertThat(result.get("one")).isNull();
    }

    @Test
    public void testGetAllowedCompatModePackages_multiplePackagesMultipleUrls() {
        final Map<String, String[]> result =
                getAllowedCompatModePackages("p1[p1u1]:p2:p3[p3u1,p3u2]");
        assertThat(result).hasSize(3);
        assertThat(result.get("p1")).asList().containsExactly("p1u1");
        assertThat(result.get("p2")).isNull();
        assertThat(result.get("p3")).asList().containsExactly("p3u1", "p3u2");
    }

    @Test
    public void testGetAllowedCompatModePackages_threePackagesOneInvalid() {
        final Map<String, String[]> result =
                getAllowedCompatModePackages("p1[p1u1]:p2[:p3[p3u1,p3u2]");
        assertThat(result).hasSize(2);
        assertThat(result.get("p1")).asList().containsExactly("p1u1");
        assertThat(result.get("p3")).asList().containsExactly("p3u1", "p3u2");
    }

    @Test
    public void testOnDeviceProvisionedLocked_invokedAfterProvisioning() throws Exception {
        // Arrange
        UserManagerInternal umi = mock(UserManagerInternal.class);
        LocalServices.addService(UserManagerInternal.class, umi);

        Settings.Global.putInt(mContentResolver, Settings.Global.DEVICE_PROVISIONED, 0);
        MockContentResolver spiedResolver = spy(mContentResolver);
        when(mContext.getContentResolver()).thenReturn(spiedResolver);

        List<UserInfo> users = new ArrayList<>();
        users.add(new UserInfo(10, "user10", UserInfo.FLAG_FULL));
        users.add(new UserInfo(12, "user12", UserInfo.FLAG_FULL));
        when(umi.getUserInfos()).thenReturn(users.toArray(new UserInfo[0]));

        AutofillManagerService service = spy(new AutofillManagerService(mContext));
        doReturn(mock(AutofillManagerServiceImpl.class)).when(service)
                .newServiceLocked(anyInt(), anyBoolean());
        doNothing().when(service).updateCachedServiceLocked(anyInt());

        ArgumentCaptor<ContentObserver> observerCaptor =
                ArgumentCaptor.forClass(ContentObserver.class);
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);
        verify(spiedResolver).registerContentObserver(
                eq(Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED)), eq(false),
                observerCaptor.capture(), eq(UserHandle.USER_ALL));
        ContentObserver observer = observerCaptor.getValue();

        // Act
        Settings.Global.putInt(mContentResolver, Settings.Global.DEVICE_PROVISIONED, 1);
        mContentResolver.notifyChange(
                Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED),
                null /* observer */,
                USER_ID);

        // Assert
        verify(service, timeout(1000)).onDeviceProvisionedLocked();
        verify(spiedResolver, timeout(1000)).unregisterContentObserver(observer);
    }

    @Test
    public void testOnDeviceProvisionedLocked_notInvokedIfAlreadyProvisioned() throws Exception {
        // Arrange
        Settings.Global.putInt(mContentResolver, Settings.Global.DEVICE_PROVISIONED, 1);
        MockContentResolver spiedResolver = spy(mContentResolver);
        when(mContext.getContentResolver()).thenReturn(spiedResolver);

        AutofillManagerService service = new AutofillManagerService(mContext);

        // Act
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Assert
        verify(spiedResolver, never()).registerContentObserver(
                eq(Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED)),
                anyBoolean(), any(), anyInt());
    }
}
