/**
 * Copyright (c) 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.os;

import android.gsi.GsiProgress;

/** {@hide} */
interface IAndroidOnTapService
{
    /**
     * start an AndroidOnTap procedure
     * @param size image size in bytes
     * @param userdataSize userdata size in bytes
     * @return success
     */
    boolean start(long size, long userdataSize);

    /**
     * Query the progress of the current asynchronous install operation. This
     * can be called while another operation is in progress.
     */
    GsiProgress getStartProgress();

    /**
     * Abort the start process. Note the abort call must be in a thread
     * other than the one call start() given the start won't return until it's
     * finished.
     * @return success
     */
    boolean abort();

    /**
     * @return true if the device is running an AnroidOnTap image
     */
    boolean isInUse();

    /**
     * @return true if the has an AndroidOnTap image installed
     */
    boolean isInstalled();

    /**
     * remove AndroidOnTap image if presents
     * @return success
     */
    boolean remove();

    /**
     * Enable GSI when it's not enabled, otherwise, disable it.
     * @return success
     */
    boolean toggle();

    /**
     * write the AndroidOnTap image content
     * @return success
     */
    boolean write(in byte[] buf);

    /**
     * finish write and make device to boot into the it after reboot.
     * @return success
     */
    boolean commit();
}
