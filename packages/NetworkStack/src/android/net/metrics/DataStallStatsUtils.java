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

package android.net.metrics;

import android.annotation.NonNull;
import android.net.captiveportal.CaptivePortalProbeResult;
import android.util.Log;

import com.android.internal.util.HexDump;
import com.android.server.connectivity.nano.DataStallEventProto;
import com.android.server.connectivity.nano.DnsEvent;

import com.google.protobuf.nano.MessageNano;

import java.lang.reflect.Method;

/**
 * Collection of utilities for data stall metrics.
 *
 * To see if the logs are properly sent to statsd, execute following command.
 *
 * $ adb shell cmd stats print-logs
 * $ adb logcat | grep statsd  OR $ adb logcat -b stats
 *
 * @hide
 */
public class DataStallStatsUtils {
    private static final String TAG = DataStallStatsUtils.class.getSimpleName();
    private static final boolean DBG = false;
    // Refer to the definition in atoms.proto.
    private static final int ProtoId = 121;

    private static int probeResultToEnum(@NonNull final CaptivePortalProbeResult result) {
        // TODO: Add partial connectivity support.
        if (result.isSuccessful()) {
            return DataStallEventProto.VALID;
        } else if (result.isPortal()) {
            return DataStallEventProto.PORTAL;
        } else {
            return DataStallEventProto.INVALID;
        }
    }

    /**
     * Write the metric to {@link StatsLog}.
     */
    public static void write(@NonNull final DataStallDetectionStats stats,
            @NonNull final CaptivePortalProbeResult result) {
        DnsEvent dns = new DnsEvent();
        dns.dnsReturnCode = stats.mDnsReturnCode;
        dns.dnsTime = stats.mDnsTimeStamp;
        int validationResult = probeResultToEnum(result);
        if (DBG) {
            Log.d(TAG, "write: " + stats + " with result: " + validationResult
                    + ", dns: " + HexDump.toHexString(MessageNano.toByteArray(dns)));
        }

        try {
            // TODO(b/124613085): Update to public StatsLog API.
            final Class c = Class.forName("android.util.StatsLogInternal");
            final Object o = c.newInstance();
            // Refer to the definition in frameworks/base/cmds/statsd/src/atoms.proto.
            final Method m = c.getMethod("write", int.class, int.class, int.class, int.class,
                    byte[].class, byte[].class, byte[].class);
            m.invoke(o, ProtoId,
                    stats.mEvaluationType,
                    validationResult,
                    stats.mNetworkType,
                    stats.mWifiInfo,
                    stats.mCellularInfo,
                    MessageNano.toByteArray(dns));
        } catch (Exception e) {
            Log.e(TAG, "Exception while writing the metrics to statsd: " + e);
        }
    }
}
