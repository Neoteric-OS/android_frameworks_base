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
oneway interface ICamInfoListener {
    /**
     * Called when information about the CAM being monitored, such as its insertion or removal,
     * has changed.
     *
     * @param slotId The ID or slot number of the monitored CAM.
     * @param updatedCamInfo A Bundle containing about the updated CAM information:
     *                       KEY_IS_SLOT_VALID: Whether the slot being monitored is valid.
     *                       KEY_CAM_PROFILE_NAME: The CAM profile name.
     */
    void onCamInfoChanged(int slotId, in Bundle updatedCamInfo);
    /**
     * Called to notify that the status of a CAM slot has been updated.
     *
     * @param slotId The ID of the updated slot.
     * @param updatedSlotInfo A Bundle containing information like insertion status an slot type:
     *                        KEY_CAM_SLOT_TYPE: Whether the slot supports PCMCIA or USB.
     *                        KEY_IS_CAM_INSERTED: The physical insertion state of the CAM.
     */
    void onSlotInfoChanged(int slotId, in Bundle updatedSlotInfo);
    /**
     * Called to notify that a new type of CAM has been inserted.
     *
     * @param slotId The ID of the updated slot.
     * @param newCamType A Bundle containing information about the newly inserted CAM type:
     *                   KEY_NEW_TYPE_CAM: The value indicating the new CAM type scenario,
     *                   such as SUPPORT_PCMCIA, NOT_SUPPORT_PCMCIA, SUPPORT_USB, NOT_SUPPORT_USB.
     */
    void onNewTypeCamInsert(int slotId, in Bundle newCamType);
}
