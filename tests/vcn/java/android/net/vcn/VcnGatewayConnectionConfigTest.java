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

import static android.net.ipsec.ike.SaProposal.DH_GROUP_2048_BIT_MODP;
import static android.net.ipsec.ike.SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_12;
import static android.net.ipsec.ike.SaProposal.PSEUDORANDOM_FUNCTION_AES128_XCBC;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.ipsec.ike.IkeFqdnIdentification;
import android.net.ipsec.ike.IkeSaProposal;
import android.net.ipsec.ike.IkeSessionParams;
import android.net.ipsec.ike.SaProposal;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class VcnGatewayConnectionConfigTest {
    private static final int[] EXPOSED_CAPS =
            new int[] {
                NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_MMS
            };
    private static final int[] UNDERLYING_CAPS = new int[] {NetworkCapabilities.NET_CAPABILITY_DUN};
    private static final long[] RETRY_INTERVALS_MS =
            new long[] {
                TimeUnit.SECONDS.toMillis(5),
                TimeUnit.SECONDS.toMillis(30),
                TimeUnit.MINUTES.toMillis(1),
                TimeUnit.MINUTES.toMillis(5),
                TimeUnit.MINUTES.toMillis(15),
                TimeUnit.MINUTES.toMillis(30)
            };
    private static final int MAX_MTU = 1360;

    // Package protected for use in VcnConfigTest
    static VcnGatewayConnectionConfig buildTestConfig() {
        final VcnGatewayConnectionConfig.Builder builder =
                new VcnGatewayConnectionConfig.Builder()
                        .setRetryInterval(RETRY_INTERVALS_MS)
                        .setMaxMtu(MAX_MTU);

        for (int caps : EXPOSED_CAPS) {
            builder.addExposedCapability(caps);
        }

        for (int caps : UNDERLYING_CAPS) {
            builder.addRequiredUnderlyingCapability(caps);
        }

        return builder.build();
    }

    @Test
    public void testBuilderRequiresNonEmptyExposedCaps() {
        try {
            new VcnGatewayConnectionConfig.Builder()
                    .addRequiredUnderlyingCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            fail("Expected exception due to invalid exposed capabilities");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testBuilderRequiresNonEmptyUnderlyingCaps() {
        try {
            new VcnGatewayConnectionConfig.Builder()
                    .addExposedCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            fail("Expected exception due to invalid required underlying capabilities");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testBuilderRequiresNonNullRetryInterval() {
        try {
            new VcnGatewayConnectionConfig.Builder().setRetryInterval(null);
            fail("Expected exception due to invalid retryIntervalMs");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testBuilderRequiresNonEmptyRetryInterval() {
        try {
            new VcnGatewayConnectionConfig.Builder().setRetryInterval(new long[0]);
            fail("Expected exception due to invalid retryIntervalMs");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testBuilderRequiresValidMtu() {
        try {
            new VcnGatewayConnectionConfig.Builder()
                    .setMaxMtu(VcnGatewayConnectionConfig.MIN_MTU_V6 - 1);
            fail("Expected exception due to invalid mtu");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testBuilderAndGetters() {
        final VcnGatewayConnectionConfig config = buildTestConfig();

        for (int cap : EXPOSED_CAPS) {
            config.hasExposedCapability(cap);
        }
        for (int cap : UNDERLYING_CAPS) {
            config.requiresUnderlyingCapability(cap);
        }

        assertArrayEquals(RETRY_INTERVALS_MS, config.getRetryIntervalsMs());
        assertEquals(MAX_MTU, config.getMaxMtu());
    }

    @Test
    public void testPersistableBundle() {
        final VcnGatewayConnectionConfig config = buildTestConfig();

        assertEquals(config, new VcnGatewayConnectionConfig(config.toPersistableBundle()));
    }

    @Test
    public void testConstructVcnIkeConfigWithIkeParams() {
        IkeSaProposal saProposal =
                new IkeSaProposal.Builder()
                        .addEncryptionAlgorithm(
                                ENCRYPTION_ALGORITHM_AES_GCM_12, SaProposal.KEY_LEN_AES_128)
                        .addDhGroup(DH_GROUP_2048_BIT_MODP)
                        .addPseudorandomFunction(PSEUDORANDOM_FUNCTION_AES128_XCBC)
                        .build();

        IkeSessionParams ikeParams =
                new IkeSessionParams.Builder(InstrumentationRegistry.getContext())
                        .setNetwork(mock(Network.class))
                        .setServerHostname("10.10.10.1")
                        .addSaProposal(saProposal)
                        .setLocalIdentification(new IkeFqdnIdentification("test.com"))
                        .setRemoteIdentification(new IkeFqdnIdentification("client.com"))
                        .setAuthPsk("IKE_PSK".getBytes())
                        .build();

        new VcnIkeConfig(ikeParams);
    }

    @Test
    public void testConstructVcnIkeConfigWithPersistableBundle() {
        try {
            new VcnIkeConfig(InstrumentationRegistry.getContext(), null /* bundle */);
            fail("Expect NullPointerException");
        } catch (NullPointerException expected) {
        }
    }
}
