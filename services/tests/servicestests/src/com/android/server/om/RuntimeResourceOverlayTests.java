package com.android.server.om;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.app.PackageInstallObserver;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileUtils;
import android.os.IBinder;
import android.os.PatternMatcher;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.android.frameworks.servicestests.R;

import org.xmlpull.v1.XmlPullParser;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class RuntimeResourceOverlayTests {
    private static final String APP_OVERLAY_1 = "com.android.rrotests.app_overlay_1";
    private static final String APP_OVERLAY_2 = "com.android.rrotests.app_overlay_2";
    private static final String APP_OVERLAY_3 = "com.android.rrotests.app_overlay_3";
    private static final String APP_OVERLAY_4 = "com.android.rrotests.app_overlay_4";
    private static final String SYSTEM_OVERLAY_1 = "com.android.rrotests.system_overlay_1";
    private static final String SYSTEM_OVERLAY_2 = "com.android.rrotests.system_overlay_2";
    private static final String SOME_OTHER_APP = "com.android.rrotests.some_other_app";
    private static final String SOME_OTHER_APP_OVERLAY = "com.android.rrotests.some_other_app_overlay";

    private Context mContext;
    private Resources mResources;

    @BeforeClass
    public static void beforeClass() throws Exception {
        Context ctx = InstrumentationRegistry.getContext();

        Utils.installOverlayFromResource(ctx, APP_OVERLAY_1, R.raw.app_overlay_1);
        Utils.installOverlayFromResource(ctx, APP_OVERLAY_2, R.raw.app_overlay_2);
        Utils.installOverlayFromResource(ctx, APP_OVERLAY_3, R.raw.app_overlay_3);
        Utils.installOverlayFromResource(ctx, APP_OVERLAY_4, R.raw.app_overlay_4);
        Utils.installOverlayFromResource(ctx, SYSTEM_OVERLAY_1, R.raw.system_overlay_1);
        Utils.installOverlayFromResource(ctx, SYSTEM_OVERLAY_2, R.raw.system_overlay_2);

        Utils.orderOverlays(ctx, APP_OVERLAY_1, APP_OVERLAY_2, UserHandle.myUserId());
        Utils.orderOverlays(ctx, APP_OVERLAY_2, APP_OVERLAY_3, UserHandle.myUserId());
        Utils.orderOverlays(ctx, APP_OVERLAY_3, APP_OVERLAY_4, UserHandle.myUserId());
        Utils.orderOverlays(ctx, SYSTEM_OVERLAY_1, SYSTEM_OVERLAY_2, UserHandle.myUserId());

        Utils.installPackageFromResource(ctx, SOME_OTHER_APP, R.raw.some_other_app);
        Utils.installOverlayFromResource(ctx, SOME_OTHER_APP_OVERLAY,
                R.raw.some_other_app_overlay);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        Context ctx = InstrumentationRegistry.getContext();

        Utils.uninstall(ctx, APP_OVERLAY_1);
        Utils.uninstall(ctx, APP_OVERLAY_2);
        Utils.uninstall(ctx, APP_OVERLAY_3);
        Utils.uninstall(ctx, APP_OVERLAY_4);
        Utils.uninstall(ctx, SYSTEM_OVERLAY_1);
        Utils.uninstall(ctx, SYSTEM_OVERLAY_2);

        Utils.uninstall(ctx, SOME_OTHER_APP);
        Utils.uninstall(ctx, SOME_OTHER_APP_OVERLAY);
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mResources = mContext.getResources();

        Utils.disableOverlay(mContext, SOME_OTHER_APP_OVERLAY);

        Utils.disableOverlay(mContext, SYSTEM_OVERLAY_2);
        Utils.disableOverlay(mContext, SYSTEM_OVERLAY_1);
        Utils.disableOverlay(mContext, APP_OVERLAY_4);
        Utils.disableOverlay(mContext, APP_OVERLAY_3);
        Utils.disableOverlay(mContext, APP_OVERLAY_2);
        Utils.disableOverlay(mContext, APP_OVERLAY_1);
    }

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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
        int actual = Utils.calculateRawResourceChecksum(mContext, resid);
        assertEquals(String.format("expected=0x%08x actual=0x%08x", expected, actual),
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

    private static class Utils {
        private static final long MAX_WAIT_TIME = 30 * 1000;

        private Utils() {}

        public static void installPackageFromResource(Context ctx, String packageName, int resid)
            throws Exception {
            if (!isPackageInstalled(ctx, packageName)) {
                installFromResource(ctx, packageName, resid, Intent.ACTION_PACKAGE_ADDED);
            }
        }

        public static void installOverlayFromResource(Context ctx, String packageName, int resid)
            throws Exception {
            if (!isPackageInstalled(ctx, packageName)) {
                installFromResource(ctx, packageName, resid, Intent.ACTION_OVERLAY_ADDED);
            }
        }

        private static boolean isPackageInstalled(Context ctx, String packageName) throws Exception {
            PackageManager pm = ctx.getPackageManager();
            try {
                PackageInfo info = pm.getPackageInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
            return true;
        }

        private static void installFromResource(Context ctx, String packageName, int resid,
                String action) throws Exception {
            File file = null;
            try {
                file = extractRawResource(ctx, resid);
                Uri uri = Uri.fromFile(file);

                GenericObserver observer = new GenericObserver();
                IntentFilter filter = new IntentFilter();
                filter.addAction(action);
                filter.addDataScheme("package");
                filter.addDataSchemeSpecificPart(packageName, PatternMatcher.PATTERN_LITERAL);
                ctx.registerReceiver(observer, filter);

                InstallObserver installObserver = new InstallObserver();

                PackageManager pm = ctx.getPackageManager();
                pm.installPackage(uri, installObserver, 0, null);
                if (!installObserver.getResult(MAX_WAIT_TIME) || !observer.getResult(MAX_WAIT_TIME)) {
                    throw new Exception("failed to install package " + packageName);
                }

                ctx.unregisterReceiver(observer);
            } finally {
                if (file != null) {
                    file.delete();
                }
            }
        }

        private static class InstallObserver extends PackageInstallObserver {
            private final BlockingQueue<Boolean> mResults = new LinkedBlockingQueue<Boolean>(1);

            @Override
            public void onPackageInstalled(String basePackageName, int returnCode, String msg,
                    Bundle extras) {
                try {
                    mResults.put(true);
                } catch (Exception e) {}
            }

            private boolean getResult(long maxWaitTime) throws InterruptedException {
                Boolean result = mResults.poll(maxWaitTime, MILLISECONDS);
                return result == null ? false : result;
            }
        }

        private static class GenericObserver extends BroadcastReceiver {
            private final BlockingQueue<Boolean> mResults = new LinkedBlockingQueue<Boolean>(1);

            @Override
            public void onReceive(Context ctx, Intent intent) {
                try {
                    mResults.put(true);
                } catch (Exception e) {}
            }

            private boolean getResult(long maxWaitTime) throws InterruptedException {
                Boolean result = mResults.poll(maxWaitTime, MILLISECONDS);
                return result == null ? false : result;
            }
        }

        private static File extractRawResource(Context ctx, int resid) throws Exception {
            Resources res = ctx.getResources();
            InputStream in = res.openRawResource(resid);
            String prefix = String.format("%s_", res.getResourceEntryName(resid));
            File out = File.createTempFile(prefix, ".apk", Environment.getExternalStorageDirectory());
            try {
                FileUtils.copyToFileOrThrow(in, out);
            } catch (IOException e) {
                String msg =
                    String.format("failed to extract resource 0x%08x to %s", resid, out.getPath());
                throw new Exception(msg, e);
            }
            FileUtils.setPermissions(out.getAbsolutePath(), 0777, -1, -1);
            return out;
        }

        public static void uninstall(Context ctx, String packageName) throws Exception {
            if (isPackageInstalled(ctx, packageName)) {
                DeleteObserver observer = new DeleteObserver(packageName);
                PackageManager pm = ctx.getPackageManager();
                pm.deletePackage(packageName, observer, PackageManager.DELETE_ALL_USERS);
                observer.waitForCompletion(MAX_WAIT_TIME);
            }
        }

        private static class DeleteObserver extends IPackageDeleteObserver.Stub {
            private CountDownLatch mLatch = new CountDownLatch(1);

            private int mReturnCode;

            private final String mPackageName;

            private String mObservedPackage;

            public DeleteObserver(String packageName) {
                mPackageName = packageName;
            }

            public boolean isSuccessful() {
                return mReturnCode == PackageManager.DELETE_SUCCEEDED;
            }

            public void packageDeleted(String packageName, int returnCode) throws RemoteException {
                mObservedPackage = packageName;

                mReturnCode = returnCode;

                mLatch.countDown();
            }

            public void waitForCompletion(long timeoutMillis) {
                final long deadline = SystemClock.uptimeMillis() + timeoutMillis;

                long waitTime = timeoutMillis;
                while (waitTime > 0) {
                    try {
                        boolean done = mLatch.await(waitTime, TimeUnit.MILLISECONDS);
                        if (done) {
                            assertEquals(mPackageName, mObservedPackage);
                            return;
                        }
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    waitTime = deadline - SystemClock.uptimeMillis();
                }

                throw new AssertionError("Timeout waiting for package deletion");
            }
        }

        private static IOverlayManager getOverlayManager(Context ctx) {
            IBinder b = ServiceManager.getService("overlay");
            IOverlayManager om = IOverlayManager.Stub.asInterface(b);
            return om;
        }

        public static void enableOverlay(Context ctx, String packageName) throws Exception {
            if (!isOverlayEnabled(ctx, packageName)) {
                toggleOverlay(ctx, packageName, true);
            }
        }

        public static void disableOverlay(Context ctx, String packageName) throws Exception {
            if (isOverlayEnabled(ctx, packageName)) {
                toggleOverlay(ctx, packageName, false);
            }
        }

        private static boolean isOverlayEnabled(Context ctx, String packageName) throws Exception {
            IOverlayManager om = getOverlayManager(ctx);
            OverlayInfo info = om.getOverlayInfo(packageName, UserHandle.myUserId());
            return info.isEnabled();
        }

        private static void toggleOverlay(Context ctx, String packageName, boolean enable) throws Exception {
            String action = enable ? "enable" : "disable";
            IOverlayManager om = getOverlayManager(ctx);
            ToggleOverlayObserver observer = new ToggleOverlayObserver(enable);
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_OVERLAY_CHANGED);
            filter.addDataScheme("package");
            filter.addDataSchemeSpecificPart(packageName, PatternMatcher.PATTERN_LITERAL);
            ctx.registerReceiver(observer, filter);

            if (!om.setEnabled(packageName, enable, UserHandle.myUserId())) {
                throw new Exception("failed to " + action + " overlay " + packageName);
            }
            if (!observer.getResult(MAX_WAIT_TIME)) {
                throw new Exception("failed to " + action + " overlay " + packageName);
            }

            ctx.unregisterReceiver(observer);
        }

        private static class ToggleOverlayObserver extends BroadcastReceiver {
            private final BlockingQueue<Boolean> mResults = new LinkedBlockingQueue<Boolean>(1);
            private final boolean mExpectEnabled;

            ToggleOverlayObserver(boolean expectEnabled) {
                mExpectEnabled = expectEnabled;
            }

            @Override
            public void onReceive(Context ctx, Intent intent) {
                try {
                    IOverlayManager om = getOverlayManager(ctx);
                    String packageName = intent.getData().getEncodedSchemeSpecificPart();
                    OverlayInfo info = om.getOverlayInfo(packageName, UserHandle.myUserId());
                    mResults.put(info.isEnabled() == mExpectEnabled);
                } catch (Exception e) {}
            }

            private boolean getResult(long maxWaitTime) throws InterruptedException {
                Boolean result = mResults.poll(maxWaitTime, MILLISECONDS);
                return result == null ? false : result;
            }
        }

        public static void orderOverlays(Context ctx, String lessImportantPackageName,
                String moreImportantPackageName, int userId) throws Exception {
            IOverlayManager om = getOverlayManager(ctx);

            ReorderOverlaysObserver observer = new ReorderOverlaysObserver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_OVERLAY_PRIORITY_CHANGED);
            filter.addDataScheme("package");
            filter.addDataSchemeSpecificPart(moreImportantPackageName, PatternMatcher.PATTERN_LITERAL);
            ctx.registerReceiver(observer, filter);

            if (!om.setPriority(moreImportantPackageName, lessImportantPackageName, userId)) {
                throw new Exception("failed to change priorities for overlay packages " +
                        lessImportantPackageName + " and " + moreImportantPackageName);
            }
            if (!observer.getResult(MAX_WAIT_TIME)) {
                throw new Exception("failed to change priorities for overlay packages " +
                        lessImportantPackageName + " and " + moreImportantPackageName);
            }

            ctx.unregisterReceiver(observer);
        }

        private static class ReorderOverlaysObserver extends BroadcastReceiver {
            private final BlockingQueue<Boolean> mResults = new LinkedBlockingQueue<Boolean>(1);

            @Override
            public void onReceive(Context ctx, Intent intent) {
                try {
                    mResults.put(true);
                } catch (Exception e) {}
            }

            private boolean getResult(long maxWaitTime) throws InterruptedException {
                Boolean result = mResults.poll(maxWaitTime, MILLISECONDS);
                return result == null ? false : result;
            }
        }

        private static void setLocale(Resources res, Locale locale) {
            Locale.setDefault(locale);
            Configuration config = new Configuration();
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
        }

        public static void setEnglishLocale(Context ctx) {
            setLocale(ctx.getResources(), new Locale("en", "US"));
        }

        public static void setSwedishLocale(Context ctx) {
            setLocale(ctx.getResources(), new Locale("sv", "SE"));
        }

        public static int calculateRawResourceChecksum(Context ctx, int resid) throws Exception {
            InputStream input = null;
            Resources res = ctx.getResources();
            try {
                input = res.openRawResource(resid);
                int ch, checksum = 0;
                while ((ch = input.read()) != -1) {
                    checksum = (checksum + ch) % 0xffddbb00;
                }
                return checksum;
            } finally {
                input.close();
            }
        }

        public static String readAsset(Context ctx, String path) throws Exception {
            Resources res = ctx.getResources();
            AssetManager am = res.getAssets();
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
        public static String readXml(Context ctx, int resid, String tag, String attr) throws Exception {
            Resources res = ctx.getResources();
            XmlPullParser parser = res.getXml(resid);
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

        public static boolean isOverlayApproved(Context ctx, String packageName) throws Exception {
            IOverlayManager om = getOverlayManager(ctx);
            OverlayInfo info = om.getOverlayInfo(packageName, UserHandle.myUserId());
            return info.isApproved();
        }
    }
}
