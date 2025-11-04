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

package android.media.tv.extension.scan;

import android.media.tv.extension.scan.ILcnConflictListener;
import android.os.Bundle;

/**
 * @hide
 */
interface ILcnConflict {
    /**
     * Get the LCN conflict groups information.
     * If there are no conflicts, the array of Bundle is empty.
     *
     * @return an array of bundle containing Lcn conflict groups information
     *         each bundle must contain Lcn_type and service_name
     */
    Bundle[] getLcnConflictGroups();
    /**
     * Resolve LCN conflicts caused by service scans.
     *
     * @param an array of bundle of user selected lcn conflict results,
     *         each bundle must contain Lcn_type and id.
     * @return OpResult.RESULT_SUCCESS if resolve successfully else OpResult.RESULT_FAILED
     */
    int resolveLcnConflict(in Bundle[] lcnConflictSettings);
    /**
     * Set the listener to be invoked the LCN conflict event.
     *
     * @param listener ILcnConflictListener
     * @return OpResult.RESULT_SUCCESS if set successfully else OpResult.RESULT_FAILED
     */
    int setListener(in ILcnConflictListener listener);
}
