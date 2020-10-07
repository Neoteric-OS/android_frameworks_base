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

package android.telephony.ims.stub;

import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.SipMessage;

/**
 * The {@link SipDelegate} allows a privileged application to send SIP messages as well as
 * acknowledge the receipt of incoming SIP messages delivered to the application over the existing
 * IMS registration, allowing for a single IMS registration for multiple applications.
 * <p>
 * Once the SIP delegate is created for that application,
 *   {@link ImsRegistrationImplBase#updateSipDelegateRegistration()} will be called,
 *   indicating that the application is finished setting up SipDelegates. Once
 *   registration of these features is successful, the application will start sending SIP
 *   messages to this delegate for encoding and transmission.
 * @hide
 */
public interface SipDelegate {

    /**
     * The remote RCS application will call this method when they wish to send a new outgoing
     * SIP message.
     * <p>
     * Once sent, this SIP delegate should notify the remote application of the success or
     * failure using {@link DelegateMessageCallback#onMessageSent(String)} or
     * {@link DelegateMessageCallback#onMessageSendFailure(String, int)}.
     * @param message The SIP message to be sent over the operator’s network.
     * @param configVersion The SipDelegateImsConfiguration version used to construct the
     *         SipMessage. See pDelegateImsConfiguration} for more information. If the version
     *         specified here does not match the most recently constructed
     *         SipDelegateImsConfiguration, this message should fail validation checks and
     *         {@link DelegateMessageCallback#onMessageSendFailure} should be called with code
     *         {@link SipDelegateManager#MESSAGE_FAILURE_REASON_STALE_IMS_CONFIGURATION}.
     */
    void sendMessage(SipMessage message, int configVersion);

    /**
     * The SIP dialog with the provided Call-ID has completed, it is okay to release the routing
     * resources associated with this Call-ID.
     */
    void closeDialog(String callId);

    /**
     * The remote application has received the SIP message and is processing it.
     * @param viaTransactionId The transaction ID associated with the SIP message.
     */
    void notifyMessageReceived(String viaTransactionId);

    /**
     * The remote application has either not received the SIP message or there was an error
     * processing it.
     * @param viaTransactionId The Transaction ID found in the via header field of the
     *                         previously sent {@link SipMessage}.
     * @param reason The reason why the message was not correctly received.
     */
    void notifyMessageReceiveError(String viaTransactionId,
            @SipDelegateManager.MessageFailureReason int reason);
}
