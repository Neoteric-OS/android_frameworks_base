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
public class DhcpClientEventTest {
    @Test
    public void testBuilder() {
        final DhcpClientEvent dce = new DhcpClientEvent.Builder()
                .setMsg("test")
                .setDurationMs(Integer.MAX_VALUE)
                .build();

        assertEquals("test", dce.msg);
        assertEquals(Integer.MAX_VALUE, dce.durationMs);
    }

    @Test
    public void testParceling() {
        final Parcel p = Parcel.obtain();
        final DhcpClientEvent dce1 = new DhcpClientEvent.Builder()
                .setMsg("test")
                .setDurationMs(Integer.MAX_VALUE)
                .build();

        DhcpClientEvent dce2 = null;
        try {
            dce1.writeToParcel(p, 0);
            p.setDataPosition(0);

            dce2 = new DhcpClientEvent.Builder()
                    .setMsg(p.readString())
                    .setDurationMs(p.readInt())
                    .build();
        } finally {
            p.recycle();
        }

        assertEquals(dce1.toString(), dce2.toString());
    }
}
