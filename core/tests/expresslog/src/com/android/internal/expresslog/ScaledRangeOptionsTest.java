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

import static org.junit.Assert.assertEquals;

import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ScaledRangeOptionsTest {
    private static final String TAG = ScaledRangeOptionsTest.class.getSimpleName();

    @Test
    @SmallTest
    public void testGetBinsCount() {
        Histogram.ScaledRangeOptions options1 = new Histogram.ScaledRangeOptions(1, 100, 100, 2);
        assertEquals(3, options1.getBinsCount());

        Histogram.ScaledRangeOptions options10 = new Histogram.ScaledRangeOptions(10, 100, 100, 2);
        assertEquals(12, options10.getBinsCount());
    }

    @Test(expected = IllegalArgumentException.class)
    @SmallTest
    public void testConstructZeroBinsCount() {
        new Histogram.ScaledRangeOptions(0, 100, 100, 2);
    }

    @Test(expected = IllegalArgumentException.class)
    @SmallTest
    public void testConstructNegativeBinsCount() {
        new Histogram.ScaledRangeOptions(-1, 100, 100, 2);
    }

    @Test(expected = IllegalArgumentException.class)
    @SmallTest
    public void testConstructNegativeFirstBinWidth() {
        new Histogram.ScaledRangeOptions(10, 100, -100, 2);
    }

    @Test(expected = IllegalArgumentException.class)
    @SmallTest
    public void testConstructNegativeScaleFactor() {
        new Histogram.ScaledRangeOptions(10, 100, 100, -2);
    }

    @Test
    @SmallTest
    public void testBinIndexForRangeEqual1() {
        Histogram.ScaledRangeOptions options = new Histogram.ScaledRangeOptions(10, 1, 1, 1);
        for (int i = 0, bins = options.getBinsCount(); i < bins; i++) {
            assertEquals(i, options.getBinForSample(i));
        }
    }

    @Test
    @SmallTest
    public void testBinIndexForRangeEqual2() {
        Histogram.ScaledRangeOptions options = new Histogram.ScaledRangeOptions(10, 1, 2, 1);
        for (int i = 0, bins = options.getBinsCount(); i < bins; i++) {
            assertEquals(i, options.getBinForSample(i * 2));
            assertEquals(i, options.getBinForSample(i * 2 - 1));
        }
    }

    @Test
    @SmallTest
    public void testBinIndexForRangeEqual5() {
        Histogram.ScaledRangeOptions options = new Histogram.ScaledRangeOptions(2, 0, 5, 1);
        assertEquals(4, options.getBinsCount());
        for (int i = 0; i < 2; i++) {
            for (int sample = 0; sample < 5; sample++) {
                assertEquals(i + 1, options.getBinForSample(i * 5 + sample));
            }
        }
    }

    @Test
    @SmallTest
    public void testBinIndexForRangeEqual10() {
        Histogram.ScaledRangeOptions options = new Histogram.ScaledRangeOptions(10, 1, 10, 1);
        assertEquals(0, options.getBinForSample(0));
        assertEquals(options.getBinsCount() - 2, options.getBinForSample(100));
        assertEquals(options.getBinsCount() - 1, options.getBinForSample(101));

        final float binSize = (101 - 1) / 10f;
        for (int i = 1, bins = options.getBinsCount() - 1; i < bins; i++) {
            assertEquals(i, options.getBinForSample(i * binSize));
        }
    }
}
