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

package android.telephony.ims.internal.feature;

import android.annotation.IntDef;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.ims.internal.ImsCallSessionListener;
import android.telephony.ims.internal.aidl.IImsCallSessionListener;
import android.telephony.ims.internal.aidl.IImsCapabilityCallback;
import android.telephony.ims.internal.aidl.IImsMMTelFeature;
import android.telephony.ims.internal.aidl.IImsMMTelListener;
import android.telephony.ims.stub.ImsEcbmImplBase;
import android.telephony.ims.stub.ImsMultiEndpointImplBase;
import android.telephony.ims.stub.ImsUtImplBase;

import com.android.ims.ImsCallProfile;
import com.android.ims.internal.IImsCallSession;
import com.android.ims.internal.IImsEcbm;
import com.android.ims.internal.IImsMultiEndpoint;
import com.android.ims.internal.IImsUt;
import com.android.ims.internal.ImsCallSession;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Base implementation for Voice (IR-92) and Video (IR-94) IMS support.
 *
 * Any class wishing to use MMTelFeature should extend this class and implement all methods that the
 * service supports.
 * @hide
 */

public class MMTelFeature extends ImsFeature {

    private final IImsMMTelFeature mImsMMTelBinder = new IImsMMTelFeature.Stub() {

        @Override
        public void setListener(IImsMMTelListener l) throws RemoteException {
            synchronized (mLock) {
                MMTelFeature.this.setListener(l);
            }
        }

        @Override
        public boolean isConnected(int callSessionType, int callType)
                throws RemoteException {
            synchronized (mLock) {
                return MMTelFeature.this.isConnected(callSessionType, callType);
            }
        }

        @Override
        public boolean isOpened() throws RemoteException {
            synchronized (mLock) {
                return MMTelFeature.this.isOpened();
            }
        }

        @Override
        public int getFeatureStatus() throws RemoteException {
            synchronized (mLock) {
                return MMTelFeature.this.getFeatureState();
            }
        }


        @Override
        public ImsCallProfile createCallProfile(int callSessionType, int callType)
                throws RemoteException {
            synchronized (mLock) {
                return MMTelFeature.this.createCallProfile(callSessionType,  callType);
            }
        }

        @Override
        public IImsCallSession createCallSession(ImsCallProfile profile,
                IImsCallSessionListener listener) throws RemoteException {
            synchronized (mLock) {
                ImsCallSession s = MMTelFeature.this.createCallSession(profile,
                        new ImsCallSessionListener(listener));
                return s != null ? s.getSession() : null;
            }
        }

        @Override
        public IImsUt getUtInterface() throws RemoteException {
            synchronized (mLock) {
                return MMTelFeature.this.getUt();
            }
        }

        @Override
        public IImsEcbm getEcbmInterface() throws RemoteException {
            synchronized (mLock) {
                return MMTelFeature.this.getEcbm();
            }
        }

        @Override
        public void setUiTTYMode(int uiTtyMode, Message onComplete) throws RemoteException {
            synchronized (mLock) {
                MMTelFeature.this.setUiTtyMode(uiTtyMode, onComplete);
            }
        }

        @Override
        public IImsMultiEndpoint getMultiEndpointInterface() throws RemoteException {
            synchronized (mLock) {
                return MMTelFeature.this.getMultiEndpoint();
            }
        }

        @Override
        public void addCapabilityCallback(IImsCapabilityCallback c) {
            MMTelFeature.this.addCapabilityCallback(c);
        }

        @Override
        public void removeCapabilityCallback(IImsCapabilityCallback c) {
            MMTelFeature.this.removeCapabilityCallback(c);
        }

        @Override
        public void setCapabilities(long capabilities, IImsCapabilityCallback c)
                throws RemoteException {
            MMTelFeature.this.setCapabilities(new CapabilityConfiguration(capabilities), c);
        }

        @Override
        public void addCapability(long capability, IImsCapabilityCallback c)
                throws RemoteException {
            MMTelFeature.this.addCapability(capability, c);
        }

        @Override
        public void removeCapability(long capability, IImsCapabilityCallback c
        ) throws RemoteException {
            MMTelFeature.this.removeCapability(capability, c);
        }

        @Override
        public long queryCapabilities() throws RemoteException {
            return MMTelFeature.this.queryCapabilities().mCapabilities;
        }

        @Override
        public long queryCapabilityStatus() throws RemoteException {
            return MMTelFeature.this.queryCapabilityStatus().mCapabilities;
        }
    };

    /**
     * Contains the capabilities defined and supported by a MMTelFeature in the form of a Bitmask.
     * The capabilities that are used in MMTelFeature are defined by {@link MMTelCapability}.
     */
    public static class MMTelCapabilityConfiguration extends CapabilityConfiguration {

        MMTelCapabilityConfiguration(CapabilityConfiguration c) {
            mCapabilities = c.mCapabilities;
        }

        @IntDef(flag = true,
                value = {
                        CAPABILITY_TYPE_VOICE_OVER_LTE,
                        CAPABILITY_TYPE_VIDEO_OVER_LTE,
                        CAPABILITY_TYPE_VOICE_OVER_WIFI,
                        CAPABILITY_TYPE_VIDEO_OVER_WIFI,
                        CAPABILITY_TYPE_UT_OVER_LTE,
                        CAPABILITY_TYPE_UT_OVER_WIFI
                })
        @Retention(RetentionPolicy.SOURCE)
        public @interface MMTelCapability {}
        public static final int CAPABILITY_TYPE_VOICE_OVER_LTE = 0;
        public static final int CAPABILITY_TYPE_VIDEO_OVER_LTE = 1;
        public static final int CAPABILITY_TYPE_VOICE_OVER_WIFI = 2;
        public static final int CAPABILITY_TYPE_VIDEO_OVER_WIFI = 3;
        public static final int CAPABILITY_TYPE_UT_OVER_LTE = 4;
        public static final int CAPABILITY_TYPE_UT_OVER_WIFI = 5;

        @Override
        public final void addCapability(@MMTelCapability long capability) {
            super.addCapability(capability);
        }

        @Override
        public final void removeCapability(@MMTelCapability long capability) {
            super.removeCapability(capability);
        }

        @Override
        public final boolean isCapable(@MMTelCapability long capabilities) {
            return super.isCapable(capabilities);
        }
    }

    public class Listener extends IImsMMTelListener.Stub {

        @Override
        public final void onIncomingCall(IImsCallSession c) {
            onIncomingCall(new ImsCallSession(c));
        }

        public void onIncomingCall(ImsCallSession c) {
        }
    }

    // Lock for feature synchronization
    private final Object mLock = new Object();
    private IImsMMTelListener mListener;

    /**
     * @param l A {@link Listener} used when the MMTelFeature receives an incoming call and wishes
     * to notify the framework.
     */
    private void setListener(IImsMMTelListener l) {
        synchronized (mLock) {
            mListener = l;
        }
    }

    /**
     * Copies the current MMTelFeature capabilities.
     * @return A copy of the current MMTelFeature capabilities.
     */
    @Override
    protected final MMTelCapabilityConfiguration queryCapabilities() {
        return new MMTelCapabilityConfiguration(super.queryCapabilities());
    }

    /**
     * Copies the current MMTelFeature capability status
     * @return A copy of the current MMTelFeature capability status.
     */
    @Override
    protected final MMTelCapabilityConfiguration queryCapabilityStatus() {
        return new MMTelCapabilityConfiguration(super.queryCapabilityStatus());
    }

    /**
     * Notify the framework that the status of the Capabilities has changed. Even though the
     * MMTelFeature capability may be enabled by the user, the status may be disabled due to the
     * feature being unavailable from the network.
     * @param c The current capability status of the MMTelFeature. If a capability is disabled, then
     * the status of that capability is disabled. This can happen if the network does not currently
     * support the capability that is enabled. A capability that is disabled by the user (via
     * {@link #queryCapabilities()}) should also show the status as disabled.
     */
    protected final void notifyCapabilitiesStatusChanged(MMTelCapabilityConfiguration c) {
        super.notifyCapabilitiesStatusChanged(c);
    }

    /**
     * Notify the framework of an incoming call.
     * @param c The {@link ImsCallSession} of the new incoming call.
     *
     * @throws RemoteException if the connection to the framework is not available. If this happens,
     *     the call should be no longer considered active and should be cleaned up.
     * */
    protected final void notifyIncomingCall(ImsCallSession c) throws RemoteException {
        synchronized (mLock) {
            if (mListener == null) {
                throw new RemoteException("Session is not available.");
            }
            mListener.onIncomingCall(c.getSession());
        }
    }

    /**
     * Checks if the IMS service has successfully registered to the IMS network with the specified
     * service & call type.
     *
     * @param callSessionType a service type that is specified in {@link ImsCallProfile}
     *        {@link ImsCallProfile#SERVICE_TYPE_NORMAL}
     *        {@link ImsCallProfile#SERVICE_TYPE_EMERGENCY}
     * @param callType a call type that is specified in {@link ImsCallProfile}
     *        {@link ImsCallProfile#CALL_TYPE_VOICE_N_VIDEO}
     *        {@link ImsCallProfile#CALL_TYPE_VOICE}
     *        {@link ImsCallProfile#CALL_TYPE_VT}
     *        {@link ImsCallProfile#CALL_TYPE_VS}
     * @return true if the specified service id is connected to the IMS network; false otherwise
     */
    public boolean isConnected(int callSessionType, int callType) {
        return false;
    }

    /**
     * Checks if the specified IMS service is opened.
     *
     * @return true if the specified service id is opened; false otherwise
     */
    boolean isOpened() {
        return false;
    }

    /**
     * Creates a {@link ImsCallProfile} from the service capabilities & IMS registration state.
     *
     * @param callSessionType a service type that is specified in {@link ImsCallProfile}
     *        {@link ImsCallProfile#SERVICE_TYPE_NONE}
     *        {@link ImsCallProfile#SERVICE_TYPE_NORMAL}
     *        {@link ImsCallProfile#SERVICE_TYPE_EMERGENCY}
     * @param callType a call type that is specified in {@link ImsCallProfile}
     *        {@link ImsCallProfile#CALL_TYPE_VOICE}
     *        {@link ImsCallProfile#CALL_TYPE_VT}
     *        {@link ImsCallProfile#CALL_TYPE_VT_TX}
     *        {@link ImsCallProfile#CALL_TYPE_VT_RX}
     *        {@link ImsCallProfile#CALL_TYPE_VT_NODIR}
     *        {@link ImsCallProfile#CALL_TYPE_VS}
     *        {@link ImsCallProfile#CALL_TYPE_VS_TX}
     *        {@link ImsCallProfile#CALL_TYPE_VS_RX}
     * @return a {@link ImsCallProfile} object
     */
    public ImsCallProfile createCallProfile(int callSessionType, int callType) {
        return null;
    }

    /**
     * Creates an {@link ImsCallSession} with the specified call profile.
     * Use other methods, if applicable, instead of interacting with
     * {@link ImsCallSession} directly.
     *
     * @param profile a call profile to make the call
     * @param listener An implementation of IImsCallSessionListener.
     */
    public ImsCallSession createCallSession(ImsCallProfile profile,
            ImsCallSessionListener listener) {
        return null;
    }

    /**
     * @return The Ut interface for the supplementary service configuration.
     */
    public ImsUtImplBase getUt() {
        return null;
    }

    /**
     * @return The Emergency call-back mode interface for emergency VoLTE calls that support it.
     */
    public ImsEcbmImplBase getEcbm() {
        return null;
    }

    /**
     * @return The Emergency call-back mode interface for emergency VoLTE calls that support it.
     */
    public ImsMultiEndpointImplBase getMultiEndpoint() {
        return null;
    }

    /**
     * Sets the current UI TTY mode for the MMTelFeature.
     * @param mode An integer containing the new UI TTY Mode.
     * @param onComplete A {@link Message} to be used when the mode has been set.
     */
    void setUiTtyMode(int mode, Message onComplete) {

    }

    /**{@inheritDoc}*/
    @Override
    public void onFeatureRemoved() {

    }

    /**{@inheritDoc}*/
    @Override
    public void onFeatureReady() {

    }

    /**
     * @hide
     */
    @Override
    public final IImsMMTelFeature getBinder() {
        return mImsMMTelBinder;
    }
}
