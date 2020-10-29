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

import java.util.List;

/**
 * Interface used by the framework to receive the response of the publish
 * request through {@link RcsCapabilityExchangeImplBase#publishCapabilities}
 * {@hide}
 */
interface IPublishResponseCallback {
    /**
     * Notify the framework that the command associated with this callback has failed.
     *
     * @param code The reason why the associated command has failed.
     * @throws ImsException If this {@link RcsCapabilityExchangeImplBase} instance is not
     * currently connected to the framework. This can happen if the {@link RcsFeature} is not
     * {@link ImsFeature#STATE_READY} and the {@link RcsFeature} has not received the
     * {@link ImsFeature#onFeatureReady()} callback. This may also happen in rare cases when
     * the Telephony stack has crashed.
     */
    void onCommandError(int code);


    /**
     * Provide the framework with a subsequent network response update to
     * {@link #publishCapabilities(RcsContactUceCapability, int)}.
     *
     * @param code The SIP response code sent from the network for the operation token
     *  specified.
     * @param reason The optional reason response from the network. If the network provided
     * no reason with the code, the string should be empty.
     * @throws ImsException If this {@link RcsPresenceExchangeImplBase} instance is
     * not currently connected to the framework. This can happen if the
     * {@link RcsFeature} is not {@link ImsFeature#STATE_READY} and the {@link RcsFeature} has
     * not received the {@link ImsFeature#onFeatureReady()} callback. This may also happen in
     * rare cases when the Telephony stack has crashed.
     */
    void onNetworkResponse(int code, String reason);
}
