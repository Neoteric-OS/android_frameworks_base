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

package android.telephony.ims.stub;

import android.annotation.IntDef;
import android.annotation.SystemApi;
import android.telephony.ims.RcsContactUceCapability;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Base implementation for RCS User Capability Exchange using Presence.
 *
 * @hide
 */
@SystemApi
public class RcsPresenceExchangeImplBase extends RcsCapabilityExchange {

    /**
     * The request has resulted in any other 4xx/5xx/6xx that is not covered below. No retry will be
     * attempted.
     */
    public static final int RESPONSE_SUBSCRIBE_GENERIC_FAILURE = -1;

    /**
     * The request has succeeded with a “200” message from the network.
     */
    public static final int RESPONSE_SUCCESS = 0;

    /**
     * The request has resulted in a “403” (User Not Registered) error from the network. Will retry
     * capability polling with an exponential backoff.
     */
    public static final int RESPONSE_NOT_REGISTERED = 1;

    /**
     * The request has resulted in a “403” (not authorized (Requestor)) error from the network. No
     * retry will be attempted.
     */
    public static final int RESPONSE_NOT_AUTHORIZED_FOR_PRESENCE = 2;

    /**
     * The request has resulted in a "403” (Forbidden) or other “403” error from the network and
     * will be handled the same as “404” Not found. No retry will be attempted.
     */
    public static final int RESPONSE_FORBIDDEN = 3;

    /**
     * The request has resulted in a “404” (Not found) result from the network. No retry will be
     * attempted.
     */
    public static final int RESPONSE_NOT_FOUND = 4;

    /**
     * The request has resulted in a “408” response. Retry after exponential backoff.
     */
    public static final int RESPONSE_SIP_REQUEST_TIMEOUT = 5;

    /**
     *  The network has responded with a “413” (Too Large) response from the network. Capability
     *  request contains too many items and must be shrunk before the request will be accepted.
     */
    public static final int RESPONSE_SUBSCRIBE_TOO_LARGE = 6;

    /**
     * The request has resulted in a “423” response. Retry after exponential backoff.
     */
    public static final int RESPONSE_SIP_INTERVAL_TOO_SHORT = 7;

    /**
     * The request has resulted in a “503” response. Retry after exponential backoff.
     */
    public static final int RESPONSE_SIP_SERVICE_UNAVAILABLE = 8;

    /** @hide*/
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "RESPONSE_", value = {
            RESPONSE_SUBSCRIBE_GENERIC_FAILURE,
            RESPONSE_SUCCESS,
            RESPONSE_NOT_REGISTERED,
            RESPONSE_NOT_AUTHORIZED_FOR_PRESENCE,
            RESPONSE_FORBIDDEN,
            RESPONSE_NOT_FOUND,
            RESPONSE_SIP_REQUEST_TIMEOUT,
            RESPONSE_SUBSCRIBE_TOO_LARGE,
            RESPONSE_SIP_INTERVAL_TOO_SHORT,
            RESPONSE_SIP_SERVICE_UNAVAILABLE
    })
    public @interface PresenceResponseCode {}

    /**
     * The user capabilities of one or multiple contacts have been requested.
     * This should be followed up with a call to #onCommandUpdate with an update
     * on whether or not the command completed as well as subsequent network
     * updates using #onNetworkResponse. When the operation is completed,
     * #onRequestCapabilitiesResponse should be called with the presence
     * information for the contacts specified.
     */
    public void requestCapabilities(String[] uris, int operationToken) {
        throw new UnsupportedOperationException();
    }

    /**
     * The capabilities of this device have been updated and should be published
     * to the network. The framework will expect a #onCommandUpdate call to
     * indicate whether or not this operation failed as well as network response
     * updates using #onNetworkResponse.
     */
    public void updateCapabilities(RcsContactUceCapability capabilities, int operationToken) {
        throw new UnsupportedOperationException();
    }

    /**
     * Provides the framework with SUCCESS network responses or error responses
     * if the operation created an error on the network. Responses are expected
     * from #updateCapabilities and #requestCapabilities.
     */
    public final void onNetworkResponse(@PresenceResponseCode int code, int operationToken) {
        throw new UnsupportedOperationException();
    }

    /**
     * Provides the framework with the network response to a contacts’
     * capabilities request using #requestCapabilities.
     */
    public final void onCapabilityRequestResponse(RcsContactUceCapability[] infos,
            int operationToken) {
        throw new UnsupportedOperationException();
    }

    /**
     * Notify the framework to provide a capability update using
     * #updateCapabilities. This is typically used when trying to generate an
     * initial PUBLISH for a new subscription.
     */
    public final void onNotifyUpdateCapabilites() {
        throw new UnsupportedOperationException();
    }

    /**
     * Notify the framework that the device’s capabilities have been
     * unpublished.
     */
    public final void onUnpublish() {
        throw new UnsupportedOperationException();
    }
}
