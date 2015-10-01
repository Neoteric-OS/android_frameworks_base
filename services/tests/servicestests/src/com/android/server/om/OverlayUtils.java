package com.android.server.om;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.IBinder;
import android.os.PatternMatcher;
import android.os.ServiceManager;
import android.os.SystemClock;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

class OverlayUtils {
    private static final int DELAY_MS = 100;

    public static void enable(@NonNull final Context ctx, @NonNull final String packageName,
            final int userId) throws Exception {
        if (isEnabled(ctx, packageName, userId)) {
            return;
        }

        final IOverlayManager om = getOverlayManager(ctx);
        if (!om.setEnabled(packageName, true, userId)) {
            throw new Exception("failed to enable overlay " + packageName);
        }
        SystemClock.sleep(DELAY_MS);
    }

    public static void disable(@NonNull final Context ctx, @NonNull final String packageName,
            final int userId) throws Exception {
        if (!isEnabled(ctx, packageName, userId)) {
            return;
        }

        final IOverlayManager om = getOverlayManager(ctx);
        if (!om.setEnabled(packageName, false, userId)) {
            throw new Exception("failed to disable overlay " + packageName);
        }
        SystemClock.sleep(DELAY_MS);
    }

    public static void reorder(@NonNull final Context ctx,
            @NonNull final String lessImportantPackageName,
            @NonNull final String moreImportantPackageName,
            final int userId) throws Exception {
        final IOverlayManager om = getOverlayManager(ctx);
        if (!om.setPriority(moreImportantPackageName, lessImportantPackageName, userId)) {
            throw new Exception("failed to reorder overlays " + moreImportantPackageName
                    + " and " + lessImportantPackageName);
        }
        SystemClock.sleep(DELAY_MS);
    }

    public static boolean isEnabled(@NonNull final Context ctx, @NonNull final String packageName,
            final int userId) throws Exception {
        final IOverlayManager om = getOverlayManager(ctx);
        final OverlayInfo info = om.getOverlayInfo(packageName, userId);
        return info.isEnabled();
    }

    private static IOverlayManager getOverlayManager(@NonNull final Context ctx) {
        final IBinder b = ServiceManager.getService(Context.OVERLAY_SERVICE);
        return IOverlayManager.Stub.asInterface(b);
    }

    private static IntentFilter createIntentFilter(@NonNull final String action,
            @NonNull final String packageName) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(action);
        filter.addDataScheme("package");
        filter.addDataSchemeSpecificPart(".*/" + packageName, PatternMatcher.PATTERN_SIMPLE_GLOB);
        return filter;
    }

    private OverlayUtils() {}

    private static class IntentListener extends BroadcastReceiver implements AutoCloseable {
        private static final int MAX_WAIT_TIME = 30 * 1000;

        private final BlockingQueue<Integer> mResults = new LinkedBlockingQueue<Integer>(1);
        private final Context mContext;

        IntentListener(@NonNull final Context ctx, @NonNull final IntentFilter filter) {
            mContext = ctx;
            registerReceiver(filter);
        }

        @Override
        public void close() throws Exception {
            waitForIntent();
            unregisterReceiver();
        }

        @Override
        public void onReceive(@NonNull final Context ctx, @NonNull final Intent intent) {
            try {
                mResults.put(1);
            } catch (Exception e) { }
        }

        private void waitForIntent() throws Exception {
            final Integer result = mResults.poll(MAX_WAIT_TIME, MILLISECONDS);
            if (result == null) {
                throw new Exception("operation timed out");
            }
        }

        private void registerReceiver(@NonNull final IntentFilter filter) {
            mContext.registerReceiver(this, filter);
        }

        private void unregisterReceiver() {
            mContext.unregisterReceiver(this);
        }
    }
}
