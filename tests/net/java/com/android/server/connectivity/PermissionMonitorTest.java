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
 * limitations under the License
 */

package com.android.server.connectivity;

import static android.Manifest.permission.CHANGE_NETWORK_STATE;
import static android.Manifest.permission.CHANGE_WIFI_STATE;
import static android.Manifest.permission.CONNECTIVITY_INTERNAL;
import static android.Manifest.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS;
import static android.Manifest.permission.NETWORK_STACK;
import static android.content.pm.ApplicationInfo.PRIVATE_FLAG_OEM;
import static android.content.pm.ApplicationInfo.PRIVATE_FLAG_PRODUCT;
import static android.content.pm.ApplicationInfo.PRIVATE_FLAG_VENDOR;
import static android.content.pm.PackageManager.GET_PERMISSIONS;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class PermissionMonitorTest {
    private static final int MOCK_UID = 10001;
    private static final String[] MOCK_PACKAGE_NAMES = new String[] { "com.foo.bar" };

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;

    private PermissionMonitor mPermissionMonitor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getPackagesForUid(MOCK_UID)).thenReturn(MOCK_PACKAGE_NAMES);
        mPermissionMonitor = new PermissionMonitor(mContext, null);
    }

    private void expectPermission(String[] permissions, int privateFlags,
            int targetSdkVersion) throws Exception {
        final PackageInfo packageInfo = packageInfoWithPermissions(permissions, privateFlags);
        packageInfo.applicationInfo.targetSdkVersion = targetSdkVersion;
        when(mPackageManager.getPackageInfoAsUser(
                eq(MOCK_PACKAGE_NAMES[0]), eq(GET_PERMISSIONS), anyInt())).thenReturn(packageInfo);
    }

    private PackageInfo packageInfoWithPermissions(String[] permissions, int privateFlags) {
        final PackageInfo packageInfo = new PackageInfo();
        packageInfo.requestedPermissions = permissions;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.privateFlags = privateFlags;
        return packageInfo;
    }

    @Test
    public void testHasPermission() {
        PackageInfo app = packageInfoWithPermissions(new String[] {}, 0);
        assertFalse(mPermissionMonitor.hasPermission(app, CHANGE_NETWORK_STATE));
        assertFalse(mPermissionMonitor.hasPermission(app, NETWORK_STACK));
        assertFalse(mPermissionMonitor.hasPermission(app, CONNECTIVITY_USE_RESTRICTED_NETWORKS));
        assertFalse(mPermissionMonitor.hasPermission(app, CONNECTIVITY_INTERNAL));

        app = packageInfoWithPermissions(new String[] { CHANGE_NETWORK_STATE, NETWORK_STACK }, 0);
        assertTrue(mPermissionMonitor.hasPermission(app, CHANGE_NETWORK_STATE));
        assertTrue(mPermissionMonitor.hasPermission(app, NETWORK_STACK));
        assertFalse(mPermissionMonitor.hasPermission(app, CONNECTIVITY_USE_RESTRICTED_NETWORKS));
        assertFalse(mPermissionMonitor.hasPermission(app, CONNECTIVITY_INTERNAL));

        app = packageInfoWithPermissions(new String[] {
                CONNECTIVITY_USE_RESTRICTED_NETWORKS, CONNECTIVITY_INTERNAL }, 0);
        assertFalse(mPermissionMonitor.hasPermission(app, CHANGE_NETWORK_STATE));
        assertFalse(mPermissionMonitor.hasPermission(app, NETWORK_STACK));
        assertTrue(mPermissionMonitor.hasPermission(app, CONNECTIVITY_USE_RESTRICTED_NETWORKS));
        assertTrue(mPermissionMonitor.hasPermission(app, CONNECTIVITY_INTERNAL));
    }

    @Test
    public void testIsVendorApp() {
        PackageInfo app = packageInfoWithPermissions(new String[] {}, 0);
        assertFalse(mPermissionMonitor.isVendorApp(app.applicationInfo));
        app = packageInfoWithPermissions(new String[] {}, PRIVATE_FLAG_OEM);
        assertTrue(mPermissionMonitor.isVendorApp(app.applicationInfo));
        app = packageInfoWithPermissions(new String[] {}, PRIVATE_FLAG_PRODUCT);
        assertTrue(mPermissionMonitor.isVendorApp(app.applicationInfo));
        app = packageInfoWithPermissions(new String[] {}, PRIVATE_FLAG_VENDOR);
        assertTrue(mPermissionMonitor.isVendorApp(app.applicationInfo));
    }

    @Test
    public void testHasUseBackgroundNetworksPermission() throws Exception {
        expectPermission(new String[] { CHANGE_NETWORK_STATE },0, Build.VERSION_CODES.P);
        assertTrue(mPermissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID));
        expectPermission(new String[] { NETWORK_STACK }, 0, Build.VERSION_CODES.P);
        assertTrue(mPermissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID));
        expectPermission(new String[] { CONNECTIVITY_INTERNAL },
            0, Build.VERSION_CODES.P);
        assertTrue(mPermissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID));
        expectPermission(new String[] { CONNECTIVITY_USE_RESTRICTED_NETWORKS },
            0, Build.VERSION_CODES.P);
        assertTrue(mPermissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID));

        expectPermission(new String[] { CHANGE_NETWORK_STATE },
            PRIVATE_FLAG_VENDOR, Build.VERSION_CODES.P);
        assertTrue(mPermissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID));
        expectPermission(new String[] { NETWORK_STACK },
            PRIVATE_FLAG_VENDOR, Build.VERSION_CODES.P);
        assertTrue(mPermissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID));
        expectPermission(new String[] { CONNECTIVITY_INTERNAL },
            PRIVATE_FLAG_VENDOR, Build.VERSION_CODES.P);
        assertTrue(mPermissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID));
        expectPermission(new String[] { CONNECTIVITY_USE_RESTRICTED_NETWORKS },
            PRIVATE_FLAG_VENDOR, Build.VERSION_CODES.P);
        assertTrue(mPermissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID));

        expectPermission(new String[] {}, 0, Build.VERSION_CODES.P);
        assertFalse(mPermissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID));
        expectPermission(new String[] { CHANGE_WIFI_STATE },
            0, Build.VERSION_CODES.P);
        assertFalse(mPermissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID));
        expectPermission(new String[] {}, PRIVATE_FLAG_VENDOR, Build.VERSION_CODES.P);
        assertTrue(mPermissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID));
        expectPermission(new String[] { CHANGE_WIFI_STATE },
            PRIVATE_FLAG_VENDOR, Build.VERSION_CODES.P);
        assertTrue(mPermissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID));

        // TODO: update version when starting to enforce 1:1 mapping
        expectPermission(new String[] {}, 0, Build.VERSION_CODES.P + 1);
        assertFalse(mPermissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID));
        expectPermission(new String[] { CHANGE_WIFI_STATE },
            0, Build.VERSION_CODES.P + 1);
        assertFalse(mPermissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID));
        expectPermission(new String[] {}, PRIVATE_FLAG_VENDOR, Build.VERSION_CODES.P + 1);
        assertFalse(mPermissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID));
        expectPermission(new String[] { CHANGE_WIFI_STATE },
            PRIVATE_FLAG_VENDOR, Build.VERSION_CODES.P + 1);
        assertFalse(mPermissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID));
    }
}
