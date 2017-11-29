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

package android.telephony.ims.internal.stub;

import android.annotation.NonNull;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.telephony.ims.internal.aidl.IImsRegistration;
import android.telephony.ims.internal.aidl.IImsRegistrationCallback;
import android.util.Log;

import com.android.ims.ImsReasonInfo;

import java.util.function.Consumer;

/**
 * The class that controls IMS registration for this ImsService and notifies the framework when
 * the IMS registration for this ImsService has changed status.
 * @hide
 */

public class ImsRegistrationImplBase {

    private static final String LOG_TAG = "ImsRegistrationImplBase";

    /**
     * Callback class for receiving Registration callback events.
     */
    public static class Callback extends IImsRegistrationCallback.Stub {

        /**
         * Notifies the application when the device is connected to the IMS network.
         *
         * @param imsRadioTech the radio access technology. Valid values are {@code
         * RIL_RADIO_TECHNOLOGY_*} defined in {@link ServiceState}.
         */
        @Override
        public void onRegistrationConnected(int imsRadioTech) {

        }

        /**
         * Notifies the application when the device is trying to connect the IMS network.
         *
         * @param imsRadioTech the radio access technology. Valid values are {@code
         * RIL_RADIO_TECHNOLOGY_*} defined in {@link ServiceState}.
         */
        @Override
        public void onRegistrationProcessing(int imsRadioTech) {

        }

        /**
         * Notifies the application when the device is disconnected from the IMS network.
         *
         * @param imsRadioTech the radio access technology. Valid values are {@code
         * RIL_RADIO_TECHNOLOGY_*} defined in {@link ServiceState}.
         * @param info the {@link ImsReasonInfo} associated with why registration was disconnected.
         */
        @Override
        public void onRegistrationDisconnected(int imsRadioTech, ImsReasonInfo info) {

        }

        /**
         * Notifies the application when its suspended IMS connection is resumed,
         * meaning the connection now allows throughput.
         */
        @Override
        public void onRegistrationResumed(int imsRadioTech) {

        }

        /**
         * Notifies the application when its current IMS connection is suspended,
         * meaning there is no data throughput.
         */
        @Override
        public void onRegistrationSuspended(int imsRadioTech) {

        }

        /**
         * Notifies the application when the registration change triggered by
         * {@link #onRegistrationFeaturesChanged} has failed.
         * @param info the {@link ImsReasonInfo} explaining why it failed.
         */
        @Override
        public void onRegistrationChangedFailed(ImsReasonInfo info) {

        }

        /**
         * Notifies the application when the IMS registration for this ImsService has changed. This
         * can happen when the ImsService needs to register for new IMS features on behalf of the
         * framework.
         *
         * @param info the new registration configuration that this ImsService supports.
         */
        @Override
        public void onRegistrationChanged(ImsRegistrationConfiguration info) {

        }
    }

    private final IImsRegistration mBinder = new IImsRegistration.Stub() {

        @Override
        public ImsRegistrationConfiguration querySupportedImsFeatures() throws RemoteException {
            return querySupportedImsFeaturesInternal();
        }

        @Override
        public void registrationFeaturesChanged(ImsRegistrationConfiguration info) throws
                RemoteException {
            registrationFeaturesChangedInternal(info);
        }

        @Override
        public void addRegistrationCallback(IImsRegistrationCallback c) throws RemoteException {
            ImsRegistrationImplBase.this.addRegistrationCallback(c);
        }

        @Override
        public void removeRegistrationCallback(IImsRegistrationCallback c) throws RemoteException {
            ImsRegistrationImplBase.this.removeRegistrationCallback(c);
        }
    };

    private final Object mLock = new Object();
    private ImsRegistrationConfiguration mConfig;
    private final RemoteCallbackList<IImsRegistrationCallback> mCallbacks
            = new RemoteCallbackList<>();

    private ImsRegistrationConfiguration querySupportedImsFeaturesInternal() {
        synchronized (mLock) {
            return onQuerySupportedImsFeatures();
        }
    }

    private void registrationFeaturesChangedInternal(ImsRegistrationConfiguration c)
            throws RemoteException {
        if (c == null) {
            throw new RemoteException("Invalid Configuration.");
        }
        synchronized (mLock) {
            if(mConfig == null || !mConfig.equals(c)) {
                mConfig = c;
                onRegistrationFeaturesChanged(c);
            }
        }
    }

    public final IImsRegistration getBinder() {
        return mBinder;
    }

    private void addRegistrationCallback(IImsRegistrationCallback c) {
        mCallbacks.register(c);
        // TODO: notify new callback of current registration state using callback
    }

    private void removeRegistrationCallback(IImsRegistrationCallback c) {
        mCallbacks.unregister(c);
    }

    /**
     * @return a {@link ImsRegistrationConfiguration} containing the supported IMS features of this
     * ImsService that should be registered. This, possibly along with other features that this
     * ImsService does not support, will be returned by the framework in
     * {@link #onRegistrationFeaturesChanged}.
     */
    public @NonNull ImsRegistrationConfiguration onQuerySupportedImsFeatures() {
        // Base implementation
        return new ImsRegistrationConfiguration((int[]) null);
    }

    /**
     * Called when the framework requests that this ImsService register for the features requested
     * in the provided {@link ImsRegistrationConfiguration}. This can contain features in
     * {@link ImsRegistrationConfiguration#externalFeatures} that this ImsService did not provide to
     * the framework when {@link #onQuerySupportedImsFeatures} was called. This is because the
     * framework wishes to support single IMS registration and needs this ImsService to register for
     * those external features as well.
     *
     * This should be used to configure the features used for IMS Registration on the attached
     * network. And should also be used to trigger initial IMS registration when the ImsService is
     * first started.
     *
     * @param config Contains the IMS registration configuration.
     */
    public void onRegistrationFeaturesChanged(ImsRegistrationConfiguration config) {
        // Base implementation
    }

    /**
     * @return The current {@link ImsRegistrationConfiguration} used for registration or
     * {@code null} if the framework has not triggered registration yet.
     */
    public final ImsRegistrationConfiguration getRegistrationConfiguration() {
        synchronized (mLock) {
            return mConfig;
        }
    }

    /**
     * Notify the framework that the device is connected to the IMS network.
     *
     * @param imsRadioTech the radio access technology. Valid values are {@code
     * RIL_RADIO_TECHNOLOGY_*} defined in {@link ServiceState}.
     */
    public final void registrationConnected(int imsRadioTech) {
        mCallbacks.broadcast((c) -> {
            try {
                c.onRegistrationConnected(imsRadioTech);
            } catch (RemoteException e) {
                Log.w(LOG_TAG, e + " " + "onRegistrationConnected() - Skipping " +
                        "callback.");
            }
        });
    }

    /**
     * Notify the framework that the device is trying to connect the IMS network.
     *
     * @param imsRadioTech the radio access technology. Valid values are {@code
     * RIL_RADIO_TECHNOLOGY_*} defined in {@link ServiceState}.
     */
    public final void registrationProcessing(int imsRadioTech) {
        mCallbacks.broadcast((c) -> {
            try {
                c.onRegistrationProcessing(imsRadioTech);
            } catch (RemoteException e) {
                Log.w(LOG_TAG, e + " " + "onRegistrationProcessing() - Skipping " +
                        "callback.");
            }
        });
    }

    /**
     * Notify the framework that the device is disconnected from the IMS network.
     *
     * @param imsRadioTech the radio access technology. Valid values are {@code
     * RIL_RADIO_TECHNOLOGY_*} defined in {@link ServiceState}.
     * @param info the {@link ImsReasonInfo} associated with why registration was disconnected.
     */
    public final void registrationDisconnected(int imsRadioTech, ImsReasonInfo info) {
        mCallbacks.broadcast((c) -> {
            try {
                c.onRegistrationDisconnected(imsRadioTech, info);
            } catch (RemoteException e) {
                Log.w(LOG_TAG, e + " " + "onRegistrationDisconnected() - Skipping " +
                        "callback.");
            }
        });
    }

    /**
     * Notify the framework that the previously suspended IMS connection is resumed, meaning the
     * connection now allows throughput.
     */
    public final void registrationResumed(int imsRadioTech) {
        mCallbacks.broadcast((c) -> {
            try {
                c.onRegistrationResumed(imsRadioTech);
            } catch (RemoteException e) {
                Log.w(LOG_TAG, e + " " + "onRegistrationResumed() - Skipping " +
                        "callback.");
            }
        });
    }

    /**
     * Notify the framework that its current IMS connection is suspended, meaning there is no data
     * throughput.
     */
    public final void registrationSuspended(int imsRadioTech) {
        mCallbacks.broadcast((c) -> {
            try {
                c.onRegistrationSuspended(imsRadioTech);
            } catch (RemoteException e) {
                Log.w(LOG_TAG, e + " " + "onRegistrationSuspended() - Skipping " +
                        "callback.");
            }
        });
    }

    /**
     * Notify the framework that the registration change triggered by
     * {@link #onRegistrationFeaturesChanged} has failed.
     * @param info the {@link ImsReasonInfo} explaining why it failed.
     */
    public final void registrationChangeFailed(ImsReasonInfo info) {
        mCallbacks.broadcast((c) -> {
            try {
                c.onRegistrationChangedFailed(info);
            } catch (RemoteException e) {
                Log.w(LOG_TAG, e + " " + "onRegistrationChangedFailed() - Skipping " +
                        "callback.");
            }
        });
    }

    /**
     * Notify the framework that the IMS registration for this ImsService has changed. This
     * can happen when the ImsService needs to register for new IMS features on behalf of the
     * framework.
     *
     * @param config the new registration configuration that this ImsService supports.
     */
    public final void registrationChanged(ImsRegistrationConfiguration config) {
        mCallbacks.broadcast((c) -> {
            try {
                c.onRegistrationChanged(config);
            } catch (RemoteException e) {
                Log.w(LOG_TAG, e + " " + "onRegistrationChanged() - Skipping " +
                        "callback.");
            }
        });
    }
}
