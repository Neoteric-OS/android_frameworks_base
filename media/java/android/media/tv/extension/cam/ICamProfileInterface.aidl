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
interface ICamProfileInterface {
    /**
     * Gets CAM service update information for a specific slot.
     *
     * @param slotNumber The ID of the slot to query.
     * @return A Bundle containing the CAM service update information:
     * KEY_CAM_PROFILE_NAME: The CAM profile name.
     * KEY_NEED_SERVICE_UPDATE: The service update mode. The value can be one of the following:
     *                          -1 - Invalid update mode
     *                           0 - Initial Auto tune
     *                           1 - CAM NIT Update
     *                           2 - Update Auto Tune (urgent request)
     *                           3 - Advanced warning
     *                           4 - Scheduled
     *                           5 - Acknowledgement only
     * KEY_CAM_DELIVERY_SYSTEM_HINT: The hint about the delivery system associated with the profile.
     * KEY_CAM_PROFILE_TYPE: The profile type.
     */
    Bundle getCamServiceUpdateInfo(int slotNumber);
    /**
     * Requests the CAM TV Input Service to resend the CAM info update broadcast message.
     * This is typically used when an application boots up to ensure it has the latest profile
     * information if a profile update occurred during boot.
     */
    void requestResendProfileInfoBroadcastACON();
    /**
     * Checks if CAM scanning is enabled for the current profile based on its settings.
     *
     * @return true if CAM scanning is enabled, false otherwise.
     */
    boolean isCamScanEnabled();
}
