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
         * @breif Returns bins count to be used by counter histogram
         * Does not include overflow & underflow bins
         * @hide
         */
        int getBinsCount();

        /**
         * @breif Calculates bin index for the input sample value
         * Returns zero based index - where zero stands for underflow
         * @hide
         */
        int getBinForSample(float sample);

        /**
         * @breif Calculates bin index for the input sample value
         * Returns zero based index - where zero stands for underflow
         * @hide
         */
        int getBinForSample(int sample);
    }

    /** Used by CounterHistogram to map data sample to corresponding bin for on fixed range bins */
    public static final class FixedRangeOptions implements BinOptions {

        private int mBinCount;
        private int mMinValue;
        private int mMaxValue;
        private int mBinSize;

        private float mMinValueFloat;
        private float mMaxValueFloat;
        private float mBinSizeFloat;

        public FixedRangeOptions(int binCount, int minValue, int maxValue) {
            mBinCount = binCount;
            mMinValue = minValue;
            mMaxValue = maxValue;

            //TODO: add runtime checks to enforce basic rules

            mBinSize = (maxValue - minValue) / binCount;

            //TODO: precalculate bins ranges including underflow/overflow support
        }

        public FixedRangeOptions(int binCount, float minValue, float maxValue) {
            mBinCount = binCount;
            mMinValueFloat = minValue;
            mMaxValueFloat = maxValue;

            //TODO: add runtime checks to enforce basic rules

            mBinSizeFloat = (mMaxValueFloat - mMinValueFloat) / binCount;

            //TODO: precalculate bins ranges including underflow/overflow support
        }

        @Override
        public int getBinsCount() {
            return mBinCount;
        }

        @Override
        public int getBinForSample(float sample) {
            if (sample < mMinValueFloat) {
                return 0;
            } else if (sample > mMaxValueFloat) {
                return mBinCount + 1;
            }
            return (int) ((sample + mBinSizeFloat) / mBinSizeFloat);
        }

        @Override
        public int getBinForSample(int sample) {
            if (sample < mMinValue) {
                return 0;
            } else if (sample > mMaxValue) {
                return mBinCount + 1;
            }
            return sample / mBinSize + 1;
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
     *
     * @hide
     */
    public void logSample(int sample) {
        final int binIndex = mBinOptions.getBinForSample(sample);
        FrameworkStatsLog.write(FrameworkStatsLog.EXPRESS_HISTOGRAM_INT_SAMPLE_REPORTED,
                metricIdHash, 1, binIndex);
    }

    /**
     * Increments sample count for automatically calculated bin
     *
     * @hide
     */
    public void logSample(float sample) {
        final int binIndex = mBinOptions.getBinForSample(sample);
        FrameworkStatsLog.write(FrameworkStatsLog.EXPRESS_HISTOGRAM_FLOAT_SAMPLE_REPORTED,
                metricIdHash, 1, binIndex);
    }
}
