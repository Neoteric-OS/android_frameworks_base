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
import android.content.Context;
import android.os.Binder;
import android.os.ServiceManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.aidl.IImsCapabilityCallback;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.RcsFeature;

import com.android.internal.telephony.ITelephony;

import java.util.concurrent.Executor;

/**
 * Manager for interfacing with the framework RCS services, including the User Capability Exchange
 * (UCE) service, as well as managing user settings.
 * @hide
 */
public class RcsFeatureController {

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

    /**
     * Use {@link #createForSubscriptionId(Context, int)} to instantiate an instance of this
     * manager.
     */
    private RcsFeatureController(int subId) {
        mSubId = subId;
    }

    /**
     * Create an instance of RcsFeatureController for the subscription id specified.
     *
     * @param context The context to create this RcsFeatureController instance within.
     * @param subId The ID of the subscription that this RcsFeatureController will use.
     * @see android.telephony.SubscriptionManager#getActiveSubscriptionInfoList()
     * @throws IllegalArgumentException if the subscription is invalid or
     *         the subscription ID is not an active subscription.
     */
    @NonNull
    public static RcsFeatureController createForSubscriptionId(Context context, int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)
                || !getSubscriptionManager(context).isActiveSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid subscription ID");
        }

        return new RcsFeatureController(subId);
    }

    /**
     * Registers an {@link AvailabilityCallback} with the system, which will provide RCS
     * availability updates for the subscription specified in
     * {@link #createForSubscriptionId(Context, int)}.
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
     */
    @RequiresPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public void registerRcsAvailabilityCallback(@CallbackExecutor Executor executor,
            @NonNull AvailabilityCallback c) {
        if (c == null) {
            throw new IllegalArgumentException("Must include a non-null AvailabilityCallback.");
        }
        if (executor == null) {
            throw new IllegalArgumentException("Must include a non-null Executor.");
        }
        c.setExecutor(executor);
        throw new UnsupportedOperationException("registerRcsAvailabilityCallback is not"
                + "supported.");
    }

    /**
     * Removes an existing RCS {@link AvailabilityCallback}.
     * <p>
     * Be sure to call this when cleaning up to avoid memory leaks.
     * @param c The RCS {@link AvailabilityCallback} to be removed.
     * @see #registerRcsAvailabilityCallback(Executor, AvailabilityCallback)
     */
    @RequiresPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public void unregisterRcsAvailabilityCallback(@NonNull AvailabilityCallback c) {
        if (c == null) {
            throw new IllegalArgumentException("Must include a non-null AvailabilityCallback.");
        }
        throw new UnsupportedOperationException("unregisterRcsAvailabilityCallback is not"
                + "supported.");
    }

    /**
     * Query for the capability of an IMS RCS service provided by the framework.
     * <p>
     * This only reports the status of RCS capabilities provided by the framework, not necessarily
     * RCS capabilities provided over-the-top by applications.
     *
     * @param capability The RCS capability to query.
     * @return true if the RCS capability is capable for this subscription, false otherwise. This
     * does not necessarily mean that we are registered for IMS and the capability is available, but
     * rather the subscription is capable of this service over IMS.
     * @see #isAvailable(int)
     * @see android.telephony.CarrierConfigManager#KEY_USE_RCS_PRESENCE_BOOL
     */
    @RequiresPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public boolean isCapable(@RcsFeature.RcsImsCapabilities.RcsImsCapabilityFlag int capability) {
        throw new UnsupportedOperationException("isCapable is not supported.");
    }
    /**
     * Query the availability of an IMS RCS capability.
     * <p>
     * This only reports the status of RCS capabilities provided by the framework, not necessarily
     * RCS capabilities provided over-the-top by applications.
     *
     * @param capability the RCS capability to query.
     * @return true if the RCS capability is currently available for the associated subscription,
     * false otherwise. If the capability is available, IMS is registered and the service is
     * currently available over IMS.
     * @see #isCapable(int)
     */
    @RequiresPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public boolean isAvailable(@RcsFeature.RcsImsCapabilities.RcsImsCapabilityFlag int capability) {
        throw new UnsupportedOperationException("isAvailable is not supported.");
    }

    /**
     * @return A new {@link RcsUceAdapter} used for User Capability Exchange operations for this
     * subscription.
     */
    @NonNull
    public RcsUceAdapter getUceAdapter() {
        return new RcsUceAdapter(mSubId);
    }

    private static SubscriptionManager getSubscriptionManager(Context context) {
        SubscriptionManager manager = context.getSystemService(SubscriptionManager.class);
        if (manager == null) {
            throw new RuntimeException("Could not find SubscriptionManager.");
        }
        return manager;
    }

    private static ITelephony getITelephony() {
        ITelephony binder = ITelephony.Stub.asInterface(
                ServiceManager.getService(Context.TELEPHONY_SERVICE));
        if (binder == null) {
            throw new RuntimeException("Could not find Telephony Service.");
        }
        return binder;
    }
}
