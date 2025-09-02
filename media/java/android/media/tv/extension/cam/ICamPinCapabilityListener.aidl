/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.media.tv.extension.cam;

import android.os.Bundle;

/**
 * @hide
 */
oneway interface ICamPinCapabilityListener {
    /**
     * Called to notify that the PIN capabilities of the CICAM have changed.
     *
     * @param slotId The slot ID of the corresponding CICAM.
     * @param pinCapability A Bundle containing the PIN capabilities information:
     * KEY_PIN_CAP_CAPABILITY: The PIN capability status. The value can be one of the following:
     *                         0 - The CICAM does not have PIN capability.
     *                         1 - The CICAM PIN is not cached.
     *                         2 - The CICAM can cache the PIN sent in the record.
     * KEY_PIN_CAP_DATE_TIME: The date and time of the PIN capability update.
     */
    void onCamPinCapabilityChanged(int slotId, in Bundle pinCapability);
}
