/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static org.junit.Assert.fail;

import android.net.LinkProperties;
import android.net.NetworkCapabilities;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class VcnTunnelConfigTest {
    private static final int[] TUNNEL_CAPS =
            new int[] {
                NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_MMS
            };
    private static final int[] UNDERLYING_CAPS = new int[] {NetworkCapabilities.NET_CAPABILITY_DUN};
    private static final int MAX_MTU = 1360;
    private static final boolean IS_METERED = false; // Opposite from default
    private static final boolean IS_ROAMING = true; // Opposite from default

    // Package protected for use in VcnConfigTest
    static VcnTunnelConfig buildTestTunnelConfig() {
        final VcnTunnelConfig.Builder builder =
                new VcnTunnelConfig.Builder()
                        .setMaxMtu(MAX_MTU)
                        .setMetered(IS_METERED)
                        .setRoaming(IS_ROAMING);

        for (int caps : TUNNEL_CAPS) {
            builder.addTunnelCapability(caps);
        }

        for (int caps : UNDERLYING_CAPS) {
            builder.addRequiredUnderlyingCapability(caps);
        }

        return builder.build();
    }

    @Test
    public void testBuilderRequiresNonEmptyTunnelCaps() {
        try {
            new VcnTunnelConfig.Builder()
                    .addRequiredUnderlyingCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            fail("Expected exception due to invalid exposed capabilities");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testBuilderRequiresNonEmptyUnderlyingCaps() {
        try {
            new VcnTunnelConfig.Builder()
                    .addTunnelCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            fail("Expected exception due to invalid required underlying capabilities");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testBuilderRequiresValidMtu() {
        try {
            new VcnTunnelConfig.Builder().setMaxMtu(LinkProperties.MIN_MTU_V6 - 1);
            fail("Expected exception due to invalid mtu");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testBuilderAndGetters() {
        final VcnTunnelConfig config = buildTestTunnelConfig();

        for (int cap : TUNNEL_CAPS) {
            config.hasTunnelCapability(cap);
        }
        for (int cap : UNDERLYING_CAPS) {
            config.requiresUnderlyingCapability(cap);
        }

        assertEquals(MAX_MTU, config.getMaxMtu());
        assertEquals(IS_METERED, config.isMetered());
        assertEquals(IS_ROAMING, config.isRoaming());
    }

    @Test
    public void testPersistableBundle() {
        final VcnTunnelConfig config = buildTestTunnelConfig();

        assertEquals(config, new VcnTunnelConfig(config.toPersistableBundle()));
    }
}
