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
import android.annotation.Nullable;
import android.net.util.NetworkStackUtils;
import android.net.wifi.WifiInfo;

import com.android.internal.util.HexDump;
import com.android.server.connectivity.nano.CellularData;
import com.android.server.connectivity.nano.DataStallEventProto;
import com.android.server.connectivity.nano.WifiData;

import com.google.protobuf.nano.MessageNano;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to record the stats of detection level information for data stall.
 *
 * @hide
 */
public final class DataStallDetectionStats {
    private static final int UNKNOWN_SIGNAL_STRENGTH = -1;
    @NonNull
    final byte[] mCellularInfo;
    @NonNull
    final byte[] mWifiInfo;
    @NonNull
    int[] mDnsReturnCode;
    @NonNull
    long[] mDnsTimeStamp;
    final int mEvaluationType;
    final int mNetworkType;

    public DataStallDetectionStats(@Nullable byte[] cell, @Nullable byte[] wifi,
            @NonNull int[] returnCode, @NonNull long[] dnsTime, int evalType, int netType) {
        if (cell != null) {
            this.mCellularInfo = cell;
        } else {
            this.mCellularInfo = initCellularData();
        }
        if (wifi != null) {
            this.mWifiInfo = wifi;
        } else {
            this.mWifiInfo = initWifiInfo();
        }

        this.mDnsReturnCode = returnCode;
        this.mDnsTimeStamp = dnsTime;
        this.mEvaluationType = evalType;
        this.mNetworkType = netType;
    }

    private byte[] initCellularData() {
        CellularData data  = new CellularData();
        data.ratType = DataStallEventProto.RADIO_TECHNOLOGY_UNKNOWN;
        data.networkMccmnc = "";
        data.simMccmnc = "";
        data.signalStrength = UNKNOWN_SIGNAL_STRENGTH;
        return MessageNano.toByteArray(data);
    }

    private byte[] initWifiInfo() {
        WifiData data = new WifiData();
        data.wifiBand = DataStallEventProto.AP_BAND_UNKNOWN;
        data.signalStrength = UNKNOWN_SIGNAL_STRENGTH;
        return MessageNano.toByteArray(data);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("type: ").append(mNetworkType)
          .append(", evaluation type: ")
          .append(mEvaluationType)
          .append(", wifi info: ")
          .append(HexDump.toHexString(mWifiInfo))
          .append(", cell info: ")
          .append(HexDump.toHexString(mCellularInfo));
        return sb.toString();
    }

    /**
     * Utility to create an instance of {@Link DataStallDetectionStats}
     *
     * @hide
     */
    public static class Builder {
        @Nullable
        private byte[] mCellularInfo = null;
        @Nullable
        private byte[] mWifiInfo = null;
        @NonNull
        private final List<Integer> mDnsReturnCode = new ArrayList<Integer>();
        @NonNull
        private final List<Long> mDnsTimeStamp = new ArrayList<Long>();
        private int mEvaluationType;
        private int mNetworkType;

        /**
         * Add a dns event into Builder.
         *
         * @param code the return code of the dns event.
         * @param timeMs the elapsedRealtime in ms that the the dns event was received from netd.
         * @return {@code this} {@link Builder} instance.
         */
        public Builder addDnsEvent(int code, long timeMs) {
            mDnsReturnCode.add(code);
            mDnsTimeStamp.add(timeMs);
            return this;
        }

        /**
         * Set the dns evaluation type into Builder.
         *
         * @param type the return code of the dns event.
         * @return {@code this} {@link Builder} instance.
         */
        public Builder setEvaluationType(int type) {
            mEvaluationType = type;
            return this;
        }

        /**
         * Set the network type into Builder.
         *
         * @param type the network type of the logged network.
         * @return {@code this} {@link Builder} instance.
         */
        public Builder setNetworkType(int type) {
            mNetworkType = type;
            return this;
        }

        /**
         * Set the wifi data into Builder.
         *
         * @param ss the signal strength of the logged wifi network.
         * @param info a {@link WifiInfo} of the connected wifi network.
         * @return {@code this} {@link Builder} instance.
         */
        public Builder setWiFiData(int ss, @NonNull final WifiInfo info) {
            WifiData data = new WifiData();

            int freq = info.getFrequency();
            data.wifiBand = getWifiBand(info);
            data.signalStrength = ss;
            mWifiInfo = MessageNano.toByteArray(data);
            return this;
        }

        private int getWifiBand(@NonNull final WifiInfo info) {
            int band = DataStallEventProto.AP_BAND_UNKNOWN;
            int freq = info.getFrequency();
            // Refer to ScanResult.is5GHz() and ScanResult.is24GHz().
            if (freq > 4900 && freq < 5900) {
                band = DataStallEventProto.AP_BAND_5GHZ;
            } else if (freq > 2400 && freq < 2500) {
                band = DataStallEventProto.AP_BAND_2GHZ;
            }

            return band;
        }

        /**
         * Set the cellular data into Builder.
         *
         * @param rat the radio technology of the logged cellular network.
         * @param roaming a boolean indicates if logged cellular network is roaming or not.
         * @param networkMccmnc the mccmnc of the camped network.
         * @param simMccmnc the mccmnc of the sim.
         * @return {@code this} {@link Builder} instance.
         */
        public Builder setCellData(int rat, boolean roaming,
                @NonNull String networkMccmnc, @NonNull String simMccmnc, int ss) {
            CellularData data  = new CellularData();
            data.ratType = rat;
            data.isRoaming = roaming;
            data.networkMccmnc = networkMccmnc;
            data.simMccmnc = simMccmnc;
            data.signalStrength = ss;
            mCellularInfo = MessageNano.toByteArray(data);
            return this;
        }

        /**
         * Create a new {@Link DataStallDetectionStats}.
         */
        public DataStallDetectionStats build() {
            return new DataStallDetectionStats(mCellularInfo, mWifiInfo,
                    NetworkStackUtils.convertToIntArray(mDnsReturnCode),
                    NetworkStackUtils.convertToLongArray(mDnsTimeStamp),
                    mEvaluationType, mNetworkType);
        }
    }
}
