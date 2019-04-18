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
public class IpReachabilityEventTest {
    @Test
    public void testConstructor() {
        for (int i = IpReachabilityEvent.PROBE;
                i <= IpReachabilityEvent.PROVISIONING_LOST_ORGANIC; i += 0x100) {
            final IpReachabilityEvent ipre = new IpReachabilityEvent(i);
            assertEquals(i, ipre.eventType);
        }
    }

    @Test
    public void testParceling() {
        final Parcel p = Parcel.obtain();
        final IpReachabilityEvent ipre1 = new IpReachabilityEvent(IpReachabilityEvent.PROBE);
        IpReachabilityEvent ipre2 = null;
        try {
            ipre1.writeToParcel(p, 0);
            p.setDataPosition(0);
            ipre2 = new IpReachabilityEvent(p.readInt());
        } finally {
            p.recycle();
        }
        assertEquals(ipre1.toString(), ipre2.toString());
    }
}
