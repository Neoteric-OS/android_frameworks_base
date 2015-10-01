
package com.android.server.om;

import static android.content.om.OverlayInfo.STATE_APPROVED_ALWAYS_ENABLED;
import static android.content.om.OverlayInfo.STATE_APPROVED_DISABLED;
import static android.content.om.OverlayInfo.STATE_APPROVED_ENABLED;
import static android.content.om.OverlayInfo.STATE_NOT_APPROVED_COMPONENT_DISABLED;
import static android.content.om.OverlayInfo.STATE_NOT_APPROVED_DANGEROUS_OVERLAY;
import static android.content.om.OverlayInfo.STATE_NOT_APPROVED_MISSING_TARGET;
import static android.content.om.OverlayInfo.STATE_NOT_APPROVED_NO_IDMAP;
import static android.os.UserHandle.USER_OWNER;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import android.content.om.OverlayInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.test.AndroidTestCase;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RulesTest extends AndroidTestCase {
    private Rules mRules;

    @Mock
    private IPackageManager pm;

    @Mock
    private MockIdmapManager idmap;

    @Override
    protected void setUp() throws Exception {
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().getPath());
        MockitoAnnotations.initMocks(this);

        // Let the mocks approve all overlays default
        when(pm.checkSignatures(any(String.class), any(String.class))).thenReturn(PackageManager.SIGNATURE_MATCH);

        when(idmap.idmapExists(any(PackageInfo.class))).thenReturn(TRUE);
        when(idmap.isDangerous(any(PackageInfo.class))).thenReturn(FALSE);

        mRules = new Rules(pm, idmap);
    }

    @Override
    protected void tearDown() throws Exception {
    }

    public void testGetInitialState() throws Exception {
        // Expected state, dangerous, overlay is system, target installed, signature match, overlay component enabled
        assertInitialState(STATE_APPROVED_ALWAYS_ENABLED,         true,  true,  true,  true,  true);
        assertInitialState(STATE_NOT_APPROVED_COMPONENT_DISABLED, true,  true,  true,  true,  false);
        assertInitialState(STATE_APPROVED_ALWAYS_ENABLED,         true,  true,  true,  false, true);
        assertInitialState(STATE_NOT_APPROVED_COMPONENT_DISABLED, true,  true,  true,  false, false);
        assertInitialState(STATE_NOT_APPROVED_MISSING_TARGET,     true,  true,  false, true,  true);
        assertInitialState(STATE_NOT_APPROVED_COMPONENT_DISABLED, true,  true,  false, true,  false);
        assertInitialState(STATE_NOT_APPROVED_MISSING_TARGET,     true,  true,  false, false, true);
        assertInitialState(STATE_NOT_APPROVED_COMPONENT_DISABLED, true,  true,  false, false, false);
        assertInitialState(STATE_APPROVED_DISABLED,               true,  false, true,  true,  true);
        assertInitialState(STATE_NOT_APPROVED_COMPONENT_DISABLED, true,  false, true,  true,  false);
        assertInitialState(STATE_NOT_APPROVED_DANGEROUS_OVERLAY,  true,  false, true,  false, true);
        assertInitialState(STATE_NOT_APPROVED_COMPONENT_DISABLED, true,  false, true,  false, false);
        assertInitialState(STATE_NOT_APPROVED_MISSING_TARGET,     true,  false, false, true,  true);
        assertInitialState(STATE_NOT_APPROVED_COMPONENT_DISABLED, true,  false, false, true,  false);
        assertInitialState(STATE_NOT_APPROVED_MISSING_TARGET,     true,  false, false, false, true);
        assertInitialState(STATE_NOT_APPROVED_COMPONENT_DISABLED, true,  false, false, false, false);
        assertInitialState(STATE_APPROVED_ALWAYS_ENABLED,         false, true,  true,  true,  true);
        assertInitialState(STATE_NOT_APPROVED_COMPONENT_DISABLED, false, true,  true,  true,  false);
        assertInitialState(STATE_APPROVED_ALWAYS_ENABLED,         false, true,  true,  false, true);
        assertInitialState(STATE_NOT_APPROVED_COMPONENT_DISABLED, false, true,  true,  false, false);
        assertInitialState(STATE_NOT_APPROVED_MISSING_TARGET,     false, true,  false, true,  true);
        assertInitialState(STATE_NOT_APPROVED_COMPONENT_DISABLED, false, true,  false, true,  false);
        assertInitialState(STATE_NOT_APPROVED_MISSING_TARGET,     false, true,  false, false, true);
        assertInitialState(STATE_NOT_APPROVED_COMPONENT_DISABLED, false, true,  false, false, false);
        assertInitialState(STATE_APPROVED_DISABLED,               false, false, true,  true,  true);
        assertInitialState(STATE_NOT_APPROVED_COMPONENT_DISABLED, false, false, true,  true,  false);
        assertInitialState(STATE_APPROVED_DISABLED,               false, false, true,  false, true);
        assertInitialState(STATE_NOT_APPROVED_COMPONENT_DISABLED, false, false, true,  false, false);
        assertInitialState(STATE_NOT_APPROVED_MISSING_TARGET,     false, false, false, true,  true);
        assertInitialState(STATE_NOT_APPROVED_COMPONENT_DISABLED, false, false, false, true,  false);
        assertInitialState(STATE_NOT_APPROVED_MISSING_TARGET,     false, false, false, false, true);
        assertInitialState(STATE_NOT_APPROVED_COMPONENT_DISABLED, false, false, false, false, false);
    }

    /**
     * Verify that the overlay package gets the correct updated state when the
     * target package is uninstalled.
     */
    public void testGetUpdatedStateTargetUninstalled() throws Exception {
        // Expected state, overlay is system, signature match, overlay component enabled
        assertStateTargetUninstalled(STATE_NOT_APPROVED_MISSING_TARGET,     true,  true,  true,  true);
        assertStateTargetUninstalled(STATE_NOT_APPROVED_COMPONENT_DISABLED, true,  true,  true,  false);
        assertStateTargetUninstalled(STATE_NOT_APPROVED_MISSING_TARGET,     true,  true,  false, true);
        assertStateTargetUninstalled(STATE_NOT_APPROVED_COMPONENT_DISABLED, true,  true,  false, false);
        assertStateTargetUninstalled(STATE_NOT_APPROVED_MISSING_TARGET,     true,  false, true,  true);
        assertStateTargetUninstalled(STATE_NOT_APPROVED_COMPONENT_DISABLED, true,  false, true,  false);
        assertStateTargetUninstalled(STATE_NOT_APPROVED_MISSING_TARGET,     true,  false, false, true);
        assertStateTargetUninstalled(STATE_NOT_APPROVED_COMPONENT_DISABLED, true,  false, false, false);
        assertStateTargetUninstalled(STATE_NOT_APPROVED_MISSING_TARGET,     false, true,  true,  true);
        assertStateTargetUninstalled(STATE_NOT_APPROVED_COMPONENT_DISABLED, false, true,  true,  false);
        assertStateTargetUninstalled(STATE_NOT_APPROVED_MISSING_TARGET,     false, true,  false, true);
        assertStateTargetUninstalled(STATE_NOT_APPROVED_COMPONENT_DISABLED, false, true,  false, false);
        assertStateTargetUninstalled(STATE_NOT_APPROVED_MISSING_TARGET,     false, false, true,  true);
        assertStateTargetUninstalled(STATE_NOT_APPROVED_COMPONENT_DISABLED, false, false, true,  false);
        assertStateTargetUninstalled(STATE_NOT_APPROVED_MISSING_TARGET,     false, false, false, true);
        assertStateTargetUninstalled(STATE_NOT_APPROVED_COMPONENT_DISABLED, false, false, false, false);
    }

    /**
     * Verify that the overlay package gets the correct updated state when the
     * target package is installed.
     */
    public void testGetUpdatedStateTargetInstalled() throws Exception {
        // Expected state, dangerous, overlay is system, signature match, overlay component enabled
        assertStateTargetInstalled(STATE_APPROVED_ALWAYS_ENABLED,         true,  true,  true,  true);
        assertStateTargetInstalled(STATE_NOT_APPROVED_COMPONENT_DISABLED, true,  true,  true,  false);
        assertStateTargetInstalled(STATE_APPROVED_ALWAYS_ENABLED,         true,  true,  false, true);
        assertStateTargetInstalled(STATE_NOT_APPROVED_COMPONENT_DISABLED, true,  true,  false, false);
        assertStateTargetInstalled(STATE_APPROVED_DISABLED,               true,  false, true,  true);
        assertStateTargetInstalled(STATE_NOT_APPROVED_COMPONENT_DISABLED, true,  false, true,  false);
        assertStateTargetInstalled(STATE_NOT_APPROVED_DANGEROUS_OVERLAY,  true,  false, false, true);
        assertStateTargetInstalled(STATE_NOT_APPROVED_COMPONENT_DISABLED, true,  false, false, false);
        assertStateTargetInstalled(STATE_APPROVED_ALWAYS_ENABLED,         false, true,  true,  true);
        assertStateTargetInstalled(STATE_NOT_APPROVED_COMPONENT_DISABLED, false, true,  true,  false);
        assertStateTargetInstalled(STATE_APPROVED_ALWAYS_ENABLED,         false, true,  false, true);
        assertStateTargetInstalled(STATE_NOT_APPROVED_COMPONENT_DISABLED, false, true,  false, false);
        assertStateTargetInstalled(STATE_APPROVED_DISABLED,               false, false, true,  true);
        assertStateTargetInstalled(STATE_NOT_APPROVED_COMPONENT_DISABLED, false, false, true,  false);
        assertStateTargetInstalled(STATE_APPROVED_DISABLED,               false, false, false, true);
        assertStateTargetInstalled(STATE_NOT_APPROVED_COMPONENT_DISABLED, false, false, false, false);
    }


    /**
     * Verify that the rules return the correct state when we try to enable or disable an
     * overlay.
     * If the Rules the reject to enable/disable an overlay, it's current state should be returned.
     *
     * @throws Exception
     */
    public void testGetUpdatedStateEnable() throws Exception {
        int states[][] = new int[][]{

            // State before calling setEnable,      Expected state after enable            Expected state after disable
            {STATE_NOT_APPROVED_COMPONENT_DISABLED, STATE_NOT_APPROVED_COMPONENT_DISABLED, STATE_NOT_APPROVED_COMPONENT_DISABLED},
            {STATE_NOT_APPROVED_MISSING_TARGET,     STATE_NOT_APPROVED_MISSING_TARGET,     STATE_NOT_APPROVED_MISSING_TARGET},
            {STATE_NOT_APPROVED_NO_IDMAP,           STATE_NOT_APPROVED_NO_IDMAP,           STATE_NOT_APPROVED_NO_IDMAP},
            {STATE_NOT_APPROVED_DANGEROUS_OVERLAY,  STATE_NOT_APPROVED_DANGEROUS_OVERLAY,  STATE_NOT_APPROVED_DANGEROUS_OVERLAY},
            {STATE_APPROVED_DISABLED,               STATE_APPROVED_ENABLED,                STATE_APPROVED_DISABLED},
            {STATE_APPROVED_ENABLED,                STATE_APPROVED_ENABLED,                STATE_APPROVED_DISABLED},
            {STATE_APPROVED_ALWAYS_ENABLED,         STATE_APPROVED_ALWAYS_ENABLED,         STATE_APPROVED_ALWAYS_ENABLED},
        };
        for (int[] stateArray : states) {
            assertStateOnEnableDisable(stateArray[0], stateArray[1], stateArray[2]);
        }
    }

    /**
     * Assert that Rules return the correct updated state when trying to enable
     * and disabling overlays
     *
     * @param currentState the state of the overlay before trying to
     *            enable/disable it
     * @param expectedStateAfterEnable what state we can expect after enabling
     *            an overlay with the currentState
     * @param expectedStateAfterDisable what state we can expect after disabling
     *            an overlay with the currentState
     */
    private void assertStateOnEnableDisable(int currentState, int expectedStateAfterEnable,
            int expectedStateAfterDisable) {
        OverlayInfo overlay = createOverlayInfo(currentState);
        assertSame(expectedStateAfterEnable, mRules.getUpdatedState(overlay, true));
        assertSame(expectedStateAfterDisable, mRules.getUpdatedState(overlay, false));
    }

    /**
     * Verify that getUpdatedState returns the correct state for an overlay info
     * when the overlay package component is disabled
     *
     * @throws Exception
     */
    public void testGetUpdatedStateOverlayComponentDisable() throws Exception {
        // Expected state, dangerous, overlay is system, signature match, target installed
        assertStateOverlayComponentDisabled(true,  true,  true,  true);
        assertStateOverlayComponentDisabled(true,  true,  true,  false);
        assertStateOverlayComponentDisabled(true,  true,  false, true);
        assertStateOverlayComponentDisabled(true,  true,  false, false);
        assertStateOverlayComponentDisabled(true,  false, true,  true);
        assertStateOverlayComponentDisabled(true,  false, true,  false);
        assertStateOverlayComponentDisabled(true,  false, false, true);
        assertStateOverlayComponentDisabled(true,  false, false, false);
        assertStateOverlayComponentDisabled(false, true,  true,  true);
        assertStateOverlayComponentDisabled(false, true,  true,  false);
        assertStateOverlayComponentDisabled(false, true,  false, true);
        assertStateOverlayComponentDisabled(false, true,  false, false);
        assertStateOverlayComponentDisabled(false, false, true,  true);
        assertStateOverlayComponentDisabled(false, false, true,  false);
        assertStateOverlayComponentDisabled(false, false, false, true);
        assertStateOverlayComponentDisabled(false, false, false, false);
    }

    public void testGetUpdatedStateOverlayComponentEnable() throws Exception {
        // Expected state, dangerous, overlay is system, signature match, target installed
        assertStateOverlayComponentEnabled(STATE_APPROVED_ALWAYS_ENABLED,         true,  true,  true,  true);
        assertStateOverlayComponentEnabled(STATE_NOT_APPROVED_MISSING_TARGET,     true,  true,  true,  false);
        assertStateOverlayComponentEnabled(STATE_APPROVED_ALWAYS_ENABLED,         true,  true,  false, true);
        assertStateOverlayComponentEnabled(STATE_NOT_APPROVED_MISSING_TARGET,     true,  true,  false, false);
        assertStateOverlayComponentEnabled(STATE_APPROVED_DISABLED,               true,  false, true,  true);
        assertStateOverlayComponentEnabled(STATE_NOT_APPROVED_MISSING_TARGET,     true,  false, true,  false);
        assertStateOverlayComponentEnabled(STATE_NOT_APPROVED_DANGEROUS_OVERLAY,  true,  false, false, true);
        assertStateOverlayComponentEnabled(STATE_NOT_APPROVED_MISSING_TARGET,     true,  false, false, false);
        assertStateOverlayComponentEnabled(STATE_APPROVED_ALWAYS_ENABLED,         false, true,  true,  true);
        assertStateOverlayComponentEnabled(STATE_NOT_APPROVED_MISSING_TARGET,     false, true,  true,  false);
        assertStateOverlayComponentEnabled(STATE_APPROVED_ALWAYS_ENABLED,         false, true,  false, true);
        assertStateOverlayComponentEnabled(STATE_NOT_APPROVED_MISSING_TARGET,     false, true,  false, false);
        assertStateOverlayComponentEnabled(STATE_APPROVED_DISABLED,               false, false, true,  true);
        assertStateOverlayComponentEnabled(STATE_NOT_APPROVED_MISSING_TARGET,     false, false, true,  false);
        assertStateOverlayComponentEnabled(STATE_APPROVED_DISABLED,               false, false, false, true);
        assertStateOverlayComponentEnabled(STATE_NOT_APPROVED_MISSING_TARGET,     false, false, false, false);
    }

    public void testInsertIndexEmptyList() throws Exception {
        // Inserting into an empty overlay list should always result in 0 as
        // insert index
        List<OverlayInfo> emptyOverlays = Collections.<OverlayInfo> emptyList();
        assertSame(0, mRules.getInsertIndex(createOverlayInfo(true, -1), emptyOverlays));
        assertSame(0, mRules.getInsertIndex(createOverlayInfo(true, 100), emptyOverlays));
        assertSame(0, mRules.getInsertIndex(createOverlayInfo(false, -1), emptyOverlays));
    }

    public void testInsertIndex() throws Exception {
        final int lowestPrio = 100;
        final int highestPrio = 200;
        ArrayList<OverlayInfo> overlays = new ArrayList<>();
        overlays.add(createOverlayInfo(true, lowestPrio));
        overlays.add(createOverlayInfo(true, highestPrio));
        overlays.add(createOverlayInfo(false, -1));

        assertSame(0, mRules.getInsertIndex(createOverlayInfo(true, -1), overlays));
        assertSame(0, mRules.getInsertIndex(createOverlayInfo(true, 0), overlays));
        assertSame(0, mRules.getInsertIndex(createOverlayInfo(true, lowestPrio - 1), overlays));
        assertSame(1, mRules.getInsertIndex(createOverlayInfo(true, lowestPrio), overlays));
        assertSame(1, mRules.getInsertIndex(createOverlayInfo(true, lowestPrio + 1), overlays));
        assertSame(1, mRules.getInsertIndex(createOverlayInfo(true, highestPrio - 1), overlays));
        assertSame(2, mRules.getInsertIndex(createOverlayInfo(true, highestPrio), overlays));
        assertSame(2, mRules.getInsertIndex(createOverlayInfo(true, highestPrio + 1), overlays));

        // Non system should always be added to the end, regardless of requested prio
        assertSame(overlays.size(), mRules.getInsertIndex(createOverlayInfo(false, -1), overlays));
        assertSame(overlays.size(), mRules.getInsertIndex(createOverlayInfo(false, lowestPrio), overlays));
    }

    public void testVerifyOverlayOrderValidOrder() throws Exception {
        ArrayList<OverlayInfo> overlays = new ArrayList<>();

        overlays.add(createOverlayInfo(true, 100));
        assertTrue(mRules.verifyOverlayOrder(overlays, USER_OWNER));

        overlays.add(createOverlayInfo(true, 200));
        assertTrue(mRules.verifyOverlayOrder(overlays, USER_OWNER));

        overlays.add(createOverlayInfo(false, -1));
        assertTrue(mRules.verifyOverlayOrder(overlays, USER_OWNER));

        while (overlays.size() > 0) {
            overlays.remove(0);
            assertTrue(mRules.verifyOverlayOrder(overlays, USER_OWNER));
        }
    }

    public void testVerifyOverlayOrderWrongPrioOrder() throws Exception {
        ArrayList<OverlayInfo> overlays = new ArrayList<>();

        overlays.add(createOverlayInfo(true, 200));
        overlays.add(createOverlayInfo(true, 100));
        assertFalse(mRules.verifyOverlayOrder(overlays, USER_OWNER));
    }

    public void testVerifyOverlayOrderSystemAfterNonSystem() throws Exception {
        ArrayList<OverlayInfo> overlays = new ArrayList<>();
        overlays.add(createOverlayInfo(false, -1));
        overlays.add(createOverlayInfo(true, 100));
        assertFalse(mRules.verifyOverlayOrder(overlays, USER_OWNER));
    }

    public void testVerifyOverlayOrder() throws Exception {
        ArrayList<OverlayInfo> overlays = new ArrayList<>();
        overlays.add(createOverlayInfo(false, -1));
        overlays.add(createOverlayInfo(true, 100));
        assertFalse(mRules.verifyOverlayOrder(overlays, USER_OWNER));
    }

    /**
     * Assert the the Rules.getInitialState returns the correct value for a
     * package with the given properties.
     *
     * @param expectedState the expected state.
     * @param system true if the overlay package is a system package.
     * @param targetPackageInstalled true if the target package for the overlay
     *            is installed.
     * @param signatureMatch true if the
     * @param overlayComponentEnabled
     * @throws Exception
     */
    private void assertInitialState(int expectedState, boolean dangerous, boolean system,
            boolean targetPackageInstalled, boolean signatureMatch, boolean overlayComponentEnabled)
                    throws Exception {
        PackageInfo overlayPackage = createOverlayPackage(dangerous, system, targetPackageInstalled,
                signatureMatch, overlayComponentEnabled);
        when(idmap.isDangerous(overlayPackage)).thenReturn(dangerous);
        assertSame(expectedState, mRules.getInitialState(overlayPackage, USER_OWNER));
    }

    /**
     * Assert that the Rules return the correct updated state when the target
     * package is installed.
     *
     * @param expectedState the expected updated state.
     * @param dangerous true if the idmap is dangerous
     * @param systemOverlay true if the overlay package is a system package.
     * @param signatureMatch true if the overlay package have a matching
     *            signature with the target package.
     * @param overlayComponentEnabled if the overlay package component is enabled.
     * @throws Exception
     */
    private void assertStateTargetInstalled(int expectedState, boolean dangerous,
            boolean systemOverlay, boolean signatureMatch, boolean overlayComponentEnabled)
                    throws Exception {
        PackageInfo stalePackage;
        OverlayInfo staleOverlay;
        PackageInfo freshPackage;
        stalePackage = createOverlayPackage(dangerous, systemOverlay, false, signatureMatch,
                overlayComponentEnabled);
        staleOverlay = createOverlayInfo(stalePackage);
        freshPackage = createOverlayPackage(staleOverlay.packageName, dangerous, systemOverlay,
                true, signatureMatch, overlayComponentEnabled, -1);
        assertSame(expectedState, mRules.getUpdatedState(staleOverlay, freshPackage, USER_OWNER));
    }

    /**
     * Assert that the Rules return the correct updated state when the target
     * package gets uninstalled.
     *
     * @param expectedState the expected updated state.
     * @param systemOverlay true if the overlay package is a system package.
     * @param signatureMatch true if the overlay package have a matching
     *            signature with the target package.
     * @param overlayComponentEnabled if the overlay package component is enabled.
     * @throws Exception
     */
    private void assertStateTargetUninstalled(int expectedState, boolean dangerous,
            boolean systemOverlay, boolean signatureMatch, boolean overlayComponentEnabled)
            throws Exception {
        PackageInfo stalePackage;
        OverlayInfo staleOverlay;
        PackageInfo freshPackage;
        stalePackage = createOverlayPackage(systemOverlay, true, true, signatureMatch,
                overlayComponentEnabled);
        staleOverlay = createOverlayInfo(stalePackage);
        freshPackage = createOverlayPackage(staleOverlay.packageName, dangerous, systemOverlay,
                false, signatureMatch, overlayComponentEnabled, -1);
        assertSame(expectedState, mRules.getUpdatedState(staleOverlay, freshPackage, USER_OWNER));
    }

    /**
     * Assert that the rules return the correct update state when the overlay
     * package component is disabled
     *
     * @param expectedState
     * @param systemOverlay
     * @param signatureMatch
     * @param targetInstalled
     * @throws Exception
     */
    private void assertStateOverlayComponentDisabled(boolean dangerous, boolean systemOverlay,
            boolean signatureMatch, boolean targetInstalled) throws Exception {
        PackageInfo stalePackage;
        OverlayInfo staleOverlay;
        PackageInfo freshPackage;
        stalePackage = createOverlayPackage(dangerous, systemOverlay, targetInstalled,
                signatureMatch, true);
        staleOverlay = createOverlayInfo(stalePackage);
        freshPackage = createOverlayPackage(staleOverlay.packageName, dangerous, systemOverlay,
                targetInstalled, signatureMatch, false, -1);
        assertSame(STATE_NOT_APPROVED_COMPONENT_DISABLED,
                mRules.getUpdatedState(staleOverlay, freshPackage, USER_OWNER));
    }

    private void assertStateOverlayComponentEnabled(int expectedState, boolean dangerous,
            boolean systemOverlay, boolean signatureMatch, boolean targetInstalled)
            throws Exception {
        PackageInfo stalePackage;
        OverlayInfo staleOverlay;
        PackageInfo freshPackage;
        stalePackage = createOverlayPackage(dangerous, systemOverlay, targetInstalled,
                signatureMatch, false);
        staleOverlay = createOverlayInfo(stalePackage);
        freshPackage = createOverlayPackage(staleOverlay.packageName, dangerous, systemOverlay,
                targetInstalled, signatureMatch, true, -1);
        assertSame(expectedState, mRules.getUpdatedState(staleOverlay, freshPackage, USER_OWNER));
    }

    private OverlayInfo createOverlayInfo(boolean system, int requestedOverlayPriority)
            throws Exception {
        return createOverlayInfo(createOverlayPackage(false, system, requestedOverlayPriority));
    }

    private OverlayInfo createOverlayInfo(int state) {
        return new OverlayInfo("overlay.package.name", "target.package.name",
                "/data/app/overlay/base.apk", state, USER_OWNER);
    }

    private OverlayInfo createOverlayInfo(PackageInfo pi) throws RemoteException {
        when(pm.getPackageInfo(eq(pi.packageName), anyInt(), anyInt())).thenReturn(pi);
        return new OverlayInfo(pi.packageName, pi.overlayTarget,
                pi.applicationInfo.getBaseCodePath(), mRules.getInitialState(pi, USER_OWNER),
                USER_OWNER);
    }

    private PackageInfo createOverlayPackage(boolean dangerous, boolean system,
            int requestedOverlayPriority) throws Exception {
        return createOverlayPackage(dangerous, system, true, true, true, requestedOverlayPriority);
    }

    private PackageInfo createOverlayPackage(boolean dangerous, boolean system,
            boolean targetPackagePresent, boolean signatureMatch, boolean componentEnabled)
            throws Exception {
        PackageInfo pi = createOverlayPackage(dangerous, system, targetPackagePresent, signatureMatch,
                componentEnabled, -1);
        when(idmap.isDangerous(pi)).thenReturn(dangerous);
        return pi;
    }

    private PackageInfo createOverlayPackage(boolean dangerous, boolean system,
            boolean targetPackagePresent, boolean signatureMatch, boolean componentEnabled,
            int requestedOverlayPriority) throws Exception {
        // Append a time stamp to the package names to keep them unique
        return createOverlayPackage("package.name." + SystemClock.elapsedRealtime(), dangerous,
                system, targetPackagePresent, signatureMatch, componentEnabled,
                requestedOverlayPriority);
    }

    private PackageInfo createOverlayPackage(String packageName, boolean dangerous, boolean system,
            boolean targetPackagePresent, boolean signatureMatch, boolean componentEnabled,
            int requestedOverlayPriority) throws Exception {

        String overlayTarget = "some.target.package";
        PackageInfo overlayPackage = createPackageInfo(packageName, overlayTarget, system,
                componentEnabled, requestedOverlayPriority);

        // Update the PackageManager mock to return wanted values
        when(pm.checkSignatures(overlayTarget, packageName)).thenReturn(signatureMatch
                ? PackageManager.SIGNATURE_MATCH : PackageManager.SIGNATURE_NO_MATCH);
        when(pm.getPackageInfo(eq(overlayTarget), anyInt(), anyInt())).thenReturn(
                targetPackagePresent ? createPackageInfo(overlayTarget, null, system, true) : null);
        when(idmap.isDangerous(overlayPackage)).thenReturn(dangerous);
        return overlayPackage;
    }

    private PackageInfo createPackageInfo(String packageName, String overlayTarget, boolean system,
            boolean componentEnabled) {
        return createPackageInfo(packageName, overlayTarget, system, componentEnabled, -1);
    }

    private PackageInfo createPackageInfo(String packageName, String overlayTarget, boolean system,
            boolean componentEnabled, int requestedOverlayPriority) {
        PackageInfo pi = new PackageInfo();
        pi.packageName = packageName;
        pi.overlayTarget = overlayTarget;
        pi.applicationInfo = new ApplicationInfo();
        pi.applicationInfo.flags = system ? ApplicationInfo.FLAG_SYSTEM : 0;
        pi.applicationInfo.enabled = componentEnabled;
        pi.requestedOverlayPriority = requestedOverlayPriority;
        pi.applicationInfo.sourceDir = "/dummy/path";
        return pi;
    }
}
