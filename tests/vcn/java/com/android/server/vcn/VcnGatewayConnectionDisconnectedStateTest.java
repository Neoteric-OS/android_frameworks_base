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

package com.android.server.vcn;



import static org.junit.Assert.assertEquals;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for VcnGatewayConnection.DisconnectedState */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class VcnGatewayConnectionDisconnectedStateTest extends VcnGatewayConnectionTestBase {
    @Test
    public void testNetworkChangesTriggerStateTransitions() throws Exception {
        mGatewayConnection.transitionTo(mGatewayConnection.mDisconnectedState);
        mTestLooper.dispatchAll();

        mGatewayConnection.onSelectedUnderlyingNetworkChanged(TEST_UNDERLYING_NETWORK_RECORD_1);
        mTestLooper.dispatchAll();

        assertEquals(mGatewayConnection.mDnsResolutionState, mGatewayConnection.getCurrentState());
    }
}
