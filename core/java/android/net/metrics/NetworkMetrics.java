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

package android.net.metrics;

import android.net.NetworkCapabilities;

import com.android.internal.util.BitUtils;
import com.android.internal.util.TokenBucket;

import java.util.StringJoiner;

/**
 * A class aggregating network metrics received by Netd for dns queries and
 * connect() calls. This class also keeps running sums of dns and connect stats,
 * error counts, and latencies for bug report logging.
 *
 * @hide
 */
public class NetworkMetrics {

    private static final int INITIAL_DNS_BATCH_SIZE = 100;
    private static final int CONNECT_LATENCY_MAXIMUM_RECORDS = 20000;

    public final int netId;
    public final long transports;
    public final ConnectStats connect;
    public final DnsEvent dns;
    public final Summary stats;
    public Summary pendingStats; // starts null until some events are added

    public NetworkMetrics(int netId, long transports, TokenBucket tb) {
        this.netId = netId;
        this.transports = transports;
        this.connect = new ConnectStats(netId, transports, tb, CONNECT_LATENCY_MAXIMUM_RECORDS);
        this.dns = new DnsEvent(netId, transports, INITIAL_DNS_BATCH_SIZE);
        this.stats = new Summary(netId, transports);
    }

    /**
     * get current Summary statistics if any for this NetworkMetrics and merge
     * them into long running Summary statistics of this NetworkMetrics.
     */
    public Summary getPendingStats() {
        if (pendingStats != null) {
            stats.merge(pendingStats);
        }
        Summary s = pendingStats;
        pendingStats = null;
        return s;
    }

    /** Aggregate a dns query result reported by netd. */
    public void addDnsResult(int eventType, int returnCode, int latencyMs) {
        if (pendingStats == null) {
            pendingStats = new Summary(netId, transports);
        }
        boolean isSuccess = dns.addResult((byte) eventType, (byte) returnCode, latencyMs);
        pendingStats.dnsLatencies.count(latencyMs / 1000.0);
        pendingStats.dnsErrorRate.count(isSuccess ? 0 : 1);
    }

    /** Aggregate a connect query result reported by netd. */
    public void addConnectResult(int error, int latencyMs, String ipAddr) {
        if (pendingStats == null) {
            pendingStats = new Summary(netId, transports);
        }
        boolean isSuccess = connect.addEvent(error, latencyMs, ipAddr);
        pendingStats.connectErrorRate.count(isSuccess ? 0 : 1);
        if (ConnectStats.isNonBlocking(error)) {
            pendingStats.connectLatencies.count(latencyMs / 1000.0);
        }
    }

    /** Represents running sums for dns and connect average error counts and average latencies. */
    public static class Summary {

        public final int netId;
        public final long transports;
        public final Metrics dnsLatencies = new Metrics();
        public final Metrics dnsErrorRate = new Metrics();
        public final Metrics connectLatencies = new Metrics();
        public final Metrics connectErrorRate = new Metrics();

        public Summary(int netId, long transports) {
            this.netId = netId;
            this.transports = transports;
        }

        void merge(Summary that) {
            dnsLatencies.merge(that.dnsLatencies);
            dnsErrorRate.merge(that.dnsErrorRate);
            connectLatencies.merge(that.connectLatencies);
            connectErrorRate.merge(that.connectErrorRate);
        }

        @Override
        public String toString() {
            StringJoiner j = new StringJoiner(", ", "{", "}");
            j.add("netId=" + netId);
            for (int t : BitUtils.unpackBits(transports)) {
                j.add(NetworkCapabilities.transportNameOf(t));
            }
            j.add(String.format("dns avg=%05.2fs max=%05.2fs err=%04.1f%% tot=%d",
                    dnsLatencies.average(), dnsLatencies.max,
                    100 * dnsErrorRate.average(), dnsErrorRate.count));
            j.add(String.format("connect avg=%05.2fs max=%05.2fs err=%04.1f%% tot=%d",
                    connectLatencies.average(), connectLatencies.max,
                    100 * connectErrorRate.average(), connectErrorRate.count));
            return j.toString();
        }
    }

    /** Tracks a running sum and returns the average of a metrics. */
    static class Metrics {
        public double sum;
        public double max;
        public int count;

        void merge(Metrics that) {
            this.sum += that.sum;
            this.max = Math.min(this.max, that.max);
            this.count += that.count;
        }

        void count(double value) {
            count++;
            sum += value;
            max = Math.max(max, value);
        }

        double average() {
            double a = sum / (double) count;
            if (a != a) { // only NaN != NaN is true
                a = 0;
            }
            return a;
        }
    }
}
