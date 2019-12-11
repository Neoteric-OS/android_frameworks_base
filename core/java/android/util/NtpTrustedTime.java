/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.util;

import android.annotation.UnsupportedAppUsage;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.SntpClient;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;

/**
 * {@link TrustedTime} that connects with a remote NTP server as its trusted
 * time source.
 *
 * @hide
 */
public class NtpTrustedTime implements TrustedTime {
    private static final String TAG = "NtpTrustedTime";
    private static final boolean LOGD = false;

    private final Context mContext;
    private final String mSecureServer;
    private final long mGlobalTimeout;

    private ConnectivityManager mCM;

    private boolean mHasCache;
    private long mCachedNtpTime;
    private long mCachedNtpElapsedRealtime;
    private long mCachedNtpCertainty;

    public NtpTrustedTime(Context context) {
        final ContentResolver resolver = context.getContentResolver();

        mSecureServer = Settings.Global.getString(
                resolver, Settings.Global.NTP_SERVER);
        mGlobalTimeout = Settings.Global.getLong(
                resolver, Settings.Global.NTP_TIMEOUT, -1);

        mContext = context;
    }

    @Override
    @UnsupportedAppUsage
    public boolean forceRefresh() {
        // We can't do this at initialization time: ConnectivityService might not be running yet.
        if (mCM == null) {
            mCM = mContext.getSystemService(ConnectivityManager.class);
        }

        final Network network = mCM == null ? null : mCM.getActiveNetwork();
        return forceRefresh(network);
    }

    public boolean forceRefresh(Network network) {
        final String server = mSecureServer != null ? mSecureServer :
                mContext.getResources().getString(com.android.internal.R.string.config_ntpServer);
        if (LOGD) Log.d(TAG, "refreshing NtpTrustedTime using " + server);
        final long timeout = mGlobalTimeout != -1 ? mGlobalTimeout :
                mContext.getResources().getInteger(com.android.internal.R.integer.config_ntpTimeout);

        // We can't do this at initialization time: ConnectivityService might not be running yet.
        if (mCM == null) {
            mCM = mContext.getSystemService(ConnectivityManager.class);
        }

        final NetworkInfo ni = mCM == null ? null : mCM.getNetworkInfo(network);
        if (ni == null || !ni.isConnected()) {
            if (LOGD) Log.d(TAG, "forceRefresh: no connectivity");
            return false;
        }


        if (LOGD) Log.d(TAG, "forceRefresh() from cache miss");
        final SntpClient client = new SntpClient();
        if (client.requestTime(server, (int) timeout, network)) {
            mHasCache = true;
            mCachedNtpTime = client.getNtpTime();
            mCachedNtpElapsedRealtime = client.getNtpTimeReference();
            mCachedNtpCertainty = client.getRoundTripTime() / 2;
            return true;
        } else {
            return false;
        }
    }

    @Override
    @UnsupportedAppUsage
    public boolean hasCache() {
        return mHasCache;
    }

    @Override
    public long getCacheAge() {
        if (mHasCache) {
            return SystemClock.elapsedRealtime() - mCachedNtpElapsedRealtime;
        } else {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public long getCacheCertainty() {
        if (mHasCache) {
            return mCachedNtpCertainty;
        } else {
            return Long.MAX_VALUE;
        }
    }

    @Override
    @UnsupportedAppUsage
    public long currentTimeMillis() {
        if (!mHasCache) {
            throw new IllegalStateException("Missing authoritative time source");
        }
        if (LOGD) Log.d(TAG, "currentTimeMillis() cache hit");

        // current time is age after the last ntp cache; callers who
        // want fresh values will hit makeAuthoritative() first.
        return mCachedNtpTime + getCacheAge();
    }

    @UnsupportedAppUsage
    public long getCachedNtpTime() {
        if (LOGD) Log.d(TAG, "getCachedNtpTime() cache hit");
        return mCachedNtpTime;
    }

    @UnsupportedAppUsage
    public long getCachedNtpTimeReference() {
        return mCachedNtpElapsedRealtime;
    }
}
