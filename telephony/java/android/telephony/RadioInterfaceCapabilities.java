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

package android.telephony;

import android.util.ArraySet;

/**
 * Contains the set of available capabilities that the Radio Interface supports on this device.
 *
 * @hide
 */
public class RadioInterfaceCapabilities {

    private final ArraySet<String> mAvailableCapabilities;


    public RadioInterfaceCapabilities() {
        mAvailableCapabilities = new ArraySet<>();
    }

    /**
     * Marks a capability as available
     *
     * @param capabilityName the name of the capability
     */
    public void addAvailableCapability(
            @TelephonyManager.RadioInterfaceCapability String capabilityName) {
        mAvailableCapabilities.add(capabilityName);
    }

    /**
     * Whether the capability is available
     * @param capabilityName the name of the capability
     */
    public boolean isAvailable(String capabilityName) {
        return mAvailableCapabilities.contains(capabilityName);
    }
}
