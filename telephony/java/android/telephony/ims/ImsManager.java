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

package android.telephony.ims;


import android.Manifest;
import android.annotation.IntDef;
import android.annotation.RequiresPermission;

import com.android.systemui.plugins.annotations.Requires;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 *
 * @hide
 */
public class ImsManager {

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix="IMS_CONFIG_", value = {
            IMS_CONFIG_OMA_DM_VLT_STATUS,
            IMS_CONFIG_OMA_DM_LVC_STATUS,
            IMS_CONFIG_OMA_DM_VWF_STATUS
    })
    public @interface ImsConfigParameters {}

    /**
     * OMA-DM Voice over LTE provisioned status from the carrier network.
     */
    public static final int IMS_CONFIG_OMA_DM_VLT_STATUS = 10;
    /**
     * OMA-DM Video Telephony provisioned status from the carrier network.
     */
    public static final int IMS_CONFIG_OMA_DM_LVC_STATUS = 11;
    /**
     * OMA-DM Voice over IWLAN provisioned status from the carrier network.
     */
    public static final int IMS_CONFIG_OMA_DM_VWF_STATUS = 28;


    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix="WIFI_MODE_", value = {
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
     * Create an instance of ImsManager for the subscription id specified.
     * @see android.telephony.SubscriptionManager#getActiveSubscriptionInfoList()
     */
    public static ImsManager createForSubscriptionId(int subid) {
        return new ImsManager();
    }

    /**
     *  This user setting enables MMTEL IMS features, such as voice and video over IMS, depending
     *  on carrier configuration.
     * @return true if the user’s setting for “Enhanced 4G LTE” is enabled and false otherwise.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public boolean isEnhanced4gLteSettingEnabled() {
        return false;
    }

    /**
     * Modify the user’s setting for “Enhanced 4G LTE”, which is used to enable MMTEL IMS features,
     * such as voice and video calling over IMS, depending on the carrier configuration.
     * @see #isEnhanced4gLteSettingEnabled()
     */
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setEnhanced4gLteModeSetting(boolean isEnabled) {

    }

    /**
     * The current VoLTE capability status of the device. This does not mean that voice over LTE is
     * available at the current time.
     * @return true if VoLTE is capable on this device and capable per the current carrier
     * configuration.
     * @see #isVoLteAvailable()
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_VOLTE_AVAILABLE_BOOL
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_IMS_GBA_REQUIRED_BOOL
     */
    public boolean isVoLteCapable() {
        return false;
    }

    /**
     * The current VoLTE availability status of the device. If true, this device is currently
     * registered to the network using IMS and voice
     * @see #isVoLteCapable()
     */
    public boolean isVoLteAvailable() {
        return false;
    }

    /**
     * @return true if Voice over LTE is provisioned on this device or the device does not require
     * carrier provisioning, or false if the device has not been provisioned yet.
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL
     * @see IMS_CONFIG_OMA_DM_VLT_STATUS
     */
    public boolean isVoLteProvisioned() {
        return false;
    }

    /**
     * The Video Telephony capability of the device, which depends on the carrier configuration.
     * This does not mean that ViLTE is available at the current time.
     * @see #isVilteAvailable
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_VT_AVAILABLE_BOOL
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_IMS_GBA_REQUIRED_BOOL
     */
    public boolean isVtCapable() {
        return false;
    }

    /**
     * @return true if IMS is registered and the device is available to place Video Telephony calls.
     * @see #isViLteCapable
     */
    public boolean isVtAvailable() {
        return false;
    }

    /**
     * The user's setting for whether or not they have enabled the "Video Calling" setting.
     * @return true if the user’s “Video Calling” setting is currently enabled.
     * @see #setVtUserSetting(boolean)
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public boolean isVtUserSettingEnabled() {

    }

    /**
     * Change the user's setting for Video Telephony and enable the Video Telephony capability.
     * @see #isVtUserSettingEnabled()
     */
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setVtUserSetting(boolean isEnabled) {

    }

    /**
     * @return true if Video Telephony is provisioned on this device or the device does not require
     * carrier provisioning, or false if the device has not been provisioned yet.
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL
     * @see IMS_CONFIG_OMA_DM_LVC_STATUS
     */
    public boolean isVtProvisioned() {
        return false;
    }

    /**
     * @return true if Voice over IWLAN is provisioned on this device or the device does not require
     * carrier provisioning, or false if the device has not been provisioned yet.
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_VOLTE_OVERRIDE_WFC_PROVISIONING_BOOL
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL
     * @see IMS_CONFIG_OMA_DM_VWF_STATUS
     */
    public boolean isVoWiFiProvisioned() {
        return false;
    }

    /**
     * @return true if Voice over WiFi is capable on this device, false if it is not. This does not
     * mean that Voice over WiFi is available.
     * @see #isVoWiFiAvailable()
     */
    public boolean isVoWiFiCapable() {
        return false;
    }

    /**
     * @return true if IMS is registered over IWLAN and voice is available, false otherwise.
     * @see #isVoWiFiCapable()
     */
    public boolean isVoWiFiAvailable() {
        return false;
    }

    /**
     * @return true if the user's setting for Voice over WiFi is enabled and false if it is not.
     * @see #setVoWiFiUserSetting(boolean)
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public boolean isVoWiFiUserSettingEnabled() {
        return false;
    }

    /**
     * Sets the user's setting for whether or not Voice over WiFi is enabled.
     * @param isEnabled true if the user's setting for Voice over WiFi is enabled, false otherwise=
     * @see #isVoWiFiUserSettingEnabled()
     */
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setVoWiFiUserSetting(boolean isEnabled) {
    }

    /**
     * @return true if the user's setting for Voice over WiFi while roaming is enabled, false
     * if disabled.
     * @see #setVoWiFiRoamingUserSetting(boolean)
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public boolean isVoWiFiRoamingUserSettingEnabled() {
        return false;
    }

    /**
     * Change the user's setting for Voice over WiFi while roaming.
     * @param isEnabled true if the user's setting for Voice over WiFi while roaming is enabled,
     *     false otherwise.
     * @see #isVoWiFiRoamingUserSettingEnabled()
     */
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setVoWiFiRoamingUserSetting(boolean isEnabled) {

    }

    /**
     * Sets the Voice over WiFi capability to true for IMS, but do not persist the setting.
     * Typically used during Voice over WiFi registration for some carriers.
     *
     * @param isCapable true if the IMS stack should try to register for IMS over IWLAN, false
     *     otherwise.
     * @see #setVoWiFiUserSetting(boolean)
     */
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setVoWiFiNonPersistent(boolean isCapable) {
    }

    /**
     * @return The Voice over WiFi Mode preference set by the user, which can be one of the
     * following:
     * - {@link #WIFI_MODE_WIFI_ONLY}
     * - {@link #WIFI_MODE_CELLULAR_PREFERRED}
     * - {@link #WIFI_MODE_WIFI_PREFERRED}
     * @see #setVoWiFiUserSetting(boolean)
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    public @WiFiCallingMode int getVoWiFiModeUserSetting() {
        return WIFI_MODE_WIFI_ONLY;
    }

    /**
     * Set the user's preference for Voice over WiFi calling mode.
     * @param mode The user's preference for the technology to register for IMS over, can be one of
     *    the following:
     * - {@link #WIFI_MODE_WIFI_ONLY}
     * - {@link #WIFI_MODE_CELLULAR_PREFERRED}
     * - {@link #WIFI_MODE_WIFI_PREFERRED}
     * @see #getVoWiFiModeUserSetting()
     */
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setVoWiFiModeUserSetting(@WiFiCallingMode int mode) {

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
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    @WiFiCallingMode int getVoWiFiRoamingModeUserSetting() {
        return WIFI_MODE_WIFI_ONLY;
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
     */
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setVoWiFiRoamingModeUserSetting(@WiFiCallingMode int mode) {

    }

    /**
     * Change the user's setting for RTT capability of this device.
     * @param isEnabled if true RTT will be enabled during calls.
     */
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setRttCapabilityEnabled(boolean isEnabled) {
    }

    /**
     * @return true if TTY over VoLTE is supported
     * @see android.telecom.TelecomManager#getCurrentTtyMode
     * @see android.telephony.CarrierConfigManager#KEY_CARRIER_VOLTE_TTY_SUPPORTED_BOOL
     */
    boolean isTtyOverVoLteEnabled() {
        return false;
    }

    /**
     * Reset all user settings to defaults for this carrier.
     */
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    void factoryReset() {

    }
}
