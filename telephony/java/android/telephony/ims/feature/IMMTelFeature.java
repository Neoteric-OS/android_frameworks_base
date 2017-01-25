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

package android.telephony.ims.feature;

import android.app.PendingIntent;
import android.os.Message;
import android.os.RemoteException;

import com.android.ims.ImsCallProfile;
import com.android.ims.internal.IImsCallSession;
import com.android.ims.internal.IImsCallSessionListener;
import com.android.ims.internal.IImsConfig;
import com.android.ims.internal.IImsEcbm;
import com.android.ims.internal.IImsMultiEndpoint;
import com.android.ims.internal.IImsRegistrationListener;
import com.android.ims.internal.IImsUt;

/**
 * MMTel interface for an ImsService. When updating this interface, ensure that your changes are
 * also present
 * @hide
 */

public interface IMMTelFeature {
    /**
     * @return an integer representing the session id associated with the session that has been
     * started.
     */
    int startSession(PendingIntent incomingCallIntent, IImsRegistrationListener listener)
            throws RemoteException;
    
    void endSession(int sessionId) throws RemoteException;
    
    boolean isConnected(int sessionId, int callServiceType, int callType) throws RemoteException;
    
    boolean isOpened(int sessionId) throws RemoteException;

    void addRegistrationListener(int sessionId, IImsRegistrationListener listener)
            throws RemoteException;

    void removeRegistrationListener(int sessionId, IImsRegistrationListener listener)
            throws RemoteException;

    ImsCallProfile createCallProfile(int sessionId, int callServiceType, int callType)
            throws RemoteException;
    
    IImsCallSession createCallSession(int sessionId, ImsCallProfile profile,
            IImsCallSessionListener listener) throws RemoteException;
    
    IImsCallSession getPendingCallSession(int sessionId, String callId) throws RemoteException;

    IImsUt getUtInterface(int sessionId) throws RemoteException;

    IImsConfig getConfigInterface(int sessionId) throws RemoteException;

    void turnOnIms(int sessionId) throws RemoteException;

    void turnOffIms(int sessionId) throws RemoteException;

    IImsEcbm getEcbmInterface(int sessionId) throws RemoteException;

    void setUiTTYMode(int sessionId, int uiTtyMode, Message onComplete) throws RemoteException;

    IImsMultiEndpoint getMultiEndpointInterface(int sessionId) throws RemoteException;
}
