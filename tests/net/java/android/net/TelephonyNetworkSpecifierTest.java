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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
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
     * Validate we can get valid default object by {@link TelephonyNetworkSpecifier.Builder#build()}
     * without calling {@link TelephonyNetworkSpecifier.Builder#setSubscriptionId(int)}.
     */
    @Test
    public void testTelephonyNetworkSpecifierBuilderDefault() {
        new TelephonyNetworkSpecifier.Builder().build();
    }

    /**
     * Validate the no exception will be thrown even if pass invalid subscription id to
     * {@link TelephonyNetworkSpecifier.Builder#setSubscriptionId(int)}.
     */
    @Test
    public void testTelephonyNetworkSpecifierBuilderWithInvalidSubId() {
        new TelephonyNetworkSpecifier.Builder()
                .setSubscriptionId(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                .build();
    }

    /**
     * Validate the correctness of TelephonyNetworkSpecifier when provide valid subId.
     */
    @Test
    public void testTelephonyNetworkSpecifierBuilderWithValidSubId() {
        NetworkSpecifier specifer = new TelephonyNetworkSpecifier.Builder()
                .setSubscriptionId(TEST_SUBID)
                .build();
        assertTrue(specifer instanceof TelephonyNetworkSpecifier);
        TelephonyNetworkSpecifier ts = (TelephonyNetworkSpecifier) specifer;
        assertEquals(TEST_SUBID, ts.getSubscriptionId());
    }

    /**
     * Validate that parcel marshlling/unmarshalling works.
     */
    @Test
    public void testTelephonyNetworkSpecifierParcel() {
        TelephonyNetworkSpecifier specifier = new TelephonyNetworkSpecifier.Builder()
                .setSubscriptionId(TEST_SUBID)
                .build();
        Parcel parcelW = Parcel.obtain();
        specifier.writeToParcel(parcelW, 0);
        byte[] bytes = parcelW.marshall();
        parcelW.recycle();

        Parcel parcelR = Parcel.obtain();
        parcelR.unmarshall(bytes, 0, bytes.length);
        parcelR.setDataPosition(0);
        TelephonyNetworkSpecifier parcelSpecifier =
                TelephonyNetworkSpecifier.CREATOR.createFromParcel(parcelR);

        assertEquals(specifier, parcelSpecifier);
    }
}
