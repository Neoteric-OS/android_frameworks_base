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

package android.telephony;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.ArrayUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Objects;

/**
 * Defines the threshold value of the signal strength.
 */
public final class SignalThresholdInfo implements Parcelable {
    /**
     * Received Signal Strength Indication.
     * Range: -113 dBm and -51 dBm
     * Used RAN: GERAN, CDMA2000
     * Reference: 3GPP TS 27.007 section 8.5.
     */
    public static final int SIGNAL_RSSI = 1;

    /**
     * Received Signal Code Power.
     * Range: -120 dBm to -25 dBm;
     * Used RAN: UTRAN
     * Reference: 3GPP TS 25.123, section 9.1.1.1
     */
    public static final int SIGNAL_RSCP = 2;

    /**
     * Reference Signal Received Power.
     * Range: -140 dBm to -44 dBm;
     * Used RAN: EUTRAN
     * Reference: 3GPP TS 36.133 9.1.4
     */
    public static final int SIGNAL_RSRP = 3;

    /**
     * Reference Signal Received Quality
     * Range: -34 dB to 3 dB;
     * Used RAN: EUTRAN
     * Reference: 3GPP TS 36.133 9.1.7
     */
    public static final int SIGNAL_RSRQ = 4;

    /**
     * Reference Signal Signal to Noise Ratio
     * Range: -20 dB to 30 dB;
     * Used RAN: EUTRAN
     */
    public static final int SIGNAL_RSSNR = 5;

    /**
     * 5G SS reference signal received power.
     * Range: -140 dBm to -44 dBm.
     * Used RAN: NGRAN
     * Reference: 3GPP TS 38.215.
     */
    public static final int SIGNAL_SSRSRP = 6;

    /**
     * 5G SS reference signal received quality.
     * Range: -43 dB to 20 dB.
     * Used RAN: NGRAN
     * Reference: 3GPP TS 38.133 section 10.1.11.1.
     */
    public static final int SIGNAL_SSRSRQ = 7;

    /**
     * 5G SS signal-to-noise and interference ratio.
     * Range: -23 dB to 40 dB
     * Used RAN: NGRAN
     * Reference: 3GPP TS 38.215 section 5.1.*, 3GPP TS 38.133 section 10.1.16.1.
     */
    public static final int SIGNAL_SSSINR = 8;

    /** @hide */
    @IntDef(prefix = { "SIGNAL_" }, value = {
        SIGNAL_RSSI,
        SIGNAL_RSCP,
        SIGNAL_RSRP,
        SIGNAL_RSRQ,
        SIGNAL_RSSNR,
        SIGNAL_SSRSRP,
        SIGNAL_SSRSRQ,
        SIGNAL_SSSINR
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SignalMeasurementType {}

    @SignalMeasurementType
    private int mSignalMeasurement;

    /**
     * A hysteresis time in milliseconds to prevent flapping.
     * A value of 0 disables hysteresis
     */
    private int mHysteresisMs;

    /**
     * An interval in dB defining the required magnitude change between reports.
     * hysteresisDb must be smaller than the smallest threshold delta.
     * An interval value of 0 disables hysteresis.
     */
    private int mHysteresisDb;

    /**
     * List of threshold values.
     * Range and unit must reference specific SignalMeasurementType
     * The threshold values for which to apply criteria.
     * A vector size of 0 disables the use of thresholds for reporting.
     */
    private int[] mThresholds = null;

    /**
     * {@code true} means modem must trigger the report based on the criteria;
     * {@code false} means modem must not trigger the report based on the criteria.
     */
    private boolean mIsEnabled = true;

    /**
     * Indicates the hysteresisMs is disabled.
     *
     * @hide
     */
    public static final int HYSTERESIS_MS_DISABLED = 0;

    /**
     * Default value when set by application, indicating the hysteresisMs is not used.
     *
     * @hide
     */
    public static final int HYSTERESIS_MS_UNUSED = -1;

    /**
     * Indicates the hysteresisDb is disabled.
     *
     * @hide
     */
    public static final int HYSTERESIS_DB_DISABLED = 0;

    /**
     * Default value when set by application, indicating the hysteresisDb is not used.
     *
     * @hide
     */
    public static final int HYSTERESIS_DB_UNUSED = -1;

    /**
     * Default value when set by application. The default false value indicates the isEnabled is not
     * used, instead of asking system to disable the thresholds.
     *
     * @hide
     */
    public static final boolean IS_ENABLED_UNUSED = false;


    /**
     * Minimum valid value for {@link #SIGNAL_RSSI}.
     *
     * @hide
     */
    public static final int SIGNAL_RSSI_MIN_VALUE = -113;

    /**
     * Maximum valid value for {@link #SIGNAL_RSSI}.
     *
     * @hide
     */
    public static final int SIGNAL_RSSI_MAX_VALUE = -51;

    /**
     * Minimum valid value for {@link #SIGNAL_RSCP}.
     *
     * @hide
     */
    public static final int SIGNAL_RSCP_MIN_VALUE = -120;

    /**
     * Maximum valid value for {@link #SIGNAL_RSCP}.
     *
     * @hide
     */
    public static final int SIGNAL_RSCP_MAX_VALUE = -25;

    /**
     * Minimum valid value for {@link #SIGNAL_RSRP}.
     *
     * @hide
     */
    public static final int SIGNAL_RSRP_MIN_VALUE = -140;

    /**
     * Maximum valid value for {@link #SIGNAL_RSRP}.
     *
     * @hide
     */
    public static final int SIGNAL_RSRP_MAX_VALUE = -44;

    /**
     * Minimum valid value for {@link #SIGNAL_RSRQ}.
     *
     * @hide
     */
    public static final int SIGNAL_RSRQ_MIN_VALUE = -34;

    /**
     * Maximum valid value for {@link #SIGNAL_RSRQ}.
     *
     * @hide
     */
    public static final int SIGNAL_RSRQ_MAX_VALUE = 3;

    /**
     * Minimum valid value for {@link #SIGNAL_RSSNR}.
     *
     * @hide
     */
    public static final int SIGNAL_RSSNR_MIN_VALUE = -20;

    /**
     * Maximum valid value for {@link #SIGNAL_RSSNR}.
     *
     * @hide
     */
    public static final int SIGNAL_RSSNR_MAX_VALUE = 30;

    /**
     * Minimum valid value for {@link #SIGNAL_SSRSRP}.
     *
     * @hide
     */
    public static final int SIGNAL_SSRSRP_MIN_VALUE = -140;

    /**
     * Maximum valid value for {@link #SIGNAL_SSRSRP}.
     *
     * @hide
     */
    public static final int SIGNAL_SSRSRP_MAX_VALUE = -44;

    /**
     * Minimum valid value for {@link #SIGNAL_SSRSRQ}.
     *
     * @hide
     */
    public static final int SIGNAL_SSRSRQ_MIN_VALUE = -43;

    /**
     * Maximum valid value for {@link #SIGNAL_SSRSRQ}.
     *
     * @hide
     */
    public static final int SIGNAL_SSRSRQ_MAX_VALUE = 20;

    /**
     * Minimum valid value for {@link #SIGNAL_SSSINR}.
     *
     * @hide
     */
    public static final int SIGNAL_SSSINR_MIN_VALUE = -23;

    /**
     * Maximum valid value for {@link #SIGNAL_SSSINR}.
     *
     * @hide
     */
    public static final int SIGNAL_SSSINR_MAX_VALUE = 40;


    /**
     * Constructor
     *
     * @param signalMeasurement Signal Measurement Type
     * @param hysteresisMs hysteresisMs
     * @param hysteresisDb hysteresisDb
     * @param thresholds threshold value
     * @param isEnabled isEnabled
     *
     * @hide
     */
    public SignalThresholdInfo(@SignalMeasurementType int signalMeasurement,
            int hysteresisMs, int hysteresisDb, @NonNull int [] thresholds, boolean isEnabled) {
        mSignalMeasurement = signalMeasurement;
        mHysteresisMs = hysteresisMs < 0 ? HYSTERESIS_MS_DISABLED : hysteresisMs;
        mHysteresisDb = hysteresisDb < 0 ? HYSTERESIS_DB_DISABLED : hysteresisDb;
        mThresholds = thresholds == null ? null : thresholds.clone();
        mIsEnabled = isEnabled;
    }

    /**
     * Construct SignalThresholdInfo from signal measurement type and the corresponding thresholds.
     *
     * @param signalMeasurement signal measurement type defines in {@link SignalMeasurementType}
     * @param thresholds threshold values of the corresponding signal measurement type. Range and
     *                  unit must reference specific SignalMeasurementType.
     *
     * @throws IllegalArgumentException if the signal measurement type is invalid, the thresholds
     * array is null or empty, or any value in the thresholds is out of range.
     *
     * @see {@link SignalMeasurementType} for the valid range.
     */
    public SignalThresholdInfo(@SignalMeasurementType int signalMeasurement,
            @NonNull int[] thresholds) {
        // Applications are not allowed to set empty thresholds which is used by system to disable
        // the use of thresholds for reporting.
        if (ArrayUtils.isEmpty(thresholds)) {
            throw new IllegalArgumentException("thresholds array is null or empty");
        }

        for (int threshold : thresholds) {
            if (!isValidThreshold(signalMeasurement, threshold)) {
                throw new IllegalArgumentException(
                        "invalid signal measurement type or thresholds");
            }
        }

        mSignalMeasurement = signalMeasurement;
        mHysteresisMs = HYSTERESIS_MS_UNUSED;
        mHysteresisDb = HYSTERESIS_DB_UNUSED;
        mThresholds = thresholds.clone();
        mIsEnabled = IS_ENABLED_UNUSED;
    }

    /**
     * Get the signal measurement type.
     *
     * @return the {@link SignalMeasurementType}.
     */
    public @SignalMeasurementType int getSignalMeasurement() {
        return mSignalMeasurement;
    }

    /** @hide */
    public int getHysteresisMs() {
        return mHysteresisMs;
    }

    /** @hide */
    public int getHysteresisDb() {
        return mHysteresisDb;
    }

    /** @hide */
    public boolean isEnabled() {
        return mIsEnabled;
    }

    /**
     * Get the signal threshold values.
     *
     * @return array of integer of the signal thresholds
     */
    @Nullable
    public int[] getThresholds() {
        return mThresholds == null ? null : mThresholds.clone();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeInt(mSignalMeasurement);
        out.writeInt(mHysteresisMs);
        out.writeInt(mHysteresisDb);
        out.writeIntArray(mThresholds);
        out.writeBoolean(mIsEnabled);
    }

    private SignalThresholdInfo(Parcel in) {
        mSignalMeasurement = in.readInt();
        mHysteresisMs = in.readInt();
        mHysteresisDb = in.readInt();
        mThresholds = in.createIntArray();
        mIsEnabled = in.readBoolean();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof SignalThresholdInfo)) {
            return false;
        }

        SignalThresholdInfo other = (SignalThresholdInfo) o;
        return mSignalMeasurement == other.mSignalMeasurement
                && mHysteresisMs == other.mHysteresisMs
                && mHysteresisDb == other.mHysteresisDb
                && Arrays.equals(mThresholds, other.mThresholds)
                && mIsEnabled == other.mIsEnabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mSignalMeasurement, mHysteresisMs, mHysteresisDb, mThresholds, mIsEnabled);
    }

    public static final @NonNull Parcelable.Creator<SignalThresholdInfo> CREATOR =
            new Parcelable.Creator<SignalThresholdInfo>() {
                @Override
                public SignalThresholdInfo createFromParcel(Parcel in) {
                    return new SignalThresholdInfo(in);
                }

                @Override
                public SignalThresholdInfo[] newArray(int size) {
                    return new SignalThresholdInfo[size];
                }
            };

    @Override
    public String toString() {
        return new StringBuilder("SignalThresholdInfo{")
            .append("mSignalMeasurement=").append(mSignalMeasurement)
            .append("mHysteresisMs=").append(mSignalMeasurement)
            .append("mHysteresisDb=").append(mHysteresisDb)
            .append("mThresholds=").append(Arrays.toString(mThresholds))
            .append("mIsEnabled=").append(mIsEnabled)
            .append("}").toString();
    }

    /**
     * Return true if signal measurement type is valid and the threshold value is in range.
     */
    private boolean isValidThreshold(@SignalMeasurementType int type, int threshold) {
        switch (type) {
            case SIGNAL_RSSI:
                return threshold >= SIGNAL_RSSI_MIN_VALUE && threshold <= SIGNAL_RSSI_MAX_VALUE;
            case SIGNAL_RSCP:
                return threshold >= SIGNAL_RSCP_MIN_VALUE && threshold <= SIGNAL_RSCP_MAX_VALUE;
            case SIGNAL_RSRP:
                return threshold >= SIGNAL_RSRP_MIN_VALUE && threshold <= SIGNAL_RSRP_MAX_VALUE;
            case SIGNAL_RSRQ:
                return threshold >= SIGNAL_RSRQ_MIN_VALUE && threshold <= SIGNAL_RSRQ_MAX_VALUE;
            case SIGNAL_RSSNR:
                return threshold >= SIGNAL_RSSNR_MIN_VALUE && threshold <= SIGNAL_RSSNR_MAX_VALUE;
            case SIGNAL_SSRSRP:
                return threshold >= SIGNAL_SSRSRP_MIN_VALUE && threshold <= SIGNAL_SSRSRP_MAX_VALUE;
            case SIGNAL_SSRSRQ:
                return threshold >= SIGNAL_SSRSRQ_MIN_VALUE && threshold <= SIGNAL_SSRSRQ_MAX_VALUE;
            case SIGNAL_SSSINR:
                return threshold >= SIGNAL_SSSINR_MIN_VALUE && threshold <= SIGNAL_SSSINR_MAX_VALUE;
            default:
                return false;
        }
    }
}
