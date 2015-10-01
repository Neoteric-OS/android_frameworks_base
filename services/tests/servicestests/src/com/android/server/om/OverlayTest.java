package com.android.server.om;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.test.AndroidTestCase;

import com.android.frameworks.servicestests.R;

public class OverlayTest extends AndroidTestCase {
    private static final String APP_OVERLAY_1 = "com.android.rrotests.app_overlay_1";
    private static final String APP_OVERLAY_2 = "com.android.rrotests.app_overlay_2";
    private static final String APP_OVERLAY_3 = "com.android.rrotests.app_overlay_3";
    private static final String APP_OVERLAY_4 = "com.android.rrotests.app_overlay_4";
    private static final String SYSTEM_OVERLAY_1 = "com.android.rrotests.system_overlay_1";
    private static final String SYSTEM_OVERLAY_2 = "com.android.rrotests.system_overlay_2";
    private static final String SOME_OTHER_APP = "com.android.rrotests.some_other_app";
    private static final String SOME_OTHER_APP_OVERLAY = "com.android.rrotests.some_other_app_overlay";

    private Resources mResources;

    private void installAllPackages() throws Exception {
        Utils.installOverlayFromResource(mContext, APP_OVERLAY_1, R.raw.app_overlay_1);
        Utils.installOverlayFromResource(mContext, APP_OVERLAY_2, R.raw.app_overlay_2);
        Utils.installOverlayFromResource(mContext, APP_OVERLAY_3, R.raw.app_overlay_3);
        Utils.installOverlayFromResource(mContext, APP_OVERLAY_4, R.raw.app_overlay_4);
        Utils.installOverlayFromResource(mContext, SYSTEM_OVERLAY_1, R.raw.system_overlay_1);
        Utils.installOverlayFromResource(mContext, SYSTEM_OVERLAY_2, R.raw.system_overlay_2);

        Utils.orderOverlays(mContext, APP_OVERLAY_1, APP_OVERLAY_2);
        Utils.orderOverlays(mContext, APP_OVERLAY_2, APP_OVERLAY_3);
        Utils.orderOverlays(mContext, APP_OVERLAY_3, APP_OVERLAY_4);
        Utils.orderOverlays(mContext, SYSTEM_OVERLAY_1, SYSTEM_OVERLAY_2);

        Utils.installPackageFromResource(mContext, SOME_OTHER_APP, R.raw.some_other_app);
        Utils.installOverlayFromResource(mContext, SOME_OTHER_APP_OVERLAY,
                R.raw.some_other_app_overlay);
    }

    private void disableAllOverlays() throws Exception {
        Utils.disableOverlay(mContext, SOME_OTHER_APP_OVERLAY);

        Utils.disableOverlay(mContext, SYSTEM_OVERLAY_2);
        Utils.disableOverlay(mContext, SYSTEM_OVERLAY_1);
        Utils.disableOverlay(mContext, APP_OVERLAY_4);
        Utils.disableOverlay(mContext, APP_OVERLAY_3);
        Utils.disableOverlay(mContext, APP_OVERLAY_2);
        Utils.disableOverlay(mContext, APP_OVERLAY_1);
    }

    protected void setUp() throws Exception {
        mResources = mContext.getResources();

        installAllPackages();
        disableAllOverlays();
    }

    protected void tearDown() throws Exception {
        disableAllOverlays();
    }

    public void testNoOverlaysEnabled() throws Exception {
        Utils.setEnglishLocale(mContext);
        assertResource(true, R.bool.b);
        assertResource(0, R.integer.i);
        assertResource("a", R.string.s);
        assertResource(new int[]{1, 2, 3}, R.array.i);
        assertDrawableResource(0xffff9700, 0, 0, R.drawable.d);
        assertRawResource(0x00005665, R.drawable.d);
        assertXmlResource("KitKat", R.xml.cookie, "cookie", "value");
        assertAssetResource("KitKat", "cookie.txt");

        assertResource(true, com.android.internal.R.bool.config_annoy_dianne);

        Utils.setSwedishLocale(mContext);
        assertResource("A", R.string.s);

        Utils.setEnglishLocale(mContext);
        assertResource(100, R.integer.matrix_100000);
        assertResource(100, R.integer.matrix_100001);
        assertResource(100, R.integer.matrix_100010);
        assertResource(100, R.integer.matrix_100011);
        assertResource(100, R.integer.matrix_100100);
        assertResource(100, R.integer.matrix_100101);
        assertResource(100, R.integer.matrix_100110);
        assertResource(100, R.integer.matrix_100111);
        assertResource(100, R.integer.matrix_101000);
        assertResource(100, R.integer.matrix_101001);
        assertResource(100, R.integer.matrix_101010);
        assertResource(100, R.integer.matrix_101011);
        assertResource(100, R.integer.matrix_101100);
        assertResource(100, R.integer.matrix_101101);
        assertResource(100, R.integer.matrix_101110);
        assertResource(100, R.integer.matrix_101111);
        assertResource(100, R.integer.matrix_110000);
        assertResource(100, R.integer.matrix_110001);
        assertResource(100, R.integer.matrix_110010);
        assertResource(100, R.integer.matrix_110011);
        assertResource(100, R.integer.matrix_110100);
        assertResource(100, R.integer.matrix_110101);
        assertResource(100, R.integer.matrix_110110);
        assertResource(100, R.integer.matrix_110111);
        assertResource(100, R.integer.matrix_111000);
        assertResource(100, R.integer.matrix_111001);
        assertResource(100, R.integer.matrix_111010);
        assertResource(100, R.integer.matrix_111011);
        assertResource(100, R.integer.matrix_111100);
        assertResource(100, R.integer.matrix_111101);
        assertResource(100, R.integer.matrix_111110);
        assertResource(100, R.integer.matrix_111111);

        Utils.setSwedishLocale(mContext);
        assertResource(100, R.integer.matrix_100000);
        assertResource(100, R.integer.matrix_100001);
        assertResource(100, R.integer.matrix_100010);
        assertResource(100, R.integer.matrix_100011);
        assertResource(100, R.integer.matrix_100100);
        assertResource(100, R.integer.matrix_100101);
        assertResource(100, R.integer.matrix_100110);
        assertResource(100, R.integer.matrix_100111);
        assertResource(100, R.integer.matrix_101000);
        assertResource(100, R.integer.matrix_101001);
        assertResource(100, R.integer.matrix_101010);
        assertResource(100, R.integer.matrix_101011);
        assertResource(100, R.integer.matrix_101100);
        assertResource(100, R.integer.matrix_101101);
        assertResource(100, R.integer.matrix_101110);
        assertResource(100, R.integer.matrix_101111);
        assertResource(200, R.integer.matrix_110000);
        assertResource(200, R.integer.matrix_110001);
        assertResource(200, R.integer.matrix_110010);
        assertResource(200, R.integer.matrix_110011);
        assertResource(200, R.integer.matrix_110100);
        assertResource(200, R.integer.matrix_110101);
        assertResource(200, R.integer.matrix_110110);
        assertResource(200, R.integer.matrix_110111);
        assertResource(200, R.integer.matrix_111000);
        assertResource(200, R.integer.matrix_111001);
        assertResource(200, R.integer.matrix_111010);
        assertResource(200, R.integer.matrix_111011);
        assertResource(200, R.integer.matrix_111100);
        assertResource(200, R.integer.matrix_111101);
        assertResource(200, R.integer.matrix_111110);
        assertResource(200, R.integer.matrix_111111);
    }

    public void testSingleOverlayEnabled() throws Exception {
        Utils.enableOverlay(mContext, APP_OVERLAY_1);
        Utils.enableOverlay(mContext, SYSTEM_OVERLAY_1);

        Utils.setEnglishLocale(mContext);
        assertResource(false, R.bool.b);
        assertResource(1, R.integer.i);
        assertResource("b", R.string.s);
        assertResource(new int[]{4, 5}, R.array.i);
        assertDrawableResource(0xff58ff00, 0, 0, R.drawable.d);
        assertRawResource(0x000051da, R.drawable.d);
        assertXmlResource("Lollipop", R.xml.cookie, "cookie", "value");
        assertAssetResource("Lollipop", "cookie.txt");

        assertResource(false, com.android.internal.R.bool.config_annoy_dianne);

        Utils.setSwedishLocale(mContext);
        assertResource("B", R.string.s);

        Utils.setEnglishLocale(mContext);
        assertResource(100, R.integer.matrix_100000);
        assertResource(100, R.integer.matrix_100001);
        assertResource(100, R.integer.matrix_100010);
        assertResource(100, R.integer.matrix_100011);
        assertResource(100, R.integer.matrix_100100);
        assertResource(100, R.integer.matrix_100101);
        assertResource(100, R.integer.matrix_100110);
        assertResource(100, R.integer.matrix_100111);
        assertResource(300, R.integer.matrix_101000);
        assertResource(300, R.integer.matrix_101001);
        assertResource(300, R.integer.matrix_101010);
        assertResource(300, R.integer.matrix_101011);
        assertResource(300, R.integer.matrix_101100);
        assertResource(300, R.integer.matrix_101101);
        assertResource(300, R.integer.matrix_101110);
        assertResource(300, R.integer.matrix_101111);
        assertResource(100, R.integer.matrix_110000);
        assertResource(100, R.integer.matrix_110001);
        assertResource(100, R.integer.matrix_110010);
        assertResource(100, R.integer.matrix_110011);
        assertResource(100, R.integer.matrix_110100);
        assertResource(100, R.integer.matrix_110101);
        assertResource(100, R.integer.matrix_110110);
        assertResource(100, R.integer.matrix_110111);
        assertResource(300, R.integer.matrix_111000);
        assertResource(300, R.integer.matrix_111001);
        assertResource(300, R.integer.matrix_111010);
        assertResource(300, R.integer.matrix_111011);
        assertResource(300, R.integer.matrix_111100);
        assertResource(300, R.integer.matrix_111101);
        assertResource(300, R.integer.matrix_111110);
        assertResource(300, R.integer.matrix_111111);

        Utils.setSwedishLocale(mContext);
        assertResource(100, R.integer.matrix_100000);
        assertResource(100, R.integer.matrix_100001);
        assertResource(100, R.integer.matrix_100010);
        assertResource(100, R.integer.matrix_100011);
        assertResource(400, R.integer.matrix_100100);
        assertResource(400, R.integer.matrix_100101);
        assertResource(400, R.integer.matrix_100110);
        assertResource(400, R.integer.matrix_100111);
        assertResource(300, R.integer.matrix_101000);
        assertResource(300, R.integer.matrix_101001);
        assertResource(300, R.integer.matrix_101010);
        assertResource(300, R.integer.matrix_101011);
        assertResource(400, R.integer.matrix_101100);
        assertResource(400, R.integer.matrix_101101);
        assertResource(400, R.integer.matrix_101110);
        assertResource(400, R.integer.matrix_101111);
        assertResource(200, R.integer.matrix_110000);
        assertResource(200, R.integer.matrix_110001);
        assertResource(200, R.integer.matrix_110010);
        assertResource(200, R.integer.matrix_110011);
        assertResource(400, R.integer.matrix_110100);
        assertResource(400, R.integer.matrix_110101);
        assertResource(400, R.integer.matrix_110110);
        assertResource(400, R.integer.matrix_110111);
        assertResource(200, R.integer.matrix_111000);
        assertResource(200, R.integer.matrix_111001);
        assertResource(200, R.integer.matrix_111010);
        assertResource(200, R.integer.matrix_111011);
        assertResource(400, R.integer.matrix_111100);
        assertResource(400, R.integer.matrix_111101);
        assertResource(400, R.integer.matrix_111110);
        assertResource(400, R.integer.matrix_111111);
    }

    public void testBothOverlaysEnabled() throws Exception {
        Utils.enableOverlay(mContext, APP_OVERLAY_1);
        Utils.enableOverlay(mContext, APP_OVERLAY_2);
        Utils.enableOverlay(mContext, SYSTEM_OVERLAY_1);
        Utils.enableOverlay(mContext, SYSTEM_OVERLAY_2);

        Utils.setEnglishLocale(mContext);
        assertResource(true, R.bool.b);
        assertResource(2, R.integer.i);
        assertResource("c", R.string.s);
        assertResource(new int[]{6, 7, 8, 9}, R.array.i);
        assertDrawableResource(0xff00d5fe, 0, 0, R.drawable.d);
        assertRawResource(0x0000527d, R.drawable.d);
        assertXmlResource("Marshmallow", R.xml.cookie, "cookie", "value");
        assertAssetResource("Marshmallow", "cookie.txt");

        assertResource(true, com.android.internal.R.bool.config_annoy_dianne);

        Utils.setSwedishLocale(mContext);
        assertResource("C", R.string.s);

        Utils.setEnglishLocale(mContext);
        assertResource(100, R.integer.matrix_100000);
        assertResource(100, R.integer.matrix_100001);
        assertResource(500, R.integer.matrix_100010);
        assertResource(500, R.integer.matrix_100011);
        assertResource(100, R.integer.matrix_100100);
        assertResource(100, R.integer.matrix_100101);
        assertResource(500, R.integer.matrix_100110);
        assertResource(500, R.integer.matrix_100111);
        assertResource(300, R.integer.matrix_101000);
        assertResource(300, R.integer.matrix_101001);
        assertResource(500, R.integer.matrix_101010);
        assertResource(500, R.integer.matrix_101011);
        assertResource(300, R.integer.matrix_101100);
        assertResource(300, R.integer.matrix_101101);
        assertResource(500, R.integer.matrix_101110);
        assertResource(500, R.integer.matrix_101111);
        assertResource(100, R.integer.matrix_110000);
        assertResource(100, R.integer.matrix_110001);
        assertResource(500, R.integer.matrix_110010);
        assertResource(500, R.integer.matrix_110011);
        assertResource(100, R.integer.matrix_110100);
        assertResource(100, R.integer.matrix_110101);
        assertResource(500, R.integer.matrix_110110);
        assertResource(500, R.integer.matrix_110111);
        assertResource(300, R.integer.matrix_111000);
        assertResource(300, R.integer.matrix_111001);
        assertResource(500, R.integer.matrix_111010);
        assertResource(500, R.integer.matrix_111011);
        assertResource(300, R.integer.matrix_111100);
        assertResource(300, R.integer.matrix_111101);
        assertResource(500, R.integer.matrix_111110);
        assertResource(500, R.integer.matrix_111111);

        Utils.setSwedishLocale(mContext);
        assertResource(100, R.integer.matrix_100000);
        assertResource(600, R.integer.matrix_100001);
        assertResource(500, R.integer.matrix_100010);
        assertResource(600, R.integer.matrix_100011);
        assertResource(400, R.integer.matrix_100100);
        assertResource(600, R.integer.matrix_100101);
        assertResource(400, R.integer.matrix_100110);
        assertResource(600, R.integer.matrix_100111);
        assertResource(300, R.integer.matrix_101000);
        assertResource(600, R.integer.matrix_101001);
        assertResource(500, R.integer.matrix_101010);
        assertResource(600, R.integer.matrix_101011);
        assertResource(400, R.integer.matrix_101100);
        assertResource(600, R.integer.matrix_101101);
        assertResource(400, R.integer.matrix_101110);
        assertResource(600, R.integer.matrix_101111);
        assertResource(200, R.integer.matrix_110000);
        assertResource(600, R.integer.matrix_110001);
        assertResource(200, R.integer.matrix_110010);
        assertResource(600, R.integer.matrix_110011);
        assertResource(400, R.integer.matrix_110100);
        assertResource(600, R.integer.matrix_110101);
        assertResource(400, R.integer.matrix_110110);
        assertResource(600, R.integer.matrix_110111);
        assertResource(200, R.integer.matrix_111000);
        assertResource(600, R.integer.matrix_111001);
        assertResource(200, R.integer.matrix_111010);
        assertResource(600, R.integer.matrix_111011);
        assertResource(400, R.integer.matrix_111100);
        assertResource(600, R.integer.matrix_111101);
        assertResource(400, R.integer.matrix_111110);
        assertResource(600, R.integer.matrix_111111);
    }

    public void testResourcesFromOtherPackage() throws Exception {
        Resources otherResources =
            mContext.getPackageManager().getResourcesForApplication(SOME_OTHER_APP);
        int resid = otherResources.getIdentifier("i", "integer", SOME_OTHER_APP);
        assertFalse(resid == 0);
        int value = otherResources.getInteger(resid);
        assertEquals(100, value);

        Utils.enableOverlay(mContext, APP_OVERLAY_1);
        Utils.enableOverlay(mContext, APP_OVERLAY_2);
        Utils.enableOverlay(mContext, SYSTEM_OVERLAY_1);
        Utils.enableOverlay(mContext, SYSTEM_OVERLAY_2);

        value = otherResources.getInteger(resid);
        assertEquals(100, value);

        Utils.enableOverlay(mContext, SOME_OTHER_APP_OVERLAY);

        value = otherResources.getInteger(resid);
        assertEquals(100, value);
    }

    public void testResourcesFromAndroidPackage() throws Exception {
        Resources otherResources =
            mContext.getPackageManager().getResourcesForApplication("android");
        int resid = otherResources.getIdentifier("config_annoy_dianne", "bool", "android");
        assertFalse(resid == 0);
        boolean value = otherResources.getBoolean(resid);
        assertTrue(value);

        Utils.enableOverlay(mContext, APP_OVERLAY_1);
        Utils.enableOverlay(mContext, APP_OVERLAY_2);
        Utils.enableOverlay(mContext, SYSTEM_OVERLAY_1);
        Utils.enableOverlay(mContext, SYSTEM_OVERLAY_2);

        value = otherResources.getBoolean(resid);
        assertTrue(value);
    }

    public void testResourcesFromOtherSignatures() throws Exception {
        assertTrue(Utils.isOverlayApproved(mContext, APP_OVERLAY_3));
        assertFalse(Utils.isOverlayApproved(mContext, APP_OVERLAY_4));

        Utils.enableOverlay(mContext, APP_OVERLAY_3);
        assertResource(3, R.integer.i);
    }

    private void assertResource(boolean expected, int resid) throws Exception {
        boolean actual = mResources.getBoolean(resid);
        assertEquals(expected, actual);
    }

    private void assertResource(int expected, int resid) throws Exception {
        int actual = mResources.getInteger(resid);
        assertEquals(expected, actual);
    }

    private void assertResource(String expected, int resid) throws Exception {
        String actual = mResources.getString(resid);
        assertEquals(expected, actual);
    }

    private void assertResource(int[] expected, int resid) throws Exception {
        int[] actual = mResources.getIntArray(resid);
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i]);
        }
    }

    private void assertDrawableResource(int expected, int x, int y, int resid) throws Exception {
        int[] actual = new int[1];
        Bitmap bitmap = BitmapFactory.decodeResource(mResources, resid);
        bitmap.getPixels(actual, 0, bitmap.getWidth(), x, y, 1, 1);
        assertEquals(String.format("actual=0x%08x expected=0x%08x", actual[0], expected),
                actual[0], expected);
    }

    private void assertRawResource(int expected, int resid) throws Exception {
        int actual = Utils.calculateRawResourceChecksum(mContext, resid);
        assertEquals(String.format("actual=0x%08x expected=0x%08x", expected, actual),
                expected, actual);
    }

    private void assertXmlResource(String expected, int resid, String tag, String attr)
            throws Exception {
        String actual = Utils.readXml(mContext, resid, tag, attr);
        assertEquals(expected, actual);
    }

    private void assertAssetResource(String expected, String path) throws Exception {
        String actual = Utils.readAsset(mContext, path);
        assertEquals(expected, actual);
    }
}
