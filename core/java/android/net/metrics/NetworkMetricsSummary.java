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

/**
 * WRITEME
 *
 * TODO: Consider embedding me into NetworkMetrics
 * @hide
 */
public class NetworkMetricsSummary {

    public final int netId;
    public final long transports;
    public final MetricsSummary dns = new MetricsSummary();
    public final MetricsSummary connect = new MetricsSummary();

    public NetworkMetricsSummary(int netId, long transports) {
        this.netId = netId;
        this.transports = transports;
    }

    void mergeFrom(NetworkMetricsSummary that) {
        dns.mergeFrom(that.dns);
        connect.mergeFrom(that.connect);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("(").append(netId).append(", ");
        for (int t : BitUtils.unpackBits(transports)) {
            builder.append(NetworkCapabilities.transportNameOf(t)).append(", ");
        }
        builder.append(String.format("dns: {%s}, connect: {%s})", dns, connect));
        return builder.toString();
    }


    /**
     * Summarizes statistics about a metrics for which every event has an associated value
     * and an error or success return status.
     */
    static class MetricsSummary {
        public double sum;
        public double max;
        public int count;
        public int successCount;
        public int errorCount;

        void mergeFrom(MetricsSummary that) {
            this.sum += that.sum;
            this.max = Math.min(this.max, that.max);
            this.count += that.count;
            this.successCount += that.successCount;
            this.errorCount += that.errorCount;
        }

        void countEvent(boolean isSuccess) {
            if (isSuccess) {
                successCount++;
            } else {
                errorCount++;
            }
        }

        void countValue(double value) {
            count++;
            sum += value;
            max = Math.max(max, value);
        }

        @Override
        public String toString() {
            double avg = sum / (double) count;
            if (avg != avg) { // only NaN != NaN is true
                avg = 0;
            }
            int tot = successCount + errorCount;
            int errRate = (100 * errorCount) / tot;
            return String.format("avg: %.2f, max: %.2f, tot: %d, err: %d%%",
                    avg, max, tot, errRate);
        }
    }
}
