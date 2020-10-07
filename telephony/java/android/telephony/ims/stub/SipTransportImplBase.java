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
import android.annotation.Nullable;
import android.telephony.ims.DelegateRequest;
import android.telephony.ims.FeatureTagState;
import android.telephony.ims.SipDelegateManager;
import android.telephony.ims.aidl.ISipTransport;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Manages the creation and destruction of SipDelegates in order to proxy SIP traffic to other
 * IMS applications in order to support IMS single registration.
 */
public class SipTransportImplBase {

    private final Executor mBinderExecutor;
    private final ISipTransport mSipTransportImpl = new ISipTransport.Stub() {

    };

    /**
     * Create an implementation of SipTransportImplBase.
     *
     * @param executor The executor that remote calls from the framework should be called on.
     */
    public SipTransportImplBase(@NonNull Executor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }

        mBinderExecutor = executor;
    }

    /**
     * Creates a new {@link SipDelegate} for the specified subscription ID.
     *
     * @param request A SIP delegate request containing the parameters that the remote RCS
     * application wishes to use.
     * @param dc A callback back to the remote application to be used to communicate SipDelegate state
     * callbacks.
     * @param mc A callback back to the remote application to be used to send SIP messages to the remote
     * application and acknowledge the sending of outgoing SIP messages.
     * @hide
     */
    public void createSipDelegate(DelegateRequest request, DelegateStateCallback dc,
            DelegateMessageCallback mc) {

    }

    /**
     * Destroys the SipDelegate associated with a remote IMS application. After the delegate is
     * destroyed, SipDelegate#onDestroy should be called to notify listeners of its destruction to
     * release resources.
     * @param delegate The delegate to be modified.
     * @param reason The reason the remote connection to this SipDelegate is being destroyed.
     * @hide
     */
    public void destroySipDelegate(SipDelegate delegate,
            @SipDelegateManager.DelegateConnectionDestroyReason int reason) {

    }

    /**
     * Creates a new {@link SipDelegate} for the specified subscription ID.
     *
     * @param request The {@link DelegateRequest} previously used in {@link #createSipDelegate}.
     * @param delegate The SipDelegate created to service the DelegateRequest.
     * @param deniedTags A List of Pairs, where the first parameter contains the feature tags
     *    associated with this SipDelegate that have no access to send/receive SIP messages and the second
     *    parameter contains a reason for why the feature tag is denied. For
     *    more information on the reason why the feature tag was denied access, see the
     *    {@link SipDelegateManager.DeniedReason} reasons. This is considered a permanent denial due
     *    to this SipDelegate not supporting a feature or the ImsService already implements this feature elsewhere.
     * @hide
     */
    public final void onSipDelegateCreated(@NonNull DelegateRequest request,
            @NonNull SipDelegate delegate, @Nullable List<FeatureTagState> deniedTags) {

    }

    /**
     * @return The IInterface used by the framework.
     * @hide
     */
    public ISipTransport getBinder() {
        return mSipTransportImpl;
    }
}
