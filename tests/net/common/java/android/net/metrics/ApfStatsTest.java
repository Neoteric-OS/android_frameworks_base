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

import static org.junit.Assert.assertEquals;

import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class ApfStatsTest {
    @Test
    public void testBuilder() {
        final ApfStats stats = new ApfStats.Builder()
                .setDurationMs(Long.MAX_VALUE)
                .setReceivedRas(10)
                .setMatchingRas(5)
                .setDroppedRas(3)
                .setZeroLifetimeRas(1)
                .setParseErrors(1)
                .setProgramUpdates(1)
                .setProgramUpdatesAll(1)
                .setProgramUpdatesAllowingMulticast(1)
                .setMaxProgramSize(5)
                .build();

        assertEquals(Long.MAX_VALUE, stats.durationMs);
        assertEquals(10, stats.receivedRas);
        assertEquals(5, stats.matchingRas);
        assertEquals(3, stats.droppedRas);
        assertEquals(1, stats.zeroLifetimeRas);
        assertEquals(1, stats.parseErrors);
        assertEquals(1, stats.programUpdates);
        assertEquals(1, stats.programUpdatesAll);
        assertEquals(1, stats.programUpdatesAllowingMulticast);
        assertEquals(5, stats.maxProgramSize);
    }

    @Test
    public void testParceling() {
        final Parcel p = Parcel.obtain();
        final ApfStats stats1 = new ApfStats.Builder()
                .setDurationMs(Long.MAX_VALUE)
                .setReceivedRas(10)
                .setMatchingRas(5)
                .setDroppedRas(3)
                .setZeroLifetimeRas(1)
                .setParseErrors(1)
                .setProgramUpdates(1)
                .setProgramUpdatesAll(1)
                .setProgramUpdatesAllowingMulticast(1)
                .setMaxProgramSize(5)
                .build();

        ApfStats stats2 = null;
        try {
            stats1.writeToParcel(p, 0);
            p.setDataPosition(0);

            stats2 = new ApfStats.Builder()
                    .setDurationMs(p.readLong())
                    .setReceivedRas(p.readInt())
                    .setMatchingRas(p.readInt())
                    .setDroppedRas(p.readInt())
                    .setZeroLifetimeRas(p.readInt())
                    .setParseErrors(p.readInt())
                    .setProgramUpdates(p.readInt())
                    .setProgramUpdatesAll(p.readInt())
                    .setProgramUpdatesAllowingMulticast(p.readInt())
                    .setMaxProgramSize(p.readInt())
                    .build();
        } finally {
            p.recycle();
        }

        assertEquals(stats1.toString(), stats2.toString());
    }
}
