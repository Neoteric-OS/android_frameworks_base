/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.app.timezone;

import android.app.timezone.IInstallOperationCallback;
import android.app.timezone.RulesState;
import android.os.ParcelFileDescriptor;

 /**
  * Interface to the TimeZone Rules Manager Service that can be used to query the current time zone
  * rules, install a time zone bundle (a zip containing new rules) and uninstall a previously
  * installed time zone bundle.
  *
  * <p>This interface is only intended for system apps to call. They should use the
  * {@link android.app.timezone.TimeZoneRulesManager} class rather than going through this
  * Binder interface directly.
  *
  * {@hide}
  */
interface IRulesManager {

    /**
     * Returns information about the current time zone rules state such as the IANA version of
     * the system and any currently installed bundle. This method is intended to allow clients to
     * determine if the current state can be improved; for example by passing the information to a
     * server that may provide a new bundle for download.
     */
    RulesState getRulesState();

    /**
     * Requests installation of the supplied bundle. The bundle must have been checked for integrity
     * by the caller or have been received via a trusted mechanism.
     *
     * @param timeZoneBundle the file descriptor for the bundle
     * @param callback the {@link IInstallOperationCallback} to receive callbacks related to the
     *     installation
     * @return zero if the installation will be attempted; nonzero on error
     */
    int requestInstall(in ParcelFileDescriptor timeZoneBundle, IInstallOperationCallback callback);

    /**
     * Requests uninstallation of the currently installed bundle (leaving the device with no
     * bundle installed).
     *
     * @param callback the {@link IInstallOperationCallback} to receive callbacks related to the
     *     uninstall
     * @return zero if the uninstallation will be attempted; nonzero on error
     */
    int requestUninstall(IInstallOperationCallback callback);

    /**
     * Records the fact that a time zone check operation triggered by the system is now complete.
     * The token passed should be the one presented when the check was triggered.
     *
     * @param token the token presented when the check was triggered
     * @param success true if the check was successful, false if it was not
     */
    void checkComplete(in byte[] token, boolean success);
}
