/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.telephony.ims.feature.compat;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.RemoteException;
import android.telephony.ims.aidl.IImsMmTelListener;
import android.telephony.ims.feature.CapabilityChangeRequest;
import android.util.Log;

import com.android.ims.ImsCallProfile;
import com.android.ims.ImsReasonInfo;
import com.android.ims.internal.IImsCallSession;
import com.android.ims.internal.IImsCallSessionListener;
import com.android.ims.internal.IImsRegistrationListener;
import com.android.ims.internal.ImsCallSession;

/**
 * Compatability layer for older implementations of MMTelFeature.
 *
 * @hide
 */

public class MMTelFeature extends android.telephony.ims.feature.MMTelFeature {

    private static final String TAG = "MMTelFeature(Compat)";

    private static final String ACTION_INCOMING_CALL_INTENT =
            "com.android.ims.internal.compat.INCOMING_CALL";

    /**
     * Key to retrieve the call ID from an incoming call intent.
     */
    private static final String EXTRA_CALL_ID = "android:imsCallID";

    /**
     * Part of the ACTION_IMS_INCOMING_CALL intents.
     * An integer value; service identifier.
     * Internal use only.
     */
    private static final String EXTRA_SERVICE_ID = "android:imsServiceId";

    private int mSessionId = -1;
    private final IncomingCallIntentReceiver mIncomingCallReceiver
            = new IncomingCallIntentReceiver();
    private class IncomingCallIntentReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_INCOMING_CALL_INTENT.equals(intent.getAction())) {
                //trampoline incoming call to new API
                Log.i(TAG, "Received incoming call intent, trampolining to framework");
                IImsCallSession session = getCallSession(intent);
                if (session != null) {
                    try {
                        notifyIncomingCall(session);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Can not notify framework of incoming call, "
                                + "disconnecting.");
                        try {
                            session.terminate(ImsReasonInfo.CODE_LOCAL_INTERNAL_ERROR);
                        } catch (RemoteException e2) {
                            // Shouldn't happen, local process.
                            Log.e(TAG, "Can not terminate call.");
                        }
                    }
                }
            }
        }
    }

    @Override
    public final IImsCallSession createCallSession(int sessionId, ImsCallProfile profile) {
        return createCallSession(sessionId, profile, null /*listener*/);
    }

    @Override
    protected final void setListener(IImsMmTelListener listener) {
        synchronized (mLock) {
            mListener = listener;
        }
        mContext.registerReceiver(mIncomingCallReceiver,
                new IntentFilter(ACTION_INCOMING_CALL_INTENT));
        mSessionId = startSession(createIncomingCallPendingIntent(), null);
    }

    @Override
    public void changeEnabledCapabilities(CapabilityChangeRequest request,
            CapabilityCallbackProxy c) {
        //todo
    }

    @Override
    public void onFeatureRemoved() {
        mContext.unregisterReceiver(mIncomingCallReceiver);
    }

    private PendingIntent createIncomingCallPendingIntent() {
        Intent intent = new Intent(ACTION_INCOMING_CALL_INTENT);
        intent.setClass(mContext, MMTelFeature.class);
        return PendingIntent.getBroadcast(mContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private IImsCallSession getCallSession(Intent intent) {
        int incomingServiceId = getImsSessionId(intent);

        if (mSessionId != incomingServiceId) {
            Log.e(TAG, "Service id is mismatched in the incoming call intent");
            return null;
        }

        String callId = getCallId(intent);

        if (callId == null) {
            Log.e(TAG, "Call ID missing in the incoming call intent");
            return null;
        }

        return getPendingCallSession(mSessionId, callId);
    }

    /**
     * Gets the call ID from the specified incoming call broadcast intent.
     *
     * @param incomingCallIntent the incoming call broadcast intent
     * @return the call ID or null if the intent does not contain it
     */
    private static String getCallId(Intent incomingCallIntent) {
        if (incomingCallIntent == null) {
            return null;
        }

        return incomingCallIntent.getStringExtra(EXTRA_CALL_ID);
    }

    /**
     * Gets the service type from the specified incoming call broadcast intent.
     *
     * @param incomingCallIntent the incoming call broadcast intent
     * @return the session identifier or -1 if the intent does not contain it
     */
    private static int getImsSessionId(Intent incomingCallIntent) {
        if (incomingCallIntent == null) {
            return (-1);
        }

        return incomingCallIntent.getIntExtra(EXTRA_SERVICE_ID, -1);
    }

    /**
     * Creates an {@link ImsCallSession} with the specified call profile.
     * Use other methods, if applicable, instead of interacting with
     * {@link ImsCallSession} directly.
     *
     * @param sessionId a session id which is obtained from {@link #startSession}
     * @param profile a call profile to make the call
     * @param listener An implementation of IImsCallSessionListener.
     */
    public IImsCallSession createCallSession(int sessionId, ImsCallProfile profile,
            IImsCallSessionListener listener) {
        return null;
    }

    /**
     * Add a new registration listener for the client associated with the session Id.
     * @param listener An implementation of IImsRegistrationListener.
     */
    public void addRegistrationListener(IImsRegistrationListener listener) {
    }

    /**
     * Remove a previously registered listener using {@link #addRegistrationListener} for the client
     * associated with the session Id.
     * @param listener A previously registered IImsRegistrationListener
     */
    public void removeRegistrationListener(IImsRegistrationListener listener) {
    }

    /**
     * Notifies the MMTel feature that you would like to start a session. This should always be
     * done before making/receiving IMS calls. The IMS service will register the device to the
     * operator's network with the credentials (from ISIM) periodically in order to receive calls
     * from the operator's network. When the IMS service receives a new call, it will send out an
     * intent with the provided action string. The intent contains a call ID extra
     * {@link IImsCallSession#getCallId} and it can be used to take a call.
     *
     * @param incomingCallIntent When an incoming call is received, the IMS service will call
     * {@link PendingIntent#send} to send back the intent to the caller with
     * ImsManager#INCOMING_CALL_RESULT_CODE as the result code and the intent to fill in the call
     * ID; It cannot be null.
     * @param listener To listen to IMS registration events; It cannot be null
     * @return an integer (greater than 0) representing the session id associated with the session
     * that has been started.
     */
    public int startSession(PendingIntent incomingCallIntent, IImsRegistrationListener listener) {
        return 0;
    }

    /**
     * End a previously started session using the associated sessionId.
     * @param sessionId an integer (greater than 0) representing the ongoing session. See
     * {@link #startSession}.
     */
    public void endSession(int sessionId) {
    }
}
