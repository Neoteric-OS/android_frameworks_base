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
public class RaEventTest {
    private static final long NO_LIFETIME = -1L;
    @Test
    public void testBuilder() {
        RaEvent re = new RaEvent.Builder().build();
        assertEquals(NO_LIFETIME, re.routerLifetime);
        assertEquals(NO_LIFETIME, re.prefixValidLifetime);
        assertEquals(NO_LIFETIME, re.prefixPreferredLifetime);
        assertEquals(NO_LIFETIME, re.routeInfoLifetime);
        assertEquals(NO_LIFETIME, re.rdnssLifetime);
        assertEquals(NO_LIFETIME, re.dnsslLifetime);

        re = new RaEvent.Builder()
                .updateRouterLifetime(Long.MIN_VALUE)
                .updatePrefixValidLifetime(Long.MIN_VALUE)
                .updatePrefixPreferredLifetime(Long.MIN_VALUE)
                .updateRouteInfoLifetime(Long.MIN_VALUE)
                .updateRdnssLifetime(Long.MIN_VALUE)
                .updateDnsslLifetime(Long.MIN_VALUE)
                .build();
        assertEquals(Long.MIN_VALUE, re.routerLifetime);
        assertEquals(Long.MIN_VALUE, re.prefixValidLifetime);
        assertEquals(Long.MIN_VALUE, re.prefixPreferredLifetime);
        assertEquals(Long.MIN_VALUE, re.routeInfoLifetime);
        assertEquals(Long.MIN_VALUE, re.rdnssLifetime);
        assertEquals(Long.MIN_VALUE, re.dnsslLifetime);

        re = new RaEvent.Builder()
                .updateRouterLifetime(Long.MIN_VALUE)
                .updateRouterLifetime(Long.MAX_VALUE)
                .build();
        assertEquals(Long.MIN_VALUE, re.routerLifetime);
    }

    @Test
    public void testParceling() {
        final Parcel p = Parcel.obtain();
        final RaEvent re1 = new RaEvent.Builder()
                .updateRouterLifetime(Long.MIN_VALUE)
                .updatePrefixValidLifetime(Long.MIN_VALUE)
                .updatePrefixPreferredLifetime(Long.MIN_VALUE)
                .updateRouteInfoLifetime(Long.MIN_VALUE)
                .updateRdnssLifetime(Long.MIN_VALUE)
                .updateRdnssLifetime(Long.MIN_VALUE)
                .build();

        RaEvent re2 = null;
        try {
            re1.writeToParcel(p, 0);
            p.setDataPosition(0);

            re2 = new RaEvent.Builder()
                    .updateRouterLifetime(p.readLong())
                    .updatePrefixValidLifetime(p.readLong())
                    .updatePrefixPreferredLifetime(p.readLong())
                    .updateRouteInfoLifetime(p.readLong())
                    .updateRdnssLifetime(p.readLong())
                    .updateRdnssLifetime(p.readLong())
                    .build();
        } finally {
            p.recycle();
        }
        assertEquals(re1.toString(), re2.toString());
    }
}
