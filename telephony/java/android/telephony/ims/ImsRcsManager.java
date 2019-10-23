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
 * limitations under the License.
 */

package android.telephony.ims;

import android.Manifest;
import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SystemService;
import android.annotation.UnsupportedAppUsage;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.aidl.IImsCapabilityCallback;
import android.telephony.ims.aidl.IImsRcsController;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.RcsFeature;
import android.util.Log;

import java.util.concurrent.Executor;

/**
 * Manager for interfacing with the framework RCS services, including the User Capability Exchange
 * (UCE) service, as well as managing user settings.
 *
 * Use {@link #createForSubscriptionId(Context, int)} to create an instance of this manager.
 * @hide
 */
@SystemService(Context.TELEPHONY_RCS_SERVICE)
public class ImsRcsManager {
    private static final String TAG = "ImsRcsManager";

    /**
     * Receives RCS availability status updates from the ImsService.
     *
     * @see #isAvailable(int)
     * @see #registerRcsAvailabilityCallback(Executor, AvailabilityCallback)
     * @see #unregisterRcsAvailabilityCallback(AvailabilityCallback)
     */
    public static class AvailabilityCallback {

        private static class CapabilityBinder extends IImsCapabilityCallback.Stub {

            private final AvailabilityCallback mLocalCallback;
            private Executor mExecutor;

            CapabilityBinder(AvailabilityCallback c) {
                mLocalCallback = c;
            }

            @Override
            public void onCapabilitiesStatusChanged(int config) {
                if (mLocalCallback == null) return;

                Binder.withCleanCallingIdentity(() ->
                        mExecutor.execute(() -> mLocalCallback.onAvailabilityChanged(
                                new RcsFeature.RcsImsCapabilities(config))));
            }

            @Override
            public void onQueryCapabilityConfiguration(int capability, int radioTech,
                    boolean isEnabled) {
                // This is not used for public interfaces.
            }

            @Override
            public void onChangeCapabilityConfigurationError(int capability, int radioTech,
                    @ImsFeature.ImsCapabilityError int reason) {
                // This is not used for public interfaces
            }

            private void setExecutor(Executor executor) {
                mExecutor = executor;
            }
        }

        private final CapabilityBinder mBinder = new CapabilityBinder(this);

        /**
         * The availability of the feature's capabilities has changed to either available or
         * unavailable.
         * <p>
         * If unavailable, the feature does not support the capability at the current time. This may
         * be due to network or subscription provisioning changes, such as the IMS registration
         * being lost, network type changing, or OMA-DM provisioning updates.
         *
         * @param capabilities The new availability of the capabilities.
         */
        public void onAvailabilityChanged(RcsFeature.RcsImsCapabilities capabilities) {
        }

        /**@hide*/
        public final IImsCapabilityCallback getBinder() {
            return mBinder;
        }

        private void setExecutor(Executor executor) {
            mBinder.setExecutor(executor);
        }
    }

    private final int mSubId;
    private final Context mContext;
    private final ImsUceAdapter mUceAdapter;

    /**
     * Create an instance of ImsRcsManager for the subscription id specified.
     *
     * @param context The context to create this ImsRcsManager instance within.
     * @param subscriptionId The ID of the subscription that this ImsRcsManager will use.
     * @see android.telephony.SubscriptionManager#getActiveSubscriptionInfoList()
     * @throws IllegalArgumentException if the subscription is invalid.
     * @hide
     */
    public static ImsRcsManager createForSubscriptionId(Context context, int subscriptionId) {
        if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
            throw new IllegalArgumentException("Invalid subscription ID");
        }

        return new ImsRcsManager(context, subscriptionId);
    }

    /** @hide */
    @UnsupportedAppUsage
    private ImsRcsManager() {
        this(null, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
    }

    /** @hide */
    @UnsupportedAppUsage
    public ImsRcsManager(Context context) {
        this(context, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
    }

    /**
     * Use {@link #createForSubscriptionId(Context, int)} to create an instance of this class.
     * @hide
     */
    @UnsupportedAppUsage
    private ImsRcsManager(Context context, int subId) {
        mSubId = subId;
        Context appContext = context.getApplicationContext();
        if (appContext != null) {
            mContext = appContext;
        } else {
            mContext = context;
        }

        mUceAdapter = new ImsUceAdapter(mSubId);
    }

    /**
     * @return A {@link ImsUceAdapter} used for User Capability Exchange (UCE) operations for
     * this subscription.
     */
    @NonNull
    public ImsUceAdapter getUceAdapter() {
        return mUceAdapter;
    }

    /**
     * Registers an {@link AvailabilityCallback} with the system, which will provide RCS
     * availability updates for the subscription specified.
     *
     * Use {@link SubscriptionManager.OnSubscriptionsChangedListener} to listen to
     * subscription changed events and call
     * {@link #unregisterRcsAvailabilityCallback(AvailabilityCallback)} to clean up after a
     * subscription is removed.
     * <p>
     * When the callback is registered, it will initiate the callback c to be called with the
     * current capabilities.
     *
     * @param executor The executor the callback events should be run on.
     * @param c The RCS {@link AvailabilityCallback} to be registered.
     * @see #unregisterRcsAvailabilityCallback(AvailabilityCallback)
     * @throws ImsException if the subscription associated with this instance of
     * {@link ImsRcsManager} is valid, but the ImsService associated with the subscription is not
     * available. This can happen if the ImsService has crashed, for example, or if the subscription
     * becomes inactive. See {@link ImsException#getCode()} for more information on the error codes.
     */
    @RequiresPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public void registerRcsAvailabilityCallback(@CallbackExecutor Executor executor,
            @NonNull AvailabilityCallback c) throws ImsException {
        if (c == null) {
            throw new IllegalArgumentException("Must include a non-null AvailabilityCallback.");
        }
        if (executor == null) {
            throw new IllegalArgumentException("Must include a non-null Executor.");
        }

        IImsRcsController imsRcsController = getIImsRcsController();
        if (imsRcsController == null) {
            Log.e(TAG, "Register availability callback: IImsRcsController is null");
            throw new ImsException("IImsRcsController is null");
        }

        c.setExecutor(executor);
        try {
            imsRcsController.registerRcsAvailabilityCallback(c.getBinder());
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling IImsRcsController#registerRcsAvailabilityCallback", e);
        }
    }

    /**
     * Removes an existing RCS {@link AvailabilityCallback}.
     * <p>
     * When the subscription associated with this callback is removed (SIM removed, ESIM swap,
     * etc...), this callback will automatically be unregistered. If this method is called for an
     * inactive subscription, it will result in a no-op.
     * @param c The RCS {@link AvailabilityCallback} to be removed.
     * @see #registerRcsAvailabilityCallback(Executor, AvailabilityCallback)
     */
    @RequiresPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public void unregisterRcsAvailabilityCallback(@NonNull AvailabilityCallback c) {
        if (c == null) {
            throw new IllegalArgumentException("Must include a non-null AvailabilityCallback.");
        }

        IImsRcsController imsRcsController = getIImsRcsController();
        if (imsRcsController == null) {
            Log.e(TAG, "Unregister availability callback: IImsRcsController is null");
            return;
        }

        try {
            imsRcsController.unregisterRcsAvailabilityCallback(c.getBinder());
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling IImsRcsController#unregisterRcsAvailabilityCallback", e);
        }
    }

    /**
     * Query for the capability of an IMS RCS service provided by the framework.
     * <p>
     * This only reports the status of RCS capabilities provided by the framework, not necessarily
     * RCS capabilities provided over-the-top by applications.
     *
     * @param subId the subscription ID.
     * @param capability The RCS capability to query.
     * @return true if the RCS capability is capable for this subscription, false otherwise. This
     * does not necessarily mean that we are registered for IMS and the capability is available, but
     * rather the subscription is capable of this service over IMS.
     * @see #isAvailable(int)
     * @see android.telephony.CarrierConfigManager#KEY_USE_RCS_PRESENCE_BOOL
     */
    @RequiresPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public boolean isCapable(int subId,
            @RcsFeature.RcsImsCapabilities.RcsImsCapabilityFlag int capability) {
        IImsRcsController imsRcsController = getIImsRcsController();
        if (imsRcsController == null) {
            Log.e(TAG, "isCapable: IImsRcsController is null");
            return false;
        }

        try {
            return imsRcsController.isCapable(subId, capability);
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling IImsRcsController#isCapable", e);
        }
        return false;
    }

    /**
     * Query the availability of an IMS RCS capability.
     * <p>
     * This only reports the status of RCS capabilities provided by the framework, not necessarily
     * RCS capabilities provided by over-the-top by applications.
     *
     * @param subId the subscription ID.
     * @param capability the RCS capability to query.
     * @return true if the RCS capability is currently available for the associated subscription,
     * false otherwise. If the capability is available, IMS is registered and the service is
     * currently available over IMS.
     * @see #isCapable(int)
     */
    @RequiresPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public boolean isAvailable(int subId,
            @RcsFeature.RcsImsCapabilities.RcsImsCapabilityFlag int capability) {
        IImsRcsController imsRcsController = getIImsRcsController();
        if (imsRcsController == null) {
            Log.e(TAG, "isAvailable: IImsRcsController is null");
            return false;
        }

        try {
            return imsRcsController.isAvailable(subId, capability);
        } catch (RemoteException e) {
            Log.e(TAG, "Error calling IImsRcsController#isAvailable", e);
        }
        return false;
    }

    /**
     * @hide
     */
    @UnsupportedAppUsage
    private IImsRcsController getIImsRcsController() {
        IBinder binder = ServiceManager.getService(Context.TELEPHONY_RCS_SERVICE);
        return IImsRcsController.Stub.asInterface(binder);
    }
}
