/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.telephony.ims.aidl;

import android.net.Uri;
import android.telephony.ims.RcsContactTerminatedReason;

import java.util.List;
import java.util.Map;

/**
 * Interface used by the framework to receive the response of the subscribe
 * request through {@link RcsCapabilityExchangeImplBase#subscribeForCapabilities}
 * {@hide}
 */
interface ISubscribeResponseCallback {
    /**
     * Notify the framework that the command associated with this callback has failed.
     *
     * @param code The reason why the associated command has failed.
     * @throws ImsException If this {@link RcsCapabilityExchangeImplBase} instance is
     * not currently connected to the framework. This can happen if the
     * {@link RcsFeature} is not {@link ImsFeature#STATE_READY} and the {@link RcsFeature} has
     * not received the {@link ImsFeature#onFeatureReady()} callback. This may also happen in
     * rare cases when the Telephony stack has crashed.
     */
    void onCommandError(int code);

    /**
     * Notify the framework  of the response to the SUBSCRIBE request from
     * {@link #subscribeForCapabilities(RcsContactUceCapability, int)}.
     *
     * @param code The SIP response code sent from the network for the operation token
     * specified.
     * @param reason The optional reason response from the network. If the network provided
     * no reason with the code, the string should be empty.
     * @throws ImsException If this {@link RcsCapabilityExchangeImplBase} instance is not
     * currently connected to the framework. This can happen if the {@link RcsFeature} is not
     * {@link ImsFeature#STATE_READY} and the {@link RcsFeature} has not received
     * the {@link ImsFeature#onFeatureReady()} callback. This may also happen in rare cases
     * when the Telephony stack has crashed.
     */
    void onNetworkResponse(int code, in String reason);

    /**
     * Provides the framework with latest XML PIDF documents included in the
     * network response for the requested  contacts' capabilities requested by the
     * Framework  using {@link #requestCapabilities(List, int)}. This should be
     * called every time a new NOTIFY event is received with new capability
     * information.
     *
     * @throws ImsException If this {@link RcsCapabilityExchangeImplBase} instance is
     * not currently
     * connected to the framework. This can happen if the {@link RcsFeature} is not
     * {@link ImsFeature#STATE_READY} and the {@link RcsFeature} has not received
     * the {@link ImsFeature#onFeatureReady()} callback. This may also happen in
     * rare cases when the
     * Telephony stack has crashed.
     */
    void onNotifyCapabilitiesUpdate(in List<String> pidfXmls);

    /**
     * A resource in the resource list for the presence subscribe event has been terminated.
     * <p>
     * This allows the framework to know that there will not be any capability information for
     * a specific contact URI that they subscribed for.
     */
    void onResourceTerminated(in List<RcsContactTerminatedReason> uriTerminatedReason);

    /**
     * The subscription associated with a previous #requestCapabilities operation has been
     * terminated. This will mostly be due to the subscription expiring, but may also happen
     * due to an error.
     * <p>
     * This allows the framework to know that there will no longer be any capability updates
     * for the requested operationToken.
     */
    void onTerminated(in String reason, in String retryAfter);
}
