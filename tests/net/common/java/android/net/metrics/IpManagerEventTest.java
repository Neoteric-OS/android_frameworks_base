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
public class IpManagerEventTest {
    @Test
    public void testConstructor() {
        for (int i = IpManagerEvent.PROVISIONING_OK;
                i <= IpManagerEvent.ERROR_INTERFACE_NOT_FOUND; i++) {
            final IpManagerEvent ipme = new IpManagerEvent(i, Long.MAX_VALUE);
            assertEquals(i, ipme.eventType);
            assertEquals(Long.MAX_VALUE, ipme.durationMs);
        }
    }

    @Test
    public void testParceling() {
        final Parcel p = Parcel.obtain();
        final IpManagerEvent ipme1 =
                new IpManagerEvent(IpManagerEvent.PROVISIONING_OK, Long.MAX_VALUE);
        IpManagerEvent ipme2 = null;
        try {
            ipme1.writeToParcel(p, 0);
            p.setDataPosition(0);
            ipme2 = new IpManagerEvent(p.readInt(), p.readLong());
        } finally {
            p.recycle();
        }
        assertEquals(ipme1.toString(), ipme2.toString());
    }
}
