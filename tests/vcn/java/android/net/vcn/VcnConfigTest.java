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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class VcnConfigTest {
    private static final long[] RETRY_INTERVALS_MS =
            new long[] {
                TimeUnit.SECONDS.toMillis(5),
                TimeUnit.SECONDS.toMillis(30),
                TimeUnit.MINUTES.toMillis(1),
                TimeUnit.MINUTES.toMillis(5),
                TimeUnit.MINUTES.toMillis(15),
                TimeUnit.MINUTES.toMillis(30)
            };
    private static final List<VcnGatewayConnectionConfig> TUNNEL_CONFIGS =
            Arrays.asList(VcnGatewayConnectionConfigTest.buildTestConfig());

    // Public visibility for VcnManagementServiceTest
    public static VcnConfig buildTestConfig() {
        VcnConfig.Builder builder = new VcnConfig.Builder().setRetryInterval(RETRY_INTERVALS_MS);

        for (VcnGatewayConnectionConfig tunnelConfig : TUNNEL_CONFIGS) {
            builder.addTunnelConfig(tunnelConfig);
        }

        return builder.build();
    }

    @Test
    public void testBuilderRequiresNonNullRetryInterval() {
        try {
            new VcnConfig.Builder().setRetryInterval(null);
            fail("Expected exception due to invalid retryIntervalMs");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testBuilderRequiresNonEmptyRetryInterval() {
        try {
            new VcnConfig.Builder().setRetryInterval(new long[0]);
            fail("Expected exception due to invalid retryIntervalMs");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testBuilderRequiresTunnelConfig() {
        try {
            new VcnConfig.Builder().build();
            fail("Expected exception due to no VcnGatewayConnectionConfigs provided");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testBuilderAndGetters() {
        final VcnConfig config = buildTestConfig();

        assertArrayEquals(RETRY_INTERVALS_MS, config.getRetryIntervalsMs());
        assertEquals(TUNNEL_CONFIGS, config.getTunnelConfigs());
    }

    @Test
    public void testPersistableBundle() {
        final VcnConfig config = buildTestConfig();

        assertEquals(config, new VcnConfig(config.toPersistableBundle()));
    }

    @Test
    public void testParceling() {
        final VcnConfig config = buildTestConfig();

        Parcel parcel = Parcel.obtain();
        config.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        assertEquals(config, VcnConfig.CREATOR.createFromParcel(parcel));
    }
}
