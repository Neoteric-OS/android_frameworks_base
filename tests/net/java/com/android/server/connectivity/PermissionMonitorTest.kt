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
 * limitations under the License
 */

package com.android.server.connectivity

import android.Manifest.permission.CHANGE_NETWORK_STATE
import android.Manifest.permission.CHANGE_WIFI_STATE
import android.Manifest.permission.CONNECTIVITY_INTERNAL
import android.Manifest.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS
import android.Manifest.permission.INTERNET
import android.Manifest.permission.NETWORK_STACK
import android.Manifest.permission.UPDATE_DEVICE_STATS
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.ApplicationInfo.PRIVATE_FLAG_OEM
import android.content.pm.ApplicationInfo.PRIVATE_FLAG_PRODUCT
import android.content.pm.ApplicationInfo.PRIVATE_FLAG_VENDOR
import android.content.pm.PackageInfo
import android.content.pm.PackageList
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_PERMISSIONS
import android.content.pm.PackageManager.MATCH_ANY_USER
import android.content.pm.PackageManagerInternal
import android.content.pm.UserInfo
import android.net.INetd
import android.net.INetd.PERMISSION_INTERNET
import android.net.INetd.PERMISSION_NONE
import android.net.INetd.PERMISSION_SYSTEM
import android.net.INetd.PERMISSION_UNINSTALLED
import android.net.INetd.PERMISSION_UPDATE_DEVICE_STATS
import android.net.NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK
import android.net.UidRange
import android.os.Process.SYSTEM_UID
import android.os.UserHandle
import android.os.UserManager
import android.util.SparseArray
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.server.LocalServices
import com.android.server.connectivity.PermissionMonitor.NETWORK
import com.android.server.connectivity.PermissionMonitor.SYSTEM
import com.android.server.connectivity.PermissionMonitor.UidNetdPermissionInfo
import junit.framework.Assert.fail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.AdditionalMatchers.aryEq
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import java.util.HashMap
import android.os.Build.VERSION_CODES.P as VERSION_P
import android.os.Build.VERSION_CODES.Q as VERSION_Q

@RunWith(AndroidJUnit4::class)
@SmallTest
class PermissionMonitorTest {
    private val MOCK_USER1 = 0
    private val MOCK_USER2 = 1
    private val MOCK_UID1 = 10001
    private val MOCK_UID2 = 10086
    private val SYSTEM_UID1 = 1000
    private val SYSTEM_UID2 = 1008
    private val VPN_UID = 10002
    private val MOCK_PACKAGE1 = "appName1"
    private val MOCK_PACKAGE2 = "appName2"
    private val SYSTEM_PACKAGE1 = "sysName1"
    private val SYSTEM_PACKAGE2 = "sysName2"
    private val PARTITION_SYSTEM = "system"
    private val PARTITION_OEM = "oem"
    private val PARTITION_PRODUCT = "product"
    private val PARTITION_VENDOR = "vendor"

    private val context = mock(Context::class.java)
    private val packageManager = mock(PackageManager::class.java)
    private val netdService = mock(INetd::class.java)
    private val mockPmi = mock(PackageManagerInternal::class.java)
    private val userManager = mock(UserManager::class.java)
    private val deps = mock(PermissionMonitor.Dependencies::class.java)

    // lateinit for these classes under test, as they should be reset to a different instance for
    // every test but should always be initialized before use (or the test should crash).
    private lateinit var permissionMonitor: PermissionMonitor

    @Before
    @Throws(Exception::class)
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        doReturn(packageManager).`when`(context).getPackageManager()
        doReturn(userManager).`when`(context).getSystemService(eq(Context.USER_SERVICE))
        doReturn(listOf(UserInfo(MOCK_USER1, "", 0), UserInfo(MOCK_USER2, "", 0)))
                .`when`(userManager).getUsers(eq(true))
        doReturn(PackageManager.PERMISSION_DENIED).`when`(deps).uidPermission(anyString(), anyInt())

        permissionMonitor = spy(PermissionMonitor(context, netdService, deps))

        LocalServices.removeServiceForTest(PackageManagerInternal::class.java)
        LocalServices.addService(PackageManagerInternal::class.java, mockPmi)
        doReturn(PackageList(listOf(), /* observer */ null))
                .`when`(mockPmi).getPackageList(any())
        doReturn(/* empty app list */ null).`when`(packageManager).getInstalledPackages(anyInt())
        permissionMonitor.startMonitoring()
        verify(mockPmi).getPackageList(permissionMonitor)
    }

    private fun packageInfoWithPartition(partition: String) = PackageInfo().apply {
        applicationInfo = ApplicationInfo()
        applicationInfo.privateFlags = when (partition) {
            PARTITION_OEM -> PRIVATE_FLAG_OEM
            PARTITION_PRODUCT -> PRIVATE_FLAG_PRODUCT
            PARTITION_VENDOR -> PRIVATE_FLAG_VENDOR
            else -> 0
        }
    }

    private fun buildPackageInfo(
        partition: String = PARTITION_SYSTEM,
        uid: Int,
        userId: Int = MOCK_USER1
    ) = packageInfoWithPartition(partition).apply {
        applicationInfo.uid = UserHandle.getUid(userId, UserHandle.getAppId(uid))
    }

    /** This will REMOVE all previously set permissions from given uid. */
    private fun removeAllPermissions(uid: Int) =
        doReturn(PackageManager.PERMISSION_DENIED).`when`(deps).uidPermission(anyString(), eq(uid))

    /** Set up mocks so that given UID has the requested permissions. */
    private fun addPermissions(uid: Int, vararg permissions: String) {
        for (permission in permissions) {
            doReturn(PackageManager.PERMISSION_GRANTED)
                    .`when`(deps).uidPermission(eq(permission), eq(uid))
        }
    }

    @Test
    fun testHasPermission() {
        assertFalse(permissionMonitor.hasPermission(CHANGE_NETWORK_STATE, MOCK_UID1))
        assertFalse(permissionMonitor.hasPermission(NETWORK_STACK, MOCK_UID1))
        assertFalse(permissionMonitor.hasPermission(
                CONNECTIVITY_USE_RESTRICTED_NETWORKS, MOCK_UID1))
        assertFalse(permissionMonitor.hasPermission(CONNECTIVITY_INTERNAL, MOCK_UID1))

        addPermissions(MOCK_UID1, CHANGE_NETWORK_STATE, NETWORK_STACK)
        assertTrue(permissionMonitor.hasPermission(CHANGE_NETWORK_STATE, MOCK_UID1))
        assertTrue(permissionMonitor.hasPermission(NETWORK_STACK, MOCK_UID1))
        assertFalse(permissionMonitor.hasPermission(
                CONNECTIVITY_USE_RESTRICTED_NETWORKS, MOCK_UID1))
        assertFalse(permissionMonitor.hasPermission(CONNECTIVITY_INTERNAL, MOCK_UID1))
        assertFalse(permissionMonitor.hasPermission(CHANGE_NETWORK_STATE, MOCK_UID2))
        assertFalse(permissionMonitor.hasPermission(NETWORK_STACK, MOCK_UID2))

        addPermissions(MOCK_UID2, CONNECTIVITY_USE_RESTRICTED_NETWORKS, CONNECTIVITY_INTERNAL)
        assertFalse(permissionMonitor.hasPermission(
                CONNECTIVITY_USE_RESTRICTED_NETWORKS, MOCK_UID1))
        assertFalse(permissionMonitor.hasPermission(CONNECTIVITY_INTERNAL, MOCK_UID1))
        assertTrue(permissionMonitor.hasPermission(
                CONNECTIVITY_USE_RESTRICTED_NETWORKS, MOCK_UID2))
        assertTrue(permissionMonitor.hasPermission(CONNECTIVITY_INTERNAL, MOCK_UID2))
    }

    @Test
    fun testIsVendorApp() {
        fun test(partition: String, result: Boolean) {
            val app = packageInfoWithPartition(partition)
            assertEquals(result, PermissionMonitor.isVendorApp(app.applicationInfo))
        }
        test(PARTITION_SYSTEM, false)
        test(PARTITION_OEM, true)
        test(PARTITION_PRODUCT, true)
        test(PARTITION_VENDOR, true)
    }

    @Test
    fun testHasRestrictedNetworkPermission() {
        /**
         * Remove all permissions from the uid then setup permissions to uid for checking restricted
         * network permission.
         */
        fun test(result: Boolean, vararg permissions: String) {
            removeAllPermissions(MOCK_UID1)
            addPermissions(MOCK_UID1, *permissions)
            assertEquals(result, permissionMonitor.hasRestrictedNetworkPermission(MOCK_UID1))
        }
        assertFalse(permissionMonitor.hasRestrictedNetworkPermission(MOCK_UID1))
        test(false, CHANGE_NETWORK_STATE)
        test(true, NETWORK_STACK)
        test(false, CONNECTIVITY_INTERNAL)
        test(true, CONNECTIVITY_USE_RESTRICTED_NETWORKS)
        test(false, CHANGE_WIFI_STATE)
        test(true, PERMISSION_MAINLINE_NETWORK_STACK)

        assertFalse(permissionMonitor.hasRestrictedNetworkPermission(MOCK_UID2))
        assertFalse(permissionMonitor.hasRestrictedNetworkPermission(SYSTEM_UID))
    }

    @Test
    fun testIsCarryoverPackage() {
        fun test(partition: String, targetSdkVersion: Int, uid: Int, result: Boolean) {
            val packageInfo = buildPackageInfo(partition, uid).apply {
                applicationInfo.targetSdkVersion = targetSdkVersion
            }
            assertEquals(result, permissionMonitor.isCarryoverPackage(packageInfo.applicationInfo))
        }
        doReturn(VERSION_P).`when`(deps).deviceFirstSdkInt
        test(PARTITION_SYSTEM, VERSION_P, SYSTEM_UID, true)
        test(PARTITION_VENDOR, VERSION_P, SYSTEM_UID, true)
        test(PARTITION_SYSTEM, VERSION_P, MOCK_UID1, false)
        test(PARTITION_VENDOR, VERSION_P, MOCK_UID1, true)
        test(PARTITION_SYSTEM, VERSION_Q, SYSTEM_UID, true)
        test(PARTITION_VENDOR, VERSION_Q, SYSTEM_UID, true)
        test(PARTITION_SYSTEM, VERSION_Q, MOCK_UID1, false)
        test(PARTITION_VENDOR, VERSION_Q, MOCK_UID1, false)

        doReturn(VERSION_Q).`when`(deps).deviceFirstSdkInt
        test(PARTITION_SYSTEM, VERSION_P, SYSTEM_UID, false)
        test(PARTITION_VENDOR, VERSION_P, SYSTEM_UID, true)
        test(PARTITION_SYSTEM, VERSION_P, MOCK_UID1, false)
        test(PARTITION_VENDOR, VERSION_P, MOCK_UID1, true)
        test(PARTITION_SYSTEM, VERSION_Q, SYSTEM_UID, false)
        test(PARTITION_VENDOR, VERSION_Q, SYSTEM_UID, false)
        test(PARTITION_SYSTEM, VERSION_Q, MOCK_UID1, false)
        test(PARTITION_VENDOR, VERSION_Q, MOCK_UID1, false)

        test(PARTITION_OEM, VERSION_Q, SYSTEM_UID, false)
        test(PARTITION_PRODUCT, VERSION_Q, SYSTEM_UID, false)
        test(PARTITION_OEM, VERSION_Q, MOCK_UID1, false)
        test(PARTITION_PRODUCT, VERSION_Q, MOCK_UID1, false)
    }

    private fun assertBackgroundPermission(
        hasPermission: Boolean,
        name: String,
        uid: Int,
        vararg permissions: String
    ) {
        doReturn(buildPackageInfo(uid = uid))
                .`when`(packageManager).getPackageInfo(eq(name), anyInt())
        addPermissions(uid, *permissions)
        permissionMonitor.onPackageAdded(name, uid)
        assertEquals(hasPermission, permissionMonitor.hasUseBackgroundNetworksPermission(uid))
    }

    @Test
    fun testHasUseBackgroundNetworksPermission() {
        assertFalse(permissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID1))
        assertBackgroundPermission(false, "mock1", MOCK_UID1)
        assertBackgroundPermission(false, "mock2", MOCK_UID1, CONNECTIVITY_INTERNAL)
        assertBackgroundPermission(true, "mock3", MOCK_UID1, NETWORK_STACK)

        assertFalse(permissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID2))
        assertBackgroundPermission(false, "mock4", MOCK_UID2)
        assertBackgroundPermission(true, "mock5", MOCK_UID2,
                CONNECTIVITY_USE_RESTRICTED_NETWORKS)

        doReturn(VERSION_Q).`when`(deps).deviceFirstSdkInt
        assertFalse(permissionMonitor.hasUseBackgroundNetworksPermission(SYSTEM_UID))
        assertBackgroundPermission(false, "system1", SYSTEM_UID)
        assertBackgroundPermission(true, "system2", SYSTEM_UID, CHANGE_NETWORK_STATE)
        doReturn(VERSION_P).`when`(deps).deviceFirstSdkInt
        removeAllPermissions(SYSTEM_UID)
        assertBackgroundPermission(true, "system3", SYSTEM_UID)
    }

    private class NetdMonitor internal constructor(mockNetd: INetd) {
        private val mApps: HashMap<Int, Boolean> = HashMap()

        init {
            // Add hook to verify and track result of setPermission.
            doAnswer { invocation ->
                val args = invocation.arguments
                val isSystem = args[0] == PERMISSION_SYSTEM
                for (uid in args[1] as IntArray) {
                    // TODO: Currently, permission monitor will send duplicate commands for each uid
                    // corresponding to each user. Need to fix that and uncomment the test below.
                    // if (mApps.containsKey(uid) && mApps[uid] == isSystem) {
                    //     fail("uid " + uid + " is already set to " + isSystem);
                    // }
                    mApps[uid] = isSystem
                }
                null
            }.`when`(mockNetd).networkSetPermissionForUser(anyInt(), any(IntArray::class.java))

            // Add hook to verify and track result of clearPermission.
            doAnswer { invocation ->
                val args = invocation.arguments
                for (uid in args[0] as IntArray) {
                    // TODO: Currently, permission monitor will send duplicate commands for each uid
                    // corresponding to each user. Need to fix that and uncomment below test.
                    // if (!mApps.containsKey(uid)) {
                    //     fail("uid " + uid + " does not exist.");
                    // }
                    mApps.remove(uid)
                }
                null
            }.`when`(mockNetd).networkClearPermissionForUser(any(IntArray::class.java))
        }

        fun expectPermission(permission: Boolean, users: IntArray, vararg apps: Int) {
            for (user in users) {
                for (app in apps) {
                    val uid = UserHandle.getUid(user, app)
                    if (!mApps.containsKey(uid)) {
                        fail("uid $uid does not exist.")
                    }
                    if (mApps[uid] !== permission) {
                        fail("uid $uid has wrong permission: ${mApps[uid]} (expected $permission)")
                    }
                }
            }
        }

        fun expectNoPermission(users: IntArray, vararg apps: Int) {
            for (user in users) {
                for (app in apps) {
                    val uid = UserHandle.getUid(user, app)
                    if (mApps.containsKey(uid)) {
                        fail("uid $uid has listed permissions, expected none.")
                    }
                }
            }
        }
    }

    @Test
    fun testUserAndPackageAddRemove() {
        val mNetdMonitor = NetdMonitor(netdService)

        // MOCK_UID1: MOCK_PACKAGE1 only has network permission.
        // SYSTEM_UID: SYSTEM_PACKAGE1 has system permission.
        // SYSTEM_UID: SYSTEM_PACKAGE2 only has network permission.
        doReturn(SYSTEM).`when`(permissionMonitor).highestPermissionForUid(eq(SYSTEM),
                anyString(), anyInt())
        doReturn(SYSTEM).`when`(permissionMonitor).highestPermissionForUid(any(),
                eq(SYSTEM_PACKAGE1), anyInt())
        doReturn(NETWORK).`when`(permissionMonitor).highestPermissionForUid(any(),
                eq(SYSTEM_PACKAGE2), anyInt())
        doReturn(NETWORK).`when`(permissionMonitor).highestPermissionForUid(any(),
                eq(MOCK_PACKAGE1), anyInt())

        // Add SYSTEM_PACKAGE2, expect only have network permission.
        permissionMonitor.onUserAdded(MOCK_USER1)
        addPackageForUsers(intArrayOf(MOCK_USER1), SYSTEM_PACKAGE2, SYSTEM_UID)
        mNetdMonitor.expectPermission(NETWORK, intArrayOf(MOCK_USER1), SYSTEM_UID)

        // Add SYSTEM_PACKAGE1, expect permission escalate.
        addPackageForUsers(intArrayOf(MOCK_USER1), SYSTEM_PACKAGE1, SYSTEM_UID)
        mNetdMonitor.expectPermission(SYSTEM, intArrayOf(MOCK_USER1), SYSTEM_UID)
        permissionMonitor.onUserAdded(MOCK_USER2)
        mNetdMonitor.expectPermission(SYSTEM, intArrayOf(MOCK_USER1, MOCK_USER2), SYSTEM_UID)
        addPackageForUsers(intArrayOf(MOCK_USER1, MOCK_USER2), MOCK_PACKAGE1, MOCK_UID1)
        mNetdMonitor.expectPermission(SYSTEM, intArrayOf(MOCK_USER1, MOCK_USER2), SYSTEM_UID)
        mNetdMonitor.expectPermission(NETWORK, intArrayOf(MOCK_USER1, MOCK_USER2), MOCK_UID1)

        // Remove MOCK_UID1, expect no permission left for all user.
        permissionMonitor.onPackageRemoved(MOCK_PACKAGE1, MOCK_UID1)
        removePackageForUsers(intArrayOf(MOCK_USER1, MOCK_USER2), MOCK_PACKAGE1, MOCK_UID1)
        mNetdMonitor.expectNoPermission(intArrayOf(MOCK_USER1, MOCK_USER2), MOCK_UID1)

        // Remove SYSTEM_PACKAGE1, expect permission downgrade.
        doReturn(arrayOf(SYSTEM_PACKAGE2)).`when`(packageManager).getPackagesForUid(anyInt())
        removePackageForUsers(intArrayOf(MOCK_USER1, MOCK_USER2), SYSTEM_PACKAGE1, SYSTEM_UID)
        mNetdMonitor.expectPermission(NETWORK, intArrayOf(MOCK_USER1, MOCK_USER2), SYSTEM_UID)
        permissionMonitor.onUserRemoved(MOCK_USER1)
        mNetdMonitor.expectPermission(NETWORK, intArrayOf(MOCK_USER2), SYSTEM_UID)

        // Remove all packages, expect no permission left.
        doReturn(arrayOf<String>()).`when`(packageManager).getPackagesForUid(anyInt())
        removePackageForUsers(intArrayOf(MOCK_USER2), SYSTEM_PACKAGE2, SYSTEM_UID)
        mNetdMonitor.expectNoPermission(intArrayOf(MOCK_USER1, MOCK_USER2), SYSTEM_UID, MOCK_UID1)

        // Remove last user, expect no redundant clearPermission is invoked.
        permissionMonitor.onUserRemoved(MOCK_USER2)
        mNetdMonitor.expectNoPermission(intArrayOf(MOCK_USER1, MOCK_USER2), SYSTEM_UID, MOCK_UID1)
    }

    @Test
    fun testUidFilteringDuringVpnConnectDisconnectAndUidUpdates() {
        doReturn(listOf(buildPackageInfo(uid = SYSTEM_UID1),
                buildPackageInfo(uid = MOCK_UID1),
                buildPackageInfo(uid = MOCK_UID2),
                buildPackageInfo(uid = VPN_UID)))
                .`when`(packageManager).getInstalledPackages(eq(GET_PERMISSIONS or MATCH_ANY_USER))
        doReturn(buildPackageInfo(uid = MOCK_UID1))
                .`when`(packageManager).getPackageInfo(eq(MOCK_PACKAGE1), eq(GET_PERMISSIONS))

        addPermissions(SYSTEM_UID,
                CHANGE_NETWORK_STATE, NETWORK_STACK, CONNECTIVITY_USE_RESTRICTED_NETWORKS)
        permissionMonitor.startMonitoring()
        // Every app on user 0 except MOCK_UID2 are under VPN.
        val vpnRange1 = setOf(
                UidRange(0, MOCK_UID2 - 1),
                UidRange(MOCK_UID2 + 1, UserHandle.PER_USER_RANGE - 1))
        val vpnRange2 = setOf(UidRange(MOCK_UID2, MOCK_UID2))

        // When VPN is connected, expect a rule to be set up for user app MOCK_UID1
        permissionMonitor.onVpnUidRangesAdded("tun0", vpnRange1, VPN_UID)
        verify(netdService).firewallAddUidInterfaceRules(eq("tun0"), aryEq(intArrayOf(MOCK_UID1)))
        reset(netdService)

        // When MOCK_UID1 package is uninstalled and reinstalled, expect Netd to be updated
        permissionMonitor.onPackageRemoved(MOCK_PACKAGE1, UserHandle.getUid(MOCK_USER1, MOCK_UID1))
        verify(netdService).firewallRemoveUidInterfaceRules(aryEq(intArrayOf(MOCK_UID1)))
        permissionMonitor.onPackageAdded(MOCK_PACKAGE1, UserHandle.getUid(MOCK_USER1, MOCK_UID1))
        verify(netdService).firewallAddUidInterfaceRules(eq("tun0"), aryEq(intArrayOf(MOCK_UID1)))
        reset(netdService)

        // During VPN uid update (vpnRange1 -> vpnRange2), ConnectivityService first deletes the
        // old UID rules then adds the new ones. Expect netd to be updated
        permissionMonitor.onVpnUidRangesRemoved("tun0", vpnRange1, VPN_UID)
        verify(netdService).firewallRemoveUidInterfaceRules(aryEq(intArrayOf(MOCK_UID1)))
        permissionMonitor.onVpnUidRangesAdded("tun0", vpnRange2, VPN_UID)
        verify(netdService).firewallAddUidInterfaceRules(eq("tun0"), aryEq(intArrayOf(MOCK_UID2)))
        reset(netdService)

        // When VPN is disconnected, expect rules to be torn down
        permissionMonitor.onVpnUidRangesRemoved("tun0", vpnRange2, VPN_UID)
        verify(netdService).firewallRemoveUidInterfaceRules(aryEq(intArrayOf(MOCK_UID2)))
        assertNull(permissionMonitor.getVpnUidRanges("tun0"))
    }

    @Test
    fun testUidFilteringDuringPackageInstallAndUninstall() {
        doReturn(listOf(buildPackageInfo(uid = SYSTEM_UID1), buildPackageInfo(uid = VPN_UID)))
                .`when`(packageManager).getInstalledPackages(eq(GET_PERMISSIONS or MATCH_ANY_USER))
        doReturn(buildPackageInfo(uid = MOCK_UID1))
                .`when`(packageManager).getPackageInfo(eq(MOCK_PACKAGE1), eq(GET_PERMISSIONS))
        permissionMonitor.startMonitoring()
        val vpnRange = setOf(UidRange.createForUser(MOCK_USER1))
        permissionMonitor.onVpnUidRangesAdded("tun0", vpnRange, VPN_UID)

        // Newly-installed package should have uid rules added
        permissionMonitor.onPackageAdded(MOCK_PACKAGE1, UserHandle.getUid(MOCK_USER1, MOCK_UID1))
        verify(netdService).firewallAddUidInterfaceRules(eq("tun0"), aryEq(intArrayOf(MOCK_UID1)))

        // Removed package should have its uid rules removed
        permissionMonitor.onPackageRemoved(MOCK_PACKAGE1, UserHandle.getUid(MOCK_USER1, MOCK_UID1))
        verify(netdService).firewallRemoveUidInterfaceRules(aryEq(intArrayOf(MOCK_UID1)))
    }

    // Normal package add/remove operations will trigger multiple intent for uids corresponding to
    // each user. To simulate generic package operations, the onPackageAdded/Removed will need to be
    // called multiple times with the uid corresponding to each user.
    private fun addPackageForUsers(users: IntArray, packageName: String, uid: Int) {
        for (user in users) {
            permissionMonitor.onPackageAdded(packageName, UserHandle.getUid(user, uid))
        }
    }

    private fun removePackageForUsers(users: IntArray, packageName: String, uid: Int) {
        for (user in users) {
            permissionMonitor.onPackageRemoved(packageName, UserHandle.getUid(user, uid))
        }
    }

    private class NetdServiceMonitor internal constructor(mockNetdService: INetd) {
        private val mPermissions: HashMap<Int, Int> = HashMap()

        init {
            // Add hook to verify and track result of setPermission.
            doAnswer { invocation ->
                val args = invocation.arguments
                val permission = args[0] as Int
                for (uid in args[1] as IntArray) {
                    mPermissions[uid] = permission
                }
                null
            }.`when`(mockNetdService).trafficSetNetPermForUids(anyInt(), any(IntArray::class.java))
        }

        fun expectPermission(permission: Int, vararg apps: Int) {
            for (app in apps) {
                if (!mPermissions.containsKey(app)) {
                    fail("uid $app does not exist.")
                }
                if (mPermissions[app] !== permission) {
                    fail("uid $app has wrong permission: ${mPermissions[app]}")
                }
            }
        }
    }

    private val PERMISSION_TRAFFIC_ALL = PERMISSION_INTERNET or PERMISSION_UPDATE_DEVICE_STATS

    @Test
    fun testPackagePermissionUpdate() {
        val mNetdServiceMonitor = NetdServiceMonitor(netdService)
        // MOCK_UID1: MOCK_PACKAGE1 only has internet permission.
        // MOCK_UID2: MOCK_PACKAGE2 does not have any permission.
        // SYSTEM_UID1: SYSTEM_PACKAGE1 has internet permission and update device stats permission.
        // SYSTEM_UID2: SYSTEM_PACKAGE2 has only update device stats permission.
        val uidNetdPerms = SparseArray<UidNetdPermissionInfo>()
        fun Int.toUnpi() = UidNetdPermissionInfo(this)
        uidNetdPerms.put(MOCK_UID1, PERMISSION_INTERNET.toUnpi())
        uidNetdPerms.put(MOCK_UID2, PERMISSION_NONE.toUnpi())
        uidNetdPerms.put(SYSTEM_UID1, PERMISSION_TRAFFIC_ALL.toUnpi())
        uidNetdPerms.put(SYSTEM_UID2, PERMISSION_UPDATE_DEVICE_STATS.toUnpi())

        // Send the permission information to netd, expect permission updated.
        permissionMonitor.sendPackagePermissionsToNetd(uidNetdPerms)
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET, MOCK_UID1)
        mNetdServiceMonitor.expectPermission(PERMISSION_NONE, MOCK_UID2)
        mNetdServiceMonitor.expectPermission(PERMISSION_TRAFFIC_ALL, SYSTEM_UID1)
        mNetdServiceMonitor.expectPermission(PERMISSION_UPDATE_DEVICE_STATS, SYSTEM_UID2)

        // Update permission of MOCK_UID1, expect new permission show up.
        permissionMonitor.sendPackagePermissionsForUid(MOCK_UID1, PERMISSION_TRAFFIC_ALL.toUnpi())
        mNetdServiceMonitor.expectPermission(PERMISSION_TRAFFIC_ALL, MOCK_UID1)

        // Change permissions of SYSTEM_UID2, expect new permission show up and old permission
        // revoked.
        permissionMonitor.sendPackagePermissionsForUid(SYSTEM_UID2, PERMISSION_INTERNET.toUnpi())
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET, SYSTEM_UID2)

        // Revoke permission from SYSTEM_UID1, expect no permission stored.
        permissionMonitor.sendPackagePermissionsForUid(SYSTEM_UID1, PERMISSION_NONE.toUnpi())
        mNetdServiceMonitor.expectPermission(PERMISSION_NONE, SYSTEM_UID1)
    }

    private fun setPackagePermissions(packageName: String, uid: Int, vararg permissions: String) =
        buildPackageInfo(uid = uid).apply {
            doReturn(this).`when`(packageManager).getPackageInfo(eq(packageName), anyInt())
            doReturn(arrayOf(packageName)).`when`(packageManager).getPackagesForUid(eq(uid))
            addPermissions(uid, *permissions)
        }

    private fun addPackage(packageName: String, uid: Int, vararg permissions: String) =
        setPackagePermissions(packageName, uid, *permissions).also {
            permissionMonitor.onPackageAdded(packageName, uid)
        }

    @Test
    fun testPackageInstall() {
        val mNetdServiceMonitor = NetdServiceMonitor(netdService)
        addPackage(MOCK_PACKAGE1, MOCK_UID1, INTERNET, UPDATE_DEVICE_STATS)
        mNetdServiceMonitor.expectPermission(PERMISSION_TRAFFIC_ALL, MOCK_UID1)
        addPackage(MOCK_PACKAGE2, MOCK_UID2, INTERNET)
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET, MOCK_UID2)
    }

    @Test
    fun testPackageInstallSharedUid() {
        val mNetdServiceMonitor = NetdServiceMonitor(netdService)
        addPackage(MOCK_PACKAGE1, MOCK_UID1, INTERNET, UPDATE_DEVICE_STATS)
        mNetdServiceMonitor.expectPermission(PERMISSION_TRAFFIC_ALL, MOCK_UID1)

        // Install another package with the same uid and no permissions should not cause the UID to
        // lose permissions.
        val packageInfo2 = buildPackageInfo(uid = MOCK_UID1)
        doReturn(packageInfo2).`when`(packageManager).getPackageInfo(eq(MOCK_PACKAGE2), anyInt())
        doReturn(arrayOf(MOCK_PACKAGE1, MOCK_PACKAGE2))
                .`when`(packageManager).getPackagesForUid(MOCK_UID1)
        permissionMonitor.onPackageAdded(MOCK_PACKAGE2, MOCK_UID1)
        mNetdServiceMonitor.expectPermission(PERMISSION_TRAFFIC_ALL, MOCK_UID1)
    }

    @Test
    fun testPackageUninstallBasic() {
        val mNetdServiceMonitor = NetdServiceMonitor(netdService)
        addPackage(MOCK_PACKAGE1, MOCK_UID1, INTERNET, UPDATE_DEVICE_STATS)
        mNetdServiceMonitor.expectPermission(PERMISSION_TRAFFIC_ALL, MOCK_UID1)
        doReturn(arrayOf<String>()).`when`(packageManager).getPackagesForUid(MOCK_UID1)
        permissionMonitor.onPackageRemoved(MOCK_PACKAGE1, MOCK_UID1)
        mNetdServiceMonitor.expectPermission(PERMISSION_UNINSTALLED, MOCK_UID1)
    }

    @Test
    fun testPackageRemoveThenAdd() {
        val mNetdServiceMonitor = NetdServiceMonitor(netdService)
        addPackage(MOCK_PACKAGE1, MOCK_UID1, INTERNET, UPDATE_DEVICE_STATS)
        mNetdServiceMonitor.expectPermission(PERMISSION_TRAFFIC_ALL, MOCK_UID1)
        doReturn(arrayOf<String>()).`when`(packageManager).getPackagesForUid(MOCK_UID1)
        removeAllPermissions(MOCK_UID1)
        permissionMonitor.onPackageRemoved(MOCK_PACKAGE1, MOCK_UID1)
        mNetdServiceMonitor.expectPermission(PERMISSION_UNINSTALLED, MOCK_UID1)
        addPackage(MOCK_PACKAGE1, MOCK_UID1, INTERNET)
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET, MOCK_UID1)
    }

    @Test
    fun testPackageUpdate() {
        val mNetdServiceMonitor = NetdServiceMonitor(netdService)
        addPackage(MOCK_PACKAGE1, MOCK_UID1)
        mNetdServiceMonitor.expectPermission(PERMISSION_NONE, MOCK_UID1)
        addPackage(MOCK_PACKAGE1, MOCK_UID1, INTERNET)
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET, MOCK_UID1)
    }

    @Test
    fun testPackageUninstallWithMultiplePackages() {
        val mNetdServiceMonitor = NetdServiceMonitor(netdService)
        addPackage(MOCK_PACKAGE1, MOCK_UID1, INTERNET, UPDATE_DEVICE_STATS)
        mNetdServiceMonitor.expectPermission(PERMISSION_TRAFFIC_ALL, MOCK_UID1)

        // Mock another package with the same uid but different permissions.
        val packageInfo2 = buildPackageInfo(uid = MOCK_UID1)
        doReturn(packageInfo2).`when`(packageManager).getPackageInfo(eq(MOCK_PACKAGE2), anyInt())
        doReturn(arrayOf(MOCK_PACKAGE2)).`when`(packageManager).getPackagesForUid(MOCK_UID1)
        removeAllPermissions(MOCK_UID1)
        addPermissions(MOCK_UID1, INTERNET)
        permissionMonitor.onPackageRemoved(MOCK_PACKAGE1, MOCK_UID1)
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET, MOCK_UID1)
    }

    @Test
    fun testRealSystemPermission() {
        // Use the real context as this test must ensure the *real* system package holds the
        // necessary permission.
        val realContext = InstrumentationRegistry.getContext()
        val monitor = PermissionMonitor(realContext, netdService)
        assertTrue(monitor.hasPermission(CONNECTIVITY_USE_RESTRICTED_NETWORKS, SYSTEM_UID))
    }
}
