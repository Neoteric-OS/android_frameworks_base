/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.telephony.ims.internal;

import android.os.RemoteException;
import android.telephony.ims.internal.aidl.IImsCallSessionListener;

import com.android.ims.ImsCallProfile;
import com.android.ims.ImsConferenceState;
import com.android.ims.ImsReasonInfo;
import com.android.ims.ImsStreamMediaProfile;
import com.android.ims.ImsSuppServiceNotification;
import com.android.ims.internal.ImsCallSession;

/**
 * Base implementation of ImsCallSessionListenerBase, which implements stub versions of the methods
 * in the IImsCallSessionListener AIDL. Override the methods that your implementation of
 * ImsCallSessionListener supports.
 *
 * DO NOT remove or change the existing APIs, only add new ones to this Base implementation or you
 * will break other implementations of ImsCallSessionListener maintained by other ImsServices.
 *
 * @hide
 */
public class ImsCallSessionListener {

    private final IImsCallSessionListener mListener;

    public ImsCallSessionListener(IImsCallSessionListener l) {
        mListener = l;
    }

    /**
     * Called when a request is sent out to initiate a new session
     * and 1xx response is received from the network.
     */
    public void callSessionProgressing(ImsStreamMediaProfile profile)
            throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionProgressing(profile);
    }

    /**
     * Called when the session is established.
     */
    public void callSessionStarted(ImsCallProfile profile) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionStarted(profile);
    }

    /**
     * Called when the session establishment is failed.
     *
     * @param reasonInfo detailed reason of the session establishment failure
     */
    public void callSessionStartFailed(ImsReasonInfo reasonInfo) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionStartFailed(reasonInfo);
    }

    /**
     * Called when the session is terminated.
     *
     * @param reasonInfo detailed reason of the session termination
     */
    public void callSessionTerminated(ImsReasonInfo reasonInfo) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionTerminated(reasonInfo);
    }

    /**
     * Called when the session is in hold.
     */
    public void callSessionHeld(ImsCallProfile profile) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionHeld(profile);
    }

    /**
     * Called when the session hold is failed.
     *
     * @param reasonInfo detailed reason of the session hold failure
     */
    public void callSessionHoldFailed(ImsReasonInfo reasonInfo) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionHoldFailed(reasonInfo);
    }

    /**
     * Called when the session hold is received from the remote user.
     */
    public void callSessionHoldReceived(ImsCallProfile profile) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionHoldReceived(profile);
    }

    /**
     * Called when the session resume is done.
     */
    public void callSessionResumed(ImsCallProfile profile) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionResumed(profile);
    }

    /**
     * Called when the session resume is failed.
     *
     * @param reasonInfo detailed reason of the session resume failure
     */
    public void callSessionResumeFailed(ImsReasonInfo reasonInfo) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionResumeFailed(reasonInfo);
    }

    /**
     * Called when the session resume is received from the remote user.
     */
    public void callSessionResumeReceived(ImsCallProfile profile) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionResumeReceived(profile);
    }

    /**
     * Called when the session merge has been started.  At this point, the {@code newSession}
     * represents the session which has been initiated to the IMS conference server for the
     * new merged conference.
     *
     * @param newSession the session object that is merged with an active & hold session
     */
    public void callSessionMergeStarted(ImsCallSession newSession, ImsCallProfile profile)
            throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionMergeStarted(newSession != null ? newSession.getSession() : null,
                profile);
    }

    /**
     * Called when the session merge is successful and the merged session is active.
     *
     * @param newSession the new session object that is used for the conference
     */
    public void callSessionMergeComplete(ImsCallSession newSession) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionMergeComplete(newSession != null ? newSession.getSession() : null);
    }

    /**
     * Called when the session merge has failed.
     *
     * @param reasonInfo detailed reason of the call merge failure
     */
    public void callSessionMergeFailed(ImsReasonInfo reasonInfo) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionMergeFailed(reasonInfo);
    }

    /**
     * Called when the session is updated (except for hold/unhold).
     */
    public void callSessionUpdated(ImsCallProfile profile) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionUpdated(profile);
    }

    /**
     * Called when the session update is failed.
     *
     * @param reasonInfo detailed reason of the session update failure
     */
    public void callSessionUpdateFailed(ImsReasonInfo reasonInfo) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionUpdateFailed(reasonInfo);
    }

    /**
     * Called when the session update is received from the remote user.
     */
    public void callSessionUpdateReceived(ImsCallProfile profile) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionUpdateReceived(profile);
    }

    /**
     * Called when the session is extended to the conference session.
     *
     * @param newSession the session object that is extended to the conference
     *      from the active session
     */
    public void callSessionConferenceExtended(ImsCallSession newSession, ImsCallProfile profile)
            throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionConferenceExtended(newSession != null ? newSession.getSession() : null,
                profile);
    }

    /**
     * Called when the conference extension is failed.
     *
     * @param reasonInfo detailed reason of the conference extension failure
     */
    public void callSessionConferenceExtendFailed(ImsReasonInfo reasonInfo) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionConferenceExtendFailed(reasonInfo);
    }

    /**
     * Called when the conference extension is received from the remote user.
     */
    public void callSessionConferenceExtendReceived(ImsCallSession newSession,
            ImsCallProfile profile) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionConferenceExtendReceived(newSession != null
                ? newSession.getSession() : null, profile);
    }

    /**
     * Called when the invitation request of the participants is delivered to the conference
     * server.
     */
    public void callSessionInviteParticipantsRequestDelivered() throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionInviteParticipantsRequestDelivered();
    }

    /**
     * Called when the invitation request of the participants is failed.
     *
     * @param reasonInfo detailed reason of the conference invitation failure
     */
    public void callSessionInviteParticipantsRequestFailed(ImsReasonInfo reasonInfo)
            throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionInviteParticipantsRequestFailed(reasonInfo);
    }

    /**
     * Called when the removal request of the participants is delivered to the conference
     * server.
     */
    public void callSessionRemoveParticipantsRequestDelivered() throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionRemoveParticipantsRequestDelivered();
    }

    /**
     * Called when the removal request of the participants is failed.
     *
     * @param reasonInfo detailed reason of the conference removal failure
     */
    public void callSessionRemoveParticipantsRequestFailed(ImsReasonInfo reasonInfo)
            throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionInviteParticipantsRequestFailed(reasonInfo);
    }

    /**
     * Notifies the changes of the conference info. the conference session.
     */
    public void callSessionConferenceStateUpdated(ImsConferenceState state) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionConferenceStateUpdated(state);
    }

    /**
     * Notifies the incoming USSD message.
     */

    public void callSessionUssdMessageReceived(int mode, String ussdMessage)
            throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionUssdMessageReceived(mode, ussdMessage);
    }

    /**
     * Notifies of a case where a {@link com.android.ims.internal.ImsCallSession} may potentially
     * handover from one radio technology to another.
     *
     * @param srcAccessTech    The source radio access technology; one of the access technology
     *                         constants defined in {@link android.telephony.ServiceState}.  For
     *                         example
     *                         {@link android.telephony.ServiceState#RIL_RADIO_TECHNOLOGY_LTE}.
     * @param targetAccessTech The target radio access technology; one of the access technology
     *                         constants defined in {@link android.telephony.ServiceState}.  For
     *                         example
     *                         {@link android.telephony.ServiceState#RIL_RADIO_TECHNOLOGY_LTE}.
     */
    public void callSessionMayHandover(int srcAccessTech, int targetAccessTech)
            throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionMayHandover(srcAccessTech, targetAccessTech);
    }

    /**
     * Called when session access technology changes
     *
     * @param srcAccessTech original access technology
     * @param targetAccessTech new access technology
     * @param reasonInfo
     */
    public void callSessionHandover(int srcAccessTech, int targetAccessTech,
            ImsReasonInfo reasonInfo) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionHandover(srcAccessTech, targetAccessTech, reasonInfo);
    }

    /**
     * Called when session access technology change fails
     *
     * @param srcAccessTech original access technology
     * @param targetAccessTech new access technology
     * @param reasonInfo handover failure reason
     */
    public void callSessionHandoverFailed(int srcAccessTech, int targetAccessTech,
            ImsReasonInfo reasonInfo) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionHandoverFailed(srcAccessTech, targetAccessTech, reasonInfo);
    }

    /**
     * Notifies the TTY mode change by remote party.
     *
     * @param mode one of the following: -
     *             {@link com.android.internal.telephony.Phone#TTY_MODE_OFF} -
     *             {@link com.android.internal.telephony.Phone#TTY_MODE_FULL} -
     *             {@link com.android.internal.telephony.Phone#TTY_MODE_HCO} -
     *             {@link com.android.internal.telephony.Phone#TTY_MODE_VCO}
     */
    public void callSessionTtyModeReceived(int mode) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionTtyModeReceived(mode);
    }

    /**
     * Notifies of a change to the multiparty state for this
     * {@code ImsCallSession}.
     *
     * @param isMultiParty {@code true} if the session became multiparty,
     *                     {@code false} otherwise.
     */

    public void callSessionMultipartyStateChanged(boolean isMultiParty) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionMultipartyStateChanged(isMultiParty);
    }

    /**
     * Notifies the supplementary service information for the current session.
     */
    public void callSessionSuppServiceReceived(ImsSuppServiceNotification suppSrvNotification)
            throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionSuppServiceReceived(suppSrvNotification);
    }

    /**
     * Received RTT modify request from Remote Party
     *
     * @param callProfile ImsCallProfile with updated attribute
     */
    public void callSessionRttModifyRequestReceived(ImsCallProfile callProfile)
            throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionRttModifyRequestReceived(callProfile);
    }

    /**
     * Received response for RTT modify request
     *
     * @param status true : Accepted the request
     *               false : Declined the request
     */
    public void callSessionRttModifyResponseReceived(int status) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionRttModifyResponseReceived(status);
    }

    /**
     * Device received RTT message from Remote UE
     *
     * @param rttMessage RTT message received
     */
    public void callSessionRttMessageReceived(String rttMessage) throws RemoteException {
        if (mListener == null) {
            throw new RemoteException("Listener is not available.");
        }
        mListener.callSessionRttMessageReceived(rttMessage);
    }
}

