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
 * Interface used by the framework to receive the response from the remote user
 * through {@link RcsCapabilityExchangeImplBase#sendOptionsCapabilityRequest}
 * {@hide}
 */
interface IOptionsResponseCallback {
    /**
     * Notify the framework that the command associated with this callback has failed.
     *
     * @param code The reason why the associated command has failed.
     * @throws ImsException If this {@link RcsCapabilityExchangeImplBase} instance is not
     * currently connected to the framework. This can happen if the {@link RcsFeature} is not
     * {@link ImsFeature#STATE_READY} and the {@link RcsFeature} has not received
     * the {@link ImsFeature#onFeatureReady()} callback. This may also happen in rare cases
     * when the Telephony stack has crashed.
     */
    void onCommandError(int code);

    /**
     * Send the response of a SIP OPTIONS capability exchange to the framework.
     *
     * @param code The SIP response code that was sent by the network in response to the
     * request sent by {@link #sendOptionsCapabilityRequest}.
     * @param reason The optional SIP response reason sent by the network. If none was sent,
     * this should be an empty string.
     * @param theirCaps the contact's UCE capabilities associated with the capability request.
     * @throws ImsException If this {@link RcsSipOptionsImplBase} instance is not currently
     * connected to the framework. This can happen if the {@link RcsFeature} is not
     * {@link ImsFeature#STATE_READY} and the {@link RcsFeature} has not received the
     * {@link ImsFeature#onFeatureReady()} callback. This may also happen in rare cases when
     * the Telephony stack has crashed.
     */
    void onNetworkResponse(int code, String reason, in List<String> theirCaps);
}
