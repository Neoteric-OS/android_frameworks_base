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

import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING;
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
import android.telephony.SubscriptionManager;
import android.text.TextUtils;

import androidx.annotation.ArrayRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.android.internal.annotations.VisibleForTesting;
import com.android.networkstack.tethering.R;

import java.util.HashMap;
import java.util.IllegalFormatException;

/**
 * A class to display tethering-related notifications.
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
    // Id to update and cancel restrict notification. Must be unique within the tethering app.
    @VisibleForTesting
    static final int RESTRICT_NOTIFICATION_ID = 1001;
    // Id to update and cancel roaming notification. Must be unique within the tethering app.
    @VisibleForTesting
    static final int ROAMING_NOTIFICATION_ID = 1002;
    @VisibleForTesting
    static final int NO_ICON_ID = 0;
    @VisibleForTesting
    static final int DOWNSTREAM_NONE = 0;
    // Used to synchronize update notification
    private final Object mUpdateLock = new Object();
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
    private boolean mDataRoaming = false;
    private NetworkCapabilities mUpstreamNetworkCap = null;

    @IntDef({ENABLE_NOTIFICATION_ID, RESTRICT_NOTIFICATION_ID, ROAMING_NOTIFICATION_ID})
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
        synchronized (mUpdateLock) {
            if (mDownstreamTypesMask == downstreamTypesMask) return;
            mDownstreamTypesMask = downstreamTypesMask;
            updateNotification();
        }
    }

    /** Called when active data subscription id changed */
    public void onActiveDataSubscriptionIdChanged(final int subId) {
        synchronized (mUpdateLock) {
            if (mActiveDataSubId == subId) return;
            mActiveDataSubId = subId;
            updateNotification();
        }
    }

    /** Called when power saving changed status */
    public void onPowerSavingChanged(final boolean status) {
        synchronized (mUpdateLock) {
            if (mPowerSaving == status) return;
            mPowerSaving = status;
            updateNotification();
        }
    }

    /** Called when the number of connected clients changed */
    public void onConnectedClientsChanged(@IntRange(from = 0) final int number) {
        synchronized (mUpdateLock) {
            if (mConnectedClients == number) return;
            mConnectedClients = number;
            updateNotification();
        }
    }

    /** Called when upstream network capabilities changed */
    public void onUpstreamCapabilitiesChanged(@NonNull final NetworkCapabilities capabilities) {
        synchronized (mUpdateLock) {
            final boolean isNetworkSuspended =
                    !capabilities.hasCapability(NET_CAPABILITY_NOT_SUSPENDED);
            final boolean isDataRoaming = !capabilities.hasCapability(NET_CAPABILITY_NOT_ROAMING);
            mUpstreamNetworkCap = capabilities;

            if (mUpstreamSuspended == isNetworkSuspended
                    && mDataRoaming == isDataRoaming) {
                return;
            }
            mUpstreamSuspended = isNetworkSuspended;
            mDataRoaming = isDataRoaming;
            updateNotification();
        }
    }

    @VisibleForTesting
    Resources getResourcesForSubId(@NonNull final Context c, final int subId) {
        return SubscriptionManager.getResourcesForSubId(c, subId);
    }

    private boolean isCellularUpstream() {
        if (mUpstreamNetworkCap == null) return false;
        return mUpstreamNetworkCap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
    }

    private void updateNotification() {
        synchronized (mUpdateLock) {
            final boolean tetheringInactive = mDownstreamTypesMask <= DOWNSTREAM_NONE;

            if (tetheringInactive) {
                // Tethering is inactive. Clear all notifications.
                clearNotificationLocked(ENABLE_NOTIFICATION_ID);
                clearNotificationLocked(ROAMING_NOTIFICATION_ID);
            } else {
                // Hide roaming notification if one of conditions is satisfied.
                if (!mDataRoaming
                        || !isCellularUpstream()
                        || !setupUpstreamRoamingNotificationLocked()) {
                    clearNotificationLocked(ROAMING_NOTIFICATION_ID);
                }

                // Show notification if one of conditions is satisfied.
                if ((mUpstreamSuspended && setupPauseNotificationLocked())
                        || (mConnectedClients > 0 && setupClientsNotificationLocked())
                        || setupNotificationLocked()) {
                    return;
                }
                // Tethering is active but no need shows notification.
                clearNotificationLocked(ENABLE_NOTIFICATION_ID);
            }
        }
    }

    void clearNotificationLocked(@NotificationId final int id) {
        synchronized (mUpdateLock) {
            mNotificationManager.cancel(null /* tag */, id);
        }
    }

    void setupRestrictedNotificationLocked() {
        synchronized (mUpdateLock) {
            final Resources res = getResourcesForSubId(mContext, mActiveDataSubId);
            final String title = res.getString(R.string.disable_tether_notification_title);
            final String message = res.getString(R.string.disable_tether_notification_message);

            showNotificationLocked(RESTRICT_NOTIFICATION_ID, R.drawable.stat_sys_tether_general,
                    title, message, "");
        }
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
     * Returns the icons {@link java.util.HashMap} which get from the given array resource id.
     *
     * @param id Array resource id
     *
     * @return {@link java.util.HashMap} with downstream types and icon id info.
     */
    @NonNull
    @VisibleForTesting
    HashMap<Integer, Integer> getIcons(@ArrayRes int id) {
        final Resources res = getResourcesForSubId(mContext, mActiveDataSubId);
        final String[] array = res.getStringArray(id);
        final HashMap<Integer, Integer> icons = new HashMap<>();
        for (String config : array) {
            if (TextUtils.isEmpty(config)) continue;

            final String[] elements = config.split(";");
            if (elements.length != 2) continue;

            final String[] types = elements[0].split(",");
            for (String type : types) {
                int mask;
                if (id == R.array.tethering_notification_icons_with_client) {
                    try {
                        mask = Integer.valueOf(type.trim());
                    } catch (NumberFormatException e) {
                        continue;
                    }
                } else {
                    mask = getDownstreamTypesMask(type);
                    if (mask == DOWNSTREAM_NONE) continue;
                }
                icons.put(mask, res.getIdentifier(
                        elements[1].trim(), null /* defType */, null /* defPackage */));
            }
        }
        return icons;
    }

    /**
     * Returns the texts {@link java.util.HashMap} which get from the given string array resource
     * id.
     *
     * @param id String array resource id
     *
     * @return {@link java.util.HashMap} with downstream types and string for this downstream.
     */
    @NonNull
    @VisibleForTesting
    HashMap<Integer, String> getTexts(@ArrayRes int id) {
        final Resources res = getResourcesForSubId(mContext, mActiveDataSubId);
        final String[] array = res.getStringArray(id);
        final HashMap<Integer, String> texts = new HashMap<>();
        for (String config : array) {
            if (TextUtils.isEmpty(config)) continue;

            final String[] elements = config.split(";");
            if (elements.length != 2) continue;

            final String[] types = elements[0].split(",");
            for (String type : types) {
                int mask = getDownstreamTypesMask(type);
                if (mask == DOWNSTREAM_NONE) continue;
                texts.put(mask, elements[1].trim());
            }
        }
        return texts;
    }

    private boolean setupUpstreamRoamingNotificationLocked() {
        final Resources res = getResourcesForSubId(mContext, mActiveDataSubId);
        final boolean upstreamRoamingNotification =
                res.getBoolean(R.bool.config_upstream_roaming_notification);

        if (!upstreamRoamingNotification) return NO_NOTIFY;

        String title = res.getString(R.string.upstream_roaming_notification_title);
        String message = res.getString(R.string.upstream_roaming_notification_message);

        showNotificationLocked(ROAMING_NOTIFICATION_ID, R.drawable.stat_sys_tether_upstream_roaming,
                title, message, "");
        return NOTIFY_DONE;
    }

    private boolean setupPauseNotificationLocked() {
        final Resources res = getResourcesForSubId(mContext, mActiveDataSubId);
        final HashMap<Integer, Integer> pauseIcons =
                getIcons(R.array.tethering_notification_pause_icons);

        final int iconId = pauseIcons.getOrDefault(mDownstreamTypesMask, NO_ICON_ID);
        if (iconId == NO_ICON_ID) return NO_NOTIFY;

        final String title = res.getString(R.string.tethering_notification_pause_title);
        final String message = res.getString(R.string.tethering_notification_pause_message);
        final HashMap<Integer, String> combinationTexts =
                getTexts(R.array.tethering_downstream_combinations);
        final String downstreamText = combinationTexts.getOrDefault(mDownstreamTypesMask, "");

        showNotificationLocked(ENABLE_NOTIFICATION_ID, iconId, title, message, downstreamText);
        return NOTIFY_DONE;
    }

    private boolean setupClientsNotificationLocked() {
        final Resources res = getResourcesForSubId(mContext, mActiveDataSubId);
        final HashMap<Integer, Integer> numberIcons =
                getIcons(R.array.tethering_notification_icons_with_client);

        final int iconId = numberIcons.getOrDefault(mConnectedClients, NO_ICON_ID);
        if (iconId == NO_ICON_ID) return NO_NOTIFY;

        final String title = res.getQuantityString(
                R.plurals.tethering_notification_title_with_client, mConnectedClients);
        final String message;
        if (mPowerSaving) {
            message = res.getQuantityString(
                    R.plurals.tethering_notification_message_with_client_power_saving,
                    mConnectedClients);
        } else {
            message = res.getQuantityString(
                    R.plurals.tethering_notification_message_with_client, mConnectedClients);
        }
        final HashMap<Integer, String> combinationTexts =
                getTexts(R.array.tethering_downstream_combinations);
        final String downstreamText = combinationTexts.getOrDefault(mDownstreamTypesMask, "");

        showNotificationLocked(ENABLE_NOTIFICATION_ID, iconId, title, message, downstreamText);
        return NOTIFY_DONE;
    }

    private boolean setupNotificationLocked() {
        final Resources res = getResourcesForSubId(mContext, mActiveDataSubId);
        final HashMap<Integer, Integer> downstreamIcons =
                getIcons(R.array.tethering_notification_icons);

        final int iconId = downstreamIcons.getOrDefault(mDownstreamTypesMask, NO_ICON_ID);
        if (iconId == NO_ICON_ID) return NO_NOTIFY;

        final String title = res.getString(R.string.tethering_notification_title);
        final String message;
        if (mPowerSaving) {
            message = res.getString(R.string.tethering_notification_message_power_saving);
        } else {
            message = res.getString(R.string.tethering_notification_message);
        }
        final HashMap<Integer, String> combinationTexts =
                getTexts(R.array.tethering_downstream_combinations);
        final String downstreamText = combinationTexts.getOrDefault(mDownstreamTypesMask, "");

        showNotificationLocked(ENABLE_NOTIFICATION_ID, iconId, title, message, downstreamText);
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

    private void showNotificationLocked(@NotificationId final int id, @DrawableRes final int iconId,
            @NonNull final String title, @NonNull final String message,
            @NonNull final String downstreamText) {
        final String formatTitle = formatText(
                title, downstreamText, R.string.tethered_notification_title);
        final String formatMessage = formatText(
                message, downstreamText, R.string.tethered_notification_message);
        final Intent intent = new Intent("android.settings.TETHER_SETTINGS");
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
