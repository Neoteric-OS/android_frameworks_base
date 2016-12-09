/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.carrierdefaultreceivers;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.carrierdefaultreceivers.R;

import com.android.internal.telephony.PhoneConstants;

public class CarrierActionUtils {
    private static final String TAG = "CarrierActionUtils";

    private static final String CARRIER_PORTAL_LAUNCH = "com.android.carrierDefaultApp.portal";
    private static final String RESTRCTED_NETWORK_AVAIL =
            "com.android.carrierDefaultApp.restricted_nw_avail";
    private static final String PORTAL_NOTIFICATION_TAG = "CarrierDefault.Portal.Notification";
    private static final String NO_DATA_NOTIFICATION_TAG= "CarrierDefault.NoData.Notification";
    private static final int PORTAL_NOTIFICATION_ID = 0;
    private static final int NO_DATA_NOTIFICATION_ID = 1;


    public static void onDisableAllMeteredApns(Intent intent, Context context) {
        int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.getDefaultVoiceSubscriptionId());
        logd("onDisableAllMeteredApns subId: " + subId);
        final TelephonyManager telephonyMgr = context.getSystemService(TelephonyManager.class);
        telephonyMgr.carrierActionSetMeteredApnsEnabled(subId, false);
    }

    public static void onEnableAllMeteredApns(Intent intent, Context context) {
        int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.getDefaultVoiceSubscriptionId());
        logd("onEnableAllMeteredApns subId: " + subId);
        final TelephonyManager telephonyMgr = context.getSystemService(TelephonyManager.class);
        telephonyMgr.carrierActionSetMeteredApnsEnabled(subId, true);
    }

    public static void onDisableRadio(Intent intent, Context context) {
        int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.getDefaultVoiceSubscriptionId());
        logd("onDisableRadio subId: " + subId);
        final TelephonyManager telephonyMgr = context.getSystemService(TelephonyManager.class);
        telephonyMgr.carrierActionSetRadioEnabled(subId, false);
    }

    public static void onEnableRadio(Intent intent, Context context) {
        int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.getDefaultVoiceSubscriptionId());
        logd("onEnableRadio subId: " + subId);
        final TelephonyManager telephonyMgr = context.getSystemService(TelephonyManager.class);
        telephonyMgr.carrierActionSetRadioEnabled(subId, true);
    }

    public static void onShowCaptivePortalNotification(Intent intent, Context context) {
        logd("onShowCaptivePortalNotification");
        final NotificationManager notificationMgr = context.getSystemService(
                NotificationManager.class);
        Intent portalIntent = new Intent(CARRIER_PORTAL_LAUNCH);
        portalIntent.putExtras(intent);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, portalIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = getNotification(context, R.string.portal_notification_id,
                R.string.portal_notification_detail, pendingIntent);
        try {
            notificationMgr.notify(PORTAL_NOTIFICATION_TAG, PORTAL_NOTIFICATION_ID, notification);
        } catch (NullPointerException npe) {
            loge("setNotificationVisible: " + npe);
        }
    }

    public static void onShowNoServiceNotification(Context context) {
        logd("Post no service notification");
        final NotificationManager notificationMgr = context.getSystemService(
                NotificationManager.class);
        Notification notification = getNotification(context, R.string.no_data_notification_id,
                R.string.no_data_notification_detail, null);
        try {
            notificationMgr.notify(NO_DATA_NOTIFICATION_TAG, NO_DATA_NOTIFICATION_ID, notification);
        } catch (NullPointerException npe) {
            loge("setNotificationVisible: " + npe);
        }
    }

    public static void onCancelAllNotifications(Context context) {
        logd("onCancelAllNotifications");
        final NotificationManager notificationMgr = context.getSystemService(
                NotificationManager.class);
        notificationMgr.cancelAll();
    }

    private static Notification getNotification(Context context, int titleId, int textId,
                                         PendingIntent pendingIntent) {
        Resources resources = context.getResources();
        Notification.Builder builder = new Notification.Builder(context)
                .setContentTitle(resources.getString(titleId))
                .setContentText(resources.getString(textId))
                .setSmallIcon(R.drawable.ic_sim_card)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL)
                /*.setAutoCancel(true)*/
                .setLocalOnly(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(false);

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent);
        }
        return builder.build();
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }

    private static void loge(String s) {
        Log.e(TAG, s);
    }
}
