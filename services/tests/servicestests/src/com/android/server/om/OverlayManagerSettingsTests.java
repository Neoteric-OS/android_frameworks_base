package com.android.server.om;

import static android.content.om.OverlayInfo.STATE_APPROVED_DISABLED;
import static android.content.om.OverlayInfo.STATE_APPROVED_ENABLED;
import static android.content.om.OverlayInfo.STATE_NOT_APPROVED_COMPONENT_DISABLED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.annotation.NonNull;
import android.content.om.OverlayInfo;
import android.os.UserHandle;
import android.support.test.runner.AndroidJUnit4;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class OverlayManagerSettingsTests {
    private OverlayManagerSettings mSettings;

    private static final OverlayInfo OVERLAY_A0 = new OverlayInfo(
            "com.dummy.overlay_a",
            "com.dummy.target",
            "/data/app/com.dummy.overlay_a-1/base.apk",
            STATE_APPROVED_DISABLED,
            0);

    private static final OverlayInfo OVERLAY_B0 = new OverlayInfo(
            "com.dummy.overlay_b",
            "com.dummy.target",
            "/data/app/com.dummy.overlay_b-1/base.apk",
            STATE_APPROVED_DISABLED,
            0);

    private static final OverlayInfo OVERLAY_C0 = new OverlayInfo(
            "com.dummy.overlay_c",
            "com.dummy.target",
            "/data/app/com.dummy.overlay_c-1/base.apk",
            STATE_APPROVED_DISABLED,
            0);

    private static final OverlayInfo OVERLAY_A1 = new OverlayInfo(
            "com.dummy.overlay_a",
            "com.dummy.target",
            "/data/app/com.dummy.overlay_a-1/base.apk",
            STATE_APPROVED_DISABLED,
            1);

    private static final OverlayInfo OVERLAY_B1 = new OverlayInfo(
            "com.dummy.overlay_b",
            "com.dummy.target",
            "/data/app/com.dummy.overlay_b-1/base.apk",
            STATE_APPROVED_DISABLED,
            1);

    @Before
    public void setUp() throws Exception {
        mSettings = new OverlayManagerSettings();
    }

    // tests: generic functionality

    @Test
    public void testSettingsInitiallyEmpty() throws Exception {
        final int userId = UserHandle.USER_OWNER;
        Map<String, List<OverlayInfo>> map = mSettings.getOverlaysForUser(userId);
        assertEquals(0, map.size());
    }

    @Test
    public void testBasicSetAndGet() throws Exception {
        assertFalse(mSettings.contains(OVERLAY_A0.packageName, OVERLAY_A0.userId));

        insert(OVERLAY_A0);
        assertTrue(mSettings.contains(OVERLAY_A0.packageName, OVERLAY_A0.userId));
        OverlayInfo oi = mSettings.getOverlayInfo(OVERLAY_A0.packageName, OVERLAY_A0.userId);
        assertEquals(OVERLAY_A0, oi);

        mSettings.remove(OVERLAY_A0.packageName, OVERLAY_A0.userId);
        assertFalse(mSettings.contains(OVERLAY_A0.packageName, OVERLAY_A0.userId));
    }

    @Test
    public void testGetUsers() throws Exception {
        List<Integer> users = mSettings.getUsers();
        assertEquals(0, users.size());

        insert(OVERLAY_A0);
        users = mSettings.getUsers();
        assertEquals(1, users.size());
        assertTrue(users.contains(OVERLAY_A0.userId));

        insert(OVERLAY_A1);
        insert(OVERLAY_B1);
        users = mSettings.getUsers();
        assertEquals(2, users.size());
        assertTrue(users.contains(OVERLAY_A0.userId));
        assertTrue(users.contains(OVERLAY_A1.userId));
    }

    @Test
    public void testGetOverlaysForUser() throws Exception {
        insert(OVERLAY_A0);
        insert(OVERLAY_B0);
        insert(OVERLAY_A1);
        insert(OVERLAY_B1);

        Map<String, List<OverlayInfo>> map = mSettings.getOverlaysForUser(OVERLAY_A0.userId);
        assertEquals(1, map.keySet().size());
        assertTrue(map.keySet().contains(OVERLAY_A0.targetPackageName));

        List<OverlayInfo> list = map.get(OVERLAY_A0.targetPackageName);
        assertEquals(2, list.size());
        assertTrue(list.contains(OVERLAY_A0));
        assertTrue(list.contains(OVERLAY_B0));

        // getOverlaysForUser should never return null
        map = mSettings.getOverlaysForUser(-1);
        assertNotNull(map);
        assertEquals(0, map.size());
    }

    @Test
    public void testGetTargetPackageNamesForUser() throws Exception {
        insert(OVERLAY_A0);
        insert(OVERLAY_B0);
        insert(OVERLAY_A1);
        insert(OVERLAY_B1);

        List<String> list = mSettings.getTargetPackageNamesForUser(OVERLAY_A0.userId);
        assertEquals(1, list.size());
        assertTrue(list.contains(OVERLAY_A0.targetPackageName));

        // getTargetPackageNamesForUser should never return null
        list = mSettings.getTargetPackageNamesForUser(-1);
        assertNotNull(list);
        assertEquals(0, list.size());
    }

    @Test
    public void testRemoveUser() throws Exception {
        insert(OVERLAY_A0);
        insert(OVERLAY_B0);
        insert(OVERLAY_A1);

        assertTrue(mSettings.contains(OVERLAY_A0.packageName, OVERLAY_A0.userId));
        assertTrue(mSettings.contains(OVERLAY_B0.packageName, OVERLAY_B0.userId));
        assertTrue(mSettings.contains(OVERLAY_A1.packageName, OVERLAY_A1.userId));

        mSettings.removeUser(OVERLAY_A0.userId);

        assertFalse(mSettings.contains(OVERLAY_A0.packageName, OVERLAY_A0.userId));
        assertFalse(mSettings.contains(OVERLAY_B0.packageName, OVERLAY_B0.userId));
        assertTrue(mSettings.contains(OVERLAY_A1.packageName, OVERLAY_A1.userId));
    }

    @Test
    public void testOrderOfNewlyAddedItems() throws Exception {
        // new items are appended to the list
        insert(OVERLAY_A0);
        insert(OVERLAY_B0);
        insert(OVERLAY_C0);

        List<OverlayInfo> list =
            mSettings.getOverlaysForTarget(OVERLAY_A0.targetPackageName, OVERLAY_A0.userId);
        assertOrder(list, OVERLAY_A0, OVERLAY_B0, OVERLAY_C0);

        // overlays keep their positions when updated
        mSettings.setState(OVERLAY_B0.packageName, OVERLAY_B0.userId, STATE_APPROVED_ENABLED);
        OverlayInfo oi = mSettings.getOverlayInfo(OVERLAY_B0.packageName, OVERLAY_B0.userId);

        list = mSettings.getOverlaysForTarget(OVERLAY_A0.targetPackageName, OVERLAY_A0.userId);
        assertOrder(list, OVERLAY_A0, oi, OVERLAY_C0);
    }

    @Test
    public void testSetPriority() throws Exception {
        insert(OVERLAY_A0);
        insert(OVERLAY_B0);
        insert(OVERLAY_C0);

        List<OverlayInfo> list =
            mSettings.getOverlaysForTarget(OVERLAY_A0.targetPackageName, OVERLAY_A0.userId);
        assertOrder(list, OVERLAY_A0, OVERLAY_B0, OVERLAY_C0);

        boolean changed = mSettings.setPriority(OVERLAY_B0.packageName, OVERLAY_C0.packageName,
                OVERLAY_B0.userId);
        assertTrue(changed);

        list = mSettings.getOverlaysForTarget(OVERLAY_A0.targetPackageName, OVERLAY_A0.userId);
        assertOrder(list, OVERLAY_A0, OVERLAY_C0, OVERLAY_B0);

        changed = mSettings.setPriority(OVERLAY_B0.packageName, "does.not.exist",
                OVERLAY_B0.userId);
        assertFalse(changed);

        list = mSettings.getOverlaysForTarget(OVERLAY_A0.targetPackageName, OVERLAY_A0.userId);
        assertOrder(list, OVERLAY_A0, OVERLAY_C0, OVERLAY_B0);

        OverlayInfo otherTarget = new OverlayInfo(
                "com.dummy.overlay_other",
                "com.dummy.some.other.target",
                "/data/app/com.dummy.overlay_other-1/base.apk",
                STATE_APPROVED_DISABLED,
                0);
        insert(otherTarget);
        changed = mSettings.setPriority(OVERLAY_A0.packageName, otherTarget.packageName,
                OVERLAY_A0.userId);
        assertFalse(changed);
    }

    @Test
    public void testSetLowestPriority() throws Exception {
        insert(OVERLAY_A0);
        insert(OVERLAY_B0);
        insert(OVERLAY_C0);

        List<OverlayInfo> list =
            mSettings.getOverlaysForTarget(OVERLAY_A0.targetPackageName, OVERLAY_A0.userId);
        assertOrder(list, OVERLAY_A0, OVERLAY_B0, OVERLAY_C0);

        boolean changed = mSettings.setLowestPriority(OVERLAY_B0.packageName, OVERLAY_B0.userId);
        assertTrue(changed);

        list = mSettings.getOverlaysForTarget(OVERLAY_A0.targetPackageName, OVERLAY_A0.userId);
        assertOrder(list, OVERLAY_B0, OVERLAY_A0, OVERLAY_C0);
    }

    @Test
    public void testSetHighestPriority() throws Exception {
        insert(OVERLAY_A0);
        insert(OVERLAY_B0);
        insert(OVERLAY_C0);

        List<OverlayInfo> list =
            mSettings.getOverlaysForTarget(OVERLAY_A0.targetPackageName, OVERLAY_A0.userId);
        assertOrder(list, OVERLAY_A0, OVERLAY_B0, OVERLAY_C0);

        boolean changed = mSettings.setHighestPriority(OVERLAY_B0.packageName, OVERLAY_B0.userId);
        assertTrue(changed);

        list = mSettings.getOverlaysForTarget(OVERLAY_A0.targetPackageName, OVERLAY_A0.userId);
        assertOrder(list, OVERLAY_A0, OVERLAY_C0, OVERLAY_B0);
    }

    private void assertOrder(List<OverlayInfo> list, OverlayInfo... array) {
        assertEquals(list.size(), array.length);
        for (int i = 0; i < list.size(); i++) {
            OverlayInfo a = list.get(i);
            OverlayInfo b = array[i];
            assertEquals(a, b);
        }
    }

    // tests: change listeners

    private static class SettingsChangeListener implements OverlayManagerSettings.ChangeListener {
        int externalCallbacks;
        int internalCallbacks;
        OverlayInfo addedOverlayInfo;
        OverlayInfo removedOverlayInfo;
        OverlayInfo changedPriorityOverlayInfo;

        @Override
        public void onSettingsChanged() {
            internalCallbacks++;
        }

        @Override
        public void onOverlayAdded(@NonNull OverlayInfo oi) {
            assertNotNull(oi);

            externalCallbacks++;
            addedOverlayInfo = oi;
        }

        @Override
        public void onOverlayRemoved(@NonNull OverlayInfo oi) {
            assertNotNull(oi);

            externalCallbacks++;
            removedOverlayInfo = oi;
        }

        @Override
        public void onOverlayChanged(@NonNull OverlayInfo oi, @NonNull OverlayInfo oldOi) {
            assertNotNull(oi);
            assertNotNull(oldOi);

            externalCallbacks++;
            addedOverlayInfo = oi;
            removedOverlayInfo = oldOi;
        }

        @Override
        public void onOverlayPriorityChanged(@NonNull OverlayInfo oi) {
            assertNotNull(oi);

            externalCallbacks++;
            changedPriorityOverlayInfo = oi;
        }
    }

    @Test
    public void testChangeListenerCallbacks() throws Exception {
        // add listener
        SettingsChangeListener listener = new SettingsChangeListener();
        mSettings.addChangeListener(listener);
        assertEquals(0, listener.externalCallbacks);

        // onOverlayAdded
        insert(OVERLAY_A0);
        assertEquals(1, listener.externalCallbacks);
        assertEquals(OVERLAY_A0, listener.addedOverlayInfo);

        insert(OVERLAY_B0);
        assertEquals(2, listener.externalCallbacks);
        assertEquals(OVERLAY_B0, listener.addedOverlayInfo);

        insert(OVERLAY_C0);
        assertEquals(3, listener.externalCallbacks);
        assertEquals(OVERLAY_C0, listener.addedOverlayInfo);

        // onOverlayPriorityChanged
        mSettings.setPriority(OVERLAY_A0.packageName, OVERLAY_B0.packageName, OVERLAY_A0.userId);
        assertEquals(4, listener.externalCallbacks);
        assertEquals(OVERLAY_A0, listener.changedPriorityOverlayInfo);

        mSettings.setHighestPriority(OVERLAY_B0.packageName, OVERLAY_B0.userId);
        assertEquals(5, listener.externalCallbacks);
        assertEquals(OVERLAY_B0, listener.changedPriorityOverlayInfo);

        mSettings.setLowestPriority(OVERLAY_A0.packageName, OVERLAY_A0.userId);
        assertEquals(6, listener.externalCallbacks);
        assertEquals(OVERLAY_A0, listener.changedPriorityOverlayInfo);

        // onOverlayChanged
        OverlayInfo oi = new OverlayInfo(OVERLAY_A0, STATE_APPROVED_ENABLED);
        mSettings.setState(OVERLAY_A0.packageName, OVERLAY_A0.userId, STATE_APPROVED_ENABLED);
        assertEquals(7, listener.externalCallbacks);
        assertEquals(oi, listener.addedOverlayInfo);
        assertEquals(OVERLAY_A0, listener.removedOverlayInfo);

        // onOverlayRemoved
        mSettings.remove(OVERLAY_C0.packageName, OVERLAY_C0.userId);
        assertEquals(8, listener.externalCallbacks);
        assertEquals(OVERLAY_C0, listener.removedOverlayInfo);

        // remove listener
        mSettings.removeChangeListener(listener);

        mSettings.remove(OVERLAY_A0.packageName, OVERLAY_A0.userId);
        assertEquals(8, listener.externalCallbacks);

        insert(OVERLAY_A0);
        assertEquals(8, listener.externalCallbacks);
    }

    @Test
    public void testNoCallbacksDuringFailingOperations() throws Exception {
        SettingsChangeListener listener = new SettingsChangeListener();
        mSettings.addChangeListener(listener);

        mSettings.remove("does.not.exist", -1);
        assertEquals(listener.externalCallbacks, 0);
    }

    @Test
    public void testInternalCallbacks() throws Exception {
        SettingsChangeListener listener = new SettingsChangeListener();
        mSettings.addChangeListener(listener);

        insert(OVERLAY_A0);
        assertEquals(1, listener.externalCallbacks);

        int i = listener.internalCallbacks;
        boolean enable = !OVERLAY_A0.isEnabled();
        mSettings.setEnabled(OVERLAY_A0.packageName, OVERLAY_A0.userId, enable);
        assertTrue(i < listener.internalCallbacks);

        i = listener.internalCallbacks;
        mSettings.setEnabled(OVERLAY_A0.packageName, OVERLAY_A0.userId, enable);
        assertEquals(i, listener.internalCallbacks); // no change this time

        insert(OVERLAY_B0);
        mSettings.setState(OVERLAY_B0.packageName, OVERLAY_B0.userId,
                STATE_NOT_APPROVED_COMPONENT_DISABLED);

        i = listener.internalCallbacks;
        int e = listener.externalCallbacks;
        mSettings.setEnabled(OVERLAY_B0.packageName, OVERLAY_B0.userId, true);
        assertTrue(i < listener.internalCallbacks);
        assertEquals(e, listener.externalCallbacks);

        i = listener.internalCallbacks;
        mSettings.setUpgrading(OVERLAY_B0.packageName, OVERLAY_B0.userId, true);
        assertTrue(i < listener.internalCallbacks);

        i = listener.internalCallbacks;
        mSettings.setBaseCodePath(OVERLAY_B0.packageName, OVERLAY_B0.userId, "/foo/bar.apk");
        assertTrue(i < listener.internalCallbacks);
    }

    // tests: persist and restore

    @Test
    public void testPersistEmpty() throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mSettings.persist(os);
        String xml = new String(os.toByteArray(), "utf-8");

        assertEquals(1, countXmlTags(xml, "overlays"));
        assertEquals(0, countXmlTags(xml, "item"));
    }

    @Test
    public void testPersistDifferentOverlaysSameUser() throws Exception {
        insert(OVERLAY_A0);
        insert(OVERLAY_B0);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mSettings.persist(os);
        final String xml = new String(os.toByteArray(), "utf-8");

        assertEquals(1, countXmlTags(xml, "overlays"));
        assertEquals(2, countXmlTags(xml, "item"));
        assertEquals(1, countXmlAttributesWhere(xml, "item", "packageName", OVERLAY_A0.packageName));
        assertEquals(1, countXmlAttributesWhere(xml, "item", "packageName", OVERLAY_B0.packageName));
        assertEquals(2, countXmlAttributesWhere(xml, "item", "userId", Integer.toString(OVERLAY_A0.userId)));
    }

    @Test
    public void testPersistSameOverlayDifferentUsers() throws Exception {
        insert(OVERLAY_A0);
        insert(OVERLAY_A1);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mSettings.persist(os);
        String xml = new String(os.toByteArray(), "utf-8");

        assertEquals(1, countXmlTags(xml, "overlays"));
        assertEquals(2, countXmlTags(xml, "item"));
        assertEquals(2, countXmlAttributesWhere(xml, "item", "packageName", OVERLAY_A0.packageName));
        assertEquals(1, countXmlAttributesWhere(xml, "item", "userId", Integer.toString(OVERLAY_A0.userId)));
        assertEquals(1, countXmlAttributesWhere(xml, "item", "userId", Integer.toString(OVERLAY_A1.userId)));
    }

    @Test
    public void testPersistEnabled() throws Exception {
        insert(OVERLAY_A0);
        mSettings.setEnabled(OVERLAY_A0.packageName, OVERLAY_A0.userId, true);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mSettings.persist(os);
        String xml = new String(os.toByteArray(), "utf-8");

        assertEquals(1, countXmlAttributesWhere(xml, "item", "isEnabled", "true"));
        assertEquals(1, countXmlAttributesWhere(xml, "item", "isUpgrading", "false"));
    }

    @Test
    public void testPersistUpgrading() throws Exception {
        insert(OVERLAY_A0);
        mSettings.setUpgrading(OVERLAY_A0.packageName, OVERLAY_A0.userId, true);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mSettings.persist(os);
        String xml = new String(os.toByteArray(), "utf-8");

        assertEquals(1, countXmlAttributesWhere(xml, "item", "isEnabled", "false"));
        assertEquals(1, countXmlAttributesWhere(xml, "item", "isUpgrading", "true"));
    }

    @Test
    public void testRestoreEmpty() throws Exception {
        final String xml =
            "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
            "<overlays version=\"1\" />\n";
        ByteArrayInputStream is = new ByteArrayInputStream(xml.getBytes("utf-8"));

        mSettings.restore(is);
        assertFalse(mSettings.contains("com.dummy.overlay", UserHandle.USER_OWNER));
    }

    @Test
    public void testRestoreSingleUserSingleOverlay() throws Exception {
        final String xml =
            "<?xml version='1.0' encoding='utf-8' standalone='yes'?>\n" +
            "<overlays version='1'>\n" +
            "<item packageName='com.dummy.overlay'\n" +
            "      userId='1234'\n" +
            "      targetPackageName='com.dummy.target'\n" +
            "      baseCodePath='/data/app/com.dummy.overlay-1/base.apk'\n" +
            "      state='" + STATE_APPROVED_DISABLED + "'\n" +
            "      isEnabled='false'\n" +
            "      isUpgrading='false' />\n" +
            "</overlays>\n";
        ByteArrayInputStream is = new ByteArrayInputStream(xml.getBytes("utf-8"));

        mSettings.restore(is);
        OverlayInfo oi = mSettings.getOverlayInfo("com.dummy.overlay", 1234);
        assertNotNull(oi);
        assertEquals("com.dummy.overlay", oi.packageName);
        assertEquals("com.dummy.target", oi.targetPackageName);
        assertEquals("/data/app/com.dummy.overlay-1/base.apk", oi.baseCodePath);
        assertEquals(1234, oi.userId);
        assertEquals(STATE_APPROVED_DISABLED, oi.state);
        assertFalse(mSettings.getEnabled("com.dummy.overlay", 1234));
        assertFalse(mSettings.getUpgrading("com.dummy.overlay", 1234));
    }

    @Test
    public void testPersistAndRestore() throws Exception {
        insert(OVERLAY_A0);
        insert(OVERLAY_B1);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mSettings.persist(os);
        String xml = new String(os.toByteArray(), "utf-8");
        ByteArrayInputStream is = new ByteArrayInputStream(xml.getBytes("utf-8"));
        OverlayManagerSettings newSettings = new OverlayManagerSettings();
        newSettings.restore(is);

        OverlayInfo a = newSettings.getOverlayInfo(OVERLAY_A0.packageName, OVERLAY_A0.userId);
        assertEquals(OVERLAY_A0, a);

        OverlayInfo b = newSettings.getOverlayInfo(OVERLAY_B1.packageName, OVERLAY_B1.userId);
        assertEquals(OVERLAY_B1, b);
    }

    private int countXmlTags(String xml, String tagToLookFor) throws Exception {
        int count = 0;
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new StringReader(xml));
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && tagToLookFor.equals(parser.getName())) {
                count++;
            }
            event = parser.next();
        }
        return count;
    }

    private int countXmlAttributesWhere(String xml, String tag, String attr, String value)
        throws Exception {
        int count = 0;
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new StringReader(xml));
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && tag.equals(parser.getName())) {
                String v = parser.getAttributeValue(null, attr);
                if (value.equals(v)) {
                    count++;
                }
            }
            event = parser.next();
        }
        return count;
    }

    private void insert(OverlayInfo oi) throws Exception {
        mSettings.init(oi.packageName, oi.userId, oi.targetPackageName, oi.baseCodePath);
        mSettings.setState(oi.packageName, oi.userId, oi.state);
        mSettings.setEnabled(oi.packageName, oi.userId, false);
        mSettings.setUpgrading(oi.packageName, oi.userId, false);
    }
}
