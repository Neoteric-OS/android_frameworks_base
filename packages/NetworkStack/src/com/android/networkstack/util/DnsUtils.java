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

package com.android.networkstack.util;

import static android.net.DnsResolver.FLAG_NO_CACHE_LOOKUP;
import static android.net.DnsResolver.TYPE_A;
import static android.net.DnsResolver.TYPE_AAAA;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.DnsResolver;
import android.net.Network;
import android.net.TrafficStats;
import android.net.shared.PrivateDnsConfig;
import android.net.util.Stopwatch;
import android.util.Log;

import com.android.internal.util.TrafficStatsConstants;
import com.android.server.connectivity.NetworkMonitor.DnsLogFunc;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Collection of utilities for dns query.
 */
public class DnsUtils {
    // Decide what queries to make depending on what IP addresses are on the system.
    public static final int TYPE_ADDRCONFIG = -1;
    private static final String TAG = DnsUtils.class.getSimpleName();
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    /**
     * Return both A and AAAA query results regardless the ip address type of the giving network.
     * Used for probing in NetworkMonitor.
     */
    @NonNull
    public static InetAddress[] getAllByName(@NonNull final DnsResolver dnsResolver,
            @NonNull final Network network, @NonNull String host, int timeout,
            @NonNull final DnsLogFunc logger) throws UnknownHostException {
        final List<InetAddress> result = new ArrayList<InetAddress>();
        final StringBuffer errorMsg = new StringBuffer(host);

        try {
            result.addAll(Arrays.asList(
                    getAllByName(dnsResolver, network, host, TYPE_AAAA, FLAG_NO_CACHE_LOOKUP,
                    timeout, logger)));
        } catch (UnknownHostException e) {
            // Might happen if the host is v4-only, still need to query TYPE_A
            errorMsg.append(String.format(" (%s)%s", dnsTypeToStr(TYPE_AAAA), e.getMessage()));
        }
        try {
            result.addAll(Arrays.asList(
                    getAllByName(dnsResolver, network, host, TYPE_A, FLAG_NO_CACHE_LOOKUP,
                    timeout, logger)));
        } catch (UnknownHostException e) {
            // Might happen if the host is v6-only, still need to return AAAA answers
            errorMsg.append(String.format(" (%s)%s", dnsTypeToStr(TYPE_A), e.getMessage()));
        }

        PrivateDnsConfig privateDnsConfig =
                new PrivateDnsConfig(host, result.toArray(new InetAddress[0]));
        if (result.size() == 0) {
            logger.log("Strict mode hostname resolution failed: " + errorMsg.toString());
            throw new UnknownHostException(errorMsg.toString());
        }
        logger.log("Strict mode hostname resolved: " + privateDnsConfig);
        return result.toArray(new InetAddress[0]);
    }

    /**
     * Return dns query result based on the given QueryType(TYPE_A, TYPE_AAAA) or TYPE_ADDRCONFIG.
     * Used for probing in NetworkMonitor.
     */
    @NonNull
    public static InetAddress[] getAllByName(@NonNull final DnsResolver dnsResolver,
            @NonNull final Network network, @NonNull final String host, int type, int flag,
            int timeoutMs, @Nullable final DnsLogFunc logger) throws UnknownHostException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<List<InetAddress>> resultRef = new AtomicReference<>();
        final StringBuffer errorMsg = new StringBuffer();
        final Stopwatch watch = new Stopwatch().start();
        final DnsResolver.Callback<List<InetAddress>> callback =
                new DnsResolver.Callback<List<InetAddress>>() {
            @Override
            public void onAnswer(List<InetAddress> answer, int rcode) {
                if (rcode == 0) {
                    resultRef.set(answer);
                }
                latch.countDown();
            }

            @Override
            public void onError(@NonNull DnsResolver.DnsException e) {
                errorMsg.append(e.getMessage());
                if (DBG) {
                    Log.d(TAG, "DNS error resolving " + errorMsg);
                }
                latch.countDown();
            }
        };
        // TODO: Investigate whether this is still useful.
        // The packets that actually do the DNS queries are sent by netd, but netd doesn't
        // look at the tag at all. Given that this is a library, the tag should be passed in by the
        // caller.
        final int oldTag = TrafficStats.getAndSetThreadStatsTag(
                TrafficStatsConstants.TAG_SYSTEM_PROBE);

        if (type == TYPE_ADDRCONFIG) {
            dnsResolver.query(network, host, flag, r -> r.run(), null /* cancellationSignal */,
                    callback);
        } else {
            dnsResolver.query(network, host, type, flag, r -> r.run(),
                    null /* cancellationSignal */, callback);
        }

        TrafficStats.setThreadStatsTag(oldTag);

        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        }
        final long latency = watch.stop();
        String connectInfo;
        final List<InetAddress> result = resultRef.get();
        if (result == null || result.size() == 0) {
            connectInfo = "FAIL ";
            if (logger != null) {
                logger.log(String.format("%dms %sin type %s", latency, connectInfo,
                        dnsTypeToStr(type), errorMsg.toString()));
            }
            throw new UnknownHostException((errorMsg.length() == 0)
                    ? "Timeout" : errorMsg.toString());
        }
        StringBuffer buffer = new StringBuffer();
        for (InetAddress address : result) {
            buffer.append(',').append(address.getHostAddress());
        }
        connectInfo = "OK " + buffer.substring(1);
        if (logger != null) {
            logger.log(String.format("%dms %s", latency, connectInfo));
        }
        return result.toArray(new InetAddress[0]);
    }

    private static String dnsTypeToStr(int type) {
        switch (type) {
            case TYPE_A:
                return "A";
            case TYPE_AAAA:
                return "AAAA";
            case TYPE_ADDRCONFIG:
                return "ADDRCONFIG";
            default:
        }
        return "UNDEFINED";
    }
}
