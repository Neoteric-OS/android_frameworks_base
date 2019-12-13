/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.net;

import static com.android.testutils.ParcelUtilsKt.assertParcelSane;
import static com.android.testutils.ParcelUtilsKt.assertParcelingIsLossless;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.telephony.SubscriptionManager;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/**
 * Unit test for {@link android.net.TelephonyNetworkSpecifier}.
 */
@SmallTest
public class TelephonyNetworkSpecifierTest {
    private static final int TEST_SUBID = 5;

    /**
     * Validate that IllegalArgumentException will be thrown if build TelephonyNetworkSpecifier
     * without calling {@link TelephonyNetworkSpecifier.Builder#setSubscriptionId(int)}.
     */
    @Test
    public void testTelephonyNetworkSpecifierBuilderDefault() {
        try {
            new TelephonyNetworkSpecifier.Builder().build();
        } catch (IllegalArgumentException iae) {
            // expected, test pass
        }
    }

    /**
     * Validate that no exception will be thrown even if pass invalid subscription id to
     * {@link TelephonyNetworkSpecifier.Builder#setSubscriptionId(int)}.
     */
    @Test
    public void testTelephonyNetworkSpecifierBuilderWithInvalidSubId() {
        new TelephonyNetworkSpecifier.Builder()
                .setSubscriptionId(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                .build();
    }

    /**
     * Validate the correctness of TelephonyNetworkSpecifier when provide valid subId on
     * runtime.
     */
    @Test
    public void testTelephonyNetworkSpecifierBuilderWithValidSubIdOnRuntime() {
        final NetworkSpecifier specifier = new TelephonyNetworkSpecifier.Builder()
                .setSubscriptionId(TEST_SUBID)
                .build();
        assertTrue(specifier instanceof TelephonyNetworkSpecifier);
        TelephonyNetworkSpecifier ts = (TelephonyNetworkSpecifier) specifier;
        assertEquals(TEST_SUBID, ts.getSubscriptionId());
    }

    /**
     * Validate that parcel marshalling/unmarshalling works.
     */
    @Test
    public void testTelephonyNetworkSpecifierParcel() {
        TelephonyNetworkSpecifier specifier = new TelephonyNetworkSpecifier.Builder()
                .setSubscriptionId(TEST_SUBID)
                .build();
        assertParcelingIsLossless(specifier);

        specifier = new TelephonyNetworkSpecifier.Builder()
                .setSubscriptionId(TEST_SUBID + 1)
                .build();
        assertParcelSane(specifier, 1);
    }
}
