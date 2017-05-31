/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.connectivity.tethering;

import static com.android.server.connectivity.tethering.OffloadController.OFFLOAD_ENABLED_SYSPROP_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.net.util.SharedLog;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.server.connectivity.MockableSystemProperties;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


@RunWith(AndroidJUnit4.class)
@SmallTest
public class OffloadControllerTest {

    @Mock private OffloadHardwareInterface mHardware;
    @Mock private MockableSystemProperties mSystemProperties;

    @Before public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    private void setupFunctioningHardwareInterface() {
        when(mHardware.initOffloadConfig()).thenReturn(true);
        when(mHardware.initOffloadControl(any(OffloadHardwareInterface.ControlCallback.class)))
                .thenReturn(true);
    }

    @Test
    public void testSystemPropertyAllowsStart() {
        setupFunctioningHardwareInterface();
        when(mSystemProperties.getBoolean(eq(OFFLOAD_ENABLED_SYSPROP_KEY), anyBoolean()))
                .thenReturn(true);

        final OffloadController offload =
                new OffloadController(null, mHardware, mSystemProperties, new SharedLog("test"));
        offload.start();

        final InOrder inOrder = inOrder(mHardware, mSystemProperties);
        inOrder.verify(mSystemProperties, times(1)).getBoolean(OFFLOAD_ENABLED_SYSPROP_KEY, true);
        inOrder.verify(mHardware, times(1)).initOffloadConfig();
        inOrder.verify(mHardware, times(1)).initOffloadControl(
                any(OffloadHardwareInterface.ControlCallback.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSystemPropertyDisablesStart() {
        setupFunctioningHardwareInterface();
        when(mSystemProperties.getBoolean(eq(OFFLOAD_ENABLED_SYSPROP_KEY), anyBoolean()))
                .thenReturn(false);

        final OffloadController offload =
                new OffloadController(null, mHardware, mSystemProperties, new SharedLog("test"));
        offload.start();

        final InOrder inOrder = inOrder(mHardware, mSystemProperties);
        inOrder.verify(mSystemProperties, times(1)).getBoolean(OFFLOAD_ENABLED_SYSPROP_KEY, true);
        inOrder.verifyNoMoreInteractions();
    }
}
