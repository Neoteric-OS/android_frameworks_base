/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.devicepolicy;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.security.KeyChain;
import android.security.KeyChain.KeyChainConnection;
import android.util.Log;

import com.android.internal.R;
import com.android.internal.util.ParcelableString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MonitoringCertNotificationTask extends AsyncTask<Integer, Void, Void> {
    protected static final String LOG_TAG = DevicePolicyManagerService.LOG_TAG;
    protected static final int MONITORING_CERT_NOTIFICATION_ID = R.string.ssl_ca_cert_warning;

    private final DevicePolicyManagerService mService;

    public MonitoringCertNotificationTask(final DevicePolicyManagerService service) {
        super();
        mService = service;
    }

    @Override
    protected Void doInBackground(Integer... params) {
        int userHandle = params[0];

        if (userHandle == UserHandle.USER_ALL) {
            for (UserInfo userInfo : getUserManager().getUsers(true)) {
                repostOrClearNotification(userInfo.getUserHandle());
            }
        } else {
            repostOrClearNotification(new UserHandle(userHandle));
        }
        return null;
    }

    private void repostOrClearNotification(UserHandle userHandle) {
        if (!getUserManager().isUserRunning(userHandle)) {
            return;
        }

        // Call out to KeyChain to check for CAs which are waiting for approval.
        final int pendingCertificateCount;
        try {
            pendingCertificateCount = getInstalledCaCertificates(userHandle).size();
        } catch (RemoteException | RuntimeException e) {
            Log.e(LOG_TAG, "Could not retrieve certificates from KeyChain service", e);
            return;
        }

        if (pendingCertificateCount != 0) {
            showNotification(userHandle, pendingCertificateCount);
        } else {
            getNotificationManager().cancelAsUser(
                    LOG_TAG, MONITORING_CERT_NOTIFICATION_ID, userHandle);
        }
    }

    private void showNotification(UserHandle userHandle, int pendingCertificateCount) {
        // Create a context for the target user.
        final Context userContext;
        try {
            userContext = mService.mContext.createPackageContextAsUser(
                    mService.mContext.getPackageName(), 0, userHandle);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Create context as " + userHandle + " failed", e);
            return;
        }

        // Build and show a warning notification
        final int smallIconId;
        final String contentText;
        final String ownerName = mService.getDeviceOwnerName();
        if (ownerName != null) {
            contentText = mService.mContext.getString(R.string.ssl_ca_cert_noti_managed, ownerName);
            smallIconId = R.drawable.stat_sys_certificate_info;
        } else {
            contentText = mService.mContext.getString(R.string.ssl_ca_cert_noti_by_unknown);
            smallIconId = android.R.drawable.stat_sys_warning;
        }

        Intent dialogIntent = new Intent(Settings.ACTION_MONITORING_CERT_INFO);
        dialogIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        // TODO this next line is taken from original notification code in
        // {@link DevicePolicyManagerService} but not a very good way of doing it. Do it better.
        dialogIntent.setPackage("com.android.settings");
        PendingIntent notifyIntent = PendingIntent.getActivityAsUser(mService.mContext, 0,
                dialogIntent, PendingIntent.FLAG_UPDATE_CURRENT, null, UserHandle.CURRENT);

        final Resources resources = mService.mContext.getResources();
        final Notification noti = new Notification.Builder(userContext)
            .setSmallIcon(smallIconId)
            .setContentTitle(resources.getText(R.string.ssl_ca_cert_warning))
            .setContentText(contentText)
            .setContentIntent(notifyIntent)
            .setPriority(Notification.PRIORITY_HIGH)
            .setShowWhen(false)
            .setColor(R.color.system_notification_accent_color)
            .build();

        getNotificationManager().notifyAsUser(
                LOG_TAG, MONITORING_CERT_NOTIFICATION_ID, noti, userHandle);
    }

    private List<String> getInstalledCaCertificates(UserHandle userHandle)
            throws RemoteException, RuntimeException {
        KeyChainConnection conn = null;
        try {
            conn = KeyChain.bindAsUser(mService.mContext, userHandle);
            List<ParcelableString> aliases = conn.getService().getUserCaAliases().getList();
            List<String> result = new ArrayList<>(aliases.size());
            for (int i = 0; i < aliases.size(); i++) {
                result.add(aliases.get(i).string);
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Collections.<String> emptyList();
        } catch (AssertionError e) {
            throw new RuntimeException(e);
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager)
                mService.mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private UserManager getUserManager() {
        return (UserManager)
                mService.mContext.getSystemService(Context.USER_SERVICE);
    }
}
