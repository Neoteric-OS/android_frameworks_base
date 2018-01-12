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

package android.telephony.ims.compat;

import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_LTE;

import android.annotation.SystemApi;
import android.net.Uri;
import android.os.RemoteException;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.compat.MMTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.Log;

import com.android.ims.ImsReasonInfo;
import com.android.ims.internal.IImsRegistrationListener;

import java.lang.ref.WeakReference;
import java.util.HashMap;

/**
 * Compatibility implementation of ImsService to be used with older versions of ImsService.
 *
 * If using this compatibility layer, you MUST make sure to also use all of the other IMS
 * compatibility classes.
 *
 *   @hide
 */
@SystemApi
public class ImsService extends android.telephony.ims.ImsService {

    private static final String LOG_TAG = "ImsServiceCompat";

    private ImsRegistrationImplBase mmTelRegistrationConverter;
    private final Object mLock = new Object();

    private static HashMap<Integer, Integer> RADIO_TECH_CONVERTER;
    static {
        RADIO_TECH_CONVERTER = new HashMap<>();
        RADIO_TECH_CONVERTER.put(RIL_RADIO_TECHNOLOGY_LTE,
                ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
        RADIO_TECH_CONVERTER.put(RIL_RADIO_TECHNOLOGY_IWLAN,
                ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN);
    }

    private static class MmTelRegistration extends ImsRegistrationImplBase {

        private final ImsRegistrationListenerProxy mListenerProxy =
                new ImsRegistrationListenerProxy();

        // We want this to be cleaned up along with MMTelFeature if MMTelFeature is destroyed,
        // so only tie the lifecycle of this class to its registration listener and allow for
        // GC when the MMTelFeature's registration listeners are destroyed.
        private final WeakReference<MMTelFeature> mMmTelFeature;

        public MmTelRegistration(MMTelFeature mmTelFeature) {
             mmTelFeature.addRegistrationListener(mListenerProxy);
            mMmTelFeature = new WeakReference<>(mmTelFeature);
        }

        private class ImsRegistrationListenerProxy extends IImsRegistrationListener.Stub {

            @Override
            public void registrationConnected() throws RemoteException {
                onRegistered(REGISTRATION_TECH_NONE);
            }

            @Override
            public void registrationProgressing() throws RemoteException {
                onRegistering(REGISTRATION_TECH_NONE);
            }

            @Override
            public void registrationConnectedWithRadioTech(int tech) throws RemoteException {
                onRegistered(RADIO_TECH_CONVERTER.get(tech));
            }

            @Override
            public void registrationProgressingWithRadioTech(int tech) throws RemoteException {
                onRegistering(RADIO_TECH_CONVERTER.get(tech));
            }

            @Override
            public void registrationDisconnected(ImsReasonInfo imsReasonInfo) throws RemoteException {
                onDeregistered(imsReasonInfo);
            }

            @Override
            public void registrationResumed() throws RemoteException {
                // Not used, ignore
            }

            @Override
            public void registrationSuspended() throws RemoteException {
                // Not used, ignore
            }

            @Override
            public void registrationServiceCapabilityChanged(int i, int i1) throws RemoteException {
                // Not used, ignore
            }

            @Override
            public void registrationFeatureCapabilityChanged(int serviceClass,
                    int[] enabledFeatures, int[] disabledFeatures) throws RemoteException {
                MMTelFeature feature = mMmTelFeature.get();
                if (feature != null) {
                    //todo
                }
            }

            @Override
            public void voiceMessageCountUpdate(int count) throws RemoteException {
                MMTelFeature feature = mMmTelFeature.get();
                if (feature != null) {
                    feature.notifyVoiceMessageCountUpdate(count);
                } else {
                    Log.e(LOG_TAG, "voiceMessageCountUpdate called on invalid MmTelFeature");
                }
            }

            @Override
            public void registrationAssociatedUriChanged(Uri[] uris) throws RemoteException {
                onSubscriberAssociatedUriChanged(uris);
            }

            @Override
            public void registrationChangeFailed(int i, ImsReasonInfo imsReasonInfo)
                    throws RemoteException {
                onTechnologyChangeFailed(RADIO_TECH_CONVERTER.get(i), imsReasonInfo);
            }
        }
    }

    @Override
    public final ImsRegistrationImplBase getRegistration(int slotId) {

        synchronized (mLock) {
            if (mmTelRegistrationConverter != null) {
                return mmTelRegistrationConverter;
            }
        }

        ImsFeature feature = getFeature(slotId, ImsFeature.MMTEL);
        MMTelFeature mmTelFeature;
        // check for compat instance of MMTelFeature
        if (feature instanceof MMTelFeature) {
             mmTelFeature = (MMTelFeature) feature;
        } else {
            Log.e(LOG_TAG, "Can not resolve MMTel feature for slot " + slotId + ". Returning"
                    + "stub implementation.");
            return new ImsRegistrationImplBase();
        }

        synchronized (mLock) {
            mmTelRegistrationConverter = new MmTelRegistration(mmTelFeature);
            return mmTelRegistrationConverter;
        }
    }
}
