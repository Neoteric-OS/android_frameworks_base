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

package android.net.apf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.res.Resources;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;
import com.android.internal.util.ParcelableTestUtil;
import com.android.internal.util.TestUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class ApfCapabilitiesTest {
    @Test
    public void testConstructorAndParcel() {
        final ApfCapabilities caps = new ApfCapabilities(123, 456, 789);
        assertEquals(123, caps.apfVersionSupported);
        assertEquals(456, caps.maximumApfProgramSize);
        assertEquals(789, caps.apfPacketFormat);

        ParcelableTestUtil.assertFieldCountEquals(3, ApfCapabilities.class);

        TestUtils.assertParcelingIsLossless(caps);
    }

    @Test
    public void testEquals() {
        assertEquals(new ApfCapabilities(1, 2, 3), new ApfCapabilities(1, 2, 3));
        assertNotEquals(new ApfCapabilities(2, 2, 3), new ApfCapabilities(1, 2, 3));
        assertNotEquals(new ApfCapabilities(1, 3, 3), new ApfCapabilities(1, 2, 3));
        assertNotEquals(new ApfCapabilities(1, 2, 4), new ApfCapabilities(1, 2, 3));
    }

    @Test
    public void testHasDataAccess() {
        ApfCapabilities caps = new ApfCapabilities(1, 2, 3);
        assertFalse(caps.hasDataAccess());

        caps = new ApfCapabilities(4, 5, 6);
        assertTrue(caps.hasDataAccess());
    }

    @Test
    public void testGetApfDrop8023Frames() {
        final boolean apfDrop802_3Frames =
                Resources.getSystem().getBoolean(R.bool.config_apfDrop802_3Frames);
        assertEquals(apfDrop802_3Frames, ApfCapabilities.getApfDrop8023Frames());
    }

    @Test
    public void testGetApfEtherTypeBlackList() {
        final int[] apfEthTypeBlackList =
                Resources.getSystem().getIntArray(R.array.config_apfEthTypeBlackList);
        assertTrue(Arrays.equals(apfEthTypeBlackList, ApfCapabilities.getApfEtherTypeBlackList()));
    }
}
