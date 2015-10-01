package com.android.server.om;

import static android.content.om.OverlayInfo.STATE_APPROVED_DISABLED;
import static android.content.om.OverlayInfo.STATE_APPROVED_ENABLED;
import static android.content.om.OverlayInfo.STATE_NOT_APPROVED_COMPONENT_DISABLED;

import android.annotation.NonNull;
import android.content.om.OverlayInfo;
import android.os.UserHandle;
import android.test.AndroidTestCase;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

public class OverlayManagerDatabaseTests extends AndroidTestCase {
    private OverlayManagerDatabase mDatabase;

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

    public void setUp() throws Exception {
        mDatabase = new OverlayManagerDatabase();
    }

    // tests: generic functionality

    public void testDatabaseInitiallyEmpty() throws Exception {
        final int userId = UserHandle.USER_OWNER;
        Map<String, List<OverlayInfo>> map = mDatabase.getOverlaysForUser(userId);
        assertEquals(0, map.size());
    }

    public void testBasicSetAndGet() throws Exception {
        assertFalse(mDatabase.contains(OVERLAY_A0.packageName, OVERLAY_A0.userId));

        insert(OVERLAY_A0);
        assertTrue(mDatabase.contains(OVERLAY_A0.packageName, OVERLAY_A0.userId));
        OverlayInfo oi = mDatabase.getOverlayInfo(OVERLAY_A0.packageName, OVERLAY_A0.userId);
        assertEquals(OVERLAY_A0, oi);

        mDatabase.remove(OVERLAY_A0.packageName, OVERLAY_A0.userId);
        assertFalse(mDatabase.contains(OVERLAY_A0.packageName, OVERLAY_A0.userId));
    }

    public void testGetUsers() throws Exception {
        List<Integer> users = mDatabase.getUsers();
        assertEquals(0, users.size());

        insert(OVERLAY_A0);
        users = mDatabase.getUsers();
        assertEquals(1, users.size());
        assertTrue(users.contains(OVERLAY_A0.userId));

        insert(OVERLAY_A1);
        insert(OVERLAY_B1);
        users = mDatabase.getUsers();
        assertEquals(2, users.size());
        assertTrue(users.contains(OVERLAY_A0.userId));
        assertTrue(users.contains(OVERLAY_A1.userId));
    }

    public void testGetOverlaysForUser() throws Exception {
        insert(OVERLAY_A0);
        insert(OVERLAY_B0);
        insert(OVERLAY_A1);
        insert(OVERLAY_B1);

        Map<String, List<OverlayInfo>> map = mDatabase.getOverlaysForUser(OVERLAY_A0.userId);
        assertEquals(1, map.keySet().size());
        assertTrue(map.keySet().contains(OVERLAY_A0.targetPackageName));

        List<OverlayInfo> list = map.get(OVERLAY_A0.targetPackageName);
        assertEquals(2, list.size());
        assertTrue(list.contains(OVERLAY_A0));
        assertTrue(list.contains(OVERLAY_B0));

        // getOverlaysForUser should never return null
        map = mDatabase.getOverlaysForUser(-1);
        assertNotNull(map);
        assertEquals(0, map.size());
    }

    public void testGetTargetPackageNamesForUser() throws Exception {
        insert(OVERLAY_A0);
        insert(OVERLAY_B0);
        insert(OVERLAY_A1);
        insert(OVERLAY_B1);

        List<String> list = mDatabase.getTargetPackageNamesForUser(OVERLAY_A0.userId);
        assertEquals(1, list.size());
        assertTrue(list.contains(OVERLAY_A0.targetPackageName));

        // getTargetPackageNamesForUser should never return null
        list = mDatabase.getTargetPackageNamesForUser(-1);
        assertNotNull(list);
        assertEquals(0, list.size());
    }

    public void testRemoveUser() throws Exception {
        insert(OVERLAY_A0);
        insert(OVERLAY_B0);
        insert(OVERLAY_A1);

        assertTrue(mDatabase.contains(OVERLAY_A0.packageName, OVERLAY_A0.userId));
        assertTrue(mDatabase.contains(OVERLAY_B0.packageName, OVERLAY_B0.userId));
        assertTrue(mDatabase.contains(OVERLAY_A1.packageName, OVERLAY_A1.userId));

        mDatabase.removeUser(OVERLAY_A0.userId);

        assertFalse(mDatabase.contains(OVERLAY_A0.packageName, OVERLAY_A0.userId));
        assertFalse(mDatabase.contains(OVERLAY_B0.packageName, OVERLAY_B0.userId));
        assertTrue(mDatabase.contains(OVERLAY_A1.packageName, OVERLAY_A1.userId));
    }

    public void testOrderOfNewlyAddedItems() throws Exception {
        // new items are appended to the list
        insert(OVERLAY_A0);
        insert(OVERLAY_B0);
        insert(OVERLAY_C0);

        List<OverlayInfo> list =
            mDatabase.getOverlaysForTarget(OVERLAY_A0.targetPackageName, OVERLAY_A0.userId);
        assertOrder(list, OVERLAY_A0, OVERLAY_B0, OVERLAY_C0);

        // overlays keep their positions when updated
        mDatabase.setState(OVERLAY_B0.packageName, OVERLAY_B0.userId, STATE_APPROVED_ENABLED);
        OverlayInfo oi = mDatabase.getOverlayInfo(OVERLAY_B0.packageName, OVERLAY_B0.userId);

        list = mDatabase.getOverlaysForTarget(OVERLAY_A0.targetPackageName, OVERLAY_A0.userId);
        assertOrder(list, OVERLAY_A0, oi, OVERLAY_C0);
    }

    public void testSetPriority() throws Exception {
        insert(OVERLAY_A0);
        insert(OVERLAY_B0);
        insert(OVERLAY_C0);

        List<OverlayInfo> list =
            mDatabase.getOverlaysForTarget(OVERLAY_A0.targetPackageName, OVERLAY_A0.userId);
        assertOrder(list, OVERLAY_A0, OVERLAY_B0, OVERLAY_C0);

        boolean changed = mDatabase.setPriority(OVERLAY_B0.packageName, OVERLAY_C0.packageName,
                OVERLAY_B0.userId);
        assertTrue(changed);

        list = mDatabase.getOverlaysForTarget(OVERLAY_A0.targetPackageName, OVERLAY_A0.userId);
        assertOrder(list, OVERLAY_A0, OVERLAY_C0, OVERLAY_B0);

        changed = mDatabase.setPriority(OVERLAY_B0.packageName, "does.not.exist",
                OVERLAY_B0.userId);
        assertFalse(changed);

        list = mDatabase.getOverlaysForTarget(OVERLAY_A0.targetPackageName, OVERLAY_A0.userId);
        assertOrder(list, OVERLAY_A0, OVERLAY_C0, OVERLAY_B0);

        OverlayInfo otherTarget = new OverlayInfo(
                "com.dummy.overlay_other",
                "com.dummy.some.other.target",
                "/data/app/com.dummy.overlay_other-1/base.apk",
                STATE_APPROVED_DISABLED,
                0);
        insert(otherTarget);
        changed = mDatabase.setPriority(OVERLAY_A0.packageName, otherTarget.packageName,
                OVERLAY_A0.userId);
        assertFalse(changed);
    }

    public void testSetLowestPriority() throws Exception {
        insert(OVERLAY_A0);
        insert(OVERLAY_B0);
        insert(OVERLAY_C0);

        List<OverlayInfo> list =
            mDatabase.getOverlaysForTarget(OVERLAY_A0.targetPackageName, OVERLAY_A0.userId);
        assertOrder(list, OVERLAY_A0, OVERLAY_B0, OVERLAY_C0);

        boolean changed = mDatabase.setLowestPriority(OVERLAY_B0.packageName, OVERLAY_B0.userId);
        assertTrue(changed);

        list = mDatabase.getOverlaysForTarget(OVERLAY_A0.targetPackageName, OVERLAY_A0.userId);
        assertOrder(list, OVERLAY_B0, OVERLAY_A0, OVERLAY_C0);
    }

    public void testSetHighestPriority() throws Exception {
        insert(OVERLAY_A0);
        insert(OVERLAY_B0);
        insert(OVERLAY_C0);

        List<OverlayInfo> list =
            mDatabase.getOverlaysForTarget(OVERLAY_A0.targetPackageName, OVERLAY_A0.userId);
        assertOrder(list, OVERLAY_A0, OVERLAY_B0, OVERLAY_C0);

        boolean changed = mDatabase.setHighestPriority(OVERLAY_B0.packageName, OVERLAY_B0.userId);
        assertTrue(changed);

        list = mDatabase.getOverlaysForTarget(OVERLAY_A0.targetPackageName, OVERLAY_A0.userId);
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

    private static class DatabaseChangeListener implements OverlayManagerDatabase.ChangeListener {
        int externalCallbacks;
        int internalCallbacks;
        OverlayInfo addedOverlayInfo;
        OverlayInfo removedOverlayInfo;
        OverlayInfo changedPriorityOverlayInfo;

        @Override
        public void onDatabaseChanged() {
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

    public void testChangeListenerCallbacks() throws Exception {
        // add listener
        DatabaseChangeListener listener = new DatabaseChangeListener();
        mDatabase.addChangeListener(listener);
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
        mDatabase.setPriority(OVERLAY_A0.packageName, OVERLAY_B0.packageName, OVERLAY_A0.userId);
        assertEquals(4, listener.externalCallbacks);
        assertEquals(OVERLAY_A0, listener.changedPriorityOverlayInfo);

        mDatabase.setHighestPriority(OVERLAY_B0.packageName, OVERLAY_B0.userId);
        assertEquals(5, listener.externalCallbacks);
        assertEquals(OVERLAY_B0, listener.changedPriorityOverlayInfo);

        mDatabase.setLowestPriority(OVERLAY_A0.packageName, OVERLAY_A0.userId);
        assertEquals(6, listener.externalCallbacks);
        assertEquals(OVERLAY_A0, listener.changedPriorityOverlayInfo);

        // onOverlayChanged
        OverlayInfo oi = new OverlayInfo(OVERLAY_A0, STATE_APPROVED_ENABLED);
        mDatabase.setState(OVERLAY_A0.packageName, OVERLAY_A0.userId, STATE_APPROVED_ENABLED);
        assertEquals(7, listener.externalCallbacks);
        assertEquals(oi, listener.addedOverlayInfo);
        assertEquals(OVERLAY_A0, listener.removedOverlayInfo);

        // onOverlayRemoved
        mDatabase.remove(OVERLAY_C0.packageName, OVERLAY_C0.userId);
        assertEquals(8, listener.externalCallbacks);
        assertEquals(OVERLAY_C0, listener.removedOverlayInfo);

        // remove listener
        mDatabase.removeChangeListener(listener);

        mDatabase.remove(OVERLAY_A0.packageName, OVERLAY_A0.userId);
        assertEquals(8, listener.externalCallbacks);

        insert(OVERLAY_A0);
        assertEquals(8, listener.externalCallbacks);
    }

    public void testNoCallbacksDuringFailingOperations() throws Exception {
        DatabaseChangeListener listener = new DatabaseChangeListener();
        mDatabase.addChangeListener(listener);

        mDatabase.remove("does.not.exist", -1);
        assertEquals(listener.externalCallbacks, 0);
    }

    public void testInternalCallbacks() throws Exception {
        DatabaseChangeListener listener = new DatabaseChangeListener();
        mDatabase.addChangeListener(listener);

        insert(OVERLAY_A0);
        assertEquals(1, listener.externalCallbacks);

        int i = listener.internalCallbacks;
        boolean enable = !OVERLAY_A0.isEnabled();
        mDatabase.setEnabled(OVERLAY_A0.packageName, OVERLAY_A0.userId, enable);
        assertTrue(i < listener.internalCallbacks);

        i = listener.internalCallbacks;
        mDatabase.setEnabled(OVERLAY_A0.packageName, OVERLAY_A0.userId, enable);
        assertEquals(i, listener.internalCallbacks); // no change this time

        insert(OVERLAY_B0);
        mDatabase.setState(OVERLAY_B0.packageName, OVERLAY_B0.userId,
                STATE_NOT_APPROVED_COMPONENT_DISABLED);

        i = listener.internalCallbacks;
        int e = listener.externalCallbacks;
        mDatabase.setEnabled(OVERLAY_B0.packageName, OVERLAY_B0.userId, true);
        assertTrue(i < listener.internalCallbacks);
        assertEquals(e, listener.externalCallbacks);

        i = listener.internalCallbacks;
        mDatabase.setUpgrading(OVERLAY_B0.packageName, OVERLAY_B0.userId, true);
        assertTrue(i < listener.internalCallbacks);

        i = listener.internalCallbacks;
        mDatabase.setBaseCodePath(OVERLAY_B0.packageName, OVERLAY_B0.userId, "/foo/bar.apk");
        assertTrue(i < listener.internalCallbacks);
    }

    // tests: persist and restore

    public void testPersistEmpty() throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mDatabase.persist(os);
        String xml = new String(os.toByteArray(), "utf-8");

        assertEquals(1, countXmlTags(xml, "overlays"));
        assertEquals(0, countXmlTags(xml, "row"));
    }

    public void testPersistDifferentOverlaysSameUser() throws Exception {
        insert(OVERLAY_A0);
        insert(OVERLAY_B0);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mDatabase.persist(os);
        final String xml = new String(os.toByteArray(), "utf-8");

        assertEquals(1, countXmlTags(xml, "overlays"));
        assertEquals(2, countXmlTags(xml, "row"));
        assertEquals(1, countXmlAttributesWhere(xml, "row", "packageName", OVERLAY_A0.packageName));
        assertEquals(1, countXmlAttributesWhere(xml, "row", "packageName", OVERLAY_B0.packageName));
        assertEquals(2, countXmlAttributesWhere(xml, "row", "userId", Integer.toString(OVERLAY_A0.userId)));
    }

    public void testPersistSameOverlayDifferentUsers() throws Exception {
        insert(OVERLAY_A0);
        insert(OVERLAY_A1);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mDatabase.persist(os);
        String xml = new String(os.toByteArray(), "utf-8");

        assertEquals(1, countXmlTags(xml, "overlays"));
        assertEquals(2, countXmlTags(xml, "row"));
        assertEquals(2, countXmlAttributesWhere(xml, "row", "packageName", OVERLAY_A0.packageName));
        assertEquals(1, countXmlAttributesWhere(xml, "row", "userId", Integer.toString(OVERLAY_A0.userId)));
        assertEquals(1, countXmlAttributesWhere(xml, "row", "userId", Integer.toString(OVERLAY_A1.userId)));
    }

    public void testPersistEnabled() throws Exception {
        insert(OVERLAY_A0);
        mDatabase.setEnabled(OVERLAY_A0.packageName, OVERLAY_A0.userId, true);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mDatabase.persist(os);
        String xml = new String(os.toByteArray(), "utf-8");

        assertEquals(1, countXmlAttributesWhere(xml, "row", "isEnabled", "true"));
        assertEquals(1, countXmlAttributesWhere(xml, "row", "isUpgrading", "false"));
    }

    public void testPersistUpgrading() throws Exception {
        insert(OVERLAY_A0);
        mDatabase.setUpgrading(OVERLAY_A0.packageName, OVERLAY_A0.userId, true);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mDatabase.persist(os);
        String xml = new String(os.toByteArray(), "utf-8");

        assertEquals(1, countXmlAttributesWhere(xml, "row", "isEnabled", "false"));
        assertEquals(1, countXmlAttributesWhere(xml, "row", "isUpgrading", "true"));
    }

    public void testRestoreEmpty() throws Exception {
        final String xml =
            "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
            "<overlays version=\"1\" />\n";
        ByteArrayInputStream is = new ByteArrayInputStream(xml.getBytes("utf-8"));

        mDatabase.restore(is);
        assertFalse(mDatabase.contains("com.dummy.overlay", UserHandle.USER_OWNER));
    }

    public void testRestoreSingleUserSingleOverlay() throws Exception {
        final String xml =
            "<?xml version='1.0' encoding='utf-8' standalone='yes'?>\n" +
            "<overlays version='1'>\n" +
            "<row packageName='com.dummy.overlay'\n" +
            "     userId='1234'\n" +
            "     targetPackageName='com.dummy.target'\n" +
            "     baseCodePath='/data/app/com.dummy.overlay-1/base.apk'\n" +
            "     state='" + STATE_APPROVED_DISABLED + "'\n" +
            "     isEnabled='false'\n" +
            "     isUpgrading='false' />\n" +
            "</overlays>\n";
        ByteArrayInputStream is = new ByteArrayInputStream(xml.getBytes("utf-8"));

        mDatabase.restore(is);
        OverlayInfo oi = mDatabase.getOverlayInfo("com.dummy.overlay", 1234);
        assertNotNull(oi);
        assertEquals("com.dummy.overlay", oi.packageName);
        assertEquals("com.dummy.target", oi.targetPackageName);
        assertEquals("/data/app/com.dummy.overlay-1/base.apk", oi.baseCodePath);
        assertEquals(1234, oi.userId);
        assertEquals(STATE_APPROVED_DISABLED, oi.state);
        assertFalse(mDatabase.getEnabled("com.dummy.overlay", 1234));
        assertFalse(mDatabase.getUpgrading("com.dummy.overlay", 1234));
    }

    public void testPersistAndRestore() throws Exception {
        insert(OVERLAY_A0);
        insert(OVERLAY_B1);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mDatabase.persist(os);
        String xml = new String(os.toByteArray(), "utf-8");
        ByteArrayInputStream is = new ByteArrayInputStream(xml.getBytes("utf-8"));
        OverlayManagerDatabase newDatabase = new OverlayManagerDatabase();
        newDatabase.restore(is);

        OverlayInfo a = newDatabase.getOverlayInfo(OVERLAY_A0.packageName, OVERLAY_A0.userId);
        assertEquals(OVERLAY_A0, a);

        OverlayInfo b = newDatabase.getOverlayInfo(OVERLAY_B1.packageName, OVERLAY_B1.userId);
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
        mDatabase.init(oi.packageName, oi.userId, oi.targetPackageName, oi.baseCodePath);
        mDatabase.setState(oi.packageName, oi.userId, oi.state);
        mDatabase.setEnabled(oi.packageName, oi.userId, false);
        mDatabase.setUpgrading(oi.packageName, oi.userId, false);
    }
}
