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

package com.android.server.hdmi;

import static com.android.server.hdmi.HdmiCecMessageValidator.ERROR_DESTINATION;
import static com.android.server.hdmi.HdmiCecMessageValidator.ERROR_PARAMETER;
import static com.android.server.hdmi.HdmiCecMessageValidator.ERROR_PARAMETER_SHORT;
import static com.android.server.hdmi.HdmiCecMessageValidator.ERROR_SOURCE;
import static com.android.server.hdmi.HdmiCecMessageValidator.OK;

import static com.google.common.truth.Truth.assertThat;

import android.os.test.TestLooper;
import android.platform.test.annotations.Presubmit;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.google.common.truth.IntegerSubject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link com.android.server.hdmi.HdmiCecMessageValidator} class. */
@SmallTest
@Presubmit
@RunWith(JUnit4.class)
public class HdmiCecMessageValidatorTest {

    private HdmiCecMessageValidator mHdmiCecMessageValidator;
    private TestLooper mTestLooper = new TestLooper();

    @Before
    public void setUp() throws Exception {
        HdmiControlService mHdmiControlService = new HdmiControlService(
                InstrumentationRegistry.getTargetContext());

        mHdmiControlService.setIoLooper(mTestLooper.getLooper());
        mHdmiCecMessageValidator = new HdmiCecMessageValidator(mHdmiControlService);
    }

    @Test
    public void isValid_giveDevicePowerStatus() {
        assertMessageValidity("04:8F").isEqualTo(OK);

        assertMessageValidity("0F:8F").isEqualTo(ERROR_DESTINATION);
        assertMessageValidity("F4:8F").isEqualTo(ERROR_SOURCE);
    }

    @Test
    public void isValid_reportPowerStatus() {
        assertMessageValidity("04:90:00").isEqualTo(OK);

        assertMessageValidity("0F:90:00").isEqualTo(ERROR_DESTINATION);
        assertMessageValidity("F0:90").isEqualTo(ERROR_SOURCE);
        assertMessageValidity("04:90").isEqualTo(ERROR_PARAMETER_SHORT);
    }

    @Test
    public void isValid_setDigitalTimer_clearDigitalTimer() {
        // Services identified by Digital IDs - ARIB Broadcast System
        assertMessageValidity("04:99:0C:08:15:05:04:1E:00:00:C4:C2:11:D8:75:30").isEqualTo(OK);
        // Service identified by Digital IDs - ATSC Broadcast System
        assertMessageValidity("04:97:1E:07:12:20:50:28:01:01:8B:5E:39:5A").isEqualTo(OK);
        // Service identified by Digital IDs - DVB Broadcast System
        assertMessageValidity("04:99:05:0C:06:0A:19:3B:40:19:8B:44:03:11:04:FC").isEqualTo(OK);
        // Service identified by Channel - 1 part channel number
        assertMessageValidity("04:97:12:06:0C:2D:5A:19:08:91:04:00:B1").isEqualTo(OK);
        // Service identified by Channel - 2 part channel number
        assertMessageValidity("04:99:15:09:00:0F:00:2D:04:82:09:C8:72:C8").isEqualTo(OK);

        assertMessageValidity("4F:97:0C:08:15:05:04:1E:00:00:C4:C2:11:D8:75:30")
                .isEqualTo(ERROR_DESTINATION);
        assertMessageValidity("F0:99:15:09:00:0F:00:2D:04:82:09:C8:72:C8").isEqualTo(ERROR_SOURCE);
        assertMessageValidity("04:97:1E:12:20:58:01:01:8B:5E:39:5A")
                .isEqualTo(ERROR_PARAMETER_SHORT);
        // Out of range Day of Month
        assertMessageValidity("04:99:24:0C:06:0A:19:3B:40:19:8B:44:03:11:04:FC")
                .isEqualTo(ERROR_PARAMETER);
        // Out of range Month of Year
        assertMessageValidity("04:97:12:10:0C:2D:5A:19:08:91:04:00:B1").isEqualTo(ERROR_PARAMETER);
        // Out of range Start Time - Hour
        assertMessageValidity("04:99:0C:08:20:05:04:1E:00:00:C4:C2:11:D8:75:30")
                .isEqualTo(ERROR_PARAMETER);
        // Out of range Start Time - Minute
        assertMessageValidity("04:97:15:09:00:4B:00:2D:04:82:09:C8:72:C8")
                .isEqualTo(ERROR_PARAMETER);
        // Out of range Duration - Duration Hours
        assertMessageValidity("04:99:1E:07:12:20:78:28:01:01:8B:5E:39:5A")
                .isEqualTo(ERROR_PARAMETER);
        // Out of range Duration - Minute
        assertMessageValidity("04:97:05:0C:06:0A:19:48:40:19:8B:44:03:11:04:FC")
                .isEqualTo(ERROR_PARAMETER);
        // Invalid Recording Sequence
        assertMessageValidity("04:99:12:06:0C:2D:5A:19:90:91:04:00:B1").isEqualTo(ERROR_PARAMETER);
        // Invalid Recording Sequence
        assertMessageValidity("04:97:0C:08:15:05:04:1E:21:00:C4:C2:11:D8:75:30")
                .isEqualTo(ERROR_PARAMETER);

        // Invalid Digital Broadcast System
        assertMessageValidity("04:99:1E:07:12:20:50:28:01:04:8B:5E:39:5A")
                .isEqualTo(ERROR_PARAMETER);
        // Invalid Digital Broadcast System
        assertMessageValidity("04:97:05:0C:06:0A:19:3B:40:93:8B:44:03:11:04:FC")
                .isEqualTo(ERROR_PARAMETER);
        // Insufficient data for ARIB Broadcast system
        assertMessageValidity("04:99:0C:08:15:05:04:1E:00:00:C4:C2:11:D8:75")
                .isEqualTo(ERROR_PARAMETER);
        // Insufficient data for ATSC Broadcast system
        assertMessageValidity("04:97:1E:07:12:20:50:28:01:01:8B:5E:39").isEqualTo(ERROR_PARAMETER);
        // Insufficient data for DVB Broadcast system
        assertMessageValidity("04:99:05:0C:06:0A:19:3B:40:19:8B:44:03:11:04")
                .isEqualTo(ERROR_PARAMETER);
        // Insufficient data for 2 part channel number
        assertMessageValidity("04:97:15:09:00:0F:00:2D:04:82:09:C8:72").isEqualTo(ERROR_PARAMETER);
        // Invalid Channel Number format
        assertMessageValidity("04:99:12:06:0C:2D:5A:19:08:91:0D:00:B1").isEqualTo(ERROR_PARAMETER);
    }

    private IntegerSubject assertMessageValidity(String message) {
        return assertThat(mHdmiCecMessageValidator.isValid(buildMessage(message)));
    }

    /**
     * Build a CEC message from a hex byte string with bytes separated by {@code :}.
     *
     * <p>This format is used by both cec-client and www.cec-o-matic.com
     */
    private static HdmiCecMessage buildMessage(String message) {
        String[] parts = message.split(":");
        int src = Integer.parseInt(parts[0].substring(0, 1), 16);
        int dest = Integer.parseInt(parts[0].substring(1, 2), 16);
        int opcode = Integer.parseInt(parts[1], 16);
        byte[] params = new byte[parts.length - 2];
        for (int i = 0; i < params.length; i++) {
            params[i] = (byte) Integer.parseInt(parts[i + 2], 16);
        }
        return new HdmiCecMessage(src, dest, opcode, params);
    }
}
