/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.net.vcn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import android.net.wifi.WifiInfo;
import android.os.Parcel;

import org.junit.Test;

public class VcnTransportInfoTest {
    private static final int SUB_ID = 1;
    private static final int NETWORK_ID = 5;

    private static final VcnTransportInfo DEFAULT_TRANSPORT_INFO =
            new VcnTransportInfo(null /* wifiInfo */, 0 /* subId */);
    private static final VcnTransportInfo SAMPLE_TRANSPORT_INFO =
            new VcnTransportInfo(
                    new WifiInfo.Builder().setNetworkId(NETWORK_ID).build(), 0 /* flags */);

    @Test
    public void testEquals() {
        assertEquals(SAMPLE_TRANSPORT_INFO, SAMPLE_TRANSPORT_INFO);
        assertNotEquals(SAMPLE_TRANSPORT_INFO, DEFAULT_TRANSPORT_INFO);
    }

    @Test
    public void testParcelUnparcel() {
        Parcel parcel = Parcel.obtain();

        SAMPLE_TRANSPORT_INFO.writeToParcel(parcel, 0 /* flags */);

        VcnTransportInfo unparceled = VcnTransportInfo.CREATOR.createFromParcel(parcel);

        assertNotEquals(SAMPLE_TRANSPORT_INFO, unparceled);
        assertNull(unparceled.getWifiInfo());
        assertEquals(0, unparceled.getSubId());
    }
}
