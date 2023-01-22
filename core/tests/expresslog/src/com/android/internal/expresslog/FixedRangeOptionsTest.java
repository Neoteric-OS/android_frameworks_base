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

import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FixedRangeOptionsTest {
    private static final String TAG = FixedRangeOptionsTest.class.getSimpleName();
    @Test
    @SmallTest
    public void testGetBinsCount() {
        CounterHistogram.FixedRangeOptions options1 = new CounterHistogram.FixedRangeOptions(1, 100,
                1000);
        Assert.assertEquals(3, options1.getBinsCount());

        CounterHistogram.FixedRangeOptions options10 = new CounterHistogram.FixedRangeOptions(10,
                100, 1000);
        Assert.assertEquals(12, options10.getBinsCount());
    }

    @Test(expected = IllegalArgumentException.class)
    @SmallTest
    public void testConstructZeroBinsCount() {
        CounterHistogram.FixedRangeOptions options = new CounterHistogram.FixedRangeOptions(0, 100,
                1000);
    }

    @Test(expected = IllegalArgumentException.class)
    @SmallTest
    public void testConstructNegativeBinsCount() {
        CounterHistogram.FixedRangeOptions options = new CounterHistogram.FixedRangeOptions(-1, 100,
                1000);
    }

    @Test(expected = IllegalArgumentException.class)
    @SmallTest
    public void testConstructWrongRange() {
        CounterHistogram.FixedRangeOptions options = new CounterHistogram.FixedRangeOptions(10,
                1000, 100);
    }

    @SmallTest
    public void testBinIndexForRangeEqual1() {
        CounterHistogram.FixedRangeOptions options = new CounterHistogram.FixedRangeOptions(10, 1,
                10);
        for (int i = 0, bins = options.getBinsCount(); i < bins; i++) {
            Assert.assertEquals(i, options.getBinForSample(i));
        }
    }

    @SmallTest
    public void testBinIndexForRangeEqual2() {
        CounterHistogram.FixedRangeOptions options = new CounterHistogram.FixedRangeOptions(10, 1,
                20);
        for (int i = 0, bins = options.getBinsCount(); i < bins; i++) {
            Assert.assertEquals(i, options.getBinForSample(i));
        }
    }

    @SmallTest
    public void testBinIndexForRangeEqual10() {
        CounterHistogram.FixedRangeOptions options = new CounterHistogram.FixedRangeOptions(10, 1,
                100);
        Assert.assertEquals(0, options.getBinForSample(0));
        Assert.assertEquals(options.getBinsCount() - 1, options.getBinForSample(100));

        final float binSize = (100 - 1) / 10;

        for (int i = 1, bins = options.getBinsCount() - 1; i < bins; i++) {
            Assert.assertEquals((int) (1 + i * binSize), options.getBinForSample(i * binSize));
        }
    }

    @SmallTest
    public void testBinIndexForRangeEqual90() {
        final int binCount = 10;
        final int minValue = 100;
        final int maxValue = 100000;

        CounterHistogram.FixedRangeOptions options = new CounterHistogram.FixedRangeOptions(
                binCount, minValue, maxValue);

        // logging underflow sample
        Assert.assertEquals(0, options.getBinForSample(minValue - 1));

        // logging overflow sample
        Assert.assertEquals(binCount + 1, options.getBinForSample(maxValue));
        Assert.assertEquals(binCount + 1, options.getBinForSample(maxValue + 1));

        // logging min edge sample
        Assert.assertEquals(1, options.getBinForSample(minValue));

        // logging max edge sample
        Assert.assertEquals(binCount, options.getBinForSample(maxValue - 1));

        // logging single valid sample per bin
        final int binSize = (maxValue - minValue) / binCount;

        for (int i = 0; i < binCount; i++) {
            Assert.assertEquals(binCount + 1, options.getBinForSample(minValue + binSize * i));
        }
    }

}
