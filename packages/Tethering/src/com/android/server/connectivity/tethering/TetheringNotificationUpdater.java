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
import android.content.res.TypedArray;
import android.os.UserHandle;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;

import androidx.annotation.ArrayRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.networkstack.tethering.R;

import java.util.HashMap;

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
    private static final int NOTIFY_ID = 20191115;
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

    @VisibleForTesting
    Resources getResourcesForSubId(@NonNull final Context c, final int subId) {
        return SubscriptionManager.getResourcesForSubId(c, subId);
    }

    private void updateNotification() {
        synchronized (mUpdateLock) {
            final boolean tetheringInactive = mDownstreamTypesMask <= DOWNSTREAM_NONE;

            if (tetheringInactive || setupNotificationLocked() == NO_NOTIFY) {
                clearNotificationLocked();
            }
        }
    }

    private void clearNotificationLocked() {
        mNotificationManager.cancel(null /* tag */, NOTIFY_ID);
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
        final TypedArray array = res.obtainTypedArray(id);
        final HashMap<Integer, Integer> icons = new HashMap<>();
        for (int i = 0; i < array.length(); i++) {
            final String config = array.getString(i);
            if (TextUtils.isEmpty(config)) continue;

            final String[] elements = config.split(";");
            if (elements.length != 2) continue;

            final String[] types = elements[0].split(",");
            for (String type : types) {
                int mask = getDownstreamTypesMask(type);
                if (mask == DOWNSTREAM_NONE) continue;
                icons.put(mask, res.getIdentifier(
                        elements[1].trim(), null /* defType */, null /* defPackage */));
            }
        }
        return icons;
    }

    private boolean setupNotificationLocked() {
        final Resources res = getResourcesForSubId(mContext, mActiveDataSubId);
        final HashMap<Integer, Integer> downstreamIcons =
                getIcons(R.array.tethering_notification_icons);

        final int iconId = downstreamIcons.getOrDefault(mDownstreamTypesMask, NO_ICON_ID);
        if (iconId == NO_ICON_ID) return NO_NOTIFY;

        final String title = res.getString(R.string.tethered_notification_title);
        final String message = res.getString(R.string.tethered_notification_message);

        showNotificationLocked(iconId, title, message);
        return NOTIFY_DONE;
    }

    private void showNotificationLocked(@DrawableRes final int iconId, @NonNull final String title,
            @NonNull final String message) {
        final Intent intent = new Intent("android.settings.TETHER_SETTINGS");
        final PendingIntent pi = PendingIntent.getActivity(
                mContext.createContextAsUser(UserHandle.CURRENT, 0),
                0 /* requestCode */, intent, 0 /* flags */, null /* options */);
        final Notification notification =
                new Notification.Builder(mContext, mChannel.getId())
                        .setSmallIcon(iconId)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setOngoing(true)
                        .setColor(mContext.getColor(
                                android.R.color.system_notification_accent_color))
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setCategory(Notification.CATEGORY_STATUS)
                        .setContentIntent(pi)
                        .build();

        mNotificationManager.notify(null /* tag */, NOTIFY_ID, notification);
    }
}
