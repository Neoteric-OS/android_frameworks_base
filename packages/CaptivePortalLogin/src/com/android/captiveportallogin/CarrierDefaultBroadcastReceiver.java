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


package com.android.captiveportallogin;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.CaptivePortal;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.UserHandle;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.net.ICaptivePortal;
import com.android.captiveportallogin.R;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

import static android.net.CaptivePortal.APP_RETURN_DISMISSED;

public class CarrierDefaultBroadcastReceiver extends BroadcastReceiver{

    private static final String TAG = "CarrierDefaultReceiver";
    private static final String CARRIER_PORTAL_LAUNCH = "com.android.carrierDefaultApp.portal";
    private static final String RESTRCTED_NETWORK_AVAIL =
            "com.android.carrierDefaultApp.restricted_nw_avail";
    private static final String PORTAL_NOTIFICATION_TAG = "CarrierDefault.Portal.Notification";
    private static final String NO_DATA_NOTIFICATION_TAG= "CarrierDefault.NoData.Notification";
    private static final int PORTAL_NOTIFICATION_ID = 0;
    private static final int NO_DATA_NOTIFICATION_ID = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(CARRIER_PORTAL_LAUNCH)) {
            onLaunchPortal(intent, context);
        } else if (intent.getAction().equals(RESTRCTED_NETWORK_AVAIL)) {
            onLaunchPortal(intent, context);
        } else if (intent.getAction().equals(TelephonyIntents.ACTION_CARRIER_SIGNAL_REDIRECTED)) {
            onShowCaptivePortalNotification(intent, context);
            onDisableAllMeteredApns(intent, context);
        }
    }

    private void onRequestRestrictedNetwork(final Intent intent, final Context context) {
        final ConnectivityManager mConnectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .build();

        mConnectivityManager.requestNetwork(request,
                getIntentForRestrictedNetwork(intent, context));
    }

    private void onReleaseRestrictedNetworkRequest(Intent intent, Context context) {
        final ConnectivityManager mConnectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        mConnectivityManager.unregisterNetworkCallback(
                getIntentForRestrictedNetwork(intent, context));
    }

    private void onLaunchPortal(final Intent intent, final Context context) {
        logd("onLaunchPortal");
        // network could either be a restricted sent from ConnecitityManager or
        // get the existing default network, make sure the connection before launching the portal
        final ConnectivityManager connManager = (ConnectivityManager)context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        Network network = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK);
        if (network == null)  {
            network = getCaptivePortalNetwork(context);
        } else {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                    .build();

            connManager.requestNetwork(request, new ConnectivityManager.NetworkCallback());
        }

        NetworkInfo nwInfo = connManager.getNetworkInfo(network);
        if (nwInfo == null || !nwInfo.isConnected()) {
            loge("Launch portal fails due to network unavailable, request NW connection first.");
            onRequestRestrictedNetwork(intent, context);
            return;
        }
        connManager.bindProcessToNetwork(network);

        String redirectUrl = intent.getStringExtra(TelephonyIntents.EXTRA_REDIRECTION_URL_KEY);
        if (TextUtils.isEmpty(redirectUrl)) {
            loge("Launch portal fails due to incorrect redirectionURL: " + redirectUrl);
            return;
        }

        final Intent portalIntent = new Intent(
                ConnectivityManager.ACTION_CAPTIVE_PORTAL_SIGN_IN);
        portalIntent.putExtra(ConnectivityManager.EXTRA_NETWORK, network);
        portalIntent.putExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL,
                new CaptivePortal(new ICaptivePortal.Stub() {
                    @Override
                    public void appResponse(int response) {
                        logd("portal response code: " + response);
                        if(response == APP_RETURN_DISMISSED) {
                            onActivationSucceed(intent, context);
                        } else {
                            onReleaseRestrictedNetworkRequest(intent, context);
                        }
                    }
                }));
        portalIntent.putExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL_URL, redirectUrl);
        portalIntent.setFlags(
                Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivityAsUser(portalIntent, new UserHandle(UserHandle.myUserId()));
    }

    private void onActivationSucceed(Intent intent, Context context) {
        logd("OnActivationSucceed");
        onReleaseRestrictedNetworkRequest(intent, context);
        onEnableRadio(intent, context);
        onEnableAllMeteredApns(intent, context);
        onCancelAllNotifications(context);
    }

    private void onShowCaptivePortalNotification(Intent intent, Context context) {
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

    private void onShowNoServiceNotification(Context context) {
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

    private void onCancelAllNotifications(Context context) {
        logd("onCancelAllNotifications");
        final NotificationManager notificationMgr = context.getSystemService(
                NotificationManager.class);
        notificationMgr.cancelAll();
    }

    private void onDisableAllMeteredApns(Intent intent, Context context) {
        int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.getDefaultVoiceSubscriptionId());
        logd("onDisableAllMeteredApns subId: " + subId);
        final TelephonyManager telephonyMgr = context.getSystemService(TelephonyManager.class);
        telephonyMgr.carrierActionSetMeteredApnsEnabled(subId, false);
    }

    private void onEnableAllMeteredApns(Intent intent, Context context) {
        int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.getDefaultVoiceSubscriptionId());
        logd("onEnableAllMeteredApns subId: " + subId);
        final TelephonyManager telephonyMgr = context.getSystemService(TelephonyManager.class);
        telephonyMgr.carrierActionSetMeteredApnsEnabled(subId, true);
    }

    private void onDisableRadio(Intent intent, Context context) {
        int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.getDefaultVoiceSubscriptionId());
        logd("onDisableRadio subId: " + subId);
        final TelephonyManager telephonyMgr = context.getSystemService(TelephonyManager.class);
        telephonyMgr.carrierActionSetRadioEnabled(subId, false);
    }

    private void onEnableRadio(Intent intent, Context context) {
        int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.getDefaultVoiceSubscriptionId());
        logd("onEnableRadio subId: " + subId);
        final TelephonyManager telephonyMgr = context.getSystemService(TelephonyManager.class);
        telephonyMgr.carrierActionSetRadioEnabled(subId, true);
    }

    private Network getCaptivePortalNetwork(Context context) {
        final ConnectivityManager connManager = (ConnectivityManager)context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        Network[] info = connManager.getAllNetworks();
        for (Network nw : info) {
            final NetworkCapabilities nc = connManager.getNetworkCapabilities(nw);
           // Validation is not applicable to restricted NW.
           // skip checking CAPABILITY_CAPTIVE_PORTAL.
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                    nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return nw;
            }
        }
        loge(" No valid network for captive portal");
        return null;
    }

    private PendingIntent getIntentForRestrictedNetwork(Intent intent, Context context) {
        Intent intentForRestrictedNetwork = new Intent(RESTRCTED_NETWORK_AVAIL)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtras(intent);
        return PendingIntent.getBroadcast(context, 0,
                intentForRestrictedNetwork, 0);
    }

    private Notification getNotification(Context context, int titleId, int textId,
                                         PendingIntent pendingIntent) {
        Resources resources = context.getResources();
        Notification.Builder builder = new Notification.Builder(context)
                .setContentTitle(resources.getString(titleId))
                .setContentText(resources.getString(textId))
                .setSmallIcon(R.drawable.ic_sim_card)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true)
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
