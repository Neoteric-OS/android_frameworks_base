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
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.telephony.SubscriptionManager;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.connectivity.tethering.Tethering.TetheringType;
import com.android.tethering.R;

import java.util.ArrayList;
import java.util.IllegalFormatException;

/**
 * A class to display tethering-related notifications.
 *
 * @hide
 */
public class TetheringNotificationUpdater {
    private static final String TAG = TetheringNotificationUpdater.class.getSimpleName();
    private static final String CHANNEL_ID = "Tether";
    private static final String CHANNEL_NAME = "Tethering Notification";
    private static final int NOTIFY_ID = 20191115;
    static final int NO_ICON_ID = 0;
    // Used to synchronize update notification
    private final Object mUpdateSync;
    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private final NotificationChannel mChannel;

    private int mActiveDataSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private int mConnectedClients = 0;
    private boolean mPowerSaving = false;
    private boolean mNetworkSuspended = false;

    @NonNull
    private final ArrayList<Integer> mCurrentDownstreams = new ArrayList<>();

    public TetheringNotificationUpdater(@NonNull final Context context) {
        mUpdateSync = new Object();
        mContext = context;
        mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mChannel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
        mNotificationManager.createNotificationChannel(mChannel);
    }

    /** Called when the number of connected clients changed */
    public void onConnectedClientsChanged(@IntRange(from = 0) final int number) {
        mConnectedClients = number;
        updateNotification();
    }

    /** Called when a downstream has started */
    public void onDownstreamStarted(@TetheringType final int downstreamType) {
        mCurrentDownstreams.add(downstreamType);
        updateNotification();
    }

    /** Called when a downstream has stopped */
    public void onDownstreamStopped(@TetheringType final int downstreamType) {
        mCurrentDownstreams.remove(Integer.valueOf(downstreamType));
        updateNotification();
    }

    /** Called when power saving changed status */
    public void onPowerSavingChanged(final boolean status) {
        if (mPowerSaving != status) {
            mPowerSaving = status;
            updateNotification();
        }
    }

    /** Called when network suspend changed status */
    public void onNetworkSuspendChanged(final boolean status) {
        if (mNetworkSuspended != status) {
            mNetworkSuspended = status;
            updateNotification();
        }
    }

    /** Called when active subscription id changed */
    public void onActiveSubscriptionIdChanged(final int activeDataSubId) {
        mActiveDataSubId =  activeDataSubId;
        updateNotification();
    }

    private int getDownstreamTypesMask() {
        int index = 0;
        for (int downstream : mCurrentDownstreams) {
            // Downstream type is one of ConnectivityManager.TETHERING_* constants, 0 1 or 2.
            // It has to be made 1 2 and 4, and OR'd with the others.
            index |= (1 << downstream);
        }
        return index;
    }

    private Resources getResources(@NonNull final Context c, final int subId) {
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return getResourcesForSubId(c, subId);
        } else {
            return c.getResources();
        }
    }

    @VisibleForTesting
    protected Resources getResourcesForSubId(@NonNull final Context c, final int subId) {
        return SubscriptionManager.getResourcesForSubId(c, subId);
    }

    private void updateNotification() {
        final Resources res = getResources(mContext, mActiveDataSubId);
        final TypedArray iconArray = res.obtainTypedArray(R.array.tethering_notification_icons);
        final boolean showNotification = iconArray.length() > 0;
        final boolean tetheringInactive = mCurrentDownstreams.size() < 1;

        synchronized (mUpdateSync) {
            if (tetheringInactive) {
                mNetworkSuspended = false;
                clearNotification();
            } else if (showNotification) {
                final int downstreamIndex = getDownstreamTypesMask();
                final int iconIndex = Math.min(downstreamIndex, iconArray.length() - 1);
                final int iconId = iconArray.getResourceId(iconIndex, NO_ICON_ID);

                if (iconId == NO_ICON_ID) {
                    clearNotification();
                } else if (mNetworkSuspended) {
                    setupPauseNotification();
                } else {
                    setupNotification();
                }
            }
        }
    }

    private void clearNotification() {
        mNotificationManager.cancel(null, NOTIFY_ID);
    }

    private void setupPauseNotification() {
        final Resources res = getResources(mContext, mActiveDataSubId);
        final TypedArray iconArray = res.obtainTypedArray(
                R.array.tethering_notification_pause_icons);
        if (iconArray.length() < 1) return;

        final int downstreamIndex = getDownstreamTypesMask();
        final int iconIndex = downstreamIndex < iconArray.length() ? downstreamIndex : 0;
        final int iconId = iconArray.getResourceId(iconIndex, 0);
        final String title = res.getString(R.string.tethering_notification_pause_title);
        final String message = res.getString(R.string.tethering_notification_pause_text);

        showNotification(iconId, title, message, "");
    }

    private void setupNotification() {
        final Resources res = getResources(mContext, mActiveDataSubId);
        final int downstreamIndex = getDownstreamTypesMask();
        final TypedArray numberIconArray =
                res.obtainTypedArray(R.array.tethering_notification_number_icons);
        final boolean noHotspotClients = mConnectedClients <= 0 || numberIconArray.length() < 2;
        final TypedArray iconArray = noHotspotClients
                ? res.obtainTypedArray(R.array.tethering_notification_icons)
                : numberIconArray;

        final String[] downstreamTexts =
                res.getStringArray(R.array.tethering_downstream_combinations);
        // Index can't be negative.
        final String downstreamText = downstreamTexts.length > downstreamIndex
                ? downstreamTexts[downstreamIndex] : "";
        final int iconIndex = Math.min(noHotspotClients
                ? downstreamIndex
                // First number icon should be none. So the first available number icon index is 1.
                : mConnectedClients,
                iconArray.length() - 1);
        final int iconId = iconArray.getResourceId(iconIndex, 0);

        final String title = noHotspotClients
                ? res.getString(R.string.tethering_notification_title_noclients)
                : res.getQuantityString(R.plurals.tethering_notification_title, mConnectedClients);

        final String message;
        if (mPowerSaving) {
            message = noHotspotClients
                    ? res.getString(R.string.tethering_notification_text_power_saving_noclients)
                    : res.getQuantityString(
                            R.plurals.tethering_notification_text_power_saving, mConnectedClients);
        } else {
            message = noHotspotClients
                    ? res.getString(R.string.tethering_notification_text_noclients)
                    : res.getQuantityString(
                            R.plurals.tethering_notification_text, mConnectedClients);
        }
        showNotification(iconId, title, message, downstreamText);
    }

    private String makeFormatText(@NonNull final String text,
            @NonNull final String downstreamText, final int defaultTextId) {
        String formatText;
        try {
            formatText = String.format(text, mConnectedClients, downstreamText);
        } catch (IllegalFormatException e) {
            final Resources res = getResources(mContext, mActiveDataSubId);
            final String defaultText = res.getString(defaultTextId);
            formatText = String.format(defaultText, mConnectedClients, downstreamText);
        }
        return formatText;
    }

    private void showNotification(final int iconId, @NonNull final String title,
            @NonNull final String message, @NonNull final String downstreamText) {
        final String formatTitle = makeFormatText(
                title, downstreamText, R.string.tethered_notification_title);
        final String formatMessage = makeFormatText(
                message, downstreamText, R.string.tethered_notification_message);
        final Intent intent = new Intent();
        intent.setClassName("com.android.settings", "com.android.settings.TetherSettings");
        final PendingIntent pi = PendingIntent.getActivity(mContext, 0, intent, 0, null);
        final Notification notification =
                new Notification.Builder(mContext, mChannel.getId())
                        .setSmallIcon(iconId)
                        .setContentTitle(formatTitle)
                        .setContentText(formatMessage)
                        .setWhen(0)
                        .setOngoing(true)
                        .setColor(mContext.getColor(
                                com.android.internal.R.color.system_notification_accent_color))
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setCategory(Notification.CATEGORY_STATUS)
                        .setContentIntent(pi)
                        .build();

        mNotificationManager.notify(null, NOTIFY_ID, notification);
    }
}
