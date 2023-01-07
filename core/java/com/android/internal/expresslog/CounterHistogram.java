/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.internal.expresslog;

import android.annotation.NonNull;

import com.android.internal.util.FrameworkStatsLog;

/** CounterHistogram encapsulates StatsD write API calls */
public final class CounterHistogram {

    /** Used by CounterHistogram to map data sample to corresponding bin */
    public interface BinOptions {
        /**
         * @brief Returns bins count to be used by counter histogram
         * Does not include overflow & underflow bins
         * @hide
         */
        int getBinsCount();

        /**
         * @brief Calculates bin index for the input sample value
         * Returns zero based index - where zero stands for underflow
         * @hide
         */
        int getBinForSample(float sample);
    }

    /** Used by CounterHistogram to map data sample to corresponding bin for on fixed range bins */
    public static final class FixedRangeOptions implements BinOptions {

        private final int mBinCount;
        private final float mMinValue;
        private final float mMaxValue;
        private final float mBinSize;

        public FixedRangeOptions(int binCount, float minValue, float maxValue) {
            mBinCount = binCount;
            mMinValue = minValue;
            mMaxValue = maxValue;

            //TODO: add runtime checks to enforce basic rules

            mBinSize = (maxValue - minValue) / binCount;

            //TODO: precalculate bins ranges including underflow/overflow support
        }

        @Override
        public int getBinsCount() {
            // we include underflow & overflow bins
            return mBinCount + 2;
        }

        @Override
        public int getBinForSample(float sample) {
            if (sample < mMinValue) {
                // goes to underflow
                return 0;
            } else if (sample > mMaxValue) {
                // goes to overflow
                return mBinCount + 1;
            }
            return (int) (sample / mBinSize) + 1;
        }
    }

    private final long mMetricIdHash;

    private final BinOptions mBinOptions;

    public CounterHistogram(@NonNull String metricId, @NonNull BinOptions binOptions) {
        mMetricIdHash = Utils.hashString(metricId);
        mBinOptions = binOptions;
    }

    /**
     * Increments sample count for automatically calculated bin
     * @hide
     */
    public void logSample(float sample) {
        final int binIndex = mBinOptions.getBinForSample(sample);
        FrameworkStatsLog.write(FrameworkStatsLog.EXPRESS_EVENT_REPORTED,
                mMetricIdHash, 1, binIndex);
    }
}
