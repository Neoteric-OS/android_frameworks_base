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

package android.telephony.ims;

import android.app.PendingIntent;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.ims.feature.IRcsFeature;
import android.util.Log;

import com.android.ims.ImsCallProfile;
import com.android.ims.internal.IImsCallSession;
import com.android.ims.internal.IImsCallSessionListener;
import com.android.ims.internal.IImsConfig;
import com.android.ims.internal.IImsEcbm;
import com.android.ims.internal.IImsMultiEndpoint;
import com.android.ims.internal.IImsRegistrationListener;
import com.android.ims.internal.IImsServiceController;
import com.android.ims.internal.IImsServiceFeatureListener;
import com.android.ims.internal.IImsUt;

/**
 * A Container of the IImsServiceController binder, which maintains which features the
 * ImsService currently supports.
 * @hide
 */

public class ImsServiceProxy extends ImsServiceProxyCompat implements IRcsFeature {

    protected String LOG_TAG = "ImsServiceProxy";
    private final int mSupportedFeature;

    private boolean mIsAvailable = false;
    private final Object mLock = new Object();

    private final IImsServiceFeatureListener mListenerBinder =
            new IImsServiceFeatureListener.Stub() {

        @Override
        public void imsFeatureCreated(int slotId, int feature) throws RemoteException {
            // The feature has been re-enabled. This may happen when the service crashes.
            synchronized (mLock) {
                if (!mIsAvailable && mSlotId == slotId && feature == mSupportedFeature) {
                    Log.i(LOG_TAG, "Feature enabled on slotId: " + slotId + " for feature: " +
                            feature);
                    mIsAvailable = true;
                }
            }
        }

        @Override
        public void imsFeatureRemoved(int slotId, int feature) throws RemoteException {
            synchronized (mLock) {
                if (mIsAvailable) {
                    Log.i(LOG_TAG, "Feature disabled on slotId: " + slotId + " for feature: " +
                            feature);
                    mIsAvailable = false;
                }
            }
        }
    };

    public ImsServiceProxy(int slotId, IBinder binder, int featureType) {
        super(slotId, binder);
        mSupportedFeature = featureType;
        mIsAvailable = true;
    }

    public ImsServiceProxy(int slotId, int featureType) {
        super(slotId, null /*IBinder*/);
        mSupportedFeature = featureType;
        mIsAvailable = true;
    }

    public IImsServiceFeatureListener getListener() {
        return mListenerBinder;
    }

    public void setBinder(IBinder binder) {
        mBinder = binder;
    }

    @Override
    public int startSession(PendingIntent incomingCallIntent, IImsRegistrationListener listener)
            throws RemoteException {
        synchronized (mLock) {
            checkBinderConnection();
            return getServiceInterface(mBinder).startSession(mSlotId, mSupportedFeature,
                    incomingCallIntent, listener);
        }
    }

    @Override
    public void endSession(int sessionId) throws RemoteException {
        synchronized (mLock) {
            checkBinderConnection();
            getServiceInterface(mBinder).endSession(mSlotId, mSupportedFeature, sessionId);
        }
    }

    @Override
    public boolean isConnected(int sessionId, int callServiceType, int callType)
            throws RemoteException {
        synchronized (mLock) {
            checkBinderConnection();
            return getServiceInterface(mBinder).isConnected(mSlotId, mSupportedFeature, sessionId,
                    callServiceType, callType);
        }
    }

    @Override
    public boolean isOpened(int sessionId) throws RemoteException {
        synchronized (mLock) {
            checkBinderConnection();
            return getServiceInterface(mBinder).isOpened(mSlotId, mSupportedFeature, sessionId);
        }
    }

    @Override
    public void addRegistrationListener(int sessionId, IImsRegistrationListener listener)
    throws RemoteException {
        synchronized (mLock) {
            checkBinderConnection();
            getServiceInterface(mBinder).addRegistrationListener(mSlotId, mSupportedFeature,
                    sessionId, listener);
        }
    }

    @Override
    public void removeRegistrationListener(int sessionId, IImsRegistrationListener listener)
            throws RemoteException {
        synchronized (mLock) {
            checkBinderConnection();
            getServiceInterface(mBinder).removeRegistrationListener(mSlotId, mSupportedFeature,
                    sessionId, listener);
        }
    }

    @Override
    public ImsCallProfile createCallProfile(int sessionId, int callServiceType, int callType)
            throws RemoteException {
        synchronized (mLock) {
            checkBinderConnection();
            return getServiceInterface(mBinder).createCallProfile(mSlotId, mSupportedFeature,
                    sessionId, callServiceType, callType);
        }
    }

    @Override
    public IImsCallSession createCallSession(int sessionId, ImsCallProfile profile,
            IImsCallSessionListener listener) throws RemoteException {
        synchronized (mLock) {
            checkBinderConnection();
            return getServiceInterface(mBinder).createCallSession(mSlotId, mSupportedFeature,
                    sessionId, profile, listener);
        }
    }

    @Override
    public IImsCallSession getPendingCallSession(int sessionId, String callId)
            throws RemoteException {
        synchronized (mLock) {
            checkBinderConnection();
            return getServiceInterface(mBinder).getPendingCallSession(mSlotId, mSupportedFeature,
                    sessionId, callId);
        }
    }

    @Override
    public IImsUt getUtInterface(int sessionId) throws RemoteException {
        synchronized (mLock) {
            checkBinderConnection();
            return getServiceInterface(mBinder).getUtInterface(mSlotId, mSupportedFeature,
                    sessionId);
        }
    }

    @Override
    public IImsConfig getConfigInterface(int sessionId) throws RemoteException {
        synchronized (mLock) {
            checkBinderConnection();
            return getServiceInterface(mBinder).getConfigInterface(mSlotId, mSupportedFeature,
                    sessionId);
        }
    }

    @Override
    public void turnOnIms(int sessionId) throws RemoteException {
        synchronized (mLock) {
            checkBinderConnection();
            getServiceInterface(mBinder).turnOnIms(mSlotId, mSupportedFeature, sessionId);
        }
    }

    @Override
    public void turnOffIms(int sessionId) throws RemoteException {
        synchronized (mLock) {
            checkBinderConnection();
            getServiceInterface(mBinder).turnOffIms(mSlotId, mSupportedFeature, sessionId);
        }
    }

    @Override
    public IImsEcbm getEcbmInterface(int sessionId) throws RemoteException {
        synchronized (mLock) {
            checkBinderConnection();
            return getServiceInterface(mBinder).getEcbmInterface(mSlotId, mSupportedFeature,
                    sessionId);
        }
    }

    @Override
    public void setUiTTYMode(int sessionId, int uiTtyMode, Message onComplete)
            throws RemoteException {
        synchronized (mLock) {
            checkBinderConnection();
            getServiceInterface(mBinder).setUiTTYMode(mSlotId, mSupportedFeature, sessionId,
                    uiTtyMode, onComplete);
        }
    }

    @Override
    public IImsMultiEndpoint getMultiEndpointInterface(int sessionId) throws RemoteException {
        synchronized (mLock) {
            checkBinderConnection();
            return getServiceInterface(mBinder).getMultiEndpointInterface(mSlotId,
                    mSupportedFeature, sessionId);
        }
    }

    @Override
    public boolean isBinderAlive() {
        return mIsAvailable && mBinder != null && mBinder.isBinderAlive();
    }

    private IImsServiceController getServiceInterface(IBinder b) {
        return IImsServiceController.Stub.asInterface(b);
    }
}
