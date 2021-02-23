/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.net;

import android.annotation.LongDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A container for transport-specific capabilities which is returned by
 * {@link NetworkCapabilities#getTransportInfo()}. Specific networks
 * may provide concrete implementations of this interface.
 * @see android.net.wifi.aware.WifiAwareNetworkInfo
 * @see android.net.wifi.WifiInfo
 */
public interface TransportInfo {

    /**
     * Mechanism to support redaction of fields in TransportInfo that are guarded by specific
     * app permissions.
     **/
    /**
     * Don't redact any fields since the receiving app holds all the necessary permissions.
     *
     * @hide
     */
    @SystemApi
    long REDACTION_NONE = 0;

    /**
     * Redact any fields that needs {@link android.Manifest.permission#ACCESS_FINE_LOCATION}
     * permission since the receiving app does not hold this permission or the location toggle
     * is off.
     *
     * @see android.Manifest.permission#ACCESS_FINE_LOCATION
     * @hide
     */
    @SystemApi
    long REDACTION_ACCESS_FINE_LOCATION = 1 << 0;

    /**
     * Redact any fields that needs {@link android.Manifest.permission#LOCAL_MAC_ADDRESS}
     * permission since the receiving app does not hold this permission.
     *
     * @see android.Manifest.permission#LOCAL_MAC_ADDRESS
     * @hide
     */
    @SystemApi
    long REDACTION_LOCAL_MAC_ADDRESS = 1 << 1;

    /**
     *
     * Redact any fields that needs {@link android.Manifest.permission#NETWORK_SETTINGS}
     * permission since the receiving app does not hold this permission.
     *
     * @see android.Manifest.permission#NETWORK_SETTINGS
     * @hide
     */
    @SystemApi
    long REDACTION_NETWORK_SETTINGS = 1 << 2;

    /**
     * Constant to use for redacting all the fields.
     * @hide
     */
    @SystemApi
    long REDACTION_ALL = -1L;

    /** @hide */
    @LongDef(flag = true, prefix = { "REDACTION_" }, value = {
            REDACTION_NONE,
            REDACTION_ACCESS_FINE_LOCATION,
            REDACTION_LOCAL_MAC_ADDRESS,
            REDACTION_NETWORK_SETTINGS,
            REDACTION_ALL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RedactionType {}

    /**
     * Create a copy of a {@link TransportInfo} with some fields redacted based on the permissions
     * held by the receiving app.
     *
     * <p>
     * Usage by connectivity stack:
     * <ul>
     * <li> Connectivity stack will invoke {@link #getRequiredRedactions()} to find the list
     * of redactions that are required by this {@link TransportInfo} instance.</li>
     * <li> Connectivity stack then loops through each bit in the bitmask returned and checks if the
     * receiving app holds the corresponding permission.
     * <ul>
     * <li> If the app holds the corresponding permission, the bit is cleared from the
     * |redactions| bitmask. </li>
     * <li> If the app does not hold the corresponding permission, the bit is retained in the
     * |redactions| bitmask. </li>
     * </ul>
     * <li> Connectivity stack then invokes {@link #makeCopy(long)} with the necessary |redactions|
     * to create a copy to send to the corresponding app. </li>
     * </ul>
     * </p>
     *
     * @param redactions bitmask of |REDACTION_| constants that correspond to the
     *                   redactions that needs to be performed on this instance.
     * @return Copy of this instance with the necessary redactions.
     * @hide
     */
    @SystemApi
    @NonNull
    default TransportInfo makeCopy(@RedactionType long redactions) {
        return this;
    }

    /**
     * Returns a bitmask of |REDACTION_| constants which indicate the necessary redactions
     * (based on the permissions held by the receiving app) to be performed on this
     * TransportInfo.
     *
     * @return bitmask of |REDACTION_| constants.
     * @see #makeCopy(int)
     * @hide
     */
    @SystemApi
    default @RedactionType long getRequiredRedactions() {
        return REDACTION_NONE;
    }
}
