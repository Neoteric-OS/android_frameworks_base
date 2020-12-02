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

package com.android.server.locksettings;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.service.resumeonreboot.ResumeOnRebootService;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

@SmallTest
@RunWith(JUnit4.class)
public class ResumeOnRebootServiceProviderTests {

    @Mock
    Context mMockContext;
    @Mock
    IPackageManager mMockPackageManager;
    @Mock
    ParceledListSlice<ResolveInfo> mMockParceledListSlice;
    @Mock
    ResolveInfo mMockResolvedInfo;
    @Mock
    ServiceInfo mMockServiceInfo;
    @Mock
    ComponentName mMockComponentName;
    @Captor
    ArgumentCaptor<Intent> intentArgumentCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getUserId()).thenReturn(0);
        when(mMockResolvedInfo.serviceInfo).thenReturn(mMockServiceInfo);
        when(mMockServiceInfo.getComponentName()).thenReturn(mMockComponentName);
    }

    @Test
    public void noPermission() throws Exception {
        when(mMockPackageManager.checkPermission(eq(Manifest.permission.RECOVERY),
                eq("com.google.android.gms"), any())).thenReturn(
                PackageManager.PERMISSION_DENIED);
        assertThat(new ResumeOnRebootServiceProvider(mMockContext,
                mMockPackageManager).getServiceConnection()).isNull();
        verifyNoMoreInteractions(mMockPackageManager);
    }

    @Test
    public void noServiceFound() throws Exception {
        when(mMockPackageManager.checkPermission(eq(Manifest.permission.RECOVERY),
                eq("com.google.android.gms"), any())).thenReturn(
                PackageManager.PERMISSION_GRANTED);
        when(mMockPackageManager.queryIntentServices(any(), eq(null), eq(0), eq(0))).thenReturn(
                null);
        assertThat(new ResumeOnRebootServiceProvider(mMockContext,
                mMockPackageManager).getServiceConnection()).isNull();
    }

    @Test
    public void serviceNotGuardedWithPermission() throws Exception {
        when(mMockPackageManager.checkPermission(eq(Manifest.permission.RECOVERY),
                eq("com.google.android.gms"), any())).thenReturn(
                PackageManager.PERMISSION_GRANTED);
        when(mMockPackageManager.queryIntentServices(any(), any(), any(), any())).thenReturn(
                mMockParceledListSlice);
        ArrayList<ResolveInfo> resultList = new ArrayList<>();
        resultList.add(mMockResolvedInfo);
        when(mMockParceledListSlice.getList()).thenReturn(resultList);
        when(mMockServiceInfo.permission).thenReturn("");
        assertThat(new ResumeOnRebootServiceProvider(mMockContext,
                mMockPackageManager).getServiceConnection()).isNull();
    }

    @Test
    public void serviceResolved() throws Exception {
        when(mMockPackageManager.checkPermission(eq(Manifest.permission.RECOVERY),
                eq("com.google.android.gms"), any())).thenReturn(
                PackageManager.PERMISSION_GRANTED);
        when(mMockPackageManager.queryIntentServices(any(), eq(null),
                eq(0), eq(0))).thenReturn(
                mMockParceledListSlice);
        ArrayList<ResolveInfo> resultList = new ArrayList<>();
        resultList.add(mMockResolvedInfo);
        when(mMockParceledListSlice.getList()).thenReturn(resultList);
        when(mMockServiceInfo.permission).thenReturn(Manifest.permission.RECOVERY);

        assertThat(new ResumeOnRebootServiceProvider(mMockContext,
                mMockPackageManager).getServiceConnection()).isNotNull();

        verify(mMockPackageManager).queryIntentServices(intentArgumentCaptor.capture(), eq(null),
                eq(0), eq(0));
        assertThat(intentArgumentCaptor.getValue().getPackage()).isEqualTo(
                "com.google.android.gms");
        assertThat(intentArgumentCaptor.getValue().getAction()).isEqualTo(
                ResumeOnRebootService.SERVICE_INTERFACE);
    }
}
