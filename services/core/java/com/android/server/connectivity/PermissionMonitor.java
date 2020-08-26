/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.connectivity;

import static android.Manifest.permission.CHANGE_NETWORK_STATE;
import static android.Manifest.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS;
import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.NETWORK_STACK;
import static android.Manifest.permission.UPDATE_DEVICE_STATS;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.MATCH_ANY_USER;
import static android.net.INetd.PERMISSION_INTERNET;
import static android.net.INetd.PERMISSION_NETWORK;
import static android.net.INetd.PERMISSION_NONE;
import static android.net.INetd.PERMISSION_SYSTEM;
import static android.net.INetd.PERMISSION_UNINSTALLED;
import static android.net.INetd.PERMISSION_UPDATE_DEVICE_STATS;
import static android.os.Process.INVALID_UID;
import static android.os.Process.NETWORK_STACK_UID;
import static android.os.Process.SYSTEM_UID;

import static com.android.internal.util.ArrayUtils.convertToIntArray;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManagerInternal;
import android.content.pm.UserInfo;
import android.net.INetd;
import android.net.UidRange;
import android.os.Build;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.UserHandle;
import android.os.UserManager;
import android.system.OsConstants;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.LocalServices;
import com.android.server.SystemConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A utility class to inform Netd of UID permisisons.
 * Does a mass update at boot and then monitors for app install/remove.
 *
 * @hide
 */
public class PermissionMonitor implements PackageManagerInternal.PackageListObserver {
    private static final String TAG = "PermissionMonitor";
    private static final boolean DBG = true;
    private static final int VERSION_Q = Build.VERSION_CODES.Q;

    private final PackageManager mPackageManager;
    private final UserManager mUserManager;
    private final INetd mNetd;
    private final Dependencies mDeps;

    // Values are User IDs.
    @GuardedBy("this")
    private final Set<Integer> mUsers = new HashSet<>();

    // Keys are app ids. Values are netd permissions info of app ids.
    @GuardedBy("this")
    private final SparseArray<AppIdNetdPermissionInfo> mAppIdsPermInfo = new SparseArray<>();

    // Keys are active non-bypassable and fully-routed VPN's interface name, Values are uid ranges
    // for apps under the VPN
    @GuardedBy("this")
    private final Map<String, Set<UidRange>> mVpnUidRanges = new HashMap<>();

    // A set of appIds for apps across all users on the device. We track appIds instead of uids
    // directly to reduce its size and also eliminate the need to update this set when user is
    // added/removed.
    @GuardedBy("this")
    private final Set<Integer> mAllApps = new HashSet<>();

    /**
     * Dependencies of PermissionMonitor, for injection in tests.
     */
    @VisibleForTesting
    public static class Dependencies {
        /**
         * Get device first sdk version.
         */
        public int getDeviceFirstSdkInt() {
            return Build.VERSION.FIRST_SDK_INT;
        }

        /**
         * Check whether given uid has specific permission.
         */
        public int uidPermission(@NonNull final String permission, final int uid) {
            return ActivityManager.checkUidPermission(permission, uid);
        }
    }

    /**
     * A data class to store each app id Netd permission information. Netd permissions includes
     * PERMISSION_NETWORK, PERMISSION_SYSTEM, PERMISSION_INTERNET, PERMISSION_UPDATE_DEVICE_STATS
     * and OR'd with the others. Default permission is PERMISSION_NONE. PERMISSION_UNINSTALLED will
     * be set if all packages are removed from all users and app id.
     */
    public static class AppIdNetdPermissionInfo {
        private final int mNetdPermissions;
        private final boolean mHasCarryoverPackage;

        AppIdNetdPermissionInfo() {
            this(PERMISSION_NONE, false/* hasCarryoverPackage */);
        }

        AppIdNetdPermissionInfo(final int permissions) {
            this(permissions, false/* hasCarryoverPackage */);
        }

        AppIdNetdPermissionInfo(final int permissions, final boolean hasCarryoverPackage) {
            mNetdPermissions = permissions;
            mHasCarryoverPackage = hasCarryoverPackage;
        }

        /** Plus given permissions and return new AppIdNetdPermissionInfo instance. */
        public AppIdNetdPermissionInfo plusNetdPermissions(final int permissions) {
            return new AppIdNetdPermissionInfo(
                    mNetdPermissions | permissions, mHasCarryoverPackage);
        }

        /** Return whether app id has some carryover packages */
        public boolean hasCarryoverPackage() {
            return mHasCarryoverPackage;
        }

        /** Return whether all packages is uninstalled from app id. */
        public boolean isAllPackagesUninstalled() {
            return mNetdPermissions == PERMISSION_UNINSTALLED;
        }

        /** Check that app id has given permissions */
        public boolean hasNetdPermissions(final int permissions) {
            if (isAllPackagesUninstalled()) return false;
            if (permissions == PERMISSION_NONE) return true;
            return (mNetdPermissions & permissions) == permissions;
        }

        /** Return true if given object is same AppIdNetdPermissionInfo object has the same
         *  permissions and carryover package value or false otherwise.
         */
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof AppIdNetdPermissionInfo)) return false;
            final AppIdNetdPermissionInfo other = (AppIdNetdPermissionInfo) obj;
            return mNetdPermissions == other.mNetdPermissions
                    && mHasCarryoverPackage == other.mHasCarryoverPackage;
        }
    }

    public PermissionMonitor(@NonNull final Context context, @NonNull final INetd netd) {
        this(context, netd, new Dependencies());
    }

    @VisibleForTesting
    PermissionMonitor(@NonNull final Context context, @NonNull final INetd netd,
            @NonNull final Dependencies deps) {
        mPackageManager = context.getPackageManager();
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        mNetd = netd;
        mDeps = deps;
    }

    // Intended to be called only once at startup, after the system is ready. Installs a broadcast
    // receiver to monitor ongoing UID changes, so this shouldn't/needn't be called again.
    public synchronized void startMonitoring() {
        log("Monitoring");

        PackageManagerInternal pmi = LocalServices.getService(PackageManagerInternal.class);
        if (pmi != null) {
            pmi.getPackageList(this);
        } else {
            loge("failed to get the PackageManagerInternal service");
        }

        final List<UserInfo> users = mUserManager.getUsers(true);  // exclude dying users
        if (users != null) {
            for (UserInfo user : users) {
                mUsers.add(user.id);
            }
        }

        for (int userId : mUsers) {
            final List<PackageInfo> apps =
                    mPackageManager.getInstalledPackagesAsUser(GET_PERMISSIONS, userId);
            if (apps == null) {
                loge("No apps for userId=" + userId);
                continue;
            }

            for (PackageInfo app : apps) {
                final int uid = app.applicationInfo != null ? app.applicationInfo.uid : INVALID_UID;
                if (uid < 0) {
                    continue;
                }
                final int appId = UserHandle.getAppId(uid);
                mAllApps.add(appId);

                final boolean hasCarryoverPackage = app.applicationInfo != null
                        ? isCarryoverPackage(app.applicationInfo) : false;

                // An app id can have multiple packages and only some of them may be carryover.
                // Permission checks for a given uid always return the same thing, so unless this
                // package is carryover, any previous computation for this app id doesn't need to be
                // done again.
                if (!hasCarryoverPackage && mAppIdsPermInfo.get(appId) != null) continue;

                final int permissions = getNetdPermissionMask(uid);
                final AppIdNetdPermissionInfo permInfo =
                        new AppIdNetdPermissionInfo(permissions, hasCarryoverPackage);
                mAppIdsPermInfo.put(appId, permInfo);
            }
        }

        final SparseArray<ArraySet<String>> systemPermission =
                SystemConfig.getInstance().getSystemPermissions();
        for (int i = 0; i < systemPermission.size(); i++) {
            ArraySet<String> perms = systemPermission.valueAt(i);
            int appId = systemPermission.keyAt(i);
            int netdPermission = PERMISSION_NONE;
            // Get the uids of native services that have UPDATE_DEVICE_STATS or INTERNET permission.
            if (perms != null) {
                netdPermission |= perms.contains(UPDATE_DEVICE_STATS)
                        ? PERMISSION_UPDATE_DEVICE_STATS : 0;
                netdPermission |= perms.contains(INTERNET) ? PERMISSION_INTERNET : 0;
            }
            final AppIdNetdPermissionInfo permInfo = mAppIdsPermInfo.get(appId);
            mAppIdsPermInfo.put(appId, permInfo != null
                    ? permInfo.plusNetdPermissions(netdPermission)
                    : new AppIdNetdPermissionInfo(netdPermission));
        }
        log("Users: " + mUsers.size() + ", Uids: " + mAppIdsPermInfo.size());
        update(mUsers, mAppIdsPermInfo, true);
        sendPackagePermissionsToNetd(mAppIdsPermInfo);
    }

    @VisibleForTesting
    static boolean isVendorApp(@NonNull ApplicationInfo appInfo) {
        return appInfo.isVendor() || appInfo.isOem() || appInfo.isProduct();
    }

    @VisibleForTesting
    boolean hasPermission(@NonNull final String permission, final int uid) {
        return mDeps.uidPermission(permission, uid) == PackageManager.PERMISSION_GRANTED;
    }

    @VisibleForTesting
    // TODO : remove this check in the future(b/162295056). All apps should just request the
    // appropriate permission for their use case since android Q.
    boolean isCarryoverPackage(@NonNull final ApplicationInfo appInfo) {
        return (appInfo.targetSdkVersion < VERSION_Q && isVendorApp(appInfo))
                // Backward compatibility for b/114245686, on devices that launched before Q daemons
                // and apps running as the system UID are exempted from this check.
                || (UserHandle.getAppId(appInfo.uid) == SYSTEM_UID
                        && mDeps.getDeviceFirstSdkInt() < VERSION_Q);
    }

    @VisibleForTesting
    boolean hasRestrictedNetworkPermission(final int uid) {
        // Only NETWORK_STACK_UID can hold MAINLINE_NETWORK_STACK, so it's enough to check uid only.
        return (uid == NETWORK_STACK_UID)
                // Only InProcessNetworkStack(SYSTEM_UID) or system service can hold NETWORK_STACK.
                || ((uid == SYSTEM_UID) && hasPermission(NETWORK_STACK, uid))
                || hasPermission(CONNECTIVITY_USE_RESTRICTED_NETWORKS, uid);
    }

    /** Returns whether the given uid has using background network permission. */
    public synchronized boolean hasUseBackgroundNetworksPermission(final int uid) {
        final AppIdNetdPermissionInfo permInfo = mAppIdsPermInfo.get(UserHandle.getAppId(uid));
        if (null == permInfo) return false;

        return permInfo.hasNetdPermissions(PERMISSION_NETWORK)
                || permInfo.hasNetdPermissions(PERMISSION_SYSTEM)
                || permInfo.hasCarryoverPackage();
    }

    private int[] toIntArray(Collection<Integer> list) {
        int[] array = new int[list.size()];
        int i = 0;
        for (Integer item : list) {
            array[i++] = item;
        }
        return array;
    }

    // TODO: Migrate this method into sendPackagePermissionsToNetd(). Basically, they are doing a
    // similar thing but update the different permissions to different module.
    private void update(@NonNull final Set<Integer> users,
            @NonNull final SparseArray<AppIdNetdPermissionInfo> appIdsPermInfo, final boolean add) {
        final List<Integer> network = new ArrayList<>();
        final List<Integer> system = new ArrayList<>();
        for (int i = 0; i < appIdsPermInfo.size(); i++) {
            final int appId = appIdsPermInfo.keyAt(i);
            final AppIdNetdPermissionInfo permInfo = appIdsPermInfo.valueAt(i);
            for (int user : users) {
                // Choose the highest permission first.
                if (permInfo.hasNetdPermissions(PERMISSION_SYSTEM)
                        || permInfo.hasCarryoverPackage()) {
                    system.add(UserHandle.getUid(user, appId));
                } else if (permInfo.hasNetdPermissions(PERMISSION_NETWORK)) {
                    network.add(UserHandle.getUid(user, appId));
                }
            }
        }
        try {
            if (add) {
                mNetd.networkSetPermissionForUser(PERMISSION_NETWORK, convertToIntArray(network));
                mNetd.networkSetPermissionForUser(PERMISSION_SYSTEM, convertToIntArray(system));
            } else {
                mNetd.networkClearPermissionForUser(convertToIntArray(network));
                mNetd.networkClearPermissionForUser(convertToIntArray(system));
            }
        } catch (RemoteException e) {
            loge("Exception when updating permissions: " + e);
        }
    }

    /**
     * Called when a user is added. See {link #ACTION_USER_ADDED}.
     *
     * @param user The integer userHandle of the added user. See {@link #EXTRA_USER_HANDLE}.
     *
     * @hide
     */
    public synchronized void onUserAdded(int user) {
        if (user < 0) {
            loge("Invalid user in onUserAdded: " + user);
            return;
        }
        mUsers.add(user);

        Set<Integer> users = new HashSet<>();
        users.add(user);
        update(users, mAppIdsPermInfo, true/* add */);
    }

    /**
     * Called when an user is removed. See {link #ACTION_USER_REMOVED}.
     *
     * @param user The integer userHandle of the removed user. See {@link #EXTRA_USER_HANDLE}.
     *
     * @hide
     */
    public synchronized void onUserRemoved(int user) {
        if (user < 0) {
            loge("Invalid user in onUserRemoved: " + user);
            return;
        }
        mUsers.remove(user);

        Set<Integer> users = new HashSet<>();
        users.add(user);
        update(users, mAppIdsPermInfo, false/* add */);
    }

    /** Check all uids permission and update app id permission to netd if need */
    private void updateAppIdPermissionsToNetdIfNeeded(final int appId, final int[] uids,
            final boolean newCarryoverPackage) {
        final AppIdNetdPermissionInfo permInfo = mAppIdsPermInfo.get(appId);
        // Combine carryover package status with previous value.
        final boolean uidHasCarryoverPkg =
                newCarryoverPackage | (permInfo == null ? false : permInfo.hasCarryoverPackage());
        int permissions = PERMISSION_NONE;
        for (int uid : uids) {
            permissions |= getNetdPermissionMask(uid);
        }
        final AppIdNetdPermissionInfo newPermInfo =
                new AppIdNetdPermissionInfo(permissions, uidHasCarryoverPkg);

        if (newPermInfo.equals(permInfo)) return;

        mAppIdsPermInfo.put(appId, newPermInfo);
        final SparseArray<AppIdNetdPermissionInfo> appIdsPermInfo = new SparseArray<>();
        appIdsPermInfo.put(appId, newPermInfo);
        update(mUsers, appIdsPermInfo, true/* add */);
        sendPackagePermissionsToNetd(appIdsPermInfo);
    }

    private int[] getUids(final int appId) {
        final List<Integer> uids = new ArrayList<>();
        for (int userId : mUsers) {
            uids.add(UserHandle.getUid(userId, appId));
        }
        return convertToIntArray(uids);
    }

    /**
     * Called when a package is added.
     *
     * @param packageName The name of the new package.
     * @param appId The app id (base uid) of the new package.
     *
     * @hide
     */
    @Override
    public synchronized void onPackageAdded(@NonNull final String packageName, final int appId) {
        final int[] uids = getUids(appId);
        final PackageInfo pkgInfo = getPackageInfo(packageName);
        final boolean carryoverPackage = (pkgInfo != null && pkgInfo.applicationInfo != null)
                ? isCarryoverPackage(pkgInfo.applicationInfo) : false;

        updateAppIdPermissionsToNetdIfNeeded(appId, uids, carryoverPackage);

        // If the newly-installed package falls within some VPN's uid range, update Netd with it.
        // This needs to happen after the mAppIdsPermInfo update above, since removeBypassingUids()
        // depends on mAppIdsPermInfo to check if the package can bypass VPN.
        for (Map.Entry<String, Set<UidRange>> vpn : mVpnUidRanges.entrySet()) {
            for (int uid : uids) {
                if (UidRange.containsUid(vpn.getValue(), uid)) {
                    final Set<Integer> changedUids = new HashSet<>();
                    changedUids.add(uid);
                    removeBypassingUids(changedUids, /* vpnAppUid */ -1);
                    updateVpnUids(vpn.getKey(), changedUids, true);
                }
            }
        }
        mAllApps.add(appId);
    }

    /**
     * Called when a package is removed.
     *
     * @param packageName The name of the removed package or null.
     * @param appId containing the integer app id (base uid) previously assigned to the package.
     *
     * @hide
     */
    @Override
    public synchronized void onPackageRemoved(@NonNull final String packageName, final int appId) {
        final int[] uids = getUids(appId);

        // If the newly-removed package falls within some VPN's uid range, update Netd with it.
        // This needs to happen before the mAppIdsPermInfo update below, since removeBypassingUids()
        // depends on mAppIdsPermInfo to check if the package can bypass VPN.
        for (Map.Entry<String, Set<UidRange>> vpn : mVpnUidRanges.entrySet()) {
            for (int uid : uids) {
                if (UidRange.containsUid(vpn.getValue(), uid)) {
                    final Set<Integer> changedUids = new HashSet<>();
                    changedUids.add(uid);
                    removeBypassingUids(changedUids, /* vpnAppUid */ -1);
                    updateVpnUids(vpn.getKey(), changedUids, false);
                }
            }
        }

        // If all packages has been removed from all users on the device, clear it from mAllApps and
        // mAppIdsPermInfo.
        if (mPackageManager.getNameForUid(appId) == null) {
            mAllApps.remove(appId);
            mAppIdsPermInfo.remove(appId);

            final SparseArray<AppIdNetdPermissionInfo> appIdsPermInfo = new SparseArray<>();
            // Doesn't matter which permission is picked up here because the permission is only used
            // to recognize which uid should be cleared.
            appIdsPermInfo.put(appId,
                    new AppIdNetdPermissionInfo(PERMISSION_NETWORK | PERMISSION_SYSTEM));
            update(mUsers, appIdsPermInfo, false/* add */);
            sendPackagePermissionsForAppId(appId,
                    new AppIdNetdPermissionInfo(PERMISSION_UNINSTALLED));
            return;
        }

        // Pass not carryover package to keep current status.
        // TODO: Recheck whether there are any carryover packages left in this uid.
        updateAppIdPermissionsToNetdIfNeeded(appId, uids, false/* newCarryoverPackage */);
    }

    /**
     * Called when a package is changed.
     *
     * @param packageName The name of the changed package.
     * @param appId The app id (base uid) of the changed package.
     *
     * @hide
     */
    @Override
    public synchronized void onPackageChanged(@NonNull final String packageName, final int appId) {
        // No package added or removed, so the carryover package status doesn't change.
        updateAppIdPermissionsToNetdIfNeeded(
                appId, getUids(appId), false/* newCarryoverPackage */);
    }

    private int getNetdPermissionMask(final int uid) {
        int permissions = PERMISSION_NONE;
        if (hasPermission(CHANGE_NETWORK_STATE, uid)) {
            permissions |= PERMISSION_NETWORK;
        }
        if (hasRestrictedNetworkPermission(uid)) {
            permissions |= PERMISSION_SYSTEM;
        }
        if (hasPermission(INTERNET, uid)) {
            permissions |= PERMISSION_INTERNET;
        }
        if (hasPermission(UPDATE_DEVICE_STATS, uid)) {
            permissions |= PERMISSION_UPDATE_DEVICE_STATS;
        }
        return permissions;
    }

    private PackageInfo getPackageInfo(String packageName) {
        try {
            PackageInfo app = mPackageManager.getPackageInfo(packageName, GET_PERMISSIONS
                    | MATCH_ANY_USER);
            return app;
        } catch (NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Called when a new set of UID ranges are added to an active VPN network
     *
     * @param iface The active VPN network's interface name
     * @param rangesToAdd The new UID ranges to be added to the network
     * @param vpnAppUid The uid of the VPN app
     */
    public synchronized void onVpnUidRangesAdded(@NonNull String iface, Set<UidRange> rangesToAdd,
            int vpnAppUid) {
        // Calculate the list of new app uids under the VPN due to the new UID ranges and update
        // Netd about them. Because mAllApps only contains appIds instead of uids, the result might
        // be an overestimation if an app is not installed on the user on which the VPN is running,
        // but that's safe.
        final Set<Integer> changedUids = intersectUids(rangesToAdd, mAllApps);
        removeBypassingUids(changedUids, vpnAppUid);
        updateVpnUids(iface, changedUids, true);
        if (mVpnUidRanges.containsKey(iface)) {
            mVpnUidRanges.get(iface).addAll(rangesToAdd);
        } else {
            mVpnUidRanges.put(iface, new HashSet<UidRange>(rangesToAdd));
        }
    }

    /**
     * Called when a set of UID ranges are removed from an active VPN network
     *
     * @param iface The VPN network's interface name
     * @param rangesToRemove Existing UID ranges to be removed from the VPN network
     * @param vpnAppUid The uid of the VPN app
     */
    public synchronized void onVpnUidRangesRemoved(@NonNull String iface,
            Set<UidRange> rangesToRemove, int vpnAppUid) {
        // Calculate the list of app uids that are no longer under the VPN due to the removed UID
        // ranges and update Netd about them.
        final Set<Integer> changedUids = intersectUids(rangesToRemove, mAllApps);
        removeBypassingUids(changedUids, vpnAppUid);
        updateVpnUids(iface, changedUids, false);
        Set<UidRange> existingRanges = mVpnUidRanges.getOrDefault(iface, null);
        if (existingRanges == null) {
            loge("Attempt to remove unknown vpn uid Range iface = " + iface);
            return;
        }
        existingRanges.removeAll(rangesToRemove);
        if (existingRanges.size() == 0) {
            mVpnUidRanges.remove(iface);
        }
    }

    /**
     * Compute the intersection of a set of UidRanges and appIds. Returns a set of uids
     * that satisfies:
     *   1. falls into one of the UidRange
     *   2. matches one of the appIds
     */
    private Set<Integer> intersectUids(Set<UidRange> ranges, Set<Integer> appIds) {
        Set<Integer> result = new HashSet<>();
        for (UidRange range : ranges) {
            for (int userId = range.getStartUser(); userId <= range.getEndUser(); userId++) {
                for (int appId : appIds) {
                    final int uid = UserHandle.getUid(userId, appId);
                    if (range.contains(uid)) {
                        result.add(uid);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Remove all apps which can elect to bypass the VPN from the list of uids
     *
     * An app can elect to bypass the VPN if it hold SYSTEM permission, or if its the active VPN
     * app itself.
     *
     * @param uids The list of uids to operate on
     * @param vpnAppUid The uid of the VPN app
     */
    private void removeBypassingUids(Set<Integer> uids, int vpnAppUid) {
        uids.remove(vpnAppUid);
        uids.removeIf(uid -> {
            final AppIdNetdPermissionInfo permInfo = mAppIdsPermInfo.get(UserHandle.getAppId(uid));
            if (null == permInfo) return false;
            return permInfo.hasNetdPermissions(PERMISSION_SYSTEM) || permInfo.hasCarryoverPackage();
        });
    }

    /**
     * Update netd about the list of uids that are under an active VPN connection which they cannot
     * bypass.
     *
     * This is to instruct netd to set up appropriate filtering rules for these uids, such that they
     * can only receive ingress packets from the VPN's tunnel interface (and loopback).
     *
     * @param iface the interface name of the active VPN connection
     * @param uids The list of uids to operate on
     * @param add {@code true} if the uids are to be added to the interface, {@code false} if they
     *        are to be removed from the interface.
     */
    private void updateVpnUids(String iface, Set<Integer> uids, boolean add) {
        if (uids.size() == 0) {
            return;
        }
        try {
            if (add) {
                mNetd.firewallAddUidInterfaceRules(iface, toIntArray(uids));
            } else {
                mNetd.firewallRemoveUidInterfaceRules(toIntArray(uids));
            }
        } catch (ServiceSpecificException e) {
            // Silently ignore exception when device does not support eBPF, otherwise just log
            // the exception and do not crash
            if (e.errorCode != OsConstants.EOPNOTSUPP) {
                loge("Exception when updating permissions: ", e);
            }
        } catch (RemoteException e) {
            loge("Exception when updating permissions: ", e);
        }
    }

    /**
     * Send the updated traffic controller permission information to netd.
     *
     * @param appId the app id (base uid) of the package installed/uninstalled/changed.
     * @param permissionInfo the permission info of given app id.
     *
     * @hide
     */
    @VisibleForTesting
    void sendPackagePermissionsForAppId(int appId, AppIdNetdPermissionInfo permissionInfo) {
        final SparseArray<AppIdNetdPermissionInfo> appIdsPermInfo = new SparseArray<>();
        appIdsPermInfo.put(appId, permissionInfo);
        sendPackagePermissionsToNetd(appIdsPermInfo);
    }

    /**
     * Called by packageManagerService to send IPC to netd. Grant or revoke the INTERNET
     * and/or UPDATE_DEVICE_STATS permission of the app ids in array.
     *
     * @param appIdsPermInfo permission info array generated from each app id. If the app id
     *                       permission is PERMISSION_NONE or PERMISSION_UNINSTALLED, revoke all
     *                       permissions of that app id.
     *
     * @hide
     */
    @VisibleForTesting
    void sendPackagePermissionsToNetd(final SparseArray<AppIdNetdPermissionInfo> appIdsPermInfo) {
        if (mNetd == null) {
            Log.e(TAG, "Failed to get the netd service");
            return;
        }
        ArrayList<Integer> allPermissionAppIds = new ArrayList<>();
        ArrayList<Integer> internetPermissionAppIds = new ArrayList<>();
        ArrayList<Integer> updateStatsPermissionAppIds = new ArrayList<>();
        ArrayList<Integer> noPermissionAppIds = new ArrayList<>();
        ArrayList<Integer> uninstalledAppIds = new ArrayList<>();
        for (int i = 0; i < appIdsPermInfo.size(); i++) {
            final int uid = appIdsPermInfo.keyAt(i);
            final AppIdNetdPermissionInfo permInfo = appIdsPermInfo.valueAt(i);
            if (permInfo.hasNetdPermissions(
                    PERMISSION_INTERNET | PERMISSION_UPDATE_DEVICE_STATS)) {
                allPermissionAppIds.add(uid);
            } else if (permInfo.hasNetdPermissions(PERMISSION_INTERNET)) {
                internetPermissionAppIds.add(uid);
            } else if (permInfo.hasNetdPermissions(PERMISSION_UPDATE_DEVICE_STATS)) {
                updateStatsPermissionAppIds.add(uid);
            } else if (permInfo.isAllPackagesUninstalled()) {
                uninstalledAppIds.add(uid);
            } else {
                noPermissionAppIds.add(uid);
            }
        }
        try {
            // TODO: add a lock inside netd to protect IPC trafficSetNetPermForUids()
            if (allPermissionAppIds.size() != 0) {
                mNetd.trafficSetNetPermForUids(
                        PERMISSION_INTERNET | PERMISSION_UPDATE_DEVICE_STATS,
                        convertToIntArray(allPermissionAppIds));
            }
            if (internetPermissionAppIds.size() != 0) {
                mNetd.trafficSetNetPermForUids(PERMISSION_INTERNET,
                        convertToIntArray(internetPermissionAppIds));
            }
            if (updateStatsPermissionAppIds.size() != 0) {
                mNetd.trafficSetNetPermForUids(PERMISSION_UPDATE_DEVICE_STATS,
                        convertToIntArray(updateStatsPermissionAppIds));
            }
            if (noPermissionAppIds.size() != 0) {
                mNetd.trafficSetNetPermForUids(PERMISSION_NONE,
                        convertToIntArray(noPermissionAppIds));
            }
            if (uninstalledAppIds.size() != 0) {
                mNetd.trafficSetNetPermForUids(PERMISSION_UNINSTALLED,
                        convertToIntArray(uninstalledAppIds));
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Pass appId list of special permission failed." + e);
        }
    }

    /** Should only be used by unit tests */
    @VisibleForTesting
    public Set<UidRange> getVpnUidRanges(String iface) {
        return mVpnUidRanges.get(iface);
    }

    /** Dump info to dumpsys */
    public void dump(IndentingPrintWriter pw) {
        pw.println("Interface filtering rules:");
        pw.increaseIndent();
        for (Map.Entry<String, Set<UidRange>> vpn : mVpnUidRanges.entrySet()) {
            pw.println("Interface: " + vpn.getKey());
            pw.println("UIDs: " + vpn.getValue().toString());
            pw.println();
        }
        pw.decreaseIndent();
    }

    private static void log(String s) {
        if (DBG) {
            Log.d(TAG, s);
        }
    }

    private static void loge(String s) {
        Log.e(TAG, s);
    }

    private static void loge(String s, Throwable e) {
        Log.e(TAG, s, e);
    }
}
