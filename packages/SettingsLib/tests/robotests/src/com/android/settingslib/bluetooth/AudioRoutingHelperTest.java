/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.settingslib.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiopolicy.AudioProductStrategy;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.List;

/** Tests for {@link AudioRoutingHelperTest}. */
@RunWith(RobolectricTestRunner.class)
public class AudioRoutingHelperTest {

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Spy private final Context mContext = ApplicationProvider.getApplicationContext();
    private static final String TEST_DEVICE_A_ADDRESS = "00:A1:A1:A1:A1:A1";
    private static final String TEST_DEVICE_B_ADDRESS = "11:B2:B2:B2:B2:B2";

    @Mock private AudioProductStrategy mAudioStrategy;
    @Spy private AudioManager mAudioManager = mContext.getSystemService(AudioManager.class);
    @Mock private AudioDeviceInfo mDeviceInfoOutput;
    @Mock private AudioDeviceInfo mDeviceInfoInput;
    @Mock private CachedBluetoothDevice mCachedBluetoothDeviceOutput;
    @Mock private CachedBluetoothDevice mCachedBluetoothDeviceInput;
    private AudioRoutingHelper mHelper;

    @Before
    public void setUp() {
        doReturn(mAudioManager).when(mContext).getSystemService(AudioManager.class);
        when(mDeviceInfoOutput.getAddress()).thenReturn(TEST_DEVICE_A_ADDRESS);
        when(mCachedBluetoothDeviceOutput.getAddress()).thenReturn(TEST_DEVICE_A_ADDRESS);
        when(mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
                .thenReturn(new AudioDeviceInfo[] {mDeviceInfoOutput});

        when(mDeviceInfoInput.getAddress()).thenReturn(TEST_DEVICE_B_ADDRESS);
        when(mCachedBluetoothDeviceInput.getAddress()).thenReturn(TEST_DEVICE_B_ADDRESS);
        when(mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS))
                .thenReturn(new AudioDeviceInfo[] {mDeviceInfoInput});

        doReturn(Collections.emptyList())
                .when(mAudioManager)
                .getPreferredDevicesForStrategy(any(AudioProductStrategy.class));
        when(mAudioStrategy.getAudioAttributesForLegacyStreamType(AudioManager.STREAM_MUSIC))
                .thenReturn((new AudioAttributes.Builder()).build());

        mHelper = spy(new AudioRoutingHelper(mContext));
        doReturn(List.of(mAudioStrategy)).when(mHelper).getAudioProductStrategies();
    }

    @Test
    public void getMatchedDeviceAttributes_correctDeviceAudioDirection() {
        when(mDeviceInfoOutput.getType()).thenReturn(AudioDeviceInfo.TYPE_BLE_HEADSET);
        when(mDeviceInfoInput.getType()).thenReturn(AudioDeviceInfo.TYPE_BLE_HEADSET);

        AudioDeviceAttributes attributesOutput = new AudioDeviceAttributes(mDeviceInfoOutput);
        AudioDeviceAttributes resultAudioDeviceAttributes =
                mHelper.getMatchedDeviceAttributes(
                        mCachedBluetoothDeviceOutput,
                        List.of(AudioDeviceInfo.TYPE_BLE_HEADSET),
                        (
                        /* isOutput */ true));
        assertThat(resultAudioDeviceAttributes.equals(attributesOutput));

        resultAudioDeviceAttributes =
                mHelper.getMatchedDeviceAttributes(
                        mCachedBluetoothDeviceOutput,
                        List.of(AudioDeviceInfo.TYPE_BLE_HEADSET),
                        (
                        /* isOutput */ false));
        assertThat(resultAudioDeviceAttributes).isNull();

        AudioDeviceAttributes attributesInput = new AudioDeviceAttributes(mDeviceInfoInput);
        resultAudioDeviceAttributes =
                mHelper.getMatchedDeviceAttributes(
                        mCachedBluetoothDeviceInput,
                        List.of(AudioDeviceInfo.TYPE_BLE_HEADSET),
                        (
                        /* isOutput */ false));
        assertThat(resultAudioDeviceAttributes.equals(attributesInput));

        resultAudioDeviceAttributes =
                mHelper.getMatchedDeviceAttributes(
                        mCachedBluetoothDeviceInput,
                        List.of(AudioDeviceInfo.TYPE_BLE_HEADSET),
                        (
                        /* isOutput */ true));
        assertThat(resultAudioDeviceAttributes).isNull();
    }

    @Test
    public void setPreferredDeviceForMediaAudioRoute_noneAudioDeviceAttributesMatched() {
        when(mDeviceInfoOutput.getType()).thenReturn(AudioDeviceInfo.TYPE_USB_HEADSET);
        doReturn(true).when(mAudioManager).setPreferredDeviceForStrategy(any(), any());

        assertThat(mHelper.setPreferredDeviceForMediaAudioRoute(mCachedBluetoothDeviceOutput))
                .isFalse();
        verify(mAudioManager, times(1)).getDevices(eq(AudioManager.GET_DEVICES_OUTPUTS));
    }

    @Test
    public void setPreferredDeviceForMediaAudioRoute_success() {
        AudioDeviceAttributes deviceOutputAttributes = new AudioDeviceAttributes(mDeviceInfoOutput);
        AudioAttributes outputAttributes =
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build();

        when(mDeviceInfoOutput.getType()).thenReturn(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
        when(mAudioStrategy.supportsAudioAttributes(eq(outputAttributes))).thenReturn(true);
        doReturn(true).when(mAudioManager).setPreferredDeviceForStrategy(any(), any());
        doReturn(deviceOutputAttributes).when(mAudioManager).getPreferredDeviceForStrategy(any());
        doReturn(true).when(mAudioManager).removePreferredDeviceForStrategy(any());

        assertThat(mHelper.setPreferredDeviceForMediaAudioRoute(mCachedBluetoothDeviceOutput))
                .isTrue();

        verify(mAudioManager, times(1)).getPreferredDeviceForStrategy(mAudioStrategy);
        verify(mAudioManager, times(1)).removePreferredDeviceForStrategy(mAudioStrategy);
        verify(mAudioManager, times(1)).setPreferredDeviceForStrategy(any(), any());
    }
}
