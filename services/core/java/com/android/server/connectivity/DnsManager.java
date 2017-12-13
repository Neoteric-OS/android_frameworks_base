/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.connectivity;

import static android.Manifest.permission.CONNECTIVITY_INTERNAL;
import static android.net.ConnectivityManager.PRIVATE_DNS_DEFAULT_MODE;
import static android.net.ConnectivityManager.PRIVATE_DNS_MODE_OPPORTUNISTIC;
import static android.net.ConnectivityManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;
import static android.provider.Settings.Global.DNS_RESOLVER_MIN_SAMPLES;
import static android.provider.Settings.Global.DNS_RESOLVER_MAX_SAMPLES;
import static android.provider.Settings.Global.DNS_RESOLVER_SAMPLE_VALIDITY_SECONDS;
import static android.provider.Settings.Global.DNS_RESOLVER_SUCCESS_THRESHOLD_PERCENT;
import static android.provider.Settings.Global.PRIVATE_DNS_MODE;
import static android.provider.Settings.Global.PRIVATE_DNS_SPECIFIER;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkUtils;
import android.os.Binder;
import android.os.INetworkManagementService;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;

import java.net.InetAddress;
import java.util.Collection;


/**
 * Encapsulate the management of DNS settings for networks.
 *
 * This class it NOT designed for concurrent access.
 *
 * @hide
 */
public class DnsManager {
    private static final String TAG = DnsManager.class.getSimpleName();

    /* Defaults for resolver parameters. */
    private static final int DNS_RESOLVER_DEFAULT_SAMPLE_VALIDITY_SECONDS = 1800;
    private static final int DNS_RESOLVER_DEFAULT_SUCCESS_THRESHOLD_PERCENT = 25;
    private static final int DNS_RESOLVER_DEFAULT_MIN_SAMPLES = 8;
    private static final int DNS_RESOLVER_DEFAULT_MAX_SAMPLES = 64;

    private final Handler mHandler;
    private final Context mContext;
    private final ContentResolver mContentResolver;
    private INetworkManagementService mNMS;

    private int mSampleValidity;
    private int mSuccessThreshold;
    private int mMinSamples;
    private int mMaxSamples;
    private String mPrivateDnsMode;
    private String mPrivateDnsSpecifier;

    public DnsManager(Handler h, Context ctx, INetworkManagementService nms) {
        mHandler = h;
        mContext = ctx;
        mContentResolver = mContext.getContentResolver();
        mNMS = nms;

        // TODO: Create and register ContentObservers to track every setting
        // used
    }

    public void setDnsConfigurationForNetwork(
            int netId, Collection<InetAddress> servers, String domains) {
        // TODO: Is this permission check still necessary?
        mContext.enforceCallingOrSelfPermission(CONNECTIVITY_INTERNAL, TAG);

        updateParametersSettings();
        updatePrivateDnsSettings();

        final String[] serverStrs = NetworkUtils.makeStrings(servers);
        final String[] domainStrs = (domains == null) ? new String[0] : domains.split(" ");
        final int[] params = { mSampleValidity, mSuccessThreshold, mMinSamples, mMaxSamples };
        final boolean useTls = shouldUseTls(mPrivateDnsMode);
        // TODO: Populate tlsHostname once it's decided how the hostname's IP
        // addresses will be resolved:
        //
        //     [1] network-provided DNS servers are included here with the
        //         hostname and netd will use the network-provided servers to
        //         resolve the hostname and fix up its internal structures, or
        //
        //     [2] network-provided DNS servers are included here without the
        //         hostname, the ConnectivityService layer resolves the given
        //         hostname, and then reconfigures netd with this information.
        //
        // In practice, there will always be a need for ConnectivityService or
        // the captive portal app to use the network-provided services to make
        // some queries. This argues in favor of [1], in concert with another
        // mechanism, perhaps setting a high bit in the netid, to indicate
        // via existing DNS APIs which set of servers (network-provided or
        // non-network-provided private DNS) should be queried.
        final String tlsHostname = "";
        try {
            mNMS.setDnsConfigurationForNetwork(
                    netId, serverStrs, domainStrs, params, useTls, tlsHostname);
        } catch (Exception e) {
            Slog.e(TAG, "Error setting DNS configuration: " + e);
        }
    }

    public void flushVmDnsCache() {
        /*
         * Tell the VMs to toss their DNS caches
         */
        final Intent intent = new Intent(Intent.ACTION_CLEAR_DNS_CACHE);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        /*
         * Connectivity events can happen before boot has completed ...
         */
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        final long ident = Binder.clearCallingIdentity();
        try {
            mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public boolean inPrivateDnsStrictMode() {
        return !TextUtils.isEmpty(mPrivateDnsMode) &&
               mPrivateDnsMode.startsWith(PRIVATE_DNS_MODE_PROVIDER_HOSTNAME) &&
               !TextUtils.isEmpty(mPrivateDnsSpecifier);
    }

    private void updatePrivateDnsSettings() {
        mPrivateDnsMode = getStringSetting(PRIVATE_DNS_MODE);
        mPrivateDnsSpecifier = getStringSetting(PRIVATE_DNS_SPECIFIER);
    }

    private void updateParametersSettings() {
        mSampleValidity = getIntSetting(
                DNS_RESOLVER_SAMPLE_VALIDITY_SECONDS,
                DNS_RESOLVER_DEFAULT_SAMPLE_VALIDITY_SECONDS);
        if (mSampleValidity < 0 || mSampleValidity > 65535) {
            Slog.w(TAG, "Invalid sampleValidity=" + mSampleValidity + ", using default=" +
                    DNS_RESOLVER_DEFAULT_SAMPLE_VALIDITY_SECONDS);
            mSampleValidity = DNS_RESOLVER_DEFAULT_SAMPLE_VALIDITY_SECONDS;
        }

        mSuccessThreshold = getIntSetting(
                DNS_RESOLVER_SUCCESS_THRESHOLD_PERCENT,
                DNS_RESOLVER_DEFAULT_SUCCESS_THRESHOLD_PERCENT);
        if (mSuccessThreshold < 0 || mSuccessThreshold > 100) {
            Slog.w(TAG, "Invalid successThreshold=" + mSuccessThreshold + ", using default=" +
                    DNS_RESOLVER_DEFAULT_SUCCESS_THRESHOLD_PERCENT);
            mSuccessThreshold = DNS_RESOLVER_DEFAULT_SUCCESS_THRESHOLD_PERCENT;
        }

        mMinSamples = getIntSetting(DNS_RESOLVER_MIN_SAMPLES, DNS_RESOLVER_DEFAULT_MIN_SAMPLES);
        mMaxSamples = getIntSetting(DNS_RESOLVER_MAX_SAMPLES, DNS_RESOLVER_DEFAULT_MAX_SAMPLES);
        if (mMinSamples < 0 || mMinSamples > mMaxSamples || mMaxSamples > 64) {
            Slog.w(TAG, "Invalid sample count (min, max)=(" + mMinSamples + ", " + mMaxSamples +
                    "), using default=(" + DNS_RESOLVER_DEFAULT_MIN_SAMPLES + ", " +
                    DNS_RESOLVER_DEFAULT_MAX_SAMPLES + ")");
            mMinSamples = DNS_RESOLVER_DEFAULT_MIN_SAMPLES;
            mMaxSamples = DNS_RESOLVER_DEFAULT_MAX_SAMPLES;
        }
    }

    private String getStringSetting(String which) {
        return Settings.Global.getString(mContentResolver, which);
    }

    private int getIntSetting(String which, int dflt) {
        return Settings.Global.getInt(mContentResolver, which, dflt);
    }

    private static boolean shouldUseTls(String mode) {
        if (TextUtils.isEmpty(mode)) {
            mode = PRIVATE_DNS_DEFAULT_MODE;
        }
        return mode.equals(PRIVATE_DNS_MODE_OPPORTUNISTIC) ||
               mode.startsWith(PRIVATE_DNS_MODE_PROVIDER_HOSTNAME);
    }
}
