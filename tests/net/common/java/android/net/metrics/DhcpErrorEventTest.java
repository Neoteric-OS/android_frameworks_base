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
import static org.junit.Assert.assertTrue;

import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DhcpErrorEventTest {
    /**
     * DHCP Optional Type: DHCP Subnet Mask (Copy from DhcpPacket.java)
     */
    private static final byte DHCP_SUBNET_MASK = 1;

    @Test
    public void testConstructor() {
        final DhcpErrorEvent dee = new DhcpErrorEvent(DhcpErrorEvent.L2_TOO_SHORT);
        assertTrue((DhcpErrorEvent.L2_TOO_SHORT & dee.errorCode) == DhcpErrorEvent.L2_TOO_SHORT);
    }

    @Test
    public void testParceling() {
        final Parcel p = Parcel.obtain();
        final DhcpErrorEvent dee1 = new DhcpErrorEvent(DhcpErrorEvent.L2_TOO_SHORT);
        DhcpErrorEvent dee2 = null;
        try {
            dee1.writeToParcel(p, 0);
            p.setDataPosition(0);
            dee2 = new DhcpErrorEvent(p.readInt());
        } finally {
            p.recycle();
        }
        assertEquals(dee1.toString(), dee2.toString());
    }

    @Test
    public void testErrorCodeWithOption() {
        int errorCode = DhcpErrorEvent.errorCodeWithOption(
                DhcpErrorEvent.DHCP_INVALID_OPTION_LENGTH,
                DHCP_SUBNET_MASK);
        assertTrue((DhcpErrorEvent.DHCP_INVALID_OPTION_LENGTH & errorCode)
                == DhcpErrorEvent.DHCP_INVALID_OPTION_LENGTH);
        assertTrue((DHCP_SUBNET_MASK & errorCode) == DHCP_SUBNET_MASK);
    }
}
