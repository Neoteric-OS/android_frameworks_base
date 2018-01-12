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
import android.content.Context;
import android.content.Intent;
import android.os.IInterface;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.telephony.SubscriptionManager;
import android.telephony.ims.aidl.IImsCapabilityCallback;
import android.util.Log;

import com.android.ims.internal.IImsFeatureStatusCallback;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Base class for all IMS features that are supported by the framework.
 * @hide
 */
public abstract class ImsFeature {

    private static final String LOG_TAG = "ImsFeature";

    /**
     * Action to broadcast when ImsService is up.
     * Internal use only.
     * Only defined here separately compatibility purposes with the old ImsService.
     * @hide
     */
    public static final String ACTION_IMS_SERVICE_UP =
            "com.android.ims.IMS_SERVICE_UP";

    /**
     * Action to broadcast when ImsService is down.
     * Internal use only.
     * Only defined here separately for compatibility purposes with the old ImsService.
     * @hide
     */
    public static final String ACTION_IMS_SERVICE_DOWN =
            "com.android.ims.IMS_SERVICE_DOWN";

    /**
     * Part of the ACTION_IMS_SERVICE_UP or _DOWN intents.
     * A long value; the phone ID corresponding to the IMS service coming up or down.
     * Only defined here separately for compatibility purposes with the old ImsService.
     * @hide
     */
    public static final String EXTRA_PHONE_ID = "android:phone_id";

    // Invalid feature value
    public static final int INVALID = -1;
    // ImsFeatures that are defined in the Manifests. Ensure that these values match the previously
    // defined values in ImsServiceClass for compatibility purposes.
    public static final int EMERGENCY_MMTEL = 0;
    public static final int MMTEL = 1;
    public static final int RCS = 2;
    // Total number of features defined
    public static final int MAX = 3;

    // Integer values defining the state of the ImsFeature at any time.
    @IntDef(flag = true,
            value = {
                    STATE_NOT_AVAILABLE,
                    STATE_INITIALIZING,
                    STATE_READY,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ImsState {}
    public static final int STATE_NOT_AVAILABLE = 0;
    public static final int STATE_INITIALIZING = 1;
    public static final int STATE_READY = 2;

    // Integer values defining the result codes that should be returned from
    // {@link changeEnabledCapabilities} when the framework tries to set a feature's capability.
    @IntDef(flag = true,
            value = {
                    CAPABILITY_ERROR_GENERIC,
                    CAPABILITY_SUCCESS
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ImsCapabilityError {}

    public static final int CAPABILITY_ERROR_GENERIC = -1;
    public static final int CAPABILITY_SUCCESS = 0;

    /**
     * The framework implements this callback in order to register for Feature Capability status
     * updates, via {@link #onCapabilitiesStatusChanged(Capabilities)}, query Capability
     * configurations, via {@link #onQueryCapabilityConfiguration}, as well as to receive error
     * callbacks when the ImsService can not change the capability as requested, via
     * {@link #onChangeCapabilityConfigurationError}.
     */
    public static class CapabilityCallback extends IImsCapabilityCallback.Stub {

        @Override
        public final void onCapabilitiesStatusChanged(int config) throws RemoteException {
            onCapabilitiesStatusChanged(new Capabilities(config));
        }

        /**
         * Returns the result of a query for the capability configuration of a requested capability.
         *
         * @param capability The capability that was requested.
         * @param radioTech The IMS radio technology associated with the capability.
         * @param isEnabled true if the capability is enabled, false otherwise.
         */
        @Override
        public void onQueryCapabilityConfiguration(int capability, int radioTech,
                boolean isEnabled) {

        }

        /**
         * Called when a change to the capability configuration has returned an error.
         *
         * @param capability The capability that was requested to be changed.
         * @param radioTech The IMS radio technology associated with the capability.
         * @param reason error associated with the failure to change configuration.
         */
        @Override
        public void onChangeCapabilityConfigurationError(int capability, int radioTech,
                int reason) {
        }

        /**
         * The status of the feature's capabilities has changed to either available or unavailable.
         * If unavailable, the feature is not able to support the unavailable capability at this
         * time.
         *
         * @param config The new availability of the capabilities.
         */
        public void onCapabilitiesStatusChanged(Capabilities config) {
        }
    }

    /**
     * Used by the ImsFeature to call back to the CapabilityCallback that the framework has
     * provided.
     */
    protected static class CapabilityCallbackProxy {
        private final IImsCapabilityCallback mCallback;

        public CapabilityCallbackProxy(IImsCapabilityCallback c) {
            mCallback = c;
        }

        /**
         * This method notifies the provided framework callback that the request to change the
         * indicated capability has failed and has not changed.
         *
         * @param capability The Capability that will be notified to the framework.
         * @param radioTech The radio tech that this capability failed for.
         * @param reason The reason this capability was unable to be changed.
         */
        public void onChangeCapabilityConfigurationError(int capability, int radioTech,
                @ImsCapabilityError int reason) {
            try {
                mCallback.onChangeCapabilityConfigurationError(capability, radioTech, reason);
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "onChangeCapabilityConfigurationError called on dead binder.");
            }
        }

        public void onQueryCapabilityConfiguration(int capability, int radioTech,
                boolean isEnabled) {
            try {
                mCallback.onQueryCapabilityConfiguration(capability, radioTech, isEnabled);
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "onQueryCapabilityConfiguration called on dead binder.");
            }
        }
    }

    /**
     * Contains the capabilities defined and supported by an ImsFeature in the form of a bit mask.
     */
    public static class Capabilities {
        protected int mCapabilities = 0;

        public Capabilities() {
        }

        protected Capabilities(int capabilities) {
            mCapabilities = capabilities;
        }

        /**
         * @param capabilities Capabilities to be added to the configuration in the form of a
         *     bit mask.
         */
        public void addCapabilities(int capabilities) {
            mCapabilities |= capabilities;
        }

        /**
         * @param capabilities Capabilities to be removed to the configuration in the form of a
         *     bit mask.
         */
        public void removeCapabilities(int capabilities) {
            mCapabilities &= ~capabilities;
        }

        /**
         * @return true if all of the capabilities specified are capable.
         */
        public boolean isCapable(int capabilities) {
            return (mCapabilities & capabilities) == capabilities;
        }

        public Capabilities copy() {
            return new Capabilities(mCapabilities);
        }

        /**
         * @return a bitmask containing the capability flags directly.
         */
        public int getMask() {
            return mCapabilities;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Capabilities)) return false;

            Capabilities that = (Capabilities) o;

            return mCapabilities == that.mCapabilities;
        }

        @Override
        public int hashCode() {
            return mCapabilities;
        }
    }

    private final Set<IImsFeatureStatusCallback> mStatusCallbacks = Collections.newSetFromMap(
            new WeakHashMap<IImsFeatureStatusCallback, Boolean>());
    private @ImsState int mState = STATE_NOT_AVAILABLE;
    private int mSlotId = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
    protected Context mContext;
    private final Object mLock = new Object();
    private final RemoteCallbackList<IImsCapabilityCallback> mCapabilityCallbacks
            = new RemoteCallbackList<>();
    private Capabilities mCapabilityStatus = new Capabilities();

    public void setContext(Context context) {
        mContext = context;
    }

    public void setSlotId(int slotId) {
        mSlotId = slotId;
    }

    public int getFeatureState() {
        return mState;
    }

    protected final void setFeatureState(@ImsState int state) {
        if (mState != state) {
            mState = state;
            notifyFeatureState(state);
        }
    }

    public void addImsFeatureStatusCallback(IImsFeatureStatusCallback c) {
        if (c == null) {
            return;
        }
        try {
            // If we have just connected, send queued status.
            c.notifyImsFeatureStatus(mState);
            // Add the callback if the callback completes successfully without a RemoteException.
            synchronized (mStatusCallbacks) {
                mStatusCallbacks.add(c);
            }
        } catch (RemoteException e) {
            Log.w(LOG_TAG, "Couldn't notify feature state: " + e.getMessage());
        }
    }

    public void removeImsFeatureStatusCallback(IImsFeatureStatusCallback c) {
        if (c == null) {
            return;
        }
        synchronized (mStatusCallbacks) {
            mStatusCallbacks.remove(c);
        }
    }

    /**
     * Internal method called by ImsFeature when setFeatureState has changed.
     * @param state
     */
    private void notifyFeatureState(@ImsState int state) {
        synchronized (mStatusCallbacks) {
            for (Iterator<IImsFeatureStatusCallback> iter = mStatusCallbacks.iterator();
                 iter.hasNext(); ) {
                IImsFeatureStatusCallback callback = iter.next();
                try {
                    Log.i(LOG_TAG, "notifying ImsFeatureState=" + state);
                    callback.notifyImsFeatureStatus(state);
                } catch (RemoteException e) {
                    // remove if the callback is no longer alive.
                    iter.remove();
                    Log.w(LOG_TAG, "Couldn't notify feature state: " + e.getMessage());
                }
            }
        }
        sendImsServiceIntent(state);
    }

    /**
     * Provide backwards compatibility using deprecated service UP/DOWN intents.
     */
    private void sendImsServiceIntent(@ImsState int state) {
        if(mContext == null || mSlotId == SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
            return;
        }
        Intent intent;
        switch (state) {
            case ImsFeature.STATE_NOT_AVAILABLE:
            case ImsFeature.STATE_INITIALIZING:
                intent = new Intent(ACTION_IMS_SERVICE_DOWN);
                break;
            case ImsFeature.STATE_READY:
                intent = new Intent(ACTION_IMS_SERVICE_UP);
                break;
            default:
                intent = new Intent(ACTION_IMS_SERVICE_DOWN);
        }
        intent.putExtra(EXTRA_PHONE_ID, mSlotId);
        mContext.sendBroadcast(intent);
    }

    public final void addCapabilityCallback(IImsCapabilityCallback c) {
        mCapabilityCallbacks.register(c);
    }

    public final void removeCapabilityCallback(IImsCapabilityCallback c) {
        mCapabilityCallbacks.unregister(c);
    }

    /**
     * @return the cached capabilities status for this feature.
     */
    @VisibleForTesting
    public Capabilities queryCapabilityStatus() {
        synchronized (mLock) {
            return mCapabilityStatus.copy();
        }
    }

    // Called internally to request the change of enabled capabilities.
    @VisibleForTesting
    public final void requestChangeEnabledCapabilities(
            android.telephony.ims.feature.CapabilityChangeRequest request,
            IImsCapabilityCallback c) throws RemoteException {
        if (request == null) {
            throw new IllegalArgumentException(
                    "ImsFeature#requestChangeEnabledCapabilities called with invalid params.");
        }
        changeEnabledCapabilities(request, new CapabilityCallbackProxy(c));
    }

    /**
     * Called by the ImsFeature when the capabilities status has changed.
     *
     * @param c A {@link Capabilities} containing the new Capabilities status.
     */
    protected final void notifyCapabilitiesStatusChanged(Capabilities c) {
        synchronized (mLock) {
            mCapabilityStatus = c.copy();
        }
        int count = mCapabilityCallbacks.beginBroadcast();
        try {
            for (int i = 0; i < count; i++) {
                try {
                    mCapabilityCallbacks.getBroadcastItem(i).onCapabilitiesStatusChanged(
                            c.mCapabilities);
                } catch (RemoteException e) {
                    Log.w(LOG_TAG, e + " " + "notifyCapabilitiesStatusChanged() - Skipping " +
                            "callback.");
                }
            }
        } finally {
            mCapabilityCallbacks.finishBroadcast();
        }
    }

    /**
     * Features should override this method to receive Capability preference change requests from
     * the framework using the provided {@link android.telephony.ims.feature.CapabilityChangeRequest}. If any of the capabilities
     * in the {@link android.telephony.ims.feature.CapabilityChangeRequest} are not able to be completed due to an error,
     * {@link CapabilityCallbackProxy#onChangeCapabilityConfigurationError} should be called for
     * each failed capability.
     *
     * @param request A {@link android.telephony.ims.feature.CapabilityChangeRequest} containing requested capabilities to
     *     enable/disable.
     * @param c A {@link CapabilityCallbackProxy}, which will be used to call back to the framework
     * setting a subset of these capabilities fail, using
     * {@link CapabilityCallbackProxy#onChangeCapabilityConfigurationError}.
     */
    public abstract void changeEnabledCapabilities(
            android.telephony.ims.feature.CapabilityChangeRequest request,
            CapabilityCallbackProxy c);

    /**
     * Called when the feature is ready to use.
     */
    public abstract void onFeatureReady();

    /**
     * Called when the feature is being removed and must be cleaned up.
     */
    public abstract void onFeatureRemoved();

    /**
     * @return Binder instance
     */
    public abstract IInterface getBinder();
}
