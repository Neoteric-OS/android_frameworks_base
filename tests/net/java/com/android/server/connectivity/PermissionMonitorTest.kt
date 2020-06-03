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
import android.content.pm.ApplicationInfo.PRIVATE_FLAG_OEM
import android.content.pm.ApplicationInfo.PRIVATE_FLAG_PRODUCT
import android.content.pm.ApplicationInfo.PRIVATE_FLAG_VENDOR
import android.content.pm.PackageManager.GET_PERMISSIONS
import android.content.pm.PackageManager.MATCH_ANY_USER
import android.net.INetd.PERMISSION_INTERNET
import android.net.INetd.PERMISSION_NONE
import android.net.INetd.PERMISSION_SYSTEM
import android.net.INetd.PERMISSION_UNINSTALLED
import android.net.INetd.PERMISSION_UPDATE_DEVICE_STATS
import android.os.Process.SYSTEM_UID
import com.android.server.connectivity.PermissionMonitor.NETWORK
import com.android.server.connectivity.PermissionMonitor.SYSTEM
import com.android.server.connectivity.PermissionMonitor.UidNetdPermissionInfo
import junit.framework.Assert.fail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.mockito.AdditionalMatchers.aryEq
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.reset
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageList
import android.content.pm.PackageManager
import android.content.pm.PackageManagerInternal
import android.content.pm.UserInfo
import android.net.INetd
import android.net.UidRange
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.server.LocalServices
import com.android.server.connectivity.PermissionMonitor.Dependencies
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock
import java.util.Collections
import java.util.HashMap

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
    private val VERSION_P = Build.VERSION_CODES.P
    private val VERSION_Q = Build.VERSION_CODES.Q
    private val MOCK_PACKAGE1 = "appName1"
    private val MOCK_PACKAGE2 = "appName2"
    private val SYSTEM_PACKAGE1 = "sysName1"
    private val SYSTEM_PACKAGE2 = "sysName2"
    private val PARTITION_SYSTEM = "system"
    private val PARTITION_OEM = "oem"
    private val PARTITION_PRODUCT = "product"
    private val PARTITION_VENDOR = "vendor"

    // lateinit used here for mocks as they need to be reinitialized between each test and the test
    // should crash if they are used before being initialized.
    @Mock private lateinit var context: Context
    @Mock private lateinit var packageManager: PackageManager
    @Mock private lateinit var netdService: INetd
    @Mock private lateinit var mockPmi: PackageManagerInternal
    @Mock private lateinit var userManager: UserManager
    @Mock private lateinit var deps: Dependencies

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
        doReturn(PackageManager.PERMISSION_DENIED)
                .`when`(deps).checkUidPermission(anyString(), anyInt())

        permissionMonitor = spy(PermissionMonitor(context, netdService, deps))

        LocalServices.removeServiceForTest(PackageManagerInternal::class.java)
        LocalServices.addService(PackageManagerInternal::class.java, mockPmi)
        doReturn(PackageList(listOf(), /* observer */ null))
                .`when`(mockPmi).getPackageList(any())
        doReturn(/* empty app list */ null).`when`(packageManager).getInstalledPackages(anyInt())
        permissionMonitor.startMonitoring()
        verify(mockPmi).getPackageList(permissionMonitor)
    }

    private fun isLegacyPackage(partition: String, targetSdkVersion: Int, uid: Int): Boolean {
        val packageInfo = packageInfoWithPartition(partition).apply {
            applicationInfo.targetSdkVersion = targetSdkVersion
            applicationInfo.uid = uid
        }
        return permissionMonitor.isLegacyPackage(packageInfo)
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

    private fun buildPackageInfo(uid: Int, userId: Int) =
            packageInfoWithPartition(PARTITION_SYSTEM).apply {
                applicationInfo.uid = UserHandle.getUid(userId, UserHandle.getAppId(uid))
            }

    private fun uidWithPermissions(uid: Int, vararg permissions: String) {
        doReturn(PackageManager.PERMISSION_DENIED)
                .`when`(deps).checkUidPermission(anyString(), anyInt())
        for (permission in permissions) {
            doReturn(PackageManager.PERMISSION_GRANTED)
                    .`when`(deps).checkUidPermission(eq(permission), eq(uid))
        }
    }

    @Test
    fun testHasPermission() {
        uidWithPermissions(MOCK_UID1)
        assertFalse(permissionMonitor.hasPermission(CHANGE_NETWORK_STATE, MOCK_UID1))
        assertFalse(permissionMonitor.hasPermission(NETWORK_STACK, MOCK_UID1))
        assertFalse(permissionMonitor.hasPermission(
                CONNECTIVITY_USE_RESTRICTED_NETWORKS, MOCK_UID1))
        assertFalse(permissionMonitor.hasPermission(CONNECTIVITY_INTERNAL, MOCK_UID1))

        uidWithPermissions(MOCK_UID1, CHANGE_NETWORK_STATE, NETWORK_STACK)
        assertTrue(permissionMonitor.hasPermission(CHANGE_NETWORK_STATE, MOCK_UID1))
        assertTrue(permissionMonitor.hasPermission(NETWORK_STACK, MOCK_UID1))
        assertFalse(permissionMonitor.hasPermission(
                CONNECTIVITY_USE_RESTRICTED_NETWORKS, MOCK_UID1))
        assertFalse(permissionMonitor.hasPermission(CONNECTIVITY_INTERNAL, MOCK_UID1))
        assertFalse(permissionMonitor.hasPermission(CHANGE_NETWORK_STATE, MOCK_UID2))
        assertFalse(permissionMonitor.hasPermission(NETWORK_STACK, MOCK_UID2))

        uidWithPermissions(MOCK_UID2, CONNECTIVITY_USE_RESTRICTED_NETWORKS, CONNECTIVITY_INTERNAL)
        assertFalse(permissionMonitor.hasPermission(
                CONNECTIVITY_USE_RESTRICTED_NETWORKS, MOCK_UID1))
        assertFalse(permissionMonitor.hasPermission(CONNECTIVITY_INTERNAL, MOCK_UID1))
        assertTrue(permissionMonitor.hasPermission(
                CONNECTIVITY_USE_RESTRICTED_NETWORKS, MOCK_UID2))
        assertTrue(permissionMonitor.hasPermission(CONNECTIVITY_INTERNAL, MOCK_UID2))
    }

    @Test
    fun testIsVendorApp() {
        var app = packageInfoWithPartition(PARTITION_SYSTEM)
        assertFalse(PermissionMonitor.isVendorApp(app.applicationInfo))
        app = packageInfoWithPartition(PARTITION_OEM)
        assertTrue(PermissionMonitor.isVendorApp(app.applicationInfo))
        app = packageInfoWithPartition(PARTITION_PRODUCT)
        assertTrue(PermissionMonitor.isVendorApp(app.applicationInfo))
        app = packageInfoWithPartition(PARTITION_VENDOR)
        assertTrue(PermissionMonitor.isVendorApp(app.applicationInfo))
    }

    @Test
    fun testHasNetworkPermission() {
        uidWithPermissions(MOCK_UID1)
        assertFalse(permissionMonitor.hasNetworkPermission(MOCK_UID1))
        uidWithPermissions(MOCK_UID1, CHANGE_NETWORK_STATE)
        assertTrue(permissionMonitor.hasNetworkPermission(MOCK_UID1))
        uidWithPermissions(MOCK_UID1, NETWORK_STACK)
        assertFalse(permissionMonitor.hasNetworkPermission(MOCK_UID1))
        uidWithPermissions(MOCK_UID1, CONNECTIVITY_USE_RESTRICTED_NETWORKS)
        assertFalse(permissionMonitor.hasNetworkPermission(MOCK_UID1))
        uidWithPermissions(MOCK_UID1, CONNECTIVITY_INTERNAL)
        assertFalse(permissionMonitor.hasNetworkPermission(MOCK_UID1))
    }

    @Test
    fun testHasRestrictedNetworkPermission() {
        uidWithPermissions(MOCK_UID1)
        assertFalse(permissionMonitor.hasRestrictedNetworkPermission(MOCK_UID1))
        uidWithPermissions(MOCK_UID1, CHANGE_NETWORK_STATE)
        assertFalse(permissionMonitor.hasRestrictedNetworkPermission(MOCK_UID1))
        uidWithPermissions(MOCK_UID1, NETWORK_STACK)
        assertTrue(permissionMonitor.hasRestrictedNetworkPermission(MOCK_UID1))
        uidWithPermissions(MOCK_UID1, CONNECTIVITY_INTERNAL)
        assertFalse(permissionMonitor.hasRestrictedNetworkPermission(MOCK_UID1))
        uidWithPermissions(MOCK_UID1, CONNECTIVITY_USE_RESTRICTED_NETWORKS)
        assertTrue(permissionMonitor.hasRestrictedNetworkPermission(MOCK_UID1))
        uidWithPermissions(MOCK_UID1, CHANGE_WIFI_STATE)
        assertFalse(permissionMonitor.hasRestrictedNetworkPermission(MOCK_UID1))
        uidWithPermissions(MOCK_UID1, CONNECTIVITY_USE_RESTRICTED_NETWORKS)
        assertFalse(permissionMonitor.hasRestrictedNetworkPermission(MOCK_UID2))
        assertFalse(permissionMonitor.hasRestrictedNetworkPermission(SYSTEM_UID))
    }

    @Test
    fun testIsLegacyPackage() {
        doReturn(VERSION_P).`when`(deps).deviceFirstSdkInt
        assertTrue(isLegacyPackage(PARTITION_SYSTEM, VERSION_P, SYSTEM_UID))
        assertTrue(isLegacyPackage(PARTITION_VENDOR, VERSION_P, SYSTEM_UID))
        assertFalse(isLegacyPackage(PARTITION_SYSTEM, VERSION_P, MOCK_UID1))
        assertTrue(isLegacyPackage(PARTITION_VENDOR, VERSION_P, MOCK_UID1))
        assertTrue(isLegacyPackage(PARTITION_SYSTEM, VERSION_Q, SYSTEM_UID))
        assertTrue(isLegacyPackage(PARTITION_VENDOR, VERSION_Q, SYSTEM_UID))
        assertFalse(isLegacyPackage(PARTITION_SYSTEM, VERSION_Q, MOCK_UID1))
        assertFalse(isLegacyPackage(PARTITION_VENDOR, VERSION_Q, MOCK_UID1))
        doReturn(VERSION_Q).`when`(deps).deviceFirstSdkInt
        assertFalse(isLegacyPackage(PARTITION_SYSTEM, VERSION_P, SYSTEM_UID))
        assertTrue(isLegacyPackage(PARTITION_VENDOR, VERSION_P, SYSTEM_UID))
        assertFalse(isLegacyPackage(PARTITION_SYSTEM, VERSION_P, MOCK_UID1))
        assertTrue(isLegacyPackage(PARTITION_VENDOR, VERSION_P, MOCK_UID1))
        assertFalse(isLegacyPackage(PARTITION_SYSTEM, VERSION_Q, SYSTEM_UID))
        assertFalse(isLegacyPackage(PARTITION_VENDOR, VERSION_Q, SYSTEM_UID))
        assertFalse(isLegacyPackage(PARTITION_SYSTEM, VERSION_Q, MOCK_UID1))
        assertFalse(isLegacyPackage(PARTITION_VENDOR, VERSION_Q, MOCK_UID1))
    }

    @Throws(Exception::class)
    private fun assertBackgroundPermission(
        hasPermission: Boolean,
        name: String,
        uid: Int,
        vararg permissions: String
    ) {
        doReturn(packageInfoWithPartition(PARTITION_SYSTEM))
                .`when`(packageManager).getPackageInfo(eq(name), anyInt())
        uidWithPermissions(uid, *permissions)
        permissionMonitor.onPackageAdded(name, uid)
        assertEquals(hasPermission, permissionMonitor.hasUseBackgroundNetworksPermission(uid))
    }

    @Test
    @Throws(Exception::class)
    fun testHasUseBackgroundNetworksPermission() {
        assertFalse(permissionMonitor.hasUseBackgroundNetworksPermission(SYSTEM_UID))
        assertBackgroundPermission(false, SYSTEM_PACKAGE1, SYSTEM_UID)
        assertBackgroundPermission(false, SYSTEM_PACKAGE1, SYSTEM_UID, CONNECTIVITY_INTERNAL)
        assertBackgroundPermission(true, SYSTEM_PACKAGE1, SYSTEM_UID, CHANGE_NETWORK_STATE)
        assertBackgroundPermission(true, SYSTEM_PACKAGE1, SYSTEM_UID, NETWORK_STACK)
        assertFalse(permissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID1))
        assertBackgroundPermission(false, MOCK_PACKAGE1, MOCK_UID1)
        assertBackgroundPermission(true, MOCK_PACKAGE1, MOCK_UID1,
                CONNECTIVITY_USE_RESTRICTED_NETWORKS)
        assertFalse(permissionMonitor.hasUseBackgroundNetworksPermission(MOCK_UID2))
        assertBackgroundPermission(false, MOCK_PACKAGE2, MOCK_UID2)
        assertBackgroundPermission(false, MOCK_PACKAGE2, MOCK_UID2,
                CONNECTIVITY_INTERNAL)
        assertBackgroundPermission(true, MOCK_PACKAGE2, MOCK_UID2, NETWORK_STACK)
    }

    private class NetdMonitor internal constructor(mockNetd: INetd) {
        private val mApps: HashMap<Int, Boolean> = HashMap()

        init {
            // Add hook to verify and track result of setPermission.
            doAnswer { invocation: InvocationOnMock ->
                val args = invocation.arguments
                val isSystem = args[0] == PERMISSION_SYSTEM
                for (uid in args[1] as IntArray) {
                    // TODO: Currently, permission monitor will send duplicate commands for each uid
                    // corresponding to each user. Need to fix that and uncomment below test.
                    // if (mApps.containsKey(uid) && mApps.get(uid) == isSystem) {
                    //     fail("uid " + uid + " is already set to " + isSystem);
                    // }
                    mApps.put(uid, isSystem)
                }
                null
            }.`when`(mockNetd).networkSetPermissionForUser(anyInt(), any(IntArray::class.java))

            // Add hook to verify and track result of clearPermission.
            doAnswer { invocation: InvocationOnMock ->
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

        fun expectPermission(permission: Boolean, users: IntArray, apps: IntArray) {
            for (user in users) {
                for (app in apps) {
                    val uid = UserHandle.getUid(user, app)
                    if (!mApps.containsKey(uid)) {
                        fail("uid $uid does not exist.")
                    }
                    if (mApps.get(uid) !== permission) {
                        fail("uid $uid has wrong permission: $permission")
                    }
                }
            }
        }

        fun expectNoPermission(users: IntArray, apps: IntArray) {
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
    @Throws(Exception::class)
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
        mNetdMonitor.expectPermission(NETWORK, intArrayOf(MOCK_USER1), intArrayOf(SYSTEM_UID))

        // Add SYSTEM_PACKAGE1, expect permission escalate.
        addPackageForUsers(intArrayOf(MOCK_USER1), SYSTEM_PACKAGE1, SYSTEM_UID)
        mNetdMonitor.expectPermission(SYSTEM, intArrayOf(MOCK_USER1), intArrayOf(SYSTEM_UID))
        permissionMonitor.onUserAdded(MOCK_USER2)
        mNetdMonitor.expectPermission(
                SYSTEM, intArrayOf(MOCK_USER1, MOCK_USER2), intArrayOf(SYSTEM_UID))
        addPackageForUsers(intArrayOf(MOCK_USER1, MOCK_USER2), MOCK_PACKAGE1, MOCK_UID1)
        mNetdMonitor.expectPermission(
                SYSTEM, intArrayOf(MOCK_USER1, MOCK_USER2), intArrayOf(SYSTEM_UID))
        mNetdMonitor.expectPermission(
                NETWORK, intArrayOf(MOCK_USER1, MOCK_USER2), intArrayOf(MOCK_UID1))

        // Remove MOCK_UID1, expect no permission left for all user.
        permissionMonitor.onPackageRemoved(MOCK_PACKAGE1, MOCK_UID1)
        removePackageForUsers(intArrayOf(MOCK_USER1, MOCK_USER2), MOCK_UID1)
        mNetdMonitor.expectNoPermission(intArrayOf(MOCK_USER1, MOCK_USER2), intArrayOf(MOCK_UID1))

        // Remove SYSTEM_PACKAGE1, expect permission downgrade.
        doReturn(arrayOf(SYSTEM_PACKAGE2)).`when`(packageManager).getPackagesForUid(anyInt())
        removePackageForUsers(intArrayOf(MOCK_USER1, MOCK_USER2), SYSTEM_UID)
        mNetdMonitor.expectPermission(
                NETWORK, intArrayOf(MOCK_USER1, MOCK_USER2), intArrayOf(SYSTEM_UID))
        permissionMonitor.onUserRemoved(MOCK_USER1)
        mNetdMonitor.expectPermission(NETWORK, intArrayOf(MOCK_USER2), intArrayOf(SYSTEM_UID))

        // Remove all packages, expect no permission left.
        doReturn(arrayOf<String>()).`when`(packageManager).getPackagesForUid(anyInt())
        removePackageForUsers(intArrayOf(MOCK_USER2), SYSTEM_UID)
        mNetdMonitor.expectNoPermission(
                intArrayOf(MOCK_USER1, MOCK_USER2), intArrayOf(SYSTEM_UID, MOCK_UID1))

        // Remove last user, expect no redundant clearPermission is invoked.
        permissionMonitor.onUserRemoved(MOCK_USER2)
        mNetdMonitor.expectNoPermission(
                intArrayOf(MOCK_USER1, MOCK_USER2), intArrayOf(SYSTEM_UID, MOCK_UID1))
    }

    @Test
    @Throws(Exception::class)
    fun testUidFilteringDuringVpnConnectDisconnectAndUidUpdates() {
        doReturn(listOf(buildPackageInfo(SYSTEM_UID1, MOCK_USER1),
                buildPackageInfo(MOCK_UID1, MOCK_USER1),
                buildPackageInfo(MOCK_UID2, MOCK_USER1),
                buildPackageInfo(VPN_UID, MOCK_USER1)))
                .`when`(packageManager).getInstalledPackages(eq(GET_PERMISSIONS or MATCH_ANY_USER))
        doReturn(buildPackageInfo(MOCK_UID1, MOCK_USER1))
                .`when`(packageManager).getPackageInfo(eq(MOCK_PACKAGE1), eq(GET_PERMISSIONS))

        uidWithPermissions(SYSTEM_UID,
                CHANGE_NETWORK_STATE, NETWORK_STACK, CONNECTIVITY_USE_RESTRICTED_NETWORKS)
        permissionMonitor.startMonitoring()
        // Every app on user 0 except MOCK_UID2 are under VPN.
        val vpnRange1: Set<UidRange> = setOf(
                UidRange(0, MOCK_UID2 - 1),
                UidRange(MOCK_UID2 + 1, UserHandle.PER_USER_RANGE - 1))
        val vpnRange2: Set<UidRange> = Collections.singleton(UidRange(MOCK_UID2, MOCK_UID2))

        // When VPN is connected, expect a rule to be set up for user app MOCK_UID1
        permissionMonitor.onVpnUidRangesAdded("tun0", vpnRange1, VPN_UID)
        verify(netdService).firewallAddUidInterfaceRules(eq("tun0"), aryEq(intArrayOf(MOCK_UID1)))
        reset(netdService)

        // When MOCK_UID1 package is uninstalled and reinstalled, expect Netd to be updated
        permissionMonitor.onPackageRemoved(
                MOCK_PACKAGE1, UserHandle.getUid(MOCK_USER1, MOCK_UID1))
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
    @Throws(Exception::class)
    fun testUidFilteringDuringPackageInstallAndUninstall() {
        doReturn(listOf(buildPackageInfo(SYSTEM_UID1, MOCK_USER1),
                buildPackageInfo(VPN_UID, MOCK_USER1)))
                .`when`(packageManager).getInstalledPackages(eq(GET_PERMISSIONS or MATCH_ANY_USER))
        doReturn(buildPackageInfo(MOCK_UID1, MOCK_USER1))
                .`when`(packageManager).getPackageInfo(eq(MOCK_PACKAGE1), eq(GET_PERMISSIONS))
        permissionMonitor.startMonitoring()
        val vpnRange: Set<UidRange> = Collections.singleton(UidRange.createForUser(MOCK_USER1))
        permissionMonitor.onVpnUidRangesAdded("tun0", vpnRange, VPN_UID)

        // Newly-installed package should have uid rules added
        permissionMonitor.onPackageAdded(MOCK_PACKAGE1, UserHandle.getUid(MOCK_USER1, MOCK_UID1))
        verify(netdService).firewallAddUidInterfaceRules(eq("tun0"), aryEq(intArrayOf(MOCK_UID1)))

        // Removed package should have its uid rules removed
        permissionMonitor.onPackageRemoved(
                MOCK_PACKAGE1, UserHandle.getUid(MOCK_USER1, MOCK_UID1))
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

    private fun removePackageForUsers(users: IntArray, uid: Int) {
        for (user in users) {
            permissionMonitor.onPackageRemoved("", UserHandle.getUid(user, uid))
        }
    }

    private class NetdServiceMonitor internal constructor(mockNetdService: INetd) {
        private val mPermissions: HashMap<Int, Int> = HashMap()

        init {
            // Add hook to verify and track result of setPermission.
            doAnswer { invocation: InvocationOnMock ->
                val args = invocation.arguments
                val permission = args[0] as Int
                for (uid in args[1] as IntArray) {
                    mPermissions.put(uid, permission)
                }
                null
            }.`when`(mockNetdService).trafficSetNetPermForUids(anyInt(), any(IntArray::class.java))
        }

        fun expectPermission(permission: Int, apps: IntArray) {
            for (app in apps) {
                if (!mPermissions.containsKey(app)) {
                    fail("uid $app does not exist.")
                }
                if (mPermissions.get(app) !== permission) {
                    fail("uid " + app + " has wrong permission: " + mPermissions.get(app))
                }
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testPackagePermissionUpdate() {
        val mNetdServiceMonitor = NetdServiceMonitor(netdService)
        // MOCK_UID1: MOCK_PACKAGE1 only has internet permission.
        // MOCK_UID2: MOCK_PACKAGE2 does not have any permission.
        // SYSTEM_UID1: SYSTEM_PACKAGE1 has internet permission and update device stats permission.
        // SYSTEM_UID2: SYSTEM_PACKAGE2 has only update device stats permission.
        val uidNetdPerms: HashMap<Int, UidNetdPermissionInfo> = HashMap()
        uidNetdPerms[MOCK_UID1] = UidNetdPermissionInfo(PERMISSION_INTERNET)
        uidNetdPerms[MOCK_UID2] = UidNetdPermissionInfo(PERMISSION_NONE)
        uidNetdPerms[SYSTEM_UID1] = UidNetdPermissionInfo(
                PERMISSION_INTERNET or PERMISSION_UPDATE_DEVICE_STATS)
        uidNetdPerms[SYSTEM_UID2] = UidNetdPermissionInfo(PERMISSION_UPDATE_DEVICE_STATS)

        // Send the permission information to netd, expect permission updated.
        permissionMonitor.sendPackagePermissionsToNetd(uidNetdPerms)
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET, intArrayOf(MOCK_UID1))
        mNetdServiceMonitor.expectPermission(PERMISSION_NONE, intArrayOf(MOCK_UID2))
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET
                or PERMISSION_UPDATE_DEVICE_STATS, intArrayOf(SYSTEM_UID1))
        mNetdServiceMonitor.expectPermission(
                PERMISSION_UPDATE_DEVICE_STATS, intArrayOf(SYSTEM_UID2))

        // Update permission of MOCK_UID1, expect new permission show up.
        permissionMonitor.sendPackagePermissionsForUid(MOCK_UID1, UidNetdPermissionInfo(
                PERMISSION_INTERNET or PERMISSION_UPDATE_DEVICE_STATS))
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET
                or PERMISSION_UPDATE_DEVICE_STATS, intArrayOf(MOCK_UID1))

        // Change permissions of SYSTEM_UID2, expect new permission show up and old permission
        // revoked.
        permissionMonitor.sendPackagePermissionsForUid(SYSTEM_UID2, UidNetdPermissionInfo(
                PERMISSION_INTERNET))
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET, intArrayOf(SYSTEM_UID2))

        // Revoke permission from SYSTEM_UID1, expect no permission stored.
        permissionMonitor.sendPackagePermissionsForUid(SYSTEM_UID1, UidNetdPermissionInfo(
                PERMISSION_NONE))
        mNetdServiceMonitor.expectPermission(PERMISSION_NONE, intArrayOf(SYSTEM_UID1))
    }

    @Throws(Exception::class)
    private fun setPackagePermissions(packageName: String, uid: Int, permissions: Array<String>):
            PackageInfo {
        val packageInfo = packageInfoWithPartition(PARTITION_SYSTEM)
        doReturn(packageInfo).`when`(packageManager).getPackageInfo(eq(packageName), anyInt())
        doReturn(arrayOf(packageName)).`when`(packageManager).getPackagesForUid(eq(uid))
        uidWithPermissions(uid, *permissions)
        return packageInfo
    }

    @Throws(Exception::class)
    private fun addPackage(packageName: String, uid: Int, permissions: Array<String>): PackageInfo {
        val packageInfo = setPackagePermissions(packageName, uid, permissions)
        permissionMonitor.onPackageAdded(packageName, uid)
        return packageInfo
    }

    @Test
    @Throws(Exception::class)
    fun testPackageInstall() {
        val mNetdServiceMonitor = NetdServiceMonitor(netdService)
        addPackage(MOCK_PACKAGE1, MOCK_UID1, arrayOf(INTERNET, UPDATE_DEVICE_STATS))
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET
                or PERMISSION_UPDATE_DEVICE_STATS, intArrayOf(MOCK_UID1))
        addPackage(MOCK_PACKAGE2, MOCK_UID2, arrayOf(INTERNET))
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET, intArrayOf(MOCK_UID2))
    }

    @Test
    @Throws(Exception::class)
    fun testPackageInstallSharedUid() {
        val mNetdServiceMonitor = NetdServiceMonitor(netdService)
        addPackage(MOCK_PACKAGE1, MOCK_UID1, arrayOf(INTERNET, UPDATE_DEVICE_STATS))
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET
                or PERMISSION_UPDATE_DEVICE_STATS, intArrayOf(MOCK_UID1))

        // Install another package with the same uid and no permissions should not cause the UID to
        // lose permissions.
        val packageInfo2 = packageInfoWithPartition(PARTITION_SYSTEM)
        doReturn(packageInfo2).`when`(packageManager).getPackageInfo(eq(MOCK_PACKAGE2), anyInt())
        doReturn(arrayOf(MOCK_PACKAGE1, MOCK_PACKAGE2))
                .`when`(packageManager).getPackagesForUid(MOCK_UID1)
        permissionMonitor.onPackageAdded(MOCK_PACKAGE2, MOCK_UID1)
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET
                or PERMISSION_UPDATE_DEVICE_STATS, intArrayOf(MOCK_UID1))
    }

    @Test
    @Throws(Exception::class)
    fun testPackageUninstallBasic() {
        val mNetdServiceMonitor = NetdServiceMonitor(netdService)
        addPackage(MOCK_PACKAGE1, MOCK_UID1, arrayOf(INTERNET, UPDATE_DEVICE_STATS))
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET
                or PERMISSION_UPDATE_DEVICE_STATS, intArrayOf(MOCK_UID1))
        doReturn(arrayOf<String>()).`when`(packageManager).getPackagesForUid(MOCK_UID1)
        permissionMonitor.onPackageRemoved(MOCK_PACKAGE1, MOCK_UID1)
        mNetdServiceMonitor.expectPermission(PERMISSION_UNINSTALLED, intArrayOf(MOCK_UID1))
    }

    @Test
    @Throws(Exception::class)
    fun testPackageRemoveThenAdd() {
        val mNetdServiceMonitor = NetdServiceMonitor(netdService)
        addPackage(MOCK_PACKAGE1, MOCK_UID1, arrayOf(INTERNET, UPDATE_DEVICE_STATS))
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET
                or PERMISSION_UPDATE_DEVICE_STATS, intArrayOf(MOCK_UID1))
        doReturn(arrayOf<String>()).`when`(packageManager).getPackagesForUid(MOCK_UID1)
        permissionMonitor.onPackageRemoved(MOCK_PACKAGE1, MOCK_UID1)
        mNetdServiceMonitor.expectPermission(PERMISSION_UNINSTALLED, intArrayOf(MOCK_UID1))
        addPackage(MOCK_PACKAGE1, MOCK_UID1, arrayOf(INTERNET))
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET, intArrayOf(MOCK_UID1))
    }

    @Test
    @Throws(Exception::class)
    fun testPackageUpdate() {
        val mNetdServiceMonitor = NetdServiceMonitor(netdService)
        addPackage(MOCK_PACKAGE1, MOCK_UID1, arrayOf())
        mNetdServiceMonitor.expectPermission(PERMISSION_NONE, intArrayOf(MOCK_UID1))
        addPackage(MOCK_PACKAGE1, MOCK_UID1, arrayOf(INTERNET))
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET, intArrayOf(MOCK_UID1))
    }

    @Test
    @Throws(Exception::class)
    fun testPackageUninstallWithMultiplePackages() {
        val mNetdServiceMonitor = NetdServiceMonitor(netdService)
        addPackage(MOCK_PACKAGE1, MOCK_UID1, arrayOf(INTERNET, UPDATE_DEVICE_STATS))
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET
                or PERMISSION_UPDATE_DEVICE_STATS, intArrayOf(MOCK_UID1))

        // Mock another package with the same uid but different permissions.
        val packageInfo2 = packageInfoWithPartition(PARTITION_SYSTEM)
        doReturn(packageInfo2).`when`(packageManager).getPackageInfo(eq(MOCK_PACKAGE2), anyInt())
        doReturn(arrayOf(MOCK_PACKAGE2)).`when`(packageManager).getPackagesForUid(MOCK_UID1)
        uidWithPermissions(MOCK_UID1, INTERNET)
        permissionMonitor.onPackageRemoved(MOCK_PACKAGE1, MOCK_UID1)
        mNetdServiceMonitor.expectPermission(PERMISSION_INTERNET, intArrayOf(MOCK_UID1))
    }

    @Test
    @Throws(Exception::class)
    fun testRealSystemPermission() {
        // Use the real context as this test must ensure the *real* system package holds the
        // necessary permission.
        val realContext = InstrumentationRegistry.getContext()
        val monitor = PermissionMonitor(realContext, netdService)
        assertTrue(monitor.hasPermission(CONNECTIVITY_USE_RESTRICTED_NETWORKS, SYSTEM_UID))
    }
}
