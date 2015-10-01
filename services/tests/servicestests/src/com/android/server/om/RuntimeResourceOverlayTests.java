package com.android.server.om;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.ResourceId;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.SystemClock;
import android.os.UserHandle;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.Suppress;
import android.support.test.runner.AndroidJUnit4;

import com.android.frameworks.servicestests.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;

import org.junit.AfterClass;
import org.junit.AssumptionViolatedException;
import org.junit.Ignore;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.xmlpull.v1.XmlPullParser;

@RunWith(AndroidJUnit4.class)
public class RuntimeResourceOverlayTests {
    private static final String APP_OVERLAY_1 = "com.android.rrotests.app_overlay_1";
    private static final String APP_OVERLAY_2 = "com.android.rrotests.app_overlay_2";
    private static final String SYSTEM_OVERLAY_1 = "com.android.rrotests.system_overlay_1";
    private static final String SYSTEM_OVERLAY_2 = "com.android.rrotests.system_overlay_2";
    private static final String SOME_OTHER_APP = "com.android.rrotests.some_other_app";
    private static final String SOME_OTHER_APP_OVERLAY = "com.android.rrotests.some_other_app_overlay";

    private static final String SOME_OTHER_APP_URI =
        "android.resource://com.android.frameworks.servicestests/raw/some_other_app";

    private static boolean mInitOK = false;

    private Context mContext;
    private int mUserId;
    private Resources mResources;

    @BeforeClass
    public static void beforeClass() throws Exception {
        Context ctx = InstrumentationRegistry.getContext();
        int userId = UserHandle.myUserId();

        // When support for overlays in /data is added, the checks below should
        // be replaced with calls to PackageUtils.install.
        try {
            assumeTrue(PackageUtils.isInstalled(ctx, APP_OVERLAY_1));
            assumeTrue(PackageUtils.isInstalled(ctx, APP_OVERLAY_2));
            assumeTrue(PackageUtils.isInstalled(ctx, SYSTEM_OVERLAY_1));
            assumeTrue(PackageUtils.isInstalled(ctx, SYSTEM_OVERLAY_2));
            assumeTrue(PackageUtils.isInstalled(ctx, SOME_OTHER_APP_OVERLAY));
            mInitOK = true;
        } catch (AssumptionViolatedException e) {
            throw new AssumptionViolatedException("Missing overlay packages: run " +
                    "prepare-overlay-tests.sh, reboot the device and try again");
        }
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (!mInitOK) {
            return;
        }

        Context ctx = InstrumentationRegistry.getContext();
        int userId = UserHandle.myUserId();

        OverlayUtils.disable(ctx, SOME_OTHER_APP_OVERLAY, userId);

        OverlayUtils.disable(ctx, SYSTEM_OVERLAY_2, userId);
        OverlayUtils.disable(ctx, SYSTEM_OVERLAY_1, userId);
        OverlayUtils.disable(ctx, APP_OVERLAY_2, userId);
        OverlayUtils.disable(ctx, APP_OVERLAY_1, userId);
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mUserId = UserHandle.myUserId();
        mResources = mContext.getResources();

        OverlayUtils.disable(mContext, SOME_OTHER_APP_OVERLAY, mUserId);

        OverlayUtils.disable(mContext, SYSTEM_OVERLAY_2, mUserId);
        OverlayUtils.disable(mContext, SYSTEM_OVERLAY_1, mUserId);
        OverlayUtils.disable(mContext, APP_OVERLAY_2, mUserId);
        OverlayUtils.disable(mContext, APP_OVERLAY_1, mUserId);

        OverlayUtils.reorder(mContext, APP_OVERLAY_1, APP_OVERLAY_2, mUserId);
        OverlayUtils.reorder(mContext, SYSTEM_OVERLAY_1, SYSTEM_OVERLAY_2, mUserId);
    }

    @Test
    public void testNoOverlaysEnabled() throws Exception {
        setEnglishLocale();
        assertResource(true, R.bool.b);
        assertResource(0, R.integer.i);
        assertResource("a", R.string.s);
        assertResource(new int[]{1, 2, 3}, R.array.i);
        assertDrawableResource(0xffff9700, 0, 0, R.drawable.d);
        assertRawResource(0x00005665, R.drawable.d);
        assertXmlResource("KitKat", R.xml.cookie, "cookie", "value");
        assertAssetResource("KitKat", "cookie.txt");

        assertResource(true, com.android.internal.R.bool.config_annoy_dianne);

        setSwedishLocale();
        assertResource("A", R.string.s);

        setEnglishLocale();
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

        setSwedishLocale();
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

    @Test
    public void testSingleOverlayEnabled() throws Exception {
        OverlayUtils.enable(mContext, APP_OVERLAY_1, mUserId);
        OverlayUtils.enable(mContext, SYSTEM_OVERLAY_1, mUserId);

        setEnglishLocale();
        assertResource(false, R.bool.b);
        assertResource(1, R.integer.i);
        assertResource("b", R.string.s);
        assertResource(new int[]{4, 5}, R.array.i);
        assertDrawableResource(0xff58ff00, 0, 0, R.drawable.d);
        assertRawResource(0x000051da, R.drawable.d);
        assertXmlResource("Lollipop", R.xml.cookie, "cookie", "value");
        assertAssetResource("Lollipop", "cookie.txt");

        assertResource(false, com.android.internal.R.bool.config_annoy_dianne);

        setSwedishLocale();
        assertResource("B", R.string.s);

        setEnglishLocale();
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

        setSwedishLocale();
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

    @Test
    public void testBothOverlaysEnabled() throws Exception {
        OverlayUtils.enable(mContext, APP_OVERLAY_1, mUserId);
        OverlayUtils.enable(mContext, APP_OVERLAY_2, mUserId);
        OverlayUtils.enable(mContext, SYSTEM_OVERLAY_1, mUserId);
        OverlayUtils.enable(mContext, SYSTEM_OVERLAY_2, mUserId);

        setEnglishLocale();
        assertResource(true, R.bool.b);
        assertResource(2, R.integer.i);
        assertResource("c", R.string.s);
        assertResource(new int[]{6, 7, 8, 9}, R.array.i);
        assertDrawableResource(0xff00d5fe, 0, 0, R.drawable.d);
        assertRawResource(0x0000527d, R.drawable.d);
        assertXmlResource("Marshmallow", R.xml.cookie, "cookie", "value");
        assertAssetResource("Marshmallow", "cookie.txt");

        assertResource(true, com.android.internal.R.bool.config_annoy_dianne);

        setSwedishLocale();
        assertResource("C", R.string.s);

        setEnglishLocale();
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

        setSwedishLocale();
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

    @Ignore("required bookkeeping to expensive to implement")
    @Test
    public void testResourcesFromOtherPackage() throws Exception {
        try {
            PackageUtils.install(mContext, Uri.parse(SOME_OTHER_APP_URI));

            Resources otherResources =
                mContext.getPackageManager().getResourcesForApplication(SOME_OTHER_APP);
            int resid = otherResources.getIdentifier("i", "integer", SOME_OTHER_APP);
            assertTrue(ResourceId.isValid(resid));
            int value = otherResources.getInteger(resid);
            assertEquals(100, value);

            OverlayUtils.enable(mContext, APP_OVERLAY_1, mUserId);
            OverlayUtils.enable(mContext, APP_OVERLAY_2, mUserId);
            OverlayUtils.enable(mContext, SYSTEM_OVERLAY_1, mUserId);
            OverlayUtils.enable(mContext, SYSTEM_OVERLAY_2, mUserId);

            value = otherResources.getInteger(resid);
            assertEquals(100, value);

            OverlayUtils.enable(mContext, SOME_OTHER_APP_OVERLAY, mUserId);

            value = otherResources.getInteger(resid);
            assertEquals(1234, value);
        } finally {
            PackageUtils.uninstall(mContext, SOME_OTHER_APP);
        }
    }

    @Test
    public void testSystemOverlaysShouldApplyAfterInstallation() throws Exception {
        try {
            OverlayUtils.enable(mContext, SYSTEM_OVERLAY_1, mUserId);
            PackageUtils.install(mContext, Uri.parse(SOME_OTHER_APP_URI));

            // give the overlay manager time to detect the new package and
            // inform the package manager about which overlays to use
            SystemClock.sleep(1000);

            Resources otherResources =
                mContext.getPackageManager().getResourcesForApplication(SOME_OTHER_APP);
            int resid = otherResources.getIdentifier("config_annoy_dianne", "bool", "android");
            assertTrue(ResourceId.isValid(resid));
            boolean value  = otherResources.getBoolean(resid);
            assertEquals(false, value);
        } finally {
            PackageUtils.uninstall(mContext, SOME_OTHER_APP);
        }
    }

    @Test
    public void testResourcesFromAndroidPackage() throws Exception {
        Resources otherResources =
                mContext.getPackageManager().getResourcesForApplication("android");
        int resid = otherResources.getIdentifier("config_annoy_dianne", "bool", "android");
        assertTrue(ResourceId.isValid(resid));
        boolean value = otherResources.getBoolean(resid);
        assertTrue(value);

        OverlayUtils.enable(mContext, APP_OVERLAY_1, mUserId);
        OverlayUtils.enable(mContext, APP_OVERLAY_2, mUserId);
        OverlayUtils.enable(mContext, SYSTEM_OVERLAY_1, mUserId);
        OverlayUtils.enable(mContext, SYSTEM_OVERLAY_2, mUserId);

        value = otherResources.getBoolean(resid);
        assertTrue(value);
    }

    @Test
    public void testTheOrderInWhichOverlaysAreEnabledDoesNotMatterPart1() throws Exception {
        OverlayUtils.enable(mContext, APP_OVERLAY_1, mUserId);
        OverlayUtils.enable(mContext, SYSTEM_OVERLAY_1, mUserId);

        setEnglishLocale();
        assertResource(false, R.bool.b);
        assertResource(false, com.android.internal.R.bool.config_annoy_dianne);
    }

    @Test
    public void testTheOrderInWhichOverlaysAreEnabledDoesNotMatterPart2() throws Exception {
        OverlayUtils.enable(mContext, SYSTEM_OVERLAY_1, mUserId);
        OverlayUtils.enable(mContext, APP_OVERLAY_1, mUserId);

        setEnglishLocale();
        assertResource(false, R.bool.b);
        assertResource(false, com.android.internal.R.bool.config_annoy_dianne);
    }

    @Test
    public void testReorderOverlays() throws Exception {
        OverlayUtils.enable(mContext, APP_OVERLAY_1, mUserId);
        OverlayUtils.enable(mContext, APP_OVERLAY_2, mUserId);
        assertResource(2, R.integer.i);

        OverlayUtils.reorder(mContext, APP_OVERLAY_2, APP_OVERLAY_1, mUserId);
        assertResource(1, R.integer.i);
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

    private boolean isApproxEqual(int expected, int actual, int tolerance) {
        return Math.abs(expected - actual) < tolerance;
    }

    private void assertDrawableResource(int expected, int x, int y, int resid) throws Exception {
        int[] actual = new int[1];
        Bitmap bitmap = BitmapFactory.decodeResource(mResources, resid);
        bitmap.getPixels(actual, 0, bitmap.getWidth(), x, y, 1, 1);
        int tolerance = 5;

        // The pixel decoding may produce slightly different values on different platforms:
        // allow for some small variance in the expected vs actual value
        assertTrue(String.format("alpha diff: expected=0x%08x actual=0x%08x", expected, actual[0]),
                isApproxEqual(Color.alpha(expected), Color.alpha(actual[0]), tolerance));
        assertTrue(String.format("red diff: expected=0x%08x actual=0x%08x", expected, actual[0]),
                isApproxEqual(Color.red(expected), Color.red(actual[0]), tolerance));
        assertTrue(String.format("green diff: expected=0x%08x actual=0x%08x", expected, actual[0]),
                isApproxEqual(Color.green(expected), Color.green(actual[0]), tolerance));
        assertTrue(String.format("blue diff: expected=0x%08x actual=0x%08x", expected, actual[0]),
                isApproxEqual(Color.blue(expected), Color.blue(actual[0]), tolerance));
    }

    private void assertRawResource(int expected, int resid) throws Exception {
        int actual = calculateRawResourceChecksum(resid);
        assertEquals(String.format("expected=0x%08x actual=0x%08x", expected, actual),
                expected, actual);
    }

    private void assertXmlResource(String expected, int resid, String tag, String attr)
            throws Exception {
        String actual = readXml(resid, tag, attr);
        assertEquals(expected, actual);
    }

    private void assertAssetResource(String expected, String path) throws Exception {
        String actual = readAsset(path);
        assertEquals(expected, actual);
    }

    private static void setLocale(Resources res, Locale locale) {
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.locale = locale;
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    private void setEnglishLocale() {
        setLocale(mResources, new Locale("en", "US"));
    }

    private void setSwedishLocale() {
        setLocale(mResources, new Locale("sv", "SE"));
    }

    private int calculateRawResourceChecksum(int resid) throws Exception {
        InputStream input = null;
        try {
            input = mResources.openRawResource(resid);
            int ch, checksum = 0;
            while ((ch = input.read()) != -1) {
                checksum = (checksum + ch) % 0xffddbb00;
            }
            return checksum;
        } finally {
            input.close();
        }
    }

    private String readAsset(String path) throws Exception {
        AssetManager am = mResources.getAssets();
        StringBuilder sb = new StringBuilder();
        BufferedReader br = null;
        try {
            String line;
            InputStream is = am.open(path);
            br = new BufferedReader(new InputStreamReader(is));
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } finally {
            if (br != null) {
                br.close();
            }
        }
        return sb.toString();
    }

    /**
     * Fetch the value of the first <tag attr="..."/> tag in XML resource resid.
     */
    private String readXml(int resid, String tag, String attr) throws Exception {
        XmlPullParser parser = mResources.getXml(resid);
        String value = null;
        int type = parser.getEventType();
        while (type != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.START_TAG && tag.equals(parser.getName())) {
                value = parser.getAttributeValue(null, attr);
                break;
            }
            type = parser.next();
        }
        return value;
    }
}
