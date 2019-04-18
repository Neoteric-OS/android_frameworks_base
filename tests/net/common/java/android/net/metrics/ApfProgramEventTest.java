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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class ApfProgramEventTest {
    private boolean hasFlag(int flag, int target) {
        return (target & (1 << flag)) != 0;
    }

    @Test
    public void testBuilder() {
        final long time = System.currentTimeMillis();
        final ApfProgramEvent ape = new ApfProgramEvent.Builder()
                .setLifetime(Long.MAX_VALUE)
                .setActualLifetime(time)
                .setFilteredRas(5)
                .setCurrentRas(1)
                .setProgramLength(10)
                .setFlags(true, true)
                .build();

        assertEquals(Long.MAX_VALUE, ape.lifetime);
        assertEquals(time, ape.actualLifetime);
        assertEquals(5, ape.filteredRas);
        assertEquals(1, ape.currentRas);
        assertEquals(10, ape.programLength);
        assertEquals(ApfProgramEvent.flagsFor(true, true), ape.flags);
    }

    @Test
    public void testParceling() {
        final Parcel p = Parcel.obtain();
        final long time = System.currentTimeMillis();
        final ApfProgramEvent ape1 = new ApfProgramEvent.Builder()
                .setLifetime(Long.MAX_VALUE)
                .setActualLifetime(time)
                .setFilteredRas(5)
                .setCurrentRas(1)
                .setProgramLength(10)
                .setFlags(true, true)
                .build();

        ApfProgramEvent ape2 = null;
        try {
            ape1.writeToParcel(p, ape1.flags);
            p.setDataPosition(0);

            ApfProgramEvent.Builder builder = new ApfProgramEvent.Builder()
                    .setLifetime(p.readLong())
                    .setActualLifetime(p.readLong())
                    .setFilteredRas(p.readInt())
                    .setCurrentRas(p.readInt())
                    .setProgramLength(p.readInt());
            final int flags = p.readInt();
            builder.setFlags(
                    hasFlag(ApfProgramEvent.FLAG_HAS_IPV4_ADDRESS, flags),
                    hasFlag(ApfProgramEvent.FLAG_MULTICAST_FILTER_ON, flags));
            ape2 = builder.build();
        } finally {
            p.recycle();
        }
        assertEquals(ape1.toString(), ape2.toString());
    }

    @Test
    public void testFlagsFor() {
        int flags = ApfProgramEvent.flagsFor(false, false);
        assertFalse(hasFlag(ApfProgramEvent.FLAG_HAS_IPV4_ADDRESS, flags));
        assertFalse(hasFlag(ApfProgramEvent.FLAG_MULTICAST_FILTER_ON, flags));

        flags = ApfProgramEvent.flagsFor(true, false);
        assertTrue(hasFlag(ApfProgramEvent.FLAG_HAS_IPV4_ADDRESS, flags));
        assertFalse(hasFlag(ApfProgramEvent.FLAG_MULTICAST_FILTER_ON, flags));

        flags = ApfProgramEvent.flagsFor(false, true);
        assertFalse(hasFlag(ApfProgramEvent.FLAG_HAS_IPV4_ADDRESS, flags));
        assertTrue(hasFlag(ApfProgramEvent.FLAG_MULTICAST_FILTER_ON, flags));

        flags = ApfProgramEvent.flagsFor(true, true);
        assertTrue(hasFlag(ApfProgramEvent.FLAG_HAS_IPV4_ADDRESS, flags));
        assertTrue(hasFlag(ApfProgramEvent.FLAG_MULTICAST_FILTER_ON, flags));
    }
}
