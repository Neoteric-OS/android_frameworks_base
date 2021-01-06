/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Request used to register {@link SignalThresholdInfo} to be notified when the signal strength
 * breach the specified thresholds.
 */
public final class SignalStrengthUpdateRequest implements Parcelable {

    /**
     * The minimum number of thresholds allowed in the request for each SignalThresholdInfo.
     *
     * @see SignalThresholdInfo#getThresholds()
     */
    public static final int MINIMUM_NUMBER_OF_THRESHOLDS = 1;

    /**
     * The maximum number of thresholds allowed in the request for each SignalThresholdInfo.
     *
     * @see SignalThresholdInfo#getThresholds()
     */
    public static final int MAXIMUM_NUMBER_OF_THRESHOLDS = 4;

    /**
     * List of SignalThresholdInfo for the request.
     */
    private final ArrayList<SignalThresholdInfo> mSignalThresholdInfos;

    /**
     * Whether the reporting is required while device is idle.
     */
    private final boolean mIsReportingRequestedWhileIdle;

    /**
     * Whether the system thresholds should be honored when screen is off.
     *
     * System signal thresholds are loaded from carrier config items and mainly used for UI
     * displaying. By default, they are ignored when screen is off. When setting the value to true,
     * modem will continue reporting signal strength changes over the system signal thresholds even
     * screen is off.
     *
     * This should only set to true by the system caller.
     */
    private final boolean mShouldHonorSystemThresholdsWhenScreenOff;

    /** @hide */
    public SignalStrengthUpdateRequest(
            @NonNull Collection<SignalThresholdInfo> signalThresholdInfos,
            boolean isReportingRequestedWhileIdle,
            boolean shouldHonorSystemThresholdsWhenScreenOff) {
        validate(signalThresholdInfos);

        mSignalThresholdInfos = new ArrayList<>(signalThresholdInfos);
        // Sort the collection with RAN ascending order, make the ordering not matter for equals
        mSignalThresholdInfos.sort(
                Comparator.comparingInt(SignalThresholdInfo::getRadioAccessNetworkType));
        mIsReportingRequestedWhileIdle = isReportingRequestedWhileIdle;
        mShouldHonorSystemThresholdsWhenScreenOff = shouldHonorSystemThresholdsWhenScreenOff;
    }

    /**
     * Construct request from a collection of {@link SignalThresholdInfo}.
     *
     * Note that the collection should not be empty size. Each radio access network type in the
     * collection should be unique. In each SignalThresholdInfo, the length of thresholds should
     * between {@link #MINIMUM_NUMBER_OF_THRESHOLDS} and {@link #MAXIMUM_NUMBER_OF_THRESHOLDS}. An
     * IllegalArgumentException will throw otherwise.
     *
     * isReportingRequestedWhileIdle should be set to true only when really needed to have minimum
     * battery consumption. The flag will not impact system signal thresholds which is mainly used
     * for UI displaying.
     *
     * @param signalThresholdInfos a collection of SignalThresholdInfo which specifies the
     *                             radio access network type, SignalMeasurementType and
     *                             corresponding thresholds.
     * @param isReportingRequestedWhileIdle whether the request reporting while device is idle.
     */
    public SignalStrengthUpdateRequest(
            @NonNull Collection<SignalThresholdInfo> signalThresholdInfos,
            boolean isReportingRequestedWhileIdle) {
        this(signalThresholdInfos, isReportingRequestedWhileIdle,
                false /* mShouldHonorSystemThresholdsWhenScreenOff */);
    }

    private SignalStrengthUpdateRequest(Parcel in) {
        mSignalThresholdInfos = in.createTypedArrayList(SignalThresholdInfo.CREATOR);
        // Sort the collection with RAN ascending order
        mSignalThresholdInfos.sort(
                Comparator.comparingInt(SignalThresholdInfo::getRadioAccessNetworkType));
        mIsReportingRequestedWhileIdle = in.readBoolean();
        mShouldHonorSystemThresholdsWhenScreenOff = in.readBoolean();
    }

    /**
     * Get the collection of SignalThresholdInfo in the request.
     *
     * @return the collection of SignalThresholdInfo.
     */
    @NonNull
    public Collection<SignalThresholdInfo> getSignalThresholdInfos() {
        return mSignalThresholdInfos;
    }

    /**
     * Get the flag whether reporting is required while device is idle.
     *
     * @return true if the request requires the reporting while device is idle.
     */
    public boolean isReportingRequestedWhileIdle() {
        return mIsReportingRequestedWhileIdle;
    }

    /**
     * @return if system thresholds should be honored when screen is off.
     *
     * @hide
     */
    public boolean getShouldHonorSystemThresholdsWhenScreenOff() {
        return mShouldHonorSystemThresholdsWhenScreenOff;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeTypedList(mSignalThresholdInfos);
        dest.writeBoolean(mIsReportingRequestedWhileIdle);
        dest.writeBoolean(mShouldHonorSystemThresholdsWhenScreenOff);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;

        if (!(other instanceof SignalStrengthUpdateRequest)) {
            return false;
        }

        SignalStrengthUpdateRequest request = (SignalStrengthUpdateRequest) other;
        return request.mSignalThresholdInfos.equals(mSignalThresholdInfos)
                && request.mIsReportingRequestedWhileIdle == mIsReportingRequestedWhileIdle
                && request.mShouldHonorSystemThresholdsWhenScreenOff
                == mShouldHonorSystemThresholdsWhenScreenOff;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSignalThresholdInfos, mIsReportingRequestedWhileIdle,
                mShouldHonorSystemThresholdsWhenScreenOff);
    }

    public static final @NonNull Parcelable.Creator<SignalStrengthUpdateRequest> CREATOR =
            new Parcelable.Creator<SignalStrengthUpdateRequest>() {
                @Override
                public SignalStrengthUpdateRequest createFromParcel(Parcel source) {
                    return new SignalStrengthUpdateRequest(source);
                }

                @Override
                public SignalStrengthUpdateRequest[] newArray(int size) {
                    return new SignalStrengthUpdateRequest[size];
                }
            };

    @Override
    public String toString() {
        return new StringBuilder("SignalStrengthUpdateRequest{")
                .append("mSignalThresholdInfos=")
                .append(mSignalThresholdInfos)
                .append(" mIsReportingRequestedWhileIdle=")
                .append(mIsReportingRequestedWhileIdle)
                .append(" mShouldHonorSystemThresholdsWhenScreenOff=")
                .append(mShouldHonorSystemThresholdsWhenScreenOff)
                .append("}").toString();
    }

    /**
     * Throw IAE when one of the conditions met:
     * 1. The collection is empty
     * 2. The RAN in the collection is not unique
     * 3. The thresholds array includes too little or too many values
     */
    private static void validate(Collection<SignalThresholdInfo> infos) {
        if (infos == null || infos.isEmpty()) {
            throw new IllegalArgumentException("SignalThresholdInfo is empty");
        }

        Set<Integer> uniqueRan = new HashSet<>(infos.size());
        for (SignalThresholdInfo info : infos) {
            final int ran = info.getRadioAccessNetworkType();
            if (!uniqueRan.add(ran)) {
                throw new IllegalArgumentException("RAN: " + ran + " is not unique");
            }

            int[] thresholds = info.getThresholds();
            if (ArrayUtils.isEmpty(thresholds)
                    || thresholds.length > MAXIMUM_NUMBER_OF_THRESHOLDS) {
                throw new IllegalArgumentException(
                        "thresholds length is out of range: " + thresholds.length);
            }
        }
    }
}
