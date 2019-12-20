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

import android.annotation.Nullable;
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

import com.android.internal.annotations.GuardedBy;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link TrustedTime} that connects with a remote NTP server as its trusted time source. This class
 * is thread-safe. The {@link #forceRefresh()} method is synchronous, i.e. it may occupy the
 * current thread while performing an NTP request. All other threads calling {@link #forceRefresh()}
 * will block during that request.
 *
 * @hide
 */
public class NtpTrustedTime implements TrustedTime {
    private static final String TAG = "NtpTrustedTime";
    private static final boolean LOGD = false;

    private static NtpTrustedTime sSingleton;

    private final Context mContext;
    private final Supplier<ConnectivityManager> mConnectivityManagerSupplier =
            new Supplier<ConnectivityManager>() {
        private ConnectivityManager mConnectivityManager;

        @Nullable
        @Override
        public synchronized ConnectivityManager get() {
            // We can't do this at initialization time: ConnectivityService might not be running
            // yet.
            if (mConnectivityManager == null) {
                mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
            }
            return mConnectivityManager;
        }
    };

    @GuardedBy("this") private boolean mHasCache;
    @GuardedBy("this") private long mCachedNtpTime;
    @GuardedBy("this") private long mCachedNtpElapsedRealtime;
    @GuardedBy("this") private long mCachedNtpCertainty;

    private NtpTrustedTime(Context context) {
        mContext = Objects.requireNonNull(context);
    }

    @UnsupportedAppUsage
    public static synchronized NtpTrustedTime getInstance(Context context) {
        if (sSingleton == null) {
            Context appContext = context.getApplicationContext();
            sSingleton = new NtpTrustedTime(appContext);
        }
        return sSingleton;
    }

    @Override
    @UnsupportedAppUsage
    public boolean forceRefresh() {
        synchronized (this) {
            NtpConnectionInfo connectionInfo = getNtpConnectionInfo();
            if (connectionInfo == null) {
                // missing server config, so no trusted time available
                return false;
            }

            ConnectivityManager connectivityManager = mConnectivityManagerSupplier.get();
            if (connectivityManager == null) {
                if (LOGD) Log.d(TAG, "forceRefresh: no ConnectivityManager");
                return false;
            }
            final Network network = connectivityManager.getActiveNetwork();
            final NetworkInfo ni = connectivityManager.getNetworkInfo(network);
            if (ni == null || !ni.isConnected()) {
                if (LOGD) Log.d(TAG, "forceRefresh: no connectivity");
                return false;
            }

            if (LOGD) Log.d(TAG, "forceRefresh() from cache miss");
            final SntpClient client = new SntpClient();
            final String serverName = connectionInfo.getServer();
            final int timeoutMillis = connectionInfo.getTimeoutMillis();
            if (client.requestTime(serverName, timeoutMillis, network)) {
                mHasCache = true;
                mCachedNtpTime = client.getNtpTime();
                mCachedNtpElapsedRealtime = client.getNtpTimeReference();
                mCachedNtpCertainty = client.getRoundTripTime() / 2;
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    @UnsupportedAppUsage
    public synchronized boolean hasCache() {
        return mHasCache;
    }

    @Override
    public synchronized long getCacheAge() {
        if (mHasCache) {
            return SystemClock.elapsedRealtime() - mCachedNtpElapsedRealtime;
        } else {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public synchronized long getCacheCertainty() {
        if (mHasCache) {
            return mCachedNtpCertainty;
        } else {
            return Long.MAX_VALUE;
        }
    }

    @Override
    @UnsupportedAppUsage
    public synchronized long currentTimeMillis() {
        if (!mHasCache) {
            throw new IllegalStateException("Missing authoritative time source");
        }
        if (LOGD) Log.d(TAG, "currentTimeMillis() cache hit");

        // current time is age after the last ntp cache; callers who
        // want fresh values will hit makeAuthoritative() first.
        return mCachedNtpTime + getCacheAge();
    }

    @UnsupportedAppUsage
    public synchronized long getCachedNtpTime() {
        if (LOGD) Log.d(TAG, "getCachedNtpTime() cache hit");
        return mCachedNtpTime;
    }

    @UnsupportedAppUsage
    public synchronized long getCachedNtpTimeReference() {
        return mCachedNtpElapsedRealtime;
    }

    /**
     * Returns the combination of {@link #getCachedNtpTime()} and {@link
     * #getCachedNtpTimeReference()} as a {@link TimestampedValue}. This method is useful when
     * passing the time to another component that will adjust for elapsed time.
     *
     * @throws IllegalStateException if there is no cached value
     */
    @UnsupportedAppUsage
    public synchronized TimestampedValue<Long> getCachedNtpTimeSignal() {
        if (!mHasCache) {
            throw new IllegalStateException("Missing authoritative time source");
        }
        if (LOGD) Log.d(TAG, "getCachedNtpTimeSignal() cache hit");

        return new TimestampedValue<>(mCachedNtpElapsedRealtime, mCachedNtpTime);
    }

    private static class NtpConnectionInfo {

        private final String mServer;
        private final int mTimeoutMillis;

        NtpConnectionInfo(String server, int timeoutMillis) {
            mServer = server;
            mTimeoutMillis = timeoutMillis;
        }

        public String getServer() {
            return mServer;
        }

        int getTimeoutMillis() {
            return mTimeoutMillis;
        }
    }

    @GuardedBy("this")
    private NtpConnectionInfo getNtpConnectionInfo() {
        final ContentResolver resolver = mContext.getContentResolver();

        final Resources res = mContext.getResources();
        final String defaultServer = res.getString(
                com.android.internal.R.string.config_ntpServer);
        final int defaultTimeoutMillis = res.getInteger(
                com.android.internal.R.integer.config_ntpTimeout);

        final String secureServer = Settings.Global.getString(
                resolver, Settings.Global.NTP_SERVER);
        final int timeoutMillis = Settings.Global.getInt(
                resolver, Settings.Global.NTP_TIMEOUT, defaultTimeoutMillis);

        final String server = secureServer != null ? secureServer : defaultServer;
        return server == null ? null : new NtpConnectionInfo(server, timeoutMillis);
    }
}
