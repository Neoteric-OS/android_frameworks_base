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

import android.annotation.NonNull;

import java.util.List;

/**
 * The callback associated with a {@link SipDelegateConnection} that manages the state of the
 * SipDelegateConnection.
 * <p>
 *
 * After {@link SipDelegateManager#createSipDelegate} is used to request a new SipDelegateConnection be
 * created, {@link #onCreated} will be called with the {@link SipDelegateConnection} instance that
 * will be used to communicate with the remote SipDelegate. After, {@link #onFeatureTagStatusChanged}
 * will always be called once with the current status of the feature tags that have been requested. The
 * application may receive multiple {@link #onFeatureTagStatusChanged} callbacks over the lifetime of
 * the SipDelegateConnection, which will signal changes to how SIP messages associated with those
 * feature tags should be handled.
 * <p>
 * In order to start sending SIP messages, the SIP configuration parameters will need to be received,
 * so the messaging application should make no assumptions about these parameters and wait until {@link
 * #onImsConfigurationChanged(SipDelegateImsConfiguration)} has been called. This is guaranteed to
 * happen after the first {@link #onFeatureTagStatusChanged} if there is at least one feature tag that has been successfully
 * associated with this SipDelegateConnection. If all feature tags were denied, no IMS configuration
 * will be sent.
 * <p>
 * The SipDelegateConnection will stay associated with this RCS application until either the RCS
 * application calls {@link SipDelegateManager#destroySipDelegate} or telephony destroys the
 * SipdelegateConnection. Telephony destroying the SipDelegateConnection is rare and will only happen in
 * rare cases, such as if telephony or IMS service dies unexpectedly. See
 * {@link SipDelegateManager.SipDelegateDestroyReason} reasons for more information on all of the
 * cases where the SipDelegateConnection will be destroyed.
 * @hide
 */
public interface DelegateConnectionStateCallback {

    /**
     * A SipDelegateConnection has been created for the DelegateRequest.
     */
    void onCreated(SipDelegateConnection c);

    /**
     * The status of the RCS feature tags that were requested as part of the initial
     * {@link DelegateRequest}.
     * There are four states that each RCS feature tag can be in: registered, deregistering,
     * deregistered, and denied.
     * <p>
     * When a
     * feature tag is considered registered, SIP messages associated with that feature tag may be sent and
     * received freely. When a feature tag is deregistering, the network IMS registration still contains
     * the feature tag, however the SipDelegate is in the progress of modifying the IMS registration to
     * remove this feature tag and requires application to perform an action before the IMS registration
     * can change. The specific action required for the SipDelegate to continue modifying the IMS
     * registration can be found in the definition of each
     * {@link DelegateRegistrationState.DeregisteringReason}. When a feature tag is in the deregistered
     * state ,new out-of-dialog SIP messages for that feature tag will be rejected, however due to
     * network race conditions, the RCS application should still be able to handle new out-of-dialog SIP
     * requests from the network. This may not be possible, however, if the IMS registration itself was
     * lost. See the {@link DelegateRegistrationState.DeregisteredReason}
     * reasons for more information on how SIP messages are handled in each of these cases. If a feature
     * tag is denied, no incoming messages will be routed to the associated
     * {@link DelegateConnectionMessageCallback} and all outgoing SIP messages related to this
     * feature tag will be rejected. See (@link DeniedReason)
     * reasons for more information about the conditions when this will happen. The set of feature tags
     * contained in the registered, deregistering, deregistered, and denied lists will always equal the
     * set of feature tags requested in the initial {@link DelegateRequest}.
     * <p>
     * Transitions of feature tags from registered to deregistered and vice-versa may happen quite often,
     * however transitions to/from denied are rare and only occur if the user has changed the role of your
     * application to add/remove support for one or more requested feature tags or carrier provisioning
     * has enabled or disabled single registration. Please see
     * {@link SipDelegateManager.DeniedReason} reasons for an
     * explanation of each of these cases as well as what may cause them to change.
     * @param registrationState The new IMS registration state of each of the feature tags associated with
     * the SipDelegate.See {@link DelegateRegistrationState}.
     * @param deniedFeatureTags A List of Pairs, where the first parameter contains the feature tags
     *    associated with this SipDelegateConnection that have no access to send/receive SIP
     *    messages and the second parameter contains a reason for why the feature tag is denied. For
     *    more information on the reason why the feature tag was denied access, see the
     *    {@link @SipDelegateManager.DeniedReason} reasons.
     */
    void onFeatureTagStatusChanged(@NonNull DelegateRegistrationState registrationState,
            @NonNull List<FeatureTagState> deniedFeatureTags);


    /**
     * SIP configuration of the underlying IMS stack used for SIP registration. Configuration may
     * change due to initial SIP registration and re-registration with or without any feature tag
     * changes.
     * If IMS stack is already registered at the time of callback registration, then this
     * method shall be invoked with the current configuration
     * @param registeredSipConfig The configuration of the SIP stack registered on IMS network.
     * @see SipDelegateImsConfiguration for bundle details.
     */
    void onImsConfigurationChanged(SipDelegateImsConfiguration registeredSipConfig);

    /**
     * The SipDelegateConnection has been destroyed. This interface should no longer be used
     * for any SIP message handling.
     * @param reason The reason for the failure.
     */
    void onDestroyed(@SipDelegateManager.SipDelegateDestroyReason int reason);
}
