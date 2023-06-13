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

import static com.android.net.module.util.PermissionUtils.enforceAnyPermissionOf;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.UserIdInt;
import android.annotation.SuppressLint;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

import android.net.VpnManager;

import android.os.Build;

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

  public DisabledVpnManager() {
  }

  /**
   * An intent is always returned to a system activity that grants the permission to provision
   * VPN profiles. If the device doesn't support VPN the dialog will fail.
   */
  @Nullable
  public Intent provisionVpnProfile(@NonNull PlatformVpnProfile profile) {
    unsupported();
    final Intent intent = new Intent();
    final ComponentName componentName = ComponentName.unflattenFromString(
        Resources.getSystem().getString(
            com.android.internal.R.string.config_platformVpnConfirmDialogComponent));
    intent.setComponent(componentName);
    return intent;
  }

  public void deleteProvisionedVpnProfile() {
    unsupported();
    // No-op
  }

  @NonNull
  public String startProvisionedVpnProfileSession() {
    unsupported();
    return "";
  }

  @Deprecated
  public void startProvisionedVpnProfile() {
    unsupported();
    // No-Op
  }

  public void stopProvisionedVpnProfile() {
    unsupported();
    // no-op
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
    unsupported();
    // No-Op
  }

  public boolean prepareVpn(@Nullable String oldPackage, @Nullable String newPackage,
      int userId) {
    unsupported();
    return false;
  }

  public void setVpnPackageAuthorization(
      String packageName, int userId, @VpnManager.VpnType int vpnType) {
    unsupported();
    // No-Op
  }

  public boolean isAlwaysOnVpnPackageSupportedForUser(int userId, @Nullable String vpnPackage) {
    unsupported();
    return false;
  }

  @RequiresPermission(android.Manifest.permission.CONTROL_ALWAYS_ON_VPN)
  public boolean setAlwaysOnVpnPackageForUser(int userId, @Nullable String vpnPackage,
      boolean lockdownEnabled, @Nullable List<String> lockdownAllowlist) {
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

  @RequiresPermission(anyOf = {
      android.Manifest.permission.NETWORK_SETTINGS,
      NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
      android.Manifest.permission.NETWORK_STACK})
  public boolean setAppExclusionList(int userId, @NonNull String vpnPackage,
      @NonNull List<String> excludedApps) {
    unsupported();
    return false;
  }

  @RequiresPermission(anyOf = {
      android.Manifest.permission.NETWORK_SETTINGS,
      NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
      android.Manifest.permission.NETWORK_STACK})
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
    // no-op
  }

  public boolean updateLockdownVpn() {
    unsupported();
    return false;
  }

  private static void unsupported() {
    if (DEBUG) Log.w(TAG, "Unsupported method called", new Exception());
  }
}
