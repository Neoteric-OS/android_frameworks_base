/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.bluetooth.le;

import android.annotation.Nullable;
import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * ScanResult for Bluetooth LE scan.
 */
public final class ScanResult implements Parcelable {

    /**
     * The data returned is complete
     */
    public static final int DATA_COMPLETE = 0x00;

    /**
     * The data returned is incomplete. The controller was unsuccessfull to
     * receive all chained packets, returning only partial data.
     */
    public static final int DATA_INCOMPLETE_TRUNCATED = 2;

    /**
     * No packets were received on secondary advertising PHY.
     */
    public static final int PHY_NO_PACKETS = 0;

    /**
     * 1M advertiser PHY.
     */
    public static final int PHY_LE_1M = 1;

    /**
     * 2M advertiser PHY.
     */
    public static final int PHY_LE_2M = 2;

    /**
     * LE Coded advertiser PHY.
     */
    public static final int PHY_LE_CODED = 3;

    /**
     * Advertising Set ID is not present in the packet.
     */
    public static final int SID_NOT_PRESENT = 0xFF;

    /**
     * Mask for checking wether event type represents legacy advertisement.
     */
    private static final int ET_LEGACY_MASK = 0x10;

    /**
     * Mask for checking wether event type represents connectable advertisement.
     */
    private static final int ET_CONNECTABLE_MASK = 0x01;

    // Remote bluetooth device.
    private BluetoothDevice mDevice;

    // Scan record, including advertising data and scan response data.
    @Nullable
    private ScanRecord mScanRecord;

    // Received signal strength.
    private int mRssi;

    // Device timestamp when the result was last seen.
    private long mTimestampNanos;

    private int mEventType;
    private int mPrimaryPhy;
    private int mSecondaryPhy;
    private int mAdvertisingSid;
    private int mTxPower;
    private int mPeriodicAdvertisingInterval;

    /**
     * Constructor of scan result.
     *
     * @param device Remote bluetooth device that is found.
     * @param scanRecord Scan record including both advertising data and scan response data.
     * @param rssi Received signal strength.
     * @param timestampNanos Device timestamp when the scan result was observed.
     * @deprecated use {@link #ScanResult(BluetoothDevice, int, int, int, int, int, int, int, ScanRecord, long)}
     */
    public ScanResult(BluetoothDevice device, ScanRecord scanRecord, int rssi,
            long timestampNanos) {
        mDevice = device;
        mScanRecord = scanRecord;
        mRssi = rssi;
        mTimestampNanos = timestampNanos;
        mEventType = (DATA_COMPLETE << 5) | ET_LEGACY_MASK | ET_CONNECTABLE_MASK;
        mPrimaryPhy = PHY_LE_1M;
        mSecondaryPhy = PHY_NO_PACKETS;
        mAdvertisingSid = SID_NOT_PRESENT;
        mTxPower = 127;
        mPeriodicAdvertisingInterval = 0;
    }

    /**
     * Constructor of scan result.
     *
     * @param device Remote bluetooth device that is found.
     * @param eventType event type.
     * @param primaryPhy primary advertising phy.
     * @param secondaryPhy secondary advertising phy.
     * @param advertisingSid advertising set ID.
     * @param txPower transmit power.
     * @param rssi Received signal strength.
     * @param periodicAdvertisingInterval periodic advertising interval.
     * @param scanRecord Scan record including both advertising data and scan response data.
     * @param timestampNanos Device timestamp when the scan result was observed.
     */
    public ScanResult(BluetoothDevice device, int eventType, int primaryPhy, int secondaryPhy,
                      int advertisingSid, int txPower, int rssi, int periodicAdvertisingInterval,
                      ScanRecord scanRecord, long timestampNanos) {
        mDevice = device;
        mEventType = eventType;
        mPrimaryPhy = primaryPhy;
        mSecondaryPhy = secondaryPhy;
        mAdvertisingSid = advertisingSid;
        mTxPower = txPower;
        mRssi = rssi;
        mPeriodicAdvertisingInterval = periodicAdvertisingInterval;
        mScanRecord = scanRecord;
        mTimestampNanos = timestampNanos;
    }

    private ScanResult(Parcel in) {
        readFromParcel(in);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (mDevice != null) {
            dest.writeInt(1);
            mDevice.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        if (mScanRecord != null) {
            dest.writeInt(1);
            dest.writeByteArray(mScanRecord.getBytes());
        } else {
            dest.writeInt(0);
        }
        dest.writeInt(mRssi);
        dest.writeLong(mTimestampNanos);
        dest.writeInt(mEventType);
        dest.writeInt(mPrimaryPhy);
        dest.writeInt(mSecondaryPhy);
        dest.writeInt(mAdvertisingSid);
        dest.writeInt(mTxPower);
        dest.writeInt(mPeriodicAdvertisingInterval);
    }

    private void readFromParcel(Parcel in) {
        if (in.readInt() == 1) {
            mDevice = BluetoothDevice.CREATOR.createFromParcel(in);
        }
        if (in.readInt() == 1) {
            mScanRecord = ScanRecord.parseFromBytes(in.createByteArray());
        }
        mRssi = in.readInt();
        mTimestampNanos = in.readLong();
        mEventType = in.readInt();
        mPrimaryPhy = in.readInt();
        mSecondaryPhy = in.readInt();
        mAdvertisingSid = in.readInt();
        mTxPower = in.readInt();
        mPeriodicAdvertisingInterval = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Returns the remote bluetooth device identified by the bluetooth device address.
     */
    public BluetoothDevice getDevice() {
        return mDevice;
    }

    /**
     * Returns the scan record, which is a combination of advertisement and scan response.
     */
    @Nullable
    public ScanRecord getScanRecord() {
        return mScanRecord;
    }

    /**
     * Returns the received signal strength in dBm. The valid range is [-127, 127].
     */
    public int getRssi() {
        return mRssi;
    }

    /**
     * Returns timestamp since boot when the scan record was observed.
     */
    public long getTimestampNanos() {
        return mTimestampNanos;
    }

    /**
     * Returns true if this object represents legacy scan result.
     */
    public boolean isLegacy() {
        return (mEventType & ET_LEGACY_MASK) != 0;
    }

    /**
     * Returns true if this object represents connectable scan result.
     */
    public boolean isConnectable() {
        return (mEventType & ET_CONNECTABLE_MASK) != 0;
    }

    /**
     * Returns the data status. Can be one of {@link ScanResult#DATA_COMPLETE},
     * or {@link ScanResult#DATA_INCOMPLETE_TRUNCATED}.
     */
    public int getDataStatus() {
        // return bit 5 and 6
        return (mEventType >> 5) & 0x03;
    }

    /**
     * Returns the primary Physical Layer on which this advertisment was
     * received. Can be one of {@link ScanResult#PHY_LE_1M} or {@link
     * ScanResult#PHY_LE_CODED}.
     */
    public int getPrimaryPhy() { return mPrimaryPhy; }

    /**
     * Returns the secondary Physical Layer on which this advertisment was
     * received. Can be one of {@link ScanResult#PHY_NO_PACKETS}, {@link
     * ScanResult#PHY_LE_1M}, {@link ScanResult#PHY_LE_2M} or {@link
     * ScanResult#PHY_LE_CODED}
     */
    public int getSecondaryPhy() { return mSecondaryPhy; }

    /**
     * Returns the advertising set id, or {@link ScanResult#SID_NOT_PRESENT} if
     * set id was not present.
     */
    public int getAdvertisingSid() { return mAdvertisingSid; }

    /**
     * Returns the transmit power in dBm. The valid range is [-127, 126]. Value
     * of 127 means information was not available.
     */
    public int getTxPower() { return mTxPower; }

    /**
     * Returns the periodic advertising interval in units of 1.25ms. The valid
     * range is from 6 (7.5ms) to 65536 (81918.75ms). Value of 0 means periodic
     * advertising is not avaliable for this scan result.
     */
    public int getPeriodicAdvertisingInterval() {
        return mPeriodicAdvertisingInterval;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDevice, mRssi, mScanRecord, mTimestampNanos,
                            mEventType, mPrimaryPhy, mSecondaryPhy,
                            mAdvertisingSid, mTxPower,
                            mPeriodicAdvertisingInterval);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ScanResult other = (ScanResult) obj;
        return Objects.equals(mDevice, other.mDevice) && (mRssi == other.mRssi) &&
            Objects.equals(mScanRecord, other.mScanRecord) &&
            (mTimestampNanos == other.mTimestampNanos) &&
            mEventType == other.mEventType &&
            mPrimaryPhy == other.mPrimaryPhy &&
            mSecondaryPhy == other.mSecondaryPhy &&
            mAdvertisingSid == other.mAdvertisingSid &&
            mTxPower == other.mTxPower &&
            mPeriodicAdvertisingInterval == other.mPeriodicAdvertisingInterval;
    }

    @Override
    public String toString() {
      return "ScanResult{" + "mDevice=" + mDevice + ", mScanRecord=" +
          Objects.toString(mScanRecord) + ", mRssi=" + mRssi +
          ", mTimestampNanos=" + mTimestampNanos + ", eventType=" + mEventType +
          ", primaryPhy=" + mPrimaryPhy + ", secondaryPhy=" + mSecondaryPhy +
          ", advertisingSid=" + mAdvertisingSid + ", txPower=" + mTxPower +
          ", mPeriodicAdvertisingInterval=" + mPeriodicAdvertisingInterval + '}';
    }

    public static final Parcelable.Creator<ScanResult> CREATOR = new Creator<ScanResult>() {
            @Override
        public ScanResult createFromParcel(Parcel source) {
            return new ScanResult(source);
        }

            @Override
        public ScanResult[] newArray(int size) {
            return new ScanResult[size];
        }
    };

}
