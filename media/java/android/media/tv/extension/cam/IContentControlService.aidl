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

import android.media.tv.extension.cam.ICamDrmInfoListener;
import android.os.Bundle;

/**
 * @hide
 */
interface IContentControlService {
     /**
     * Checks if the current channel is scrambled.
     *
     * @return true if the channel is scrambled, else false.
     */
    boolean isChannelScrambled();
    /**
     * Registers a listener to receive notifications when DRM information is changed by the CICAM.
     *
     * @param listener The ICamDrmInfoListener to register for receiving notifications.
     */
    void addCamDrmInfoListener(in ICamDrmInfoListener listener);
    /**
     * Unregisters a previously added listener to stop monitoring DRM info.
     *
     * @param listener The ICamDrmInfoListener that was previously registered.
     */
    void removeCamDrmInfoListener(in ICamDrmInfoListener listener);
    /**
     * Gets the DRM information for the currently watched channel. The application should obtain
     * the slotId from ICamMonitoringService#getSlotIds and verify that the region supports CAM
     * before calling this API.
     *
     * @param slotId The ID of the corresponding CICAM slot.
     * @param camDrmInfo An output Bundle that will contain the DRM information with the following keys:
     * KEY_CAM_DRM_TYPE: The DRM type. The value can be one of the following:
     *                   -1 - CAM is not capable.
     *                   0 - Copying is not restricted.
     *                   1 - No further copying is permitted.
     *                   2 - One copying is permitted.
     *                   3 - Copying is prohibited.
     * KEY_CAM_PRGM_NUM: The program number.
     * @return An integer status code {@link CamOpResult} indicating the result.
     */
    int getCamDrmInfo(int slotId, out Bundle camDrmInfo);
}
