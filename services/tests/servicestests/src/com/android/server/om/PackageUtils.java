package com.android.server.om;

import static android.app.PendingIntent.FLAG_ONE_SHOT;
import static android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.annotation.NonNull;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.os.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

class PackageUtils {
    public static void install(@NonNull final Context ctx, @NonNull final Uri uri)
            throws Exception {
        final PackageInstaller installer = ctx.getPackageManager().getPackageInstaller();
        final int sessionId =
                installer.createSession(new PackageInstaller.SessionParams(MODE_FULL_INSTALL));
        final PackageInstaller.Session session = installer.openSession(sessionId);

        try (
            final InputStream is = ctx.getContentResolver().openInputStream(uri);
            final OutputStream os = session.openWrite(PackageUtils.class.getSimpleName(), 0, -1);
        ) {
            int c;
            byte[] buffer = new byte[1024];
            while ((c = is.read(buffer)) != -1) {
                os.write(buffer, 0, c);
            }
        }

        try (final PackageInstallerCallback cb = new PackageInstallerCallback(ctx)) {
            session.commit(cb.getIntentSender());
        }
    }

    public static void uninstall(@NonNull final Context ctx, @NonNull String packageName)
            throws Exception {
        if (!isInstalled(ctx, packageName)) {
            return;
        }

        try (final PackageInstallerCallback cb = new PackageInstallerCallback(ctx)) {
            final PackageInstaller installer = ctx.getPackageManager().getPackageInstaller();
            installer.uninstall(packageName, cb.getIntentSender());
        }
    }

    public static boolean isInstalled(@NonNull final Context ctx,
            @NonNull final String packageName) throws Exception {
        final PackageManager pm = ctx.getPackageManager();
        try {
            final PackageInfo info = pm.getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return true;
    }

    private PackageUtils() {}

    private static class PackageInstallerCallback extends BroadcastReceiver
            implements AutoCloseable {

        private static final String ACTION_PACKAGE_INSTALLER_CALLBACK = "com.android.server.om."
                + "PackageUtils.PackageInstallerCallback.ACTION_PACKAGE_INSTALLER_CALLBACK";
        private static final int REQUEST_CODE_PACKAGE_INSTALLER_CALLBACK = 1;
        private static final int MAX_WAIT_TIME = 30 * 1000;

        private final Context mContext;
        private final PendingIntent mPendingIntent;
        private final BlockingQueue<Integer> mResults = new LinkedBlockingQueue<Integer>(1);

        PackageInstallerCallback(@NonNull final Context ctx) {
            mContext = ctx;
            mPendingIntent = PendingIntent.getBroadcast(mContext,
                    REQUEST_CODE_PACKAGE_INSTALLER_CALLBACK,
                    new Intent(ACTION_PACKAGE_INSTALLER_CALLBACK), FLAG_ONE_SHOT);
            registerCallback();
        }

        @Override
        public void onReceive(@NonNull final Context ctx, @NonNull final Intent intent) {
            if (!ACTION_PACKAGE_INSTALLER_CALLBACK.equals(intent.getAction())) {
                return;
            }
            final int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE);
            try {
                mResults.put(status);
            } catch (Exception e) { }
        }

        @Override
        public void close() throws Exception {
            waitForCallback();
            unregisterCallback();
        }

        public IntentSender getIntentSender() {
            return mPendingIntent.getIntentSender();
        }

        private void registerCallback() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_PACKAGE_INSTALLER_CALLBACK);
            mContext.registerReceiver(this, filter);
        }

        private void waitForCallback() throws Exception {
            final Integer result = mResults.poll(MAX_WAIT_TIME, MILLISECONDS);
            if (result == null) {
                throw new Exception("operation timed out");
            }
            if (result.intValue() != PackageInstaller.STATUS_SUCCESS) {
                throw new Exception("operation failed (" + result.intValue() + ")");
            }
        }

        private void unregisterCallback() {
            mContext.unregisterReceiver(this);
        }
    }
}
