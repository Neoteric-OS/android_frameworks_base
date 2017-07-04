package com.android.server.om;

import static android.content.om.OverlayInfo.STATE_DISABLED;
import static android.content.om.OverlayInfo.STATE_ENABLED;
import static android.content.om.OverlayInfo.STATE_MISSING_TARGET;
import static android.content.om.OverlayInfo.STATE_NO_IDMAP;
import static android.content.om.OverlayInfo.STATE_OVERLAY_UPGRADING;
import static android.content.om.OverlayInfo.STATE_TARGET_UPGRADING;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.annotation.NonNull;
import android.content.om.OverlayInfo;
import android.support.test.runner.AndroidJUnit4;
import android.util.ArraySet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OverlayManagerTests {
    private OverlayManagerServiceImpl mImpl;
    private DummyState mState;
    private DummyListener mListener;

    private static final String OVERLAY = "com.dummy.overlay";
    private static final String TARGET = "com.dummy.target";
    private static final int USER = 0;

    @Before
    public void setUp() throws Exception {
        mState = new DummyState();
        mListener = new DummyListener();
        mImpl = new OverlayManagerServiceImpl(new DummyPackageManagerHelper(mState),
                new DummyIdmapManager(mState),
                new OverlayManagerSettings(),
                new ArraySet<>(),
                mListener);
    }

    // tests: basics

    @Test
    public void testGetOverlayInfo() throws Exception {
        installOverlayPackage(OVERLAY, TARGET, USER, false);
        final OverlayInfo oi = mImpl.getOverlayInfo(OVERLAY, USER);
        assertNotNull(oi);
        assertEquals(oi.packageName, OVERLAY);
        assertEquals(oi.targetPackageName, TARGET);
        assertEquals(oi.userId, USER);
    }

    @Test
    public void testGetOverlayInfosForTarget() throws Exception {
        final String OVERLAY2 = OVERLAY + "2";
        final String OVERLAY3 = OVERLAY + "3";
        final int USER2 = USER + 1;
        final int USER3 = USER2 + 1;

        installOverlayPackage(OVERLAY, TARGET, USER, false);
        installOverlayPackage(OVERLAY2, TARGET, USER, false);

        installOverlayPackage(OVERLAY3, TARGET, USER2, false);

        final List<OverlayInfo> ois = mImpl.getOverlayInfosForTarget(TARGET, USER);
        assertEquals(ois.size(), 2);
        assertTrue(ois.contains(mImpl.getOverlayInfo(OVERLAY, USER)));
        assertTrue(ois.contains(mImpl.getOverlayInfo(OVERLAY2, USER)));

        final List<OverlayInfo> ois2 = mImpl.getOverlayInfosForTarget(TARGET, USER2);
        assertEquals(ois2.size(), 1);
        assertTrue(ois2.contains(mImpl.getOverlayInfo(OVERLAY3, USER2)));

        final List<OverlayInfo> ois3 = mImpl.getOverlayInfosForTarget(TARGET, USER3);
        assertNotNull(ois3);
        assertEquals(ois3.size(), 0);

        final List<OverlayInfo> ois4 = mImpl.getOverlayInfosForTarget("no.such.overlay", USER);
        assertNotNull(ois4);
        assertEquals(ois4.size(), 0);
    }

    @Test
    public void testGetOverlayInfosForUser() throws Exception {
        final String OVERLAY2 = OVERLAY + "2";
        final String OVERLAY3 = OVERLAY + "3";
        final String TARGET2 = TARGET + "2";
        final int USER2 = USER + 1;

        installOverlayPackage(OVERLAY, TARGET, USER, false);
        installOverlayPackage(OVERLAY2, TARGET, USER, false);
        installOverlayPackage(OVERLAY3, TARGET2, USER, false);

        final Map<String, List<OverlayInfo>> everything = mImpl.getOverlaysForUser(USER);
        assertEquals(everything.size(), 2);

        final List<OverlayInfo> ois = everything.get(TARGET);
        assertNotNull(ois);
        assertEquals(ois.size(), 2);
        assertTrue(ois.contains(mImpl.getOverlayInfo(OVERLAY, USER)));
        assertTrue(ois.contains(mImpl.getOverlayInfo(OVERLAY2, USER)));

        final List<OverlayInfo> ois2 = everything.get(TARGET2);
        assertNotNull(ois2);
        assertEquals(ois2.size(), 1);
        assertTrue(ois2.contains(mImpl.getOverlayInfo(OVERLAY3, USER)));

        final Map<String, List<OverlayInfo>> everything2 = mImpl.getOverlaysForUser(USER2);
        assertNotNull(everything2);
        assertEquals(everything2.size(), 0);
    }

    @Test
    public void testPriority() throws Exception {
        final String OVERLAY2 = OVERLAY + "2";
        final String OVERLAY3 = OVERLAY + "3";

        installOverlayPackage(OVERLAY, TARGET, USER, false);
        installOverlayPackage(OVERLAY2, TARGET, USER, false);
        installOverlayPackage(OVERLAY3, TARGET, USER, false);

        final OverlayInfo o1 = mImpl.getOverlayInfo(OVERLAY, USER);
        final OverlayInfo o2 = mImpl.getOverlayInfo(OVERLAY2, USER);
        final OverlayInfo o3 = mImpl.getOverlayInfo(OVERLAY3, USER);

        assertOverlayInfoList(TARGET, USER, o1, o2, o3);

        assertTrue(mImpl.setLowestPriority(OVERLAY3, USER));
        assertOverlayInfoList(TARGET, USER, o3, o1, o2);

        assertTrue(mImpl.setHighestPriority(OVERLAY3, USER));
        assertOverlayInfoList(TARGET, USER, o1, o2, o3);

        assertTrue(mImpl.setPriority(OVERLAY, OVERLAY2, USER));
        assertOverlayInfoList(TARGET, USER, o2, o1, o3);
    }

    @Test
    public void testOverlayInfoStateTransitions() throws Exception {
        assertNull(mImpl.getOverlayInfo(OVERLAY, USER));

        installOverlayPackage(OVERLAY, TARGET, USER, true);
        assertState(STATE_MISSING_TARGET, OVERLAY, USER);

        installTargetPackage(TARGET, USER);
        assertState(STATE_DISABLED, OVERLAY, USER);

        mImpl.setEnabled(OVERLAY, true, USER);
        assertState(STATE_ENABLED, OVERLAY, USER);

        beginUpgradeTargetPackage(TARGET, USER);
        assertState(STATE_TARGET_UPGRADING, OVERLAY, USER);

        endUpgradeTargetPackage(TARGET, USER);
        assertState(STATE_ENABLED, OVERLAY, USER);

        uninstallTargetPackage(TARGET, USER);
        assertState(STATE_MISSING_TARGET, OVERLAY, USER);

        installTargetPackage(TARGET, USER);
        assertState(STATE_ENABLED, OVERLAY, USER);
    }

    // tests: listener interface

    @Test
    public void testListener() throws Exception {
        installOverlayPackage(OVERLAY, TARGET, USER, true);
        assertEquals(1, mListener.count);
        mListener.count = 0;

        installTargetPackage(TARGET, USER);
        assertEquals(1, mListener.count);
        mListener.count = 0;

        mImpl.setEnabled(OVERLAY, true, USER);
        assertEquals(1, mListener.count);
        mListener.count = 0;

        mImpl.setEnabled(OVERLAY, true, USER);
        assertEquals(0, mListener.count);
    }

    // tests: overlay installation and removal

    @Test
    public void testUninstallOverlay() throws Exception {
        assertNull(mImpl.getOverlayInfo(OVERLAY, USER));

        installOverlayPackage(OVERLAY, TARGET, USER, false);
        assertNotNull(mImpl.getOverlayInfo(OVERLAY, USER));

        uninstallOverlayPackage(OVERLAY, USER);
        assertNull(mImpl.getOverlayInfo(OVERLAY, USER));
    }

    @Test
    public void testUpgradeOverlay() throws Exception {
        installOverlayPackage(OVERLAY, TARGET, USER, true);
        installTargetPackage(TARGET, USER);
        mImpl.setEnabled(OVERLAY, true, USER);
        assertState(STATE_ENABLED, OVERLAY, USER);

        beginUpgradeOverlayPackage(OVERLAY, USER);
        assertState(STATE_OVERLAY_UPGRADING, OVERLAY, USER);

        endUpgradeOverlayPackage(OVERLAY, TARGET, USER, true);
        assertState(STATE_ENABLED, OVERLAY, USER);

        beginUpgradeOverlayPackage(OVERLAY, USER);
        assertState(STATE_OVERLAY_UPGRADING, OVERLAY, USER);

        endUpgradeOverlayPackage(OVERLAY, TARGET, USER, false);
        assertState(STATE_NO_IDMAP, OVERLAY, USER);
    }

    @Test
    public void testUpgradeSneakyOverlay() throws Exception {
        final String otherTarget = "some.other.target";
        installTargetPackage(otherTarget, USER);

        installOverlayPackage(OVERLAY, TARGET, USER, true);
        installTargetPackage(TARGET, USER);
        mImpl.setEnabled(OVERLAY, true, USER);
        assertState(STATE_ENABLED, OVERLAY, USER);

        beginUpgradeOverlayPackage(OVERLAY, USER);
        assertState(STATE_OVERLAY_UPGRADING, OVERLAY, USER);

        // changing the overlay's target as part of an upgrade should be the
        // same as uninstalling the overlay and installing the new version;
        // especially the OverlayInfo's target should be updated and it should
        // not be enabled
        endUpgradeOverlayPackage(OVERLAY, otherTarget, USER, true);
        assertState(STATE_DISABLED, OVERLAY, USER);
        final OverlayInfo oi = mImpl.getOverlayInfo(OVERLAY, USER);
        assertEquals(otherTarget, oi.targetPackageName);
    }

    // helper methods

    private void assertState(int expected, final String overlayPackageName, int userId) {
        int actual = mImpl.getOverlayInfo(OVERLAY, USER).state;
        String msg = String.format("expected %s but was %s:",
                OverlayInfo.stateToString(expected), OverlayInfo.stateToString(actual));
        assertEquals(msg, expected, actual);
    }

    private void assertOverlayInfoList(final String targetPackageName, int userId,
            OverlayInfo... overlayInfos) {
        final List<OverlayInfo> expected =
                mImpl.getOverlayInfosForTarget(targetPackageName, userId);
        final List<OverlayInfo> actual = Arrays.asList(overlayInfos);
        assertEquals(expected, actual);
    }

    private void installTargetPackage(String packageName, int userId) {
        if (mState.select(packageName, userId) != null) {
            throw new IllegalStateException("package already installed");
        }
        mState.add(packageName, null, userId, false);
        mImpl.onTargetPackageAdded(packageName, userId);
    }

    private void beginUpgradeTargetPackage(String packageName, int userId) {
        if (mState.select(packageName, userId) == null) {
            throw new IllegalStateException("package not installed");
        }
        mState.add(packageName, null, userId, false);
        mImpl.onTargetPackageUpgrading(packageName, userId);
    }

    private void endUpgradeTargetPackage(String packageName, int userId) {
        if (mState.select(packageName, userId) == null) {
            throw new IllegalStateException("package not installed");
        }
        mState.add(packageName, null, userId, false);
        mImpl.onTargetPackageUpgraded(packageName, userId);
    }

    private void uninstallTargetPackage(String packageName, int userId) {
        if (mState.select(packageName, userId) == null) {
            throw new IllegalStateException("package not installed");
        }
        mState.remove(packageName, userId);
        mImpl.onTargetPackageRemoved(packageName, userId);
    }

    private void installOverlayPackage(String packageName, String targetPackageName, int userId,
            boolean canCreateIdmap) {
        if (mState.select(packageName, userId) != null) {
            throw new IllegalStateException("package already installed");
        }
        mState.add(packageName, targetPackageName, userId, canCreateIdmap);
        mImpl.onOverlayPackageAdded(packageName, userId);
    }

    private void beginUpgradeOverlayPackage(String packageName, int userId) {
        if (mState.select(packageName, userId) == null) {
            throw new IllegalStateException("package not installed");
        }
        mState.add(packageName, null, userId, false);
        mImpl.onOverlayPackageUpgrading(packageName, userId);
    }

    private void endUpgradeOverlayPackage(String packageName, String targetPackageName, int userId,
            boolean canCreateIdmap) {
        if (mState.select(packageName, userId) == null) {
            throw new IllegalStateException("package not installed");
        }
        mState.add(packageName, targetPackageName, userId, canCreateIdmap);
        mImpl.onOverlayPackageUpgraded(packageName, userId);
    }

    private void uninstallOverlayPackage(String packageName, int userId) {
        if (mState.select(packageName, userId) == null) {
            throw new IllegalStateException("package not installed");
        }
        mState.remove(packageName, userId);
        mImpl.onOverlayPackageRemoved(packageName, userId);
    }

    private static final class DummyState {
        private List<Package> mPackages = new ArrayList<>();

        public void add(String packageName, String targetPackageName, int userId,
                boolean canCreateIdmap) {
            remove(packageName, userId);
            Package pkg = new Package();
            pkg.packageName = packageName;
            pkg.targetPackageName = targetPackageName;
            pkg.userId = userId;
            pkg.canCreateIdmap = canCreateIdmap;
            mPackages.add(pkg);
        }

        public void remove(String packageName, int userId) {
            final Iterator<Package> iter = mPackages.iterator();
            while (iter.hasNext()) {
                final Package pkg = iter.next();
                if (pkg.packageName.equals(packageName) && pkg.userId == userId) {
                    iter.remove();
                    return;
                }
            }
        }

        public List<Package> select(int userId) {
            List<Package> out = new ArrayList<>();
            final int N = mPackages.size();
            for (int i = 0; i < N; i++) {
                final Package pkg = mPackages.get(i);
                if (pkg.userId == userId) {
                    out.add(pkg);
                }
            }
            return out;
        }

        public Package select(String packageName, int userId) {
            final int N = mPackages.size();
            for (int i = 0; i < N; i++) {
                final Package pkg = mPackages.get(i);
                if (pkg.packageName.equals(packageName) && pkg.userId == userId) {
                    return pkg;
                }
            }
            return null;
        }

        private static final class Package {
            String packageName;
            int userId;
            String targetPackageName;
            boolean canCreateIdmap;
        }
    }

    private static final class DummyPackageManagerHelper implements
            OverlayManagerServiceImpl.PackageManagerHelper {
        private final DummyState mState;

        public DummyPackageManagerHelper(DummyState state) {
            mState = state;
        }

        @Override
        public PackageInfoLite getPackageInfoLite(@NonNull String packageName, int userId) {
            final DummyState.Package pkg = mState.select(packageName, userId);
            if (pkg == null) {
                return null;
            }
            String codePath = String.format("%s/%s/base.apk",
                    pkg.targetPackageName == null ? "/system/app/" : "/vendor/overlay/",
                    pkg.packageName);
            PackageInfoLite pil = new PackageInfoLite(pkg.packageName, pkg.targetPackageName,
                    USER, false, 0, codePath);
            return pil;
        }

        @Override
        public boolean signaturesMatching(@NonNull String packageName1,
                @NonNull String packageName2, int userId) {
            return true;
        }

        @Override
        public List<PackageInfoLite> getOverlayPackages(int userId) {
            List<PackageInfoLite> out = new ArrayList<>();
            final List<DummyState.Package> pkgs = mState.select(userId);
            final int N = pkgs.size();
            for (int i = 0; i < N; i++) {
                final DummyState.Package pkg = pkgs.get(i);
                if (pkg.targetPackageName != null) {
                    out.add(getPackageInfoLite(pkg.packageName, pkg.userId));
                }
            }
            return out;
        }
    }

    private static class DummyIdmapManager extends IdmapManager {
        private final DummyState mState;
        private Set<String> mIdmapFiles = new ArraySet<>();

        DummyIdmapManager(DummyState state) {
            super(null);
            mState = state;
        }

        @Override
        boolean createIdmap(@NonNull final PackageInfoLite targetPackage,
                @NonNull final PackageInfoLite overlayPackage, int userId) {
            final DummyState.Package t = mState.select(targetPackage.packageName, userId);
            if (t == null) {
                return false;
            }
            final DummyState.Package o = mState.select(overlayPackage.packageName, userId);
            if (o == null) {
                return false;
            }
            if (!o.canCreateIdmap) {
                return false;
            }
            final String key = createKey(overlayPackage.packageName, userId);
            mIdmapFiles.add(key);
            return true;
        }

        @Override
        boolean removeIdmap(@NonNull final OverlayInfo oi, final int userId) {
            final String key = createKey(oi.packageName, oi.userId);
            if (!mIdmapFiles.contains(key)) {
                return false;
            }
            mIdmapFiles.remove(key);
            return true;
        }

        @Override
        boolean idmapExists(@NonNull final OverlayInfo oi) {
            final String key = createKey(oi.packageName, oi.userId);
            return mIdmapFiles.contains(key);
        }

        @Override
        boolean idmapExists(@NonNull final PackageInfoLite overlayPackage, final int userId) {
            final String key = createKey(overlayPackage.packageName, userId);
            return mIdmapFiles.contains(key);
        }

        private String createKey(@NonNull final String packageName, final int userId) {
            return String.format("%s:%d", packageName, userId);
        }
    }

    private static class DummyListener implements OverlayManagerServiceImpl.OverlayChangeListener {
        public int count;

        public void onOverlaysChanged(@NonNull String targetPackage, int userId) {
            count++;
        }
    }
}
