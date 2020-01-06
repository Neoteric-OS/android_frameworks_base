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

import androidx.annotation.DrawableRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.android.internal.annotations.VisibleForTesting;
import com.android.networkstack.tethering.R;

/**
 * A class to display tethering-related notifications.
 *
 * @hide
 */
public class TetheringNotificationUpdater {
    private static final String TAG = TetheringNotificationUpdater.class.getSimpleName();
    private static final String CHANNEL_ID = "TetheringNotificationUpdater";
    private static final boolean NOTIFY_DONE = true;
    private static final boolean NOTIFY_DROP = false;
    private static final int NOTIFY_ID = 20191115;
    static final int NO_ICON_ID = 0;
    static final int DOWNSTREAM_NONE = 0;
    // Used to synchronize update notification
    private final Object mUpdateLock;
    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private final NotificationChannel mChannel;

    private int mActiveDataSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    // Downstream type is one of ConnectivityManager.TETHERING_* constants, 0 1 or 2.
    // This value has to be made 1 2 and 4, and OR'd with the others.
    private int mDownstreamTypesMask = DOWNSTREAM_NONE;
    private boolean mPowerSaving = false;
    private boolean mTetheringRestricted = false;
    private boolean mUpstreamSuspended = false;

    public TetheringNotificationUpdater(@NonNull final Context context) {
        mUpdateLock = new Object();
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
    public void onDownstreamChanged(@IntRange(from = 0) final int downstreamTypesMask) {
        mDownstreamTypesMask = downstreamTypesMask;
        updateNotification();
    }

    /** Called when power saving changed status */
    public void onPowerSavingChanged(final boolean status) {
        if (mPowerSaving != status) {
            mPowerSaving = status;
            updateNotification();
        }
    }

    /** Called when active data subscription id changed */
    public void onActiveDataSubscriptionIdChanged(final int subId) {
        mActiveDataSubId = subId;
        updateNotification();
    }

    /** Called when tethering restrictions changed status */
    public void onTetheringRestrictionsChanged(final boolean restricted) {
        if (mTetheringRestricted != restricted) {
            mTetheringRestricted = restricted;
            updateNotification();
        }
    }

    /** Called when network suspend changed status */
    public void onUpstreamNetworkSuspended(final boolean status) {
        if (mUpstreamSuspended != status) {
            mUpstreamSuspended = status;
            updateNotification();
        }
    }

    private Resources getResources(@NonNull final Context c, final int subId) {
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return getResourcesForSubId(c, subId);
        } else {
            return c.getResources();
        }
    }

    @VisibleForTesting
    Resources getResourcesForSubId(@NonNull final Context c, final int subId) {
        return SubscriptionManager.getResourcesForSubId(c, subId);
    }

    private void updateNotification() {
        final boolean tetheringInactive = mDownstreamTypesMask <= DOWNSTREAM_NONE;

        synchronized (mUpdateLock) {
            if (mTetheringRestricted) {
                setupRestrictedNotification();
            } else if (tetheringInactive) {
                mUpstreamSuspended = false;
                clearNotification();
            } else {
                // Show notification if one of conditions is satisfied.
                if ((mUpstreamSuspended && setupPauseNotification())
                        || setupNotification()) {
                    return;
                }
                // Tethering is active but no need shows notification.
                clearNotification();
            }
        }
    }

    private void clearNotification() {
        mNotificationManager.cancel(null /* tag */, NOTIFY_ID);
    }

    private void setupRestrictedNotification() {
        final Resources res = getResources(mContext, mActiveDataSubId);
        final String title = res.getString(R.string.disable_tether_notification_title);
        final String message = res.getString(R.string.disable_tether_notification_message);

        showNotification(R.drawable.stat_sys_tether_general, title, message, "");
    }

    private boolean setupPauseNotification() {
        final Resources res = getResources(mContext, mActiveDataSubId);
        final TypedArray iconArray = res.obtainTypedArray(
                R.array.tethering_notification_pause_icons);

        if (iconArray.length() < 1) return NOTIFY_DROP;

        final int iconIndex = mDownstreamTypesMask < iconArray.length() ? mDownstreamTypesMask : 0;
        final int iconId = iconArray.getResourceId(iconIndex, NO_ICON_ID);
        final String title = res.getString(R.string.tethering_notification_pause_title);
        final String message = res.getString(R.string.tethering_notification_pause_text);

        return showNotification(iconId, title, message, "");
    }

    private boolean setupNotification() {
        final Resources res = getResources(mContext, mActiveDataSubId);
        final TypedArray iconArray = res.obtainTypedArray(R.array.tethering_notification_icons);

        if (iconArray.length() < 1) return NOTIFY_DROP;

        final String[] downstreamTexts =
                res.getStringArray(R.array.tethering_downstream_combinations);
        // Index can't be negative.
        final String downstreamText = downstreamTexts.length > mDownstreamTypesMask
                ? downstreamTexts[mDownstreamTypesMask] : "";
        final int iconIndex = Math.min(mDownstreamTypesMask, iconArray.length() - 1);
        final int iconId = iconArray.getResourceId(iconIndex, NO_ICON_ID);
        final String title = res.getString(R.string.tethering_notification_title_noclients);
        final String message;
        if (mPowerSaving) {
            message = res.getString(R.string.tethering_notification_text_power_saving_noclients);
        } else {
            message = res.getString(R.string.tethering_notification_text_noclients);
        }
        return showNotification(iconId, title, message, downstreamText);
    }

    @VisibleForTesting
    String formatText(@NonNull final String text,
            @NonNull final String downstreamText, @StringRes final int defaultTextId) {
        String formatText;
        try {
            if (TextUtils.isEmpty(text)) throw new IllegalArgumentException("Wrong string format");
            formatText = String.format(text, downstreamText);
        } catch (IllegalArgumentException e) {
            formatText = getResources(mContext, mActiveDataSubId).getString(defaultTextId);
        }
        return formatText;
    }

    private boolean showNotification(@DrawableRes final int iconId, @NonNull final String title,
            @NonNull final String message, @NonNull final String downstreamText) {
        if (iconId == NO_ICON_ID) return NOTIFY_DROP;

        final String formatTitle = formatText(
                title, downstreamText, R.string.tethered_notification_title);
        final String formatMessage = formatText(
                message, downstreamText, R.string.tethered_notification_message);
        final Intent intent = new Intent();
        intent.setClassName("com.android.settings", "com.android.settings.TetherSettings");
        final PendingIntent pi = PendingIntent.getActivity(
                mContext.createContextAsUser(UserHandle.CURRENT, 0),
                0 /* requestCode */, intent, 0 /* flags */, null /* options */);
        final Notification notification =
                new Notification.Builder(mContext, mChannel.getId())
                        .setSmallIcon(iconId)
                        .setContentTitle(formatTitle)
                        .setContentText(formatMessage)
                        .setWhen(0)
                        .setOngoing(true)
                        .setColor(mContext.getColor(
                                android.R.color.system_notification_accent_color))
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setCategory(Notification.CATEGORY_STATUS)
                        .setContentIntent(pi)
                        .build();

        mNotificationManager.notify(null /* tag */, NOTIFY_ID, notification);
        return NOTIFY_DONE;
    }
}
