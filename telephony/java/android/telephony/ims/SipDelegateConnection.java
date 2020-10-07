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

package android.telephony.ims;

/**
 * Represents a connection to the SIP delegate that will handle messages sent by this connection
 * as well as routing messages received from the network back to this connection if they are
 * associated with the registered feature tags.
 *
 * @see SipDelegateManager#createSipDelegate
 * @hide
 */
public interface SipDelegateConnection {

    /**
     * Send a SIP message to the SIP delegate to be sent over the carrier’s network.
     * @param sipMessage The SipMessage to be sent.
     * @param configVersion The SipDelegateImsConfiguration version used to construct the
     *                      SipMessage.
     * See {@link SipDelegateImsConfiguration#getVersion} for more information on this parameter and
     *                      why it is used.
     */
    void sendMessage(SipMessage sipMessage, int configVersion);

    /**
     * Notify the SIP delegate that the SIP message has been received and is being processed.
     * @param viaTransactionId The transaction ID associated with the via branch parameter.
     */
    void notifyMessageReceived(String viaTransactionId);

    /**
     * Notify the SIP delegate that the SIP message has been received, however there was an
     * error processing it.
     * @param viaTransactionId The transaction ID associated with the via branch parameter.
     * @param reason The reason why the error occurred.
     */
    void notifyMessageReceiveError(String viaTransactionId, int reason);
}
