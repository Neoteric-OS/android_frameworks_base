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

import com.android.ims.ImsCallProfile;
import com.android.ims.internal.IImsCallSession;
import com.android.ims.internal.IImsCallSessionListener;
import com.android.ims.internal.IImsConfig;
import com.android.ims.internal.IImsEcbm;
import com.android.ims.internal.IImsMultiEndpoint;
import com.android.ims.internal.IImsRegistrationListener;
import com.android.ims.internal.IImsUt;

import java.util.ArrayList;
import java.util.List;

/**
 * Base implementation, which implements all methods in IMMTelFeature. Any class wishing to use
 * MMTelFeature should extend this class and implement all methods that the service supports.
 *
 * @hide
 */

public class MMTelFeature extends ImsFeature {

    public int startSession(PendingIntent incomingCallIntent, IImsRegistrationListener listener) {
        return 0;
    }

    public void endSession(int sessionId) {
    }

    public boolean isConnected(int callSessionType, int callType) {
        return false;
    }

    public boolean isOpened() {
        return false;
    }

    public void addRegistrationListener(IImsRegistrationListener listener) {
    }

    public void removeRegistrationListener(IImsRegistrationListener listener) {
    }

    public ImsCallProfile createCallProfile(int sessionId, int callSessionType, int callType) {
        return null;
    }

    public IImsCallSession createCallSession(int sessionId, ImsCallProfile profile,
            IImsCallSessionListener listener) {
        return null;
    }

    public IImsCallSession getPendingCallSession(int sessionId, String callId) {
        return null;
    }

    public IImsUt getUtInterface() {
        return null;
    }

    public IImsConfig getConfigInterface() {
        return null;
    }

    public void turnOnIms() {
    }

    public void turnOffIms() {
    }

    public IImsEcbm getEcbmInterface() {
        return null;
    }

    public void setUiTTYMode(int uiTtyMode, Message onComplete) {
    }

    public IImsMultiEndpoint getMultiEndpointInterface() {
        return null;
    }

    @Override
    public void onFeatureRemoved() {

    }
}
