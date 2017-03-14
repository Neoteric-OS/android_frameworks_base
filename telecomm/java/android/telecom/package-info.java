/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

/**
 * The Android Telecom framework is responsible for managing calls on an Android device.  This can
 * include SIM-based calls using the {@code Telephony} framework, VOIP calls using SIP (e.g. the
 * {@code SipConnectionService}), or via a third-party VOIP
 * {@link android.telecom.ConnectionService}.  Telecom acts as a switchboard, routing calls and
 * audio focus between {@link android.telecom.Connection}s provided by
 * {@link android.telecom.ConnectionService} implementations, and
 * {@link android.telecom.InCallService} implementations which provide a user interface for calls.
 * <p>
 * Android supports the following calling use cases (with increasing level of complexity):
 * <ul>
 *     <li>Implement the self-managed {@link android.telecom.ConnectionService} API - this is ideal
 *     for developers of standalone calling apps which do not wish to show their calls within the
 *     default phone app, and do not wish to have other calls shown in their user interface.  Using
 *     a self-managed {@link android.telecom.ConnectionService} implementation within your
 *     standalone calling app helps you ensure that your app will interoperate not only with native
 *     telephony calling on the device, but also other standalone calling apps implementing this
 *     API.  It also manages audio routing and focus for you.</li>
 *     <li>Implement the managed {@link android.telecom.ConnectionService} API - facilitates
 *     development of a calling solution that relies on the existing device phone application (see
 *     {@link android.telecom.TelecomManager#getDefaultDialerPackage()}) to provide the user
 *     interface for calls.  An example might be a third party implementation of SIP calling, or a
 *     VOIP calling service.  A {@link android.telecom.ConnectionService} alone provides only the
 *     means of connecting calls, but has no associated user interface.</li>
 *     <li>Implement the {@link android.telecom.InCallService} API - facilitates development of a
 *     replacement for the device's default Phone/Dialer app.  The
 *     {@link android.telecom.InCallService} alone does not have any calling capability and consists
 *     of the user-interface side of calling only.  An {@link android.telecom.InCallService} must
 *     handle all Calls the Telecom framework is aware of.  It must not make assumptions about the
 *     nature of the calls (e.g. assuming calls are SIM-based telephony calls), and should not
 *     implement calling restrictions based on any one {@link android.telecom.ConnectionService}
 *     (e.g. it should not enforce Telephony restrictions for video calls).</li>
 *     <li>Implement both the {@link android.telecom.InCallService} and
 *     {@link android.telecom.ConnectionService} API - ideal if you wish to create your own
 *     {@link android.telecom.ConnectionService} based calling solution, complete with its own
 *     full user interface, while showing all other Android calls in the same user interface.  Using
 *     this approach, you must still ensure that your {@link android.telecom.InCallService} makes
 *     no assumption about the source of the calls it displays.  You must also ensure that your
 *     {@link android.telecom.ConnectionService} implementation can still function without the
 *     default phone app being set to your custom {@link android.telecom.InCallService}.</li>
 * </ul>
 * <p>
 * <em></em>Implementing the Self-Managed {@link android.telecom.ConnectionService} API</em>
 * <p>
 * The following are the high-level steps you should take if you have a calling app and you want to
 * implement the Self-Managed {@link android.telecom.ConnectionService} API.
 * <ol>
 *     <li>Implement a custom extension of the {@link android.telecom.ConnectionService} class.</li>
 *     <li>Add an entry in your {@code AndroidManifest.xml} for your
 *     {@link android.telecom.ConnectionService} (see {@link android.telecom.ConnectionService} for
 *     more information on how to do this).</li>
 *     <li>Your app should create a new {@link android.telecom.PhoneAccount} with
 *     {@link android.telecom.PhoneAccount#CAPABILITY_SELF_MANAGED} set and register it using
 *     {@link android.telecom.TelecomManager#registerPhoneAccount(android.telecom.PhoneAccount)}.
 *     Ensure you set the name of your app as the {@code label} when building your phone account.
 *     This is the name Telecom will use to refer to your app.  If you do not use your app's label,
 *     Telecom will replace it with the {@code android:label} attribute defined for your application
 *     in its {@code AndroidManifest.xml}.
 *     </li>
 *     <li>Create a custom extension of the {@link android.telecom.Connection} class.</li>
 *     <li>In your {@link android.telecom.ConnectionService} implementation, {@code @Override} the
 *         following methods:
 *         <ul>
 *             <li>{@link android.telecom.ConnectionService#onCreateIncomingConnection(
 *             PhoneAccountHandle, ConnectionRequest)} - To inform
 *             Telecom of a new incoming call in your app, your app first calls
 *             {@link android.telecom.TelecomManager#addNewIncomingCall(
 *             PhoneAccountHandle, android.os.Bundle)} to inform Telecom of the
 *             call.  Telecom will then call {@code onCreateIncomingConnection} if your new incoming
 *             call can be added.  In your override, return a new instance of your
 *             {@link android.telecom.Connection} class.  Set the phone number of the caller with
 *             {@link android.telecom.Connection#setAddress(android.net.Uri, int)}.  The name of the
 *             caller can be set with
 *             {@link android.telecom.Connection#setCallerDisplayName(java.lang.String, int)}.</li>
 *
 *             <li>{@link android.telecom.ConnectionService#onCreateOutgoingConnection(
 *             PhoneAccountHandle, ConnectionRequest)} - To inform
 *             Telecom that your app wishes to place a new outgoing call, your app first calls
 *             {@link android.telecom.TelecomManager#placeCall(android.net.Uri, android.os.Bundle)}.
 *             Telecom will call {@code onCreateOutgoingConnection} if your new outgoing call can be
 *             added.  In your override, return a new instance of your
 *             {@link android.telecom.Connection} class to represent the outgoing
 *             call your app is placing.  Set the address and display name of as in
 *             {@code onCreateIncomingConnection}.  To ensure proper audio operation you should also
 *             {@link android.telecom.Connection#setAudioModeIsVoip(boolean)} {@code true} </li>
 *
 *             <li>{@link android.telecom.ConnectionService#onCreateIncomingConnectionFailed(
 *             android.telecom.PhoneAccountHandle, android.telecom.ConnectionRequest)} - When you
 *             call {@link android.telecom.TelecomManager#addNewIncomingCall(
 *             android.telecom.PhoneAccountHandle, android.os.Bundle)}, and conditions do not allow
 *             your incoming call to be placed, Telecom will call this method.  Your app should
 *             reject the new incoming call, and may decide to use a
 *             {@link android.app.Notification} to inform the user of the missed call.  To ensure
 *             proper audio operation you should also
 *             {@link android.telecom.Connection#setAudioModeIsVoip(boolean)} {@code true}.</li>
 *
 *             <li>{@link android.telecom.ConnectionService#onCreateOutgoingConnectionFailed(
 *             android.telecom.PhoneAccountHandle, android.telecom.ConnectionRequest)} - When you
 *             call {@link android.telecom.TelecomManager#placeCall(android.net.Uri,
 *             android.os.Bundle)}, and conditions do not allow your incoming call to be placed,
 *             Telecom will call this method.  Your app should display a message to the user
 *             indicating that the call cannot be placed at this time due to an ongoing call in
 *             another app.</li>
 *         </ul>
 *     </li>
 *     <li>
 *         In your {@link android.telecom.Connection} implementation, {@code @Override} the
 *         following methods:
 *         <ul>
 *             <li>
 *                 {@link android.telecom.Connection#onShowIncomingCallUi()} - Telecom calls this
 *                 method when your app should show its own incoming call UI for a new incoming
 *                 call.  See {@link android.telecom.Connection#onShowIncomingCallUi()} for more
 *                 information.
 *             </li>
 *
 *             <li>
 *                 {@link android.telecom.Connection#onAnswer()} - Telecom calls this when it
 *                 displays the incoming call UI for your incoming call, and the user has chosen to
 *                 answer the call.  You should call {@link android.telecom.Connection#setActive()}
 *                 when the call has been answered and it active.
 *             </li>
 *
 *             <li>
 *                 {@link android.telecom.Connection#onReject()} - Telecom calls this when it
 *                 displays the incoming call UI for your incoming call, and the user has chosen to
 *                 reject the call.  Your app should reject the call, and then call
 *                 {@link android.telecom.Connection#setDisconnected(
 *                 android.telecom.DisconnectCause)} and specify the
 *                 {@link android.telecom.DisconnectCause#REJECTED} disconnect cause to indicate
 *                 that the call was rejected.  Ensure you call
 *                 {@link android.telecom.Connection#destroy()}.
 *             </li>
 *
 *             <li>
 *                 {@link android.telecom.Connection#onDisconnect()} - Telecom calls this when your
 *                 call needs to be disconnected.  This can also happen if the user has answered an
 *                 incoming call in another app, or if another {@link android.telecom.InCallService}
 *                 such as Android Auto is relaying a request to disconnect the call to your app.
 *             </li>
 *
 *             <li>
 *                 {@link android.telecom.Connection#onUnhold()} - Telecom calls this when your call
 *                 should be un-held.  Call {@link android.telecom.Connection#setActive()} when your
 *                 call has become active again.  If your call supports hold, make sure to set
 *                 {@link android.telecom.Connection#CAPABILITY_SUPPORT_HOLD}.  If your app only
 *                 supports a single active call at one time, you should ensure that you invoke
 *                 {@link android.telecom.Connection#setOnHold()} for any calls which will be held
 *                 as a result of un-holding this call.
 *             </li>
 *
 *             <li>
 *                 {@link android.telecom.Connection#onHold()} - Telecom calls this when your call
 *                 should be held.  Call {@link android.telecom.Connection#setOnHold()} when your
 *                 call has been held.  If your call supports hold, make sure to set
 *                 {@link android.telecom.Connection#CAPABILITY_SUPPORT_HOLD}.
 *             </li>
 *         </ul>
 *     </li>
 * </ol>
 */
package android.telecom;