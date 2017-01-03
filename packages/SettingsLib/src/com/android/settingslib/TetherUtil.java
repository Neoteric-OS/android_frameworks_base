/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.settingslib;

import android.content.Context;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.telephony.CarrierConfigManager;
import android.util.Log;

public class TetherUtil {
    private static final String TAG = "TetherUtil";

    // Return whether tether provisioning is disabled by system properties
    private static boolean isProvisioningDisabled() {
        return SystemProperties.getBoolean("net.tethering.noprovisioning", false);
    }

    public static boolean isProvisioningNeeded(Context context) {
        // Keep in sync with other usages of KEY_MOBILE_HOTSPOT_PROVISION_APP_STRING_ARRAY
        // ConnectivityManager#enforceTetherChangePermission
        // Tethering#isTetherProvisioningRequired

        // If tether provisioning is disabled return false without checking CarrierConfig
        if (isProvisioningDisabled()) {
            return false;
        }

        // These are default config values in case configManager or config is null
        boolean hasProvisioningApp = false;
        boolean isEntitlementCheckRequired = true;

        final CarrierConfigManager configManager = (CarrierConfigManager)
                context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager != null) {
            PersistableBundle b = configManager.getConfig();
            if (b != null) {
                String[] provisionApp = b.getStringArray(CarrierConfigManager.
                        KEY_MOBILE_HOTSPOT_PROVISION_APP_STRING_ARRAY);
                if (provisionApp == null) Log.d(TAG, "provisionApp[] is null");
                if (provisionApp != null && provisionApp.length == 2) {
                    hasProvisioningApp = true;
                }
                isEntitlementCheckRequired = b.getBoolean(
                        CarrierConfigManager.KEY_REQUIRE_ENTITLEMENT_CHECKS_BOOL);
            }
        }

        if (!hasProvisioningApp || !isEntitlementCheckRequired) {
            return false;
        } else {
            return true;
        }
    }
}
