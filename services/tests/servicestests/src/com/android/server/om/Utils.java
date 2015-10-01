package com.android.server.om;

import static android.content.pm.PackageManager.INSTALL_SUCCEEDED;

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
import android.net.Uri;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.IBinder;
import android.os.PatternMatcher;
import android.os.ServiceManager;
import android.os.UserHandle;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Utils {
    private static final long MAX_WAIT_TIME = 30 * 1000;

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
        Uri uri = extractRawResource(ctx, resid);

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

    private static Uri extractRawResource(Context ctx, int resid) throws Exception {
        Resources res = ctx.getResources();
        InputStream in = res.openRawResource(resid);
        File out = new File(ctx.getCacheDir(), res.getResourceEntryName(resid));
        if (!FileUtils.copyToFile(in, out)) {
            String msg =
                String.format("failed to extract resource 0x%08x to %s", resid, out.getPath());
            throw new Exception(msg);
        }
        FileUtils.setPermissions(out.getAbsolutePath(), 0777, -1, -1);
        return Uri.fromFile(out);
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
            String moreImportantPackageName) throws Exception {
        IOverlayManager om = getOverlayManager(ctx);
        OverlayInfo info = om.getOverlayInfo(moreImportantPackageName, UserHandle.myUserId());
        List<OverlayInfo> overlays = om.getOverlayInfosForTarget(info.targetPackageName, info.userId);

        OverlayInfo prev = null;
        for (OverlayInfo oi : overlays) {
            if (oi.packageName.equals(moreImportantPackageName) && prev != null
                    && prev.packageName.equals(lessImportantPackageName)) {
                return;
            }
            prev = oi;
        }
        ReorderOverlaysObserver observer = new ReorderOverlaysObserver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_OVERLAYS_REORDERED);
        filter.addDataScheme("package");
        filter.addDataSchemeSpecificPart(info.targetPackageName, PatternMatcher.PATTERN_LITERAL);
        ctx.registerReceiver(observer, filter);

        if (!om.setPriority(info, lessImportantPackageName)) {
            throw new Exception("failed to change priorities for overlays " +
                    lessImportantPackageName + " and " + moreImportantPackageName);
        }
        if (!observer.getResult(MAX_WAIT_TIME)) {
            throw new Exception("failed to change priorities for overlays " +
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
