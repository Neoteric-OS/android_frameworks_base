/*
 * Copyright 2020 The Android Open Source Project
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

package com.android.server;

import static org.mockito.Mockito.*;

import android.provider.DeviceConfig;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class BluetoothDeviceConfigListenerTest {
    private BluetoothDeviceConfigListener mBluetoothDeviceConfigListener;

    @Mock BluetoothManagerService mBluetoothManagerService;
    @Mock BluetoothModeChangeHelper mBluetoothModeChangeHelper;

    @Before
    public void setUp() throws Exception {
        mBluetoothDeviceConfigListener = new BluetoothDeviceConfigListener(
                    mBluetoothManagerService, mBluetoothModeChangeHelper);
    }

    @Test
    public void testTriggerInitFlagChange() {
        DeviceConfig.setProperty("bluetooth", "INIT_gd_core", "true", false);
        verify(mBluetoothManagerService).onInitFlagsChanged();
    }

    @Test
    public void test_False() {
        Assert.assertFalse(1 == 1);
    }
}
