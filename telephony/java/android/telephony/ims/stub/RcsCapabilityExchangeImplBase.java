/*
 * Copyright (c) 2020 The Android Open Source Project
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
import android.annotation.NonNull;
import android.net.Uri;
import android.telephony.ims.aidl.IOptionsResponseCallback;
import android.telephony.ims.aidl.IPublishResponseCallback;
import android.telephony.ims.aidl.ISubscribeResponseCallback;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * Base class for different types of Capability exchange.
 * @hide
 */
public class RcsCapabilityExchangeImplBase {

    private static final String LOG_TAG = "RcsCapExchangeImplBase";

    /**  Service is unknown. */
    public static final int COMMAND_CODE_SERVICE_UNKNOWN = 0;

    /** The command failed with an unknown error. */
    public static final int COMMAND_CODE_GENERIC_FAILURE = 1;

    /**  Invalid parameter(s). */
    public static final int COMMAND_CODE_INVALID_PARAM = 2;

    /**  Fetch error. */
    public static final int COMMAND_CODE_FETCH_ERROR = 3;

    /**  Request timed out. */
    public static final int COMMAND_CODE_REQUEST_TIMEOUT = 4;

    /**  Failure due to insufficient memory available. */
    public static final int COMMAND_CODE_INSUFFICIENT_MEMORY = 5;

    /**  Network connection is lost. */
    public static final int COMMAND_CODE_LOST_NETWORK_CONNECTION = 6;

    /**  Requested feature/resource is not supported. */
    public static final int COMMAND_CODE_NOT_SUPPORTED = 7;

    /**  Contact or resource is not found. */
    public static final int COMMAND_CODE_NOT_FOUND = 8;

    /**  Service is not available. */
    public static final int COMMAND_CODE_SERVICE_UNAVAILABLE = 9;

    /** Command resulted in no change in state, ignoring. */
    public static final int COMMAND_CODE_NO_CHANGE = 10;

    /**@hide*/
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "COMMAND_CODE_", value = {
            COMMAND_CODE_SERVICE_UNKNOWN,
            COMMAND_CODE_GENERIC_FAILURE,
            COMMAND_CODE_INVALID_PARAM,
            COMMAND_CODE_FETCH_ERROR,
            COMMAND_CODE_REQUEST_TIMEOUT,
            COMMAND_CODE_INSUFFICIENT_MEMORY,
            COMMAND_CODE_LOST_NETWORK_CONNECTION,
            COMMAND_CODE_NOT_SUPPORTED,
            COMMAND_CODE_NOT_FOUND,
            COMMAND_CODE_SERVICE_UNAVAILABLE,
            COMMAND_CODE_NO_CHANGE
    })
    public @interface CommandCode {}

    /**
     * The user capabilities of one or multiple contacts have been requested by the framework.
     * <p>
     * The implementer must follow up this call with an {@link #onCommandUpdate} call to indicate
     * whether or not this operation succeeded. The response from the network to the SUBSCRIBE
     * request must be sent back to the framework using
     * {@link #onSubscribeNetworkResponse(int, String, int)}. As NOTIFY requests come in from the
     * network, the requested contact’s capabilities should be sent back to the framework using
     * {@link #onSubscribeNotifyRequest} and {@link onSubscribeResourceTerminated}
     * should be called with the presence information for the contacts specified.
     * <p>
     * Once the subscription is terminated, {@link #onSubscriptionTerminated} must be called for
     * the framework to finish listening for NOTIFY responses.
     * @param uris A {@link List} of the {@link Uri}s that the framework is requesting the UCE
     * capabilities for.
     * @param cb The callback of the subscribe request.
     * @hide
     */
    public void subscribeForCapabilities(@NonNull List<Uri> uris,
            @NonNull ISubscribeResponseCallback cb) {
        Log.w(LOG_TAG, "subscribeForCapabilities called with no implementation.");
    }

    /**
     * The capabilities of this device have been updated and should be published to the network.
     * <p>
     * The implementer must follow up this call with an {@link #onCommandUpdate(int, int)} call to
     * indicate whether or not this operation succeeded. If this operation succeeds, network
     * response updates should be sent to the framework using
     * {@link #onPublishNetworkResponse(int, String, int)}.
     * @param pidfXml The XML PIDF document containing the capabilities of this device to be sent
     * to the carrier’s presence server.
     * @param cb The callback of the publish request
     * @hide
     */
    public void publishCapabilities(@NonNull String pidfXml, @NonNull IPublishResponseCallback cb) {
        Log.w(LOG_TAG, "publishCapabilities called with no implementation.");
    }

    /**
     * Push one's own capabilities to a remote user via the SIP OPTIONS presence exchange mechanism
     * in order to receive the capabilities of the remote user in response.
     * <p>
     * The implementer must call {@link #onCapabilityRequestResponse} to send the response of this
     * query back to the framework.
     * @param contactUri The URI of the remote user that we wish to get the capabilities of.
     * @param myCapabilities The capabilities of this device to send to the remote user.
     * @param callback The callback of this request which is sent from the remote user.
     * @hide
     */
    public void sendOptionsCapabilityRequest(@NonNull Uri contactUri,
            @NonNull List<String> myCapabilities, @NonNull IOptionsResponseCallback callback) {
        Log.w(LOG_TAG, "sendOptionsCapabilityRequest called with no implementation.");
    }
}
