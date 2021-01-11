/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.net.util;

import static android.provider.Settings.Global.NETWORK_METERED_MULTIPATH_PREFERENCE;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;

/**
 * A class to update metered multipath preference.
 * @hide
 */
public class MeteredMultipathPreferenceTracker {
    private static final String TAG = MeteredMultipathPreferenceTracker.class.getSimpleName();

    private final Context mContext;
    private final Handler mHandler;
    private final ContentResolver mResolver;
    private final Uri mSettingsUri;
    private final SettingObserver mSettingObserver;
    private final BroadcastReceiver mBroadcastReceiver;
    private volatile int mMeteredMultipathPreference;
    private int mActiveSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    public MeteredMultipathPreferenceTracker(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        mResolver = mContext.getContentResolver();
        mSettingsUri = Settings.Global.getUriFor(NETWORK_METERED_MULTIPATH_PREFERENCE);
        mSettingObserver = new SettingObserver();
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateMeteredMultipathPreference();
            }
        };

        context.getSystemService(TelephonyManager.class).listen(
                new PhoneStateListener(handler.getLooper()) {
                @Override
                public void onActiveDataSubscriptionIdChanged(int subId) {
                    mActiveSubId = subId;
                    updateMeteredMultipathPreference();
                }
            }, PhoneStateListener.LISTEN_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGE);
        updateMeteredMultipathPreference();
    }

    /**
     * Start listening intent event for config changed.
     */
    public void start() {
        mResolver.registerContentObserver(mSettingsUri, false, mSettingObserver);

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        mContext.registerReceiverForAllUsers(
                mBroadcastReceiver, intentFilter, null /* broadcastPermission */, mHandler);
        reevaluate();
    }

    /**
     * Stop listening intent event.
     */
    public void stop() {
        mResolver.unregisterContentObserver(mSettingObserver);
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    /**
     * Post updateMeteredMultipathPreference to handler thread.
     */
    @VisibleForTesting
    public void reevaluate() {
        mHandler.post(this::updateMeteredMultipathPreference);
    }

    /**
     * The default (device and carrier-dependent) value for metered multipath preference.
     */
    public int configMeteredMultipathPreference() {
        return mContext.getResources().getInteger(
                R.integer.config_networkMeteredMultipathPreference);
    }

    /**
     * Update metered multipath preference from settings.
     */
    public void updateMeteredMultipathPreference() {
        String setting = Settings.Global.getString(mResolver, NETWORK_METERED_MULTIPATH_PREFERENCE);
        try {
            mMeteredMultipathPreference = Integer.parseInt(setting);
        } catch (NumberFormatException e) {
            mMeteredMultipathPreference = configMeteredMultipathPreference();
        }
    }

    public int getMeteredMultipathPreference() {
        return mMeteredMultipathPreference;
    }

    private class SettingObserver extends ContentObserver {
        SettingObserver() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            Log.wtf(TAG, "Should never be reached.");
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (!mSettingsUri.equals(uri)) {
                Log.wtf(TAG, "Unexpected settings observation: " + uri);
            }
            reevaluate();
        }
    }
}
