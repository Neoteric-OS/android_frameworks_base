/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.audiopolicy.AudioProductStrategy;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

/** A helper class to configure the audio routing for hearing aids. */
public class HearingAidAudioRoutingHelper extends AudioRoutingHelper {

    private static final String TAG = "HearingAidAudioRoutingHelper";

    public HearingAidAudioRoutingHelper(Context context) {
        super(context);
    }

    /**
     * Sets the preferred device for the given strategies.
     *
     * @param supportedStrategies A list of {@link AudioProductStrategy} used to configure audio
     *                            routing
     * @param hearingDevice {@link AudioDeviceAttributes} of the device to be changed in audio
     *                      routing
     * @param routingValue one of value defined in
     *                     {@link HearingAidAudioRoutingConstants.RoutingValue}, denotes routing
     *                     destination.
     * @return {code true} if the routing value successfully configure
     */
    public boolean setPreferredDeviceRoutingStrategies(
            List<AudioProductStrategy> supportedStrategies, AudioDeviceAttributes hearingDevice,
            @HearingAidAudioRoutingConstants.RoutingValue int routingValue) {
        boolean status;
        switch (routingValue) {
            case HearingAidAudioRoutingConstants.RoutingValue.AUTO:
                status = removePreferredDeviceForStrategies(supportedStrategies);
                return status;
            case HearingAidAudioRoutingConstants.RoutingValue.HEARING_DEVICE:
                status = removePreferredDeviceForStrategies(supportedStrategies);
                status &= setPreferredDeviceForStrategies(supportedStrategies, hearingDevice);
                return status;
            case HearingAidAudioRoutingConstants.RoutingValue.DEVICE_SPEAKER:
                status = removePreferredDeviceForStrategies(supportedStrategies);
                status &= setPreferredDeviceForStrategies(supportedStrategies,
                        HearingAidAudioRoutingConstants.DEVICE_SPEAKER_OUT);
                return status;
            default:
                throw new IllegalArgumentException("Unexpected routingValue: " + routingValue);
        }
    }

    /**
     * Gets the matched hearing device {@link AudioDeviceAttributes} for {@code device}.
     *
     * <p>Will also try to match the {@link CachedBluetoothDevice#getSubDevice()} of {@code device}
     *
     * @param device the {@link CachedBluetoothDevice} need to be hearing aid device
     * @return the requested AudioDeviceAttributes or {@code null} if not match
     */
    @Nullable
    public AudioDeviceAttributes getMatchedHearingDeviceAttributes(CachedBluetoothDevice device) {
        if (device == null || !device.isHearingAidDevice()) {
            return null;
        }

        // ASHA for TYPE_HEARING_AID, HAP for TYPE_BLE_HEADSET
        return getMatchedDeviceAttributes(
                device,
                Arrays.asList(
                        AudioDeviceInfo.TYPE_HEARING_AID,
                        AudioDeviceInfo.TYPE_BLE_HEADSET), /* isOutput */
                true);
    }

    /**
     * Gets the matched input hearing device {@link AudioDeviceAttributes} for {@code device}.
     *
     * <p>Will also try to match the {@link CachedBluetoothDevice#getSubDevice()} and
     * {@link CachedBluetoothDevice#getMemberDevice()} of {@code device}
     *
     * @param device the {@link CachedBluetoothDevice} need to be hearing aid device
     * @return the requested AudioDeviceAttributes or {@code null} if not match
     */
    @Nullable
    private AudioDeviceAttributes getMatchedHearingDeviceAttributesInput(
            @Nullable CachedBluetoothDevice device) {
        if (device == null || !device.isHearingAidDevice()) {
            return null;
        }

        // ASHA for TYPE_HEARING_AID, HAP for TYPE_BLE_HEADSET
        return getMatchedDeviceAttributes(
                device,
                List.of(
                        AudioDeviceInfo.TYPE_HEARING_AID,
                        AudioDeviceInfo.TYPE_BLE_HEADSET), /* isOutput */
                false);
    }
}
