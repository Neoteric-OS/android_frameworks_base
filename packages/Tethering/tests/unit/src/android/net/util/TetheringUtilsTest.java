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
package android.net.util;

import static android.net.TetheringManager.TETHERING_WIFI;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.net.LinkAddress;
import android.net.TetheringRequestParcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.testutils.MiscAssertsKt;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TetheringUtilsTest {

    private static final int TEST_TYPE = TETHERING_WIFI;
    private static final LinkAddress TEST_SERVER_LINKADDR = new LinkAddress("192.168.43.1/24");
    private TetheringRequestParcel mTetheringRequest;

    @Before
    public void setUp() {
        mTetheringRequest = new TetheringRequestParcel();
        mTetheringRequest.tetheringType = TEST_TYPE;
        mTetheringRequest.localIPv4Address = TEST_SERVER_LINKADDR;
        mTetheringRequest.staticClientAddress = null;
        mTetheringRequest.exemptFromEntitlementCheck = false;
        mTetheringRequest.showProvisioningUi = true;
    }

    @Test
    public void testIsTetheringRequestEquals() throws Exception {
        final TetheringRequestParcel request = new TetheringRequestParcel();
        request.tetheringType = TEST_TYPE;
        request.localIPv4Address = TEST_SERVER_LINKADDR;
        request.staticClientAddress = null;
        request.exemptFromEntitlementCheck = false;
        request.showProvisioningUi = true;

        assertTrue(TetheringUtils.isTetheringRequestEquals(mTetheringRequest, mTetheringRequest));
        assertTrue(TetheringUtils.isTetheringRequestEquals(mTetheringRequest, request));
        assertTrue(TetheringUtils.isTetheringRequestEquals(null, null));
        assertFalse(TetheringUtils.isTetheringRequestEquals(mTetheringRequest, null));
        assertFalse(TetheringUtils.isTetheringRequestEquals(null, mTetheringRequest));

        request.localIPv4Address = null;
        assertFalse(TetheringUtils.isTetheringRequestEquals(mTetheringRequest, request));

        MiscAssertsKt.assertFieldCountEquals(5, TetheringRequestParcel.class);
    }
}
