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

import android.annotation.IntDef;
import android.os.Message;
import android.os.RemoteException;
import android.telephony.ims.aidl.IImsCapabilityCallback;
import android.telephony.ims.aidl.IImsMmTelListener;
import android.util.Log;

import com.android.ims.ImsCallProfile;
import com.android.ims.internal.IImsCallSession;
import com.android.ims.internal.IImsConfig;
import com.android.ims.internal.IImsEcbm;
import com.android.ims.internal.IImsMMTelFeature;
import com.android.ims.internal.IImsMultiEndpoint;
import com.android.ims.internal.IImsUt;
import com.android.ims.internal.ImsCallSession;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Base implementation for MMTel.
 * Any class wishing to use MMTelFeature should extend this class and implement all methods that the
 * service supports.
 *
 * @hide
 */

public class MMTelFeature extends ImsFeature {

    // Lock for feature synchronization
    protected final Object mLock = new Object();
    protected IImsMmTelListener mListener;

    private final IImsMMTelFeature mImsMMTelBinder = new IImsMMTelFeature.Stub() {

        @Override
        public void setListener(IImsMmTelListener l) throws RemoteException {
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
        public ImsCallProfile createCallProfile(int sessionId, int callSessionType, int callType)
                throws RemoteException {
            synchronized (mLock) {
                return MMTelFeature.this.createCallProfile(sessionId, callSessionType,  callType);
            }
        }

        @Override
        public IImsCallSession createCallSession(int sessionId, ImsCallProfile profile)
                throws RemoteException {
            synchronized (mLock) {
                return MMTelFeature.this.createCallSession(sessionId, profile);
            }
        }

        @Override
        public IImsCallSession getPendingCallSession(int sessionId, String callId)
                throws RemoteException {
            synchronized (mLock) {
                return MMTelFeature.this.getPendingCallSession(sessionId, callId);
            }
        }

        @Override
        public IImsUt getUtInterface() throws RemoteException {
            synchronized (mLock) {
                return MMTelFeature.this.getUtInterface();
            }
        }

        @Override
        public IImsConfig getConfigInterface() throws RemoteException {
            synchronized (mLock) {
                return MMTelFeature.this.getConfigInterface();
            }
        }

        @Override
        public void turnOnIms() throws RemoteException {
            synchronized (mLock) {
                MMTelFeature.this.turnOnIms();
            }
        }

        @Override
        public void turnOffIms() throws RemoteException {
            synchronized (mLock) {
                MMTelFeature.this.turnOffIms();
            }
        }

        @Override
        public IImsEcbm getEcbmInterface() throws RemoteException {
            synchronized (mLock) {
                return MMTelFeature.this.getEcbmInterface();
            }
        }

        @Override
        public void setUiTTYMode(int uiTtyMode, Message onComplete) throws RemoteException {
            synchronized (mLock) {
                MMTelFeature.this.setUiTTYMode(uiTtyMode, onComplete);
            }
        }

        @Override
        public IImsMultiEndpoint getMultiEndpointInterface() throws RemoteException {
            synchronized (mLock) {
                return MMTelFeature.this.getMultiEndpointInterface();
            }
        }

        @Override
        public int queryCapabilityStatus() throws RemoteException {
            return MMTelFeature.this.queryCapabilityStatus().mCapabilities;
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
        public void changeCapabilitiesConfiguration(CapabilityChangeRequest request,
                IImsCapabilityCallback c) throws RemoteException {
            MMTelFeature.this.requestChangeEnabledCapabilities(request, c);
        }

        @Override
        public void queryCapabilityConfiguration(int capability, int radioTech,
                IImsCapabilityCallback c) {
            queryCapabilityConfigurationInternal(capability, radioTech, c);
        }
    };

    /**
     * Contains the capabilities defined and supported by a MmTelFeature in the form of a Bitmask.
     * The capabilities that are used in MmTelFeature are defined by {@link MmTelCapability}.
     *
     * The capabilities of this MmTelFeature will be set by the framework and can be queried with
     * {@link #queryCapabilityStatus()}.
     *
     * This MmTelFeature can then return the status of each of these capabilities (enabled or not)
     * by sending a {@link #notifyCapabilitiesStatusChanged} callback to the framework. The current
     * status can also be queried using {@link #queryCapabilityStatus()}.
     */
    public static class MmTelCapabilities extends Capabilities {

        @VisibleForTesting
        public MmTelCapabilities() {
            super();
        }

        public MmTelCapabilities(Capabilities c) {
            mCapabilities = c.mCapabilities;
        }

        @IntDef(flag = true,
                value = {
                        CAPABILITY_TYPE_VOICE,
                        CAPABILITY_TYPE_VIDEO,
                        CAPABILITY_TYPE_UT,
                        CAPABILITY_TYPE_SMS
                })
        @Retention(RetentionPolicy.SOURCE)
        public @interface MmTelCapability {}

        /**
         * This MmTelFeature supports Voice calling (IR.92)
         */
        public static final int CAPABILITY_TYPE_VOICE = 1 << 0;

        /**
         * This MmTelFeature supports Video (IR.94)
         */
        public static final int CAPABILITY_TYPE_VIDEO = 1 << 1;

        /**
         * This MmTelFeature supports XCAP over Ut for supplementary services. (IR.92)
         */
        public static final int CAPABILITY_TYPE_UT = 1 << 2;

        /**
         * This MmTelFeature supports SMS (IR.92)
         */
        public static final int CAPABILITY_TYPE_SMS = 1 << 3;

        @Override
        public final void addCapabilities(@MmTelCapability int capabilities) {
            super.addCapabilities(capabilities);
        }

        @Override
        public final void removeCapabilities(@MmTelCapability int capability) {
            super.removeCapabilities(capability);
        }

        @Override
        public final boolean isCapable(@MmTelCapability int capabilities) {
            return super.isCapable(capabilities);
        }
    }

    /**
     * Listener that the framework implements for communication from the MmTelFeature.
     */
    public static class Listener extends IImsMmTelListener.Stub {

        @Override
        public final void onIncomingCall(IImsCallSession c) {
            onIncomingCall(new ImsCallSession(c));
        }

        /**
         * Updates the Listener when the voice message count for IMS has changed.
         * @param count an integer representing the new message count.
         */
        @Override
        public void onVoiceMessageCountUpdate(int count) {

        }

        /**
         * Called when the IMS provider receives an incoming call.
         * @param c The {@link ImsCallSession} associated with the new call.
         */
        public void onIncomingCall(ImsCallSession c) {
        }
    }

    /**
     * @hide
     */
    @Override
    public final IImsMMTelFeature getBinder() {
        return mImsMMTelBinder;
    }

    /**
     * @param listener A {@link MmTelFeature.Listener} used when the MmTelFeature receives an
     *     incoming call and notifies the framework.
     */
    protected void setListener(IImsMmTelListener listener) {
        synchronized (mLock) {
            mListener = listener;
        }
    }

    private void queryCapabilityConfigurationInternal(int capability, int radioTech,
            IImsCapabilityCallback c) {
        boolean enabled = queryCapabilityConfiguration(capability, radioTech);
        try {
            if (c != null) {
                c.onQueryCapabilityConfiguration(capability, radioTech, enabled);
            }
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "queryCapabilityConfigurationInternal called on dead binder!");
        }
    }

    /**
     * The current capability status that this MmTelFeature has defined is available. This
     * configuration will be used by the platform to figure out which capabilities are CURRENTLY
     * available to be used.
     *
     * Should be a subset of the capabilities that are enabled by the framework in
     * {@link #changeEnabledCapabilities}.
     * @return A copy of the current MmTelFeature capability status.
     */
    @Override
    public final MmTelCapabilities queryCapabilityStatus() {
        return new MmTelCapabilities(super.queryCapabilityStatus());
    }

    /**
     * Notify the framework that the status of the Capabilities has changed. Even though the
     * MmTelFeature capability may be enabled by the framework, the status may be disabled due to
     * the feature being unavailable from the network.
     * @param c The current capability status of the MmTelFeature. If a capability is disabled, then
     * the status of that capability is disabled. This can happen if the network does not currently
     * support the capability that is enabled. A capability that is disabled by the framework (via
     * {@link #changeEnabledCapabilities}) should also show the status as disabled.
     */
    protected final void notifyCapabilitiesStatusChanged(MmTelCapabilities c) {
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
        notifyIncomingCall(c.getSession());
    }

    /**
     * Notify the framework of an incoming call.
     * @param c The {@link ImsCallSession} of the new incoming call.
     *
     * @throws RemoteException if the connection to the framework is not available. If this happens,
     *     the call should be no longer considered active and should be cleaned up.
     * @hide
     */
    protected final void notifyIncomingCall(IImsCallSession c) throws RemoteException {
        synchronized (mLock) {
            if (mListener == null) {
                throw new IllegalStateException("Session is not available.");
            }
            mListener.onIncomingCall(c);
        }
    }

    /**
     * Updates the framework with the new Voice Message count.
     *
     * @throws RemoteException if the connection to the framework is not available. If this happens,
     *     the connection should be no longer considered active and should be cleaned up.
     * */
    public final void notifyVoiceMessageCountUpdate(int newCount) throws RemoteException {
        synchronized (mLock) {
            if (mListener == null) {
                throw new IllegalStateException("Session is not available.");
            }
            mListener.onVoiceMessageCountUpdate(newCount);
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
    public boolean isOpened() {
        return false;
    }

    /**
     * The MmTelFeature should override this method to handle the enabling/disabling of
     * MmTel Features, defined in {@link MmTelCapabilities.MmTelCapability}. The framework assumes
     * the {@link android.telephony.ims.feature.CapabilityChangeRequest} was processed successfully.
     * If a subset of capabilities could not be set to their new values,
     * {@link CapabilityCallbackProxy#onChangeCapabilityConfigurationError} must be called
     * individually for each capability whose processing resulted in an error.
     *
     * Enabling/Disabling a capability here indicates that the capability should be registered or
     * deregistered (depending on the capability change) and become available or unavailable to
     * the framework.
     */
    @Override
    public void changeEnabledCapabilities(CapabilityChangeRequest request,
            CapabilityCallbackProxy c) {
        // Base implementation, no-op
    }

    /**
     * Creates a {@link ImsCallProfile} from the service capabilities & IMS registration state.
     *
     * @param sessionId a session id which is obtained from {@link #startSession}
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
    public ImsCallProfile createCallProfile(int sessionId, int callSessionType, int callType) {
        return null;
    }

    /**
     * Creates an {@link ImsCallSession} with the specified call profile.
     * Use other methods, if applicable, instead of interacting with
     * {@link ImsCallSession} directly.
     *
     * @param sessionId a session id which is obtained from {@link #startSession}
     * @param profile a call profile to make the call
     */
    public IImsCallSession createCallSession(int sessionId, ImsCallProfile profile) {
        return null;
    }

    /**
     * Retrieves the call session associated with a pending call.
     *
     * @param sessionId a session id which is obtained from {@link #startSession}
     * @param callId a call id to make the call
     */
    public IImsCallSession getPendingCallSession(int sessionId, String callId) {
        return null;
    }

    /**
     * @return The Ut interface for the supplementary service configuration.
     */
    public IImsUt getUtInterface() {
        return null;
    }

    /**
     * @return The config interface for IMS Configuration
     */
    public IImsConfig getConfigInterface() {
        return null;
    }

    /**
     * Signal the MMTelFeature to turn on IMS when it has been turned off using {@link #turnOffIms}
     */
    public void turnOnIms() {
    }

    /**
     * Signal the MMTelFeature to turn off IMS when it has been turned on using {@link #turnOnIms}
     */
    public void turnOffIms() {
    }

    /**
     * @return The Emergency call-back mode interface for emergency VoLTE calls that support it.
     */
    public IImsEcbm getEcbmInterface() {
        return null;
    }

    /**
     * Sets the current UI TTY mode for the MMTelFeature.
     * @param uiTtyMode An integer containing the new UI TTY Mode.
     * @param onComplete A {@link Message} to be used when the mode has been set.
     */
    public void setUiTTYMode(int uiTtyMode, Message onComplete) {
    }

    /**
     * @return MultiEndpoint interface for DEP notifications
     */
    public IImsMultiEndpoint getMultiEndpointInterface() {
        return null;
    }

    @Override
    public void onFeatureReady() {

    }

    /**
     * {@inheritDoc}
     */
    public void onFeatureRemoved() {

    }
}
