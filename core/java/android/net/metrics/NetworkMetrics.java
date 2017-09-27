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

import com.android.internal.util.TokenBucket;

/**
 * WRITEME
 * @hide
 */
public class NetworkMetrics {

    private static final int INITIAL_DNS_BATCH_SIZE = 100;
    private static final int CONNECT_LATENCY_MAXIMUM_RECORDS = 20000;

    public final int netId;
    public final long transports;
    public final ConnectStats connect;
    public final DnsEvent dns;

    public final NetworkMetricsSummary stats;
    public NetworkMetricsSummary currentStatsBucket; // better name ??

    public NetworkMetrics(int netId, long transports, TokenBucket tb) {
        this.netId = netId;
        this.transports = transports;
        this.connect = new ConnectStats(netId, transports, tb, CONNECT_LATENCY_MAXIMUM_RECORDS);
        this.dns = new DnsEvent(netId, transports, INITIAL_DNS_BATCH_SIZE);
        this.stats = new NetworkMetricsSummary(netId, transports);
        this.currentStatsBucket = new NetworkMetricsSummary(netId, transports);
    }

    /** WRITEME */
    public NetworkMetricsSummary pushCurrentStatsBucket() {
        stats.mergeFrom(currentStatsBucket);
        NetworkMetricsSummary x = currentStatsBucket;
        currentStatsBucket = new NetworkMetricsSummary(netId, transports);
        return x;
    }

    /** WRITEME */
    public void addDnsResult(int eventType, int returnCode, int latencyMs) {
        boolean isSuccess = dns.addResult((byte) eventType, (byte) returnCode, latencyMs);
        currentStatsBucket.dns.countEvent(isSuccess);
        currentStatsBucket.dns.countValue(latencyMs / 1000.0);
    }

    /** WRITEME */
    public void addConnectResult(int error, int latencyMs, String ipAddr) {
        boolean isSuccess = connect.addEvent(error, latencyMs, ipAddr);
        currentStatsBucket.connect.countEvent(isSuccess);
        if (ConnectStats.isNonBlocking(error)) {
            currentStatsBucket.connect.countValue(latencyMs / 1000.0);
        }
    }
}
