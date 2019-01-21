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

package android.net;

import android.annotation.NonNull;
import android.net.captiveportal.CaptivePortalProbeResult;
import android.util.Log;
import android.util.StatsLog;

import com.android.framework.protobuf.nano.MessageNano;
import com.android.server.connectivity.DataStallEventProto;
import com.android.server.connectivity.nano.CellularData;
import com.android.server.connectivity.nano.DnsEvent;
import com.android.server.connectivity.nano.WifiData;

import java.util.ArrayList;
import java.util.List;
/**
 * Class for logging data stall events.
 *
 * @hide
 */
public class DataStallEventLogger{
    private static final String TAG = DataStallEventLogger.class.getSimpleName();
    private static final int UNKNOWN_SIGNAL_STRENGTH = -1;

    private int mEvaluationType;
    private int mProbeResult;
    private int mNetworkType;

    @NonNull
    private CellularData mCellularInfo = new CellularData();
    @NonNull
    private WifiData mWifiInfo = new WifiData();
    private List<Integer> mDnsReturnCode = new ArrayList<Integer>();
    private List<Long> mDnsTimeStamp = new ArrayList<Long>();

    public DataStallEventLogger() {
        // Initialize CellularData and WifiData though they are optional proto data in practice.
        // It depends on the statsd design.
        // TODO: Refactor it once statsd support optional field.
        initCellularData();
        initWifiInfo();
    }

    private void initCellularData() {
        mCellularInfo.ratType = DataStallEventProto.RADIO_TECHNOLOGY_UNKNOWN;
        mCellularInfo.isRoaming = false;
        mCellularInfo.networkMccmnc = "";
        mCellularInfo.simMccmnc = "";
        mCellularInfo.signalStrength = UNKNOWN_SIGNAL_STRENGTH;
    }

    private void initWifiInfo() {
        mWifiInfo.wifiBand = DataStallEventProto.AP_BAND_UNKNOWN;
        mWifiInfo.signalStrength = UNKNOWN_SIGNAL_STRENGTH;
    }

    /**
     * Log a dns event into DataStallEventLogger.
     *
     * @param code the return code of the dns event.
     * @param timeMs the elapsedRealtime in ms that the the dns event received from netd.
     * @return {@code this} {@link DataStallEventLogger} instance
     */
    public DataStallEventLogger setDnsEvent(int code, long timeMs) {
        mDnsReturnCode.add(code);
        mDnsTimeStamp.add(timeMs);
        return this;
    }

    /**
     * Log the dns evaluation type into DataStallEventLogger.
     *
     * @param type the return code of the dns event.
     * @return {@code this} {@link DataStallEventLogger} instance
     */
    public DataStallEventLogger setEvaluationType(int type) {
        mEvaluationType = type;
        return this;
    }

    /**
     * Log the validation probe result into DataStallEventLogger.
     *
     * @param result a CaptivePortalProbeResult after data stall being suspected.
     * @return {@code this} {@link DataStallEventLogger} instance
     */
    public DataStallEventLogger setProbeResult(@NonNull CaptivePortalProbeResult result) {
        if (result.isSuccessful()) {
            mProbeResult = DataStallEventProto.VALID;
        } else if (result.isPortal()) {
            mProbeResult = DataStallEventProto.PORTAL;
        } else {
            mProbeResult = DataStallEventProto.INVALID;
        }

        return this;
    }

    /**
     * Log the network type into DataStallEventLogger.
     *
     * @param type the network type of the logged network.
     * @return {@code this} {@link DataStallEventLogger} instance
     */
    public DataStallEventLogger setNetworkType(int type) {
        mNetworkType = type;
        return this;
    }

    /**
     * Log the wifi data into DataStallEventLogger.
     *
     * @param ss the signal strength of the logged wifi network.
     * @param is5G a boolean indicates if logged wifi band is 5G or not.
     * @return {@code this} {@link DataStallEventLogger} instance
     */
    public DataStallEventLogger setWiFiData(int ss, boolean is5G) {
        mWifiInfo.wifiBand = (is5G
            ? DataStallEventProto.AP_BAND_5GHZ : DataStallEventProto.AP_BAND_2GHZ);
        mWifiInfo.signalStrength = ss;
        return this;
    }

    /**
     * Log the cellular data into DataStallEventLogger.
     *
     * @param rat the radio technology of the logged cellular network.
     * @param roaming a boolean indicates if logged cellular network is roaming or not.
     * @param networkMccmnc the mccmnc of the camped network.
     * @param simMccmnc the mccmnc of the sim.
     * @return {@code this} {@link DataStallEventLogger} instance
     */
    public DataStallEventLogger setCellData(int rat, boolean roaming,
            @NonNull String networkMccmnc, @NonNull String simMccmnc, int ss) {
        mCellularInfo.ratType = rat;
        mCellularInfo.isRoaming = roaming;
        mCellularInfo.networkMccmnc = networkMccmnc;
        mCellularInfo.simMccmnc = simMccmnc;
        mCellularInfo.signalStrength = ss;
        return this;
    }

    /**
     * Writes the metric to {@link StatsLog}.
     */
    public void write() {
        Log.d(TAG, "write(), network: " + mNetworkType + ", evaluation type=" + mEvaluationType);
        DnsEvent dns = new DnsEvent();
        dns.dnsReturnCode = mDnsReturnCode.stream().mapToInt(i -> i).toArray();
        dns.dnsTime = mDnsTimeStamp.stream().mapToLong(i -> i).toArray();

        StatsLog.write(StatsLog.DATA_STALL_EVENT,
                mEvaluationType,                        /* evaluation_type */
                mProbeResult,                           /* validation_result */
                mNetworkType,                           /* network_type */
                MessageNano.toByteArray(mWifiInfo),     /* wifi_info */
                MessageNano.toByteArray(mCellularInfo), /* cell_info */
                MessageNano.toByteArray(dns)            /* dns_event */
        );
        // Reset the dns event data whenever
        resetDnsResult();
    }

    private void resetDnsResult() {
        mDnsReturnCode.clear();
        mDnsTimeStamp.clear();
    }
}
