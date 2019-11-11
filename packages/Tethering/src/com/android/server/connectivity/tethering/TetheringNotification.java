/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.connectivity.tethering;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.PowerManager;
import android.os.UserHandle;
import android.telephony.SubscriptionManager;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.tethering.R;

import java.util.ArrayList;

/**
 * A class to display tethering-related notifications.
 * @hide
 */
public class TetheringNotification {
    private static final String CHANNEL_ID = "Tether";
    private static final String CHANNEL_NAME = "Tethering Notification";
    private static final int NOTIFY_ID = SystemMessage.NOTE_TETHER_GENERAL;
    private final Context mContext;
    private final NotificationManager mNotificationManager;

    public TetheringNotification(@NonNull final Context context) {
        mContext = context;
        mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        context.registerReceiver(new PowerSaveModeListener(),
                new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGING));
    }

    private int mActiveDataSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private int mConnectedClients = 0;
    private ArrayList<Downstream> mCurrentDownstreams = new ArrayList<>();
    private boolean mPowerSaving = false;
    private boolean mNetworkSuspend = false;

    private class Downstream {
        int mType;
        int mSubId;

        Downstream(final int type, final int subId) {
            mType = type;
            mSubId = subId;
        }
    }

    /** Called when the number of clients changed */
    public void onNumClientsChanged(final int numClients) {
        mConnectedClients = numClients;
        setupNotification();
    }

    /** Called when a downstream has started */
    public void onDownstreamStarted(final int downstreamType, final int subId) {
        mCurrentDownstreams.add(new Downstream(downstreamType, subId));
        setupNotification();
    }

    /** Called when a downstream has stopped */
    public void onDownstreamStopped(final int downstreamType, final int subId) {
        for (Downstream d : mCurrentDownstreams) {
            if (d.mType == downstreamType && d.mSubId == subId) {
                mCurrentDownstreams.remove(d);
                break;
            }
        }
        setupNotification();
    }

    /** Called when power saving changed status */
    public void onPowerSavingChanged(final boolean status) {
        mPowerSaving = status;
        setupNotification();
    }

    /** Called when network suspend changed status */
    public void onNetworkSuspendChanged(final boolean status) {
        mNetworkSuspend = status;
        setupNotification();
    }

    /** Called when subscription id changed */
    public void onSubscriptionIdChanged(final int activeDataSubId) {
        mActiveDataSubId =  activeDataSubId;
        setupNotification();
    }

    private int getActiveDownstreamSize() {
        int size = 0;
        for (Downstream d : mCurrentDownstreams) {
            if (d.mSubId != mActiveDataSubId) continue;
            size++;
        }
        return size;
    }

    private int getDownstreamIndex() {
        int index = 0;
        for (Downstream d : mCurrentDownstreams) {
            // Downstream type is one of ConnectivityManager.TETHERING_* constants, 0 1 or 2.
            // It has to be made 1 2 and 4, and OR'd with the others.
            if (d.mSubId != mActiveDataSubId) continue;
            index |= (1 << d.mType);
        }
        return index;
    }

    private Resources getResources(@NonNull final Context c, final int subId) {
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return getResourcesForSubIdWrapper(c, subId);
        } else {
            return c.getResources();
        }
    }

    @VisibleForTesting
    protected Resources getResourcesForSubIdWrapper(@NonNull final Context c, int subId) {
        return SubscriptionManager.getResourcesForSubId(c, subId);
    }

    private void setupPauseNotification() {
        final Resources res = getResources(mContext, mActiveDataSubId);
        final TypedArray iconArray = res.obtainTypedArray(
                R.array.tethering_notification_pause_icons);
        final int iconIndex = Math.min(getDownstreamIndex(), iconArray.length() - 1);
        final int iconId = iconArray.getResourceId(iconIndex, 0);
        final String title = res.getString(R.string.tethering_notification_pause_title);
        final String text = res.getString(R.string.tethering_notification_pause_text);

        showNotification(iconId, title, text, "");
    }

    private void setupNotification() {
        final boolean tetheringActive = getActiveDownstreamSize() > 0;
        final boolean hotspotActiveWithClients = 0 == mConnectedClients;

        if (!tetheringActive) {
            mNotificationManager.cancelAsUser(null, NOTIFY_ID, UserHandle.ALL);
            return;
        }

        final Resources res = getResources(mContext, mActiveDataSubId);
        final TypedArray iconArray = hotspotActiveWithClients
                ? res.obtainTypedArray(R.array.tethering_notification_icons)
                : res.obtainTypedArray(R.array.tethering_notification_number_icons);
        if (iconArray.length() < 1) return;

        if (mNetworkSuspend) {
            setupPauseNotification();
            return;
        }

        final String[] downstreamTexts =
                res.getStringArray(R.array.tethering_downstream_combinations);
        final int downstreamIndex = getDownstreamIndex();

        // Index can't be negative.
        final String downstreamText = downstreamTexts.length > downstreamIndex
                ? downstreamTexts[downstreamIndex] : "";
        final int iconIndex = Math.min(hotspotActiveWithClients
                ? downstreamIndex : mConnectedClients - 1, iconArray.length() - 1);
        final int iconId = iconArray.getResourceId(iconIndex, 0);

        final String title = hotspotActiveWithClients
                ? res.getString(R.string.tethering_notification_title_noclients)
                : res.getQuantityString(R.plurals.tethering_notification_title, mConnectedClients);

        final String text;
        if (mPowerSaving) {
            text = hotspotActiveWithClients
                    ? res.getString(R.string.tethering_notification_text_power_saving_noclients)
                    : res.getQuantityString(
                            R.plurals.tethering_notification_text_power_saving, mConnectedClients);
        } else {
            text = hotspotActiveWithClients
                    ? res.getString(R.string.tethering_notification_text_noclients)
                    : res.getQuantityString(
                            R.plurals.tethering_notification_text, mConnectedClients);
        }
        showNotification(iconId, title, text, downstreamText);
    }

    private void showNotification(final int iconId, final String title, final String text,
            final String downstreamText) {

        final Intent intent = new Intent();
        intent.setClassName("com.android.settings", "com.android.settings.TetherSettings");
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        PendingIntent pi = PendingIntent.getActivityAsUser(mContext, 0, intent, 0,
                null, UserHandle.CURRENT);

        final NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(channel);

        final Notification notification =
                new Notification.Builder(mContext, channel.getId())
                        .setSmallIcon(iconId)
                        .setContentTitle(String.format(title, mConnectedClients, downstreamText))
                        .setContentText(String.format(text, mConnectedClients, downstreamText))
                        .setColor(mContext.getColor(
                                com.android.internal.R.color.system_notification_accent_color))
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setCategory(Notification.CATEGORY_STATUS)
                        .setContentIntent(pi)
                        .build();

        mNotificationManager.notifyAsUser(null, NOTIFY_ID, notification, UserHandle.ALL);
    }

    private class PowerSaveModeListener extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (!PowerManager.ACTION_POWER_SAVE_MODE_CHANGING.equals(intent.getAction())) return;
            final boolean save = intent.getBooleanExtra(PowerManager.EXTRA_POWER_SAVE_MODE, false);
            onPowerSavingChanged(save);
        }
    }
}
