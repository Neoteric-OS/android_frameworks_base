/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED;
import static android.net.TetheringManager.TETHERING_BLUETOOTH;
import static android.net.TetheringManager.TETHERING_USB;
import static android.net.TetheringManager.TETHERING_WIFI;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.NetworkCapabilities;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.SparseArray;

import androidx.annotation.ArrayRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.android.internal.annotations.VisibleForTesting;
import com.android.networkstack.tethering.R;

import java.util.IllegalFormatException;
import java.util.function.Function;

/**
 * A class to display tethering-related notifications.
 *
 * <p>This class is not thread safe, it is intended to be used only from the tethering handler
 * thread.
 *
 * @hide
 */
public class TetheringNotificationUpdater {
    private static final String CHANNEL_ID = "TETHERING_STATUS";
    private static final boolean NOTIFY_DONE = true;
    private static final boolean NO_NOTIFY = false;
    // Id to update and cancel tethering notification. Must be unique within the tethering app.
    @VisibleForTesting
    static final int ENABLE_NOTIFICATION_ID = 1000;
    // Id to update and cancel restricted notification. Must be unique within the tethering app.
    @VisibleForTesting
    static final int RESTRICTED_NOTIFICATION_ID = 1001;
    @VisibleForTesting
    static final int NO_ICON_ID = 0;
    @VisibleForTesting
    static final int DOWNSTREAM_NONE = 0;
    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private final NotificationChannel mChannel;
    // Downstream type is one of ConnectivityManager.TETHERING_* constants, 0 1 or 2.
    // This value has to be made 1 2 and 4, and OR'd with the others.
    private int mDownstreamTypesMask = DOWNSTREAM_NONE;
    private int mActiveDataSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private int mConnectedClients = 0;
    private boolean mPowerSaving = false;
    private boolean mUpstreamSuspended = false;

    @IntDef({ENABLE_NOTIFICATION_ID, RESTRICTED_NOTIFICATION_ID})
    @interface NotificationId {}

    public TetheringNotificationUpdater(@NonNull final Context context) {
        mContext = context;
        mNotificationManager = (NotificationManager) context.createContextAsUser(UserHandle.ALL, 0)
                .getSystemService(Context.NOTIFICATION_SERVICE);
        mChannel = new NotificationChannel(
                CHANNEL_ID,
                context.getResources().getString(R.string.notification_channel_tethering_status),
                NotificationManager.IMPORTANCE_LOW);
        mNotificationManager.createNotificationChannel(mChannel);
    }

    /** Called when downstream has changed */
    public void onDownstreamChanged(@IntRange(from = 0, to = 7) final int downstreamTypesMask) {
        if (mDownstreamTypesMask == downstreamTypesMask) return;
        mDownstreamTypesMask = downstreamTypesMask;
        updateNotification();
    }

    /** Called when active data subscription id changed */
    public void onActiveDataSubscriptionIdChanged(final int subId) {
        if (mActiveDataSubId == subId) return;
        mActiveDataSubId = subId;
        updateNotification();
    }

    /** Called when power saving changed status */
    public void onPowerSavingChanged(final boolean status) {
        if (mPowerSaving == status) return;
        mPowerSaving = status;
        updateNotification();
    }

    /** Called when the number of connected clients changed */
    public void onConnectedClientsChanged(@IntRange(from = 0) final int number) {
        if (mConnectedClients == number) return;
        mConnectedClients = number;
        updateNotification();
    }

    /** Called when upstream network capabilities changed */
    public void onUpstreamCapabilitiesChanged(@NonNull final NetworkCapabilities capabilities) {
        final boolean isNetworkSuspended =
                !capabilities.hasCapability(NET_CAPABILITY_NOT_SUSPENDED);
        if (mUpstreamSuspended == isNetworkSuspended) return;
        mUpstreamSuspended = isNetworkSuspended;
        updateNotification();
    }

    @VisibleForTesting
    Resources getResourcesForSubId(@NonNull final Context c, final int subId) {
        return SubscriptionManager.getResourcesForSubId(c, subId);
    }

    private void updateNotification() {
        final boolean tetheringInactive = mDownstreamTypesMask <= DOWNSTREAM_NONE;

        if (tetheringInactive) {
            clearNotification(ENABLE_NOTIFICATION_ID);
        } else {
            // Show notification if one of conditions is satisfied.
            if ((mUpstreamSuspended && setupPauseNotification() == NOTIFY_DONE)
                    || (mConnectedClients > 0 && setupClientsNotification() == NOTIFY_DONE)
                    || setupNotification() == NOTIFY_DONE) {
                return;
            }
            // Tethering is active but no need shows notification.
            clearNotification(ENABLE_NOTIFICATION_ID);
        }
    }

    void clearNotification(@NotificationId final int id) {
        mNotificationManager.cancel(null /* tag */, id);
    }

    void setupRestrictedNotification() {
        final Resources res = getResourcesForSubId(mContext, mActiveDataSubId);
        final String title = res.getString(R.string.disable_tether_notification_title);
        final String message = res.getString(R.string.disable_tether_notification_message);

        showNotification(RESTRICTED_NOTIFICATION_ID, R.drawable.stat_sys_tether_general,
                title, message, "");
    }

    @VisibleForTesting
    int getDownstreamTypesMask(@NonNull final String types) {
        int downstreamTypesMask = DOWNSTREAM_NONE;
        final String[] downstreams = types.split("\\|");
        for (String downstream : downstreams) {
            if ("USB".equals(downstream.trim())) {
                downstreamTypesMask |= (1 << TETHERING_USB);
            } else if ("WIFI".equals(downstream.trim())) {
                downstreamTypesMask |= (1 << TETHERING_WIFI);
            } else if ("BT".equals(downstream.trim())) {
                downstreamTypesMask |= (1 << TETHERING_BLUETOOTH);
            }
        }
        return downstreamTypesMask;
    }

    /**
     * Returns the configuration string {@link android.util.SparseArray} which get from given
     * string-array resource id.
     *
     * @param res Resources of active data sub id.
     * @param id String-array resource id
     *
     * @return configuration string {@link android.util.SparseArray} for each downstream types.
     */
    @NonNull
    @VisibleForTesting
    SparseArray<String> getConfigs(@NonNull final Resources res, @ArrayRes int id,
            @NonNull Function<String, Integer> function) {
        final String[] array = res.getStringArray(id);
        final SparseArray<String> configs = new SparseArray<>();
        for (String config : array) {
            if (TextUtils.isEmpty(config)) continue;

            final String[] elements = config.split(";");
            if (elements.length != 2) continue;

            final String[] types = elements[0].split(",");
            for (String type : types) {
                int mask = function.apply(type);
                if (mask == DOWNSTREAM_NONE) continue;
                configs.put(mask, elements[1].trim());
            }
        }
        return configs;
    }

    private boolean setupPauseNotification() {
        final Resources res = getResourcesForSubId(mContext, mActiveDataSubId);
        final String pauseIcons = getConfigs(res, R.array.tethering_notification_pause_icons,
                s -> getDownstreamTypesMask(s))
                .get(mDownstreamTypesMask, "");
        final int iconId = res.getIdentifier(
                pauseIcons, null /* defType */, null /* defPackage */);

        if (iconId == NO_ICON_ID) return NO_NOTIFY;

        final String combinationText = getConfigs(res, R.array.tethering_downstream_combinations,
                s -> getDownstreamTypesMask(s))
                .get(mDownstreamTypesMask, "");
        final String title = res.getString(R.string.tethering_notification_pause_title);
        final String message = res.getString(R.string.tethering_notification_pause_message);

        showNotification(ENABLE_NOTIFICATION_ID, iconId, title, message, combinationText);
        return NOTIFY_DONE;
    }

    private boolean setupClientsNotification() {
        final Resources res = getResourcesForSubId(mContext, mActiveDataSubId);
        final String numberIcon = getConfigs(res, R.array.tethering_notification_icons_with_client,
                s -> {
                    try {
                        return Integer.valueOf(s.trim());
                    } catch (NumberFormatException e) {
                        return DOWNSTREAM_NONE;
                    }
                }).get(mConnectedClients, "");
        final int iconId = res.getIdentifier(
                numberIcon, null /* defType */, null /* defPackage */);

        if (iconId == NO_ICON_ID) return NO_NOTIFY;

        final String combinationText = getConfigs(res, R.array.tethering_downstream_combinations,
                s -> getDownstreamTypesMask(s))
                .get(mDownstreamTypesMask, "");
        final String title = res.getQuantityString(
                R.plurals.tethering_notification_title_with_client, mConnectedClients);
        final String message = res.getQuantityString(mPowerSaving
                    ? R.plurals.tethering_notification_message_with_client_power_saving
                    : R.plurals.tethering_notification_message_with_client,
                    mConnectedClients);

        showNotification(ENABLE_NOTIFICATION_ID, iconId, title, message, combinationText);
        return NOTIFY_DONE;
    }

    private boolean setupNotification() {
        final Resources res = getResourcesForSubId(mContext, mActiveDataSubId);
        final String downstreamIcon = getConfigs(res, R.array.tethering_notification_icons,
                s -> getDownstreamTypesMask(s))
                .get(mDownstreamTypesMask, "");
        final int iconId = res.getIdentifier(
                downstreamIcon, null /* defType */, null /* defPackage */);

        if (iconId == NO_ICON_ID) return NO_NOTIFY;

        final String combinationText = getConfigs(res, R.array.tethering_downstream_combinations,
                s -> getDownstreamTypesMask(s))
                .get(mDownstreamTypesMask, "");
        final String title = res.getString(R.string.tethering_notification_title);
        final String message = res.getString(mPowerSaving
                ? R.string.tethering_notification_message_power_saving
                : R.string.tethering_notification_message);

        showNotification(ENABLE_NOTIFICATION_ID, iconId, title, message, combinationText);
        return NOTIFY_DONE;
    }

    @VisibleForTesting
    String formatText(@NonNull final String text, @NonNull final String downstreamText,
            @StringRes final int defaultTextId) {
        try {
            return String.format(text, downstreamText, mConnectedClients);
        } catch (IllegalFormatException e) { }

        return getResourcesForSubId(mContext, mActiveDataSubId).getString(defaultTextId);
    }

    private void showNotification(@NotificationId final int id, @DrawableRes final int iconId,
            @NonNull final String title, @NonNull final String message,
            @NonNull final String downstreamText) {
        final String formatTitle = formatText(
                title, downstreamText, R.string.tethered_notification_title);
        final String formatMessage = formatText(
                message, downstreamText, R.string.tethered_notification_message);
        final Intent intent = new Intent(Settings.ACTION_TETHER_SETTINGS);
        final PendingIntent pi = PendingIntent.getActivity(
                mContext.createContextAsUser(UserHandle.CURRENT, 0),
                0 /* requestCode */, intent, 0 /* flags */, null /* options */);
        final Notification notification =
                new Notification.Builder(mContext, mChannel.getId())
                        .setSmallIcon(iconId)
                        .setContentTitle(formatTitle)
                        .setContentText(formatMessage)
                        .setOngoing(true)
                        .setColor(mContext.getColor(
                                android.R.color.system_notification_accent_color))
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setCategory(Notification.CATEGORY_STATUS)
                        .setContentIntent(pi)
                        .build();

        mNotificationManager.notify(null /* tag */, id, notification);
    }
}
