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
public class NetworkEventTest {
    @Test
    public void testConstructor() {
        for (int i = NetworkEvent.NETWORK_CONNECTED;
                i <= NetworkEvent.NETWORK_PARTIAL_CONNECTIVITY; i++) {
            NetworkEvent ne = new NetworkEvent(i);
            assertEquals(i, ne.eventType);
            assertEquals(0, ne.durationMs);

            ne = new NetworkEvent(i, Long.MAX_VALUE);
            assertEquals(i, ne.eventType);
            assertEquals(Long.MAX_VALUE, ne.durationMs);
        }
    }

    @Test
    public void testParceling() {
        final Parcel p = Parcel.obtain();
        final NetworkEvent ne1 = new NetworkEvent(NetworkEvent.NETWORK_CONNECTED);
        NetworkEvent ne2 = null;
        try {
            ne1.writeToParcel(p, 0);
            p.setDataPosition(0);
            ne2 = new NetworkEvent(p.readInt(), p.readLong());
        } finally {
            p.recycle();
        }
        assertEquals(ne1.toString(), ne2.toString());
    }
}
