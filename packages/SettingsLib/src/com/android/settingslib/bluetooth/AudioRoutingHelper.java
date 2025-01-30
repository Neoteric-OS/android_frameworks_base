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

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiopolicy.AudioProductStrategy;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** A helper class to configure the audio routing for BT devices. */
public class AudioRoutingHelper {

    private static final String TAG = "AudioRoutingHelper";

    protected final AudioManager mAudioManager;

    public AudioRoutingHelper(Context context) {
        mAudioManager = context.getSystemService(AudioManager.class);
    }

    /**
     * Gets the list of {@link AudioProductStrategy} referred by the given list of usage values
     * defined in {@link AudioAttributes}
     */
    public List<AudioProductStrategy> getSupportedStrategies(int[] attributeSdkUsageList) {
        final List<AudioAttributes> audioAttrList = new ArrayList<>(attributeSdkUsageList.length);
        for (int attributeSdkUsage : attributeSdkUsageList) {
            audioAttrList.add(new AudioAttributes.Builder().setUsage(attributeSdkUsage).build());
        }

        final List<AudioProductStrategy> allStrategies = getAudioProductStrategies();
        final List<AudioProductStrategy> supportedStrategies = new ArrayList<>();
        for (AudioProductStrategy strategy : allStrategies) {
            for (AudioAttributes audioAttr : audioAttrList) {
                if (strategy.supportsAudioAttributes(audioAttr)) {
                    supportedStrategies.add(strategy);
                }
            }
        }

        return supportedStrategies.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Gets the matched output {@link AudioDeviceAttributes} for {@code device}.
     *
     * <p>Will also try to match the {@link CachedBluetoothDevice#getSubDevice()} and {@link
     * CachedBluetoothDevice#getMemberDevice()} of {@code device}
     *
     * @param device the {@link CachedBluetoothDevice}
     * @param audioDeviceTypes the audio device types to match attributes
     * @param isOutput the device's audio direction, will result in {@link
     *     AudioManager.AudioDeviceRole} or
     * @return the requested AudioDeviceAttributes or {@code null} if not match
     */
    @Nullable
    public AudioDeviceAttributes getMatchedDeviceAttributes(
            @Nullable CachedBluetoothDevice device,
            List<Integer> audioDeviceTypes,
            boolean isOutput) {
        if (device == null) {
            Log.e(TAG, "getMatchedDeviceAttributes: device is null");
            return null;
        }

        if (audioDeviceTypes.isEmpty()) {
            Log.e(TAG, "getMatchedDeviceAttributes: audioDeviceTypes list is empty");
            return null;
        }

        int deviceAudioDirection =
                isOutput ? AudioManager.GET_DEVICES_OUTPUTS : AudioManager.GET_DEVICES_INPUTS;
        AudioDeviceInfo[] audioDevices = mAudioManager.getDevices(deviceAudioDirection);
        for (AudioDeviceInfo audioDevice : audioDevices) {
            if (audioDeviceTypes.contains(audioDevice.getType())) {
                if (matchAddress(device, audioDevice)) {
                    return new AudioDeviceAttributes(audioDevice);
                }
            }
        }
        return null;
    }

    @VisibleForTesting
    public List<AudioProductStrategy> getAudioProductStrategies() {
        return AudioManager.getAudioProductStrategies();
    }

    protected boolean matchAddress(CachedBluetoothDevice device, AudioDeviceInfo audioDevice) {
        final String audioDeviceAddress = audioDevice.getAddress();
        final CachedBluetoothDevice subDevice = device.getSubDevice();
        final Set<CachedBluetoothDevice> memberDevices = device.getMemberDevice();

        return device.getAddress().equals(audioDeviceAddress)
                || (subDevice != null && subDevice.getAddress().equals(audioDeviceAddress))
                || (!memberDevices.isEmpty()
                        && memberDevices.stream()
                                .anyMatch(m -> m.getAddress().equals(audioDeviceAddress)));
    }

    protected boolean setPreferredDeviceForStrategies(
            List<AudioProductStrategy> strategies, AudioDeviceAttributes audioDevice) {
        boolean status = true;
        for (AudioProductStrategy strategy : strategies) {
            status &= mAudioManager.setPreferredDeviceForStrategy(strategy, audioDevice);
        }

        return status;
    }

    protected boolean removePreferredDeviceForStrategies(List<AudioProductStrategy> strategies) {
        boolean status = true;
        for (AudioProductStrategy strategy : strategies) {
            if (mAudioManager.getPreferredDeviceForStrategy(strategy) != null) {
                status &= mAudioManager.removePreferredDeviceForStrategy(strategy);
            }
        }

        return status;
    }
}
