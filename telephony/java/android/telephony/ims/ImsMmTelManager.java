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
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.content.Context;
import android.net.Uri;
import android.os.Binder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.aidl.IImsCapabilityCallback;
import android.telephony.ims.aidl.IImsRegistrationCallback;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.Log;

import com.android.internal.telephony.ITelephony;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Executor;

/**
 * Manager for the MMTEL feature of IMS.
 *
 * @hide
 */
public class ImsMmTelManager {

    private static final String TAG = "ImsMmTelManager";

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "WIFI_MODE_", value = {
            WIFI_MODE_WIFI_ONLY,
            WIFI_MODE_CELLULAR_PREFERRED,
            WIFI_MODE_WIFI_PREFERRED
            })
    public @interface WiFiCallingMode {}

    /**
     * Register for IMS over IWLAN if WiFi signal quality is high enough. Do not hand over to LTE
     * registration if signal quality degrades.
     */
    public static final int WIFI_MODE_WIFI_ONLY = 0;
    /**
     * Prefer registering for IMS over LTE if signal quality is high enough.
     */
    public static final int WIFI_MODE_CELLULAR_PREFERRED = 1;
    /**
     * Prefer registering for IMS over IWLAN if possible if signal quality is high enough.
     */
    public static final int WIFI_MODE_WIFI_PREFERRED = 2;

    /**
     * Callback class for receiving Registration callback events.
     * @see #addImsRegistrationCallback(Executor, RegistrationCallback) (RegistrationCallback)
     * @see #removeImsRegistrationCallback(RegistrationCallback)
     * @hide
     */
    public static class RegistrationCallback {

        private static class RegistrationBinder extends IImsRegistrationCallback.Stub {

            private final RegistrationCallback mLocalCallback;
            private Executor mExecutor;

            RegistrationBinder(RegistrationCallback localCallback) {
                mLocalCallback = localCallback;
            }

            @Override
            public void onRegistered(int imsRadioTech) {
                if (mLocalCallback == null) {
                    return;
                }
                mExecutor.execute(() -> {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mLocalCallback.onRegistered(imsRadioTech);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                });
            }

            @Override
            public void onRegistering(int imsRadioTech) {
                if (mLocalCallback == null) {
                    return;
                }
                mExecutor.execute(() -> {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mLocalCallback.onRegistering(imsRadioTech);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                });
            }

            @Override
            public void onDeregistered(ImsReasonInfo info) {
                if (mLocalCallback == null) {
                    return;
                }
                mExecutor.execute(() -> {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mLocalCallback.onDeregistered(info);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                });
            }

            @Override
            public void onTechnologyChangeFailed(int imsRadioTech, ImsReasonInfo info) {
                if (mLocalCallback == null) {
                    return;
                }
                mExecutor.execute(() -> {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mLocalCallback.onTechnologyChangeFailed(imsRadioTech, info);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                });
            }

            @Override
            public void onSubscriberAssociatedUriChanged(Uri[] uris) {
                if (mLocalCallback == null) {
                    return;
                }
                mExecutor.execute(() -> {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mLocalCallback.onSubscriberAssociatedUriChanged(uris);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                });
            }

            @Override
            public void onSubscriptionRemoved(int subId) {
                mExecutor.execute(() -> {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mLocalCallback.onSubscriptionRemoved(subId);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                });
            }

            private void setExecutor(Executor executor) {
                mExecutor = executor;
            }
        }

        private final RegistrationBinder mBinder = new RegistrationBinder(this);

        /**
         * Notifies the framework when the IMS Provider is connected to the IMS network.
         *
         * @param imsRadioTech the radio access technology. Valid values are defined in
         * {@link ImsRegistrationImplBase.ImsRegistrationTech}.
         */
        public void onRegistered(@ImsRegistrationImplBase.ImsRegistrationTech int imsRadioTech) {
        }

        /**
         * Notifies the framework when the IMS Provider is trying to connect the IMS network.
         *
         * @param imsRadioTech the radio access technology. Valid values are defined in
         * {@link ImsRegistrationImplBase.ImsRegistrationTech}.
         */
        public void onRegistering(@ImsRegistrationImplBase.ImsRegistrationTech int imsRadioTech) {
        }

        /**
         * Notifies the framework when the IMS Provider is disconnected from the IMS network.
         *
         * @param info the {@link ImsReasonInfo} associated with why registration was disconnected.
         */
        public void onDeregistered(ImsReasonInfo info) {
        }

        /**
         * A failure has occurred when trying to handover registration to another technology type,
         * defined in {@link ImsRegistrationImplBase.ImsRegistrationTech}
         *
         * @param imsRadioTech The {@link ImsRegistrationImplBase.ImsRegistrationTech} type that has
         *         failed
         * @param info A {@link ImsReasonInfo} that identifies the reason for failure.
         */
        public void onTechnologyChangeFailed(
                @ImsRegistrationImplBase.ImsRegistrationTech int imsRadioTech, ImsReasonInfo info) {
        }

        /**
         * Returns a list of subscriber {@link Uri}s associated with this IMS subscription when
         * it changes.
         * @param uris new array of subscriber {@link Uri}s that are associated with this IMS
         *         subscription.
         * @hide
         */
        public void onSubscriberAssociatedUriChanged(Uri[] uris) {

        }

        /**
         * The subscription that this callback has been registered for has been removed. This will
         * happen when the subscription has either been removed from the device or the IMS service
         * managing this callback has crashed or become unavailable. This callback will be cleaned
         * up in Telephony and will no longer receive registration updates.
         * No need to call {@link #removeMmTelCapabilityCallback(CapabilityCallback)}.
         * @param subId the ID of the subscription that has been removed.
         */
        public void onSubscriptionRemoved(int subId) {

        }

        /**@hide*/
        public final IImsRegistrationCallback getBinder() {
            return mBinder;
        }

        /**@hide*/
        //Only exposed as public for compatibility with deprecated ImsManager APIs.
        public void setExecutor(Executor executor) {
            mBinder.setExecutor(executor);
        }
    }

    /**
     * Receives IMS capability status updates from the ImsService.
     *
     * @see #addMmTelCapabilityCallback(Executor, CapabilityCallback) (CapabilityCallback)
     * @see #removeMmTelCapabilityCallback(CapabilityCallback)
     *
     * @hide
     */
    public static class CapabilityCallback {

        private static class CapabilityBinder extends IImsCapabilityCallback.Stub {

            private final CapabilityCallback mLocalCallback;
            private Executor mExecutor;

            CapabilityBinder(CapabilityCallback c) {
                mLocalCallback = c;
            }

            @Override
            public void onCapabilitiesStatusChanged(int config) {
                if (mLocalCallback == null) {
                    return;
                }
                mExecutor.execute(() -> {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mLocalCallback.onCapabilitiesStatusChanged(new MmTelFeature
                                .MmTelCapabilities(config));
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                });
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

            @Override
            public void onSubscriptionRemoved(int subId) {
                if (mLocalCallback == null) {
                    return;
                }
                mExecutor.execute(() -> {
                    long token = Binder.clearCallingIdentity();
                    try {
                        mLocalCallback.onSubscriptionRemoved(subId);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                });
            }

            private void setExecutor(Executor executor) {
                mExecutor = executor;
            }
        }

        private final CapabilityBinder mBinder = new CapabilityBinder(this);

        /**
         * The status of the feature's capabilities has changed to either available or unavailable.
         * If unavailable, the feature is not able to support the unavailable capability at this
         * time.
         *
         * @param capabilities The new availability of the capabilities.
         */
        public void onCapabilitiesStatusChanged(
                MmTelFeature.MmTelCapabilities capabilities) {
        }

        /**
         * The subscription that this callback has been registered for has been removed. This
         * callback will be cleaned up in Telephony and will no longer receive capability updates.
         * No need to call {@link #removeMmTelCapabilityCallback}.
         * @param subId the ID of the subscription that has been removed.
         */
        public void onSubscriptionRemoved(int subId) {

        }

        /**@hide*/
        public final IImsCapabilityCallback getBinder() {
            return mBinder;
        }

        /**@hide*/
        //Only exposed as public for compatibility with deprecated ImsManager APIs.
        public void setExecutor(Executor executor) {
            mBinder.setExecutor(executor);
        }
    }

    private Context mContext;
    private int mSubId;

    /**
     * Create an instance of ImsManager for the subscription id specified.
     *
     * @param context
     * @param subId The ID of the subscription that this ImsManager will use.
     * @see android.telephony.SubscriptionManager#getActiveSubscriptionInfoList()
     * @throws IllegalArgumentException if the subscription is invalid.
     * @throws RuntimeException if the IMS service for the given subscription is not available or
     * the subscription ID is not an active subscription.
     */
    public static ImsMmTelManager createForSubscriptionId(Context context, int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)
                || !getSubscriptionManager(context).isActiveSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid subscription ID");
        }

        return new ImsMmTelManager(context, subId);
    }

    private ImsMmTelManager(Context context, int subId) {
        mContext = context;
        mSubId = subId;
    }

    /**
     * Registers a {@link RegistrationCallback} with the system, which will provide registration
     * updates for the subscription specified in {@link #createForSubscriptionId(Context, int)}.
     * @param executor The executor the callback events should be run on.
     * @param c The {@link RegistrationCallback} to be added.
     * @see #removeImsRegistrationCallback(RegistrationCallback)
     * @throws RuntimeException if the IMS service for the given subscription is not available.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public void addImsRegistrationCallback(@CallbackExecutor Executor executor,
            @NonNull RegistrationCallback c) {
        if (c == null) {
            throw new IllegalArgumentException("Must include a non-null RegistrationCallback.");
        }
        if (executor == null) {
            throw new IllegalArgumentException("Must include a non-null Executor.");
        }
        c.setExecutor(executor);
        try {
            getITelephony().addImsRegistrationCallback(mSubId, c.getBinder(),
                    mContext.getOpPackageName());
            return;
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "addImsRegistrationCallback: remote error - could not add callback.");
    }

    /**
     * Removes an existing {@link RegistrationCallback}. Ensure to call this method when cleaning
     * up to avoid memory leaks.
     * @param c The {@link RegistrationCallback} to be removed.
     * @see #addImsRegistrationCallback(Executor, RegistrationCallback)
     * @throws RuntimeException if the IMS service for the given subscription is not available.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public void removeImsRegistrationCallback(@NonNull RegistrationCallback c) {
        if (c == null) {
            throw new IllegalArgumentException("Must include a non-null RegistrationCallback.");
        }
        try {
            getITelephony().removeImsRegistrationCallback(mSubId, c.getBinder(),
                    mContext.getOpPackageName());
            return;
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "removeImsRegistrationCallback: remote error - could not remove callback.");
    }

    /**
     * Registers a {@link CapabilityCallback} with the system, which will provide MmTel capability
     * updates for the subscription specified in {@link #createForSubscriptionId(Context, int)}.
     * @param executor The executor the callback events should be run on.
     * @param c The MmTel {@link CapabilityCallback} to be registered.
     * @see #removeMmTelCapabilityCallback(CapabilityCallback)
     * @throws RuntimeException if the IMS service for the given subscription is not available.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public void addMmTelCapabilityCallback(@CallbackExecutor Executor executor,
            @NonNull CapabilityCallback c) {
        if (c == null) {
            throw new IllegalArgumentException("Must include a non-null RegistrationCallback.");
        }
        if (executor == null) {
            throw new IllegalArgumentException("Must include a non-null Executor.");
        }
        c.setExecutor(executor);
        try {
            getITelephony().addMmTelCapabilityCallback(mSubId, c.getBinder(),
                    mContext.getOpPackageName());
            return;
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "addMmTelCapabilityCallback: remote error - could not add callback.");
    }

    /**
     * Removes an existing MmTel {@link CapabilityCallback}. Be sure to call this when cleaning
     * up to avoid memory leaks.
     * @param c The MmTel {@link CapabilityCallback} to be removed.
     * @see #addMmTelCapabilityCallback(Executor, CapabilityCallback)
     * @throws RuntimeException if the IMS service for the given subscription is not available.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public void removeMmTelCapabilityCallback(@NonNull CapabilityCallback c) {
        if (c == null) {
            throw new IllegalArgumentException("Must include a non-null RegistrationCallback.");
        }
        try {
            getITelephony().removeMmTelCapabilityCallback(mSubId, c.getBinder(),
                    mContext.getOpPackageName());
            return;
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "removeMmTelCapabilityCallback: remote error - could not remove callback.");
    }

    /**
     *  This user setting enables MMTEL IMS features, such as voice and video over IMS, depending
     *  on carrier configuration.
     * @return true if the user’s setting for “Enhanced 4G LTE” is enabled and false otherwise.
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public boolean isEnhanced4gLteSettingEnabled() {
        try {
            return getITelephony().isImsEnhanced4gLteSettingEnabled(mSubId,
                    mContext.getOpPackageName());
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "isEnhanced4gLteSettingEnabled: could not get value, returning false.");
        return false;
    }

    /**
     * Modify the user’s setting for “Enhanced 4G LTE”, which is used to enable MMTEL IMS features,
     * such as voice and video calling over IMS, depending on the carrier configuration.
     * @see #isEnhanced4gLteSettingEnabled()
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setEnhanced4gLteModeSetting(boolean isEnabled) {
        try {
            getITelephony().setImsEnhanced4gLteModeSetting(mSubId, isEnabled);
            return;
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "setEnhanced4gLteModeSetting: could not set value.");
    }

    /**
     * The current VoLTE capability status of the device. This does not mean that voice over LTE is
     * available at the current time.
     * @return true if VoLTE is capable on this device and capable per the current carrier
     * configuration.
     * @see #isVoLteAvailable()
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_VOLTE_AVAILABLE_BOOL
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_IMS_GBA_REQUIRED_BOOL
     * @hide
     */
    @SystemApi
    public boolean isVoLteCapable() {
        try {
            return getITelephony().isVoLteCapable(mSubId);
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "isVoLteCapable: could not get value, returning false.");
        return false;
    }

    /**
     * The current VoLTE availability status of the device. If true, this device is currently
     * registered to the network using IMS and voice
     * @see #isVoLteCapable()
     */
    public boolean isVoLteAvailable() {
        try {
            return getITelephony().isVoLteAvailable(mSubId);
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "isVoLteAvailable: could not get value, returning false.");
        return false;
    }

    /**
     * @return true if Voice over LTE is provisioned on this device or the device does not require
     * carrier provisioning, or false if the device has not been provisioned yet.
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public boolean isVoLteProvisioned() {
        try {
            return getITelephony().isVoLteProvisioned(mSubId, mContext.getOpPackageName());
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "isVoLteProvisioned: could not get value, returning false.");
        return false;
    }

    /**
     * Sets the OMA-DM VoLTE provisioning state for the subscription ID specified.
     * @see #createForSubscriptionId(Context, int)
     * @param isProvisioned true if the subscription is provisioned for VoLTE, false if it is not.
     *         This call will be ignored if the carrier does not require OMA-DM provisioning.
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setVoLteProvisioningState(boolean isProvisioned) {
        try {
            getITelephony().setVoLteProvisioningState(mSubId, isProvisioned);
            return;
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "setVoLteProvisioningState: could not set value.");
    }

    /**
     * The Video Telephony capability of the device, which depends on the carrier configuration.
     * This does not mean that ViLTE is available at the current time.
     * @see #isVtAvailable()
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_VT_AVAILABLE_BOOL
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_IMS_GBA_REQUIRED_BOOL
     * @hide
     */
    @SystemApi
    public boolean isVtCapable() {
        try {
            return getITelephony().isVtCapable(mSubId);
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "isVtCapable: could not get value, returning false.");
        return false;
    }

    /**
     * @return true if IMS is registered and the device is available to place Video Telephony calls.
     * @see #isVtCapable()
     */
    public boolean isVtAvailable() {
        try {
            return getITelephony().isVtAvailable(mSubId);
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "isVtAvailable: could not get value, returning false.");
        return false;
    }

    /**
     * The user's setting for whether or not they have enabled the "Video Calling" setting.
     * @return true if the user’s “Video Calling” setting is currently enabled.
     * @see #setVtUserSetting(boolean)
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public boolean isVtUserSettingEnabled() {
        try {
            return getITelephony().isVtUserSettingEnabled(mSubId, mContext.getOpPackageName());
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "isVtUserSettingEnabled: could not get value, returning false.");
        return false;
    }

    /**
     * Change the user's setting for Video Telephony and enable the Video Telephony capability.
     * @see #isVtUserSettingEnabled()
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setVtUserSetting(boolean isEnabled) {
        try {
            getITelephony().setVtUserSetting(mSubId, isEnabled);
            return;
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "setVtUserSetting: could not set value.");
    }

    /**
     * @return true if Video Telephony is provisioned on this device or the device does not require
     * carrier provisioning, or false if the device has not been provisioned yet.
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public boolean isVtProvisioned() {
        try {
            return getITelephony().isVtProvisioned(mSubId, mContext.getOpPackageName());
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "isVtProvisioned: could not get value, returning false.");
        return false;
    }

    /**
     * Sets the OMA-DM Video Telephony provisioning state for the subscription ID specified.
     * @see #createForSubscriptionId(Context, int)
     * @param isProvisioned true if the subscription is provisioned for VT, false if it is not.
     *         This call will be ignored if the carrier does not require OMA-DM provisioning.
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setVtProvisioningState(boolean isProvisioned) {
        try {
            getITelephony().setVtProvisioningState(mSubId, isProvisioned);
            return;
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "setVtProvisioningState: could not set value.");
    }

    /**
     * @return true if Voice over IWLAN is provisioned on this device or the device does not require
     * carrier provisioning, or false if the device has not been provisioned yet.
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_VOLTE_OVERRIDE_WFC_PROVISIONING_BOOL
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public boolean isVoWiFiProvisioned() {
        try {
            return getITelephony().isVoWiFiProvisioned(mSubId, mContext.getOpPackageName());
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "isVoWiFiProvisioned: could not get value, returning false.");
        return false;
    }

    /**
     * Sets the OMA-DM Voice over WiFi provisioning state for the subscription ID specified.
     * @see #createForSubscriptionId(Context, int)
     * @param isProvisioned true if the subscription is provisioned for VoWiFi, false if it is not.
     *         This call will be ignored if the carrier does not require OMA-DM provisioning.
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setVoWiFiProvisioningState(boolean isProvisioned) {
        try {
            getITelephony().setVoWiFiProvisioningState(mSubId, isProvisioned);
            return;
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "setVoWiFiProvisioningState: could not set value.");
    }

    /**
     * @return true if Voice over WiFi is capable on this device, false if it is not. This does not
     * mean that Voice over WiFi is available.
     * @see #isVoWiFiAvailable()
     * @hide
     */
    @SystemApi
    public boolean isVoWiFiCapable() {
        try {
            return getITelephony().isVoWiFiCapable(mSubId);
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "isVoWiFiCapable: could not get value, returning false.");
        return false;
    }

    /**
     * @return true if IMS is registered over IWLAN and voice is available, false otherwise.
     * @see #isVoWiFiCapable()
     */
    public boolean isVoWiFiAvailable() {
        try {
            return getITelephony().isVoWiFiAvailable(mSubId);
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "isVoWiFiAvailable: could not get value, returning false.");
        return false;
    }

    /**
     * @return true if the user's setting for Voice over WiFi is enabled and false if it is not.
     * @see #setVoWiFiUserSetting(boolean)
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public boolean isVoWiFiUserSettingEnabled() {
        try {
            return getITelephony().isVoWiFiUserSettingEnabled(mSubId, mContext.getOpPackageName());
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "isVoWiFiUserSettingEnabled: could not get value, returning false.");
        return false;
    }

    /**
     * Sets the user's setting for whether or not Voice over WiFi is enabled.
     * @param isEnabled true if the user's setting for Voice over WiFi is enabled, false otherwise=
     * @see #isVoWiFiUserSettingEnabled()
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setVoWiFiUserSetting(boolean isEnabled) {
        try {
            getITelephony().setVoWiFiUserSetting(mSubId, isEnabled);
            return;
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "setVoWiFiUserSetting: could not set value.");
    }

    /**
     * @return true if the user's setting for Voice over WiFi while roaming is enabled, false
     * if disabled.
     * @see #setVoWiFiRoamingUserSetting(boolean)
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public boolean isVoWiFiRoamingUserSettingEnabled() {
        try {
            return getITelephony().isVoWiFiRoamingUserSettingEnabled(mSubId,
                    mContext.getOpPackageName());
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "isVoWiFiRoamingUserSettingEnabled: could not get value, returning false.");
        return false;
    }

    /**
     * Change the user's setting for Voice over WiFi while roaming.
     * @param isEnabled true if the user's setting for Voice over WiFi while roaming is enabled,
     *     false otherwise.
     * @see #isVoWiFiRoamingUserSettingEnabled()
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setVoWiFiRoamingUserSetting(boolean isEnabled) {
        try {
            getITelephony().setVoWiFiRoamingUserSetting(mSubId, isEnabled);
            return;
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "setVoWiFiRoamingUserSetting: could not set value.");
    }

    /**
     * Sets the Voice over WiFi capability to true for IMS, but do not persist the setting.
     * Typically used during the Voice over WiFi registration process for some carriers.
     *
     * @param isCapable true if the IMS stack should try to register for IMS over IWLAN, false
     *     otherwise.
     * @param mode the Voice over WiFi mode preference to set, which can be one of the following:
     * - {@link #WIFI_MODE_WIFI_ONLY}
     * - {@link #WIFI_MODE_CELLULAR_PREFERRED}
     * - {@link #WIFI_MODE_WIFI_PREFERRED}
     * @see #setVoWiFiUserSetting(boolean)
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setVoWiFiNonPersistent(boolean isCapable, int mode) {
        try {
            getITelephony().setVoWiFiNonPersistent(mSubId, isCapable, mode);
            return;
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "setVoWiFiNonPersistent: could not set value.");
    }

    /**
     * @return The Voice over WiFi Mode preference set by the user, which can be one of the
     * following:
     * - {@link #WIFI_MODE_WIFI_ONLY}
     * - {@link #WIFI_MODE_CELLULAR_PREFERRED}
     * - {@link #WIFI_MODE_WIFI_PREFERRED}
     * @see #setVoWiFiUserSetting(boolean)
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public @WiFiCallingMode int getVoWiFiModeUserSetting() {
        try {
            return getITelephony().getVoWiFiModeUserSetting(mSubId, mContext.getOpPackageName());
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "getVoWiFiModeUserSetting: could not get value, returning"
                + "WIFI_MODE_CELLULAR_PREFERRED.");
        return WIFI_MODE_CELLULAR_PREFERRED;
    }

    /**
     * Set the user's preference for Voice over WiFi calling mode.
     * @param mode The user's preference for the technology to register for IMS over, can be one of
     *    the following:
     * - {@link #WIFI_MODE_WIFI_ONLY}
     * - {@link #WIFI_MODE_CELLULAR_PREFERRED}
     * - {@link #WIFI_MODE_WIFI_PREFERRED}
     * @see #getVoWiFiModeUserSetting()
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setVoWiFiModeUserSetting(@WiFiCallingMode int mode) {
        try {
            getITelephony().setVoWiFiModeUserSetting(mSubId, mode);
            return;
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "setVoWiFiModeUserSetting: could not set value.");
    }

    /**
     * Set the user's preference for Voice over WiFi calling mode while the device is roaming on
     * another network.
     *
     * @return The user's preference for the technology to register for IMS over when roaming on
     *     another network, can be one of the following:
     *     - {@link #WIFI_MODE_WIFI_ONLY}
     *     - {@link #WIFI_MODE_CELLULAR_PREFERRED}
     *     - {@link #WIFI_MODE_WIFI_PREFERRED}
     * @see #setVoWiFiRoamingUserSetting(boolean)
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    @WiFiCallingMode int getVoWiFiRoamingModeUserSetting() {
        try {
            return getITelephony().getVoWiFiRoamingModeUserSetting(mSubId,
                    mContext.getOpPackageName());
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "getVoWiFiRoamingModeUserSetting: could not get value, returning"
                + "WIFI_MODE_CELLULAR_PREFERRED.");
        return WIFI_MODE_CELLULAR_PREFERRED;
    }

    /**
     * Set the user's preference for Voice over WiFi mode while the device is roaming on another
     * network.
     *
     * @param mode The user's preference for the technology to register for IMS over when roaming on
     *     another network, can be one of the following:
     *     - {@link #WIFI_MODE_WIFI_ONLY}
     *     - {@link #WIFI_MODE_CELLULAR_PREFERRED}
     *     - {@link #WIFI_MODE_WIFI_PREFERRED}
     * @see #getVoWiFiRoamingModeUserSetting()
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setVoWiFiRoamingModeUserSetting(@WiFiCallingMode int mode) {
        try {
            getITelephony().setVoWiFiRoamingModeUserSetting(mSubId, mode);
            return;
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "setVoWiFiRoamingModeUserSetting: could not set value.");
    }

    /**
     * Change the user's setting for RTT capability of this device.
     * @param isEnabled if true RTT will be enabled during calls.
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setRttCapabilityEnabled(boolean isEnabled) {
        try {
            getITelephony().setRttCapabilityEnabled(mSubId, isEnabled);
            return;
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "setRttCapabilityEnabled: could not set value.");
    }

    /**
     * @return true if TTY over VoLTE is supported
     * @see android.telecom.TelecomManager#getCurrentTtyMode
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_VOLTE_TTY_SUPPORTED_BOOL
     * @hide
     */
    @SystemApi
    boolean isTtyOverVoLteEnabled() {
        try {
            return getITelephony().isTtyOverVoLteEnabled(mSubId);
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
        Log.w(TAG, "isTtyOverVoLteEnabled: could not get value, returning false.");
        return false;
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
