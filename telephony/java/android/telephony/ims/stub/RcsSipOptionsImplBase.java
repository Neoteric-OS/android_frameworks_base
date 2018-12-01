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
 * Base implementation for RCS User Capability Exchange using SIP OPTIONS.
 *
 * @hide
 */
@SystemApi
public class RcsSipOptionsImplBase extends RcsCapabilityExchange {

    /**
     * Indicates a SIP response from the remote user other than 200, 480, 408,
     * 404, or 604.
     */
    public static final int RESPONSE_GENERIC_FAILURE = -1;

    /**
     * Indicates that the remote user responded with a 200 OK response.
     */
    public static final int RESPONSE_SUCCESS = 0;

    /**
     * Indicates that the remote user responded with a 480 TEMPORARY UNAVAILABLE
     * response.
     */
    public static final int RESPONSE_TEMPORARILY_UNAVAILABLE = 1;

    /**
     * Indicates that the remote user responded with a 408 REQUEST TIMEOUT
     * response.
     */
    public static final int RESPONSE_REQUEST_TIMEOUT = 2;

    /**
     * Indicates that the remote user responded with a 404 NOT FOUND response.
     */
    public static final int RESPONSE_NOT_FOUND = 3;

    /**
     * Indicates that the remote user responded with a 604 DOES NOT EXIST
     * ANYWHERE response.
     */
    public static final int RESPONSE_DOES_NOT_EXIST_ANYWHERE = 4;

    /** @hide*/
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "RESPONSE_", value = {
            RESPONSE_GENERIC_FAILURE,
            RESPONSE_SUCCESS,
            RESPONSE_TEMPORARILY_UNAVAILABLE,
            RESPONSE_REQUEST_TIMEOUT,
            RESPONSE_NOT_FOUND,
            RESPONSE_DOES_NOT_EXIST_ANYWHERE
    })
    public @interface SipResponseCode {}

    /**
     * Push ones own capabilities to a remote user via the SIP OPTIONS presence exchange mechanism
     * in order to receive the capabilities of the remove user in response.
     * The implementer must convey the response back to the caller via the
     * {@link #onCapabilityRequestResponse(int, RcsContactUceCapability, int)} method.
     */
    public void sendCapabilityRequest(String uri, RcsContactUceCapability capabilities,
            int operationToken) {
        throw new UnsupportedOperationException();
    }

    /**
     * Send the response of a SIP OPTIONS capability exchange to the framework.If {@code code} is
     * {@link #RESPONSE_SUCCESS}, info must be non-null.
     */
    public final void onCapabilityRequestResponse(@SipResponseCode int code,
            RcsContactUceCapability info, int operationToken) {
        throw new UnsupportedOperationException();
    }

    /**
     * Inform the framework of a query for this device's UCE capabilities.
     * <p>
     * The framework will respond via the
     * {@link #respondToCapabilityRequest(String, RcsContactUceCapability, int)} method.
     */
    public final void onRemoteCapabilityRequest(String uri,
            RcsContactUceCapability remoteInfo, int operationToken) {
        throw new UnsupportedOperationException();
    }

    /**
     * Respond to a remote capability request from the contact specified with the capabilities of
     * this device.
     * <p>
     * The framework will use the same token and uri as what was passed in to
     * {@link #onRemoteCapabilityRequest(String, RcsContactUceCapability, int)}.
     */
    public void respondToCapabilityRequest(String contactUri,
            RcsContactUceCapability ownCapabilities, int operationToken) {
        throw new UnsupportedOperationException();
    }

    /**
     * Respond to a remote capability request from the contact specified with the specified error.
     * <p>
     * The framework will use the same token and uri as what was passed in to
     * {@link #onRemoteCapabilityRequest(String, RcsContactUceCapability, int)}.
     */
    public void respondToCapabiltyRequestWithError(String contactUri,
            @SipResponseCode int code, String reasonPhrase, int operationToken) {
        throw new UnsupportedOperationException();
    }
}
