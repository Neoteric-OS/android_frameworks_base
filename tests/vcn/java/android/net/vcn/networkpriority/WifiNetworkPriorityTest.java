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
package android.vcn.networkpriority;

import static android.net.vcn.networkpriority.NetworkPriority.NETWORK_QUALITY_OK;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.vcn.networkpriority.NetworkPriority;
import android.net.vcn.networkpriority.WifiNetworkPriority;

import org.junit.Test;

public class WifiNetworkPriorityTest {
    private static final String SSID = "TestWifi";
    private static final int INVALID_NETWORK_QUALITY = 999;

    private static WifiNetworkPriority getTestNetworkPriority() {
        return new WifiNetworkPriority.Builder()
                .setNetworkQuality(NETWORK_QUALITY_OK)
                .setAllowMetered(true /* allowMetered */)
                .setSsid(SSID)
                .build();
    }

    @Test
    public void testBuilderAndGetters() {
        final WifiNetworkPriority wifiNetworkPriority = getTestNetworkPriority();
        assertEquals(NETWORK_QUALITY_OK, wifiNetworkPriority.getNetworkQuality());
        assertTrue(wifiNetworkPriority.allowMetered());
        assertEquals(SSID, wifiNetworkPriority.getSsid());
    }

    @Test
    public void testBuildWithInvalidNetworkQuality() {
        try {
            new WifiNetworkPriority.Builder().setNetworkQuality(INVALID_NETWORK_QUALITY);
            fail("Expected to throw for invalid network quality");
        } catch (Exception expected) {
            // TODO: handle exception
        }
    }

    @Test
    public void testPersistableBundle() {
        final WifiNetworkPriority wifiNetworkPriority = getTestNetworkPriority();
        assertEquals(
                wifiNetworkPriority,
                NetworkPriority.fromPersistableBundle(wifiNetworkPriority.toPersistableBundle()));
    }
}
