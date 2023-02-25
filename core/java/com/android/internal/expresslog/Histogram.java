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

import java.util.Arrays;

/** Histogram encapsulates StatsD write API calls */
public final class Histogram {

    private final long mMetricIdHash;
    private final BinOptions mBinOptions;

    /**
     * Creates Histogram metric logging wrapper
     *
     * @param metricId   to log, logging will be no-op if metricId is not defined in the TeX catalog
     * @param binOptions to calculate bin index for samples
     * @hide
     */
    public Histogram(@NonNull String metricId, @NonNull BinOptions binOptions) {
        mMetricIdHash = Utils.hashString(metricId);
        mBinOptions = binOptions;
    }

    /**
     * Logs increment sample count for automatically calculated bin
     *
     * @param sample value
     * @hide
     */
    public void logSample(float sample) {
        final int binIndex = mBinOptions.getBinForSample(sample);
        FrameworkStatsLog.write(FrameworkStatsLog.EXPRESS_HISTOGRAM_SAMPLE_REPORTED, mMetricIdHash,
                /*count*/ 1, binIndex);
    }

    /** Used by Histogram to map data sample to corresponding bin */
    public interface BinOptions {
        /**
         * Returns bins count to be used by a histogram
         *
         * @return bins count used to initialize Options, including overflow & underflow bins
         * @hide
         */
        int getBinsCount();

        /**
         * Returns bin index for the input sample value
         * index == 0 stands for underflow
         * index == getBinsCount() - 1 stands for overflow
         *
         * @return zero based index
         * @hide
         */
        int getBinForSample(float sample);
    }

    /** Used by Histogram to map data sample to corresponding bin for uniform bins */
    public static final class UniformOptions implements BinOptions {

        private final int mBinCount;
        private final float mMinValue;
        private final float mExclusiveMaxValue;
        private final float mBinSize;

        /**
         * Creates options for uniform (linear) sized bins
         *
         * @param binCount          amount of histogram bins. 2 bin indexes will be calculated
         *                          automatically to represent underflow & overflow bins
         * @param minValue          is included in the first bin, values less than minValue
         *                          go to underflow bin
         * @param exclusiveMaxValue is included in the overflow bucket. For accurate
         *                          measure up to kMax, then exclusiveMaxValue
         *                          should be set to kMax + 1
         * @hide
         */
        public UniformOptions(int binCount, float minValue, float exclusiveMaxValue) {
            if (binCount < 1) {
                throw new IllegalArgumentException("Bin count should be positive number");
            }

            if (exclusiveMaxValue <= minValue) {
                throw new IllegalArgumentException("Bins range invalid (maxValue < minValue)");
            }

            mMinValue = minValue;
            mExclusiveMaxValue = exclusiveMaxValue;
            mBinSize = (mExclusiveMaxValue - minValue) / binCount;

            // Implicitly add 2 for the extra underflow & overflow bins
            mBinCount = binCount + 2;
        }

        @Override
        public int getBinsCount() {
            return mBinCount;
        }

        @Override
        public int getBinForSample(float sample) {
            if (sample < mMinValue) {
                // goes to underflow
                return 0;
            } else if (sample >= mExclusiveMaxValue) {
                // goes to overflow
                return mBinCount - 1;
            }
            return (int) ((sample - mMinValue) / mBinSize + 1);
        }
    }

    /** Used by Histogram to map data sample to corresponding bin for scaled bins */
    public static final class ScaledRangeOptions implements BinOptions {

        private final int mBinCount;
        private final float mMinValue;
        private final float mFirstBinWidth;
        private final float mScaleFactor;

        // store minimum value per bin
        private final float[] mBins;
        private final int mOverflowBinIndex;

        /**
         * Creates options for scaled range bins
         *
         * @param binCount      amount of histogram bins. 2 bin indexes will be calculated
         *                      automatically to represent underflow & overflow bins
         * @param minValue      is included in the first bin, values less than minValue
         *                      go to underflow bin
         * @param firstBinWidth used to represent first bin width and as a reference to calculate
         *                      width for consecutive bins
         * @param scaleFactor   used to calculate width for consecutive bins
         * @hide
         */
        public ScaledRangeOptions(int binCount, float minValue, float firstBinWidth,
                float scaleFactor) {
            if (binCount < 1) {
                throw new IllegalArgumentException("Bin count should be positive number");
            }

            if (firstBinWidth <= 0.f) {
                throw new IllegalArgumentException(
                        "First bin width invalid (should be positive value)");
            }

            if (scaleFactor <= 0.f) {
                throw new IllegalArgumentException(
                        "Scaled factor invalid (should be positive value)");
            }

            mMinValue = minValue;
            mFirstBinWidth = firstBinWidth;

            // precalculating bins ranges
            mBins = initBins(binCount + 1, minValue, firstBinWidth, scaleFactor);

            mOverflowBinIndex = binCount;

            // Implicitly add 2 for the extra underflow & overflow bins
            mBinCount = binCount + 2;
        }

        @Override
        public int getBinsCount() {
            return mBinCount;
        }

        @Override
        public int getBinForSample(float sample) {
            if (sample < mMinValue) {
                // goes to underflow
                return 0;
            } else if (sample >= bins[mOverflowBinIndex]) {
                // goes to overflow
                return mBinCount - 1;
            }

            return lower_bound(mBins, sample);
        }

        // To find lower bound using binary search implementation of Arrays utility class
        private static int lower_bound(float[] array, float sample) {
            int index = Arrays.binarySearch(array, sample);
            // If key is not present in the array
            if (index < 0) {
                // Index specify the position of the key when inserted in the sorted array
                // so the element currently present at this position will be the lower bound
                return Math.abs(index) - 1;
            }
            return index;
        }

        private static float[] initBins(int count, float minValue, float firstBinWidth,
                float scaleFactor) {
            float[] bins = new float[count];
            bins[0] = minValue;
            bins[1] = bins[0] + firstBinWidth;
            for (int i = 2; i < count; i++) {
                // previous bin width * scaleFactor
                bins[i] = bins[i - 1] + (bins[i - 1] - bins[i - 2]) * scaleFactor;
            }
            return bins;
        }
    }
}
