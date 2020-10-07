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

import android.annotation.NonNull;
import android.telephony.ims.DelegateRegistrationState;
import android.telephony.ims.DelegateRequest;
import android.telephony.ims.SipDelegateImsConfiguration;
import android.telephony.ims.SipDelegateManager;

/**
 * Callback interface to notify a remote application of the following:
 * - notify the RCS application that the SIP IMS configuration associated with this SipDelegate has
 *     changed, and
 * - notify the RCS application of changes to the IMS registration of the feature tags associated with
 *     this SipDelegate.
 * @hide
 */
public interface DelegateStateCallback {

    /**
     * Must be called after {@link SipTransportImplBase#destroySipDelegate} to notify the framework and
     * remote application that this SipDelegate has been destroyed.
     * @param reasonCode The reason for closing this delegate.
     */
    void onDestroyed(@SipDelegateManager.SipDelegateDestroyReason int reasonCode);

    /**
     * Notify the remote application of a configuration change associated with this SipDelegate.
     * <p>
     * The remote application will not be able to proceed sending SIP messages until after this
     * configuration is sent  the first time, so this configuration should be sent as soon as the
     * SipDelegate has access to these configuration parameters.
     * <p>
     * Incoming SIP messages should not be routed to the remote application until AFTER this configuration
     * change is sent to ensure that the remote application can respond correctly. Similarly, if there is
     * an event that triggers the IMS configuration to change, incoming SIP messages routing should be
     * delayed until the SipDelegate sends the IMS configuration change event to prevent conditions where
     * the remote application is using a stale IMS configuration.
     */
    void onImsConfigurationChanged(@NonNull SipDelegateImsConfiguration config);

    /**
     * The SipDelegate has modified the IMS registration state of the RCS feature tags that were requested
     * as part of the initial {@link DelegateRequest}. See {@link DelegateRegistrationState} for more
     * information about how IMS
     * Registration state should be communicated the associated SipDelegateConnection in cases such as
     * IMS deregistration, handover, PDN change, provisioning changes, etc…
     *
     * @param registrationState The current network IMS registration state for all feature tags associated
     * with this SipDelegate.
     * <p>
     * Note: Even after the status of the feature tags are updated here to deregistered, the
     * SipDelegate must still be able to handle these messages and call
     * {@link DelegateMessageCallback#onMessageSendFailure} to notify the RCS application that the
     * message was not sent.
     */
    void onFeatureTagRegistrationChanged(DelegateRegistrationState registrationState);

}
