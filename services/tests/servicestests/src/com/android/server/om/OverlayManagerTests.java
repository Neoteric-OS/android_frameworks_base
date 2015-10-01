package com.android.server.om;

import static android.content.om.OverlayInfo.STATE_APPROVED_DISABLED;
import static android.content.om.OverlayInfo.STATE_APPROVED_ENABLED;
import static android.content.om.OverlayInfo.STATE_NOT_APPROVED_COMPONENT_DISABLED;
import static android.content.om.OverlayInfo.STATE_NOT_APPROVED_DANGEROUS_OVERLAY;
import static android.content.om.OverlayInfo.STATE_NOT_APPROVED_MISSING_TARGET;
import static android.content.om.OverlayInfo.STATE_NOT_APPROVED_NO_IDMAP;
import static android.content.om.OverlayInfo.stateToString;
import static android.os.UserHandle.USER_OWNER;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.content.Context;
import android.content.om.OverlayInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.test.AndroidTestCase;
import android.util.ArrayMap;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class OverlayManagerTests extends AndroidTestCase {
    private PackageManagerHelper mPackageManager;

    @Mock
    private MockIdmapManager mIdmapManager;

    private OverlayManagerDatabase mDatabase;

    private OverlayManagerServiceImpl mImpl;

    public void setUp() throws Exception {
        System.setProperty("dexmaker.dexcache", mContext.getCacheDir().getPath());
        MockitoAnnotations.initMocks(this);

        mDatabase = new OverlayManagerDatabase();
        DatabaseChangeListener listener = new DatabaseChangeListener();
        mDatabase.addChangeListener(listener);

        mPackageManager = new PackageManagerHelper();

        mImpl = new OverlayManagerServiceImpl(mPackageManager, mIdmapManager, mDatabase);
    }

    // tests: helper methods

    public void testBasicPackageInstallation() throws Exception {
        final int userId = USER_OWNER;

        // OMSImpl is told about new packages by OMS, or in this case, by this test case
        PackageInfo overlay =
            installOverlayPackage("com.dummy.overlay", "com.dummy.target", userId);
        OverlayInfo oi = mImpl.onGetOverlayInfo(overlay.packageName, userId);
        assertNull(oi);

        // emulate that OMS recevied ACTION_PACKAGE_ADDED, tell OMSImpl about it
        mImpl.onOverlayPackageAdded(overlay.packageName, userId);
        assertState(overlay.packageName, userId, STATE_NOT_APPROVED_MISSING_TARGET);

        // now install the target package
        PackageInfo target = installTargetPackage(overlay.overlayTarget, userId);
        mImpl.onTargetPackageAdded(target.packageName, userId);
        assertState(overlay.packageName, userId, STATE_APPROVED_DISABLED);

        // uninstall the target package
        uninstallTargetPackage(target.packageName, userId);
        mImpl.onTargetPackageRemoved(target.packageName, userId);
        assertState(overlay.packageName, userId, STATE_NOT_APPROVED_MISSING_TARGET);

        // and finally, uninstall the overlay package
        uninstallOverlayPackage(overlay.packageName, userId);
        mImpl.onOverlayPackageRemoved(overlay.packageName, userId);
        oi = mImpl.onGetOverlayInfo(overlay.packageName, userId);
        assertNull(oi);
    }

    // tests: state transitions

    public void testStateWhenBothPackagesAreInstalled() throws Exception {
        // Install an overlay and its target and verify the overlay's state
        // once the dust has settled. This test should cover all permutations
        // and should ensure no security holes are introduced.

        // comp enabled | target inst | idmap ok | is system | same sig | is dangerous | state
        assertState(false, false, false, false, false, false, STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, false, false, false, false, true,  STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, false, false, false, true,  false, STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, false, false, false, true,  true,  STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, false, false, true,  false, false, STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, false, false, true,  false, true,  STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, false, false, true,  true,  false, STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, false, false, true,  true,  true,  STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, false, true,  false, false, false, STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, false, true,  false, false, true,  STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, false, true,  false, true,  false, STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, false, true,  false, true,  true,  STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, false, true,  true,  false, false, STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, false, true,  true,  false, true,  STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, false, true,  true,  true,  false, STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, false, true,  true,  true,  true,  STATE_NOT_APPROVED_COMPONENT_DISABLED);

        // comp enabled | target inst | idmap ok | is system | same sig | is dangerous | state
        assertState(false, true,  false, false, false, false, STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, true,  false, false, false, true,  STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, true,  false, false, true,  false, STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, true,  false, false, true,  true,  STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, true,  false, true,  false, false, STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, true,  false, true,  false, true,  STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, true,  false, true,  true,  false, STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, true,  false, true,  true,  true,  STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, true,  true,  false, false, false, STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, true,  true,  false, false, true,  STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, true,  true,  false, true,  false, STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, true,  true,  false, true,  true,  STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, true,  true,  true,  false, false, STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, true,  true,  true,  false, true,  STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, true,  true,  true,  true,  false, STATE_NOT_APPROVED_COMPONENT_DISABLED);
        assertState(false, true,  true,  true,  true,  true,  STATE_NOT_APPROVED_COMPONENT_DISABLED);

        // comp enabled | target inst | idmap ok | is system | same sig | is dangerous | state
        assertState(true,  false, false, false, false, false, STATE_NOT_APPROVED_MISSING_TARGET);
        assertState(true,  false, false, false, false, true,  STATE_NOT_APPROVED_MISSING_TARGET);
        assertState(true,  false, false, false, true,  false, STATE_NOT_APPROVED_MISSING_TARGET);
        assertState(true,  false, false, false, true,  true,  STATE_NOT_APPROVED_MISSING_TARGET);
        assertState(true,  false, false, true,  false, false, STATE_NOT_APPROVED_MISSING_TARGET);
        assertState(true,  false, false, true,  false, true,  STATE_NOT_APPROVED_MISSING_TARGET);
        assertState(true,  false, false, true,  true,  false, STATE_NOT_APPROVED_MISSING_TARGET);
        assertState(true,  false, false, true,  true,  true,  STATE_NOT_APPROVED_MISSING_TARGET);
        assertState(true,  false, true,  false, false, false, STATE_NOT_APPROVED_MISSING_TARGET);
        assertState(true,  false, true,  false, false, true,  STATE_NOT_APPROVED_MISSING_TARGET);
        assertState(true,  false, true,  false, true,  false, STATE_NOT_APPROVED_MISSING_TARGET);
        assertState(true,  false, true,  false, true,  true,  STATE_NOT_APPROVED_MISSING_TARGET);
        assertState(true,  false, true,  true,  false, false, STATE_NOT_APPROVED_MISSING_TARGET);
        assertState(true,  false, true,  true,  false, true,  STATE_NOT_APPROVED_MISSING_TARGET);
        assertState(true,  false, true,  true,  true,  false, STATE_NOT_APPROVED_MISSING_TARGET);
        assertState(true,  false, true,  true,  true,  true,  STATE_NOT_APPROVED_MISSING_TARGET);

        // comp enabled | target inst | idmap ok | is system | same sig | is dangerous | state
        assertState(true,  true,  false, false, false, false, STATE_NOT_APPROVED_NO_IDMAP);
        assertState(true,  true,  false, false, false, true,  STATE_NOT_APPROVED_NO_IDMAP);
        assertState(true,  true,  false, false, true,  false, STATE_NOT_APPROVED_NO_IDMAP);
        assertState(true,  true,  false, false, true,  true,  STATE_NOT_APPROVED_NO_IDMAP);
        assertState(true,  true,  false, true,  false, false, STATE_NOT_APPROVED_NO_IDMAP);
        assertState(true,  true,  false, true,  false, true,  STATE_NOT_APPROVED_NO_IDMAP);
        assertState(true,  true,  false, true,  true,  false, STATE_NOT_APPROVED_NO_IDMAP);
        assertState(true,  true,  false, true,  true,  true,  STATE_NOT_APPROVED_NO_IDMAP);
        assertState(true,  true,  true,  false, false, false, STATE_APPROVED_DISABLED);
        assertState(true,  true,  true,  false, false, true,  STATE_NOT_APPROVED_DANGEROUS_OVERLAY);
        assertState(true,  true,  true,  false, true,  false, STATE_APPROVED_DISABLED);
        assertState(true,  true,  true,  false, true,  true,  STATE_APPROVED_DISABLED);
        assertState(true,  true,  true,  true,  false, false, STATE_APPROVED_DISABLED);
        assertState(true,  true,  true,  true,  false, true,  STATE_APPROVED_DISABLED);
        assertState(true,  true,  true,  true,  true,  false, STATE_APPROVED_DISABLED);
        assertState(true,  true,  true,  true,  true,  true,  STATE_APPROVED_DISABLED);
    }

    private void assertState(boolean isOverlayEnabled, boolean isTargetInstalled,
            boolean isIdmapGenerated, boolean isOverlaySystem, boolean areSignaturesMatching,
            boolean isDangerous, int expectedState) throws Exception {
        final int userId = USER_OWNER;
        final int firstState = isOverlayEnabled ? STATE_NOT_APPROVED_MISSING_TARGET :
            STATE_NOT_APPROVED_COMPONENT_DISABLED;

        PackageInfo overlay =
            installOverlayPackage("com.dummy.overlay", "com.dummy.target", userId,
                    isOverlayEnabled, isIdmapGenerated, isOverlaySystem, areSignaturesMatching,
                    isDangerous);
        mImpl.onOverlayPackageAdded(overlay.packageName, userId);
        assertState(overlay.packageName, userId, firstState);

        if (isTargetInstalled) {
            PackageInfo target = installTargetPackage(overlay.overlayTarget, userId);
            mImpl.onTargetPackageAdded(target.packageName, userId);
            assertState(overlay.packageName, userId, expectedState);

            uninstallTargetPackage(target.packageName, userId);
            mImpl.onTargetPackageRemoved(target.packageName, userId);
            assertState(overlay.packageName, userId, firstState);
        }

        uninstallOverlayPackage(overlay.packageName, userId);
        mImpl.onOverlayPackageRemoved(overlay.packageName, userId);
    }

    public void testStateAfterOverlayUpgrade() throws Exception {
        final int userId = USER_OWNER;

        PackageInfo v1 = installOverlayPackage("com.dummy.overlay", "com.dummy.target", userId);
        mImpl.onOverlayPackageAdded(v1.packageName, userId);
        assertState(v1.packageName, userId, STATE_NOT_APPROVED_MISSING_TARGET);

        PackageInfo target = installTargetPackage(v1.overlayTarget, userId);
        mImpl.onTargetPackageAdded(target.packageName, userId);
        assertState(v1.packageName, userId, STATE_APPROVED_DISABLED);

        boolean status = mImpl.onSetEnabled(v1.packageName, true, userId);
        assertTrue(status);
        assertState(v1.packageName, userId, STATE_APPROVED_ENABLED);

        // begin overlay package upgrade
        uninstallOverlayPackage(v1.packageName, userId);
        mImpl.onOverlayPackageUpgrading(v1.packageName, userId);
        OverlayInfo oi = mImpl.onGetOverlayInfo(v1.packageName, userId);
        assertNull(oi);

        // overlay package upgrade finished: the old state should have been remembered
        PackageInfo v2 = installOverlayPackage("com.dummy.overlay", "com.dummy.target", userId);
        mImpl.onOverlayPackageUpgraded(v2.packageName, userId);
        assertState(v2.packageName, userId, STATE_APPROVED_ENABLED);
    }

    public void testStateAfterOverlayUpgradeAndChangedTarget() throws Exception {
        final int userId = USER_OWNER;

        PackageInfo o1 = installOverlayPackage("com.dummy.overlay", "com.dummy.target", userId);
        mImpl.onOverlayPackageAdded(o1.packageName, userId);
        assertState(o1.packageName, userId, STATE_NOT_APPROVED_MISSING_TARGET);

        PackageInfo t1 = installTargetPackage(o1.overlayTarget, userId);
        mImpl.onTargetPackageAdded(t1.packageName, userId);
        assertState(o1.packageName, userId, STATE_APPROVED_DISABLED);

        PackageInfo t2 = installTargetPackage("com.dummy.some_other_target", userId);
        mImpl.onTargetPackageAdded(t2.packageName, userId);

        boolean status = mImpl.onSetEnabled(o1.packageName, true, userId);
        assertTrue(status);
        assertState(o1.packageName, userId, STATE_APPROVED_ENABLED);

        // begin overlay package upgrade
        uninstallOverlayPackage(o1.packageName, userId);
        mImpl.onOverlayPackageUpgrading(o1.packageName, userId);
        OverlayInfo oi = mImpl.onGetOverlayInfo(o1.packageName, userId);
        assertNull(oi);

        // overlay package upgrade finished; the new overlay version has switched to a new
        // target package. Stashed information about the old overlay package refers to the
        // old target package and should be ignored.
        PackageInfo o2 = installOverlayPackage(o1.packageName, t2.packageName, userId);
        mImpl.onOverlayPackageUpgraded(o2.packageName, userId);
        assertState(o2.packageName, userId, STATE_APPROVED_DISABLED);
    }

    public void testStateAfterTargetUpgrade() throws Exception {
        final int userId = USER_OWNER;

        PackageInfo v1 = installTargetPackage("com.dummy.target", userId);
        mImpl.onTargetPackageAdded(v1.packageName, userId);

        PackageInfo overlay =
            installOverlayPackage("com.dummy.overlay", v1.packageName, userId);
        mImpl.onOverlayPackageAdded(overlay.packageName, userId);
        assertState(overlay.packageName, userId, STATE_APPROVED_DISABLED);

        boolean status = mImpl.onSetEnabled(overlay.packageName, true, userId);
        assertTrue(status);
        assertState(overlay.packageName, userId, STATE_APPROVED_ENABLED);

        // begin target package upgrade
        uninstallTargetPackage(v1.packageName, userId);
        mImpl.onTargetPackageUpgrading(v1.packageName, userId);
        assertState(overlay.packageName, userId, STATE_NOT_APPROVED_MISSING_TARGET);

        // target package upgrade finished: the old state should have been remembered
        PackageInfo v2 = installTargetPackage("com.dummy.target", userId);
        mImpl.onTargetPackageUpgraded(v2.packageName, userId);
        assertState(overlay.packageName, userId, STATE_APPROVED_ENABLED);
    }

    public void testOrderAfterOverlayUpgrade() throws Exception {
        final int userId = USER_OWNER;

        PackageInfo target = installTargetPackage("com.dummy.target", userId);
        mImpl.onTargetPackageAdded(target.packageName, userId);

        PackageInfo a = installOverlayPackage("com.dummy.a", target.packageName, userId);
        mImpl.onOverlayPackageAdded(a.packageName, userId);
        assertState(a.packageName, userId, STATE_APPROVED_DISABLED);

        PackageInfo b = installOverlayPackage("com.dummy.b", target.packageName, userId);
        mImpl.onOverlayPackageAdded(b.packageName, userId);
        assertState(a.packageName, userId, STATE_APPROVED_DISABLED);

        PackageInfo c = installOverlayPackage("com.dummy.c", target.packageName, userId);
        mImpl.onOverlayPackageAdded(c.packageName, userId);
        assertState(a.packageName, userId, STATE_APPROVED_DISABLED);

        boolean status = mImpl.onSetEnabled(a.packageName, true, userId);
        assertTrue(status);
        assertState(a.packageName, userId, STATE_APPROVED_ENABLED);

        status = mImpl.onSetEnabled(b.packageName, true, userId);
        assertTrue(status);
        assertState(b.packageName, userId, STATE_APPROVED_ENABLED);

        List<OverlayInfo> list = mImpl.onGetOverlayInfosForTarget(target.packageName, userId);
        assertOrder(list, a, b, c);
        assertStates(list, STATE_APPROVED_ENABLED, STATE_APPROVED_ENABLED, STATE_APPROVED_DISABLED);

        // setup complete, begin overlay package upgrade
        uninstallOverlayPackage(b.packageName, userId);
        mImpl.onOverlayPackageUpgrading(b.packageName, userId);
        OverlayInfo oi = mImpl.onGetOverlayInfo(b.packageName, userId);
        assertNull(oi);

        list = mImpl.onGetOverlayInfosForTarget(target.packageName, userId);
        assertOrder(list, a, c);
        assertStates(list, STATE_APPROVED_ENABLED, STATE_APPROVED_DISABLED);

        // overlay package upgrade finished
        PackageInfo b2 = installOverlayPackage(b.packageName, b.overlayTarget, userId);
        mImpl.onOverlayPackageUpgraded(b2.packageName, userId);
        assertState(b2.packageName, userId, STATE_APPROVED_ENABLED);

        list = mImpl.onGetOverlayInfosForTarget(target.packageName, userId);
        assertOrder(list, a, b, c);
        assertStates(list, STATE_APPROVED_ENABLED, STATE_APPROVED_ENABLED, STATE_APPROVED_DISABLED);
    }

    public void testReorderIsDisabledDuringUpgrade() throws Exception {
        final int userId = USER_OWNER;

        PackageInfo a = installOverlayPackage("com.dummy.a", "com.dummy.target", userId);
        mImpl.onOverlayPackageAdded(a.packageName, userId);
        assertState(a.packageName, userId, STATE_NOT_APPROVED_MISSING_TARGET);

        PackageInfo b = installOverlayPackage("com.dummy.b", "com.dummy.target", userId);
        mImpl.onOverlayPackageAdded(b.packageName, userId);
        assertState(b.packageName, userId, STATE_NOT_APPROVED_MISSING_TARGET);

        PackageInfo c = installOverlayPackage("com.dummy.c", "com.dummy.target", userId);
        mImpl.onOverlayPackageAdded(c.packageName, userId);
        assertState(c.packageName, userId, STATE_NOT_APPROVED_MISSING_TARGET);

        List<OverlayInfo> list = mImpl.onGetOverlayInfosForTarget("com.dummy.target", userId);
        assertOrder(list, a, b, c);

        // begin overlay package upgrade
        uninstallOverlayPackage(b.packageName, userId);
        mImpl.onOverlayPackageUpgrading(b.packageName, userId);

        // while the upgrade is ongoing, attempt to reorder the overlays
        boolean status = mImpl.onSetPriority(b.packageName, c.packageName, userId);
        assertFalse(status);

        // finish overlay package upgrade
        b = installOverlayPackage(b.packageName, "com.dummy.target", userId);
        mImpl.onOverlayPackageUpgraded(b.packageName, userId);
        assertState(b.packageName, userId, STATE_NOT_APPROVED_MISSING_TARGET);

        list = mImpl.onGetOverlayInfosForTarget("com.dummy.target", userId);
        assertOrder(list, a, b, c);
    }

    private void assertOrder(List<OverlayInfo> list, PackageInfo... array) {
        assertEquals(list.size(), array.length);
        for (int i = 0; i < list.size(); i++) {
            OverlayInfo a = list.get(i);
            PackageInfo b = array[i];
            assertEquals(a.packageName, b.packageName);
        }
    }

    private void assertStates(List<OverlayInfo> list, int... array) {
        assertEquals(list.size(), array.length);
        for (int i = 0; i < list.size(); i++) {
            OverlayInfo a = list.get(i);
            int b = array[i];
            assertEquals(a.state, b);
        }
    }

    public void testSetEnabledStateNotApprovedComponentDisabled() throws Exception {
        final int userId = USER_OWNER;
        PackageInfo target = installTargetPackage("com.dummy.target", userId);
        mImpl.onTargetPackageAdded(target.packageName, userId);
        PackageInfo overlay = installOverlayPackage("com.dummy.overlay", target.packageName, userId,
                false, true, false, false, false);
        mImpl.onOverlayPackageAdded(overlay.packageName, userId);
        assertState(overlay.packageName, userId, STATE_NOT_APPROVED_COMPONENT_DISABLED);
        boolean status = mImpl.onSetEnabled(overlay.packageName, true, userId);
        assertTrue(status);
        assertState(overlay.packageName, userId, STATE_NOT_APPROVED_COMPONENT_DISABLED);
    }

    public void testSetEnabledStateNotApprovedMissingTarget() throws Exception {
        final int userId = USER_OWNER;
        PackageInfo overlay = installOverlayPackage("com.dummy.overlay", "com.dummy.target", userId,
                true, true, false, false, false);
        mImpl.onOverlayPackageAdded(overlay.packageName, userId);
        assertState(overlay.packageName, userId, STATE_NOT_APPROVED_MISSING_TARGET);
        boolean status = mImpl.onSetEnabled(overlay.packageName, true, userId);
        assertTrue(status);
        assertState(overlay.packageName, userId, STATE_NOT_APPROVED_MISSING_TARGET);
    }

    public void testSetEnabledStateNotApprovedNoIdmap() throws Exception {
        final int userId = USER_OWNER;
        PackageInfo target = installTargetPackage("com.dummy.target", userId);
        mImpl.onTargetPackageAdded(target.packageName, userId);
        PackageInfo overlay = installOverlayPackage("com.dummy.overlay", target.packageName, userId,
                true, true, false, false, true);
        mImpl.onOverlayPackageAdded(overlay.packageName, userId);
        assertState(overlay.packageName, userId, STATE_NOT_APPROVED_DANGEROUS_OVERLAY);
        boolean status = mImpl.onSetEnabled(overlay.packageName, true, userId);
        assertTrue(status);
        assertState(overlay.packageName, userId, STATE_NOT_APPROVED_DANGEROUS_OVERLAY);
    }

    public void testSetEnabledStateApproved() throws Exception {
        final int userId = USER_OWNER;
        PackageInfo target = installTargetPackage("com.dummy.target", userId);
        mImpl.onTargetPackageAdded(target.packageName, userId);
        PackageInfo overlay = installOverlayPackage("com.dummy.overlay", target.packageName, userId,
                true, true, false, false, false);
        mImpl.onOverlayPackageAdded(overlay.packageName, userId);
        assertState(overlay.packageName, userId, STATE_APPROVED_DISABLED);

        boolean status = mImpl.onSetEnabled(overlay.packageName, true, userId);
        assertTrue(status);
        assertState(overlay.packageName, userId, STATE_APPROVED_ENABLED);

        status = mImpl.onSetEnabled(overlay.packageName, true, userId);
        assertTrue(status);
        assertState(overlay.packageName, userId, STATE_APPROVED_ENABLED);

        status = mImpl.onSetEnabled(overlay.packageName, false, userId);
        assertTrue(status);
        assertState(overlay.packageName, userId, STATE_APPROVED_DISABLED);
    }

    // tests: AIDL interface

    public void testOnGetOverlaysForUser() throws Exception {
        Map<String, List<OverlayInfo>> map = mImpl.onGetOverlaysForUser(0);
        assertNotNull(map);
        assertTrue(map.isEmpty());

        PackageInfo a0 = installOverlayPackage("com.dummy.a", "com.dummy.target", 0);
        mImpl.onOverlayPackageAdded(a0.packageName, 0);

        PackageInfo b0 = installOverlayPackage("com.dummy.b", "com.dummy.target", 0);
        mImpl.onOverlayPackageAdded(b0.packageName, 0);

        PackageInfo a1 = installOverlayPackage("com.dummy.a", "com.dummy.target", 1);
        mImpl.onOverlayPackageAdded(a1.packageName, 1);

        map = mImpl.onGetOverlaysForUser(0);
        assertEquals(1, map.size());
        assertTrue(map.containsKey("com.dummy.target"));
        List<OverlayInfo> list = map.get("com.dummy.target");
        assertEquals(2, list.size());
    }

    // tests: switching or removing users

    public void testSwitchUserEmptyDatabase() throws Exception {
        Collection<String> targetPackages = mImpl.onSwitchUser(0);
        assertTrue(targetPackages.isEmpty());
    }

    public void testSwitchUser() throws Exception {
        installTargetPackage("com.dummy.target", 0);
        installTargetPackage("com.dummy.foo", 0);
        installTargetPackage("com.dummy.target", 1);

        PackageInfo a0 = installOverlayPackage("com.dummy.a", "com.dummy.target", 0);
        mImpl.onOverlayPackageAdded(a0.packageName, 0);

        PackageInfo b0 = installOverlayPackage("com.dummy.b", "com.dummy.target", 0);
        mImpl.onOverlayPackageAdded(b0.packageName, 0);

        PackageInfo c0 = installOverlayPackage("com.dummy.c", "com.dummy.foo", 0);
        mImpl.onOverlayPackageAdded(b0.packageName, 0);

        PackageInfo a1 = installOverlayPackage("com.dummy.a", "com.dummy.target", 1);
        mImpl.onOverlayPackageAdded(a1.packageName, 1);

        Collection<String> targetPackages = mImpl.onSwitchUser(0);
        assertEquals(2, targetPackages.size());
        assertTrue(targetPackages.contains("com.dummy.target"));
        assertTrue(targetPackages.contains("com.dummy.foo"));
    }

    public void testSwitchUserOverlayHasBeenRemoved() throws Exception {
        installTargetPackage("com.dummy.target", 0);
        installTargetPackage("com.dummy.target", 1);

        PackageInfo a0 = installOverlayPackage("com.dummy.a", "com.dummy.target", 0);
        mImpl.onOverlayPackageAdded(a0.packageName, 0);

        PackageInfo a1 = installOverlayPackage("com.dummy.a", "com.dummy.target", 1);
        mImpl.onOverlayPackageAdded(a1.packageName, 1);

        Collection<String> targetPackages = mImpl.onSwitchUser(0);
        assertEquals(1, targetPackages.size());
        assertTrue(targetPackages.contains("com.dummy.target"));

        targetPackages = mImpl.onSwitchUser(1);
        assertEquals(1, targetPackages.size());
        assertTrue(targetPackages.contains("com.dummy.target"));

        uninstallOverlayPackage(a0.packageName, 0);
        mImpl.onOverlayPackageRemoved(a0.packageName, 0);

        targetPackages = mImpl.onSwitchUser(0);
        assertTrue(targetPackages.isEmpty());
    }

    public void testSwitchUserANewOverlayHasBeenAddedToPMSButNotToOMS() throws Exception {
        final int userId = USER_OWNER;

        installTargetPackage("com.dummy.target", 0);
        PackageInfo overlay = installOverlayPackage("com.dummy.a", "com.dummy.target", userId);

        // Simulate that the package was installed for all users when
        // another user than userId was active, but do *not* tell mImpl about
        // the new overlay package. onSwitchUser should detect new packages itself.

        Collection<String> targetPackages = mImpl.onSwitchUser(userId);
        assertEquals(1, targetPackages.size());
        assertTrue(targetPackages.contains("com.dummy.target"));
    }

    public void testSwitchUserOverlayHasBeenUpgradedAndChangedTarget() throws Exception {
        final int userId = USER_OWNER;
        installTargetPackage("com.dummy.target", userId);
        installTargetPackage("com.dummy.other", userId);

        PackageInfo v1 = installOverlayPackage("com.dummy.a", "com.dummy.target", userId);
        mImpl.onOverlayPackageAdded(v1.packageName, userId);

        Collection<String> targetPackages = mImpl.onSwitchUser(userId);
        assertEquals(1, targetPackages.size());
        assertTrue(targetPackages.contains("com.dummy.target"));

        // upgrade com.dummy.a without telling OMS -- simulate that user 'userId'
        // isn't running
        uninstallOverlayPackage(v1.packageName, userId);
        PackageInfo v2 = installOverlayPackage(v1.packageName, "com.dummy.other", userId);

        // since com.dummy.target has had an overlay removed, and
        // com.dummy.other had an overlay added, both packages must refresh
        // during this user switch
        targetPackages = mImpl.onSwitchUser(userId);
        assertEquals(2, targetPackages.size());
        assertTrue(targetPackages.contains("com.dummy.target"));
        assertTrue(targetPackages.contains("com.dummy.other"));

        targetPackages = mImpl.onSwitchUser(userId);
        assertEquals(1, targetPackages.size());
        assertTrue(targetPackages.contains("com.dummy.other"));
    }

    public void testOverlayStateRetainedOnUserSwitch() throws Exception {
        PackageInfo t0 = installTargetPackage("com.dummy.target", 0);
        mImpl.onTargetPackageAdded(t0.packageName, 0);

        PackageInfo t1 = installTargetPackage("com.dummy.target", 1);
        mImpl.onTargetPackageAdded(t1.packageName, 1);

        PackageInfo o0 = installOverlayPackage("com.dummy.overlay", t0.packageName, 0);
        mImpl.onOverlayPackageAdded(o0.packageName, 0);
        assertState(o0.packageName, 0, STATE_APPROVED_DISABLED);

        PackageInfo o1 = installOverlayPackage("com.dummy.overlay", t1.packageName, 1);
        mImpl.onOverlayPackageAdded(o1.packageName, 1);
        assertState(o1.packageName, 1, STATE_APPROVED_DISABLED);

        Collection<String> targetPackages = mImpl.onSwitchUser(0);
        assertEquals(1, targetPackages.size());
        assertTrue(targetPackages.contains("com.dummy.target"));

        boolean status = mImpl.onSetEnabled(o0.packageName, true, 0);
        assertTrue(status);
        assertState(o0.packageName, 0, STATE_APPROVED_ENABLED);

        mImpl.onSwitchUser(1);
        assertState(o1.packageName, 1, STATE_APPROVED_DISABLED);

        mImpl.onSwitchUser(0);
        assertState(o1.packageName, 0, STATE_APPROVED_ENABLED);
    }

    public void testOnUserRemoved() throws Exception {
        final int userId = 1;

        PackageInfo o1 = installOverlayPackage("com.dummy.a", "com.dummy.target", userId);
        mImpl.onOverlayPackageAdded(o1.packageName, userId);

        PackageInfo o2 = installOverlayPackage("com.dummy.b", "com.dummy.target", userId);
        mImpl.onOverlayPackageAdded(o2.packageName, userId);

        List<OverlayInfo> overlays = mImpl.onGetOverlayInfosForTarget("com.dummy.target", userId);
        assertEquals(2, overlays.size());

        // now remove the user: there should be no overlays left
        mImpl.onUserRemoved(userId);
        overlays = mImpl.onGetOverlayInfosForTarget("com.dummy.target", userId);
        assertEquals(0, overlays.size());

        // old overlays (i.e. "com.dummy.b") shouldn't be resurrected
        mImpl.onOverlayPackageAdded(o1.packageName, userId);
        overlays = mImpl.onGetOverlayInfosForTarget("com.dummy.target", userId);
        assertEquals(1, overlays.size());
    }

    // tests: OMS <-> OMSImpl

    public void testGetAssetPaths() throws Exception {
        final int userId = USER_OWNER;
        PackageInfo targetPackage = installTargetPackage("com.dummy.target", userId);
        PackageInfo overlayPackage =
            installOverlayPackage("com.dummy.overlay", targetPackage.packageName, userId);

        // no overlays
        mImpl.onTargetPackageAdded(targetPackage.packageName, userId);
        String[] paths = mImpl.onGetAssetPaths(targetPackage.packageName, userId);
        assertEquals(1, paths.length);
        assertEquals(targetPackage.applicationInfo.getBaseCodePath(), paths[0]);

        // only disabled overlays
        mImpl.onOverlayPackageAdded(overlayPackage.packageName, userId);
        assertState(overlayPackage.packageName, userId, STATE_APPROVED_DISABLED);
        paths = mImpl.onGetAssetPaths(targetPackage.packageName, userId);
        assertEquals(1, paths.length);
        assertEquals(targetPackage.applicationInfo.getBaseCodePath(), paths[0]);

        // finally, overlays!
        mImpl.onSetEnabled(overlayPackage.packageName, true, userId);
        assertState(overlayPackage.packageName, userId, STATE_APPROVED_ENABLED);
        paths = mImpl.onGetAssetPaths(targetPackage.packageName, userId);
        assertEquals(2, paths.length);
        assertEquals(targetPackage.applicationInfo.getBaseCodePath(), paths[0]);
        assertEquals(overlayPackage.applicationInfo.getBaseCodePath(), paths[1]);
    }

    public void testGetAssetPathsBadInput() throws Exception {
        final int userId = USER_OWNER;
        PackageInfo target = installTargetPackage("com.dummy.target", userId);
        mImpl.onTargetPackageAdded(target.packageName, userId);
        PackageInfo overlay =
            installOverlayPackage("com.dummy.overlay", target.packageName, userId);
        mImpl.onOverlayPackageAdded(overlay.packageName, userId);
        mImpl.onSetEnabled(overlay.packageName, true, userId);
        assertState(overlay.packageName, userId, STATE_APPROVED_ENABLED);

        String[] paths = mImpl.onGetAssetPaths("package.does.not.exist", userId);
        assertNull(paths);

        paths = mImpl.onGetAssetPaths(overlay.packageName, userId);
        assertNull(paths);
    }

    // helper methods

    private PackageInfo installOverlayPackage(@NonNull String packageName,
            @NonNull String targetPackageName, int userId,
            boolean isEnabled, boolean idmapSuccess,
            boolean isSystem, boolean isMatchingSignatures,
            boolean isDangerous) throws Exception {
        assertNotNull(packageName);
        assertNotNull(targetPackageName);

        ApplicationInfo ai = new ApplicationInfo();
        ai.sourceDir = String.format("/data/app/%s-1/base.apk", packageName);
        ai.enabled = isEnabled;
        if (isSystem) {
            ai.flags |= ApplicationInfo.FLAG_SYSTEM;
        }

        PackageInfo pi = new PackageInfo();
        pi.applicationInfo = ai;
        pi.packageName = packageName;
        pi.overlayTarget = targetPackageName;

        when(mIdmapManager.createIdmap(eq(pi), any(PackageInfo.class), eq(userId))).
            thenReturn(idmapSuccess);

        when(mIdmapManager.idmapExists(eq(pi), eq(userId))).thenReturn(idmapSuccess);

        mPackageManager.fakeGetPackageInfo(pi.packageName, userId, pi);

        mPackageManager.fakeSignaturesMatching(pi.packageName, isMatchingSignatures);

        when(mIdmapManager.isDangerous(eq(pi), anyInt())).thenReturn(isDangerous);

        return pi;
    }

    private PackageInfo installOverlayPackage(@NonNull String packageName,
            @NonNull String targetPackageName, int userId) throws Exception {
        return installOverlayPackage(packageName, targetPackageName, userId,
                true, true, true, true, false);
    }

    private PackageInfo installTargetPackage(@NonNull String packageName, int userId)
            throws Exception {
        assertNotNull(packageName);

        ApplicationInfo ai = new ApplicationInfo();
        ai.sourceDir = String.format("/data/app/%s-1/base.apk", packageName);

        PackageInfo pi = new PackageInfo();
        pi.applicationInfo = ai;
        pi.packageName = packageName;
        pi.overlayTarget = null;

        mPackageManager.fakeGetPackageInfo(pi.packageName, userId, pi);

        return pi;
    }

    private void uninstallOverlayPackage(@NonNull String packageName, int userId) throws Exception {
        mPackageManager.forgetGetPackageInfo(packageName, userId);
    }

    private void uninstallTargetPackage(@NonNull String packageName, int userId) throws Exception {
        mPackageManager.forgetGetPackageInfo(packageName, userId);
    }

    private void assertState(@NonNull String packageName, int userId, int expectedState) {
        OverlayInfo oi = mImpl.onGetOverlayInfo(packageName, userId);
        assertNotNull(oi);
        String msg = String.format("expected %s but was %s:",
                stateToString(expectedState), stateToString(oi.state));
        assertEquals(msg, expectedState, oi.state);
    }

    /*
     * Package protected methods in OMSImpl missing test cases:
     *
     * List<String> onSwitchUser(int newUserId) {
     * void onUserRemoved(int userId) {
     *
     * boolean onSetPriority(@NonNull String packageName, @NonNull String newParentPackageName,
     * boolean onSetHighestPriority(@NonNull String packageName, int userId) {
     * boolean onSetLowestPriority(@NonNull String packageName, int userId) {
     */

    private static class PackageManagerHelper implements OverlayManagerServiceImpl.PackageManagerHelper {
        class Id {
            final String packageName;
            final int userId;

            Id(@NonNull String packageName, int userId) {
                this.packageName = packageName;
                this.userId = userId;
            }

            @Override
            public int hashCode() {
                final int prime = 31;
                int result = 1;
                result = prime * result + ((packageName == null) ? 0 : packageName.hashCode());
                result = prime * result + userId;
                return result;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                Id other = (Id) obj;
                return packageName.equals(other.packageName) && userId == other.userId;
            }
        }

        private Map<Id, PackageInfo> mInstalledPackages = new ArrayMap<>();
        private Map<String, Boolean> mMatchingSignatures = new ArrayMap<>();

        @Override
        public PackageInfo getPackageInfo(@NonNull String packageName, int userId) {
            Id id = new Id(packageName, userId);
            if (mInstalledPackages.containsKey(id)) {
                return mInstalledPackages.get(id);
            }
            return null;
        }

        @Override
        public boolean signaturesMatching(@NonNull String packageName1, @NonNull String packageName2,
                int userId) {
            if (mMatchingSignatures.containsKey(packageName1)) {
                return mMatchingSignatures.get(packageName1);
            }
            if (mMatchingSignatures.containsKey(packageName2)) {
                return mMatchingSignatures.get(packageName2);
            }
            return false;
        }

        @Override
        public List<PackageInfo> getOverlayPackages(int userId) {
            List<PackageInfo> overlays = new ArrayList<>();
            for (Id id : mInstalledPackages.keySet()) {
                if (id.userId != userId) {
                    continue;
                }
                PackageInfo pi = mInstalledPackages.get(id);
                if (pi.overlayTarget == null) {
                    continue;
                }
                overlays.add(pi);
            }
            return overlays;
        }

        public void fakeGetPackageInfo(@NonNull String packageName, int userId,
                @NonNull PackageInfo resultToReturn) {
            Id id = new Id(packageName, userId);
            mInstalledPackages.put(id, resultToReturn);
        }

        public void fakeSignaturesMatching(@NonNull String packageName, boolean resultToReturn) {
            mMatchingSignatures.put(packageName, resultToReturn);
        }

        public void forgetGetPackageInfo(@NonNull String packageName, int userId) {
            Id id = new Id(packageName, userId);
            mInstalledPackages.remove(id);
            mMatchingSignatures.remove(packageName);
        }
    }

    private static class DatabaseChangeListener implements OverlayManagerDatabase.ChangeListener {
        @Override
        public void onDatabaseChanged() {}

        @Override
        public void onOverlayAdded(@NonNull OverlayInfo oi) {
            assertNotNull(oi);
        }

        @Override
        public void onOverlayRemoved(@NonNull OverlayInfo oi) {
            assertNotNull(oi);
        }

        @Override
        public void onOverlayChanged(@NonNull OverlayInfo oi, @NonNull OverlayInfo oldOi) {
            assertNotNull(oi);
            assertNotNull(oldOi);
        }

        @Override
        public void onOverlayPriorityChanged(@NonNull OverlayInfo oi) {
            assertNotNull(oi);
        }
    }
}
