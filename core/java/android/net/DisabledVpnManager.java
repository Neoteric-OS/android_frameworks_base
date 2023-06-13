/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.net;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.UserIdInt;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import android.net.VpnManager;
import android.net.VpnProfileState;

import android.os.Build;
import android.os.ParcelFileDescriptor;

import android.util.Log;

import com.android.internal.net.LegacyVpnInfo;
import com.android.internal.net.VpnConfig;
import com.android.internal.net.VpnProfile;

import java.util.List;

/**
 * A no-op implementation of {@link VpnManager} for devices that doesn't support VPN.
 *
 * @hide
 */
public class DisabledVpnManager extends VpnManager {
    private static final String TAG = DisabledVpnManager.class.getSimpleName();

    private static final boolean DEBUG = false;

    private static final DisabledVpnManagerService disabledVpnManagerService
        = new DisabledVpnManagerService();

    public DisabledVpnManager(Context context) {
        super(context, disabledVpnManagerService);
    }

    /**
     * An intent is always returned that has a non-existing component instead of the Vpn dialog.
     *
     * If an app tries to launch the permission dialog using the intent, it will fail.
     */
    @Nullable
    public Intent provisionVpnProfile(@NonNull PlatformVpnProfile profile) {
        unsupported();
        final Intent intent = new Intent();
        intent.setComponent(new ComponentName("NonExisitngPackage", "NonExistingActivity"));
        return intent;
    }

    public void deleteProvisionedVpnProfile() {
        unsupported();
    }

    @NonNull
    public String startProvisionedVpnProfileSession() {
        unsupported();
        return "";
    }

    @Deprecated
    public void startProvisionedVpnProfile() {
        unsupported();
    }

    public void stopProvisionedVpnProfile() {
        unsupported();
    }

    @Nullable
    public VpnConfig getVpnConfig(@UserIdInt int userId) {
        unsupported();
        return null;
    }

    @Nullable
    public VpnProfileState getProvisionedVpnProfileState() {
        unsupported();
        return null;
    }

    @RequiresPermission(android.Manifest.permission.NETWORK_SETTINGS)
    public void factoryReset() {
        // No-Op.
    }

    public boolean prepareVpn(
            @Nullable String oldPackage, @Nullable String newPackage, int userId) {
        unsupported();
        return false;
    }

    public void setVpnPackageAuthorization(
            String packageName, int userId, @VpnManager.VpnType int vpnType) {
        unsupported();
    }

    public boolean isAlwaysOnVpnPackageSupportedForUser(int userId, @Nullable String vpnPackage) {
        unsupported();
        return false;
    }

    @RequiresPermission(android.Manifest.permission.CONTROL_ALWAYS_ON_VPN)
    public boolean setAlwaysOnVpnPackageForUser(
            int userId,
            @Nullable String vpnPackage,
            boolean lockdownEnabled,
            @Nullable List<String> lockdownAllowlist) {
        unsupported();
        return false;
    }

    @RequiresPermission(android.Manifest.permission.CONTROL_ALWAYS_ON_VPN)
    public String getAlwaysOnVpnPackageForUser(int userId) {
        unsupported();
        return null;
    }

    @RequiresPermission(android.Manifest.permission.CONTROL_ALWAYS_ON_VPN)
    public boolean isVpnLockdownEnabled(int userId) {
        unsupported();
        return false;
    }

    @RequiresPermission(
            anyOf = {
                android.Manifest.permission.NETWORK_SETTINGS,
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
                android.Manifest.permission.NETWORK_STACK
            })
    public boolean setAppExclusionList(
            int userId, @NonNull String vpnPackage, @NonNull List<String> excludedApps) {
        unsupported();
        return false;
    }

    @RequiresPermission(
            anyOf = {
                android.Manifest.permission.NETWORK_SETTINGS,
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
                android.Manifest.permission.NETWORK_STACK
            })
    @Nullable
    public List<String> getAppExclusionList(int userId, @NonNull String vpnPackage) {
        unsupported();
        return null;
    }

    @RequiresPermission(android.Manifest.permission.CONTROL_ALWAYS_ON_VPN)
    public List<String> getVpnLockdownAllowlist(int userId) {
        unsupported();
        return null;
    }

    public LegacyVpnInfo getLegacyVpnInfo(@UserIdInt int userId) {
        unsupported();
        return null;
    }

    /**
     * Legacy VPN is deprecated starting from Android S. So this API shouldn't be called if the
     * initial SDK version of device is Android S+.
     */
    public void startLegacyVpn(VpnProfile profile) {
        if (Build.VERSION.DEVICE_INITIAL_SDK_INT >= Build.VERSION_CODES.S
                && VpnProfile.isLegacyType(profile.type)) {
            throw new UnsupportedOperationException("Legacy VPN is deprecated");
        }
        unsupported();
    }

    public boolean updateLockdownVpn() {
        unsupported();
        return false;
    }

    private static void unsupported() {
        if (DEBUG) Log.w(TAG, "Unsupported method called", new RuntimeException());
    }

    /**
     * A disabled version of {@link VpnManagerService}.
     *
     * This service should only be used to initialize the disabled VPN manager, and calls to any
     * method will throw an unsupported operation exception.
     */
    private static final class DisabledVpnManagerService extends IVpnManager.Stub {
        private final UnsupportedOperationException mUnsupportedException
            = new UnsupportedOperationException(
            "This method shouldn't be called when VPN is disabled.");

        public boolean prepareVpn(String oldPackage, String newPackage, int userId) {
            throw mUnsupportedException;
        }

        public void setVpnPackageAuthorization(String packageName, int userId, int vpnType) {
            throw mUnsupportedException;
        }

        public ParcelFileDescriptor establishVpn(VpnConfig config) {
            throw mUnsupportedException;
        }

        public boolean addVpnAddress(String address, int prefixLength) {
            throw mUnsupportedException;
        }

        public boolean removeVpnAddress(String address, int prefixLength) {
            throw mUnsupportedException;
        }
        public boolean setUnderlyingNetworksForVpn(Network[] networks) {
            throw mUnsupportedException;
        }

        public boolean provisionVpnProfile(VpnProfile profile, String packageName) {
            throw mUnsupportedException;
        }
        public void deleteVpnProfile(String packageName) {
            throw mUnsupportedException;
        }
        public String startVpnProfile(String packageName) {
            throw mUnsupportedException;
        }

        public void stopVpnProfile(String packageName) {
            throw mUnsupportedException;
        }

        public VpnProfileState getProvisionedVpnProfileState(String packageName) {
            throw mUnsupportedException;
        }

        public boolean setAppExclusionList(int userId, String vpnPackage,
            List<String> excludedApps) {
            throw mUnsupportedException;
        }

        public List<String> getAppExclusionList(int userId, String vpnPackage) {
            throw mUnsupportedException;
        }

        public boolean isAlwaysOnVpnPackageSupported(int userId, String packageName) {
            throw mUnsupportedException;
        }

        public boolean setAlwaysOnVpnPackage(int userId, String packageName, boolean lockdown,
            List<String> lockdownAllowlist) {
            throw mUnsupportedException;
        }

        public String getAlwaysOnVpnPackage(int userId) {
            throw mUnsupportedException;
        }
        public boolean isVpnLockdownEnabled(int userId) {
            throw mUnsupportedException;
        }

        public List<String> getVpnLockdownAllowlist(int userId) {
            throw mUnsupportedException;
        }

        public boolean isCallerCurrentAlwaysOnVpnApp() {
            throw mUnsupportedException;
        }

        public boolean isCallerCurrentAlwaysOnVpnLockdownApp() {
            throw mUnsupportedException;
        }

        /** Legacy VPN APIs */
        public void startLegacyVpn(VpnProfile profile) {
            throw mUnsupportedException;
        }

        public LegacyVpnInfo getLegacyVpnInfo(int userId) {
            throw mUnsupportedException;
        }

        public boolean updateLockdownVpn() {
            throw mUnsupportedException;
        }

        public VpnConfig getVpnConfig(int userId) {
            throw mUnsupportedException;
        }

        public void factoryReset() {
            throw mUnsupportedException;
        }
    }
}
