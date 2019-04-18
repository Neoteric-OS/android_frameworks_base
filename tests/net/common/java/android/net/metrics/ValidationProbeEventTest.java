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

import static android.net.metrics.ValidationProbeEvent.PROBE_DNS;
import static android.net.metrics.ValidationProbeEvent.PROBE_FALLBACK;
import static android.net.metrics.ValidationProbeEvent.PROBE_HTTP;
import static android.net.metrics.ValidationProbeEvent.PROBE_HTTPS;
import static android.net.metrics.ValidationProbeEvent.PROBE_PAC;
import static android.net.metrics.ValidationProbeEvent.PROBE_PRIVDNS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class ValidationProbeEventTest {
    // Copy from ValidationProbeEvent.java
    private static final int FIRST_VALIDATION  = 1 << 8;
    private static final int REVALIDATION      = 2 << 8;

    private boolean hasType(int type, int target) {
        return (type & target) == type;
    }

    @Test
    public void testBuilder() {
        ValidationProbeEvent vpe = new ValidationProbeEvent.Builder()
                .setDurationMs(Long.MAX_VALUE)
                .setProbeType(ValidationProbeEvent.PROBE_DNS, true)
                .setReturnCode(ValidationProbeEvent.DNS_SUCCESS)
                .build();

        assertEquals(Long.MAX_VALUE, vpe.durationMs);
        assertTrue(hasType(ValidationProbeEvent.PROBE_DNS, vpe.probeType));
        assertTrue(hasType(FIRST_VALIDATION, vpe.probeType));
        assertEquals(ValidationProbeEvent.DNS_SUCCESS, vpe.returnCode);

        vpe = new ValidationProbeEvent.Builder()
                .setProbeType(ValidationProbeEvent.PROBE_DNS, false)
                .build();

        assertTrue(hasType(REVALIDATION, vpe.probeType));
    }

    @Test
    public void testParceling() {
        final Parcel p = Parcel.obtain();
        final ValidationProbeEvent vpe1 = new ValidationProbeEvent.Builder()
                .setDurationMs(Long.MAX_VALUE)
                .setProbeType(ValidationProbeEvent.PROBE_DNS, true)
                .setReturnCode(ValidationProbeEvent.DNS_SUCCESS)
                .build();

        ValidationProbeEvent vpe2 = null;
        try {
            vpe1.writeToParcel(p, 0);
            p.setDataPosition(0);

            ValidationProbeEvent.Builder builder = new ValidationProbeEvent.Builder()
                    .setDurationMs(p.readLong());
            final int type = p.readInt();
            builder.setProbeType(type & 0xff, hasType(FIRST_VALIDATION, type));
            builder.setReturnCode(p.readInt());
            vpe2 = builder.build();
        } finally {
            p.recycle();
        }

        assertEquals(vpe1.toString(), vpe2.toString());
    }

    @Test
    public void testGetProbeName() {
        assertEquals("PROBE_DNS", ValidationProbeEvent.getProbeName(PROBE_DNS));
        assertEquals("PROBE_HTTP", ValidationProbeEvent.getProbeName(PROBE_HTTP));
        assertEquals("PROBE_HTTPS", ValidationProbeEvent.getProbeName(PROBE_HTTPS));
        assertEquals("PROBE_PAC", ValidationProbeEvent.getProbeName(PROBE_PAC));
        assertEquals("PROBE_FALLBACK", ValidationProbeEvent.getProbeName(PROBE_FALLBACK));
        assertEquals("PROBE_PRIVDNS", ValidationProbeEvent.getProbeName(PROBE_PRIVDNS));
        assertEquals("PROBE_???", ValidationProbeEvent.getProbeName(-1));
    }
}
