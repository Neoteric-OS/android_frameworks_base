
package com.android.server.om;

import static android.content.om.OverlayInfo.STATE_APPROVED_ALWAYS_ENABLED;
import static android.content.om.OverlayInfo.STATE_APPROVED_DISABLED;
import static android.content.om.OverlayInfo.STATE_APPROVED_ENABLED;
import static android.content.om.OverlayInfo.STATE_NOT_APPROVED_DANGEROUS_OVERLAY;
import static android.content.om.OverlayInfo.STATE_NOT_APPROVED_MISSING_TARGET;
import static android.os.UserHandle.USER_OWNER;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import android.content.om.OverlayInfo;
import android.test.AndroidTestCase;

import com.android.server.om.State.StateListener;

import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StateTest extends AndroidTestCase {
    private static final String BASE_CODE_PATH = "baseCodePath";
    private static final String TARGET_PACKAGE_NAME = "targetPackageName";
    private static final String PACKAGE_NAME = "packageName";
    private static final String STATE = "state";

    private static final String DEFAULT_BASE_CODE_PATH = "/vendor/overlay/dummy.path.apk";
    private static final String DEFAULT_TARGET_NAME = "dummy.target.package.name";
    private static final String DEFAULT_PACKAGE_NAME = "dummy.package.name";

    private State state;

    @Mock
    private MockRules mRules;

    @Override
    protected void setUp() throws Exception {
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().getPath());
        MockitoAnnotations.initMocks(this);
        state = new State(mRules);
    }

    @Override
    protected void tearDown() throws Exception {

    }

    public void testGetOverlaysNotNull() throws Exception {
        List<OverlayInfo> overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        assertNotNull("getOverlays must return an empty list on missing package", overlays);
        assertEquals("getOverlays must return an empty list on missing package", 0,
                overlays.size());
    }

    public void testGetOverlays() throws Exception {
        // disabled overlays
        state.insertOverlay(createOverlayInfo(PACKAGE_NAME, "package.name.missing_target", STATE,
                STATE_NOT_APPROVED_MISSING_TARGET));
        state.insertOverlay(createOverlayInfo(PACKAGE_NAME, "package.name.not_approved.", STATE,
                STATE_NOT_APPROVED_DANGEROUS_OVERLAY));
        state.insertOverlay(createOverlayInfo(PACKAGE_NAME, "package.name.approved_disabled", STATE,
                STATE_APPROVED_DISABLED));

        // enabled overlays
        state.insertOverlay(createOverlayInfo(PACKAGE_NAME, "package.name.approved_enabled", STATE,
                STATE_APPROVED_ENABLED));
        state.insertOverlay(createOverlayInfo(PACKAGE_NAME, "package.name.always_enabled", STATE,
                STATE_APPROVED_ALWAYS_ENABLED));

        List<OverlayInfo> overlays = state.getOverlays(DEFAULT_TARGET_NAME, true, USER_OWNER);
        assertSame("getOverlays must not include disabled packages when enabledOnly is true", 2, overlays.size());
        overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        assertSame("getOverlays must include disabled packages when enabledOnly is false", 5,
                overlays.size());
    }

    public void testAddOverlay() throws Exception {
        OverlayInfo overlay = createOverlayInfo();
        state.insertOverlay(overlay);
        assertEquals(1, state
                .getOverlays(overlay.targetPackageName, false, USER_OWNER).size());
        assertTrue(state.getOverlays(overlay.targetPackageName, false, USER_OWNER)
                .contains(overlay));
        assertEquals(overlay, state.getOverlayInfo(overlay.packageName, USER_OWNER));
    }

    public void testUpdateOverlay() throws Exception {
        List<OverlayInfo> overlays = createOverlays(5);
        for (OverlayInfo oi : overlays) {
            state.insertOverlay(oi);
        }
        int index = 2;
        overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        OverlayInfo overlay = overlays.get(index);
        state.insertOverlay(overlay);
        overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        assertSame("overlays length must not change from an update", 5, overlays.size());
        assertEquals("Position of overlay in overlay list must not change by an update", overlay,
                overlays.get(index));
    }

    public void testReorder() throws Exception {
        OverlayInfo a = createOverlayInfo(PACKAGE_NAME, "a");
        OverlayInfo b = createOverlayInfo(PACKAGE_NAME, "b");
        OverlayInfo c = createOverlayInfo(PACKAGE_NAME, "c");
        insertOverlaysInOrder(mRules, state, c, b, a);
        List<OverlayInfo> overlays = null;

        assertTrue(state.changePriority(c, b));
        overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        assertOrder(overlays, b, c, a);

        assertTrue(state.changePriority(c, a));
        overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        assertOrder(overlays, b, a, c);

        assertTrue(state.changePriority(b, a));
        overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        assertOrder(overlays, a, b, c);
    }

    public void testReorderToParentSelf() throws Exception {
        OverlayInfo a = createOverlayInfo(PACKAGE_NAME, "a");
        OverlayInfo b = createOverlayInfo(PACKAGE_NAME, "b");
        OverlayInfo c = createOverlayInfo(PACKAGE_NAME, "c");
        insertOverlaysInOrder(mRules, state, c, b, a);

        List<OverlayInfo> overlays = null;

        assertFalse(state.changePriority(a, a));
        overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        assertOrder(overlays, c, b, a);

        assertFalse(state.changePriority(b, b));
        overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        assertOrder(overlays, c, b, a);

        assertFalse(state.changePriority(c, c));
        overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        assertOrder(overlays, c, b, a);
    }

    public void testReorderMissingOverlay() throws Exception {
        OverlayInfo a = createOverlayInfo(PACKAGE_NAME, "a");
        OverlayInfo b = createOverlayInfo(PACKAGE_NAME, "b");
        OverlayInfo c = createOverlayInfo(PACKAGE_NAME, "c");
        insertOverlaysInOrder(mRules, state, b, a); // Not adding c to the state

        List<OverlayInfo> overlays = null;

        // c is not added to the state, assert that reordering with it fails.
        assertFalse(state.changePriority(c, a));
        overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        assertOrder(overlays, b, a);

        assertFalse(state.changePriority(c, b));
        overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        assertOrder(overlays, b, a);

        assertFalse(state.changePriority(c, c));
        overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        assertOrder(overlays, b, a);
    }

    public void testSetLowestPriority() throws Exception {
        OverlayInfo a = createOverlayInfo(PACKAGE_NAME, "a");
        OverlayInfo b = createOverlayInfo(PACKAGE_NAME, "b");
        OverlayInfo c = createOverlayInfo(PACKAGE_NAME, "c");
        insertOverlaysInOrder(mRules, state, c, b, a);

        List<OverlayInfo> overlays = null;

        assertTrue(state.setLowestPriority(a));
        overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        assertOrder(overlays, a, c, b);
    }

    public void testSetLowestPriorityAlreadyLowest() throws Exception {
        OverlayInfo a = createOverlayInfo(PACKAGE_NAME, "a");
        OverlayInfo b = createOverlayInfo(PACKAGE_NAME, "b");
        OverlayInfo c = createOverlayInfo(PACKAGE_NAME, "c");
        insertOverlaysInOrder(mRules, state, c, b, a);

        List<OverlayInfo> overlays = null;

        assertTrue("True should be returned for valid priority operation",
                state.setLowestPriority(c));
        overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        assertOrder(overlays, c, b, a);
    }

    public void testsetLowestPriorityMissing() throws Exception {
        OverlayInfo a = createOverlayInfo(PACKAGE_NAME, "a");
        OverlayInfo b = createOverlayInfo(PACKAGE_NAME, "b");
        OverlayInfo c = createOverlayInfo(PACKAGE_NAME, "c");
        insertOverlaysInOrder(mRules, state, c, b);

        List<OverlayInfo> overlays = null;

        assertFalse("Moving a missing overlay is not a valid priority operation",
                state.setLowestPriority(a));
        overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        assertOrder(overlays, c, b);
    }

    public void testSetHighestPriority() throws Exception {
        OverlayInfo a = createOverlayInfo(PACKAGE_NAME, "a");
        OverlayInfo b = createOverlayInfo(PACKAGE_NAME, "b");
        OverlayInfo c = createOverlayInfo(PACKAGE_NAME, "c");
        insertOverlaysInOrder(mRules, state, a, b, c);

        List<OverlayInfo> overlays = null;

        assertTrue(state.setHighestPriority(a));
        overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        assertOrder(overlays, b, c, a);
    }

    public void testSetHighestPriorityAlreadyHighest() throws Exception {
        OverlayInfo a = createOverlayInfo(PACKAGE_NAME, "a");
        OverlayInfo b = createOverlayInfo(PACKAGE_NAME, "b");
        OverlayInfo c = createOverlayInfo(PACKAGE_NAME, "c");
        insertOverlaysInOrder(mRules, state, a, b, c);

        List<OverlayInfo> overlays = null;

        assertTrue("True should be returned for valid priority operation",
                state.setHighestPriority(c));
        overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        assertOrder(overlays, a, b, c);
    }

    public void testSetHighestPriorityMissing() throws Exception {
        OverlayInfo a = createOverlayInfo(PACKAGE_NAME, "a");
        OverlayInfo b = createOverlayInfo(PACKAGE_NAME, "b");
        OverlayInfo c = createOverlayInfo(PACKAGE_NAME, "c");
        insertOverlaysInOrder(mRules, state, b, c);

        List<OverlayInfo> overlays = null;

        assertFalse("Moving a missing overlay should not result in a change of the overlay list",
                state.setHighestPriority(a));
        overlays = state.getOverlays(DEFAULT_TARGET_NAME, false, USER_OWNER);
        assertOrder(overlays, b, c);
    }

    public void testRemoveOverlayInfo() throws Exception {
        OverlayInfo overlay = createOverlayInfo();
        state.insertOverlay(overlay);
        assertTrue(state.removeOverlay(overlay.packageName, USER_OWNER));
        assertEquals(0, state
                .getOverlays(overlay.targetPackageName, false, USER_OWNER).size());
    }

    public void testRemoveNonAddedOverlayInfo() throws Exception {
        OverlayInfo overlay = createOverlayInfo();
        assertFalse(state.removeOverlay(overlay.packageName, USER_OWNER));
    }

    public void testOverlayChangeListener() throws Exception {
        when(mRules.verifyOverlayOrder(anyListOf(OverlayInfo.class), eq(USER_OWNER)))
                .thenReturn(true);

        OverlayInfo overlay1 = createOverlayInfo(PACKAGE_NAME, "dummy.package.name.1",
                BASE_CODE_PATH, "path1"); // Set a value that we can distinguish
                                          // the instances with since equals
                                          // method only check the package name
        OverlayInfo overlay2 = createOverlayInfo(PACKAGE_NAME, "dummy.package.name.2");
        OverlayInfo overlay1v2 = createOverlayInfo(PACKAGE_NAME, "dummy.package.name.1",
                BASE_CODE_PATH, "path2");

        OverlayChangeHandler changeListener = new OverlayChangeHandler();
        state.addChangeListener(changeListener);

        state.insertOverlay(overlay1);
        assertEquals(overlay1, changeListener.addedOverlay);
        assertNull(changeListener.removedOverlay);
        assertEquals(1, changeListener.callbacks);

        state.insertOverlay(overlay2);
        assertEquals(overlay2, changeListener.addedOverlay);
        assertNull(changeListener.removedOverlay);
        assertEquals(2, changeListener.callbacks);

        state.insertOverlay(overlay1v2);
        assertEquals("path2", changeListener.addedOverlay.baseCodePath);
        assertEquals("path1", changeListener.removedOverlay.baseCodePath);
        assertEquals(3, changeListener.callbacks);

        state.changePriority(overlay2, overlay1);
        assertEquals(DEFAULT_TARGET_NAME, changeListener.reorderedTargetPackage);
        assertEquals(4, changeListener.callbacks);
    }

    public void testGetAllOverlaysNotNull() throws Exception {
        Map<String, List<OverlayInfo>> allOverlays = state.getAllOverlays(USER_OWNER);
        assertNotNull("getAllOverlays must return an empty map when empty.", allOverlays);
        assertEquals("getAllOverlays must return an empty map when no overlays exists.", 0,
                allOverlays.size());
    }

    public void testGetOverlayInfo() throws Exception {
        final String[] targets = new String[] {
                "target.1", "target.2"
        };
        OverlayInfo[] addedOverlays = new OverlayInfo[6];
        for (int i = 0; i < addedOverlays.length; i++) {
            OverlayInfo overlay = createOverlayInfo(PACKAGE_NAME, "package." + i,
                    TARGET_PACKAGE_NAME, targets[i % 2]);
            state.insertOverlay(overlay);
            addedOverlays[i] = overlay;
        }
        String.format(Locale.US, "", "");
        for (OverlayInfo overlay : addedOverlays) {
            assertEquals(overlay, state.getOverlayInfo(overlay.packageName, USER_OWNER));
        }
    }

    public void testGetNullOverlayInfo() throws Exception {
        state.getOverlayInfo(null, USER_OWNER);
    }

    private static void assertOrder(List<OverlayInfo> overlays, OverlayInfo... expected) {
        assertSame(expected.length, overlays.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], overlays.get(i));
        }
    }

    private static void insertOverlaysInOrder(Rules rules, State state, OverlayInfo... overlays) {
        when(rules.verifyOverlayOrder(Matchers.anyListOf(OverlayInfo.class), eq(USER_OWNER)))
                .thenReturn(true);

        // Mock for getInsertIndex to add all overlays to the end of the list.
        when(rules.getInsertIndex(any(OverlayInfo.class), Matchers.anyListOf(OverlayInfo.class)))
                .thenAnswer(new Answer<Integer>() {
                    @Override
                    public Integer answer(InvocationOnMock invocation) throws Throwable {
                        @SuppressWarnings("unchecked")
                        List<OverlayInfo> overlays = (List<OverlayInfo>) invocation
                                .getArguments()[1];
                        return overlays.size();
                    }
                });
        for (OverlayInfo overlay : overlays) {
            state.insertOverlay(overlay);
        }
    }

    private static List<OverlayInfo> createOverlays(int count) {
        ArrayList<OverlayInfo> overlays = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            overlays.add(createOverlayInfo(PACKAGE_NAME, DEFAULT_PACKAGE_NAME + "." + i));
        }
        return overlays;
    }

    private static OverlayInfo createOverlayInfo(Object... values) {
        String packageName = DEFAULT_PACKAGE_NAME;
        String targetPackageName = DEFAULT_TARGET_NAME;
        String baseCodePath = DEFAULT_BASE_CODE_PATH;
        int state = STATE_APPROVED_ENABLED;

        if (values != null) {
            for (int i = 0; i < values.length; i += 2) {
                switch (values[i].toString()) {
                    case PACKAGE_NAME:
                        packageName = values[i + 1].toString();
                        break;
                    case TARGET_PACKAGE_NAME:
                        targetPackageName = values[i + 1].toString();
                        break;
                    case BASE_CODE_PATH:
                        baseCodePath = values[i + 1].toString();
                        break;
                    case STATE:
                        state = (int) values[i + 1];
                        break;
                }
            }
        }

        return new OverlayInfo(packageName, targetPackageName, baseCodePath, state, USER_OWNER);
    }

    private static class OverlayChangeHandler implements StateListener {
        int callbacks;
        OverlayInfo addedOverlay;
        OverlayInfo removedOverlay;
        String reorderedTargetPackage;

        @Override
        public void onOverlayAdded(OverlayInfo overlay) {
            this.addedOverlay = overlay;
            callbacks++;
        }

        @Override
        public void onOverlayRemoved(OverlayInfo overlay) {
            this.removedOverlay = overlay;
            callbacks++;
        }

        @Override
        public void onOverlayChanged(OverlayInfo overlay, OverlayInfo oldOverlay) {
            this.addedOverlay = overlay;
            this.removedOverlay = oldOverlay;
            callbacks++;
        }

        @Override
        public void onOverlaysReordered(String targetPackage, int userId) {
            reorderedTargetPackage = targetPackage;
            callbacks++;
        }
    }
}
