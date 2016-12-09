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

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.CaptivePortal;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.UserHandle;
import android.telephony.PhoneStateListener;
import android.text.TextUtils;
import android.util.Log;
import android.net.ICaptivePortal;
import com.android.carrierdefaultreceivers.R;

import com.android.internal.telephony.TelephonyIntents;

import java.lang.ref.WeakReference;

import static android.net.CaptivePortal.APP_RETURN_DISMISSED;

public class CarrierDefaultBroadcastReceiver extends BroadcastReceiver{

    private static final String TAG = "CarrierDefaultReceiver";
    private static final String CARRIER_PORTAL_LAUNCH = "com.android.carrierDefaultApp.portal";
    private static final String RESTRCTED_NETWORK_AVAIL =
            "com.android.carrierDefaultApp.restricted_nw_avail";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(CARRIER_PORTAL_LAUNCH) ||
                intent.getAction().equals(RESTRCTED_NETWORK_AVAIL)) {
            onLaunchPortal(intent,context);
        } else if (intent.getAction().equals(TelephonyIntents.ACTION_CARRIER_SIGNAL_REDIRECTED)) {
            CarrierActionUtils.onShowCaptivePortalNotification(intent, context);
            //CarrierActionUtils.onDisableAllMeteredApns(intent, context);
        }
        logd("onReceive finished");
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

    private static void onReleaseRestrictedNetworkRequest(Intent intent, Context context) {
        logd("onReleaseRestrictedNetworkRequest");
        final ConnectivityManager mConnectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        mConnectivityManager.unregisterNetworkCallback(
                getIntentForRestrictedNetwork(intent, context));
    }
    @TargetApi(24)
    private void onLaunchPortal(final Intent intent, final Context context) {
        logd("onLaunchPortal");
        // network could either be a restricted sent from ConnecitityManager or
        // the existing default network, check the connection before launching the portal
        final ConnectivityManager connManager = (ConnectivityManager)context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        Network network = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK);
        if (network == null)  network = getNetworkForCaptivePortal(context);
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
                new CaptivePortal(new Portal(intent, context)));
        portalIntent.putExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL_URL, redirectUrl);
        portalIntent.setFlags(
                Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivityAsUser(portalIntent, new UserHandle(UserHandle.myUserId()));
    }

    private static void onActivationSucceed(Intent intent, Context context) {
        logd("OnActivationSucceed");
        onReleaseRestrictedNetworkRequest(intent, context);
        CarrierActionUtils.onEnableRadio(intent, context);
        CarrierActionUtils.onEnableAllMeteredApns(intent, context);
        CarrierActionUtils.onCancelAllNotifications(context);
    }

    private Network getNetworkForCaptivePortal(Context context) {
        final ConnectivityManager connManager = (ConnectivityManager)context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        Network[] info = connManager.getAllNetworks();
        for (Network nw : info) {
            final NetworkCapabilities nc = connManager.getNetworkCapabilities(nw);
           // Validation is not applicable to restricted NW, skip checking CAPABILITY_CAPTIVE_PORTAL
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                    nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return nw;
            }
        }
        loge(" No valid network for captive portal");
        return null;
    }

    private static PendingIntent getIntentForRestrictedNetwork(Intent intent, Context context) {
        Intent intentForRestrictedNetwork = new Intent(RESTRCTED_NETWORK_AVAIL)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtras(intent);
        return PendingIntent.getBroadcast(context, 0,
                intentForRestrictedNetwork, 0);
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }

    private static void loge(String s) {
        Log.e(TAG, s);
    }

    private static class Portal extends ICaptivePortal.Stub {
        private final Intent mIntent;
        private final Context mContext;

        public Portal(Intent intent, Context context) {
            mIntent = intent;
            mContext = context;
        }
        @Override
        public void appResponse(int response) {
            logd("portal response code: " + response);
            if(response == APP_RETURN_DISMISSED) {
                onActivationSucceed(mIntent, mContext);
            } else {
                onReleaseRestrictedNetworkRequest(mIntent, mContext);
            }
        }
    }
}
