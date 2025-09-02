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
oneway interface ICamPinStatusListener {
    /**
     * Called to notify the result of a PIN code validation by the CICAM.
     *
     * @param slotId The slot ID of the corresponding CICAM.
     * @param pinValidationReply A Bundle that provides the status of the PIN code validation
     * from the CICAM, with the following keys:
     * KEY_PIN_VALIDATION_RESULT: The validation result. Possible values are:
     *                            0 - Success
     *                            1 - Invalid SlotId
     *                            2 - CICAM not inserted
     *                            3 - CICAM does not support PIN capability
     * KEY_PIN_VALIDATION_PINCODE_STATUS: The status of the PIN code. Possible values are:
     *                                    0 - Incorrect PIN passed for CICAM PIN verification.
     *                                    1 - The host may retry the CAM PIN entry.
     *                                    2 - Correct PIN passed for CICAM PIN verification.
     *                                    3 - The host is not required to retry the CAM PIN entry.
     */
    void onCamPinValidationReply(int slotId, in Bundle pinValidationReply);
}
